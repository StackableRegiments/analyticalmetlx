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
function flash(selector,complete){
    var delay = 250;
    for(var i = 0; i < 3; i++){
        $(selector,$("#simulation").contents()).animate({opacity:0},delay).animate({opacity:1},delay,function(){
            if(i == 3){
                if(complete){
                    complete();
                }
            }
        });
    }
}
function showClick(selector){
    flash(selector,function(){
        $(selector,$("#simulation").contents()).click();
    });
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
function showTools(){
    _.map([
        "#thumbsColumn",
        "#toolsColumn",
        "#applicationMenuButton",
        "#slideControls",
        ".meters",
        "#masterFooter"
    ],highlight);
    _.delay(function(){
        $("#simulation").animate({opacity:1});
    },1000);
}
function highlight(selector){
    $(selector,$("#simulation").contents()).animate({opacity:1},500);
}
function simulatedUsers(users){
    var container = $("#simulationPopulation").empty();
    _.each(_.sortBy(users,"name"),function(user){
        var userC = $("<div />").addClass("simulatedUser").appendTo(container);
        switch(user.attention.label){
        case "leftwards": $("<span />").addClass("fa fa-arrow-left").appendTo(userC); break;
        case "above": $("<span />").addClass("fa fa-arrow-up").appendTo(userC); break;
        case "rightwards": $("<span />").addClass("fa fa-arrow-right").appendTo(userC); break;
        case "below": $("<span />").addClass("fa fa-arrow-down").appendTo(userC); break;
        }
        $("<span />").text(user.name).appendTo(userC);
        $("<span />").text(user.activity.label).appendTo(userC);
        $("<span />").addClass("coord").text(user.claim.left).appendTo(userC);
        $("<span />").addClass("coord").text(user.claim.top).appendTo(userC);
        $("<span />").addClass("coord").text(user.claim.right).appendTo(userC);
        $("<span />").addClass("coord").text(user.claim.bottom).appendTo(userC);
        $("<span />").addClass("coord").text(user.history.length).appendTo(userC);
    });
}
