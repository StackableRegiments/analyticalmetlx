var Admin = (function(){
    var conversations = [];
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
    return {
        getConversations:function(){return conversations},
	renderConversations:function(){
	    var container = $("#conversations");
	    _.each(conversations, function(c){
		$("<div />",{
		    text:c.title,
		    class:"tile conversation"
		}).appendTo(container);
	    });
	    container.masonry({
		itemSelector:".tile"
	    });
	}
    };
})();
