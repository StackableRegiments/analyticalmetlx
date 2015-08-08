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
            var worldPos = screenToWorld(x,y);
            var worldX = worldPos.x;
            var worldY = worldPos.y;
            /*
             console.log(sprintf("mouseOut %s,%s from [%s,%s,%s,%s]",
             worldX,worldY,viewboxX,viewboxY,viewboxWidth,viewboxHeight));
             */
            WorkQueue.canWork(true);
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
                Pan.shift(xDelta,yDelta);
                break;
            }
        });
        context.bind("touchend",function(e){
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
            e.preventDefault();
            isDown = false;
            WorkQueue.canWork(true);
            var scale = e.originalEvent.scale;
            Zoom.shift(previousScale / scale,true);
            previousScale = scale;
        });
        context.bind("gestureend",function(){
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
function drawSelectionBounds(item){
    var origin = worldToScreen(item.bounds[0],item.bounds[1]);
    var end = worldToScreen(item.bounds[2],item.bounds[3]);
    var originalWidth = end.x-origin.x;
    var originalHeight = end.y-origin.y;
    return $("<div />")
        .addClass("selectionAdorner")
        .css({
            left:origin.x,
            top:origin.y,
            width:originalWidth,
            height:originalHeight
        })
        .data("originalWidth",originalWidth)
        .data("originalHeight",originalHeight)
        .appendTo($("#selectionAdorner"));
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
    };
    return {
        currentMode:{
            deactivate:function(){
                removeActiveMode();
            }
        },
        groups:{
            activate:function(){
                Modes.currentMode.deactivate();
                Modes.currentMode = Modes.groups;
                removeActiveMode();
		_.each(GroupFinder.parties,function(g){
		    g.location = Conversations.getCurrentSlideJid();
		    g.conversation = Conversations.getCurrentConversationJid();
		});
                Modes.groups.drawTools();
            },
            deactivate:function(){}
        },
        quiz:{
            activate:function(){
                Modes.currentMode.deactivate();
                Modes.currentMode = Modes.quiz;
                removeActiveMode();
                Modes.quiz.drawTools();
            },
            deactivate:function(){}
        },
        tag:{
            additive:"Add",
            raySpan:"Loose",
            drawTools:function(){
            },
            blankGroup:function(){
                return {};
            },
            activeGroup:{},
            clearGroup:function(){
                var base = Modes.tag;
                base.activeGroup = base.blankGroup();
                Progress.call("tagGroupChanged");
            },
            activate:function(){
                Modes.currentMode.deactivate();
                Modes.currentMode = Modes.tag;
                removeActiveMode();
                Modes.tag.drawTools();
                var blankGroup = function(){
                    return {};
                };
                var includeContentAtPoint = function(worldPos){
                    var d = {
                        Tight:20,
                        Loose:100
                    }[Modes.tag.raySpan];
                    var ray = [worldPos.x - d, worldPos.y - d, worldPos.x + d, worldPos.y + d];
                    var intersects = function(e){
                        if(intersectRect(ray,e.bounds)){
                            if(e.identity in Modes.tag.activeGroup){
                                if(Modes.tag.additive == "Subtract"){
                                    delete Modes.tag.activeGroup[e.identity];
                                }
                            }
                            else{
                                if(Modes.tag.additive == "Add"){
                                    Modes.tag.activeGroup[e.identity] = e;
                                }
                            }
                        }
                    }
                    _.values(boardContent.images).map(intersects);
                    _.values(boardContent.highlighters).map(intersects);
                    _.values(boardContent.texts).map(intersects);
                    _.values(boardContent.inks).map(intersects);
                    Progress.call("tagGroupChanged");
                }
                var down = function(x,y,worldPos){
                    includeContentAtPoint(worldPos);
                };
                var move = function(x,y,worldPos){
                    includeContentAtPoint(worldPos);
                };
                var up = function(x,y,worldPos){};
                registerPositionHandlers(board,down,move,up);
            },
            deactivate:function(){
                unregisterPositionHandlers(board);
                $(".postit").remove();
                $("#focussedPostit").remove();
            }
        },
        insert:{
            typingTimer:undefined,
            selectedTexts:[],
            textStyles:[
                {
                    size:20,
                    family:"Arial"
                },
                {
                    size:10,
                    family:"Arial"
                }
            ],
            insertModes:[
                "text",
                "image"
            ],
            currentInsertMode:"text",
            drawTools:function(){
                $("#textTools").empty();
                _.forEach(Modes.insert.insertModes,function(modeName){
                    var tsButton = $("<div/>",{
                        class:"modeSpecificTool",
                        text:modeName
                    }).on("click",function(){
                        Modes.insert.currentInsertMode = modeName;
                        $(".activeBrush").removeClass("activeBrush");
                        $(this).addClass("activeBrush");
                    }).appendTo("#textTools");
                    if (modeName == Modes.insert.currentInsertMode){
                        tsButton.addClass("activeBrush");
                    }
                });
            },
            activate:function(){
                if(!Modes.insert.currentStyle){
                    Modes.insert.currentStyle = Modes.insert.textStyles[0];
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
                Modes.insert.drawTools();
                setActiveMode("#insertTools","#insertMode");
                $(".activeBrush").removeClass("activeBrush");
                $("#minorText").click(function(){});
                var typingDelay = 1000;
                var typingTicks = 100;
                var startTime = Date.now();
                var changeToTextBoxMade = false;
                var currentCaretPos = 0;
                var oldText = "";
                var newText = "";
                var previousTime = Date.now();
                var typingTimerElapsed = function(){
                    clearTimeout(Modes.insert.typingTimer);
                    delete Modes.insert.typingTimer;
                    var subject = $.extend({},Modes.insert.selectedTexts[0]);
                    subject.text = oldText;
                    delete subject.canvas;
                    console.log("Text being sent after",Date.now() - startTime,subject);
                    sendStanza(subject);
                }
                var checkTyping = function(){
                    var t = Date.now();
                    var elapsed = t - startTime;
                    if(oldText == newText || changeToTextBoxMade){
                        if(elapsed > typingDelay){
                            changeToTextBoxMade = false;
                            typingTimerElapsed();
                        }
                        else{
                            Modes.insert.typingTimer = setTimeout(checkTyping,typingTicks);
                        }
                    }
                    else{
                        oldText = newText;
                        startTime = Date.now();
                        Modes.insert.typingTimer = setTimeout(checkTyping,typingTicks);
                    }
                };
                $("#deleteTextUnderEdit").unbind("click").on("click",function(){
                    deletedStanza = Modes.insert.selectedTexts[0];
                    updateStatus(sprintf("Deleted %s",deletedStanza.identity));
                    var deleteTransform = batchTransform();
                    deleteTransform.isDeleted = true;
                    deleteTransform.textIds = [deletedStanza.identity];
                    sendStanza(deleteTransform);
                    upload.hide();
                });
                updateStatus("Text input mode");
                var noop = function(){};
                var handleFilesSelected = function(worldPos){
                    return function(e){
                        e.stopPropagation();
                        e.preventDefault();
                        var files = e.target.files || e.dataTransfer.files;
                        var limit = files.length;
                        var file = files[0];
                        if (file.type.indexOf("image") == 0) {
                            var t = Date.now();
                            var identity = sprintf("%s%s",username,t);
                            var actionsCompleted = 0;
                            var necessaryActions = 5;//readLocal,thumb,upload,sendStanza,receiveStanza
                            var reader = new FileReader();
                            var pos = worldToScreen(worldPos.x,worldPos.y);
                            var pkg = $("<div />").css({
                                position:"relative",
                                left:px(pos.left),
                                top:px(pos.top)
                            }).insertAfter($("#marquee"));
                            var thumbnail = $("<img />").css({
                                position:"absolute",
                                top:0,
                                left:0
                            }).appendTo(pkg);
                            var p = progress().value(actionsCompleted).max(necessaryActions);
                            p.element.css({
                                margin:"auto"
                            });
                            var progressContainer = $("<div />").css({
                                position:"absolute",
                                top:0,
                                left:0,
                                width:"100%",
                                height:"100%",
                                "text-align":"center"
                            }).appendTo(pkg).append(p.element);
                            var statusMesage = $("<div />").appendTo(pkg);
                            var img = new Image();
                            updateTracking(identity,function(){
                                actionsCompleted++;
                                p.value(actionsCompleted);
                                if(actionsCompleted >= necessaryActions){
                                    stopTracking(identity);
                                }
                            },function(){
                                pkg.remove();
                            });
                            reader.onload = function(e){
                                updateTracking(identity);
                                img.onload = function(e){
                                    var sx = viewboxWidth / img.width;
                                    var sy = viewboxHeight / img.height;
                                    var shift = 1 / (Math.min(sx,sy) - 0.1);
                                    var scale = viewboxWidth / boardWidth;
                                    if(shift > 1){
                                        console.log("Zooming out to accomodate image",shift,viewboxWidth,viewboxHeight,img.width,img.height);
                                        Zoom.shift(shift,true);//Leave a little space around the edges
                                    }
                                    scale = viewboxWidth / boardWidth;
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
                                        width:px(screenWidth),
                                        height:px(screenHeight),
                                        "background-color":"lightgray",
                                        border:"2px dotted blue"
                                    });
                                    thumbnail.css({
                                        opacity:0.1,
                                        width:px(screenWidth),
                                        height:px(screenHeight)
                                    });
                                    thumbnail.attr("src",img.src);
                                    updateTracking(identity);
                                    var currentSlide = Conversations.getCurrentSlideJid();
                                    var url = sprintf("/uploadDataUri?jid=%s&filename=%s",currentSlide.toString(),encodeURI(file.name.split(".")[0]));
                                    $.ajax({
                                        url: url,
                                        type: 'POST',
                                        success: function(e){
                                            updateTracking(identity);
                                            var loader = $("#upload");
                                            var imageStanza = {
                                                type:"image",
                                                author:username,
                                                timestamp:t,
                                                tag:identity,
                                                identity:identity,
                                                slide:currentSlide.toString(),
                                                source:$(e).text(),
                                                width:img.width,
                                                height:img.height,
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
                                        data:img.src,
                                        cache: false,
                                        contentType: false,
                                        processData: false
                                    });
                                };
                                img.src = e.target.result;
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
                    // start of new canvas handler behaviour
                    var options = $("<div />",{
                        id:"insertOptions",
                        class:"insertOptionsContainer"
                    }).css({
                        position:"absolute",
                        left:px(x - 30),
                        top:px(y)
                    }).insertAfter("#marquee");

                    if(Modes.insert.currentInsertMode == "image"){
                        var insertImage = $("<input />",{
                            type:"file",
                            class:"fileUploadControl"
                        }).appendTo(options);
                        insertImage[0].addEventListener("change",function(e){
                            clearOptions();
                            handleFilesSelected(worldPos)(e);
                        },false)
                        return options;
                    } else if (Modes.insert.currentInsertMode == "text"){
                        onText();
                        return false;
                    } else {
                        return false;
                    }
                    //end of new canvas handler behaviour - replaced code appears in comments below.
                    /*
                     if(Modes.insert[uploadKey]){
                     var options = $("<div />",{
                     id:"insertOptions"
                     }).css({
                     position:"absolute",
                     left:px(x - 30),
                     top:px(y)
                     }).insertAfter("#marquee");
                     var insertText = $("<input />",{
                     type:"button",
                     value:"Insert textbox"
                     }).css({
                     "margin-right":"5em"
                     }).click(onText).appendTo(options);
                     var insertImage = $("<input />",{
                     type:"file"
                     }).appendTo(options);
                     insertImage[0].addEventListener("change",function(e){
                     clearOptions();
                     handleFilesSelected(worldPos)(e);
                     },false);
                     return options;
                     }
                     else{
                     onText();
                     return false;
                     }
                     */
                }
                var color = "Black";
                var updateTextFont = function(t){
                    t.font = sprintf("%spx %s",t.size,t.family);
                }
                var createBlankText = function(worldPos){
                    var id = sprintf("%s%s",username,Date.now());
                    var style = Modes.insert.currentStyle;
                    var currentSlide = Conversations.getCurrentSlideJid();
                    var text = {
                        author:username,
                        color:["#666666",255],
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
                    Modes.insert.selectedTexts.push(text);
                    return text;
                };
                var up = function(x,y,worldPos){
                    if(Modes.insert.typingTimer){
                        typingTimerElapsed();
                    }
                    $("#insertOptions").remove();
                    $("#textEditor").remove();
                    var newScreenPos = worldToScreen(worldPos.x,worldPos.y);
                    var threshold = 10;
                    var ray = [worldPos.x - threshold,worldPos.y - threshold,worldPos.x + threshold,worldPos.y + threshold];
                    Modes.insert.selectedTexts  = _.values(boardContent.texts).filter(function(text){
                        return intersectRect(text.bounds,ray);
                    });
                    var textCustomizationOptions = function(){
                        var unparseColor = function(inputColor){
                            if (inputColor[0] == "#ff0000" && inputColor[1] == 255){
                                return "red";
                            } else if (inputColor[0] == "#0000ff" && inputColor[1] == 255){
                                return "blue";
                            } else if (inputColor[0] == "#00ffff" && inputColor[1] == 255){
                                // I think I'm wrong about yellow - this might be cyan
                                return "yellow";
                            } else if (inputColor[0] == "#00ff00" && inputColor[1] == 255){
                                return "green";
                            } else {
                                return "black";
                            }
                        };
                        var parseColor = function(inputString){
                            if (inputString == "red"){
                                return ["#ff0000",255];
                            } else if (inputString == "blue"){
                                return ["#0000ff",255];
                            } else if (inputString == "yellow"){
                                // I think I'm wrong about yellow - this might be cyan
                                return ["#00ffff",255];
                            } else if (inputString == "green"){
                                return ["#00ff00",255];
                            } else {
                                return ["#000000",255];
                            }
                        };
                        var options = $("<div />",{
                            class:"textOptionsContainer"
                        });
                        var alteredText = function(t){
                            changeToTextBoxMade = true;
                            updateTextFont(t);
                            prerenderText(t);
                            checkTyping();
                            return t;
                        }
                        var dropdown = function(opts,property,transform,transformBack){
                            var value = text[property];
                            var cont = $("<select />",{
                                class:"textOptionsDropdown"
                            }).html(unwrap(opts.map(function(opt){
                                var transformedValue = transformBack ? transformBack(value) : value;
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
                                editText();
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
                                editText();
                            }).appendTo(el);
                            if (transformedValue){
                                cb.prop("checked",true);
                            } else {
                                cb.prop("checked",false);
                            }
                            options.append(el)
                        }
                        dropdown(["Arial","Helvetica","Times New Roman"],"family");
                        dropdown(["8","10","14","20","30"],"size",parseInt);
                        dropdown(["red","black","green","blue","yellow"],"color",parseColor,unparseColor);
                        toggle("bold","weight",function(bool){if (bool){ return "bold";} else {return "Normal";}},function(string){return string == "bold";});
                        toggle("underline","decoration",function(bool){if (bool) {return "underline";} else {return "Normal";}}, function(string){return string == "underline";});
                        return options;
                    }
                    var openingEditBox = false;
                    var editText = function(){
                        if(openingEditBox){
                            oldText = text.text;
                            openingEditBox = false;
                        }
                        var chars = Math.max.apply(Math,_.pluck(text.runs,"length"));
                        var xInset = (boardWidth / 2 - text.width / 2);
                        var yInset = (boardHeight / 2 - text.height / 2);
                        var xDelta = text.bounds[0] - viewboxX - xInset;
                        var yDelta = text.bounds[1] - viewboxY - yInset;
                        Extend.shift(xDelta,yDelta,function(){
                            var b = text.bounds;
                            var newPos = worldToScreen(
                                b[0],
                                b[1]);
                            var input = $("<textarea />").css({
                                "font-family":text.family,
                                "font-size":px(text.size),
                                width:px(text.width * 1.1),
                                height:px(text.height)
                            });
                            input.on("keyup",function(){
                                newText = $(this).val();
                                if(!Modes.insert.typingTimer){
                                    Modes.insert.typingTimer = setTimeout(checkTyping,typingTicks);
                                }
                            });
                            $("#textEditor").remove();
                            var textEditor = $("<div />",{
                                id:"textEditor"
                            }).css({
                                position:"absolute",
                                left:px(newPos.x),
                                top:px(newPos.y)
                            }).append(input).append(textCustomizationOptions()).insertAfter("#marquee");
                            input.val(text.runs.join("\n"));
                            input.focus();
                        });
                    }
                    var text = Modes.insert.selectedTexts[0];
                    //start new handling of text vs image modes on click
                    if(Modes.insert.currentInsertMode != "text" || !text){
                        //end new handling of text vs image modes on click - replaced code appears below.
                        //if(!text){
                        var options = newInsertOptions(
                            newScreenPos.x,newScreenPos.y,worldPos,
                            function(){
                                text = createBlankText(worldPos);
                                editText();
                            },
                            function(){
                            });
                        if(options){
                            $("#marquee").after(options);
                        }
                    }
                    else{
                        openingEditBox = true;
                        editText();
                    }
                }
                registerPositionHandlers(board,noop,noop,up);
            },
            deactivate:function(){
                if(Modes.insert.typingTimer){
                    typingTimerElapsed();
                }
                $("#insertOptions").remove();
                $("#textEditor").remove();
                $("#textTools .modeSpecificTool").unbind("click");
                unregisterPositionHandlers(board);
                removeActiveMode();
            }
        },
        pan:{
            name:"pan",
            drawTools:function(){
            },
            activate:function(){
                updateStatus("PAN");
                Modes.currentMode.deactivate();
                setActiveMode("#panTools","#panMode");
                Modes.currentMode = Modes.pan;
                Modes.pan.drawTools();
                var originX;
                var originY;
                var down = function(x,y){
                    originX = x;
                    originY = y;
                }
                var move = function(x,y){
                    var xDelta = x - originX;
                    var yDelta = y - originY;
                    Pan.shift(xDelta,yDelta);
                    originX = x;
                    originY = y;
                }
                var up = function(x,y){
                    reportViewboxMoved();
                }
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
                $("#selectionAdorner").empty();
                _.forEach(["images","texts","inks"],function(category){
                    var cat = sel[category];
                    if(cat){
                        $.each(cat,function(i,item){
                            drawSelectionBounds(item);
                        });
                    }
                });
            };
            var clearSelectionFunction = function(){
                Modes.select.selected = {images:{},text:{},inks:{}};
                Progress.call("onSelectionChanged",[Modes.select.selected]);
            }
            var updateSelectionWhenBoardChanges = _.debounce(function(){
                var is = "inks";
                var hl = "highlighters";
                _.forEach(["images","texts",is,hl],function(catName){
                    var selCatName = catName == hl ? is : hl;
                    var boardCatName = catName;
                    if (Modes && Modes.select && Modes.select.selected && catName in Modes.select.selected){
                        var cat = Modes.select.selected[selCatName];
                        if(cat) {
                            _.forEach(cat,function(i){
                                var id = i.identity;
                                if (cat && boardCatName in boardContent && id in boardContent[boardCatName]){
                                    cat[id] = boardContent[catName][id];
                                }
                                else {
                                    if(selCatName == "inks"){
                                        if(boardCatName == "highlighters" && id in cat && cat[id].isHighlighter){
                                            delete cat[id];
                                        }
                                        else if(boardCatName == "inks" && id in cat && cat[id].isHighlighter == false){
                                            delete cat[id];
                                        }
                                        else{
                                            delete cat[id];
                                        }
                                    }
                                }
                            });
                        }
                    }
                });
                Progress.call("onSelectionChanged",[Modes.select.selected]);
            },100);
            Progress.onBoardContentChanged["ModesSelect"] = updateSelectionWhenBoardChanges;
            Progress.viewboxMoved["ModesSelect"] = updateSelectionWhenBoardChanges;
            Progress.onSelectionChanged["ModesSelect"] = updateSelectionVisualState;
            Progress.historyReceived["ModesSelect"] = clearSelectionFunction;
            return {
                name:"select",
                selected:{
                    images:{},
                    text:{},
                    inks:{}
                },
                clearSelection:clearSelectionFunction,
                drawTools:function(){},
                activate:function(){
                    Modes.currentMode.deactivate();
                    Modes.currentMode = Modes.select;
                    setActiveMode("#selectTools","#selectMode");
                    Modes.select.drawTools();
                    var marqueeOriginX;
                    var marqueeOriginY;
                    var lastX;
                    var lastY;
                    var marqueeWorldOrigin;
                    var marquee = $("#marquee");
                    var adorner = $("#selectionAdorner");
                    var dragging = false;
                    var resizing = false;
                    $("#delete").bind("click",function(){
                        var deleteTransform = batchTransform();
                        deleteTransform.isDeleted = true;
                        deleteTransform.inkIds = _.keys(Modes.select.selected.inks);
                        deleteTransform.textIds = _.keys(Modes.select.selected.texts);
                        deleteTransform.imageIds = _.keys(Modes.select.selected.images);
                        sendStanza(deleteTransform);
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
                                src:"/static/images/resizeHandle",
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
                        if(modifiers.ctrl){//You can't ctrl-click to resize or drag
                        }
                        else if(intersectRect(resizeHandle,[x-threshold,y-threshold,x+threshold,y+threshold])){
                            updateStatus("Resizing");
                            resizing = true;
                        }
                        else{
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
                            marquee.show();
                            marquee.css({
                                left:px(x),
                                top:px(y)
                            });
                            adorner.empty();
                            adorner.append(marquee);
                        }
                    };
                    var move = function(x,y){
                        var xDelta = x - lastX;
                        var yDelta = y - lastY;
                        lastX = x;
                        lastY = y;
                        if(resizing){
                            var xScale = x / resizeHandle[0];
                            updateStatus(sprintf("Resizing %s%%",xScale * 100));
                            $(".selectionAdorner").map(function(){
                                var a = $(this);
                                a.css({
                                    width:a.data("originalWidth") * xScale,
                                    height:a.data("originalHeight") * xScale
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
                            var width = x - marqueeOriginX;
                            var height = y - marqueeOriginY;
                            marquee.css({
                                width:px(width),
                                height:px(height)
                            });
                        }
                    };
                    var up = function(x,y,worldPos,modifiers){
                        if(dragging){
                            var moved = batchTransform();
                            moved.xTranslate = worldPos.x - marqueeWorldOrigin.x;
                            moved.yTranslate = worldPos.y - marqueeWorldOrigin.y;
                            moved.inkIds = _.keys(Modes.select.selected.inks);
                            moved.textIds = _.keys(Modes.select.selected.texts);
                            moved.imageIds = _.keys(Modes.select.selected.images);
                            console.log("Moved",moved);
                            sendStanza(moved);
                        }
                        else if(resizing){
                            var resized = batchTransform();
                            var xScale = x / resizeHandle[0];
                            resized.inkIds = _.keys(Modes.select.selected.inks);
                            resized.textIds = _.keys(Modes.select.selected.texts);
                            resized.imageIds = _.keys(Modes.select.selected.images);
                            resized.xScale = xScale;
                            resized.yScale = xScale;
                            sendStanza(resized);
                            resizing = false;
                        }
                        else{
                            var selectionBounds = [marqueeWorldOrigin.x,marqueeWorldOrigin.y,worldPos.x,worldPos.y];
                            console.log("Selection bounds",selectionBounds);
                            var intersected = {
                                images:{},
                                texts:{},
                                inks:{}
                            };
                            var intersectAuthors = {};
                            var intersectCategory = function(category){
                                $.each(boardContent[category],function(i,item){
                                    if(intersectRect(item.bounds,selectionBounds)){
                                        incrementKey(intersectAuthors,item.author);
                                        if(item.author == username){
                                            intersected[category][item.identity] = item;
                                        }
                                    }
                                });
                            }
                            categories(intersectCategory);
                            $.each(boardContent.highlighters,function(i,item){
                                if(intersectRect(item.bounds,selectionBounds)){
                                    incrementKey(intersectAuthors,item.author);
                                    if(item.author == username){
                                        intersected.inks[item.identity] = item;
                                        console.log("Picked up highlighted item",item);
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
                            Progress.call("onSelectionChanged",[Modes.select.selected]);
                            updateStatus(status);
                        }
                        marquee.css(
                            {width:0,height:0}
                        ).hide();
                        dragging = false;
                        resizing = false;
                    }
                    registerPositionHandlers(board,down,move,up);
                },
                deactivate:function(){
                    unregisterPositionHandlers(board);
                    removeActiveMode();
                    $("#delete").unbind("click");
                    $("#resize").unbind("click");
                    $("#selectionAdorner").empty();
                    $("#marquee").hide();
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
                }).appendTo($("#selectionAdorner"));
                var startX = 0;
                var startY = 0;
                var startWorldPos;
                var proportion;
                var down = function(x,y,worldPos){
                    proportion = boardHeight / boardWidth;
                    startX = x;
                    startY = y;
                    startWorldPos = worldPos;
                    marquee.show();
                    marquee.appendTo($("#selectionAdorner"));
                    marquee.css({
                        left:px(x),
                        top:px(y),
                        width:0,
                        height:0
                    });
                }
                var move = function(x,y,worldPos){
                    var width = x - startX;
                    var height = y - startY;
                    var dimensions = aspectConstrainedDimensions(width,height);
                    marquee.css({
                        width:px(dimensions.width),
                        height:px(dimensions.height)
                    });
                }
                var up = function(x,y,worldPos){
                    marquee.hide();
                    var vX = contentOffsetX + startWorldPos.x;
                    var vY = contentOffsetY + startWorldPos.y;
                    var width = Math.abs(worldPos.x - startWorldPos.x);
                    var height = Math.abs(worldPos.y - startWorldPos.y);
                    var dimensions = aspectConstrainedDimensions(width,height);
                    var vW = dimensions.width;
                    var vH = dimensions.height;
                    requestedViewboxWidth = vW;
                    requestedViewboxHeight = vH;
                    includeView(vX,vY,vW,vH);
                    blit();
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
            };
            Progress.onConversationJoin["setConversationRole"] = applyStateStyling;
            var teacherTools=function(){
                var tools = $("<div />");
                $("<div/>",{
                    id:"changeCollaborationModeButton",
                    class:"modeSpecificTool",
                    text:Conversations.getConversationModeDescriptor()
                }).on("click",function(){
                    var currentConversation = Conversations.getCurrentConversation();
                    if (currentConversation && currentConversation.permissions.studentCanPublish){
                        Conversations.changeConversationToLecture();
                    } else {
                        Conversations.changeConversationToTutorial();
                    }
                }).appendTo(tools);
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
                    text:"Show Quizzes"
                }).on("click",function(){
                    showBackstage("quizzesPopup");
                }).appendTo(tools);
                $("<div/>",{
                    class:"modeSpecificTool",
                    text:"Show Submissions"
                }).on("click",function(){
                    showBackstage("submissionsPopup");
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
                $("<div/>",{
                    id:"syncToTeacherButton",
                    class:"modeSpecificTool",
                    text:Conversations.getIsSyncedToTeacherDescriptor()
                }).on("click",function(){
                    Conversations.toggleSyncMove();
                    applyStateStyling();
                }).appendTo(tools);
                $("<div/>",{
                    class:"modeSpecificTool",
                    text:"Show Quizzes"
                }).on("click",function(){
                    showBackstage("quizzesPopup");
                }).appendTo(tools);
                $("<div/>",{
                    class:"modeSpecificTool",
                    text:"Show Submissions"
                }).on("click",function(){
                    showBackstage("submissionsPopup");
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
                }
            };
        })(),
        draw:{
            name:"draw",
            brushes:[
                {
                    width:2.0,
                    color:"#000000",
                    isHighlighter:false
                },
                {
                    width:5.0,
                    color:"#FF0000",
                    isHighlighter:false
                },
                {
                    width:30.0,
                    color:"#FFFF00",
                    isHighlighter:true
                }
            ],
            erasing:false,
            drawTools:function(){
                $(".activeBrush").removeClass("activeBrush");
                $("#drawTools").empty().html(
                    Modes.draw.brushes.map(function(brush){
                        var dot = Canvas.circle(brush.color,brush.width);
                        var dotButton = $("<div />")
                                .click(function(){
                                    Modes.draw.erasing = false;
                                    Modes.draw.drawingAttributes = brush;
                                    $(".activeBrush").removeClass("activeBrush");
                                    $(this).addClass("activeBrush");
                                    Modes.draw.drawAdvancedTools(Modes.draw.drawingAttributes);
                                })
                                .addClass("modeSpecificTool")
                                .append(dot)[0];
                        if (brush == Modes.draw.drawingAttributes){
                            $(dotButton).addClass("activeBrush");
                        } else {
                            $(dotButton).removeClass("activeBrush");
                        }
                        return dotButton;
                    }))
                    .append($("<div />",{
                        text:"Erase",
                        class:"modeSpecificTool",
                        click:function(){
                            Modes.draw.erasing = true;
                            $(".activeBrush").removeClass("activeBrush");
                            $(this).addClass("activeBrush");
                        }
                    }))
                    .append($("<div />",{
                        text:"More"
                    }).addClass("modeSpecificTool")
                            .click(function(){
                                Modes.draw.drawAdvancedTools(Modes.draw.drawingAttributes);
                                showBackstage("customizeBrush");
                            }));
            },
            drawAdvancedTools : function(brush){
                var dots = $("<table />",{
                    class:"dots"
                });
                var colors = ["#000000","#FF0000","#FFFF00","#00FF00","#0000FF"];
                var widths = [2,4,5,8,10,16,30];
                var offset = widths[widths.length-1];
                widths.map(function(width){
                    var dotGroup = $("<tr />");
                    colors.map(function(color){
                        var dot = Canvas.circle(color,width);
                        if(width == brush.width && color == brush.color){
                            dot.addClass("activeTool");
                        }
                        dotGroup.append($("<td />").css({
                            "vertical-align":"middle"
                        }).click(function(){
                            brush.width = width;
                            brush.color = color;
                            Modes.draw.drawTools();
                            window.hideBackstage();
                        }).append(dot));
                    });
                    dots.append(dotGroup);
                });
                $("#colors").html(dots);
                $("#colors td").css({
                    width:px(offset*3),
                    height:px(offset*3)
                });
            },
            activate:function(){
                if(!Modes.draw.drawingAttributes){
                    Modes.draw.drawingAttributes = Modes.draw.brushes[0];
                }
                var mousePressure = 128;
                Modes.currentMode.deactivate();
                Modes.currentMode = Modes.draw;

                setActiveMode("#drawTools","#drawMode");
                Modes.draw.drawTools();
                var currentStroke = [];
                var isDown = false;
                var resumeWork;
                var down = function(x,y){
                    deleted = [];
                    isDown = true;
                    WorkQueue.canWork(false);
                    if(!Modes.draw.erasing){
                        boardContext.strokeStyle = Modes.draw.drawingAttributes.color;
                        boardContext.lineWidth = Modes.draw.drawingAttributes.lineWidth * 128 * mousePressure;
                        boardContext.beginPath();
                        boardContext.moveTo(x,y);
                        currentStroke = [x, y, mousePressure];
                    }
                };
                var raySpan = 10;
                var deleted = [];
                var move = function(x,y,worldPos){
                    if(Modes.draw.erasing){
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
                        boardContext.lineTo(x,y);
                        boardContext.stroke();
                        currentStroke = currentStroke.concat([x,y,mousePressure]);
                    }
                };
                var up = function(x,y){
                    isDown = false;
                    if(resumeWork){
                        clearTimeout(resumeWork);
                    }
                    resumeWork = setTimeout(function(){
                        WorkQueue.canWork(!isDown);
                    },1000);
                    if(Modes.draw.erasing){
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
                console.log("Deactivating ink");
                removeActiveMode();
                WorkQueue.canWork(true);
                unregisterPositionHandlers(board);
                if(window.currentBackstage == "customizeBrushPopup"){
                    window.hideBackstage();
                }
            }
        },
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
