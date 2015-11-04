var PSY1011Weeks = [
  Date.UTC(2012,1,20),
  Date.UTC(2012,1,27),
  Date.UTC(2012,2,5),
  Date.UTC(2012,2,12),
  Date.UTC(2012,2,19),
  Date.UTC(2012,2,26),
  Date.UTC(2012,3,2),
  Date.UTC(2012,3,16),
  Date.UTC(2012,3,23),
  Date.UTC(2012,3,30),
  Date.UTC(2012,4,7),
  Date.UTC(2012,4,14),
  Date.UTC(2012,4,21)
]
var teachingWeeks = (function(){
  var ws = [];
  var w = Date.UTC(2015,5,6);
  for(var i = 0; i < 24; i++){
    w = w + 7;
    ws.push(w);
  }
  ws.reverse();
  return ws;
})();
function px(i){
  return sprintf("%spx",i);
}
function hours(i){
  return 1000 * 60 * 60 * i;
}
function offset(i){
  return i >= 6 ? hours(1) : 0;
}
var allLectures = teachingWeeks.map(function(x,i){
  var lect = function(unoffsetStart){
    var start = x + unoffsetStart + offset(i);
    return {
      start:start,
      end:start+hours(2)
    }
  }
  return [
    i == 0 ? lect(hours(48)) : lect(hours(52)),
    lect(hours(69)),
    lect(hours(98))
  ];
});

function renderBlendedTimeline(){
  var containerId = "masterTimeline";
  var container = $("#"+containerId);
  var min = teachingWeeks[0];
  var max = teachingWeeks[12] + hours(24);
  var focussedWeek = 2;
  var academicPhases = function(data,key){
    return allLectures.map(function(lectureSet){
      var between = function(low,high){
        return data.filter(function(c){
          if(key){
            c = c[key];
          }
          return c > low && c < high;
        });
      }
      var res = {
        times:lectureSet,
        pre:[],
        during:[],
        post:[],
        data:data
      }
      lectureSet.map(function(lecture){
        between(lecture.start - hours(12), lecture.start).map(function(x){
          res.pre.push(x);
        });
        between(lecture.start, lecture.end).map(function(x){
          res.during.push(x);
        });
        between(lecture.end, lecture.end + hours(12)).map(function(x){
          res.post.push(x);
        });
      });
      return res;
    });
  }
  lectureStack = academicPhases(_.values(items),"creationDate");
  lectureMeTL = allLectures.map(function(l){
    return {
      pre:[],
      during:[],
      post:[],
      conversations:{}
    }
  });
  lectureEcho = allLectures.map(function(l,_i){
    var i = _i;
    return {
      url:sprintf("/static/echo/Echo360%s.mp3",i),
      exists:true
    }
  })
  lectureNotes = {};
  allLectures.map(function(l,i){
    if(i == 2){
      $.get(sprintf("/static/notes/lecture%s.txt",i),function(txt){
        lectureNotes[i] = txt;
      });
    }
  });
  lectureText = {};
  allLectures.map(function(l,i){
    $.get(sprintf("/static/captions/lecture%s.cap",i),function(xml){
      var matches = xml.split("\n").map(function(line){
        return line.match(/ENUSCC>(.*)/);
      });
      lectureText[i] = _.compact(matches).map(function(m){
        return m[1];
      }).join(" ");
    });
  });
  presentationText = {};
  allLectures.map(function(l,i){
    $.get(sprintf("/static/powerpoint/lecture%s.txt",i),function(text){
      presentationText[i] = text;
    });
  });
  /*
   lectureEcho.map(function(possibleEcho){
   $.exists(possibleEcho.url,function(exists){
   console.log("Echo exists?",possibleEcho.url,exists)
   possibleEcho.exists = exists
   })
   })
   */
  var lowResYMax = function(){
    var ys = [];
    var push = function(x){
      ys.push(x.length);
    }
    var contexts = [lectureStack,lectureMeTL]
    contexts.map(function(weeks){
      weeks.map(function(week){
        [week.pre,week.during,week.post].map(push);
      });
    });
    return d3.max(ys);
  }
  var h = 250;
  var barH = h - 60;
  var interval = 10000;//Milis to aggregate events within
  var stackIncidents = _.groupBy(_.pluck(_.values(items),"creationDate").filter(function(t){
    return t > 0;
  }),function(t){
    return Math.floor(t / interval) * interval;
  });
  var stackTimes = [];
  _.keys(stackIncidents).sort().map(function(t){
    t = parseInt(t);
    stackTimes.push([t - interval / 2,0]);
    stackTimes.push([t,stackIncidents[t].length]);
    stackTimes.push([t + interval / 2,0]);
  });

  var loc = location;
  var dataProvider = loc.hostname;
  var logProgress = function(message){
    $("#timelineLog").text(message);
  }
  var start = allLectures[0][0].start - hours(24);
  var end = allLectures[allLectures.length-1][2].end;

  var allMeTLUsers = {};
  var bootstrap = function(json) {
    json.map(function(conversationDetails){
      logProgress(sprintf("Indexing %s",conversationDetails.title));
      conversationDetails.elements.map(function(element){
        allLectures.map(function(ls,weekIndex){
          ls.map(function(l){
            if(element[0] >= l.start && element[0] <= l.end){
              setIfMissing(WeeksBySlide,element[1],weekIndex);
              allMeTLUsers[element[element.length-1]] = 1;
            }
          })
        })
      })
      console.log("AllMeTLUsers",allMeTLUsers);
      window.metlUsers = allMeTLUsers;
      items[conversationDetails.jid] = conversationDetails;
      indexConversation(conversationDetails);
      var phases = academicPhases(conversationDetails.elements,"0");
      var groupedTimes = _.groupBy(conversationDetails.elements,function(t){
        return Math.floor(t[0] / interval) * interval;
      });
      var ts = [];
      _.keys(groupedTimes).sort().map(function(t){
        t = parseInt(t);
        ts.push([t - interval / 2,0]);
        ts.push([t,groupedTimes[t].length]);
        ts.push([t + interval / 2,0]);
      });
      phases.map(function(w,i){
        $.merge(lectureMeTL[i].pre,w.pre);
        $.merge(lectureMeTL[i].during,w.during);
        $.merge(lectureMeTL[i].post,w.post);
        if(w.pre.length + w.during.length + w.post.length > 0){
          lectureMeTL[i].conversations[conversationDetails.jid] = {
            title:conversationDetails.title,
            jid:conversationDetails.jid,
            data:ts
          };
        }
      });
    });
    render();
    logProgress("Data load complete");
  }
  var weekTooltip = function(week){
    var classTimes = "";
    allLectures[week].map(function(lecture){
      classTimes += sprintf("%s - %s\n",new Date(lecture.start).toString("HH:mm"),new Date(lecture.end).toString("HH:mm dd/MM yyyy"));
    });
    return sprintf("Week %s:\n%s",week,classTimes);
  }
  var highlightedWeeks = [];
  var render = function(){
    container.html("");
    var labelHeight = 30;
    var w = container.width(),
        cellHeight = h - labelHeight * 2,
        focussedWidth = w / 4,
        cellWidth = (w - focussedWidth)/ teachingWeeks.length - 1,
        picWidth = cellWidth / 4,
        p = 30,
        colX = d3.scale.linear().domain([0,2]).range([0,cellWidth * 2 / 3]),
        x = d3.scale.linear().domain([min,max]).range([0, w]),
        y = d3.scale.linear().domain([0,lowResYMax()]).range([0,barH]);

    var weekLabel = function(week){
      return $("<div />").css("text-align","center").append($("<div />",{
        text:week == 0 ? "O Week" : sprintf("Week %s",week),
        click:function(){
          focussedWeek = week;
          render()
        },
        title:weekTooltip(week),
        class:"weekLabel"
      }));
    }
    var lowResId = function(week){
      return sprintf("lowRes_%s",week);
    }
    var mediaSummary = function(result,week){
      result.append(weekLabel(week));
      if(_.values(lectureMeTL[week].conversations).length > 0){
        result.append($("<img />",{
          src:"/images/Collaborate.png",
          width:px(picWidth),
          class:"mediaLink",
          title:"A MeTL conversation during this week's lecture is available.  Click the week label for more detail."
        }));
      }
      if(lectureEcho[week].exists){
        result.append($("<img />",{
          src:"/images/icon/agt_mp3-256.png",
          width:px(picWidth),
          class:"mediaLink",
          title:"An audio capture of this week's lecture is available.  Click the week label for more detail."
        }))
      }
      var stacks = lectureStack[week];
      if(stacks.pre.length+stacks.during.length+stacks.post.length > 0){
        result.append($("<img />",{
          src:"/images/icon/db-256.png",
          width:px(picWidth),
          class:"mediaLink",
          title:"A Lecture Q & A from this week's lecture is available.  Click the week label for more detail."
        }))
      }
      if(week in lectureNotes){
        result.append($("<img />",{
          src:"/images/icon/contents-256.png",
          width:px(picWidth),
          class:"mediaLink",
          title:"A Google Document in which you took notes from this lecture is available."
        }));
      }
      if(highlightedWeeks.indexOf(week) >= 0){
        result.css("background-color","yellow");
      }
    }
    var lowResContainer = function(week){
      var phaseSummary = $("<div />",{
        id:lowResId(week)
      });

      var result = $("<div />").css("height",px(cellHeight)).addClass("lowResWeekData")
            .append(phaseSummary)
      mediaSummary(result,week);
      return result;
    }
    var lowResChart = function(week){
      var chart = d3.select(sprintf("#%s",lowResId(week))).append("svg:svg");
      var offsetPos = $(chart[0]).position();
      var data = [
        lectureStack[week].pre.length + lectureMeTL[week].pre.length,
        lectureStack[week].during.length + lectureMeTL[week].during.length,
        lectureStack[week].post.length + lectureMeTL[week].post.length
      ];
      chart.selectAll("rect.lecture")
        .data(data)
        .enter()
        .append("svg:rect")
        .attr("class","lecture")
        .attr("width",cellWidth / 4)
        .attr("height",y)
        .attr("x",function(d,i){
          return colX(i)
        })
        .attr("y",function(d){
          return barH - y(d);
        })
        .attr("fill","lightgreen")
    }

    var highRes = function(week){
      var cont = $("<div />",{
        id:genId()
      }).css("height",px(cellHeight));
      var createChart = function(){
        var xs = [];
        var ys = [];
        var series = [];
        var data = _.values(lectureMeTL[week].conversations);
        if(data.length == 0){
          return;
        };
        _.map(data,function(serie){
          var is = serie.data.map(function(xy){
            return [parseInt(xy[0]),parseInt(xy[1])];
          });
          series.push(is);
          is.map(function(xy){
            xs.push(xy[0]);
            ys.push(xy[1]);
          });
        });
        var minX = allLectures[focussedWeek][0].start - hours(4);
        var maxX = allLectures[focussedWeek][2].end + hours(4);
        var minY = d3.min(ys);
        var maxY = d3.max(ys);
        var color = d3.scale.category10();
        var x = d3.scale.linear().domain([minX,maxX]).range([0,focussedWidth]);
        var y = d3.scale.linear().domain([minY,maxY]).range([cellHeight,0]);
        var chart = d3.select("#"+cont.attr("id"))
              .append("svg:svg")
              .append("svg:g");

        chart.selectAll("rect.inClass")
          .data(allLectures[week])
          .enter()
          .append("svg:rect")
          .attr("class","inClass")
          .attr("width",function(d){
            return x(d.end) - x(d.start);
          })
          .attr("height",cellHeight)
          .attr("fill","lightgreen")
          .attr("opacity",0.4)
          .attr("x",function(d){
            return x(d.start);
          })
          .attr("y",0)

        chart.selectAll("path.metl")
          .data(series)
          .enter()
          .append("svg:g")
          .append("svg:path")
          .attr("stroke",function(d,i){
            return color(i)
          })
          .attr("class","metl")
          .attr("d",d3.svg.line()
                .x(function(d){
                  return x(d[0]);
                })
                .y(function(d){
                  return y(d[1]);
                }));

        var metlWeek = _.values(lectureMeTL[week].conversations);

        var localStackTimes = stackTimes.filter(function(t){
          return t[0] > minX && t[0] < maxX;
        });

        if(localStackTimes.length > 0){
          var maxStackY = d3.max(_.pluck(localStackTimes,"1"));
          var stackY = d3.scale.linear().domain([0,maxStackY]).range([cellHeight,0]);

          chart.selectAll("path.stack")
            .data([localStackTimes])
            .enter()
            .append("svg:g")
            .append("svg:path")
            .attr("stroke",function(d,i){
              return color(metlWeek.length);
            })
            .attr("class","stack")
            .attr("d",d3.svg.line()
                  .x(function(d){
                    return x(d[0]);
                  })
                  .y(function(d){
                    return stackY(d[1]);
                  }));
          metlWeek.push({
            data:localStackTimes,
            title:"Lecture Q & A",
            image:"images/icon/db-256.png"
          });
        }

        d3.select("#"+cont.attr("id")).append("div").attr("class","legendContainer").selectAll("div")
          .data(metlWeek)
          .enter()
          .append("img")
          .attr("src",function(d){
            return "image" in d ? d.image : "images/Collaborate.png"
          })
          .style("width",px(picWidth))
          .attr("class","mediaLink")
          .attr("title",function(d){
            var phases = academicPhases(d.data,"0")[week];
            var total = phases.pre.length + phases.during.length + phases.post.length;
            var percent = function(xs){
              return sprintf("%s%%",Math.floor(xs.length * 100 / total));
            }
            var perConversationListing = "";
            perConversationListing += sprintf("%s prework",percent(phases.pre));
            perConversationListing += "\n";
            perConversationListing += sprintf("%s inclass",percent(phases.during));
            perConversationListing += "\n";
            perConversationListing += sprintf("%s postwork",percent(phases.post));
            return sprintf("Week %s\n%s:\n%s",week,d.title,perConversationListing);
          })
          .style("border",function(d,i){
            return sprintf("4px solid %s",color(i));
          })
          .on("mouseover",function(d,i){
            var offset = d3.mouse(this);
            var ex = d3.event.layerX + picWidth / 2 - offset[0];
            var ey = 15;
            var e = [ex,ey];
            chart.selectAll("polygon.conversation")
              .data([[d.data[0],d.data[d.data.length-1]]])
              .enter()
              .append("svg:polygon")
              .attr("class","conversation")
              .style("fill","yellow")
              .style("opacity",0.6)
              .attr("points",function(d){
                return sprintf("%s,%s %s,%s %s,%s",e[0],e[1],x(d[0][0]),cellHeight,x(d[1][0]),cellHeight)
              })
            chart.selectAll("line.conversation")
              .data(d.data)
              .enter()
              .append("svg:line")
              .attr("class","conversation")
              .style("stroke","red")
              .style("stroke-width",0.1)
              .style("opacity",0.6)
              .attr("x1",ex)
              .attr("y1",ey)
              .attr("x2",function(d){
                return x(d[0]);
              })
              .attr("y2",cellHeight)
          })
          .on("mouseout",function(d,i){
            chart.selectAll("polygon.conversation").remove()
            chart.selectAll("line.conversation").remove()
          })
          .on("click",function(d,i){
            repl("slide",[items[d.jid].slides[0]],d.title);
          })
        var scrub = chart.selectAll("line.scrubPosition")
              .data([x(min)])
              .enter()
              .append("svg:line")
              .attr("class","scrubPosition")
              .style("stroke","red")
              .style("stroke-width",1)
              .attr("x1",_.identity)
              .attr("y1",0)
              .attr("x2",_.identity)
              .attr("y2",cellHeight);
        $(document).on("onPlaybackTimeChanged",function(e){
          scrub.data([x(e.position[0])])
            .transition()
            .attr("x1",_.identity)
            .attr("x2",_.identity)

        });
      }
      _.defer(createChart);
      _.defer(function(){
        mediaSummary(cont,week)
        container.append($("<input />",{
          id:"masterSearch"
        }).keypress(function(t){
          if(t.which == 13){
            var terms = $(this).val();
            var matches = [];
            eachTerm(terms,function(term){
              if(term in MeTLSearch){
                _.keys(MeTLSearch[term]).map(function(slide){
                  matches.push(
                    {
                      type:"slide",
                      slide:slide
                    });
                });
              }
              if(term in StackSearch){
                matches.push({
                  type:types[StackSearch[term]],
                  term:unclean[term],
                  item:StackSearch[term]
                });
              }
            });
            console.log("Matches:",terms,matches);
            var matchId = genId();
            items[matchId] = matches;
            repl("searchTerms",[matchId],sprintf("%s matches for %s",matches.length,terms));
            $(this).val("");
          }
        }));
      })
      return cont;
    }
    teachingWeeks.forEach(function(week,i){
      container.append($("<div />",{
        class:"teachingWeek",
        html:i == focussedWeek? highRes(i) : lowResContainer(i)
      }).css({
        width:i == focussedWeek? focussedWidth : cellWidth,
        height:px(h)
      }))
    });
    _.defer(function(){
      teachingWeeks.forEach(function(week,i){
        if(focussedWeek != i){
          lowResChart(i);
        }
      });
    });
  }
};
