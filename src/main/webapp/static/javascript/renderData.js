var renderers = {
    topic:renderTopic,
    question:renderQuestionDetail,
    answer:renderAnswerDetail,
    comment:renderCommentDetail,
    vote:renderVote,
    author:renderAuthor,
    conversation:renderConversation,
    slideVisualElements:renderSlideVisualElements,
    searchTerms:renderSearchTerms,
    slide:renderSlide,
    objectives:renderObjectives
}
function render(id){
    var t = types[id]
    var r = renderers[t]
    var i = items[id]
    return r(i)
}
function renderObjectives(context){
    var container = $("<div />");
    var week = inferWeek(context);
    var o = $("<ol />");
    keywordObjectives.objectives[Math.ceil(week / 2)].map(function(objective){
        o.append($("<li />",{
            text:objective
        }));
    })
    container.append(o);
    var kw = $("<ul />");
    container.append(kw);
    keywordObjectives.keywords[week].terms.map(function(keyword){
        kw.append($("<li />",{
            text:keyword
        }));
    });
    return container;
}
function renderSlide(slide){
    var handwriting = metlTextForSlide(slide).map(function(term){
        return unclean[term];
    })
    var root = ""
    return $("<img />",{
        src:sprintf("%s/thumbnail/standalone/%s",root,slide),
        title:sprintf("Page %s: %s",slide,handwriting)
    }).css("width",px($(".replContainer").width()));
}
function renderSearchTerms(matchId){
    var matches = items[matchId];
    var w = $(".replContainer").width()
    var cont = $("<div />",{
        width:w,
        border:"1px solid black"
    })
    matches.map(function(match){
        switch(match.type){
        case "slide":cont.append(renderers.slide(match.slide).click(function(){
            repl("slide",[match.slide],sprintf("Slide %s",match.slide));
        })); break;
        default:
            if(types[match.item] == "question"){
                cont.append(renderers.question(match.item,2));//More than 1 so it can isolate itself
            }
            else{
                cont.append(renderers[match.type](match.item));
            }
            break;
        }
    });
    return cont;
}
function renderConversation(jid){
    var details = items[jid];
    var contId = genId();
    var w = $(".replContainer").width()
    var cont = $("<div />",{
        width:w,
        height:px(260),
        border:"1px solid black",
        id:contId
    })
    cont.css("position","relative");
    var background = $("<img />").css("width",px(w));
    cont.append(background)
    var playbackPos = 0;
    var imageProvider = location.hostname;
    return playback(cont,_.pluck(details.elements,"0"),function(i){
        _.defer(function(){
            var pos = details.elements[i];
            playbackPos = i;
            background.attr("src",sprintf("http://%s:9090/deified/slide/%s/small",imageProvider,pos[1]))
            $(document).trigger($.Event("onPlaybackTimeChanged",{
                position:pos
            }));
        });
    });
}
function renderSlideVisualElements(id){
    var allData = items[id];
    var i = function(index){
        return function(array){
            return array[index];
        }
    }
    var container = genDiv().css("position","relative");
    var bottomControls = $("<div />",{
        id:genId(),
        class:"legendControlContainer"
    });
    var mainLegendDisplay = $("<div />",{
        id:genId()
    }).css("border","1px solid black")
    var pauseWidth = 30;
    var leftControls = $("<div />").css({
        position:"absolute",
        left:0,
        top:0,
        height:"100%",
        width:pauseWidth
    })
    var colors = [{
        code:"D",
        name:"green"
    },{
        code:"C",
        name:"yellow"
    },{
        code:"P",
        name:"red"
    },{
        code:"NA",
        name:"black"
    }];
    colors.forEach(function(c){
        c.active = true;
    });
    var topControls = $("<div />",{
        position:"absolute",
        top:0,
        width:"100%"
    })
    colors.map(function(c,i){
        topControls.append($("<div />").css({
            width:px(pauseWidth),
            height:px(pauseWidth),
            "background-color":c.name,
            position:"absolute",
            "border-radius":px(4),
            top:0,
            left:px(30 + i * 40)
        }))
        topControls.append($("<input />",{
            type:"checkbox",
            checked:true,
            click:function(){
                c.active = !c.active;
                rerender();
            }
        }).css({
            position:"absolute",
            left:px(40 + i * 40)
        }))
    })
    topControls.append($("<img />",{
        src:"/images/icon/player_time-256.png",
        class:"showTimes"
    }).css({
        position:"absolute",
        top:0,
        left:px(30 + colors.length * 40),
        width:px(pauseWidth)
    }))
    container.append(leftControls)
    container.append(topControls)
    container.append(mainLegendDisplay)
    container.append(bottomControls);
    var underMouseContainer = $("<span />",{
        class:"userUnderMouse"
    })
    var userUnderMouseDisplay = $("<span />",{
        text:"authcate"
    }).click(function(){
        alert($(this).text());
    });
    var scrubHeight = 30

    underMouseContainer.append(userUnderMouseDisplay);
    underMouseContainer.append($("<img />",{
        src:"/images/icon/viewmag-256.png",
        height:px(scrubHeight)
    }).css("float","right"))
    var scrubContainer = $("<span />",{
        id:genId()
    })
    var pauseButton = $("<img />",{
        class:"playPauseButton",
        src:"/images/icon/player_play-256.png",
        width:px(pauseWidth)
    });
    var actualTime = $("<span />").css({
        position:"absolute",
        left:px(100)
    })
    var caption = $("<div />").addClass("caption").hide();
    bottomControls.append(scrubContainer)
    bottomControls.append(actualTime);
    bottomControls.append(pauseButton);
    bottomControls.append(caption);
    topControls.append(underMouseContainer)
    var seconds = function(milis){
        return Math.floor(milis/1000);
    }
    function rerender(){
        var data = [];
        colors.map(function(c){
            if(c.active){
                $.merge(data,allData.filter(function(d){
                    var s = standing(clean(d[6]));
                    console.log(s,c.code,s == c.code);
                    return s == c.code;
                }));
            }
        })
        var w = $(".replContainer").width()
        $("#"+mainLegendDisplay.attr("id")).html("");
        var chart = d3.select("#"+mainLegendDisplay.attr("id"))
                .append("svg:svg")
                .style("width",width)
                .style("height",width)
                .attr("class","playbackDecorators")

        if(data.length > 0){
            var width = w -30;
            var maxX = d3.max(_.pluck(data,"5"));
            var maxY = d3.max(_.pluck(data,"4"));
            var times = _.pluck(data,"0");
            var earliestTime = d3.min(times)
            var latestTime = d3.max(times)
            var span = seconds(latestTime - earliestTime);
            console.log("Seconds spanned by content",seconds(span))
            var timeScale = d3.scale.linear().domain([earliestTime,latestTime]).range([0,maxX])
            var scrubScale = d3.scale.linear().domain([earliestTime,latestTime]).range([0,width])
            var pointerScale  = d3.scale.linear().domain([0,width]).range([0,span])
            chart.attr("viewBox",sprintf("%s %s %s %s",0,0,maxX,maxY));
            chart.attr("width",width)
            chart.attr("height",width)

            console.log(sprintf("scrubScale [%s:%s] -> [%s:%s]",earliestTime,latestTime,0,width))

            var fillByStanding = function(d){
                switch (standing(clean(d[6]))){
                case "P" : return "red";
                case "C" : return "yellow";
                case "D" : return "green";
                default :{
                    console.log("Unknown",d[6],standing(d[6]))
                    return "black";
                }
                }
            }
            console.log("Set domain to",0,width);
            var scrub = d3.select("#"+scrubContainer.attr("id")).append("svg:svg")
                    .attr("class","scrubBar")
                    .style("height",scrubHeight)
                    .append("svg:g")

            var scrubField = scrub.append("svg:rect")
                    .attr("x",0)
                    .attr("y",0)
                    .attr("width",width)
                    .attr("height",scrubHeight)
                    .attr("fill","aliceblue")
                    .attr("stroke","blue")
                    .attr("strokeWidth",2)
                    .on("click",function(){
                        var mouseX = d3.mouse(this)[0]
                        var pos = pointerScale(mouseX);
                        console.log(sprintf("Scrubfield set head to [%s/%s] -> [%s:%s]",pos,echoDuration,mouseX,width))
                        audio.jPlayer("pause",pos);
                        audio.jPlayer("play")
                    })

            var projectedScrubBar = scrub.selectAll("line.projectedScrubPosition")
                    .data([scrubScale(earliestTime)])
                    .enter()
                    .append("svg:line")
                    .attr("class","projectedScrubPosition")
                    .style("stroke","red")
                    .attr("x1",_.identity)
                    .attr("y1",0)
                    .attr("x2",_.identity)
                    .attr("y2",scrubHeight);

            var actualScrubBar = scrub.selectAll("line.actualScrubPosition")
                    .data([scrubScale(earliestTime)])
                    .enter()
                    .append("svg:line")
                    .attr("class","actualScrubPosition")
                    .style("stroke","red")
                    .attr("x1",_.identity)
                    .attr("y1",0)
                    .attr("x2",_.identity)
                    .attr("y2",scrubHeight);

            chart.selectAll("rect")
                .data(data)
                .enter()
                .append("svg:rect")
                .attr("x",i(3))
                .attr("y",i(2))
                .attr("width",function(d){
                    return d[5] - d[3];
                })
                .attr("height",function(d){
                    return d[4] - d[2];
                })
                .attr("fill",fillByStanding)
                .attr("opacity",0.3)
                .on("click",function(d){
                    var offset = seconds(d[0] - earliestTime)
                    console.log("Canvas set head to",offset,"/",echoDuration,d[0],earliestTime)
                    audio.jPlayer("pause",offset)
                    audio.jPlayer("play")
                })
                .on("mouseover",function(d){
                    userUnderMouseDisplay.text(clean(d[6]))
                    $.get(sprintf("/static/captions/%s_%s.sami",d[6],d[1]),function(xml){
                        var syncs = $(xml).find("sync");
                        var time = scrubScale(parseInt(syncs.eq(1).attr("start")));
                        caption.text(syncs.eq(1).text()).css("left",time).show();
                    });
                    projectedScrubBar.data([scrubScale(d[0])])
                        .transition()
                        .attr("x1",_.identity)
                        .attr("x2",_.identity)
                })
                .on("mouseout",function(d){
                    caption.hide();
                })

            chart.attr("viewBox",sprintf("%s %s %s %s",0,0,maxX,maxY));
            chart.attr("width",width)
            chart.attr("height",width)

            var fillByStanding = function(d){
                switch (standing(clean(d[6]))){
                case "P" : return "red";
                case "C" : return "yellow";
                case "D" : return "green";
                default :{
                    console.log("Unknown",d[6],standing(d[6]))
                    return "black";
                }
                }
            }
            pointerScale.domain([0,width]);
            console.log("Set domain to",0,width);

            var pointElementsToTimeline = function(data){
                chart.selectAll("line")
                    .data(data)
                    .enter()
                    .append("svg:line")
                    .attr("x1",function(d){
                        return d[3] + (d[5] - d[3]) / 2;
                    })
                    .attr("y1",i(4))
                    .attr("x2",function(d){
                        return timeScale(d[0]);
                    })
                    .attr("stroke",fillByStanding)
                    .attr("stroke-width",2)
                    .attr("y2",maxX)
            }

            var clearElementsFromTimeline = function(data){
                chart.selectAll("line")
                    .remove()
            }
            var timesVisible = false;
            container.find(".showTimes").click(function(){
                timesVisible = !timesVisible;
                clearElementsFromTimeline();
                if(timesVisible){
                    pointElementsToTimeline(data)
                }
            });
            audio.bind($.jPlayer.event.timeupdate, function(event) {
                var head = event.jPlayer.status.currentTime * 1000;
                var time = scrubScale(earliestTime + head)
                actualTime.css("left",px(time)).text(new Date(earliestTime + head).toString("HH:mm:ss"))
                actualScrubBar.data([time])
                    .transition()
                    .attr("x1",_.identity)
                    .attr("x2",_.identity)
            })
            audio.bind($.jPlayer.event.loadedmetadata,function(event){
                echoDuration = event.jPlayer.status.duration;
            })
            audio.bind($.jPlayer.event.pause,doPause);
            audio.bind($.jPlayer.event.play,doPlay);
        }
    }
    var echoDuration = 0;
    var audio = $("#sampleAudioPlayer").clone()
    var audioId = genId();
    audio.find("#jquery-jplayer-1").attr("id",audioId);
    var doPause = function(){
        pauseButton.unbind("click").click(function(){
            audio.jPlayer("play");
        });
        pauseButton.attr("src","/images/icon/player_play-256.png")
    }
    var doPlay = function(){
        audio.jPlayer("pauseOthers")
        pauseButton.attr("src","/images/icon/agt_pause-queue-256.png")
        pauseButton.unbind("click").click(function(){
            audio.jPlayer("pause");
        });
    }
    audio.jPlayer({
        ready: function () {
            $(this).jPlayer("setMedia", {
                mp3: "/static/echo/Echo3602.mp3"
            });
        },
        swfPath: "/flash",
        supplied: "mp3"
    });
    container.append(audio)
    _.defer(rerender);
    return container;
}
function renderAuthor(_a){
    var a = clean(_a.id)
    var c = $("<span />",{class:'renderAuthor'})
    c.append($("<a />",{
        text:standing(a),
        href:"#",
        click:function(){
            trunk.push("author",items[a])
        }
    }))
    c.append($("<a />",{
        class:"reputation",
        href:"#",
        text:sprintf(" (%s) ",standings[a]),
        click:function(){
            vjq(standingBands(standings[a]),"Peers scoring",standings[a])
        }
    }))
    c.append($("<a href='#' class='inbox'>In</a>").click(function(){
        var cont = $("<div />")
        var relations = loadRelations({
            questions:_.filter(items,function(i,item){
                return types[item] == "question"
            })
        })
        var rel = relations[a]
        var lift = function(func,authors){
            for(var author in authors){
                var content = authors[author]
                for(var id in content){
                    cont.append(func(content[id]))
                }
            }
        }
        if(a in relations){
            $.map(rel,function(v,k){
                lift(renderers[k],v);
            })
        }
        var count = typeof rel == "undefined" ? 0 : rel.length
        vjq(cont,sprintf("Responses to %s",a))
    }));
    c.append($("<a href='#' class='inbox'>Out</a>").click(searcher(a)));
    return c;
}
function renderVote(_v){
    var context = parents[_v.id]

    var v = $.extend(_v,{
        parentId:context.parentId?context.parentId:context.id,
        discussion:{
            author:_v.author,
            content:"+1"
        }
    })
    return renderDiscussable("vote",v,[context])
}
function renderCommentDetail(id){
    var c = items[id]
    return $("<a />",{
        text:sprintf("%s comments",c.comments.length),
        href:"#",
        click:function(){
            trunk.pushAll("comment",_.pluck(c.comments,"id"),id)
        }
    })
}
function renderAnswerDetail(id){
    var a = items[id]
    return $("<a />",{
        text:sprintf("%s comments",a.comments.length),
        href:"#",
        click:function(){
            trunk.pushAll("answer",[id],id)
        }
    })
}
function renderQuestionDetail(id,count){
    var q = items[id]
    return $("<a />",{
        text:sprintf("%s answers",q.answers.length),
        href:"#",
        title:q.discussion.content,
        click:function(){
            if(count > 1){
                trunk.pushAll("question",[id],q.discussion.content)
            }
            else{
                trunk.pushAll("answer",_.pluck(q.answers,"id"),q.discussion.content)
            }
        }
    })
}
function renderTopic(id,count){
    var t = items[id]
    var qs = t.questions
    return $("<a />",{
        title:sprintf("[%s] %s questions",id,qs.length),
        href:"#",
        click:function(){
            if(count > 1){
                repl("topic",[id],t.id);
            }
            else{
                trunk.pushAll("question",_.pluck(qs,"id"),t.id);
            }
        },
        text:t.id
    })
}
function vjq(jq,label,container){
    var element;
    container = container || $("#output")
    if(label){
        var open = false
        var toggleLink = $("<a />",{
            href:"#",
            text:"[-]",
            click:function(){
                $(jq).slideToggle()
                toggleLink.text(open? "[-]" : "[+]")
                open = !open;
            }
        })
        element = $("<div />",{
            class:"labelledBorder"
        })
        element.append($("<span />",{
            class:"borderLabel",
            html:label
        }).after(toggleLink).after($("<br />")))
        element.append(jq)
    }
    else{
        element = jq
    }
    element.hide()
    container.prepend(element)
    container.find(".closeButton").remove()
    container.prepend(closeButton(container));
    element.slideDown();
}
function closeButton(container){
    return $("<input />",{
        type:"button",
        value:"X",
        class:"closeButton",
        click:function(){
            container.remove();
        }
    });
}
function prettyJson(q){
    return JSON.stringify(q,null,2);
}
