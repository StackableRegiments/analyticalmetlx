function simulationOn(conversation,slide){
    $("#simulation").attr("src",sprintf("/board?conversationJid=%s&slideId=%s&showTools=true&showSlides=true&unique=true",conversation,slide));
}
