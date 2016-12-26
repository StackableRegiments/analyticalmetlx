var Plugins = (function(){
    return {
				"Chat":(function(){
					var outer = {};
					var cmHost = {};
					var cmTemplate = {};
					var containerId = sprintf("chatbox_%s",_.uniqueId());
					var container = $("<div />",{
						id:containerId
					});
					var chatMessages = [];
					var renderChatMessage = function(chatMessage){
						var rootElem = cmTemplate.clone();
						rootElem.find(".chatMessageAuthor").text(chatMessage.author);
						rootElem.find(".chatMessageTimestamp").text(new Date(chatMessage.timestamp).toISOString());
						switch (chatMessage.contentType){
							case "text":
								rootElem.find(".chatMessageContent").text(chatMessage.content);
								break;
							case "html":
								rootElem.find(".chatMessageContent").html(chatMessage.content);
								break;
						}
						return rootElem;
					};
					var actOnStanzaReceived = function(stanza){
						if (stanza && "type" in stanza && stanza.type == "chatMessage"){
							chatMessages.push(stanza);
							cmHost.append(renderChatMessage(stanza));
							cmHost.scrollTop(cmHost[0].scrollHeight);
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
						return cm;
					};

					return {
						style:".chatMessage {color:white}"+
					".chatMessageContainer {background:black; overflow-y:auto; height:110px;}"+
					".chatContainer {width:320px;height:140px;}"+
					".chatMessageAuthor {color:gray; background:black}"+
					".chatMessageTimestamp {color:red; background:black; font-size:small;}"+
					".chatMessageContent {background:black}"+
					".chatboxContainer {background:black}"+
					".chatbox {background:white; color:black; display:inline-block; padding:0px; margin:0px;}"+
					".chatboxSend {display:inline-block; background:white; color:black; padding:0px; margin:0px;}",
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
									var newText = $(this).val();
									if (newText && newText.length){
										sendStanza(createChatMessage(newText));
										$(this).val("");
									}
								}
							});
							var sendButton = outer.find(".chatboxContainer .chatboxSend").on("click",function(){
								var newText = chatbox.val();
								if (newText && newText.length){
									sendStanza(createChatMessage(newText));
									chatbox.val("");
								}
							});
							console.log("chatInit:",outer,this,chatbox);
						}
					};
				})(),
        "Face to face":(function(){
            var container = $("<div />");
            return {
                style:".publishedStream {background:green;} .subscribedStream {background:red;}"+
                    " .videoConfStartButton, .videoConfSubscribeButton {background:white;margin:1px;}"+
                    " .videoConfSessionContainer, .videoConfStartButtonContainer, .videoConfContainer{display:flex;}"+
                    " .videoConfStartButtonContainer{flex-direction:row;}"+
                    " .videoConfStartButton{padding:0 1em;font-size:1rem;}"+
                    " #videoConfSessionsContainer{display:flex;}"+
                    " .videoConfSessionContainer{width:160px;flex-direction:column;}"+
                    " .context{font-size:1rem;}"+
                    " .broadcastContainer{display:none;}",
                load:function(bus,params){
                    container.append('<span id="videoConfSessionsContainer">'+
                                     '<div class="videoConfSessionContainer">'+
                                     '<div class="videoConfStartButtonContainer">'+
                                     '<button class="videoConfStartButton">'+
                                     '<div>Start sending</div>'+
                                     '</button>'+
                                     '<span class="context"></span>'+
                                     '</div>'+
                                     '<div class="viewscreen"></div>'+
                                     '<div class="broadcastContainer">'+
                                     '<a class="floatingToolbar btn-menu fa fa-television btn-icon broadcastLink">'+
                                     '<div class="icon-txt">Watch class</div>'+
                                     '</a>'+
                                     '</div>'+
                                     '<div class="videoSubscriptionsContainer"></div>'+
                                     '<div class="videoConfContainer">'+
                                     '<span class="videoContainer">'+
                                     '<button class="floatingToolbar btn-menu fa fa-television btn-icon videoConfSubscribeButton">'+
                                     '<div class="icon-txt">Receive</div>'+
                                     '</button>'+
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
                style:".groupsPluginMember{margin-left:0.5em;}"+
                    " .groupsPluginGroupContainer{display:flex;margin-right:1em;}"+
                    " .groupsPluginGroupContainer .icon-txt, .groupsPluginAllGroupsControls .icon-txt, .groupsPluginAllGroupsControls .fa{color:white;}"+
                    " .groupsPluginGroup{display:inline-block;text-align:center;vertical-align:top;}"+
                    " .groupsPluginGroupGrade button, .groupsPluginGroupGrade .icon-txt{padding:0;color:white;margin-top:0;}"+
                    " .groupsPluginGroupControls button, .groupsPluginGroupControls .icon-txt{padding:0;color:white;margin-top:0;}"+
                    " .isolateGroup label{margin-top:1px;}"+
                    " .isolateGroup{margin-top:0.8em;}"+
                    " .memberCurrentGrade{color:black;background-color:white;margin-right:0.5em;padding:0 .5em;}"+
                    " .groupsPluginGroupControls{display:flex;}"+
                    " .groupsPluginGroupGrade{background-color:white;color:black;margin:2px;padding:0 0.3em;height:3em;display:inline;}"+
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
                                        var allControls = $("<div />",{
                                            class:"groupsPluginAllGroupsControls"
                                        }).append($("<input />",{
                                            type:"radio",
                                            name:"groupView",
                                            id:"showAll"
                                        }).click(function(){
                                            _.each(groups,function(g){
                                                ContentFilter.setFilter(g.id,true);
                                            });
                                            ContentFilter.clearAudiences();
                                            blit();
                                        })).append($("<label />",{
                                            for:"showAll"
                                                }).css({
                                                    "flex-grow":0
                                                }).append($("<span />",{
                                                    class:"icon-txt",
                                                    text:"Show all"
                                                }).css({
                                                    "margin-top":"3px"
                                                }))).appendTo(overContainer);
                                        var container = $("<div />").css({display:"flex"}).appendTo(overContainer);
                                        _.each(groups,function(group){
                                            var gc = $("<div />",{
                                                class:"groupsPluginGroupContainer"
                                            }).appendTo(container);

                                            var grades = $("<div />",{
                                                class:"groupsPluginGroup"
                                            }).css({display:"block"});
                                            _.each("A B C D F".split(" "),function(gradeLetter){
                                                $("<div />",{
                                                    text:gradeLetter,
                                                    class:"groupsPluginGroupGrade"
                                                }).appendTo(grades).on("click",function(){
                                                    if (linkedGrade != undefined){
                                                        _.each(group.members,function(member){
                                                            var groupMemberGrade = {
                                                                type:"textGradeValue",
                                                                gradeId:linkedGrade.id,
                                                                gradeValue:gradeLetter,
                                                                gradedUser:member,
                                                                author:UserSettings.getUsername(),
                                                                timestamp:0,
                                                                audiences:[]
                                                            };
                                                            sendStanza(groupMemberGrade);
                                                        });
                                                    }
                                                });
                                            });

                                            var right = $("<div />").appendTo(gc);
                                            $("<span />",{
                                                text:sprintf("Group %s",group.title),
                                                class:"ml"
                                            }).appendTo(right);
                                            var controls = $("<div />",{
                                                class:"groupsPluginGroupControls"
                                            }).appendTo(right);
                                            button("fa-share-square","",function(){
                                                isolate.find("input").prop("checked",true).change();
                                                console.log("Isolating and screenshotting",isolate);
                                                _.defer(Submissions.sendSubmission);
                                            }).appendTo(controls);
                                            var id = sprintf("isolateGroup_%s",group.title);
                                            var isolate = $("<div />",{
                                                class:"isolateGroup"
                                            }).append($("<input />",{
                                                type:"radio",
                                                name:"groupView",
                                                id:id
                                            }).change(function(){
                                                _.each(groups,function(g){
                                                    ContentFilter.setFilter(g.id,false);
                                                });
                                                ContentFilter.setFilter(group.id,true);
                                                ContentFilter.setAudience(group.id);
                                                blit();
                                            })).append($("<label />",{
                                                for:id
                                            }).append($("<span />",{
                                                class:"icon-txt",
                                                text:"Isolate"
                                            }).css({
                                                "margin-top":"5px"
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
                                            grades.appendTo(right);
                                        });
                                    }
                                }
                            }
                            else {
                                var studentContainer = $("<div />").css({display:"flex"}).appendTo(overContainer);
                                console.log("Rendering student");
                                _.each(groups,function(group){
                                    console.log(group,Conversations.getCurrentGroup());
                                    if(_.find(Conversations.getCurrentGroup(),group)){
                                        var gc = $("<div />",{
                                            class:"groupsPluginGroupContainer"
                                        }).appendTo(studentContainer);
                                        var right = $("<div />").appendTo(gc);
                                        $("<span />",{
                                            text:sprintf("Group %s",group.title),
                                            class:"ml"
                                        }).appendTo(right);
                                        var controls = $("<div />",{
                                            class:"groupsPluginGroupControls"
                                        }).appendTo(right);
                                        var id = sprintf("isolateGroup_%s",group.title);
                                        var members = $("<div />",{
                                            class:"groupsPluginGroup"
                                        }).prependTo(gc);
                                        _.each(group.members,function(member){
                                            var mV = $("<div />",{
                                                text:member,
                                                class:"groupsPluginMember"
                                            }).appendTo(members);
                                        });
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
