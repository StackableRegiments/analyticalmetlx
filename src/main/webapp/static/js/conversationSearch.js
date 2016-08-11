var Conversations = (function(){
	var username = "";
	var userGroups = [];
	var conversationTemplate = {};
	var conversationsListing = undefined;
	var importTemplate = {};
	var importListing = undefined;

	var currentQuery = "";
	var currentSearchResults = [];
	var currentImports = [];
	var searchBox = {};

	$(function(){
			$('#activeImportsListing').hide();
			$("#importConversationContainer").hide();
			$("#showImportConversationWorkflow").click(function(){
					$("#importConversationContainer").show();
			});
			$('#importConversationInputElement').fileupload({
					dataType: 'json',
					add: function (e,data) {
							$('#importConversationProgress').css('width', '0%');
							$('#importConversationProgressBar').show();
							$('#activeImportsListing').show();
							data.submit();
					},
					progressall: function (e, data) {
							var progress = parseInt(data.loaded / data.total * 100, 10) + '%';
							$('#importConversationProgressBar').css('width', progress);
					},
					done: function (e, data) {
							$.each(data.files, function (index, file) {
									$('<p/>').text(file.name).appendTo(document.body);
							});
							$('#importConversationProgress').fadeOut();
					}
			});
			conversationsListing = $("#conversationContainerListing");
			conversationTemplate = conversationsListing.find(".conversationContainer").clone();
			conversationsListing.empty();
			importListing = $("#activeImportsListing");
			importTemplate = importListing.find(".importContainer").clone();
			importListing.empty();
			var searchBoxContainer = $("#conversationSearchBox");
			searchBox = $("<input/>",{
				type:"text",
				val:currentQuery				
			});
			searchBoxContainer.append(searchBox);
			var qFunc = function(){
				var q = $(this).val();
				query = q;
				searchFunc(q);
			};
			searchBox.on("blur",qFunc);
			var createConversationButton = $("#createConversationButton");
			createConversationButton.on("click",function(){
				var title = sprintf("%s at %s",username,new Date().toString()); 
				createFunc(title);
			});
	});

	var shouldModifyConversation = function(details){
		return (details.author == username);
	};
	var shouldDisplayConversation = function(details){
		return (details.subject != "deleted" && (details.author == username || _.some(userGroups,function(g){ 
			return g.toLowerCase().trim() == details.subject.toLowerCase().trim();
		})));
	};	
	var constructConversation = function(details){
		var rootElem = conversationTemplate.clone();
		rootElem.find(".conversationAnchor").attr("href",sprintf("/board?conversationJid=%s",details.jid));
		rootElem.find(".conversationTitle").text(details.title);
		rootElem.find(".conversationAuthor").text(details.author);
		rootElem.find(".conversationJid").text(details.jid);
		if (shouldModifyConversation(details)){
			rootElem.find(".editConversationLink").attr("href",sprintf("/editConversation?conversationJid=%s",details.jid));
		} else {
			rootElem.find(".conversationEditingContainer").remove();
		};
		return rootElem;
	};
	var constructImport = function(importDesc){
		var rootElem = importTemplate.clone();
		rootElem.find(".importId").text(importDesc.id);
		rootElem.find(".importName").text(importDesc.name);
		rootElem.find(".importAuthor").text(importDesc.author);
		var impProgContainer = rootElem.find(".importProgressContainer");
		var impResContainer = rootElem.find(".importResultContainer");
		if ("result" in importDesc){
			if ("a" in importDesc.result){
				// Left(e)
				impResContainer.find(".importError").text(importDesc.a.toString()); 
				impResContainer.find(".importSuccess").remove();
				impProgContainer.remove();
			} else if ("b" in importDesc.result){
				// Right(conv)
				impResContainer.find(".importSuccess").attr("href",sprintf("/board?conversationJid=%s",importDesc.result.b.jid));
				impResContainer.find(".importError").remove();
				impProgContainer.remove();
			}
		} else {
			// still importing
			var ovProg = rootElem.find(".importOverallProgressContainer");
			ovProg.find(".importProgressDescriptor").text(importDesc.overallProgress.name);
			ovProg.find(".importProgressProgressBar").css({"width":sprintf("%s%%",(importDesc.overallProgress.numerator / importDesc.overallProgress) * 100)});
			var stgProg = rootElem.find(".importStageProgressContainer");
			stgProg.find(".importProgressDescriptor").text(importDesc.stageProgress.name);
			stgProg.find(".importProgressProgressBar").css({"width":sprintf("%s%%",(importDesc.stageProgress.numerator / importDesc.stageProgress) * 100)});
			impResContainer.remove();
		}
		return rootElem;
	};

	var reRender = function(){
		console.log("reRender:",currentQuery,currentSearchResults,currentImports);
		var convs = _.map(currentSearchResults,constructConversation);
		conversationsListing.html(convs);
		var imports = _.map(currentImports,constructImport);
		importListing.html(imports);
		var convCount = sprintf("%s result%s",convs.length,convs.length == 1 ? "" : "s");
		$("#conversationListing").find(".aggregateContainer").find(".count").text(convCount);
	};
	var searchFunc = function(query){
		currentQuery = query;
		getSearchResult(query); //injected from Lift		
	};
	var createFunc = function(title){
		createConversation(title); //injected from Lift
	};
	var receiveUsernameFunc = function(user){
		username = user;
	};
	var receiveUserGroupsFunc = function(groups){
		userGroups = groups;
	};
	var receiveConversationDetailsFunc = function(details){
		currentSearchResults = _.map(currentSearchResults,function(conv){
			if (conv.jid == details.jid){
				return details;
			} else {
				return conv;
			}
		});
		reRender();
	};
	var receiveSearchResultsFunc = function(results){
		currentSearchResults = results;
		reRender();
	};
	var receiveNewConversationDetailsFunc = function(details){
		currentSearchResults.push(details);
		reRender();
	};
	var receiveImportDescriptionFunc = function(importDesc){
		console.log("importDesc",importDesc);
		currentImports = _.filter(currentImports,function(id){return id.id != importDesc.id;});
		currentImports.push(importDesc);
		reRender();
	};
	var receiveImportDescriptionsFunc = function(importDescs){
		console.log("importDescs",importDescs);
		currentImports = importDescs;
		reRender();
	};
	var receiveQueryFunc = function(q){
		query = q;
		searchBox.val(q);
		reRender();
	};
	var getConversationListingFunc = function(){
		return listing;
	};
	var getImportListingFunc = function(){
		return currentImports;
	};
	var getQueryFunc = function(){
		return query;
	};
	return {
		receiveUsername:receiveUsernameFunc,
		receiveUserGroups:receiveUserGroupsFunc,
		receiveConversationDetails:receiveConversationDetailsFunc,
		receiveSearchResults:receiveSearchResultsFunc,
		receiveNewConversationDetails:receiveNewConversationDetailsFunc,	
		receiveImportDescription:receiveImportDescriptionFunc,
		receiveImportDescriptions:receiveImportDescriptionsFunc,
		receiveQuery:receiveQueryFunc,
		getConversationListing:getConversationListingFunc,
		getImportListing:getImportListingFunc,
		getQuery:getQueryFunc,
		search:searchFunc,
		create:createFunc
	};
})();

function serverResponse(response){ //invoked by Lift
    console.log("serverResponse:",response);
}
function receiveUsername(username){ //invoked by Lift
	Conversations.receiveUsername(username);
}
function receiveUserGroups(userGroups){ //invoked by Lift
	Conversations.receiveUserGroups(userGroups);
}
function receiveConversationDetails(details){ //invoked by Lift
	Conversations.receiveConversationDetails(details);
}
function receiveConversations(conversations){ //invoked by Lift
	Conversations.receiveSearchResults(conversations);
}
function receiveNewConversationDetails(details){ //invoked by Lift
	Conversations.receiveNewConversationDetails(details);
}
function receiveImportDescription(importDesc){ //invoked by Lift
	Conversations.receiveImportDescription(importDesc);
}
function receiveImportDescriptions(importDescs){ //invoked by Lift
	Conversations.receiveImportDescriptions(importDescs);
}
function receiveQuery(query){ //invoked by Lift
	Conversations.receiveQuery(query);
}
