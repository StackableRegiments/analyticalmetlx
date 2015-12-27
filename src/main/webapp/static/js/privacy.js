var Privacy = (function(){
    var privacy = "PUBLIC";
    var setPrivacyIndicators = function(){
        $.each({privateMode:"PRIVATE",publicMode:"PUBLIC"},function(id,p){
            if (p == privacy){
                $("#"+id).addClass("activePrivacy active");
            } else {
                $("#"+id).removeClass("activePrivacy active");
            }
        });
        $("#currentPrivacyStatus").text(privacy == "PUBLIC"?
                                        "publicly" : "privately");
    };
    var attemptToSetPrivacy = function(p){
        if (shouldSetPrivacy(p)){
            privacy = p;
            Progress.call("onPrivacyChanged");
            return true;
        }
        return false;
    };
    var privacyButtons = {privateMode:"PRIVATE",publicMode:"PUBLIC"};
    var changePrivacyButtons = {publicize:"PUBLIC",privatize:"PRIVATE"};
    var shouldSetPrivacy = function(p){
        if (p != "PUBLIC" || (p == "PUBLIC" && Conversations.shouldPublishInConversation())){
            return true;
        } else {
            return false;
        }
    };
    var adjustPrivacyForConversation = function(){
        $("#currentConversationState").text(Conversations.getConversationModeDescriptor());
        $("#currentConversationTitle").text(Conversations.getCurrentConversation().title);
        if (Conversations.shouldPublishInConversation()){
            $("#publicMode").removeClass("disabledButton");
        } else {
            $("#publicMode").addClass("disabledButton");
            attemptToSetPrivacy("PRIVATE");//You must be forced into private mode if the conversation changes mode under you
        }
    };
    var adjustButtonsToIndicateSelection = function(selection){
        $.each(changePrivacyButtons,function(id,p){
            var button = $("#"+id);
            button.addClass("disabledButton");
            if ("images" in selection){
                if (_.any(selection.images,function(item){return item.privacy != p;})){
                    button.removeClass("disabledButton");
                }
            }
            if ("inks" in selection){
                if (_.any(selection.inks,function(item){return item.privacy != p;})){
                    button.removeClass("disabledButton");
                }
            }
            if ("texts" in selection){
                if (_.any(selection.texts,function(item){return item.privacy != p;})){
                    button.removeClass("disabledButton");
                }
            }
        });
    };
    $(function(){
        $.each(privacyButtons,function(id,p){
            $("#"+id).click(function(){
                attemptToSetPrivacy(p);
            });
        });
        $.each(changePrivacyButtons,function(id,p){
            $("#"+id).click(function(){
                if (shouldSetPrivacy(p)){
                    if(Modes.currentMode == Modes.select){
                        var republished = batchTransform();
                        republished.newPrivacy = p;
                        republished.imageIds = _.pluck(Modes.select.selected.images,"identity");
                        republished.textIds = _.pluck(Modes.select.selected.texts,"identity");
                        republished.inkIds = _.pluck(Modes.select.selected.inks,"identity");
                        sendStanza(republished);
                    }
                }
            });
        });
        adjustPrivacyForConversation();
        setPrivacyIndicators();
        adjustButtonsToIndicateSelection({});
    });

    Progress.conversationDetailsReceived["privacy"] = adjustPrivacyForConversation;
    Progress.onPrivacyChanged["privacy"] = setPrivacyIndicators;
    Progress.onConversationJoin["privacy"] = function(){
        adjustPrivacyForConversation();
        attemptToSetPrivacy("PUBLIC");
    }
    Progress.onSelectionChanged["privacy"] = adjustButtonsToIndicateSelection;
    return {
        getCurrentPrivacy:function(){return privacy;},
        setPrivacy:function(newPrivacy){
            attemptToSetPrivacy(newPrivacy);
            return privacy;
        }
    };
})();
