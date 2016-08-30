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

	function setBandwidth(sdp) {
		var audioBandwidth = 50, videoBandwidth = 200;

		//sdp = sdp.replace(/a=mid:audio\r\n/g, 'a=mid:audio\r\nb=AS:' + audioBandwidth + '\r\n');
		//sdp = sdp.replace(/a=mid:video\r\n/g, 'a=mid:video\r\nb=AS:' + videoBandwidth + '\r\n');
		return sdp;
	}
	
	var activeVideoClients = {}; 
	
	var createVideoStream = function(id,videoType,send,recv,constraints){
		var remoteStream = undefined, localStream = undefined;
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
			offerToReceiveAudio:recv,
			offerToReceiveVideo:recv
		};
		var shutdownFunc = function(){
			localVideo.pause();
			remoteVideo.pause();
			if (localStream != undefined){
				_.forEach(localStream.getTracks(),function(track){
					track.stop();
				});
			}
			if (remoteStream != undefined){
				_.forEach(remoteStream.getTracks(),function(track){
					track.stop();
				});
			}
			localVideo.remove();
			remoteVideo.remove();
			webRtcPeer.close();
			delete activeVideoClients[id];
		};
		webRtcPeer.onaddstream = function(remoteE){
			//console.log("remoteStream arrived:",remoteE,remoteE.stream.getVideoTracks(),remoteE.stream.getAudioTracks());
			if (recv){
				remoteVideo.srcObject = remoteE.stream;
				remoteStream = remoteE.stream;
				remoteVideo.play();
			}
		};
		webRtcPeer.onicecandidate = function(e){
			sendVideoStreamIceCandidate(e.candidate,id);
		};
		webRtcPeer.onnegotiationeeded = function(negError) {
			console.log("local negotiation requested, error:",negError); //not sure what to do about negotiation yet
		};
		if (send){
			if ("mediaDevices" in navigator && "getUserMedia" in navigator.mediaDevices){
				navigator.mediaDevices.getUserMedia(constraints ? constraints : defaultConstraints).then(function(stream){
					localVideo.srcObject = stream;
					localVideo.muted = true; // we don't want to build a local feedback loop
					localVideo.play();
					webRtcPeer.addStream(stream);
					webRtcPeer.createOffer(offerOptions).then(function(desc){
						//console.log("creating offer:",desc);
						var sdp = setBandwidth(desc.sdp);
						var updatedDesc = {
							"type":"offer",
							"sdp":sdp
						};
						webRtcPeer.setLocalDescription(updatedDesc);
						initiateVideoStream(videoType,updatedDesc.sdp,id);
					});
				}).catch(function(error){
					console.log("error creating send behaviour:",error);
				});
			}
		} else if (recv){
			//webRtcPeer.createAnswer().then(function(desc){
			webRtcPeer.createOffer(offerOptions).then(function(desc){
				//console.log("creating answer:",desc);
				var sdp = setBandwidth(desc.sdp);
				var updatedDesc = {
					"type":"offer",
					"sdp":sdp
				};
				webRtcPeer.setLocalDescription(updatedDesc);
				initiateVideoStream(videoType,updatedDesc.sdp,id);
			}).catch(function(error){
				console.log("error while creating non-send behaviour:",error);
			});
		}
		var addIceCandidateFunc = function(candidate){
			webRtcPeer.addIceCandidate(new RTCIceCandidate(candidate));
		};
		var processAnswerFunc = function(answer){
			//console.log("processing answer func:",answer,answer.sdpAnswer);
			webRtcPeer.setRemoteDescription(new RTCSessionDescription({
				"type":"answer",
				"sdp":setBandwidth(answer.sdpAnswer)
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
		listen:listenFunc,
		roulette:rouletteFunc,
		groupchat:groupChatFunc,
		removeVideoStream:removeFunc,
		getActiveSessions:function(){return activeVideoClients;}
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
	var stream = WebRtcStreamManager.groupchat(id);// VideoStream(undefined,sessionId);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
}


function receiveKurentoAnswer(answer,id){
	//console.log("receiveKurentoAnswer",answer);
	WebRtcStreamManager.receiveAnswer(id,answer);
}
function receiveKurentoIceCandidate(candidate,id){
	var iceCandidate = JSON.parse(candidate.iceCandidateJsonString).candidate;
	//console.log("receiveCandidate",iceCandidate);
	WebRtcStreamManager.receiveIceCandidate(id,iceCandidate);
}
//injected by lift
//function initiateVideoStream(videoType,offer,id){}
//function sendVideoStreamIceCandidate(iceCandidate,id){}
//function shutdwnVideoStream(id){}
