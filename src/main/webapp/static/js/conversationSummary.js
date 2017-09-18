var Conversation = (function(){
		var profiles = {};
		var receiveProfilesFunc = function(newProfiles){
			_.forEach(newProfiles,function(prof){
				profiles[prof.id] = prof;
			});
		};
		var receiveCurrentProfileFunc = function(prof){
			profile = prof;
			reRender();
		};
    var conversation = {};
    var userGroups = [];
    var username = "";
		var profile = {};
    var getUsernameFunc = function(){
        return username;
    };
    var receiveUsernameFunc = function(u){
        username = u;
    };
    var getConversationDetailsFunc = function(){
        return conversation;
    };
    var receiveConversationDetailsFunc = function(cd){
        conversation = cd;
        reRender();
    };
    var getUserGroupsFunc = function(){
        return userGroups;
    };
    var receiveUserGroupsFunc = function(ug){
        userGroups = ug;
        reRender();
    };

    var slidesContainer = {};
    var slideTemplate = {};

		var thumbnailForSlide = function(slide,slideElem){
			var thumbnailImage = slideElem.find(".slideThumbnail");
			switch (slide.slideType){
				case "SLIDE":
					thumbnailImage.attr("src",sprintf("/thumbnail/%s",slide.id));
					break;
				default:
					thumbnailImage.after($("<div/>",{
						text:slide.slideType + "_" +slide.id
					}));
					thumbnailImage.remove();
					break;
			}
		};

		var renderUser = function(userId){
			var cand = profiles[userId];
			if (cand !== undefined){
				return cand.name;
			} else {
				return userId;
			}
		};

    var reRender = function(){
        slidesContainer.html(_.map(_.sortBy(conversation.slides,"index"),function(slide){
            var rootElem = slideTemplate.clone();
            rootElem.find(".slideId").text(slide.id);
            rootElem.find(".slideIndex").text(slide.index);
					
						rootElem.find(".slideAnchor").attr("href",sprintf("/metl?conversationJid=%s&slideId=%s&unique=true",conversation.jid,slide.id));
						thumbnailForSlide(slide,rootElem);
            return rootElem;
        }));

				console.log("profiles",profiles);
        $(".conversationJid").text(conversation.jid);
        $(".conversationAuthor").text(renderUser(conversation.author));
        $(".conversationCreated").text(new Date(conversation.creation).toString());
        $(".conversationLastModified").text(new Date(conversation.lastAccessed).toString());

        $(".conversationTitle").text(conversation.title);
    }
		MeTLBus.subscribe("receiveUsername","editConversation",function(username){
			receiveUsernameFunc(username);
		});
		MeTLBus.subscribe("receiveUserGroups","editConversation",function(userGroups){
			receiveUserGroupsFunc(userGroups);
		});
		MeTLBus.subscribe("receiveConversationDetails","editConversation",function(details){
			receiveConversationDetailsFunc(details);
		});
			MeTLBus.subscribe("receiveNewConversationDetails","editConversation",function(details){
			console.log("new conversation received");
		});
		MeTLBus.subscribe("receiveProfiles","editConversation",function(profiles){ //invoked by Lift
			receiveProfilesFunc(profiles);
		});
		MeTLBus.subscribe("receiveCurrentProfile","editConversation",function(profile){ //invoked by Lift
			receiveCurrentProfileFunc(profile);
		});

    $(function(){
        slidesContainer = $("#sortableRoot");
        slideTemplate = slidesContainer.find(".slideContainer").clone();
        slidesContainer.empty();
        $(".backToConversations").attr("href","/conversationSearch");

        reRender();
    });
    return {
        getConversationDetails:getConversationDetailsFunc,
        getUsername:getUsernameFunc,
        getUserGroups:getUserGroupsFunc
    };
})();


