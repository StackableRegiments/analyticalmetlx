function loadRelations(_root){
    var relations = {}
    function relate(protagonist,antagonist,type,post){
        var prot = clean(protagonist);
        var ant = clean(antagonist);
        if(!(prot in relations)){
            relations[prot] = {};
        }
        if(!(type in relations[prot])){
            relations[prot][type] = {}
        }
        if(!(ant in relations[prot][type])){
            relations[prot][type][ant] = {}
        }
        relations[prot][type][ant][post.id] = post;
    }
    treeWalk(_root,
             function(q){
                 relate(q.discussion.author,q.discussion.author,"question",q);
             },
             function(a,q){
                 relate(q.discussion.author,a.discussion.author,"answer",a);
             },
             function(c,parent){
                 relate(parent.discussion.author,c.discussion.author,"comment",c);
             },
             function(v,context){
                 relate(context.discussion.author,v.author,"vote",v);
             });
    return relations;
}
function chronoWalk(_root,transform){
    var _items = {};
    var _types = {};
    var _parents = {};
    var root = $.extend(true,{},_root);
    var states = [];
    treeWalk(root,
             function(_q){
                 var q = $.extend(true,{},_q);
                 q.answers = [];
                 q.votes = [];
                 _types[q.id] = "question";
                 _items[q.id] = q;
             },
             function(_a,q){
                 var a = $.extend(true,{},_a);
                 a.comments = [];
                 a.votes = [];
                 _types[a.id] = "answer";
                 _items[a.id] = a;
                 _parents[a.id] = q.id;
             },
             function(_c,parent){
                 var c = $.extend(true,{},_c);
                 c.comments = [];
                 c.votes = [];
                 _types[c.id] = "comment";
                 _items[c.id] = c;
                 _parents[c.id] = parent.id;
             },
             function(v,context){
                 _parents[v.id] = context.id;
                 _types[v.id] = "vote";
                 _items[v.id] = v;
                 v.creationDate = v.time;
             });
    var state = {};
    var sequence = [];
    sortObject(_items,"creationDate").forEach(function(kv){
        var id = kv[0];
        var t= _types[id];
        var item = kv[1];
        sequence.push({
            id:id,
            t:t,
            item:item,
            parentId:_parents[id],
            parent:_items[_parents[id]]
        });
        switch(t){
        case "question":
            setIfMissing(state,"questions",[]);
            state.questions.push(item);
            break;
        case "answer":
            _items[_parents[id]].answers.push(item);
            break;
        case "comment":
            _items[_parents[id]].comments.push(item);
            break;
        case "vote":
            _items[_parents[id]].votes.push(item);
            break;
        }
        if(transform){
            states.push(transform(state));
        }
        else{
            states.push($.extend(true,{},state));
        }
    });
    return {sequence:sequence,states:states,_items:_items};
}
function treeWalk(root,funcQ,funcA,funcC,funcV){
    if(root.questions){
        root.questions.forEach(function(q){
            treeWalk(q,funcQ,funcA,funcC,funcV);
        })
    }
    var walkC = function(c,parent){
        funcC(c,parent);
        c.comments.forEach(function(_c){
            walkC(_c,c);
        });
        c.votes.map(function(v){
            funcV(v,c);
        });
    }
    if(root.answers){
        funcQ(root);
        root.answers.forEach(function(a){
            funcA(a,root);
            a.comments.map(function(c){
                walkC(c,a);
            });
            a.votes.map(function(v){
                funcV(v,a);
            })
        })
        root.votes.forEach(function(v){
            funcV(v,root);
        })
    }
}
function square(matrix,length){
    var blank = squareMatrix(length);
    for(var i = 0;i<matrix.length;i++){
        var xs = matrix[i];
        for(var j = 0;j<xs.length;j++){
            blank[i][j] = xs[j];
        }
    }
    return blank;
}
function progressiveMatrix(root){
    var progression = chronoWalk(root,matrix);
    var end = progression.states[progression.states.length-1];
    return {
        sequence:progression.sequence,
        states:progression.states.map(function(m){
            return square(m.m,end.m.length)
        }),
        authorSet:sortObjectOn(end.authorSet,_.identity).map(function(kv){
            return kv[0];
        })
    };
}
function matrix(root){
    var authorSet = {}
    var index = 0
    var identifiedRelations = loadRelations(root)
    var relations = identifiedRelations
    function claimIndex(a){
        if(!(a in authorSet)){
            authorSet[a] = index++
        }
    }
    $.each(relations,function(p,cs){
        p.antagonists = {}
        claimIndex(p)
        $.each(cs,function(c,as){
            setIfMissing(cs,"antagonists",{})
            $.each(as,function(a){
                claimIndex(a)
                setIfMissing(cs.antagonists,a,0)
                cs.antagonists[a]++;
            })
        })
    })
    var m = []
    authorIndex = []
    $.each(authorSet,function(p){
        authorIndex[authorSet[p]] = p
        var antagonists = []
        $.each(authorSet,function(a,j){
            if(!(p in relations)){
                antagonists[j] = 0
            }
            else{
                antagonists[j] = relations[p].antagonists[a] || 0
            }
        })
        m.push(antagonists)
    })
    return {
        m:m,
        authorSet:authorSet
    }
}
