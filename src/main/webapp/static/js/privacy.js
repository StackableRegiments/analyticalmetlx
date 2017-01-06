var Privacy = (function(){
    var privacy = "PUBLIC";
    var setPrivacyIndicators = function(){
        $.each(privacyButtons,function(id,p){
            if (p == privacy){
                $("#"+id).addClass("activePrivacy active");
            } else {
                $("#"+id).removeClass("activePrivacy active");
            }
        });
	var banned = Conversations.getIsBanned();
	$("#publicize").toggleClass("disabled",banned);
        $("#currentlyBanned").text(banned == true ? "(banned because of inappropriate content)" : "");
        $("#currentPrivacyStatus").text(privacy == "PUBLIC"? "publicly" : "privately");
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
        setPrivacyIndicators();
    };
    var adjustButtonsToIndicateSelection = function(selection){
        if(selection){
            $.each(changePrivacyButtons,function(id,p){
                var button = $("#"+id);
                button.addClass("disabledButton");
                if ("images" in selection){
                    if (_.some(selection.images,function(item){return item.privacy != p;})){
                        button.removeClass("disabledButton");
                    }
                }
                if ("inks" in selection){
                    if (_.some(selection.inks,function(item){return item.privacy != p;})){
                        button.removeClass("disabledButton");
                    }
                }
                if ("multiWordTexts" in selection){
                    if (_.some(selection.multiWordTexts,function(item){return item.privacy != p;})){
                        button.removeClass("disabledButton");
                    }
                }
                if ("texts" in selection){
                    if (_.some(selection.texts,function(item){return item.privacy != p;})){
                        button.removeClass("disabledButton");
                    }
                }
            });
        }
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
                        republished.imageIds = _.map(Modes.select.selected.images,"identity");
                        republished.textIds = _.map(Modes.select.selected.texts,"identity");
                        republished.multiWordTextIds = _.map(Modes.select.selected.multiWordTexts,"identity");
                        republished.inkIds = _.map(Modes.select.selected.inks,"identity");
                        if (_.size(republished.imageIds) > 0 || _.size(republished.textIds) > 0 || _.size(republished.inkIds) > 0 || _.size(republished.multiWordTextIds) > 0){
                            sendStanza(republished);
                        }
                    }
                }
            }).addClass("disabledButton");
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
        setPrivacyIndicators();
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
