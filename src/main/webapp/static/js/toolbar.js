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
    $.each("mouseup mousemove mousedown touchstart touchmove touchend touchcancelled mouseout touchleave gesturechange gesturestart".split(" "),function(i,evt){
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
function screenToWorld(x,y){
    var p = proportion(boardWidth,boardHeight);
    var scale;
    if(p > 1){//Viewbox wider than board
        scale = viewboxWidth / boardWidth;
    }
    else{//Viewbox narrower than board
        scale = viewboxHeight / boardHeight;
    }
    var worldX = x * scale + viewboxX;
    var worldY = y * scale + viewboxY;
    return {x:worldX,y:worldY};
}
function worldToScreen(x,y){
    var p = proportion(boardWidth,boardHeight);
    var scale;
    if(p > 1){//Viewbox wider than board
        scale = viewboxWidth / boardWidth;
    }
    else{//Viewbox narrower than board
        scale = viewboxHeight / boardHeight;
    }
    var screenX = (x - viewboxX) / scale;
    var screenY = (y - viewboxY) / scale;
    return {x:screenX,y:screenY};
}
/*
 RegisterPositionHandlers takes a set of contexts (possibly a single jquery), and handlers for down/move/up, normalizing them for touch.  Optionally, the mouse is raised when it leaves the boundaries of the context.  This is particularly to handle selection, which has 2 cooperating event sources which constantly give way to each other.
 * */

var throttledBlit = _.debounce(blit,50);
function registerPositionHandlers(contexts,down,move,up){
    var isDown = false;
    var modifiers = function(e){
        return {
            shift:e.shiftKey,
            ctrl:e.ctrlKey,
            alt:e.altKey
        }
    }
    $.each(contexts,function(i,_context){
        var context = $(_context);//Might have to rewrap single jquerys
        var offset = function(){
            return context.offset();
        }
        context.bind("mousedown",function(e){
            WorkQueue.pause();
            var o = offset();
            e.preventDefault();
            isDown = true;
            var x = e.pageX - o.left;
            var y = e.pageY - o.top;
            var worldPos = screenToWorld(x,y);
            down(x,y,worldPos,modifiers(e));
        });
        context.bind("mousemove",function(e){
            if(isDown){
                var o = offset();
                e.preventDefault();
                var x = e.pageX - o.left;
                var y = e.pageY - o.top;
                move(x,y,screenToWorld(x,y),modifiers(e));
            }
        });
        context.bind("mouseup",function(e){
            WorkQueue.gracefullyResume();
            e.preventDefault();
            if(isDown){
                var o = offset();
                var x = e.pageX - o.left;
                var y = e.pageY - o.top;
                var worldPos = screenToWorld(x,y);
                up(x,y,worldPos,modifiers(e));
            }
            isDown = false;
        });
        var mouseOut = function(x,y){
            WorkQueue.gracefullyResume();
            var worldPos = screenToWorld(x,y);
            var worldX = worldPos.x;
            var worldY = worldPos.y;
            if(worldX < viewboxX){
                Extend.left();
            }
            else if(worldX >= (viewboxX + viewboxWidth)){
                Extend.right();
            }
            else if(worldY < viewboxY){
                Extend.up();
            }
            else if(worldY >= (viewboxY + viewboxHeight)){
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
                x:average(_.pluck(touches,"x")),
                y:average(_.pluck(touches,"y"))
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
                down(t.x,t.y,worldPos,modifiers(e));
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
                    move(t.x,t.y,screenToWorld(t.x,t.y),modifiers(e));
                }
                break;
            default:
                var pos = averagePos(touches);
                var xDelta = pos.x - prevPos.x;
                var yDelta =  pos.y - prevPos.y;
                prevPos = pos;
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
                if(x < 0 || y < 0 || x > boardWidth || y > boardHeight){
                    mouseOut(x,y);
                }
                else{
                    up(x,y,screenToWorld(x,y),modifiers(e));
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
            Zoom.scale(previousScale / scale);
            previousScale = scale;
        });
        context.bind("gestureend",function(){
            WorkQueue.gracefullyResume();
            previousScale = 1.0;
        });
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
        y:y,
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
var Modes = (function(){
    var removeActiveMode = function(){
        $(".activeTool").removeClass("activeTool");
        $(".activeMode").removeClass("activeMode");
    };
    var setActiveMode = function(toolsSelector,headerSelector){
        $(".activeMode").removeClass("activeMode");
        $(toolsSelector).addClass("activeMode");
        $(".activeTool").removeClass("activeTool");
        $(headerSelector).addClass("activeTool");
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
        insert:(function(){
            var marquee = undefined;
            var typingTimer = undefined;
            var textStyles = [
                {
                    size:20,
                    family:"Arial"
                },
                {
                    size:10,
                    family:"Arial"
                }
            ];
            var insertModes = [
                "Text",
                "Image"
            ];
            var textEditor = undefined;
            var currentInsertMode = "text";
            var currentStyle = undefined;
            var selectedTexts = [];
            var typingDelay = 1000;
            var typingTicks = 100;
            var startTime = Date.now();
            var changeToTextBoxMade = false;
            var currentCaretPos = undefined;
            var currentScrollTop = 0;
            var oldText = "";
            var newText = "";
            var typingTimerElapsed = function(){
                WorkQueue.gracefullyResume();
                clearTimeout(typingTimer);
                typingTimer = undefined;
                var subject = $.extend({},selectedTexts[0]);
                subject.text = oldText;
                delete subject.canvas;
                sendStanza(subject);
            }
            var checkTyping = function(){
                WorkQueue.pause();
                var oldTextTest = oldText;
                oldText = newText;
                var el = $("#textEditorInputArea").get(0);
                if ("selectionStart" in el){
                    currentCaretPos = el.selectionStart;
                    currentScrollTop = el.scrollTop;
                }

                var t = Date.now();
                var elapsed = t - startTime;
                if(oldTextTest == newText || changeToTextBoxMade){
                    if(elapsed > typingDelay){
                        changeToTextBoxMade = false;
                        typingTimerElapsed();
                    }
                    else{
                        clearTimeout(typingTimer);
                        typingTimer = setTimeout(checkTyping,typingTicks);
                    }
                }
                else{
                    startTime = Date.now();
                    clearTimeout(typingTimer);
                    typingTimer = setTimeout(checkTyping,typingTicks);
                }
            };
            var removeTextEditor = function(){
                if(typingTimer){
                    typingTimerElapsed();
                }
                $("#insertOptions").remove();
                $("#textEditor").remove();
            };
            var noop = function(){};
            var handleFilesSelected = function(worldPos){
                return function(e){
                    e.stopPropagation();
                    e.preventDefault();
                    var files = e.target.files || e.dataTransfer.files;
                    var limit = files.length;
                    var file = files[0];
                    if (file.type.indexOf("image") == 0) {
                        WorkQueue.pause();
                        var t = Date.now();
                        var identity = sprintf("%s%s",UserSettings.getUsername(),t);
                        var actionsCompleted = 0;
                        var necessaryActions = 4;//readLocal,upload,sendStanza,receiveStanza
                        var reader = new FileReader();
                        var pos = worldToScreen(worldPos.x,worldPos.y);
                        var pkg = $("<div />").css({
                            position:"relative",
                            left:px(pos.left),
                            top:px(pos.top)
                        });
                        if (marquee){
                            var selectionAdorner = $("#textAdorner");
                            selectionAdorner.empty();
                            selectionAdorner.append(marquee);
                            pkg.insertAfter(marquee);
                        }
                        var imageSizeControls = $("<div/>", {
                            id:"imageSizeControls"
                        }).css({
                            height:px("80")
                        }).appendTo(pkg);
                        var thumbnail = $("<canvas />",{id:"imageUploadThumbnail"}).appendTo(pkg);
                        var p = progress().value(actionsCompleted).max(necessaryActions);
                        p.element.css({
                            margin:"auto"
                        });
                        var progressContainer = $("<div />").css({
                            position:"absolute",
                            top:90,
                            left:0,
                            width:"100%",
                            "text-align":"center"
                        }).appendTo(pkg).append(p.element);
                        var statusMesage = $("<div />").appendTo(pkg);
                        updateTracking(identity,function(){
                            actionsCompleted++;
                            p.value(actionsCompleted);
                            if(actionsCompleted >= necessaryActions){
                                WorkQueue.gracefullyResume();
                                stopTracking(identity);
                            }
                        },function(){
                            WorkQueue.gracefullyResume();
                            pkg.remove();
                        });
                        reader.onload = function(e){
                            updateTracking(identity);
                            var img = new Image();
                            img.onload = function(e){
                                var onComplete = function(){
                                    var scale = viewboxWidth / boardWidth;
                                    var screenWidth = img.width / scale;
                                    var screenHeight = img.height / scale;
                                    var centerX = worldPos.x + img.width / 2;
                                    var centerY = worldPos.y + img.height / 2;
                                    Extend.center(centerX,centerY);
                                    p.element.css({
                                        top:px(centerY / 2)
                                    });
                                    pkg.css({
                                        left:px((boardWidth - screenWidth) / 2),
                                        top:px((boardHeight - screenHeight) / 2),
                                        "background-color":"lightgray",
                                        border:"2px dotted blue"
                                    });
                                    var thumbnailDrawer = function(w,h){
                                        return function(){
                                            thumbnail.attr("width",w);
                                            thumbnail.attr("height",h);
                                            thumbnail.css({
                                                width:px(w),
                                                height: px(h)
                                            });
                                            thumbnail[0].getContext("2d").drawImage(img,0,0,w,h);
                                        }
                                    }
                                    thumbnailDrawer(img.width,img.height)();
                                    var scaleByProportion = function(scaleFactor, buttonName) {
                                        return $("<div/>", {
                                            class:"imageUploadSizeChoice"
                                        })
                                            .on("click",thumbnailDrawer(img.width * scaleFactor, img.height * scaleFactor))
                                            .append($("<span/>",{text:buttonName}));
                                    };
                                    var scaleAtFixedSize = function(size, buttonName) {
                                        return scaleByProportion(size/ Math.max(img.width,img.height),buttonName);
                                    };
                                    var thumbSize = scaleAtFixedSize(120, "120px");
                                    var miniature = scaleAtFixedSize(320, "320px");
                                    var nativeImage = scaleByProportion(1, "Native Size");
                                    var mediumImage = scaleByProportion(0.75, "3/4 of Size");
                                    var smallImage = scaleByProportion(0.5, "1/2 of Size");
                                    var currentSlide = Conversations.getCurrentSlideJid();
                                    var url = sprintf("/uploadDataUri?jid=%s&filename=%s",currentSlide.toString(),encodeURI(file.name.split(".")[0]));
                                    var uploadImage = $("<div/>", {
                                        class:"imageUploadSizeChoice",
                                        text:"Upload"
                                    }).on("click", function() {
                                        $.ajax({
                                            url: url,
                                            type: 'POST',
                                            success: function(e){
                                                updateTracking(identity);
//                                                var thumbnail = $(thumbnail);
																								var thumbnail = $("#imageUploadThumbnail");
                                                var loader = $("#upload");
                                                var imageStanza = {
                                                    type:"image",
                                                    author:UserSettings.getUsername(),
                                                    timestamp:t,
                                                    tag:"{\"author\":\""+UserSettings.getUsername()+"\",\"privacy\":\""+Privacy.getCurrentPrivacy()+"\",\"id\":\""+identity+"\",\"isBackground\":false,\"zIndex\":0,\"timestamp\":-1}",
//identity,
                                                    identity:identity,
                                                    slide:currentSlide.toString(),
                                                    source:$(e).text(),
                                                    width:parseFloat(thumbnail.width()),
                                                    height:parseFloat(thumbnail.height()),
                                                    target:"presentationSpace",
                                                    privacy:Privacy.getCurrentPrivacy(),
                                                    x:worldPos.x,
                                                    y:worldPos.y
                                                };
                                                console.log("Sending image stanza",imageStanza);
                                                sendStanza(imageStanza);
                                                updateTracking(identity);
                                            },
                                            error: function(e){
                                                pkg.addClass("fail");
                                            },
                                            data:thumbnail[0].toDataURL(),
                                            cache: false,
                                            contentType: false,
                                            processData: false
                                        });
                                    });
                                    imageSizeControls.append(thumbSize).append(miniature).append(smallImage).append(mediumImage).append(nativeImage);
                                    uploadImage.appendTo(pkg);
                                }
                                var sx = viewboxWidth / img.width;
                                var sy = viewboxHeight / img.height;
                                var shift = 1 / Math.min(sx,sy);
                                var scale = viewboxWidth / boardWidth;
                                if(shift > 1){
                                    Zoom.zoom(shift,true,onComplete);
                                }
                                else{
                                    onComplete();
                                }
                            }
                            img.src=e.target.result;
                        }
                        reader.readAsDataURL(file);
                    }
                }
            };
            var newInsertOptions = function(x,y,worldPos,onText,onImage){
                var clearOptions = function(){
                    $("#insertOptions").remove();

                }
                var clearBefore = function(func){
                    return function(){
                        clearOptions();
                        func();
                    }
                }
                onText = clearBefore(onText);
                onImage = clearBefore(onImage);
                clearOptions();
                var options = $("<div />",{
                    id:"insertOptions",
                    class:"insertOptionsContainer"
                }).css({
                    position:"absolute",
                    left:px(x - 30),
                    top:px(y)
                });
                options.append($("<div />",{
                    text:"X",
                    class:"closeButton"
                }).click(function(){
                    options.remove();
                }));
                if(currentInsertMode.toLowerCase() == "image"){
                    var insertImage = $("<input />",{
                        type:"file",
                        accept:"image/*",
                        class:"fileUploadControl"
                    }).appendTo(options);
                    if (options){
                        insertImage[0].addEventListener("change",function(e){
                            clearOptions();
                            handleFilesSelected(worldPos)(e);
                        },false)
                    }
                    return options;
                } else if (currentInsertMode.toLowerCase() == "text"){
                    onText();
                    return false;
                } else {
                    return false;
                }
            }
            var color = "Black";
            var updateTextFont = function(t){
                t.font = sprintf("%spx %s",t.size,t.family);
            }
            var createBlankText = function(worldPos){
                var id = sprintf("%s%s",UserSettings.getUsername(),Date.now());
                var style = currentStyle;
                var currentSlide = Conversations.getCurrentSlideJid();
                var text = {
                    author:UserSettings.getUsername(),
                    color:Colors.getColorForName("black"),
                    decoration:"None",
                    identity:id,
                    privacy:Privacy.getCurrentPrivacy(),
                    family:style.family,
                    size:style.size,
                    slide:currentSlide,
                    style:"Normal",
                    tag:id,
                    width:200,
                    caret:0,
                    height:60,
                    x:worldPos.x,
                    y:worldPos.y,
                    target:"presentationSpace",
                    text:"",
                    timestamp:Date.now(),
                    type:"text",
                    weight:"Normal"
                };
                updateTextFont(text);
                prerenderText(text);
                selectedTexts = [];
                selectedTexts.push(text);
                return text;
            };
            var alteredText = function(t){
                changeToTextBoxMade = true;
                updateTextFont(t);
                prerenderText(t);
                checkTyping();
                return t;
            };
            var textCustomizationOptions = function(text){
                var options = $("<div />",{
                    class:"textOptionsContainer"
                });
                var combobox = function(opts,property,transform,transformBack){
                    var value = text[property];
                    var transformedValue = transformBack ? transformBack(value) : value;
                    var cont = $("<span/>").css({
                        position:"relative"
                    });
                    var setTextPropValue = function(t){
                        var newVal = transform ? transform(t) : t;
                        var oldVal = text[property];
                        if (newVal != oldVal){
                            text[property] = newVal;
                            alteredText(text);
                            editText(text);
                        }
                    };
                    var topRow = $("<div/>");
                    var textArea = $("<input/>",{
                        type:"text",
                        class: "comboBoxTextfield",
                        value:transformedValue
                    }).on("blur",function(){
                        var v = $(this).val();
                        setTextPropValue(v);
                    }).keydown(function(e){
                    }).keyup(function(e){
                        e.preventDefault();
                        e.stopPropagation();
                        if (e.keyCode == 13){
                            var v = $(this).val();
                            setTextPropValue(v);
                        }
                    });
                    var toggleButton = $("<input/>",{
                        class: "comboBoxButton ",
                        type: "button",
                        value:"More"
                    });
                    var bottomRow = $("<div/>").css({
                        position:"absolute",
                        "z-index":100
                    });
                    var listOfOptions = $("<div/>",{
                        class:"comboBoxList"
                    }).html(unwrap(opts.map(function(opt){
                        return $("<div/>",{
                            class:"comboBoxListItem",
                            text:opt
                        }).click(function(){setTextPropValue(opt);});
                    })));
                    var listContainer = $("<span/>",{
                        class:"comboBoxListContainer"
                    }).append(listOfOptions).hide();
                    toggleButton.click(function(){
                        var isOpen = listOfOptions.is(":visible");
                        $(".comboBoxListContainer").hide();
                        if (!isOpen){
                            listContainer.show();
                        }
                    });
                    topRow.append(textArea,toggleButton);
                    bottomRow.append(listContainer);
                    cont.append(topRow,bottomRow);
                    options.append(cont);
                }
                var dropdown = function(opts,property,transform,transformBack){
                    var value = text[property];
                    var transformedValue = transformBack ? transformBack(value) : value;
                    if (!(_.contains(opts,transformedValue))){
                        opts.push(transformedValue);
                    }
                    var cont = $("<select />",{
                        class:"textOptionsDropdown"
                    }).html(unwrap(opts.map(function(opt){
                        var o = $("<option />",{
                            value:opt,
                            text:opt
                        });
                        if(transformedValue == opt){
                            o.prop("selected",true);
                        }
                        return o;
                    }))).change(function(){
                        var v = $(this).val();
                        text[property] = transform? transform(v) : v;
                        alteredText(text);
                        editText(text);
                    });
                    options.append(cont);
                }
                var toggle = function(name,property,transform,transformBack){
                    var value = text[property];
                    var transformedValue = transformBack ? transformBack(value) : value;
                    var el = $("<span />",{
                        class:"textOptionsToggleContainer"
                    });
                    $("<label />",{
                        text:name,
                        class:"textOptionsToggleLabel",
                        for:name
                    }).appendTo(el);
                    var cb = $("<input />",{
                        type:"checkbox",
                        class:"textOptionsToggleButton",
                        name:name
                    }).change(function(change){
                        var v = $(this).prop("checked");
                        text[property] = transform? transform(v) : v;
                        alteredText(text);
                        editText(text);
                    }).appendTo(el);
                    if (transformedValue){
                        cb.prop("checked",true);
                    } else {
                        cb.prop("checked",false);
                    }
                    options.append(el);

                }
                dropdown(Fonts.getAllFamilies(),"family");
                combobox(Fonts.getAllSizes(),"size",parseInt);
                combobox(_.map(Colors.getAllNamedColors(),function(c){return c.name;}),"color",Colors.getColorForName,Colors.getNameForColor);
                toggle("Bold","weight",function(bool){if (bool){ return "bold";} else {return "Normal";}},function(string){return string == "bold";});
                toggle("Italic", "style", function(bool){if(bool){return "italic";} else { return "Normal";}}, function(string){return string=="italic"});
                toggle("Underline","decoration",function(bool){if (bool) {return "underline";} else {return "Normal";}}, function(string){return string == "underline";});
                return options;
            }
            var openingEditBox = false;
            var lineWithSeparatorRatio = 1.3;
            var createTextEditor = function(text){
                var b = text.bounds;
                var newPos = worldToScreen(
                    b[0],
                    b[1]);
                var input = $("<textarea />",{
                    id:"textEditorInputArea"
                }).css({
                    "font-family":text.family,
                    "font-size":px(text.size),
                    width:px(text.width),
                    height:px(text.runs.length * text.size * lineWithSeparatorRatio)
                });
                input.keyup(function(e){
                    e.stopPropagation();
                    var el = $(this).get(0);
                    if ("selectionStart" in el && currentCaretPos != undefined){
                        currentCaretPos = el.selectionStart;
                        currentScrollTop = el.scrollTop;
                    }
                    if (e.ctrlKey){
                        if(e.keyCode == 73) {
                            if(text.style == "Normal"){
                                text["style"] = "italic";
                            }
                            else {
                                text["style"] = "Normal";
                            }
                            alteredText(text);
                            editText(text);
                        }
                        if (e.keyCode == 66){
                            if (text.weight == "Normal"){
                                text["weight"] = "bold";
                            } else {
                                text["weight"] = "Normal";
                            }
                            alteredText(text);
                            editText(text);
                        }
                    } else {
                        newText = $(this).val();
                        checkTyping();
                    }
                }).keydown(function(e){
                    e.stopPropagation();
                });
                input.bind("paste",function(e){
                    var thisBox = $(this);
                    if ("type" in e && e.type == "paste"){
                        window.setTimeout(function(){
                            newText = thisBox.val();
                            checkTyping();
                        },0);
                    }
                });
                $("#textEditorCustomizationOptionsContainer").remove();
                var customizationOptionsContainer = $("<div />",{
                    id:"textEditorCustomizationOptionsContainer"
                }).html(textCustomizationOptions(text));
                var textEditor = $("<div />",{
                    id:"textEditor"
                }).css({
                    position:"absolute",
                    left:px(newPos.x),
                    top:px(newPos.y)
                }).append(input).append(customizationOptionsContainer);
                if (marquee){
                    var selectionAdorner = $("#textAdorner");
                    selectionAdorner.empty();
                    selectionAdorner.append(marquee);
                    textEditor.insertAfter(marquee);
                }
                input.val(text.runs.join("\n"));
                updateTextEditor(text);
                input.focus();
            }
            var updateTextEditor = function(text){
                var b = text.bounds;
                var newPos = worldToScreen(
                    b[0],
                    b[1]);
                var possiblyAdjustedHeight = text.height;
                var possiblyAdjustedWidth = text.width * 1.1;
                var possiblyAdjustedX = newPos.x;
                var possiblyAdjustedY = newPos.y;
                var acceptableMaxHeight = boardHeight * 0.7;
                var acceptableMaxWidth = boardWidth * 0.7;
                var acceptableMinX = 30;
                var acceptableMinY = 30;
                var acceptableMaxX = boardWidth - 100;
                var acceptableMaxY = boardHeight - 100;

                if (possiblyAdjustedWidth > acceptableMaxWidth){
                    possiblyAdjustedWidth = acceptableMaxWidth;
                }
                if (possiblyAdjustedHeight > acceptableMaxHeight){
                    possiblyAdjustedHeight = acceptableMaxHeight;
                }
                if (possiblyAdjustedX < acceptableMinX){
                    possiblyAdjustedX = acceptableMinX;
                }
                if ((possiblyAdjustedX + possiblyAdjustedWidth) > acceptableMaxX){
                    possiblyAdjustedX = acceptableMaxX - possiblyAdjustedWidth;
                }
                if (possiblyAdjustedY < acceptableMinY){
                    possiblyAdjustedY = acceptableMinY;
                }
                if ((possiblyAdjustedY + possiblyAdjustedHeight) > acceptableMaxY){
                    possiblyAdjustedY = acceptableMaxY - possiblyAdjustedHeight;
                }
                var textEditor = $("#textEditor");
                var h = px(text.runs.length * text.size * lineWithSeparatorRatio);
                textEditor.css({
                    position:"absolute",
                    left:px(possiblyAdjustedX),
                    top:px(possiblyAdjustedY),
                    width:px(possiblyAdjustedWidth),
                    "min-width":px(240)
                });
                $("#textEditorCustomizationOptionsContainer").html(textCustomizationOptions(text));
                textEditor.find("#closeTextEditor").remove();
                textEditor.prepend($("<div />",{
                    id:"closeTextEditor",
                    class:"closeButton",
                    text:"X"
                }).css({
                    "float":"right"
                }).click(function(){
                    textEditor.remove();
                }));

                var inputArea = $("#textEditorInputArea");
                inputArea.css({
                    width:px(possiblyAdjustedWidth),
                    "font-weight": text.weight,
                    "font-style": text.style,
                    "text-decoration": text.decoration,
                    "color": text.color[0],
                    "height":h,
                    "font-family":text.family,
                    "font-size":px(text.size)
                });
                var el = inputArea;
                if ("setSelectionRange" in el){
                    el.setSelectionRange(currentCaretPos,currentCaretPos);
                    $(el).scrollTop(currentScrollTop);
                }
                inputArea.focus();
            }
            var editText = function(text,shouldNotPan){
                var innerCreateTextboxFunction = undefined;
                if (openingEditBox == true){
                    oldText = text.text;
                    openingEditBox = false;
                    innerCreateTextboxFunction = createTextEditor;
                } else {
                    innerCreateTextboxFunction = updateTextEditor;
                }
                var inputContents = "";
                if (oldText.runs != undefined){
                    inputContents = oldText.runs.join("\n");
                } else {
                    inputContents = oldText;
                }
                newText = inputContents;
                startTime = Date.now();
                var chars = Math.max.apply(Math,_.pluck(text.runs,"length"));
                var xInset = (boardWidth / 2 - text.width / 2);
                var yInset = (boardHeight / 2 - text.height / 2);
                var xDelta = text.bounds[0] - viewboxX - xInset;
                var yDelta = text.bounds[1] - viewboxY - yInset;
                if (shouldNotPan){
                    innerCreateTextboxFunction(text);
                } else {
                    innerCreateTextboxFunction(text);
                    //Extend.shift(xDelta,yDelta,innerCreateTextboxFunction(text));
                }
            }
            var possiblyClearEditBoxesFunction = _.debounce(function(){
                var newSelectedTexts = [];
                _.forEach(selectedTexts,function(st){
                    if ("texts" in boardContent && "identity" in st && st.identity in boardContent.texts && "slide" in st && st.slide.toLowerCase() == Conversations.getCurrentSlideJid().toLowerCase()){
                        newSelectedTexts.push(st);
                    } else if ("runs" in st && st.runs[0] == "" && "slide" in st && st.slide.toLowerCase() == Conversations.getCurrentSlideJid().toLowerCase()){
                        newSelectedTexts.push(st);
                    }
                });
                var selectionAdorner = $("#selectionAdorner");
                selectedTexts = newSelectedTexts;
                if (Modes.currentMode == Modes.insert && currentInsertMode.toLowerCase() == "text"){
                    selectionAdorner.empty();
                    if (_.size(selectedTexts) > 0){
                        var text = selectedTexts[0];
                        if (Modes.currentMode == Modes.insert && currentInsertMode.toLowerCase() == "text"){
                            var view = [viewboxX,viewboxY,viewboxX+viewboxWidth,viewboxY+viewboxWidth];
                            if (intersectRect(text.bounds,view)){
                                if (!($("#textEditorInputArea").val())){
                                    openingEditBox = true;
                                }
                                editText(text,true);
                                drawSelectionBounds(text);
                            } else {
                                removeTextEditor();
                            }
                        } else {
                            removeTextEditor();
                        }
                    } else {
                        removeTextEditor();
                    }
                }
            },200);

            Progress.onBoardContentChanged["Modes.insert"] = possiblyClearEditBoxesFunction;
            Progress.onSelectionChanged["Modes.insert"] = possiblyClearEditBoxesFunction;
            Progress.historyReceived["Modes.insert"] = possiblyClearEditBoxesFunction;
            Progress.onViewboxChanged["Modes.insert"] = possiblyClearEditBoxesFunction;
            return {
                activate:function(){
                    marquee = $("<div />",{
                        id:"textMarquee"
                    });
                    var adorner = $("#textAdorner");
                    adorner.empty();
                    if(!currentStyle){
                        currentStyle = textStyles[0];
                    }
                    var uploadKey = "fileUploadSupported";
                    if(uploadKey in Modes.insert){}
                    else{
                        Modes.insert[uploadKey] = !($("<input />",{
                            type:"file"
                        })[0].disabled);
                    }
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.insert;
                    setActiveMode("#insertTools","#insertMode");
                    $(".activeBrush").removeClass("activeBrush");
                    $("#textTools").empty();
                    _.forEach(insertModes,function(modeName){
                        var tsButton = $("<div/>",{
                            class:"modeSpecificTool",
                            text:modeName
                        }).on("click",function(){
                            currentInsertMode = modeName;
                            $(".activeBrush").removeClass("activeBrush");
                            $(this).addClass("activeBrush");
                        }).appendTo("#textTools");
                        if (modeName.toLowerCase() == currentInsertMode.toLowerCase()){
                            tsButton.addClass("activeBrush");
                        }
                    });
                    Progress.call("onLayoutUpdated");
                    $("#minorText").click(function(){});
                    $("#deleteTextUnderEdit").unbind("click").on("click",function(){
                        deletedStanza = selectedTexts[0];
                        updateStatus(sprintf("Deleted %s",deletedStanza.identity));
                        var deleteTransform = batchTransform();
                        deleteTransform.isDeleted = true;
                        deleteTransform.textIds = [deletedStanza.identity];
                        sendStanza(deleteTransform);
                        upload.hide();
                    });
                    updateStatus("Text input mode");
                    var up = function(x,y,worldPos){
                        if(typingTimer){
                            typingTimerElapsed();
                        }
                        adorner.append(marquee);
                        marquee.show();
                        marquee.css({
                            left:px(x),
                            top:px(y)
                        });
                        oldText = "";
                        newText = "";
                        $("#insertOptions").remove();
                        $("#textEditor").remove();
                        var newScreenPos = worldToScreen(worldPos.x,worldPos.y);
                        var threshold = 10;
                        var ray = [worldPos.x - threshold,worldPos.y - threshold,worldPos.x + threshold,worldPos.y + threshold];
                        currentCaretPos = 0;
                        currentScrollTop = 0;
                        selectedTexts  = _.values(boardContent.texts).filter(function(text){
                            return intersectRect(text.bounds,ray) && text.author == UserSettings.getUsername();
                        });
                        var text = selectedTexts[0];
                        if(currentInsertMode.toLowerCase() != "text" || !text){
                            var options = newInsertOptions(
                                newScreenPos.x,newScreenPos.y,worldPos,
                                function(){
                                    openingEditBox = true;
                                    text = createBlankText(worldPos);
                                    selectedTexts[0] = text;
                                    editText(text);
                                },
                                function(){
                                });
                            if(options){
                                $(marquee).after(options);
                            }
                        }
                        else{
                            openingEditBox = true;
                            editText(text);
                        }
                    }
                    registerPositionHandlers(board,noop,noop,up);
                },
                deactivate:function(){
                    removeTextEditor();
                    selectedTexts = [];
                    $("#selectionAdorner").empty();
                    $("#textTools .modeSpecificTool").unbind("click");
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
                var down = function(x,y){
                    originX = x;
                    originY = y;
                }
                var move = function(x,y){
                    var xDelta = x - originX;
                    var yDelta = y - originY;
                    Pan.translate(-1 * xDelta,-1 * yDelta);
                    originX = x;
                    originY = y;
                }
                var up = function(x,y){}
                registerPositionHandlers(board,down,move,up);
            },
            deactivate:function(){
                removeActiveMode();
                unregisterPositionHandlers(board);
            }
        },
        select:(function(){
            var updateSelectionVisualState = function(sel){
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
                } else {
                    $("#delete").addClass("disabledButton");
                    $("#resize").addClass("disabledButton");
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
            Progress.onBoardContentChanged["ModesSelect"] = updateSelectionWhenBoardChanges;
            Progress.onViewboxChanged["ModesSelect"] = updateSelectionWhenBoardChanges;
            Progress.onSelectionChanged["ModesSelect"] = updateSelectionVisualState;
            Progress.historyReceived["ModesSelect"] = clearSelectionFunction;
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
                    var down = function(x,y,worldPos,modifiers){
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
                                return _.any(Modes.select.selected[property],function(el){
                                    if (el){
                                        return intersectRect(el.bounds,ray);
                                    } else {
                                        return false;
                                    }
                                });
                            }
                            dragging = _.any(["images","texts","inks"],isDragHandle);
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
                    var move = function(x,y,worldPos,modifiers){
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
                    var up = function(x,y,worldPos,modifiers){
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
                            console.log("Selection bounds",selectionBounds);
                            var intersected = {
                                images:{},
                                texts:{},
                                inks:{}
                            };
                            var intersectAuthors = {};
                            var overlapThreshold = 0.5;
                            var intersectCategory = function(category){
                                $.each(boardContent[category],function(i,item){
																		if ("bounds" in item){
																			var b = item.bounds;
																			var selectionThreshold = Math.abs(overlapThreshold * ((b[2] - b[0]) * (b[3] - b[1])));
																			var overlap = overlapRect(selectionBounds,item.bounds);
																			console.log(overlap,selectionThreshold);
																			if(overlap >= selectionThreshold){
																					//if(intersectRect(item.bounds,selectionBounds)){
																					incrementKey(intersectAuthors,item.author);
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
                                    if(item.author == UserSettings.getUsername()){
                                        intersected.inks[item.identity] = item;
                                    }
                                }
                            });
                            if(modifiers.ctrl){
                                var toggleCategory = function(category){
                                    $.each(intersected[category],function(id,item){
                                        if(id in Modes.select.selected[category]){
                                            delete Modes.select.selected[category][id];
                                        }
                                        else{
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
                var down = function(x,y,worldPos){
                    proportion = boardHeight / boardWidth;
                    startX = x;
                    startY = y;
                    startWorldPos = worldPos;
                    marquee.show();
                    marquee.appendTo($("#selectionAdorner"));
                    originPoint = {x:x,y:y};
                    updateMarquee(marquee,originPoint,originPoint);
                }
                var move = function(x,y,worldPos){
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
                var up = function(x,y,worldPos){
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
                $("#feedbackTools").empty().append(roleAppropriateTools());
                var currentConversation = Conversations.getCurrentConversation();
                var enabled = currentConversation.permissions && currentConversation.permissions.studentCanPublish;
                var enable = $("#enableCollaboration").unbind("click").on("click",function(){
                    currentConversation = Conversations.getCurrentConversation();
                    enabled = currentConversation.permissions.studentCanPublish;
                    if(!enabled){
                        Conversations.changeConversationToTutorial();
                        applyStateStyling();
                    }
                });
                var disable = $("#disableCollaboration").unbind("click").on("click",function(){
                    currentConversation = Conversations.getCurrentConversation();
                    enabled = currentConversation.permissions.studentCanPublish;
                    if(enabled){
                        Conversations.changeConversationToLecture();
                        applyStateStyling();
                    }
                });
                var sync = $("#enableSync").unbind("click").on("click",Conversations.enableSyncMove);
                var desync = $("#disableSync").unbind("click").on("click",Conversations.disableSyncMove);
                enable.removeClass("activePrivacy");
                disable.removeClass("activePrivacy");
                if(enabled){
                    enable.addClass("activePrivacy");
                }
                else{
                    disable.addClass("activePrivacy");
                }
                if(Conversations.isAuthor()){
                    enable.show();
                    disable.show();
                    sync.hide();
                    desync.hide();
                }
                else{
                    enable.hide();
                    disable.hide();
                    sync.show();
                    desync.show();
                }
            };
            Progress.onConversationJoin["setConversationRole"] = function(){
                applyStateStyling();
                if(!Conversations.isAuthor()){
                    Conversations.enableSyncMove();
                }
            }
            Progress.conversationDetailsReceived["respectNewPermissions"] = applyStateStyling;
            var teacherTools=function(){
                var tools = $("<div />");
                $("<div/>",{
                    id:"submitScreenshotButton",
                    class:"modeSpecificTool",
                    text:"Submit screenshot"
                }).on("click",function(){
                    var currentConversation = Conversations.getCurrentConversation();
                    var currentSlide = Conversations.getCurrentSlideJid();
                    if("jid" in currentConversation){
                        submitScreenshotSubmission(currentConversation.jid.toString(),currentSlide);
                    }
                }).appendTo(tools);
                $("<div/>",{
                    class:"modeSpecificTool",
                    text:"Quizzes"
                }).on("click",function(){
                    showBackstage("quizzes");
                }).appendTo(tools);
                $("<div/>",{
                    class:"modeSpecificTool",
                    text:"Submissions"
                }).on("click",function(){
                    showBackstage("submissions");
                }).appendTo(tools);
                return tools;
            };
            var studentTools=function(){
                var tools = $("<div />");
                $("<div/>",{
                    id:"submitScreenshotButton",
                    class:"modeSpecificTool",
                    text:"Submit screenshot"
                }).on("click",function(){
                    var currentConversation = Conversations.getCurrentConversation();
                    var currentSlide = Conversations.getCurrentSlideJid();
                    if("jid" in currentConversation){
                        submitScreenshotSubmission(currentConversation.jid.toString(),currentSlide);
                    }
                }).appendTo(tools);
                /*
                 $("<div/>",{
                 id:"syncToTeacherButton",
                 class:"modeSpecificTool",
                 text:Conversations.getIsSyncedToTeacherDescriptor()
                 }).on("click",function(){
                 Conversations.toggleSyncMove();
                 applyStateStyling();
                 }).appendTo(tools);
                 */
                $("<div/>",{
                    class:"modeSpecificTool",
                    text:"Quizzes"
                }).on("click",function(){
                    showBackstage("quizzes");
                }).appendTo(tools);
                $("<div/>",{
                    class:"modeSpecificTool",
                    text:"Submissions"
                }).on("click",function(){
                    showBackstage("submissions");
                }).appendTo(tools);
                return tools;
            };
            var roleAppropriateTools = function(){
                return Conversations.shouldModifyConversation() ? teacherTools() : studentTools();
            };
            return {
                name:"feedback",
                activate:function(){
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.feedback;
                    applyStateStyling();
                    setActiveMode("#feedbackTools","#feedbackMode");
                    var down = function(x,y,worldPos){
                    }
                    var move = function(x,y,worldPos){
                    }
                    var up = function(x,y,worldPos){
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
            return {
                name:"draw",
                brushes:_.map(originalBrushes,function(i){return _.clone(i);}),
                activate:function(){
                    if(!Modes.draw.drawingAttributes){
                        Modes.draw.drawingAttributes = Modes.draw.brushes[0];
                    }
                    if(!currentBrush){
                        currentBrush = Modes.draw.brushes[0];
                    }
                    if(Modes.currentMode == Modes.draw){
                        return;
                    }
                    var mousePressure = 128;
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.draw;
                    var drawAdvancedTools = function(brush){
                        var dots = $("<div />",{
                            class:"dots"
                        });
                        var bars = $("<div />",{
                            class:"bars"
                        });
                        var colors = Colors.getAllNamedColors();
                        var widths = Brushes.getAllBrushSizes();
                        widths.map(function(width){
                            var sizeDot = $("<div />", {
                                class: "sizeDot"
                            }).css({
                                "text-align":"center"
                            }).click(function(){
                                brush.width = width;
                                currentBrush = brush;
                                drawTools();
                                drawAdvancedTools(brush);
                            })
                            var bar = Canvas.circle(brush.color,width,60);
                            if (width == brush.width){
                                sizeDot.addClass("activeTool");
                            }
                            sizeDot.append(bar)
                            bars.append(sizeDot);
                        });
                        colors.map(function(color){
                            var colorDot = $("<div />").css({
                                "vertical-align":"middle"
                            }).click(function(){
                                brush.color = color.rgb;
                                currentBrush = brush;
                                drawTools();
                                drawAdvancedTools(brush);
                            });
                            var dot = Canvas.circle(color.rgb,50,50);
                            if (color == brush.color){
                                colorDot.addClass("activeTool");
                            }
                            colorDot.append(dot);
                            dots.append(colorDot);
                        });
                        var offset = widths[widths.length-1];
                        var highlighterModeText = brush.isHighlighter ? "highlighter" : "pen";
                        var penModeControl = $("<div/>");
                        var hlButton = $("<span/>",{
                            text:"highlighter",
                            class:"toolbar"

                        }).on("click",function(){
                            brush.isHighlighter = true;
                            currentBrush = brush;
                            drawTools();
                            drawAdvancedTools(brush);
                        });
                        var penButton = $("<span/>",{
                            text:"pen",
                            class:"toolbar"
                        }).on("click",function(){
                            brush.isHighlighter = false;
                            currentBrush = brush;
                            drawTools();
                            drawAdvancedTools(brush);
                        });
                        if ("isHighlighter" in currentBrush && currentBrush.isHighlighter){
                            hlButton.addClass("activeTool");
                            penButton.removeClass("activeTool");
                        } else {
                            penButton.addClass("activeTool");
                            hlButton.removeClass("activeTool");
                        }
                        $("#colors").html(dots);
                        $("#sizes").html(bars);
                        $("#penMode").html(penModeControl.append(penButton).append(hlButton));
                        $("#colors td").css({
                            width:px(offset*3),
                            height:px(offset*3)
                        });
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
                    var drawTools = function(){
                        $(".activeBrush").removeClass("activeBrush");
                        $("#drawTools").empty().html(unwrap(
                            Modes.draw.brushes.map(function(brush){
                                var dot = Canvas.swirl(brush.color,brush.width,parseInt(UserSettings.getUserPref("subModeSize")),brush.isHighlighter);
                                var dotButton = $("<div />")
                                        .click(function(){
                                            currentBrush = brush;
                                            erasing = false;
                                            Modes.draw.drawingAttributes = brush;
                                            $(".activeBrush").removeClass("activeBrush");
                                            $(this).addClass("activeBrush");
                                            drawAdvancedTools(Modes.draw.drawingAttributes);
                                        })
                                        .addClass("modeSpecificTool")
                                        .append(dot);
                                if (brush == currentBrush){
                                    dotButton.addClass("activeBrush");
                                } else {
                                    dotButton.removeClass("activeBrush");
                                }
                                return dotButton;
                            })))
                            .append($("<div />",{
                                text:"Erase",
                                class:"modeSpecificTool",
                                click:function(){
                                    erasing = true;
                                    $(".activeBrush").removeClass("activeBrush");
                                    $(this).addClass("activeBrush");
                                }
                            }))
                            .append($("<div />",{
                                text:"More"
                            }).addClass("modeSpecificTool")
                                    .click(function(){
                                        drawAdvancedTools(Modes.draw.drawingAttributes);
                                        showBackstage("customizeBrush");
                                    }));
                        Progress.call("onLayoutUpdated");
                    }
                    setActiveMode("#drawTools","#drawMode");
                    drawTools();
                    var currentStroke = [];
                    var isDown = false;
                    var resumeWork;
                    var down = function(x,y){
                        deleted = [];
                        isDown = true;
                        if(!erasing){
                            boardContext.strokeStyle = Modes.draw.drawingAttributes.color;
                            boardContext.lineWidth = Modes.draw.drawingAttributes.lineWidth * 128 * mousePressure;
                            boardContext.beginPath();
                            boardContext.moveTo(x,y);
                            currentStroke = [x, y, mousePressure];
                        }
                    };
                    var erasing = false;
                    var raySpan = 10;
                    var deleted = [];
                    var move = function(x,y,worldPos){
                        if(erasing){
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
                            boardContext.lineTo(x,y);
                            boardContext.stroke();
                            currentStroke = currentStroke.concat([x,y,mousePressure]);
                        }
                    };
                    var up = function(x,y){
                        isDown = false;
                        if(erasing){
                            var deleteTransform = batchTransform();
                            deleteTransform.isDeleted = true;
                            deleteTransform.inkIds = deleted;
                            sendStanza(deleteTransform);
                        }
                        else{
                            boardContext.lineTo(x,y);
                            boardContext.stroke();
                            currentStroke = currentStroke.concat([x,y,mousePressure]);
                            strokeCollected(currentStroke.join(" "));
                        }
                    };

                    registerPositionHandlers(board,down,move,up);
                },
                deactivate:function(){
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
                var down = function(x,y,worldPos){};
                var move = function(x,y,worldPos){
                };
                var up = function(x,y,worldPos){};
                registerPositionHandlers(board,down,move,up);
            },
            deactivate:function(){
                unregisterPositionHandlers(board);
            }
        }
    }
})();
