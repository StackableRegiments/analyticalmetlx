var VideoStream = function(constraints,sessionId){
	var userId = new Date().getTime().toString();
	var isCaller = false;
	var configuration = null; // I'm not sure what config a WebRTC connection can take yet;
	var peerConnection = new RTCPeerConnection(configuration);
	var receiveDescriptionFunc = function(desc){
		console.log("addingDesc: ",desc);
		peerConnection.setRemoteDescription(new RTCSessionDescription(desc));
		if (isCaller == false){
			startConference(false);
		}
	};
	var receiveCandidateFunc = function(candidate){
		if (peerConnection != undefined && candidate != null){
			console.log("addingIceCandidate: ",candidate);
			peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
		}
	};
	var receive = function(message){
		console.log("messageReceived:",message);
		if ("candidate" in message.data){
			receiveCandidateFunc(JSON.parse(message.data.candidate));
		}
		if ("description" in message.data){
			receiveDescriptionFunc(JSON.parse(message.data.description));
		}
		if ("connect" in message.data){
			if (message.data.isCaller){
				isCaller = true;
				startConference(message.data.isCaller);
			};
		}
	};

	// this is the bit which will have to be switched out for a more robust signalling mechanism
	var signallingChannel = (function(){
		var worker = new SharedWorker("/static/js/videoSignallingServer.js");
		worker.onerror = function(error){
			console.log("workerError",error);
		};
		worker.port.onmessage = receive;
		console.log("starting signaller",worker);
		var sendFunc = function(message){
			message.sessionId = sessionId;
			message.userId = userId;
			console.log("sending:",message);
			worker.port.postMessage(message);
		};
		sendFunc({
			"action":"connect"
		});
		return {
			send:sendFunc
		};
	})();
	// that's all.


	var localVideo = $("<video/>",{
		width:240,
		height:180
	})[0];
	var remoteVideo = $("<video/>",{
		width:240,
		height:180
	})[0];
	var offerOptions = {
		offerToReceiveAudio:true,
		offerToReceiveVideo:true
	};
	var defaultConstraints = {
		"audio":true,
		"video":{
			"width": {
				min: "300",
				max: "640"
			},
			"height": {
				min:"200",
				max:"480"
			},
			"frameRate": {
				"min":"25"
			}
		}
	};
	var startConference = function(isCaller){
		console.log("startingConference: ",isCaller);

		peerConnection.onaddstream = function(remoteE){
			remoteVideo.srcObject = remoteE.stream;
			remoteVideo.play();
		};
		peerConnection.onicecandidate = function(e){
			sendCandidate(e.candidate);
		};
		if ("mediaDevices" in navigator && "getUserMedia" in navigator.mediaDevices){
			navigator.mediaDevices.getUserMedia(constraints ? constraints : defaultConstraints).then(function(localStream){
				localVideo.srcObject = localStream;
				localVideo.play();
				peerConnection.addStream(localStream);
				peerConnection.onnegotiationeeded = function(negError) {
					console.log("local negotiation requested, error:",negError); //not sure what to do about negotiation yet
				};
				if (isCaller == true){
					console.log("calling");
					peerConnection.createOffer(offerOptions).then(function(desc){
						console.log("creating offer:",desc);
						peerConnection.setLocalDescription(desc);
						sendDescription(desc);
					});
				} else {
					console.log("answering",peerConnection.remoteDescription);
					peerConnection.createAnswer().then(function(desc){
						console.log("answerCreated",desc);
						peerConnection.setLocalDescription(desc);
						sendDescription(desc);
					});
				}
			}).catch(function(error){
				console.log("error getting device:",error);
			});
		}	
	};
	var sendDescription = function(desc){
		signallingChannel.send({
			description:JSON.stringify(desc)
		});
	};
	var sendCandidate = function(candidate){
		signallingChannel.send({
			candidate:JSON.stringify(candidate)
		});
	};
	var getLocalVideoFunc = function(){
		return localVideo;
	};
	var getRemoteVideoFunc = function(){
		return remoteVideo;
	};
	var hangupFunc = function(){
	};
	return {
		getLocalVideo:getLocalVideoFunc,
		getRemoteVideo:getRemoteVideoFunc,
		hangup:hangupFunc	
	};
};

var VideoTest = function(selector){
	VideoStream.getDevice(function(stream){
		var vidElem = $("<video/>",{
			height:180,
			width:240,
			id:"videoStream"	
		});
		vidElem[0].srcObject = stream;
		$(selector).append(vidElem);
		vidElem[0].play();
	},function(error){
		console.log("error getting videoStream:",error);
	});
};

var StreamTest = function(videoSelector,sessionId){
	var vs = VideoStream(undefined,sessionId);
	var localVideo = vs.getLocalVideo();
	var remoteVideo = vs.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
	return vs;
}
