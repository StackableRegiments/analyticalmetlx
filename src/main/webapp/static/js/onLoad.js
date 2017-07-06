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
var reapplyStylingToServerGeneratedContent = function(contentId){
    $("#" + contentId).find('.simpleMultipleButtonInteractableMessageButton a').addClass('button-transparent-border button');
};
var bounceAnd = function(func){
    return function(e){
        //bounceButton(this);
        func(e);
    }
};
function updateStatus(message){
    var status = $("#status");
    status.text(message);
}
var noActiveBackstage = "none";
var flash = function(el){
    var t = 100;
    el.fadeOut(t).fadeIn(t);
}
function updateActiveMenu(menuItem) {
    $(".activeBackstageTab").removeClass("activeBackstageTab active");
    $(menuItem).addClass("activeBackstageTab active");
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
        }
    };
    var pauseFunction = function(){
        stopResume();
        canWorkFunction(false);
        Progress.call("afterWorkQueuePause");
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
        Progress.call("beforeWorkQueueResume");
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
        takeControlOfViewbox(true);
	    var s = scale();
        TweenController.panViewboxRelative(xDelta / s, yDelta / s);
    },
        translate:function(xDelta,yDelta){
        takeControlOfViewbox(true);
	    var s = scale();
        TweenController.translateViewboxRelative(xDelta / s, yDelta / s);
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
            takeControlOfViewbox(true);
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
            takeControlOfViewbox(true);
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
    var updateRequestedPosition = function(){
        requestedViewboxX = viewboxX;
        requestedViewboxY = viewboxY;
        requestedViewboxWidth = viewboxWidth;
        requestedViewboxHeight = viewboxHeight;
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
        viewboxX = finalX;
        viewboxY = finalY;
        viewboxWidth = finalWidth;
        viewboxHeight = finalHeight;
        if (!shouldAvoidUpdatingRequestedViewbox){
            updateRequestedPosition();
        }
        if (onComplete){
            onComplete();
        }
        blit();
        teacherViewUpdated(finalX,finalY,finalWidth,finalHeight);
        Progress.call("onViewboxChanged");
    };
    var teacherViewUpdated = _.throttle(function(x,y,w,h){
        if(Conversations.isAuthor() && UserSettings.getIsInteractive()){
            var ps = [x,y,w,h,Date.now(),Conversations.getCurrentSlideJid(),"autoZooming" in Progress.onBoardContentChanged];
            if(w <= 0 || h <= 0){
                return;
            }
            if(_.some(ps,function(p){
                return typeof(p) == "undefined" || isNaN(p);
            })){
                return;
            };
            sendStanza({
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
                viewboxX = finalX;
                viewboxY = finalY;
                viewboxWidth = finalWidth;
                viewboxHeight = finalHeight;
                blit();
                if (!shouldAvoidUpdatingRequestedViewbox){
                    updateRequestedPosition();
                }
                if (onComplete){
                    onComplete();
                }
                Progress.call("onViewboxChanged");
                Progress.call("textBoundsChanged");
            }).start();
        var update = function(t){
            if (tween){
                TWEEN.update();
                blit();
                requestAnimationFrame(update);
            }
        };
        requestAnimationFrame(update);
        if("Conversations" in window && Conversations.isAuthor()){
            if(notFollowable || shouldAvoidUpdatingRequestedViewbox){
                //console.log("not following viewbox update");
            }
            else if (hasChanged()){
                //console.log("sending viewbox update");
                teacherViewUpdated(finalX,finalY,finalWidth,finalHeight);
            }
        }
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

var subcategoryMapping = {
    metaToolbar:".metaConversationGroup",
    roomToolbar:".inConversationGroup",
    optsToolbar:".applicationGroup"
};
var categoryMapping = _.fromPairs(_.flatMap({
    metaToolbar:"integrations print recycleBin help",
    optsToolbar:"settings healthCheck",
    roomToolbar:"grades blacklist submissions attachments participants groups quizzes contentFilter"
},function(v,k){
    return _.map(v.split(" "),function(backstage){
        return [backstage,k];
    });
}));

var active = "activeBackstageTab active";
function showBackstage(id){
    $("html").css("overflow-y","auto");
    $("#masterFooter").hide();
    window.currentBackstage = id;
    $(".backstage").hide();
    if ("HealthCheckViewer" in window){
        HealthCheckViewer.pause();
    }

    $(".backstageTabHeaderGroup").hide();
    $(".backstageTabHeader").removeClass(active);
    $(".backstageCategory").removeClass("active");
    $(".backstageCategory").removeClass(active);
    $(".modeSpecificTool").removeClass(active);
    $(".backstage").removeClass(active);

    var popup = $("#"+id+"Popup");
    var popupParent = categoryMapping[id];

    $(subcategoryMapping[popupParent]).show();
    $("#backstageContainer").css("overflow-y","scroll").show();
    $("#hideBackstage").show();
    popup.show();

    $("#applicationMenuPopup").addClass('active');
    $("#applicationMenuButton").addClass(active);
    $(".modeSpecificTool."+id).addClass(active);
    $("#"+popupParent).addClass("active");
    $(".backstage-menu").addClass('active');
    $("#"+id).addClass(active);

    if(Conversations.inConversation()){
        $("#backstageTabHeaders").show();
        $("#applicationMenuButton").show();
        $("#roomToolbar").show();
    }
    else{
        $("#backstageTabHeaders").hide();
        $("#applicationMenuButton").hide();
        $("#roomToolbar").hide();
    }
    $(".dedicatedClose").click(hideBackstage);
    $("#masterLayout").css({"opacity": Conversations.getCurrentConversationJid() ? 0.3 : 0.0 });
    Progress.call("onBackstageShow",[id]);
}
function hideBackstage(){
    $("html").css("overflow-y","hidden");
    $("#masterFooter").show();
    window.currentBackstage = noActiveBackstage;
    $(".backstage-menu").removeClass('active');
    $(".backstage").hide();
    $(".backstageTabHeader").removeClass(active);
    $(".backstage").removeClass(active);
    $("#applicationMenuButton").removeClass(active);
    $("#applicationMenuPopup").removeClass('active');
    $("#backstageTabHeaders").hide();
    $("#backstageContainer").hide();
    $("#hideBackstage").hide();
    $("#notices").show();
    $(".modeSpecificTool").removeClass(active);
    hideSpinner();
    $("#masterLayout").css({"opacity":1.0});
    if ("HealthCheckViewer" in window){
        HealthCheckViewer.pause();
    }
    Progress.call("onBackstageHide");
};
function showSpinner() {
    $("#loadingSlidePopup").show();
};
function hideSpinner() {
    $("#loadingSlidePopup").hide();
}
function toggleSubOptions(selector){
    return function(){
        $(selector).find(".backstageTabHeader").eq(0).click();
    };
}
$(function(){
    var heading = $("#heading");
    var p = progress().max(8);
    p.element.prependTo($("#progress"));
    var setLoadProgress = function(value){
        p.value(value);
    };
    setLoadProgress(0);
    heading.text(sprintf("Logged in as %s",UserSettings.getUsername()));
    setupStatus();
    board = $("#board");
    boardContext = board[0].getContext("2d");
    heading.text("Set up board");
    setLoadProgress(1);
    $("input.toolbar").addClass("commandModeInactive").addClass(commandMode ? "commandModeActive" : "commandModeInactive");
    $("#slideContainer button").addClass("commandModeInactive").addClass(commandMode ? "commandModeActive" : "commandModeInactive");
    $("#up").click(bounceAnd(Extend.up));
    $("#down").click(bounceAnd(Extend.down));
    $("#left").click(bounceAnd(Extend.left));
    $("#right").click(bounceAnd(Extend.right));
    $("#in").click(bounceAnd(function(){
        Zoom.in();
    }));
    $("#out").click(bounceAnd(function(){
        Zoom.out();
    }));
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
    $("#insertText").click(function(){
        if(Modes.currentMode != Modes.text){
            Modes.text.activate();
        }
    });
    $("#panMode").click(function(){
        if(Modes.currentMode != Modes.pan){
            Modes.pan.activate();
        }
    });
    $("#insertMode").click(function(){
        if(Modes.currentMode != Modes.image){
            Modes.currentMode.deactivate();
            Modes.image.activate();
        }
    });
    $("#imageMode").click(function(){
        if(Modes.currentMode != Modes.image){
            Modes.image.activate();
        }
    });
    $("#videoMode").click(function(){
        if(Modes.currentMode != Modes.video){
            Modes.video.activate();
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
    $("#zoomToFull").click(bounceAnd(function(){ zoomToFit(); }));
    $("#zoomToPage").click(bounceAnd(function(){ zoomToPage(); }));
    $("#zoomToOriginal").click(bounceAnd(function(){ zoomToOriginal(); }));
    $("#zoomToCurrent").click(bounceAnd(function(){ takeControlOfViewbox(true); }));
    window.currentBackstage = noActiveBackstage;
    $("#hideBackstage").click(bounceAnd(hideBackstage));
    $("#applicationMenuButton").click(function(){
        if(window.currentBackstage != noActiveBackstage){
            hideBackstage();
        }
        else{
            showBackstage("integrations");
            updateActiveMenu($("#menuIntegrations"));
        }
    });
    loadSlidesAtNativeZoom = UserSettings.getUserPref("loadSlidesAtNativeZoom") == "true";
    var zoom = $("#loadSlidesAtNativeZoom");
    if(loadSlidesAtNativeZoom){
        zoom.attr("checked",true);
    }
    zoom.click(bounceAnd(function(){
        loadSlidesAtNativeZoom = $(this).is(":checked");
        UserSettings.setUserPref("loadSlidesAtNativeZoom",loadSlidesAtNativeZoom);
        receiveHistory(boardContent);
    }));
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
                .click(bounceAnd(function(){
                    // console.log("Setting user pref",pref,size);
                    UserSettings.setUserPref(pref,size);
                    var mode = Modes.currentMode;
                    Modes.none.activate();
                    mode.activate();
                    Progress.call("onLayoutUpdated");
                }))
                .appendTo(container);
        };
        values.map(choice);
        return container;
    }
    $("#preferencesContainerRight")
        .append(sizeChooser("subModeSize",[30,60]))
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
    $("#submissionsButton").on("click",function(){
        showBackstage("submissions");
        Submissions.reRender();
        updateActiveMenu($("#menuSubmissions"));
    });
    $("#gradesButton").on("click",function(){
        showBackstage("grades");
        Grades.reRender();
        updateActiveMenu($("#menuGrades"));
    });

    $("#quizzesButton").on("click",function(){
        showBackstage("quizzes");
        Quizzes.reRender();
        updateActiveMenu($("#menuPolls"));
    });
    $("#submitScreenshotButton").on("click",function(){
        if ("Submissions" in window){
            Submissions.sendSubmission();
        }
    });
    if ("Conversations" in window){
        $("#enableSync").on("click",Conversations.enableSyncMove);
        $("#disableSync").on("click",Conversations.disableSyncMove);
    }
    _.each(subcategoryMapping,function(v,k){
        $("#"+k).click(toggleSubOptions(v));
    });

    setLoadProgress(7);
    Progress.stanzaReceived["boardOnLoad"] = actOnReceivedStanza;

    Modes.select.activate();

    $("#progress").hide();
    setLoadProgress(8);

    $('#updatePens').click(function(){
        showBackstage("customizeBrush");
        updateActiveMenu(this);
    });
    var printPrivate = true;
    var includeTitle = true;
    var includePageCount = true;
    $('#menuPrint').click(function(){
        showBackstage("print");
        updateActiveMenu(this);
        var conversationJid = Conversations.getCurrentConversationJid();
        var rangeAllRadio = $("#rangeAll");
        var rangeSpecifiedRadio = $("#rangeSpecified");
        var rangeThisSlideRadio = $("#rangeThisSlide");
        var rangeSpecifiedInput = $("#rangeSpecifiedInput");
        var printButton = $("#printButton");
        var showPrivateCheckbox = $("#printPrivateNotes");
        var includePageCountCheckbox = $("#printPageCount");
        var includeTitleCheckbox = $("#printConversationTitle");
        var pageRange = Conversations.getCurrentSlide().index + 1;
        var uncheckAll = function(){
            _.forEach([rangeSpecifiedRadio,rangeAllRadio,rangeThisSlideRadio],function(item){
                item.prop("checked",false);
            });
            rangeSpecifiedInput.prop("disabled",true);
        };
        showPrivateCheckbox.prop("checked",printPrivate);
        includeTitleCheckbox.prop("checked",includeTitle);
        includePageCountCheckbox.prop("checked",includePageCount);
        uncheckAll();
        rangeThisSlideRadio.prop("checked",true);
        rangeSpecifiedInput.val(pageRange);
        var updatePrintState = function(){
            printButton.attr("target","blank").attr("href",sprintf("clientSidePrintConversation?conversationJid=%s&pageRange=%s&includePrivateContent=%s&includeConversationTitle=%s&includePageCount=%s",conversationJid,pageRange,printPrivate,includeTitle,includePageCount));
        };
        updatePrintState();
        rangeAllRadio.unbind("click");
        rangeAllRadio.on("click",function(){
            uncheckAll();
            $(this).prop("checked",true);
            pageRange = "all";
            updatePrintState();
        });
        rangeSpecifiedRadio.unbind("click");
        rangeSpecifiedRadio.on("click",function(){
            uncheckAll();
            $(this).prop("checked",true);
            rangeSpecifiedInput.prop("disabled",false);
            pageRange = rangeSpecifiedInput.val();
            updatePrintState();
        });
        rangeSpecifiedInput.unbind("change");
        rangeSpecifiedInput.on("change",function(){
            var text = $(this).val();
            pageRange = text;
            updatePrintState();
        });
        rangeThisSlideRadio.unbind("click");
        rangeThisSlideRadio.on("click",function(){
            uncheckAll();
            $(this).prop("checked",true);
            pageRange = Conversations.getCurrentSlide().index + 1;
            updatePrintState();
        });
        showPrivateCheckbox.unbind("change");
        showPrivateCheckbox.on("change",function(){
            printPrivate = $(this).prop("checked");
            updatePrintState();
        });
        includeTitleCheckbox.unbind("change");
        includeTitleCheckbox.on("change",function(){
            includeTitle = $(this).prop("checked");
            updatePrintState();
        });
        includePageCountCheckbox.unbind("change");
        includePageCountCheckbox.on("change",function(){
            includePageCount = $(this).prop("checked");
            updatePrintState();
        });
    });
    $('#menuGroups').click(function(){
        showBackstage("groups");
        updateActiveMenu(this);
    });
    $('#menuGrades').click(function(){
        showBackstage("grades");
        updateActiveMenu(this);
        Grades.reRender();
    });
    $('#menuSubmissions').click(function(){
        showBackstage("submissions");
        updateActiveMenu(this);
        Submissions.reRender();
    });
    $('#menuPolls').click(function(){
        showBackstage("quizzes");
        updateActiveMenu(this);
        Quizzes.reRender();
    });
    $('#menuBlacklist').click(function(){
        showBackstage("blacklist");
        updateActiveMenu(this);
        Blacklist.reRender();
    });
    $('#menuSettings').click(function(){
        showBackstage("settings");
        updateActiveMenu(this);
    });
    $('#menuIntegrations').click(function(){
        showBackstage("integrations");
        updateActiveMenu(this);
    });
    $('#menuHelp').click(function(){
        showBackstage("help");
        updateActiveMenu(this);
    });
    $("#menuHealthCheck").click(function(){
        showBackstage("healthCheck");
        updateActiveMenu(this);
        if ("HealthCheckViewer" in window){
            HealthCheckViewer.resume();
        }
    });
    $("#conversations").click(function(){
        window.location.href = "/conversationSearch";
    });
    var pasteDialogTemplate = $("#pasteDialogTemplate").clone();
    $("#pasteDialogTemplate").remove();
    var func = function(ev){
        //console.log("ev",ev,ev.clipboardData,ev.clipboardData.files[0],ev.clipboardData.items[0]);
        var df = ("dataTransfer" in ev) ? ev.dataTransfer : ev.clipboardData;
        if ("types" in df){
            var x = ev.offsetX || 10;
            var y = ev.offsetY || 10;
            var availableTypes = _.filter(df.types,function(t){
                return t.toLowerCase().startsWith("text") || t.toLowerCase().startsWith("image") || t.toLowerCase().startsWith("file");
            });
            var items = _.toArray(df.items);
            var files = _.toArray(df.files);
            var newDf = {
                items:items,
                files:files,
                types:availableTypes
            };
            // console.log("newDf",newDf);
            var dataSets = _.map(availableTypes,function(type){
                return {
                    key:type,
                    value:df.getData(type)
                };
            });
            var conditionallyActOn = function(coll,itemPred,action){
                var elem = _.find(coll,itemPred);
                if (elem != undefined && elem != null){
                    action(elem,df.getData(elem));
                };
            };
            console.log("Data transfer - availableTypes:",availableTypes,items,files);
            if (_.size(availableTypes) > 1){
                var rootId = sprintf("pasteEventHandler_%s",_.uniqueId());
                var rootElem = pasteDialogTemplate.clone().attr("id",rootId);
                var optionContainer = rootElem.find(".dialogOptions");
                var optionTemplate = optionContainer.find(".dialogOption").clone();
                optionContainer.empty();
                var modal = $.jAlert({
                    title:"Data to paste",
                    content:rootElem[0].outerHTML,
                    closeOnClick:true,
                    closeOnEsc:true,
                    blurBackground:true
                });
                var acceptedTypes = [
                    {
                        key:function(t){return t == "text/html";},
                        name:"rich text and images",
                        faClass:"fa-file-code-o",
                        onClick:function(type){
                            var html = _.find(dataSets,function(ds){
                                return ds.key == type;
                            }).value;
                            var htmlElem = $(html);
                            var yOffset = 0;
                            html = _.join(_.map(htmlElem,function(he){return he.outerHTML;}),"");
                            _.forEach(htmlElem.find("img"),function(imgNode){
                                try {
                                    Modes.image.handleDroppedSrc(imgNode.src,x,y + yOffset);
                                    yOffset += Math.max(imgNode.height,50);
                                } catch (e){
                                    errorAlert("Error dropping image","The source server you're dragging the image from does not want to allow dragging the image directly.  You may need to download the image first and then upload it.  " + e);
                                }
                            });
                            if (htmlElem.text().trim().length > 1){
                                Modes.text.handleDrop(html,x,y + yOffset);
                            }
                        }
                    },
                    {
                        key:function(t){return t == "text/plain";},
                        name:"plain text",
                        faClass:"fa-file-text-o",
                        onClick:function(type){
                            var text = _.find(dataSets,function(ds){
                                return ds.key == type;
                            }).value;
                            Modes.text.handleDrop(text,x,y);
                        }
                    },
                    {
                        key:function(t){return t == "Files";},
                        name:"files or images",
                        faClass:"fa-file-o",
                        onClick:function(type){
                            Modes.image.handleDrop(newDf,x,y);
                        }
                    },
                    {
                        key:function(t){return t.indexOf("image/") == 0;},
                        name:"images",
                        faClass:"fa-file-image-o",
                        onClick:function(type){
                            Modes.image.handleDrop(newDf,x,y);
                        }
                    }
                ];
                $("#"+rootId).find(".dialogOptions").html(_.map(_.filter(availableTypes,function(type){
                    return _.some(acceptedTypes,function(acceptableType){
                        return acceptableType.key(type);
                    });
                }),function(type){
                    var knownType = _.find(acceptedTypes,function(acceptableType){
                        return acceptableType.key(type);
                    });
                    var outerElem = optionTemplate.clone();
                    var button = outerElem.find("button");
                    button.addClass(knownType.faClass);
                    button.on("click",function(){
                        knownType.onClick(type);
                        modal.closeAlert();
                    });
                    button.find(".icon-txt").text(knownType.name);
                    return outerElem;
                }));
            } else {
                var handled = false;
                conditionallyActOn(availableTypes,function(label){return label == "Files";},function(type,file){
                    if (!handled){
                        Modes.image.handleDrop(df,x,y);
                        handled = true;
                    }
                });
                conditionallyActOn(availableTypes,function(label){return label.indexOf("image") == 0;},function(type,image){
                    if (!handled){
                        Modes.image.handleDrop(df,x,y);
                        handled = true;
                    }
                });
                conditionallyActOn(availableTypes,function(label){return label == "text/html";},function(type,html){
                    if (!handled){
                        var htmlElem = $(html);
                        var yOffset = 0;
                        html = _.join(_.map(htmlElem,function(he){return he.outerHTML;}),"");
                        _.forEach(htmlElem.find("img"),function(imgNode){
                            try {
                                Modes.image.handleDroppedSrc(imgNode.src,x,y + yOffset);
                                yOffset += Math.max(imgNode.height,50);
                            } catch (e){
                                errorAlert("Error dropping image","The source server you're dragging the image from does not want to allow dragging the image directly.  You may need to download the image first and then upload it.  " + e);
                            }
                        });
                        if (htmlElem.text().trim().length > 1){
                            Modes.text.handleDrop(html,x,y + yOffset);
                        }
                        handled = true;
                    }
                });
                conditionallyActOn(availableTypes,function(label){return label.indexOf("text") == 0;},function(type,html){
                    if (!handled){
                        Modes.text.handleDrop(html,x,y);
                        handled = true;
                    }
                });
                if (!handled){
                    console.log("Data transfer - unknown type",df);
                }
            }
            ev.preventDefault();
            return false;
        } else {
            return true;
        }
    };
    window.addEventListener("paste",function(jEv){
        if ("originalEvent" in jEv){
            return func(jEv.originalEvent);
        } else {
            return func(jEv);
        }
    });
    $("#board").on("drop",function(jEv){
        if ("originalEvent" in jEv){
            return func(jEv.originalEvent);
        } else {
            return false;
        }
    });
    new Clipboard('.deeplink', {
        text: function(trigger) {
            /*The native browser will absolutize all hrefs where JQuery would not.  Don't convert this into $.attr*/
            var t = $(trigger.nextElementSibling).find("a")[0].href;
            return t;
        }
    }).on("success",function(e){
        // console.log(e);
        alert("Link copied to clipboard");
    }).on('error', function(e) {
        console.error('Action:', e.action);
        console.error('Trigger:', e.trigger);
    });
    var deadFunc = function(deadEvent){
        deadEvent.preventDefault();
        return false;
    };
    var fakeFunc = function(deadEvent){
        return true;
    };
    $(document).on("drop",deadFunc);
    window.onbeforepaste = deadFunc;
    _.forEach(["dragover","dragleave","dragenter"],function(label){
        window["on"+label] = deadFunc;
        $(window).on(label,deadFunc);
        $("#board")[0]["on"+label] = deadFunc;
        $("#board").on(label,deadFunc);
    });
});
