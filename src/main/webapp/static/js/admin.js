var Admin = (function(){
    var conversations = [];
    var itemSelector = ".tile";
    var layoutMode = "packery";
    return {
        loadConversations:function(){
            $.ajax("/search", {
                data:{
                    query:"",
                    format:"json"
                },
                success:function(jConversations){
                    conversations = jConversations.conversations.conversation;
                    Admin.renderConversations();
                }
            });
        },
        getConversations:function(){return conversations},
        renderConversations:function(){
            var container = $("#conversations");
            var template = container.find(".template");
            _.each(conversations, function(conversation){
                var c = template.clone().removeClass("template").appendTo(container);
                c.find(".author").text(conversation.author);
                c.find(".slideCount").text(conversation.slides.length);
                c.find(".title").text(conversation.title);
                c.find(".activityCount").text(100000);
                var created = moment(conversation.created,
                                     [//Try American format first
					 "M/d/YYYY H:m:s a",
					 "d/M/YYYY H:m:s a",
                                         "ddd MMM D H:m:s YYYY"
                                     ]);
		//Override supplied created date for later consistency on sorting and range filtering
		conversation.created = created;
                c.find(".creation").text(created.format("DD MMM YYYY, h:mm a"));
            });
            container.isotope({
                itemSelector:itemSelector,
                layoutMode:"packery"
            });
        },
        initializeControls:function(){
            var filters = $("#conversationFilters");
            var authorFilter = filters.find(".author");
            var container = $("#conversations");
            authorFilter.find("input").blur(function(){
                var criteria = $(this).val();
                container.isotope({
                    itemSelector:itemSelector,
                    layoutMode:layoutMode,
                    filter:function(){return $(this).find(".author").text() == criteria}
                });
            });
        }
    }
})();
$(function(){
    Admin.loadConversations();
})
