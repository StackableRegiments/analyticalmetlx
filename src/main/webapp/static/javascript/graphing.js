function renderMatrix(matrixObject){
    var m = matrixObject.m
    var authors = _.pluck(sortObject(matrixObject.authorSet,1),0)
    var t = $("<table />",{
        class:"tinyTable"
    })
    var headers = $("<tr />")
        headers.append("<th />")//Spacer
        authors.map(function(a){
            headers.append($("<th />",{
                text:a
            }))
        })
    t.append(headers)
    authors.map(function(a,i){
        var tr = $("<tr />")
        var th = $("<th />",{
            text:a
        })
        tr.append(th)
        authors.map(function(b,j){
            var td = $("<td />",{
                text:m[i][j]
            })
            tr.append(td)
        })
        t.append(tr)
    })
    return t
}
function academicBands(rootId,assign,labels,forceEvenDistribution){
    var root = items[rootId];
    var rel = loadRelations(root)
    var authors = {}
    $.each(rel,function(targetAuthor,value){
        authors[targetAuthor] = 0
        $.each(value,function(activity,activees){
            $.each(activees,function(sourceAuthor,post){
                authors[sourceAuthor] = 0
            })
        })
    })
    function x(a){
        return labels.indexOf(assign(a))
    }
    var y = (function(){
        var grades = {}
        for(var author in authors){
            var s = assign(author)
            setIfMissing(grades,s,[])
            grades[s].push(author)
        }
        $.each(grades,function(name,grade){
            grade.sort(function(a,b){
                var sa = standings[a]
                var sb = standings[b]
                return sa - sb
            })
            grades[name] = grade
        })
        return function(a){
            var grade = grades[assign(a)]
            var ss = grade.map(function(g){
                return standings[g]
            })
            var maxStanding = _.max(ss)
            if(forceEvenDistribution){
                return grade.indexOf(a) / grade.length
            }
            else{
                return standings[a] / maxStanding
            }
        }
    })()
    var authorNodes = {}
    var nodes = $.map(authors,function(_zero,author){
        var node = {x:x(author),y:y(author),name:author}
        authorNodes[author] = node
        return node
    })
    var links = []
    var content = {}
    var times = []
    $.each(rel,function(targetAuthor,value){
        $.each(value,function(activity,activees){
            $.each(activees,function(sourceAuthor,posts){
                $.each(posts,function(postId,post){
                    setIfMissing(content,assign(sourceAuthor),[])
                    times.push(post.time || post.creationDate);
                    if(post.discussion){
                        content[assign(sourceAuthor)].push(postId)
                    }
                })
                links.push({
                    source:authorNodes[sourceAuthor],
                    target:authorNodes[targetAuthor]
                })
            })
        })
    })
    times.sort(function(a,b){
        return a - b;
    })
    return {nodes:nodes,links:links,labels:labels,content:content,times:times}
}
function timedEvents(context){
    var all = []
    var format = function(s){
        return context.name? sprintf("%s %s",context.name,s) : s;
    }
    var push = function(code,item){
        all.push([format(code),
                  item.creationDate? item.creationDate : item.time]);
    }
    treeWalk(items[context.rootId],
             function(q){push("question",q)},
             function(a){push("answer",a)},
             function(c){push("comment",c)},
             function(v){push("vote",v)});
    return {
        data:all,
        labels:"question answer comment vote".split(" ").map(format)
    };
}
function timingData(contexts){
    var series = {}
    var counts = {}
    var all = []
    contexts.forEach(function(context){
        var events = timedEvents(context)
        events.data.forEach(function(e){
            all.push(e)
        })
        events.labels.forEach(function(label){
            setIfMissing(series,label,{
                name:label,
                data:[]
            })
            counts[label] = 0
        })
    })
    all.sort(function(a,b){
        return a[1] - b[1]
    })
    all.forEach(function(whatWhen){
        var what = whatWhen[0]
        counts[what]++;
        _.each(series,(function(serie,what){
            serie.data.push([whatWhen[1],counts[what]])
        }))
    })
    return _.values(series);
}
var traces = []
var masterTrace;
function liftTimingTrace(rootId){
    traces.push({
        name:rootId,
        rootId:rootId
    })
    drawMasterTrace(timingData(traces))
}
function drawMasterTrace(series){
    if(typeof masterTrace != "undefined"){
        masterTrace.destroy()
    }
    console.log("Master trace",series)
    masterTrace = new Highcharts.StockChart({
        chart: {
            renderTo: 'headerBar',
            borderWidth: 1,
            height:200
        },
        credits: {
            enabled: false
        },
        rangeSelector: {
            buttons: [{
                count: 10,
                type: 'second',
                text: '10s'
            }, {
                count: 1,
                type: 'minute',
                text: '1m'
            }, {
                type: 'all',
                text: 'All'
            }],
            inputEnabled: false,
            selected: 0,
            enabled:false
        },
        navigator: {
            height: 30
        },
        title: {
            text: 'Monash Stack',
            floating: true,
            style: {
                fontSize: '12px'
            }
        },
        xAxis: {
        },
        yAxis:{
            min:0
        },
        series:series
    });
}
function renderTimingTrace(containerId,rootId,width){
    var trace = new Highcharts.StockChart({
        chart:{
            renderTo:containerId.slice(1),
            borderWidth:1,
            height:width,
            width:width
        },
        credits:{
            enabled:false
        },
        rangeSelector:{
            enabled:false
        },
        navigator:{
            enabled:false
        },
        yAxis:{
            min:0
        },
        series:timingData([{
            prefix:"",
            rootId:rootId
        }])
    })
}
function drawTimeSeries(){
    var overview = new Highcharts.StockChart({
        chart: {
            renderTo: 'headerBar',
            borderWidth: 1,
            height:200
        },
        credits: {
            enabled: false
        },
        rangeSelector: {
            buttons: [{
                count: 10,
                type: 'second',
                text: '10s'
            }, {
                count: 1,
                type: 'minute',
                text: '1m'
            }, {
                type: 'all',
                text: 'All'
            }],
            inputEnabled: false,
            selected: 0,
            enabled:false
        },
        navigator: {
            height: 30
        },
        title: {
            text: 'Monash Stack Overview',
            floating: true,
            style: {
                fontSize: '12px'
            }
        },
        xAxis: {
        },
        yAxis:{
            min:0
        },
        series: [
            {
                name: 'Overall activity',
                data:[]
            },
            {
                name: 'Questions',
                data:[]
            },
            {
                name: 'Answers',
                data:[]
            },
            {
                name: 'Comments',
                data:[]
            },
            {
                name: 'Votes',
                data:[]
            }
        ]
    });
    function count(ts,type){
        return ts.filter(function(t){
            return t == type;
        }).length
    }
    $(document).on("previewOnQuestion",function(e,q){
        var typeSnapshot = _.values(types)
        function processVotes(post){
            post.votes.forEach(function(v){
                if(!(v.id in items)){
                    overview.series[4].addPoint([v.time,count(typeSnapshot,"vote")+1])
                }
            })
        }
        function processComment(c){
            processVotes(c)
            if(!(c.id in items)){
                overview.series[3].addPoint([c.creationDate,count(typeSnapshot,"comment")+1])
            }
            c.comments.forEach(processComment)
        }
        if(!(q.id in items)){
            overview.series[1].addPoint([q.creationDate,count(typeSnapshot,"question")+1])
        }
        processVotes(q)
        q.answers.forEach(function(a){
            if(!(a.id in items)){
                overview.series[2].addPoint([a.creationDate,count(typeSnapshot,"answer")+1])
            }
            processVotes(a)
            a.comments.forEach(processComment)
        })
    })
}
function clearChart(){
    $('#chart').html("")
    $(document).off("onQuestion")
}
function drawMeters(){
    clearChart()
    function overTime(context){
        return context+"_overTime";
    }
    var interval = 200;
    var series = {}
    function meter(label,context,filter){
        var blips = 0;
        var history = [
            0,0,0,0,0,
            0,0,0,0,0
        ];
        var meterElement = $("<meter /",{
            id:context,
            min:0,
            max:5,
            value:0,
            title:filter.toString
        })
        var timeGraph = $(sprintf("<div id='%s' class='timeSeries'/>",overTime(context)))
        $(document).on("onQuestion",function(e,q){
            if(filter(q)){
                blips++;
            }
        })
        series[label] = []
        var container = $("<div />",{
            text:label
        })
        container.prepend(meterElement)
        container.append(timeGraph)
        $('#chart').append(container)
        var graph = d3.select("#"+overTime(context)).append("svg:svg").attr("width", "100%").attr("height", "100%");
        var x = d3.scale.linear().domain([0, 9]).range([0, 320]);
        var y = d3.scale.linear().domain([0, 9]).range([50,0]);
        var line = d3.svg.line()
                .x(function(d,i) {
                    return x(i);
                })
                .y(function(d) {
                    return y(d);
                })
        graph.append("svg:path").attr("d", line(history));
        setInterval(function(){
            meterElement.val(blips);
            history = history.slice(1)
            history[history.length] = blips
            graph.selectAll("path").data(history).transition().duration(200).attr("d",line(history))
            blips = 0;
        },interval);
    }
    meter("Any publishing action within the class","allPublishing",function(){
        return true;
    })
    meter("Any publication not by Wendy","wendyPublishing",function(p){
        return p.discussion.author != "wmck"
    })
    meter("Any publication by Wendy","wendyPublishing",function(p){
        return p.discussion.author == "wmck"
    })
}
function staticChord(containerSelector,matrixRoot,width){
    var expandedMatrix = matrix(matrixRoot)
    var chord = d3.layout.chord()
            .matrix(expandedMatrix.m)
    groups = chord.groups()
    var w = width || 320,
        h = w,
        r0 = Math.min(w, h) * .41,
        r1 = r0 * 1.1;

    var fill = d3.scale.category20c();

    var svg = d3.select(containerSelector)
            .append("svg:svg")
            .attr("width", w)
            .attr("height", h)
            .append("svg:g")
            .attr("transform", "translate(" + w / 2 + "," + h / 2 + ")");

    var container = $(containerSelector).closest(".replContainer")
    svg.append("svg:g")
        .selectAll("path")
        .data(chord.groups)
        .enter().append("svg:path")
        .style("fill", function(d) { return fill(d.index); })
        .style("stroke", function(d) { return fill(d.index); })
        .attr("d", d3.svg.arc().innerRadius(r0).outerRadius(r1))
        .on("mouseover", fade(.1))
        .on("mouseout", fade(1))
        .on("click",function(){
            vjq(renderMatrix(expandedMatrix),"Expanded matrix "+containerSelector,container)
        })

    var ticks = svg.append("svg:g")
            .selectAll("g")
            .data(chord.groups)
            .enter().append("svg:g")
            .selectAll("g")
            .data(groupTicks)
            .enter().append("svg:g")
            .attr("transform", function(d) {
                return "rotate(" + (d.angle * 180 / Math.PI - 90) + ")"
                    + "translate(" + r1 + ",0)";
            });

    ticks.append("svg:line")
        .attr("x1", 1)
        .attr("y1", 0)
        .attr("x2", 5)
        .attr("y2", 0)
        .style("stroke", "#000");

    ticks.append("svg:text")
        .attr("x", 8)
        .attr("dy", ".35em")
        .attr("text-anchor", function(d) {
            return d.angle > Math.PI ? "end" : null;
        })
        .attr("transform", function(d) {
            return d.angle > Math.PI ? "rotate(180)translate(-16)" : null;
        })
        .attr("font-size","6pt")
        .text(function(d) { return d.label; });

    svg.append("svg:g")
        .attr("class", "chord")
        .selectAll("path")
        .data(chord.chords)
        .enter().append("svg:path")
        .style("fill", function(d) { return fill(d.target.index); })
        .attr("d", d3.svg.chord().radius(r0))
        .style("opacity", 1);

    /** Returns an array of tick angles and labels, given a group. */
    function groupTicks(d) {
        var width = d.endAngle - d.startAngle
        var offset = 0//width / 2
        return [{
            angle: d.endAngle - offset,
            label: d.value > 0 ? authorIndex[d.index] : ""
        }];
    }

    /** Returns an event handler for fading a given chord group. */
    function fade(opacity) {
        return function(g, i) {
            svg.selectAll("g.chord path")
                .filter(function(d) {
                    return d.source.index != i && d.target.index != i;
                })
                .transition()
                .style("opacity", opacity);
        };
    }
}
function narrate(e,i){
    var author = function(post){
        if(post){
            if("discussion" in post){
                return post.discussion.author;
            }
            else if("author" in post){
                return post.author;
            }
            else if("name" in post){
                return post.name;
            }
            else{
                return "";
            }
        }
        else{
            return "";
        }
    }
    var protagonist = author(e.item);
    var antagonist = author(e.parent);
    return $("<div />",{
        text:sprintf("%s: %s %s %s",i+1,protagonist,e.t,antagonist)
    });
}
function chord(containerSelector,matrixRoot,width){
    var progression = progressiveMatrix(matrixRoot)
    var matrices = progression.states;
    console.log("Chord buildup",progression)

    var i = 0;
    var visual;
    var render = function(matrix){
        if(visual){
            visual.clear();
        }
        visual =
            d3.chart.chord({
                container:containerSelector,
                fill:d3.scale.category20(),
                width:width,
                authorSet:progression.authorSet
            })
        visual.update(matrix);
    }
    var narration = genDiv().addClass("narration");
    $(containerSelector).after(narration);
    var timestamps = progression.sequence.map(function(state){
        return state.item.creationDate;
    });
    narration.before(playback($(containerSelector),timestamps,function(i){
        narration.prepend(narrate(progression.sequence[i],i));
        render(matrices[i]);
    }));
}
function playback(content,times,change){
    var max = times.length - 1;
    var doChange = function(i){
        progress.text(sprintf("%s / %s",i+1,times.length));
        clock.text(new Date(times[i]).toString("d/M/yy HH:mm:ss"));
        change(i);
    }
    var container = genDiv().css("padding",0);
    var interval;
    var isPlaying = false;
    var playing = function(){
        var next = parseInt(slider.val()) + 1;
        if(next <= max && isPlaying){
            slider.val(next);
            doChange(next);
        }
        else{
            clearInterval(interval);
        }
    }
    var controls = $("<div />",{
        class:"playbackControls"
    });
    var play = $("<a />",{
        click:function(){
            isPlaying = !isPlaying;
            if(isPlaying){
                interval = setInterval(playing,100);
            }
            else{
                if(parseInt(slider.val()) == max){
                    isPlaying = true;
                    slider.val(0);
                    clearInterval(interval);
                    interval = setInterval(playing,100);
                    doChange(0);
                }
            }
        }
    });
    play.html($("<img />",{
        src:"/images/key_play_pause.png"
    }));
    var progress = $("<span />");
    var slider = $("<input />",{
        type:"range",
        min:0,
        max:max,
        value:0,
        change:function(){
            console.log("change",this.value);
            doChange(parseInt(this.value));
        }
    });
    var clock = $("<div />",{
        text:"0:00.00",
        class:"playbackClock"
    });
    doChange(0);
    controls.append(play);
    controls.append(slider);
    controls.append(progress);
    container.append(clock);
    container.append(content);
    container.append(controls);
    _.defer(function(){
        slider.val(max);
        slider.trigger("change");
    });
    return container;
}
function link(forceCurve,forcePerpendicular) {
    var source = function(d) { return d.source; },
        target = function(d) { return d.target; },
        angle = function(d) { return d.angle; },
        startRadius = function(d) { return d.radius; },
        endRadius = startRadius,
        arcOffset = -Math.PI / 2;
    var end = 1
    function link(d, i) {
        var s = node(source, this, d, i),
            t = node(target, this, d, i),
            x;
        if (t.a < s.a) x = t, t = s, s = x;
        if (t.a - s.a > Math.PI) s.a += 2 * Math.PI;
        var a1 = s.a + (t.a - s.a) / 3,
            a2 = t.a - (t.a - s.a) / 3;
        var start = Math.cos(s.a) * s.r0 + "," + Math.sin(s.a) * s.r0
        var origin1 = Math.cos(a1) * s.r1 + "," + Math.sin(a1) * s.r1
        var control1 = Math.cos(a2) * t.r1 + "," + Math.sin(a2) * t.r1
        var dest1 = Math.cos(t.a) * t.r1 + "," + Math.sin(t.a) * t.r1
        var origin2 =  Math.cos(a2) * t.r0 + "," + Math.sin(a2) * t.r0
        var control2 = Math.cos(a1) * s.r0 + "," + Math.sin(a1) * s.r0

        if(forceCurve){
            var height = 100 * end
            end = end * -1
            var startX = Math.sin(a1) * s.r1
            var endX = Math.sin(t.a) * t.r1
            var cX = (endX - (endX - startX))
            control1 = height + "," +cX
        }
        if(forcePerpendicular){
            dest1 = start
        }
        return "M" + start
            + "C" + origin1
            + " " + control1
            + " " + dest1
        //Close the curve
            + "C" + origin2
            + " " + control2
            + " " + start
    }

    function node(method, thiz, d, i) {
        var node = method.call(thiz, d, i),
            a = +(typeof angle === "function" ? angle.call(thiz, node, i) : angle) + arcOffset,
            r0 = +(typeof startRadius === "function" ? startRadius.call(thiz, node, i) : startRadius),
            r1 = (startRadius === endRadius ? r0 : +(typeof endRadius === "function" ? endRadius.call(thiz, node, i) : endRadius));
        return {r0: r0, r1: r1, a: a};
    }

    link.source = function(_) {
        if (!arguments.length) return source;
        source = _;
        return link;
    };

    link.target = function(_) {
        if (!arguments.length) return target;
        target = _;
        return link;
    };

    link.angle = function(_) {
        if (!arguments.length) return angle;
        angle = _;
        return link;
    };

    link.radius = function(_) {
        if (!arguments.length) return startRadius;
        startRadius = endRadius = _;
        return link;
    };

    link.startRadius = function(_) {
        if (!arguments.length) return startRadius;
        startRadius = _;
        return link;
    };

    link.endRadius = function(_) {
        if (!arguments.length) return endRadius;
        endRadius = _;
        return link;
    };

    return link;
}
function degrees(radians) {
    return radians / Math.PI * 180 - 90;
}
function renderQueue(containerSelector,rootId,width){
    var bands = academicBands(rootId,function(){return 0},[0],false);
    console.log("queue",bands);
    var nodes = bands.nodes;
    var links = bands.links;
    var height = width,
        radius = d3.scale.linear().range([0, width]),
        color = d3.scale.category10().domain(d3.range(20));
    var angle = d3.scale.ordinal().domain(d3.range(4)).rangePoints([0, 2 * Math.PI]);
    var render = function(limit){
        $(containerSelector).find("svg").remove();
        var svg = d3.select(containerSelector).append("svg:svg")
                .attr("class","queue")
                .attr("width", width)
                .attr("height", height)
                .append("svg:g")
                .attr("transform", sprintf("translate(0 %s) rotate(90)",height / 2));
        svg.selectAll(".link")
            .data(links.slice(0,limit))
            .enter().append("svg:path")
            .attr("class", "link")
            .style("stroke", function(d) { return color(d.source.x); })
            .attr("d",link(true,true)
                  .angle(function(d){return radius(d.x)})
                  .radius(function(d){return radius(d.y)}))
            .attr("d", link(true,false)
                  .angle(function(d){return radius(d.x)})
                  .radius(function(d){return radius(d.y)}));

        var node = svg.selectAll(".node")
                .data(nodes.slice(0,limit))
                .enter().append("svg:g");
        node.append("svg:circle")
            .attr("class", function(d){
                return d.name == "wmck" ? "node visualizedAuthor" : "node";
            })
            .attr("cx", function(d) { return radius(d.y); })
            .attr("transform", function(d) { return "rotate(" + degrees(angle(d.x)) + ")"; })
            .attr("r", 5)
            .style("fill", function(d) {
                return color(d.x);
            });
        node.append("title")
            .text(function(d){
                return d.name;
            })

        var axes = svg.selectAll("group.axisContainer")
                .data(d3.range(1))
                .enter().append("svg:g");

        axes.append("svg:line")
            .attr("class", "axis")
            .attr("transform", function(d) { return "rotate(" + degrees(angle(d)) + ")"; })
            .attr("x1", radius.range()[0])
            .attr("x2", radius.range()[1]);
    }
    var narration = genDiv().addClass("narration");
    $(containerSelector).after(narration);
    narration.before(playback($(containerSelector),bands.times,function(i){
        var link = links[i];
        if(link){
            narration.prepend($("<div />",{
                text:sprintf("%s: %s @ %s",i+1,link.source.name,link.target.name)
            }));
        }
        render(i);
    }));
}
function heatMap(containerSelector,rootId,width){
    var container = $(containerSelector);
    var height = width;
    var grid = $("<div />").css({
        border:"1px solid blue",
        position:"relative",
        width:width,
        height:height
    });
    var px = function(i){
        return i+"px";
    }
    var data = progressiveMatrix(items[rootId]);
    var authorCount = data.authorSet.length;
    var cells = [];
    var cellWidth = width / authorCount;
    for(var y = 0; y < authorCount; y++){
        for(var x = 0; x < authorCount; x++){
            var offset = y * authorCount + x;
            cells[offset] = $("<div />",{
                class:"heatMapCell"
            }).css({
                left:px(cellWidth * x),
                top:px(cellWidth * y),
                width:px(cellWidth),
                height:px(cellWidth)
            }).appendTo(grid);
        }
    }
    var render = function(i){
        var color = {
            question:"black",
            answer:"blue",
            comment:"purple",
            vote:"lightblue"
        };
        var index = function(item){
            var author = item.discussion? item.discussion.author : item.author;
            var ind = data.authorSet.indexOf(clean(author));
            console.log(author,ind);
            return ind;
        }
        cells.forEach(function(cell){
            cell.css("background-color","transparent");
            cell.text("");
        });
        data.sequence.forEach(function(e,j){
            if(j <= i){
                cells[index(e.parent? e.parent : e.item) * authorCount + index(e.item)].css("background-color",color[e.t]);
            }
        });
    }
    console.log(data);
    var narration = genDiv().addClass("narration");
    container.append(narration);
    var times = data.sequence.map(function(s){
        return s.item.creationDate;
    });
    narration.before(playback(grid,times,function(i){
        render(i);
        narration.prepend(narrate(data.sequence[i],i));
    }));
}
function renderHive(containerSelector,rootId,width){
    var assign = standing;
    var labels = ["D","P","C"];
    var bands = academicBands(rootId,assign,labels,true);
    var nodes = bands.nodes;
    var links = bands.links;
    var width = width || 320,
        height = width,
        innerRadius = 20,
        outerRadius = width / 2;

    console.log("hiveBands",bands);
    var angle = d3.scale.ordinal().domain(d3.range(4)).rangePoints([0, 2 * Math.PI]),
        radius = d3.scale.linear().range([innerRadius, outerRadius]),
        color = d3.scale.category10().domain(d3.range(20));

    var render = function(limit){
        $(containerSelector).find("svg").remove();
        var svg = d3.select(containerSelector).append("svg:svg")
                .attr("width", width)
                .attr("height", height)
                .append("svg:g")
                .attr("transform", "translate(" + width / 2 + "," + ((height / 2) + 20) + ")");

        var axes = svg.selectAll("group.axisContainer")
                .data(d3.range(labels.length))
                .enter().append("svg:g")

        axes.append("svg:line")
            .attr("class", "axis")
            .attr("transform", function(d) { return "rotate(" + degrees(angle(d)) + ")"; })
            .attr("x1", radius.range()[0])
            .attr("x2", radius.range()[1])

        svg.selectAll(".link")
            .data(links.slice(0,limit))
            .enter().append("svg:path")
            .attr("class", "link")
            .attr("d", link(false,false)
                  .angle(function(d) { return angle(d.x); })
                  .radius(function(d) { return radius(d.y); }))
            .style("stroke", function(d) { return color(d.source.x); });
        svg.selectAll(".node")
            .data(nodes.slice(0,limit))
            .enter().append("svg:circle")
            .attr("class", function(d){
                return d.name == "wmck" ? "node visualizedAuthor" : "node";
            })
            .attr("transform", function(d) { return "rotate(" + degrees(angle(d.x)) + ")"; })
            .attr("cx", function(d) { return radius(d.y); })
            .attr("r", 5)
            .style("fill", function(d) {
                return color(d.x);
            });
    }
    var narration = genDiv().addClass("narration");
    $(containerSelector).after(narration);
    narration.before(playback($(containerSelector),bands.times,function(i){
        var link = links[i];
        if(link){
            narration.prepend($("<div />",{
                text:sprintf("%s: %s @ %s",i+1,link.source.name,link.target.name)
            }));
        }
        render(i);
    }));

}
