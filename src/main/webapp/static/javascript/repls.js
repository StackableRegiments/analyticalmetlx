function genDiv(){
    return $("<div />",{
        id:genId(),
        class:"labelledBorder"
    }).css("display","inline-block")
}
function parentConversation(slide){
    return Math.floor(slide / 1000) * 1000 + 400;
}
function genId(){
    return sprintf("gen_%s",new Date().getTime() - Math.floor(Math.random() * 10000));
}
function integer(input,fail){
    return parseInt(input) || (input && input.integer) || fail || false;
}
var linkedRepls = {};
function renderLinkedRepls(){
    var separateWords = _.values(linkedRepls).map(function(repl){
        return _.keys(repl.words());
    });
    var linkedWords = _.intersection.apply(_,separateWords);
    _.values(linkedRepls).forEach(function(repl){
        repl.highlight(linkedWords);
    });
}
function repl(dataType,seed,label){
    var id = new Date().getTime();
    var dataStack = seed.map(_.identity)
    var pushStack = function(){
        var continuation = dataStack.map(_.identity)
        if(continuation.length > 0){
            vjq(dataStack.map(function(datum){
                return renderers[dataType](datum,dataStack.length);
            }),$("<a />",{
                text:sprintf("%s %s",label || "",dataType),
                href:"#",
                click:function(){
                    dataStack = continuation
                    pushStack()
                    prompt()
                }
            }))
        }
    }
    var Words = {
        data:{
            narration:"Move the data in this stack back up to the top for easy clickable access.",
            invoke:pushStack
        },
        x:{
            narration:"Close the current stack",
            invoke:function(){
                container.find(".repl").off("keypress")
                container.remove()
            }
        },
        sum:{
            invoke:function(){
                var s = 0;
                var f = function(){
                    if(integer(peek())){
                        s += integer(pop(),0)
                        f()
                    }
                }
                f()
                push(s)
            }
        },
        replay:{
            narration:"Load all the data in the Lecture Q & A system from the server, listing the top level topics discovered.",
            invoke:function(){
                $("#replay").click()
            }
        },
        sortBy:{
            invoke:function(){
                var key = pop()
                dataStack = sort(function(a,b){
                    return a[key] < b[key]
                })
            }
        },
        interplay:{
            narration:"Show the interactions between members of the class, including the teacher.  Each interaction is a curved wedge, stretching from protagonist to antagonist.  The thick end of the wedge is at the antagonist, and the color represents the protagonist.  All wedges are the same thickness, regardless of how many interactions have gone between two users.",
            applies:["question","answer","topic"],
            limit:1,
            invoke:function(){
                var d = genDiv()
                container.prepend(d)
                chord("#"+d.attr("id"),items[peek()],container.width() - 30)//Padding magic number
            }
        },
        hive:{
            narration:"Show the interactions between members of the class.  They are sorted into low achievers, high achievers and medium achievers.  Each axis sorts the members of that category, lowest scores toward the center, highest scores towards the edge.  Interactions between members are colored according to the initiator of the interaction - answering a question, or voting on something, for instance.  The teacher is colored distinctly from the other high achievers, and lives on that axis.",
            applies:["question","answer","topic"],
            limit:1,
            invoke:function(){
                var d = genDiv();
                container.prepend(d);
                renderHive("#"+d.attr("id"),peek(),container.width() - 30);//Padding magic number
            }
        },
        queue:{
            narration:"Show the interactions between members of the class.  They are all listed on the same axis, sorted lowest scorers to the left, highest scorers to the right.  The teacher is colored distinctly from the rest of the class.  The line denoting interaction between members curves on the side of the initiator, and will be colored red while it is the current action.  There is no other significance to the coloring of the links.",
            applies:["question","answer","topic"],
            limit:1,
            invoke:function(){
                var d = genDiv()
                container.prepend(d)
                renderQueue("#"+d.attr("id"),peek(),container.width() - 30)//Padding magic number
            }
        },
        standings:{
            narration:"Sort the answers to this question by the author's projected grade, and create a new stack for each category.  Each new stack is a copy of this question, but contains only the subset of the answers that was authored by the relevant grade category.  If applied to a topic, it creates new copies of each question in that topic in just the same way.",
            applies:["question","topic"],
            limit:1,
            invoke:function(){
                var results = [];
                switch(dataType){
                case "question":
                    var q = items[peek()]
                    $.each(_.groupBy(q.answers,function(item){
                        return standing(clean(item.discussion.author))
                    }),function(group,xs){
                        var subset = _.clone(q)
                        subset.id = sprintf("%s@%s",group,q.id)
                        subset.answers = xs
                        items[subset.id] = subset
                        var result = repl(dataType,[subset.id],sprintf("%s student",group));
                        results.push(result);
                        result.setLinked(true);
                    })
                    break;
                case "topic":
                    var t = items[peek()]
                    availableStandings.forEach(function(grade){
                        var subT = _.clone(t);
                        subT.id = sprintf("%s@%s",grade,t.id)
                        subT.questions = t.questions.map(function(q){
                            var subQ = _.clone(q);
                            subQ.id = sprintf("%s@%s",grade,q.id);
                            items[subQ.id] = subQ;
                            subQ.answers = _.filter(q.answers,function(a){
                                return standing(clean(a.discussion.author)) == grade;
                            });
                            return subQ;
                        });
                        items[subT.id] = subT
                        var result = repl(dataType,[subT.id],sprintf("%s students answering within %s",grade,t.id));
                        result.setLinked(true);
                        results.push(result);
                    })
                    break;
                }
                return results;
            }
        },
        expand:{
            narration:"List all the data in the current stack, in pretty printed JSON format.",
            invoke:function(){
                container.prepend($("<pre />",{
                    text:prettyJson(dataStack.map(function(id){
                        return items[id]
                    }))
                }))
            }
        },
        answers:{
            limit:1,
            invoke:function(){
                trunk.pushAll("answer",_.pluck(items[peek()].answers,"id"),peek())
            }
        },
        questions:{
            narration:"Create a new stack listing the individual questions asked within this topic",
            limit:1,
            invoke:function(){
                var t = items[peek()]
                trunk.pushAll("question",_.pluck(t.questions,"id"),t.id)
            }
        },
        timing:{
            narration:"Show the frequency of questions, answers, comments and votes over time within this data set",
            applies:["question","answer"],
            limit:1,
            invoke:function(){
                var d = genDiv()
                container.prepend(d)
                renderTimingTrace("#"+d.attr("id"),peek(),container.width() - 30)//Padding magic number
            }
        },
        compare:{
            narration:"Add the timing of this dataset to the aggregate timing comparison",
            applies:["question","answer"],
            limit:1,
            invoke:function(){
                liftTimingTrace(peek())
            }
        },
        keywords:{
            narration:"Show which of the teaching objectives for the relevant teaching event are in common usage in this cohort.",
            applies:["slide"],
            limit:1,
            invoke:function(){
                var d = genDiv();
                container.prepend(d);
                container.width(container.width() * 2);
                d.css("width",container.width() - 30);
                radar("#"+d.attr("id"),peek(),container.width() - 30);
                _.defer(function(){
                    container.append($("<div />",{
                        text:weekText(inferWeek(peek()))
                    }));
                });
            }
        },
        tron:{
            narration:"Simulate the social interactions in relation to the teaching objectives for this subject's week.",
            applies:["slide"],
            limit:1,
            invoke:function(){
                var d = genDiv();
                container.prepend(d);
                d.css("width",container.width() - 30);
                simulateHistory("#"+d.attr("id"),peek(),container.width() - 30);
            }
        },
        objectives:{
            narration:"List the registered teaching objectives for the context to this workspace.",
            applies:["question","answer","topic","conversation","slide"],
            limit:1,
            invoke:function(){
                var context = peek();
                repl("objectives",[context],sprintf("Teaching objectives for Week %s",inferWeek(context)));
            }
        },
        legend:{
            narration:"Show the authors, timing and context of the elements of the currently visible content.",
            applies:["slide"],
            limit:1,
            invoke:function(){
                var slide = peek();
                var details = items[parentConversation(slide)];
                var positionable = (function(){
                    var candidates = details.elements.filter(function(t){
                        return t.length > 3 && t[4] != t[2] && t[5] != t[3];//Zero dimension
                    })
                    return function(slide){
                        return candidates.filter(function(t){
                            return t[1] == slide;
                        });
                    }
                })();
                var id = sprintf("legend_%s",slide)
                items[id] = positionable(slide);
                repl("slideVisualElements",[id],sprintf("Content breakdown for page %s",slide));
            }
        },
        prev:{
            narration:"Show the previous page",
            applies:["slide"],
            limit:1,
            invoke:function(){
                var context = pop();
                var details = items[parentConversation(context)];
                var current = details.slides.indexOf(context);
                var slide = current > 0 ? details.slides[current - 1] : details.slides[details.slides.length - 1];
                container.find("img").remove();
                push(dataType,slide);
            }
        },
        next:{
            narration:"Show the next page",
            applies:["slide"],
            limit:1,
            invoke:function(){
                var context = pop();
                var details = items[parentConversation(context)];
                var current = details.slides.indexOf(context);
                var slide = current < details.slides.length - 1 ? details.slides[current + 1] : details.slides[0];
                container.find("img").remove();
                push(dataType,slide);
            }
        },
        themes:{
            narration:"Show all the important words and ideas used within this dataset.  Individual words are colored according to the grade of the users who used them, and collect the grades of the users who voted for them to their right.",
            applies:["question","answer","topic","conversation"],
            limit:1,
            invoke:function(){
                var cloudContainer = function(){
                    var c = genDiv();
                    c.addClass("cloudContainer");
                    c.css("width",container.width() - 30);
                    container.prepend(c);
                    return "#"+c.attr("id");
                }
                renderDifferentiatedCloud(
                    cloudContainer(),
                    peek());
            }
        },
        heatmap:{
            narration:"Arranges all members of this teaching event such that the highest are in the top left of a visual square.  Displays interaction between users according to their relative ranks.",
            limit:1,
            applies:["question","topic"],
            invoke:function(){
                var d = genDiv();
                container.prepend(d);
                d.css("width",container.width() - 30);
                heatMap("#"+d.attr("id"),peek(),container.width() - 30);
            }
        },
        relations:{
            narration:"Show the associations between users.  WIP.",
            applies:["question","topic"],
            limit:1,
            invoke:function(){
                var d = genDiv()
                container.prepend(d)
                d.css("width",container.width() - 30)
                springRelations("#"+d.attr("id"),peek(),container.width() - 30)
            }
        }
    }
    var container = $("<div />",{
        id:sprintf("repl_%s",new Date().getTime()),
        class:"replContainer labelledBorder"
    })
    $("#repls").prepend(container)
    var vjq = function(els,label){
        var cont = $("<div />",{
            class:"stackFrame"
        })
        els.forEach(function(el){
            cont.append(el)
        })
        window.vjq(cont,false,container)
    }
    var peek = function(){
        return dataStack[dataStack.length - 1]
    }
    var push = function(type,data){
        dataStack.push(data)
        pushStack()
    }
    var pop = function(){
        return dataStack.pop()
    }
    var processKey = function(e){
        if(e.which == 13){//Enter
            readInput()
            prompt()
        }
    }
    var highlights = [];
    var isLinked = false;
    var prompt = function(){
        container.find(".repl").off("keypress").remove()
        container.find(".availableWord").remove()
        container.find(".stackDepth").remove()
        var cont = $("<div />",{
            class:"stackDepth"
        });
        cont.append($("<div />",{
            text:label == dataType? "" : label
        }));
        if(dataStack.length > 1){
            cont.append($("<div />",{
                text:sprintf("%s %ss",dataStack.length,dataType)
            }));
        }
        var input = $("<input />",{
            class:"repl"
        }).on("keypress",processKey);
        var toggleLink = $("<input />",{
            type:"checkbox",
            click:function(){
                setLinked(!isLinked);
            }
        });
        if(isLinked){
            toggleLink.prop("checked",true);
        }
        cont.append(toggleLink);
        cont.append(input)
        _.each(availableWords(),function(word,label){
            if(!word.limit || word.limit == dataStack.length){
                cont.append($("<input />",{
                    type:"button",
                    value:label,
                    class:sprintf("availableWord%s",highlights.indexOf(label) > -1? " linkedWord" : ""),
                    title:word.narration || "No narration yet defined",
                    click:function(){
                        if(id in linkedRepls && highlights.indexOf(label) >= 0){
                            _.values(linkedRepls).forEach(function(repl){
                                repl.words()[label].invoke();
                                repl.prompt();
                            });
                        }
                        else{
                            word.invoke()
                            prompt()
                        }
                    }
                }));
            }
        });
        vjq([cont])
        input.focus()
    }
    var availableWords = function(){
        var available = {};
        $.each(Words,function(k,v){
            if(v.applies && _.any([dataType,"*"],function(a){
                return v.applies.indexOf(a) >= 0;
            })){
                available[k] = v;
            }
        });
        return available;
    }
    var readInput = function(){
        var words = container.find(".repl").val()
        if(words){
            words.split(" ").map(function(word){
                if(word.length > 0){
                    dataStack.push(word)
                    processDataStack()
                }
            });
        }
    }
    var filterPs = function(type){
        var query = peek()
        var field = []
        var t = types[query.id]
        switch(t){
        case "author":
            var n = clean(query.id)
            field = _.values(items).filter(function(item){
                if(item.discussion) return clean(item.discussion.author) == n
                if(item.author) return clean(item.author) == n
                return false;
            })
            break;
        case "topic":
            field = _.values(items).filter(function(item){
                if(item.teachingEvent) return item.teachingEvent == query.id
                return false;
            })
            break;
        default:
            alert("Unknown context "+t)
            break;
        }
        var result = field
        if(type){
            result = _.filter(field,function(post){
                return types[post.id] == type
            })
        }
        repl(result,sprintf("%s in %s",type,query.id),query.id)
    }

    var processDataStack = function(){
        var w = pop()
        if(w in Words){
            if(id in linkedRepls && highlights.indexOf(w) >= 0){
                _.values(linkedRepls).forEach(function(repl){
                    repl.words()[w].invoke();
                    repl.prompt();
                });
            }
            else{
                Words[w].invoke()
            }
        }
        else{
            push(w)
        }
    }

    var setLinked = function(newLinkedStatus){
        isLinked = newLinkedStatus;
        if(isLinked){
            linkedRepls[id] = replInterface;
        }
        else{
            delete linkedRepls[id];
            prompt();
            highlights = [];
        }
        renderLinkedRepls();
        prompt();
    }
    pushStack()
    prompt()
    var replInterface = {
        push:push,
        pushAll:function(type,xs,label){
            if(type == dataType){
                xs.forEach(function(x){
                    dataStack.push(x)
                })
                pushStack()
                prompt()
            }
            else{
                repl(type,xs,label)
            }
        },
        prepend:function(content){
            container.prepend(genDiv().append(content));
            prompt();
        },
        highlight:function(highlightedWords){
            highlights = highlightedWords;
            prompt();
        },
        words:availableWords,
        prompt:prompt,
        setLinked:setLinked
    }
    return replInterface;
}
