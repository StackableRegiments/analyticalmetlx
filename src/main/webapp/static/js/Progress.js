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
        onConversationJoin:{},
        onSelectionChanged:{},
        onBoardContentChanged:{},
        onViewboxChanged:{},
        onLayoutUpdated:{},
<<<<<<< HEAD
				textBoundsChanged:{},
=======
        textBoundsChanged:{},
>>>>>>> 92e00c129dd517db6860a0e328b53b4892d19ac6
        postRender:{},
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
<<<<<<< HEAD
				afterWorkQueuePause:{}, //these two are sensitive - don't put anything into these which itself would pause the workqueue, or you'll get deadlocks.
				beforeWorkQueueResume:{}
=======
        onCanvasContentDeleted:{}
>>>>>>> 92e00c129dd517db6860a0e328b53b4892d19ac6
    }
})();
