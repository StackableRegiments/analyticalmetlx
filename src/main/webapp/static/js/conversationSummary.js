var Conversation = (function(){
		var profiles = {};
		var receiveProfilesFunc = function(newProfiles){
			profiles = _.merge(profiles,newProfiles);
		};
    var conversation = {};
    var userGroups = [];
    var username = "";
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
					thumbnailImage.replace($("<div/>",{
						text:slide.slideType + "_" +slide.id
					}));
					break;
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

        $(".conversationJid").text(conversation.jid);
        $(".conversationAuthor").text(conversation.author);
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
		MeTLBus.subscribe("receiveProfile","editConversation",function(profile){ //invoked by Lift
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


