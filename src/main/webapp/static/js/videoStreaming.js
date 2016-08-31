var TokBox = (function(){
	var session = undefined;
	var isConnected = function(){
		return session != undefined && "isConnected" in session && session.isConnected();
	};
	var subscriberSection = undefined;
	var streamButton = undefined, streamContainer = undefined;
	var enabled = false;
	var streams = {};
	var receiveTokBoxSessionFunc = function(desc){
		session = OT.initSession(desc.apiKey,desc.sessionId);
		session.on("streamDestroyed",function(ev){
			if (ev.stream.id in streams){
				var elem = streams[ev.stream.id];
				delete streams[ev.stream.id];
				console.log("streamDestroyed",ev,elem);
				elem.elem.remove();
				// I'm not sure what I want to do with this yet, but no doubt I'll have some reason to subscribe;
				refreshVisualState();
			}
		});
		session.on("streamCreated",function(ev){
			var stream = ev.stream;
			var rootElem = $(subscriberSection.clone());
			var uniqueId = sprintf("tokBoxVideoElemSubscriber_%s",_.uniqueId());
			rootElem.attr("id",uniqueId);
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
						elem:rootElem,
						subscriber:subscriber,
						refreshVisual:refreshUI
					};
				}
				refreshVisualState();
			});
			streamContainer.append(rootElem);
			refreshVisualState();
		});
		session.on("sessionConnected",function(){
			refreshVisualState();
		});
		session.on("sessionDisconnected",function(){
			refreshVisualState();
		});
		session.on("sessionReconnected",function(){
			refreshVisualState();
		});
		session.on("sessionReconnecting",function(){
			refreshVisualState();
		});
		session.connect(desc.token,function(error){
			if (!error){
			} else {
				console.log("error when connecting to tokBox",error,desc);
			}
			refreshVisualState();
		});
	};
	var startSessionFunc = function(id){
		getTokBoxToken(id);
	};
	$(function(){
		streamButton = $("#videoConfStartButton");
		streamContainer = $("#videoConfContainer");
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
		if (enabled && isConnected()){
			streamButton.show();
			streamContainer.show();
			streamButton.on("click",startPublishFunc);
		} else {
			streamButton.hide();
			streamContainer.hide();
			streamButton.unbind("click");
		}	
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
		console.log("attempting to start send:",isConnected(),session);
		var tokBoxVideoElemPublisher = $("<span/>",{id:"tokBoxVideoElemPublisher"});
		if (isConnected()){
		  if (thisPublisher == undefined){
				streamContainer.append(tokBoxVideoElemPublisher);
				var publisher = OT.initPublisher("tokBoxVideoElemPublisher", {
					insertMode:"append",
					width:320,
					height:240,
					name:UserSettings.getUsername()	
				});
				thisPublisher = publisher;
				session.publish(publisher);
				console.log("publishing",publisher);
			} else {
				var pub = thisPublisher;
				thisPublisher = undefined;
				tokBoxVideoElemPublisher.remove();
				session.unpublish(pub);
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
//injected by lift
//function getTokBoxToken(id){}
