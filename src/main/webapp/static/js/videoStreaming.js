var TokBox = (function(){
    var shownError = false;
    var enabled = false;
    var setTokBoxEnabledStateFunc = function(isEnabled){
        enabled = isEnabled;
        //refreshAllVisualStates();
        Plugins.streaming.changeVisualState(enabled,true,false);
    };
    var initialized = false;
    var sessions = {};
    var receiveTokBoxSessionFunc = function(desc){
        if (initialized){
            sessionsContainer.css({display:"flex"});
            if(window["OT"]){
                var support = OT.checkSystemRequirements();
                if (support == 0){
                    if (!shownError){
                        errorAlert("Video conferencing disabled","Video conferencing is disabled because your browser does not support it.  You could try recent versions of Chrome or Firefox.");
                        shownError = true;
                    }
                } else if (enabled && !(desc.sessionId in sessions)){
                    var container = sessionContainer.clone();
                    sessionsContainer.append(container);
                    var session = TokBoxSession(desc,container);
                    sessions[session.id] = session;
                    session.refreshVisualState();
                }
            }
            else{
                errorAlert("Could not connect video","Please check your network connection");
            }
        }
    };
    var removeSessionsFunc = function(sessionIds){
        _.forEach(sessions,function(session){
            if (_.some(sessionIds,function(sid){
                return sid == session.id;
            })){
                session.shutdown();
                delete sessions[session.id];
            }
        });
    };
    var sessionsContainer = undefined;
    var sessionContainer = undefined;
    var teacherControls = undefined;
    var permitStudentsToPublishCheckbox = undefined;
    var publishingPermitted = false;
    var actOnConversationDetails = function(c){
        if (c){
            publishingPermitted = ("permissions" in c && c.permissions.studentsMayBroadcast && !Conversations.getIsBanned(c));
            if (teacherControls){
                if (Conversations.shouldModifyConversation(c)){
                    teacherControls.show();
                    permitStudentsToPublishCheckbox.prop("checked",publishingPermitted).unbind("click").on("click",function(){
                        var mayBroadcast = $(this).prop("checked");
                        if ("Conversations" in window){
                            var conv = Conversations.getCurrentConversation();
                            var perms = conv.permissions;
                            perms.studentsMayBroadcast = mayBroadcast;
                            changePermissionsOfConversation(conv.jid.toString(),perms);
                        }
                    });
                } else {
                    permitStudentsToPublishCheckbox.unbind("click");
                    teacherControls.hide();
                }
            }
        }
        _.forEach(sessions,function(s){
            s.refreshVisualState();
        });
    };

    Progress.conversationDetailsReceived["TokBox"] = actOnConversationDetails;
    Progress.userBanned["TokBox"] = actOnConversationDetails;
    Progress.userUnbanned["TokBox"] = actOnConversationDetails;
    return {
        getSessions:function(){return sessions;},
        initialize:function(){
            sessionsContainer = $("#videoConfSessionsContainer");
            teacherControls = sessionsContainer.find(".teacherControls").clone();
            permitStudentsToPublishCheckbox = teacherControls.find("#canBroadcast");
            sessionContainer = sessionsContainer.find(".videoConfSessionContainer").clone();
            sessionsContainer.empty();
            sessionsContainer.append(teacherControls);
            actOnConversationDetails(Conversations.getCurrentConversation());
            initialized = true;
        },
        receiveTokBoxSession:receiveTokBoxSessionFunc,
        getTokBoxEnabledState:function(){return enabled},
        setTokBoxEnabledState:setTokBoxEnabledStateFunc,
        removeSessions:removeSessionsFunc,
        canPublish:function(){ return Conversations.shouldModifyConversation() || publishingPermitted; }
    }
})();
var TokBoxSession = function(desc,sessionContainer){
    var autoSubscribeAndPublish = false;
    var videoWidth = 160;
    var videoHeight = 120;
    var videoFps = 15;

    var validWidths = [320,640,1280];
    var validHeights = [240,480,720];
    var validFpss = [1,7,15,30];

    var safeFps = function(preferred){
        var coll = validFpss;
        var candidate = _.reverse(_.filter(coll,function(c){return c <= preferred;}))[0];
        if (candidate == undefined){
            candidate = coll[0];
        }
        return candidate;
    };
    var safeWidth = function(preferred){
        var coll = validWidths;
        var candidate = _.reverse(_.filter(coll,function(c){return c <= preferred;}))[0];
        if (candidate == undefined){
            candidate = coll[0];
        }
        return candidate;
    };
    var safeHeight = function(preferred){
        var coll = validHeights;
        var candidate = _.reverse(_.filter(coll,function(c){return c <= preferred;}))[0];
        if (candidate == undefined){
            candidate = coll[0];
        }
        return candidate;
    };

    var isConnected = function(){
        return "isConnected" in session && session.isConnected();
    };

    var preferredStreams = {};

    var streamButton = sessionContainer.find(".videoConfStartButton");
    var streamContainer = sessionContainer.find(".videoConfContainer");
    var publishButtonContainer = sessionContainer.find(".videoConfStartButtonContainer");
    var subscriptionsContainer = sessionContainer.find(".videoSubscriptionsContainer");
    var subscriberSection = sessionContainer.find(".videoContainer").clone();
    var broadcastContainer = sessionContainer.find(".broadcastContainer");
    var broadcastButton = sessionContainer.find(".broadcastLink");
    subscriptionsContainer.empty();
    broadcastContainer.empty();
    streamContainer.empty();

    var streams = {};
    var thisPublisher = undefined;
    var refreshVisualState = function(){
        streamButton.unbind("click");

        if (isConnected() && TokBox.canPublish() && !session.studentsMayBroadcast){
            streamContainer.show();
            if ("capabilities" in session && "publish" in session.capabilities && session.capabilities.publish == 1){
                publishButtonContainer.show();
                streamButton.show();
                streamButton.on("click",function(){
                    togglePublishFunc(session);
                });
            } else {
                if (isConnected()){
                    stopPublishFunc(true);
                }
                publishButtonContainer.hide();
                streamButton.hide();
                streamContainer.hide();
            }
        } else {
            if (isConnected()){
                stopPublishFunc(true);
            }
            publishButtonContainer.hide();
            streamButton.hide();
            streamContainer.hide();
        }
        sessionContainer.find(".subscribedStream").removeClass("subscribedStream");
        if(session.connection){
            var context = session.connection.data.match(/description=(.+)$/)[1];
            var label = context;
            if(context == Conversations.getCurrentConversationJid()){label = "everyone"}
            else{
                var groupContext = _.flatMap(Conversations.getCurrentSlide().groupSets,function(groupSet){
                    return _.find(groupSet.groups,function(group){
                        return group.id == context;
                    });
                });
                if(groupContext.length){
                    label = sprintf("group %s",groupContext[0].title);
                }
            }
            streamButton.find(".context").text(label);
        }
        if (thisPublisher != undefined){
            streamButton.addClass("publishedStream").find(".videoConfStartButtonLabel").text("Hide from ");
        } else {
            streamButton.removeClass("publishedStream").find(".videoConfStartButtonLabel").text("Stream to ");
        }
        _.forEach(streams,function(s){
            if ("refreshVisual" in s){
                s.refreshVisual();
            }
        });
        DeviceConfiguration.applyFit();
    };
    var togglePublishFunc = function(s){
        if (isConnected()){
            if (thisPublisher == undefined){
                startPublishFunc();
            } else {
                stopPublishFunc();
            }
        }
    };
    var startPublishFunc = function(){
        refreshVisualState();
        if (isConnected()){
            if (thisPublisher == undefined){
                var publisherUniqueId = sprintf("tokBoxVideoElemPublisher_%s",_.uniqueId());
                var tokBoxVideoElemPublisher = $("<span />",{id:publisherUniqueId,"class":"publisherVideoElem"});
                sessionContainer.find(".viewscreen").append(tokBoxVideoElemPublisher);
                var targetResolution = sprintf("%sx%s",safeWidth(videoWidth),safeHeight(videoHeight));
                var publisher = OT.initPublisher(publisherUniqueId, {
                    name:UserSettings.getUsername(),
                    width:videoWidth,
                    height:videoHeight,
                    resolution:"320x240",
                    frameRate:safeFps(videoFps),
                    insertMode:"append"
                },function(error){
                    if (error){
                        console.log("tokbox error:",error);
                    }
                });
                thisPublisher = publisher;
                publisher.element.style.width = videoWidth;
                publisher.element.style.height = videoHeight;
                session.publish(publisher);
                sessionContainer.find(".videoConfStartButton").addClass("publishedStream");
            }
        }
        refreshVisualState();
    };
    var stopPublishFunc = function(skipRefresh){
        if (!skipRefresh){
            refreshVisualState();
        }
        if (isConnected() && thisPublisher != undefined){
            sessionContainer.find(".publisherVideoElem").remove();
            session.unpublish(thisPublisher);
            thisPublisher = undefined;
            sessionContainer.find(".videoConfStartButton").removeClass("publishedStream");
        }
        if (!skipRefresh){
            refreshVisualState();
        }
    };
    var receiveBroadcastFunc = function(broadcast){
        if (broadcast != null && "broadcastUrls" in broadcast && "hls" in broadcast.broadcastUrls){
            var rootElem = broadcastButton.clone();
            rootElem.attr("href",broadcast.broadcastUrls.hls);
            broadcastContainer.append(rootElem);
        } else {
            broadcastContainer.empty();
        }
    };
    var downgradeVideoStreams = function(){
        _.forEach(streams,function(stream){
            if ("subscriber" in stream && stream.subscriber != null && "restrictFramerate" in stream.subscriber){
                stream.subscriber.restrictFramerate(true);
            }
        });
    };
    var upgradeVideoStreams = function(){
        _.forEach(streams,function(stream){
            if ("subscriber" in stream && stream.subscriber != null && "restrictFramerate" in stream.subscriber){
                stream.subscriber.restrictFramerate(false);
            }
        });
    };
    Progress.afterWorkQueuePause["videoStreaming"] = downgradeVideoStreams;
    Progress.beforeWorkQueueResume["videoStreaming"] = upgradeVideoStreams;

    var onConversationDetailsReceived = function(conv){
        if ("jid" in conv && "Conversations" in window && "permissions" in conv && "studentsMayBroadcast" in conv.permissions){
            refreshVisualState();
        }
    };

    Progress.conversationDetailsReceived["videoStreaming"] = onConversationDetailsReceived;

    var shutdownFunc = function(){
        session.disconnect();
        sessionContainer.remove();
    };

    var session = OT.initSession(desc.apiKey,desc.sessionId)
    session.on({
        "streamDestroyed":function(ev){
            if (ev.stream.id in streams){
                var elem = streams[ev.stream.id];
                elem.elem.remove();
                delete streams[ev.stream.id];
                refreshVisualState();
            }
        },
        "streamCreated":function(ev){
            if ("capabilities" in session && "subscribe" in session.capabilities && session.capabilities.subscribe == 1){
                var stream = ev.stream;
                var oldStream = streams[stream.id];
                if (oldStream == undefined){
                    oldStream = {
                        stream: stream,
                        subscribed: false,
                        refreshVisual:function(){}
                    };
                    streams[stream.id] = oldStream;
                    var uniqueId = sprintf("tokBoxVideoElemSubscriber_%s",_.uniqueId());
                    var rootElem = $(subscriberSection.clone());
                    var tokBoxVideoElemSubscriber = $("<span />",{id:uniqueId,"class":"subscriberVideoElem"});
                    rootElem.find(".publisherName").text(ev.stream.name);
                    var button = rootElem.find(".videoConfSubscribeButton");
                    var refreshUI = function(){
                        button.toggleClass("subscribedStream",oldStream.subscribed);
                    };
                    refreshUI();
                    var s = streams[stream.id];
                    var preferredStreamId = stream.name;
                    //console.log("subscribing to",preferredStreamId);
                    var toggleSubscription = function(){
                        if (s.subscribed){
                            stopSubscribing();
                        } else {
                            startSubscribing();
                        }
                        refreshVisualState();
                    };
                    var startSubscribing = function(){
                        s.subscribed = true;
                        preferredStreams[preferredStreamId] = true;
                        var subscriber = session.subscribe(s.stream,uniqueId,{
                            insertMode:"append",
                            width:videoWidth,
                            height:videoHeight
                        },function(error){
                            if (!error){
                                //console.log("subscribed to stream:",s.stream.name,s.stream.id);
                            } else {
                                rootElem.remove();
                                console.log("error when subscribing to stream",error,s.stream.name,s.stream.id);
                            }
                        });
                        subscriber.element.style.width = videoWidth;
                        subscriber.element.style.height = videoHeight;
                        subscriber.on("videoDimensionsChanged", function(event) {
                            subscriber.element.style.width = event.newValue.width + 'px';
                            subscriber.element.style.height = event.newValue.height + 'px';
                        });
                        s.subscriber = subscriber;
                        s.refreshVisual = refreshUI;
                    };
                    var stopSubscribing = function(){
                        s.subscribed = false;
                        session.unsubscribe(s.subscriber);
                        //console.log("unsubscribed from stream:",s.stream.name,s.stream.id);
                        delete preferredStreams[preferredStreamId];
                    };
                    rootElem.find(".videoConfSubscribeButton").on("click",toggleSubscription);
                    tokBoxVideoElemSubscriber.prepend(rootElem);
                    streamContainer.append(tokBoxVideoElemSubscriber);
                    oldStream.videoSelectorId = uniqueId;
                    oldStream.elem = rootElem;
                    if (autoSubscribeAndPublish){
                        startSubscribing();
                    }
                    if (preferredStreamId in preferredStreams){
                        startSubscribing();
                    }
                    refreshVisualState();
                } else {
                    oldStream["stream"] = stream;
                    //console.log("updating stream with a new version of it, for whatever reason.",ev);
                }
            }
        },
        "sessionConnected":function(ev){
            refreshVisualState();
        },
        "sessionDisconnected":function(ev){
            refreshVisualState();
        },
        "sessionReconnected":function(ev){
            refreshVisualState();
        },
        "sessionReconnecting":function(ev){
            refreshVisualState();
        }
    });
    session.connect(desc.token,function(error){
        if (!error){
            if (autoSubscribeAndPublish){
                startPublishFunc();
            }
        } else {
            console.log("error when connecting to tokBox",error,desc);
        }
        refreshVisualState();
    });
    return {
        startPublish:startPublishFunc,
        receiveBroadcast:receiveBroadcastFunc,
        getIsConnected:isConnected,
        id:session.id,
        getSession:function(){return session;},
        refreshVisualState:refreshVisualState,
        shutdown:shutdownFunc,
        resizeVideo:function(w,h,fps){
            if (w != undefined){
                videoWidth = w;
            }
            if (h != undefined){
                videoHeight = h;
            }
            if (fps != undefined){
                videoFps = fps;
            }
            if (thisPublisher != undefined){
                stopPublishFunc();
                startPublisherFunc();
            }
            _.forEach(streams,function(stream){
                if ("subscriber" in stream && stream.subscriber != null){
                    stream.subscriber.setPreferredResolution({
                        width:videoWidth,
                        height:videoHeight
                    });
                    if ("refreshVisual" in stream){
                        stream.refreshVisual();
                    }
                }
            });
            refreshVisualState();
        }
    };
};

function receiveTokBoxSessionToken(tokenMsg){
    if ("token" in tokenMsg){
        TokBox.receiveTokBoxSession(tokenMsg);
    }
}
function removeTokBoxSessions(sessionIds){
    //console.log("removeTokBoxSessions",sessionIds);
    TokBox.removeSessions(sessionIds);
}
function receiveTokBoxEnabled(isEnabled){
    console.log("TokBox enabled",isEnabled);
    TokBox.setTokBoxEnabledState(isEnabled);
}
function receiveTokBoxArchives(archives){
    //console.log("archives:",archives);
}
function receiveTokBoxBroadcast(broadcast){
    //TokBox.receiveBroadcast(broadcast);
}
//injected by lift
//function getTokBoxToken(id){}
//function getTokBoxArchives(){}
