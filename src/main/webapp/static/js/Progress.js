var Progress = (function(){
    return {
				manifest:function(){
						var funcs = _.map(Progress,function(v,k){
					return [k,_.keys(v).length];
						});
						_.each(_.sortBy(funcs,"1").reverse(),function(func){
					console.log(func);
						});
				},
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
				blit:{},
        onBackstageShow:{},
        onBackstageHide:{},
        onPrivacyChanged:{},
        beforeLeavingSlide:{},
        beforeChangingAudience:{},
        afterJoiningSlide:{},
        onConversationJoin:{},
        onSelectionChanged:{},
        isolated:{},
        deisolated:{},
        onBoardContentChanged:{},
        onViewboxChanged:{},
        onLayoutUpdated:{},
        textBoundsChanged:{},
        postRender:{},
        attendanceReceived:{},
        historyReceived:{},
        stanzaReceived:{},
        themeReceived:{},
        currentConversationJidReceived:{},
        currentSlideJidReceived:{},
        conversationDetailsReceived:{},
        newConversationDetailsReceived:{},
        conversationsReceived:{},
        syncMoveReceived:{},
        userGroupsReceived:{},
        groupProvidersReceived:{},
        orgUnitsReceived:{},
        groupSetsReceived:{},
        groupsReceived:{},
        gradeValueReceived:{},
        usernameReceived:{},
        userOptionsReceived:{},
        userBanned:{},
        userUnbanned:{},
        afterWorkQueuePause:{}, //these two are sensitive - don't put anything into these which itself would pause the workqueue, or you'll get deadlocks.
        beforeWorkQueueResume:{},
        onCanvasContentDeleted:{}
    }
})();

var WorkQueue = (function(){
    var isAbleToWork = true;
    var work = [];
		var afterFuncs = [];
    var blitNeeded = false;
    var popState = function(){
        var f = work.pop();
        if(f){
            blitNeeded = blitNeeded || f();
						Progress.call("blit")
            popState();
        }
        else{
            if(blitNeeded){
								Progress.call("blit")
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
		var attachAfterActionFunc = function(f){
			afterFuncs.push(f);
		};
    return {
        pause:pauseFunction,
        gracefullyResume:gracefullyResumeFunction,
        enqueue:function(func){//A function returning a bool, blit needed or not.
            if(isAbleToWork){
                if(func()){
									Progress.call("blit")
                };
            }
            else{
                work.push(function(){
                    return func();
                });
            }
        },
				attachAfterAction:attachAfterActionFunc
    };
})();
