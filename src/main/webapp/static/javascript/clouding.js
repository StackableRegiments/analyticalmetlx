$('#stopwordToggle').click(
  function(){
    $('.stopped').toggle();
  });
var filter = /[^a-z|0-9]/g;
var unclean = {}
function clean(s){
  var lower = s.toLowerCase().replace(filter,"")
  var cleaned = stemmer(lower);
  setIfMissing(unclean,cleaned,lower)
  return cleaned
}
function grow(el){
  el.css("font-size",Math.min(40,parseInt(el.css("font-size")+1))+"pt")
}
function textSearch(item,term){
  if(!item.discussion) return false;
  return clean(item.discussion.content).indexOf(term) >= 0;
}
function authorSearch(item,what){
  if(item.discussion){
    return clean(item.discussion.author) == what
  }
  else if(item.author){
    return clean(item.author) == what
  }
  else return false;
}
function searcher(what){
  return function(){
    search(what)
  }
}
function search(what){
  var cont = $("<div />")
  var matches = 0;
  for(var id in items){
    var item = items[id];
    if(authorSearch(item,what) || textSearch(item,what)){
      cont.append(render(id))
      matches++;
    }
  }
  return vjq(cont,sprintf("%s matching posts for %s",matches,what));
}
function renderDifferentiatedCloud(containerSelector,rootId){
  var item = items[rootId];
  var incidences = {}
  var walkVotes = function(root){
    var content = root.discussion.content;
    eachTerm(content,function(w){
      root.votes.forEach(function(v){
        var s = standing(clean(v.author))
        setIfMissing(incidences[w].votes,s,0)
        incidences[w].votes[s]++;
      })
    })
  }
  var walkComments = function(root){
    includeText(root.discussion.content);
    root.comments.map(walkComments);
    walkVotes(root)
  }
  var includeText = function(text){
    eachTerm(text,function(w){
      setIfMissing(incidences,w,{
        count:0,
        votes:{}
      });
      incidences[w].count++;
    })
  }
  var objectives = {};
  var includeObjectives = function(text){
    eachTerm(text,function(w){
      objectives[w] = w;
    });
  }
  if(item.handwriting){
    includeText(item.handwriting);
  }
  if(item.discussion){
    includeObjectives(item.discussion.content);
  }
  if(item.questions){
    item.questions.forEach(function(q){
      includeText(q.discussion.content);
      includeObjectives(q.discussion.content);
      q.answers.forEach(function(a){
        includeText(a.discussion.content);
        a.comments.map(walkComments);
        walkVotes(a);
      });
      walkVotes(q)
    })
  }
  if(item.answers){
    item.answers.forEach(function(a){
      includeText(a.discussion.content);
      a.comments.map(walkComments);
      walkVotes(a)
    })
  }
  var sorted = sortObjectOn(incidences,function(inc){
    return inc.count;
  }).reverse()
  var shade = function(votes,parent){
    var sum = _.values(votes).reduce(function(acc,item){return acc + item},0);
    if(sum != 0){
      var dStop = (votes.D || 0) / sum;
      var cStop = dStop + (votes.C || 0) / sum;
      var pStop = cStop + (votes.P || 0) / sum;
      var gradient = sprintf(
        "-webkit-gradient(linear, "
          + "left top, "
          + "right top, "
          + "from(rgb(0,255,0)), "
          + "color-stop(%s, rgb(0,255,0)), "
          + "color-stop(%s, rgb(0,0,255)), "
          + "color-stop(%s, rgb(0,0,255)), "
          + "color-stop(%s, rgb(255,0,0)), "
          + "to(rgb(255,0,0)))"
        ,dStop,dStop,cStop,cStop,pStop,pStop);
      parent.css("background-image",gradient);
      parent.css("-webkit-background-clip","text");
      parent.css("-webkit-text-fill-color","transparent");
    }
    else{
      parent.css("color","black");
    }
  }
  var cont = $(containerSelector);
  var noVotes = "No post in which it appeared received any votes.";
  var explain = function(wordInc){
    var inc = wordInc[1];
    var word = wordInc[0];
    var d = inc.votes.D || 0;
    var c = inc.votes.C || 0;
    var p = inc.votes.P || 0;
    var votes = sprintf("It received %s votes from D students, %s votes from C students and %s votes from P students",d,c,p);
    var voteCount = d+c+p;
    var voteExplanation = voteCount > 0 ? votes : noVotes;
    var objectiveExplanation = (word in objectives) ? "  It is highlighted to indicate that it was in the original teaching objective." : "";
    return sprintf("The fragment '%s' (or a similar word) was used by a student in this workspace's cohort %s times.%s%s",
                   unclean[word],
                   inc.count,
                   voteExplanation,
                   objectiveExplanation);
  }
  sorted.forEach(function(wordInc){
    var stopped = Stopwords.indexOf(wordInc[0]) >= 0;
    if(!stopped){
      var word = wordInc[0];
      var incs = wordInc[1].count;
      for(var i = 0; i<incs; i++){
        var objective = word in objectives;
        cloudIn(containerSelector,word,stopped,objective);
      }
      var parent = cont.find(sprintf(".tag_%s",clean(wordInc[0])))
      parent.wrap("<span />").parent().appendTo();
      shade(wordInc[1].votes,parent);
      parent.attr("title",explain(wordInc));
    }
  })
}
function cloudIn(containerId,_what,stopped,objective){
  var container = $(containerId)
  var what = clean(_what)
  var t = container.find(sprintf(".tag_%s",what));
  if(t.length == 0){
    t = $("<span />",{
      class:"tag tag_"+what,
      text:unclean[what],
      click:searcher(what)
    })
    container.append(t)
  }
  else{
    grow(t)
  }
  if(stopped){
    t.addClass("stopped")
  }
  if(objective){
    t.addClass("objective");
  }
}
function _cloud(where,_what,stopped){
  var containerId = sprintf("#%sContainer",where)
  var container = $(containerId)
  var cloudId = sprintf('#%sCloud',where)
  if(container.find(cloudId).length == 0){
    var el = $(sprintf("<div class='cloudContainer' id='%sCloud'><h1>%s</h1></div>",where,where))
    container.append(el)
  }
  cloudIn(cloudId,_what,stopped)
}
function eachTerm(text,f){
  var terms = text.split(" ").map(clean)
  terms.map(function(term){
    if(term.length > 0){
      f(term,terms)
    }
  })
}
console.log("Function setup complete")
