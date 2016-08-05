var Blacklist = (function(){
    var blacklistSummaryListing = {};
    var blacklistSummaryTemplate = {};
    var currentBlacklistTemplate = {};
    var currentBlacklistContainer = {};
		var blacklistAuthorsContainer = {};
		var blacklistAuthorTemplate = {};
    var blacklists = [];
		var blacklistAuthors = [];
    var currentBlacklist = {};
    $(function(){
        blacklistSummaryListing = $("#blacklistListing");
        blacklistSummaryTemplate = blacklistSummaryListing.find(".blacklistSummary").clone();
        currentBlacklistContainer = $("#currentBlacklist");
        currentBlacklistTemplate = currentBlacklistContainer.find(".blacklistContainer").clone();
        blacklistAuthorsContainer = $("#currentBlacklistAuthorList");
        blacklistAuthorTemplate = blacklistAuthorsContainer.find(".blacklistAuthorContainer").clone();
        blacklistSummaryListing.empty();
        blacklistAuthorsContainer.empty();
				refreshToolState();
    });
    var filteredBlacklists = function(){
			return _.filter(blacklists,filterBlacklist);
    };
    var filterBlacklist = function(sub){
      return Conversations.shouldModifyConversation();
    };
		var updateAuthorList = function(conversation){
			if ("blacklist" in conversation && "jid" in conversation && Conversations.getCurrentConversationJid() == conversation.jid){
				blacklistAuthors = conversation.blacklist;
				renderBlacklistAuthorsInPlace();
				refreshToolState(conversation);
			}
		};
		var renderBlacklistAuthorsInPlace = function(){
			blacklistAuthorsContainer.empty();
			var unbanAllButton = $("#unbanAll");
			if (blacklistAuthors.length > 0){
				unbanAllButton.show();
				unbanAllButton.unbind("click");
				unbanAllButton.on("click",function(){
					console.log("unbanall click");
					changeBlacklistOfConversation(Conversations.getCurrentConversationJid(),[]);
				});
			} else {
				unbanAllButton.unbind("click");
				unbanAllButton.hide();
			}
			blacklistAuthors.map(function(author){
				var rootElem = blacklistAuthorTemplate.clone();
				rootElem.find(".blacklistAuthorName").text(author);
				rootElem.find(".blacklistAuthorUnbanButton").on("click",function(){
					console.log(sprintf("unban %s click",author));
					blacklistAuthors = _.filter(blacklistAuthors,function(a){return a != author;});
					changeBlacklistOfConversation(Conversations.getCurrentConversationJid(),blacklistAuthors);
				});
				blacklistAuthorsContainer.append(rootElem);
			});
		};
		var refreshToolState = function(conversation){
			if (Conversations.shouldModifyConversation(conversation)){
				$("#ban").show();
				$("#administerContent").show();
				$("#menuBlacklist").show();
			} else {
				$("#ban").hide();
				$("#administerContent").hide();
				$("#menuBlacklist").hide();
				$("#blacklistPopup").hide();
			}
		};
    var clearState = function(conversation){
				refreshToolState(conversation);
        blacklists = [];
        currentBlacklist = {};
    };
    var renderBlacklistsInPlace = function(){
        blacklistSummaryListing.empty();
        filteredBlacklists().map(function(blacklist){
            renderBlacklistSummary(blacklist);
        })
        renderCurrentBlacklistInPlace();
    }
    var renderCurrentBlacklistInPlace = function(){
        currentBlacklistContainer.html(renderBlacklist(currentBlacklist));
    };
    var renderBlacklistSummary = function(blacklist){
        if ("type" in blacklist && blacklist.type == "submission" && "target" in blacklist && blacklist.target == "bannedcontent"){
            var rootElem = blacklistSummaryTemplate.clone();
            blacklistSummaryListing.append(rootElem);
            rootElem.find(".blacklistDescription").text(sprintf("submitted by %s at %s %s", blacklist.author, new Date(blacklist.timestamp).toDateString(),new Date(blacklist.timestamp).toLocaleTimeString()));
            rootElem.find(".blacklistImageThumb").attr("src",sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),blacklist.author,blacklist.identity));
            rootElem.find(".viewBlacklistButton").attr("id",sprintf("viewBlacklistButton_%s",blacklist.identity)).on("click",function(){
                currentBlacklist = blacklist;
                renderCurrentBlacklistInPlace();
            });
        }
    };
    var renderBlacklist = function(blacklist){
        var rootElem = $("<div />");
        if ("type" in blacklist && blacklist.type == "submission" && "target" in blacklist && blacklist.target == "bannedcontent"){
            if (Conversations.shouldModifyConversation()){
							rootElem = currentBlacklistTemplate.clone();
							rootElem.attr("id",sprintf("blacklist_%s",blacklist.identity))
							rootElem.find(".blacklistDescription").text(sprintf("submitted by %s at %s",blacklist.author, blacklist.timestamp));
							rootElem.find(".blacklistImage").attr("src",sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),blacklist.author,blacklist.identity));
							var authorContainer = rootElem.find(".blacklistAuthors");
							var authorTemplate = authorContainer.find(".blacklistAuthor").clone();
							authorContainer.empty();
							if ("blacklist" in blacklist){
								_.each(blacklist.blacklist,function(ba){
									if ("username" in ba && "highlight" in ba){
										var authorElem = authorTemplate.clone();
										authorElem.find(".blacklistAuthorName").text(ba.username);
										var color = ba.highlight[0];
										var opacity = ba.highlight[1];
										authorElem.find(".blacklistAuthorColor").css({"background-color":color,"opacity":opacity});
										authorContainer.append(authorElem);
									}
								});
							}
							rootElem.find(".displaySubmissionOnNextSlide").on("click",function(){
								addSubmissionSlideToConversationAtIndex(Conversations.getCurrentConversationJid(),Conversations.getCurrentSlide().index + 1,blacklist.identity);
							});
            } else {
							// do we need to do any hiding here?
            }
        }
        return rootElem;
    };
    var historyReceivedFunction = function(history){
        try {
            if ("type" in history && history.type == "history"){
                clearState();
                _.forEach(history.submissions,function(blacklist){
									onBlacklistReceived(blacklist,true);
								});
                renderBlacklistsInPlace();
            }
        }
        catch (e){
            console.log("Blacklists.historyReceivedFunction",e);
        }
    };
    var onBlacklistReceived = function(blacklist,skipRender){
        try {
            if ("target" in blacklist && blacklist.target == "bannedcontent"){
                if (filterBlacklist(blacklist)){
                    blacklists.push(blacklist);
										if (!skipRender){
											renderBlacklistsInPlace();
										}
                }
            }
        }
        catch (e){
            console.log("Blacklists.stanzaReceivedFunction",e);
        }
    };

    Progress.conversationDetailsReceived["blacklist"] = updateAuthorList;
    Progress.onConversationJoin["blacklist"] = clearState;
    Progress.historyReceived["blacklist"] = historyReceivedFunction;
		Progress.stanzaReceived["blacklist"] = onBlacklistReceived;
    return {
			getAllBlacklists:function(){return Conversations.shouldModifyConversation() ? filteredBlacklists() : [];},
			getCurrentBlacklist:function(){return Conversations.shouldModifyConversation() ? currentBlacklist : {};},
			processBlacklist:onBlacklistReceived,
			getBlacklistedAuthors:function(){return Conversations.shouldModifyConversation() ? blacklistAuthors : [];}
    };
})();
