var Conversations = (function(){
    var currentSearchTerm = "";
    var currentlyDisplayedConversations = [];
    var currentConversation = {};
    var currentServerConfigName = "external";
    var currentSlide = 0;
    var targetConversationJid = "";
    var currentTeacherSlide = 0;
    var isSyncedToTeacher = false;
    var currentGroup = [];

    var conversationTemplate = undefined;
    var conversationSearchListing = undefined;

    var BannedState = (function(){
        var haveCheckedBanned = false;
        var bannedState = false;
        var pushBannedMessage = function(){
            warningAlert("Banned","You have been banned from contributing publically to this class because you published some inappropriate content that is deemed to be contrary to the expectations of the university.\r\nThe content has been deleted on every screen, but the instructor has a record of your action.\r\nYou must contact your instructor in order to be reinstated as a contributing member of the classroom community.");
        };
        var pushUnbannedMessage = function(){
            successAlert("Unbanned","The instructor has unbanned you.  You are once again permitted to contribute publicly in this class.");
        };
        var updateBannedVisualState = function(){
        };
        return {
            checkIsBanned:function(conversation,freshCheck){
                var originalBannedState = bannedState;
                var newBannedState = getIsBannedFunction(conversation);
                if (freshCheck == true){
                    haveCheckedBanned = false;
                    bannedState = false;
                }
                if (!haveCheckedBanned && newBannedState){
                    haveCheckedBanned = true;
                    bannedState = true;
                    pushBannedMessage();
                }
                if (originalBannedState == true && newBannedState == false){
                    bannedState = false;
                    haveCheckedBanned = false;
                    pushUnbannedMessage();
                }
                updateBannedVisualState();
            },
            reset:function(){
                haveCheckedBanned = false;
                bannedState = false;
            }
        };
    })();
    $(function(){
        //take a template of the html for the searchResultItem
        conversationSearchListing = $("#searchResults");
        conversationTemplate = conversationSearchListing.find(".searchResultItem").clone();
        conversationSearchListing.empty();
    });
    var ThumbCache = (function(){
        var cacheRefreshTime = 0
        var cache = {};
        var groupActivity = {};
        var groupTraces = {};

        var ensureTracking = function(audience){
            if(!(audience in groupActivity)){
                groupActivity[audience] = {
                    bucket:0,
                    line:_.map(_.range(50),function(){return 0})
                }
            }
        }
        var audienceAction = function(audienceO){
            var audience = audienceO.name;
            ensureTracking(audience);
            groupActivity[audience].bucket += 1;
        }
        var rollAudiences = function(){
            _.each(groupActivity,function(meter){
                meter.line.pop();
                meter.line.unshift(meter.bucket);
                meter.bucket = 0;
            });
        }
        var SENSOR_INTERVAL = 500;
        var DISPLAY_INTERVAL = 2500;
        setInterval(rollAudiences,SENSOR_INTERVAL);
        setInterval(function(){
            _.each(currentConversation.slides,function(slide){
                if(slide.groupSet){
                    if(! _.every(slide.groupSet.groups,function(group){
                        return group.id in groupTraces && groupTraces[group.id].group.members.length == group.members.length;
                    })){
                        var scrollContainer = $("#thumbScrollContainer");
                        var slideContainer = scrollContainer.find(sprintf("#slideContainer_%s",slide.id));
                        paintGroups(slide,slideContainer);
                    }
                    _.each(slide.groupSet.groups,function(group){
                        var trace = groupTraces[group.id];
                        trace.update(groupActivity[group.id].line);
                    });
                }
                var conversationActivity = $("#conversationActivity");
                ensureTracking("anyone");
                groupTraces.anyone = groupTraces.anyone || {};
                if(conversationActivity.find("svg").length == 0){
                    groupTraces.anyone.update = SparkLine.svg(conversationActivity,groupActivity.anyone.line,100,15,1000,1000,SENSOR_INTERVAL,DISPLAY_INTERVAL);
                }
                groupTraces.anyone.update(groupActivity.anyone.line);
            });
        },DISPLAY_INTERVAL);
        Progress.stanzaReceived["thumbnailSparkline"] = function(stanza){
            _.each(stanza.audiences,audienceAction);
            audienceAction({name:"anyone"});
        }
        /*
         Workaround for parallel connection limits queueing thumbnail loads behind long poll
         */
        var fetchAndPaintThumb = function(slide,slideContainer,slideImage){
            if(slide.groupSet){
            }
            else{
                var thumbUrl = sprintf("/thumbnailDataUri/%s",slide.id);
                var storeThumb = function(data){
                    cache[slide.id] = {
                        data:data,
                        when:Date.now()
                    };
                    slideImage.attr("src",data);
                };
                $.ajax({
                    url:thumbUrl,
                    beforeSend: function ( xhr ) {
                        xhr.overrideMimeType("text/plain; charset=x-user-defined");
                    },
                    dataType: "text"
                }).done(storeThumb);
            }
        };
        var paintGroups = function(slide,slideContainer){
            console.log("Paint groups");
            var groupsContainer = slideContainer.find(".groupSlideContainer")
            if(groupsContainer.length == 0){
                groupsContainer = $("<div />").addClass("groupSlideContainer").appendTo(slideContainer);
                slideContainer
                    .addClass("groupSlide")
                    .find("img")
                    .attr("src",blank4to3Canvas);
            }
            _.each(slide.groupSet.groups,function(group){
                ensureTracking(group.id);
                var label = sprintf("group_%s",group.id);
                var groupContainer = groupsContainer.find("#"+label);
                if(groupContainer.length == 0){
                    groupContainer = $("<div />",{
                        id:label,
                        class:"thumbGroup"
                    }).append($("<span />",{
                        text:group.members.length,
                        class:"count"
                    })).appendTo(groupsContainer);
                    if(!(group.id in groupTraces)){
                        groupTraces[group.id] = {};
                    }
                    groupTraces[group.id].update = SparkLine.svg(groupContainer,groupActivity[group.id].line,80,15,1000,1000,SENSOR_INTERVAL,DISPLAY_INTERVAL);
                }
                groupTraces[group.id].group = group;
                groupContainer.find('.count').text(group.members.length);
            });
        }
        var blank4to3Canvas = (function(w,h){
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
        })(320,240);

        return {
            paint:function(slide,scrollContainer){
                if(slide){
                    scrollContainer = scrollContainer || $("#thumbScrollContainer");
                    var slideContainer = scrollContainer.find(sprintf("#slideContainer_%s",slide.id));
                    if(slide.groupSet){
                    }
                    else{
                        var slideImage = slideContainer.find("img");
                        if (slide.id in cache && cache[slide.id].when > (Date.now() - cacheRefreshTime)){
                            slideImage.attr("src",cache[slide.id].data);
                        } else {
                            fetchAndPaintThumb(slide,slideContainer,slideImage);
                        }
                    }
                }
            },
            clearCache:function(){
                cache = {};
                groupActivity = {};
                groupTraces = {};
            }
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
    var refreshSlideDisplay = function(){
        console.log("Refreshing slide display");
        var slideContainer = $("#slideContainer")
        //Add the new slides
        _.each(_.filter(currentConversation.slides,function(slide){
            return $(sprintf("#slideContainer_%s",slide.id)).length == 0;
        }),function(slide){
            slideContainer.append(constructSlide(slide)[0]);
            updateThumbnailFor(slide.id);
        });
        //Remove the deleted slides
        _.each(_.filter(currentConversation.slides,function(slide){
            var keep = _.map(currentConversation.slides,constructSlideId);
            $(".slideButtonContainer").filter(function(i,el){
                var id = $(el).attr("id");
                return ! _.includes(keep,id);
            }).remove();
        }));
        //Apply the new index positions
        var positions = _.fromPairs(_.map(currentConversation.slides,function(slide){
            return [constructSlideId(slide),slide.index];
        }));
        $(".slideButtonContainer").sort(function(ja,jb){
            var ia = $(ja).attr("id");
            var ib = $(jb).attr("id");
            return positions[ia] - positions[ib];
        }).detach().appendTo(slideContainer);
        //Build the UI
        var slideControls = $("#slideControls");
        slideControls.empty();
        constructPrevSlideButton(slideControls),
        constructNextSlideButton(slideControls),
        constructAddSlideButton(slideControls)
        constructAddGroupSlideButton(slideControls)
        indicateActiveSlide(currentSlide);
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
    var loadCurrentGroup = function(details){
        var currentSlideDetails = _.find(details.slides,function(slide){
            return slide.id == currentSlide;
        });
        if(currentSlideDetails){
            currentGroup = [];
            var groupSet = currentSlideDetails.groupSet;
            if(groupSet){
                _.each(groupSet.groups,function(group){
                    if(_.includes(group.members,UserSettings.getUsername())){
                        currentGroup.push(group.id);
                    }
                });
            }
        }
    }
    var actOnConversationDetails = function(details){
        try{
            var oldConversationJid = "";
            if ("jid" in currentConversation){
                oldConversationJid = currentConversation.jid.toString().toLowerCase();
            };
            if ("jid" in details && targetConversationJid && details.jid.toString().toLowerCase() == targetConversationJid.toLowerCase()){
                if (shouldDisplayConversationFunction(details)){
                    currentConversation = details;
                    if ("configName" in details){
                        currentServerConfigName = details.configName;
                    }
                    if (currentConversation.jid.toString().toLowerCase() != oldConversationJid){
                        Progress.call("onConversationJoin");
                        BannedState.checkIsBanned(details,true);
                        ThumbCache.clearCache();
                    }
                    loadCurrentGroup(details);
                }
                else {
                    currentConversation = {};
                    targetConversationJid = "";
                }
            }
            updateCurrentConversation(details);
            BannedState.checkIsBanned(details);
            if (!(_.some(currentlyDisplayedConversations,function(c){return c.jid == details.jid;})) && shouldModifyConversationFunction(details)){
                currentlyDisplayedConversations.push(details);
                refreshConversationSearchResults();
            }
        }
        catch(e){
            console.log("exception in actOnConversationDetails",e);
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
                    doMoveToSlide(jid);
                }
            }
        }
    };
    var updateThumbnailFor = function(slideId) {
        var slide = _.find(currentConversation.slides, ['id',parseInt(slideId)]);
        ThumbCache.paint(slide);
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
            $("#conversationAnalysis").empty();
            $("#oneNoteExport").empty();
        } else {
            $("#projectorViewLink").html($("<a/>",{
                href:sprintf("/board?conversationJid=%s&slideId=%s&showTools=false&unique=true",targetConversationJid,currentSlide),
                text:"Project this conversation"
            }));
            $("#conversationAnalysis").html($("<a/>",{
                href:sprintf("/dashboard?source=%s",targetConversationJid),
                text:"Dashboard for this conversation"
            }));
            $("#slideDeepLink").html($("<a/>",{
                href:sprintf("/board?conversationJid=%s&slideId=%s&unique=true",targetConversationJid,currentSlide),
                text:"DeepLink this slide"
            }));
            $("#conversationDeepLink").html($("<a/>",{
                href:sprintf("/board?conversationJid=%s&unique=true",targetConversationJid),
                text:"Deeplink this conversation"
            }));
            $("#oneNoteExport").html($("<a/>",{
                href:sprintf("/saveToOneNote/%s",targetConversationJid),
                text:"Export this conversation"
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
            $(".syncCheckbox").hide();
        } else {
            $("#syncButtons").show();
            $(".syncCheckbox").show();
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
        if (!("jid" in conversation)){
            return false;
        }
        if ("author" in conversation && conversation.author.toLowerCase() == UserSettings.getUsername().toLowerCase() || ("UserSettings" in window && _.some(UserSettings.getUserGroups(),function(g){
            var key = g.key ? g.key : g.ouType;
            var name = g.name ? g.name : g.value;
            return (key == "special" && name == "superuser");
        }))){
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
        var subject = "subject" in conversation ? conversation.subject.toLowerCase().trim() : "nosubject";
        if ("subject" in conversation && subject != "deleted" && (("author" in conversation && conversation.author == UserSettings.getUsername()) || _.some(UserSettings.getUserGroups(), function(g){
            var key = g.key ? g.key : g.ouType;
            var name = g.name ? g.name : g.value;
            return (key == "special" && name == "superuser") || name.toLowerCase().trim() == subject;
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
            }),function(conv){return new Date(conv.creation);}).reverse().map(constructConversation);
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
    var constructSlideButton = function(name,label,icon,teacherOnlyFunction,container){
        if (teacherOnlyFunction(currentConversation)){
            container.append($("<button/>",{
                id: name,
                class:sprintf("toolbar fa %s btn-icon nmt",icon),
                name: name,
                type: "button"
            }).append($("<div class='icon-txt' />",{
                text:label
            })));
        }
    }
    var addGroupSlideFunction = function(){
        if(shouldModifyConversationFunction()){
            var currentJid = currentConversation.jid;
            var currentSlideIndex = currentConversation.slides.filter(function(slide){return slide.id == currentSlide;})[0].index;
            var newIndex = currentSlideIndex + 1;
            addGroupSlideToConversationAtIndex(currentConversation.jid.toString(),newIndex);
            Progress.conversationDetailsReceived["JoinAtIndexIfAvailable"] = function(incomingDetails){
                if ("jid" in incomingDetails && incomingDetails.jid == currentJid){
                    if ("slides" in incomingDetails){
                        var newSlide = _.find(incomingDetails.slides,function(s){
                            return s.index == newIndex && s.id != currentSlide;
                        });
                        setStudentsMustFollowTeacherFunction(true);
                        doMoveToSlide(newSlide.id.toString());
                    }
                }
            };
        }
    };
    var addSlideFunction = function(){
        if(shouldModifyConversationFunction()){
            var currentJid = currentConversation.jid;
            var currentSlideIndex = currentConversation.slides.filter(function(slide){return slide.id == currentSlide;})[0].index;
            var newIndex = currentSlideIndex + 1;
            addSlideToConversationAtIndex(currentConversation.jid.toString(),newIndex);
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
        }
    };
    var always = function(){return true;}
    var constructPrevSlideButton = function(container){
        constructSlideButton("prevSlideButton","Prev Slide","fa-angle-left",always,container);
    }
    var constructAddSlideButton = function(container){
        constructSlideButton("addSlideButton","Add Slide","fa-plus",shouldModifyConversationFunction,container);
    }
    var constructAddGroupSlideButton = function(container){
        constructSlideButton("addGroupSlideButton","Add Group Slide","fa-group",shouldModifyConversationFunction,container);
    }
    var constructNextSlideButton = function(container){
        constructSlideButton("nextSlideButton","Next Slide","fa-angle-right",always,container);
    }
    var getCurrentSlideFunc = function(){return _.find(currentConversation.slides,function(i){return i.id.toString() == currentSlide.toString();})};
    var updateQueryParams = function(){
        if (window != undefined && "history" in window && "pushState" in window.history){
            var l = window.location;
            var c = currentConversation;
            var s = getCurrentSlideFunc();
            var newUrl = sprintf("%s//%s%s",l.protocol,l.host,l.pathname);
            if (c != undefined && "jid" in c && s != undefined && "id" in s){
                newUrl = sprintf("%s?conversationJid=%s&slideId=%s&unique=true&showTools=%s",newUrl,c.jid.toString(),s.id.toString(),UserSettings.getIsInteractive().toString());
            }
            window.history.replaceState({
                path:newUrl,
                url:newUrl
            },newUrl,newUrl);
        }
        if (s != undefined && "id" in s && document != undefined && "title" in document){
            document.title = sprintf("MeTL - %s",s.id.toString());
        }
    };
    var doMoveToSlide = function(slideId){
        console.log("doMoveToSlide",slideId);
        var move = false;
        if(Conversations.isAuthor()){
            move = true;
        }
        else if(getStudentsMustFollowTeacherFunction()){
            if(slideId == currentTeacherSlide){
                move = true;
            }
        }
        else {
            move = true;
        }
        if(move){
            if(slideId != currentSlide){
                Progress.call("beforeLeavingSlide",[slideId]);
                currentSlide = slideId;
                indicateActiveSlide(slideId);
                delete Progress.conversationDetailsReceived["JoinAtIndexIfAvailable"];
                loadSlide(slideId);
                updateQueryParams();
                loadCurrentGroup(currentConversation);
                Progress.call("afterJoiningSlide",[slideId]);
            }
        }
        else{
            alert("You must remain on the current page");
        }
    };
    var indicateActiveSlide = function(slideId){
        $(".slideButtonContainer").removeClass("activeSlide");
        var activeSlide = $(sprintf("#slideContainer_%s",slideId));
        activeSlide.addClass("activeSlide");
        var position = activeSlide.find(".slideThumbnailNumber").text();
        $("#currentSlide").text(position);
    };
    var constructSlideId = function(slide){
        return sprintf("slideContainer_%s",slide.id);
    }
    var constructSlide = function(slide){
        var slideIndex = slide.index + 1;
        var newSlide = $("<div/>",{
            id: constructSlideId(slide),
            class:"slideButtonContainer"
        });
        $("<img/>",{
            id: sprintf("slideButton_%s",slide.id),
            class:"thumbnail",
            alt:sprintf("Slide %s",slideIndex),
            title:sprintf("Slide %s (%s)",slideIndex,slide.id)
        }).on("click",function(e){
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
            BannedState.reset();
            hideBackstage();
            Progress.call("onConversationJoin",[conversation]);
            doMoveToSlide(firstSlide.id.toString());
        }));
        var row1 = newConv.find(".searchResultTopRow");
        var row2 = newConv.find(".searchResultMiddleRow");
        var row3 = newConv.find(".teacherConversationTools");
        row3.attr("id",uniq("extraConversationTools"));
        newConv.find(".conversationTitle").attr("id",uniq("conversationTitle")).text(conversation.title);
        newConv.find(".conversationAuthor").text(conversation.author);
        newConv.find(".conversationSubject").text(conversation.subject);
        newConv.find(".conversationCreated").text(conversation.created);

        if (shouldModifyConversationFunction(conversation)){
            newConv.find(".conversationEditButton").attr("id",uniq("conversationEditLink")).attr("href",sprintf("/editConversation?conversationJid=%s",conversation.jid));
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
    var getCurrentGroupFunction = function(){
        return _.clone(currentGroup);
    };
    Progress.newConversationDetailsReceived["Conversations"] = actOnNewConversationDetailsReceived;
    Progress.conversationsReceived["Conversations"] = actOnConversations;
    Progress.syncMoveReceived["Conversations"] = actOnSyncMove;
    Progress.conversationDetailsReceived["Conversations"] = actOnConversationDetails;
    Progress.currentSlideJidReceived["Conversations"] = actOnCurrentSlideJidReceived;
    Progress.currentConversationJidReceived["Conversations"] = actOnCurrentConversationJidReceived;
    $(function(){
        $("#slideControls").on("click","#prevSlideButton",goToPrevSlideFunction)
            .on("click","#nextSlideButton",goToNextSlideFunction)
            .on("click","#addGroupSlideButton",addGroupSlideFunction)
            .on("click","#addSlideButton",addSlideFunction);
        $("#conversations").click(function(){
            showBackstage("conversations");
        });
        $("#importConversationButton").on("click",bounceAnd(function(){
            importConversation();
        }));
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
        getCurrentGroup : getCurrentGroupFunction,
        getCurrentTeacherSlide : function(){return currentTeacherSlide;},
        getCurrentSlideJid : function(){return currentSlide;},
        getCurrentSlide : getCurrentSlideFunc,
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

function updateThumb(jid){
    console.log(".updateThumb",jid)
    Conversations.updateThumbnail(jid);
}

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
    console.log("receiveConversationDetails");
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
function receiveAttendance(attendances){
    Progress.call("attendanceReceived",[attendances])
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
