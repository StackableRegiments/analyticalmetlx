$(function(){
    fit = function(){
        try{
            var xInset = 20;
            var yInset = 30;
            var width = window.innerWidth - xInset;
            var height = window.innerHeight - yInset;
            var containerWidth = width;
            boardWidth = containerWidth;
            boardHeight = height;

            $("#masterLayout").height(height).width(width);
            var container = $("#boardContainer");
            container.height(height);
            container.width(containerWidth);
            container.css({
                "margin-left":px(xInset / 2),
                "margin-top":px(-5)
            });
            board.attr("width",px(containerWidth));
            board.attr("height",px(height));
            board.width(containerWidth);
            board.height(height);

            viewboxWidth = requestedViewboxWidth;
            viewboxHeight = requestedViewboxHeight;
            includeView(viewboxX,viewboxY,viewboxWidth,viewboxHeight);
        }
        catch(e){
            console.log("exception in labs fit",e);
        }
    };
    var boomph = function(){
        var bx = 400;
        var by = 400;
        var br = 200;

        var drawRepulsion = function(){
            boardContext.strokeStyle = "orange";
            boardContext.beginPath();
            boardContext.arc(bx,by,br,0,Math.PI * 2);
            boardContext.stroke();
        }

        var offsets = {};
        var boomphTl = screenToWorld(bx - br / 2, by - br / 2);
        var boomphBr = screenToWorld(bx + br / 2, by + br / 2);
        var boomphBounds = [boomphTl.x,boomphTl.y,boomphBr.x,boomphBr.y];
        $.each(boardContent.contentGroups,function(i,g){
            if(intersectRect(g.bounds,boomphBounds)){
                var sb = screenBounds(g.bounds);
                var sp = sb.screenPos;
                var r = repel(sp.x,sp.y,sb.screenWidth/2,sb.screenHeight/2,{
                    x:bx,
                    y:by
                },br);
                var offsetX = sp.x + sb.screenWidth / 2;
                var offsetY = sp.y + sb.screenHeight / 2;
                var offset = {
                    x:r.x - offsetX,
                    y:r.y - offsetY
                };
                $.each(_.pluck(g.items,"identity"),function(j,id){
                    offsets[id] = offset;
                });
            }
        });
        if(_.keys(offsets).length > 0){
            boardContent.elementOffsets = offsets;
            blit();
            drawRepulsion();
        }
    };
    //Progress.viewboxMoved.boomph = _.debounce(boomph,200);
    var handleTilt = function(){
        if(window.DeviceMotionEvent){
            var output = $("<div />",{
                text:"accelerometry"
            }).volatilize().css({
                color:"black",
                "background-color":"lightblue",
                top:px(10),
                left:px(100)
            });
            window.addEventListener("deviceorientation",function(e){
                output.text(sprintf("%x,%s,%s",e.alpha,e.beta,e.gamma));
                applyTilt(e.alpha,e.beta,e.gamma);
            });
        }
    }
    var velocity = {
        x:0,
        y:0
    };
    var acc = 3;
    var tiltThreshold = 15;
    var applyTilt = function(x,y,z){
        if(x > tiltThreshold){
            velocity.x += acc;
        }
        else if(x < -tiltThreshold){
            velocity.x -= acc;
        }
        if(y > tiltThreshold){
            velocity.y = acc;
        }
        else if(y < -tiltThreshold){
            velocity.y -= acc;
        }
    };
    //Progress.historyReceived.addTiltHandlers = handleTilt;
});
function receivePresentUsers(users){
    try{
        var id = "onlineUsersPopup";
        var retriveProfile = function(user){
            return Profiles.list[user] || {
                username:user,
                image:"mysteryPerson.png",
                caption:"Is not registered in the system"
            };
        }
        var renderInSummary = function(user){
            var p = retriveProfile(user);
            return $("<div />").append(
                $("<img />",{
                    src:sprintf("/static/images/%s",p.image),
                    width:px(40),
                    height:px(40)
                })).css({
                    display:"inline-block"
                }).append($("<span />",{
                    text:p.username
                }).css({
                    "margin-left":"1em",
                    "vertical-align":"top"
                }));
        };
        var renderInFull = function(user){
            var p = retriveProfile(user);
            return renderInSummary(user).append($("<div />",{
                text:p.caption
            }).css({
                "margin-bottom":"1em"
            }));
        }
        $("#"+id).remove();
        var popup = $("<div />",{
            class:"midstage",
            id:id
        }).html(unwrap(_.filter(users,function(u){
            return u != username;
        }).map(renderInSummary)));
        popup.prepend($("<div />",{
            class:"knownPeers"
        }).html(unwrap(_.filter(users,function(u){
            return u == username;
        }).map(renderInFull))));
        popup.prepend($("<div />",{
            text:sprintf("%s online Users:",users.length)
        }).css({
            "margin-bottom":"1em"
        })).appendTo($("body")).show();
    }
    catch(e){
        console.log("receivePresentUsers exception",e);
        alert(e);
    }
}
function showIntentionsDialog(){
    var w = LabsLayout.sizes.paletteWidth;
    var l = (boardWidth - w) / 2;
    var t = (boardHeight - w) / 2;
    var intentions;
    var left = [
        Canvas.button(
            LabsLayout.sizes.smallButtonWidth,{
                icon:"friendsplaceholder.png",
                labelDirection:Math.PI
            },
            "I'm looking for someone").click(function(){
                Scene.cancel();
                intentions.move(boardWidth,200);
                retrievePresentUsers();
            }),
        Canvas.button(
            LabsLayout.sizes.smallButtonWidth,{
                icon:"Help.png",
                labelDirection:Math.PI
            },
            "I just want help").click(function(){
                Scene.cancel();
                intentions.move(boardWidth,200);
            })
    ];
    var right = [
        Canvas.button(
            LabsLayout.sizes.smallButtonWidth,{
                icon:"Ribbon-Tutorial.png",
                labelDirection:0
            },
            "I want to go to class").click(function(){
                Scene.cancel();
                $("#startMenu").trigger("click");
                intentions.move(boardWidth,200);
            })
    ];
    intentions = $("<div />").palette("intentionsPalette",w,{
        left:left,
        right:right,
        position:{
            left:l,
            top:t
        }
    });
    intentions.activate();
}
function repel(repelledX,repelledY,repelledWidth,repelledHeight,repulsionCenter,repulsionRadius){
    var repelledCenter = {
        x: repelledX + repelledWidth,
        y: repelledY + repelledHeight
    };
    var deltaX = repelledCenter.x - repulsionCenter.x;
    var deltaY = repelledCenter.y -  repulsionCenter.y;
    var currentDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    var penetration = repulsionRadius - currentDistance;
    if(penetration < 0){
        return {
            x:repelledX,
            y:repelledY,
            penetration:penetration
        }
    }
    else{
        var angle = Math.atan2(deltaY,deltaX);
        return {
            x:repelledX + Math.cos(angle) * (penetration + repelledWidth),
            y:repelledY + Math.sin(angle) * (penetration + repelledHeight),
            penetration:penetration
        };
    }
}
(function($){
    $.fn.depenetrate = function(center,radius){
        this.map(function(){
            var base = $(this);
            var p = base.offset();
            var repelled = repel(
                p.left,p.top,
                base.width() / 2,base.height() /2,
                center,radius);
            if(repelled.penetration > 0){
                var tween = new TWEEN.Tween({
                    x:p.left,
                    y:p.top
                })
                        .to(repelled,800)
                        .easing(TWEEN.Easing.Elastic.Out)
                        .onUpdate(function(){
                            base.css({
                                left:px(this.x),
                                top:px(this.y)
                            });
                        })
                        .onComplete(function(){
                            tween = false;
                        }).start();
                var update = function(){
                    if(tween){
                        requestAnimationFrame(update);
                        TWEEN.update();
                    }
                }
                requestAnimationFrame(update);
            }
            return this;
        });
    }
    $.fn.flash = function(count){
        var t = 100;
        count = count || 2;
        for(var i = 0; i < count; i++){
            this.fadeOut(t).fadeIn(t);
        }
        return this;
    };
    $.fn.volatilize = function(){
        return this.css({
            position:"absolute"
        }).wrap("<span />").appendTo($("body"));
    }
    var defaultStartingPosition = {
        left:0,
        top:200
    };
    var palettes = {};
    var palettePositions = {};
    var docks = {
        left:{},
        right:{},
        top:{},
        bottom:{}
    };
    $.fn.toggles = function(id,activator){
        var base = this;
        base.bind("click",function(){
            var subject = palettes[id];
            if(subject){
                var pos = base.offset();
                delete palettes[id];
                subject.element().remove();
                base.flash();
            }
            else{
                activator();
            }
        });
        return base;
    };
    $.fn.palette = function(id,size,opts){
        var rollLocked = opts.rollLocked || false;
        var left = opts.left || [];
        var right = opts.right || [];
        var position = opts.position;
        var draggable = "draggable" in opts ? opts.draggable : true;

        if(!(id in palettePositions)){
            if(position){
                palettePositions[id] = position;
            }
            else{
                palettePositions[id] = $.extend({},defaultStartingPosition);
            }
        }
        var base = Canvas.button(size,{
            transparent:true
        },"");
        var palettePos = palettePositions[id];
        $(sprintf("#%s",id)).remove();
        var parent = this.attr("id",id);
        parent.append(base);
        var tweens = {};
        var attach = function(n){
            parent.append(n.css({
                position:"absolute"
            }));
        }
        left.map(attach);
        right.map(attach);
        var actOnPosition = function(){
            var threshold = size / 3;
            $.each(tweens,function(i,t){
                t.stop();
            });
            if(!rollLocked){
                if(palettePos.left < threshold){
                    unroll({
                        position:"left"
                    });
                }
                else if(palettePos.top < threshold){
                    unroll({
                        position:"top"
                    });
                }
                else if(palettePos.left > window.innerWidth - threshold - size){
                    unroll({
                        position:"right"
                    });
                }
                else if(palettePos.top > window.innerHeight - threshold - size){
                    unroll({
                        position:"bottom"
                    });
                }
                else{
                    roll();
                }
            }
            else{
                roll();
            }
            $.each(tweens,function(i,t){
                t.start();
            });
            requestAnimationFrame(update);
            if(explaining){
                parent.find(".explicable").explain();
            }
        }
        var stopTweens = function(){
            $.each(tweens,function(i,t){
                t.stop();
            });
        }
        var roll = function(){
            $.each(docks,function(i,d){
                if(id in d){
                    delete d[id];
                }
            });
            base.show();
            var centerInset = (LabsLayout.sizes.paletteWidth - LabsLayout.sizes.centerButtonWidth) / 2;
            var centerOffset = LabsLayout.sizes.centerButtonWidth / 3;
            var offset = size / 2;
            var arc = Math.PI / (right.length);
            var start = {};
            var target = {};
            tweens = {};
            var rot = Math.PI / 3;
            $.each(right,function(i,child){
                var drawChild = function(){
                    var innerOffset = child.width() / 2;
                    var radiate = function(length){
                        return {
                            x:offset - innerOffset + Math.cos(arc * i - rot) * length,
                            y:offset - innerOffset + Math.sin(arc * i - rot) * length
                        }
                    }
                    var target = radiate(offset);
                    child.explain(parent,radiate(offset));
                    tweens[i] = new TWEEN.Tween({
                        x:centerInset - centerOffset,
                        y:centerInset + centerOffset
                    })
                        .to(target,800)
                        .easing(TWEEN.Easing.Elastic.Out)
                        .onUpdate(function(){
                            child.css({
                                left:px(this.x),
                                top:px(this.y)
                            });
                        })
                        .onComplete(function(){
                            delete tweens[i];
                        });
                    return tweens[i];
                };
                if(child.hasClass("focussedSubmode") && false){
                    _.defer(function(){
                        drawChild().start();
                    });//Draw it afterwards so it sits on top
                }
                else{
                    drawChild();
                }
            });
            arc = Math.PI / left.length;
            left.map(function(node,i){
                var w = node.width();
                var xOffset = w / 2;
                var yOffset = w / 2;
                node.css({
                    left:offset - xOffset + Math.cos(arc * i + Math.PI - rot) * offset,
                    top:offset - yOffset + Math.sin(arc * i + Math.PI - rot) * offset
                });
                node.explain(parent,{
                    x:offset - xOffset + Math.cos(arc * i + Math.PI - rot) * offset,
                    y:offset - yOffset + Math.sin(arc * i + Math.PI - rot) * offset
                });
            });
        }
        var unroll = function(opts){
            //console.log("unroll",id,parent.css("left"),parent.css("top"));
            base.hide();
            tweens = {};
            var padding = 5;
            var xOffset = padding;
            var yOffset = padding;
            var dir = opts.position;
            var length = _.foldl(docks[dir],function(acc,val,key){
                var res = acc;
                if(key != id){
                    res += val;
                }
                return res;
            },0);
            var maxWidth = Math.max.apply(Math,_.map(left,function(n){
                return n.width();
            }));
            if(length > 0){
                length += LabsLayout.sizes.centerButtonWidth;
            }
            var windowWidth = window.innerWidth;
            var windowHeight = window.innerHeight;
            switch(dir){
            case "left":
                yOffset += $("#startMenu").outerWidth()
                yOffset += length;
                break;
            case "right":
                yOffset += $("#startMenu").outerWidth()
                yOffset += length;
                xOffset += windowWidth - maxWidth;
                xOffset -= padding * 2;
                parent.css({
                    left:"",
                    right:px(padding)
                });
                break;
            case "top":
                xOffset += $("#startMenu").outerWidth()
                xOffset += length;
                break;
            case "bottom":
                xOffset += $("#startMenu").outerWidth()
                xOffset += length;
                yOffset += windowHeight - maxWidth - padding;
                break;
            }
            parent.css({
                top:px(yOffset),
                left:px(xOffset)
            });
            var i = 0;
            xOffset = 0;
            yOffset = 0;
            var joinStack = function(node){
                var _i = i;
                var xo = xOffset;
                var yo = yOffset;
                node.css({
                    "padding-top":px(padding)
                });
                var pos = node.position();
                var target = {
                    x:xo,
                    y:yo
                };
                tweens[_i] = new TWEEN.Tween({
                    x:pos.left + palettePos.left,
                    y:pos.top + palettePos.top
                })
                    .to(target,800)
                    .easing(TWEEN.Easing.Elastic.Out)
                    .onUpdate(function(){
                        node.css({
                            left:px(this.x),
                            top:px(this.y)
                        });
                    })
                    .onComplete(function(){
                        delete tweens[_i];
                    });

                switch(dir){
                case "left":
                    node.explain(parent,{
                        x:xo + padding,
                        y:yo + padding * 2
                    });
                    break;
                case "right":
                    node.explain(parent,{
                        x:xo,
                        y:yo + padding * 2
                    });
                    break;
                case "top":
                    break;
                case "bottom":
                    break;
                };

                switch(dir){
                case "left":
                case "right":
                    yOffset += node.outerHeight();
                    break;
                case "top":
                case "bottom":
                    xOffset += node.outerHeight();
                    break;
                };

                i++;
            };
            left.map(joinStack);
            right.map(joinStack);
            $.each(docks,function(i,d){
                if(id in d){
                    delete d[id];
                }
            });
            var sum = function(coll,prop){
                return _.foldl(coll,function(acc,item){
                    return acc + $(item)[prop]();
                },0);
            }
            docks[dir][id] = sum(left,"width") + sum(right,"width");
            return this;
        }
        var update = function(t){
            if(_.keys(tweens).length > 0){
                requestAnimationFrame(update);
                TWEEN.update();
            }
        }
        var explaining = false;
        parent.volatilize();
        if(draggable){
            parent.draggable({
                start:function(){
                    explaining = parent.find(".explanationLabel").length > 0;
                    parent.unexplain();
                },
                stop:function(){
                    var pos = $(this).position();
                    palettePos.left = pos.left;
                    palettePos.top = pos.top;
                    actOnPosition();
                }
            })
        };
        parent.css({
            position:"absolute",
            left:px(palettePos.left),
            top:px(palettePos.top)
        });
        var p = {
            element:function(){
                return parent;
            },
            activate:function(){
                actOnPosition();
                return this;
            },
            deactivate:function(){
                $.each(tweens,function(i,t){
                    t.stop();
                });
                return this;
            },
            move:function(x,y,opts){
                explaining = parent.find(".explanationLabel").length > 0;
                parent.unexplain();
                opts = opts || {};
                palettePositions[id] = {
                    left:x,
                    top:y
                }
                palettePos = palettePositions[id];
                parent.css({
                    position:"absolute",
                    left:px(palettePos.left),
                    top:px(palettePos.top)
                });
                actOnPosition();
                if(opts.complete){
                    opts.complete();
                }
            },
            docks:docks,
            context:function(){
                return base[0].getContext("2d");
            }
        };
        palettes[id] = p;
        return p;
    }
})(jQuery);
function showLobby(){
    fit();
    Scene.change("What do you want to do today?",function(){},6000);
    var s = LabsLayout.sizes.centerButtonWidth;
    var sourceX = (boardWidth - s) / 2;
    var sourceY = (boardHeight - s) / 2;
    var targetX = 5;
    var help = $("#explainAll");
    var tween = new TWEEN.Tween({
        x:sourceX,
        y:sourceY
    }).to({
        x:targetX,
        y:5
    },1000)
        .easing(TWEEN.Easing.Cubic.InOut)
        .delay(0)
        .onUpdate(function(){
            help.css({
                right:px(this.x),
                top:px(this.y)
            });
        }).onComplete(function(){
            tween = false;
            help.flash()
            _.delay(function(){
                help.trigger("click");
            },1000);
        });
    tween.start();
    var update = function(){
        if(tween){
            requestAnimationFrame(update);
            TWEEN.update();
        }
    }
    requestAnimationFrame(update);
    showIntentionsDialog();
}
function postits(){
    var validPostits = _.filter(boardContent.userClumps,function(c){
        return _.any(c.annotations,function(a){
            return a.text.length > 0;
        });
    });
    return _.values(_.reduce(_.sortBy(validPostits,"timestamp"),function(acc,item){
        acc[item.identity] = item;
        if(!("focussed" in item)){
            item.focussed = false;
        }
        return acc;
    },{}));
}
function annotations(p){
    return _.values(_.reduce(_.sortBy(p.annotations,"timestamp"),function(acc,item){
        acc[item.identity] = item;
        return acc;
    },{}));
}
var LabsLayout = (function(){
    var paletteWidth = 250;
    var centerButtonWidth = 80;
    var smallButtonWidth = 50;
    $("head").append($("<link />",{
        rel:"stylesheet",
        href:"static/css/labs.css"
    }));
    var similarFilter = function(p){
        return function(p2){
            return _.any(p2.inkIds,function(id){
                return p.inkIds.indexOf(id) >= 0;
            });
        }
    }
    var overlappingPostits = function(p){
        return _.values(postits()).filter(similarFilter(p));
    };
    var tools = {
        draw:1
    };
    var setTools = function(ts){
        tools = ts;
        Modes.currentMode.deactivate();
        Progress.call("configurationChanged");
    };
    var toggleTool = function(t){
        if(t in tools){
            delete tools[t];
        }
        else{
            tools[t] = 1;
        }
        Modes.currentMode.deactivate();
        Progress.call("configurationChanged");
    };
    var overrideToolbar = function(){
        console.log("Overriding toolbar");
        var modes = function(){
            return Object.keys(tools).map(function(mode,i){
                var isCurrent = Modes[mode] == Modes.currentMode;
                var w = isCurrent? LabsLayout.sizes.centerButtonWidth: LabsLayout.sizes.smallButtonWidth;
                var xOffset = w / 2;
                var yOffset = w / 2;
                return Canvas.button(w,{
                    labelDirection:Math.PI,
                    color:"blue",
                    glyphColor:"white",
                    glyph:mode[0].toUpperCase()
                },mode).css({
                }).click(function(){
                    Modes[mode].activate();
                });
            });
        }
        var toolsPaletteId = "toolsPalette";
        Modes.pan.drawTools = function(){
            var p = $("<div />").palette(toolsPaletteId,paletteWidth,{
                left:modes(),
                right:[]
            }).activate();
        };
        Modes.quiz.drawTools = function(){
            var p = $("<div />").palette(toolsPaletteId,paletteWidth,{
                left:modes(),
                right:[
                    Canvas.button(LabsLayout.sizes.smallButtonWidth,{
                        glyph:"?",
                        glyphColor:"black"
                    }).click(function(){
                        showBackstage("quizzes");
                    })
                ]
            }).activate();
        };
        Modes.groups.drawTools = function(){
            var p = $("<div />").palette(toolsPaletteId,paletteWidth,{
                left:modes(),
                right:[
                    Canvas.button(LabsLayout.sizes.smallButtonWidth,{
                        glyph:"G",
                        glyphColor:"black"
                    }).click(function(){
                        GroupFinder.showPopup();
                    })
                ]
            }).activate();
        };
        Modes.tag.drawTools = function(){
            var size = Math.min(paletteWidth * 3, Math.min(boardHeight,boardWidth) * 0.8);
            console.log("drawing tag tools",size);
            var p = $("<div />").palette(toolsPaletteId,size,{
                left:modes(),
                right:[
                    Canvas.button(Modes.tag.raySpan == "Tight" ? LabsLayout.sizes.centerButtonWidth : LabsLayout.sizes.smallButtonWidth,{
                        glyph:"<",
                        glyphColor:"black"
                    },"Select things close to your pointer").click(function(){
                        Modes.tag.raySpan = "Tight";
                        Modes.tag.drawTools();
                    }),
                    Canvas.button(Modes.tag.raySpan == "Loose" ? LabsLayout.sizes.centerButtonWidth : LabsLayout.sizes.smallButtonWidth,{
                        glyph:">",
                        glyphColor:"black"
                    },"Select things in a wide range from your pointer").click(function(){
                        Modes.tag.raySpan = "Loose";
                        Modes.tag.drawTools();
                    }),
                    Canvas.button(Modes.tag.additive == "Add" ? LabsLayout.sizes.centerButtonWidth : LabsLayout.sizes.smallButtonWidth,{
                        glyph:"+",
                        glyphColor:"black"
                    },"Add elements to your selection").click(function(){
                        Modes.tag.additive = "Add";
                        Modes.tag.drawTools();
                    }),
                    Canvas.button(Modes.tag.additive == "Subtract" ? LabsLayout.sizes.centerButtonWidth : LabsLayout.sizes.smallButtonWidth,{
                        glyph:"-",
                        glyphColor:"black"
                    },"Remove elements from your selection").click(function(){
                        Modes.tag.additive = "Subtract";
                        Modes.tag.drawTools();
                    }),
                    Canvas.button(LabsLayout.sizes.smallButtonWidth,{
                        glyph:"X",
                        glyphColor:"red"
                    },"Clear selection").click(function(){
                        Modes.tag.clearGroup();
                    }),
                    Canvas.button(LabsLayout.sizes.centerButtonWidth,{
                        icon:"tick.jpg"
                    },"Submit").click(function(){
                        var time = Date.now();
                        var clump = {
                            type:"clump",
                            identity:sprintf("c_%s_%s",username,Date.now()),
                            author:username,
                            timestamp:Date.now(),
                            target:"presentationSpace",
                            privacy:"public",
                            slide:Conversations.getCurrentSlideJid(),
                            inkIds:_.pluck(_.values(Modes.tag.activeGroup),"identity"),
                            active:true,
                            annotations:[
                                {
                                    author:username,
                                    text:"",
                                    identity:sprintf("%s_%s",username,time),
                                    timestamp:time
                                }
                            ]
                        };
                        focussedPostit = clump.identity;
                        sendStanza(clump);
                    })
                ]
            }).activate();
            var c = p.context();
            var drawTaggedContent = function(){
                var g = Modes.tag.activeGroup;
                c.fillStyle = "black";
                c.fillRect(0,0,size,size);
                $("#selectionAdorner").empty();
                $.each(g,function(k,v){
                    drawSelectionBounds(v);
                });
            };
            var mode = {
                overview:0,
                sticky:1,
                peers:0
            };
            var focussedPostit;
            var focusPostit = function(p){
                focussedPostit = p.identity;
                var b = p.bounds;
                var x = b.centerX;
                var y = b.centerY;
                var pEl = $(".postit");
                $.each(postits(),function(i,c){
                    c.focussed = false;
                });
                p.focussed = true;
                var pw = size;
                Extend.center(x,y,
                              function(){
                                  var screenPos = worldToScreen(x,y);
                                  var targetX = screenPos.x;
                                  var targetY = screenPos.y;
                                  var clearMode = function(){
                                      $.each(mode,function(k){
                                          mode[k] = 0;
                                      });
                                  }
                                  var size = function(k){
                                      return mode[k] == 1 ? LabsLayout.sizes.centerButtonWidth : LabsLayout.sizes.smallButtonWidth;
                                  }
                                  var activator = function(k,label,action){
                                      var c = Canvas.button(size(k),{
                                          glyphColor:"black",
                                          glyph:label[0].toUpperCase()
                                      },label).click(function(){
                                          clearMode();
                                          mode[k] = 1;
                                          drawFocus();
                                          action();
                                      });
                                      if(mode[k] == 1){
                                          _.defer(action);
                                      }
                                      return c;
                                  }
                                  var addAnnotation = function(){
                                      var square = Math.sqrt(Math.pow(pw,2)/2);
                                      var yOffset = (pw - square) / 2;
                                      var c = $("<div />").css({
                                          id:"annotationControls",
                                          position:"absolute",
                                          width:"100%",
                                          "max-height":px(square),
                                          top:px(yOffset),
                                          "text-align":"center",
                                          left:0,
                                          "overflow-y":"auto"
                                      });
                                      var input = $("<textarea />",{
                                          class:"postitAnnotation"
                                      }).css({
                                          "background-color":"white"
                                      }).appendTo(c);
                                      var controls = $("<div />").appendTo(c);
                                      $("<input />",{
                                          type:"button",
                                          value:"Clear"
                                      }).css({
                                          "background-color":"white"
                                      }).click(function(){
                                          input.val("").focus();
                                      }).appendTo(controls);
                                      $("<input />",{
                                          type:"button",
                                          value:"Submit"
                                      }).css({
                                          "background-color":"white"
                                      }).click(function(){
                                          var t= input.val();
                                          var time = Date.now();
                                          if(t.length > 0){
                                              p.annotations.push({
                                                  author:username,
                                                  text:t,
                                                  identity:sprintf("%s_%s",username,time),
                                                  timestamp:time
                                              });
                                              sendStanza(p);
                                          }
                                      }).appendTo(controls);
                                      _.defer(function(){
                                          input.focus();
                                      });
                                      return c;
                                  };
                                  var drawFocus = function(){
                                      var left = [
                                          Canvas.button(LabsLayout.sizes.centerButtonWidth,{
                                              glyphColor:"black",
                                              glyph:"X"
                                          },"Close").click(function(){
                                              focussedPostit = undefined;
                                              $("#focussedPostit").remove();
                                              drawPostits();
                                          }),
                                          activator("overview","Overview",function(){
                                              drawPreview();
                                          }),
                                          activator("sticky","Sticky",function(){
                                              drawPreview();
                                              var parent = $("#focussedPostit");
                                              var container = $("<div />").css({
                                                  position:"absolute",
                                                  width:"100%",
                                                  top:0
                                              }).appendTo(parent);
                                              $("#annotationControls").remove();
                                              var top = addAnnotation().prependTo(parent);
                                              for(var i = p.annotations.length - 1; i >= 0; i--){
                                                  var a = p.annotations[i];
                                                  if(a.text.length > 0){
                                                      var d = $("<div />",{
                                                          text:a.text,
                                                          class:"postitAnnotation"
                                                      }).appendTo(top);
                                                      return d;
                                                  }
                                              }
                                          }),
                                          activator("peers","Related content",function(){})
                                      ];
                                      var focus = $("<div />").palette("focussedPostit",pw,{
                                          left:left,
                                          position:{
                                              left:targetX - pw / 2,
                                              top:targetY - pw / 2
                                          },
                                          rollLocked:true,
                                          draggable:false
                                      },"").activate();
                                      var c = focus.context();
                                      var drawPreview = function(){
                                          c.fillStyle = "gray";
                                          c.fillRect(0,0,pw,pw);
                                          var inks = _.compact(p.inkIds.map(function(id){
                                              return boardContent.inks[id] || boardContent.highlighters[id];
                                          }));
                                          var pb = p.bounds;
                                          var canvas = $("<canvas />");
                                          canvas.attr("width",px(pb.width));
                                          canvas.attr("height",px(pb.height));
                                          var context = canvas[0].getContext("2d");
                                          context.fillStyle = "white";
                                          context.fillRect(0,0,pb.width,pb.height);
                                          $.each(inks,function(i,item){
                                              var ix = item.bounds[0] - pb.minX;
                                              var iy = item.bounds[1] - pb.minY;
                                              context.drawImage(item.canvas,ix,iy);
                                          });
                                          var scale;
                                          var height;
                                          var width;
                                          if(pb.width > pb.height){
                                              scale = pb.width / pw;
                                              width = pw;
                                              height = pb.height / scale;
                                          }
                                          else{
                                              scale = pb.height / pw;
                                              width = pb.width / scale;
                                              height = pb.height;
                                          }
                                          width *= 0.9;
                                          height *= 0.9;
                                          var xOffset = (pw - width) / 2;
                                          var yOffset = (pw - height) / 2;
                                          c.drawImage(canvas[0],
                                                      0,0,Math.floor(pb.width),Math.floor(pb.height),
                                                      xOffset,yOffset,width,height
                                                     );
                                          c.strokeStyle = "black";
                                          var lw = 4;
                                          c.lineWidth = lw;
                                          c.beginPath();
                                          var r = (pw / 2) - lw / 2;
                                          c.arc(pw/2,pw/2,
                                                r,
                                                0,Math.PI * 2);
                                          c.stroke();
                                      }
                                      drawPreview();
                                  }
                                  drawFocus();
                                  drawPostits();
                              });
            }
            var drawPostit = function(p){
                var b = p.bounds;
                var pos = worldToScreen(average([b[2],b[0]]),average([b[3],b[1]]));
                var jitter = 0;//Math.random() * jitterLength - jitterLength / 2;
                pos.x += jitter;
                pos.y += jitter;
                return Canvas.button(LabsLayout.sizes.centerButtonWidth,{
                    color:"yellow"
                },p.annotations[p.annotations.length - 1].text).addClass("postit").volatilize().css({
                    left:px(pos.x),
                    top:px(pos.y)
                }).click(function(e){
                    focusPostit(p);
                    e.preventDefault();
                })
            };
            var drawPostits = function(){
                $(".postit").remove();
                $(".boomphedCanvas").remove();
                var viewBounds = [viewboxX,viewboxY,viewboxX+viewboxWidth,viewboxY+viewboxHeight];
                var ps = _.groupBy(postits(),function(p){
                    return p.focussed;
                });
                if("false" in ps){
                    ps.false.map(drawPostit);
                }
                if("true" in ps){
                    var repeller = ps["true"][0];
                    var b = repeller.bounds;
                    var screenPos = worldToScreen(b.centerX,b.centerY);
                    $(".postit").depenetrate(screenPos,size/2);
                    var boomphContent = function(){
                        boardContent.contentGroups.filter(function(g){
                            return intersectRect(b,g.bounds);
                        }).map(function(g,i){
                            var gb = g.bounds;
                            var gScreenPos = worldToScreen(gb[0],gb[1]);
                            var gScreenExtent = worldToScreen(gb[2],gb[3]);
                            var gsw = gScreenExtent.x - gScreenPos.x;
                            var gsh = gScreenExtent.y - gScreenPos.y;
                            var thumbSize = 100;
                            var thumbScale = gsw / thumbSize;
                            gsw = gsw / thumbScale;
                            gsh = gsh / thumbScale;
                            var canvas = $("<canvas />",{
                                width:px(gsw),
                                height:px(gsh),
                                class:"boomphedCanvas"
                            }).volatilize().css({
                                width:px(gsw),
                                height:px(gsh),
                                border:"2px solid red",
                                left:px(gScreenPos.x),
                                top:px(gScreenPos.y),
                                opacity:0.5
                            });
                            canvas[0].getContext("2d").drawImage(g.canvas,0,0,gsw,gsh);
                            canvas.depenetrate(screenPos,size);
                        });
                    }
                }
            }
            Progress.tagGroupChanged.redrawPuck = drawTaggedContent;
            Progress.tagGroupChanged.redrawPostits = drawPostits;
            Progress.viewboxMoved.redrawPostits = drawPostits;
            Progress.clumpReceived.redrawPostits = function(c){
                if(focussedPostit == c.identity){
                    c.focussed = true;
                    focusPostit(c);
                }
                else{
                    drawPostits();
                }
            }
            var fps = postits().filter(function(p){
                return p.focussed;
            });
            if(fps.length > 0){
                focusPostit(fps[0]);
            }
            else{
                drawPostits();
            }
        };
        Modes.insert.drawTools=function(){
            var buttons = "text image".split(" ").map(function(t){
                return Canvas.button(Modes.insert.currentInsertMode == t? LabsLayout.sizes.centerButtonWidth : LabsLayout.sizes.smallButtonWidth, {
                    glyphColor:"black",
                    glyph:t[0].toUpperCase()
                },sprintf("Insert %s",t)).click(function(){
                    Modes.insert.currentInsertMode = t;
                    Modes.insert.drawTools();
                });
            });
            $("<div />").palette(toolsPaletteId,paletteWidth,{
                left:modes(),
                right:buttons,
                position:{
                    left:paletteWidth,
                    top:(boardHeight - paletteWidth) / 2
                }
            }).activate();
        };
        Modes.select.drawTools = function(){
            $("<div />").palette(toolsPaletteId,paletteWidth,{
                left:modes(),
                right:"delete resize".split(" ").map(function(id){
                    return Canvas.button(smallButtonWidth,{
                        color:"red"
                    },id).attr("id",id);
                },{
                    position:{
                        left:(window.innerWidth - paletteWidth)/2,
                        top:(window.innerHeight - paletteWidth)/2
                    }
                })
            }).activate();
        };
        Modes.draw.drawTools = function(){
            var brushes = Modes.draw.brushes.map(function(brush){
                var hasFocus = !(Modes.draw.erasing || brush != Modes.draw.drawingAttributes);
                var size = hasFocus? centerButtonWidth : smallButtonWidth;
                var dotButton = Canvas.button(size,{
                    color:"white",
                    draw:function(context,width,height){
                        context.fillStyle = brush.color;
                        context.beginPath();
                        context.arc(width/2,height/2,brush.width,0,Math.PI*2);
                        context.fill();
                        if(brush.isHighlighter){
                            var fontSize = size / 2;
                            context.fillStyle = "white";
                            context.font = sprintf("bold %spt Arial",fontSize);
                            context.fillText("H",width/2 - fontSize / 2,height/2 + fontSize / 2);
                        }
                    }
                }).click(function(){
                    Modes.draw.erasing = false;
                    Modes.draw.drawingAttributes = brush;
                    Modes.draw.drawTools();
                    Modes.draw.drawAdvancedTools(Modes.draw.drawingAttributes);
                });
                if(hasFocus){
                    dotButton.addClass("focussedSubmode");
                }
                return dotButton;
            });
            var eraser = Canvas.button(Modes.draw.erasing? centerButtonWidth : smallButtonWidth,{
                icon:"ShinyEraser.png"
            },"Erase")
                    .click(function(){
                        Modes.draw.erasing = true;
                        Modes.draw.drawTools();
                    });
            if(Modes.draw.erasing){
                eraser.addClass("focussedSubmode");
            }
            brushes.push(eraser);
            brushes.push(Canvas.button(smallButtonWidth,{
                glyphColor:"black",
                glyph:"..."
            },"Advanced").click(function(){
                if(window.currentBackstage == "customizeBrush"){
                    hideBackstage();
                }
                else{
                    Modes.draw.drawAdvancedTools(Modes.draw.drawingAttributes);
                    showBackstage("customizeBrush");
                }
            }));
            var p = $("<div />").palette(toolsPaletteId,paletteWidth,{
                left:modes(),
                right:brushes,
                position:{
                    left:0,
                    top:paletteWidth
                }
            }).activate();
        }
    };
    var showStartMenuOptions = function(){
        var p = $("<div />").palette("startMenuPalette",paletteWidth,{
            left:[
                Canvas.button(LabsLayout.sizes.smallButtonWidth,{
                    icon:"close.png"
                },"Leave conversation").click(leaveConversation),
                Canvas.button(LabsLayout.sizes.smallButtonWidth,{
                    glyphColor:"black",
                    glyph:"..."
                },"Preferences").click(function(){
                    showBackstage("preferences");
                })],
            right:[
                Canvas.button(LabsLayout.sizes.smallButtonWidth,{
                    icon:"search.png"
                },"Search for a conversation").click(function(){
                    p.move(paletteWidth,0,{
                        complete:function(){
                            showBackstage("conversations");
                            var sb = $("#searchForConversationBox").attr({
                                placeholder:"Enter terms to search conversations by title"
                            });
                            _.defer(function(){
                                sb.focus();
                            });
                        }
                    });
                }).attr({
                    id:"conversationSearch"
                })],
            position:{
                left:paletteWidth,
                top:paletteWidth
            }
        }).activate();
        p.move(0, paletteWidth, {
            complete:function(){
                $("#conversationSearch").trigger("click");
            }
        });
    };
    var minimap = (function(){
        var rollLocked = true;
        var mm;
        var context;
        var size = paletteWidth;
        var clear = function(){
            if(!context){
                minimap.activate();
            }
            context.fillStyle = "white";
            context.fillRect(0,0,size,size);
            var diameter = size;
            var radius = diameter / 2;
            context.lineWidth = 3;
            context.strokeStyle = "black";
            context.beginPath();
            context.arc(
                radius,
                radius,
                radius - context.lineWidth / 2,
                0,Math.PI*2);
            context.stroke();
        }
        var draw = function(){
            clear();
            var xOffset = size / 3;
            var yOffset = size / 3;
            var xScale = boardContent.width / (size - xOffset);
            var yScale = boardContent.height / (size - yOffset);

            var worldToMap = function(bounds){
                return {
                    x:(bounds[2] - bounds[0]) / xScale + xOffset / 2,
                    y:(bounds[3] - bounds[1]) / yScale + yOffset / 2
                };
            }
            var drawElement = function(e,color){
                var c = e.canvas || e.imageData;
                var b = e.bounds;
                b = [
                    b[0] - boardContent.minX,
                    b[1] - boardContent.minY,
                    b[2] - boardContent.minX,
                    b[3] - boardContent.minY
                ];
                context.drawImage(
                    c,
                    0,0,c.width,c.height,
                    b[0] / xScale,
                    b[1] / yScale,
                    (b[2] - b[0]) / xScale,
                    (b[3] - b[1]) / yScale
                );
            }
            var drawCategory = function(k,color){
                $.each(boardContent[k],function(i,el){
                    drawElement(el,k);
                });
            }
            var drawCamera = function(){
                context.strokeStyle = "red";
                context.fillStyle = "yellow";
                context.lineWidth = 1;
                var x = (viewboxX - boardContent.minX) / xScale;
                var y = (viewboxY - boardContent.minY) / yScale;
                var x2 = (viewboxWidth - boardContent.minX + viewboxX) / xScale;
                var y2 = (viewboxHeight -boardContent.minY + viewboxY) / yScale;

                /*
                 console.log("viewbox",viewboxX,viewboxY,viewboxWidth,viewboxHeight);
                 console.log("drawCamera",x,y,x2,y2);
                 */

                context.globalAlpha = 0.4;

                context.fillRect(x,y,x2 - x,y2 - y);

                context.beginPath();
                context.moveTo(x,0);
                context.lineTo(x,size);
                context.stroke();

                context.beginPath();
                context.moveTo(x2,0);
                context.lineTo(x2,size);
                context.stroke();

                context.beginPath();
                context.moveTo(0,y);
                context.lineTo(size,y);
                context.stroke();

                context.beginPath();
                context.moveTo(0,y2);
                context.lineTo(size,y2);
                context.stroke();

                context.globalAlpha = 1.0;
            }
            drawCategory("images","blue");
            drawCategory("highlighters","yellow");
            drawCategory("texts","green");
            drawCategory("inks","purple");
            drawCamera();
        };
        return {
            draw:draw,
            activate:function(){
                var currentP = Privacy.getCurrentPrivacy();
                var left = [
                    Canvas.button("PRIVATE" == currentP ? centerButtonWidth : smallButtonWidth,{
                        color:"red",
                        glyphColor:"white",
                        glyph:"p"
                    },"Private mode").click(function(){
                        Privacy.setPrivacy("PRIVATE");
                        minimap.activate();
                        minimap.draw();
                    })
                ];
                if(Conversations.shouldPublishInConversation(Conversations.getCurrentConversation())){
                    var p = "PUBLIC";
                    left.push(Canvas.button(p == currentP ? centerButtonWidth : smallButtonWidth,{
                        color:"blue",
                        glyphColor:"white",
                        glyph:"P"

                    },"Public mode").click(function(){
                        Privacy.setPrivacy(p);
                        minimap.activate();
                        minimap.draw();
                    }));
                }
                var right = [
                    Canvas.button(smallButtonWidth,{
                        icon:"Attachment.png"
                    },"What do you want to do today?").toggles("intentionsPalette",showIntentionsDialog),
                    Canvas.button(smallButtonWidth,{
                        icon:rollLocked ? "SyncGreen.png" : "SyncRed.png"
                    }).click(function(){
                        rollLocked = !rollLocked;
                        minimap.activate();
                        minimap.draw();
                    })
                ];
                mm = $("<div />").palette("minimapPalette",paletteWidth,{
                    left:left,
                    right:right,
                    rollLocked:rollLocked
                });
                mm.activate();
                mm.element().css({
                    position:"fixed",
                    left:"",//Unset value
                    top:px(5),
                    right:px(centerButtonWidth)
                });
                context = mm.context();
                clear();
            }
        }
    })();

    $(function(){
        $("#boardHeader").hide();
        $("#thumbsColumn").empty();
        $("#toolsColumn").empty();
        $("#applicationMenuButton").remove();

        var padding = 5;

        Canvas.button(centerButtonWidth,{
            icon:"metlx.png"
        },"Menu").attr({
            id:"startMenu"
        }).volatilize().css({
            top:px(padding),
            left:px(padding)
        }).click(function(){
            if(window.currentBackstage != "none"){
                hideBackstage();
            }
            $(".midstage").remove();
        }).toggles("startMenuPalette",showStartMenuOptions);

        Canvas.button(centerButtonWidth,{
            icon:"Help.png"
        },"Context specific help").volatilize().click(function(){
            var explicable = $(".explicable");
            var explanations = $(".explanationLabel");
            if(explanations.length > 0){
                explicable.parent().unexplain();
                $(this).flash();
            }
            else{
                explicable.explain();
            }
        }).attr({
            id:"explainAll"
        }).volatilize().css({
            right:px(padding),
            top:px(padding)
        });

        Progress.configurationChanged["showPalettes"] = function(){
            console.log("showPalettes overriding toolbar");
            overrideToolbar();
            Modes.currentMode.activate();
        };
        Progress.onConversationJoin["showPalettes"] = function(){
            overrideToolbar();
            minimap.activate();
            Modes.draw.activate();
        }
        Progress.onConversationJoin["showTitle"] = function(){
            Scene.change(Conversations.getCurrentConversation().title,8000);
            $(".explicable").parent().unexplain();
            $("#intentionsPalette").remove();
        };
        Progress.historyReceived["drawMinimap"] = function(){
            minimap.draw();
        };
        Progress.viewboxMoved["redrawMinimap"] = function(){
            if("jid" in Conversations.getCurrentConversation()){
                minimap.draw();
            }
        }

        Composition.activate();
    });
    return {
        toggleTool:toggleTool,
        setTools:setTools,
        sizes:{
            smallButtonWidth:smallButtonWidth,
            centerButtonWidth:centerButtonWidth,
            paletteWidth:paletteWidth
        }
    }
})();
