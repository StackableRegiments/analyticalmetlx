var TokBox = (function(){
	
	var enabled = false;
	var setTokBoxEnabledStateFunc = function(isEnabled){
		enabled = isEnabled;
		//refreshAllVisualStates();
	};
	var sessions = [];
	var receiveTokBoxSessionFunc = function(desc){
			var support = OT.checkSystemRequirements();
			if (support == 0){
				errorAlert("Video conferencing disabled","Video conferencing is disabled because your browser does not support it.  You could try recent versions of Chrome, Firefox or Internet Explorer.");
			} else if (enabled && !_.some(sessions,function(s){return s.id == desc.sessionId;})){
				var container = sessionContainer.clone();
				sessionsContainer.append(container);
				var session = TokBoxSession(desc,container);
				sessions.push(session);
				console.log("received session:",session.id,container);
				session.refreshVisualState();
			}
	};
	var removeSessionsFunc = function(sessionIds){
		_.forEach(sessions,function(session){
			if (_.some(sessionIds,function(sid){
				return sid == session.id;
			})){
				session.shutdown();
				sessions = _.filter(sessions,function(s){
					return s.id != session.id;
				});
			};
		});
	};
	var sessionsContainer = undefined;
	var sessionContainer = undefined;
	$(function(){
		sessionsContainer = $("#videoConfSessionsContainer");
		sessionContainer = sessionsContainer.find(".videoConfSessionContainer").clone();
		sessionsContainer.empty();
	});
	return {
		getSessions:function(){return sessions;},
		receiveTokBoxSession:receiveTokBoxSessionFunc,
		setTokBoxEnabledState:setTokBoxEnabledStateFunc,
		removeSessions:removeSessionsFunc
	}
})();
var TokBoxSession = function(desc,sessionContainer){
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

    var streamButton = sessionContainer.find(".videoConfStartButton");
		var streamContainer = sessionContainer.find(".videoConfContainer");
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
			if (isConnected()){
				streamContainer.show();
				if ("capabilities" in session && "publish" in session.capabilities && session.capabilities.publish == 1){
					streamButton.show();
					streamButton.on("click",function(){
						togglePublishFunc(session);
					});
				} else {
					streamButton.hide();
				}
			} else {
				streamButton.hide();
				streamContainer.hide();
			}
			sessionContainer.find(".subscribedStream").removeClass("subscribedStream");
			if (thisPublisher != undefined){
				streamButton.addClass("publishedStream");
			} else {
				streamButton.removeClass("publishedStream");
			}
			_.forEach(streams,function(s){
				console.log("refreshing s:",s);
				if ("refreshVisual" in s){
					s.refreshVisual();
				}
			});
    };
		var togglePublishFunc = function(s){
			if (isConnected()){
				if (thisPublisher == undefined){
					console.log("attempting to start send:",isConnected(),session);
					startPublishFunc();
				} else {
					console.log("attempting to stop send:",isConnected(),session);
					stopPublishFunc();
				}
			}
		};
    var startPublishFunc = function(){
        refreshVisualState();
        console.log("attempting to start send:",isConnected(),session);
        if (isConnected()){
            if (thisPublisher == undefined){
                var publisherUniqueId = sprintf("tokBoxVideoElemPublisher_%s",_.uniqueId());
                var tokBoxVideoElemPublisher = $("<span />",{id:publisherUniqueId,"class":"publisherVideoElem"});
                streamContainer.append(tokBoxVideoElemPublisher);
                var targetResolution = sprintf("%sx%s",safeWidth(videoWidth),safeHeight(videoHeight));
                console.log("target resolution:",targetResolution)
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
                console.log("publishing",publisher,session);
                session.publish(publisher);
                sessionContainer.find(".videoConfStartButton").addClass("publishedStream");
            }
				}
        refreshVisualState();
		};
		var stopPublishFunc = function(s){
			refreshVisualState();
			if (isConnected() && thisPublisher != undefined){
				sessionContainer.find(".publisherVideoElem").remove();
				session.unpublish(thisPublisher);
				thisPublisher = undefined;
				sessionContainer.find(".videoConfStartButton").removeClass("publishedStream");
			}
			refreshVisualState();
    };
    var receiveBroadcastFunc = function(broadcast){
        console.log("broadcast:",broadcast);
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

		var shutdownFunc = function(){
			console.log("removing this session:",session);
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
					console.log("streamDestroyed",ev,elem);
					refreshVisualState();
				}
			},
			"streamCreated":function(ev){
				if ("capabilities" in session && "subscribe" in session.capabilities && session.capabilities.subscribe == 1){
					console.log("streamCreated",ev,streams);
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
						rootElem.find(".icon-txt").text(ev.stream.name);
						var button = rootElem.find(".videoConfSubscribeButton");
						var refreshUI = function(){
							if (oldStream.subscribed){
								button.addClass("subscribedStream");
							} else {
								button.removeClass("subscribedStream");
							}
						};
						refreshUI();
						rootElem.find(".videoConfSubscribeButton").on("click",function(){
						var s = streams[stream.id];
						if (s.subscribed){
							s.subscribed = false;
							session.unsubscribe(s.subscriber);
							console.log("unsubscribed from stream:",s.stream.name,s.stream.id);
						} else {
							s.subscribed = true;
							var subscriber = session.subscribe(s.stream,uniqueId,{
								insertMode:"append",
								width:videoWidth,
								height:videoHeight
							},function(error){
								if (!error){
										console.log("subscribed to stream:",s.stream.name,s.stream.id);
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
						}
						refreshVisualState();
					});
					subscriptionsContainer.append(rootElem);
					streamContainer.append(tokBoxVideoElemSubscriber);
					oldStream.videoSelectorId = uniqueId;
					oldStream.elem = rootElem;
					refreshVisualState();
				} else {
					oldStream["stream"] = stream;
					console.log("updating stream with a new version of it, for whatever reason.",ev);
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
			};
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
			})
			refreshVisualState();
		}
	};
};

function receiveTokBoxSessionToken(tokenMsg){
	if ("token" in tokenMsg){
		console.log("receiveTokBoxSession:",tokenMsg);
		TokBox.receiveTokBoxSession(tokenMsg);
	}
}
function removeTokBoxSessions(sessionIds){
	console.log("removeTokBoxSessions",sessionIds);
	TokBox.removeSessions(sessionIds);
}
function receiveTokBoxEnabled(isEnabled){
	TokBox.setTokBoxEnabledState(isEnabled);
}
function receiveTokBoxArchives(archives){
    console.log("archives:",archives);
}
function receiveTokBoxBroadcast(broadcast){
    //TokBox.receiveBroadcast(broadcast);
}
//injected by lift
//function getTokBoxToken(id){}
//function getTokBoxArchives(){}
