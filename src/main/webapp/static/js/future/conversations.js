var Conversations = (function(){
    var currentSearchTerm = "";
    var currentlyDisplayedConversations = [];
    var currentConversation = {};
    var currentSlide = 0;
    var targetConversationJid = "";
    var currentTeacherSlide = 0;
    var isSyncedToTeacher = false;

    var refreshSlideDisplay = function(){
        updateStatus("Refreshing slide display");
        $("#slideContainer").html(unwrap(currentConversation.slides.sort(function(a,b){return a.index - b.index;}).map(constructSlide))).append(constructAddSlideButton());
    }
    var changeConvToLectureFunction = function(jid){
        if (!jid){
            jid = currentConversation.jid.toString();
        }
        var newPermissions = {"studentCanOpenFriends":false,"studentCanPublish":false,"usersAreCompulsorilySynced":true};
        changePermissionsOfConversation(jid,newPermissions);
    };
    var changeConvToTutorialFunction = function(jid){
        if (!jid){
            jid = currentConversation.jid.toString();
        }
        var newPermissions = {"studentCanOpenFriends":false,"studentCanPublish":true,"usersAreCompulsorilySynced":false};
        changePermissionsOfConversation(jid,newPermissions);
    };
    var enableSyncMoveFunction = function(){ isSyncedToTeacher = true; };
    var disableSyncMoveFunction = function(){ isSyncedToTeacher = false; };
    var toggleSyncMoveFunction = function(){
        if (isSyncedToTeacher){
            isSyncedToTeacher = false;
        } else {
            isSyncedToTeacher = true;
        }
    };
    var getIsSyncedToTeacherDescriptorFunction = function(){
        if (isSyncedToTeacher){
            return "sync on";
        } else {
            return "sync off";
        }
    };
    var getConversationModeDescriptorFunction = function(){
        if (currentConversation && currentConversation.permissions && currentConversation.permissions.studentCanPublish){
            return "Tutorial";
        } else {
            return "Lecture";
        }
    };
    var actOnConversationDetails = function(details){
        try{
            updateStatus(sprintf("Updating to conversation %s",details.jid));
            if (details.jid.toString().toLowerCase() == targetConversationJid.toLowerCase()){
                if (shouldDisplayConversationFunction(details)){
                    currentConversation = details;
                }
                else {
                    currentConversation = {};
                    targetConversationJid = "";
                }
            }
            updateCurrentConversation(details);
        }
        catch(e){
            updateStatus(sprintf("FAILED: ReceiveConversationDetails exception: %s",e));
        }

    };
    var actOnConversations = function(listOfConversations){
        console.log("receiveConversations",listOfConversations);
        currentlyDisplayedConversations = listOfConversations;
        refreshConversationSearchResults();
    };
    var actOnSyncMove = function(jid){
        if (!shouldModifyConversationFunction(currentConversation) && currentConversation.slides.filter(function(slide){return slide.id.toString() == jid.toString();}).length > 0){
            currentTeacherSlide = jid;
            if (isSyncedToTeacher){
                if (currentSlide != jid){
                    console.log("syncMove moving to",jid);
                    currentSlide = jid;
                    doMoveToSlide(jid);
                }
            }
        }
    };
    var actOnNewConversationDetailsReceived = function(details){
        if (details.title.indexOf(currentSearchTerm) > -1 || details.author.indexOf(currentSearchTerm) > -1){
            currentlyDisplayedConversations.push(details);
            refreshConversationSearchResults();
        }
    };
    var actOnCurrentConversationJidReceived = function(jid){
        targetConversationJid = jid;
    };
    var actOnCurrentSlideJidReceived = function(jid){
        currentSlide = jid;
        indicateActiveSlide(jid);
    };


    var updateCurrentConversation = function(details){
        if (details.jid == currentConversation.jid){
            updateConversationHeader();
            Progress.call("onConversationJoin");
        }
        updateCurrentlyDisplayedConversations(details);
    };
    var updateCurrentlyDisplayedConversations = function(details){
        currentlyDisplayedConversations = currentlyDisplayedConversations.map(
            function(conv){
                if (conv.jid == details.jid){
                    return details;
                } else {
                    return conv;
                }
            }
        )
        refreshConversationSearchResults();
    };

    var shouldModifyConversationFunction = function(conversation){
        if (!conversation){
            conversation = currentConversation;
        }
        if ("author" in conversation && conversation.author.toLowerCase() == username.toLowerCase()){
            return true;
        } else {
            return false;
        }
    };
    var shouldDisplayConversationFunction = function(conversation){
        if (!conversation){
            conversation = currentConversation;
        }
        if("subject" in conversation && conversation.subject.toLowerCase() != "deleted") {
            return true;
        } else {
            return false;
        }
    };
    var shouldPublishInConversationFunction = function(conversation){
        if (!conversation){
            conversation = currentConversation;
        }
        if("permissions" in conversation && "studentCanPublish" in conversation.permissions && (shouldModifyConversationFunction(conversation) || conversation.permissions.studentCanPublish)){
            return true;
        } else {
            return false;
        }
    };
    var refreshConversationSearchResults = function(){
        $("#searchResults").html(unwrap(currentlyDisplayedConversations.filter(function(conv){return shouldDisplayConversationFunction(conv)}).map(constructConversation)));
    };
    var constructAddSlideButton = function(){
        if (shouldModifyConversationFunction()){
            return $("<input/>",{
                id: "addSlideButton",
                value: "add slide",
                name: "addSlideButton",
                type: "button"
            }).on("click",function(){
                var currentSlideIndex = currentConversation.slides.filter(function(slide){return slide.id == currentSlide;})[0].index;
                addSlideToConversationAtIndex(currentConversation.jid,currentSlideIndex);
            });
        } else {
            return $("<div/>");
        }
    }
    var doMoveToSlide = function(slideId){
        indicateActiveSlide(slideId);
        loadSlide(slideId);
    };
    var indicateActiveSlide = function(slideId){
        $(".slideButtonContainer").removeClass("activeSlide");
        $(sprintf("#slideContainer_%s",slideId)).addClass("activeSlide");
    };
    var constructSlide = function(slide){
        var slideIndex = slide.index + 1;
        var newSlide = $("<div/>",{
            id: sprintf("slideContainer_%s",slide.id),
            class:"slideButtonContainer"
        })
        $("<img/>",{
            id: sprintf("slideButton_%s",slide.id),
            src:sprintf("/thumbnail/madam/%s",slide.id),
            class:"thumbnail",
            alt:sprintf("Slide %s",slideIndex),
            title:sprintf("Slide %s (%s)",slideIndex,slide.id),
            width:px(getUserPref("thumbnailSize")),
            height:px(0.75 * getUserPref("thumbnailSize"))
        }).on("click",function(){
            disableSyncMoveFunction();
            doMoveToSlide(slide.id.toString());
        }).appendTo(newSlide);
        $("<span/>",{
            text: sprintf("%s/%s",slideIndex,currentConversation.slides.length)
        }).appendTo($("<div/>").appendTo(newSlide));
        return newSlide;
    }
    var constructConversation = function(conversation){
        var uniq = function(name){
            return sprintf("%s_%s",name,conversation.jid);
        };
        var jidString = conversation.jid.toString();
        var deleteSpan = $("<span>Delete</span>")
        var renameSpan = $("<span>Rename</span>")
        var sharingSpan = $("<span>Sharing</span>")
        var newConv = $("<div/>",{
            id: uniq("conversation"),
            class:"searchResult"
        }).on("click",function(e){
            var id1 = e.target.parentElement.id;
            var id2 = e.target.parentElement.parentElement.id;
            if(id1 ==uniq("extraConversationTools") || id2==uniq("extraConversationTools")) return;
            targetConversationJid = jidString;
            var firstSlide = conversation.slides.filter(function(slide){return slide.index == 0;})[0];
            hideBackstage();
            doMoveToSlide(firstSlide.id.toString());
        });
        var jidString = conversation.jid.toString();
        var row1 = $("<div/>");
        var row2 = $("<div/>",{
            class:"middleRow",
        });
        var row3 = $("<div/>",{
            id:uniq("extraConversationTools"),
            class: "extraConversationTools"
        });
        var convTitle = $("<span/>",{
            id: uniq("conversationTitle"),
            class: "conversationTitle",
            text: conversation.title
        });
        var convAuthor = $("<span/>",{
            class:"conversationAuthor",
            text: sprintf("by %s",conversation.author)
        });
        var joinConvButton = $("<div/>", {
            id: uniq("conversationJoin"),
            class: "conversationJoinButton conversationSearchButton",
            name: uniq("conversationJoin"),
            type: "button"
        }).on("click",function(){
            targetConversationJid = jidString;
            var firstSlide = conversation.slides.filter(function(slide){return slide.index == 0;})[0];
            hideBackstage();
            doMoveToSlide(firstSlide.id.toString());
        }).append("<span>join</span>");
        var renameConvButton = $("<div/>", {
            id: uniq("conversationRenameSubmit"),
            class: "conversationSearchButton",
            name: uniq("conversationRenameSubmit"),
            type: "button"
        }).on("click",function(){requestRenameConversationDialogue(jidString);}).append(renameSpan);
        var changeSharingButton = $("<div/>", {
            id: uniq("conversationChangeSubjectSubmit"),
            name: uniq("conversationChangeSubjectSubmit"),
            class: "conversationSearchButton",
            type: "button"
        }).on("click",function(){requestChangeSubjectOfConversationDialogue(jidString);}).append(sharingSpan);
        var deleteConvButton = $("<div/>", {
            id: uniq("conversationDelete"),
            class: "conversationSearchButton",
            name: uniq("conversationDelete"),
            type: "button"
        }).on("click",function(){
            requestDeleteConversationDialogue(jidString);
        }).append(deleteSpan);
        newConv.append(row1.append(convTitle));
        newConv.append(row2.append(convAuthor));
        if (shouldModifyConversationFunction(conversation)){
            newConv.append(row3.append(renameConvButton).append(changeSharingButton).append(deleteConvButton));
        }
        return newConv;
    }
    Progress.newConversationDetailsReceived["Conversations"] = actOnNewConversationDetailsReceived;
    Progress.conversationsReceived["Conversations"] = actOnConversations;
    Progress.syncMoveReceived["Conversations"] = actOnSyncMove;
    Progress.conversationDetailsReceived["Conversations"] = actOnConversationDetails;
    Progress.onConversationJoin["Conversations"] = refreshSlideDisplay;
    Progress.currentSlideJidReceived["Conversations"] = actOnCurrentSlideJidReceived;
    Progress.currentConversationJidReceived["Conversations"] = actOnCurrentConversationJidReceived;
    $(function(){
        $("#conversations").click(function(){
            showBackstage("conversations");
        });
        $("<div/>", {
            id:"createConversationButton",
            class: "conversationSearchButton",
            name:"createConversationButton",
            type:"button"
        }).append($("<span/>",{text:"Create Conversation"})).on("click",function(){
            createConversation(sprintf("%s created on %s",username,Date()));
        }).appendTo("#createConversationContainer");
        $("<div/>", {
            id:"myConversationsButton",
            class: "conversationSearchButton",
            type:"button",
        }).append($("<span/>",{text:"My Conversations"})).on("click",function(){
            getSearchResult(username);
        }).appendTo("#createConversationContainer");
        $("<div/>", {
            id:"searchButton",
            class: "conversationSearchButton",
            name:"searchButton",
            type: "button"
        }).append($("<span/>", {text: "Search"})).on("click",function(){
            getSearchResult(currentSearchTerm);
        }).appendTo("#searchButtonContainer");
        var updateSearchTerm = function(){
            currentSearchTerm = this.value;
            if(window.event){
                if (window.event.keyCode == 13){
                    getSearchResult(currentSearchTerm);
                }
            }
        };
        $("<input/>", {
            id:"searchForConversationBox",
            name:"searchForConversationBox",
            blur:updateSearchTerm,
            change:updateSearchTerm,
            focus:updateSearchTerm,
            keydown:updateSearchTerm,
            select:updateSearchTerm
        }).appendTo("#searchForConversationBoxContainer");
    });
    return {
        getCurrentTeacherSlide : function(){return currentTeacherSlide;},
        getCurrentSlideJid : function(){return currentSlide;},
        getCurrentSlide : function(){return _.find(currentConversation.slides,function(i){return i.id.toString() == currentSlide.toString();})},
        getCurrentConversationJid : function(){return currentConversation.jid.toString();},
        getCurrentConversation : function(){return currentConversation;},
        getIsSyncedToTeacher : function(){return isSyncedToTeacher;},
        getIsSyncedToTeacherDescriptor : getIsSyncedToTeacherDescriptorFunction,
        getConversationModeDescriptor : getConversationModeDescriptorFunction,
        enableSyncMove : enableSyncMoveFunction,
        disableSyncMove : disableSyncMoveFunction,
        toggleSyncMove : toggleSyncMoveFunction,
        changeConversationToTutorial : changeConvToTutorialFunction,
        changeConversationToLecture : changeConvToLectureFunction,
        shouldDisplayConversation : shouldDisplayConversationFunction,
        shouldPublishInConversation : shouldPublishInConversationFunction,
        shouldModifyConversation : shouldModifyConversationFunction
    };
})();

function unwrap(jqs){
    return _.pluck(jqs,"0");
}
function receiveCurrentSlide(jid){
    Progress.call("currentSlideJidReceived",[jid]);
}
function receiveCurrentConversation(jid){
    Progress.call("currentConversationJidReceived",[jid]);
}
function receiveConversationDetails(details){
    Progress.call("conversationDetailsReceived",[details]);
}
function receiveSyncMove(jid){
    Progress.call("syncMoveReceived",[jid]);
}
function receiveNewConversationDetails(details){
    Progress.call("newConversationDetailsReceived",[details]);
}
function receiveConversations(listOfConversations){
    Progress.call("conversationsReceived",[listOfConversations]);
}
// these will be injected by lift
//function moveToSlide(jid)
//function joinConversation(jid)
//function getConversation()
//function leaveConversation(jid)
//function getSearchResult(query)
//function createConversation(title)
//function deleteConversation(jid)
//function renameConversation(jid,newTitle)
//function changePermissions(jid,newPermissions)
//function changeSubject(jid,newSubject)
//function addSlide(jid,indexOfNewSlide)
//function reorderSlides(jid,alteredSlides)
