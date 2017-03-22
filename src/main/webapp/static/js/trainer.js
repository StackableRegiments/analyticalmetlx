function simulationOn(conversation,slide){
    $("#simulation").attr("src",sprintf("/board?conversationJid=%s&slideId=%s&showTools=true&showSlides=true&unique=true",conversation,slide));
}
function simulatedUsers(users){
    var container = $("#simulationPopulation").empty();
    _.each(_.sortBy(users,"name"),function(user){
        var userC = $("<div />").appendTo(container);
        $("<span />").text(user.name).appendTo(userC);
        $("<span />").text(user.activity.label).appendTo(userC);
        $("<span />").text(user.attention.label).appendTo(userC);
        $("<span />").text(user.claim.left).appendTo(userC);
        $("<span />").text(user.claim.top).appendTo(userC);
        $("<span />").text(user.claim.right).appendTo(userC);
        $("<span />").text(user.claim.bottom).appendTo(userC);
        $("<span />").text(user.history.length).appendTo(userC);
    });
}
