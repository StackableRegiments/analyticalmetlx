var Submissions = (function(){
    var submissions = [];
    var submissionsDatagrid;
    var insertButtonTemplate = {};
    var reRenderDatagrid = function(){
        if(submissionsDatagrid && submissionsDatagrid.length > 0){
            if( WorkQueue != undefined ) {
                WorkQueue.enqueue(function () {
                    submissionsDatagrid.jsGrid("loadData");
                    var sortObj = submissionsDatagrid.jsGrid("getSorting");
                    if ("field" in sortObj) {
                        submissionsDatagrid.jsGrid("sort", sortObj);
                    }
                });
            }
        }
    };
    $(function(){
        var setupSubmissions = function(){
            submissionsDatagrid = $("#submissionsDatagrid");
            insertButtonTemplate = submissionsDatagrid.find(".insertOnNextSlideButtonContainer");
            submissionsDatagrid.empty();
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

            var cellStyle = "max-height:100px;max-width:100%;cursor:zoom-in";
            var gridFields = [
                {
                    name:"url",
                    type:"text",
                    title:"Preview",
                    readOnly:true,
                    sorting:false,
                    itemTemplate:function(thumbnailUrl,submission){
                        var url = sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),submission.author,submission.identity);
                        var img = $("<img/>",{src:url,class:"submissionThumbnail",style:cellStyle}).on("click",function(){
                            var url = sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),submission.author,submission.identity);
                            var title = sprintf("Submission from %s at %s on page %s",submission.author,new Date(submission.timestamp),submission.slide);
                            $.jAlert({
                                title:title,
                                closeOnClick:true,
                                width:"90%",
                                content:$("<img/>",{src:url})[0].outerHTML
                            });
                        });
                        return img;
                    }
                },
                {name:"slide",type:"number",title:"Page",readOnly:true},
                {name:"timestamp",type:"dateField",title:"When",readOnly:true},
                {name:"author",type:"text",title:"Who",readOnly:true}
            ];
            var teacherFields = [
                {
                    name:"identity",
                    type:"text",
                    title:"actions",
                    readOnly:true,
                    sorting:false,
                    itemTemplate:function(identity,submission){
                        var rootElem = insertButtonTemplate.clone();
                        rootElem.find(".insertOnNextSlideButton").on("click",function(){
                            addSubmissionSlideToConversationAtIndex(Conversations.getCurrentConversationJid(),Conversations.getCurrentSlide().index + 1,submission.identity);
                        });
                        return rootElem;
                    }
                }
            ];
            if (Conversations.shouldModifyConversation()){
                gridFields = gridFields.concat(teacherFields);
            }
            submissionsDatagrid.jsGrid({
                width:"100%",
                height:"auto",
                inserting:false,
                editing:false,
                sorting:true,
                paging:true,
                noDataContent: "No submissions",
                controller: {
                    loadData: function(filter){
                        if ("sortField" in filter){
                            var sorted = _.sortBy(filteredSubmissions(),function(sub){
                                return sub[filter.sortField];
                            });
                            if ("sortOrder" in filter && filter.sortOrder == "desc"){
                                sorted = _.reverse(sorted);
                            }
                            return sorted;
                        } else {
                            return filteredSubmissions();
                        }
                    }
                },
                pageLoading:false,
                fields: gridFields
            });
            submissionsDatagrid.jsGrid("sort",{
                field:"timestamp",
                order:"desc"
            });
            renderSubmissionsInPlace();
        };
        var loadAfterConversationsLoaded = function(){
            if (Conversations.inConversation()){
                setupSubmissions();
            } else {
                _.delay(loadAfterConversationsLoaded,500);
            }
        };
        loadAfterConversationsLoaded();
    });
    var filteredSubmissions = function(){
        return _.filter(submissions,filterSubmission);
    };
    var filterSubmission = function(sub){
        return (Conversations.shouldModifyConversation() || sub.author.toLowerCase() == UserSettings.getUsername().toLowerCase());
    };
    var clearState = function(){
        submissions = [];
    };
    var renderSubmissionsInPlace = function(){
        reRenderDatagrid();
    }
    var historyReceivedFunction = function(history){
        try {
            if ("type" in history && history.type == "history"){
                clearState();
                _.forEach(history.submissions,function(submission){onSubmissionReceived(submission,true);});
                renderSubmissionsInPlace();
            }
        }
        catch (e){
            console.log("Submissions.historyReceivedFunction",e);
        }
    };
    var onSubmissionReceived = function(submission,skipRender){
        try {
            if ("target" in submission && submission.target == "submission"){
                if (filterSubmission(submission)){
                    submissions.push(submission);
                    if (!skipRender){
                        renderSubmissionsInPlace();
                    }
                }
            }
        }
        catch (e){
            console.log("Submissions.stanzaReceivedFunction",e);
        }
    };

    Progress.onConversationJoin["Submissions"] = clearState;
    Progress.historyReceived["Submissions"] = historyReceivedFunction;
    var clientSideSubmissionFunc = function(afterFunc){
        WorkQueue.pause();
				afterFunc = afterFunc || function(succeeded){
					if (succeeded){
						successAlert("Submission sent","Your submission has been sent to the instructor");
					} else {
						errorAlert("Submission failed","This image cannot be processed, either because of image protocol issues or because it exceeds the maximum image size.");
					}
				}
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
        var currentSlide = Conversations.getCurrentSlide().id;
        var currentConversation = Conversations.getCurrentConversation().jid;
        var title = sprintf("submission%s%s.jpg",username,t.toString());
        var identity = sprintf("%s:%s:%s",currentConversation,title,t);
        var url = sprintf("/uploadDataUri?jid=%s&filename=%s",currentConversation.toString(),encodeURI(identity));
        $.ajax({
            url: url,
            type: 'POST',
            success: function(e){
                var newIdentity = $(e).find("resourceUrl").text();
                var submissionStanza = {
                    audiences:[],
                    author:username,
                    blacklist:[],
                    identity:identity,
                    privacy:Privacy.getCurrentPrivacy(),
                    slide:currentSlide,
                    target:"submission",
                    timestamp:t,
                    title:title,
                    type:"submission",
                    url:newIdentity
                };
                console.log(submissionStanza);
                sendStanza(submissionStanza);
                WorkQueue.gracefullyResume();
								afterFunc(true);
            },
            error: function(e){
                console.log(e);
								afterFunc(false);
								WorkQueue.gracefullyResume();
            },
            data: imageData,
            cache: false,
            contentType: false,
            processData: false
        });
    };
    var serverSideSubmissionFunc = function(){
        if ("Conversations" in window){
            var currentConversation = Conversations.getCurrentConversation();
            var currentSlide = Conversations.getCurrentSlideJid();
            if("jid" in currentConversation){
                submitScreenshotSubmission(currentConversation.jid.toString(),currentSlide);
            }
        }
    };
    return {
        getAllSubmissions:function(){return filteredSubmissions();},
        getCurrentSubmission:function(){return currentSubmission;},
        processSubmission:onSubmissionReceived,
        sendSubmission:clientSideSubmissionFunc,
        requestServerSideSubmission:serverSideSubmissionFunc,
        reRender:reRenderDatagrid
    };
})();
