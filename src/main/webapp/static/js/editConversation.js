function serverResponse(response){
    //console.log("serverResponse:",response);
}
function receiveUsername(username){
    //console.log("receiveUsername:",username);
}
function receiveUserGroups(userGroups){
    //console.log("receiveUserGroups:",userGroups);
}
function receiveConversationDetails(details){
    if (currentConversation != undefined && details != undefined && "jid" in currentConversation && "jid" in details && details.jid == currentConversation.jid) {
        //console.log("receiveConversationDetails:",details);
        currentConversation = details;
    }
}
function receiveNewConversationDetails(details){
    //console.log("receiveNewConversationDetails:",details);
}
var slideReorderInProgress = false;
var reapplyVisualState = function(){
	var reorderContainer = $("#reorderInProgress");
	if (slideReorderInProgress){
		reorderContainer.show();
	} else {
		reorderContainer.hide();
	}
	$("#sortableRoot").sortable({
		handle: "td.slideDragHandle",
		stop: function(ev,ui){
			slideReorderInProgress = true;
			reapplyVisualState();
		}
	}).disableSelection();
	$("#reorderSlides").unbind("click").on("click",function(){
		sendChangedSlides();
	});
	$("#cancelReorderSlides").unbind("click").on("click",function(){
		slideReorderInProgress = false;
		refreshFromServer();
	});
	_.forEach($(".slideContainer"),function(sce){
		var sc = $(sce);
		var nextItem = $(sc).next(".slideContainer");
		var prevItem = $(sc).prev(".slideContainer");
		var moveBackButton = sc.find(".moveSlideBack");
		var moveForwardButton = sc.find(".moveSlideForward");
		moveBackButton.unbind("click");	
		moveForwardButton.unbind("click");
		if (nextItem.length == 0){
			moveForwardButton.hide();
		} else {
			moveForwardButton.show();
			moveForwardButton.on("click",function(){
				slideReorderInProgress = true;
				sc.detach();
				nextItem.after(sc);
				reapplyVisualState();
			});
		}
		if (prevItem.length == 0){
			moveBackButton.hide();
		} else {
			moveBackButton.show();
			moveBackButton.on("click",function(){
				slideReorderInProgress = true;
				sc.detach();
				prevItem.before(sc);
				reapplyVisualState();
			});
		}
	});
}
function sendChangedSlides(){
	try {
			if (currentConversation != undefined && "jid" in currentConversation && "slides" in currentConversation) {
					var oldSlides = currentConversation.slides;
					var newIndex = 0;
					var newSlides = _.map($(".slideId"),function(el){
							var slideId = parseInt($(el).text());
							//console.log("searching for:",slideId);
							var returnedSlide = _.find(currentConversation.slides,function(slide){
									if (slide.id == slideId){
											//console.log("found:",slide);
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
					});
					//console.log("reordering slides:",oldSlides,newSlides);
					reorderSlidesOfCurrentConversation(currentConversation.jid,newSlides);
					slideReorderInProgress = false;
					reapplyVisualState();
			}
	} catch(e){
			console.log("exception while reordering slides",e);
	}
}
$(function(){
	reapplyVisualState();
});
