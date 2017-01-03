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
                    if (text.startsWith("/w")){
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
                    } else if (text.startsWith("/g")){
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
                style:".chatMessage {color:white}"+
                    ".chatMessageContainer {overflow-y:auto; height:110px;}"+
                    ".chatContainer {width:320px;height:140px;}"+
                    ".chatMessageAuthor {color:gray;margin-right:1em;font-weight:bold;}"+
                    ".chatMessageTimestamp {color:red;font-size:small;display:none;}"+
                    ".chatboxContainer {display:flex;}"+
                    ".chatboxContainer input{flex-grow:1;}"+
                    ".chatbox {background-color:white;color:black; display:inline-block; padding:0px; margin:0px;}"+
                    ".chatboxSend {display:inline-block; background:white; color:black; padding:0px; margin:0px;}"+
                    ".groupChat {color:orange}"+
                    ".whisper {color:pink}",
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
        "Face to face":(function(){
            var container = $("<div />");
            return {
                style:".publishedStream {background:green;} .subscribedStream {background:red;}"+
                    " .videoConfStartButton, .videoConfSubscribeButton, .videoConfPermitStudentBroadcastButton {background:white;margin:1px;}"+
                    " .videoConfSessionContainer, .videoConfStartButtonContainer, .videoConfContainer, .videoConfPermitStudentBroadcastContainer{display:flex;}"+
                    " .videoConfStartButtonContainer, .videoConfPermitStudentBroadcastContainer{flex-direction:row;}"+
                    " .videoConfStartButton, .videoConfPermitStudentBroadcastButton{padding:0 1em;font-size:1rem;}"+
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
                                     '<div class="videoConfPermitStudentBroadcastContainer">'+
                                     '<button class="videoConfPermitStudentBroadcastButton">'+
                                     '<div>Permit students to broadcast</div>'+
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
                style:".groupsPluginMember{margin-left:0.5em;display:flex;}"+
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
