var Progress = (function(){
    return {
        call:function(key,args){
            args = args || [];
            $.each(Progress[key],function(k,f){
                try{
                    f.apply(f,args);
                }
                catch(e){
                    console.log("Progress.call exception",key,k,e);
                }
            });
        },
	configurationChanged:{},
	tagGroupChanged:{},
	hideBackstage:{},
        viewboxMoved:{},//Ideally, don't use this in between frames of an animation.  Wait till it's done.
        onPrivacyChanged:{},
        onConversationJoin:{},
        onSelectionChanged:{},
        onBoardContentChanged:{},
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
        userOptionsReceivd:{},
        searchResultsReceived:{},
        conversationsPopup:{},
	clumpReceived:{}
    }
})();
