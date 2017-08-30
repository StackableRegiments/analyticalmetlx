var createInteractiveCanvas = function(boardDiv){
	var history = {};
	// link in the renderer, and attach handlers as appropriate
	var rendererObj = createCanvasRenderer(boardDiv);
	var statistic = function(category,time,success,exception){ };
	rendererObj.onStatistic(function(c,t,s,e){return statistic(c,t,s,e);});
	var errorFunc = function(exception,location,parameters){ };
	rendererObj.onException(function(e,l,p){return errorFunc(e,l,p);});
	var renderStarting = function(ctx,elem,history){ };
	rendererObj.onRenderStarting(function(c,e,h){return renderStarting(c,e,h);});
	var renderComplete = function(ctx,elem,history){ };
	rendererObj.onRenderComplete(function(c,e,h){
		interactableCanvasRender(c);
		return renderComplete(c,e,h);
	});
	var viewboxChanged = function(vb,ctx,elem){ };
	rendererObj.onViewboxChanged(function(v,c,e){return viewboxChanged(v,c,e);});
	var scaleChanged = function(s,ctx,elem){ };
	rendererObj.onScaleChanged(function(s,c,e){return scaleChanged(s,c,e);});
	var dimensionsChanged = function(dims,ctx,elem){ };
	rendererObj.onDimensionsChanged(function(d,c,e){return dimensionsChanged(d,c,e);});
	var canvasStanzaAdded = function(stanza,after){
		// do things locally to the new stanza first
		after(stanza);
	};
	var stanzaAdded = function(stanza){};
	var canvasHistoryChanged = function(hist,after){
		history = hist;
		clearCanvasInteractables("video");
		_.forEach(history.videos,function(video){
			pushCanvasInteractableFunc("video",videoControlInteractable(video));
		});
		updateSelectionWhenBoardChanges();
		if (after !== undefined){
			after(hist);
		}
	};

	var setImageSourceCalcFunc = function(image){return image.source;};
	rendererObj.setImageSourceCalculationFunction(function(image){return setImageSourceCalcFunc(image)});
	var setVideoSourceCalcFunc = function(video){return video.source};
	rendererObj.setVideoSourceCalculationFunction(function(video){return setVideoSourceCalcFunc(video)});
	var historyChanged = function(history){ };
	rendererObj.onHistoryChanged(function(h){return canvasHistoryChanged(h,historyChanged);});
	rendererObj.onHistoryUpdated(function(h){return canvasHistoryChanged(h);});
	rendererObj.onStanzaAdded(function(s){
		return canvasStanzaAdded(s,stanzaAdded);
	});

	var preRenderItem = function(item,ctx){
		return true;
	};
	rendererObj.onPreRenderItem(function(i,c){return preRenderItem(i,c);});
	var postRenderItem = function(item){
	};
	rendererObj.onPostRenderItem(function(i,c){return postRenderItem(i,c);});
	/*
		RegisterPositionHandlers takes a set of contexts (possibly a single jquery), and handlers for down/move/up, normalizing them for touch.  Optionally, the mouse is raised when it leaves the boundaries of the context.  This is particularly to handle selection, which has 2 cooperating event sources which constantly give way to each other.
		* */

	var detectPointerEvents = function(){
			try {
					return (("pointerEnabled" in Navigator && Navigator.pointerEnabled == true) || PointerEvent != undefined);
			} catch(e) {
					return false;
			}
	}
	var unregisterPositionHandlers = function(){
			$.each("pointerdown pointermove pointerup pointerout pointerleave pointercancel mouseup mousemove mousedown touchstart touchmove touchend touchcancelled mouseout touchleave gesturechange gesturestart".split(" "),function(i,evt){
					boardDiv.unbind(evt);
			});
			WorkQueue.gracefullyResume();
	}
	var takeControlOfViewbox = function(control){
    if(control){
			MeTLBus.unsubscribe("onBoardContentChanged","autoZooming");
		}
	};
	var registerPositionHandlers = function(down,move,up){
			var isDown = false;

			var touchTolerance = 10;
			var noInteractableConsumed = function(worldPos,event){
					var worldRay = [
							worldPos.x - touchTolerance,
							worldPos.y - touchTolerance,
							worldPos.x + touchTolerance,
							worldPos.y + touchTolerance
					];
					var unconsumed = true;
					_.each(canvasInteractables,function(category,label){
							_.each(category,function(interactable){
									if(interactable != undefined && event in interactable){
											if(interactable.activated || intersectRect(worldRay,interactable.getBounds())){
													unconsumed = unconsumed && interactable[event](worldPos);
											}
									}
							});
					});
					return unconsumed;
			}
			var modifiers = function(e,isErasing){
					return {
							shift:e.shiftKey,
							ctrl:e.ctrlKey,
							alt:e.altKey,
							eraser:isErasing
					}
			}
			var context = boardDiv;//Might have to rewrap single jquerys
			var offset = function(){
					return context.offset();
			}
			context.css({"touch-action":"none"});
			var isGesture = false;
			var trackedTouches = {};
			var checkIsGesture = function(pointerEvent){
					if (pointerEvent !== undefined && pointerEvent.originalEvent.pointerType == "touch"){
							return (_.size(trackedTouches) > 1);
					} else {
							return false;
					}
			}
			var updatePoint = function(pointerEvent){
					var pointId = pointerEvent.originalEvent.pointerId;
					var isEraser = pointerEvent.originalEvent.pointerType == "pen" && pointerEvent.originalEvent.button == 5;
					var o = offset();
					var x = pointerEvent.pageX - o.left;
					var y = pointerEvent.pageY - o.top;
					var z = pointerEvent.originalEvent.pressure || 0.5;
					var worldPos = rendererObj.screenToWorld(x,y);
					var newPoint = {
							"x":worldPos.x,
							"y":worldPos.y,
							"screenX":x,
							"screenY":y,
							"z":z
					};
					var pointItem = trackedTouches[pointId] || {
							"pointerId":pointId,
							"pointerType":pointerEvent.originalEvent.pointerType,
							"eraser":isEraser,
							"points":[]
					};
					pointItem.points.push(newPoint);
					pointItem.eraser = pointItem.eraser || isEraser;
					if (pointerEvent.originalEvent.pointerType == "touch"){
							trackedTouches[pointId] = pointItem;
					}
					if (checkIsGesture(pointerEvent)){
							if (isGesture == false){
								_.each(trackedTouches,function(series){
										series.points = [_.last(series.points)];
								});
							}
							isGesture = true;
					}
					var returnedObj = {
							"pointerType":pointerEvent.pointerType,
							"eraser":pointItem.eraser,
							"x":x,
							"y":y,
							"z":z,
							"worldPos":worldPos
					};
					return returnedObj;
			};
			var releasePoint = function(pointerEvent){
					var pointId = pointerEvent.originalEvent.pointerId;
					var isEraser = pointerEvent.originalEvent.pointerType == "pen" && pointerEvent.originalEvent.button == 5;
					var o = offset();
					var x = pointerEvent.pageX - o.left;
					var y = pointerEvent.pageY - o.top;
					var z = pointerEvent.originalEvent.pressure || 0.5;
					var worldPos = rendererObj.screenToWorld(x,y);
					if (pointerEvent.originalEvent.pointerType == "touch"){
						delete trackedTouches[pointId];
					}
					if (isGesture && _.size(trackedTouches) == 0){
							isGesture = false;
							isDown = false;
					}
					return {
							"pointerType":pointerEvent.pointerType,
							"eraser":isEraser,
							"x":x,
							"y":y,
							"z":z,
							"worldPos":worldPos
					};
			};
			if (detectPointerEvents()){
					var performGesture = function(){
							takeControlOfViewbox(true);

							var calculationPoints = _.map(_.filter(trackedTouches,function(item){return _.size(item.points) > 1;}),function(item){
									var first = _.first(item.points);
									var last = _.last(item.points);
									return [first,last];
							});
							if (_.size(calculationPoints) > 1){
								trackedTouches = {};
								var xDelta = _.meanBy(calculationPoints,function(i){return i[0].x - i[1].x;});
								var yDelta = _.meanBy(calculationPoints,function(i){return i[0].y - i[1].y;});

								Pan.translate(rendererObj.scaleWorldToScreen(xDelta),rendererObj.scaleWorldToScreen(yDelta));

								var prevSouthMost = _.min(_.map(calculationPoints,function(touch){return touch[0].y;}));
								var prevNorthMost = _.max(_.map(calculationPoints,function(touch){return touch[0].y;}));
								var prevEastMost =  _.min(_.map(calculationPoints,function(touch){return touch[0].x;}));
								var prevWestMost =  _.max(_.map(calculationPoints,function(touch){return touch[0].x;}));
								var prevYScale = prevNorthMost - prevSouthMost;
								var prevXScale = prevWestMost - prevEastMost;

								var southMost = _.min(_.map(calculationPoints,function(touch){return touch[1].y;}));
								var northMost = _.max(_.map(calculationPoints,function(touch){return touch[1].y;}));
								var eastMost =  _.min(_.map(calculationPoints,function(touch){return touch[1].x;}));
								var westMost =  _.max(_.map(calculationPoints,function(touch){return touch[1].x;}));
								var yScale = northMost - southMost;
								var xScale = westMost - eastMost;

								var previousScale = (prevXScale + prevYScale) / 2;
								var currentScale = (xScale + yScale) / 2;
								Zoom.scale(previousScale / currentScale);
							}
					};
					var actOnMissedEvents = function(action,e){
						if ("getCoalescedEvents" in e){
							var missedEvents = e.getCoalescedEvents();
							_.forEach(missedEvents,function(pointerEvent){
								console.log("catching up pointerEvent",pointerEvent);
								action(pointerEvent);
							});
						} else {
							action(e);
						}
					};
					context.bind("pointerdown",function(e){
						if ((e.originalEvent.pointerType == e.POINTER_TYPE_TOUCH || e.originalEvent.pointerType == "touch") && checkIsGesture(e)){
							isGesture = true;
						}
						var point = updatePoint(e);
						e.preventDefault();
						WorkQueue.pause();
						if (!checkIsGesture(e) && !isGesture){
							isDown = true;
							if(noInteractableConsumed(point.worldPos,"down")){
								down(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
							}
						}
					});
					context.bind("pointermove",function(e){
						actOnMissedEvents(function(e){
							var point = updatePoint(e);
							e.preventDefault();
							if ((e.originalEvent.pointerType == e.POINTER_TYPE_TOUCH || e.originalEvent.pointerType == "touch") && (checkIsGesture(e) || isGesture)){
								performGesture();
							} else {
								if(noInteractableConsumed(point.worldPos,"move")){
									if(isDown){
										move(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
									}
								}
							}
						},e);
					});
					context.bind("pointerup",function(e){
						var point = releasePoint(e);
						WorkQueue.gracefullyResume();
						e.preventDefault();
						if(noInteractableConsumed(point.worldPos,"up")){
								if(isDown && !isGesture){
										up(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
								}
						}
						isDown = false;
						finishInteractableStates();
					});
					var pointerOut = function(x,y,e){
							var vb = rendererObj.getViewbox();
							var point = releasePoint(e);
							trackedTouches = {};
							WorkQueue.gracefullyResume();
							var worldPos = rendererObj.screenToWorld(x,y);
							var worldX = worldPos.x;
							var worldY = worldPos.y;
							if(worldX < vb.x){
								takeControlOfViewbox(true);
								Extend.left();
							}
							else if(worldX >= (vb.x + vb.width)){
								takeControlOfViewbox(true);
								Extend.right();
							} else if (worldY < vb.y){
								takeControlOfViewbox(true);
								Extend.up();
							} else if (worldY >= (vb.y + vb.height)){
								takeControlOfViewbox(true);
								Extend.down();
							}
							else{
									if(noInteractableConsumed(worldPos,"up")){
											if(isDown && !isGesture){
													up(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
											}
									}
							}
							isDown = false;
							finishInteractableStates();
					}
					var pointerClose = function(e){
							var point = releasePoint(e);
							WorkQueue.gracefullyResume();
							e.preventDefault();
							if(isDown){
								pointerOut(e.offsetX,e.offsetY,e);
							}
							isDown = false;
							finishInteractableStates();
					};
					context.bind("pointerout",pointerClose);
					context.bind("pointerleave",pointerClose);
					context.bind("pointercancel",pointerClose);
			} else {
					context.bind("mousedown",function(e){
							WorkQueue.pause();
							var o = offset();
							isDown = true;
							var x = e.pageX - o.left;
							var y = e.pageY - o.top;
							var z = 0.5;
							var worldPos = rendererObj.screenToWorld(x,y);
							if(noInteractableConsumed(worldPos,"down")){
									down(x,y,z,worldPos,modifiers(e));
							}
							e.preventDefault();
					});
					context.bind("mousemove",function(e){
							var o = offset();
							e.preventDefault();
							var x = e.pageX - o.left;
							var y = e.pageY - o.top;
							var z = 0.5;
							var worldPos = rendererObj.screenToWorld(x,y);
							if(noInteractableConsumed(worldPos,"move")){
									if(isDown){
											move(x,y,z,worldPos,modifiers(e));
									}
							}
					});
					context.bind("mouseup",function(e){
							WorkQueue.gracefullyResume();
							e.preventDefault();
							var o = offset();
							var x = e.pageX - o.left;
							var y = e.pageY - o.top;
							var z = 0.5;
							var worldPos = rendererObj.screenToWorld(x,y);
							if(noInteractableConsumed(worldPos,"up")){
									if(isDown){
											up(x,y,z,worldPos,modifiers(e));
									}
							}
							isDown = false;
							finishInteractableStates();
					});
					var mouseOut = function(x,y,e){
							WorkQueue.gracefullyResume();
							var worldPos = rendererObj.screenToWorld(x,y);
							var worldX = worldPos.x;
							var worldY = worldPos.y;
							var z = 0.5;
							if(worldX < viewboxX){
									takeControlOfViewbox(true);
									Extend.left();
							}
							else if(worldX >= (viewboxX + viewboxWidth)){
									takeControlOfViewbox(true);
									Extend.right();
							}
							else{
									if(noInteractableConsumed(worldPos,"up")){
											if(isDown && !isGesture){
													up(x,y,z,worldPos,modifiers(e,false));
											}
									}
							}
							isDown = false;
					}
					context.bind("mouseout",function(e){
							WorkQueue.gracefullyResume();
							e.preventDefault();
							if(isDown){
									mouseOut(e.offsetX,e.offsetY,e);
							}
							isDown = false;
							finishInteractableStates();
					});
					var touches;
					var masterTouch;
					var prevPos;
					var touchesToWorld = function(touches){
							return touches.map(function(t){
									return rendererObj.screenToWorld(t.x,t.y);
							});
					}
					var averagePos = function(touches){
							return {
									x:average(_.map(touches,"x")),
									y:average(_.map(touches,"y"))
							};
					}
					var offsetTouches = function(ts){
							var touches = [];
							var o = offset();
							$.each(ts,function(i,touch){
									touches.push({
											x: touch.pageX - o.left,
											y: touch.pageY - o.top,
											identifier:touch.identifier
									});
							});
							return touches;
					}
					context.bind("touchstart",function(e){
							WorkQueue.pause();
							e.preventDefault();
							var touches = offsetTouches(e.originalEvent.touches);
							if(touches.length == 1){
									var t = touches[0];
									var worldPos = rendererObj.screenToWorld(t.x,t.y);
									isDown = true;
									var z = 0.5;
									if(noInteractableConsumed(worldPos,"down")){
											down(t.x,t.y,z,worldPos,modifiers(e));
									}
							}
							else{
									var avg = averagePos(touches);
									prevPos = avg;
							}
					});
					var distance = function(p1,p2){
							return Math.sqrt(Math.pow(p2.pageX - p1.pageX,2) + Math.pow(p2.pageY - p1.pageY,2));
					}
					context.bind("touchmove",function(e){
							e.preventDefault();
							var touches = offsetTouches(e.originalEvent.touches);
							switch(touches.length){
							case 0 : break;
							case 1:
									var t = touches[0];
									var worldPos = rendererObj.screenToWorld(t.x,t.y);
									if(noInteractableConsumed(worldPos,"move")){
											if(isDown){
													var z = 0.5;
													move(t.x,t.y,z,worldPos,modifiers(e));
											}
									}
									break;
							default:
									var pos = averagePos(touches);
									var xDelta = pos.x - prevPos.x;
									var yDelta =  pos.y - prevPos.y;
									prevPos = pos;
									takeControlOfViewbox(true);
									Pan.translate(-1 * xDelta,-1 * yDelta);
									break;
							}
					});
					context.bind("touchend",function(e){
							WorkQueue.gracefullyResume();
							e.preventDefault();
							var o = offset();
							var t = e.originalEvent.changedTouches[0];
							var x = t.pageX - o.left;
							var y = t.pageY - o.top;
							var worldPos = rendererObj.screenToWorld(x,y);
							if(noInteractableConsumed(worldPos,"up")){
									if(isDown){
											var z = 0.5;
											if(x < 0 || y < 0 || x > boardWidth || y > boardHeight){
													mouseOut(x,y);
											}
											else{
													up(x,y,z,worldPos,modifiers(e));
											}
									}
							}
							isDown = false;
							finishInteractableStates();
					});
					var previousScale = 1.0;
					var changeFactor = 0.1;
					context.bind("gesturechange",function(e){
							WorkQueue.pause();
							e.preventDefault();
							isDown = false;
							var scale = e.originalEvent.scale;
							//Zoom.scale(previousScale / scale,true);
							// I don't think it's right that the touch gestures of an iPad can zoom farther than the default controls.
							takeControlOfViewbox(true);
							Zoom.scale(previousScale / scale);
							previousScale = scale;
					});
					context.bind("gestureend",function(){
							WorkQueue.gracefullyResume();
							previousScale = 1.0;
					});
			}
			return function(forceDown){
					isDown = forceDown;
			}
	}

	var interactableAttrs = {
		opacity:0
	};
	var selected = {};
	var	aspectLocked = true;
	var dragging = false;
	var resizing = false;

	var selectionOffset = 0;
	var selectionWorldOrigin = 0;
	var resizeHandleSize = 20;
	var handlesAtZoom = function(){
		var zoom = rendererObj.getScale();
		return resizeHandleSize / zoom;
	};
	var clipToInteractableSpace = function(worldX,worldY){
		var s = handlesAtZoom();
		var screen = rendererObj.worldToScreen(worldX,worldY);
		var minX = 0;
		var minY = 0;
		var dims = rendererObj.getDimensions();
		var maxX = dims.width;
		var maxY = dims.height;
		var newX = Math.max(minX + s,Math.min(screen.x,maxX - s));
		var newY = Math.max(minY + s,Math.min(screen.y,maxY - s));
		var worldPos = rendererObj.screenToWorld(newX,newY);
		return {x:worldPos.x,y:worldPos.y};	
		/*
		var ceiling = rendererObj.scaleScreenToWorld(DeviceConfiguration.headerHeight)+s;
		var floor = rendererObj.scaleScreenToWorld(DeviceConfiguration.footerHeight);
		var clipped = Math.min(viewboxY+viewboxHeight-(floor - s/2),Math.max(y,viewboxY+ceiling));
		return clipped;
		*/
	}
	var manualMove = (function(){
		var activated = false;
		var bounds = undefined;
		var deactivateFunc = function(){
			activated = false;
			dragging = false;
		};
		return {
			getBounds:function(){return bounds;},
			getActivated:function(){return activated},
			originalHeight:1,
			originalWidth:1,
			rehome:function(root){
				if(!activated){
					var s = handlesAtZoom();
					var inPos = clipToInteractableSpace(root.x,root.y);
					var x = inPos.x;
					var y = inPos.y;
					bounds = [ x - s, y - s, x, y];
				}
			},
			down:function(worldPos){
				activated = true;
				dragging = true;
				resizing = false;
				selectionOffset = worldPos;
				selectionWorldOrigin = worldPos;
				rendererObj.render();
			},
			move:function(worldPos){
				if(activated){
					var s = handlesAtZoom();
					bounds = [
						worldPos.x - s,
						worldPos.y,
						worldPos.x + s,
						worldPos.y
					];
					selectionOffset = worldPos;
					rendererObj.render();
				}
				return false;
			},
			deactivate:deactivateFunc,
			up:function(worldPos){
				deactivateFunc();
				var moved = batchTransform();
				var xDelta = worldPos.x - selectionWorldOrigin.x;
				var yDelta = worldPos.y - selectionWorldOrigin.y;
				moved.xTranslate = xDelta;
				moved.yTranslate = yDelta;
				moved.inkIds = _.keys(selected.inks);
				moved.textIds = _.keys(selected.texts);
				moved.imageIds = _.keys(selected.images);
				moved.videoIds = _.keys(selected.videos);
				moved.multiWordTextIds = _.keys(selected.multiWordTexts);
				/*
				_.each(moved.multiWordTextIds,function(id){
					echoesToDisregard[id] = true;
				});
				*/
				dragging = false;
				/*
				text.mapSelected(function(box){
					box.doc.position.x += xDelta;
					box.doc.position.y += yDelta;
					box.doc.invalidateBounds();
				});
				*/
				stanzaAvailable(moved);
				rendererObj.render();
				return false;
			},
			render:function(canvasContext){
				if(bounds){
					var tl = rendererObj.worldToScreen(bounds[0],bounds[1]);
					var br = rendererObj.worldToScreen(bounds[2],bounds[3]);
					var size = br.x - tl.x;
					var x = tl.x;
					var y = tl.y;
					canvasContext.globalAlpha = interactableAttrs.opacity;
					canvasContext.setLineDash([]);
					canvasContext.strokeStyle = "black";
					canvasContext.fillStyle = "white";
					canvasContext.strokeWidth = 2;
					canvasContext.fillRect(x,y,size,size);
					canvasContext.strokeRect(x,y,size,size);
					canvasContext.font = sprintf("%spx FontAwesome",size);
					canvasContext.fillStyle = "black";
					canvasContext.fillText("\uF047",x,y+size - 4);
				}
			}
		};
	})();
	var videoControlInteractable = function(video){
    var bounds = undefined;
    var deactivateFunc = function(){
        // I don't think this is necessary for this control
    };
    return {
        activated:false,
        rehome : function(root){
            // I'm not doing anything with this stuff
        },
        down: function(worldPos){
            return false;
        },
        move: function(worldPos){
            return false;
        },
        up: function(worldPos){
            deactivateFunc();

            var bw = handlesAtZoom();

            var position = (worldPos.x - video.x); // this is a 0 - video.width value to describe where the click landed.
            if (position < bw){
                if (video.getState().paused){
                    video.play();
                } else {
                    video.pause();
                }
            } else if (position > (video.width - bw)){
                video.muted(!video.muted());
            } else {
                var mediaState = video.getState();
                var seekPos = ((position - bw) / (video.width - (2 * bw))) * mediaState.duration;
                video.seek(seekPos);
            }
            return false;
        },
        getBounds:function(){return bounds;},
        deactivate:deactivateFunc,
        render:function(canvasContext){
            if (video.identity in history.videos){
                var h = handlesAtZoom();
                var x = video.bounds[0];
                var y = video.bounds[1];
                bounds = [
                    x,
                    video.bounds[3],
                    video.bounds[2],
                    video.bounds[3] + h
                ];

                var tl = rendererObj.worldToScreen(bounds[0],bounds[1]);
                var br = rendererObj.worldToScreen(bounds[2],bounds[3]);
                var width = br.x - tl.x;
                var height = br.y - tl.y;

                var mediaState = video.getState();
                canvasContext.globalAlpha = 1.0;
                canvasContext.setLineDash([]);
                canvasContext.strokeStyle = "black";
                canvasContext.font = sprintf("%spx FontAwesome",height);

                var buttonWidth = height;

                // play/pause button

                canvasContext.fillStyle = "white";
                canvasContext.fillRect(tl.x,tl.y,buttonWidth,height);
                canvasContext.fillStyle = "black";
                if (mediaState.paused){
                    canvasContext.fillText("\uF04B",tl.x,br.y);
                } else {
                    canvasContext.fillText("\uF04C",tl.x,br.y);
                }

                // progress meter

                var progressX = tl.x + buttonWidth;
                var progressWidth = width - buttonWidth;
                canvasContext.fillStyle = "black";
                canvasContext.fillRect(progressX,tl.y,progressWidth,height);
                canvasContext.fillStyle = "blue";
                var progressWidth = (mediaState.currentTime / mediaState.duration) * progressWidth;
                canvasContext.fillRect(progressX,tl.y,progressWidth,height);

                // mute button

                var muteX = tl.x + (width - buttonWidth);
                canvasContext.fillStyle = "white";
                canvasContext.fillRect(muteX,tl.y,buttonWidth,height);
                canvasContext.fillStyle = "black";
                if (mediaState.muted){
                    canvasContext.fillText("\uF0F3",muteX,br.y);
                } else {
                    canvasContext.fillText("\uF1F6",muteX,br.y);
                }
            }
        }
    };
	};

	var resizeAspectLocked = (function(){
		var bounds = undefined;
		var activated = false;
		var rehomeFunc = function(root){
			if(!activated){
				var s = handlesAtZoom();
				var inPos = clipToInteractableSpace(root.x2,root.y);
				var x = inPos.x;
				var y = inPos.y;
				bounds = [
					x,
					y,
					x + s,
					y + s
				];
			}
		};
		var deactivateFunc = function(){
			aspectLocked = false;
			activated = false;
			resizing = false;
		};
		return {
			getBounds:function(){return bounds;},
			getActivated:function(){return activated;}, 
			originalHeight:1,
			originalWidth:1,
			rehome : rehomeFunc,
			down:function(worldPos){
				aspectLocked = true;
				dragging = false;
				resizing = true;
				activated = true;
				selectionWorldOrigin = worldPos;
				var root = totalSelectedBounds();
				selectionoffset = {x:root.x2,y:root.y2};
				rehomeFunc(root);
				rendererObj.render();
				return false;
			},
			move:function(worldPos){
				if(activated){
					var s = handlesAtZoom();
					bounds = [
						worldPos.x - s,
						bounds[1],
						worldPos.x + s,
						bounds[3]
					];
					var totalBounds = totalSelectedBounds();
					var originalWidth = totalBounds.x2 - totalBounds.x;
					var requestedWidth = worldPos.x - totalBounds.x;
					var xScale = requestedWidth / originalWidth;
					selectionOffset = {
						x:worldPos.x,
						y:totalBounds.y + totalBounds.height * xScale
					};
					rendererObj.render();
				}
				return false;
			},
			deactivate:deactivateFunc,
			up:function(worldPos){
				deactivateFunc();
				var resized = batchTransform();
				var totalBounds = totalSelectedBounds();
				var originalWidth = totalBounds.x2 - totalBounds.x;
				var requestedWidth = worldPos.x - totalBounds.x;
				resized.xScale = requestedWidth / originalWidth;
				resized.yScale = resized.xScale;
				resized.xOrigin = totalBounds.x;
				resized.yOrigin = totalBounds.y;
				resized.inkIds = _.keys(selected.inks);
				resized.textIds = _.keys(selected.texts);
				resized.imageIds = _.keys(selected.images);
				resized.videoIds = _.keys(selected.videos);
				resized.multiWordTextIds = _.keys(selected.multiWordTexts);
				/*
				_.each(selected.multiWordTexts,function(text,id){
				//		Modes.text.echoesToDisregard[id] = true;
						var range = text.doc.documentRange();
						text.doc.select(range.start,range.end);
						Modes.text.scaleEditor(text.doc,resized.xScale);
						var startingWidth = text.doc.width();
						text.doc.width(startingWidth * resized.xScale);
						text.doc.select(0);
						text.doc.updateCanvas();
						rendererObj.render();
				});
				*/
				stanzaAvailable(resized);
				rendererObj.render();
				return false;
			},
			render:function(canvasContext){
				if(bounds){
					var tl = rendererObj.worldToScreen(bounds[0],bounds[1]);
					var br = rendererObj.worldToScreen(bounds[2],bounds[3]);
					var size = br.x - tl.x;
					var inset = size / 10;
					var xOffset = -1 * size;
					var yOffset = -1 * size;
					var rot = 90;
					canvasContext.globalAlpha = interactableAttrs.opacity;
					canvasContext.setLineDash([]);
					canvasContext.strokeStyle = "black";
					canvasContext.fillStyle = "white";
					canvasContext.strokeWidth = 2;
					canvasContext.translate(tl.x,tl.y);
					canvasContext.rotate(rot * Math.PI / 180);
					/*Now the x and y are reversed*/
					canvasContext.fillRect(0,xOffset,size,size);
					canvasContext.strokeRect(0,xOffset,size,size);
					canvasContext.font = sprintf("%spx FontAwesome",size);
					canvasContext.fillStyle = "black";
					canvasContext.fillText("\uF0B2",inset,-1 * inset);
				}
			}
		};
	})();
	var hexToRgb = function(hex){
		return hex;
		/*
    if(typeof hex == "object" && hex.alpha) {
			return Colors.getColorForColorParts(hex.alpha,hex.red,hex.green,hex.blue);
    } else if (typeof hex == "object" && hex[0] && hex[1] && typeof hex[0] == "string" && typeof hex[1] == "number"){
			return hex;
    } else if(typeof hex == "string") {
			return Colors.getColorObjForHex(hex);
    } else if(typeof hex == "array") {
			return hex;
    } else {
			return Colors.getDefaultColorObj();
    }
		*/
	}

	var richTextEditorToStanza = function(t){
		if(!t.bounds) t.doc.invalidateBounds();
		var bounds = t.bounds;
		var text = t.doc.save();
		var w = t.doc.width();
		var stanza = {
			timestamp:-1,
			tag:"_",
			identity:t.identity,
			type:t.type,
			x:bounds[0],
			y:bounds[1],
			requestedWidth:w,
			width:w,
			height:bounds[3]-bounds[1],
			words:text.map(function partToStanza(p){
				var defaults = carota.runs.defaultFormatting;
				var color = hexToRgb(p.color || defaults.color);
				return {
					text:p.text,
					color:color,
					size:parseFloat(p.size) || parseFloat(defaults.size),
					font:p.font || defaults.font,
					justify:p.align || defaults.align,
					bold:p.bold === true,
					underline:p.underline === true,
					italic:p.italic === true
				};
			})
		};
		return stanza;
	}

	var resizeFree = (function(){
		var bounds = undefined;
		var activated = false;
		var deactivateFunc = function(){
			activated = false;
			resizing = false;
		};
		var rehomeFunc = function(root){
			if(!activated){
				var s = handlesAtZoom();
				var inPos = clipToInteractableSpace(root.x2,root.y2);
				var x = inPos.x;
				var y = inPos.y;
				var width = root.x2 - root.x;
				var center = root.x + (s / 2);
				bounds = [
					x,
					y - s,
					x + s,
					y
				];
			}
		};
		return {
			getActivated:function(){ return activated;},
			getBounds:function(){return bounds;},
			rehome : rehomeFunc,
			down:function(worldPos){
				activated = true;
				dragging = false;
				resizing = true;
				aspectLocked = false;
				selectionWorldOrigin = worldPos;
				var root = totalSelectedBounds();
				selectionOffset = {x:root.x2,y:root.y2};
				rendererObj.render();
				return false;
			},
			move:function(worldPos){
				if(activated){
					var s = handlesAtZoom();
					bounds = [
						worldPos.x - s,
						worldPos.y - s,
						worldPos.x + s,
						worldPos.y + s
					];
					selectionOffset = {x:worldPos.x,y:worldPos.y};
					rendererObj.render();
				}
				return false;
			},
			deactivate:deactivateFunc,
			up:function(worldPos){
				deactivateFunc();
				var resized = batchTransform();
				var totalBounds = totalSelectedBounds();
				var originalWidth = totalBounds.x2 - totalBounds.x;
				var originalHeight = totalBounds.y2 - totalBounds.y;
				var requestedWidth = worldPos.x - totalBounds.x;
				var requestedHeight = worldPos.y - totalBounds.y;
				resized.xScale = requestedWidth / originalWidth;
				resized.yScale = requestedHeight / originalHeight;
				resized.xOrigin = totalBounds.x;
				resized.yOrigin = totalBounds.y;
				resized.inkIds = _.keys(selected.inks);
				resized.textIds = _.keys(selected.texts);
				resized.imageIds = _.keys(selected.images);
				resized.videoIds = _.keys(selected.videos);
				var s = rendererObj.getScale();
				_.each(selected.multiWordTexts,function(word){
					if(word.doc.save().length > 0){
						word.doc.width(Math.max(
							word.doc.width() * resized.xScale,
							minimumTextWidth / s
						));
						word.doc.updateCanvas();
						stanzaAvailable(richTextEditorToStanza(word));
					}
				});
				/*
				registerTracker(resized.identity,function(){
						MeTLBus.call("onSelectionChanged");
						rendererObj.render();
				});
				*/
				rendererObj.render();
				stanzaAvailable(resized);
				return false;
			},
			render:function(canvasContext){
				if(bounds){
					var tl = rendererObj.worldToScreen(bounds[0],bounds[1]);
					var br = rendererObj.worldToScreen(bounds[2],bounds[3]);
					var size = br.x - tl.x;
					var inset = size / 10;
					var xOffset = -1 * size;
					var yOffset = -1 * size;
					var rot = 90;
					canvasContext.globalAlpha = interactableAttrs.opacity;
					canvasContext.setLineDash([]);
					canvasContext.strokeStyle = "black";
					canvasContext.fillStyle = "white";
					canvasContext.strokeWidth = 2;
					canvasContext.translate(tl.x,tl.y);
					canvasContext.rotate(rot * Math.PI / 180);
					/*Now the x and y are reversed*/
					canvasContext.fillRect(0,xOffset,size,size);
					canvasContext.strokeRect(0,xOffset,size,size);
					canvasContext.font = sprintf("%spx FontAwesome",size);
					canvasContext.fillStyle = "black";
					canvasContext.fillText("\uF065",inset,-1 * inset);
				}
			}
		};
	})();
	var canvasInteractables = {};
	var pushCanvasInteractableFunc = function(category,interaction){
		if(!(category in canvasInteractables)){
				canvasInteractables[category] = [];
		}
		canvasInteractables[category].push(interaction);
	};
	pushCanvasInteractableFunc("manualMove",manualMove);
	pushCanvasInteractableFunc("resizeFree",resizeFree);
	pushCanvasInteractableFunc("resizeAspectLocked",resizeAspectLocked);

	/*
	MeTLBus.subscribe("textBoundsChanged","selectionHandles",function(textId,bounds){
		if(textId in selected.multiWordTexts){
			var totalBounds = totalSelectedBounds();
			manualMove.rehome(totalBounds);
			resizeFree.rehome(totalBounds);
			resizeAspectLocked.rehome(totalBounds);
			rendererObj.render();
		}
	});
	*/

	var getCanvasInteractables = function(){
		return _.mapValues(canvasInteractables,function(interactables){
			return _.map(interactables,function(v){
				var _v = _.clone(v);
				_v.bounds = _v.getBounds();
				return _v;
			});
		});
	};
	var clearCanvasInteractables = function(category){
		canvasInteractables[category] = [];
	};
	var finishInteractableStates = function(){
		_.forEach(_.keys(canvasInteractables),function(k){
			_.forEach(canvasInteractables[k],function(ci){
					if (ci != undefined && "deactivate" in ci){
							ci.deactivate();
					}
			});
		});
		resizing = false;
		dragging = false;
		aspectLocked = false;
	};
	var noneMode = {
		name:"none",
		activate:function(){
			currentMode.deactivate();
			currentMode = Modes.none;
		},
		deactivate:function(){
			unregisterPositionHandlers();
		}
	};													 
	var currentModes = noneMode;
	var minimumTextWidth = 240;

	var videoMode = (function(){
			var noop = function(){};
			var currentVideo = {};
			var insertOptions = undefined;
			var videoFileChoice = undefined;
			var insertOptionsClose = undefined;
			var resetVideoUpload = function(){
					insertOptions.hide();
					$("#imageWorking").hide();
					$("#imageFileChoice").show();
					var videoForm = videoFileChoice.wrap("<form>").closest("form").get(0);
					if (videoForm != undefined){
							videoForm.reset();
					}
					videoFileChoice.unwrap();
			};
			var clientSideProcessVideo = function(onComplete){
					if (currentVideo == undefined || currentVideo.fileUpload == undefined || onComplete == undefined){
							return;
					}
					$("#videoWorking").show();
					$("#videoFileChoice").hide();
					var reader = new FileReader();
					reader.onload = function(readerE){
							var vid = $("<video/>");
							var thisVid = vid[0];
							var originalSrc = readerE.target.result;
							var originalSize = originalSrc.length;
							thisVid.addEventListener("loadeddata",function(e){
									var width = thisVid.videoWidth;
									var height = thisVid.videoHeight;
									currentVideo.width = width;
									currentVideo.height = height;
									currentVideo.video = vid;
									currentVideo.videoSrc = originalSrc;
									onComplete();
							},false);
							vid.append($("<source />",{
									src:originalSrc,
									type:"video/mp4"
							}));
							vid.load();
					}
					reader.readAsDataURL(currentVideo.fileUpload);
			};
			var sendVideoToServer = function(){
					if (currentVideo.type == "imageDefinition"){
							WorkQueue.pause();
							var worldPos = {x:currentVideo.x,y:currentVideo.y};
							var screenPos= {x:currentVideo.screenX,y:currentVideo.screenY};
							var t = Date.now();
							var identity = sprintf("%s%s",UserSettings.getUsername(),t);
							var currentSlide = Conversations.getCurrentSlideJid();
							var url = sprintf("/uploadDataUri?jid=%s&filename=%s",currentSlide.toString(),encodeURI(identity));
							$.ajax({
									url: url,
									type: 'POST',
									success: function(e){
											var newIdentity = $(e).find("resourceUrl").text().trim();
											var videoStanza = {
													type:"video",
													author:UserSettings.getUsername(),
													timestamp:t,
													identity:newIdentity,
													slide:currentSlide.toString(),
													source:newIdentity,
													bounds:[currentVideo.x,currentVideo.y,currentVideo.x+currentVideo.width,currentVideo.y+currentVideo.height],
													width:currentVideo.width,
													height:currentVideo.height,
													target:"presentationSpace",
													privacy:Privacy.getCurrentPrivacy(),
													x:currentVideo.x,
													y:currentVideo.y,
													audiences:_.map(Conversations.getCurrentGroup(),"id").concat(ContentFilter.getAudiences()).map(audienceToStanza)
											};
											registerTracker(newIdentity,function(){
													var insertMargin = Modes.select.handlesAtZoom();
													var newX = videoStanza.x;
													var newY = videoStanza.y;
													var newW = Math.max(videoStanza.width,viewboxWidth);
													var newH = Math.max(videoStanza.height,viewboxHeight);
													Modes.select.activate();
													IncludeView.specific(
															newX - insertMargin,
															newY - insertMargin,
															newW + insertMargin * 2,
															newH + insertMargin * 2);
													selectMode.clearSelection();
													Modes.select.selected.videos[videoStanza.identity] = boardContent.videos[videoStanza.identity];
													MeTLBus.call("onSelectionChanged",[Modes.select.selected]);
											});
											stanzaAvailable(videoStanza);
											resetVideoUpload();
											WorkQueue.gracefullyResume();
									},
									error: function(e){
											console.log("Image upload ex",e);
											resetImageUpload();
											errorAlert("Upload failed.  This image cannot be processed, either because of image protocol issues or because it exceeds the maximum image size.");
											WorkQueue.gracefullyResume();
									},
									data:currentVideo.videoSrc,
									cache: false,
									contentType: false,
									processData: false
							});
					}
			};
			var hasInitialized = false;
			$(function(){
					if (!hasInitialized){
							hasInitialized = true;
							/*
							insertOptions = $("#videoInsertOptions").css({position:"absolute",left:0,top:0});
							insertOptionsClose = $("#videoInsertOptionsClose").click(Modes.select.activate);
							videoFileChoice = $("#videoFileChoice").attr("accept","video/mp4");
							videoFileChoice[0].addEventListener("change",function(e){
									var files = e.target.files || e.dataTransfer.files;
									var limit = files.length;
									var file = files[0];
									if (file.type.indexOf("video") == 0) {
											currentVideo.fileUpload = file;
									}
									clientSideProcessVideo(sendVideoToServer);
							},false);
							resetVideoUpload();
							*/
					}
			});
			MeTLBus.subscribe("beforeLeavingSlide","videos",function(){
					if ("videos" in boardContent){
							_.forEach(boardContent.videos,function(video){
									if (video != null && video != undefined && "destroy" in video){
											video.destroy();
									}
							});
					}
			});
			return {
					activate:function(){
							Modes.currentMode.deactivate();
							Modes.currentMode = Modes.video;
							setActiveMode("#insertTools","#videoMode");
							$(".activeTool").removeClass("activeTool").addClass("inactiveTool");
							$("#insertMode").addClass("activeTool").removeClass("inactiveTool");
							$(".toolbar.active").removeClass("active");
							$("#videoMode").addClass("active");
							$("#insertTools .insetColumn").hide();
							$("#videoTools").show();
							var x = 10;
							var y = 10;
							var worldPos = rendererObj.screenToWorld(x,y);
							currentVideo = {
									"type":"imageDefinition",
									"screenX":x,
									"screenY":y,
									"x":worldPos.x,
									"y":worldPos.y
							}
							MeTLBus.call("onLayoutUpdated");
							insertOptions.show();
					},
					deactivate:function(){
							resetVideoUpload();
					}
			};
	})();
	var imageMode = (function(){
			var noop = function(){};
			var currentImage = {};
			var insertOptions = undefined;
			var imageFileChoice = undefined;
			var insertOptionsClose = undefined;
			var resetImageUpload = function(){
					insertOptions.hide();
					$("#imageWorking").hide();
					$("#imageFileChoice").show();
					var imageForm = imageFileChoice.wrap("<form>").closest("form").get(0);
					if (imageForm != undefined){
							imageForm.reset();
					}
					imageFileChoice.unwrap();
			};
			var imageModes = (function(){
					var keepUnder = function(threshold,incW,incH,quality){
							var w = incW;
							var h = incH;
							var currentTotal = w * h;
							while (currentTotal > threshold){
									w = w * 0.8;
									h = h * 0.8;
									currentTotal = w * h;
							};
							return {w:w,h:h,q:quality};
					}
					var modes = {
							"native":{
									resizeFunc:function(w,h){ return {w:w,h:h,q:1.0};},
									selector:"#imageInsertNative"
							},
							"optimized":{
									resizeFunc:function(w,h){ return keepUnder(1 * megaPixels,w,h,0.4);},
									selector:"#imageInsertOptimized"
							},
							"highDef":{
									resizeFunc:function(w,h){ return keepUnder(3 * megaPixels,w,h,0.8);},
									selector:"#imageInsertHighDef"
							}
					}
					var currentMode = modes.optimized;

					var megaPixels = 1024 * 1024;
					var redrawModeButtons = function(){
							_.forEach(modes,function(resizeMode){
									var el = $(resizeMode.selector);
									if (currentMode.selector == resizeMode.selector){
											el.addClass("activeBrush");
									} else {
											el.removeClass("activeBrush");
									}
							});
					}
					$(function(){
							_.forEach(modes,function(resizeMode){
									var el = $(resizeMode.selector);
									el.on("click",function(){
											currentMode = resizeMode;
											redrawModeButtons();
									});
							});
							redrawModeButtons();
					});
					return {
							"reapplyVisualStyle":redrawModeButtons,
							"changeMode":function(newMode){
									if (newMode in modes){
											currentMode = modes[newMode];
											redrawModeButtons();
									}
							},
							"getResizeFunction":function(){
									return currentMode.resizeFunc;
							}
					}
			})();
			var clientSideProcessImage = function(onComplete,thisCurrentImage){
					var state = thisCurrentImage == undefined ? currentImage : thisCurrentImage;
					if (state == undefined || state.fileUpload == undefined || onComplete == undefined){
							console.log("returning because currentImage is empty",currentImage);
							return;
					}
					$("#imageWorking").show();
					$("#imageFileChoice").hide();
					var reader = new FileReader();
					reader.onload = function(readerE){
							var originalSrc = readerE.target.result;
							clientSideProcessImageSrc(originalSrc,state,onComplete,function(img){
									var originalSize = originalSrc.length;
									return originalSize < img;
							});
					}
					reader.readAsDataURL(state.fileUpload);
			};
			var clientSideProcessImageSrc = function(originalSrc,state,onComplete,ifBiggerPred){
					var thisCurrentImage = state != undefined ? state : currentImage;
					var renderCanvas = $("<canvas/>");
					var img = new Image();
					if (originalSrc.indexOf("data") == 0){
							// if it's a dataUrl, then don't set crossOrigin of anonymous
					} else {
							// set cross origin if it's not a dataUrl
							img.setAttribute("crossOrigin","Anonymous");
					}
					img.onerror = function(e){
							errorAlert("Error dropping image","The source server you're dragging the image from does not allow dragging the image directly.  You may need to download the image first and then upload it.");
					};
					img.onload = function(e){
							var width = img.width;
							var height = img.height;
							var dims = imageModes.getResizeFunction()(width,height);
							var w = dims.w;
							var h = dims.h;
							var quality = dims.q;
							/*
								renderCanvas.width = w;
								renderCanvas.height = h;
								renderCanvas.attr("width",w);
								renderCanvas.attr("height",h);
								renderCanvas.css({
								width:px(w),
								height:px(h)
								});
								renderCanvas[0].getContext("2d").drawImage(img,0,0,w,h);
								currentImage.resizedImage = renderCanvas[0].toDataURL("image/jpeg",quality);
							*/
							renderCanvas.width = width;
							renderCanvas.height = height;
							renderCanvas.attr("width",width);
							renderCanvas.attr("height",height);
							renderCanvas.css({
									width:px(width),
									height:px(height)
							});
							var ctx = renderCanvas[0].getContext("2d");
							ctx.rect(0,0,width,height);
							ctx.fillStyle = "white";
							ctx.fill();
							ctx.drawImage(img,0,0,width,height);
							var resizedCanvas = multiStageRescale(renderCanvas[0],w,h);
							thisCurrentImage.width = w;
							thisCurrentImage.height = h;
							thisCurrentImage.resizedImage = resizedCanvas.toDataURL("image/jpeg",quality);
							var newSize = thisCurrentImage.resizedImage.length;
							if (ifBiggerPred(newSize)){
									thisCurrentImage.resizedImage = originalSrc;
							}
							onComplete(thisCurrentImage);
					};
					img.src = originalSrc;
			};
			var sendImageToServer = function(imageDef){
					if (imageDef.type == "imageDefinition"){
							WorkQueue.pause();
							var worldPos = {x:imageDef.x,y:imageDef.y};
							var screenPos= {x:imageDef.screenX,y:imageDef.screenY};
							var t = Date.now();
							var identity = sprintf("%s%s%s",UserSettings.getUsername(),t,_.uniqueId());
							var currentSlide = Conversations.getCurrentSlideJid();
							var url = sprintf("/uploadDataUri?jid=%s&filename=%s",currentSlide.toString(),encodeURI(identity));
							$.ajax({
									url: url,
									type: 'POST',
									success: function(e){
											var newIdentity = $(e).find("resourceUrl").text().trim();
											var imageStanza = {
													type:"image",
													author:UserSettings.getUsername(),
													timestamp:t,
													tag:"{\"author\":\""+UserSettings.getUsername()+"\",\"privacy\":\""+Privacy.getCurrentPrivacy()+"\",\"id\":\""+newIdentity+"\",\"isBackground\":false,\"zIndex\":0,\"timestamp\":-1}",
													identity:newIdentity,
													slide:currentSlide.toString(),
													source:newIdentity,
													bounds:[imageDef.x,imageDef.y,imageDef.x+imageDef.width,imageDef.y+imageDef.height],
													width:imageDef.width,
													height:imageDef.height,
													x:imageDef.x,
													y:imageDef.y,
													target:"presentationSpace",
													privacy:Privacy.getCurrentPrivacy(),
													audiences:_.map(Conversations.getCurrentGroup(),"id").concat(ContentFilter.getAudiences()).map(audienceToStanza)
											};
											registerTracker(newIdentity,function(){
													var insertMargin = Modes.select.handlesAtZoom();
													var newX = imageStanza.x;
													var newY = imageStanza.y;
													var newW = Math.max(imageStanza.width,viewboxWidth);
													var newH = Math.max(imageStanza.height,viewboxHeight);
													Modes.select.activate();
													IncludeView.specific(
															newX - insertMargin,
															newY - insertMargin,
															newW + insertMargin * 2,
															newH + insertMargin * 2);
													selectMode.clearSelection();
													Modes.select.selected.images[imageStanza.identity] = boardContent.images[imageStanza.identity];
													MeTLBus.call("onSelectionChanged",[Modes.select.selected]);
											});
											stanzaAvailable(imageStanza);
											resetImageUpload();
											WorkQueue.gracefullyResume();
											zoomToFit();
									},
									error: function(e){
											console.log(e);
											resetImageUpload();
											errorAlert("Upload failed.  This image cannot be processed, either because of image protocol issues or because it exceeds the maximum image size.");
											WorkQueue.gracefullyResume();
									},
									data:imageDef.resizedImage,
									cache: false,
									contentType: false,
									processData: false
							});
					}
			};
			var hasInitialized = false;
			$(function(){
					if (!hasInitialized){
							hasInitialized = true;
							/*
							insertOptions = $("#imageInsertOptions").css({position:"absolute",left:0,top:0});
							insertOptionsClose = $("#imageInsertOptionsClose").click(Modes.select.activate);
							imageFileChoice = $("#imageFileChoice").attr("accept","image/*");
							imageFileChoice[0].addEventListener("change",function(e){
									try {
											var files = e.target.files || e.dataTransfer.files;
											var file = files[0];
											if (file.type.indexOf("image") == 0) {
													currentImage.fileUpload = file;
											}
											clientSideProcessImage(sendImageToServer);
									} catch(ex) {
											console.log("imageFileChoiceHandleChanged exception:",ex);
									}
							},false);
							resetImageUpload();
							*/
					}
			});
			return {
					activate:function(){
							currentMode.deactivate();
							currentMode = imageMode;
							var x = 10;
							var y = 10;
							var worldPos = rendererObj.screenToWorld(x,y);
							currentImage = {
									"type":"imageDefinition",
									"screenX":x,
									"screenY":y,
									"x":worldPos.x,
									"y":worldPos.y
							}
							modeChanged(imageMode);
					},
					handleDroppedSrc:function(src,x,y){
							var worldPos = rendererObj.screenToWorld(x,y);
							var thisCurrentImage = {
									"type":"imageDefinition",
									"screenX":x,
									"screenY":y,
									"x":worldPos.x,
									"y":worldPos.y
							};
							clientSideProcessImageSrc(src,thisCurrentImage,sendImageToServer,function(newSize){return false;});
					},

					handleDrop:function(dataTransfer,x,y){
							var yOffset = 0;
							var processed = [];
							console.log("handling drop:",dataTransfer,x,y);
							var processFile = function(file,sender){
									try {
											if (file != undefined && file != null && "type" in file && file.type.indexOf("image") == 0 && !_.some(processed,function(i){return i == file;})){
													var worldPos = rendererObj.screenToWorld(x,y + yOffset);
													var thisCurrentImage = {
															"type":"imageDefinition",
															"screenX":x,
															"screenY":y + yOffset,
															"x":worldPos.x,
															"y":worldPos.y
													};
													thisCurrentImage.fileUpload = file;
													processed.push(file);
													clientSideProcessImage(sendImageToServer,thisCurrentImage);
													yOffset += 50;
											}
									} catch(e){
											console.log("could not processFile:",file,sender);
									}
							};
							_.forEach(dataTransfer.files,function(f){processFile(f,"file");});
							_.forEach(dataTransfer.items,function(item){
									try {
											var file = item.getAsFile(0);
											processFile(file,"item");
									} catch(e){
											console.log("could not get item as file:",e);
									}
							});

					},
					deactivate:function(){
							resetImageUpload();
							modeChanged(noneMode);
					}
			};
	})();
	var	panMode = {
		name:"pan",
		activate:function(){
			currentMode.deactivate();
			currentMode = panMode;
			var originX;
			var originY;
			var down = function(x,y,z){
				takeControlOfViewbox(true);
				originX = x;
				originY = y;
			}
			var move = function(x,y,z){
				var xDelta = x - originX;
				var yDelta = y - originY;
				Pan.translate(-1 * xDelta,-1 * yDelta);
				originX = x;
				originY = y;
			}
			var up = function(x,y,z){
			}
			registerPositionHandlers(down,move,up);
			modeChanged(panMode);
		},
		deactivate:function(){
			unregisterPositionHandlers();
			modeChanged(noneMode);
		}
	};
	var batchTransform = function(){
		return {
			type:"moveDelta",
			identity:Date.now().toString(),
			timestamp:Date.now(),
			inkIds:[],
			textIds:[],
			multiWordTextIds:[],
			videoIds:[],
			imageIds:[],
			xOrigin:0,
			yOrigin:0,
			xTranslate:0,
			yTranslate:0,
			xScale:1.0,
			yScale:1.0,
			isDeleted:false
		}
	}
	var totalSelectedBounds = function(){
		var totalBounds = {x:Infinity,y:Infinity,x2:-Infinity,y2:-Infinity};
		var incorporate = function(item){
				var bounds = item.bounds;
				totalBounds.x = Math.min(totalBounds.x,bounds[0]);
				totalBounds.y = Math.min(totalBounds.y,bounds[1]);
				totalBounds.x2 = Math.max(totalBounds.x2,bounds[2]);
				totalBounds.y2 = Math.max(totalBounds.y2,bounds[3]);
		};
		_.forEach(selected.inks,incorporate);
		_.forEach(selected.texts,incorporate);
		_.forEach(selected.images,incorporate);
		_.forEach(selected.multiWordTexts,incorporate);
		_.forEach(selected.videos,incorporate);
		totalBounds.width = totalBounds.x2 - totalBounds.x;
		totalBounds.height = totalBounds.y2 - totalBounds.y;
		totalBounds.tl = rendererObj.worldToScreen(totalBounds.x,totalBounds.y);
		totalBounds.br = rendererObj.worldToScreen(totalBounds.x2,totalBounds.y2);
		return totalBounds;
	};

		var selectionCategories = [
				{
						selCatName:"inks",
						boardCatName:"inks",
						filterFunc:function(i){return !i.isHighlighter;}
				},
				{
						selCatName:"inks",
						boardCatName:"highlighters",
						filterFunc:function(i){return i.isHighlighter;}
				},
				{
						selCatName:"images",
						boardCatName:"images",
						filterFunc:function(i){return true;}
				},
				{
						selCatName:"texts",
						boardCatName:"texts",
						filterFunc:function(i){return true;}
				},
				{
						selCatName:"multiWordTexts",
						boardCatName:"multiWordTexts",
						filterFunc:function(i){return true;}
				},
				{
						selCatName:"videos",
						boardCatName:"videos",
						filterFunc:function(i){return true;}
				}
		]; // this is necessary to fix a bug which comes up in the next bit, which results in the onSelectionChanged firing repeatedly, because inks are checked twice and highlighters don't appear in inks, and inks don't appear in highlighters.
		var updateSelectionWhenBoardChanges = function(){
				var changed = false;
				_.forEach(selectionCategories,function(category){
					var selCatName = category.selCatName;
					var boardCatName = category.boardCatName;
					if (selected && selCatName in selected){
						var cat = selected[selCatName];
						_.forEach(cat,function(i){
							if (category.filterFunc(i)) {
								if (cat && boardCatName in history && i && i.identity in history[boardCatName]) {
									cat[i.identity] = history[boardCatName][i.identity];
								} else {
									changed = true;
									delete cat[i.identity];
								}
							}
						});
					}
				});
				rehomeInteractablesToSelection();
				if(changed){
					selectionChanged(selected);
				}
		};

			var deleteSelectionFunction = function(){
				if (selected != undefined){
					var deleteTransform = batchTransform();
					deleteTransform.isDeleted = true;
					if ("inks" in selected){
							deleteTransform.inkIds = _.keys(selected.inks);
					}
					if ("texts" in selected){
							deleteTransform.textIds = _.keys(selected.texts);
					}
					if ("images" in selected){
							deleteTransform.imageIds = _.keys(selected.images);
					}
					if ("multiWordTexts" in selected){
							deleteTransform.multiWordTextIds = _.keys(selected.multiWordTexts);
					}
					if ("videos" in selected){
							deleteTransform.videoIds = _.keys(selected.videos);
					}
					stanzaAvailable(deleteTransform);
					clearSelectionFunction();
				}
			};

	var selectMode = (function(){
			var clearSelectionFunction = function(){
					selected = {images:{},texts:{},inks:{},multiWordTexts:{},videos:{}};
					selectionChanged(selected);
			};

			return {
				name:"select",
				setSelection:function(selected){
						selected = _.merge(selected,selected);
				},
				offset:{x:0,y:0},
				resizing:false,
				dragging:false,
				aspectLocked:false,
				clearSelection:clearSelectionFunction,
				deleteSelection:deleteSelectionFunction,
				activate:function(){
					currentMode.deactivate();
					currentMode = selectMode;
					var originPoint = {x:0,y:0};
					var marqueeOriginX;
					var marqueeOriginY;
					var marquee = $("<div/>",{
							id:"selectMarquee"
					});
					var adorner = $("#selectionAdorner");
					var threshold = 30;
					var categories = function(func){
							func("images");
							func("texts");
							func("multiWordTexts");
							func("inks");
							func("videos");
					}
					var down = function(x,y,z,worldPos,modifiers){
							resizing = false;
							dragging = false;
							originPoint = {x:x,y:y};
							marqueeOriginX = x;
							marqueeOriginY = y;
							selectionWorldOrigin = worldPos;
							if (!(modifiers.ctrl)){
									var tb = totalSelectedBounds();
									if(tb.x != Infinity){
											var threshold = 3 / rendererObj.getScale();
											var ray = [
													worldPos.x - threshold,
													worldPos.y - threshold,
													worldPos.x + threshold,
													worldPos.y + threshold
											];
											var isDragHandle = function(property){
													return _.some(selected[property],function(el){
															if (el){
																	return intersectRect(el.bounds,ray);
															} else {
																	return false;
															}
													});
											}
											dragging = _.some(["images","texts","inks","multiWordTexts","videos"],isDragHandle);
									}
							}
							if(dragging){
									selectionOffset = worldPos;
							}
							else if(resizing){
							}
							else{
									adorner.empty();
									adorner.append(marquee);
									marquee.show();
									updateMarquee(marquee,originPoint,originPoint);
							}
					};
					var move = function(x,y,z,worldPos,modifiers){
							var currentPoint = {x:x,y:y};
							selectionOffset = worldPos;
							if(dragging){
								rendererObj.render();
							}
							else if(resizing){
								rendererObj.render();
							}
							else{
								updateMarquee(marquee,originPoint,currentPoint);
							}
					};
					var up = function(x,y,z,worldPos,modifiers){
						WorkQueue.gracefullyResume();

						var getMostRecentStanza = function(stanzas,prefix) {
								var topSelectedItem = null;
								if (_.size(stanzas) > 0) {
										topSelectedItem = _.reverse(_.sortBy(stanzas, 'timestamp'))[0];
								}
								return topSelectedItem;
						}
						var hasValue = function(stanza) {
								return stanza && null !== stanza && 'undefined' !== stanza;
						};
						try{
							var xDelta = worldPos.x - selectionWorldOrigin.x;
							var yDelta = worldPos.y - selectionWorldOrigin.y;
							var dragThreshold = 15;
							if(Math.abs(xDelta) + Math.abs(yDelta) < dragThreshold){
									dragging = false;
							}
							if(dragging){
									var root = totalSelectedBounds();
									_.each(selected.multiWordTexts,function(text,id){
											//Modes.text.echoesToDisregard[id] = true;
									});
									/*
									Modes.text.mapSelected(function(box){
											box.doc.position.x += xDelta;
											box.doc.position.y += yDelta;
											box.doc.invalidateBounds();
									});
									*/
									var moved = batchTransform();
									moved.xTranslate = xDelta;
									moved.yTranslate = yDelta;
									moved.inkIds = _.keys(selected.inks);
									moved.textIds = _.keys(selected.texts);
									moved.imageIds = _.keys(selected.images);
									moved.videoIds = _.keys(selected.videos);
									moved.multiWordTextIds = _.keys(selected.multiWordTexts);
									dragging = false;
									stanzaAvailable(moved);
							} else {
								var selectionRect = rectFromTwoPoints(selectionWorldOrigin,worldPos,2);
								var selectionBounds = [selectionRect.left,selectionRect.top,selectionRect.right,selectionRect.bottom];
								var intersected = {
										images:{},
										texts:{},
										inks:{},
										multiWordTexts:{},
										videos:{}
								};
								var intersections = {};
								var intersectCategory = function(category){
										_.forEach(history[category],function(item,i){
											/*
												if (!("bounds" in item)){
														if ("type" in item){
																switch(item.type){
																case "text":
																		prerenderText(item);
																		break;
																case "image":
																		prerenderImage(item);
																		break;
																case "ink":
																		prerenderInk(item);
																		break;
																case "video":
																		prerenderVideo(item);
																		break;
																case "multiWordText":
																		prerenderMultiwordText(item);
																		break;
																default:
																		item.bounds = [NaN,NaN,NaN,NaN];
																}
														}
												}
												*/
												var b = item.bounds;
												var selectionThreshold = 1;
												var overlap = overlapRect(selectionBounds,item.bounds);
												if(overlap >= selectionThreshold){
													intersected[category][item.identity] = item;
												}
										});
								};
								categories(intersectCategory);
								_.forEach(history.highlighters,function(item,i){
									if(item !== undefined && intersectRect(item.bounds,selectionBounds)){
										intersected.inks[item.identity] = item;
									}
								});
								/*Default behaviour is now to toggle rather than clear.  Ctrl-clicking doesn't do anything different*/
								var toggleCategory = function(category){
									_.forEach(intersected[category],function(item,id){
										if (!(category in selected)){
											selected[category] = {};
										}
										if(category in selected && id in selected[category]){
											delete selected[category][id];
										} else {
											if (item !== undefined && preSelectItem(item)){
												selected[category][id] = item;
												postSelectItem(item);
											}
										}
									});
								};
								categories(toggleCategory);
								if(_.size(intersected) == 0){
										selectMode.clearSelection();
								}

								// A single click generates a selectionRect of (2,2).
								if( selectionRect.width <= 2 && selectionRect.height <= 2) {
									// Select only the top canvasContent in order (top to bottom):
									// ink, richtext, text, highlighter, video, image

									var normalInks = _.filter(intersected.inks, function (ink) {
											return !ink.isHighlighter;
									});
									var topNormalInk = getMostRecentStanza(normalInks, "ink");
									if (hasValue(topNormalInk)) {
										selectMode.clearSelection();
										if (preSelectItem(topNormalInk)){
											selected.inks[topNormalInk.identity] = topNormalInk;
											postSelectItem(topNormalInk);
										}
									}
									else {
										var topMultiWordText = getMostRecentStanza(intersected.multiWordTexts, "multiWordText");
										if (hasValue(topMultiWordText)) {
											selectMode.clearSelection();
											if (preSelectItem(topMultiWordText)){
												selected.multiWordTexts[topMultiWordText.identity] = topMultiWordText;
												postSelectItem(topMultiWordText);
											}
										}
										else {
											var topText = getMostRecentStanza(intersected.texts, "text");
											if (hasValue(topText)) {
												selectMode.clearSelection();
												if (preSelectItem(topText)){
													selected.texts[topText.identity] = topText;
													postSelectItem(topText);
												}
											}
											else {
												var highlighters = _.filter(intersected.inks, function (ink) {
														return ink.isHighlighter;
												});
												var topHighlighter = getMostRecentStanza(highlighters, "highlighter");
												if (hasValue(topHighlighter)) {
													selectMode.clearSelection();
													if (preSelectItem(topHighlighter)){
														selected.inks[topHighlighter.identity] = topHighlighter;
														postSelectItem(topHighlighter);
													}
												}
												else {
													var topVideo = getMostRecentStanza(intersected.videos, "video");
													if (hasValue(topVideo)) {
														selectMode.clearSelection();
														if (preSelectItem(topVideo)){
															selected.videos[topVideo.identity] = topVideo;
															postSelectItem(topVideo);
														}
													}
													else {
														var topImage = getMostRecentStanza(intersected.images, "image");
														if (hasValue(topImage)) {
															selectMode.clearSelection();
															if (preSelectItem(topImage)){
																selected.images[topImage.identity] = topImage;
																postSelectItem(topImage);
															}
														}
													}
												}
											}
										}
									}
								}
								selectionChanged(selected);
							}
							marquee.css(
								{width:0,height:0}
							).hide();
							rendererObj.render();
						}
						catch(e){
							errorFunc(e,"selection up",[x,y,z,worldPos,modifiers]);
						}
					};
					dragging = false;
					resizing = false;
					registerPositionHandlers(down,move,up);
				},
				deactivate:function(){
					unregisterPositionHandlers();
					clearSelectionFunction();
					rendererObj.render();
				}
			}
	})();
	var updateMarquee = function(marquee,pointA,pointB){
		var rect = rectFromTwoPoints(pointA,pointB);
		var selectionAdorner = $("#selectionAdorner");
		if (!(jQuery.contains(selectionAdorner,marquee))){
				selectionAdorner.append(marquee);
		}
		if (!(marquee.is(":visible"))){
				marquee.show();
		}
		marquee.css({
				left:px(rect.left),
				top:px(rect.top),
				width:px(rect.width),
				height:px(rect.height)
		});
	}

	var zoomMode = {
			name:"zoom",
			activate:function(){
					currentMode.deactivate();
					currentMode = zoomMode;
					var marquee = $("<div />",{
							id:"zoomMarquee"
					})
					var startX = 0;
					var startY = 0;
					var startWorldPos;
					var proportion;
					var originPoint = {x:0,y:0};
					if(MeTLBus.check("onBoardContentChanged","autoZooming")){
						takeControlOfViewbox(false);
					}
					else{
						takeControlOfViewbox(true);
					}
					var aspectConstrainedDimensions = function(width,height){
						var boardDims = rendererObj.getDimensions();
						var proportion = boardDims.height / boardDims.width;
						var comparisonProportion = height / width;
						var dims = {
							height:height,
							width:width
						};
						if(comparisonProportion > proportion){
							dims.height = width * proportion;
						}
						else{
							dims.width = height / proportion;
						}
						return dims;
					}
					var aspectConstrainedRect = function(rect,hAlign,vAlign){ // vAlign and hAlign are strings to determine how to align the position of the aspect constrained rect within itself after adjusting the proportion.  It should be "top","bottom","center" and "left","right","center".
						var boardDims = rendererObj.getDimensions();
						var proportion = boardDims.height / boardDims.width;
						var comparisonProportion = rect.height / rect.width;
						var dims = {
								left:rect.left,
								top:rect.top,
								right:rect.right,
								bottom:rect.bottom,
								width:rect.width,
								height:rect.height
						};
						if (comparisonProportion > proportion){
								dims.height = rect.width * proportion;
								if (vAlign){
										var vOffset = rect.height - dims.height;
										if (vAlign == "center"){
											dims.top += vOffset / 2;
										} else if (vAlign == "bottom"){
											dims.top += vOffset;
										}
								}
						}
						else {
							dims.width = rect.height / proportion;
							if (hAlign){
								var hOffset = rect.width - dims.width;
								if (hAlign == "center"){
									dims.left += hOffset / 2;
								} else if (hAlign == "right"){
									dims.left += hOffset;
								}
							}
						}
						dims.right = dims.left + dims.width;
						dims.bottom = dims.top + dims.height;
						return dims;
					}

					var down = function(x,y,z,worldPos){
							//adding this so that using the zoom marquee results in the autofit being turned off.
							takeControlOfViewbox(true);
							var dims = rendererObj.getDimensions();
							proportion = dims.height / dims.width;
							startX = x;
							startY = y;
							startWorldPos = worldPos;
							marquee.show();
							marquee.appendTo($("#selectionAdorner"));
							originPoint = {x:x,y:y};
							updateMarquee(marquee,originPoint,originPoint);
					}
					var move = function(x,y,z,worldPos){
							var currentPoint = {x:x,y:y};
							var rect = rectFromTwoPoints(currentPoint,originPoint);
							var hAlign = "left";
							var vAlign = "top";
							if (currentPoint.x == rect.left){
									hAlign = "right";
							}
							if (currentPoint.y == rect.top){
									vAlign = "bottom";
							}
							var constrainedRect = aspectConstrainedRect(rect,hAlign,vAlign);
							updateMarquee(marquee,{x:constrainedRect.right,y:constrainedRect.bottom},{x:constrainedRect.left,y:constrainedRect.top});
					}
					var up = function(x,y,z,worldPos){
						WorkQueue.gracefullyResume();
						var newRect = rectFromTwoPoints(worldPos,startWorldPos);//[Math.min(startWorldPos.x,worldPos.x),Math.min(startWorldPos.y,worldPos.y),Math.abs(startWorldPos.x - worldPos.x),Math.abs(startWorldPos.y - worldPos.y)];
						var aspectConstrained = aspectConstrainedRect(newRect);
						var constrained = constrainRequestedViewboxFunction({
							x:aspectConstrained.left,
							y:aspectConstrained.top,
							width:aspectConstrained.width,
							height:aspectConstrained.height
						});	
						marquee.hide();
						TweenController.zoomAndPanViewbox(constrained.x,constrained.y,constrained.width,constrained.height);
					}
					registerPositionHandlers(down,move,up);
					modeChanged(zoomMode);
			},
			deactivate:function(){
					$("#zoomMarquee").remove();
					unregisterPositionHandlers();
					modeChanged(noneMode);
			}
	};
	var raySpan = 10;
	var deleted = [];
	var eraseDown = function(x,y,z,worldPos,modifiers){
		deleted = [];
	};
	var eraseMove = function(x,y,z,worldPos,modifiers){
		var boardContext = rendererObj.getBoardContext();
		var ray = [worldPos.x - raySpan, worldPos.y - raySpan, worldPos.x + raySpan, worldPos.y + raySpan];
		var markAsDeleted = function(bounds){
				var tl = rendererObj.worldToScreen(bounds[0],bounds[1]);
				var br = rendererObj.worldToScreen(bounds[2],bounds[3]);
				boardContext.fillRect(tl.x,tl.y,br.x - tl.x, br.y - tl.y);
		}
		var deleteInRay = function(coll){
			$.each(coll,function(i,item){
				if (intersectRect(item.bounds,ray) && preDeleteItem(item)){
					delete coll[item.identity];
					deleted.push(item);
					markAsDeleted(item.bounds);
					postDeleteItem(item);
				}
			})
		}
		boardContext.globalAlpha = 0.4;
		boardContext.fillStyle = "red";
		deleteInRay(history.inks);
		deleteInRay(history.highlighters);
		boardContext.globalAlpha = 1.0;
	};
	var eraseUp = function(x,y,z,worldPos,modifiers){
		var deleteTransform = batchTransform();
		deleteTransform.isDeleted = true;
		deleteTransform.inkIds = _.map(deleted,function(stanza){return stanza.identity;});
		_.forEach(deleted,function(stanza){
			MeTLBus.call("onCanvasContentDeleted",[stanza]);
		});
		stanzaAvailable(deleteTransform);
	};

	var drawMode = (function(){
			var erasing = false;
			var isHighlighter = false;
			var color = "#808080";
			var size = 10;
			var mousePressure = 256;
			var currentStroke = [];
			var isDown = false;
			var resumeWork;
			var down = function(x,y,z,worldPos,modifiers){
					isDown = true;
					if(!erasing && !modifiers.eraser){
							var boardContext = rendererObj.getBoardContext();
							boardContext.strokeStyle = color;
							boardContext.fillStyle = color;
							if (isHighlighter){
									boardContext.globalAlpha = 0.4;
							} else {
									boardContext.globalAlpha = 1.0;
							}
							currentStroke = [worldPos.x, worldPos.y, mousePressure * z];
							trail.x = x;
							trail.y = y;
							boardContext.beginPath();
							var newWidth = size * z;
							boardContext.arc(x,y,newWidth/2,0,Math.PI*2);
							boardContext.fill();
					} else {
					}
			};
			var deleted = [];
			var trail = {};
			var move = function(x,y,z,worldPos,modifiers){
					x = Math.round(x);
					y = Math.round(y);
					if(erasing || modifiers.eraser){
						return eraseMove(x,y,z,worldPos,modifiers);
					}
					else{
							var boardContext = rendererObj.getBoardContext();
							var newWidth = size * z;
							boardContext.beginPath();
							boardContext.lineCap = "round";
							boardContext.lineWidth = newWidth;
							var lastPoint = _.takeRight(currentStroke,3);
							boardContext.moveTo(trail.x,trail.y);
							boardContext.lineTo(x,y);
							boardContext.stroke();
							currentStroke = currentStroke.concat([worldPos.x,worldPos.y,mousePressure * z]);
					}
					trail.x = x;
					trail.y = y;
			};
			var up = function(x,y,z,worldPos,modifiers){
					isDown = false;
					if(erasing || modifiers.eraser){
						eraseUp(x,y,z,worldPos,modifiers);
					} else {
							var boardContext = rendererObj.getBoardContext();
							var newWidth = size * z;
							boardContext.lineWidth = newWidth;
							boardContext.beginPath();
							boardContext.lineWidth = newWidth;
							boardContext.lineCap = "round";
							boardContext.moveTo(trail.x,trail.y);
							boardContext.lineTo(x,y);
							boardContext.stroke();
							currentStroke = currentStroke.concat([worldPos.x,worldPos.y,mousePressure * z]);
							strokeCollected(currentStroke,color,size,isHighlighter);
					}
					boardContext.globalAlpha = 1.0;
			};
			return {
					name:"draw",
					mousePressure:mousePressure,
					setColor:function(newColor){
						color = newColor;
						modeChanged(drawMode);
					},
					setSize:function(newSize){
						size = newSize;
						modeChanged(drawMode);
					},
					getColor:function(){
						return color;
					},
					getSize:function(){
						return size;
					},
					getIsHighlighter:function(){
						return isHighlighter;
					},
					setIsHighlighter:function(newIsHighlighter){
						isHighlighter = newIsHighlighter;
					},
					activate:function(){
							var boardContext = rendererObj.getBoardContext();
							boardContext.setLineDash([]);
							currentMode.deactivate();
							currentMode = drawMode;
							registerPositionHandlers(down,move,up);
							modeChanged(drawMode);
					},
					deactivate:function(){
							WorkQueue.gracefullyResume();
							unregisterPositionHandlers();
							modeChanged(noneMode);
					}
			};
	})();
	var eraseMode = (function(){
		var isDown = false;
		return {
			name:"erase",
			activate:function(){
					currentMode.deactivate();
					currentMode = eraseMode;
					var down = function(x,y,z,worldPos,modifiers){
						isDown = true;
						eraseDown(x,y,z,worldPos,modifiers)
					};
					var move = function(x,y,z,worldPos,modifiers){
						eraseMove(x,y,z,worldPos,modifiers)
					};
					var up = function(x,y,z,worldPos,modifiers){
						isDown = false;
						eraseUp(x,y,z,worldPos);
					};
					registerPositionHandlers(down,move,up);
					modeChanged(eraseMode);
			},
			deactivate:function(){
					unregisterPositionHandlers();
					modeChanged(noneMode);
			}
		};
	})();

	var Pan = {
    pan:function(xDelta,yDelta){
			takeControlOfViewbox(true);
	    var s = rendererObj.getScale();
			TweenController.panViewboxRelative(xDelta / s, yDelta / s);
    },
		translate:function(xDelta,yDelta){
			takeControlOfViewbox(true);
	    var s = rendererObj.getScale();
			TweenController.translateViewboxRelative(xDelta / s, yDelta / s);
    }
	}
  var constrainRequestedViewboxFunction = function(vb){
		var maxClamped = getMaxViewboxSizeFunction();
		var minClamped = getMinViewboxSizeFunction();
		var outW = undefined;
		var outH = undefined;
		var outX = undefined;
		var outY = undefined;
		if ("width" in vb){
				outW = vb.width;
				if (outW > maxClamped.width){
						outW = maxClamped.width;
				}
				if (outW < minClamped.width){
						outW = minClamped.width;
				}
		}
		if ("height" in vb){
				outH = vb.height;
				if (outH > maxClamped.height){
						outH = maxClamped.height;
				}
				if (outH < minClamped.height){
						outH = minClamped.height;
				}
		}
		if ("x" in vb){
				outX = vb.x;
				if (outW != vb.width){
						outX +=  (vb.width - outW) / 2;
				}
		}
		if ("y" in vb && outH){
				outY = vb.y;
				if (outH != vb.height){
						outY += (vb.height - outH) / 2;
				}
		}
		return {width:outW,height:outH,x:outX,y:outY};
	}
	var getMaxViewboxSizeFunction = function(){
		return {
			width:history.width * maxZoomOut,
			height:history.height * maxZoomOut
		};
	};
	var getMinViewboxSizeFunction = function(){
		var dims = rendererObj.getDimensions();
		var boardWidth = dims.width;
		var boardHeight = dims.height;
			return {
				width:boardWidth * maxZoomIn,
				height:boardHeight * maxZoomIn
			};
	};
	var maxZoomOut = 3;
	var maxZoomIn = 0.1;

	var Zoom = (function(){
    var zoomFactor = 1.2;

 		var scaleFunc = function(scale,ignoreLimits){
			takeControlOfViewbox(true);
			var vb = rendererObj.getViewbox();
			var requestedWidth = vb.width * scale;
			var requestedHeight = vb.height * scale;
			if(!ignoreLimits){
					var constrained = constrainRequestedViewboxFunction({height:requestedHeight,width:requestedWidth});
					requestedWidth = constrained.width;
					requestedHeight = constrained.height;
			}
			var ow = vb.width;
			var oh = vb.height;
			var xDelta = (ow - requestedWidth) / 2;
			var yDelta = (oh - requestedHeight) / 2;
			var finalX = xDelta + vb.x;
			var finalY = yDelta + vb.y;
			TweenController.scaleAndTranslateViewbox(finalX,finalY,requestedWidth,requestedHeight);
		};
		var zoomFunc = function(scale,ignoreLimits,onComplete){
			takeControlOfViewbox(true);
			var vb = rendererObj.getViewbox();
			var requestedWidth = vb.width * scale;
			var requestedHeight = vb.height * scale;
			if(!ignoreLimits){
					var constrained = constrainRequestedViewboxFunction({height:requestedHeight,width:requestedWidth});
					requestedWidth = constrained.width;
					requestedHeight = constrained.height;
			}
			var ow = vb.width;
			var oh = vb.height;
			var wDelta = requestedWidth - ow;
			var hDelta = requestedHeight - oh;
			var xDelta = -1 * (wDelta / 2);
			var yDelta = -1 * (hDelta / 2);
			TweenController.zoomAndPanViewboxRelative(xDelta,yDelta,wDelta,hDelta,onComplete);
		};
    return {
        scale:scaleFunc,
        zoom:zoomFunc,
        out:function(){
            zoomFunc(zoomFactor);
        },
        "in":function(){
            zoomFunc(1 / zoomFactor);
        },
        constrainRequestedViewbox:constrainRequestedViewboxFunction
    };
	})();
	var TweenController = (function(){
    var panViewboxFunction = function(xDelta,yDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
			var vb = rendererObj.getViewbox();
			return easingAlterViewboxFunction(xDelta,yDelta,vb.width,vb.height,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var translateViewboxFunction = function(xDelta,yDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
			var vb = rendererObj.getViewbox();
			return instantAlterViewboxFunction(xDelta,yDelta,vb.width,vb.height,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var panViewboxRelativeFunction = function(xDelta,yDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
			var vb = rendererObj.getViewbox();
			return easingAlterViewboxFunction(xDelta + vb.x,yDelta + vb.y,vb.width,vb.height,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var translateViewboxRelativeFunction = function(xDelta,yDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
			var vb = rendererObj.getViewbox();
			return instantAlterViewboxFunction(xDelta + vb.x,yDelta + vb.y,vb.width,vb.height,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var zoomAndPanViewboxFunction = function(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox,notFollowable){
			var vb = rendererObj.getViewbox();
			return easingAlterViewboxFunction(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox,notFollowable);
    };
    var zoomAndPanViewboxRelativeFunction = function(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
			var vb = rendererObj.getViewbox();
			return easingAlterViewboxFunction(xDelta + vb.x,yDelta + vb.y,widthDelta + vb.width,heightDelta + vb.height,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var scaleAndTranslateViewboxFunction = function(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
			var vb = rendererObj.getViewbox();
			return instantAlterViewboxFunction(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var scaleAndTranslateViewboxRelativeFunction = function(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
			var vb = rendererObj.getViewbox();
			return instantAlterViewboxFunction(xDelta + vb.x,yDelta + vb.y,widthDelta + vb.width,heightDelta + vb.height,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var updateRequestedPosition = function(){
			var vb = rendererObj.getViewbox();
        requestedViewboxX = vb.x;
        requestedViewboxY = vb.y;
        requestedViewboxWidth = vb.width;
        requestedViewboxHeight = vb.height;
    };
    var throttleSpeed = 30;
    var instantAlterViewboxFunction = function(finalX,finalY,finalWidth,finalHeight,onComplete,shouldAvoidUpdatingRequestedViewbox){
        if (isNaN(finalX) || isNaN(finalY) || isNaN(finalWidth) || isNaN(finalHeight)){
            if (onComplete){
                onComplete();
            }
            return;
        }
        if(tween){
            tween.stop();
        }
        tween = false;
				rendererObj.setViewbox(finalX,finalY,finalWidth,finalHeight);
				rendererObj.render();
        if (!shouldAvoidUpdatingRequestedViewbox){
            updateRequestedPosition();
        }
        if (onComplete){
            onComplete();
        }
        teacherViewUpdated(finalX,finalY,finalWidth,finalHeight);
				viewboxChanged(rendererObj.getViewbox());
    };
    var teacherViewUpdated = _.throttle(function(x,y,w,h){
        if("Conversations" in window && Conversations.isAuthor() && UserSettings.getIsInteractive()){
            var ps = [x,y,w,h,Date.now(),Conversations.getCurrentSlideJid(),MeTLBus.check("onBoardContentChanged","autoZooming")];
            if(w <= 0 || h <= 0){
                return;
            }
            if(_.some(ps,function(p){
                return typeof(p) == "undefined" || isNaN(p);
            })){
                return;
            };
            stanzaAvailable({
                author:UserSettings.getUsername(),
                timestamp:Date.now(),
                type:"command",
                command:"/TEACHER_VIEW_MOVED",
                parameters:ps.map(function(p){
                    return p.toString();
                })
            });
        }
    },300);
    var tween;
    var easingAlterViewboxFunction = function(finalX,finalY,finalWidth,finalHeight,onComplete,shouldAvoidUpdatingRequestedViewbox,notFollowable){
        if (isNaN(finalX) || isNaN(finalY) || isNaN(finalWidth) || isNaN(finalHeight)){
            if (onComplete){
                onComplete();
            }
            return;
        }
				var vb = rendererObj.getViewbox();
        var interval = 300;//milis
        var startX = vb.x;
        var startY = vb.y;
        var startWidth = vb.width;
        var startHeight = vb.height;
        var xDelta = finalX - startX;
        var yDelta = finalY - startY;
        var widthDelta = finalWidth - startWidth;
        var heightDelta = finalHeight - startHeight;
        var hasChanged = function(){
            return (finalX != undefined && finalY != undefined && finalWidth > 0 && finalHeight > 0) && (xDelta != 0 || yDelta != 0 || widthDelta != 0 || heightDelta != 0);
        };
        if (tween){
            tween.stop();
            tween = false;
        }
        tween = new TWEEN.Tween({x:0,y:0,w:0,h:0})
            .to({x:xDelta,y:yDelta,w:widthDelta,h:heightDelta}, interval)
            .easing(TWEEN.Easing.Quadratic.Out)
            .onUpdate(function(){
								rendererObj.setViewbox(startX + this.x,startY + this.y,startWidth + this.w,startHeight + this.h);
            }).onComplete(function(){
                tween = false;
								rendererObj.setViewbox(finalX,finalY,finalWidth,finalHeight);
								rendererObj.render();
                if (!shouldAvoidUpdatingRequestedViewbox){
                    updateRequestedPosition();
                }
                if (onComplete){
                    onComplete();
                }
								viewboxChanged(rendererObj.getViewbox());
            }).start();
        var update = function(t){
					if (tween){
						TWEEN.update();
						rendererObj.render();
						requestAnimationFrame(update);
					}
        };
        requestAnimationFrame(update);
				/*
        if("Conversations" in window && Conversations.isAuthor()){
            if(notFollowable || shouldAvoidUpdatingRequestedViewbox){
                //console.log("not following viewbox update");
            }
            else if (hasChanged()){
                //console.log("sending viewbox update");
                teacherViewUpdated(finalX,finalY,finalWidth,finalHeight);
            }
        }
				*/
    };
    return {
        panViewbox:panViewboxFunction,
        translateViewbox:translateViewboxFunction,
        zoomAndPanViewbox:zoomAndPanViewboxFunction,
        scaleAndTranslateViewbox:scaleAndTranslateViewboxFunction,
        panViewboxRelative:panViewboxRelativeFunction,
        translateViewboxRelative:translateViewboxRelativeFunction,
        zoomAndPanViewboxRelative:zoomAndPanViewboxRelativeFunction,
        scaleAndTranslateViewboxRelative:scaleAndTranslateViewboxRelativeFunction,
        immediateView:function(){
            return [viewboxX, viewboxY, viewboxX+viewboxWidth, viewboxY+viewboxHeight];
        }
    }
	})();

	var renderSelectionOutlines = function(canvasContext){
			var size = resizeHandleSize;
			canvasContext.save();
			canvasContext.lineWidth = 1;
			var multipleItems = [];
			_.forEach(selected,function(category){
				_.forEach(category,function(item){
					var bounds = item.bounds;
					var tl = rendererObj.worldToScreen(bounds[0],bounds[1]);
					var br = rendererObj.worldToScreen(bounds[2],bounds[3]);
					multipleItems.push([tl,br]);
					if(bounds){
						canvasContext.setLineDash([5]);
						canvasContext.strokeStyle = "blue";
						canvasContext.strokeRect(tl.x,tl.y,br.x-tl.x,br.y-tl.y);
					}
				});
			});
			var tb = totalSelectedBounds();
			if(multipleItems.length > 0){
					canvasContext.strokeStyle = "blue";
					canvasContext.strokeWidth = 3;
					canvasContext.strokeRect(tb.tl.x,tb.tl.y,tb.br.x - tb.tl.x,tb.br.y - tb.tl.y);
			}
			canvasContext.restore();
	};
	var renderContentIdentification = function(canvasContext,rendered){
		return;
			canvasContext.save();
			if(Modes.select.isAdministeringContent()){
					var visibleUsers = _.groupBy(rendered,"author");
					var pad = 3;
					_.each(visibleUsers,function(content,user){
							var userBounds = _.reduce(_.map(content,"bounds"),mergeBounds);
							var tl = worldToScreen(userBounds[0],userBounds[1]);
							canvasContext.strokeStyle = "black";
							canvasContext.lineWidth = 0.1;
							_.each(content,function(c){
									canvasContext.beginPath();
									canvasContext.moveTo(tl.x,tl.y);
									var cB = worldToScreen(c.bounds[0],c.bounds[1]);
									canvasContext.lineTo(cB.x,cB.y);
									canvasContext.stroke();
							});
							canvasContext.fillStyle = "black";
							canvasContext.fillRect(tl.x - pad,tl.y,canvasContext.measureText(user).width + pad * 2,14);
							canvasContext.fillStyle = "white";
							canvasContext.fillText(user,tl.x,tl.y+10);
					});
			}
			canvasContext.restore();
	};
	var renderSelectionGhosts = function(canvasContext){
		var zero = selectionWorldOrigin;
		if(dragging){
			canvasContext.save();
			var s = rendererObj.getScale();
			var x = selectionOffset.x - zero.x;
			var y = selectionOffset.y - zero.y;
			var screenOffset = rendererObj.worldToScreen(x,y);
			var relativeOffset = rendererObj.worldToScreen(0,0);
			canvasContext.translate(
					screenOffset.x - relativeOffset.x,
					screenOffset.y - relativeOffset.y);
			canvasContext.globalAlpha = 0.7;
			_.forEach(selected,function(category,name){
					_.forEach(category,function(item){
							switch(name){
							case "images":
									rendererObj.drawImage(item);
									break;
							case "videos":
									rendererObj.drawVideo(item);
									break;
							case "texts":
									rendererObj.drawText(item);
									break;
							case "multiWordTexts":
									rendererObj.drawMultiwordText(item);
									break;
							case "inks":
									rendererObj.drawInk(item);
									break;
							}
					});
			});
			canvasContext.restore();
		} else if(resizing){
			var totalBounds = totalSelectedBounds();
			var originalWidth = totalBounds.x2 - totalBounds.x;
			var originalHeight = totalBounds.y2 - totalBounds.y;
			var requestedWidth = selectionOffset.x - totalBounds.x;
			var requestedHeight = selectionOffset.y - totalBounds.y;
			var xScale = requestedWidth / originalWidth;
			var yScale = requestedHeight / originalHeight;
			var transform = function(x,y,func){
				canvasContext.save();
				canvasContext.globalAlpha = 0.7;
				canvasContext.translate(x,y);
				canvasContext.scale(xScale,yScale);
				canvasContext.translate(-x,-y);
				func();
				canvasContext.restore();
			};
			var noop = function(){};
			_.forEach(selected,function(category,name){
				_.forEach(category,function(item){
					var bounds = item.bounds;
					var screenPos = rendererObj.worldToScreen(bounds[0],bounds[1]);
					var x = screenPos.x;
					var y = screenPos.y;
					switch(name){
						case "images":
							transform(x,y,function(){
								rendererObj.drawImage(item);
							});
							break;
						case "videos":
							transform(x,y,function(){
								rendererObj.drawVideo(item);
							});
							break;
						case "texts":
							transform(x,y,function(){
								rendererObj.drawText(item);
							});
							break;
						case "multiWordTexts":
							if(aspectLocked){
								transform(x,y,function(){
									rendererObj.drawMultiwordText(item);
								});
							} else {
								canvasContext.save();
								canvasContext.translate(x,y);
								canvasContext.globalAlpha = 0.7;
								var s = rendererObj.getScale();
								canvasContext.scale(s,s);
								var scaledText = carota.editor.create({
									querySelector:function(){
										return {
											addEventListener:noop
										}
									},
									handleEvent:noop
								}, canvasContext, _.cloneDeep(item),rendererObj,function(evn,evps){},20,40);
								scaledText.position = {x:bounds[0],y:bounds[1]};
								scaledText.load(item.doc.save());
								/*
								delete scaledText.canvas;
								*/
								var fullRange = scaledText.documentRange();
								var nominatedWidth = Math.max(
										item.doc.width() * xScale,
										minimumTextWidth / rendererObj.getScale()
								);
								scaledText.width(nominatedWidth);
								scaledText.updateCanvas();
								rendererObj.drawMultiwordText(scaledText);
								//scaledText.editor.paint(board[0]);
								//carota.editor.paint(board[0],scaledText);
								canvasContext.restore();
							}
							break;
						case "inks":
							transform(x,y,function(){
								rendererObj.drawInk(item);
							});
							break;
					}
				});
			});
		}
	};
	
	var renderCanvasInteractables = function(canvasContext){
		_.each(canvasInteractables,function(category){
			_.each(category,function(interactable){
				if (interactable != undefined && "render" in interactable){
					canvasContext.save();
					canvasContext.lineWidth = 1;
					interactable.render(canvasContext);
					canvasContext.restore();
				}
			});
		});
	};

	var interactableCanvasRender = function(context){
		renderSelectionOutlines(context);
		renderSelectionGhosts(context);
		//renderContentIdentification(rendered);
		renderCanvasInteractables(context);
		//renderTint({x:0,y:0,w:boardWidth,h:boardHeight});
	};

	var preSelectItem = function(item){
		return true;
	};
	var preDeleteItem = function(item){
		return true;
	};
	var postSelectItem = function(item){
	};
	var postDeleteItem = function(item){
	};
	var selectionChangedOuter = function(selected){
		console.log("selectionChanged",selected);
	};

	var rehomeInteractablesToSelection = function(){
		var totalBounds = totalSelectedBounds();
		if(totalBounds.x == Infinity){
			interactableAttrs.opacity = 0;
		}
		else{
			interactableAttrs.opacity = 1;
			manualMove.rehome(totalBounds);
			resizeFree.rehome(totalBounds);
			resizeAspectLocked.rehome(totalBounds);
		}
	}
	var selectionChanged = function(selected){
		rehomeInteractablesToSelection();
		selectionChangedOuter(selected);
	};
	var modeChanged = function(m){
		console.log("modeChanged",m);
	};
	var setHistoryFunc = function(history){
		rendererObj.setHistory(history,function(){
			rendererObj.render();
		});
	};
	var strokeCollected = function(points,color,thickness,isHighlighter){
    if(points.length > 0) {
			var ink = {
				thickness : rendererObj.scaleScreenToWorld(thickness),
				color:[color,255],
				type:"ink",
				timestamp:Date.now(),
				isHighlighter:isHighlighter
			};
			var x;
			var y;
			var worldPos;
			ink.points = points;
			ink.checksum = ink.points.reduce(function(a,b){return a+b},0);
			ink.startingSum = ink.checksum;
			ink.identity = ink.checksum.toFixed(1);
			stanzaAvailable(ink);
    }
	};
	var stanzaAvailable = function(stanza){};
	var addStanzaFunc = function(stanza){
		if (stanza !== undefined && "type" in stanza){

			rendererObj.addStanza(stanza);
		}
	};

	var minimumTextHeight = resizeHandleSize * 3;
	var richTextMode = (function(){
		var texts = [];
		var echoesToDisregard = {};
		var createBlankText = function(worldPos,runs){
			var width = minimumTextWidth / rendererObj.getScale();
			var identity = sprintf("%s_%s",Date.now(),_.uniqueId());
			var stanza = rendererObj.prerenderMultiwordText({
				bounds:[worldPos.x,worldPos.y,worldPos.x,worldPos.y],
				identity:identity,
				requestedWidth:width,
				width:width,
				height:minimumTextHeight,
				x:worldPos.x,
				y:worldPos.y,
				type:"multiWordText",
				words:[]
			});
			stanza.doc.load(runs);
			return stanza;
		};
		var defaultTextAttrs = {
			fontSize:12,
			bold:false,
			underline:false,
			italic:false,
			color:["#000000",255]
		};
		var editorFor = function(t){
			if (t !== undefined){
				var editor = history.multiWordTexts[t.identity];
				if (editor !== undefined && preSelectItem(editor)){
					if(!editor){
						editor = history.multiWordTexts[t.identity] = richTextEditorToStanza(t);
					}
					if (!editor.doc){
						editor = prerenderMultiwordText(t);
					}
					var onChange = function(){
						var source = history.multiWordTexts[editor.identity];
						console.log("onChange",editor,source);
						if (source && source.doc){
							var stanza = richTextEditorToStanza(source);
							stanzaAvailable(stanza);
						}
					};
					editor.doc.contentChanged(onChange);
					editor.doc.selectionChanged(function(formatReport,canMoveViewport){
						// not sure what I need to do with this yet.  This'll be about updating controls for what's current under the selection, I'd think.
					});
					editor.doc.position = {x:t.x,y:t.y};
					editor.doc.width(t.width);
					postSelectItem(t);
				}
			}
			return t;
		};
		var editorAt = function(x,y,z,worldPos){
			var threshold = 10;
			var ray = [worldPos.x - threshold,worldPos.y - threshold,worldPos.x + threshold,worldPos.y + threshold];
			var texts = _.values(history.multiWordTexts).filter(function(text){
				var intersects = intersectRect(text.bounds,ray)
				return intersects && preSelectItem(text) && preRenderItem(text);
			});
			if(texts.length > 0){
				return texts[0];
			}
			else{
				return false;
			}
		};
		var editorContextFor = function(editor,worldPos){
			var relativePos = {x:worldPos.x - editor.position.x, y:worldPos.y - editor.position.y};
			var node = editor.byCoordinate(relativePos.x,relativePos.y);
			return {
				node:node,
				relativePos:relativePos
			}
		};
		var disableOtherEditors = function(editor){
			_.forEach(history.multiWordTexts,function(mwt){
				if (mwt.identity != editor.identity){
					mwt.isActive = false;
					mwt.caretVisible = false;
				}
			});
		};
		return {
			name:"richText",
			activate:function(){
				var doubleClickThreshold = 500;
				currentMode.deactivate();
				currentMode = richTextMode;
				modeChanged(richTextMode);
				var lastClick = 0;
				var down = function(x,y,z,worldPos,modifiers){
					var editor = editorAt(x,y,z,worldPos).doc;
					if (editor){
						disableOtherEditors(editor);
						editor.isActive = true;
						editor.caretVisible = true;
						editor.mousedownHandler(editorContextFor(editor,worldPos).node);
					};
				}
				var move = function(x,y,z,worldPos,modifiers){
					var editor = editorAt(x,y,z,worldPos).doc;
					if (editor){
							editor.mousemoveHandler(editorContextFor(editor,worldPos).node);
					}
				};
				var up = function(x,y,z,worldPos,modifiers){
					var clickTime = Date.now();
					var oldEditor = editorAt(x,y,z,worldPos);
					var editor = editorAt(x,y,z,worldPos);
					_.each(history.multiWordTexts,function(t){
						t.doc.isActive = t.doc.identity == editor.identity;
						if((t.doc.selection.start + t.doc.selection.end) > 0 && t.doc.identity != editor.identity){
							t.doc.select(0,0);
							t.doc.updateCanvas();
						}
						if(t.doc.documentRange().plainText().trim().length == 0){
							delete history.multiWordTexts[t.identity];
							rendererObj.render();
						}
					});
					var sel;
					selectMode.clearSelection();
					if (editor){
						var doc = editor.doc;
						var context = editorContextFor(doc,worldPos);
						if(clickTime - lastClick <= doubleClickThreshold){
							doc.dblclickHandler(context.node);
						} else{
							doc.mouseupHandler(context.node);
						}
						lastClick = clickTime;
						sel = {
							multiWordTexts:{}
						};
						sel.multiWordTexts[editor.identity] = editorFor(editor);
						selectMode.setSelection(sel);
					} else {
						var newEditor = createBlankText(worldPos,[{
							text:" ",
							italic:false, //carota.runs.nextInsertFormatting.italic == true,
							bold:false, //carota.runs.nextInsertFormatting.bold == true,
							underline:false, //carota.runs.nextInsertFormatting.underline == true,
							color:"#FFFFFF",//carota.runs.nextInsertFormatting.color || carota.runs.defaultFormatting.color,
							size:12 / rendererObj.getScale()//carota.runs.defaultFormatting.newBoxSize / scale()
						}]);
						var newDoc = newEditor.doc;
						var editor = editorFor(newEditor);
						newDoc.select(0,1);
						console.log("created",newEditor,newDoc);
						history.multiWordTexts[newEditor.identity] = newEditor;
						sel = {multiWordTexts:{}};
						sel.multiWordTexts[newEditor.identity] = history.multiWordTexts[newEditor.identity];
						selectMode.setSelection(sel);
						editor = newEditor;
						var node = newDoc.byOrdinal(0);
						newDoc.mousedownHandler(node);
						newDoc.mouseupHandler(node);
					}
					editor.doc.invalidateBounds();
					editor.doc.isActive = true;
				};
				registerPositionHandlers(down,move,up);
			},
			deactivate:function(){
				unregisterPositionHandlers();
				_.each(history.multiWordTexts,function(t){
						t.doc.isActive = false;
						if(t.doc.documentRange().plainText().trim().length == 0){
								delete history.multiWordTexts[t.identity];
						}
				});
				/*Necessary to ensure that no carets or marquees remain on the editors*/
				selectMode.clearSelection();
				rendererObj.render();
				modeChanged(noneMode);
			}
		};
	})();

	var availableModes = [noneMode,drawMode,eraseMode,panMode,zoomMode,selectMode,richTextMode];
	var currentMode = noneMode;
	return {
		boardElem:boardDiv,
		renderer:rendererObj,
		render:function(){
			if (rendererObj !== undefined){
				rendererObj.render();
			}
		},
		getMode:function(){return currentMode;},
		getAvailableModes:function(){
			return availableModes;
		},
		setDimensions:function(dims){
			if (rendererObj !== undefined){
				rendererObj.setDimensions(dims);
			}
		},
		setMode:function(mode){
			if (mode !== undefined && "activate" in mode){
				mode.activate();
			}
		},
		getSelected:function(){
			return selected;
		},
		onSelectionChanged:function(f){
			selectionChangedOuter = f;
		},
		onModeChanged:function(f){
			modeChanged = f;
		},
		onStatistic:function(f){
			statistic = f;
		},
		onError:function(f){
			errorFunc = f;
		},
		onViewboxChanged:function(f){
			viewboxChanged = f;
		},
		onScaleChanged:function(f){
			scaleChanged = f;
		},
		onDimensionsChanged:function(f){
			dimensionsChanged = f;
		},
		onRenderStarting:function(f){
			renderStarting = f;
		},
		onRenderComplete:function(f){
			renderComplete = f;
		},
		onPreRenderItem:function(f){
			//preRenderItem takes an item and a canvasContext and returns whether to continue rendering the item
			preRenderItem = f;
		},
		onPostRenderItem:function(f){
			//postRenderItem takes an item and a canvasContext
			postRenderItem = f;
		},
		onPreSelectItem:function(f){
			preRenderItem = f;
		},
		onPostSelectItem:function(f){
			postRenderItem = f;
		},
		onPreDeleteItem:function(f){
			preDeleteItem = f;
		},
		onPostDeleteItem:function(f){
			postDeleteItem = f;
		},
		getHistory:function(){
			return history;
		},
		setHistory:setHistoryFunc,
		onHistoryChanged:function(f){
			historyChanged = f;
		},
		onStanzaAdded:function(f){
			stanzaAdded = f;
		},
		onStanzaAvailable:function(f){
			stanzaAvailable = f;
		},
		addStanza:addStanzaFunc,	
		setImageSourceCalculationFunction:function(f){
			setImageSourceCalcFunc = f;
		},
		setVideoSourceCalculationFunction:function(f){
			setVideoSourceCalcFunc = f;
		},
		getZoomController:function(){return Zoom},
		getPanController:function(){return Pan},
		alertSnapshot:function(){
			var dims = rendererObj.getDimensions();
			var win = window.open();
			var iframe = $("<iframe/>",{
				src:rendererObj.getDataURI(),
				allowfullscreen:true,
				frameborder:0,
				style:"border:0; top:0px; left:0px; bottom:0px; right:0px; width:100%; height:100%;"
			});
			win.document.write(iframe[0].outerHTML);
		}
	};
};
