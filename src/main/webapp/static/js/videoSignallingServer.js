self.sessions = {};

self.addEventListener("connect",function(e){
	var port = e.ports[0];
	port.onmessage = function(rawMsg){
		var msg = rawMsg.data;
		if ("sessionId" in msg){
			var sessionId = msg.sessionId;
			if (!("sessionId" in sessions)){
				sessions[sessionId] = {
					id:sessionId
				};
			}
			var session = sessions[sessionId];
			if ("initiator" in session && session.initiator.userId == msg.userId){
				//you're the pre-existing initiator
				if ("receiver" in session){
					session.receiver.port.postMessage(msg);
				} else {
					session.initiator.storedMessages.push(msg);
				}
			} else if ("receiver" in session && session.receiver.userId == msg.userId){
				//you're the pre-existing receiver
				if ("initiator" in session){
					session.initiator.port.postMessage(msg);
				} else {
					session.receiver.storedMessages.push(msg);
				}
			} else if (!("initiator" in session)){
				//you're the new initiator
				session.initiator = {
					userId:msg.userId,
					port:port,
					storedMessages:[]
				};
				sessions[sessionId] = session;
				port.postMessage({
					"connect":sessionId,
					"isCaller":true
				});
				if ("receiver" in session && "storedMessages" in session.receiver){
					//playback any stored messages from the receiver 
					while (session.receiver.storedMessages.peek() != undefined){
						port.postMessage(session.receiver.storedMessages.pop());
					}
				}
				throw JSON.stringify(sessions);
			} else if (!("receiver" in session)){
				//you're the new recipient
				session.receiver = {
					userId:msg.userId,
					port:port,
					storedMessage:[]
				};
				sessions[sessionId] = session;
				port.postMessage({
					"connect":sessionId,
					"isCaller":false
				});
				if ("initiator" in session && "storedMessages" in session.initiator){
					//playback any stored messages from the initiator
					while (session.initiator.storedMessages.peek() != undefined){
						port.postMessage(session.initiator.storedMessages.pop());
					}
				}
			} else {
				port.postMessage("too many people in this session");
				port.close();
			}
		}
	};
},false);
