var Conversations = (function(){
    var currentConversation = {};
    var currentServerConfigName = "external";
    var currentSlide = 0;
    var targetConversationJid = "";
    var currentTeacherSlide = 0;
    var isSyncedToTeacher = false;
    var currentGroup = [];

    var BannedState = (function(){
        var haveCheckedBanned = false;
        var bannedState = false;
        var pushBannedMessage = function(){
            Progress.call("userBanned");
            warningAlert("Banned","You have been banned from contributing publically to this class because you published some inappropriate content that is deemed to be contrary to the expectations of the university.\r\nThe content has been deleted on every screen, but the instructor has a record of your action.\r\nYou must contact your instructor in order to be reinstated as a contributing member of the classroom community.");
        };
        var pushUnbannedMessage = function(){
            Progress.call("userUnbanned");
            successAlert("Unbanned","The instructor has unbanned you.  You are once again permitted to contribute publicly in this class.");
        };
        var updateBannedVisualState = function(){
            $("#publicMode").prop("disabled",bannedState).toggleClass("btn-raised disabled",bannedState);
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
                return bannedState;
            },
            reset:function(){
                haveCheckedBanned = false;
                bannedState = false;
            }
        };
    })();
    var ThumbCache = (function(){
        var cacheRefreshTime = 0;
        var cache = {};
        var groupActivity = {};
        var groupTraces = {};

        var ensureTracking = function(audience){
            if(!(audience in groupActivity)){
                groupActivity[audience] = {
                    bucket:0,
                    line:_.map(_.range(SIGNAL_HISTORY),function(){return 0})
                }
            }
        };
        var audienceAction = function(audienceO){
            var audience = audienceO.name;
            ensureTracking(audience);
            groupActivity[audience].bucket += 1;
        };
        var rollAudience = function(meter){
            meter.line.pop();
            meter.line.unshift(meter.bucket);
            meter.bucket = 0;
        };
        var rollAudiences = function(){
            _.each(groupActivity,rollAudience);
        };
        var conversationActivity;
        var displayAudiences = function(){
            ensureTracking("anyPrivate");
            ensureTracking("anyPublic");
            groupTraces.anyPrivate = groupTraces.anyPrivate || {};
            groupTraces.anyPublic = groupTraces.anyPublic || {};
            if(!conversationActivity || !conversationActivity.length){
                conversationActivity = $("#conversationActivity");
            }
            if( WorkQueue != undefined ) {
                WorkQueue.enqueue(function () {
                    _.each(currentConversation.slides, updateSlide);
                    if (conversationActivity.find("svg").length == 0) {
                        groupTraces.anyPublic.update = SparkLine.svg(conversationActivity,
                            [groupActivity.anyPublic.line,
                                groupActivity.anyPrivate.line], 50, 26, 1000, 1000, SENSOR_INTERVAL, DISPLAY_INTERVAL);
                    }
                    if (groupTraces && "anyPublic" in groupTraces && "update" in groupTraces.anyPublic) {
                        groupTraces.anyPublic.update([
                            groupActivity.anyPublic.line,
                            groupActivity.anyPrivate.line
                        ]);
                    }
                });
            }
        };
        var groupTraceIsAccurate = function(group){
            return group.id in groupTraces && groupTraces[group.id].group.members.length == group.members.length;
        };
        var scrollContainer;
        var updateSlide = function(slide){
            var gs = Conversations.getGroupsFor(slide);
            scrollContainer = scrollContainer || $("#thumbScrollContainer");
            var slideContainer = scrollContainer.find(sprintf("#slideContainer_%s",slide.id));
            if(slideContainer){
                slideContainer.find(".slideThumbnailNumber").text(slideLabel(slide));
            }
            if(! _.every(gs,groupTraceIsAccurate)){
                paintGroups(slide,slideContainer);
            }
            _.each(gs,function(group){
                var trace = groupTraces[group.id];
                if(trace){//It won't exist if they haven't painted yet
                    trace.update([groupActivity[group.id].line]);
                }
            });
        };
        var SENSOR_INTERVAL = 500;
        var DISPLAY_INTERVAL = 1000;
        var SIGNAL_HISTORY = (1000 /*milis*/ / SENSOR_INTERVAL) * 60 * 15;
        setInterval(rollAudiences,SENSOR_INTERVAL);
        setInterval(displayAudiences,DISPLAY_INTERVAL);
        Progress.stanzaReceived["thumbnailSparkline"] = function(stanza){
            if(stanza.type == "theme"){
                switch(stanza.author){
                case "private":audienceAction({name:"anyPrivate"});
                    break;
                case "public":audienceAction({name:"anyPublic"});
                    break;
                }
                audienceAction({name:stanza.author});
            }
            _.each(stanza.audiences,audienceAction);
        };
        /*
         Workaround for parallel connection limits queueing thumbnail loads behind long poll
         */
        var fetchAndPaintThumb = function(slide,slideContainer,slideImage){
            if(slide.groupSets.length){
            }
            else{
                var thumbUrl = sprintf("/thumbnailDataUri/%s",slide.id);
                var storeThumb = function(data){
                    cache[slide.id] = {
                        data:data,
                        when:Date.now()
                    };
                    if( WorkQueue != undefined ) {
                        WorkQueue.enqueue(function () {
                            slideImage.attr("src", data);
                        });
                    }
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
            var groupsContainer = slideContainer.find(".groupSlideContainer");
            if(groupsContainer.length == 0){
                groupsContainer = $("<div />").addClass("groupSlideContainer").appendTo(slideContainer);
                slideContainer
                    .addClass("groupSlide")
                    .find("img")
                    .attr("src",blank4to3Canvas);
            }
            _.each(Conversations.getCurrentGroups(),function(group){
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
                    groupTraces[group.id].update = SparkLine.svg(groupContainer,[groupActivity[group.id].line],80,15,1000,1000,SENSOR_INTERVAL,DISPLAY_INTERVAL);
                }
                groupTraces[group.id].group = group;
                groupContainer.find('.count').text(group.members.length);
            });
        };
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
                    if(slide.groupSets.length){
                    }
                    else{
                        var slideImage = slideContainer.find("img");
                        fetchAndPaintThumb(slide,slideContainer,slideImage);
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
    };
    var doIfVisible = function(slide,thumbScroller,func){
        var slidesTop = 0;
        var slidesBottom = thumbScroller.height();
        var slideContainer = thumbScroller.find(sprintf("#slideContainer_%s",slide.id));
        var slideImage = slideContainer.find("img");
        var slideTop = slideContainer.position().top;
        var slideBottom = slideTop + slideContainer.height();
        var visible =  (slideBottom >= slidesTop) && (slideTop <= slidesBottom);
        if(slidesTop + slidesBottom + slideTop + slideBottom == 0){
            _.delay(function(){
                doIfVisible(slide,thumbScroller,func);
            },1000);
            visible = false;
        }
        else if(visible){
            func();
        }
    };
    var refreshSlideDisplay = function(){
        if ("slides" in currentConversation) {
            var slideContainer = $("#slideContainer");
            var scrollContainer = $("#thumbScrollContainer");
            var ss = _.filter(currentConversation.slides, "exposed");
            //Add the new slides
            _.each(_.filter(ss, function (slide) {
                return $(sprintf("#slideContainer_%s", slide.id)).length == 0;
            }), function (slide) {
                slideContainer.append(constructSlide(slide)[0]);
            });
            //Remove the deleted slides
            _.each(_.filter(ss, function (slide) {
                var keep = _.map(ss, constructSlideId);
                $(".slideButtonContainer").filter(function (i, el) {
                    var id = $(el).attr("id");
                    return !_.includes(keep, id);
                }).remove();
            }));
            //Apply the new index positions
            var positions = _.fromPairs(_.map(currentConversation.slides, function (slide) {
                return [constructSlideId(slide), slide.index];
            }));
            $(".slideButtonContainer").sort(function (ja, jb) {
                var ia = $(ja).attr("id");
                var ib = $(jb).attr("id");
                return positions[ia] - positions[ib];
            }).detach().appendTo(slideContainer);
            _.each(ss, function (slide) {
                doIfVisible(slide, scrollContainer, function () {
                    updateThumbnailFor(slide.id);
                });
            });
            //Build the UI
            var cs = _.find(ss, function (s) {
                return s.id == currentSlide
            });
            var minIndex = _.minBy(ss, function (s) {
                return s.index;
            }).index;
            var maxIndex = _.maxBy(ss, function (s) {
                return s.index;
            }).index;
            var slideControls = $("#slideControls");
            slideControls.empty();
            // console.log("slides:", ss, cs, minIndex, maxIndex);
            constructPrevSlideButton(slideControls,cs.index,minIndex);
//            slideControls.append($('<span/>').addClass("pageCounter").text(sprintf("%s/%s",cs.index + 1,maxIndex + 1)));
            var column = $('<span/>').attr('id',"pageCountContainer").appendTo(slideControls);
            column.append($('<div/>').addClass("pageCounter").text(cs.index + 1));
            column.append($('<div/>').addClass("pageCounter").text("of"));
            column.append($('<div/>').addClass("pageCounter").text(maxIndex + 1));
            constructNextSlideButton(slideControls,cs.index,maxIndex);
            constructAddSlideButton(slideControls);
            constructAddGroupSlideButton(slideControls);
            constructHelpButton(slideControls);
            indicateActiveSlide(currentSlide);
            $(".thumbnail:not(.groupSlide)").map(function () {
                var t = $(this);
                if (t.width() <= 0) {
                    t.width(DeviceConfiguration.preferredSizes.thumbColumn.width);
                    t.css({width: sprintf("%spx", t.width())});
                }
                t.height(t.width() * 0.75);
            });
            Progress.call("onLayoutUpdated");
        }
    };

    var setStudentsCanPublishFunction = function(publishingAllowed){
        var jid = currentConversation.jid.toString();
        var oldPerms = currentConversation.permissions;
        var newPermissions = {
            "studentCanOpenFriends":oldPerms.studentCanOpenFriends,
            "studentCanPublish":publishingAllowed,
            "usersAreCompulsorilySynced":oldPerms.usersAreCompulsorilySynced,
            "studentsMayBroadcast":oldPerms.studentsMayBroadcast,
            "studentsMayChatPublicly":oldPerms.studentsMayChatPublicly
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
            "usersAreCompulsorilySynced":mustFollowTeacher,
            "studentsMayBroadcast":oldPerms.studentsMayBroadcast,
            "studentsMayChatPublicly":oldPerms.studentsMayChatPublicly
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
    };
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
            _.each(Conversations.getCurrentGroups(),function(group){
                if(_.includes(group.members,UserSettings.getUsername())){
                    currentGroup.push(group);
                }
            });
        }
    };
    var actOnConversationDetails = function(details){
        try{
            console.log("received conversation:",details);
            var oldConversationJid = "";
            if ("jid" in currentConversation){
                oldConversationJid = currentConversation.jid.toString().toLowerCase();
            }
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
        }
        catch(e){
            console.log("exception in actOnConversationDetails",e);
            updateStatus(sprintf("FAILED: ReceiveConversationDetails exception: %s",e));
        }
        Progress.call("onLayoutUpdated");
    };
    var actOnSyncMove = function(jid){
        console.log("actOn",jid);
        if ((Conversations.getIsSyncedToTeacher() || shouldModifyConversationFunction(currentConversation)) || (!UserSettings.getIsInteractive())){
            if ("slides" in currentConversation && currentConversation.slides.filter(function(slide){return slide.id.toString() == jid.toString();}).length > 0){
                if( WorkQueue != undefined ) {
                    WorkQueue.enqueue(function () {
                        if ("slides" in currentConversation && currentConversation.slides.filter(function (slide) {
                                return slide.id.toString() == jid.toString();
                            }).length > 0) {
                            currentTeacherSlide = jid;
                            doMoveToSlide(jid, true);
                        }
                        return false;
                    });
                }
            }
        }
    };
    var updateThumbnailFor = function(slideId) {
        var slide = _.find(currentConversation.slides, ['id',parseInt(slideId)]);
        ThumbCache.paint(slide);
    };
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
    var actOnCurrentConversationJidReceived = function(jid){
        console.log("currentConversationJid received:",jid);
        targetConversationJid = jid;
        updateLinks();
    };
    var actOnCurrentSlideJidReceived = function(jid){
        console.log("currentSlideJid received:",jid);
        currentSlide = jid;
        indicateActiveSlide(jid);
        updateLinks();
        loadCurrentGroup(currentConversation);
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
                text:"DeepLink this page"
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
        if (Conversations.shouldModifyConversation()){
            $("#editConversation").unbind("click").click(function(){
                $.jAlert({
                    title:"Edit conversation",
                    iframe:sprintf("/editConversation?conversationJid=%s&unique=true&links=false", targetConversationJid),
                    width:"100%"
                });
            }).show();
        } else {
            $("#editConversation").unbind("click").hide();
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
        ftc.prop("disabled", details.permissions.usersAreCompulsorilySynced);
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
            var myLocation = _.find(details.slides,function(s){ return s.id == currentSlide; });
            var teacherLocation = _.find(details.slides,function(s){ return sprintf("%s",s.id) == currentTeacherSlide; });
            var relocate = false;
            if(myLocation){
                relocate = !myLocation.exposed;
            }
            else {
                relocate = true;
            }
            if(relocate){
                var possibleLocation = teacherLocation || _.find(_.orderBy(details.slides,'index'),function(s){ return s.exposed; });
                if (possibleLocation){
                    doMoveToSlide(possibleLocation.id.toString());
                } else {
                    window.location = sprintf("/conversationSearch?q=%s&unique=true",encodeURIComponent(details.title)); // redirect to the conversation search
                }
            }
            if (shouldRefreshSlideDisplay(details)){
                refreshSlideDisplay();
            }
            Progress.call("onLayoutUpdated");
        }
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
		var shouldDisplayConversationFunction = function(details){
			if (!details){
				details = currentConversation;
			}
			var userGroups = UserSettings.getUserGroups();
			var username = UserSettings.getUsername();
        var subject = details.subject.toLowerCase().trim();
        var title = details.title.toLowerCase().trim();
        var author = details.author;
				var cfr = details.foreignRelationship;
        return ((subject != "deleted" || (includeDeleted && author == username)) && (author == username || _.some(userGroups,function(g){
						var fr = g.foreignRelationship;
            var key = g.key ? g.key : g.ouType;
            var name = g.name ? g.name : g.value;
						var matches = key == "special" && name == "superuser";
						if (!matches){
							if (cfr !== undefined && "key" in cfr && "system" in cfr && fr !== undefined){
								matches = cfr.key == fr.key && cfr.system == fr.system;
							}
						}
						if (!matches){
							matches = name.toLowerCase().trim() == subject;
						}
						return matches;
        })));
    };
    var shouldPublishInConversationFunction = function(conversation){
        if (!conversation){
            conversation = currentConversation;
        }
        if(shouldModifyConversationFunction(conversation) || ("permissions" in conversation && "studentCanPublish" in conversation.permissions && conversation.permissions.studentCanPublish) && !getIsBannedFunction(conversation)){
            return true;
        } else {
            return false;
        }
    };
    var constructSlideButton = function(name,label,icon,teacherOnlyFunction,container,clickHandler,shouldAttachClickHandler){
        if (teacherOnlyFunction(currentConversation)){
            var button = $("<button/>",{
                 id: name,
                 class:sprintf("toolbar fa %s btn-icon nmt",icon),
                 name: name,
                 type: "button"
             }).append($("<div class='icon-txt'/>").text(label));
             container.append(button);
             if( clickHandler !== undefined && (shouldAttachClickHandler == undefined || _.isFunction(shouldAttachClickHandler) && shouldAttachClickHandler())) {
                 button.on("click", clickHandler)
             }
             return button;
        }
    };
    var overrideAllocationFunction = function(slide){
        if(shouldModifyConversationFunction()){
            overrideAllocation(Conversations.getCurrentConversationJid(),slide);
        }
    };
    var addGroupSlideFunction = function(strategy,parameter,initialGroups){
        if(shouldModifyConversationFunction()){
            initialGroups = initialGroups || [];
            var currentJid = currentConversation.jid;
            var currentSlideIndex = currentConversation.slides.filter(function(slide){return slide.id == currentSlide;})[0].index;
            var newIndex = currentSlideIndex + 1;
            addGroupSlideToConversationAtIndex(currentConversation.jid.toString(),newIndex,strategy,initialGroups,parameter);
            Progress.conversationDetailsReceived["JoinAtIndexIfAvailable"] = function(incomingDetails){
                if ("jid" in incomingDetails && incomingDetails.jid == currentJid){
                    if ("slides" in incomingDetails){
                        var newSlide = _.find(incomingDetails.slides,function(s){
                            return s.index == newIndex && s.id != currentSlide;
                        });
                        var linkedGradeLoc = sprintf("groupWork_%s",newSlide.id);
                        var user = UserSettings.getUsername();
                        var newLinkedGrade = {
                            type:"grade",
                            name:sprintf("GroupSlide %s",newSlide.id),
                            description:"Auto generated grade for group work on group slide.",
                            audiences:[],
                            author:user,
                            location:linkedGradeLoc,
                            id:sprintf("%s_%s_%s",linkedGradeLoc,user,new Date().getTime().toString()),
                            gradeType:"text",
                            visible:false,
                            timestamp:0
                        };
                        sendStanza(newLinkedGrade);
                        setStudentsMustFollowTeacherFunction(true);
                        doMoveToSlide(newSlide.id.toString());
                    }
                }
            };
        }
    };
    var helpFunction = function(){
        updateConversationHeader();
        showBackstage("help");
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
    var always = function(){return true;};
    var constructPrevSlideButton = function(container,csIndex,minIndex){
        var prevButton = constructSlideButton("prevSlideButton","Prev Page","fa-angle-left",always,container,goToPrevSlideFunction,function(){
          return csIndex !== minIndex;
        });
        if (csIndex === minIndex) {
            prevButton.addClass('disabledButton');
            prevButton.attr("disabled", true);
        }
    };
    var constructAddSlideButton = function(container){
        constructSlideButton("addSlideButton","Add Page","fa-plus",shouldModifyConversationFunction,container,addSlideFunction,function(){
          return shouldModifyConversationFunction(currentConversation);
        });
    };
    var constructAddGroupSlideButton = function(container){
        constructSlideButton("addGroupSlideButton","Groups","fa-group",shouldModifyConversationFunction,container,GroupBuilder.showAddGroupSlideDialog,function(){
          return shouldModifyConversationFunction(currentConversation);
        });
    };
    var constructHelpButton = function(container){
        constructSlideButton("helpButton","Help","fa-question-circle",always,container,helpFunction);
    };
    var constructNextSlideButton = function(container,csIndex,maxIndex){
        var nextButton = constructSlideButton("nextSlideButton","Next Page","fa-angle-right",always,container,goToNextSlideFunction,function(){
          return csIndex !== maxIndex;
        });
        if (csIndex === maxIndex) {
            nextButton.addClass('disabledButton');
            nextButton.attr("disabled", true);
        }
    };
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
    var doMoveToSlide = function(slideId,suppressSyncMove){
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
                try{
                    Progress.call("beforeLeavingSlide",[slideId]);
                    currentSlide = slideId;
                    indicateActiveSlide(slideId);
                    delete Progress.conversationDetailsReceived["JoinAtIndexIfAvailable"];
                    loadSlide(slideId);
                    updateQueryParams();
                    loadCurrentGroup(currentConversation);
                    Progress.call("afterJoiningSlide",[slideId]);
                }
                catch(e){
                    console.log(e);
                }
            } else if (Conversations.isAuthor() && !suppressSyncMove){
                var newStanza = {
                    type:"command",
                    author:UserSettings.getUsername(),
                    audiences:[],
                    timestamp:0,
                    command:"/SYNC_MOVE",
                    parameters:[slideId]
                };
                console.log("sending:",newStanza);
                sendStanza(newStanza);
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
    };
    var slideLabel = function(slide){
        return slide.index+1;
    };
    var constructSlide = function(slide){
        var slideIndex = slide.index + 1;
        var newSlide = $("<div/>",{
            id: constructSlideId(slide),
            class:"slideButtonContainer"
        });
        $("<img/>",{
            id: sprintf("slideButton_%s",slide.id),
            class:"thumbnail",
            alt:sprintf("Page %s",slideIndex),
            title:sprintf("Page %s (%s)",slideIndex,slide.id)
        }).on("click",function(e){
            disableSyncMoveFunction();
            doMoveToSlide(slide.id.toString());
        }).appendTo(newSlide);
        $("<span/>",{
            text: slideLabel(slide),
            class: "slideThumbnailNumber"
        }).appendTo($("<div/>").addClass("slide-count").appendTo(newSlide));
        return newSlide;
    };
    var getCurrentGroupFunction = function(){
        return _.clone(currentGroup);
    };
    Progress.syncMoveReceived["Conversations"] = actOnSyncMove;
    Progress.conversationDetailsReceived["Conversations"] = actOnConversationDetails;
    Progress.currentSlideJidReceived["Conversations"] = actOnCurrentSlideJidReceived;
    Progress.currentConversationJidReceived["Conversations"] = actOnCurrentConversationJidReceived;
    $(function(){
        $("#thumbScrollContainer").on("scroll",_.throttle(refreshSlideDisplay,500));
        $("#conversations").click(function(){
            showBackstage("conversations");
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
        refreshSlideDisplay();
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
        getGroupsFor : function(slide){
            return slide ? _.map(_.sortBy(_.flatMap(slide.groupSets,function(gs){return gs.groups}),'timestamp'),function(v,k){
                v.title = k + 1;
                return v;
            }) : [];
        },
        getCurrentGroups : function(){
            return Conversations.getGroupsFor(getCurrentSlideFunc());
        },
        addGroupSlide : addGroupSlideFunction,
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
        overrideAllocation : overrideAllocationFunction,
        goToNextSlide : goToNextSlideFunction,
        goToPrevSlide : goToPrevSlideFunction,
        goToSlide : doMoveToSlide,
        updateThumbnail :updateThumbnailFor,
        getIsBanned : getIsBannedFunction,
        refreshSlideDisplay : refreshSlideDisplay
    };
})();

function updateThumb(jid){
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
    Progress.call("conversationDetailsReceived",[details]);
}
function receiveSyncMove(jid){
    if(Conversations.getIsSyncedToTeacher() || Conversations.shouldModifyConversation()){
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
function receiveGroupsProviders(providers){
    Progress.call("groupProvidersReceived",[providers]);
}
function receiveOrgUnitsFromGroupsProviders(orgUnits){
    Progress.call("orgUnitsReceived",[orgUnits]);
    if ("orgUnits" in orgUnits && orgUnits.orgUnits.length){
        _.forEach(orgUnits.orgUnits,function(orgUnit){
            var ou = _.cloneDeep(orgUnit);
            delete ou.groupSets;
            delete ou.members;
            getGroupSetsForOrgUnit(orgUnits.groupsProvider.storeId,ou,"async");
        });
    }
}
function receiveGroupSetsForOrgUnit(groupSets){
    Progress.call("groupSetsReceived",[groupSets]);
    if ("groupSets" in groupSets && groupSets.groupSets.length){
        _.forEach(groupSets.groupSets,function(groupSet){
            if ("groups" in groupSet && groupSet.groups.length){
                receiveGroupsForGroupSet({
                    orgUnit:groupSets.orgUnit,
                    groupSet:groupSet,
                    groups:groupSet.groups
                });
            } else {
                var ou = _.cloneDeep(groupSets.orgUnit);
                delete ou.members;
                delete ou.groupSets;
                var gs = _.cloneDeep(groupSet);
                delete gs.members;
                delete gs.groups;
                getGroupsForGroupSet(groupSets.groupsProvider.storeId,ou,gs,"async");
            }
        });
    }
}
function receiveGroupsForGroupSet(groups){
    Progress.call("groupsReceived",[groups]);
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
//function changePermissionsOfConversation(jid,newPermissions)
//function changeSubject(jid,newSubject)
//function addSlide(jid,indexOfNewSlide)
//function reorderSlides(jid,alteredSlides)
//function getGroupsProviders()
//function getOrgUnitsFromGroupProviders(storeId)
//function getGroupSetsForOrgUnit(storeId,orgUnit)
//function getGroupsForGroupSet(storeId,orgUnit,groupSet)
