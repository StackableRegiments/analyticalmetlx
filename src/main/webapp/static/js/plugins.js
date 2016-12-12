var Plugins = (function(){
    return {
        "Face to face":(function(){
            var container = $("<div />");
            return {
                style:".publishedStream {background:green;} .subscribedStream {background:red;}"+
                    " .publisherVideoElem, .subscriberVideoElem {display:flex;}"+
                    " .videoConfStartButton, .videoConfSubscribeButton {background:white;}"+
                    " .videoConfSessionContainer, .videoConfStartButtonContainer, .videoConfContainer{display:flex;}"+
                    " .broadcastContainer{display:none;}",
                load:function(bus,params){
                    container.append('<span id="videoConfSessionsContainer">'+
                                     '<div class="videoConfSessionContainer">'+
                                     '<span class="videoConfStartButtonContainer">'+
                                     '<button class="floatingToolbar btn-menu fa fa-video-camera btn-icon videoConfStartButton">'+
                                     '<div class="icon-txt">Send video</div>'+
                                     '</button>'+
                                     '</span>'+
                                     '<span class="broadcastContainer">'+
                                     '<a class="floatingToolbar btn-menu fa fa-television btn-icon broadcastLink">'+
                                     '<div class="icon-txt">Watch class</div>'+
                                     '</a>'+
                                     '</span>'+
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
                    " .groupsPluginGroupControls{display:flex;}"+
                    " .groupsPluginGroupGrade{border:0.5px solid white;background-color:white;color:black;margin-bottom:1px;}"+
                    " .groupsPluginAllGroupsControls{margin-bottom:0.5em;border-bottom:0.5px solid white;padding-left:1em;display:flex;}",
                load:function(bus,params) {
                    var render = function(){
                        try {
                            overContainer.empty();
                            if(Conversations.shouldModifyConversation()){
                                var slide = Conversations.getCurrentSlide();
                                if(slide){
                                    var groups = Conversations.getCurrentGroups();
                                    if(groups.length){
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
                                        })).append($("<label />",{
                                            for:"showAll"
                                                }).css({
                                                    "flex-grow":0
                                                }).append($("<span />",{
                                                    class:"icon-txt",
                                                    text:"Show all"
                                                }))).appendTo(overContainer);
                                        var container = $("<div />").css({display:"flex"}).appendTo(overContainer);
                                        _.each(groups,function(group){
                                            var gc = $("<div />",{
                                                class:"groupsPluginGroupContainer"
                                            }).appendTo(container);

                                            var grades = $("<div />",{
                                                class:"groupsPluginGroup"
                                            }).appendTo(gc);
                                            _.each("A B C D".split(" "),function(grade){
                                                $("<div />",{
                                                    text:grade,
                                                    class:"groupsPluginGroupGrade btn-icon"
                                                }).appendTo(grades);
                                            });

                                            var right = $("<div />").appendTo(gc);
                                            $("<span />",{
                                                text:sprintf("Group %s",group.title),
						class:"ml"
                                            }).appendTo(right);
                                            var controls = $("<div />",{
                                                class:"groupsPluginGroupControls"
                                            }).appendTo(right);
                                            button("fa-share-square","Submit screen",function(){
                                                isolate.find("input").prop("checked",true).change();
                                                console.log("Isolating and screenshotting",isolate);
                                                _.defer(Submissions.sendSubmission);
                                            }).appendTo(controls);
                                            var id = _.uniqueId("l");
                                            var isolate = $("<div />",{
                                                class:"isolateGroup"
                                            }).append($("<input />",{
                                                type:"radio",
                                                name:"groupView",
                                                id:id
                                            }).change(function(){
                                                console.log("Isolate changed",group.id);
                                                _.each(groups,function(g){
                                                    ContentFilter.setFilter(g.id,false);
                                                });
                                                ContentFilter.setFilter(group.id,true);
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
                                            }).appendTo(right);
                                            _.each(group.members,function(member){
                                                $("<div />",{
                                                    text:member,
                                                    class:"groupsPluginMember"
                                                }).appendTo(members);
                                            });
                                        });
                                    }
                                }
                            }
                        }
                        catch(e){
                            console.log("Groups plugin render e",e);
                        }
                    }

                    bus.currentSlideJidReceived["Groups plugin"] = render;
                    bus.conversationDetailsReceived["Groups plugin"] = render;
                    return overContainer;
                },
                initialize:function(){}
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
        $("<div />",{
            text:" ",
            class:"pluginName"
        }).prependTo(container);
        plugin.load(Progress).appendTo(container);
        styleContainer.append(plugin.style);
        container.appendTo(pluginBar);
        plugin.initialize();
    });
});
