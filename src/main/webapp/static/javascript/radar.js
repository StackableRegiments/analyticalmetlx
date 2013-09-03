function countIncidences(text,keywords){
    console.log("Counting incidences of %s keywords, over text of length %s",keywords.length,text.length);
    var partials = [];
    var matchers = keywords.map(function(kw,i){
        var kww = kw.split(" ").map(clean);
        partials[i] = _.uniq(kww).map(function(w){
            return new RegExp(w,"g");
        });
        return new RegExp(kww.join(" "),"g");
    });
    var counts = keywords.map(function(){
        return {
	    score:0,
	    partials:[],
	    fulls:0
	};
    });
    matchers.forEach(function(matcher,i){
        var match = text.match(matcher);
        if(match){
            counts[i].fulls = match.length;
        }
    });
    partials.forEach(function(partial,i){
        partial.forEach(function(part){
            var match = text.match(part);
            if(match){
                counts[i].partials.push(match.length / partial.length);
            }
        });
    });
    counts.map(function(keyword){
	keyword.score = keyword.fulls;
	keyword.partials.forEach(function(partial){
	    keyword.score += partial;
	});
    });
    console.log("Keywords",keywords);
    console.log("Incidences",counts);
    return counts;
}
function itemText(id){
    var texts = [];
    var includeDiscussion = function(discussable){
        eachTerm(discussable.discussion.content,function(term){
            texts.push(term);
        });
    }
    treeWalk(items[id],
             includeDiscussion,
             includeDiscussion,
             includeDiscussion,
             function(){});
    return texts.join(" ");
}
function metlTextForSlide(slide){
    var slideTexts = [];
    if(slide in SlideThemes){
        var themes = SlideThemes[slide];
        _.keys(themes).map(function(word){
            _.values(themes[word]).map(function(count){
                slideTexts.push(word);
            })
        });
    }
    return slideTexts;
}
function metlTextForWeek(week){
    return _.flatten(_.keys(WeeksBySlide).filter(function(slide){
        return WeeksBySlide[slide] == week;
    }).map(function(slide){
        return metlTextForSlide(slide);
    })).join(" ");
}
function stackTextForWeek(week){
    return _.keys(WeeksByItem).filter(function(key){
        return WeeksByItem[key] == week;
    }).map(itemText).join(" ");
}
function notesTextForWeek(week){
    return lectureNotes[week] || "";
}
function lectureTextForWeek(week){
    return lectureText[week] || "";
}
function presentationTextForWeek(week){
    return presentationText[week] || "";
}
function weekText(week){
    return _.flatten([stackTextForWeek(week),metlTextForWeek(week),notesTextForWeek(week),lectureTextForWeek(week),presentationTextForWeek(week)]).join(" ");
}
var radar = function (containerId,root,width) {
    var week = inferWeek(root);
    var keywords = keywordObjectives.keywords[week].terms;
    console.log("Drawing radar for keywords",keywords);
    var data = countIncidences(weekText(week),keywords).map(function(incs){
	return incs.score;
    });
    var min = d3.min(data);
    var max = d3.max(data)+1;
    var count = data.length;
    var radius = d3.scale.linear().domain([0,max]).range([0,width/3]);//Leave some slack around the edges
    var fontSize = d3.scale.linear().domain([min,max]).range([40,9]);
    var labelOffset = d3.scale.linear().domain([min,max]).range([10,70])
    var radiusLength = radius(max);
    var vis = d3.select(containerId).append("svg:svg")
            .attr("width",width)
            .attr("height",width)
            .append("svg:g")
            .attr("transform",sprintf("translate(%s,%s)",width/2,width/2))

    var radialTicks = radius.ticks(5),
        i,
        circleAxes,
        lineAxes;

    vis.selectAll('.circle-ticks').remove();
    vis.selectAll('.line-ticks').remove();

    circleAxes = vis.selectAll('.circle-ticks')
        .data(radialTicks)
        .enter().append('svg:g')
        .attr("class", "circle-ticks");

    circleAxes.append("svg:circle")
        .attr("r", function (d, i) {
            return radius(d);
        })
        .attr("class", "circle")
        .style("stroke", "grey")
        .style("fill", "none");

    var groups = vis.selectAll('.series')
            .data([data]);
    groups.enter().append("svg:g")
        .attr('class', 'series')
        .attr("stroke","red")
        .attr("fill","red")
        .attr("opacity",0.3)

    var lines = groups.append('svg:path')
            .attr("class", "line")
            .attr("d", d3.svg.line.radial()
                  .radius(radius)
                  .angle(function (d, i) {
                      if (i === count) {
                          i = 0;
                      } //close the line
                      return (i / count) * 2 * Math.PI;
                  }))

    lineAxes = vis.selectAll('.line-ticks')
        .data(data)
        .enter().append('svg:g')
        .attr("transform", function (d, i) {
            return "rotate(" + ((i / data.length * 360) - 90) +
                ")translate(" + radius(max) + ")";
        })
        .attr("class", "line-ticks");

    lineAxes.append('svg:line')
        .attr("x2", -1 * radius(max))
        .style("stroke", "grey")
        .style("fill", "none")
        .style("opacity",0.4)

    lineAxes.append("svg:g")
        .attr("transform",function(d,i){
            return sprintf("translate(%s,0)",labelOffset(d));
        })
        .append('svg:text')
        .text(function(d,i){
            return keywords[i];
        })
        .attr("text-anchor","middle")
        .attr("font-size",fontSize)
        .attr("class","radarLabels")
        .attr("transform", function (d, i) {
            return (i / count) < 0.5 ? null : "rotate(180)";
        });
    var visualConfirmation = genId();
    items[visualConfirmation] = _.keys(WeeksBySlide).filter(function(slide){
        return WeeksBySlide[slide] == week;
    }).map(function(slide){
        return {
            slide:slide,
            type:"slide",
            term:"confirmation"
        }
    });
    repl("searchTerms",[visualConfirmation],sprintf("Visual confirmation of text recognition in week %s",week));
};