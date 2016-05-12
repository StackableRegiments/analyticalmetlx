var Submissions = (function(){
    var submissionSummaryListing = {};
    var submissionSummaryTemplate = {};
    var currentSubmissionTemplate = {};
    var currentSubmissionContainer = {};
    var submissions = [];
    var currentSubmission = {};
    $(function(){
        submissionSummaryListing = $("#submissionListing");
        submissionSummaryTemplate = submissionSummaryListing.find(".submissionSummary").clone();
        currentSubmissionContainer = $("#currentSubmission");
        currentSubmissionTemplate = currentSubmissionContainer.find(".submissionContainer").clone();
        submissionSummaryListing.empty();
        $("#submissions").click(function(){
            showBackstage("submissions");
        });
        var submissionsCount = $("<div />",{
            id:"submissionCount",
            class:"icon-txt"
        });
        $("#feedbackStatus").prepend(submissionsCount);
        submissionsCount.click(function(){
            showBackstage("submissions");
        });
        refreshSubmissionCount();
    });
    var refreshSubmissionCount = function(){
        var submissionCount = _.size(filteredSubmissions());
        if (submissionCount > 0){
            if(submissionCount == 1){
                $("#submissionCount").text(sprintf("%s submission",submissionCount));
                $("#dedicatedSubmissionCount").text("This conversation has 1 submission");
            } else{
                $("#submissionCount").text(sprintf("%s submissions",submissionCount));
                $("#dedicatedSubmissionCount").text(sprintf("This conversation has %s submissions",submissionCount));
            }
        } else {
            $("#submissionCount").text("");
            $("#dedicatedSubmissionCount").text(sprintf("This conversation has %s submissions",submissionCount));
        }
    };
    var filteredSubmissions = function(){
        return _.filter(submissions,filterSubmission);
    };
    var filterSubmission = function(sub){
        return (Conversations.shouldModifyConversation() || sub.author.toLowerCase() == UserSettings.getUsername().toLowerCase());
    };
    var clearState = function(){
        submissions = [];
        currentSubmission = {};
        $("#submissionCount").text("");
    };
    var renderSubmissionsInPlace = function(){
        submissionSummaryListing.empty();
        filteredSubmissions().map(function(submission){
            renderSubmissionSummary(submission);
        })
        /*
         $("#submissionListing").html(unwrap(filteredSubmissions().map(renderSubmissionSummary)));
         */
        renderCurrentSubmissionInPlace();
        refreshSubmissionCount();
    }
    var renderCurrentSubmissionInPlace = function(){
        currentSubmissionContainer.html(renderSubmission(currentSubmission));
    };
    var renderSubmissionSummary = function(submission){
        if ("type" in submission && submission.type == "submission"){
            var rootElem = submissionSummaryTemplate.clone();
            submissionSummaryListing.append(rootElem);
            rootElem.find(".submissionDescription").text(sprintf("submitted by %s at %s %s", submission.author, new Date(submission.timestamp).toDateString(),new Date(submission.timestamp).toLocaleTimeString()));
            rootElem.find(".submissionImageThumb").attr("src",sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),submission.author,submission.identity));
            rootElem.find(".viewSubmissionButton").attr("id",sprintf("viewSubmissionButton_%s",submission.identity)).on("click",function(){
                currentSubmission = submission;
                renderCurrentSubmissionInPlace();
            });
        }
    };
    var renderSubmission = function(submission){
        var rootElem = $("<div />");
        if ("type" in submission && submission.type == "submission"){
            rootElem = currentSubmissionTemplate.clone();
            rootElem.attr("id",sprintf("submission_%s",submission.identity))
            rootElem.find(".submissionDescription").text(sprintf("submitted by %s at %s",submission.author, submission.timestamp));
            rootElem.find(".submissionImage").attr("src",sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),submission.author,submission.identity));
            if (Conversations.shouldModifyConversation()){
                rootElem.find(".displaySubmissionOnNextSlide").on("click",function(){
                    addSubmissionSlideToConversationAtIndex(Conversations.getCurrentConversationJid(),Conversations.getCurrentSlide().index + 1,submission.identity);
                });
            } else {
                rootElem.find("submissionTeacherControls").hide();
            }
        }
        return rootElem;
    };
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
                }
            }
            if (!skipRender){
                renderSubmissionsInPlace();
            }
        }
        catch (e){
            console.log("Submissions.stanzaReceivedFunction",e);
        }
    };

    Progress.onConversationJoin["Submissions"] = clearState;
    Progress.historyReceived["Submissions"] = historyReceivedFunction;
    return {
        getAllSubmissions:function(){return filteredSubmissions();},
        getCurrentSubmission:function(){return currentSubmission;},
        processSubmission:onSubmissionReceived
    };
})();
