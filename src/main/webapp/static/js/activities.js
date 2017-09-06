var MeTLActivities = (function(){
	var templates = {};
	var containerRoot = undefined;

	var bus = MeTLBus;
	var conversation = undefined;
	var target = "presentationSpace";
	var slide = undefined;
	var author = undefined;
	bus.subscribe("receiveUsername","activities",function(u){
		author = u;
	});
	bus.subscribe("receiveConversationDetails","activities",function(cd){
		conversation = cd;
	});
	var currentActivity = {};
	bus.subscribe("receiveSlideDetails","activites",function(s){
		console.log("slide received:",s);
		if ("slideType" in s){
			if (slide === undefined || ("id" in slide && "id" in s && s.id != slide.id)){
				slide = s;
				switch (slide.slideType){
					case "SLIDE":
						var activity = activateActivity(createMeTLCanvasActivity(bus,author,target,slide.id));
						break;
					default:
						break;
				}
			}
		}
	});

	$(function(){
		_.forEach($("#activityTemplates").children(),function(tr){
			var templateRoot = $(tr);
			var templateKey = templateRoot.attr("templateName");
			templates[templateKey] = templateRoot.clone();
		});
		$("#activityTemplates").empty();
		containerRoot = $("#metlContainerRoot");
	});

	var createAuditCanvasActivity = function(bus,slideId){
		var rootElem = templates["canvasAudit"].clone();
		var allStanzas = [];
		var renderer = createCanvasRenderer(rootElem.find(".auditView"));
		var slider = rootElem.find(".auditSlider");
		renderer.setDimensions({width:640,height:480});
		var reRender = function(stanzas){
			renderer.setHistory({
				inks:{},
				highlighters:{},
				images:{},
				videos:{},
				texts:{},
				multiWordTexts:{}
			});
			_.forEach(stanzas,function(s){
				renderer.addStanza(s);
			});
		};
		return {
			activate:function(){
				slider.on("change",function(){
					var val = $(this).val();
					var stanzas = _.take(allStanzas,val);
					reRender(stanzas);
					console.log("rendered",stanzas);
				});

				containerRoot.html(rootElem);
				slider.focus();
			},
			deactivate:function(){
				rootElem.empty();
			},	
			rootElem:rootElem,
			renderer:renderer,
			slider:slider,
			setStanzas:function(stanzas){
				allStanzas = stanzas;
				slider.focus();
			}
		};
	};

	var createMeTLCanvasActivity = function(bus,author,target,slideId){
		var busId = "canvas_" + new Date().getTime().toString(); 
		var rootElem = templates["canvas"].clone();
		var boardElemSelector = rootElem.find(".metlCanvas");
		var newCanvas = createInteractiveCanvas(boardElemSelector);
		newCanvas.setImageSourceCalculationFunction(function(image){
			var slide = image.privacy.toUpperCase() == "PRIVATE" ? sprintf("%s%s",image.slide,image.author) : image.slide;
			return sprintf("/proxyImageUrl/%s?source=%s",urlEncodeSlideName(slide),encodeURIComponent(image.source.trim()));
		});
		newCanvas.setVideoSourceCalculationFunction(function(video){
			var slide = video.privacy.toUpperCase() == "PRIVATE" ? sprintf("%s%s",video.slide,video.author) : video.slide;
			return sprintf("/videoProxy/%s/%s",urlEncodeSlideName(slide),encodeURIComponent(video.identity.trim()));
		});
		newCanvas.onSelectionChanged(function(selected){
				console.log("selected",selected);
			if (_.some(selected,function(category,categoryName){ return _.size(category) > 0; })){
				rootElem.find("#deleteSelection").show();
				if (_.some(selected,function(category,categoryName){return _.find(category,function(item){return "privacy" in item && item.privacy.toLowerCase() == Privacy.privatePrivacy;}) !== undefined})){
					rootElem.find("#showSelection").show();
				}
				if (_.some(selected,function(category,categoryName){
						return _.find(category,function(item){
							return "privacy" in item && item.privacy.toLowerCase() == Privacy.publicPrivacy;}) !== undefined;
						})){
					rootElem.find("#hideSelection").show();
				}
			} else {
				rootElem.find("#deleteSelection").hide();
				rootElem.find("#showSelection").hide();
				rootElem.find("#hideSelection").hide();
			}
		});
		newCanvas.onModeChanged(function(mode){
			rootElem.find(".subTools").hide();
			rootElem.find(".modeButton").removeClass("activeMode");
			switch (mode.name){
				case "select":
					rootElem.find("#setSelectMode").addClass("activeMode");
					rootElem.find("#selectTools").show();
					console.log("selectModeSelected");
					break;
				case "zoom":
					rootElem.find("#setZoomMode").addClass("activeMode");
					rootElem.find("#zoomTools").show();
					break;
				case "pan":
					rootElem.find("#setPanMode").addClass("activeMode");
					rootElem.find("#panTools").show();
					break;
				case "draw":
					rootElem.find("#setDrawMode").addClass("activeMode");
					var color = mode.getColor();
					var isHighlighter = mode.getIsHighlighter();
					var size = mode.getSize();
					rootElem.find("#inkWidth").val(size);
					rootElem.find("#inkColor").val(color);
					rootElem.find("#isHighlighter").val(isHighlighter);
					rootElem.find("#drawTools").show();
					break;
				case "pan":
					rootElem.find("#setEraseMode").addClass("activeMode");
					rootElem.find("#eraseTools").show();
					break;
				case "richText":
					rootElem.find("#setRichTextMode").addClass("activeMode");
					rootElem.find("#richTextTools").show();	
				default:
					break;	 
			}
		});
		rootElem.find("#setZoomMode").on("click",function(){
			newCanvas.setMode(_.find(newCanvas.getAvailableModes(),function(m){return m.name == "zoom";}));
		});
		rootElem.find("#setPanMode").on("click",function(){
			newCanvas.setMode(_.find(newCanvas.getAvailableModes(),function(m){return m.name == "pan";}));
		});
		rootElem.find("#setDrawMode").on("click",function(){
			newCanvas.setMode(_.find(newCanvas.getAvailableModes(),function(m){return m.name == "draw";}));
		});
		rootElem.find("#setSelectMode").on("click",function(){
			newCanvas.setMode(_.find(newCanvas.getAvailableModes(),function(m){return m.name == "select";}));
		});

		var processOutboundStanza = function(stanza){
			stanza.author = author;//"testUser";
			stanza.privacy = Privacy.getPrivacy();
			stanza.target = target;//"presentationSpace";
			stanza.slide = slideId;//"thisSlide";
			//stanza.timestamp = new Date().getTime();
			console.log("test.html stanza:",stanza);
			//stanzas.push(stanza);

			//newCanvas.addStanza(stanza);
			sendStanza(stanza);
		};
		bus.subscribe("receiveMeTLStanza",busId,function(stanza){
			newCanvas.addStanza(stanza);
		});
		var Privacy = (function(){
				var publicMode = "public";
				var privateMode = "private";
				var myPrivacy = publicMode;
				var publicSelector = rootElem.find("#setPublicMode");
				var privateSelector = rootElem.find("#setPrivateMode");
				publicSelector.on("click",function(){
					myPrivacy = publicMode;
					reRenderPrivacyButtons();
				});
				privateSelector.on("click",function(){
					myPrivacy = privateMode;
					reRenderPrivacyButtons();
				});
				var reRenderPrivacyButtons = function(){
					publicSelector.prop("checked",myPrivacy == publicMode);
					privateSelector.prop("checked",myPrivacy == privateMode);
				};
				var hideSelector = rootElem.find("#hideSelection");
				var showSelector = rootElem.find("#showSelection");
				hideSelector.on("click",function(){
					var selected = newCanvas.getSelected();
					console.log("got selected",selected);
					var transform = newCanvas.createBatchTransform();
					_.forEach(selected,function(category,categoryName){
						var catName = categoryName.substr(0,_.size(categoryName) - 1) + "Ids";
						console.log("selecting",category,categoryName,catName);
						transform[catName] = _.map(category,function(stanza){
							return stanza.identity;
						});
					});
					transform.newPrivacy = privateMode;
					processOutboundStanza(transform);
				});
				showSelector.on("click",function(){
					var selected = newCanvas.getSelected();
					console.log("got selected",selected);
					var transform = newCanvas.createBatchTransform();
					_.forEach(selected,function(category,categoryName){
						var catName = categoryName.substr(0,_.size(categoryName) - 1) + "Ids";
						console.log("selecting",category,categoryName,catName);
						transform[catName] = _.map(category,function(stanza){
							return stanza.identity;
						});
					});
					transform.newPrivacy = publicMode;
					processOutboundStanza(transform);
				});

				newCanvas.onPostTransformItem(function(item,transform){
					console.log("onPostTransformItem",item,transform);	
					if (transform.newPrivacy == publicMode){
						item.privacy = publicMode;
					} else if (transform.newPrivacy == privateMode){
						item.privacy = privateMode;
					}
				});
				reRenderPrivacyButtons();
				var beforePreRenderPrivacyStylingFunc = function(item,context,scaleMeasurements,additionalArgs){
					if ("privacy" in item && "type" in item){
						switch (item.type){
							case "ink":
								if ("points" in additionalArgs){
									var points = additionalArgs.points
									var contentOffsetY = additionalArgs.contentOffsetY;
									var contentOffsetX = additionalArgs.contentOffsetX;
									var scaledThickness = additionalArgs.scaledThickness;
									if(item.privacy.toLowerCase() == privateMode){
										x = points[0] + contentOffsetX;
										y = points[1] + contentOffsetY;
										context.lineWidth = scaledThickness;
										context.lineCap = "round";
										context.strokeStyle = "red";
										context.globalAlpha = 0.3;
										context.moveTo(x,y);
										for(p = 0; p < points.length; p += 3){
											context.beginPath();
											context.moveTo(x,y);
											x = points[p]+contentOffsetX;
											y = points[p+1]+contentOffsetY;
											pr = scaledThickness * points[p+2];
											context.lineWidth = pr + Math.max(2,pr * 0.2);
											context.lineTo(x,y);
											context.stroke();
										}
										context.globalAlpha = 1.0;
									}
								}
								break;
							default:
								break;
						}
					}		
				};
				var afterPreRenderPrivacyStylingFunc = function(item,context,scaleMeasurements,additionalArgs){
					if ("privacy" in item && "type" in item){
						switch (item.type){
							case "image":
								if(item.privacy.toLowerCase() == privateMode){ //red for private
									context.globalAlpha = 0.2;
									context.fillStyle = "red";
									context.fillRect(0,0,scaleMeasurements.width + additionalArgs.borderWidth,scaleMeasurements.height + additionalArgs.borderHeight);
									context.globalAlpha = 1.0;
								} else { // blue for public
									context.globalAlpha = 0.2;
									context.fillStyle = "blue";
									context.fillRect(0,0,scaleMeasurements.width + additionalArgs.borderWidth,scaleMeasurements.height + additionalArgs.borderHeight);
									context.globalAlpha = 1.0;
								}
								break;
							case "text":
								if(item.privacy.toLowerCase() == privateMode){ //red for private
									context.globalAlpha = 0.2;
									context.fillStyle = "red";
									context.fillRect(0,0,scaleMeasurements.width,scaleMeasurements.height);
									context.globalAlpha = 1.0;
								} else { // blue for public
									context.globalAlpha = 0.2;
									context.fillStyle = "blue";
									context.fillRect(0,0,scaleMeasurements.width,scaleMeasurements.height);
									context.globalAlpha = 1.0;
								}
								break;
							default:
								break;
						}
					}		
				};
				var beforeRenderPrivacyStylingFunc = function(item,context,sBounds){
					if ("privacy" in item && "type" in item){
						switch (item.type){
							default:
								break;
						}
					}		
				};
				var afterRenderPrivacyStylingFunc = function(item,context,sBounds){
					if ("privacy" in item && "type" in item){
						switch (item.type){
							case "video":
								var l = sBounds.screenPos.x, t = sBounds.screenPos.y, w = sBounds.screenWidth, h = sBounds.screenHeight;
								if(item.privacy.toLowerCase() == privateMode){ //red for private
									context.globalAlpha = 0.2;
									context.fillStyle = "red";
									context.fillRect(l,t,w,h);
									context.globalAlpha = 1.0;
								} else { // blue for public
									context.globalAlpha = 0.2;
									context.fillStyle = "blue";
									context.fillRect(l,t,w,h);
									context.globalAlpha = 1.0;
								}
								break;
							case "multiWordText":
								var l = sBounds.screenPos.x, t = sBounds.screenPos.y, w = sBounds.screenWidth, h = sBounds.screenHeight;
								if(item.privacy.toLowerCase() == privateMode){ //red for private
									context.globalAlpha = 0.2;
									context.fillStyle = "red";
									context.fillRect(l,t,w,h);
									context.globalAlpha = 1.0;
								} else { // blue for public
									context.globalAlpha = 0.2;
									context.fillStyle = "blue";
									context.fillRect(l,t,w,h);
									context.globalAlpha = 1.0;
								}
								break;
							default:
								break;
						}
					}		
				};
				return {
					publicPrivacy:publicMode,
					privatePrivacy:privateMode,
					beforePreRenderPrivacyStyling:beforePreRenderPrivacyStylingFunc,
					afterPreRenderPrivacyStyling:afterPreRenderPrivacyStylingFunc,
					beforeRenderPrivacyStyling:beforeRenderPrivacyStylingFunc,
					afterRenderPrivacyStyling:afterRenderPrivacyStylingFunc,
					getPrivacy:function(){
						return myPrivacy;
					}
				};
		})();

		rootElem.find("#deleteSelection").on("click",function(){
			var selectMode = _.find(newCanvas.getAvailableModes(),function(m){return m.name == "select"});
			selectMode.deleteSelection();
		});
		rootElem.find("#inkColor").on("change",function(e){
			var value = $(this).val();
			var drawMode = _.find(newCanvas.getAvailableModes(),function(m){return m.name == "draw"});
			drawMode.setColor(value);
			//newCanvas.setMode(drawMode);
		});
		rootElem.find("#inkWidth").on("change",function(e){
			var value = $(this).val();
			var drawMode = _.find(newCanvas.getAvailableModes(),function(m){return m.name == "draw"});
			drawMode.setSize(value);
			//newCanvas.setMode(drawMode);
		});
		rootElem.find("#isHighlighter").on("click",function(e){
			var value = $(this).prop("checked");
			var drawMode = _.find(newCanvas.getAvailableModes(),function(m){return m.name == "draw"});
			drawMode.setIsHighlighter(value);
			//newCanvas.setMode(drawMode);
		});
		rootElem.find("#setEraseMode").on("click",function(){
			newCanvas.setMode(_.find(newCanvas.getAvailableModes(),function(m){return m.name == "erase";}));
		});
		rootElem.find("#setRichTextMode").on("click",function(){
			newCanvas.setMode(_.find(newCanvas.getAvailableModes(),function(m){return m.name == "richText";}));
		});
		var fonts = ["arial","verdana","garamond","wingdings","times new roman"];
		var sizes = [8,10,12,14,16,20,24,30,36,48];
		var colors = [
		{
			name:"",
			code:""
		},
		{
			name:"black",
			code:"#000000"
		},
		{
			name:"red",
			code:"#FF0000"
		},	
		{
			name:"green",
			code:"#00FF00"
		},
		{
			name:"blue",
			code:"#0000FF"
		},	
		{
			name:"white",
			code:"#FFFFFF"
		}];
		var hasBuiltText = false;
		var respondToTextAttributes = function(attrs){
			var tempFonts = _.clone(fonts);
			var tempSizes = _.clone(sizes);
			var tempColors = _.clone(colors);
			var fontSelector = rootElem.find("#textFont");
			var sizeSelector = rootElem.find("#textSize");
			var colorSelector = rootElem.find("#textColor");
			var boldSelector = rootElem.find("#isBold");
			var italicSelector = rootElem.find("#isItalic");
			var underlineSelector = rootElem.find("#isUnderline");	

			var updateSelect = function(select,collection,optionFunc,newValue,method,valueMutator){
				if (newValue){
					collection.push(newValue);
				}
				select.html(_.map(collection,function(item){
					var i = optionFunc(item);
					return $("<option/>",{
						value:i.value,
						text:i.name
					});
				}));
				if (!hasBuiltText){
					select.unbind("change").on("change",function(){
						var value = $(this).val();
						if (valueMutator !== undefined){
							value = valueMutator(value);
						}
						_.find(newCanvas.getAvailableModes(),function(m){return m.name == "richText";})[method](value);
					});
				}
				if (newValue){
					select.val(newValue);
				}
			}
			updateSelect(fontSelector,tempFonts,function(font){return {name:font,value:font};},attrs.font,"setFont");
			updateSelect(sizeSelector,tempSizes,function(size){return {name:size,value:size};},attrs.size,"setFontSize");
			var color = "";
			if (attrs.color && attrs.color != {} && attrs.color[0]){
				color = attrs.color[0];
			}
			updateSelect(colorSelector,tempColors,function(c){return {name:c.name,value:c.code};},color,"setFontColor",function(c){return [c,255];});
			var updateCheckbox = function(cb,newValue,method){
				if (!hasBuiltText){
					cb.unbind("click").on("click",function(){
						var value = $(this).prop("checked");
						_.find(newCanvas.getAvailableModes(),function(m){return m.name == "richText";})[method](value);
					});
				}
				if (newValue === true || newValue === false){
					cb.prop("checked",newValue);
				}
			};
			updateCheckbox(boldSelector,attrs.bold,"setFontBold");
			updateCheckbox(italicSelector,attrs.italic,"setFontItalic");
			updateCheckbox(underlineSelector,attrs.underline,"setFontUnderline");
			hasBuiltText = true;
		};
		_.find(newCanvas.getAvailableModes(),function(m){return m.name == "richText";}).onTextAttributesChanged(function(font,size,color,bold,italic,underline){
			respondToTextAttributes({
				font:font,
				size:size,
				color:color,
				bold:bold,
				italic:italic,
				underline:underline
			});	
		});
		respondToTextAttributes(_.find(newCanvas.getAvailableModes(),function(m){return m.name == "richText";}).getFontAttributes());
		rootElem.find("#zoomIn").on("click",function(){
			newCanvas.getZoomController().in();
		});
		rootElem.find("#zoomOut").on("click",function(){
			newCanvas.getZoomController().out();
		});
		var panAmount = 50;
		rootElem.find("#panLeft").on("click",function(){
			newCanvas.getPanController().pan(-1 * panAmount,0);
		});
		rootElem.find("#panRight").on("click",function(){
			newCanvas.getPanController().pan(panAmount,0);
		});
		rootElem.find("#panUp").on("click",function(){
			newCanvas.getPanController().pan(0,-1 * panAmount);
		});
		rootElem.find("#panDown").on("click",function(){
			newCanvas.getPanController().pan(0,panAmount);
		});
		newCanvas.onStatistic(function(category,time,success,exception){
			if ("HealthChecker" in window){
				HealthChecker.addMeasure(category,time,success);
			}
		});
		newCanvas.onError(function(exception,caller,params){
			console.log("InteractiveCanvasException",caller,exception,params);
		});
		newCanvas.onBeforePreRenderItem(function(item,ctx,scaleMeasurements,params){
			Privacy.beforePreRenderPrivacyStyling(item,ctx,scaleMeasurements,params);
			return true;
		});
		newCanvas.onAfterPreRenderItem(function(item,ctx,scaleMeasurements,params){
			Privacy.afterPreRenderPrivacyStyling(item,ctx,scaleMeasurements,params);
		});
		newCanvas.onBeforeRenderItem(function(item,ctx,sBounds){
			Privacy.beforeRenderPrivacyStyling(item,ctx,sBounds);
			return true;
		});
		newCanvas.onAfterRenderItem(function(item,ctx,sBounds){
			Privacy.afterRenderPrivacyStyling(item,ctx,sBounds);
		});
		newCanvas.onPreSelectItem(function(item){
//				console.log("preSelect",item);
			return true;
		});
		newCanvas.onPostSelectItem(function(item){
//				console.log("postSelect",item);
		});
		newCanvas.onStanzaAvailable(function(stanza){
			processOutboundStanza(stanza);
		});
		var reduceCanvas = function(dims){
			var gutter = 10;
			var header = rootElem.find("#metlHeaderContainer");
			var headerHeight = header.height();
			return {
				width:dims.width - gutter,
				height:dims.height - headerHeight - gutter
			};
		};
		return {
			canvas:newCanvas,
			activate:function(){
				bus.subscribe("layoutUpdated",busId,function(dims){
					var reduced = reduceCanvas(dims);
					newCanvas.setDimensions(reduced);
				});
				bus.subscribe("beforeWorkQueueResume",busId,function(){});
				bus.subscribe("afterWorkQueuePause",busId,function(){});
				bus.subscribe("receiveMeTLStanza",busId,function(s){
					newCanvas.addStanza(s);
				});
				bus.subscribe("receiveHistory",busId,function(h){
					newCanvas.setHistory(h);
				});
				containerRoot.html(rootElem);
				newCanvas.setDimensions(reduceCanvas(DeviceConfiguration.getMeasurements()));
				newCanvas.setMode(_.find(newCanvas.getAvailableModes(),function(m){return m.name == "draw";}));
				joinRoom(slideId);
			},
			deactivate:function(){
				bus.unsubscribe("layoutUpdated",busId);
				bus.unsubscribe("beforeWorkQueueResume",busId);
				bus.unsubscribe("afterWorkQueuePause",busId);
				bus.unsubscribe("receiveHistory",busId);
				bus.unsubscribe("receiveMeTLStanza",busId);
				rootElem.empty();
			}			
		};
	};
	var activateActivity = function(activity){
		if ("deactivate" in currentActivity){
			currentActivity.deactivate();
		}
		currentActivity = activity;
		activity.activate();
		return activity;
	}

	return {
		getBus:function(){ return bus; },
		getConversation:function(){ return conversation; },
		getSlide:function(){ return slide; },
		getAtivity:function(){ return currentActivity; },
		override:{
			audit:function(slideId){
				return activateActivity(createAuditCanvasActivity(bus,slideId));
			},
			canvas:function(slideId){
				return activateActivity(createMeTLCanvasActivity(bus,author,target,slideId));
			}	
		}
	};
})();
