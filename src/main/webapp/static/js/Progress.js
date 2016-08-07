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
        totalSelectionChanged:{},
	textBoundsChanged:{},
        postRender:{},
        historyReceived:{},
        stanzaReceived:{},
        currentConversationJidReceived:{},
        currentSlideJidReceived:{},
        conversationDetailsReceived:{},
        newConversationDetailsReceived:{},
        conversationsReceived:{},
        syncMoveReceived:{},
        userGroupsReceived:{},
        usernameReceived:{},
        userOptionsReceived:{}
    }
})();
