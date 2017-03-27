var Plugins = (function(){
    return {
        "Chat":(function(){
            var chatMessages = {};
            var outer = {};
            var cmHost = {};
            var cmTemplate = {};
            var containerId = sprintf("chatbox_%s",_.uniqueId());
            var container = $("<div />",{
                id:containerId
            });
            var renderChatMessage = function(chatMessage,targetType,target,context){
                var rootElem = cmTemplate.clone();
                var username = UserSettings.getUsername();
                var authorElem = rootElem.find(".chatMessageAuthor");
                authorElem.text(chatMessage.author);
                rootElem.find(".chatMessageTimestamp").text(new Date(chatMessage.timestamp).toISOString());
                var contentElem = rootElem.find(".chatMessageContent");
                switch (chatMessage.contentType){
                case "text":
                    contentElem.text(chatMessage.content);
                    break;
                case "html":
                    contentElem.html(chatMessage.content);
                    break;
                }
                if (targetType && target){
                    switch (targetType){
                    case "whisperTo":
                        contentElem.addClass("whisper");
                        authorElem.text(sprintf("to %s",target));
                        break;
                    case "whisperFrom":
                        contentElem.addClass("whisper");
                        authorElem.text(sprintf("from %s",target));
                        break;
                    case "groupChatTo":
                        contentElem.addClass("groupChat");
                        authorElem.text(sprintf("to %s",target));
                        break;
                    case "groupChatFrom":
                        contentElem.addClass("groupChat");
                        authorElem.text(sprintf("from %s",target));
                        break;
                    }
                }
                return rootElem;
            };
            var actOnStanzaReceived = function(stanza){
                if (stanza && "type" in stanza && stanza.type == "chatMessage" && "identity" in stanza){
                    if (stanza.identity in chatMessages){
                        //ignore this one, I've already got it.
                    } else {
                        chatMessages[stanza.identity] = stanza;
                        var username = UserSettings.getUsername();
                        var convGroups = _.flatten(_.flatten(_.map(Conversations.getCurrentConversation().slides,function(slide){
                            return _.map(slide.groupSets,function(groupSet){
                                return groupSet.groups;
                            });
                        })));
                        boardContent.chatMessages.push(stanza);
                        if (!stanza.audiences.length){ //it's a public message
                            cmHost.append(renderChatMessage(stanza));
                            cmHost.scrollTop(cmHost[0].scrollHeight);
                        } else { // it's targetted
                            var relAud = _.find(stanza.audiences,function(aud){
                                return aud.type == "user" || aud.type == "group";
                            });
                            if (stanza.author == username){ //it's from me
                                if (relAud && relAud.type == "user"){
                                    cmHost.append(renderChatMessage(stanza,"whisperTo",relAud.name));
                                    cmHost.scrollTop(cmHost[0].scrollHeight);
                                } else if (relAud && relAud.type == "group"){
                                    cmHost.append(renderChatMessage(stanza,"groupChatTo",relAud.name));
                                    cmHost.scrollTop(cmHost[0].scrollHeight);
                                }
                            } else { // it's possibly targetted to me
                                if (relAud && relAud.type == "user" && relAud.name == username){
                                    cmHost.append(renderChatMessage(stanza,"whisperFrom",stanza.author));
                                    cmHost.scrollTop(cmHost[0].scrollHeight);
                                } else if (relAud && relAud.type == "group" && _.some(convGroups,function(g){ return g.name == relAud.name && _.some(g.members,function(m){ return m.name == username; });})){
                                    cmHost.append(renderChatMessage(stanza,"groupChatFrom",stanza.author,relAud.name));
                                    cmHost.scrollTop(cmHost[0].scrollHeight);
                                }
                            }
                        }
                    }
                }
            };
            var actOnHistoryReceived = function(history){
                _.forEach(history.chatMessages,actOnStanzaReceived);
            };
            var createChatMessage = function(text,context,audiences){
                var author = UserSettings.getUsername();
                var loc = Conversations.getCurrentSlideJid();
                var now = new Date().getTime();
                var id = sprintf("%s_%s_%s",author,loc,now);
                var cm = {
                    type:"chatMessage",
                    author:author,
                    timestamp:now,
                    identity:id,
                    contentType:"text",
                    content:text,
                    context:context || loc,
                    audiences:audiences || []
                };
                console.log("created chat message:",cm);
                return cm;
            };
            var sendChatMessage = function(text){
                if (text && text.length){
                    var audiences = [];
                    var context = "";
                    var message = "";
                    if (_.startsWith(text,"/w")){
                        var parts = text.split(" ");
                        if (parts.length && parts.length >= 2){
                            audiences.push({
                                domain:"metl",
                                name:parts[1],
                                type:"user",
                                action:"read"
                            });
                            message = _.drop(parts,2).join(" ");
                        } else {
                            return text;
                        }
                    } else if (_.startsWith(text,"/g")){
                        var parts = text.split(" ");
                        if (parts.length && parts.length >= 2){
                            audiences.push({
                                domain:"metl",
                                name:parts[1],
                                type:"group",
                                action:"read"
                            });
                            message = _.drop(parts,2).join(" ");
                        } else {
                            return text;
                        }
                    } else {
                        message = text;
                    }
                    sendStanza(createChatMessage(message,context,audiences));
                    return "";
                } else {
                    return text;
                }
            };

            return {
                style:".chatMessageContainer {overflow-y:auto; flex-grow:1;}"+
                    ".chatContainer {margin-left:1em;width:320px;height:140px;display:flex;flex-direction:column;}"+
                    ".chatMessageAuthor {color:slategray;margin-right:1em;}"+
                    ".chatMessageTimestamp {color:red;font-size:small;display:none;}"+
                    ".chatboxContainer {display:flex;flex-direction:row;width:100%;flex-shrink:0;}"+
                    ".chatboxContainer input{flex-grow:1;}"+
                    ".chatbox {background-color:white;display:inline-block; padding:0px; margin:0px;}"+
                    ".chatboxSend {display:inline-block; background:white;padding:0px; margin:0px;}"+
                    ".groupChat {color:darkorange}"+
                    ".whisper {color:darkblue}",
                load:function(bus,params){
                    bus.stanzaReceived["Chatbox"] = actOnStanzaReceived;
                    bus.historyReceived["Chatbox"] = actOnHistoryReceived;
                    container.append('<div class="chatContainer" >'+
                                     '<div class="chatMessageContainer" >'+
                                     '<div class="chatMessage" >'+
                                     '<span class="chatMessageTimestamp" >'+
                                     '</span>'+
                                     '<span class="chatMessageAuthor" >'+
                                     '</span>'+
                                     '<span class="chatMessageContent">'+
                                     '</span>'+
                                     '</div>'+
                                     '</div>'+
                                     '<div class="chatboxContainer">'+
                                     '<input type="text" class="chatbox">'+
                                     '</input>'+
                                     '<button class="chatboxSend">Send</button>'+
                                     '</div>'+
                                     '</div>');
                    return container;
                },
                initialize:function(){
                    outer = $("#"+containerId);
                    cmHost = outer.find(".chatMessageContainer");
                    cmTemplate = cmHost.find(".chatMessage").clone();
                    cmHost.empty();
                    var chatbox = outer.find(".chatboxContainer .chatbox").on("keydown",function(ev){
                        if (ev.keyCode == 13){
                            var cb = $(this);
                            cb.val(sendChatMessage(cb.val()));
                        }
                    });
                    var sendButton = outer.find(".chatboxContainer .chatboxSend").on("click",function(){
                        chatbox.val(sendChatMessage(chatbox.val()));
                    });
                }
            };
        })(),
				"Vidyo":(function(){
					var vidyoContainer = $("<div />");
					var isEnabled = false;

					var redrawOptionsDialog = function(){};

					//vidyoContainer.append(cameraSelector).append(microphoneSelector).append(speakerSelector).append(cameraMuteButton).append(microphoneMuteButton);


					var maximumRemoteParticipants = 16; // Maximum number of participants
					var compositeViewStyle = "VIDYO_CONNECTORVIEWSTYLE_Default"; // Visual style of the composited renderer
					//var vidyoLogFileFilter = "warning all@VidyoConnector info@VidyoClient";
					var vidyoLogFileFilter = "warning";
					var vidyoUserData = ""

					var vidyoClient = undefined; // this should have been constructed already by the onload behaviour
					var vidyoToken = undefined; // this should have already been set.
					var vidyoHost = undefined; // this should be... prod.vidyo.io?  I don't know.  I'm still trying to work out where I get that set from.


					var sessions = {};

					var cameras = {};
					var selectedCamera = undefined;
					var microphones = {};
					var selectedMicrophone = undefined;
					var speakers = {};
					var selectedSpeaker = undefined;
					var cameraPrivacy = false;
					var microphonePrivacy = false;

					var conferences = {};

					var options = $("<button />",{
						text:"options"
					}).on("click",function(){

						var uniqId = sprintf("vidyoOptionsDialog_%s",_.uniqueId());
						var outerDiv = $("<div/>",{
								id:uniqId
						});
						var optionsAlert = $.jAlert({
								title:"Change Vidyo options",
								width:"80%",
								content:outerDiv[0].outerHTML,
								onClose:function(){
									redrawOptionsDialog = function(){};
								}
						});
						var cameraSelector = $("<select />",{});
						var microphoneSelector = $("<select />",{});
						var speakerSelector = $("<select />",{});	

						var cameraMuteButton = $("<button />",{text:"camera mute"});
						var microphoneMuteButton = $("<button />",{text:"microphone mute"});
						
						var innerDiv = $("#"+uniqId);
						innerDiv.append(cameraSelector).append(microphoneSelector).append(speakerSelector).append(cameraMuteButton).append(microphoneMuteButton);

						cameraMuteButton.click(function() {
								// CameraPrivacy button clicked
								cameraPrivacy = !cameraPrivacy;
								_.forEach(conferences,function(conference){
									conference.muteCamera(cameraPrivacy);
								});
								if (cameraPrivacy) {
										cameraMuteButton.addClass("cameraOff").removeClass("cameraOn");
								} else {
										cameraMuteButton.addClass("cameraOn").removeClass("cameraOff");
								}
						});

						// Handle the microphone mute button, toggle between mute and unmute audio.
						microphoneMuteButton.click(function() {
								// MicrophonePrivacy button clicked
								microphonePrivacy = !microphonePrivacy;
								_.forEach(conferences,function(conference){
									conference.muteMicrophone(microphonePrivacy);
								});
								if (microphonePrivacy) {
										microphoneMuteButton.addClass("microphoneOff").removeClass("microphoneOn");
								} else {
										microphoneMuteButton.addClass("microphoneOn").removeClass("microphoneOff");
								}
						});


						var redrawState = function(){
							cameraSelector.html(_.map(cameras,function(camera,cameraId){
								return $("<option />",{value:cameraId,text:camera.name}).prop("selected",cameraId == selectedCamera);
							}));
							microphoneSelector.html(_.map(microphones,function(microphone,microphoneId){	
								return $("<option />",{value:microphoneId,text:microphone.name}).prop("selected",microphoneId == selectedMicrophone);
							}));
							speakerSelector.html(_.map(speakers,function(speaker,speakerId){
								return $("<option />",{value:speakerId,text:speaker.name}).prop("selected",speakerId == selectedSpeaker);
							}));
						};
						redrawState();
						redrawOptionsDialog = redrawState;
						cameraSelector.change(function() {
								// Camera selected form the drop-down menu
								cameraSelector.find("option:selected").each(function() {
										camera = cameras[$(this).val()];
										_.forEach(conferences,function(conference){
											conference.getVidyoConnector().SelectLocalCamera({
													localCamera: camera
											}).then(function() {
													console.log("SelectCamera Success");
											}).catch(function() {
													console.error("SelectCamera Failed");
											});
										});
								});
						});

						// Hook up microphone selector functions for each of the available microphones
						microphoneSelector.change(function() {
								// Microphone selected form the drop-down menu
								microphoneSelector.find("option:selected").each(function() {
										microphone = microphones[$(this).val()];
										_.forEach(conferences,function(conference){
											conference.getVidyoConnector().SelectLocalMicrophone({
													localMicrophone: microphone
											}).then(function() {
													console.log("SelectMicrophone Success");
											}).catch(function() {
													console.error("SelectMicrophone Failed");
											});
										});
								});
						});

						// Hook up speaker selector functions for each of the available speakers
						speakerSelector.change(function() {
								// Speaker selected form the drop-down menu
								speakerSelector.find("option:selected").each(function() {
										speaker = speakers[$(this).val()];
										_.forEach(conferences,function(conference){
											conference.getVidyoConnector().SelectLocalSpeaker({
													localSpeaker: speaker
											}).then(function() {
													console.log("SelectSpeaker Success");
											}).catch(function() {
													console.error("SelectSpeaker Failed");
											});
										});
								});
						});
					});
					vidyoContainer.append(options);

					// Run StartVidyoConnector when the VidyoClient is successfully loaded
					var StartVidyoConnector = function(vidyoResourceId) {
							var vidyoConnected = false;
							var videoRendererId = sprintf("vidyoRenderer_%s",_.uniqueId()); // Div ID where the composited video will be rendered, see VidyoConnectorSample.html
							var videoRenderer = $("<div />",{id:videoRendererId,height:"250px",width:"250px"});
							vidyoContainer.append(videoRenderer);

							var vidyoConnector;
							var callState = "IDLE"
							var joinLeave = function() {
								console.log("joinLeave:",vidyoConnected);
									// join or leave dependent on the joinLeaveButton, whether it
									// contains the class callStart of callEnd.
								if (!vidyoConnected) {
									vidyoConnected = true;
									connectFunc();
								} else {
									disconnectFunc();
								}
							}
							var connectFunc = function() {
									// Clear messages
									var vidyoDisplayName = "UserSettings" in window ? UserSettings.getUsername() : "unknown";
									console.log("connectToConference:",vidyoConnector,vidyoHost,vidyoToken,vidyoDisplayName,vidyoResourceId);
									vidyoConnector.Connect({
											// Take input from options form
											host: vidyoHost,
											token: vidyoToken,
											displayName: vidyoDisplayName,
											resourceId: vidyoResourceId,

											// Define handlers for connection events.
											onSuccess: function() {
													// Connected
													console.log("vidyoConnector.Connect : onSuccess callback received");
											},
											onFailure: function(reason) {
													// Failed
													console.error("vidyoConnector.Connect : onFailure callback received",reason);
													connectorDisconnected("Failed", "");
											},
											onDisconnected: function(reason) {
													// Disconnected
													console.log("vidyoConnector.Connect : onDisconnected callback received",reason);
													connectorDisconnected("Disconnected", "Call Disconnected: " + reason);
											}
									}).then(function(status) {
											if (status) {
													console.log("Connect Success");
											} else {
													console.error("Connect Failed");
													connectorDisconnected("Failed", "");
											}
									}).catch(function() {
											console.error("Connect Failed");
											connectorDisconnected("Failed", "");
									});
							}


							var disconnectFunc = function(){
								vidyoConnector.Disconnect().then(function() {
									vidyoConnected = false;
										console.log("Disconnect Success");
								}).catch(function() {
										console.error("Disconnect Failure");
								});
							};

							var registerDeviceListeners = function() {
									// Handle appearance and disappearance of camera devices in the system
									vidyoConnector.RegisterLocalCameraEventListener({
											onAdded: function(localCamera) {
													// New camera is available
													var safeCameraId = window.btoa(localCamera.id);
													cameras[safeCameraId] = localCamera;
													redrawOptionsDialog();
											},
											onRemoved: function(localCamera) {
													// Existing camera became unavailable
													var safeCameraId = window.btoa(localCamera.id);
													delete cameras[safeCameraId];
													redrawOptionsDialog();
											},
											onSelected: function(localCamera) {
													// Camera was selected/unselected by you or automatically
													if(localCamera) {
														var safeCameraId = window.btoa(localCamera.id);
														selectedCamera = safeCameraId;
													} else {
														selectedCamera = undefined;
													}
													redrawOptionsDialog();
											},
											onStateUpdated: function(localCamera, state) {
													// Camera state was updated
											}
									}).then(function() {
											console.log("RegisterLocalCameraEventListener Success");
									}).catch(function() {
											console.error("RegisterLocalCameraEventListener Failed");
									});

									// Handle appearance and disappearance of microphone devices in the system
									vidyoConnector.RegisterLocalMicrophoneEventListener({
											onAdded: function(localMicrophone) {
													// New microphone is available
													var safeMicId = window.btoa(localMicrophone.id);
													microphones[safeMicId] = localMicrophone;
													redrawOptionsDialog();
											},
											onRemoved: function(localMicrophone) {
													// Existing microphone became unavailable
													var safeMicId = window.btoa(localMicrophone.id);
													delete microphones[safeMicId];
													redrawOptionsDialog();
											},
											onSelected: function(localMicrophone) {
													// Microphone was selected/unselected by you or automatically
													if (localMicrophone){
														var safeMicId = window.btoa(localMicrophone.id);
														selectedMicrophone = safeMicId;
													} else {
														selectedMicrophone = undefined;
													}
													redrawOptionsDialog();
											},
											onStateUpdated: function(localMicrophone, state) {
													// Microphone state was updated
											}
									}).then(function() {
											console.log("RegisterLocalMicrophoneEventListener Success");
									}).catch(function() {
											console.error("RegisterLocalMicrophoneEventListener Failed");
									});

									// Handle appearance and disappearance of speaker devices in the system
									vidyoConnector.RegisterLocalSpeakerEventListener({
											onAdded: function(localSpeaker) {
													// New speaker is available
													var safeSpeakerId = window.btoa(localSpeaker.id);
													speakers[safeSpeakerId] = localSpeaker;
													redrawOptionsDialog();
											},
											onRemoved: function(localSpeaker) {
													// Existing speaker became unavailable
													var safeSpeakerId = window.btoa(localSpeaker.id);
													delete speakers[safeSpeakerId];
													redrawOptionsDialog();
											},
											onSelected: function(localSpeaker) {
													// Speaker was selected/unselected by you or automatically
													if(localSpeaker){
														var safeSpeakerId = window.btoa(localSpeaker.id);
														selectedSpeaker = safeSpeakerId;
													} else {
														selectedSpeaker = undefined;
													}
													redrawOptionsDialog();
											},
											onStateUpdated: function(localSpeaker, state) {
													// Speaker state was updated
											}
									}).then(function() {
											console.log("RegisterLocalSpeakerEventListener Success");
									}).catch(function() {
											console.error("RegisterLocalSpeakerEventListener Failed");
									});
							}
							
							var handleParticipantChange = function() {
									vidyoConnector.RegisterParticipantEventListener({
											onJoined: function(participant) {
													getParticipantName(participant, function(name) {
														console.log("vidyo particpantJoined:",participant,name);
													});
											},
											onLeft: function(participant) {
													getParticipantName(participant, function(name) {
														console.log("vidyo particpantLeft:",participant,name);
													});
											},
											onDynamicChanged: function(participants, cameras) {
													console.log("vidyo onDynamicsChanged:",participants,cameras);
													// Order of participants changed
											},
											onLoudestChanged: function(participant, audioOnly) {
													getParticipantName(participant, function(name) {
															console.log("vidyo onLoudestChanged:",participant,audioOnly,name);
													});
											}
									}).then(function() {
											console.log("RegisterParticipantEventListener Success");
									}).catch(function() {
											console.err("RegisterParticipantEventListener Failed");
									});
							}

							vidyoClient.CreateVidyoConnector({
									viewId: videoRendererId, 
									viewStyle: compositeViewStyle, 
									remoteParticipants: maximumRemoteParticipants,     
									logFileFilter: vidyoLogFileFilter,
									logFileName:"",
									userData:vidyoUserData
							}).then(function(vc) {
									vidyoConnector = vc;
									registerDeviceListeners();
									handleParticipantChange();
									joinLeave();
							}).catch(function(err) {
									console.error("CreateVidyoConnector Failed " + err);
							});

							/*
							// Handle the camera privacy button, toggle between show and hide.
							cameraMuteButton.click(function() {
									// CameraPrivacy button clicked
									cameraPrivacy = !cameraPrivacy;
									vidyoConnector.SetCameraPrivacy({
											privacy: cameraPrivacy
									}).then(function() {
											if (cameraPrivacy) {
													cameraMuteButton.addClass("cameraOff").removeClass("cameraOn");
											} else {
													cameraMuteButton.addClass("cameraOn").removeClass("cameraOff");
											}
											console.log("SetCameraPrivacy Success");
									}).catch(function() {
											console.error("SetCameraPrivacy Failed");
									});
							});

							// Handle the microphone mute button, toggle between mute and unmute audio.
							microphoneMuteButton.click(function() {
									// MicrophonePrivacy button clicked
									microphonePrivacy = !microphonePrivacy;
									vidyoConnector.SetMicrophonePrivacy({
											privacy: microphonePrivacy
									}).then(function() {
											if (microphonePrivacy) {
													microphoneMuteButton.addClass("microphoneOff").removeClass("microphoneOn");
											} else {
													microphoneMuteButton.addClass("microphoneOn").removeClass("microphoneOff");
											}
											console.log("SetMicrophonePrivacy Success");
									}).catch(function() {
											console.error("SetMicrophonePrivacy Failed");
									});
							});
							*/
							return {
								getVidyoConnector:function(){ 
									return vidyoConnector;
								},
								getConnected:function(){
									return vidyoConnected;
								},
								toggleConnection:function(){
									return joinLeave();
								},
								connect:connectFunc,
								disconnect:disconnectFunc,
								muteCamera:function(muted){
									vidyoConnector.SetCameraPrivacy({
											privacy: muted
									}).then(function() {
											console.log("SetCameraPrivacy Success",muted);
									}).catch(function() {
											console.error("SetCameraPrivacy Failed");
									});

								},
								muteMicrophone:function(muted){
									vidyoConnector.SetMicrophonePrivacy({
											privacy: muted
									}).then(function() {
											console.log("SetMicrophonePrivacy Success",muted);
									}).catch(function() {
											console.error("SetMicrophonePrivacy Failed");
									});

								}
							};
					}
					var startConferenceFunc = function(resourceId){
						if (isEnabled && vidyoToken !== undefined){
							var vc = StartVidyoConnector(resourceId,vidyoClient);
							conferences[resourceId] = vc;
						}
					};	
					var getParticipantName = function(participant, cb) {
						if (!participant) {
								cb("Undefined");
						} else if (participant.name) {
								cb(participant.name);
						} else {
							participant.GetName().then(function(name) {
									cb(name);
							}).catch(function() {
									cb("GetNameFailed");
							});
						}
					}
					// Connector either fails to connect or a disconnect completed, update UI elements
					var connectorDisconnected = function(connectionStatus, message) {
						console.log("vidyo connector disconnected:",connectionStatus,message);
					}
					// Attempt to connect to the conference
					// We will also handle connection failures
					// and network or server-initiated disconnects.


					window.onVidyoClientLoaded = function(status) {
						console.log("Status: " + status.state + "Description: " + status.description);
						switch (status.state) {
							case "READY":    // The library is operating normally
								vidyoClientReady = true;
								console.log("vidyo ready:",status);
								// After the VidyoClient is successfully initialized a global VC object will become available 
								// All of the VidyoConnector gui and logic is implemented in VidyoConnector.js
								vidyoClient = VC;
								getVidyoSession();
								break;
							case "RETRYING": // The library operating is temporarily paused
								vidyoClientReady = false;
								console.log("vidyo retrying:",status);
								break;
							case "FAILED":   // The library operating has stopped
								vidyoClientReady = false;
								console.log("vidyo failed:",status);
								break;
							case "FAILEDVERSION":   // The library operating has stopped
								vidyoClientReady = false;
								console.log("vidyo version failed:",status);
								break;
							case "NOTAVAILABLE": // The library is not available
								vidyoClientReady = false;
								console.log("vidyo library unavailable:",status);
								break;
						}
						return true; // Return true to reload the plugins if not available
					}
					
					function loadVidyoClientLibrary(webrtc, plugin) {
						//We need to ensure we're loading the VidyoClient library and listening for the callback.
						var script = document.createElement('script');
						script.type = 'text/javascript';
						script.src = 'https://static.vidyo.io/4.1.8.1/javascript/VidyoClient/VidyoClient.js?onload=onVidyoClientLoaded&webrtc=' + webrtc + '&plugin=' + plugin;    
						document.getElementsByTagName('head')[0].appendChild(script);
					}
					
					function loadVidyoLibrary() {
						var userAgent = navigator.userAgent || navigator.vendor || window.opera;

						// Opera 8.0+
						var isOpera = (userAgent.indexOf("Opera") || userAgent.indexOf('OPR')) != -1 ;
						// Firefox
						var isFirefox = userAgent.indexOf("Firefox") != -1;
						// Chrome 1+
						var isChrome = userAgent.indexOf("Chrome") != -1;
						// Safari 
						var isSafari = !isChrome && userAgent.indexOf("Safari") != -1;
						// AppleWebKit 
						var isAppleWebKit = !isSafari && !isChrome && userAgent.indexOf("AppleWebKit") != -1;
						// Internet Explorer 6-11
						var isIE = (userAgent.indexOf("MSIE") != -1 ) || (!!document.documentMode == true );
						// Edge 20+
						var isEdge = !isIE && !!window.StyleMedia;
						// Check if Mac
						var isMac = navigator.platform.indexOf('Mac') > -1;
						// Check if Windows
						var isWin = navigator.platform.indexOf('Win') > -1;
						// Check if Linux
						var isLinux = navigator.platform.indexOf('Linux') > -1;
						// Check if Android
						var isAndroid = userAgent.indexOf("android") > -1;

						var protocolHandlerLink = 'vidyoconnector://' + window.location.search;
						if (!isMac && !isWin && !isLinux) {
							if (isChrome && isAndroid) {
								/* Supports WebRTC */
								loadVidyoClientLibrary(true, false);
							} else {
								$("#helperAppLoader").attr('src', protocolHandlerLink);
								loadVidyoClientLibrary(false, false);
							} 
						} else {
							if (isChrome || isFirefox) {
								/* Supports WebRTC */
								loadVidyoClientLibrary(true, false);
							} else if (isSafari || isFirefox || (isAppleWebKit && isMac) || (isIE && !isEdge)) {
								/* Supports Plugins */
								loadVidyoClientLibrary(false, true);
							} else {
								$("#helperAppLoader").attr('src', protocolHandlerLink);
								loadVidyoClientLibrary(false, false);
							}
						}
					}
					// Runs when the page loads
					$(function() {
					});
					loadVidyoLibrary();
					window.Vidyo = {
						receiveVidyoEnabled:function(ie){
							isEnabled = ie;
						},
						receiveVidyoSessionToken:function(st){
							if ("sessionToken" in st){
								vidyoHost = "prod.vidyo.io";
								vidyoToken = st.sessionToken;
								console.log("setting sessionToken:",st,vidyoToken);
								if ("Conversations" in window){
									startConferenceFunc(Conversations.getCurrentConversationJid()); // this is an auto-start behaviour - let's not keep that in once we're working, but I'm adding it while debugging.
								}
							}
						},
						startConference:function(roomName){
							startConferenceFunc(roomName); // this is going to be the primary entry point, I think.
						},
						getConferences:function(){
							return conferences;
						}
					};
					window.receiveVidyoEnabled = function(isEnabled){
						console.log("vidyoEnabled:",isEnabled);
						Vidyo.receiveVidyoEnabled(isEnabled);
					}
					window.receiveVidyoSessionToken = function(sessionToken){
						console.log("vidyoSessionToken:",sessionToken);
						Vidyo.receiveVidyoSessionToken(sessionToken);
					}

					//injected by lift
					/*
					var getVidyoSession = function(){
						receiveVidyoSessionToken(token);
					}
					*/
					return {
						style:"",
						load:function(bus,params){
							//return the html in here.  This fires first, onload
							console.log("vidyo.load:",vidyoContainer,window.Vidyo,window.receiveVidyoEnabled,window.receiveVidyoSessionToken);
							return vidyoContainer;
						},
						initialize:function(){
							//any setup in here.  This fires on a context which has already loaded.
							console.log("vidyo.initialize");
						}
					};
				})(),
        "Face to face":(function(){
            var container = $("<div />");
            return {
                style:".publishedStream {background:green;} .subscribedStream {background:red;}"+
                    " .videoConfStartButton, .videoConfSubscribeButton, .videoConfPermitStudentBroadcastButton {background:white;margin:1px 0;}"+
                    " .videoConfSessionContainer, .videoConfStartButtonContainer, .videoConfContainer, .videoConfPermitStudentBroadcastContainer{display:flex;}"+
                    " .videoConfStartButtonContainer, .videoConfPermitStudentBroadcastContainer{flex-direction:row;}"+
                    " .videoConfStartButton, .videoConfPermitStudentBroadcastButton{padding:0 1em;font-size:1rem;}"+
                    " #videoConfSessionsContainer{display:none;}"+
                    " .videoContainer{display:flex;}"+
                    " .context, .publisherName{font-size:1rem;}"+
                    " .thumbWide{width:160px;}"+
                    " .broadcastContainer{display:none;}",
                load:function(bus,params){
                    container.append('<span id="videoConfSessionsContainer">'+
                                     '<div class="videoConfSessionContainer">'+
                                     '<div>'+
                                     '<div class="videoConfStartButtonContainer" style="margin-bottom:-0.3em">'+
                                     '<button class="videoConfStartButton">'+
                                     '<div>Start sending</div>'+
                                     '</button>'+
                                     '<span class="context mr"></span>'+
                                     '<span style="display:none;" class="teacherControls mr">'+
                                     '<input type="checkbox" id="canBroadcast">'+
                                     '<label for="canBroadcast" class="checkbox-sim"><span class="icon-txt">Students can stream</span></label>'+
                                     '</span>'+
                                     '</div>'+
                                     '<div class="viewscreen"></div>'+
                                     '</div>'+
                                     '<div class="broadcastContainer">'+
                                     '<a class="floatingToolbar btn-menu fa fa-television btn-icon broadcastLink">'+
                                     '<div class="icon-txt">Watch class</div>'+
                                     '</a>'+
                                     '</div>'+
                                     '<div class="videoSubscriptionsContainer"></div>'+
                                     '<div class="videoConfContainer">'+
                                     '<span class="videoContainer thumbWide">'+
                                     '<button class="videoConfSubscribeButton">'+
                                     '<div>Toggle</div>'+
                                     '</button>'+
                                     '<span class="publisherName"></span>'+
                                     '</span>'+
                                     '</div>'+
                                     '</div>'+
                                     '</span>');
                    return container;
                },
                initialize:TokBox.initialize
            }
        })(),
        "Groups":(function(){
            var overContainer = $("<div />");
            var button = function(icon,content,behaviour){
                var b = $("<button />",{
                    class:sprintf("%s btn-icon fa",icon),
                    click:behaviour
                });
                $("<div />",{
                    class:"icon-txt",
                    text:content
                }).appendTo(b);
                return b;
            };
            return {
                style:".groupsPluginMember{margin-left:0.5em;display:flex;}"+
                    " .groupsPluginGroupContainer{display:flex;margin-right:1em;}"+
                    " .groupsPluginGroup{display:inline-block;text-align:center;vertical-align:top;}"+
                    " .groupsPluginGroupGrade button, .groupsPluginGroupGrade .icon-txt{padding:0;margin-top:0;}"+
                    " .groupsPluginGroupControls button, .groupsPluginGroupControls .icon-txt{padding:0;margin-top:0;}"+
                    " .isolateGroup label{margin-top:1px;}"+
                    " .isolateGroup{margin-top:0.8em;}"+
                    " .rowT{display:table;width:100%;}"+
                    " .rowC{display:table-row;}"+
                    " .rowC *{display:table-cell;}"+
                    " .rowC label{text-align:left;vertical-align:middle;font-weight:bold;}"+
                    " .memberCurrentGrade{background-color:white;margin-right:0.5em;padding:0 .5em;}"+
                    " .groupsPluginGroupControls{display:flex;}"+
                    " .groupsPluginGroupGrade{background-color:white;margin:2px;padding:0 0.3em;height:3em;display:inline;}"+
                    " .groupsPluginAllGroupsControls{margin-bottom:0.5em;border-bottom:0.5px solid white;padding-left:1em;display:flex;}",
                load:function(bus,params) {
                    var render = function(){
                        try {
                            overContainer.empty();
                            var groups = Conversations.getCurrentGroups();
                            if(Conversations.shouldModifyConversation()){
                                var slide = Conversations.getCurrentSlide();
                                if(slide){
                                    if(groups.length){
                                        var linkedGradeLoc = sprintf("groupWork_%s",slide.id);
                                        var linkedGrade = _.find(Grades.getGrades(),function(grade){
                                            return grade.location == linkedGradeLoc;
                                        });
                                        if(linkedGrade){
                                            var linkedGrades = Grades.getGradeValues()[linkedGrade.id];
                                        }
                                        var xOffset = 0;
                                        var allControls = $("<div />",{
                                            class:"groupsPluginAllGroupsControls"
                                        }).on("mousedown",function(){
                                            xOffset = $("#masterFooter").scrollLeft();
                                        }).append($("<input />",{
                                            type:"radio",
                                            name:"groupView",
                                            id:"showAll"
                                        }).prop("checked",true).click(function(){
                                            _.each(groups,function(g){
                                                ContentFilter.setFilter(g.id,true);
                                            });
                                            ContentFilter.clearAudiences();
                                            blit();
                                            $("#masterFooter").scrollLeft(xOffset);
                                        })).append($("<label />",{
                                            for:"showAll"
                                                }).css({
                                                    "flex-grow":0
                                                }).append($("<span />",{
                                                    class:"icon-txt",
                                                    text:"Show all"
                                                }))).append(
                                                    button("fa-share-square","Share all",function(){
                                                        var origFilters = _.map(ContentFilter.getFilters(),function(of){return _.cloneDeep(of);});
                                                        var audiences = ContentFilter.getAudiences();
                                                        _.forEach(groups,function(g){
                                                            ContentFilter.setFilter(g.id,false);
                                                        });
                                                        Progress.deisolated.call();
                                                        blit();
                                                        var sendSubs = function(listOfGroups,afterFunc){
                                                            var group = listOfGroups[0];
                                                            if (group){
                                                                ContentFilter.setFilter(group.id,true);
                                                                ContentFilter.setAudience(group.id);
                                                                blit();
                                                                _.defer(function(){
                                                                    Submissions.sendSubmission(function(succeeded){
                                                                        if (succeeded){
                                                                            ContentFilter.setFilter(group.id,false);
                                                                            blit();
                                                                            _.defer(function(){
                                                                                sendSubs(_.drop(listOfGroups,1),afterFunc);
                                                                            });
                                                                        } else {
                                                                            errorAlert("Submission failed","Storing this submission failed.");
                                                                        }
                                                                    });
                                                                });
                                                            } else {
                                                                successAlert("Submissions sent",sprintf("%s group submissions stored.  You can check them in the submissions tab.",_.size(groups)));
                                                                afterFunc();
                                                            }
                                                        };
                                                        _.defer(function(){
                                                            sendSubs(groups,function(){
                                                                _.forEach(origFilters,function(filter){
                                                                    ContentFilter.setFilter(filter.id,filter.enabled);
                                                                });
                                                                if (audiences.length){
                                                                    ContentFilter.setAudience(audiences[0]);
                                                                } else {
                                                                    ContentFilter.clearAudiences();
                                                                }
                                                                blit();
                                                            });
                                                        });
                                                    }).css({"margin-top":0})
                                                ).appendTo(overContainer);
                                        var container = $("<div />").css({display:"flex"}).appendTo(overContainer);
                                        _.each(groups,function(group){
                                            var gc = $("<div />",{
                                                class:"groupsPluginGroupContainer"
                                            }).appendTo(container);
                                            var right = $("<div />").appendTo(gc);
                                            var controls = $("<div />",{
                                                class:"groupsPluginGroupControls"
                                            }).appendTo(right);
                                            var assess = button("fa-book","Assess",function(){
                                                linkedGrade = _.find(Grades.getGrades(),function(grade){
                                                    return grade.location == linkedGradeLoc;
                                                });
                                                if (linkedGrade !== undefined){
                                                    var uniqId = sprintf("assessGroupDialog_%s",_.uniqueId());
                                                    var outer = $("<div/>",{
                                                        id:uniqId
                                                    });
                                                    var assessAlert = $.jAlert({
                                                        title:"Assess group",
                                                        width:"80%",
                                                        content:outer[0].outerHTML,
                                                        btns:[{
                                                            text:"Save",
                                                            theme:'green',
                                                            closeAlert:true,
                                                            onClick:function(){
                                                                linkedGrade = _.find(Grades.getGrades(),function(grade){
                                                                    return grade.location == linkedGradeLoc;
                                                                });
                                                                if (gradeValue != undefined){
                                                                    _.each(group.members,function(member){
                                                                        var groupMemberGrade = {
                                                                            type:sprintf("%sGradeValue",linkedGrade.gradeType),
                                                                            gradeId:linkedGrade.id,
                                                                            gradeValue:gradeValue,
                                                                            gradedUser:member,
                                                                            author:UserSettings.getUsername(),
                                                                            gradeComment:comment,
                                                                            gradePrivateComment:privateComment,
                                                                            timestamp:0,
                                                                            audiences:[]
                                                                        };
                                                                        sendStanza(groupMemberGrade);
                                                                    });
                                                                    assessAlert.closeAlert();
                                                                } else {
                                                                    alert("you cannot submit without a gradeValue");
                                                                }
                                                            }
                                                        }]
                                                    });
                                                    var outerElem = $("#"+uniqId);
                                                    var grades = $("<div />",{
                                                        class:"groupsPluginGroup rowT"
                                                    });
                                                    var gradeValue = undefined;
                                                    var gradeValueId = sprintf("gradeValueInput_%s",_.uniqueId());
                                                    var rowC = function(){
                                                        return $("<div />",{
                                                            class:"rowC"
                                                        }).appendTo(grades);
                                                    }
                                                    var gradeC = rowC();
                                                    switch (linkedGrade.gradeType) {
                                                    case "numeric" :
                                                        $("<label/>",{
                                                            text:"Score",
                                                            "for":gradeValueId
                                                        }).appendTo(gradeC);
                                                        $("<input/>",{
                                                            id:gradeValueId,
                                                            type:"number",
                                                            max:linkedGrade.numericMaximum,
                                                            min:linkedGrade.numericMinimum
                                                        }).on("change",function(ev){
                                                            gradeValue = parseFloat($(this).val());
                                                        }).appendTo(gradeC);
                                                        break;
                                                    case "text":
                                                        $("<label/>",{
                                                            text:"Score",
                                                            "for":gradeValueId
                                                        }).appendTo(gradeC);
                                                        $("<input/>",{
                                                            id:gradeValueId,
                                                            type:"text"
                                                        }).on("change",function(ev){
                                                            gradeValue = $(this).val();
                                                        }).appendTo(gradeC);
                                                        break;
                                                    case "boolean":
                                                        gradeValue = false;
                                                        $("<input/>",{
                                                            type:"checkbox",
                                                            id:gradeValueId
                                                        }).on("change",function(ev){
                                                            gradeValue = $(this).prop("checked");
                                                        }).appendTo(gradeC);
                                                        $("<label/>",{
                                                            text:"Score",
                                                            "for":gradeValueId
                                                        }).appendTo(gradeC);
                                                        break;
                                                    default:
                                                        break;
                                                    }
                                                    var commentC = rowC();
                                                    var commentId = sprintf("gradeValueComment_%s",_.uniqueId());
                                                    var comment = "";
                                                    var commentLabel = $("<label/>",{
                                                        "for":commentId,
                                                        text:"Comment"
                                                    }).appendTo(commentC);
                                                    var commentBox = $("<input />",{
                                                        id:commentId,
                                                        type:"text"
                                                    }).on("change",function(ev){
                                                        comment = $(this).val();
                                                    }).appendTo(commentC);
                                                    var pCommentC = rowC();
                                                    var privateCommentId = sprintf("gradeValuePrivateComment_%s",_.uniqueId());
                                                    var privateComment = "";
                                                    var privateCommentLabel = $("<label/>",{
                                                        "for":privateCommentId,
                                                        text:"Private comment"
                                                    }).appendTo(pCommentC);
                                                    var privateCommentBox = $("<input/>",{
                                                        id:privateCommentId,
                                                        type:"text"
                                                    }).on("change",function(ev){
                                                        privateComment = $(this).val();
                                                    }).appendTo(pCommentC);
                                                    grades.appendTo(outerElem);
                                                } else {
                                                    alert("no linked grade");
                                                }
                                            });
                                            assess.appendTo(controls);
                                            $("<span />",{
                                                text:sprintf("Group %s",group.title),
                                                class:"ml"
                                            }).appendTo(right);
                                            button("fa-share-square","Share",function(){
                                                isolate.find("input").prop("checked",true).change();
                                                _.defer(Submissions.sendSubmission);
                                            }).appendTo(controls);
                                            var id = sprintf("isolateGroup_%s",group.title);
                                            var isolate = $("<div />",{
                                                class:"isolateGroup"
                                            }).on("mousedown",function(){
                                                xOffset = $("#masterFooter").scrollLeft();
                                            }).append($("<input />",{
                                                type:"radio",
                                                name:"groupView",
                                                id:id
                                            }).change(function(){
                                                Progress.call("beforeChangingAudience",[group.id]);
                                                _.each(groups,function(g){
                                                    ContentFilter.setFilter(g.id,false);
                                                });
                                                ContentFilter.setFilter(group.id,true);
                                                ContentFilter.setAudience(group.id);
                                                Progress.isolated.call([group.id]);
                                                Modes.select.activate();
                                                blit();
                                                $("#masterFooter").scrollLeft(xOffset);
                                            })).append($("<label />",{
                                                for:id
                                            }).append($("<span />",{
                                                class:"icon-txt",
                                                text:"Isolate"
                                            }).css({
                                                "margin-top":"2px"
                                            }))).appendTo(controls);
                                            var members = $("<div />",{
                                                class:"groupsPluginGroup"
                                            }).prependTo(gc);
                                            _.each(group.members,function(member){
                                                var mV = $("<div />",{
                                                    text:member,
                                                    class:"groupsPluginMember"
                                                }).appendTo(members);
                                                if(linkedGrades && member in linkedGrades){
                                                    $("<span />",{
                                                        class:"memberCurrentGrade",
                                                        text:linkedGrades[member].gradeValue
                                                    }).prependTo(mV);
                                                }
                                            });
                                        });
                                    }
                                }
                            }
                            else {
                                var studentContainer = $("<div />").css({display:"flex"}).appendTo(overContainer);
                                _.each(groups,function(group){
                                    if(_.find(Conversations.getCurrentGroup(),group)){
                                        var gc = $("<div />",{
                                            class:"groupsPluginGroupContainer"
                                        }).appendTo(studentContainer);
                                        var id = sprintf("isolateGroup_%s",group.title);
                                        var members = $("<div />").appendTo(gc);
                                        _.each(group.members,function(member){
                                            var mV = $("<div />",{
                                                text:member
                                            }).appendTo(members);
                                        });
                                        $("<div />",{
                                            text:sprintf("Group %s",group.title)
                                        }).prependTo(members);
                                    }
                                });
                            }
                        }
                        catch(e){
                            console.log("Groups plugin render e",e);
                        }
                    }
                    bus.gradeValueReceived["Groups plugin"] = function(gv){
                        var linkedGradeLoc = sprintf("groupWork_%s",Conversations.getCurrentSlideJid());
                        var linkedGrade = _.find(Grades.getGrades(),function(grade){
                            return grade.location == linkedGradeLoc;
                        });
                        if(linkedGrade && gv.gradeId == linkedGrade.id){
                            render();
                        }
                    }
                    bus.currentSlideJidReceived["Groups plugin"] = render;
                    bus.conversationDetailsReceived["Groups plugin"] = render;
                    return overContainer;
                },
                initialize:function(){
                }
            };
        })()
    };
})();

$(function(){
    var pluginBar = $("#pluginBar");
    var styleContainer = $("<style></style>").appendTo($("body"));
    _.each(Plugins,function(plugin,label){
        var container = $("<div />",{
            class:"plugin"
        });
        plugin.load(Progress).appendTo(container);
        styleContainer.append(plugin.style);
        container.appendTo(pluginBar);
        plugin.initialize();
    });
});
