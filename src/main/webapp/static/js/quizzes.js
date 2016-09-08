var Quizzes = (function(){
    var quizzes = {};
    var quizAnswers = {};
		var quizResultsGraphs = {};

    var currentQuiz = {};
		var unansweredQuizSummaryContainer = {};
		var unansweredQuizSummaryTemplate = {};
		var answeredQuizSummaryContainer = {};
		var answeredQuizSummaryTemplate = {};
		var currentQuizContainer = {};
		var currentQuizTemplate = {};
		
		var quizDatagrid = {};
		var editQuizTemplate = {};
		var answerQuizTemplate = {};
		var showResultsTemplate = {};
		var actionsButtonsTemplate = {};

		var reRenderQuizzes = function(){
				quizDatagrid.jsGrid("loadData");
				var sortObj = quizDatagrid.jsGrid("getSorting");
				if ("field" in sortObj){
						quizDatagrid.jsGrid("sort",sortObj);
				}
		};

    $(function(){
			quizDatagrid = $("#quizDatagrid");
			editQuizTemplate = quizDatagrid.find(".editQuizPopup").clone();
			answerQuizTemplate = quizDatagrid.find(".answerQuizPopup").clone();
			showResultsTemplate = quizDatagrid.find(".viewResultsPopup").clone();
			actionsButtonsTemplate = quizDatagrid.find(".actionsButtons").clone();
			quizDatagrid.empty();

			var DateField = function(config){
					jsGrid.Field.call(this,config);
			};
			DateField.prototype = new jsGrid.Field({
					sorter: function(a,b){
							return new Date(a) - new Date(b);
					},
					itemTemplate: function(i){
							return new Date(i).toLocaleString();
					},
					insertTemplate: function(i){return ""},
					editTemplate: function(i){return ""},
					insertValue: function(){return ""},
					editValue: function(){return ""}
			});
			jsGrid.fields.dateField = DateField;

			var gridFields = [
				{name:"question",type:"text",title:"Question",readOnly:true},
				{name:"optionCount",type:"number",title:"Options",readOnly:true},
				{
					name:"url",type:"text",title:"Image",readOnly:true,
					itemTemplate:function(url,quizSummary){
						if (url){
							var quizUrl = sprintf("/quizProxy/%s/%s",Conversations.getCurrentConversationJid(),quizSummary.id);
							return $("<img/>",{src:quizUrl,style:"width:120px;height:90px"});
						} else {
							return $("<span/>");
						}
					}
				},
				{name:"created",type:"dateField",title:"Created",readOnly:true},
				{name:"timestamp",type:"dateField",title:"Modified",readOnly:true},
				{
					name:"answerCount",type:"number",title:"Answers",readOnly:true,
					itemTemplate:function(answerCount,quiz){
						if (Conversations.shouldModifyConversation()){
							return quiz.key in quizResultsGraphs ? quizResultsGraphs[quiz.key] : answerCount;
						} else {
							return answerCount;
						}
					}																																																		 
				},
				{
					name:"id",type:"text",title:"Actions",readOnly:true,sorting:false,
					itemTemplate:function(id,quizSummary){
						var quiz = quizzes[quizSummary.key];
						var rootElem = actionsButtonsTemplate.clone();
						var editButton = rootElem.find(".editPollButton");
						var answerButton = rootElem.find(".answerPollButton");
						
						answerButton.on("click",function(){
							var answerId = sprintf("quiz_answer_%s",quiz.id);
							var answerPopupContainer = $("<span/>",{id:answerId});
							var jAlert = $.jAlert({
								title:answerTitle,
								closeOnClick:true,
								width:"auto",
								content:answerPopupContainer[0].outerHTML
							});
							var answerContainerId = sprintf("answerContainer_%s",quiz.id);
							var answerPopup = answerQuizTemplate.clone();
							$("#"+answerId).append(answerPopup);
							var answerTitle = sprintf("Answer poll: %s",quiz.question);
							var answerContainer = answerPopup.find(".quizOptionContainer");
							var answerTemplate = answerContainer.find(".quizOption").clone();
							answerContainer.html(_.map(quiz.options,function(opt){
								var answer = answerTemplate.clone();
								answer.find(".quizOptionButton").on("click",function(){
									console.log("answering:",quiz,opt);
									answerQuiz(Conversations.getCurrentConversationJid(),quiz.id,opt.name);
									jAlert.closeAlert();
								});
								answer.find(".quizOptionName").text(opt.name);
								answer.find(".quizOptionText").text(opt.text);
								return answer;	
							}));

						});
						if (Conversations.shouldModifyConversation()){
							editButton.on("click",function(){
								var newQuiz = _.cloneDeep(quiz);
								var containerId = sprintf("edit_quiz_%s",quiz.id);
								var popupContainer = $("<span/>",{id:containerId});
								var jAlert = $.jAlert({
									title:editTitle,
									width:"90%",
									content:popupContainer[0].outerHTML
								});
								var editPopup = editQuizTemplate.clone();
								editPopup.find(".quizQuestion").val(quiz.question).on("change",function(){
									newQuiz.question = $(this).val();
								});

								var answerContainer = editPopup.find(".quizOptionContainer");
								var answerTemplate = answerContainer.find(".quizOption");
								var generateOptionButton = function(opt){
									var answer = answerTemplate.clone();
									answer.find(".quizOptionName").text(opt.name);
									answer.find(".quizOptionText").val(opt.text).on("change",function(){
										opt.text = $(this).val();
									});
									answer.find(".quizOptionDelete").on("click",function(){
										newQuiz.options = _.filter(newQuiz.options,function(o){return o.name != opt.name;});
										answer.remove();
									});
									return answer;
								};
								answerContainer.html(_.map(newQuiz.options,generateOptionButton));
								editPopup.find(".addOptionButton").on("click",function(){
									var lastHighestName = _.reverse(_.orderBy(newQuiz.options,"name"))[0].name;
									var key = lastHighestName;
									if (/^z+$/.test(key)) {
										// If all z's, replace all with a's
										key = key.replace(/z/g, 'a') + 'a';
									} else {
										// (take till last char) append with (increment last char)
										key = key.slice(0, -1) + String.fromCharCode(key.slice(-1).charCodeAt() + 1);
									}
									var newOption = {
										type:"quizOption",
										name:key,
										text:"",
										correct:false,
										color:["#ffffff",255]
									};
									console.log("creating new option:",newQuiz.options,newOption);
									newQuiz.options.push(newOption);
									var optionHtml = generateOptionButton(newOption);
									answerContainer.append(optionHtml);
									var newText = optionHtml.find(".quizOptionText")[0];
									newText.scrollIntoView();
									newText.focus();
								});
								editPopup.find(".updateQuiz").on("click",function(){
									sendStanza(newQuiz);
									jAlert.closeAlert();
								});
								editPopup.find(".deleteQuiz").on("click",function(){
									newQuiz.deleted = true;
									sendStanza(newQuiz);
									jAlert.closeAlert();
								});
								var editTitle = sprintf("Edit poll: %s",quiz.question);

								$("#"+containerId).append(editPopup);
							});
						} else {
							editButton.remove();
						}
						return rootElem;						
					}
				}
			];
			quizDatagrid.jsGrid({
				width:"100%",
				height:"auto",
				inserting:false,
				editing:false,
				sorting:true,
				paging:true,
				noDataContent: "No polls",
				controller: {
					loadData: function(filter){
						var sorted = _.map(_.keys(quizzes),function(k){
							var v = quizzes[k];
							var answers = quizAnswers[k];
							return {
								key:k,
								question:v.question,
								author:v.author,
								created:v.created,
								id:v.id,
								answerCount:_.size(answers),
								answers:answers,
								timestamp:v.timestamp,
								url:v.url,
								optionCount:_.size(v.options),
								options:v.options
							};
						});
						if ("sortField" in filter){
							sorted = _.sortBy(sorted,function(sub){
								return sub[filter.sortField];
							});
							if ("sortOrder" in filter && filter.sortOrder == "desc"){
								sorted = _.reverse(sorted);
							}
						}
						return sorted;
					}
				},
				pageLoading:false,
				fields: gridFields	
			});
			quizDatagrid.jsGrid("sort",{
				field:"name",
				order:"desc"
			});
			reRenderQuizzes();
			

			unansweredQuizSummaryContainer = $("#unansweredQuizListing");
			unansweredQuizSummaryTemplate = unansweredQuizSummaryContainer.find(".quizItem").clone();
			answeredQuizSummaryContainer = $("#answeredQuizListing");
			answeredQuizSummaryTemplate = answeredQuizSummaryContainer.find(".quizItem").clone();
			currentQuizContainer = $("#currentQuiz");
			currentQuizTemplate = currentQuizContainer.find(".currentQuizItem").clone();	

    });

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
			reRenderQuizzes();
			/*
        try{
            var haveAnsweredQuiz = function(quiz) {
                return quizAnswersFunction(quiz)[UserSettings.getUsername()] != undefined;
            }
						var sortedQuizzes = _.sortBy(quizzes,function(quiz){return quiz.created});
            var answeredQuizzes = $.map(sortedQuizzes, function(quiz, id){ if(haveAnsweredQuiz(quiz)) return quiz; });
            var unansweredQuizzes = $.map(sortedQuizzes, function(quiz, id){ if(!haveAnsweredQuiz(quiz)) return quiz; });

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
        }
        catch(e){
            console.log("renderQuizzes exception",e,quizzes);
        }
				*/
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
		    $(".quizSummary").removeClass('active');
		    $(this).addClass('active');
			currentQuiz = quiz;
			// reRenderCurrentQuiz
            $("#currentQuiz").html(renderQuiz(quiz));
		});
		rootElem.find(".quizSummaryQuestion").attr("id",uniq("title")).text(quiz.question);
		var allAnswersForThisQuiz = quiz.id in quizAnswers? _.reduce(quizAnswersFunction(quiz),function(prev,curr){
        var additional = "answerCount" in curr ? curr.answerCount : 0;
            return prev + additional;
        },0) : 0;
		rootElem.find(".quizSummaryAnswerCount").attr("id",uniq("answers")).text(sprintf("%s", allAnswersForThisQuiz));
        if (Conversations.shouldModifyConversation()){
			rootElem.closest(".quizItem").find(".quizSummaryEditButton").attr("id",uniq("editButton")).on("click",function(){
				requestUpdateQuizDialogue(Conversations.getCurrentConversationJid(),quiz.id);
			});
        } else {
			rootElem.closest(".quizItem").find(".quizTeacherControls").remove();
		}
		targetContainer.append(template);
    };
    var renderQuiz = function(quiz){
        var quizOptionAnswerCount = function(quiz, qo){
            var count = 0;
            if (quiz.id in quizAnswers){
                $.each(theseQuizAnswerers,function(name,answerer){
                    if (answerer.latestAnswer.answer.toLowerCase() == qo.name.toLowerCase() && (Conversations.shouldModifyConversation() || name.toLowerCase() == UserSettings.getUsername().toLowerCase())){
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
                        text = "quizOption activeAnswer active";
                    }
                });
            };
            return text;
        }
        var uniq = function(label){return sprintf("quiz_%s_%s",label,quiz.id);};
				var rootElem = currentQuizTemplate.clone();
				rootElem.attr("id",uniq("container"));
				rootElem.find(".quizQuestion").text(quiz.question).attr("id",uniq("title"));
        if (Conversations.isAuthor()){
            rootElem.find(".resultsTitle").show();
            var graph = rootElem.find(".quizResultsGraph");
            graph.show();
            _.defer(function(){
							/*
                var data = {
                    labels:_.map(quiz.options,"name"),
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
								*/
                var options = {
									/*
										tooltips: {
											custom:function(tooltip){
												console.log("tooltip",tooltip);
												return false;
											}
										},
										*/
										scales: {
											yAxes: [{
												stacked: true,
												ticks:{
													//display:false
												}
											}],
											xAxes: [{
												type: "linear",
												position: "bottom",
												ticks : {
													stepSize:1
												}	
											}]
										},
										legend:{
											display:false
										}
                }
								var splitLines = function(text,lineLength){
									return _.map(_.chunk(text.split(""),lineLength),function(line){return _.join(line,"");}); 		
								};
								var data = {
									//labels:quiz.options.map(function(qo){return splitLines(sprintf("%s: %s",qo.name,qo.text),20);}),
									//labels:quiz.options.map(function(qo){return sprintf("%s: %s",qo.name,qo.text)}),
									labels:_.map(quiz.options,"name"),
									datasets:[{
										data:quiz.options.map(function(qo){
											return quizOptionAnswerCount(quiz,qo);
										}),
										borderColor:["black"],
										backgroundColor:["gray"],
										borderWidth:1
									}]
								}
								var chartDesc = {
									type: "horizontalBar",
									data: data,
									options: options	
								};
                console.log(chartDesc);
								new Chart(graph[0].getContext("2d"),chartDesc);
                //new Chart(graph[0].getContext("2d")).Bar(data,options);
            });
        }
        else {
            rootElem.find(".resultsTitle").hide();
            rootElem.find(".quizResultsGraph").hide();
        }

        var theseQuizAnswerers = quizAnswersFunction(quiz);
				var totalAnswerCount = _.size(theseQuizAnswerers);
				var highWaterMark = totalAnswerCount * 0.5;
				var optimumMark = totalAnswerCount;
				var lowWaterMark = totalAnswerCount * 0.25;
        if ("url" in quiz){
					rootElem.find(".quizImagePreview").attr("src",sprintf("/quizProxy/%s/%s",Conversations.getCurrentConversationJid(),quiz.id)).show();
        }
        else {
            rootElem.find(".quizImagePreview").hide();
        }
       /*var generateColorClass = function(color) {return sprintf("border-color:%s", color.toString().split(",")[0])}*/
				var quizCountContainer = rootElem.find(".quizOptionCountContainer");
				var quizOptionCountElement = rootElem.find(".quizOptionCount");
				quizOptionCountElement.text(quiz.options.length);
				var quizOptionCountPluralizer = rootElem.find(".quizOptionCountPluralizer");
				quizOptionCountPluralizer.text(quiz.options.length == 1 ? "" : "s");
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
					optionRootElem.find(".quizOptionName").text(qo.name);
					var optionMeter = optionRootElem.find(".quizOptionMeter");
					if(Conversations.shouldModifyConversation()){
						var score = quizOptionAnswerCount(quiz,qo);
						optionRootElem.find(".quizOptionAnswerCount").text(score);
						optionMeter.attr("value",score).attr("min",0).attr("max",totalAnswerCount).attr("low",lowWaterMark).attr("high",highWaterMark).attr("optimum",optimumMark).text(sprintf("%s out of %s",score,totalAnswerCount));
					} else {
						optionRootElem.find(".quizOptionCountContainer").remove();
						optionMeter.remove();
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
					rootElem.find(".currentQuizTeacherControls").hide();
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
        getAnswersForQuiz:quizAnswersFunction,
				reRender:reRenderQuizzes	
    };
})();

//from Lift
//getQuizzesForConversation(conversationJid)
//answerQuiz(conversationJid,quizId,answer)
//createQuiz(conversationJid,newQuiz)
//updateQuiz(conversationJid,quizId,updatedQuiz)
