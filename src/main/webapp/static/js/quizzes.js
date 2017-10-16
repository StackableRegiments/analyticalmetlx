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

    var reRenderActiveGraphFunction = undefined;

    var reRenderQuizzes = function(){
        if( WorkQueue != undefined ) {
            WorkQueue.enqueue(function () {
                quizDatagrid.jsGrid("loadData");
                var sortObj = quizDatagrid.jsGrid("getSorting");
                if ("field" in sortObj) {
                    quizDatagrid.jsGrid("sort", sortObj);
                }
            });
        }
    };

    var urlForQuizImage = function(quizId){
        return sprintf("/quizProxy/%s/%s",Conversations.getCurrentConversationJid(),quizId);
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

        var displayAnswerPopupForQuiz = function(quizSummary){
            var quiz = quizzes[quizSummary.key];
            var resultsW = 640;
            var resultsH = 480;
            var resultsPopupTitle = sprintf("Results for %s",quiz.question);
            var popupId = sprintf("quizResultsPopup_%s",quiz.id);
            var resultsPopupContainer = $("<div/>",{id:popupId});
            var jAlert = $.jAlert({
                title:resultsPopupTitle,
                width:"auto",
                content:resultsPopupContainer[0].outerHTML,
                onClose:function(ja){
                    reRenderActiveGraphFunction = undefined;
                }
            });
            reRenderActiveGraphFunction = function(quizId){
                if (quizId == quizSummary.key){
                    var rootElem = showResultsTemplate.clone();
                    rootElem.find(".quizQuestion").text(quiz.question);
                    var quizResultsPopupId = sprintf("quizResultsPopupGraph_%s",quiz.id);
                    var svg = $(quizResultsGraphs[quizSummary.key]).clone();
                    rootElem.find(".quizResultsGraph").attr("id",quizResultsPopupId).append(svg.clone()).css({width:"45%"});
                    var quizImagePreview = rootElem.find(".quizImagePreview").css({"max-width":"45%"});
                    quizImagePreview.attr("src",urlForQuizImage(quiz.id));
                    if ("url" in quiz){
                        quizImagePreview.show();
                    } else {
                        quizImagePreview.hide();
                    }

                    var answerContainer = rootElem.find(".quizOptionContainer");
                    var answerTemplate = answerContainer.find(".quizOption");

                    var theseQuizAnswerers = quizAnswersFunction(quiz);
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

                    var totalAnswerCount = _.size(theseQuizAnswerers);
                    var highWaterMark = totalAnswerCount * 0.5;
                    var optimumMark = totalAnswerCount;
                    var lowWaterMark = totalAnswerCount * 0.25;

                    answerContainer.html(_.map(quiz.options,function(opt){
                        var answer = answerTemplate.clone();
                        answer.find(".quizOptionName").text(opt.name);
                        answer.find(".quizOptionText").text(opt.text);
                        var optionMeter = answer.find(".quizOptionMeter");
                        if(Conversations.shouldModifyConversation()){
                            var score = quizOptionAnswerCount(quiz,opt);
                            answer.find(".quizOptionAnswerCount").text(score);
                            optionMeter.attr("value",score).attr("min",0).attr("max",totalAnswerCount).attr("low",lowWaterMark).attr("high",highWaterMark).attr("optimum",optimumMark).text(sprintf("%s out of %s",score,totalAnswerCount));
                        } else {
                            answer.find(".quizOptionCountContainer").remove();
                            optionMeter.remove();
                        }
                        return answer;
                    }));

                    var withSvgQuizImage = function(afterFunc){
                        var w = resultsW;
                        var h = resultsH;
                        var svgObj = svg.clone()[0];
                        var svgString = new XMLSerializer().serializeToString(svgObj);
                        var t = new Date().getTime();
                        var username = UserSettings.getUsername();
                        var cc = Conversations.getCurrentConversation();
                        var title = sprintf("quizresultsimage%s%s.jpg",username,t.toString());
                        var identity = sprintf("%s:%s:%s",cc.jid.toString(),title,t);
                        var url = sprintf("/uploadSvg?jid=%s&filename=%s&width=%s&height=%s",cc.jid.toString(),encodeURI(identity),w,h);
                        $.ajax({
                            url: url,
                            type: 'POST',
                            success: function(e){
                                var newIdentity = $(e).find("resourceUrl").text();
                                afterFunc(newIdentity,w,h);
                            },
                            error: function(e){
                                console.log("exception while adding the quizResultsGraph to the slide",e);
                            },
                            data: svgString,
                            cache: false,
                            contentType: false,
                            processData: false
                        });
                    };
                    var word = function(body,size){
                        return {
                            bold:false,
                            color:["#000000",255],
                            font:"sans-serif",
                            italic:false,
                            justify:"left",
                            size:size,
                            text:sprintf("%s\n",body),
                            underline:false
                        };
                    }
                    var labels = function(slide){
                        var opts = _.map(quiz.options,function(opt){
                            return word(sprintf("%s: %s",opt.name,opt.text),18);
                        });
                        opts.unshift(word(quiz.question,24));
                        opts.push(word("\n",24));
                        var stanza = {
                            author:UserSettings.getUsername(),
                            timestamp:-1,
                            target:"presentationSpace",
                            tag:"_",
                            privacy:"PUBLIC",
                            slide:slide,
                            identity:sprintf("%s_%s_%s",UserSettings.getUsername(),Date.now(),_.uniqueId()),
                            type:"multiWordText",
                            x:margin.left,
                            y:margin.bottom + resultsH,
                            requestedWidth:resultsW,
                            width:resultsW,
                            height:resultsH,
                            words:opts,
                            audiences:[]
                        }
                        return stanza;
                    };
                    rootElem.find(".quizResultsShouldDisplayOnSlide").unbind("click").on("click",function(){
                        withSvgQuizImage(function(newIdentity,w,h){
                            var slideId = Conversations.getCurrentSlideJid();
                            var username = UserSettings.getUsername();
                            var t = new Date().getTime();
                            var imageId = sprintf("%s%s%s",slideId,username,t);
                            var newTag = imageId;
                            var imageStanza = {
                                type:"image",
                                author:username,
                                height:h,
                                width:w,
                                identity:imageId,
                                slide:slideId,
                                source:newIdentity,
                                privacy:"PUBLIC",
                                tag:newTag,
                                target:"presentationSpace",
                                timestamp:t,
                                x:margin.left,
                                y:margin.top
                            };
                            sendStanza(imageStanza);
                            sendStanza(labels(slideId));
                            jAlert.closeAlert();
                            hideBackstage();
                        });
                    });
                    rootElem.find(".quizResultsShouldDisplayOnNextSlide").unbind("click").on("click",function(){
                        withSvgQuizImage(function(newIdentity,w,h){
                            var convJid = Conversations.getCurrentConversationJid();
                            var newIndex = Conversations.getCurrentSlide().index + 1;
                            addImageSlideToConversationAtIndex(convJid,newIndex,newIdentity,labels("0"));
                            jAlert.closeAlert();
                            hideBackstage();
                        });
                    });
                    $("#"+popupId).html(rootElem);
                };
            };
            reRenderActiveGraphFunction(quizSummary.key);
        };

        var gridFields = [
            {name:"question",type:"text",title:"Question",readOnly:true},
            {name:"optionCount",type:"number",title:"Options",readOnly:true},
            {
                name:"url",type:"text",title:"Image",readOnly:true,
                itemTemplate:function(url,quizSummary){
                    if (url){
                        return $("<img/>",{src:urlForQuizImage(quizSummary.id),style:"width:120px;height:90px"});
                    } else {
                        return $("<span/>");
                    }
                }
            },
            {name:"created",type:"dateField",title:"Created",readOnly:true},
            {name:"timestamp",type:"dateField",title:"Modified",readOnly:true},
            {
                name:"answerCount",type:"number",title:"Answers",readOnly:true,
                itemTemplate:function(answerCount,quizSummary){
                    var quiz = quizzes[quizSummary.key];
                    if (Conversations.shouldModifyConversation()){
                        var elem = $("<div/>");
                        elem.append(quizResultsGraphs[quizSummary.key]);
                        elem.css({
                            width:"100%",
                            height:"100%"
                        });
                        elem.on("click",function(){
                            displayAnswerPopupForQuiz(quizSummary);
                        });
                        return elem;
                    } else {
                        var myAnswer = quizAnswersFunction(quiz)[UserSettings.getUsername()];
                        if (myAnswer != undefined && "latestAnswer" in myAnswer){
                            return $("<span/>",{text:myAnswer.latestAnswer.answer});
                        } else {
                            return $("<span/>",{text:"unanswered"});
                        }
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
                    var viewAnswersButton = rootElem.find(".viewAnswersButton");
                    answerButton.on("click",function(){
                        var answerTitle = sprintf("Answer poll: %s",quiz.question);
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
                        answerPopup.find(".quizOptionCount").text(quizSummary.optionCount);
                        answerPopup.find(".quizOptionCountPluralizer").text(quizSummary.optionCount == 1 ? "" : "s");
                        var answerContainer = answerPopup.find(".quizOptionContainer");
                        var answerTemplate = answerContainer.find(".quizOption").clone();
                        answerContainer.html(_.map(quiz.options,function(opt){
                            var answer = answerTemplate.clone();
                            answer.find(".quizOptionButton").on("click",function(){
                                answerQuiz(Conversations.getCurrentConversationJid(),quiz.id,opt.name);
                                jAlert.closeAlert();
                            });
                            answer.find(".quizOptionName").text(opt.name);
                            answer.find(".quizOptionText").text(opt.text);
                            return answer;
                        }));
                        var quizImagePreview = answerPopup.find(".quizImagePreview");
                        quizImagePreview.attr("src",urlForQuizImage(quiz.id));
                        if ("url" in quiz){
                            quizImagePreview.show();
                        } else {
                            quizImagePreview.hide();
                        }
                    });
                    if (Conversations.shouldModifyConversation()){
                        editButton.on("click",function(){
                            editQuizDialog(quiz);
                        });
                        viewAnswersButton.on("click",function(){
                            displayAnswerPopupForQuiz(quizSummary);
                        });
                    } else {
                        editButton.remove();
                        viewAnswersButton.remove();
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
                    var validQuizIds = _.map(_.filter(quizzes,function(q){
                        return q.isDeleted == false;
                    }),"id");
                    var sorted = _.map(validQuizIds,function(k){
                        var v = quizzes[k];
                        var answers = quizAnswers[k];
                        quizResultsGraphs[k] = updateQuizGraph(v);
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
    });

    var editQuizDialog = function(quiz){
        var newQuiz = _.cloneDeep(quiz);
        var containerId = sprintf("edit_quiz_%s",quiz.id);
        var popupContainer = $("<span/>",{id:containerId});
        var editTitle = sprintf("Edit poll: %s",quiz.question);
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
            var key = "A";
            var currentOptions = _.reverse(_.orderBy(newQuiz.options,"name"));
            if(currentOptions.length > 0){
                key = currentOptions[0].name;
                if (/^z+$/.test(key)) {
                    // If all z's, replace all with a's
                    key = key.replace(/z/g, 'a') + 'a';
                } else {
                    // (take till last char) append with (increment last char)
                    key = key.slice(0, -1) + String.fromCharCode(key.slice(-1).charCodeAt() + 1);
                }
            }
            var newOption = {
                type:"quizOption",
                name:key,
                text:"",
                correct:false,
                color:Colors.getColorForSeed(key)
            };
            newQuiz.options.push(newOption);
            var optionHtml = generateOptionButton(newOption);
            answerContainer.append(optionHtml);
            var newText = optionHtml.find(".quizOptionText")[0];
            newText.scrollIntoView();
            newText.focus();
        });
        var imagePreview = editPopup.find(".quizImagePreview");
        imagePreview.attr("src",urlForQuizImage(newQuiz.id));
        if ("url" in newQuiz){
            imagePreview.show();
        } else {
            imagePreview.hide();
        }
        var removeQuizButton = editPopup.find(".removeSlideImageFromQuiz");
        removeQuizButton.on("click",function(){
            imagePreview.attr("src",undefined).hide();
            removeQuizButton.hide();
            newQuiz.url = undefined;
        });
        if ("url" in newQuiz){
            removeQuizButton.show();
        } else {
            removeQuizButton.hide();
        };
        editPopup.find(".addSlideImageToQuiz").on("click",function(){
            WorkQueue.pause();
            var cc = Conversations.getCurrentConversation();

            var submissionQuality = 0.4;
            var tempCanvas = $("<canvas />");
            var w = board[0].width;
            var h = board[0].height;
            tempCanvas.width = w;
            tempCanvas.height = h;
            tempCanvas.attr("width",w);
            tempCanvas.attr("height",h);
            tempCanvas.css({
                width:w,
                height:h
            });
            var tempCtx = tempCanvas[0].getContext("2d");
            tempCtx.fillStyle = "white";
            tempCtx.fillRect(0,0,w,h);
            tempCtx.drawImage(board[0],0,0,w,h);
            var imageData = tempCanvas[0].toDataURL("image/jpeg",submissionQuality);
            var t = new Date().getTime();
            var username = UserSettings.getUsername();
            var title = sprintf("quizimage%s%s.jpg",username,t.toString());
            var identity = sprintf("%s:%s:%s",cc.jid.toString(),title,t);
            var url = sprintf("/uploadDataUri?jid=%s&filename=%s",cc.jid.toString(),encodeURI(identity));
            $.ajax({
                url: url,
                type: 'POST',
                success: function(e){
                    var newIdentity = $(e).find("resourceUrl").text();
                    newQuiz.url = newIdentity;
                    imagePreview.attr("src",imageData).show();
                    removeQuizButton.show();
                    WorkQueue.gracefullyResume();
                },
                error: function(e){
                    console.log("exception while snapshotting the slide for the quizImage",e);
                    WorkQueue.gracefullyResume();
                },
                data: imageData,
                cache: false,
                contentType: false,
                processData: false
            });
        });
        editPopup.find(".updateQuiz").on("click",function(){
            sendStanza(newQuiz);
            jAlert.closeAlert();
        });
        editPopup.find(".deleteQuiz").on("click",function(){
            newQuiz.isDeleted = true;
            sendStanza(newQuiz);
            jAlert.closeAlert();
        });

        $("#"+containerId).append(editPopup);
    };

    var clearState = function(){
        quizzes = {};
        quizAnswers = {};
        if (Conversations.shouldModifyConversation()){
            $("#quizCreationButton").unbind("click").on("click",function(){
                var t = new Date().getTime();
                var username = UserSettings.getUsername();
                var id = sprintf("%s",t);
                var newQuiz = {
                    type:"quiz",
                    options:[
                        {
                            type:"quizOption",
                            name:"A",
                            text:"",
                            correct:false,
                            color:Colors.getColorForSeed("A")
                        },
                        {
                            type:"quizOption",
                            name:"B",
                            text:"",
                            correct:false,
                            color:Colors.getColorForSeed("B")
                        },
                        {
                            type:"quizOption",
                            name:"C",
                            text:"",
                            correct:false,
                            color:Colors.getColorForSeed("C")
                        }
                    ],
                    question:"",
                    author:username,
                    created:t,
                    id:id,
                    isDeleted:false,
                    timestamp:t,
                    audiences:[]
                };
                editQuizDialog(newQuiz);
            }).show();
        } else {
            $("#quizCreationButton").unbind("click").hide();
        }
    };
    var renderQuizzesInPlace = function(){
        reRenderQuizzes();
    };
    var quizAnswersFunction = function(quiz){
        if (quiz !== undefined && "type" in quiz && quiz.type == "quiz" && "id" in quiz){
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
    };
    var margin = {
        left:30,
        right:30,
        top:30,
        bottom:30
    }
    var generateQuizResultsGraph = function(quiz,w,h){
        var theseQuizAnswerers = quizAnswersFunction(quiz);
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
        var scorePerAnswer = _.map(quiz.options,function(opt){
            return {
                name:opt.name,
                score:quizOptionAnswerCount(quiz,opt),
                color:opt.color
            };
        });


        var elem = document.createElementNS("http://www.w3.org/2000/svg", 'svg')
        var svg = d3.select(elem)
                .attr("width","100%")
                .attr("height","100%")
                .attr("viewBox","0 0 "+w+" "+h);
        var x = d3.scaleBand()
                .padding(0.1)
                .range([margin.left,w - margin.right])
                .domain(_.map(scorePerAnswer,"name"));
        var y = d3.scaleLinear()
                .range([margin.top,h - margin.bottom])
                .domain([_.max(_.map(scorePerAnswer,"score")) + 1,0]);
        var xAxis = d3.axisBottom(x);
        var yAxis = d3.axisLeft(y);
        svg.append("g")
            .attr("transform",sprintf("translate(0,%s)",h - margin.bottom))
            .call(xAxis);
        svg.append("g")
            .attr("transform",sprintf("translate(%s,0)",margin.left))
            .call(yAxis);
        var bars = svg.append("g")
                .selectAll("rect")
                .data(scorePerAnswer)
                .enter()
                .append("rect")
                .attr("x",function(d,i){
                    return x(d.name);
                })
                .attr("class","bar")
                .style("fill",function(d,i){
                    return d.color[0];
                })
                .attr("y",function(d){
                    return y(d.score);
                })
                .attr("height",function(d){
                    return h - y(d.score) - margin.bottom;
                })
                .attr("width",x.bandwidth());
        return elem;
    };
    var updateQuizGraph = function(quiz){
        return generateQuizResultsGraph(quiz,640,480);
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
        if (answer.id in quizzes){
            quizResultsGraphs[answer.id] = updateQuizGraph(quizzes[answer.id]);
            if (reRenderActiveGraphFunction != undefined){
                reRenderActiveGraphFunction(answer.id);
            }
        }
    };
    var historyReceivedFunction = function(history){
        try {
            if (history !== undefined && "type" in history && history.type == "history"){
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
            if (possibleQuiz !== undefined && "type" in possibleQuiz && possibleQuiz.type == "quizResponse"){
                actOnQuizResponse(possibleQuiz);
            } else if (possibleQuiz !== undefined && "type" in possibleQuiz && possibleQuiz.type == "quiz"){
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
                    if (quiz !== undefined && "type" in quiz && quiz.type == "quiz"){
                        quizzes[quiz.id] = quiz;
                        quizResultsGraphs[quiz.id] = updateQuizGraph(quiz);
                        if (reRenderActiveGraphFunction != undefined){
                            reRenderActiveGraphFunction(answer.id);
                        }
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
                if (reRenderActiveGraphFunction != undefined){
                    reRenderActiveGraphFunction(answer.id);
                }
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
