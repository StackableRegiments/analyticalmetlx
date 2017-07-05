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
