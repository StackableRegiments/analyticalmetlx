var board = function(options){
	var getPref = function(name,defaultValue){
		return (options != undefined && name in options) ? options[name] : defaultValue;
	}
	var canvasElement = $("<canvas/>");
	var boardContext = canvasElement[0].getContext("2d");

	var textInputHost = $("<div/>");//$("#textInputInvisibleHost");

	var boardContent = {
			images:{},
			highlighters:{},
			texts:{},
			multiWordTexts:{},
			inks:{}
	};

	var currentSlide = undefined;
	var username = "";
	var isInteractive = true;

	var pressureSimilarityThreshold = getPref("pressureSimilarityThreshold",32),
    viewboxX = 0,
    viewboxY = 0,
    viewboxWidth = 80,
    viewboxHeight = 60,
    contentOffsetX = 0,
    contentOffsetY = 0,
    boardWidth = 0,
    boardHeight = 0;
	
	var renderOffsetX = 0;
	var renderOffsetY = 0;
	var loadSlidesAtNativeZoom;
	var startMark;
	var requestedViewboxX = 0;
	var requestedViewboxY = 0;
	var requestedViewboxWidth = 320;
	var requestedViewboxHeight = 240;
	var precision = getPref("inkCoordinatePrecision",Math.pow(10,3));
	var lineDrawingThreshold = getPref("lineDrawingThreshold",25);

	var maxX = getPref("maxX",2147483647);
	var maxY = getPref("maxY",2147483647);
	var visibleBounds = [];

	var proportion = function(width,height){
    var targetWidth = boardWidth;
    var targetHeight = boardHeight;
    return (width / height) / (targetWidth / targetHeight);
	};
	var scaleScreenToWorld = function(i){
    var p = proportion(boardWidth,boardHeight);
    var scale;
    if(p > 1){//Viewbox wider than board
        scale = viewboxWidth / boardWidth;
    }
    else{//Viewbox narrower than board
        scale = viewboxHeight / boardHeight;
    }
    return i * scale;
	};
	var scaleWorldToScreen = function(i){
    var p = proportion(boardWidth,boardHeight);
    var scale;
    if(p > 1){//Viewbox wider than board
        scale = viewboxWidth / boardWidth;
    }
    else{//Viewbox narrower than board
        scale = viewboxHeight / boardHeight;
    }
    return i / scale;
	};

	var screenToWorld = function(x,y){
    var worldX = scaleScreenToWorld(x) + viewboxX;
    var worldY = scaleScreenToWorld(y) + viewboxY;
    return {x:worldX,y:worldY};
	}
	var worldToScreen = function(x,y){
    var screenX = scaleWorldToScreen(x - viewboxX);
    var screenY = scaleWorldToScreen(y - viewboxY);
    return {x:screenX,y:screenY};
	};

	var Colors = (function(){
    var colors = [
        ["black","#000000",255],
        ["red","#ff0000",255],
        ["blue","#0000ff",255],
        ["cyan","#00ffff",255],
        ["green","#00ff00",255],
        ["white","#ffffff",255],
        ["dark grey","#666666",255],
        ["yellow","#ffff00",255],
        ["magenta","#ff00ff",255]
    ];
    var defaultColor = colors[0];
    var toHex = function(inputNumber){
        n = parseInt(inputNumber,10);
        if (isNaN(n)) return "00";
        nValue = Math.max(0,Math.min(n,255));
        var toHexChar = function(inputN){
            internalN = Math.max(0,Math.min(inputN,15));
            return "0123456789ABCDEF".charAt(internalN);
        }
        return (toHexChar((n-n%16)/16) + toHexChar(n%16)).toLowerCase();
    };
    var get8BitHexFromString = function(string,position){
        return parseInt(string.substring(position,position+2).toUpperCase(),16);
    }
    var getAllNamedColorsFunction = function(){
        return _.map(colors,function(color){
            return {
                name:color[0],
                rgb:color[1],
                alpha:color[2],
                red:get8BitHexFromString(color[1],1),
                green:get8BitHexFromString(color[1],3),
                blue:get8BitHexFromString(color[1],5)
            };
        });
    };
    var getNameForColorFunction = function(color){
        var rgb = color[0];
        var opacity = color[1];
        var lookedUpName = _.find(colors,function(c){return (c[1] == rgb && c[2] == opacity);});
        if (lookedUpName){
            return lookedUpName[0];
        } else {
            return "#"+toHex(opacity)+rgb.substring(1,7);
        }
    };
    var getColorForNameFunction = function(name){
        if (name.indexOf("#") == 0 && name.length == 7){
            var a = 255;
            var r = get8BitHexFromString(name,1);
            var g = get8BitHexFromString(name,3);
            var b = get8BitHexFromString(name,5);
            return [toHex(r)+toHex(g)+toHex(b),a];
        } else if (name.indexOf("#") == 0 && name.length == 9){
            var a = get8BitHexFromString(name,1);
            var r = get8BitHexFromString(name,3);
            var g = get8BitHexFromString(name,5);
            var b = get8BitHexFromString(name,7);
            return [toHex(r)+toHex(g)+toHex(b),a];
        } else {
            var color = _.find(colors,function(c){return (c[0] == name);});
            if (color){
                return [color[1],color[2]];
            } else {
                return [defaultColor[1],defaultColor[2]];
            }
        }
    };
    return {
        getAllNamedColors:getAllNamedColorsFunction,
        getNameForColor:getNameForColorFunction,
        getColorForName:getColorForNameFunction
    }
	})();

	var Fonts = (function(){
    var families = [
			"sans-serif",
			"serif"
    ];
    var sizes = [12,14,16,18,20,30,42,60];
    return {
			getAllFamilies:function(){return _.map(families,function(f){return f;})},
			getAllSizes:function(){return _.map(sizes,function(s){return s;})}
    };
	})();

	var Brushes = (function(){
    var brushes = [
			{
				id:1,
				width:2.0,
				color:"#000000",
				isHighlighter:false
			},
			{
				id:2,
				width:5.0,
				color:"#FF0000",
				isHighlighter:false
			},
			{
				id:3,
				width:8.0,
				color:"#00FF00",
				isHighlighter:false
			},
			{
				id:4,
				width:10.0,
				color:"#666666",
				isHighlighter:false
			},
			{
				id:5,
				width:30.0,
				color:"#FFFF00",
				isHighlighter:true
			}
    ];
    var brushSizes = [2,4,5,8,10,16,30];
    return {
			getDefaultBrushes:function(){return _.map(brushes,function(b){return b;})},
			getAllBrushSizes:function(){return _.map(brushSizes,function(b){return b;})}
    };
	})();

	var Progress = (function(){
    return {
        call:function(key,args){
            args = args || [];
            $.each(Progress[key],function(k,f){
                try{
                    f.apply(f,args);
                }
                catch(e){
                    console.log("exception",key,k,e);
                }
            });
        },
        onPrivacyChanged:{},
        onConversationJoin:{},
        onSelectionChanged:{},
        onBoardContentChanged:{},
        onViewboxChanged:{},
        onLayoutUpdated:{},
        postRender:{},
        historyReceived:{},
        stanzaReceived:{},
        currentConversationJidReceived:{},
        currentSlideJidReceived:{},
        conversationDetailsReceived:{},
        newConversationDetailsReceived:{},
        conversationsReceived:{},
        syncMoveReceived:{},
        userGroupsReceived:{},
        usernameReceived:{},
        userOptionsReceived:{}
    }
	})();
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

	var registerPositionHandlers = function(contexts,down,move,up){
    var isDown = false;
    var modifiers = function(e,isErasing){
        return {
            shift:e.shiftKey,
            ctrl:e.ctrlKey,
            alt:e.altKey,
            eraser:isErasing
        }
    }
    $.each(contexts,function(i,_context){
        var context = $(_context);//Might have to rewrap single jquerys
        var offset = function(){
            return context.offset();
        }
        context.css({"touch-action":"none"});
        var isGesture = false;
        var trackedTouches = {};
        var updatePoint = function(pointerEvent){
            var pointId = pointerEvent.originalEvent.pointerId;
            var isEraser = pointerEvent.originalEvent.pointerType == "pen" && pointerEvent.originalEvent.button == 5;
            var o = offset();
            var x = pointerEvent.pageX - o.left;
            var y = pointerEvent.pageY - o.top;
            var z = pointerEvent.originalEvent.pressure || 0.5;
            var worldPos = screenToWorld(x,y);
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
            trackedTouches[pointId] = pointItem;
            if (_.size(trackedTouches) > 1){
                if (isGesture == false){
                    _.each(trackedTouches,function(series){
                        series.points = [_.last(series.points)];
                    });
                }
                isGesture = true;
            }
            return {
                "pointerType":pointerEvent.pointerType,
                "eraser":pointItem.eraser,
                "x":x,
                "y":y,
                "z":z,
                "worldPos":worldPos
            };
        };
        var releasePoint = function(pointerEvent){
            var pointId = pointerEvent.originalEvent.pointerId;
            var isEraser = pointerEvent.originalEvent.pointerType == "pen" && pointerEvent.originalEvent.button == 5;
            var o = offset();
            var x = pointerEvent.pageX - o.left;
            var y = pointerEvent.pageY - o.top;
            var z = pointerEvent.originalEvent.pressure || 0.5;
            var worldPos = screenToWorld(x,y);
            delete trackedTouches[pointId];
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
        }
        if (detectPointerEvents()){
            var performGesture = function(){
                if (_.size(trackedTouches) > 1){
                    takeControlOfViewbox();

                    var calculationPoints = _.map(_.filter(trackedTouches,function(item){return _.size(item.points) > 0;}),function(item){
                        var first = _.first(item.points);
                        var last = _.last(item.points);
                        return [first,last];
                    });
                    trackedTouches = {};
                    var xDelta = _.meanBy(calculationPoints,function(i){return i[0].x - i[1].x;});
                    var yDelta = _.meanBy(calculationPoints,function(i){return i[0].y - i[1].y;});

                    Pan.translate(scaleWorldToScreen(xDelta),scaleWorldToScreen(yDelta));

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

                    var previousScale = (prevXScale + prevYScale)       / 2;
                    var currentScale = (xScale + yScale)        / 2;
                    Zoom.scale(previousScale / currentScale);
                }
            };
            context.bind("pointerdown",function(e){
                var point = updatePoint(e);
                e.preventDefault();
                WorkQueue.pause();
                if (_.size(trackedTouches) == 1 && !isGesture){
                    isDown = true;
                    down(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
                }
            });
            context.bind("pointermove",function(e){
                var point = updatePoint(e);
                e.preventDefault();
                if (e.originalEvent.pointerType == e.POINTER_TYPE_TOUCH || e.originalEvent.pointerType == "touch" && _.size(trackedTouches) > 1){
                    performGesture();
                }
                if (_.size(trackedTouches) == 1 && !isGesture){
                    if(isDown){
                        move(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
                    }
                }
            });
            context.bind("pointerup",function(e){
                var point = releasePoint(e);
                WorkQueue.gracefullyResume();
                e.preventDefault();
                if(isDown && !isGesture){
                    up(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
                }
                isDown = false;
            });
            var pointerOut = function(x,y){
                trackedTouches = {};
                WorkQueue.gracefullyResume();
                var worldPos = screenToWorld(x,y);
                var worldX = worldPos.x;
                var worldY = worldPos.y;
                if(worldX < viewboxX){
                    takeControlOfViewbox();
                    Extend.left();
                }
                else if(worldX >= (viewboxX + viewboxWidth)){
                    takeControlOfViewbox();
                    Extend.right();
                }
                else if(worldY < viewboxY){
                    takeControlOfViewbox();
                    Extend.up();
                }
                else if(worldY >= (viewboxY + viewboxHeight)){
                    takeControlOfViewbox();
                    Extend.down();
                }
                isDown = false;
            }
            var pointerClose = function(e){
                var point = releasePoint(e);
                WorkQueue.gracefullyResume();
                e.preventDefault();
                if(isDown){
                    pointerOut(e.offsetX,e.offsetY);
                }
                isDown = false;
            };
            context.bind("pointerout",pointerClose);
            context.bind("pointerleave",pointerClose);
            context.bind("pointercancel",pointerClose);

        } else {
            context.bind("mousedown",function(e){
                WorkQueue.pause();
                var o = offset();
                e.preventDefault();
                isDown = true;
                var x = e.pageX - o.left;
                var y = e.pageY - o.top;
                var z = 0.5;
                var worldPos = screenToWorld(x,y);
                down(x,y,z,worldPos,modifiers(e));
            });
            context.bind("mousemove",function(e){
                if(isDown){
                    var o = offset();
                    e.preventDefault();
                    var x = e.pageX - o.left;
                    var y = e.pageY - o.top;
                    var z = 0.5;
                    move(x,y,z,screenToWorld(x,y),modifiers(e));
                }
            });
            context.bind("mouseup",function(e){
                WorkQueue.gracefullyResume();
                e.preventDefault();
                if(isDown){
                    var o = offset();
                    var x = e.pageX - o.left;
                    var y = e.pageY - o.top;
                    var z = 0.5;
                    var worldPos = screenToWorld(x,y);
                    up(x,y,z,worldPos,modifiers(e));
                }
                isDown = false;
            });
            var mouseOut = function(x,y){
                WorkQueue.gracefullyResume();
                var worldPos = screenToWorld(x,y);
                var worldX = worldPos.x;
                var worldY = worldPos.y;
                if(worldX < viewboxX){
                    takeControlOfViewbox();
                    Extend.left();
                }
                else if(worldX >= (viewboxX + viewboxWidth)){
                    takeControlOfViewbox();
                    Extend.right();
                }
                else if(worldY < viewboxY){
                    takeControlOfViewbox();
                    Extend.up();
                }
                else if(worldY >= (viewboxY + viewboxHeight)){
                    takeControlOfViewbox();
                    Extend.down();
                }
                isDown = false;
            }
            context.bind("mouseout",function(e){
                WorkQueue.gracefullyResume();
                e.preventDefault();
                if(isDown){
                    mouseOut(e.offsetX,e.offsetY);
                }
                isDown = false;
            });
            var touches;
            var masterTouch;
            var prevPos;
            var touchesToWorld = function(touches){
                return touches.map(function(t){
                    return screenToWorld(t.x,t.y);
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
                    var worldPos = screenToWorld(t.x,t.y);
                    isDown = true;
                    var z = 0.5;
                    down(t.x,t.y,z,worldPos,modifiers(e));
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
                    if(isDown){
                        var t = touches[0];
                        var z = 0.5;
                        move(t.x,t.y,z,screenToWorld(t.x,t.y),modifiers(e));
                    }
                    break;
                default:
                    var pos = averagePos(touches);
                    var xDelta = pos.x - prevPos.x;
                    var yDelta =  pos.y - prevPos.y;
                    prevPos = pos;
                    takeControlOfViewbox();
                    Pan.translate(-1 * xDelta,-1 * yDelta);
                    break;
                }
            });
            context.bind("touchend",function(e){
                WorkQueue.gracefullyResume();
                e.preventDefault();
                if(isDown){
                    var o = offset();
                    var t = e.originalEvent.changedTouches[0];
                    var x = t.pageX - o.left;
                    var y = t.pageY - o.top;
                    var z = 0.5;
                    if(x < 0 || y < 0 || x > boardWidth || y > boardHeight){
                        mouseOut(x,y);
                    }
                    else{
                        up(x,y,z,screenToWorld(x,y),modifiers(e));
                    }
                }
                isDown = false;
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
                takeControlOfViewbox();
                Zoom.scale(previousScale / scale);
                previousScale = scale;
            });
            context.bind("gestureend",function(){
                WorkQueue.gracefullyResume();
                previousScale = 1.0;
            });
        }
    });
    return function(forceDown){
        isDown = forceDown;
    }
	}
	var aspectConstrainedDimensions = function(width,height){
    var proportion = boardHeight / boardWidth;
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
    var proportion = boardHeight / boardWidth;
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
	var copyBuffer = function(buffer){
    var tmp = document.createElement("canvas");
    tmp.width = buffer.width;
    tmp.height = buffer.height;
    tmp.getContext("2d").drawImage(buffer,
                                   0,0,buffer.width,buffer.height,
                                   0,0,buffer.width,buffer.height);
    return tmp;
	}
	var intersectRect = function(r1, r2) {//Left,top,right,bottom
//	console.log("intersectRect",r1,r2);
    if (typeof(r1) != "undefined" && typeof(r2) != "undefined"){
        return !(r2[0] > r1[2] ||
                 r2[2] < r1[0] ||
                 r2[1] > r1[3] ||
                 r2[3] < r1[1]);
    } else {
        return false;
    }
	}
	var overlapRect = function(r1,r2){
    if(!intersectRect(r1,r2)){
        return 0;
    }
    return (Math.max(r1[0], r2[0]) - Math.min(r1[2], r2[2])) * (Math.max(r1[1], r2[1]) - Math.min(r1[3], r2[3]));
	}
	var rectFromTwoPoints = function(pointA,pointB){
    var topLeft = {x:0,y:0};
    var bottomRight = {x:0,y:0};
    if (pointA.x < pointB.x){
        topLeft.x = pointA.x;
        bottomRight.x = pointB.x;
    } else {
        topLeft.x = pointB.x;
        bottomRight.x = pointA.x;
    }
    if (pointA.y < pointB.y){
        topLeft.y = pointA.y;
        bottomRight.y = pointB.y;
    } else {
        topLeft.y = pointB.y;
        bottomRight.y = pointA.y;
    }
    return {
        left:topLeft.x,
        top:topLeft.y,
        right:bottomRight.x,
        bottom:bottomRight.y,
        width:Math.abs(bottomRight.x - topLeft.x),
        height:Math.abs(bottomRight.y - topLeft.y)
    };
	}
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
	var clampToView = function(point) {
    var x = point.x;
    var y = point.y;
    if (point.x < 0){
        x = 0;
    }
    if (point.x > boardWidth){
        x = boardWidth;
    }
    if (point.y < 0){
        y = 0;
    }
    if (point.y > boardHeight){
        y = boardHeight;
    }
    return {
        x:x,
        y:y
    };
	}
	var drawSelectionBounds = function(item){
    var origin = clampToView(worldToScreen(item.bounds[0],item.bounds[1]));
    var end = clampToView(worldToScreen(item.bounds[2],item.bounds[3]));
    var originalWidth = end.x-origin.x;
    var originalHeight = end.y-origin.y;
    var bd = clampToView(item.bounds[0],item.bounds[1],item.bounds[2],item.bounds[3]);
    $("#selectionAdorner").prepend($("<div />").addClass("selectionAdorner").css({
        left:origin.x,
        top:origin.y,
        width:originalWidth,
        height:originalHeight
    }).data("originalWidth",originalWidth).data("originalHeight",originalHeight));
	}


	var unregisterPositionHandlers = function(context){
			$.each("pointerdown pointermove pointerup pointerout pointerleave pointercancel mouseup mousemove mousedown touchstart touchmove touchend touchcancelled mouseout touchleave gesturechange gesturestart".split(" "),function(i,evt){
					context.unbind(evt);
			});
			WorkQueue.gracefullyResume();
	}

	var incorporateBoardBounds = function(bounds){
    if (!isNaN(bounds[0])){
        boardContent.minX = Math.min(boardContent.minX,bounds[0]);
    }
    if (!isNaN(bounds[1])){
        boardContent.minY = Math.min(boardContent.minY,bounds[1]);
    }
    if (!isNaN(bounds[2])){
        boardContent.maxX = Math.max(boardContent.maxX,bounds[2]);
    }
    if (!isNaN(bounds[3])){
        boardContent.maxY = Math.max(boardContent.maxY,bounds[3]);
    }
    boardContent.width = boardContent.maxX - boardContent.minX;
    boardContent.height = boardContent.maxY - boardContent.minY;
	}
	var mergeBounds = function(b1,b2){
    var b = {};
    b.minX = Math.min(b1[0],b2[0]);
    b.minY = Math.min(b1[1],b2[1]);
    b.maxX = Math.max(b1[2],b2[2]);
    b.maxY = Math.max(b1[3],b2[3]);
    b.width = b.maxX - b.minX;
    b.height = b.maxY - b.minY;
    b.centerX = b.minX + b.width / 2;
    b.centerY = b.minY + b.height / 2;
    b[0] = b.minX;
    b[1] = b.minY;
    b[2] = b.maxX;
    b[3] = b.maxY;
    return b;
	};
	var boardLimit = 10000;
	var isUsable = function(element){
    var boundsOk = !(_.some(element.bounds,function(p){
        return isNaN(p) || p > boardLimit || p < -boardLimit;
    }));
    var sizeOk = "size" in element? !isNaN(element.size) : true
    var textOk =  "text" in element? element.text.length > 0 : true;
    return boundsOk && sizeOk && textOk;
	};
	var leftPoint = function(xDelta,yDelta,l,x2,y2,bulge){
    var px = yDelta * l * bulge;
    var py = xDelta * l * -bulge;
    return {
        x:px + x2,
        y:py + y2
    }
	}
	var rightPoint = function(xDelta,yDelta,l,x2,y2,bulge){
    var px = yDelta * l * -bulge;
    var py = xDelta * l * bulge;
    return {
        x:px + x2,
        y:py + y2
    }
	}

	var WorkQueue = (function(){
    var isAbleToWork = true;
    var work = [];
    var blitNeeded = false;
    var popState = function(){
        var f = work.pop();
        if(f){
            blitNeeded = blitNeeded || f();
            popState();
        }
        else{
            if(blitNeeded){
                blit();
                blitNeeded = false;
            }
						if ("Conversations" in window){
							Conversations.updateThumbnail(Conversations.getCurrentSlideJid());
						}
        }
    };
    var pauseFunction = function(){
        stopResume();
        canWorkFunction(false);
    };
    var canWorkFunction = function(state){
        isAbleToWork = state;
        if(state){
            popState();
        }
    };
    var stopResume = function(){
        if (gracefullyResumeTimeout){
            window.clearTimeout(gracefullyResumeTimeout);
            gracefullyResumeTimeout = undefined;
        }
    }
    var gracefullyResumeDelay = 1000;
    var gracefullyResumeTimeout = undefined;
    var gracefullyResumeFunction = function(){
        stopResume();
        gracefullyResumeTimeout = setTimeout(function(){canWorkFunction(true);},gracefullyResumeDelay);
    };
    return {
        pause:pauseFunction,
        gracefullyResume:gracefullyResumeFunction,
        enqueue:function(func){//A function returning a bool, blit needed or not.
            if(isAbleToWork){
                if(func()){
                    blit();
                };
            }
            else{
                work.push(function(){
                    return func();
                });
            }
        }
    };
	})();
	var Pan = {
    pan:function(xDelta,yDelta){
        var xScale = viewboxWidth / boardWidth;
        var yScale = viewboxHeight / boardHeight;
        /*
         viewboxX -= xDelta * xScale;
         viewboxY -= yDelta * yScale;
         blit();
         */
        TweenController.panViewboxRelative(xDelta * xScale, yDelta * yScale);
    },
    translate:function(xDelta,yDelta){
        var xScale = viewboxWidth / boardWidth;
        var yScale = viewboxHeight / boardHeight;
        /*
         viewboxX -= xDelta * xScale;
         viewboxY -= yDelta * yScale;
         blit();
         */
        TweenController.translateViewboxRelative(xDelta * xScale, yDelta * yScale);
    }
	}
	var Zoom = (function(){
    var zoomFactor = 1.2;
    var maxZoomOut = 3;
    var maxZoomIn = 0.1;
    var getMaxViewboxSizeFunction = function(){
        return {
            width:boardContent.width * maxZoomOut,
            height:boardContent.height * maxZoomOut
        }
    };
    var getMinViewboxSizeFunction = function(){
        return {
            width:boardWidth * maxZoomIn,
            height:boardHeight * maxZoomIn
        }
    };
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
    return {
        scale:function(scale,ignoreLimits){
            var requestedWidth = viewboxWidth * scale;
            var requestedHeight = viewboxHeight * scale;
            if(!ignoreLimits){
                var constrained = constrainRequestedViewboxFunction({height:requestedHeight,width:requestedWidth});
                requestedWidth = constrained.width;
                requestedHeight = constrained.height;
            }
            var ow = viewboxWidth;
            var oh = viewboxHeight;
            var xDelta = (ow - requestedWidth) / 2;
            var yDelta = (oh - requestedHeight) / 2;
            var finalX = xDelta + viewboxX;
            var finalY = yDelta + viewboxY;
            TweenController.scaleAndTranslateViewbox(finalX,finalY,requestedWidth,requestedHeight);
        },
        zoom:function(scale,ignoreLimits,onComplete){
            var requestedWidth = viewboxWidth * scale;
            var requestedHeight = viewboxHeight * scale;
            if(!ignoreLimits){
                var constrained = constrainRequestedViewboxFunction({height:requestedHeight,width:requestedWidth});
                requestedWidth = constrained.width;
                requestedHeight = constrained.height;
            }
            var ow = viewboxWidth;
            var oh = viewboxHeight;
            var wDelta = requestedWidth - ow;
            var hDelta = requestedHeight - oh;
            var xDelta = -1 * (wDelta / 2);
            var yDelta = -1 * (hDelta / 2);
            TweenController.zoomAndPanViewboxRelative(xDelta,yDelta,wDelta,hDelta,onComplete);
        },
        out:function(){
            Zoom.zoom(zoomFactor);
        },
        "in":function(){
            Zoom.zoom(1 / zoomFactor);
        },
        constrainRequestedViewbox:constrainRequestedViewboxFunction
    };
	})();
	var TweenController = (function(){
    var updateRequestedPosition = function(){
        requestedViewboxX = viewboxX;
        requestedViewboxY = viewboxY;
        requestedViewboxWidth = viewboxWidth;
        requestedViewboxHeight = viewboxHeight;
    };
    var instantAlterViewboxFunction = function(finalX,finalY,finalWidth,finalHeight,onComplete,shouldAvoidUpdatingRequestedViewbox){
				if (isNaN(finalX) || isNaN(finalY) || isNaN(finalWidth) || isNaN(finalHeight)){
                if (onComplete){
                    onComplete();
                }
					return;
				}
        if(tween){
            //console.log("instantAlterViewboxFunc stopped tween");
            tween.stop();
        }
        tween = false;
        viewboxX = finalX;
        viewboxY = finalY;
        viewboxWidth = finalWidth;
        viewboxHeight = finalHeight;
				//console.log("instantTweening:",finalX,finalY,finalWidth,finalHeight);
        if (!shouldAvoidUpdatingRequestedViewbox){
            updateRequestedPosition();
        }
        clearBoard();
        render(boardContent);
        if (onComplete){
            onComplete();
        }
        Progress.call("onViewboxChanged",[finalX,finalY,finalWidth,finalHeight]);
    };
    var tween;
    var easingAlterViewboxFunction = function(finalX,finalY,finalWidth,finalHeight,onComplete,shouldAvoidUpdatingRequestedViewbox,notFollowable){
				if (isNaN(finalX) || isNaN(finalY) || isNaN(finalWidth) || isNaN(finalHeight)){
                if (onComplete){
                    onComplete();
                }
					return;
				}
        var interval = 300;//milis
        var startX = viewboxX;
        var startY = viewboxY;
        var startWidth = viewboxWidth;
        var startHeight = viewboxHeight;
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
				//console.log("startingTween:",startX,startY,startWidth,startHeight,xDelta,yDelta,widthDelta,heightDelta);
        tween = new TWEEN.Tween({x:0,y:0,w:0,h:0})
            .to({x:xDelta,y:yDelta,w:widthDelta,h:heightDelta}, interval)
            .easing(TWEEN.Easing.Quadratic.Out)
            .onUpdate(function(){
                viewboxX = startX + this.x;
                viewboxY = startY + this.y;
                viewboxWidth = startWidth + this.w;
                viewboxHeight = startHeight + this.h;
            }).onComplete(function(){
                tween = false;
                if (!shouldAvoidUpdatingRequestedViewbox){
                    updateRequestedPosition();
                }
                if (onComplete){
                    onComplete();
                }
                Progress.call("onViewboxChanged",[finalX,finalY,finalWidth,finalHeight]);
            }).start();
        var update = function(t){
            if (tween){
                requestAnimationFrame(update);
                TWEEN.update();
                clearBoard();
                render(boardContent);
            }
        };
        requestAnimationFrame(update);
    };
    var panViewboxFunction = function(xDelta,yDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
        return easingAlterViewboxFunction(xDelta,yDelta,viewboxWidth,viewboxHeight,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var translateViewboxFunction = function(xDelta,yDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
        return instantAlterViewboxFunction(xDelta,yDelta,viewboxWidth,viewboxHeight,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var panViewboxRelativeFunction = function(xDelta,yDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
        return easingAlterViewboxFunction(xDelta + viewboxX,yDelta + viewboxY,viewboxWidth,viewboxHeight,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var translateViewboxRelativeFunction = function(xDelta,yDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
        return instantAlterViewboxFunction(xDelta + viewboxX,yDelta + viewboxY,viewboxWidth,viewboxHeight,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var zoomAndPanViewboxFunction = function(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox,notFollowable){
        return easingAlterViewboxFunction(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox,notFollowable);
    };
    var zoomAndPanViewboxRelativeFunction = function(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
        return easingAlterViewboxFunction(xDelta + viewboxX,yDelta + viewboxY,widthDelta + viewboxWidth,heightDelta + viewboxHeight,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var scaleAndTranslateViewboxFunction = function(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
        return instantAlterViewboxFunction(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox);
    };
    var scaleAndTranslateViewboxRelativeFunction = function(xDelta,yDelta,widthDelta,heightDelta,onComplete,shouldAvoidUpdatingRequestedViewbox){
        return instantAlterViewboxFunction(xDelta + viewboxX,yDelta + viewboxY,widthDelta + viewboxWidth,heightDelta + viewboxHeight,onComplete,shouldAvoidUpdatingRequestedViewbox);
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
				changeViewbox:instantAlterViewboxFunction,
				easeViewbox:easingAlterViewboxFunction
    }
	})();
	var Extend = (function(){
    var factor = 0.6;
    var xExtension = function(){
        return Math.floor(viewboxWidth * factor);
    }
    var yExtension = function(){
        return Math.floor(viewboxHeight * factor);
    }
    return {
        up:function(){
            TweenController.panViewboxRelative(0,-yExtension());
        },
        down:function(){
            TweenController.panViewboxRelative(0,yExtension());
        },
        left:function(){
            TweenController.panViewboxRelative(-xExtension(),0);
        },
        right:function(){
            TweenController.panViewboxRelative(xExtension(),0);
        },
        shift:TweenController.panViewboxRelative,
        center:function(x,y,onComplete){
            var targetX = x - viewboxWidth / 2;
            var targetY = y - viewboxHeight / 2;
            TweenController.panViewboxRelative(targetX - viewboxX,targetY - viewboxY,onComplete);
        }
    }
	})();

	var historyReceivedFunction = function(json){
    try{
			currentSlide = parseInt(json.jid);
        var historyDownloadedMark, prerenderInkMark, prerenderImageMark, prerenderHighlightersMark,prerenderTextMark,imagesLoadedMark,renderMultiWordMark, historyDecoratorsMark, blitMark;
        historyDownloadedMark = Date.now();
        boardContent = json;
        boardContent.minX = 0;
        boardContent.minY = 0;
        boardContent.maxX = boardWidth;
        boardContent.maxY = boardHeight;
        $.each(boardContent.inks,function(i,ink){
            prerenderInk(ink);
        });
        prerenderInkMark = Date.now();
        $.each(boardContent.highlighters,function(i,ink){
            prerenderInk(ink);
        });
        prerenderHighlightersMark = Date.now();
        $.each(boardContent.texts,function(i,text){
            if(isUsable(text)){
                prerenderText(text);
            }
            else{
                delete boardContent.texts[text.id];
            }
        });
        prerenderTextMark = Date.now();
				_.each(boardContent.multiWordTexts,function(text,i){
            var editor = Modes.text.editorFor(text).doc;
            editor.load(text.words);
            text.bounds = editor.calculateBounds();
            incorporateBoardBounds(text.bounds);
        });
        renderMultiWordMark = Date.now();

        boardContent.width = boardContent.maxX - boardContent.minX;
        boardContent.height = boardContent.maxY - boardContent.minY;
        var startRender = function(){
					console.log("rendering:",json,boardContent,boardContext);
            imagesLoadedMark = Date.now();
            Progress.call("historyReceived",[json]);
            historyDecoratorsMark = Date.now();
            if(boardContent.minX == Infinity){
                boardContent.minX = 0;
            }
            if (boardContent.minY == Infinity){
                boardContent.minY = 0;
            }
            if(loadSlidesAtNativeZoom){
                requestedViewboxX = 0;
                requestedViewboxY = 0;
                requestedViewboxWidth = boardWidth;
                requestedViewboxHeight = boardHeight;
                IncludeView.default();
            }
            else{
                requestedViewboxX = boardContent.minX;
                requestedViewboxY = boardContent.minY;
                requestedViewboxWidth = boardContent.width;
                requestedViewboxHeight = boardContent.height;
                IncludeView.default();
            }
						console.log("startRender",requestedViewboxX,requestedViewboxY,requestedViewboxWidth,requestedViewboxHeight);
						clearBoard();
            render(boardContent);
            blitMark = Date.now();
						Progress.call("postRender",[boardContent]);
        }
        if(_.keys(boardContent.images).length == 0){
            _.defer(startRender);
        }
        else{
            var loaded = 0;
            var limit = _.keys(boardContent.images).length;
            $.each(boardContent.images,function(i,image){
                image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
                incorporateBoardBounds(image.bounds);
                var dataImage = new Image();
                image.imageData = dataImage;
                var url = calculateImageSource(image,true);
                dataImage.onload = function(data){
                    var shouldReCalcBounds = false;
                    if(image.width == 0){
                        image.width = dataImage.naturalWidth;
                        shouldReCalcBounds = true;
                    }
                    if(image.height == 0){
                        image.height = dataImage.naturalHeight;
                        shouldReCalcBounds = true;
                    }
                    if(shouldReCalcBounds){
                        image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
                        incorporateBoardBounds(image.bounds);
                    }
                    loaded += 1;
                    prerenderImage(image);
                    if(loaded >= limit){
                        _.defer(startRender);
                    }
                };
                dataImage.onerror = function(e){
                    console.log("Data image load error",image,e);
                    limit -= 1;
                    console.log(sprintf("Preloaded %s/%s images",loaded,limit));
                    if(loaded >= limit){
                        _.defer(startRender);
                    }
                }
                dataImage.src = url;
            });
        }
    }
    catch(e){
        console.log("receiveHistory exception",e);
    }
		if (!isInteractive){
			//projector mode should always start viewing the entire slide
			zoomToFit();
		}
	};

	var determineCanvasConstants = _.once(function(){
		return {x:maxX,y:maxY};
	});
	var determineScaling = function(inX,inY){
    var outputX = inX;
    var outputY = inY;
    var outputScaleX = 1.0;
    var outputScaleY = 1.0;
    var canvasConstants = determineCanvasConstants();
    var maxX = canvasConstants.x;
    var maxY = canvasConstants.y;
    if (inX > maxX){
        outputScaleX = maxX / inX;
        outputX = inX * outputScaleX;
    }
    if (inY > maxY){
        outputScaleY = maxY / inY;
        outputY = inY * outputScaleY;
    }
    return {
        width:outputX,
        height:outputY,
        scaleX:outputScaleX,
        scaleY:outputScaleY,
    };
	}
	var prerenderInk = function(ink){
    if(!isUsable(ink)){
        if(ink.identity in boardContent.inks){
            delete boardContent.inks[ink.identity];
        }
        if(ink.identity in boardContent.highlighters){
            delete boardContent.highlighters[ink.identity];
        }
        return false;
    }
    calculateInkBounds(ink);
    incorporateBoardBounds(ink.bounds);
    var canvas = $("<canvas />")[0];
    ink.canvas = canvas;
    var context = canvas.getContext("2d");
    var privacyOffset = 0;
    var isPrivate = ink.privacy.toUpperCase() == "PRIVATE";
    if(isPrivate){
        privacyOffset = 3;
    }
    var rawWidth = ink.bounds[2] - ink.bounds[0] + ink.thickness + privacyOffset * 2;
    var rawHeight = ink.bounds[3] - ink.bounds[1] + ink.thickness + privacyOffset * 2;

    var scaleMeasurements = determineScaling(rawWidth,rawHeight);
    canvas.width = scaleMeasurements.width;
    canvas.height = scaleMeasurements.height;
    $(canvas).css({
        width:px(rawWidth),
        height:px(rawHeight)
    });
    var rawPoints = ink.points;
    var points = [];
    for (p = 0; p < rawPoints.length; p += 3){
        points.push(rawPoints[p] * scaleMeasurements.scaleX);
        points.push(rawPoints[p + 1] * scaleMeasurements.scaleY);
        points.push(rawPoints[p + 2]);
    }
    var contentOffsetX = -1 * ((ink.minX - ink.thickness / 2) - privacyOffset) * scaleMeasurements.scaleX;
    var contentOffsetY = -1 * ((ink.minY - ink.thickness / 2) - privacyOffset) * scaleMeasurements.scaleY;
    var x,y,pr,newPr,p;
    if(isPrivate){
        x = points[0] + contentOffsetX;
        y = points[1] + contentOffsetY;
        pr = points[2];
        context.lineWidth = ink.thickness + privacyOffset;
        context.strokeStyle = ink.color[0];
        context.fillStyle = ink.color[0];
        context.moveTo(x,y);
        context.beginPath();
        for(p = 0; p < points.length; p += 3){
            context.moveTo(x,y);
            x = points[p]+contentOffsetX;
            y = points[p+1]+contentOffsetY;
            context.lineTo(x,y);
        }
        context.lineCap = "round";
        context.stroke();
        context.closePath();
        context.strokeStyle = "white";
        context.fillStyle = "white";
    }
    else{
        context.strokeStyle = ink.color[0];
        context.fillStyle = ink.color[0];
    }
    x = points[0] + contentOffsetX;
    y = points[1] + contentOffsetY;
    pr = points[2];

    context.moveTo(x,y);
    context.beginPath();
    var x = points[0] + contentOffsetX;
    var y = points[1] + contentOffsetY;
    _.each(_.chunk(points,3),function(point){
        context.beginPath();
        context.moveTo(x,y);
        x = point[0] + contentOffsetX;
        y = point[1] + contentOffsetY;
        context.lineTo(x,y);
        context.lineWidth = ink.thickness * (point[2] / 256);
        context.lineCap = "round";
        context.stroke();
    });
    return true;
	}
	var alertCanvas = function(label){
		var canvas = canvasElement[0];
    var url = canvas.toDataURL();
    window.open(url,label,sprintf("width=%s, height=%s",canvas.width,canvas.height));
	}
	var round = function(n){
    return Math.round(n * precision) / precision;
	}
	var calculateImageBounds = function(image){
    image.bounds = [image.x,image.y,image.x + image.width,image.y + image.height];
	}
	function urlEncodeSlideName(slideName){
    var newSlideName = btoa(slideName);
    return newSlideName;
	}
	var calculateImageSource = function(image){
    var slide = image.privacy.toUpperCase() == "PRIVATE" ? sprintf("%s%s",image.slide,image.author) : image.slide;
    return sprintf("/proxyImageUrl/%s?source=%s",urlEncodeSlideName(slide),encodeURIComponent(image.source.trim()));
	}
	var calculateTextBounds = function(text){
    text.bounds = [text.x,text.y,text.x + text.width, text.y + (text.runs.length * text.size * 1.25)];
	}
	var calculateInkBounds = function(ink){
    var minX = Infinity;
    var minY = Infinity;
    var maxX = -Infinity;
    var maxY = -Infinity;
    var widths = [];
    var points = ink.points;
    var places = 4;
    for(var cindex = 0; cindex < points.length; cindex += 3){
        var x = round(points[cindex]);
        var y = round(points[cindex+1]);
        points[cindex] = x;
        points[cindex+1] = y;
        widths.push(points[cindex+2]);
        minX = Math.min(x,minX);
        minY = Math.min(y,minY);
        maxX = Math.max(x,maxX);
        maxY = Math.max(y,maxY);
    }
    ink.minX = minX;
    ink.minY = minY;
    ink.maxX = maxX;
    ink.maxY = maxY;
    ink.width = maxX - minX;
    ink.height = maxY - minY;
    ink.centerX = minX + ink.width / 2;
    ink.centerY = minY + ink.height / 2;
    ink.bounds=[minX,minY,maxX,maxY];
    ink.widths=widths;
	}
	var scale = function(){
    return Math.min(boardWidth / viewboxWidth, boardHeight / viewboxHeight);
	}
	var prerenderImage = function(image) {
    var canvas = $("<canvas/>")[0];
    image.canvas = canvas;
    canvas.width = image.width;
    canvas.height = image.height;
    var borderW = canvas.width * 0.10;
    var borderH = canvas.height * 0.10;
    canvas.width = image.width + borderW;
    canvas.height = image.height + borderH;
    var context = canvas.getContext("2d");
    context.drawImage(image.imageData,borderW / 2,borderH / 2,image.width, image.height);
    if(image.privacy.toUpperCase() == "PRIVATE"){
        context.globalAlpha = 0.2;
        context.fillStyle = "red";
        context.fillRect(
            0,0,
            canvas.width,
            canvas.height);
        context.globalAlpha = 1.0;
    }
    delete image.imageData;
	}
	var prerenderText = function(text){
    var canvas = $("<canvas />")[0];

    text.canvas = canvas;
    var context = canvas.getContext("2d");
    context.strokeStyle = text.color;
    context.font = text.font;
    var newline = /\n/;
    if(!text.width){
        text.width = Math.max.apply(Math,text.text.split(newline).map(
            function(subtext){
                return context.measureText(subtext).width;
            }));
    }
    var run = "";
    var yOffset = 0;
    var runs = [];
    var breaking = false;
    $.each(text.text.split(''),function(i,c){
        if(c.match(newline)){
            runs.push(""+run);
            run = "";
            return;
        }
        else if(breaking && c == " "){
            runs.push(run);
            run = "";
            return;
        }
        var w = context.measureText(run).width;
        breaking = w >= text.width - 80;
        run += c;
    });
    runs.push(run);
    runs = runs.map(function(r){
        return r.trim();
    });
    text.runs = runs;
    calculateTextBounds(text);
    var rawWidth = text.bounds[2] - text.bounds[0];
    var rawHeight = text.bounds[3] - text.bounds[1];
    var scaleMeasurements = determineScaling(rawWidth,rawHeight);
    canvas.width = scaleMeasurements.width;
    canvas.height = scaleMeasurements.height;

    text.height = rawHeight;
    if(text.privacy.toUpperCase() == "PRIVATE"){
        context.globalAlpha = 0.2;
        context.fillStyle = "red";
        context.fillRect(
            0,0,
            scaleMeasurements.width,
            scaleMeasurements.height);
        context.globalAlpha = 1.0;
    }
    context.fillStyle = text.color[0];
    context.textBaseline = "top";
    function generateTextFont(text) {
        var font = text.font;
        if(text.weight == "bold")
            font = font + ' bold';
        if(text.style == "italic")
            font = font + ' italic';

        return font;
    }

    $.each(text.runs,function(ri,run){
        var underline = function(){
            var lines = text.height/(text.size * 1.25);
            var range = _.range(text.size, text.height, text.height/lines);
            _.each(range, function(y){
                context.beginPath();
                context.strokeStyle = text.color[0];
                var underlineY = contentOffsetY + y;
                context.moveTo(contentOffsetX, underlineY);
                var underlineEndX = contentOffsetX + scaleMeasurements.width;
                context.lineTo(underlineEndX, underlineY);
                context.stroke();
            });
        };
        var _yOffset = ri * text.size * 1.25;
        context.font = generateTextFont(text);
        context.fillText(run,
                         contentOffsetX * scaleMeasurements.scaleX,
                         (contentOffsetY + _yOffset) * scaleMeasurements.scaleY,
                         scaleMeasurements.width);
        if(text.decoration == "underline")
            underline();

    });
    incorporateBoardBounds(text.bounds);
	}
	var render = function(content){
    if(content){
        var startMark = Date.now();
        var fitMark,imagesRenderedMark,highlightersRenderedMark,textsRenderedMark,richTextsRenderedMark,inksRenderedMark,renderDecoratorsMark;
        try{
            var viewBounds = [viewboxX,viewboxY,viewboxX+viewboxWidth,viewboxY+viewboxHeight];
						//console.log("viewbounds",viewboxX,viewboxY,viewboxWidth,viewboxHeight);
            visibleBounds = [];
            var scale = content.maxX / viewboxWidth;
            var renderInks = function(inks){
                if (inks != undefined){
                    $.each(inks,function(i,ink){
                        try{
                            if(intersectRect(ink.bounds,viewBounds)){
                                drawInk(ink);
                            }
                        }
                        catch(e){
                            console.log("ink render failed for",e,ink.canvas,ink.identity,ink);
                        }
                    });
                }
            }
            var renderRichTexts = function(texts){
                if(texts){
                    $.each(texts,function(i,text){
                        if(!text.bounds){
                            text.bounds = [text.x,text.y,text.x + text.width,text.y + text.height];
                        }
                        if(intersectRect(text.bounds,viewBounds)){
			    drawMultiwordText(text);
                        }
                    });
                }
            }
            var renderImmediateContent = function(){
                renderInks(content.highlighters);
                highlightersRenderedMark = Date.now();
                $.each(content.texts,function(i,text){
                    if(intersectRect(text.bounds,viewBounds)){
                        drawText(text);
                    }
                });
                textsRenderedMark = Date.now();
                renderRichTexts(content.multiWordTexts);
                richTextsRenderedMark = Date.now();
                renderInks(content.inks);
                inksRenderedMark = Date.now();
                Progress.call("postRender",[boardContent]);
                renderDecoratorsMark = Date.now();
            }
            var loadedCount = 0;
            var loadedLimit = Object.keys(content.images).length;
            //clearBoard();
						clearBoard();
            fitMark = Date.now();
            $.each(content.images,function(id,image){
                try{
                    if(intersectRect(image.bounds,viewBounds)){
                        drawImage(image);
                    }
                }
                catch(e){
                    console.log("image render failed for",e,image.identity,image);
                }
            });
            imagesRenderedMark = Date.now();
            renderImmediateContent();
        }
        catch(e){
            console.log("Render exception",e);
        }
        Progress.call("onViewboxChanged");
    }
	}
	var blit = function(){
		try {
			render(boardContent);
		} catch(e){
			console.log("exception in render:",e);
		}
	};
	var pica = function(value){
    return value / 128;
	}
	var unpica = function(value){
    return Math.floor(value * 128);
	}
	var px = function(value){
    return sprintf("%spx",value);
	}
	var unpix = function(str){
    return str.slice(0,str.length-2);
	}
	var clearBoard = function(){
		boardContext.clearRect(0,0,boardWidth,boardHeight);
	}
	var IncludeView = (function(){
    var fitToRequested = function(incX,incY,incW,incH){//Include at least this much content in your view
        var shouldUpdateRequestedViewbox = false;
        var x = incX;
        if (x == undefined){
            x = requestedViewboxX;
        }       else {
            shouldUpdateRequestedViewbox = true;
            requestedViewboxX = x;
        }
        var y = incY;
        if (y == undefined){
            y = requestedViewboxY;
        } else {
            shouldUpdateRequestedViewbox = true;
            requestedViewboxY = y;
        }
        var w = incW;
        if (w == undefined){
            w = requestedViewboxWidth;
        } else {
            shouldUpdateRequestedViewbox = true;
            requestedViewboxWidth = w;
        }
        var h = incH;
        if (h == undefined){
            h = requestedViewboxHeight;
        } else {
            shouldUpdateRequestedViewbox = true;
            requestedViewboxHeight = h;
        }
        var constrained = Zoom.constrainRequestedViewbox({width:w,height:h,x:x,y:y});
        var hr = boardHeight / constrained.height;
        var wr = boardWidth / constrained.width;
        var targetHeight,targetWidth;
        if(wr > hr){
            targetHeight = constrained.height;
            targetWidth = constrained.width * wr / hr;
        }
        else{
            targetHeight = constrained.height / wr * hr;
            targetWidth = constrained.width;
        }
        TweenController.zoomAndPanViewbox(constrained.x,constrained.y,targetWidth,targetHeight,undefined,!shouldUpdateRequestedViewbox);
        Progress.call("onViewboxChanged");
    };
    return {
        specific:function(x,y,w,h){
            return fitToRequested(x,y,w,h);
        },
        "default":function(){
            return fitToRequested();
        }
    };
	})();
	var strokeCollected = function(spoints){
    if(spoints.length > 0){
        var points = spoints.split(" ").map(function(p){
            return parseFloat(p);
        });

        var ink = {
            thickness : scaleScreenToWorld(Modes.draw.drawingAttributes.width),
            color:[Modes.draw.drawingAttributes.color,255],
            type:"ink",
            author:username,
            timestamp:Date.now(),
            target:"presentationSpace",
            privacy:Privacy.getCurrentPrivacy(),
            slide:currentSlide.toString(),
            isHighlighter:Modes.draw.drawingAttributes.isHighlighter
        };
        var scaledPoints = [];
        var x;
        var y;
        var worldPos;
        for(var p = 0; p < points.length; p += 3){
            x = points[p];
            y = points[p+1];
            worldPos = screenToWorld(x,y);
            scaledPoints = scaledPoints.concat([worldPos.x,worldPos.y,points[p+2]]);
        }
        ink.points = scaledPoints;
        ink.checksum = ink.points.reduce(function(a,b){return a+b},0);
        ink.startingSum = ink.checksum;
        ink.identity = ink.checksum.toFixed(1);
        calculateInkBounds(ink);
        if(ink.isHighlighter){
            boardContent.highlighters[ink.identity] = ink;
        }
        else{
            boardContent.inks[ink.identity] = ink;
        }
        sendInk(ink);
    }
	}
	var batchTransform = function(){
    return {
        type:"moveDelta",
        identity:Date.now().toString(),
        author:username,
        slide:currentSlide.toString(),
        target:"presentationSpace",
        privacy:Privacy.getCurrentPrivacy(),
        timestamp:Date.now(),
        inkIds:[],
        textIds:[],
        multiWordTextIds:[],
        imageIds:[],
        xOrigin:0,
        yOrigin:0,
        xTranslate:0,
        yTranslate:0,
        xScale:1.0,
        yScale:1.0,
        isDeleted:false,
        newPrivacy:"not_set"
    }
	}
	var sendDirtyInk = function(ink){
    sendStanza({
        type:"dirtyInk",
        identity:ink.identity,
        author:username,
        timestamp:Date.now(),
        slide:currentSlide.toString(),
        target:"presentationSpace",
        privacy:ink.privacy
    });
	}
	var sendInk = function(ink){
    updateStrokesPending(1,ink.identity);
    sendStanza(ink);
	}
	var hexToRgb = function(hex) {
    if(typeof hex == "string") hex = [hex,255];
    var result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex[0]);
    return {
        alpha: hex[1],
        red: parseInt(result[1], 16),
        green: parseInt(result[2], 16),
        blue: parseInt(result[3], 16)
    };
	}
	var partToStanza = function(p){
    var defaults = carota.runs.defaultFormatting;
    var color = hexToRgb(p.color || defaults.color);
    return {
        text:p.text,
        color:color,
        size:p.size || defaults.size,
        font:p.font || defaults.font,
        justify:p.align || defaults.align,
        bold:p.bold === true,
        underline:p.underline === true,
        italic:p.italic === true
    };
	}
	var richTextEditorToStanza = function(t){
    var bounds = t.doc.calculateBounds();
    var text = t.doc.save();
    return {
        author:t.author,
        timestamp:-1,
        target:t.target,
        tag:"_",
        privacy:t.privacy,
        slide:t.slide,
        identity:t.identity,
        type:t.type,
        x:bounds[0],
        y:bounds[1],
        requestedWidth:bounds[2]-bounds[0],
        width:bounds[2]-bounds[0],
        height:bounds[3]-bounds[1],
        words:text.map(partToStanza)
    }
	}
	var sendRichText = function(t){
    if(t.doc){
        Modes.text.echoesToDisregard[t.identity] = true;
        sendStanza(richTextEditorToStanza(t));
    }
	}
	var stanzaHandlers = {
    ink:inkReceived,
    dirtyInk:dirtyInkReceived,
    move:moveReceived,
    moveDelta:transformReceived,
    image:imageReceived,
    text:textReceived,
    multiWordText:richTextReceived,
    command:commandReceived,
    submission:submissionReceived,
    attendance:attendanceReceived,
    file:fileReceived
	};
	var fileReceived = function(file){
    //doing nothing with files yet.
	}
	var attendanceReceived = function(attendance){
    //doing nothing with attendances for the moment.
	}
	var submissionReceived = function(submission){
    Submissions.processSubmission(submission);
	}
	var commandReceived = function(c){
    if(c.command == "/TEACHER_VIEW_MOVED"){
        if(c.parameters[5] != currentSlide){
            return;
        }
        var ps = c.parameters.map(parseFloat);
        if(_.some(ps,isNaN)){
            console.log("Can't follow teacher to",c);
            return;
        }
        if(ps[4] == DeviceConfiguration.getIdentity()){
            return;
        }
        if("Conversations" in window && Conversations.getIsSyncedToTeacher()){
            var f = function(){
                zoomToPage();
                TweenController.zoomAndPanViewbox(ps[0],ps[1],ps[2],ps[3],function(){},false,true);
            };
            if(isInteractive){
                // interactive users don't chase the teacher's viewbox, only projectors do.
                //    WorkQueue.enqueue(f);
            }
            else{
                f();
            }
        }
        else{
        }
    }
	}
	var richTextReceived = function(t){
        if(t.identity in Modes.text.echoesToDisregard) return;
        if(isUsable(t)){
            if( WorkQueue != undefined ) {
                WorkQueue.enqueue(function () {
                    Modes.text.editorFor(t).doc.load(t.words);
                    blit();
                });
            }
        }
	}
	var textReceived = function(t){
    try{
        if(isUsable(t)){
            boardContent.texts[t.identity] = t;
            prerenderText(t);
            incorporateBoardBounds(t.bounds);
            if( WorkQueue != undefined ) {
                WorkQueue.enqueue(function () {
                    if (isInClearSpace(t.bounds)) {
                        drawText(t);
                        return false;
                    }
                    else {
                        return true;
                    }
                });
            }
        }
        else{
            if(t.identity in boardContent.texts){
                delete boardContent.texts[t.identity];
            }
        }
    }
    catch(e){
        console.log("textReceived exception:",e);
    }
	}
	var receiveMeTLStanza = function(stanza){
    Progress.call("stanzaReceived",[stanza]);
	}
	var actOnReceivedStanza = function(stanza){
    try{
        if(stanza.type in stanzaHandlers){
            stanzaHandlers[stanza.type](stanza);
            Progress.call("onBoardContentChanged");
        }
        else{
            console.log(sprintf("Unknown stanza: %s %s",stanza.type,stanza));
        }
    }
    catch(e){
        console.log("Exception in receiveMeTLStanza",e,stanza);
    }
	}
	var transformReceived = function(transform){
    console.log("transformReceived",transform);
    var op = "";
    var transformBounds = (function(){
        var myBounds = [undefined,undefined,undefined,undefined]; //minX,minY,maxX,maxY
        var incBounds = function(bounds){
            var max = function(count){
                var reference = myBounds[count];
                if (reference != undefined && !isNaN(reference)){
                    myBounds[count] = Math.max(reference,bounds[count]);
                } else {
                    myBounds[count] = bounds[count];
                }
            };
            var min = function(count){
                var reference = myBounds[count];
                if (reference != undefined && !isNaN(reference)){
                    myBounds[count] = Math.min(reference,bounds[count]);
                } else {
                    myBounds[count] = bounds[count];
                }
            };
            min(0);
            min(1);
            max(2);
            max(3);
        };
        var getBounds = function(){
            return myBounds;
        };
        var incBoardBounds = function(){
            var thisBounds = getBounds();
            if (thisBounds[0] != undefined && thisBounds[1] != undefined && thisBounds[2] != undefined && thisBounds[3] != undefined){
                incorporateBoardBounds(thisBounds);
            }
        };
        var setMinX = function(input){
            safelySet(input,0);
        };
        var setMinY = function(input){
            safelySet(input,1);
        };
        var setMaxX = function(input){
            safelySet(input,2);
        };
        var setMaxY = function(input){
            safelySet(input,3);
        };
        var safelySet = function(input,reference){
            if (input != undefined && !isNaN(input)){
                myBounds[reference] = input;
            }
        };
        return {
            "minX":getBounds[0],
            "setMinX":setMinX,
            "minY":getBounds[1],
            "setMinY":setMinY,
            "maxX":getBounds[2],
            "setMaxX":setMaxX,
            "maxY":getBounds[3],
            "setMaxY":setMaxY,
            "incorporateBounds":incBounds,
            "getBounds":getBounds,
            "incorporateBoardBounds":incBoardBounds
        };
    })();
    if(transform.newPrivacy != "not_set" && !transform.isDeleted){
        var p = transform.newPrivacy;
        op += "Became "+p;
        var setPrivacy = function(ink){
            if(ink){
                ink.privacy = p;
            }
        }
        $.each(transform.inkIds,function(i,id){
            setPrivacy(boardContent.inks[id]);
            setPrivacy(boardContent.highlighters[id]);
        });
        $.each(transform.imageIds,function(i,id){
            boardContent.images[id].privacy = p;
        });
        $.each(transform.textIds,function(i,id){
            boardContent.texts[id].privacy = p;
        });
        $.each(transform.multiWordTextIds,function(i,id){
            boardContent.multiWordTextIds[id].privacy = p;
        });
    }
    if(transform.isDeleted){
        op += "deleted";
        var p = transform.privacy;
        $.each(transform.inkIds,function(i,id){
            deleteInk("highlighters",p,id);
            deleteInk("inks",p,id);
        });
        $.each(transform.imageIds,function(i,id){
            deleteImage(p,id);
        });
        $.each(transform.textIds,function(i,id){
            deleteText(p,id);
        });
        $.each(transform.multiWordTextIds,function(i,id){
            deleteMultiWordText(p,id);
        });
    }
    if(transform.xScale != 1 || transform.yScale != 1){
        op += sprintf("scale (%s,%s)",transform.xScale,transform.yScale);
        var relevantInks = [];
        var relevantTexts = [];
        var relevantMultiWordTexts = [];
        var relevantImages = [];
        $.each(transform.inkIds,function(i,id){
            relevantInks.push(boardContent.inks[id]);
            relevantInks.push(boardContent.highlighters[id]);
        });
        $.each(transform.imageIds,function(i,id){
            relevantImages.push(boardContent.images[id]);
        });
        $.each(transform.textIds,function(i,id){
            relevantTexts.push(boardContent.texts[id]);
        });
        $.each(transform.multiWordTextIds,function(i,id){
            relevantMultiWordTexts.push(boardContent.multiWordTexts[id]);
        });
        var point = function(x,y){return {"x":x,"y":y};};
        var totalBounds = point(0,0);
        if ("xOrigin" in transform && "yOrigin" in transform){
            totalBounds.x = transform.xOrigin;
            totalBounds.y = transform.yOrigin;
        } else {
            var first = true;
            var updateRect = function(point){
                if (first){
                    totalBounds.x = point.x;
                    totalBounds.y = point.y;
                    first = false;
                } else {
                    if (point.x < totalBounds.x){
                        totalBounds.x = point.x;
                    }
                    if (point.y < totalBounds.y){
                        totalBounds.y = point.y;
                    }
                }
            };
            $.each(relevantInks,function(i,ink){
                if (ink != undefined && "bounds" in ink && _.size(ink.bounds) > 1){
                    updateRect(point(ink.bounds[0],ink.bounds[1]));
                }
            });
            $.each(relevantTexts,function(i,text){
                if (text != undefined && "x" in text && "y" in text){
                    updateRect(point(text.x,text.y));
                }
            });
            $.each(relevantMultiWordTexts,function(i,text){
                if (text != undefined && "x" in text && "y" in text){
                    updateRect(point(text.x,text.y));
                }
            });
            $.each(relevantImages,function(i,image){
                if (image != undefined && "x" in image && "y" in image){
                    updateRect(point(image.x,image.y));
                }
            });
        }
        transformBounds.setMinX(totalBounds.x);
        transformBounds.setMinY(totalBounds.y);
        var transformInk = function(index,ink){
            if(ink && ink != undefined){
                var ps = ink.points;
                var xPos = ink.bounds[0];
                var yPos = ink.bounds[1];
                var xp, yp;

                var internalX = xPos - totalBounds.x;
                var internalY = yPos - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));

                for(var p = 0; p < ps.length; p += 3){
                    xp = ps[p] - xPos;
                    yp = ps[p + 1] - yPos;
                    ps[p] = (xPos + xp * transform.xScale) + offsetX;
                    ps[p+1] = (yPos + yp * transform.yScale) + offsetY;
                }
                calculateInkBounds(ink);
                transformBounds.incorporateBounds(ink.bounds);
            }
        };
        var transformImage = function(index,image){
            if (image != undefined){
                image.width = image.width * transform.xScale;
                image.height = image.height * transform.yScale;

                var internalX = image.x - totalBounds.x;
                var internalY = image.y - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));
                image.x = image.x + offsetX;
                image.y = image.y + offsetY;

                calculateImageBounds(image);
                transformBounds.incorporateBounds(image.bounds);
            }
        };
        var transformText = function(index,text){
            if (text != undefined){
                text.width = text.width * transform.xScale;
                text.height = text.height * transform.yScale;

                var internalX = text.x - totalBounds.x;
                var internalY = text.y - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));
                text.x = text.x + offsetX;
                text.y = text.y + offsetY;

                text.size = text.size * transform.yScale;
                text.font = sprintf("%spx %s",text.size,text.family);
                if(isUsable(text)){
                    prerenderText(text);
                    calculateTextBounds(text);
                }
                else{
                    if(text.identity in boardContent.texts){
                        delete boardContent.texts[text.identity];
                    }
                }
                transformBounds.incorporateBounds(text.bounds);
            }
        };
        var transformMultiWordText = function(index,text){
            if (text != undefined){
                text.width = text.width * transform.xScale;
                text.height = text.height * transform.yScale;

                var internalX = text.x - totalBounds.x;
                var internalY = text.y - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));
                text.x = text.x + offsetX;
                text.y = text.y + offsetY;

                text.size = text.size * transform.yScale;
                text.font = sprintf("%spx %s",text.size,text.family);
                if(text.identity in boardContent.multiWordTexts){
                    delete boardContent.multiWordTexts[text.identity];
                }
                transformBounds.incorporateBounds(text.bounds);
            }
        };
        $.each(relevantInks,transformInk);
        $.each(relevantImages,transformImage);
        $.each(relevantTexts,transformText);
        $.each(relevantMultiWordTexts,transformMultiWordText);
    }
    if(transform.xTranslate || transform.yTranslate){
        var deltaX = transform.xTranslate;
        var deltaY = transform.yTranslate;
        op += sprintf("translate (%s,%s)",deltaX,deltaY);
        var translateInk = function(ink){
            if(ink){
                var ps = ink.points;
                for(var p = 0; p < ps.length; p += 3){
                    ps[p] += deltaX;
                    ps[p+1] += deltaY;
                }
                calculateInkBounds(ink);
                transformBounds.incorporateBounds(ink.bounds);
            }
        }
        $.each(transform.inkIds,function(i,id){
            translateInk(boardContent.inks[id]);
            translateInk(boardContent.highlighters[id]);
        });
        $.each(transform.imageIds,function(i,id){
            var image = boardContent.images[id];
            image.x += transform.xTranslate;
            image.y += transform.yTranslate;
            calculateImageBounds(image);
            transformBounds.incorporateBounds(image.bounds);
        });
        $.each(transform.textIds,function(i,id){
            var text = boardContent.texts[id];
            text.x += transform.xTranslate;
            text.y += transform.yTranslate;
            calculateTextBounds(text);
            transformBounds.incorporateBounds(text.bounds);
        });
        $.each(transform.multiWordTextIds,function(i,id){
            var text = boardContent.multiWordTexts[id];
	    var doc = text.doc;
            doc.position.x += transform.xTranslate;
            doc.position.y += transform.yTranslate;
	    text.bounds = doc.calculateBounds();
            transformBounds.incorporateBounds(text.bounds);
        });
    }
    transformBounds.incorporateBoardBounds();
    updateStatus(sprintf("%s %s %s %s %s",
                         op,
                         transform.imageIds.length,
                         transform.textIds.length,
                         transform.multiWordTextIds.length,
                         transform.inkIds.length));
    blit();
	}
	var moveReceived = function(move){
    updateStatus(sprintf("Moving %s, %s, %s",
                         Object.keys(move.images).length,
                         Object.keys(move.texts).length,
                         Object.keys(move.inks).length));
    $.each(move.inks,function(id,ink){
        boardContent.inks[id] = ink;
    });
    $.each(move.images,function(id,image){
        boardContent.images[id] = image;
    });
    $.each(move.texts,function(id,text){
        boardContent.texts[id] = text;
    });
    $.each(move.multiWordTexts,function(id,text){
        boardContent.multiWordTexts[id] = text;
    });
    blit();
	}
	var deleteInk = function(inks,privacy,id){
    if(id in boardContent[inks]){
        var ink = boardContent[inks][id];
        if(ink.privacy.toUpperCase() == privacy.toUpperCase()){
            delete boardContent[inks][id];
        }
    }
	}
	var deleteImage = function(privacy,id){
    var image = boardContent.images[id];
    if(image.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.images[id];
    }
	}
	var deleteText = function(privacy,id){
    var text = boardContent.texts[id];
    if(text.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.texts[id];
    }
	}
	var deleteMultiWordText = function(privacy,id){
    var text = boardContent.multiWordTexts[id];
    if(text.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.multiWordTexts[id];
    }
	}
	var dirtyInkReceived = function(dirtyInk){
    var id = dirtyInk.identity;
    var deletePrivacy = dirtyInk.privacy;
    deleteInk("highlighters",deletePrivacy,id);
    deleteInk("inks",deletePrivacy,id);
    updateStatus(sprintf("Deleted ink %s",id));
    blit();
	}
	var isInClearSpace = function(bounds){
    return !_.some(visibleBounds,function(onscreenElement){
        return intersectRect(onscreenElement,bounds);
    });
	}
	var screenBounds = function(worldBounds){
    var screenPos = worldToScreen(worldBounds[0],worldBounds[1]);
    var screenLimit = worldToScreen(worldBounds[2],worldBounds[3]);
    var screenWidth = screenLimit.x - screenPos.x;
    var screenHeight = screenLimit.y - screenPos.y;
    return {
        screenPos:screenPos,
        screenLimit:screenLimit,
        screenWidth:screenWidth,
        screenHeight:screenHeight
    };
	}
	var drawImage = function(image){
    try{
        if (image.canvas != undefined){
            var sBounds = screenBounds(image.bounds);
            visibleBounds.push(image.bounds);
            var borderW = sBounds.screenWidth * 0.10;
            var borderH = sBounds.screenHeight * 0.10;
            boardContext.drawImage(image.canvas, sBounds.screenPos.x - (borderW / 2), sBounds.screenPos.y - (borderH / 2), sBounds.screenWidth + borderW ,sBounds.screenHeight + borderH);
        }
    }
    catch(e){
        console.log("drawImage exception",e);
    }
	}
	var drawText = function(text){
    try{
        var sBounds = screenBounds(text.bounds);
        visibleBounds.push(text.bounds);
        boardContext.drawImage(text.canvas,
			       sBounds.screenPos.x,
			       sBounds.screenPos.y,
			       sBounds.screenWidth,
			       sBounds.screenHeight);
    }
    catch(e){
        console.log("drawText exception",e);
    }
	}
	function drawMultiwordText(item){
    Modes.text.draw(item);
	}
	var drawInk = function(ink){
    var sBounds = screenBounds(ink.bounds);
    visibleBounds.push(ink.bounds);
    boardContext.drawImage(ink.canvas,
                           sBounds.screenPos.x,sBounds.screenPos.y,
                           sBounds.screenWidth,sBounds.screenHeight);
	}
	var imageReceived = function(image){
    var dataImage = new Image();
    image.imageData = dataImage;
    dataImage.onload = function(){
        if(image.width == 0){
            image.width = dataImage.naturalWidth;
        }
        if(image.height == 0){
            image.height = dataImage.naturalHeight;
        }
        image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
        incorporateBoardBounds(image.bounds);
        boardContent.images[image.identity]  = image;
        updateTracking(image.identity);
        prerenderImage(image);
        if( WorkQueue != undefined ) {
            WorkQueue.enqueue(function () {
                if (isInClearSpace(image.bounds)) {
                    try {
                        drawImage(image);
                    } catch (e) {
                        console.log("drawImage exception", e);
                    }
                    return false;
                }
                else {
                    console.log("Rerendering image in contested space");
                    return true;
                }
            });
        }
    }
    dataImage.src = calculateImageSource(image);
	}
	var inkReceived = function(ink){
        calculateInkBounds(ink);
        updateStrokesPending(-1,ink.identity);
        if(prerenderInk(ink)){
            incorporateBoardBounds(ink.bounds);
            if(ink.isHighlighter){
                boardContent.highlighters[ink.identity] = ink;
            }
            else{
                boardContent.inks[ink.identity] = ink;
            }
            if( WorkQueue != undefined ) {
                WorkQueue.enqueue(function () {
                    if (isInClearSpace(ink.bounds)) {
                        drawInk(ink);
                        return false;
                    }
                    else {
                        return true;
                    }
                });
            }
        }
	}
	var takeControlOfViewbox = function(){
    delete Progress.onBoardContentChanged.autoZooming;
    //UserSettings.setUserPref("followingTeacherViewbox",true);
	}
	var zoomToFit = function(){
    Progress.onBoardContentChanged.autoZooming = zoomToFit;
    requestedViewboxWidth = boardContent.width;
    requestedViewboxHeight = boardContent.height;
    IncludeView.specific(boardContent.minX,boardContent.minY,boardContent.width,boardContent.height);
	}
	var zoomToOriginal = function(){
    takeControlOfViewbox();
    var oldReqVBH = requestedViewboxHeight;
    var oldReqVBW = requestedViewboxWidth;
    requestedViewboxWidth = boardWidth;
    requestedViewboxHeight = boardHeight;
    IncludeView.specific(0,0,boardWidth,boardHeight);
	}
	var zoomToPage = function(){
    takeControlOfViewbox();
    var oldReqVBH = requestedViewboxHeight;
    var oldReqVBW = requestedViewboxWidth;
    requestedViewboxWidth = boardWidth;
    requestedViewboxHeight = boardHeight;
    var xPos = viewboxX + ((oldReqVBW - requestedViewboxWidth) / 2);
    var yPos = viewboxY + ((oldReqVBH - requestedViewboxHeight) / 2);
    IncludeView.specific(xPos,yPos,boardWidth,boardHeight);
	}
	var resizeCanvasFunction = function(w,h){
		var rw = Math.round(w);
		var rh = Math.round(h);
		var oldWidth = canvasElement[0].width;
		var oldHeight = canvasElement[0].height;
		if (oldHeight != rh || oldWidth != rw){
			boardWidth = rw;
			boardHeight = rh;
			canvasElement.width = rw;
			canvasElement.height = rh;
			canvasElement[0].width = rw;
			canvasElement[0].height = rh;
			console.log("resizing canvas:",oldWidth,rw,oldHeight,rh,boardContext);
			blit();
		}
	};
	var requestViewboxFunction = function(x,y,w,h,onComplete){
		TweenController.changeViewbox(x,y,w,h,onComplete);
	}
	var bounceButton = function(button){
    var b = $(button);
    var c = "activeBrush";
    b.addClass(c);
    setTimeout(function(){
        b.removeClass(c);
    },200);
	}
	var returnedBoard = {
		historyReceived:function(json){
			historyReceivedFunction(json);
		},
		stanzaReceived:function(stanza){
			actOnReceivedStanza(stanza);
		},
		progress:Progress,
		getCanvas:function(){return canvasElement;},
		requestViewbox:requestViewboxFunction,
		resizeCanvas:resizeCanvasFunction,
		worldToScreen:worldToScreen,
		screenToWorld:screenToWorld,
		scale:scale,
		alertCanvas:alertCanvas,
		getModes:function(){
			return Modes;
		}
	};
	var Modes = (function(thisBoard){
    var removeActiveMode = function(){
        $(".activeTool").removeClass("activeTool");
        $(".activeMode").addClass("inactiveMode").removeClass("activeMode");
    };
    var setActiveMode = function(toolsSelector,headerSelector){
        removeActiveMode();
        $(toolsSelector).addClass("activeMode").removeClass("inactiveMode");
        $(".activeTool").removeClass("activeTool").addClass("inactiveTool");
        $(headerSelector).addClass("activeTool").removeClass("inactiveTool");
        //Progress.call("onLayoutUpdated");//Only necessary if the rest of the application will be disrupted.  WILL blank canvas.
    };
    var noneMode = {
        name:"none",
        activate:function(){
            Modes.currentMode.deactivate();
            Modes.currentMode = Modes.none;
            removeActiveMode();
        },
        deactivate:function(){
            removeActiveMode();
            unregisterPositionHandlers(canvasElement);
        }
    };
    return {
        currentMode:noneMode,
        none:noneMode,
        text:(function(){
            var texts = [];
            var noop = function(){};
            var fontFamilySelector, fontSizeSelector, fontColorSelector, fontBoldSelector, fontItalicSelector, fontUnderlineSelector, justifySelector,
                presetFitToText,presetRunToEdge,presetNarrow,presetWiden,presetCenterOnScreen,presetFullscreen;

            var echoesToDisregard = {};
            var createBlankText = function(worldPos){
                return Modes.text.editorFor({
                    bounds:[worldPos.x,worldPos.y,worldPos.x,worldPos.y],
                    identity:sprintf("%s_%s_%s",username,Date.now(),_.uniqueId()),
                    requestedWidth:300,
                    width:0,
                    height:0,
                    x:worldPos.x,
                    y:worldPos.y,
                    type:"multiWordText",
                    author:username,
                    words:[]
                });
            };
            $(function(){
                fontFamilySelector = $("#fontFamilySelector");
                fontSizeSelector = $("#fontSizeSelector");
                fontColorSelector = $("#fontColorSelector");
                fontBoldSelector = $("#fontBoldSelector");
                fontItalicSelector = $("#fontItalicSelector");
                fontUnderlineSelector = $("#fontUnderlineSelector");
                justifySelector = $("#justifySelector");
                presetFitToText = $("#presetFitToText");
                presetRunToEdge = $("#presetRunToEdge");
                presetNarrow = $("#presetNarrow");
                presetWiden = $("#presetWiden");
                presetFullscreen = $("#presetFullscreen");
                presetCenterOnScreen = $("#presetCenterOnScreen");
                var fontFamilyOptionTemplate = fontFamilySelector.find(".fontFamilyOption").clone();
                var fontSizeOptionTemplate = fontSizeSelector.find(".fontSizeOption").clone();
                var fontColorOptionTemplate = fontColorSelector.find(".fontColorOption").clone();
                Fonts.getAllFamilies().map(function(family){
                    fontFamilySelector.append(fontFamilyOptionTemplate.clone().attr("value",family).text(family));
                });
                Fonts.getAllSizes().map(function(size){
                    fontSizeSelector.append(fontSizeOptionTemplate.clone().attr("value",size).text(size));
                });
                Colors.getAllNamedColors().map(function(color){
                    fontColorSelector.append(fontColorOptionTemplate.clone().attr("value",color.rgb).text(color.name));
                });
                var toggleFormattingProperty = function(prop){
                    return function(){
                        _.each(boardContent.multiWordTexts,function(t){
                            if(t.doc.isActive){
                                var selRange = t.doc.selectedRange();
                                selRange.setFormatting(prop, selRange.getFormatting()[prop] !== true);
                                if(t.doc.save().length > 0){
                                    sendRichText(t);
                                }
                            }
                        });
                    }
                }
                var setFormattingProperty = function(prop){
                    return function(){
                        var newValue = $(this).val();
                        _.each(boardContent.multiWordTexts,function(t){
                            if(t.doc.isActive){
                                t.doc.selectedRange().setFormatting(prop,newValue);
                                if(t.doc.save().length > 0){
                                    sendRichText(t);
                                }
                            }
                        });
                    }
                };
                var adoptPresetWidth = function(preset){
                    return function(){
                        _.each(boardContent.multiWordTexts,function(t){
                            if(t.doc.isActive){
                                switch(preset){
                                case "runToEdge":
                                    var logicalBoardWidth = screenToWorld(boardWidth,0);
                                    t.doc.width((logicalBoardWidth.x + viewboxX - t.x));
                                    break;
                                case "fitToText":
                                    t.doc.width(t.doc.frame.actualWidth());
                                    break;
                                case "widen":
                                    t.doc.width(t.doc.frame.actualWidth() * 1.6);
                                    break;
                                case "narrow":
                                    t.doc.width(t.doc.frame.actualWidth() * 0.6);
                                    break;
                                case "centerOnScreen":
                                    t.doc.width(screenToWorld(boardWidth,0).x);
                                    t.doc.selectedRange().setFormatting("align","center");
                                    t.doc.position.x = viewboxX;
                                    break;
                                case "fullscreen":
                                    t.doc.position.x = viewboxX;
                                    t.doc.position.y = viewboxY;
                                    t.doc.width(screenToWorld(boardWidth,0).x);
                                    t.doc.select(0,t.doc.frame.length - 1);
                                    t.doc.selectedRange().setFormatting("align","center");
                                    t.doc.selectedRange().setFormatting("bold",true);
                                    var content = t.doc.documentRange().save();
                                    content.push({
                                        text:"\n"
                                    });
                                    content.push({
                                        text:"\n"
                                    });
                                    t.doc.load(content);
                                    var startOfPara = t.doc.frame.length - 1;
                                    t.doc.select(startOfPara,startOfPara);
                                    break;
                                }
                                t.doc.contentChanged.fire();
                            }
                        });
                    };
                }
                fontBoldSelector.click(toggleFormattingProperty("bold"));
                fontItalicSelector.click(toggleFormattingProperty("italic"));
                fontUnderlineSelector.click(toggleFormattingProperty("underline"));
                fontFamilySelector.change(setFormattingProperty("font"));
                fontSizeSelector.change(setFormattingProperty("size"));
                fontColorSelector.change(setFormattingProperty("color"));
                justifySelector.change(setFormattingProperty("align"));
                presetFitToText.click(adoptPresetWidth("fitToText"));
                presetRunToEdge.click(adoptPresetWidth("runToEdge"));
                presetNarrow.click(adoptPresetWidth("narrow"));
                presetWiden.click(adoptPresetWidth("widen"));
                presetCenterOnScreen.click(adoptPresetWidth("centerOnScreen"));
                presetFullscreen.click(adoptPresetWidth("fullscreen"));
            });
            return {
                echoesToDisregard:{},
                editorFor:function(t){
                    var editor = boardContent.multiWordTexts[t.identity];
                    if(!editor){
                        editor = boardContent.multiWordTexts[t.identity] = t;
                    }
                    if(!editor.doc){
                        var isAuthor = t.author == username;
                        editor.doc = carota.editor.create(
                            $("<div />",{id:sprintf("t_%s",t.identity)}).appendTo(textInputHost)[0],
                            canvasElement[0],
                            isAuthor? function(){render(boardContent)} : noop);
                        if(isAuthor){
                            editor.doc.contentChanged(function(){
                                var source = boardContent.multiWordTexts[editor.identity];
                                source.privacy = Privacy.getCurrentPrivacy();
                                source.target = "presentationSpace";
                                source.slide = Conversations.getCurrentSlideJid();
                                if(editor.doc.save().length > 0){
                                    sendRichText(source);
                                }
                            });
                            editor.doc.selectionChanged(function(formatReport){
                                var format = formatReport();
                                fontBoldSelector.toggleClass("active",format.bold == true);
                                fontItalicSelector.toggleClass("active",format.italic == true);
                                fontUnderlineSelector.toggleClass("active",format.underline == true);
                                fontSizeSelector.val(format.size || carota.runs.defaultFormatting.size);
                                fontFamilySelector.val(format.font || carota.runs.defaultFormatting.font);
                                fontColorSelector.val(format.color || carota.runs.defaultFormatting.color);
                                justifySelector.val(format.align || carota.runs.defaultFormatting.align);

                            });
                        }
                    }
                    editor.doc.position = {x:t.x,y:t.y};
                    editor.doc.width(t.requestedWidth);
                    return editor;
                },
                draw:function(t){
                    if(t && t.doc){
                        carota.editor.paint(canvasElement[0],t.doc,true,thisBoard);
                    }
                },
                activate:function(){
                    var doubleClickThreshold = 500;
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.text;
                    setActiveMode("#textTools","#insertText");
                    $(".activeBrush").removeClass("activeBrush");
                    Progress.call("onLayoutUpdated");
                    var lastClick = Date.now();
                    var up = function(x,y,z,worldPos){
                        var threshold = 10;
                        var ray = [worldPos.x - threshold,worldPos.y - threshold,worldPos.x + threshold,worldPos.y + threshold];
                        var selectedTexts = _.values(boardContent.multiWordTexts).filter(function(text){
                            /*This checks against the maximum possible bound of the textbox rather than the space occupied.
                             This is because justification makes visual comparison very hard.*/
                            var bounds = text.doc.calculateBounds();
                            return intersectRect(bounds,ray) && text.author == username;
                        });
                        _.each(boardContent.multiWordTexts,function(t){
                            t.doc.isActive = false;
                            if(t.doc.save().length == 0){
                                delete boardContent.multiWordTexts[t.identity];
                            }
                        });
                        console.log("Selected texts",selectedTexts);
                        if (selectedTexts.length > 0){
                            var editor = selectedTexts[0].doc;
                            editor.isActive = true;
                            var relativePos = {x:worldPos.x - editor.position.x, y:worldPos.y - editor.position.y};
                            var clickTime = Date.now();
                            var node = editor.byCoordinate(relativePos.x,relativePos.y);
                            editor.mouseupHandler(node);
                            if(clickTime - lastClick <= doubleClickThreshold){
                                editor.dblclickHandler(node);
                            }
                            lastClick = clickTime;
                        } else {
                            var newEditor = createBlankText(worldPos).doc;
                            newEditor.load([]);
                            newEditor.isActive = true;
                            newEditor.mouseupHandler(newEditor.byOrdinal(0));
                        }
                        Progress.historyReceived["ClearMultiTextEchoes"] = function(){
                            Modes.text.echoesToDisregard = {};
                        };
                        Progress.call("onSelectionChanged",[Modes.select.selected]);
                    }
                    registerPositionHandlers(canvasElement,noop,noop,up);
                },
                deactivate:function(){
                    removeActiveMode();
                    _.each(boardContent.multiWordTexts,function(t){
                        t.doc.isActive = false;
                    });
                    unregisterPositionHandlers(canvasElement);
                    /*Necessary to ensure that no carets or marquees remain on the editors*/
                    blit();
                }
            }
        })(),
        image:(function(){
            var marquee = undefined;
            var noop = function(){};
            var currentImage = {};
            var insertOptions = undefined;
            var imageInsertOptionsClose = undefined;
            var imageFileChoice = undefined;
            var imageSizeControls = undefined;
            var imageSizeChoiceSelector = undefined;
            var imageUploadThumbnail = undefined;
            var imageProgressContainer = undefined;
            var imageUploadButton = undefined;
            var imageUploadX = undefined;
            var imageUploadY = undefined;
            var imageUploadWidth = undefined;
            var imageUploadHeight = undefined;
            var imageSizeChoices = [
                {name:"160*120",func:function(w,h){return {w:160,h:120}}},
                {name:"320*240",func:function(w,h){return {w:320,h:240}}},
                {name:"25%",func:function(w,h){return {w:w / 4, h:h / 4}}},
                {name:"50%",func:function(w,h){return {w:w / 2, h:h / 2}}},
                {name:"75%",func:function(w,h){return {w:(w / 4) * 3, h:(h / 4) * 3}}},
                {name:"Native",func:function(w,h){return {w:w,h:h}}}
            ];
            var updateImageEditor = function(){
                if ("type" in currentImage && currentImage.type == "imageDefinition"){
                    // there is now only one place that the imageOptions dialog gets positioned, and it's here, so if you want to move it about, etc, do it right here.
                    insertOptions.css({
                        position:"absolute",
                        left:px(currentImage.screenX - 30),
                        top:px(currentImage.screenY)
                    });
                    if ("fileUpload" in currentImage){
                        imageFileChoice.hide();
                        imageSizeControls.show();
                        $.map(imageSizeChoiceSelector.find(".imageSizeChoice"),function(elem){
                            if ("thumbnailSize" in currentImage && currentImage.thumbnailSize.name == $(elem).text()){
                                $(elem).addClass("active");
                            } else {
                                $(elem).removeClass("active");
                            }
                        });
                        var reader = new FileReader();
                        reader.onload = function(e){
                            if (!("thumbnailSize" in currentImage)){
                                currentImage.thumbnailSize = imageSizeChoices[0];
                            }
                            imageUploadThumbnail[0].getContext("2d").clearRect(0,0,imageUploadThumbnail.width(),imageUploadThumbnail.height());
                            var img = new Image();
                            img.onload = function(e){
                                var resizedDimensions = currentImage.thumbnailSize.func(img.width,img.height);
                                var w = resizedDimensions.w;
                                var h = resizedDimensions.h;

                                var scaledHeight = h;
                                var scaledWidth = w;
                                if (w < h){
                                    //height is larger
                                    var scaleFactor = h / 300;
                                    scaledHeight = 300;
                                    scaledWidth = w / scaleFactor;
                                } else {
                                    var sf = w / 300;
                                    scaledWidth = 300;
                                    scaledHeight = h / sf;
                                }
                                imageUploadThumbnail.attr("width",scaledWidth);
                                imageUploadThumbnail.attr("height",scaledHeight);
                                imageUploadThumbnail.css({
                                    width:px(scaledWidth),
                                    height: px(scaledHeight)
                                });

                                imageUploadThumbnail[0].getContext("2d").drawImage(img,0,0,scaledWidth,scaledHeight);

                                imageUploadX.text(currentImage.x);
                                imageUploadY.text(currentImage.y);
                                imageUploadWidth.text(w);
                                imageUploadHeight.text(h);

                                //render canvas is responsible for the resizing.  The other canvas is a thumbnail.
                                var renderCanvas = $("<canvas/>");
                                renderCanvas.attr("width",w);
                                renderCanvas.attr("height",h);
                                renderCanvas.css({
                                    width:px(w),
                                    height: px(h)
                                });
                                renderCanvas[0].getContext("2d").drawImage(img,0,0,w,h);
                                currentImage.width = w;
                                currentImage.height = h;
                                currentImage.resizedImage = renderCanvas[0].toDataURL();
                                if ("resizedImage" in currentImage){
                                    imageUploadButton.show();
                                }
                            };
                            img.src = e.target.result;
                        };
                        reader.readAsDataURL(currentImage.fileUpload);
                        if ("resizedImage" in currentImage){
                            imageUploadButton.show();
                        } else {
                            imageUploadButton.hide();
                        }
                    } else {
                        imageFileChoice.show();
                        imageSizeControls.hide();
                    }
                    insertOptions.show();
                } else {
                    resetImageUpload();
                }
            };
            var newInsertOptions = function(x,y,worldPos,onImage){
                currentImage = {
                    "type":"imageDefinition",
                    "screenX":x,
                    "screenY":y,
                    "x":worldPos.x,
                    "y":worldPos.y
                }
                updateImageEditor();
            }
            var resetImageUpload = function(){
                insertOptions.hide();
								var imageForm = imageFileChoice.wrap("<form>").closest("form").get(0);
								if (imageForm != undefined){
									imageForm.reset();
								}
								if (imageUploadThumbnail[0] != undefined){
									imageUploadThumbnail[0].getContext("2d").clearRect(0,0,imageUploadThumbnail.width(),imageUploadThumbnail.height());
								}
                imageUploadX.text("");
                imageUploadY.text("");
                imageUploadWidth.text("");
                imageUploadHeight.text("");
                imageFileChoice.unwrap();
                currentImage = {};
                imageUploadButton
                    .text("Upload")
                    .unbind("click")
                    .on("click",actOnImageButtonClick);
            };
            var actOnImageButtonClick = function(){
                imageUploadButton.unbind("click").text("Uploading");
                if ("type" in currentImage && currentImage.type == "imageDefinition" && "resizedImage" in currentImage){
                    WorkQueue.pause();
                    var worldPos = {x:currentImage.x,y:currentImage.y};
                    var screenPos= {x:currentImage.screenX,y:currentImage.screenY};
                    var t = Date.now();
                    var identity = sprintf("%s%s",username,t);
                    var currentSlide = Conversations.getCurrentSlideJid();
                    var url = sprintf("/uploadDataUri?jid=%s&filename=%s",currentSlide.toString(),encodeURI(identity));
                    $.ajax({
                        url: url,
                        type: 'POST',
                        success: function(e){
                            updateTracking(identity);
                            var newIdentity = $(e).find("resourceUrl").text();
                            var imageStanza = {
                                type:"image",
                                author:username,
                                timestamp:t,
                                tag:"{\"author\":\""+username+"\",\"privacy\":\""+Privacy.getCurrentPrivacy()+"\",\"id\":\""+newIdentity+"\",\"isBackground\":false,\"zIndex\":0,\"timestamp\":-1}",
                                identity:newIdentity,
                                slide:currentSlide.toString(),
                                source:$(e).text(),
                                width:currentImage.width,
                                height:currentImage.height,
                                target:"presentationSpace",
                                privacy:Privacy.getCurrentPrivacy(),
                                x:currentImage.x,
                                y:currentImage.y
                            };
                            resetImageUpload();
														updateTracking(newIdentity,function(){
															var newX = Math.min(imageStanza.x,viewboxX);
															var newY = Math.min(imageStanza.y,viewboxY);
															var newW = Math.max(imageStanza.x + imageStanza.width,viewboxWidth);
															var newH = Math.max(imageStanza.y + imageStanza.height,viewboxHeight);	
															IncludeView.specific(newX,newY,newW,newH);
														});
                            sendStanza(imageStanza);
                            WorkQueue.gracefullyResume();
                        },
                        error: function(e){
                            resetImageUpload();
                            alert("Upload failed.  This image cannot be processed, either because of image protocol issues or because it exceeds the maximum image size.");
                            WorkQueue.gracefullyResume();
                        },
                        data:currentImage.resizedImage,
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
                    marquee = $("imageMarquee");
                    insertOptions = $("#imageInsertOptions");
                    imageInsertOptionsClose = $("#imageInsertOptionsClose");
                    imageFileChoice = $("#imageFileChoice");
                    imageSizeControls = $("#imageSizeControls");
                    imageSizeChoiceSelector = $("#imageSizeChoiceSelector");
                    imageUploadThumbnail = $("#imageUploadThumbnail");
                    imageProgressContainer = $("#imageProgressContainer");
                    imageUploadButton = $("#imageUploadButton");
                    imageUploadX = $("#imageUploadX");
                    imageUploadY = $("#imageUploadY");
                    imageUploadWidth = $("#imageUploadWidth");
                    imageUploadHeight = $("#imageUploadHeight");
                    imageInsertOptionsClose.on("click",resetImageUpload);
                    imageFileChoice.attr("accept","image/*");
										if (imageFileChoice[0] != undefined){
											imageFileChoice[0].addEventListener("change",function(e){
													if ("type" in currentImage && currentImage.type == "imageDefinition"){
															var files = e.target.files || e.dataTransfer.files;
															var limit = files.length;
															var file = files[0];
															if (file.type.indexOf("image") == 0) {
																	currentImage.fileUpload = file;
																	currentImage.thumbnailSize = imageSizeChoices[0];
																	updateImageEditor();
															}
													}
											},false);
										}
                    var imageSizeOptionTemplate = imageSizeChoiceSelector.find(".imageSizeChoice").clone();
                    imageSizeChoiceSelector.empty();
                    imageSizeChoices.map(function(isc){
                        var thisChoice = imageSizeOptionTemplate.clone().text(isc.name).on("click",function(){
                            if ("type" in currentImage && currentImage.type == "imageDefinition"){
                                currentImage.thumbnailSize = isc;
                                updateImageEditor();
                            }
                        });
                        imageSizeChoiceSelector.append(thisChoice);
                    });
                    resetImageUpload();
                }
            });
            return {
                activate:function(){
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.image;
                    setActiveMode("#imageTools","#insertImage");
                    resetImageUpload();
                    Progress.call("onLayoutUpdated");
                    var up = function(x,y,z,worldPos){
                        marquee.show();
                        marquee.css({
                            left:px(x),
                            top:px(y)
                        });
                        resetImageUpload();
                        var newScreenPos = worldToScreen(worldPos.x,worldPos.y);
                        var threshold = 10;
                        var options = newInsertOptions(newScreenPos.x,newScreenPos.y,worldPos);
                    }
                    registerPositionHandlers(canvasElement,noop,noop,up);
                },
                deactivate:function(){
                    resetImageUpload();
                    unregisterPositionHandlers(canvasElement);
                    removeActiveMode();
                }
            };
        })(),
        pan:{
            name:"pan",
            activate:function(){
                updateStatus("PAN");
                Modes.currentMode.deactivate();
                setActiveMode("#panTools","#panMode");
                Modes.currentMode = Modes.pan;
                var originX;
                var originY;
                var down = function(x,y,z){
                    takeControlOfViewbox();
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
                var up = function(x,y,z){}
                registerPositionHandlers(canvasElement,down,move,up);
            },
            deactivate:function(){
                removeActiveMode();
                unregisterPositionHandlers(canvasElement);
            }
        },
        select:(function(){
            var isAdministeringContent = false;
            var updateSelectionVisualState = function(sel){
                if(sel){
                    Modes.select.selected = sel;
                    var shouldShowButtons = function(){
                        if ("images" in sel && _.size(sel.images) > 0){
                            return true;
                        } else if ("inks" in sel && _.size(sel.inks) > 0){
                            return true;
                        } else if ("texts" in sel && _.size(sel.texts) > 0){
                            return true;
                        } else if ("multiWordTexts" in sel && _.size(sel.multiWordTexts) > 0){
                            return true;
                        } else {
                            return false;
                        }
                    }
                    if (shouldShowButtons()){
                        $("#delete").removeClass("disabledButton");
                        $("#resize").removeClass("disabledButton");
                        if (isAdministeringContent){
                            $("#ban").removeClass("disabledButton");
                        } else {
                            $("#ban").addClass("disabledButton");
                        }
                    } else {
                        $("#delete").addClass("disabledButton");
                        $("#resize").addClass("disabledButton");
                        $("#ban").addClass("disabledButton");
                    }

										if ("Conversations" in window && Conversations.shouldModifyConversation()){
											$("#ban").show();
											$("#administerContent").show();	
										} else {
											$("#ban").hide();
											$("#administerContent").hide();	
										}
										if (isAdministeringContent){
											$("#administerContent").removeClass("disabledButton");
										} else {
											$("#administerContent").addClass("disabledButton");
										}
                    if (Modes.currentMode == Modes.select){
                        $("#selectionAdorner").empty();
                        _.forEach(["images","texts","inks","highlighters","multiWordTexts"],function(category){
                            if (category in sel){
                                $.each(sel[category],function(i,item){
                                    drawSelectionBounds(item);
                                });
                            }
                        });
                    }
                }
            };
            var clearSelectionFunction = function(){
                Modes.select.selected = {images:{},text:{},inks:{},multiWordTexts:{}};
                Progress.call("onSelectionChanged",[Modes.select.selected]);
            }
            var updateSelectionWhenBoardChanges = _.debounce(function(){
                _.forEach(["images","texts","inks","highlighters","multiWordTexts"],function(catName){
                    var selCatName = catName == "highlighters" ? "inks" : catName;
                    var boardCatName = catName;
                    if (Modes && Modes.select && Modes.select.selected && selCatName in Modes.select.selected){
                        var cat = Modes.select.selected[selCatName];
                        _.forEach(cat,function(i){
                            if (cat && boardCatName in boardContent && i.identity in boardContent[boardCatName]){
                                cat[i.identity] = boardContent[boardCatName][i.identity];
                            } else {
                                if (selCatName == "inks"){
                                    if (boardCatName == "highlighters" && i.identity in cat && cat[i.identity].isHighlighter == true){
                                        delete cat[i.identity];
                                    } else if (boardCatName == "inks" && i.identity in cat && cat[i.identity].isHighlighter == false) {
                                        delete cat[i.identity];
                                    }
                                } else {
                                    delete cat[i.identity];
                                }
                            }
                        });
                    }
                });
                Progress.call("onSelectionChanged",[Modes.select.selected]);
            },100);
            var banContentFunction = function(){
                if (Modes.select.selected != undefined && isAdministeringContent){
                    var s = Modes.select.selected;
                    banContent(
                        Conversations.getCurrentConversationJid(),
                        Conversations.getCurrentSlideJid(),
                        _.uniq(_.map(s.inks,function(e){return e.identity;})),
                        _.uniq(_.map(s.texts,function(e){return e.identity;})),
                        _.uniq(_.map(s.multiWordTexts,function(e){return e.identity;})),
                        _.uniq(_.map(s.images,function(e){return e.identity;}))
                    );
                }
                clearSelectionFunction();
            };
            var administerContentFunction = function(){
                isAdministeringContent = Conversations.shouldModifyConversation() ? !isAdministeringContent : false;
                if (isAdministeringContent){
                    $("#administerContent").removeClass("disabledButton");
                } else {
                    $("#administerContent").addClass("disabledButton");
                }
                clearSelectionFunction();
            };

            Progress.onBoardContentChanged["ModesSelect"] = updateSelectionWhenBoardChanges;
            Progress.onViewboxChanged["ModesSelect"] = updateSelectionWhenBoardChanges;
            Progress.onSelectionChanged["ModesSelect"] = updateSelectionVisualState;
            Progress.historyReceived["ModesSelect"] = clearSelectionFunction;
            Progress.conversationDetailsReceived["ModesSelect"] = function(conversation){
                if (isAdministeringContent && Conversations.shouldModifyConversation()){
                    isAdministeringContent = false;
                }
                if (Conversations.shouldModifyConversation()){
                    $("#ban").show();
                    $("#administerContent").show();
                    $("#administerContent").bind("click",administerContentFunction);
                    $("#ban").bind("click",banContentFunction);
                } else {
                    $("#ban").hide();
                    $("#administerContent").hide();
                    $("#administerContent").unbind("click");
                    $("#ban").unbind("click");
                }
                if (isAdministeringContent){
                    $("#administerContent").removeClass("disabledButton");
                } else {
                    $("#administerContent").addClass("disabledButton");
                }
            };
            return {
                name:"select",
                selected:{
                    images:{},
                    texts:{},
                    inks:{},
                    multiWordTexts:{}
                },
                clearSelection:clearSelectionFunction,
                activate:function(){
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.select;
                    setActiveMode("#selectTools","#selectMode");
                    updateStatus("SELECT");
                    var originPoint = {x:0,y:0};
                    var marqueeOriginX;
                    var marqueeOriginY;
                    var lastX;
                    var lastY;
                    var marqueeWorldOrigin;
                    var marquee = $("<div/>",{
                        id:"selectMarquee"
                    });
                    var adorner = $("#selectionAdorner");
                    var dragging = false;
                    var resizing = false;
                    $("#delete").bind("click",function(){
                        if (Modes.select.selected != undefined){
                            var deleteTransform = batchTransform();
                            deleteTransform.isDeleted = true;
                            if ("inks" in Modes.select.selected){
                                deleteTransform.inkIds = _.keys(Modes.select.selected.inks);
                            }
                            if ("texts" in Modes.select.selected){
                                deleteTransform.textIds = _.keys(Modes.select.selected.texts);
                            }
                            if ("images" in Modes.select.selected){
                                deleteTransform.imageIds = _.keys(Modes.select.selected.images);
                            }
                            if ("multiWordTexts" in Modes.select.selected){
                                deleteTransform.multiWordTextIds = _.keys(Modes.select.selected.multiWordTexts);
                            }
                            sendStanza(deleteTransform);
                        }
                        clearSelectionFunction();
                    });
                    var threshold = 30;
                    var resizeHandle = [0,0,0,0];
                    var initialHeight = 0;
                    $("#administerContent").bind("click",administerContentFunction);
                    $("#ban").bind("click",banContentFunction);
                    $("#resize").bind("click",function(){
                        var items = _.flatten([
                            _.values(Modes.select.selected.images),
                            _.values(Modes.select.selected.texts),
                            _.values(Modes.select.selected.inks),
                            _.values(Modes.select.selected.multiWordTexts)]);
                        if(items.length > 0){
                            var x1 = Math.min.apply(Math,items.map(function(item){
                                return item.bounds[0];
                            }))
                            var y1 = Math.min.apply(Math,items.map(function(item){
                                return item.bounds[1];
                            }))
                            var x2 = Math.max.apply(Math,items.map(function(item){
                                return item.bounds[2];
                            }));
                            var y2 = Math.max.apply(Math,items.map(function(item){
                                return item.bounds[3];
                            }));
                            var screenTopLeft = worldToScreen(x1,y1);
                            var screenBottomRight = worldToScreen(x2,y2);
                            initialHeight = screenBottomRight.y - screenTopLeft.y;
                            var resizeLeft = screenBottomRight.x;
                            var resizeTop = screenTopLeft.y;
                            resizeHandle = [resizeLeft - threshold, resizeTop - threshold, resizeLeft + threshold, resizeTop + threshold];
                            adorner.append($("<img />",{
                                src:"/static/images/resize.png",
                                class:"resizeHandle"
                            }).css({
                                width:px(30),
                                height:px(30),
                                position:"absolute",
                                left:px(resizeLeft),
                                top:px(resizeTop)
                            }));

                        }
                    });
                    var categories = function(func){
                        func("images");
                        func("texts");
                        func("multiWordTexts");
                        func("inks");
                    }
                    var down = function(x,y,z,worldPos,modifiers){
                        resizing = false;
                        dragging = false;
                        originPoint = {x:x,y:y};
                        marqueeOriginX = x;
                        marqueeOriginY = y;
                        lastX = x;
                        lastY = y;
                        var threshold = 10;
                        var ray = [
                            worldPos.x - threshold,
                            worldPos.y - threshold,
                            worldPos.x + threshold,
                            worldPos.y + threshold
                        ];
                        if(intersectRect(resizeHandle,[x-threshold,y-threshold,x+threshold,y+threshold])){
                            updateStatus("Resizing");
                            resizing = true;
                        }
                        else if (!(modifiers.ctrl)){//You can't ctrl-click to drag
                            var isDragHandle = function(property){
                                return _.some(Modes.select.selected[property],function(el){
                                    if (el){
                                        return intersectRect(el.bounds,ray);
                                    } else {
                                        return false;
                                    }
                                });
                            }
                            dragging = _.some(["images","texts","inks","multiWordTexts"],isDragHandle);
                        }
                        marqueeWorldOrigin = worldPos;
                        if(dragging){
                            updateStatus("SELECT -> DRAG");
                        }
                        else if(resizing){
                            updateStatus("SELECT -> RESIZE");
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
                        var xDelta = x - lastX;
                        var yDelta = y - lastY;
                        lastX = x;
                        lastY = y;
                        if(resizing){
                            var xScale = x / resizeHandle[0];
                            var yScale = xScale;
                            if (modifiers.ctrl){
                                yScale = y / resizeHandle[0];
                            }
                            updateStatus(sprintf("Resizing %s%%",xScale * 100));
                            $(".selectionAdorner").map(function(){
                                var a = $(this);
                                a.css({
                                    width:a.data("originalWidth") * xScale,
                                    height:a.data("originalHeight") * yScale
                                });
                            });
                        }
                        else if(dragging){
                            $(".selectionAdorner").map(function(){
                                var a = $(this);
                                var left = px(parseInt(a.css("left"))+xDelta);
                                var top = px(parseInt(a.css("top"))+yDelta);
                                a.css({
                                    left:left,
                                    top:top
                                });
                            });
                        }
                        else{
                            updateMarquee(marquee,originPoint,currentPoint);
                        }
                    };
                    var up = function(x,y,z,worldPos,modifiers){
                        WorkQueue.gracefullyResume();
                        if(dragging){
                            var moved = batchTransform();
                            moved.xTranslate = worldPos.x - marqueeWorldOrigin.x;
                            moved.yTranslate = worldPos.y - marqueeWorldOrigin.y;
                            moved.inkIds = _.keys(Modes.select.selected.inks);
                            moved.textIds = _.keys(Modes.select.selected.texts);
                            moved.imageIds = _.keys(Modes.select.selected.images);
                            moved.multiWordTextIds = _.keys(Modes.select.selected.multiWordTexts);
                            dragging = false;
                            sendStanza(moved);
                        }
                        else if(resizing){
                            var resized = batchTransform();
                            var xScale = x / resizeHandle[0];
                            var totalBounds = {x:Infinity,y:Infinity};
                            _.forEach(Modes.select.selected.inks,function(ink){
                                totalBounds.x = Math.min(ink.bounds[0]);
                                totalBounds.y = Math.min(ink.bounds[1]);
                            });
                            _.forEach(Modes.select.selected.texts,function(text){
                                totalBounds.x = Math.min(text.bounds[0]);
                                totalBounds.y = Math.min(text.bounds[1]);
                            });
                            _.forEach(Modes.select.selected.images,function(image){
                                totalBounds.x = Math.min(image.bounds[0]);
                                totalBounds.y = Math.min(image.bounds[1]);
                            });
                            _.forEach(Modes.select.selected.multiWordTexts,function(image){
                                totalBounds.x = Math.min(image.bounds[0]);
                                totalBounds.y = Math.min(image.bounds[1]);
                            });
                            resized.xOrigin = totalBounds.x;
                            resized.yOrigin = totalBounds.y;
                            resized.inkIds = _.keys(Modes.select.selected.inks);
                            resized.textIds = _.keys(Modes.select.selected.texts);
                            resized.imageIds = _.keys(Modes.select.selected.images);
                            resized.multiWordTextIds = _.keys(Modes.select.selected.multiWordTexts);
                            resized.xScale = xScale;
                            if (modifiers.ctrl){
                                var yScale = y / resizeHandle[0];
                                resized.yScale = yScale;
                            } else {
                                resized.yScale = xScale;
                            }
                            sendStanza(resized);
                            resizing = false;
                        }
                        else{
                            var selectionRect = rectFromTwoPoints(marqueeWorldOrigin,worldPos);
                            var selectionBounds = [selectionRect.left,selectionRect.top,selectionRect.right,selectionRect.bottom];
                            var intersected = {
                                images:{},
                                texts:{},
                                inks:{},
                                multiWordTexts:{}
                            };
                            var intersectAuthors = {};
                            var overlapThreshold = 0.5;
                            var intersectCategory = function(category){
                                $.each(boardContent[category],function(i,item){
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
                                            default:
                                                item.bounds = [NaN,NaN,NaN,NaN];
                                            }
                                        }
                                    }
                                    var b = item.bounds;
                                    var selectionThreshold = Math.abs(overlapThreshold * ((b[2] - b[0]) * (b[3] - b[1])));
                                    var overlap = overlapRect(selectionBounds,item.bounds);
                                    if(overlap >= selectionThreshold){
                                        incrementKey(intersectAuthors,item.author);
                                        if (isAdministeringContent){
                                            if(item.author != username){
                                                intersected[category][item.identity] = item;
                                            }
                                        } else {
                                            if(item.author == username){
                                                intersected[category][item.identity] = item;
                                            }
                                        }
                                    }
                                });
                            }
                            categories(intersectCategory);
                            $.each(boardContent.highlighters,function(i,item){
                                if(intersectRect(item.bounds,selectionBounds)){
                                    incrementKey(intersectAuthors,item.author);
                                    if (isAdministeringContent){
                                        if(item.author != username){
                                            intersected.inks[item.identity] = item;
                                        }
                                    } else {
                                        if(item.author == username){
                                            intersected.inks[item.identity] = item;
                                        }
                                    }
                                }
                            });
                            if(modifiers.ctrl){
                                var toggleCategory = function(category){
                                    $.each(intersected[category],function(id,item){
                                        if(id in Modes.select.selected[category]){
                                            delete Modes.select.selected[category][id];
                                        } else {
                                            Modes.select.selected[category][id] = item;
                                        }
                                    });
                                }
                                categories(toggleCategory);
                            }
                            else{
                                Modes.select.selected = intersected;
                            }
                            var status = sprintf("Selected %s images, %s texts, %s inks, %s rich texts ",
                                                 _.keys(Modes.select.selected.images).length,
                                                 _.keys(Modes.select.selected.texts).length,
                                                 _.keys(Modes.select.selected.inks).length);
                            $.each(intersectAuthors,function(author,count){
                                status += sprintf("%s:%s ",author, count);
                            });
                            updateStatus(status);
                        }

                        Progress.call("onSelectionChanged",[Modes.select.selected]);
                        marquee.css(
                            {width:0,height:0}
                        ).hide();
                    }
                    dragging = false;
                    resizing = false;
                    registerPositionHandlers(canvasElement,down,move,up);
                },
                deactivate:function(){
                    unregisterPositionHandlers(canvasElement);
                    removeActiveMode();
                    clearSelectionFunction();
                    $("#delete").unbind("click");
                    $("#resize").unbind("click");
                    $("#selectionAdorner").empty();
                    $("#selectMarquee").hide();
                    $("#administerContent").unbind("click");
                    $("#ban").unbind("click");
                }
            }
        })(),
        zoom:{
            name:"zoom",
            activate:function(){
                Modes.currentMode.deactivate();
                Modes.currentMode = Modes.zoom;
                setActiveMode("#zoomTools","#zoomMode");
                var marquee = $("<div />",{
                    id:"zoomMarquee"
                })
                var startX = 0;
                var startY = 0;
                var startWorldPos;
                var proportion;
                var originPoint = {x:0,y:0};
                var down = function(x,y,z,worldPos){
                    //adding this so that using the zoom marquee results in the autofit being turned off.
                    takeControlOfViewbox();
                    proportion = boardHeight / boardWidth;
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
                    marquee.hide();
                    var currentPoint = {x:contentOffsetX + worldPos.x,y:contentOffsetY + worldPos.y};
                    var startingWorldPoint = {x:contentOffsetX + startWorldPos.x,y:contentOffsetY + startWorldPos.y};
                    var rect = rectFromTwoPoints(currentPoint,startingWorldPoint);
                    var hAlign = "left";
                    var vAlign = "top";
                    if (currentPoint.x == rect.left){
                        hAlign = "right";
                    }
                    if (currentPoint.y == rect.top){
                        vAlign = "bottom";
                    }
                    var constrained = aspectConstrainedRect(rect,hAlign,vAlign);
                    var vX = constrained.left;
                    var vY = constrained.top;
                    var vW = constrained.width;
                    var vH = constrained.height;
                    IncludeView.specific(vX,vY,vW,vH);
                }
                registerPositionHandlers(canvasElement,down,move,up);
            },
            deactivate:function(){
                removeActiveMode();
                $("#zoomMarquee").remove();
                unregisterPositionHandlers(canvasElement);
            }
        },
        feedback:(function(){
            var applyStateStyling = function(){
                switch(currentBackstage){
                case "quizzes":$("#quizzesButton").addClass(active);
                    break;
                case "submissions":$("#submissionButton").addClass(active);
                    break;
                default:
                    break;
                }
            };
            Progress.onConversationJoin["setConversationRole"] = function(){
                applyStateStyling();
            }
            Progress.conversationDetailsReceived["respectNewPermissions"] = applyStateStyling;
            return {
                name:"feedback",
                activate:function(){
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.feedback;
                    applyStateStyling();
                    setActiveMode("#feedbackTools","#feedbackMode");
                    var down = function(x,y,z,worldPos){
                    }
                    var move = function(x,y,z,worldPos){
                    }
                    var up = function(x,y,z,worldPos){
                    }
                    registerPositionHandlers(canvasElement,down,move,up);
                    applyStateStyling();
                },
                deactivate:function(){
                    removeActiveMode();
                    unregisterPositionHandlers(canvasElement);
                    Progress.call("onLayoutUpdated");
                }
            };
        })(),
        draw:(function(){
            var originalBrushes = Brushes.getDefaultBrushes();
            var currentBrush;
            var erasing = false;
            var hasActivated = false;
            return {
                name:"draw",
                brushes:_.map(originalBrushes,function(i){return _.clone(i);}),
                activate:function(){
                    if(Modes.currentMode == Modes.draw){
                        return;
                    }
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.draw;
                    var drawAdvancedTools = function(){};
                    var penSizeTemplate = undefined;
                    var penColorTemplate = undefined;
                    var updateOriginalBrush = function(brush){
                        Modes.draw.brushes[brush.index] = brush;
                    };
                    $(".activeBrush").removeClass("activeBrush");
                    var drawTools = function(){
                        var container = $("#drawTools");
                        _.each(container.find(".pen"),function(button,i){
                            var brush = Modes.draw.brushes[i];
                            var thisButton = $(button)
                                    .css({color:brush.color})
                                    .click(function(){
                                        $(".activeBrush").removeClass("activeBrush");
                                        $(this).addClass("activeBrush");
                                        currentBrush = brush;
                                        updateOriginalBrush(brush);
                                        Modes.draw.drawingAttributes = currentBrush;
                                        erasing = false;
                                        drawAdvancedTools(brush);
                                    });
                            thisButton.find(".widthIndicator").text(brush.width);
                            if (brush == currentBrush){
                                thisButton.addClass("activeBrush");
                            }
                        });
                    };
                    drawAdvancedTools = function(brush){
                        var dots = $("#colors .dots");
                        var bars = $("#sizes .dots");
                        var colors = Colors.getAllNamedColors();
                        var widths = Brushes.getAllBrushSizes();
                        bars.empty();
                        widths.map(function(width){
                            var sizeDot = penSizeTemplate.clone();
                            bars.append(sizeDot);
                            sizeDot.click(function(){
                                brush.width = width;
                                updateOriginalBrush(brush);
                                currentBrush = brush;
                                drawTools();
                                drawAdvancedTools(brush);
                            });
                            var bar = Canvas.circle(brush.color,width,50);
                            if (width == brush.width){
                                sizeDot.addClass("activeTool");
                            }
                            sizeDot.prepend(bar)
                            var sizeName = width.toString() + 'px';
                            sizeDot.find('.sizeDotName').append(sizeName);
                        });
                        dots.empty();
                        colors.map(function(color){
                            var colorDot = penColorTemplate.clone();
                            dots.append(colorDot);
                            colorDot.on("click",function(){
                                brush.color = color.rgb;
                                currentBrush = brush;
                                updateOriginalBrush(brush);
                                drawTools();
                                drawAdvancedTools(brush);
                            });
                            //var dot = Canvas.circle(color.rgb,50,50);
                            colorDot.css("color",color.rgb);
                            if ("rgb" in color && color.rgb == brush.color){
                                colorDot.addClass("activeTool");
                            }
                            //colorDot.prepend(dot);
                            var colorDotName = color.name;
                            colorDot.find('.colorDotName').append(colorDotName);
                        });
                        var hlButton = $("#setPenToHighlighter").unbind("click").on("click",function(){
                            brush.isHighlighter = true;
                            currentBrush = brush;
                            updateOriginalBrush(brush);
                            drawTools();
                            drawAdvancedTools(brush);
                        });
                        var penButton = $("#setPenToPen").unbind("click").on("click",function(){
                            brush.isHighlighter = false;
                            currentBrush = brush;
                            updateOriginalBrush(brush);
                            drawTools();
                            drawAdvancedTools(brush);
                        });
                        if ("isHighlighter" in currentBrush && currentBrush.isHighlighter){
                            hlButton.addClass("activeTool").addClass("active");
                            penButton.removeClass("activeTool").removeClass("active");
                        } else {
                            penButton.addClass("activeTool").addClass("active");
                            hlButton.removeClass("activeTool").removeClass("active");
                        }
                        Progress.call("onLayoutUpdated");
                    }
                    $("#resetPenButton").empty().text("reset pen").click(function(){
                        var originalBrush = _.find(originalBrushes,function(i){
                            return i.id == currentBrush.id;
                        });
                        if (originalBrush != undefined){
                            currentBrush.width = originalBrush.width;
                            currentBrush.color = originalBrush.color;
                            currentBrush.isHighlighter = originalBrush.isHighlighter;
                            drawTools();
                            drawAdvancedTools(currentBrush);
                        }
                    });
                    if(!hasActivated){
                        hasActivated = true;
                        penSizeTemplate = $("#penSize .sizeDot").clone();
                        penColorTemplate = $("#penColor .colorDot").clone();
                        currentBrush = Modes.draw.brushes[0];
                        drawTools();
                        drawAdvancedTools(currentBrush);
                        Modes.draw.drawingAttributes = currentBrush;
                        var container = $("#drawTools");
                        container.find(".eraser").click(function(button){
                            $(".activeBrush").removeClass("activeBrush");
                            $(this).addClass("activeBrush");
                            erasing = true;
                        });
                        container.find(".advancedTools").on("click",function(){
                            drawAdvancedTools(currentBrush);
                            showBackstage("customizeBrush");
                        });
                    }
                    setActiveMode("#drawTools","#drawMode");
                    var currentStroke = [];
                    var isDown = false;
                    var resumeWork;
                    var mousePressure = 256;
                    var down = function(x,y,z,worldPos,modifiers){
                        deleted = [];
                        isDown = true;
                        if(!erasing && !modifiers.eraser){
                            boardContext.strokeStyle = Modes.draw.drawingAttributes.color;
                            currentStroke = [x, y, mousePressure * z];
                        } else {
                        }
                    };
                    var raySpan = 10;
                    var deleted = [];
                    var move = function(x,y,z,worldPos,modifiers){
                        if(erasing || modifiers.eraser){
                            var ray = [worldPos.x - raySpan, worldPos.y - raySpan, worldPos.x + raySpan, worldPos.y + raySpan];
                            var markAsDeleted = function(bounds){
                                var tl = worldToScreen(bounds[0],bounds[1]);
                                var br = worldToScreen(bounds[2],bounds[3]);
                                boardContext.fillRect(tl.x,tl.y,br.x - tl.x, br.y - tl.y);
                            }
                            var deleteInRay = function(coll){
                                $.each(coll,function(i,item){
                                    if(item.author == username && intersectRect(item.bounds,ray)){
                                        delete coll[item.identity];
                                        deleted.push(item.identity);
                                        markAsDeleted(item.bounds);
                                    }
                                })
                            }
                            boardContext.globalAlpha = 0.4;
                            boardContext.fillStyle = "red";
                            deleteInRay(boardContent.inks);
                            deleteInRay(boardContent.highlighters);
                            boardContext.globalAlpha = 1.0;
                        }
                        else{
                            var oldWidth = boardContext.lineWidth;
                            var newWidth = Modes.draw.drawingAttributes.width * z;
                            boardContext.beginPath();
                            boardContext.lineCap = "round";
                            boardContext.lineWidth = newWidth;
                            var lastPoint = _.takeRight(currentStroke,3);
                            boardContext.moveTo(lastPoint[0],lastPoint[1]);
                            boardContext.lineTo(x,y);
                            boardContext.stroke();
                            currentStroke = currentStroke.concat([x,y,mousePressure * z]);
                        }
                    };
                    var up = function(x,y,z,worldPos,modifiers){
                        isDown = false;
                        if(erasing || modifiers.eraser){
                            var deleteTransform = batchTransform();
                            deleteTransform.isDeleted = true;
                            deleteTransform.inkIds = deleted;
                            sendStanza(deleteTransform);
                        } else {
                            var newWidth = Modes.draw.drawingAttributes.width * z;
                            boardContext.lineWidth = newWidth;
                            boardContext.beginPath();
                            boardContext.lineWidth = newWidth;
                            boardContext.lineCap = "round";
                            var lastPoint = _.takeRight(currentStroke,3);
                            boardContext.moveTo(lastPoint[0],lastPoint[1]);
                            boardContext.lineTo(x,y);
                            boardContext.stroke();
                            currentStroke = currentStroke.concat([x,y,mousePressure * z]);
                            strokeCollected(currentStroke.join(" "));
                        }
                    };
                    $(".activeBrush").removeClass("activeBrush");
                    if (erasing){
                        $("#drawTools").find(".eraser").addClass("activeBrush");
                    } else {
                        _.each($("#drawTools").find(".pen"),function(button,i){
                            if ((i + 1) == currentBrush.id){
                                $(button).addClass("activeBrush");
                            }
                        });
                    }
                    registerPositionHandlers(canvasElement,down,move,up);
                },
                deactivate:function(){
                    $(".activeBrush").removeClass("activeBrush");
                    removeActiveMode();
                    WorkQueue.gracefullyResume();
                    unregisterPositionHandlers(canvasElement);
                    if(window.currentBackstage == "customizeBrushPopup"){
                        window.hideBackstage();
                    }
                }
            };
        })(),
        erase:{
            activate:function(){
                Modes.currentMode.deactivate();
                Modes.currentMode = Modes.erase;
                var down = function(x,y,z,worldPos){};
                var move = function(x,y,z,worldPos){
                };
                var up = function(x,y,z,worldPos){};
                registerPositionHandlers(canvasElement,down,move,up);
            },
            deactivate:function(){
                unregisterPositionHandlers(canvasElement);
            }
        }
    }
	})(returnedBoard);
	return returnedBoard;
}
