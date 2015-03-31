function randomElement(coll){
    return coll[Math.floor(Math.random() * (coll.length - 1))];
}
var Simulation = (function(){
    var users = {};
    var user = function(name){
        var avatar = Canvas.button(LabsLayout.sizes.smallButtonWidth,{
            color:"black",
            icon:"groucho.jpg"
        },name).volatilize();
        var position = {
            x:boardWidth / 2,
            y:boardHeight / 2
        };
        var render = function(){
            var pos = worldToScreen(position.x,position.y);
            avatar.css({
                left:px(pos.x),
                top:px(pos.y)
            });
        };
        var context;
        var bot = new ElizaBot();
        var u = {
            name:name,
            speak:function(){
                if(context){
                    var a = randomElement(context.annotations);
                    var t = bot.transform(a.text);
                    var time = Date.now();
                    context.annotations.push({
                        author:name,
                        text:t,
                        identity:sprintf("%s_%s",name,time),
                        timestamp:time
                    });
                    sendStanza(context);
                }
            },
            tagSomething:function(distanceFromZero){
                distanceFromZero = distanceFromZero || 1000;
                var closeInks = _.filter(boardContent.inks,function(ink){
                    var b = ink.bounds;
                    var x = b[0];
                    var y = b[1];
                    return Math.sqrt(x * x + y * y) <= distanceFromZero;
                });
            },
            moveTo:function(x,y){
                new TWEEN.Tween(position)
                    .to({
                        x:x,
                        y:y
                    },2000)
                    .easing(TWEEN.Easing.Elastic.Out)
                    .onUpdate(function(){
                        position = this;
                        render();
                    }).start();
            },
            jiggle:function(){
                u.moveTo(
                    position.x + (Math.random() - 0.5) * 15,
                    position.y + (Math.random() - 0.5) * 10
                );
            },
            act:function(){
                if(Math.random() < 0.05){
                    if(context){
                        u.speak();
                    }
                    else{
                        var ps = postits();
                        if(ps.length > 0){
                            context = randomElement(ps);
                            var b = context.bounds;
                            if(!b){
                                measurePostit(context);
                                b = context.bounds;
                            }
                            u.moveTo(b.centerX,b.centerY);
                        }
                    }
                }
                else{
                    u.jiggle();
                }
            }
        };
        users[u.name] = u;
        return u;
    };
    var updateSimulation = function(){
        TWEEN.update();
        requestAnimationFrame(updateSimulation);
    }
    var usersAllAct = function(){
        $.each(users,function(i,u){
            u.act();
        });
    }
    setInterval(usersAllAct,1000);
    var simulating = false;
    return {
        users:function(){
            return users;
        },
        controls:function(){
            var s = LabsLayout.sizes.paletteWidth;
            var small = LabsLayout.sizes.smallButtonWidth;
            var p = $("<div />").palette("simulationControls",s,{
                left:[Canvas.button(small,{
                    icon:"friendsplaceholder.png"
                },"Add users").click(function(){
                    _.times(10,function(){
                        user(_.uniqueId("ghost_"));
                    });
                    if(!simulating){
                        requestAnimationFrame(updateSimulation);
                    }
                    simulating = true;
                }),Canvas.button(small,{
                    icon:"dice.jpg"
                },"Give users a chance to act").click(usersAllAct)],
                right:[],
                position:{//Dock at the bottom
                    left:300,
                    top:2000
                }
            }).activate();
            var c = p.context();
            var i = new Image();
            i.onload = function(){
                c.drawImage(i,0,0,s,s);
            };
            i.src = "/static/images/puppet.jpg";
            return p;
        }
    };
})();