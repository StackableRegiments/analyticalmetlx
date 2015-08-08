var Quizzes = (function(){
    var quizzes = {};
    var quizAnswers = {};
    var currentQuiz = {};
    $(function(){
        $("#quizzes").click(function(){
            showBackstage("quizzes");
        });
        var quizCount = $("<span />",{
            id:"quizCount"
        });
        $("#feedbackStatus").prepend(quizCount);
        quizCount.click(function(){
            showBackstage("quizzes");
        });
        refreshQuizCount();
    });
    var refreshQuizCount = function(){
        var quizCount = _.size(quizzes);
        if (quizCount > 0){
            $("#quizCount").text(sprintf("%s quizzes",_.size(quizzes)));
        } else {
            $("#quizCount").text("");
        }
    };
    var clearState = function(){
        quizzes = {};
        quizAnswers = {};
        currentQuiz = {};
        $("#quizCreateButtonContainer").empty();
        $("#quizListing").empty();
        $("#currentQuiz").empty();
        $("#quizCount").text("");
        if (Conversations.shouldModifyConversation()){
            $("<a/>",{
                id:"quizCreationButton",
                class: "quizCreationButton",
            }).on("click",function(){
                requestCreateQuizDialogue(Conversations.getCurrentConversation());
            }).append($("<span/>",{text: "Create Quiz"})).appendTo($("#quizCreateButtonContainer"));
        }
    };
    var renderQuizzesInPlace = function(){
        var haveAnsweredQuiz = function(quiz) {
            return quizAnswersFunction(quiz)[username] != undefined;
        }
        try{
            var answeredQuizzes = $.map(quizzes, function(quiz, id){ if(haveAnsweredQuiz(quiz)) return quiz; });
            var unansweredQuizzes = $.map(quizzes, function(quiz, id){ if(!haveAnsweredQuiz(quiz)) return quiz; });
            var answeredHtml = $("<div/>", {class: "answeredQuizzes"})
            if(answeredQuizzes.length > 0) {
                answeredHtml.append($("<span/>",{
                    class: "quizTitle",
                    text: "Answered Quizzes" }))
                $.map(answeredQuizzes,function(quizData){ answeredHtml.append(renderQuizSummary(quizData)); });
            }
            var unansweredHtml = $("<div/>", {class: "unansweredQuizzes"})
            if(unansweredQuizzes.length > 0) {
                unansweredHtml.append($("<span/>",{
                    class: "quizTitle",
                    text: "Unanswered Quizzes"}))
                $.map(unansweredQuizzes,function(quizData){ unansweredHtml.append(renderQuizSummary(quizData)); });
            }

            $("#quizListing").html($("<div/>").append(unansweredHtml).append(answeredHtml));

            if ("type" in currentQuiz){
                $("#currentQuiz").html(renderQuiz(currentQuiz));
            }
            refreshQuizCount();
        }
        catch(e){
            console.log("renderQuizzes exception",e,quizzes);
        }
    };
    var quizAnswersFunction = function(quiz){
        if ("type" in quiz && quiz.type == "quiz" && "id" in quiz){
            var quizId = quiz.id;
            var theseQuizAnswers = quizAnswers[quizId] || [];
            var theseQuizAnswerers = {};
            $.each(theseQuizAnswers,function(i,qra){
                if (Conversations.shouldModifyConversation() || qra.author.toLowerCase() == username.toLowerCase()){
                    var previousAnswer = theseQuizAnswerers[qra.answerer] || {answerCount:0};
                    theseQuizAnswerers[qra.answerer] = {latestAnswer:qra,answerCount:previousAnswer.answerCount + 1};
                }
            });
            return theseQuizAnswerers;
        } else {
            return {};
        }
    }
    var renderQuizSummary = function(quiz){
        var uniq = function(label){return sprintf("quiz_summary_%s_%s",label,quiz.id);};
        var rootElem = $("<a/>",{
            id: uniq("container"),
            class:"quizSummary"
        }).on("click",function(){
            currentQuiz = quiz;
            $("#currentQuiz").html(renderQuiz(quiz));
        })
        $("<div/>",{
            id: uniq("title"),
            class:"quizSummaryQuestion",
            text: quiz.question
        }).appendTo(rootElem);
        var allAnswersForThisQuiz = quiz.id in quizAnswers? _.reduce(quizAnswersFunction(quiz),function(prev,curr){
            var additional = "answerCount" in curr ? curr.answerCount : 0;
            return prev + additional;
        },0) : 0;
        $("<div/>",{
            id: uniq("answers"),
            class:"quizSummaryAnswerCount",
            text: sprintf("activity: %s", allAnswersForThisQuiz)
        }).appendTo(rootElem);
        if (Conversations.shouldModifyConversation()){
            $("<input/>",{
                id: uniq("editButton"),
                class:"quizSummaryEditButton",
                type: "button",
                value: "Edit Quiz"
            }).on("click",function(){
                requestUpdateQuizDialogue(Conversations.getCurrentConversationJid(),quiz.id);
            }).appendTo(rootElem);
        }
        return $("<div/>").append(rootElem);
    };
    var renderQuiz = function(quiz){
        var quizOptionClass = function(quiz, qo){
            var text = "quizOption";
            if (quiz.id in quizAnswers){
                $.each(theseQuizAnswerers,function(name,answerer){
                    if (answerer.latestAnswer.answer.toLowerCase() == qo.name.toLowerCase() && (Conversations.shouldModifyConversation() || name.toLowerCase() == username.toLowerCase())){
                        text = "quizOption activeAnswer";
                    }
                });
            };
            return text;
        }
        var uniq = function(label){return sprintf("quiz_%s_%s",label,quiz.id);};
        var rootElem = $("<div/>",{
            id: uniq("container"),
            class:"quizItem"
        });
        $("<div/>",{
            id: uniq("title"),
            class:"quizQuestion",
            text: quiz.question
        }).appendTo(rootElem);
        var theseQuizAnswerers = quizAnswersFunction(quiz);
        if ("url" in quiz){
            $("<image/>",{
                src: sprintf("/quizProxy/%s/%s",Conversations.getCurrentConversationJid(),quiz.id)
            }).appendTo(rootElem);
        }
        var generateColorClass = function(color) {return sprintf("background-color:%s", color.toString().split(",")[0])}
        $.each(quiz.options,function(i,qo){
            var quizRootElem = $("<div/>",{
                id: uniq("option_"+qo.name),
                class: quizOptionClass(quiz, qo)
            }).on("click",function(){
                answerQuiz(Conversations.getCurrentConversationJid(),quiz.id,qo.name);
            });
            var quizText = $("<span/>", {
                text: qo.text,
                class: "quizText",
            })
            var quizName = $("<span/>", {
                text: qo.name,
                style: generateColorClass(qo.color),
                class: "quizName"
            })
            $("<a/>",{
                id: uniq("option_button_"+qo.name),
            }).appendTo(quizRootElem);
            if (quiz.id in quizAnswers){
                $.each(theseQuizAnswerers,function(name,answerer){
                    if (answerer.latestAnswer.answer.toLowerCase() == qo.name.toLowerCase() && (Conversations.shouldModifyConversation() || name.toLowerCase() == username.toLowerCase())){
                        $("<span>",{
                            text: "x",
                            class: "quizOptionAnswer"
                        }).appendTo(quizRootElem);
                    }
                });
            }
            quizRootElem.appendTo(rootElem);
        });
        if (Conversations.shouldModifyConversation()){
            $("<input/>",{
                type:"button",
                class: "toolbar",
                value:"Display Quiz on next slide"
            }).on("click",function(){
                addQuizViewSlideToConversationAtIndex(Conversations.getCurrentConversationJid(),Conversations.getCurrentSlide().index + 1,quiz.id);
            }).appendTo(rootElem);
            $("<input/>",{
                type:"button",
                class: "toolbar",
                value:"Display Quiz with results on next slide"
            }).on("click",function(){
                addQuizResultsViewSlideToConversationAtIndex(Conversations.getCurrentConversationJid(),Conversations.getCurrentSlide().index + 1,quiz.id);
            }).appendTo(rootElem);
        }
        return rootElem;
    };
    var actOnQuiz = function(newQuiz){
        quizzes[newQuiz.id] = newQuiz;
    };
    var actOnQuizResponse = function(answer){
        var items = quizAnswers[answer.id];
        if (items){
            items[_.size(items)] = answer;
        } else {
            items = [answer];
        }
        quizAnswers[answer.id] = items;
    };
    var historyReceivedFunction = function(history){
        try {
            if ("type" in history && history.type == "history"){
                _.forEach(history.quizResponses,doStanzaReceivedFunction);
                _.forEach(history.quizzes,doStanzaReceivedFunction);
                renderQuizzesInPlace();
            }
        }
        catch (e){
            console.log("Quizzes.historyReceivedFunction",e);
        }
    };
    var stanzaReceivedFunction = function(input){
        doStanzaReceivedFunction(input);
        renderQuizzesInPlace();
    };
    var doStanzaReceivedFunction = function(possibleQuiz){
        try {
            if ("type" in possibleQuiz && possibleQuiz.type == "quizResponse"){
                actOnQuizResponse(possibleQuiz);
            } else if ("type" in possibleQuiz && possibleQuiz.type == "quiz"){
                actOnQuiz(possibleQuiz);
            }
        }
        catch(e){
            console.log("Quizzes.stanzaReceivedFunction exception",e);
        }
    };
    var receiveQuizzesFromLiftFunction = function(newQuizzes){
        try{
            if (_.size(newQuizzes) > 0){
                $.each(newQuizzes,function(unusedQuizName,quiz){
                    if ("type" in quiz && quiz.type == "quiz"){
                        quizzes[quiz.id] = quiz;
                    }
                });
            }
            renderQuizzesInPlace();
        }
        catch(e){
            console.log("Quizzes.receiveQuizzesFromLift exception",e);
        }
    };
    var receiveQuizResponsesFromLiftFunction = function(answers){
        try {
            if (_.size(answers) > 0){
                var firstAnswer = answers[0];
                quizAnswers[firstAnswer.id] = answers;
                renderQuizzesInPlace();
            }
        }
        catch(e){
            console.log("Quizzes.receiveQuizResponsesFromLift exception",e);
        }
    };
    var updateQuizFunction = function(quizId,newQuiz){
        var oldQuiz = _.find(quizzes,function(i){return i.id == quizId;});
        if (Conversations.shouldModifyConversation()){
            sendStanza(newQuiz);
        }
    };
    var createQuizFunction = function(newQuiz){
        if (Conversations.shouldModifyConversation()){
            sendStanza(newQuiz);
        }
    };
    Progress.onConversationJoin["Quizzes"] = clearState;
    Progress.historyReceived["Quizzes"] = historyReceivedFunction;
    Progress.stanzaReceived["Quizzes"] = stanzaReceivedFunction;
    return {
        getCurrentQuiz:function(){return currentQuiz;},
        getAllQuizzes:function(){return quizzes;},
        getAnswersForQuiz:quizAnswersFunction
    };
})();

//from Lift
//getQuizzesForConversation(conversationJid)
//answerQuiz(conversationJid,quizId,answer)
//createQuiz(conversationJid,newQuiz)
//updateQuiz(conversationJid,quizId,updatedQuiz)
