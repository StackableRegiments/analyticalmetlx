var Participants = (function(){
	/*
    var participantItemTemplate = {};
    var participantHeaderTemplate = {};
    var participantsContainer = {};
		*/
		var participantsDatagrid = {};
		var participantFollowControl = {};
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
		var reRenderParticipants = function(){
			updateParticipantsListing();
		};
    var onHistoryReceived = function(history){
        var newParticipants = {};
        var ensure = function(author){
            return newParticipants[author] || _.cloneDeep(newParticipant);
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
								var total = countTexts(stanza);
                newParticipants[author].texts[stanza.identity] = total;
            });
        });
        _.each(_.groupBy(history.quizResponses,"author"),function(authorStanzas,author){
            var itemToEdit = ensure(author);
            itemToEdit.quizResponses = itemToEdit.quizResponses + _.size(authorStanzas);
            newParticipants[author] = itemToEdit;
        });

        participants = newParticipants;
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
            if(!(author in participants)){
                var np = _.clone(newParticipant);
                np.name = author;
                participants[author] = np;
            }
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
				participantsDatagrid.jsGrid("loadData");
				var sortObj = participantsDatagrid.jsGrid("getSorting");
				if ("field" in sortObj){
						participantsDatagrid.jsGrid("sort",sortObj);
				}
				/*
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
				*/
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
        updateButtons();
				participantsDatagrid = $("#participantsDatagrid");
				participantFollowControl = participantsDatagrid.find(".followControls").clone();
				participantsDatagrid.empty();
        var DateField = function(config){
            jsGrid.Field.call(this,config);
        };
        DateField.prototype = new jsGrid.Field({
            sorter: function(a,b){
                return new Date(a) - new Date(b);
            },
            itemTemplate: function(i){
                return new Date(i).toLocaleString();
            },
            insertTemplate: function(i){return ""},
            editTemplate: function(i){return ""},
            insertValue: function(){return ""},
            editValue: function(){return ""}
        });
        jsGrid.fields.dateField = DateField;

				var gridFields = [
					{
						name:"name",
						type:"text",
						title:"Follow",
						readOnly:true,
						sorting:true,
						itemTemplate:function(username,participant){
							var rootElem = participantFollowControl.clone();
							var elemId = sprintf("participant_%s",participant.name);
							rootElem.find(".followValue").attr("id",elemId).prop("checked",participant.following).on("change",function(){
								participants[participant.name].following = $(this).is(":checked");
								blit();
								updateParticipantsListing();
							});
							rootElem.find(".followLabel").attr("for",elemId).text(participant.name);
							return rootElem;
						}	
					},
					{name:"attendances",type:"number",title:"Attendances",readOnly:true},
					{name:"images",type:"number",title:"Images",readOnly:true},
					{name:"inks",type:"number",title:"Inks",readOnly:true},
					{name:"highlighters",type:"number",title:"Highlighters",readOnly:true},
					{name:"texts",type:"number",title:"Texts",readOnly:true},
					{name:"quizResponses",type:"number",title:"Poll responses",readOnly:true},
					{name:"submissions",type:"number",title:"Submissions",readOnly:true},
				];
				participantsDatagrid.jsGrid({
					width:"100%",
					height:"auto",
					inserting:false,
					editing:false,
					sorting:true,
					paging:true,
					noDataContent: "No participants",
				 	controller: {
						loadData: function(filter){
							var sorted = _.map(_.keys(participants),function(k){
								var v = participants[k];
								return {
									name:k,
									following:v.following,
									attendances:_.size(v.attendances),
									images:v.images,
									inks:v.inks,
									texts:_.reduce(v.texts,function(acc,item){return acc + item},0),
									quizResponses:v.quizResponses,
									submissions:v.submissions,
									highlighters:v.highlighters
								};
							});
							if ("sortField" in filter){
								sorted = _.sortBy(sorted,function(sub){
									return sub[filter.sortField];
								});
								if ("sortOrder" in filter && filter.sortOrder == "desc"){
									sorted = _.reverse(sorted);
								}
							}
							return sorted;
						}
					},
					pageLoading:false,
					fields: gridFields	
				});
				participantsDatagrid.jsGrid("sort",{
					field:"name",
					order:"desc"
				});
				updateParticipantsListing();
    });
    Progress.stanzaReceived["participants"] = onStanzaReceived;
    Progress.historyReceived["participants"] = onHistoryReceived;
    Progress.conversationDetailsReceived["participants"] = onDetailsReceived;
    Progress.newConversationDetailsReceived["participants"] = onDetailsReceived;
    return {
        getParticipants:function(){return Conversations.shouldModifyConversation() ? participants : {};},
				reRender:function(){
					reRenderParticipants();
				},	
        code:function(author){
            return _.keys(participants).indexOf(author);
        }
    };
})();
