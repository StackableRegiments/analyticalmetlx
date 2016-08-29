var KurentoStream = (function(){
	//window.console = new Console();
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
			},
			mediaConstraints:{
				audio:true,
				video:{
					width:320,
					height:180,
					framerate:10	
				}
			}
		}; 
	};
	var opts = {
	};
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
	var groupRoomFunc = function(){
		return startFunc("groupRoom",true,true);
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
		groupRoom:groupRoomFunc,
		roulette:rouletteFunc,
		listen:listenFunc,
		getLocalVideo:getLocalVideoFunc,
		getRemoteVideo:getRemoteVideoFunc,
		hangup:hangupFunc	
	};
})();

window.getUserMedia = navigator.getUserMedia;

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
var GroupRoomTest = function(){
	var videoSelector = "#masterHeader";
	KurentoStream.groupRoom();// VideoStream(undefined,sessionId);
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


