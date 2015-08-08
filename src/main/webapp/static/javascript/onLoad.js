$(function(){
    trunk = repl("topic",[],"topic")
    trunk.prepend(tutorial());
    $("#timelineToggle").click(function(){
        $("#masterTimeline").slideToggle();
    });
});