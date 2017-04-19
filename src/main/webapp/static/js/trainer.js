function simulationOn(conversation,slide,onLoad){
    var f = function(){
        $("#simulation").css("opacity",0).on("load",function(){
            _.defer(onLoad);
        }).attr("src",sprintf("/board?conversationJid=%s&slideId=%s&showTools=true&showSlides=true&unique=true",conversation,slide));
    };
    $(f);
    f();
}
function hide(selector){
    $(selector,$("#simulation").contents()).animate({opacity:0},500);
}
function flash(selector){
    $(selector,$("#simulation").contents()).animate({opacity:1},500);
}
function clearTools(){
    _.map([
        "#thumbsColumn",
        "#toolsColumn",
        "#applicationMenuButton",
        "#slideControls",
        ".meters",
        "#masterFooter"
    ],hide);
    _.delay(function(){
        $("#simulation").animate({opacity:1});
    },1000);
}
function highlight(selector){
    flash(selector);
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
