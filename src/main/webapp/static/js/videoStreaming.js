var TokBox = (function(){
	var session = undefined;
	var isConnected = function(){
		return session != undefined && "isConnected" in session && session.isConnected();
	};
	var subscriberSection = undefined;
	var streamButton = undefined, streamContainer = undefined, subscriptionsContainer = undefined;
	var enabled = false;
	var streams = {};
	var receiveTokBoxSessionFunc = function(desc){
		var support = OT.checkSystemRequirements();
		if (support == 0){
		} else {
			session = OT.initSession(desc.apiKey,desc.sessionId);
			session.on({
				"streamDestroyed":function(ev){
					if (ev.stream.id in streams){
						var elem = streams[ev.stream.id];
						delete streams[ev.stream.id];
						console.log("streamDestroyed",ev,elem);
						elem.elem.remove();
						// I'm not sure what I want to do with this yet, but no doubt I'll have some reason to subscribe;
						refreshVisualState();
					}
				},
				"streamCreated":function(ev){
					var stream = ev.stream;
					var rootElem = $(subscriberSection.clone());
					var uniqueId = sprintf("tokBoxVideoElemSubscriber_%s",_.uniqueId());
					var tokBoxVideoElemSubscriber = $("<span />",{id:uniqueId,"class":"subscriberVideoElem"});
					rootElem.find(".icon-txt").text(ev.stream.name);
					var button = rootElem.find(".videoConfSubscribeButton");
					if (stream.id in streams){
						button.addClass("subscribedStream");
					} else {
						button.removeClass("subscribedStream");
					}
					rootElem.find(".videoConfSubscribeButton").on("click",function(){
						if (stream.id in streams){
							var subscriber = streams[stream.id];
							delete streams[stream.id];
							session.unsubscribe(subscriber.subscriber);
							console.log("unsubscribed from stream:",stream.name,stream.id);
						} else {
							var subscriber = session.subscribe(stream,uniqueId,{
								insertMode:"append",
								width:320,
								height:240
							},function(error){
								if (!error){
									console.log("subscribed to stream:",stream.name,stream.id);
								} else {
									rootElem.remove();
									console.log("error when subscribing to stream",error,stream.name,stream.id);
								}
							});
							var refreshUI = function(){
								if (stream.id in streams){
									button.addClass("subscribedStream");
								} else {
									button.removeClass("subscribedStream");
								}
							};
							streams[stream.id] = {
								videoSelectorId:uniqueId,
								elem:rootElem,
								subscriber:subscriber,
								refreshVisual:refreshUI
							};
						}
						refreshVisualState();
					});
					subscriptionsContainer.append(rootElem);
					streamContainer.append(tokBoxVideoElemSubscriber);
					refreshVisualState();
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
		}
	};
	var startSessionFunc = function(id){
		getTokBoxToken(id);
	};
	$(function(){
		streamButton = $("#videoConfStartButton");
		streamContainer = $("#videoConfContainer");
		subscriptionsContainer = $("#videoSubsciptionsContainer");
		if ("Conversations" in window){
			startSessionFunc(Conversations.getCurrentConversationJid());
		}
		subscriberSection = streamContainer.find(".videoContainer").clone();
		streamContainer.empty();
		refreshVisualState();
	});
	var setTokBoxEnabledStateFunc = function(isEnabled){
		enabled = isEnabled;
		refreshVisualState();
	};
	var refreshVisualState = function(){
		streamButton.unbind("click");
		if (enabled && isConnected()){
			streamButton.show();
			streamContainer.show();
			streamButton.on("click",startPublishFunc);
		} else {
			streamButton.hide();
			streamContainer.hide();
		}	
		$(".subscribedStream").removeClass("subscribedStream");
		if (thisPublisher != undefined){
			streamButton.addClass("subscribedStream");
		} else {
			streamButton.removeClass("subscribedStream");
		}
		_.forEach(streams,function(s){
			console.log("refreshing s:",s);
			s.refreshVisual();
		});
	};
	var thisPublisher = undefined;
	var startPublishFunc = function(){
		refreshVisualState();
		console.log("attempting to start send:",isConnected(),session);
		if (isConnected()){
		  if (thisPublisher == undefined){
				var publisherUniqueId = sprintf("tokBoxVideoElemPublisher_%s",_.uniqueId());
				var tokBoxVideoElemPublisher = $("<span />",{id:publisherUniqueId,"class":"publisherVideoElem"});
				streamContainer.append(tokBoxVideoElemPublisher);
				var publisher = OT.initPublisher(publisherUniqueId, {
					insertMode:"append",
					width:320,
					height:240,
					name:UserSettings.getUsername()	
				},function(error){
					if (error){
						console("error:",error);
					}
				});
				thisPublisher = publisher;
				console.log("publishing",publisher,session);
				session.publish(publisher);
			} else {
				$(".publisherVideoElem").remove();
				var pub = thisPublisher;
				thisPublisher = undefined;
				session.unpublish(pub);
				//tokBoxVideoElemPublisher.remove();
			}
		}
		refreshVisualState();
	};
	return {
		setTokBoxEnabledState:setTokBoxEnabledStateFunc,
		startSession:startSessionFunc,
		startPublish:startPublishFunc,
		receiveTokBoxSession:receiveTokBoxSessionFunc,
		getIsConnected:isConnected,
		getSession:function(){return session;}
	};
})();

function receiveTokBoxSessionToken(tokenMsg){
	if ("token" in tokenMsg){
		TokBox.receiveTokBoxSession(tokenMsg);
	}
}
function receiveTokBoxEnabled(isEnabled){
	TokBox.setTokBoxEnabledState(isEnabled);
}
function receiveTokBoxArchives(archives){
	console.log("archives:",archives);
}
function receiveTokBoxBroadcast(broadcast){
	console.log("broadcast:",broadcast);
}
//injected by lift
//function getTokBoxToken(id){}
//function getTokBoxArchives(){}
