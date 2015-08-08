var MeTLSearch = {}//Reverse index cleaned term to slide
var StackSearch = {}//Reverse index cleaned term to item
var SlideThemes = {}//Forward index slide to cleaned terms
var WeeksBySlide = {};
var WeeksByItem = {};
function inferWeek(context){
    return WeeksBySlide[context] || WeeksByItem[context] || 0;
}
function indexConversation(c){
    items[c.jid].slides.forEach(function(slide){
        $.get(sprintf("/static/captions/%s.cap",slide),function(captions){
            $(captions).find("caption").map(function(_i,_caption){
                var caption = $(_caption);
                eachTerm($(caption).text(),function(cleaned){
                    var author = caption.attr("author");
                    setIfMissing(MeTLSearch,cleaned,{});
                    setIfMissing(MeTLSearch[cleaned],slide,[]);
                    MeTLSearch[cleaned][slide].push(author);
                    setIfMissing(SlideThemes,slide,{});
                    setIfMissing(SlideThemes[slide],cleaned,{});
                    setIfMissing(SlideThemes[slide][cleaned],author,0);
                    SlideThemes[slide][cleaned][author]++;
                })
            });
        });
    });
    c.elements.map(function(element){
        var t = element[0]
        var s = element[1]
        for(var i = 0; i < teachingWeeks.length - 2; i++){
            if(t >= teachingWeeks[i] && t <= teachingWeeks[i+1]){
                setIfMissing(WeeksBySlide,s,i);
            }
        }
    });
}
function indexStack(){
    _.values(items).filter(function(item){
        return "creationDate" in item;
    }).map(function(dated){
        for(var i = 0; i < teachingWeeks.length - 2; i++){
            if(dated.creationDate >= teachingWeeks[i] && dated.creationDate <= teachingWeeks[i+1]){
                setIfMissing(WeeksByItem,dated.id,i);
            }
            if("teachingEvent" in dated){
                setIfMissing(WeeksByItem,dated.teachingEvent,i);
            }
        }
    });
    var indexContent = function(discussable){
        eachTerm(discussable.discussion.content,function(term){
            setIfMissing(StackSearch,term,discussable.id);
        })
    }
    _.keys(items).filter(function(id){
        return types[id] == "topic";
    }).map(function(t){
        treeWalk(items[t],
                 indexContent,
                 indexContent,
                 indexContent,
                 function(v){})
    })
}
