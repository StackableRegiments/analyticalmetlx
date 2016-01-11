var Quizzes = (function(){
    var quizzes = {};
    var quizAnswers = {};
    var currentQuiz = {};
		var unansweredQuizSummaryContainer = {};
		var unansweredQuizSummaryTemplate = {};
		var answeredQuizSummaryContainer = {};
		var answeredQuizSummaryTemplate = {};
		var currentQuizContainer = {};
		var currentQuizTemplate = {};
    $(function(){
			unansweredQuizSummaryContainer = $("#unansweredQuizListing");
			unansweredQuizSummaryTemplate = unansweredQuizSummaryContainer.find(".quizItem").clone();
			answeredQuizSummaryContainer = $("#answeredQuizListing");
			answeredQuizSummaryTemplate = answeredQuizSummaryContainer.find(".quizItem").clone();
			currentQuizContainer = $("#currentQuiz");
			currentQuizTemplate = currentQuizContainer.find(".currentQuizItem").clone();	
			$("#quizzes").click(function(){
					showBackstage("quizzes");
			});
			var quizCount = $("<div />",{
					id:"quizCount",
					class:"icon-txt"
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
            if(quizCount == 1){
                $("#quizCount").text(sprintf("%s quiz",_.size(quizzes)));
                $("#dedicatedQuizCount").text("This conversation has 1 quiz");
            }else {
                $("#quizCount").text(sprintf("%s quizzes",_.size(quizzes)));
                $("#dedicatedQuizCount").text(sprintf("This conversation has %s quizzes",quizCount));
            }
        } else {
            $("#quizCount").text("");
            $("#dedicatedQuizCount").text("This conversation has no quizzes");
        }
    };
    var clearState = function(){
        quizzes = {};
        quizAnswers = {};
        currentQuiz = {};
        unansweredQuizSummaryContainer.empty();
        answeredQuizSummaryContainer.empty();
        currentQuizContainer.empty();
        $("#quizCount").text("");
        if (Conversations.shouldModifyConversation()){
					$("#quizCreationButton").unbind("click").on("click",function(){
						requestCreateQuizDialogue(Conversations.getCurrentConversation());
					}).show();
				} else {
					$("#quizCreationButton").unbind("click").hide();
				}
    };
    var renderQuizzesInPlace = function(){
        try{
            var haveAnsweredQuiz = function(quiz) {
                return quizAnswersFunction(quiz)[UserSettings.getUsername()] != undefined;
            }
            var answeredQuizzes = $.map(quizzes, function(quiz, id){ if(haveAnsweredQuiz(quiz)) return quiz; });
            var unansweredQuizzes = $.map(quizzes, function(quiz, id){ if(!haveAnsweredQuiz(quiz)) return quiz; });

						answeredQuizSummaryContainer.empty();
            if(answeredQuizzes.length > 0) {
                $.map(_.filter(answeredQuizzes,function(aq){return aq.isDeleted != true;}),function(quizData){ renderQuizSummary(quizData,answeredQuizSummaryContainer,answeredQuizSummaryTemplate.clone()); });
            }
						unansweredQuizSummaryContainer.empty();
            if(unansweredQuizzes.length > 0) {
                $.map(_.filter(unansweredQuizzes,function(uaq){return uaq.isDeleted != true;}),function(quizData){ renderQuizSummary(quizData,unansweredQuizSummaryContainer,unansweredQuizSummaryTemplate.clone()); });
            }

            $("#currentQuiz").empty();
            if ("type" in currentQuiz && currentQuiz.type == "quiz" && "isDeleted" in currentQuiz && currentQuiz.isDeleted != true){
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
                if (Conversations.shouldModifyConversation() || qra.author.toLowerCase() == UserSettings.getUsername().toLowerCase()){
                    var previousAnswer = theseQuizAnswerers[qra.answerer] || {answerCount:0};
                    theseQuizAnswerers[qra.answerer] = {latestAnswer:qra,answerCount:previousAnswer.answerCount + 1};
                }
            });
            return theseQuizAnswerers;
        } else {
            return {};
        }
    }
    var renderQuizSummary = function(quiz,targetContainer,template){
        var uniq = function(label){return sprintf("quiz_summary_%s_%s",label,quiz.id);};
				var rootElem = template.find(".quizSummary");
				rootElem.attr("id",uniq("container")).on("click",function(){
					currentQuiz = quiz;
					// reRenderCurrentQuiz
          $("#currentQuiz").html(renderQuiz(quiz));
				});
				rootElem.find(".quizSummaryQuestion").attr("id",uniq("title")).text(quiz.question);
				var allAnswersForThisQuiz = quiz.id in quizAnswers? _.reduce(quizAnswersFunction(quiz),function(prev,curr){
            var additional = "answerCount" in curr ? curr.answerCount : 0;
            return prev + additional;
        },0) : 0;
				rootElem.find(".quizSummaryAnswerCount").attr("id",uniq("answers")).text(sprintf("activity: %s", allAnswersForThisQuiz));
        if (Conversations.shouldModifyConversation()){
					rootElem.find(".quizSummaryEditButton").attr("id",uniq("editButton")).on("click",function(){
							requestUpdateQuizDialogue(Conversations.getCurrentConversationJid(),quiz.id);
					});
        } else {
					rootElem.find(".quizTeacherControls").remove();
				}
				targetContainer.append(template);
    };
    var renderQuiz = function(quiz){
        var quizOptionAnswerCount = function(quiz, qo){
            var count = 0;
            if (quiz.id in quizAnswers){
                $.each(theseQuizAnswerers,function(name,answerer){
                    if (answerer.latestAnswer.answer.toLowerCase() == qo.name.toLowerCase() && (Conversations.shouldModifyConversation() || name.toLowerCase() == username.toLowerCase())){
                        count = count +1;
                    }
                });
            };
            return count;
        }
        var quizOptionClass = function(quiz, qo){
            var text = "quizOption";
            if (quiz.id in quizAnswers){
                $.each(theseQuizAnswerers,function(name,answerer){
                    if (answerer.latestAnswer.answer.toLowerCase() == qo.name.toLowerCase() && name.toLowerCase() == UserSettings.getUsername().toLowerCase()){
                        text = "quizOption activeAnswer";
                    }
                });
            };
            return text;
        }
        var uniq = function(label){return sprintf("quiz_%s_%s",label,quiz.id);};
				var rootElem = currentQuizTemplate.clone();
				rootElem.attr("id",uniq("container"));
				rootElem.find(".quizQuestion").text(quiz.question).attr("id",uniq("title"));
				var graph = rootElem.find(".quizResultsGraph");
        _.defer(function(){
            var data = {
                labels:_.pluck(quiz.options,"name"),
                datasets:[
                    {
                        fillColor:"gray",
                        strokeColor:"black",
                        data:quiz.options.map(function(qo){
                            return quizOptionAnswerCount(quiz,qo);
                        })
                    }
                ]
            };
            var options = {
                scaleOverride:true,
                scaleStepWidth:1,
                scaleSteps:Math.max.apply(Math,data.datasets[0].data),
                scaleStartValue:0
            }
            console.log(data,options);
            new Chart(graph[0].getContext("2d")).Bar(data,options);
        });

        var theseQuizAnswerers = quizAnswersFunction(quiz);
        if ("url" in quiz){
					rootElem.find(".quizImagePreview").attr("src",sprintf("/quizProxy/%s/%s",Conversations.getCurrentConversationJid(),quiz.id));
        }
        var generateColorClass = function(color) {return sprintf("background-color:%s", color.toString().split(",")[0])}
				var quizOptionContainer = rootElem.find(".quizOptionContainer");
				var quizOptionTemplate = quizOptionContainer.find(".quizOption").clone();
				quizOptionContainer.empty();
        $.each(quiz.options,function(i,qo){
					var optionRootElem = quizOptionTemplate.clone();
					quizOptionContainer.append(optionRootElem);
					optionRootElem.attr("id",uniq("option_"+qo.name)).addClass(quizOptionClass(quiz,qo)).on("click",function(){
						answerQuiz(Conversations.getCurrentConversationJid(),quiz.id,qo.name);
					});	
					optionRootElem.find(".quizOptionText").text(qo.text);
					optionRootElem.find(".quizOptionName").attr("style",generateColorClass(qo.color)).text(qo.name);
					if(Conversations.shouldModifyConversation()){
						optionRootElem.find(".quizOptionAnswerCount").text(quizOptionAnswerCount(quiz,qo));
					} else {
						optionRootElem.find(".quizOptionCountContainer").remove();
					}
        });
        if (Conversations.shouldModifyConversation()){
					rootElem.find(".quizShouldDisplayOnNextSlide").on("click",function(){
						addQuizViewSlideToConversationAtIndex(Conversations.getCurrentConversationJid(),Conversations.getCurrentSlide().index + 1,quiz.id);
					});
          rootElem.find(".quizResultsShouldDisplayOnNextSlide").on("click",function(){
						addQuizResultsViewSlideToConversationAtIndex(Conversations.getCurrentConversationJid(),Conversations.getCurrentSlide().index + 1,quiz.id);
					});
					rootElem.find(".deleteQuiz").on("click",function(){
							requestDeleteQuizDialogue(Conversations.getCurrentConversationJid(),quiz.id);
					});
        } else {
					rootElem.find(".currentQuizTeacherControls");
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
                clearState();
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
                if (currentQuiz.id == possibleQuiz.id){
                    currentQuiz = possibleQuiz;
                }
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
