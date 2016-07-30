function serverResponse(response){
    console.log("serverResponse:",response);
}
function receiveUsername(username){
    console.log("receiveUsername:",username);
}
function receiveUserGroups(userGroups){
    console.log("receiveUserGroups:",userGroups);
}
function receiveConversationDetails(details){
    if (currentConversation != undefined && details != undefined && "jid" in currentConversation && "jid" in details && details.jid == currentConversation.jid) {
        console.log("receiveConversationDetails:",details);
        currentConversation = details;
    }
}
function receiveNewConversationDetails(details){
    console.log("receiveNewConversationDetails:",details);
}
var reapplyVisualState = function(){
	$("#sortableRoot").sortable({
			handle: "td.slideDragHandle",
			stop: function(ev,ui){
				console.log("stop:",ev,ui);
				try {
						if (currentConversation != undefined && "jid" in currentConversation && "slides" in currentConversation) {
								var oldSlides = currentConversation.slides;
								var newIndex = 0;
								var newSlides = _.map($(".slideId"),function(el){
										var slideId = parseInt($(el).text());
										console.log("searching for:",slideId);
										var returnedSlide = _.find(currentConversation.slides,function(slide){
												if (slide.id == slideId){
														console.log("found:",slide);
														return true;
												} else {
														return false;
												}
										});
										if (!("groupSet" in returnedSlide)){
												returnedSlide.groupSet = [];
										}
										returnedSlide.index = newIndex;
										newIndex = newIndex + 1;
										return returnedSlide;
								})
								console.log("reordering slides:",oldSlides,newSlides);
								reorderSlidesOfCurrentConversation(currentConversation.jid,newSlides);
						}
				} catch(e){
						console.log("exception while reordering slides",e);
				}
			}
	}).disableSelection();
}
$(function(){
	reapplyVisualState();
});
