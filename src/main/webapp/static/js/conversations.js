var Conversations = (function(){
    var currentSearchTerm = "";
    var currentlyDisplayedConversations = [];
    var currentConversation = {};
    var currentServerConfigName = "external";
    var currentSlide = 0;
    var targetConversationJid = "";
    var currentTeacherSlide = 0;
    var isSyncedToTeacher = false;

    var conversationTemplate = undefined;
    var conversationSearchListing = undefined;

    $(function(){
        //take a template of the html for the searchResultItem
        conversationSearchListing = $("#searchResults");
        conversationTemplate = conversationSearchListing.find(".searchResultItem").clone();
        conversationSearchListing.empty();
    });
    var ThumbCache = (function(){
        var cacheRefreshTime = 10 * 1000; // 10 seconds
        var cache = {};
        /*
         Workaround for parallel connection limits queueing thumbnail loads behind long poll
         */
        var fetchAndPaintThumb = function(slide,slideContainer,slideImage){
            console.log("fetching",slide.id)
            var thumbUrl = sprintf("/thumbnailDataUri/%s",slide.id);
            var storeThumb = function(data){
                cache[slide.id] = {
                    data:data,
                    when:Date.now()
                };
                //Use the data straight away instead of recursing
                slideImage.attr("src",data);
            };
            $.ajax({
                url:thumbUrl,
                beforeSend: function ( xhr ) {
                    xhr.overrideMimeType("text/plain; charset=x-user-defined");
                },
                dataType: "text"
            }).done(storeThumb);
        };
        var paintThumb = function(slide,slideContainer){
            var slideImage = slideContainer.find("img");
            if (slide.id in cache && cache[slide.id].when > (Date.now() - cacheRefreshTime)){
                console.log("cached",slide.id)
                slideImage.attr("src",cache[slide.id].data);
            } else {
                fetchAndPaintThumb(slide,slideContainer,slideImage);
            }
        };
        var makeBlankCanvas = function(w,h){
            var c = $("<canvas />");
            c.width = w;
            c.height = h;
            c.attr("width",w);
            c.attr("height",h);
            var ctx = c[0].getContext("2d");
            ctx.rect(0,0,w,h);
            ctx.fillStyle="white";
            ctx.fill();
            return c[0].toDataURL();
        }
        var blank4to3Canvas = makeBlankCanvas(320,240);
        var possiblyUpdateThumbnail = function(slide){
            var thumbScroller = $("#thumbScrollContainer");
            var slidesTop = 0;
            var slidesBottom = thumbScroller.height();
            var slideContainer = thumbScroller.find(sprintf("#slideContainer_%s",slide.id));
            try {
                var slideImage = slideContainer.find("img");
                var slideTop = slideContainer.position().top;
                var slideBottom = slideTop + slideContainer.height();
                if (slideTop == slideBottom){
                    slideImage.attr("src",blank4to3Canvas);
                    return;
                }
                var isVisible = (slideBottom >= slidesTop) && (slideTop <= slidesBottom);
                if (isVisible){
                    paintThumb(slide,slideContainer);
                }
            } catch(e) {
                console.log("exception while painting thumb: ",e);
                //couldn't find the slideContainer at this time.
            }
        }
        var clearCacheFunction = function(){
            cache = {};
        };
        var paintAllThumbsFunc = function(){
            console.log("Paint all thumbs")
            _.forEach(currentConversation.slides,function(slide){
                var img = $(sprintf("#slideContainer_%s img",slide.id));
                if (img.height() == 0 || img.height() == undefined){
                    img.on("load",function(){
                        img.off("load");
                        possiblyUpdateThumbnail(slide);
                    });
                    img.attr("src",blank4to3Canvas);
                } else {
                    possiblyUpdateThumbnail(slide);
                }
            });
        };
        return {
            paintThumb:possiblyUpdateThumbnail,
            paintAllThumbs:_.debounce(paintAllThumbsFunc,500),
            clearCache:clearCacheFunction
        };
    })();

    var shouldRefreshSlideDisplay = function(details){
        return (!("slides" in currentConversation) || "slides" in details && _.some(details,function(slide,slideIndex){
            var ccs = currentConversation.slides[slideIndex];
            if (ccs && "id" in ccs && "id" in slide && "index" in slide && "index" in ccs && ccs.id == slide.id && ccs.index == slide.index){
                return false;
            } else {
                return true;
            }
        }));
    }
    var paintThumbs = function(){
        try {
            ThumbCache.paintAllThumbs();
        }
        catch(e){
            console.log("exception while painting thumbs",e);
        }
    }
    var refreshSlideDisplay = function(){
        updateStatus("Refreshing slide display");
        var slideContainer = $("#slideContainer")
        slideContainer.html(unwrap(currentConversation.slides.sort(function(a,b){return a.index - b.index;}).map(constructSlide))).append(constructAddSlideButton());
        slideContainer.off("scroll");
        slideContainer.on("scroll",paintThumbs);
        Progress.call("onLayoutUpdated");
    }

    var setStudentsCanPublishFunction = function(publishingAllowed){
        var jid = currentConversation.jid.toString();
        var oldPerms = currentConversation.permissions;
        var newPermissions = {
            "studentCanOpenFriends":oldPerms.studentCanOpenFriends,
            "studentCanPublish":publishingAllowed,
            "usersAreCompulsorilySynced":oldPerms.usersAreCompulsorilySynced
        };
        changePermissionsOfConversation(jid,newPermissions);
    };

    var getStudentsCanPublishFunction = function(){
        return currentConversation.permissions.studentCanPublish;
    };
    var setStudentsMustFollowTeacherFunction = function(mustFollowTeacher){
        var jid = currentConversation.jid.toString();
        var oldPerms = currentConversation.permissions;
        var newPermissions = {
            "studentCanOpenFriends":oldPerms.studentCanOpenFriends,
            "studentCanPublish":oldPerms.studentCanPublish,
            "usersAreCompulsorilySynced":mustFollowTeacher
        };
        changePermissionsOfConversation(jid,newPermissions);
    };
    var getStudentsMustFollowTeacherFunction = function(){
        return currentConversation.permissions.usersAreCompulsorilySynced;
    };

    var enableSyncMoveFunction = function(){
        isSyncedToTeacher = true;
        redrawSyncState();
    };
    var disableSyncMoveFunction = function(){
        if ("permissions" in currentConversation && !shouldModifyConversationFunction(currentConversation) && currentConversation.permissions.usersAreCompulsorilySynced) {
            return;
        }
        isSyncedToTeacher = false;
        redrawSyncState();
    };
    var redrawSyncState = function(){
        if (isSyncedToTeacher){
            $("#enableSync").addClass("activePrivacy active");
            $("#disableSync").removeClass("activePrivacy active");
        } else {
            $("#enableSync").removeClass("activePrivacy active");
            $("#disableSync").addClass("activePrivacy active");
        }
        $("#followTeacherCheckbox").prop("checked",isSyncedToTeacher);
    }
    var toggleSyncMoveFunction = function(){
        if (isSyncedToTeacher){
            disableSyncMoveFunction();
        } else {
            enableSyncMoveFunction();
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
            return "collaboration enabled";
        } else {
            return "collaboration disabled";
        }
    };
    var actOnConversationDetails = function(details){
        var oldConversationJid = "";
        if ("jid" in currentConversation){
            oldConversationJid = currentConversation.jid.toString().toLowerCase();
        };
        try{
            updateStatus(sprintf("Updating to conversation %s",details.jid));
            if (details.jid.toString().toLowerCase() == targetConversationJid.toLowerCase()){
                if (shouldDisplayConversationFunction(details)){
                    currentConversation = details;
                    if ("configName" in details){
                        currentServerConfigName = details.configName;
                    }
                    currentServerConfigName
                    if (currentConversation.jid.toString().toLowerCase() != oldConversationJid){
                        Progress.call("onConversationJoin");
                        ThumbCache.clearCache();
                    }
                }
                else {
                    currentConversation = {};
                    targetConversationJid = "";
                }
            }
            updateCurrentConversation(details);
            if (!(_.some(currentlyDisplayedConversations,function(c){return c.jid == details.jid;})) && shouldModifyConversationFunction(details)){
                currentlyDisplayedConversations.push(details);
                refreshConversationSearchResults();
            }
        }
        catch(e){
            updateStatus(sprintf("FAILED: ReceiveConversationDetails exception: %s",e));
        }
        Progress.call("onLayoutUpdated");
    };
    var actOnConversations = function(listOfConversations){
        currentlyDisplayedConversations = listOfConversations;
        refreshConversationSearchResults();
    };
    //var onSyncMoveTimerElapsed = undefined;
    var actOnSyncMove = function(jid){
        if ((Conversations.getIsSyncedToTeacher() && !shouldModifyConversationFunction(currentConversation)) || (!UserSettings.getIsInteractive())){
            if ("slides" in currentConversation && currentConversation.slides.filter(function(slide){return slide.id.toString() == jid.toString();}).length > 0){
                WorkQueue.enqueue(function(){
                    performSyncMoveTo(jid);
                    return false;
                });
            }
        }
        /*
         if ((!shouldModifyConversationFunction(currentConversation)) || (!UserSettings.getIsInteractive())){
         if ("slides" in currentConversation && currentConversation.slides.filter(function(slide){return slide.id.toString() == jid.toString();}).length > 0){
         var syncMoveDelay = 3000;
         onSyncMoveTimerElapsed = function(){
         onSyncMoveTimerElapsed = undefined;
         console.log("syncMove moving to",jid,new Date());
         performSyncMoveTo(jid);
         };
         var whenAble = function(){
         console.log("syncMove polling for",jid,new Date());
         if (onSyncMoveTimerElapsed){
         if (WorkQueue.isAbleToWork){
         onSyncMoveTimerElapsed();
         } else {
         WorkQueue.enqueue(function(){
         console.log("syncMove re-establishing for",jid,new Date());
         setTimeout(whenAble,syncMoveDelay);
         });
         }
         }
         };
         WorkQueue.enqueue(function(){
         console.log("syncMove establishing for",jid,new Date());
         setTimeout(whenAble,syncMoveDelay);
         });
         }
         }
         */
    }
    var performSyncMoveTo = function(jid){
        if ((!shouldModifyConversationFunction(currentConversation)) || (!UserSettings.getIsInteractive())){
            if ("slides" in currentConversation && currentConversation.slides.filter(function(slide){return slide.id.toString() == jid.toString();}).length > 0){
                currentTeacherSlide = jid;
                if (Conversations.getIsSyncedToTeacher()){
                    if (currentSlide != jid){
                        console.log("syncMove moving to",jid);
                        currentSlide = jid;
                        doMoveToSlide(jid);
                    }
                }
            }
        }
    };
    var updateThumbnailFor = function(slideId) {
        //setting index to zero because this isn't necessary.
        var slidesContainer = $("#slideContainer");
        var containerHeight = slidesContainer.height();
        ThumbCache.paintThumb({id:slideId,index:0},slidesContainer,containerHeight);
    }
    var goToNextSlideFunction = function(){
        if ("slides" in currentConversation && currentSlide > 0){
            var curr = _.find(currentConversation.slides,function(s){return s.id == currentSlide;});
            var next = _.find(currentConversation.slides,function(s){return s.index == (curr.index + 1);});
            if (next != undefined && "id" in next){
                doMoveToSlide(next.id.toString());
            }
        }
    };
    var goToPrevSlideFunction = function(){
        if ("slides" in currentConversation && currentSlide > 0){
            var curr = _.find(currentConversation.slides,function(s){return s.id == currentSlide;});
            var next = _.find(currentConversation.slides,function(s){return s.index == (curr.index - 1);});
            if (next != undefined && "id" in next){
                doMoveToSlide(next.id.toString());
            }
        }
    };
    var actOnNewConversationDetailsReceived = function(details){
        if (details.title.indexOf(currentSearchTerm) > -1 || details.author.indexOf(currentSearchTerm) > -1){
            currentlyDisplayedConversations = _.filter(currentlyDisplayedConversations,function(c){return c.jid != details.jid});
            currentlyDisplayedConversations.push(details);
            refreshConversationSearchResults();
        }
    };
    var actOnCurrentConversationJidReceived = function(jid){
        targetConversationJid = jid;
        updateLinks();
    };
    var actOnCurrentSlideJidReceived = function(jid){
        currentSlide = jid;
        indicateActiveSlide(jid);
        updateLinks();
    };
    var updateLinks = function(){
        var serviceUrlRoot = window.location.origin;
        var shareUrl = sprintf("/join?conversation=%s&slide=%s",targetConversationJid,currentSlide);
        var projectorUrl = sprintf("/projector/%s",targetConversationJid);
        $("#shareLink").html($("<a/>",{
            href:shareUrl,
            text:serviceUrlRoot + shareUrl
        }));
        $("#projectorLink").html($("<a/>",{
            href:projectorUrl,
            text:serviceUrlRoot + projectorUrl
        })).on("click",bounceAnd(function(){
            var el = document.documentElement, rfs =
                    el.requestFullScreen
                    || el.webkitRequestFullScreen
                    || el.mozRequestFullScreen;
            rfs.call(el);

            DeviceConfiguration.setCurrentDevice("projector");
            return false;
        }));
				if (targetConversationJid == "" || currentSlide == 0){
					$("#projectorViewLink").empty();
					$("#slideDeepLink").empty();
					$("#conversationDeepLink").empty();
				} else {
					$("#projectorViewLink").html($("<a/>",{
						href:sprintf("/board?conversationJid=%s&slideId=%s&showTools=false",targetConversationJid,currentSlide),
						text:"Project this conversation"
					}));
					$("#slideDeepLink").html($("<a/>",{
						href:sprintf("/board?conversationJid=%s&slideId=%s",targetConversationJid,currentSlide),
						text:"DeepLink this slide"
					}));
					$("#conversationDeepLink").html($("<a/>",{
						href:sprintf("/board?conversationJid=%s",targetConversationJid),
						text:"Deeplink this conversation"
					}));
				}
    };
    var updatePermissionButtons = function(details){
        var isAuthor = shouldModifyConversationFunction(details);
        var scpc = $("#studentsCanPublishCheckbox");
        scpc.off("change");
        scpc.prop("checked",details.permissions.studentCanPublish);
        scpc.prop("disabled",!isAuthor);
        if (isAuthor){
            scpc.on("change",function(){
                setStudentsCanPublishFunction(scpc.is(":checked"));
            });
        }
        var smftc = $("#studentsMustFollowTeacherCheckbox");
        smftc.off("change");
        smftc.prop("checked",details.permissions.usersAreCompulsorilySynced);
        smftc.prop("disabled",!isAuthor);
        var ftc = $("#followTeacherCheckbox");
        ftc.off("change");
        ftc.prop("checked",isSyncedToTeacher);
        if (isAuthor){
            smftc.on("change",function(){
                setStudentsMustFollowTeacherFunction(smftc.is(":checked"));
            });
        } else {
            ftc.on("change",function(){
                var previousState = isSyncedToTeacher;
                var currentState = ftc.is(":checked");
                if (previousState != currentState){
                    if (currentState){
                        enableSyncMoveFunction();
                    } else {
                        disableSyncMoveFunction();
                    }
                }
            });
        }
        ftc.prop("disabled", details.permissions.usersAreCompulsorilySynced)
        if (isAuthor){
            $("#syncButtons").hide();
            $("#syncCheckbox").hide();
        } else {
            $("#syncButtons").show();
            $("#syncCheckbox").show();
        }
    };
    var updateCurrentConversation = function(details){
        if (details.jid == currentConversation.jid){
            if (!shouldModifyConversationFunction(details) && details.permissions.usersAreCompulsorilySynced){
                enableSyncMoveFunction();
            }
            updatePermissionButtons(details);
            updateConversationHeader();
            updateLinks();
            redrawSyncState();
            if (shouldRefreshSlideDisplay(details)){
                refreshSlideDisplay();
            }
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
        if ("author" in conversation && conversation.author.toLowerCase() == UserSettings.getUsername().toLowerCase()){
            return true;
        } else {
            return false;
        }
    };
		var getIsBannedFunction = function(conversation){
			if (!conversation){
					conversation = currentConversation;
			}
			return ("blacklist" in conversation && _.includes(conversation.blacklist,UserSettings.getUsername()));
		};
    var shouldDisplayConversationFunction = function(conversation){
        if (!conversation){
            conversation = currentConversation;
        }
        if ("subject" in conversation && conversation.subject.toLowerCase() != "deleted" && (("author" in conversation && conversation.author == UserSettings.getUsername()) || _.some(UserSettings.getUserGroups(), function(group){
            return group.value.toLowerCase() == conversation.subject.toLowerCase();
        }))) {
            return true;
        } else {
            return false;
        }
    };
    var shouldPublishInConversationFunction = function(conversation){
        if (!conversation){
            conversation = currentConversation;
        }
        if("permissions" in conversation && "studentCanPublish" in conversation.permissions && (shouldModifyConversationFunction(conversation) || conversation.permissions.studentCanPublish) && !getIsBannedFunction(conversation)){
            return true;
        } else {
            return false;
        }
    };
    var refreshConversationSearchResults = function(){
        try {
            var convs = _.sortBy(currentlyDisplayedConversations.filter(function(conv){
                return shouldDisplayConversationFunction(conv);
            }),function(conv){return new Date(conv.created);}).reverse().map(constructConversation);
            var searchResults = $("#searchResults");
            if (_.size(convs) > 0){
                searchResults.html(unwrap(convs));
            }   else {
                searchResults.html($("<div/>",{
                    text:"No search results found"
                }));
            }
        } catch(e){
            console.log("refreshConversationSearchResults",e);
        }
    };
    var constructAddSlideButton = function(){
        if (shouldModifyConversationFunction()){
            return $("<button/>",{
                id: "addSlideButton",
                class:"toolbar fa fa-plus btn-icon nmt",
                name: "addSlideButton",
                type: "button"
            }).append($("<div class='icon-txt'>Add Slide</div>")).on("click",bounceAnd(function(){
                var currentJid = currentConversation.jid;
                var currentSlideIndex = currentConversation.slides.filter(function(slide){return slide.id == currentSlide;})[0].index;
                var newIndex = currentSlideIndex + 1;
                addSlideToConversationAtIndex(currentConversation.jid,newIndex);
                Progress.conversationDetailsReceived["JoinAtIndexIfAvailable"] = function(incomingDetails){
                    if ("jid" in incomingDetails && incomingDetails.jid == currentJid){
                        if ("slides" in incomingDetails){
                            var newSlide = _.find(incomingDetails.slides,function(s){
                                return s.index == newIndex && s.id != currentSlide;
                            });
                            doMoveToSlide(newSlide.id.toString());
                        }
                    }
                };
            }));
        } else {
            return $("<div/>");
        }
    }
    var doMoveToSlide = function(slideId){
        delete Progress.conversationDetailsReceived["JoinAtIndexIfAvailable"];
        WorkQueue.enqueue(function(){
            console.log("doMoveToslide",slideId);
            indicateActiveSlide(slideId);
            loadSlide(slideId);
            return true;
        });
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
        });
        $("<img/>",{
            id: sprintf("slideButton_%s",slide.id),
            class:"thumbnail",
            alt:sprintf("Slide %s",slideIndex),
            title:sprintf("Slide %s (%s)",slideIndex,slide.id)
        }).on("click",function(){
            disableSyncMoveFunction();
            doMoveToSlide(slide.id.toString());
        }).appendTo(newSlide);
        $("<span/>",{
            text: sprintf("%s/%s",slideIndex,currentConversation.slides.length),
            class: "slideThumbnailNumber"
        }).appendTo($("<div/>").addClass("slide-count").appendTo(newSlide));
        return newSlide;
    }
    var constructConversation = function(conversation){

        var uniq = function(name){
            return sprintf("%s_%s",name,conversation.jid);
        };
        var jidString = conversation.jid.toString();
        var newConv = conversationTemplate.clone();
        newConv.attr("id",uniq("conversation")).on("click",bounceAnd(function(e){
            var id1 = e.target.parentElement.id;
            var id2 = e.target.parentElement.parentElement.id;
            if(id1 ==uniq("extraConversationTools") || id2==uniq("extraConversationTools")) return;
            targetConversationJid = jidString;
            var firstSlide = conversation.slides.filter(function(slide){return slide.index == 0;})[0];
            hideBackstage();
            doMoveToSlide(firstSlide.id.toString());
        }));
        var jidString = conversation.jid.toString();
        var row1 = newConv.find(".searchResultTopRow");
        var row2 = newConv.find(".searchResultMiddleRow");
        var row3 = newConv.find(".teacherConversationTools");
        row3.attr("id",uniq("extraConversationTools"));
        newConv.find(".conversationTitle").attr("id",uniq("conversationTitle")).text(conversation.title);
        newConv.find(".conversationAuthor").text(conversation.author);
        newConv.find(".conversationSubject").text(conversation.subject);
        newConv.find(".conversationCreated").text(conversation.created);

        if (shouldModifyConversationFunction(conversation)){

            newConv.find(".conversationRename").attr("id",uniq("conversationRenameSubmit")).attr("name",uniq("conversationRenameSubmit")).on("click",function(){requestRenameConversationDialogue(jidString);});
            newConv.find(".conversationShare").attr("id",uniq("conversationChangeSubjectSubmit")).attr("name",uniq("conversationChangeSubjectSubmit")).on("click",function(){requestChangeSubjectOfConversationDialogue(jidString);});
            newConv.find(".conversationDelete").attr("id",uniq("conversationDelete")).attr("name",uniq("conversationDelete")).on("click",function(){ requestDeleteConversationDialogue(jidString); });
        } else {
            newConv.find(".teacherConversationTools").remove()
        }
        if ("jid" in conversation && targetConversationJid.trim().toLowerCase() == conversation.jid.toString().trim().toLowerCase()){
            newConv.addClass("activeConversation");
        }
        return newConv;
    }
    Progress.newConversationDetailsReceived["Conversations"] = actOnNewConversationDetailsReceived;
    Progress.conversationsReceived["Conversations"] = actOnConversations;
    Progress.syncMoveReceived["Conversations"] = actOnSyncMove;
    Progress.conversationDetailsReceived["Conversations"] = actOnConversationDetails;
    //    Progress.onConversationJoin["Conversations"] = refreshSlideDisplay;
    Progress.currentSlideJidReceived["Conversations"] = actOnCurrentSlideJidReceived;
    Progress.currentConversationJidReceived["Conversations"] = actOnCurrentConversationJidReceived;
    Progress.onLayoutUpdated["Conversations"] = paintThumbs;
    Progress.historyReceived["Conversations"] = paintThumbs;
    $(function(){
        $("#thumbScrollContainer").on("scroll",paintThumbs);
        $("#conversations").click(function(){
            showBackstage("conversations");
        });
        $("#createConversationButton").on("click",bounceAnd(function(){
            createConversation(sprintf("%s created on %s",UserSettings.getUsername(),Date()));
        }));
        $("#myConversationsButton").on("click",bounceAnd(function(){
            getSearchResult(UserSettings.getUsername());
        }));
        $("#searchButton").on("click",bounceAnd(function(){
            getSearchResult(currentSearchTerm);
        }));
        var updateSearchTerm = function(e){
            currentSearchTerm = this.value;
            if (e.which == 13){
                e.stopPropagation();
                getSearchResult(currentSearchTerm);
            }
        };
        var sfcb = $("#searchForConversationBox");
        _.forEach(["blur","change","focus","keydown","select"],function(item){
            sfcb.on(item,updateSearchTerm)
        });
        $("<div />",{
            text:"share",
            id:"shareButton",
            class:"icon-txt"
        }).on("click",bounceAnd(function(){
            $("#shareContainer").toggle();
            updateLinks();
        })).appendTo($("#shareButton"));
        $("#closeSharingButton").on("click", bounceAnd(function() {
            $("#shareContainer").toggle();
        }));
    });
    return {
        inConversation:function(){
            return Conversations.getCurrentConversationJid().length > 0;
        },
        isAuthor:function(){
            if(!Conversations.inConversation()){
                return false;
            }
            return UserSettings.getUsername() == Conversations.getCurrentConversation().author;
        },
        getCurrentTeacherSlide : function(){return currentTeacherSlide;},
        getCurrentSlideJid : function(){return currentSlide;},
        getCurrentSlide : function(){return _.find(currentConversation.slides,function(i){return i.id.toString() == currentSlide.toString();})},
        getCurrentConversationJid : function(){
            if ("jid" in currentConversation){
                return currentConversation.jid.toString();
            } else return targetConversationJid;
        },
        getCurrentConversation : function(){return currentConversation;},
        getIsSyncedToTeacher : function(){return isSyncedToTeacher;},
        getIsSyncedToTeacherDescriptor : getIsSyncedToTeacherDescriptorFunction,
        getConversationModeDescriptor : getConversationModeDescriptorFunction,
        enableSyncMove : enableSyncMoveFunction,
        disableSyncMove : disableSyncMoveFunction,
        toggleSyncMove : toggleSyncMoveFunction,
        setStudentsCanPublish : setStudentsCanPublishFunction,
        getStudentsCanPublish : getStudentsCanPublishFunction,
        setStudentsMustFollowTeacher : setStudentsMustFollowTeacherFunction,
        getStudentsMustFollowTeacher : getStudentsMustFollowTeacherFunction,
        shouldDisplayConversation : shouldDisplayConversationFunction,
        shouldPublishInConversation : shouldPublishInConversationFunction,
        shouldModifyConversation : shouldModifyConversationFunction,
        goToNextSlide : goToNextSlideFunction,
        goToPrevSlide : goToPrevSlideFunction,
        updateThumbnail :updateThumbnailFor,
				getIsBanned : getIsBannedFunction
    };
})();

function unwrap(jqs){
    return _.map(jqs,"0");
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
    if(Conversations.getIsSyncedToTeacher()){
        Progress.call("syncMoveReceived",[jid]);
    }
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
