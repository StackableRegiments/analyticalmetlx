var items = {}
var types = {}
var parents = {}
var standings = {}
var replayables = []
var replayableItems = {}
var replayableTypes = {}

function updateStandings(ss){
    for(var s in ss){
        standings[clean(s)] = ss[s];
    }
}
var voteCount = 0
function preProcessVotes(post){
    post.votes.forEach(function(v){
        v.id = v.time
        v.creationDate = v.time
        parents[v.id] = post.id
        replayableItems[v.id] = v
        replayableTypes[v.id] = "vote"
    })
}
function preProcessComment(c,a){
    preProcessVotes(c)
    parents[c.id] = a.id
    replayableItems[c.id] = c
    replayableTypes[c.id] = "comment"
    c.comments.map(function(_c){preProcessComment(_c,c)})
}
var receivePolicy = 0
function receiveQuestions(qs){
    _.defer(function(){
        switch(receivePolicy){
        case 0:
            $(document).off("previewOnQuestion")
            doReceiveQuestionsShort(qs)
            break;
        case 1:
            doReceiveQuestionsShort(qs)
            break;
        }
        renderBlendedTimeline();
    });
}
function doReceiveQuestionsShort(qs){
    qs.forEach(doReceiveQuestion)
    var seedType = "topic"
    trunk.pushAll(seedType,_.keys(categories[seedType]),seedType)
    indexStack();
}
function receiveQuestion(q){
    _.defer(function(){doReceiveQuestion(q)});
}
function recordAuthor(a){
    categoriesOfWork["author"](a,{id:a})
    items[a] = {
        integer:standings[a] || 0,
        standing:standing(standings[a]),
        id:a
    }
}
function doReceiveQuestion(q){
    $(document).trigger("previewOnQuestion",[q])
    items[q.id] = q;
    var recordVotes = function(post){
        post.votes.map(function(v){
            v.id = sprintf("%s%s",v.author,v.time)
            items[v.id] = v
            parents[v.id] = post.id
            categoriesOfWork["vote"](v.id,v)
            recordAuthor(v.author)
        })
    }
    var recordComments = function(c,parent){
        items[c.id] = c
        parents[c.id] = parent.id
        categoriesOfWork["comment"](c.id,c)
        recordAuthor(c.discussion.author)
        recordVotes(c)
        c.comments.map(function(c1){
            recordComments(c1,c)
        });
    }
    if(!(q.teachingEvent in items)){
        items[q.teachingEvent] = {
            id:q.teachingEvent,
            final:q,
            questions:[]
        }
    }
    else{
        items[q.teachingEvent].questions.push(q)
    }
    categoriesOfWork["topic"](q.teachingEvent,items[q.teachingEvent])
    categoriesOfWork["question"](q.id,q)
    recordAuthor(q.discussion.author)
    recordVotes(q)
    q.answers.map(function(a){
        items[a.id] = a
        parents[a.id] = q.id
        categoriesOfWork["answer"](a.id,a)
        recordAuthor(a.discussion.author)
        a.comments.map(function(c){recordComments(c,a)})
        recordVotes(a)
    })
    $(document).trigger("onQuestion",[q])
}
