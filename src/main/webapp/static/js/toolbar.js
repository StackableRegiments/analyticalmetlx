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
						takeControlOfViewbox();
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
						var currentText = {};	
            var marquee = undefined;
            var typingTimer = undefined;
            var selectedTexts = [];
            var typingDelay = 1000;
            var typingTicks = 100;
            var startTime = Date.now();
            var changeToTextBoxMade = false;
            var currentCaretPos = undefined;
            var currentScrollTop = 0;
            var lineWithSeparatorRatio = 1.3; //magic number?
            var oldText = "";
            var newText = "";
						var currentFamily = Fonts.getAllFamilies()[0];
						var currentSize = Fonts.getAllSizes()[0];
            var typingTimerElapsed = function(){
							WorkQueue.gracefullyResume();
							clearTimeout(typingTimer);
							typingTimer = undefined;
							var subject = $.extend({},currentText);
							subject.text = oldText;
							delete subject.canvas;
							sendStanza(subject);
            }
            var checkTyping = function(){
							WorkQueue.pause();
							var oldTextTest = oldText;
							oldText = newText;
							currentText.text = newText;
							selectedTexts = [currentText];
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
						var hasInitialized = false;

						var textEditor = undefined;
						var textEditorInput = undefined;
						var fontFamilySelector = undefined;
						var fontSizeSelector = undefined;
						var fontColorSelector = undefined;
						var fontBoldSelector = undefined;
						var fontItalicSelector = undefined;
						var fontUnderlineSelector = undefined;
            
						var updateTextFont = function(t){
                t.font = sprintf("%spx %s",t.size,t.family);
            }
						var updateTextEditor = function(){
							if ("type" in currentText && currentText.type == "text"){
								var h = undefined;
								prerenderText(currentText);
								if ("runs" in currentText && !(_.size(currentText.runs) == 1 && currentText.runs[0] == "")){
									h = px(currentText.runs.length * currentText.size * lineWithSeparatorRatio);
									textEditorInput.val(currentText.runs.join("\n"));
								} else {
									h = px(currentText.size);
									textEditorInput.val(currentText.text);
								}

								var screenPos = worldToScreen(currentText.x,currentText.y);
                var possiblyAdjustedHeight = Math.max(currentText.height,h);
                var possiblyAdjustedWidth = currentText.width * 1.1;
                var possiblyAdjustedX = screenPos.x;
                var possiblyAdjustedY = screenPos.y;
                var acceptableMaxHeight = boardHeight * 0.7;
                var acceptableMaxWidth = boardWidth * 0.7;
                var acceptableMinX = 30;
                var acceptableMinY = 30;
                var acceptableMaxX = boardWidth - 100;
                var acceptableMaxY = boardHeight - 100; //this should check the size of the updatedTextEditor, to ensure that it doesn't go off the bottom of the screen

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
								// there is now only one spot the textEditor is located on the screen, and it's here, if you want to move it about or keep it on screen, etc.
                textEditor.css({
									position:"absolute",
									left:px(possiblyAdjustedX),
									top:px(possiblyAdjustedY),
									/*width:px(possiblyAdjustedWidth),*/
									"min-width":px(240)
                });
								updateTextFont(currentText);
                textEditorInput.css({
									width:px(possiblyAdjustedWidth),
									"font-weight": currentText.weight,
									"font-style": currentText.style,
									"text-decoration": currentText.decoration,
									"color": currentText.color[0],
									"min-height":h,
									"font-family":currentText.family,
									"font-size":px(currentText.size)
                });
                if ("setSelectionRange" in textEditorInput){
									textEditorInput.setSelectionRange(currentCaretPos,currentCaretPos);
									$(textEditorInput).scrollTop(currentScrollTop);
                }
								$("#textEditorClose").on("click",function(){
									textEditor.hide();
								});
								fontFamilySelector.value = currentText["family"];
								fontSizeSelector.value = currentText["size"];
								fontColorSelector.value = currentText["color"];
								if (currentText.weight == "bold"){
									fontBoldSelector.addClass("active");
								} else {
									fontBoldSelector.removeClass("active");
								}
								if (currentText.style == "italic"){
									fontItalicSelector.addClass("active");
								} else {
									fontItalicSelector.removeClass("active");
								}
								if (currentText.decoration == "underline"){
									fontUnderlineSelector.addClass("active");
								} else {
									fontUnderlineSelector.removeClass("active");
								}
								textEditor.show();
								textEditorInput.focus();
							} else {
								textEditor.hide();
							}
						};
						if (!hasInitialized){
							$(function(){
								hasInitialized = true;
								textEditor = $("#textEditor");
								textEditorInput = $("#textEditorInputArea");
								fontFamilySelector = $("#fontFamilySelector");
								fontSizeSelector = $("#fontSizeSelector");
								fontColorSelector = $("#fontColorSelector");
								fontBoldSelector = $("#fontBoldSelector");
								fontItalicSelector = $("#fontItalicSelector");
								fontUnderlineSelector = $("#fontUnderlineSelector");
								textEditor.hide();
								var fontFamilyOptionTemplate = fontFamilySelector.find(".fontFamilyOption").clone();
								fontFamilySelector.empty();
								Fonts.getAllFamilies().map(function(family){
									fontFamilySelector.append(fontFamilyOptionTemplate.clone().attr("value",family).text(family));
								});
								fontFamilySelector.on("change",function(e){
									var newFamily = $(this).val();
									currentFamily = newFamily;
									if ("family" in currentText){
										currentText["family"] = newFamily;
										typingTimerElapsed();
									}
									updateTextEditor();
								});
								var fontSizeOptionTemplate = fontSizeSelector.find(".fontSizeOption").clone();
								fontSizeSelector.empty();
								Fonts.getAllSizes().map(function(size){
									fontSizeSelector.append(fontSizeOptionTemplate.clone().attr("value",size).text(size));
								});
								fontSizeSelector.on("change",function(e){
									var newSizeInt = parseInt($(this).val());
									currentSize = newSizeInt;
									if ("size" in currentText){
										currentText["size"] = newSizeInt;
										typingTimerElapsed();
									}
									updateTextEditor();
								});
								var fontColorOptionTemplate = fontColorSelector.find(".fontColorOption").clone();
								fontColorSelector.empty();
								Colors.getAllNamedColors().map(function(color){
									fontColorSelector.append(fontColorOptionTemplate.clone().attr("value",color.rgb).text(color.name));
								});
								fontColorSelector.on("change",function(e){
									var newColor = $(this).val();
									if ("color" in currentText){
										currentText["color"] = Colors.getColorForName(newColor);
										typingTimerElapsed();
									}
									updateTextEditor();
								});
								fontBoldSelector.on("click",function(){
									if ("weight" in currentText){
										currentText["weight"] = !fontBoldSelector.hasClass("active") ? "bold" : "Normal";
										typingTimerElapsed();
									}
									updateTextEditor();
								});
								fontItalicSelector.on("click",function(){
									if ("style" in currentText){
										currentText["style"] = !fontItalicSelector.hasClass("active") ? "italic" : "Normal";
										typingTimerElapsed();
									}
									updateTextEditor();
								});
								fontUnderlineSelector.on("click",function(){
									if ("decoration" in currentText){
										currentText["decoration"] = !fontUnderlineSelector.hasClass("active") ? "underline" : "Normal";
									}
									updateTextEditor();
									typingTimerElapsed();
								});
								textEditorInput.keyup(function(e){
									if ("type" in currentText && currentText.type == "text"){
										e.stopPropagation();
										var el = $(this).get(0);
										if ("selectionStart" in el && currentCaretPos != undefined){
												currentCaretPos = el.selectionStart;
												currentScrollTop = el.scrollTop;
										}
										if (e.ctrlKey){
											if(e.keyCode == 73) {
												if(currentText.style == "Normal"){
														currentText["style"] = "italic";
												}
												else {
														currentText["style"] = "Normal";
												}
												updateTextEditor();
												sendText();
											}
											if (e.keyCode == 66){
												if (currentText.weight == "Normal"){
														currentText["weight"] = "bold";
												} else {
														currentText["weight"] = "Normal";
												}
												updateTextEditor();
												typingTimerElapsed();
											}
										} else {
											newText = $(this).val();
											checkTyping();
										}
									}
								}).keydown(function(e){
									if ("type" in currentText && currentText.type == "text"){
										e.stopPropagation();
									}
								});
								textEditorInput.bind("paste",function(e){
									if ("type" in currentText && currentText.type == "text"){
										var thisBox = $(this);
										if ("type" in e && e.type == "paste"){
											window.setTimeout(function(){
												newText = thisBox.val();
												checkTyping();
											},0);
										}
									}
								});
							});
						}
            var removeTextEditor = function(){
							if(typingTimer){
								typingTimerElapsed();
							}
							currentText = {};
							$("#textEditor").hide();
            };
            var createBlankText = function(worldPos){
							var id = sprintf("%s%s",UserSettings.getUsername(),Date.now());
							var currentSlide = Conversations.getCurrentSlideJid();
							var text = {
								author:UserSettings.getUsername(),
								color:Colors.getColorForName("black"),
								decoration:"None",
								identity:id,
								privacy:Privacy.getCurrentPrivacy(),
								family:currentFamily,
								size:currentSize,
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
							prerenderText(text);
							selectedTexts = [];
							selectedTexts.push(text);
							currentText = text;
							updateTextEditor();
							return text;
            };
            var alteredText = function(t){
							changeToTextBoxMade = true;
							updateTextFont(t);
							prerenderText(t);
							checkTyping();
							return t;
            };

            var editText = function(text){
							oldText = text.text;
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
							updateTextEditor();
            }
            var possiblyClearEditBoxesFunction = _.debounce(function(){
							var newSelectedTexts = [];
							_.forEach(selectedTexts,function(st){
								if ("texts" in boardContent && "identity" in st && st.identity in boardContent.texts && "slide" in st && st.slide.toLowerCase() == Conversations.getCurrentSlideJid().toLowerCase()){
									newSelectedTexts.push(st);
								} else if ("runs" in st && _.size(st.runs) == 1 && st.runs[0] == "" && "slide" in st && st.slide.toLowerCase() == Conversations.getCurrentSlideJid().toLowerCase()){
									newSelectedTexts.push(st);
								}
							});
							selectedTexts = newSelectedTexts;
							if (Modes.currentMode == Modes.text){
								/*
								if (!("type" in currentText) && (currentText.type != "text")){
									currentText = selectedTexts[0];
								}
								*/
								if (_.size(selectedTexts) > 0 ){
									selectedTexts[0] = currentText;
									var view = [viewboxX,viewboxY,viewboxX+viewboxWidth,viewboxY+viewboxWidth];
									if (intersectRect(currentText.bounds,view)){
										editText(currentText);
										drawSelectionBounds(currentText);
									} else {
										removeTextEditor();
									}
								} else {
									removeTextEditor();
								}
							}
            },200);
            Progress.onBoardContentChanged["Modes.text"] = possiblyClearEditBoxesFunction;
            Progress.onSelectionChanged["Modes.text"] = possiblyClearEditBoxesFunction;
            Progress.historyReceived["Modes.text"] = possiblyClearEditBoxesFunction;
            Progress.onViewboxChanged["Modes.text"] = possiblyClearEditBoxesFunction;
						var noop = function(){};
            return {
							activate:function(){
								marquee = $("#textMarquee");
								Modes.currentMode.deactivate();
								Modes.currentMode = Modes.text;
								setActiveMode("#textTools","#insertText");
								$(".activeBrush").removeClass("activeBrush");
								Progress.call("onLayoutUpdated");
								$("#minorText").click(function(){});
								$("#deleteTextUnderEdit").unbind("click").on("click",bounceAnd(function(){
									deletedStanza = selectedTexts[0];
									updateStatus(sprintf("Deleted %s",deletedStanza.identity));
									var deleteTransform = batchTransform();
									deleteTransform.isDeleted = true;
									deleteTransform.textIds = [deletedStanza.identity];
									sendStanza(deleteTransform);
								}));
								updateStatus("Text input mode");
								var up = function(x,y,worldPos){
									if(typingTimer){
										typingTimerElapsed();
									}
									marquee.show();
									marquee.css({
										left:px(x),
										top:px(y)
									});
									oldText = "";
									newText = "";
									var newScreenPos = worldToScreen(worldPos.x,worldPos.y);
									var threshold = 10;
									var ray = [worldPos.x - threshold,worldPos.y - threshold,worldPos.x + threshold,worldPos.y + threshold];
									currentCaretPos = 0;
									currentScrollTop = 0;
									selectedTexts = _.values(boardContent.texts).filter(function(text){
										return intersectRect(text.bounds,ray) && text.author == UserSettings.getUsername();
									});
									if (selectedTexts.length > 0){
										currentText = selectedTexts[0];
										editText(currentText);
									} else {
										var newText = createBlankText(worldPos);
										currentText = newText;
										selectedTexts.push(newText);
										editText(newText);
									}
									Modes.select.texts = [currentText];
									progress.call("onSelectionChanged");
								}
								registerPositionHandlers(board,noop,noop,up);
							},
							deactivate:function(){
								removeTextEditor();
								selectedTexts = [];
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
						var	imageUploadX = undefined;
						var	imageUploadY = undefined;
						var	imageUploadWidth = undefined;
						var	imageUploadHeight = undefined;
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
												var scaleFactor = w / 300;
												scaledWidth = 300;
												scaledHeight = h / scaleFactor;
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

											currentImage.width = w;
											currentImage.height = h;
											//render canvas is responsible for the resizing.  The other canvas is a thumbnail.
											var renderCanvas = $("<canvas/>");
											renderCanvas.attr("width",w);
											renderCanvas.attr("height",h);
											renderCanvas.css({
													width:px(w),
													height: px(h)
											});
											renderCanvas[0].getContext("2d").drawImage(img,0,0,w,h);
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
							purrentImage = {};
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
								imageUploadButton.on("click",function(){
									if ("type" in currentImage && currentImage.type == "imageDefinition" && "resizedImage" in currentImage){
										var worldPos = {x:currentImage.x,y:currentImage.y};
										var screenPos= {x:currentImage.screenX,y:currentImage.screenY};
										WorkQueue.pause();
										var t = Date.now();
										var identity = sprintf("%s%s",UserSettings.getUsername(),t);
										var currentSlide = Conversations.getCurrentSlideJid();
										var url = sprintf("/uploadDataUri?jid=%s&filename=%s",currentSlide.toString(),encodeURI(identity));
										$.ajax({
											url: url,
											type: 'POST',
											success: function(e){
												resetImageUpload();
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
												console.log("Sending image stanza",imageStanza);
												sendStanza(imageStanza);
												WorkQueue.gracefullyResume();
											},
											error: function(e){
												resetImageUpload();
												alert("upload failed");
												console.log("image upload failed",e);
												WorkQueue.gracefullyResume();
											},
											data:currentImage.resizedImage,
											cache: false,
											contentType: false,
											processData: false
										});
									}
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
								var up = function(x,y,worldPos){
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
                var down = function(x,y){
										takeControlOfViewbox();
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
                                    console.log(overlap,selectionThreshold);
                                    if(overlap >= selectionThreshold){
                                        //if(intersectRect(item.bounds,selectionBounds)){
                                        incrementKey(intersectAuthors,item.author);
                                        if(item.author == UserSettings.getUsername()){
                                            intersected[category][item.identity] = item;
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
                    if(!hasActivated){
                        hasActivated = true;
                        currentBrush = Modes.draw.brushes[0];
                        Modes.draw.drawingAttributes = currentBrush;

                        var container = $("#drawTools");
                        _.each(container.find(".pen"),function(button,i){
                            var brush = Modes.draw.brushes[i];
                            $(button)
                                .css({color:brush.color})
                                .click(function(){
                                    $(".activeBrush").removeClass("activeBrush");
                                    $(this).addClass("activeBrush");
                                    currentBrush = brush;
                                    Modes.draw.drawingAttributes = currentBrush;
                                    erasing = false;
                                })
                                .find(".widthIndicator")
                                .text(brush.width);
                        });
                        container.find(".eraser").click(function(button){
                            $(".activeBrush").removeClass("activeBrush");
                            $(this).addClass("activeBrush");
                            erasing = true;
                        });
                        container.find("#penCustomizationButton").click(drawAdvancedTools);
                    }

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
                                drawAdvancedTools(brush);
                            })
                            var bar = Canvas.circle(brush.color,width,60);
                            console.log(width,brush.width);
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
                    setActiveMode("#drawTools","#drawMode");
                    var currentStroke = [];
                    var isDown = false;
                    var resumeWork;
                    var mousePressure = 128;
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
