
var Conversation = (function(){
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

    var reorderContainer = {};
    var sharingContainer = {};
    var sharingCategoryTemplate = {};
    var sharingChoiceTemplate = {};
    var slidesContainer = {};
    var slideTemplate = {};

    var slideReorderInProgress = false;
    var fixSlides = function(){
        if (slideReorderInProgress){
            reorderContainer.show();
        } else {
            reorderContainer.hide();
        }
        _.forEach($(".slideContainer"),function(sce){
            var sc = $(sce);
            var nextItem = $(sc).next(".slideContainer");
            var prevItem = $(sc).prev(".slideContainer");
            var moveBackButton = sc.find(".moveSlideBack");
            var moveForwardButton = sc.find(".moveSlideForward");
            moveBackButton.unbind("click");
            moveForwardButton.unbind("click");
            if (nextItem.length == 0){
                moveForwardButton.hide();
            } else {
                moveForwardButton.show();
                moveForwardButton.on("click",function(){
                    slideReorderInProgress = true;
                    sc.detach();
                    nextItem.after(sc);
                    fixSlides();
                });
            }
            if (prevItem.length == 0){
                moveBackButton.hide();
            } else {
                moveBackButton.show();
                moveBackButton.on("click",function(){
                    slideReorderInProgress = true;
                    sc.detach();
                    prevItem.before(sc);
                    fixSlides();
                });
            }
        });
    };
    var reRender = function(){
        slidesContainer.html(_.map(_.sortBy(conversation.slides,"index"),function(slide){
            var rootElem = slideTemplate.clone();
            rootElem.find(".slideId").text(slide.id);
            rootElem.find(".slideIndex").text(slide.index);
            rootElem.find(".slideAnchor").attr("href",sprintf("board?conversationJid=%s&slideId=%s&unique=true",conversation.jid.toString(),slide.id.toString())).find(".slideThumbnail").attr("src",sprintf("/thumbnail/%s",slide.id));
            rootElem.find(".addSlideBeforeButton").on("click",function(){
                addSlideToConversationAtIndex(conversation.jid.toString(),slide.index);
                reRender();
            });
            rootElem.find(".duplicateSlideButton").on("click",function(){
                duplicateSlideById(conversation.jid.toString(),slide.id);
                reRender();
            });
            rootElem.find(".addSlideAfterButton").on("click",function(){
                addSlideToConversationAtIndex(conversation.jid.toString(),slide.index + 1);
                reRender();
            });
            return rootElem;
        }));

        $("#sortableRoot").sortable({
            handle: ".slideDragHandle",
            stop: function(ev,ui){
                slideReorderInProgress = true;
                fixSlides();
            }
        }).disableSelection();

        if (conversation.subject == "deleted"){
            $("#unarchiveChallenge").show();
            $("#archiveChallenge").hide();
            $("#sharingContainer").hide();
        } else {
            $("#unarchiveChallenge").hide();
            $("#archiveChallenge").show();
            $("#sharingContainer").show();
        };

        $(".conversationJid").text(conversation.jid);
        $(".conversationAuthor").text(conversation.author);
        $(".conversationCreated").text(new Date(conversation.creation).toString());
        $(".conversationLastModified").text(new Date(conversation.lastAccessed).toString());

        $("#conversationTitleInput").val(conversation.title);
        $(".joinConversation").attr("href",sprintf("/board?conversationJid=%s&unique=true",conversation.jid));

	console.log("usergroups",userGroups);
        sharingContainer.html(_.map(_.groupBy(_.uniqBy(_.concat(userGroups,[{"ouType":"special","name":conversation.subject}]),"name"),function(item){return item.ouType;}),function(categoryGroups){
            var rootElem = sharingCategoryTemplate.clone();

            var sharingChoiceTemplate = rootElem.find(".conversationSharingChoiceContainer").clone();
            var container = rootElem.find(".conversationSharingChoiceContainer");
            rootElem.find(".conversationSharingCategory").text(categoryGroups[0].ouType);
            var choiceElem = container.find(".conversationSharingChoice").clone();
            container.html(_.map(categoryGroups,function(categoryGroup){
                var choiceId = _.uniqueId();
                var choiceRoot = choiceElem.clone();
                var inputElem = choiceRoot.find(".conversationSharingChoiceInputElement")

                inputElem.attr("id",choiceId).attr("type","radio").on("click",function(){
                    changeSubjectOfConversation(conversation.jid.toString(),categoryGroup.name);
                    reRender();
                }).prop("checked",conversation.subject == categoryGroup.name);
                choiceRoot.find(".conversationSharingChoiceLabel").attr("for",choiceId).text(categoryGroup.name);
                return choiceRoot;
            }));
            var sectionVisible = false;
            var collapser = rootElem.find(".conversationSharingCollapser").addClass(categoryGroups[0].ouType);
            collapser.on("click",function(){
                container.toggle();
                sectionVisible = !sectionVisible;
                if (sectionVisible){
                    collapser.addClass("fa-toggle-right");
                    collapser.removeClass("fa-toggle-down");
                } else {
                    collapser.addClass("fa-toggle-down");
                    collapser.removeClass("fa-toggle-right");
                }
            });
            if (_.some(categoryGroups,function(item){return item.name == conversation.subject;})){
                sectionVisible = true;
                container.show();
                collapser.addClass("fa-toggle-right");
                collapser.removeClass("fa-toggle-down");
            } else {
                sectionVisible = false;
                container.hide();
                collapser.addClass("fa-toggle-down");
                collapser.removeClass("fa-toggle-right");
            }
            return rootElem;
        }));
        fixSlides();
    }
    function sendChangedSlides(){
        try {
            if (conversation != undefined && "jid" in conversation && "slides" in conversation) {
                var oldSlides = conversation.slides;
                var newIndex = 0;
                var newSlides = _.map($(".slideId"),function(el){
                    var slideId = parseInt($(el).text());
                    var returnedSlide = _.find(conversation.slides,function(slide){
                        if (slide.id == slideId){
                            return true;
                        } else {
                            return false;
                        }
                    });
                    if (!("groupSet" in returnedSlide)){
                        returnedSlide.groupSet = [];
                    }
                    returnedSlide.index = newIndex;
                    newIndex = newIndex + 1;
                    return returnedSlide;
                });
                reorderSlidesOfCurrentConversation(conversation.jid.toString(),newSlides);
                slideReorderInProgress = false;
                reRender();
            }
        } catch(e){
            console.log("exception while reordering slides",e);
        }
    }
    $(function(){
        reorderContainer = $("#reorderInProgress");
        sharingContainer = $(".conversationSharing");
        sharingCategoryTemplate = sharingContainer.find(".conversationSharingCategoryContainer").clone();
        sharingContainer.empty();
        slidesContainer = $("#sortableRoot");
        slideTemplate = slidesContainer.find(".slideContainer").clone();
        slidesContainer.empty();
        $("#reorderSlides").on("click",function(){
            sendChangedSlides();
        });
        $("#cancelReorderSlides").on("click",function(){
            slideReorderInProgress = false;
            reRender();
        });
        $(".backToConversations").attr("href","/conversationSearch");
        var changeTitleFunc = function(){
            var newTitle = $(this).val();
            renameConversation(conversation.jid.toString(),newTitle);
        };
        $("#conversationTitleInput").on("blur",changeTitleFunc).keyup(function(e){
            if (e.which == 13){
                e.preventDefault();
                var newTitle = $(this).val();
                renameConversation(conversation.jid.toString(),newTitle);
            };
        });
        $("#archiveChallenge").on("click",function(){
            $.jAlert({
                type:"confirm",
                confirmQuestion:"Are you sure you want to archive this conversation?  It will not be available in the application.  Participants' content will become unavailable.",
                confirmBtnText:"Archive",
                onConfirm:function(e,btn){
                    e.preventDefault();
                    deleteConversation(conversation.jid.toString());
                    window.location.href = "/conversationSearch";
                },
                denyBtnText:"Cancel",
                onDeny:function(e,btn){
                    e.preventDefault();
                }
            });
        });
        $("#unarchiveChallenge").on("click",function(){
            $.jAlert({
                type:"confirm",
                confirmQuestion:"Are you sure you want to unarchive this conversation?  It will be unarchived, but set to share only with you.  If you wish to share this conversation with other participants, remember to change the sharing to an appropriate level.",
                confirmBtnText:"Unarchive",
                onConfirm:function(e,btn){
                    e.preventDefault();
                    changeSubjectOfConversation(conversation.jid.toString(),username);
                },
                denyBtnText:"Cancel",
                onDeny:function(e,btn){
                    e.preventDefault();
                }
            });
        });
        $("#duplicateChallenge").on("click",function(){
            $.jAlert({
                type:"confirm",
                confirmQuestion:"Are you sure you want to duplicate this conversation?  Only your content will be duplicated.  Content from other participants will not be duplicated.",
                confirmBtnText:"Duplicate",
                onConfirm:function(e,btn){
                    e.preventDefault();
                    duplicateConversation(conversation.jid.toString());
                },
                denyBtnText:"Cancel",
                onDeny:function(e,btn){
                    e.preventDefault();
                }
            });
        });
        reRender();
    });
    return {
        receiveUserGroups:receiveUserGroupsFunc,
        receiveUsername:receiveUsernameFunc,
        receiveConversationDetails:receiveConversationDetailsFunc,
        getConversationDetails:getConversationDetailsFunc,
        getUsername:getUsernameFunc,
        getUserGroups:getUserGroupsFunc
    };
})();

function augmentArguments(args){
    args[_.size(args)] = new Date().getTime();
    return args;
}

function serverResponse(response){
    //console.log("serverResponse:",response);
}
function receiveUsername(username){
    Conversation.receiveUsername(username);
}
function receiveUserGroups(userGroups){
    Conversation.receiveUserGroups(userGroups);
}
function receiveConversationDetails(details){
    Conversation.receiveConversationDetails(details);
}
function receiveNewConversationDetails(details){
    console.log("new conversation received");
}
