var WebRtcStreamManager = (function(){
	/*
	// set up browser shims
	if ("adapter" in window){
		if ("browserShim" in window.adapter){
			var bs = window.adapter.browserShim;
			bs.shimPeerConnection();
			bs.shimGetUserMedia();
			bs.shimSourceObject();
			bs.shimOnTrack();
//			bs.shimMediaStream();
		}
	}
*/
	var videoElemWidth = 240, videoElemHeight = 180;
	var defaultConstraints = {
		audio:true,
		/*
		video:{
			width:{
				min:"160",
				max:"320"
			},
			height:{
				min:"88",
				max:"240"
			},
			frameRate:{
				min:"10",
				max:"25"
			}
		}
		*/
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
		var audioBandwidth = "50", videoBandwidth = "200";

		sdp = sdp.replace(/a=mid:audio\r\n/g, 'a=mid:audio\r\nb=AS:' + audioBandwidth + '\r\n');
		sdp = sdp.replace(/a=mid:video\r\n/g, 'a=mid:video\r\nb=AS:' + videoBandwidth + '\r\n');
		return sdp;
	}
	
	var activeVideoClients = {}; 
	
	var createVideoStream = function(id,videoType,send,recv,constraints){
		var remoteStream = undefined, localStream = undefined;
		var configuration = {
			iceServers:[
				{
					url:"stun:kurento.stackableregiments.com"
			 	},
				{
					url:"turn:kurento.stackableregiments.com:3478?transport:udp",
					username:"kurento",
					credential:"kurento"
				},
				{
					url:"turn:kurento.stackableregiments.com:3478?transport:tcp",
					username:"kurento",
					credential:"kurento"
				}
			]

		};
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
			console.log("remoteStream arrived:",remoteE,remoteE.stream.getVideoTracks(),remoteE.stream.getAudioTracks());
			if (recv){
				remoteVideo.srcObject = remoteE.stream;
				remoteStream = remoteE.stream;
				remoteVideo.play();
			}
		};
		webRtcPeer.onicecandidate = function(e){
			if (e != null && "candidate" in e && e.candidate != null && "candidate" in e.candidate && e.candidate.candidate.indexOf("relay")<0){
				return;
			} else {
				console.log("ice candidate generated:",id,e.candidate);
				sendVideoStreamIceCandidate(e.candidate,id);
			}
		};
		webRtcPeer.onnegotiationeeded = function(negError) {
			console.log("local negotiation requested, error:",negError); //not sure what to do about negotiation yet
		};
		if (send){
			if ("mediaDevices" in navigator && "getUserMedia" in navigator.mediaDevices){
				navigator.mediaDevices.getUserMedia(constraints ? constraints : defaultConstraints).then(function(stream){
					localStream = stream;
					localVideo.srcObject = stream;
					localVideo.muted = true; // we don't want to build a local feedback loop
					webRtcPeer.addStream(stream);
					localVideo.play();
					webRtcPeer.createOffer(offerOptions).then(function(desc){
						console.log("creating offer:",desc);
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
				console.log("creating answer:",desc);
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
		var earlyIceCandidates = [];
		var addIceCandidateFunc = function(candidate){
			if ("candidate" in candidate && candidate.candidate.indexOf("relay")<0){
				return;
			} else {
				console.log("receiveCandidate",candidate);
				if (remoteDescSet){
					while (_.size(earlyIceCandidates) > 0){
						webRtcPeer.addIceCandidate(new RTCIceCandidate(earlyIceCandidates.shift()));
					}
					webRtcPeer.addIceCandidate(new RTCIceCandidate(candidate));
				} else {
					earlyIceCandidates.push(candidate);
					console.log("adding ice candidates early");
				}
			}
		};
		var remoteDescSet = false;
		var processAnswerFunc = function(answer,after){
			//console.log("processing answer func:",answer,answer.sdpAnswer);
			webRtcPeer.setRemoteDescription(new RTCSessionDescription({
				"type":"answer",
				"sdp":setBandwidth(answer.sdpAnswer)
			}));
			remoteDescSet = true;
			if (after != undefined){
				after();
			}
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
	var receiveAnswerFunc = function(id,answer,after){
		if (id in activeVideoClients){
			activeVideoClients[id].processAnswer(answer,after);
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
	var stream = WebRtcStreamManager.loopback();
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
	return stream;
}
var BroadcastTest = function(id){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.broadcast(id);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
	return stream;
}
var ListenTest = function(id){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.listen(id);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
	return stream;
}
var RouletteTest = function(id){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.roulette(id);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
	return stream;
}
var GroupRoomTest = function(id){
	var videoSelector = "#masterHeader";
	var stream = WebRtcStreamManager.groupchat(id);
	var localVideo = stream.getLocalVideo();
	var remoteVideo = stream.getRemoteVideo();
	$(videoSelector).append(localVideo).append(remoteVideo);
	return stream;
}


function receiveKurentoAnswer(answer,id){
	console.log("receiveKurentoAnswer",answer);
	WebRtcStreamManager.receiveAnswer(id,answer);
}
function receiveKurentoIceCandidate(candidate,id){
	var iceCandidate = JSON.parse(candidate.iceCandidateJsonString).candidate;
//	console.log("receiveCandidate",iceCandidate);
	WebRtcStreamManager.receiveIceCandidate(id,iceCandidate);
}
function receiveKurentoChannelDefinition(channelDefinition,id){
	console.log("IceDefinition received: ",channelDefinition);
	WebRtcStreamManager.receiveAnswer(id,channelDefinition,function(){
		_.forEach(channelDefinition.candidates,function(candidate){
			var iceCandidate = JSON.parse(candidate.iceCandidateJsonString).candidate;
			WebRtcStreamManager.receiveIceCandidate(id,iceCandidate);
		});
	});
}
//injected by lift
//function initiateVideoStream(videoType,offer,id){}
//function sendVideoStreamIceCandidate(iceCandidate,id){}
//function shutdwnVideoStream(id){}
