(function() {
    var lastTime = 0;
    var vendors = ['ms', 'moz', 'webkit', 'o'];
    for(var x = 0; x < vendors.length && !window.requestAnimationFrame; ++x) {
        window.requestAnimationFrame = window[vendors[x]+'RequestAnimationFrame'];
        window.cancelRequestAnimationFrame = window[vendors[x]+
                                                    'CancelRequestAnimationFrame'];
    }
    if (!window.requestAnimationFrame)
        window.requestAnimationFrame = function(callback, element) {
            var currTime = new Date().getTime();
            var timeToCall = Math.max(0, 16 - (currTime - lastTime));
            var id = window.setTimeout(function() {
                callback(currTime + timeToCall);
            }, timeToCall);
            lastTime = currTime + timeToCall;
            return id;
        };

    if (!window.cancelAnimationFrame)
        window.cancelAnimationFrame = function(id) {
            clearTimeout(id);
        };
}());
function updateStatus(message){
    var status = $("#status");
    status.text(message);
}
function serverResponse(){}
var noActiveBackstage = "none";
var WorkQueue = (function(){
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
        }
    }
    return {
        isAbleToWork:true,
        canWork:function(state){
            WorkQueue.isAbleToWork = state;
            if(state){
                console.log(sprintf("Enacting %s queued funcs",work.length));
                popState();
            }
        },
        enqueue:function(func){//A function returning a bool, blit needed or not.
            if(WorkQueue.isAbleToWork){
                if(func()){
                    blit();
                };
            }
            else{
                work.push(func);
            }
        }
    };
})();
var Pan = {
    shift:function(xDelta,yDelta){
        var xScale = viewboxWidth / boardWidth;
        var yScale = viewboxHeight / boardHeight;
        viewboxX -= xDelta * xScale;
        viewboxY -= yDelta * yScale;
        blit();
    }
}
var Zoom = (function(){
    var zoomFactor = 1.2;
    var maxZoomOut = 1.5;
    var maxZoomIn = 3.0;
    return {
        shift:function(scale,ignoreLimits){
            if(!ignoreLimits){
                if(viewboxWidth * scale > boardContent.width * maxZoomOut){
                    return;
                }
                else if(viewboxWidth / scale < boardWidth / maxZoomIn){
                    return;
                }
            }
            var ow = viewboxWidth;
            var oh = viewboxHeight;
            viewboxWidth *= scale;
            viewboxHeight *= scale;
            var xDelta = (ow - viewboxWidth) / 2;
            var yDelta = (oh - viewboxHeight) / 2;
            viewboxX += xDelta;
            viewboxY += yDelta;
            requestedViewboxWidth = viewboxWidth;
            requestedViewboxHeight = viewboxHeight;
            blit();
        },
        out:function(){
            Zoom.shift(zoomFactor);
        },
        "in":function(){
            Zoom.shift(1 / zoomFactor);
        }
    };
})();
var Extend = (function(){
    var factor = 0.6;
    var xExtension = function(){
        return Math.floor(viewboxWidth * factor);
    }
    var yExtension = function(){
        return Math.floor(viewboxHeight * factor);
    }
    var tween;
    var animateViewbox = function(xDelta,yDelta,onComplete){
        var interval = 300;//milis
        var startX = viewboxX;
        var startY = viewboxY;
        if(tween){
            tween.stop();
            tween = false;
        }
        tween = new TWEEN.Tween({x:0,y:0})
            .to({x:xDelta,y:yDelta}, interval)
            .easing(TWEEN.Easing.Quadratic.Out)
            .onUpdate(function(){
                viewboxX = startX + this.x;
                viewboxY = startY + this.y;
            }).onComplete(function(){
                tween = false;
                if(onComplete){
                    onComplete();
                }
                reportViewboxMoved();
            }).start();
        var update = function(t){
            if(tween){
                TWEEN.update();
                clearBoard();
                render(boardContent);//Don't go through the work queue.  We're not drawing, we're swiping.
                requestAnimationFrame(update);
            }
        }
        requestAnimationFrame(update);
    }
    return {
        up:function(){
            animateViewbox(0,-yExtension());
        },
        down:function(){
            animateViewbox(0,yExtension());
        },
        left:function(){
            animateViewbox(-xExtension(),0);
        },
        right:function(){
            animateViewbox(xExtension(),0);
        },
        shift:animateViewbox,
        center:function(x,y,onComplete){
            var targetX = x - viewboxWidth / 2;
            var targetY = y - viewboxHeight / 2;
            animateViewbox(targetX - viewboxX,targetY - viewboxY,onComplete);
        }
    }
})();
function showBackstage(id){
    console.log("showBackstage",id);
    if(id in Progress){
        Progress.call(id);
    }
    $(".backstage").hide();
    window.currentBackstage = id;
    var popup = $("#"+id+"Popup");
    popup.show();
    /*
     $(".backstageTabHeader").removeClass("activeBackstageTab");
     $(".backstage").removeClass("activeBackstageTab");
     $("#"+id).addClass("activeBackstageTab");
     $("#backstageContainer").show();
     $("#applicationMenuPopup").show();
     $("#backstageTabHeaders").show();
     $("#applicationMenuButton").addClass("activeBackstageTab");
     $("#hideBackstage").show();
     */
}
function hideBackstage(){
    window.currentBackstage = noActiveBackstage;
    Progress.call("hideBackstage");
    $(".backstage").hide();
    /*
     $(".backstageTabHeader").removeClass("activeBackstageTab");
     $(".backstage").removeClass("activeBackstageTab");
     $("#applicationMenuButton").removeClass("activeBackstageTab");
     $("#applicationMenuPopup").hide();
     $("#backstageTabHeaders").hide();
     $("#backstageContainer").hide();
     $("#hideBackstage").hide();
     */
}
$(function(){
    var heading = $("#heading");
    heading.text("Loading MeTLX...");
    var p = progress().max(8);
    p.element.prependTo($("#progress"));
    var setLoadProgress = function(value){
        p.value(value);
    };
    setLoadProgress(0);
    heading.text(sprintf("Logged in as %s",username));
    setupStatus();
    board = $("#board");
    boardContext = board[0].getContext("2d");
    heading.text("Set up board");
    setLoadProgress(1);
    $("input.toolbar").addClass("commandModeInactive").addClass(commandMode ? "commandModeActive" : "commandModeInactive");
    $("#slideContainer button").addClass("commandModeInactive").addClass(commandMode ? "commandModeActive" : "commandModeInactive");
    $("#up").click(Extend.up);
    $("#down").click(Extend.down);
    $("#left").click(Extend.left);
    $("#right").click(Extend.right);
    $("#in").click(Zoom.in);
    $("#out").click(Zoom.out);
    $("#drawMode").click(function(){
        if(Modes.currentMode != Modes.draw){
            Modes.draw.activate();
        }
    });
    $("#selectMode").click(function(){
        if(Modes.currentMode != Modes.select){
            Modes.select.activate();
        }
    });
    $("#insertMode").click(function(){
        if(Modes.currentMode != Modes.insert){
            Modes.insert.activate();
        }
    });
    $("#panMode").click(function(){
        if(Modes.currentMode != Modes.pan){
            Modes.pan.activate();
        }
    });
    $("#zoomMode").click(function(){
        if(Modes.currentMode != Modes.zoom){
            Modes.zoom.activate();
        }
    });
    $("#feedbackMode").click(function(){
        if(Modes.currentMode != Modes.feedback){
            Modes.feedback.activate();
        }
    });
    $("#tagMode").click(function(){
        if(Modes.currentMode != Modes.tag){
            Modes.tag.activate();
        }
    });
    $("#implicitlyExpanding").click(function(){
        implicitlyExpanding = $(this).is(":checked");
    });
    if(implicitlyExpanding){
        $("#implicitlyExpanding").attr("checked",implicitlyExpanding);
    }
    $("#showGrid").click(function(){
        showGrid = $(this).is(":checked");
        blit();
    });
    if(showGrid){
        $("#showGrid").attr("checked",showGrid);
    }
    setLoadProgress(2);
    $("#zoomToFull").click(zoomToFit);
    $("#zoomToPage").click(zoomToPage);
    window.currentBackstage = noActiveBackstage;
    $("#hideBackstage").click(function(){
        hideBackstage();
    });
    $("#applicationMenuButton").click(function(){
        console.log("applicationMenu click");
        if(window.currentBackstage == "applicationMenu"){
            hideBackstage();
        }
        else{
            showBackstage("applicationMenu");
        }
    });
    loadSlidesAtNativeZoom = getUserPref("loadSlidesAtNativeZoom") == "true";
    var zoom = $("#loadSlidesAtNativeZoom");
    if(getUserPref("loadSlidesAtNativeZoom") == "true"){
        zoom.attr("checked",true);
    }
    zoom.click(function(){
        loadSlidesAtNativeZoom = $(this).is(":checked");
        setUserPref("loadSlidesAtNativeZoom",loadSlidesAtNativeZoom);
        receiveHistory(boardContent);
    });
    var sizeChooser = function(pref,values){
        var container = $("<div />");
        $("<div />",{
            text:sprintf("Choose your preferred %s",pref)
        }).appendTo(container);
        var choice = function(size){
            return $("<div />")
                .text(sprintf("%s px",size))
                .addClass("preferenceChoice")
                .css({
                    width:px(size),
                    height:px(size)
                })
                .click(function(){
                    console.log("Setting user pref",pref,size);
                    setUserPref(pref,size);
                    fit();
                    render(boardContent);
                })
                .appendTo(container);
        };
        values.map(choice);
        return container;
    }
    $("#preferencesContainerRight")
        .append(sizeChooser("toolModeSize",[30,50,90]))
        .append(sizeChooser("subModeSize",[30,60,100]))
        .append(sizeChooser("thumbnailSize",[60,100,200]))
    $("#preferences")
        .click(function(){
            showBackstage("preferences");
            var inkToggle = $("#toggleHighQualityInk");
            $("#toggleHighQualityInk").attr("disabled","disabled");
        });
    var expansionAmount = 200;
    $("input[name=renderQuality]").change(function(){
        var v = $("input[name=renderQuality]:checked").val();
        pressureSimilarityThreshold = parseInt(v);
        blit();
    });
    setLoadProgress(3);
    $.each({
        red:"#FF0000",
        green:"#00FF00",
        blue:"#0000FF"
    },function(id,code){
        $("#"+id).click(function(){
            Modes.draw.drawingAttributes.color = code;
        });
    });
    $.each({
        thin:0.3,
        medium:1,
        fat:3,
        xfat:30
    },function(id,width){
        $("#"+id).attr("title",width).click(function(){
            Modes.draw.drawingAttributes.width = width;
        });
    });
    $("#toggleHighlighter").click(function(){
        Modes.draw.drawingAttributes.isHighlighter = !Modes.draw.drawingAttributes.isHighlighter;
    });
    setLoadProgress(4);
    if(window.event){
        currentSearchTerm = this.value;
        if (window.event.keyCode == 13){
            getSearchResult(currentSearchTerm);
        }
    }
    setLoadProgress(5);
    $("#openQuizzes").click(function(){
        $("#quizzingPopup").toggle();
    });
    setLoadProgress(7);
    var resetView = function(){
        window.scrollTo(0,0);
        requestedViewboxWidth = Math.max(requestedViewboxWidth,boardWidth);
        requestedViewboxHeight = Math.max(requestedViewboxHeight,boardHeight);
        fit();
        render(boardContent);
    };
    var w = $(window);
    if(window.orientation){
        w.on("orientationchange",resetView);
    }
    else{
        w.smartresize(resetView,200);
    }
    Progress.stanzaReceived["boardOnLoad"] = actOnReceivedStanza;
    Progress.viewboxMoved["boardOnLoad"] = function(){
        if(flagged){
            sendTransientStanza({
                command:"viewboxMoved",
                type:"command",
                author:username,
                timestamp:Date.now(),
                parameters:[viewboxX.toString(),viewboxY.toString(),viewboxWidth.toString(),viewboxHeight.toString()]})
        }
    };
    Modes.draw.activate();
    setLoadProgress(8);
    console.log("Setup complete");
});
