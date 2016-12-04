var Submissions = (function(){
	var submissions = [];
	var currentSubmission = {};
	$(function(){
		$("#submissions").click(function(){
			showBackstage("submissions");
		});
		var submissionsCount = $("<span />",{
			id:"submissionCount"
		});
		$("#feedbackStatus").prepend(submissionsCount);
		submissionsCount.click(function(){
			showBackstage("submissions");
		});
		refreshSubmissionCount();	
	})
	var refreshSubmissionCount = function(){
		var submissionCount = _.size(filteredSubmissions());
		if (submissionCount > 0){
			$("#submissionCount").text(sprintf("%s submissions",submissionCount));
		} else {
			$("#submissionCount").text("");
		}
	};
	var filteredSubmissions = function(){
		return _.filter(submissions,filterSubmission);
	};
	var filterSubmission = function(sub){
		return (Conversations.shouldModifyConversation() || sub.author.toLowerCase() == username.toLowerCase());
	};
	var clearState = function(){
		submissions = [];
		currentSubmission = {};
		$("#submissionCount").text("");
	};
	var renderSubmissionsInPlace = function(){
		$("#submissionListing").html(unwrap(filteredSubmissions().map(renderSubmissionSummary)));
		renderCurrentSubmissionInPlace();
		refreshSubmissionCount();
	}
	var renderCurrentSubmissionInPlace = function(){
		$("#currentSubmission").html(renderSubmission(currentSubmission));
	};
	var renderSubmissionSummary = function(submission){
		var rootElem = $("<div />",{
			class:"submissionSummary"
		});
		if ("type" in submission && submission.type == "submission"){
			var imageThumb = $("<image/>",{
				class:"submissionImageThumb",
				src:sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),submission.author,submission.identity)
			}) 
			$("<span/>",{
				text:sprintf("submitted at %s %s", new Date(submission.timestamp).toDateString(),new Date(submission.timestamp).toLocaleTimeString()),
			}).appendTo(rootElem);
			$("<div/>",{
				type:"button",
				class:"viewSubmissionButton",
				id:sprintf("viewSubmissionButton_%s",submission.identity),
			}).on("click",function(){
				currentSubmission = submission;
				renderCurrentSubmissionInPlace();		
			}).append(imageThumb).appendTo(rootElem);	
		}
		return rootElem;
	};
	var renderSubmission = function(submission){
		var rootElem = $("<div />");
		if ("type" in submission && submission.type == "submission"){
			$("<div/>",{
				text:sprintf("submitted at %s",submission.timestamp),
				class:"submissionContainer",
				id:sprintf("submission_%s",submission.identity)
			}).appendTo(rootElem);
			$("<image/>",{
					class:"submissionImage",
				src:sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),submission.author,submission.identity)
			}).appendTo(rootElem);
			if (Conversations.shouldModifyConversation()){
				$("<input/>",{
					type:"button",
					class: "toolbar",
					value:"Display Submission on next page"
				}).on("click",function(){
						addSubmissionSlideToConversationAtIndex(Conversations.getCurrentConversationJid(),Conversations.getCurrentSlide().index + 1,submission.identity); 
				}).appendTo(rootElem);
			}
		}
		return rootElem;
	};
	var historyReceivedFunction = function(history){
		try {
			if ("type" in history && history.type == "history"){
				_.forEach(history.submissions,doStanzaReceivedFunction);
				renderSubmissionsInPlace();
			}
		}
		catch (e){
			console.log("Submissions.historyReceivedFunction",e);
		}
	};
	var stanzaReceivedFunction = function(input){
		doStanzaReceivedFunction(input);
		renderSubmissionsInPlace();
	};
	var doStanzaReceivedFunction = function(possibleSubmission){
		try {
			if ("type" in possibleSubmission && possibleSubmission.type == "submission" && "target" in possibleSubmission && possibleSubmission.target == "submission"){
				if (filterSubmission(possibleSubmission)){
					submissions.push(possibleSubmission);
				}
			}
		}
		catch (e){
			console.log("Submissions.stanzaReceivedFunction",e);
		}
	};
	Progress.onConversationJoin["Submissions"] = clearState;
	Progress.historyReceived["Submissions"] = historyReceivedFunction;
	Progress.stanzaReceived["Submissions"] = stanzaReceivedFunction;
	return {
		getAllSubmissions:function(){return filteredSubmissions();},
		getCurrentSubmission:function(){return currentSubmission;}
	};
})();
