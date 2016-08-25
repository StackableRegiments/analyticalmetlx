var KurentoStream = (function(){
	var webRtcPeer = undefined;

	var localVideo = $("<video/>",{
		width:240,
		height:180
	})[0];
	var remoteVideo = $("<video/>",{
		width:240,
		height:180
	})[0];

	var options = function(){ 
		return {
			localVideo: localVideo,
			remoteVideo: remoteVideo,
			onicecandidate: function(candidate){
				sendVideoStreamIceCandidate(candidate);
			}
		}; 
	};
	var opts = {};
	var offerFunc = function(videoType){ return function(error){
		if (error){
			console.log("startFuncError:",error);
		}
		webRtcPeer.generateOffer(function(offerError,offerData){
			initiateVideoStream(videoType,offerData);
		});
	}};

	var startFunc = function(videoType,send,recv){
		if (webRtcPeer != undefined){
			shutdownVideoStream();
		}
		var peerGen = kurentoUtils.WebRtcPeer
		if (send && recv){
			opts = options();
			webRtcPeer = new peerGen.WebRtcPeerSendrecv(opts,offerFunc(videoType));
		} else if (send){
			opts = options();
			delete opts.remoteVideo;
			webRtcPeer = new peerGen.WebRtcPeerSendonly(opts,offerFunc(videoType));
		} else if (recv){
			opts = options();
			delete opts.localVideo;
			webRtcPeer = new peerGen.WebRtcPeerRecvonly(opts,offerFunc(videoType));
		}	
	};

	var loopbackFunc = function(){
		return startFunc("loopback",true,true);
	};
	var broadcastFunc = function(){
		return startFunc("broadcast",true,false);
	};
	var listenFunc = function(){
		return startFunc("broadcast",false,true);
	};
	var rouletteFunc = function(){
		return startFunc("roulette",true,true);
	};
	var getLocalVideoFunc = function(){
		return localVideo;
	};
	var getRemoteVideoFunc = function(){
		return remoteVideo;
	};
	var hangupFunc = function(){
		shutdownVideoStream();
	};
	var receiveIceCandidateFunc = function(candidate){
		if (webRtcPeer != undefined){
			webRtcPeer.addIceCandidate(candidate,function(error){
				if (error != undefined){
					console.log("receiveCandidateError",candidate,error);
				}
			});
		}
	};
	var receiveAnswerFunc = function(answer){
		if (webRtcPeer != undefined){
			webRtcPeer.processAnswer(answer,function(error){
				if (error != undefined){
					console.log("receiveAnswerError",answer,error);
				}
				if ("remoteVideo" in opts){
					remoteVideo.play(); // not sure when to do this, but I think it's here.
				}
				if ("localVideo" in opts){
					localVideo.play();
				}
			});
		}
	};	
	return {
		receiveIceCandidate:receiveIceCandidateFunc,
		receiveAnswer:receiveAnswerFunc,	
		loopback:loopbackFunc,
		broadcast:broadcastFunc,
		roulette:rouletteFunc,
		listen:listenFunc,
		getLocalVideo:getLocalVideoFunc,
		getRemoteVideo:getRemoteVideoFunc,
		hangup:hangupFunc	
	};
})();

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

var LoopbackTest = function(){
	var videoSelector = "#masterHeader";
	KurentoStream.loopback();// VideoStream(undefined,sessionId);
	var localVideo = KurentoStream.getLocalVideo();
	var remoteVideo = KurentoStream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}
var BroadcastTest = function(){
	var videoSelector = "#masterHeader";
	KurentoStream.broadcast();// VideoStream(undefined,sessionId);
	var localVideo = KurentoStream.getLocalVideo();
	var remoteVideo = KurentoStream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}
var ListenTest = function(){
	var videoSelector = "#masterHeader";
	KurentoStream.listen();// VideoStream(undefined,sessionId);
	var localVideo = KurentoStream.getLocalVideo();
	var remoteVideo = KurentoStream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}
var RouletteTest = function(){
	var videoSelector = "#masterHeader";
	KurentoStream.roulette();// VideoStream(undefined,sessionId);
	var localVideo = KurentoStream.getLocalVideo();
	var remoteVideo = KurentoStream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}

function receiveKurentoAnswer(answer){
	console.log("receiveKurentoAnswer",answer);
	KurentoStream.receiveAnswer(answer.sdpAnswer);
}
function receiveKurentoIceCandidate(candidate){
	var iceCandidate = JSON.parse(candidate.iceCandidateJsonString).candidate;
//	console.log("receiveCandidate",iceCandidate);
	KurentoStream.receiveIceCandidate(iceCandidate);
}
//injected by lift
//function initiateVideoStream(videoType,offer){}
//function sendVideoStreamIceCandidate(iceCandidate){}
//function shutdownVideoStream(){}


