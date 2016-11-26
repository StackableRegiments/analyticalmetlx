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
            var container = $("<div />");
            return {
                style:".groupsPluginMember{margin-left:0.5em;}",
                load:function(bus,params) {
                    var render = function(){
                        container.empty();
                        var slide = Conversations.getCurrentSlide();
                        if(slide){
                            var groupSet = slide.groupSet;
                            if(groupSet){
                                _.each(groupSet.groups,function(group){
                                    var gc = $("<div />",{
                                        class:"groupsPluginGroup"
                                    });
                                    _.each(group.members,function(member){
                                        $("<div />",{
                                            text:member,
                                            class:"groupsPluginMember"
                                        }).appendTo(gc);
                                    });
                                    gc.appendTo(container);
                                });
                            }
                        }
                    };
                    bus.afterJoiningSlide["Groups plugin"] = render;
                    bus.conversationDetailsReceived["Groups plugin"] = render;
                    return container;
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
            text:label,
            class:"pluginName"
        }).prependTo(container);
        plugin.load(Progress).appendTo(container);
        styleContainer.append(plugin.style);
        container.appendTo(pluginBar);
        plugin.initialize();
    });
});
