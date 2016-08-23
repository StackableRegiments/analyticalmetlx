

var VideoStream = function(isCaller,constraints){
	var localVideo = $("<video/>",{
		width:240,
		height:180
	})[0];
	var remoteVideo = $("<video/>",{
		width:240,
		height:180
	})[0];
	var configuration = null; // I'm not sure what config a WebRTC connection can take yet;
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
	var peerConnection = new RTCPeerConnection(configuration);

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
				console.log("answering");
				peerConnection.createAnswer().then(peerConnection.remoteDescription,function(desc){
					peerConnection.setLocalDescription(desc);
					sendDescription(desc);
				});
			}
		}).catch(function(error){
			console.log("error getting device:",error);
		});
	}	
	var receiveDescriptionFunc = function(desc){
		if (peerConnection != undefined){
			peerConnection.setRemoteDescription(new RTCSessionDescription(desc));
		}
	};
	var receiveCandidateFunc = function(candidate){
		if (peerConnection != undefined){
			peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
		}
	};
	var receive = function(message){
		if ("candidate" in message){
			receiveCandidateFunc(message.candidate);
		}
		if ("description" in message){
			receiveDescriptionFunc(message.description);
		}
	};

	// this is the bit which will have to be switched out for a more robust signalling mechanism
	var signallingChannel = (function(){
		var worker = new SharedWorker("sharedBus.js");
		var sendFunc = function(message){
		};
		var receiveFunc = function(message){
		};
		return {
			send:sendFunc
	})();
	// that's all.

	var sendDescription = function(desc){
		signallingChannel.send({
			description:desc
		});
	};
	var sendCandidate = function(candidate){
		signallingChannel.send({
			candidate:candidate
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

var StreamTest = function(videoSelector,isCaller){
	var vs = VideoStream(isCaller);
	var localVideo = vs.getLocalVideo();
	var remoteVideo = vs.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
	return vs;
}
