function devLogAdd(msg){
    $("#devLog").append($("<div />",{text:msg}));
}
function devLogSet(msg){
    $("#devLog").html($("<div />",{text:msg}));
}
var incrementKey = function(coll,key){
    if(key in coll){
        coll[key]++;
    }
    else{
        coll[key] = 1;
    }
}
var highQualityInkIfAvailable = false;
var implicitlyExpanding = false;
var showGrid = false;
var expansion = 100;
var commandMode = true;
function unregisterPositionHandlers(context){
    $.each("pointerdown pointermove pointerup pointerout pointerleave pointercancel mouseup mousemove mousedown touchstart touchmove touchend touchcancelled mouseout touchleave gesturechange gesturestart".split(" "),function(i,evt){
        context.unbind(evt);
    });
    WorkQueue.gracefullyResume();
}
function progress(){
    var w = 120;
    var fallback = $("<div />").css({
        display:"inline-block",
        position:"relative",
        width:px(w),
        height:px(12),
        "background-color":"gray"
    });
    var fallbackBar = $("<span />").css({
        position:"absolute",
        width:0,
        top:0,
        left:0,
        height:"100%",
        "background-color":"green"
    }).appendTo(fallback);
    var primary = $("<progress />");
    primary.append(fallback);
    var max = 1;
    var value = 0;
    var update = function(){
        fallbackBar.css("width",sprintf("%s%%",Math.floor((value / max) * 100)));
        primary.attr({
            value:value,
            max:max
        });
    }
    var result = {
        element:primary,
        value:function(v){
            value = v;
            update();
            return result;
        },
        max:function(v){
            max = v;
            update();
            return result;
        }
    }
    return result;
}
function proportion(width,height){
    var targetWidth = boardWidth;
    var targetHeight = boardHeight;
    return (width / height) / (targetWidth / targetHeight);
}
function scaleScreenToWorld(i){
    var p = proportion(boardWidth,boardHeight);
    var scale;
    if(p > 1){//Viewbox wider than board
        scale = viewboxWidth / boardWidth;
    }
    else{//Viewbox narrower than board
        scale = viewboxHeight / boardHeight;
    }
    return i * scale;
}
function scaleWorldToScreen(i){
    var p = proportion(boardWidth,boardHeight);
    var scale;
    if(p > 1){//Viewbox wider than board
        scale = viewboxWidth / boardWidth;
    }
    else{//Viewbox narrower than board
        scale = viewboxHeight / boardHeight;
    }
    return i / scale;
}

function screenToWorld(x,y){
    var worldX = scaleScreenToWorld(x) + viewboxX;
    var worldY = scaleScreenToWorld(y) + viewboxY;
    return {x:worldX,y:worldY};
}
function worldToScreen(x,y){
    var screenX = scaleWorldToScreen(x - viewboxX);
    var screenY = scaleWorldToScreen(y - viewboxY);
    return {x:screenX,y:screenY};
}
/*
 RegisterPositionHandlers takes a set of contexts (possibly a single jquery), and handlers for down/move/up, normalizing them for touch.  Optionally, the mouse is raised when it leaves the boundaries of the context.  This is particularly to handle selection, which has 2 cooperating event sources which constantly give way to each other.
 * */

function detectPointerEvents(){
    try {
        return (("pointerEnabled" in Navigator && Navigator.pointerEnabled == true) || PointerEvent != undefined);
    } catch(e) {
        return false;
    }
}

function registerPositionHandlers(contexts,down,move,up){
    var isDown = false;

    var touchTolerance = 10;
    var noInteractableConsumed = function(worldPos,event){
        var worldRay = [
            worldPos.x - touchTolerance,
            worldPos.y - touchTolerance,
            worldPos.x + touchTolerance,
            worldPos.y + touchTolerance
        ];
        var unconsumed = true;;
        _.each(Modes.canvasInteractables,function(category,label){
            _.each(category,function(interactable){
                if(event in interactable){
                    if(interactable.activated || intersectRect(worldRay,interactable.bounds)){
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
            var performGesture = _.throttle(function(){
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
            },25);
            context.bind("pointerdown",function(e){
                var point = updatePoint(e);
                e.preventDefault();
                WorkQueue.pause();
                if (_.size(trackedTouches) == 1 && !isGesture){
                    isDown = true;
                    if(noInteractableConsumed(point.worldPos,"down")){
                        down(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
                    }
                }
            });
            context.bind("pointermove",function(e){
                var point = updatePoint(e);
                e.preventDefault();
                if (e.originalEvent.pointerType == e.POINTER_TYPE_TOUCH || e.originalEvent.pointerType == "touch" && _.size(trackedTouches) > 1){
                    performGesture();
                }
                if (_.size(trackedTouches) == 1 && !isGesture){
                    if(noInteractableConsumed(point.worldPos,"move")){
                        if(isDown){
                            move(point.x,point.y,point.z,point.worldPos,modifiers(e,point.eraser));
                        }
                    }
                }
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
                _.each(Modes.canvasInteractables,function(interactable){
                    if(interactable.deactivate){interactable.deactivate()};
                });
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
                var worldPos = screenToWorld(x,y);
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
                var worldPos = screenToWorld(x,y);
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
                var worldPos = screenToWorld(x,y);
                if(noInteractableConsumed(worldPos,"up")){
                    if(isDown){
                        up(x,y,z,worldPos,modifiers(e));
                    }
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
                    var worldPos = screenToWorld(t.x,t.y);
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
                    takeControlOfViewbox();
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
                var worldPos = screenToWorld(x,y);
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
function aspectConstrainedDimensions(width,height){
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
function aspectConstrainedRect(rect,hAlign,vAlign){ // vAlign and hAlign are strings to determine how to align the position of the aspect constrained rect within itself after adjusting the proportion.  It should be "top","bottom","center" and "left","right","center".
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
function copyBuffer(buffer){
    var tmp = document.createElement("canvas");
    tmp.width = buffer.width;
    tmp.height = buffer.height;
    tmp.getContext("2d").drawImage(buffer,
                                   0,0,buffer.width,buffer.height,
                                   0,0,buffer.width,buffer.height);
    return tmp;
}
function intersectRect(r1, r2) {//Left,top,right,bottom
    if (typeof(r1) != "undefined" && typeof(r2) != "undefined"){
        return !(r2[0] > r1[2] ||
                 r2[2] < r1[0] ||
                 r2[1] > r1[3] ||
                 r2[3] < r1[1]);
    } else {
        return false;
    }
}
function overlapRect(r1,r2){
    if(!intersectRect(r1,r2)){
        return 0;
    }
    return (Math.max(r1[0], r2[0]) - Math.min(r1[2], r2[2])) * (Math.max(r1[1], r2[1]) - Math.min(r1[3], r2[3]));
}
function rectFromTwoPoints(pointA,pointB,minimumSideLength){
    minimumSideLength = minimumSideLength || 0;
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
    var width = bottomRight.x - topLeft.x;
    var height = bottomRight.y - topLeft.y;
    if(width < minimumSideLength){
        bottomRight.x += minimumSideLength - width;
        width = bottomRight.x - topLeft.x;
    }
    if(height < minimumSideLength){
        bottomRight.y += minimumSideLength - height;
        height = bottomRight.y - topLeft.y;
    }
    return {
        left:topLeft.x,
        top:topLeft.y,
        right:bottomRight.x,
        bottom:bottomRight.y,
        width:width,
        height:height
    };
}
function updateMarquee(marquee,pointA,pointB){
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
function clampToView(point) {
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
var bounceButton = function(button){
    var b = $(button);
    var c = "activeBrush";
    b.addClass(c);
    setTimeout(function(){
        b.removeClass(c);
    },200);
}
var Modes = (function(){
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
            unregisterPositionHandlers(board);
        }
    };
    var pushCanvasInteractable = function(category,interaction){
        if(!(category in Modes.canvasInteractables)){
            Modes.canvasInteractables[category] = [];
        }
        Modes.canvasInteractables[category].push(interaction);
        blit();
    }
    return {
        currentMode:noneMode,
        none:noneMode,
        canvasInteractables:{},
        text:(function(){
            var texts = [];
            var noop = function(){};
            var fontFamilySelector, fontSizeSelector, fontColorSelector, fontBoldSelector, fontItalicSelector, fontUnderlineSelector, justifySelector,
                presetFitToText,presetRunToEdge,presetNarrow,presetWiden,presetCenterOnScreen,presetFullscreen,fontOptionsToggle,fontOptions;

            var echoesToDisregard = {};
            var createBlankText = function(worldPos){
                return Modes.text.editorFor({
                    bounds:[worldPos.x,worldPos.y,worldPos.x,worldPos.y],
                    identity:sprintf("%s_%s_%s",UserSettings.getUsername(),Date.now(),_.uniqueId()),
                    privacy:Privacy.getCurrentPrivacy(),
                    requestedWidth:300,
                    width:300,
                    height:0,
                    x:worldPos.x,
                    y:worldPos.y,
                    type:"multiWordText",
                    author:UserSettings.getUsername(),
                    words:[]
                });
            };
            $(function(){
                fontOptions= $("#textDropdowns");
                fontOptionsToggle = $("#fontOptions");
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
                var setFormattingProperty = function(prop,newValue){
                    return function(){
                        newValue = newValue || $(this).val();
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
                _.each(["red","blue","black"],function(color){
                    $(sprintf("#%sText",color)).click(setFormattingProperty("color",color));
                });
                justifySelector.change(setFormattingProperty("align"));
                presetFitToText.click(adoptPresetWidth("fitToText"));
                presetRunToEdge.click(adoptPresetWidth("runToEdge"));
                presetNarrow.click(adoptPresetWidth("narrow"));
                presetWiden.click(adoptPresetWidth("widen"));
                presetCenterOnScreen.click(adoptPresetWidth("centerOnScreen"));
                presetFullscreen.click(adoptPresetWidth("fullscreen"));
                fontOptionsToggle.click(function(){fontOptions.toggle()});
                $("#closeTextDialog").click(function(){
                    fontOptions.hide();
                });
            });
            return {
                echoesToDisregard:{},
                editorFor:function(t){
                    var editor = boardContent.multiWordTexts[t.identity];
                    if(!editor){
                        editor = boardContent.multiWordTexts[t.identity] = t;
                    }
                    if(!editor.doc){
                        var isAuthor = t.author == UserSettings.getUsername();
                        editor.doc = carota.editor.create(
                            $("<div />",{id:sprintf("t_%s",t.identity)}).appendTo($("#textInputInvisibleHost"))[0],
                            board[0],
                            isAuthor? function(){render(boardContent)} : noop);
                        if(isAuthor){
                            editor.doc.contentChanged(function(){
                                var source = boardContent.multiWordTexts[editor.identity];
                                source.privacy = Privacy.getCurrentPrivacy();
                                source.target = "presentationSpace";
                                source.slide = Conversations.getCurrentSlideJid();
                                var t = editor.doc.save();
                                if(t.length > 0){
                                    sendRichText(source);
                                    var bounds = editor.doc.calculateBounds();
                                    /*This is important to the zoom strategy*/
                                    incorporateBoardBounds(bounds);
                                }
                                Progress.call("totalSelectionChanged",[Modes.select.selected]);
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
                    editor.doc.width(t.width);
                    return editor;
                },
                draw:function(t){
                    if(t && t.doc){
                        carota.editor.paint(board[0],t.doc,true);
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
                    var threshold = 10;
                    var editorAt = function(x,y,z,worldPos){
                        var ray = [worldPos.x - threshold,worldPos.y - threshold,worldPos.x + threshold,worldPos.y + threshold];
                        var texts = _.take(_.values(boardContent.multiWordTexts).filter(function(text){
                            return intersectRect(text.doc.calculateBounds(),ray) && text.author == UserSettings.getUsername();
                        }));
                        if(texts.length > 0){
                            return texts[0];
                        }
                        else{
                            return false;
                        }
                    }
                    var contextFor = function(editor,worldPos){
                        var relativePos = {x:worldPos.x - editor.position.x, y:worldPos.y - editor.position.y};
                        var node = editor.byCoordinate(relativePos.x,relativePos.y);
                        return {
                            node:node,
                            relativePos:relativePos
                        }
                    }
                    var down = function(x,y,z,worldPos){
                        var editor = editorAt(x,y,z,worldPos).doc;
                        if (editor){
                            editor.isActive = true;
                            editor.caretVisible = true;
                            editor.mousedownHandler(contextFor(editor,worldPos).node);
                        };
                    }
                    var move = function(x,y,z,worldPos){
                        var editor = editorAt(x,y,z,worldPos).doc;
                        if (editor){
                            editor.mousemoveHandler(contextFor(editor,worldPos).node);
                        }
                    };
                    var up = function(x,y,z,worldPos){
                        var clickTime = Date.now();
                        var editor = editorAt(x,y,z,worldPos);
                        _.each(boardContent.multiWordTexts,function(t){
                            t.doc.isActive = t.doc.identity == editor.identity;
                            if(t.doc.save().length == 0){
                                delete boardContent.multiWordTexts[t.identity];
                            }
                        });
                        var sel;
                        Modes.select.clearSelection();
                        if (editor){
                            var doc = editor.doc;
                            var context = contextFor(doc,worldPos);
                            if(clickTime - lastClick <= doubleClickThreshold){
                                doc.dblclickHandler(context.node);
                            }
                            lastClick = clickTime;
                            sel = {
                                multiWordTexts:{}
                            };
                            sel.multiWordTexts[editor.identity] = editor;
                            Modes.select.setSelection(sel);
                            doc.mouseupHandler(context.node);
                        } else {
                            var newEditor = createBlankText(worldPos);
                            var newDoc = newEditor.doc;
                            newDoc.load([]);
                            newDoc.select(0,0);
                            sel = {
                                multiWordTexts:{}
                            };
                            sel.multiWordTexts[newEditor.identity] = boardContent.multiWordTexts[newEditor.identity];
                            Modes.select.setSelection(sel);
                            newDoc.mouseupHandler(newDoc.byOrdinal(0));
                        }
                        Progress.historyReceived["ClearMultiTextEchoes"] = function(){
                            Modes.text.echoesToDisregard = {};
                        };
                        Modes.select.addHandles();
                        Progress.call("onSelectionChanged",[Modes.select.selected]);
                    };
                    registerPositionHandlers(board,down,move,up);
                },
                deactivate:function(){
                    Modes.select.clearSelection();
                    removeActiveMode();
                    fontOptions.hide();
                    unregisterPositionHandlers(board);
                    _.each(boardContent.multiWordTexts,function(t){
                        t.doc.isActive = false;
                        if(t.doc.save().length == 0){
                            delete boardContent.multiWordTexts[t.identity];
                        }
                    });
                    /*Necessary to ensure that no carets or marquees remain on the editors*/
                    blit();
                }
            }
        })(),
        image:(function(){
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
            var clientSideProcessImage = function(onComplete){
                if (currentImage == undefined || currentImage.fileUpload == undefined || onComplete == undefined){
                    return;
                }
                $("#imageWorking").show();
                $("#imageFileChoice").hide();
                var reader = new FileReader();
                reader.onload = function(readerE){
                    var renderCanvas = $("<canvas/>");
                    var img = new Image();
                    var originalSrc = readerE.target.result;
                    var originalSize = originalSrc.length;
                    img.onload = function(e){
                        var width = img.width;
                        var height = img.height;
                        var dims = imageModes.getResizeFunction()(width,height);
                        var w = dims.w;
                        var h = dims.h;
                        var quality = dims.q;
                        renderCanvas.width = w;
                        renderCanvas.height = h;
                        renderCanvas.attr("width",w);
                        renderCanvas.attr("height",h);
                        renderCanvas.css({
                            width:px(w),
                            height:px(h)
                        });
                        currentImage.width = w;
                        currentImage.height = h;
                        renderCanvas[0].getContext("2d").drawImage(img,0,0,w,h);
                        currentImage.resizedImage = renderCanvas[0].toDataURL("image/jpeg",quality);
                        var newSize = currentImage.resizedImage.length;
                        console.log("original => resized",originalSize,newSize);
                        if (originalSize < newSize){
                            currentImage.resizedImage = originalSrc;
                        }
                        onComplete();
                    };
                    img.src = originalSrc;
                }
                reader.readAsDataURL(currentImage.fileUpload);
            };
            var sendImageToServer = function(){
                if (currentImage.type == "imageDefinition"){
                    WorkQueue.pause();
                    var worldPos = {x:currentImage.x,y:currentImage.y};
                    var screenPos= {x:currentImage.screenX,y:currentImage.screenY};
                    var t = Date.now();
                    var identity = sprintf("%s%s",UserSettings.getUsername(),t);
                    var currentSlide = Conversations.getCurrentSlideJid();
                    var url = sprintf("/uploadDataUri?jid=%s&filename=%s",currentSlide.toString(),encodeURI(identity));
                    $.ajax({
                        url: url,
                        type: 'POST',
                        success: function(e){
                            var newIdentity = $(e).find("resourceUrl").text();
                            var imageStanza = {
                                type:"image",
                                author:UserSettings.getUsername(),
                                timestamp:t,
                                tag:"{\"author\":\""+UserSettings.getUsername()+"\",\"privacy\":\""+Privacy.getCurrentPrivacy()+"\",\"id\":\""+newIdentity+"\",\"isBackground\":false,\"zIndex\":0,\"timestamp\":-1}",
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
                            updateTracking(newIdentity,function(){
                                var roomForControls = 50
                                var newX = Math.min(imageStanza.x,viewboxX);
                                var newY = Math.min(imageStanza.y,viewboxY);
                                var newW = Math.max(imageStanza.x + imageStanza.width,viewboxWidth);
                                var newH = Math.max(imageStanza.y + imageStanza.height,viewboxHeight);
                                Modes.select.activate();
                                Modes.select.selected.images[imageStanza.identity] = imageStanza;
                                IncludeView.specific(newX,newY,newW + roomForControls,newH + roomForControls);
                            });
                            sendStanza(imageStanza);
                            resetImageUpload();
                            WorkQueue.gracefullyResume();
                        },
                        error: function(e){
                            console.log(e);
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
                    insertOptions = $("#imageInsertOptions").css({position:"absolute",left:0,top:0});
                    insertOptionsClose = $("#imageInsertOptionsClose").click(Modes.select.activate);
                    imageFileChoice = $("#imageFileChoice").attr("accept","image/*");
                    imageFileChoice[0].addEventListener("change",function(e){
                        var files = e.target.files || e.dataTransfer.files;
                        var limit = files.length;
                        var file = files[0];
                        if (file.type.indexOf("image") == 0) {
                            currentImage.fileUpload = file;
                        }
                        clientSideProcessImage(sendImageToServer);
                    },false);
                    resetImageUpload();
                }
            });
            return {
                activate:function(){
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.image;
                    setActiveMode("#imageTools","#imageMode");
                    var x = 10;
                    var y = 10;
                    var worldPos = screenToWorld(x,y);
                    currentImage = {
                        "type":"imageDefinition",
                        "screenX":x,
                        "screenY":y,
                        "x":worldPos.x,
                        "y":worldPos.y
                    }
                    Progress.call("onLayoutUpdated");
                    imageModes.reapplyVisualStyle();
                    insertOptions.show();
                },
                deactivate:function(){
                    resetImageUpload();
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
                registerPositionHandlers(board,down,move,up);
            },
            deactivate:function(){
                removeActiveMode();
                unregisterPositionHandlers(board);
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
                        _.forEach(boardContent.multiWordTexts,function(text){
                            text.bounds = text.doc.calculateBounds();
                        });
                    }
                }
            };
            var removeHandles = function(){
                _.each(["resizeFree","resizeAspectLocked","manualMove"],function(key){
                    delete Modes.canvasInteractables[key];
                    delete Progress.totalSelectionChanged[key];
                    delete Progress.onSelectionChanged[key];
                    delete Progress.onViewboxChanged[key];
                });
            };
            var clearSelectionFunction = function(){
                Modes.select.selected = {images:{},text:{},inks:{},multiWordTexts:{}};
                removeHandles();
                Progress.call("onSelectionChanged",[Modes.select.selected]);
            }
            var updateSelectionWhenBoardChanges = _.debounce(function(){
                var changed = false;
                _.forEach(["images","texts","inks","highlighters","multiWordTexts"],function(catName){
                    var selCatName = catName == "highlighters" ? "inks" : catName;
                    var boardCatName = catName;
                    if (Modes && Modes.select && Modes.select.selected && selCatName in Modes.select.selected){
                        var cat = Modes.select.selected[selCatName];
                        _.forEach(cat,function(i){
                            if (cat && boardCatName in boardContent && i.identity in boardContent[boardCatName]){
                                cat[i.identity] = boardContent[boardCatName][i.identity];
                            } else {
                                changed = true;
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
                if(changed){
                    Progress.call("onSelectionChanged",[Modes.select.selected]);
                }
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
                resizeHandleSize:20,
                setSelection:function(selected){
                    Modes.select.selected = _.merge(Modes.select.selected,selected);
                },
                handlesAtZoom:function(){
                    var zoom = scale();
                    return Modes.select.resizeHandleSize / zoom;
                },
                addHandles:function(){
                    removeHandles();
                    var attrs = {opacity:0};
                    var handleAlpha = 0.3;
                    var fadeInInterval = 1000;
                    var tween = new TWEEN.Tween(attrs).delay(fadeInInterval).to({opacity:handleAlpha},fadeInInterval);
                    var tick = function(){
                        TWEEN.update();
                        blit();
                        if(attrs.opacity < handleAlpha){
                            requestAnimationFrame(tick);
                        }
                    };
                    tick();
                    tween.start();
                    var s = Modes.select.handlesAtZoom();
                    var blitAfterDelay = function(f){
                        return function(){
                            setTimeout(function(){
                                f();
                                blit();
                            },fadeInInterval);
                        }
                    }
                    var blitImmediately = function(f){
                        return function(){
                            f();
                            blit();
                        };
                    }
                    var minimumXSpan = Modes.select.resizeHandleSize;
                    var minimumYSpan = Modes.select.resizeHandleSize;
                    var manualMove = (
                        function(){
                            var rehome = function(root){
                                if(!manualMove.activated){
                                    root = root || Modes.select.totalSelectedBounds();
                                    var s = Modes.select.handlesAtZoom();
                                    var x = root.x;
                                    var y = root.y;
                                    var width = root.x2 - root.x;
                                    var center = root.x + s / 2;
                                    manualMove.bounds = [
                                        center - s / 2,
                                        root.y - s,
                                        center + s / 2,
                                        root.y
                                    ];
                                }
                            }
                            Progress.onSelectionChanged["manualMove"] = blitImmediately(rehome);
                            Progress.totalSelectionChanged["manualMove"] = blitAfterDelay(rehome);
                            return {
                                activated:false,
                                originalHeight:1,
                                originalWidth:1,
                                down:function(worldPos){
                                    manualMove.activated = true;
                                    Modes.select.dragging = true;
                                    Modes.select.resizing = false;
                                    var root = Modes.select.totalSelectedBounds();
                                    Modes.select.offset = worldPos;
                                    Modes.select.marqueeWorldOrigin = worldPos;
                                    blit();
                                    return false;
                                },
                                move:function(worldPos){
                                    if(manualMove.activated){
                                        manualMove.bounds = [
                                            worldPos.x - s,
                                            worldPos.y,
                                            worldPos.x + s,
                                            worldPos.y
                                        ];
                                        Modes.select.offset = worldPos;
                                        blit();
                                    }
                                    return false;
                                },
                                deactivate:function(){
                                    manualMove.activated = false;
                                    Modes.select.dragging = false;
                                },
                                up:function(worldPos){
                                    manualMove.deactivate();
                                    var moved = batchTransform();
                                    var xDelta = worldPos.x - Modes.select.marqueeWorldOrigin.x;
                                    var yDelta = worldPos.y - Modes.select.marqueeWorldOrigin.y;
                                    moved.xTranslate = xDelta;
                                    moved.yTranslate = yDelta;
                                    moved.inkIds = _.keys(Modes.select.selected.inks);
                                    moved.textIds = _.keys(Modes.select.selected.texts);
                                    moved.imageIds = _.keys(Modes.select.selected.images);
                                    moved.multiWordTextIds = _.keys(Modes.select.selected.multiWordTexts);
                                    Modes.select.dragging = false;
                                    sendStanza(moved);
                                    blit();
                                    var root = Modes.select.totalSelectedBounds();
                                    Progress.call("totalSelectionChanged",[{
                                        x:root.x + xDelta,
                                        y:root.y + yDelta,
                                        x2:root.x2 + xDelta,
                                        y2:root.y2 + yDelta
                                    }]);
                                    return false;
                                },
                                render:function(canvasContext){
                                    if(manualMove.bounds){
                                        var tl = worldToScreen(manualMove.bounds[0],manualMove.bounds[1]);
                                        var br = worldToScreen(manualMove.bounds[2],manualMove.bounds[3]);
                                        var size = br.x - tl.x;
                                        var x = tl.x;
                                        var y = tl.y;
                                        canvasContext.globalAlpha = attrs.opacity;
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
                    var resizeAspectLocked = (
                        function(){
                            var rehome = function(root){
                                if(!resizeAspectLocked.activated){
                                    root = root || Modes.select.totalSelectedBounds();
                                    var s = Modes.select.handlesAtZoom();
                                    var x = root.x2;
                                    var y = root.y;
                                    root.br = root.br || {x:scaleWorldToScreen(root.x2)};
                                    root.tl = root.tl || {x:scaleWorldToScreen(root.x)};
                                    if(root.br.x - root.tl.x < minimumXSpan){
                                        x = root.x + scaleScreenToWorld(minimumXSpan)
                                    }
                                    resizeAspectLocked.bounds = [
                                        x,
                                        y,
                                        x + s,
                                        y + s
                                    ];
                                }
                            }
                            Progress.onSelectionChanged["resizeAspectLocked"] = blitImmediately(rehome);
                            Progress.totalSelectionChanged["resizeAspectLocked"] = blitAfterDelay(rehome);
                            return {
                                activated:false,
                                originalHeight:1,
                                originalWidth:1,
                                down:function(worldPos){
                                    Modes.select.aspectLocked = true;
                                    resizeAspectLocked.activated = true;
                                    Modes.select.dragging = false;
                                    Modes.select.resizing = true;
                                    var root = Modes.select.totalSelectedBounds();
                                    Modes.select.offset = {x:root.x2,y:root.y2};
                                    blit();
                                    return false;
                                },
                                move:function(worldPos){
                                    if(resizeAspectLocked.activated){
                                        resizeAspectLocked.bounds = [
                                            worldPos.x - s,
                                            resizeAspectLocked.bounds[1],
                                            worldPos.x + s,
                                            resizeAspectLocked.bounds[3]
                                        ];
                                        var totalBounds = Modes.select.totalSelectedBounds();
                                        var originalWidth = totalBounds.x2 - totalBounds.x;
                                        var requestedWidth = worldPos.x - totalBounds.x;
                                        var xScale = requestedWidth / originalWidth;
                                        Modes.select.offset = {
                                            x:worldPos.x,
                                            y:totalBounds.y + totalBounds.height * xScale
                                        };
                                        blit();
                                    }
                                    return false;
                                },
                                deactivate:function(){
                                    Modes.select.aspectLocked = false;
                                    resizeAspectLocked.activated = false;
                                    Modes.select.resizing = false;
                                },
                                up:function(worldPos){
                                    resizeAspectLocked.deactivate();
                                    var resized = batchTransform();
                                    var totalBounds = Modes.select.totalSelectedBounds();
                                    var originalWidth = totalBounds.x2 - totalBounds.x;
                                    var requestedWidth = worldPos.x - totalBounds.x;
                                    resized.xScale = requestedWidth / originalWidth;
                                    resized.yScale = resized.xScale;
                                    resized.xOrigin = totalBounds.x;
                                    resized.yOrigin = totalBounds.y;
                                    resized.inkIds = _.keys(Modes.select.selected.inks);
                                    resized.textIds = _.keys(Modes.select.selected.texts);
                                    resized.imageIds = _.keys(Modes.select.selected.images);
                                    resized.multiWordTextIds = _.keys(Modes.select.selected.multiWordTexts);
                                    sendStanza(resized);
                                    var root = Modes.select.totalSelectedBounds();
                                    Progress.call("totalSelectionChanged",[{
                                        x:root.x,
                                        y:root.y,
                                        x2:root.x + root.width * resized.xScale,
                                        y2:root.y + root.height * resized.yScale
                                    }]);
                                    blit();
                                    return false;
                                },
                                render:function(canvasContext){
                                    if(resizeAspectLocked.bounds){
                                        var tl = worldToScreen(resizeAspectLocked.bounds[0],resizeAspectLocked.bounds[1]);
                                        var br = worldToScreen(resizeAspectLocked.bounds[2],resizeAspectLocked.bounds[3]);
                                        var size = br.x - tl.x;
                                        var inset = size / 10;
                                        var xOffset = -1 * size;
                                        var yOffset = -1 * size;
                                        var rot = 90;
                                        canvasContext.globalAlpha = attrs.opacity;
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
                    var resizeFree = (function(){
                        var rehome = function(root){
                            if(!resizeFree.activated){
                                root = root || Modes.select.totalSelectedBounds();
                                var s = Modes.select.handlesAtZoom();
                                var x = root.x2;
                                var y = root.y2;
                                root.br = root.br || {x:scaleWorldToScreen(root.x2)};
                                root.tl = root.tl || {x:scaleWorldToScreen(root.x)};
                                if(root.br.x - root.tl.x < minimumXSpan){
                                    x = root.x + scaleScreenToWorld(minimumXSpan)
                                }
                                if(root.br.y - root.tl.y < minimumYSpan){
                                    y = root.y + scaleScreenToWorld(minimumYSpan)
                                }
                                resizeFree.bounds = [
                                    x,
                                    y - s,
                                    x + s,
                                    y
                                ];
                            }
                        }
                        Progress.onSelectionChanged["resizeFree"] = blitImmediately(rehome);
                        Progress.totalSelectionChanged["resizeFree"] = blitAfterDelay(rehome);
                        return {
                            activated:false,
                            down:function(worldPos){
                                resizeFree.activated = true;
                                Modes.select.dragging = false;
                                Modes.select.resizing = true;
                                Modes.select.aspectLocked = false;
                                var root = Modes.select.totalSelectedBounds();
                                Modes.select.offset = {x:root.x2,y:root.y2};
                                blit();
                                return false;
                            },
                            move:function(worldPos){
                                if(resizeFree.activated){
                                    resizeFree.bounds = [
                                        worldPos.x - s,
                                        worldPos.y - s,
                                        worldPos.x + s,
                                        worldPos.y + s
                                    ];
                                    Modes.select.offset = {x:worldPos.x,y:worldPos.y};
                                    blit();
                                }
                                return false;
                            },
                            deactivate:function(){
                                resizeFree.activated = false;
                                Modes.select.resizing = false;
                            },
                            up:function(worldPos){
                                resizeFree.deactivate();
                                var resized = batchTransform();
                                var totalBounds = Modes.select.totalSelectedBounds();
                                var originalWidth = totalBounds.x2 - totalBounds.x;
                                var originalHeight = totalBounds.y2 - totalBounds.y;
                                var requestedWidth = worldPos.x - totalBounds.x;
                                var requestedHeight = worldPos.y - totalBounds.y;
                                resized.xScale = requestedWidth / originalWidth;
                                resized.yScale = requestedHeight / originalHeight;
                                resized.xOrigin = totalBounds.x;
                                resized.yOrigin = totalBounds.y;
                                resized.inkIds = _.keys(Modes.select.selected.inks);
                                resized.textIds = _.keys(Modes.select.selected.texts);
                                resized.imageIds = _.keys(Modes.select.selected.images);
                                /*resized.multiWordTextIds = _.keys(Modes.select.selected.multiWordTexts);*/
                                _.each(Modes.select.selected.multiWordTexts,function(word){
                                    word.doc.width(word.doc.width() * resized.xScale);
                                    sendRichText(word);
                                });
                                var root = Modes.select.totalSelectedBounds();
                                Progress.call("totalSelectionChanged",[{
                                    x:root.x,
                                    y:root.y,
                                    x2:root.x + root.width * resized.xScale,
                                    y2:root.y + root.height * resized.yScale
                                }]);
                                sendStanza(resized);
                                blit();
                                return false;
                            },
                            render:function(canvasContext){
                                if(resizeFree.bounds){
                                    var tl = worldToScreen(resizeFree.bounds[0],resizeFree.bounds[1]);
                                    var br = worldToScreen(resizeFree.bounds[2],resizeFree.bounds[3]);
                                    var size = br.x - tl.x;
                                    var inset = size / 10;
                                    var xOffset = -1 * size;
                                    var yOffset = -1 * size;
                                    var rot = 90;
                                    canvasContext.globalAlpha = attrs.opacity;
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
                    pushCanvasInteractable("manualMove",manualMove);
                    pushCanvasInteractable("resizeFree",resizeFree);
                    pushCanvasInteractable("resizeAspectLocked",resizeAspectLocked);
                },
                totalSelectedBounds:Bench.track("Modes.select.totalSelectedBounds",function(){
                    var totalBounds = {x:Infinity,y:Infinity,x2:-Infinity,y2:-Infinity};
                    var incorporate = function(item){
                        var bounds = item.bounds;
                        totalBounds.x = Math.min(totalBounds.x,bounds[0]);
                        totalBounds.y = Math.min(totalBounds.y,bounds[1]);
                        totalBounds.x2 = Math.max(totalBounds.x2,bounds[2]);
                        totalBounds.y2 = Math.max(totalBounds.y2,bounds[3]);
                    };
                    _.forEach(Modes.select.selected.inks,incorporate);
                    _.forEach(Modes.select.selected.texts,incorporate);
                    _.forEach(Modes.select.selected.images,incorporate);
                    _.forEach(Modes.select.selected.multiWordTexts,function(text){
                        if(!_.reduce(text.bounds,function(item,acc){return item + acc})){
                            text.bounds = text.doc.calculateBounds();
                        }
                        incorporate(text);
                    });
                    totalBounds.width = totalBounds.x2 - totalBounds.x;
                    totalBounds.height = totalBounds.y2 - totalBounds.y;
                    totalBounds.tl = worldToScreen(totalBounds.x,totalBounds.y);
                    totalBounds.br = worldToScreen(totalBounds.x2,totalBounds.y2);
                    return totalBounds;
                }),
                offset:{x:0,y:0},
                marqueeWorldOrigin:{x:0,y:0},
                resizing:false,
                dragging:false,
                aspectLocked:false,
                clearSelection:clearSelectionFunction,
                activate:function(){
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.select;
                    setActiveMode("#selectTools","#selectMode");
                    var originPoint = {x:0,y:0};
                    var marqueeOriginX;
                    var marqueeOriginY;
                    var marquee = $("<div/>",{
                        id:"selectMarquee"
                    });
                    var adorner = $("#selectionAdorner");
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
                    $("#administerContent").bind("click",administerContentFunction);
                    $("#ban").bind("click",banContentFunction);
                    var categories = function(func){
                        func("images");
                        func("texts");
                        func("multiWordTexts");
                        func("inks");
                    }
                    var down = function(x,y,z,worldPos,modifiers){
                        Modes.select.resizing = false;
                        Modes.select.dragging = false;
                        originPoint = {x:x,y:y};
                        marqueeOriginX = x;
                        marqueeOriginY = y;
                        Modes.select.marqueeWorldOrigin = worldPos;
                        if (!(modifiers.ctrl)){
                            var tb = Modes.select.totalSelectedBounds();
                            if(tb.x != Infinity){
                                var threshold = 10 / scale();
                                var ray = [
                                    worldPos.x - threshold,
                                    worldPos.y - threshold,
                                    worldPos.x + threshold,
                                    worldPos.y + threshold
                                ];
                                var isDragHandle = function(property){
                                    return _.some(Modes.select.selected[property],function(el){
                                        if (el){
                                            return intersectRect(el.bounds,ray);
                                        } else {
                                            return false;
                                        }
                                    });
                                }
                                Modes.select.dragging = _.some(["images","texts","inks","multiWordTexts"],isDragHandle);
                            }
                        }
                        if(Modes.select.dragging){
                            Modes.select.offset = worldPos;
                            updateStatus("SELECT -> DRAG");

                        }
                        else if(Modes.select.resizing){
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
                        Modes.select.offset = worldPos;
                        if(Modes.select.dragging){
                            blit();
                        }
                        else if(Modes.select.resizing){
                            blit();
                        }
                        else{
                            updateMarquee(marquee,originPoint,currentPoint);
                        }
                    };
                    var up = function(x,y,z,worldPos,modifiers){
                        WorkQueue.gracefullyResume();
                        var xDelta = worldPos.x - Modes.select.marqueeWorldOrigin.x;
                        var yDelta = worldPos.y - Modes.select.marqueeWorldOrigin.y;
                        var dragThreshold = 15;
                        if(Math.abs(xDelta) + Math.abs(yDelta) < dragThreshold){
                            Modes.select.dragging = false;
                        }
                        if(Modes.select.dragging){
                            var root = Modes.select.totalSelectedBounds();
                            Progress.call("totalSelectionChanged",[{
                                x:root.x + xDelta,
                                y:root.y + yDelta,
                                x2:root.x2 + xDelta,
                                y2:root.y2 + yDelta
                            }]);
                            var moved = batchTransform();
                            moved.xTranslate = xDelta;
                            moved.yTranslate = yDelta;
                            moved.inkIds = _.keys(Modes.select.selected.inks);
                            moved.textIds = _.keys(Modes.select.selected.texts);
                            moved.imageIds = _.keys(Modes.select.selected.images);
                            moved.multiWordTextIds = _.keys(Modes.select.selected.multiWordTexts);
                            Modes.select.dragging = false;
                            sendStanza(moved);
                        }
                        else{
                            var selectionRect = rectFromTwoPoints(Modes.select.marqueeWorldOrigin,worldPos,2);
                            var selectionBounds = [selectionRect.left,selectionRect.top,selectionRect.right,selectionRect.bottom];
                            var intersected = {
                                images:{},
                                texts:{},
                                inks:{},
                                multiWordTexts:{}
                            };
                            var intersectAuthors = {};
                            var intersections = {};
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
                                    var selectionThreshold = 1;
                                    var overlap = overlapRect(selectionBounds,item.bounds);
                                    if(overlap >= selectionThreshold){
                                        incrementKey(intersectAuthors,item.author);
                                        incrementKey(intersections,"any");
                                        if (isAdministeringContent){
                                            if(item.author != UserSettings.getUsername()){
                                                intersected[category][item.identity] = item;
                                            }
                                        } else {
                                            if(item.author == UserSettings.getUsername()){
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
                                        if(item.author != UserSettings.getUsername()){
                                            intersected.inks[item.identity] = item;
                                        }
                                    } else {
                                        if(item.author == UserSettings.getUsername()){
                                            intersected.inks[item.identity] = item;
                                        }
                                    }
                                }
                            });
                            /*Default behaviour is now to toggle rather than clear.  Ctrl-clicking doesn't do anything different*/
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
                            if(!intersections.any){
                                Modes.select.clearSelection();
                            }
                            else{
                                Modes.select.addHandles();
                            }
                            var status = sprintf("Selected %s images, %s texts, %s inks, %s rich texts ",
                                                 _.keys(Modes.select.selected.images).length,
                                                 _.keys(Modes.select.selected.texts).length,
                                                 _.keys(Modes.select.selected.multiWordTexts).length,
                                                 _.keys(Modes.select.selected.inks).length);
                            $.each(intersectAuthors,function(author,count){
                                status += sprintf("%s:%s ",author, count);
                            });
                            Progress.call("onSelectionChanged",[Modes.select.selected]);
                        }
                        marquee.css(
                            {width:0,height:0}
                        ).hide();
                        blit();
                    }
                    Modes.select.dragging = false;
                    Modes.select.resizing = false;
                    registerPositionHandlers(board,down,move,up);
                },
                deactivate:function(){
                    unregisterPositionHandlers(board);
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
                    var touchWidth = 50;
                    var tooSmallToUse = touchWidth * touchWidth;
                    marquee.hide();
                    var currentPoint = {x:contentOffsetX + worldPos.x,y:contentOffsetY + worldPos.y};
                    var startingWorldPoint = {x:contentOffsetX + startWorldPos.x,y:contentOffsetY + startWorldPos.y};
                    var rect = rectFromTwoPoints(currentPoint,startingWorldPoint);
                    var touchArea = scale() * rect.width * rect.height;
                    if(touchArea < tooSmallToUse){
                        return;
                    }
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
                registerPositionHandlers(board,down,move,up);
            },
            deactivate:function(){
                removeActiveMode();
                $("#zoomMarquee").remove();
                unregisterPositionHandlers(board);
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
                    registerPositionHandlers(board,down,move,up);
                    applyStateStyling();
                },
                deactivate:function(){
                    removeActiveMode();
                    unregisterPositionHandlers(board);
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
                    boardContext.setLineDash([]);
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
                        _.each(container.find(".modeSpecificTool.pen"),function(button,i){
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
                    $("#resetPenButton").click(function(){
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
                            $("#drawDropdowns").toggle();
                        });
                        $("#closePenDialog").click(function(){
                            $("#drawDropdowns").hide();
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
                                    if(item.author == UserSettings.getUsername() && intersectRect(item.bounds,ray)){
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
                    registerPositionHandlers(board,down,move,up);
                },
                deactivate:function(){
                    $(".activeBrush").removeClass("activeBrush");
		    $("#drawDropdowns").hide();
                    removeActiveMode();
                    WorkQueue.gracefullyResume();
                    unregisterPositionHandlers(board);
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
                registerPositionHandlers(board,down,move,up);
            },
            deactivate:function(){
                unregisterPositionHandlers(board);
            }
        }
    }
})();
