var Trainer = (function(){
    return {
        simulationOn:function(conversation,slide,onLoad){
            var f = function(){
                $("#simulation").css("opacity",0).on("load",function(){
                    _.defer(onLoad);
                }).attr("src",sprintf("/board?conversationJid=%s&slideId=%s&showTools=true&showSlides=true&unique=true",conversation,slide));
            };
            $(f);
            f();
        },
        hide:function(selector){
            $(selector,$("#simulation").contents()).animate({opacity:0},500);
        },
        flash:function(selector,complete){
            var delay = 250;
            for(var i = 0; i < 2; i++){
                $(selector,$("#simulation").contents()).animate({opacity:0},delay).animate({opacity:1},delay,function(){
                    if(i == 3){
                        if(complete){
                            complete();
                        }
                    }
                });
            }
        },
        showClick:function(selector){
            Trainer.flash(selector,function(){
                $(selector,$("#simulation").contents()).click();
            });
        },
        clearTools:function(){
            _.map([
                "#thumbsColumn",
                "#toolsColumn",
                "#applicationMenuButton",
                "#slideControls",
                ".meters",
                "#masterFooter"
            ],Trainer.hide);
            _.delay(function(){
                $("#simulation").animate({opacity:1});
            },1000);
        },
        showTools:function(){
            _.map([
                "#thumbsColumn",
                "#toolsColumn",
                "#applicationMenuButton",
                "#slideControls",
                ".meters",
                "#masterFooter"
            ],Trainer.highlight);
            _.delay(function(){
                $("#simulation").animate({opacity:1});
            },1000);
        },
        highlight:function(selector){
            $(selector,$("#simulation").contents()).animate({opacity:1},500);
        },
        simulatedUsers:function(users){
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
    }
})();
