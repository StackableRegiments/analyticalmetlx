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
        beforeLeavingSlide:{},
        onConversationJoin:{},
        onSelectionChanged:{},
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
        usernameReceived:{},
        userOptionsReceived:{},
        afterWorkQueuePause:{}, //these two are sensitive - don't put anything into these which itself would pause the workqueue, or you'll get deadlocks.
        beforeWorkQueueResume:{},
        onCanvasContentDeleted:{}
    }
})();
