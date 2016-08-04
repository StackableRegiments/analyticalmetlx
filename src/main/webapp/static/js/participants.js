var Participants = (function(){
    var participantItemTemplate = {};
    var participantHeaderTemplate = {};
    var participantsContainer = {};
    var participants = {};
    var newParticipant = {
        inks:0,
        highlighters:0,
        texts:{},
        images:0,
        quizResponses:0,
        submissions:0,
        following:true
    };
    var onHistoryReceived = function(history){
        var newParticipants = {};
        var ensure = function(author){
            return newParticipants[author] || _.clone(newParticipant);
        }
        _.each(_.groupBy(history.attendances,"author"),function(authorAttendances){
            var author = authorAttendances[0].author;
            var itemToEdit = ensure(newParticipant);
            itemToEdit.name = author;
            itemToEdit.attendances = authorAttendances;
            newParticipants[author] = itemToEdit;
        });
        _.each(_.groupBy(history.inks,"author"),function(authorStanzas,author){
            var itemToEdit = ensure(author);
            itemToEdit.inks = itemToEdit.inks + _.size(authorStanzas);
            newParticipants[author] = itemToEdit;
        });
        _.each(_.groupBy(history.highlighters,"author"),function(authorStanzas,author){
            var itemToEdit = ensure(author);
            itemToEdit.highlighters = itemToEdit.highlighters + _.size(authorStanzas);
            newParticipants[author] = itemToEdit;
        });
        _.each(_.groupBy(history.images,"author"),function(authorStanzas,author){
            var itemToEdit = ensure(author);
            itemToEdit.images = itemToEdit.images + _.size(authorStanzas);
            newParticipants[author] = itemToEdit;
        });
        _.each(_.groupBy(history.multiWordTexts,"author"),function(authorStanzas,author){
            var itemToEdit = ensure(author);
            _.each(authorStanzas,function(stanza){
                newParticipants[author].texts[stanza.identity] = countTexts(stanza);
            });
        });
        participants = newParticipants;
	console.log(newParticipants);
        updateParticipantsListing();
    };
    var countTexts = function(stanza){
        return _.reduce(stanza.words,function(acc,v,k){
            return acc + v.text.length;
        },0);
    }
    var onStanzaReceived = function(stanza){
        if ("type" in stanza && "author" in stanza){
            var author = stanza.author;
            var itemToEdit = participants[author];
            switch (stanza.type) {
            case "ink":
                itemToEdit.inks = itemToEdit.inks + 1;
                break;
            case "image":
                itemToEdit.images = itemToEdit.images + 1;
                break;
            case "highlighter":
                itemToEdit.highlighters = itemToEdit.highlighters + 1;
                break;
            case "multiWordText":
                itemToEdit.texts[stanza.identity] = countTexts(stanza);
                break;
            case "submission":
                itemToEdit.submissions = itemToEdit.submissions + 1;
                break;
            case "quizResponse":
                itemToEdit.quizResponses = itemToEdit.quizResponses + 1;
                break;

            }
            participants[author] = itemToEdit;
        }
        updateParticipantsListing();
    };
    var updateParticipantsListing = function(){
        try {
            var replacementNodes = _.map(participants,function(participant){
                var p = participantItemTemplate.clone();
                var name = sprintf("participant_%s",participant.name)
                var label = p.find(".followLabel").attr("for",name);
                if(Conversations.shouldModifyConversation()){
                    label.text(participant.name);
                }
                p.find(".followValue").attr("id",name).prop("checked",participant.following).on("change",function(){
                    participant.following = $(this).is(":checked");
                    blit();
                });
                p.find(".attendanceCount").text(_.size(participant.attendances));
                p.find(".inksCount").text(participant.inks);
                p.find(".highlightersCount").text(participant.highlighters);
                p.find(".imagesCount").text(participant.images);
                p.find(".textsCount").text(_.reduce(participant.texts,function(acc,item){return acc + item},0));
                p.find(".quizSubmissionsCount").text(participant.submissions);
                p.find(".quizResponsesCount").text(participant.quizResponses);
                return p;
            });
            participantsContainer.html(replacementNodes);
        } catch(e) {
            console.log("participantRender failed:",e,participants);
        }
    };
    var openParticipantsMenuFunction = function(){
        showBackstage("participants");
        updateActiveMenu(this);
        updateParticipantsListing();
    };
    var updateButtons = function(){
        if (Conversations.shouldModifyConversation()){
            $("#menuParticipants").click(openParticipantsMenuFunction);
            $("#menuParticipants").show();
        } else {
            $("#menuParticipants").unbind("click");
            $("#menuParticipants").hide();
        }
    };
    var onDetailsReceived = function(){
        updateButtons();
    };
    $(function(){
        participantsContainer = $("#participantsListingContainer").find("tbody");
        participantItemTemplate = participantsContainer.find(".participation").clone();
        participantsContainer.empty();
        updateButtons();
    });
    Progress.stanzaReceived["participants"] = onStanzaReceived;
    Progress.historyReceived["participants"] = onHistoryReceived;
    Progress.conversationDetailsReceived["participants"] = onDetailsReceived;
    Progress.newConversationDetailsReceived["participants"] = onDetailsReceived;
    return {
        getParticipants:function(){return Conversations.shouldModifyConversation() ? participants : {};}
    };
})();
