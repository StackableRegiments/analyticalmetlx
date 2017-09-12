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
		console.log("conversation received",cd);
		var oldConv = conversation;
		conversation = cd;
		reRenderConversations();
	});
	bus.subscribe("receiveMeTLStanza","activities",function(stanza){
		if ("type" in stanza){
			switch (stanza.type){
				case "command":
					// perhaps it's a syncMove?
					break;
				default:
					break;
			}
		}
	});
	bus.subscribe("layoutUpdated","activities",function(dims){
		_.defer(function(){
			var reduced = reduceCanvas(dims);
			$("#metlContainerRoot").height(reduced.height);
		});
	});

	var renderTimestamp = function(ts){
		// I'd like to have uniform representations of datetimes, so that we can be neat.
		return new Date(ts).toJSON();
	};
	var renderAuthor = function(a){
		if ("Profiles" in window){
			var prof = Profiles.getProfileForId(a);
			if (prof !== undefined && "name" in prof){
				return {
					name:prof.name,
					avatarUrl:prof.attributes.avatarUrl
				};	
			}
		} 
		return {
			name:a
		};
	};

	var updateQueryParams = function(){
		if (window != undefined && "history" in window && "pushState" in window.history){
				var l = window.location;
				var c = conversation;
				var s = slide;
				var newUrl = sprintf("%s//%s%s",l.protocol,l.host,l.pathname);
				if (c != undefined && "jid" in c && s != undefined && "id" in s){
						newUrl = sprintf("%s?conversationJid=%s&slideId=%s&unique=true",newUrl,c.jid,s.id);
				}
				window.history.replaceState({
						path:newUrl,
						url:newUrl
				},newUrl,newUrl);
		}
		if (s != undefined && "id" in s && document != undefined && "title" in document){
				document.title = sprintf("MeTL - %s",s.id.toString());
		}
	};
	var reRenderConversations = function(){
		updateQueryParams();
		if (conversation !== undefined){
			var rootElem = conversationTemplate.clone();
			rootElem.find(".conversationTitle").text(conversation.title);
			var prevSlide = rootElem.find(".prevSlideContainer");
			var nextSlide = rootElem.find(".nextSlideContainer");
			var slideInConv = undefined;
		 	if (slide !== undefined && "id" in slide){
		 	 	slideInConv = _.find(conversation.slides,function(s){ return s.id == slide.id; });
			}
			if (slideInConv === undefined){
				slideInConv = slide;
			}
			if ("slides" in conversation && _.size(conversation.slides) > 1 && slideInConv !== undefined && _.minBy(conversation.slides,"index").index != slideInConv.index){
				var pSlide = _.maxBy(_.filter(conversation.slides,function(s){
					return s.index < slideInConv.index;
				}),"index");
				prevSlide.find(".prevSlideButton").on("click",function(){
					moveToSlide(pSlide.id);
				});
				prevSlide.find(".prevSlideThumbnail").attr("src","/thumbnail/"+pSlide.id);
				prevSlide.find(".prevSlideDescription").text(pSlide.slideType + "_"+pSlide.id);
			} else {
				prevSlide.empty();
			}
			if ("slides" in conversation && _.size(conversation.slides) > 1 && slideInConv !== undefined && _.maxBy(conversation.slides,"index").index != slideInConv.index){
				var nSlide = _.minBy(_.filter(conversation.slides,function(s){
					return s.index > slideInConv.index;
				}),"index");
				nextSlide.find(".nextSlideButton").on("click",function(){
					moveToSlide(nSlide.id);
				});
				nextSlide.find(".nextSlideThumbnail").attr("src","/thumbnail/"+nSlide.id);
				nextSlide.find(".nextSlideDescription").text(nSlide.slideType + "_"+nSlide.id);
			} else {
				nextSlide.empty();
			}	
			conversationRoot.html(rootElem);
		} else {
			conversationRoot.empty();
		}
		bus.call("layoutUpdated",[DeviceConfiguration.getMeasurements()]);
	};
	var currentActivity = {};
	bus.subscribe("receiveSlideDetails","activites",function(s){
		console.log("slide received:",s);
		if ("slideType" in s){
			if (slide === undefined || ("id" in slide && "id" in s && s.id != slide.id)){
				slide = s;
				switch (slide.slideType){
					case "SLIDE":
						var activity = activateActivity(createMeTLCanvasActivity(bus,author,target,slide.id),containerRoot);
						break;
					case "QUIZ":
						var activity = activateActivity(createQuizActivity(bus,author,slide.id,author == slide.author),containerRoot);
						break;
					case "FORUM":
						var activity = activateActivity(createForumActivity(bus,author,slide.id,author == slide.author),containerRoot);
						break;
					default:
						break;
				}
			}
		}
		reRenderConversations();
	});
	var reduceCanvas = function(dims){
		var gutter = 15;
		var header = $("#metlHeaderContainer");
		var headerHeight = header.height();
		var conversationHeader = $("#conversationRoot");
		var conversationHeaderHeight = conversationHeader.height();
		console.log("reduced:",dims,headerHeight,conversationHeaderHeight);
		return {
			width:dims.width - gutter,
			height:dims.height - (headerHeight + conversationHeaderHeight + gutter)
		};
	};
	$(function(){
		_.forEach($("#activityTemplates").children(),function(tr){
			var templateRoot = $(tr);
			var templateKey = templateRoot.attr("templateName");
			templates[templateKey] = templateRoot.clone();
		});
		$("#activityTemplates").empty();
		containerRoot = $("#metlContainerRoot");
		conversationRoot = $("#conversationRoot");
		conversationTemplate = conversationRoot.find(".conversationTemplate").clone();
		conversationRoot.empty();
		reRenderConversations();
	});

	var createAuditCanvasActivity = function(bus,slideId){
		var busId = "audit_canvas_" + new Date().getTime().toString(); 
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
		var history = {};
		return {
			activate:function(root){
				bus.subscribe("layoutUpdated",busId,function(dims){
					var reduced = reduceCanvas(dims);
					renderer.setDimensions(reduced);
				});
				slider.on("change",function(){
					var val = $(this).val();
					var stanzas = _.take(allStanzas,val);
					reRender(stanzas);
					console.log("rendered",stanzas);
				});
				root.html(rootElem);
				renderer.setDimensions(reduceCanvas(DeviceConfiguration.getMeasurements()));
				bus.subscribe("receiveMeTLStanza",busId,function(s){
					allStanzas.push(s);
					slider.attr("max",_.size(allStanzas));
				});
				bus.subscribe("receiveHistory",busId,function(h){
					if ("jid" in h && h.jid == slideId){
						allStanzas = _.sortBy(_.concat(_.values(h.inks),_.values(h.images),_.values(h.texts),_.values(h.videos),_.values(h.multiWordTexts)),function(i){return i.timestamp;});
						slider.attr("max",_.size(allStanzas));
						reRender(allStanzas);
					}
				});
				joinRoom(slideId);
				slider.focus();
			},
			deactivate:function(){
				bus.unsubscribe("receiveMeTLStanza",busId);
				bus.unsubscribe("receiveHistory",busId);
				leaveRoom(slideId);
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
		var selectAdornerSelector = rootElem.find(".selectionAdorner");
		var textInputInvisibleHostSelector = rootElem.find(".textInputInvisibleHost");
		var radarSelector = rootElem.find(".radar");

		var newCanvas = createInteractiveCanvas(boardElemSelector,selectAdornerSelector,textInputInvisibleHostSelector);
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
			stanza.author = author;
			stanza.privacy = Privacy.getPrivacy();
			stanza.target = target;
			stanza.slide = slideId;
			console.log("canvasProcessOutboundStanza:",stanza);
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
		rootElem.find("#insertImage").on("click",function(){

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

		return {
			canvas:newCanvas,
			activate:function(root){
				bus.subscribe("layoutUpdated",busId,function(dims){
					_.defer(function(){
						var reduced = reduceCanvas(dims);
						selectAdornerSelector.height(reduced.height).width(reduced.width);
						newCanvas.setDimensions(reduced);
					});
				});
				bus.subscribe("beforeWorkQueueResume",busId,function(){});
				bus.subscribe("afterWorkQueuePause",busId,function(){});
				bus.subscribe("receiveMeTLStanza",busId,function(s){
					newCanvas.addStanza(s);
				});
				bus.subscribe("receiveHistory",busId,function(h){
					if ("jid" in h && h.jid == slideId){
						newCanvas.setHistory(h);
					}
				});
				root.html(rootElem);
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
				leaveRoom(slideId);
				rootElem.empty();
			}			
		};
	};
	var createForumActivity = function(bus,author,slideId,authoring){
		var busId = "forumActivity_"+new Date().getTime()+"_"+slideId;
		var history = {
			forumPosts:{}
		}
		var rootElem = templates["forum"].clone();
		var postsContainer = rootElem.find(".posts");
		var addPostButton = rootElem.find(".addPostButton");
		var postTemplate = postsContainer.find(".post").clone();
		var editPostTemplate = rootElem.find(".editPostTemplate").clone();
		rootElem.find(".editPostTemplate").remove();
		postsContainer.empty();
		var renderEditPost = function(post){
			var alertId = "editPostContainer" + _.uniqueId().toString();
			var sendPost = function(){
				sendStanza(postStanza);
			};
			var alertRoot = editPostTemplate.clone();
			var alertContainer = $("<div/>",{
				id:alertId
			});
			var jAlert = $.jAlert({
				title:"forum post",
				width:"auto",
				content:alertContainer[0].outerHTML,
				onClose:function(){
					// should it submit on close?  Probably not.  This would enable people to back out, using the close button.
				}
			});
			var textInput = alertRoot.find(".postText");
			textInput.val(post.text);
			textInput.on("change",function(){
				post.text = textInput.val();
			});
			alertRoot.find(".submitPost").on("click",function(){
				sendStanza(post);
				jAlert.closeAlert();
			});
			$("#"+alertId).html(alertRoot);
		};
		addPostButton.on("click",function(){
			var now = new Date().getTime();
			var postId = now.toString() + "_" + author + "_" + busId;
			var postStanza = {
				type:"forumPost",
				author:author,
				timestamp:0,
				identity:postId,
				slide:slideId,
				timestamp:now,
				inResponseTo:"",
				text:""	
			};
			renderEditPost(postStanza);
		});
		var renderPost = function(post){
			console.log("rendering:",post,history.forumPosts);
			var postRoot = postTemplate.clone();
			var children = _.filter(history.forumPosts,function(p,pKey){
				return p.inResponseTo == post.identity;
			});
			postRoot.find(".postMessage").text(post.text);
			var author = renderAuthor(post.author);
			postRoot.find(".postAuthor").text(author.name);
			postRoot.find(".postAvatar").attr("src",author.avatarUrl);
			postRoot.find(".postTimestamp").text(renderTimestamp(post.timestamp));
			var editButton = postRoot.find(".editButton");
			if (post.author == author){
				editButton.on("click",function(){
					renderEditPost(post);
				});
			} else {
				editButton.remove();
			}
			postRoot.find(".respondButton").on("click",function(){
				var now = new Date().getTime();
				var postId = now.toString() + "_" + author + "_" + busId;
				var postStanza = {
					type:"forumPost",
					author:author,
					timestamp:0,
					identity:postId,
					slide:slideId,
					timestamp:now,
					inResponseTo:post.identity,
					text:""	
				};
				renderEditPost(postStanza);
			});
			postRoot.find(".responses").html(_.map(children,function(p){
				return renderPost(p);
			}));
			return postRoot;
		};
		var reRenderPosts = function(){
			postsContainer.html(_.map(_.filter(history.forumPosts,function(p){
				return p.inResponseTo == "";
			}),renderPost));
		};
		return {
			activate:function(container){
				container.append(rootElem);
				bus.subscribe("receiveMeTLStanza",busId,function(stanza){
					console.log("receivedStanza",stanza);
					if ("type" in stanza){
						switch (stanza.type){
							case "forumPost":
								history.forumPosts[stanza.identity] = stanza;
								reRenderPosts();
								break;
							default:
								break;
						}
					}
				});
				bus.subscribe("receiveHistory",busId,function(h){
					console.log("receivedHistory",h);
					if ("jid" in h && h.jid == slideId){
						history = h;
						reRenderPosts();
					}
				});
				joinRoom(slideId);
			},
			deactivate:function(){
				bus.unsubscribe("receiveHistory",busId);
				bus.unsubscribe("receiveMeTLStanza",busId);
				leaveRoom(slideId);
				rootElem.empty();
			}
		};	
	};
	var createQuizActivity = function(bus,author,slideId,authoring){
		var busId = "quizActivity_"+new Date().getTime()+"_"+slideId;
		var history = {
			quizzes:{},
			quizResponses:{}
		};
		var rootElem = templates["quiz"].clone();
		var addQuizButton = rootElem.find(".addQuizButton");
		var quizzesContainer = rootElem.find(".quizzesContainer");
		var quizTemplate = quizzesContainer.find(".quiz").clone();
		quizzesContainer.empty();
		if (authoring){
			addQuizButton.on("click",function(){
				var now = new Date().getTime();
				var quizId = now.toString() + "_" + author + "_" + busId;
				var quizStanza = {
					type:"quiz",
					author:author,
					timestamp:0,
					id:quizId,
					slide:slideId,
					created:now,
					question:"do you agree?",
					isDeleted:false,
					options:[
						{
							name:"YES",
							text:"yes",
							correct:false,
							color:["#00FF00",255]
						},
						{
							name:"NO",
							text:"no",
							correct:false,
							color:["#FF0000",255]
						}
					]
				}
				sendStanza(quizStanza);
			});
		} else {
			addQuizButton.remove();
		}
		var reRenderQuizzes = function(){
			var quizzes = _.map(history.quizzes,function(quiz){
				var quizRoot = quizTemplate.clone();
				quizRoot.find(".quizQuestion").text(quiz.question);
				var quizOptionsRoot = quizRoot.find(".quizOptions");
				var quizOptionTemplate = quizOptionsRoot.find(".quizOption").clone();
				quizOptionsRoot.empty();
				var quizOptions = _.map(quiz.options,function(qo){
					var quizOptionRoot = quizOptionTemplate.clone();
					quizOptionRoot.find(".quizOptionValue").text(qo.name);
					quizOptionRoot.find(".quizOptionLabel").text(qo.text);
					quizOptionRoot.find(".chooseQuizOptionButton").on("click",function(){
						var now = new Date().getTime();
						var quizResponseStanza = {
							type:"quizResponse",
							author:author,
							timestamp:0,
							id:quiz.id,
							answer:qo.name,
							answerer:author,
							slide:slideId
						};
						sendStanza(quizResponseStanza);
					});
					return quizOptionRoot;
				});
				quizOptionsRoot.html(quizOptions);
				return quizRoot;
			});
			quizzesContainer.html(quizzes);
		};
		reRenderQuizzes();
		return {
			activate:function(root){
				bus.subscribe("receiveMeTLStanza",busId,function(stanza){
					console.log("receivedStanza",stanza);
					if ("type" in stanza){
						switch (stanza.type){
							case "quiz":
								history.quizzes[stanza.id] = stanza;
								reRenderQuizzes();
								break;
							case "quizResponse":
								history.quizResponses[stanza.id] = stanza;
								reRenderQuizzes();
								break;
							default:
								break;
						}
					}
				});
				bus.subscribe("receiveHistory",busId,function(h){
					console.log("receivedHistory",h);
					if ("jid" in h && h.jid == slideId){
						history = h;
						reRenderQuizzes();
					}
				});
				root.html(rootElem);
				joinRoom(slideId);
			},
			deactivate:function(){
				bus.unsubscribe("receiveHistory",busId);
				bus.unsubscribe("receiveMeTLStanza",busId);
				leaveRoom(slideId);
				rootElem.empty();
			}	
		};
	};
	var activateActivity = function(activity,container){
		if ("deactivate" in currentActivity){
			currentActivity.deactivate();
		}
		currentActivity = activity;
		activity.activate(container);
		return activity;
	}

	return {
		getBus:function(){ return bus; },
		getConversation:function(){ return conversation; },
		getSlide:function(){ return slide; },
		getAtivity:function(){ return currentActivity; },
		override:{
			audit:function(slideId){
				return activateActivity(createAuditCanvasActivity(bus,slideId),containerRoot);
			},
			canvas:function(slideId){
				return activateActivity(createMeTLCanvasActivity(bus,author,target,slideId),containerRoot);
			},
			quiz:function(slideId){
				return activateActivity(createQuizActivity(bus,author,slideId,false),contianerRoot);
			},	
			quizAuthoring:function(slideId){
				return activateActivity(createQuizActivity(bus,author,slideId,true),containerRoot);
			}	
		}
	};
})();
