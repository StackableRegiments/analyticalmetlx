self.sessions = {};

var initiatorTag = "initiator";
var receiverTag = "receiver";
var sessionIdTag = "sessionId";
var storedMessagesTag = "storedMessages";

self.addEventListener("connect",function(e){
	var port = e.ports[0];
	port.onmessage = function(rawMsg){
		var msg = rawMsg.data;
		if (sessionIdTag in msg){
			var sessionId = msg.sessionId;
			if (!(sessionId in self.sessions)){
				self.sessions[sessionId] = {
					id:sessionId
				};
			}
			var session = sessions[sessionId];
			if (initiatorTag in session && session[initiatorTag].userId == msg.userId){
				//you're the pre-existing initiator
				if (receiverTag in session){
					session[receiverTag].port.postMessage(msg);
				} else {
					session[initiatorTag][storedMessagesTag].push(msg);
				}
			} else if (receiverTag in session && session[receiverTag].userId == msg.userId){
				//you're the pre-existing receiver
				if (initiatorTag in session){
					session[initiatorTag].port.postMessage(msg);
				} else {
					session[receiverTag][storedMessages].push(msg);
				}
			} else if (!(initiatorTag in session)){
				//you're the new initiator
				session[initiatorTag] = {
					userId:msg.userId,
					port:port,
					storedMessages:[]
				};
				sessions[sessionId] = session;
				port.postMessage({
					"connect":sessionId,
					"isCaller":true
				});
				if (receiverTag in session && storedMessagesTag in session.receiver){
					//playback any stored messages from the receiver 
					var currentMessage = session[receiverTag][storedMessagesTag].shift();
					while (currentMessage != undefined){
						port.postMessage(currentMessage);
						currentMessage = session[receiverTag][storedMessagesTag].shift();
					}
				}
			} else if (!(receiverTag in session)){
				//you're the new recipient
				session[receiverTag] = {
					userId:msg.userId,
					port:port,
					storedMessage:[]
				};
				sessions[sessionId] = session;
				port.postMessage({
					"connect":sessionId,
					"isCaller":false
				});
				if (initiatorTag in session && storedMessagesTag in session.initiator){
					//playback any stored messages from the initiator
					//
					var currentMessage = session[initiatorTag][storedMessagesTag].shift();
					while (currentMessage != undefined){
						port.postMessage(currentMessage);
						currentMessage = session[initiatorTag][storedMessagesTag].shift();
					}
				}
			} else {
				port.postMessage({
					error:"too many people in this session"
				});
				port.close();
			}
		}
	};
},false);
