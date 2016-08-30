var WebRtcStreamManager = (function(){
	var videoElemWidth = 240, videoElemHeight = 180;
	var defaultConstraints = {
		audio:true,
		video:{
			width:{
				min:"300",
				max:"640"
			},
			height:{
				min:"200",
				max:"480"
			},
			frameRate:{
				min:"10",
				max:"25"
			}
		}
	};
	
	var activeVideoClients = {}; 
	
	var createVideoStream = function(id,videoType,send,recv,constraints){
		var configuration = null;
		var webRtcPeer = new RTCPeerConnection(configuration);
		var localVideo = $("<video/>",{
			width:videoElemWidth,
			height:videoElemHeight
		})[0];
		var remoteVideo = $("<video/>",{
			width:videoElemWidth,
			height:videoElemHeight
		})[0];
		var offerOptions = {
			offerToReceiveAudio:true,
			offerToReceiveVideo:true
		};	

		var shutdownFunc = function(){
			localVideo.pause();
			remoteVideo.pause();
			localVideo.dispose();
			remoteVideo.dispose();
			webRtcPeer.disconnect();
			delete activeVideoClients[id];
		};
		webRtcPeer.onaddstream = function(remoteE){
			remoteVideo.srcObject = remoteE.stream;
			remoteVideo.play();
		};
		webRtcPeer.onicecandidate = function(e){
			sendVideoStreamIceCandidate(e.candidate,id);
		};
		if ("mediaDevices" in navigator && "getUserMedia" in navigator.mediaDevices){
			navigator.mediaDevices.getUserMedia(constraints ? constraints : defaultConstraints).then(function(localStream){
				localVideo.srcObject = localStream;
//				localVideo.mute();
				localVideo.play();
				webRtcPeer.addStream(localStream);
				webRtcPeer.onnegotiationeeded = function(negError) {
					console.log("local negotiation requested, error:",negError); //not sure what to do about negotiation yet
				};

				console.log("calling");
				webRtcPeer.createOffer(offerOptions).then(function(desc){
					console.log("creating offer:",desc);
					webRtcPeer.setLocalDescription(desc);
					initiateVideoStream(videoType,desc.sdp,id);
				});
			}).catch(function(error){
				console.log("error getting device:",error);
			});
		}
		var addIceCandidateFunc = function(candidate){
			webRtcPeer.addIceCandidate(new RTCIceCandidate(candidate));
		};
		var processAnswerFunc = function(answer){
			console.log("processing answer func:",answer,answer.sdpAnswer);
			webRtcPeer.setRemoteDescription(new RTCSessionDescription({
				"type":"answer",
				"sdp":answer.sdpAnswer
			}));
		};
		var returnObj = {
			shutdown:shutdownFunc,
			addIceCandidate:addIceCandidateFunc,
			processAnswer:processAnswerFunc,
			getLocalVideo:function(){
				return localVideo;
			},
			getRemoteVideo:function(){
				return remoteVideo;
			}
		};
		if (id in activeVideoClients){
			activeVideoClients[id].shutdown();
		}
		activeVideoClients[id] = returnObj;
		return returnObj;
	};


	var addIceCandidateFunc = function(id,iceCandidate){
		if (id in activeVideoClients){
			activeVideoClients[id].addIceCandidate(iceCandidate);
		}
	};
	var receiveAnswerFunc = function(id,answer){
		if (id in activeVideoClients){
			activeVideoClients[id].processAnswer(answer);
		}
	};
	var loopbackFunc = function(){
		return createVideoStream(new Date().getTime().toString(),"loopback",true,true);
	};
	var broadcastFunc = function(id){
		return createVideoStream(id,"broadcast",true,false);
	};
	var listenFunc = function(id){
		return createVideoStream(id,"broadcast",false,true);
	};
	var rouletteFunc = function(id){
		return createVideoStream(id,"roulette",true,true);
	};
	var groupChatFunc = function(id){
		return createVideoStream(id,"groupRoom",true,true);
	};
	var removeFunc = function(id){
		if (id in activeVideoClients){
			activeVideoClients[id].shutdown();
		}
	};
	return {
		receiveIceCandidate:addIceCandidateFunc,
		receiveAnswer:receiveAnswerFunc,
		loopback:loopbackFunc,
		broadcast:broadcastFunc,
		roulette:rouletteFunc,
		groupchat:groupChatFunc,
		removeVideoStream:removeFunc
	};
})();

var LoopbackTest = function(){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.loopback();// VideoStream(undefined,sessionId);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}
var BroadcastTest = function(id){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.broadcast(id);// VideoStream(undefined,sessionId);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}
var ListenTest = function(id){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.listen(id);// VideoStream(undefined,sessionId);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}
var RouletteTest = function(id){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.roulette(id);// VideoStream(undefined,sessionId);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}
var GroupRoomTest = function(id){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.groupRoom(id);// VideoStream(undefined,sessionId);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}


function receiveKurentoAnswer(answer,id){
	console.log("receiveKurentoAnswer",answer);
	WebRtcStreamManager.receiveAnswer(id,answer);
}
function receiveKurentoIceCandidate(candidate,id){
	var iceCandidate = JSON.parse(candidate.iceCandidateJsonString).candidate;
	console.log("receiveCandidate",iceCandidate);
	WebRtcStreamManager.receiveIceCandidate(id,iceCandidate);
}
//injected by lift
//function initiateVideoStream(videoType,offer,id){}
//function sendVideoStreamIceCandidate(iceCandidate,id){}
//function shutdownVideoStream(id){}


