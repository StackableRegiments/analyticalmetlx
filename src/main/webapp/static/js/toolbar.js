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
                    //console.log("scaling:",previousScale,currentScale);
                    Zoom.scale(previousScale / currentScale);
                }
            },25);
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
function rectFromTwoPoints(pointA,pointB){
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
function drawSelectionBounds(item){
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
    return {
        currentMode:noneMode,
        none:noneMode,
        text:(function(){
            var selectedTexts = [];
            var texts = [];
            var noop = function(){};
            var createBlankText = function(screenPos){
                var w = 150;
                var h = 50;
                richTextReceived({
                    bounds:[screenPos.x,screenPos.y,screenPos.x + w,screenPos.y + h],
                    identity:_.uniqueId(),
                    width:w,
                    height:h,
                    x:screenPos.x,
                    y:screenPos.y,
                    author:UserSettings.getUsername(),
                    runs:[
                        {text:"Sample run"},
                        {text:"30 pt run",size:30},
                        {text:"orange run",color:"orange"}
                    ]
                });
            };
            var editText = function(editor){
                editor.hasFocus = true;
                console.log("editText",editor);
            }
            return {
                create:function(t){
                    var doc = carota.editor.create(
                        $("#textInputInvisibleHost")[0],
                        board[0],
                        function(){render(boardContent)});
                    doc.position = {x:t.x,y:t.y};
                    doc.contentChanged(function(){
                        console.log(doc);
                        var fb = doc.frame.bounds();
                        if(fb){
                            t.bounds = [
                                doc.position.x,
                                doc.position.y,
                                doc.position.x+fb.w,
                                doc.position.y+fb.h];
                        }
                        console.log(t.bounds);
                    });
                    doc.load(t.runs);
                    t.doc = doc;
                    boardContent.richTexts[t.identity] = t;
                },
                draw:function(t){
                    carota.editor.paint(board[0],t.doc,true);
                },
                activate:function(){
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.text;
                    setActiveMode("#textTools","#insertText");
                    $(".activeBrush").removeClass("activeBrush");
                    Progress.call("onLayoutUpdated");
                    var up = function(x,y,z,worldPos){
                        var threshold = 10;
                        var ray = [worldPos.x - threshold,worldPos.y - threshold,worldPos.x + threshold,worldPos.y + threshold];
                        selectedTexts = _.values(boardContent.richTexts).filter(function(text){
                            return intersectRect(text.bounds,ray) && text.author == UserSettings.getUsername();
                        });
                        if (selectedTexts.length > 0){
                            currentText = selectedTexts[0];
			    console.log("Selected text",currentText);
			    editText(currentText);
                        } else {
                            var newText = createBlankText(worldPos);
                        }
                        Progress.call("onSelectionChanged",[Modes.select.selected]);
                    }
                    registerPositionHandlers(board,noop,noop,up);
                },
                deactivate:function(){
                    selectedTexts = [];
                    $("#selectionAdorner").empty();
                    unregisterPositionHandlers(board);
                    removeActiveMode();
                }
            };
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
                imageFileChoice.wrap("<form>").closest("form").get(0).reset();
                imageUploadThumbnail[0].getContext("2d").clearRect(0,0,imageUploadThumbnail.width(),imageUploadThumbnail.height());
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
                    var identity = sprintf("%s%s",UserSettings.getUsername(),t);
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
                            resetImageUpload();
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
                    registerPositionHandlers(board,noop,noop,up);
                },
                deactivate:function(){
                    resetImageUpload();
                    unregisterPositionHandlers(board);
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

                    if (Conversations.shouldModifyConversation()){
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
                        _.forEach(["images","texts","inks","highlighters"],function(category){
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
                Modes.select.selected = {images:{},text:{},inks:{}};
                Progress.call("onSelectionChanged",[Modes.select.selected]);
            }
            var updateSelectionWhenBoardChanges = _.debounce(function(){
                _.forEach(["images","texts","inks","highlighters"],function(catName){
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
                    inks:{}
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
                            _.values(Modes.select.selected.inks)]);
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
                            dragging = _.some(["images","texts","inks"],isDragHandle);
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
                            resized.xOrigin = totalBounds.x;
                            resized.yOrigin = totalBounds.y;
                            resized.inkIds = _.keys(Modes.select.selected.inks);
                            resized.textIds = _.keys(Modes.select.selected.texts);
                            resized.imageIds = _.keys(Modes.select.selected.images);
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
                                inks:{}
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
                                        //if(intersectRect(item.bounds,selectionBounds)){
                                        incrementKey(intersectAuthors,item.author);
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
                            var status = sprintf("Selected %s images, %s texts, %s inks ",
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
                            console.log("stroke collected:",currentStroke);
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
