var Scene = (function(){
    var sceneChange;
    var tutorials;
    $(function(){
        sceneChange = $("#sceneChange");
        sceneChange.css({
            "background-color":"white",
            color:"black",
            "font-size":"4em",
            "text-align":"center"
        }).hide();
        tutorials = {
            conversationsPopup:{
                conversationSearchTutorial:Scene.conversationTools
            },
            onConversationJoin:{
                coreToolsTutorial:Scene.coreTools
            }
        };
        //Scene.enable();
    });
    var isEnabled = true;
    return {
        enable:function(){
            console.log("enabling tutorial",tutorials);
            isEnabled = true;
            $.each(tutorials,function(eventName,vs){
                var e = Progress[eventName];
                $.each(vs,function(tutorialName,tutorial){
                    e[tutorialName] = tutorial;
                });
            });
        },
        disable:function(){
            isEnabled = false;
            $.each(tutorials,function(eventName,vs){
                var e = Progress[eventName];
                $.each(vs,function(tutorialName,tutorial){
                    delete e[tutorialName];
                });
                delete Progress[eventName];
            });
        },
        change:function(label,complete,duration){
            var textCorrected = false;
            duration = duration || 1000;
            sceneChange.animate({
                opacity:"show"
            },{
                duration:500,
                step:function(){
                    if(!textCorrected){
                        console.log("Setting text",label);
                        sceneChange.text(label);
                        textCorrected = true;
                    }
                }
            }).animate({
                opacity:"hide"
            },{
                duration:duration,
                complete:complete
            });
        },
        cancel:function(){
            sceneChange.hide();
        },
        conversationTools:function(){
            Scene.change("Welcome to MeTLX!");
            Scene.change("This basic tutorial will introduce the core content creation tools.");
            Scene.change("This is the conversation screen, where you can create, search for and find conversations.");
            Scene.change("Let's join a conversation now.");
            var joinConversation = function(){
                Scene.change("Type the word 'sandpit' into the search box",function(){
                    var box = $("#searchForConversationBox");
                    var targetConversation = "tutorial sandpit";
                    Progress.conversationsReceived.conversationJoinTutorial = function(conversations){
                        delete Progress.conversationsReceived.conversationJoinTutorial;
                        if(_.any(conversations,function(c){
                            return c.title == targetConversation;
                        })){
                            Scene.change("All conversations containing your search phrase are listed below the search box.");
                            Scene.change(sprintf("Click the 'Join' button beside the conversation '%s'.",targetConversation));
                        }
                        else{
                            var attempt = box.val();
                            if(attempt != "sandpit"){
                                Scene.change("You didn't find what we were looking for.  Let's try again!");
                                Scene.change(sprintf("Instead of typing '%s', type exactly 'sandpit' into the search box, and then click Search or press enter.",attempt));
                                joinConversation();
                            }
                            else{
                                console.log("Oops, they followed instructions correctly but the sandpit conversation didn't turn up in the search results.");
                            }
                        }
                    }
                    box.flash().focus();
                });
            }
            joinConversation();
        },
        coreTools:function(){
            Scene.change("You have joined a conversation.  The tools on the left of your screen will enable you to create and share content.",function(){
                $("#ribbon").flash(3);
            });
        }
    }
})();