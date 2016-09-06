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

    var conversationsDataGrid = undefined;

    var onlyMyConversations = false;
    var includeDeleted = false;
    var dataGridItems = [];

    $(function(){
        var DateField = function(config){
            jsGrid.Field.call(this,config);
        };
        var EditConversationField = function(config){
            jsGrid.Field.call(this,config);
        };
        var ConversationSharingField = function(config){
            jsGrid.Field.call(this,config);
        };
        var JoinConversationField = function(config){
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
        $("#onlyMyConversations").click(function(){
            onlyMyConversations = $(this).is(":checked");
            reRender();
        });
        $("#includeDeleted").click(function(){
            includeDeleted = $(this).is(":checked");
            reRender();
        });
        EditConversationField.prototype = new jsGrid.Field({
            sorter: function(a,b){
                return 0;
            },
            itemTemplate: function(cell,details){
                if ("importing" in details){
                    var n = details.stageProgress.numerator;
                    var d = details.stageProgress.denominator;
                    var m = details.stageProgress.name;
                    return $("<div/>").append($("<progress/>",{
                        value:n,
                        max:d,
                        text:sprintf("%s out of %s",n,d)
                    })).append($("<div/>",{
                        text:m
                    }));
                } else {
                    if (shouldModifyConversation(details)){
                        return $("<a/>",{
                            href:sprintf("/editConversation?conversationJid=%s",details.jid),
                            text:"Edit"
                        });
                    } else {
                        return "";
                    }
                }
            },
            insertTemplate: function(i){return ""},
            editTemplate: function(i){return ""},
            insertValue: function(){return ""},
            editValue: function(){return ""}
        });
        ConversationSharingField.prototype = new jsGrid.Field({
            sorter: function(a,b){
                return 0;
            },
            itemTemplate: function(cell,details){
                if ("importing" in details){
                    var n = details.overallProgress.numerator;
                    var d = details.overallProgress.denominator;
                    var m = details.overallProgress.name;
                    return $("<div/>").append($("<progress/>",{
                        value:n,
                        max:d,
                        text:sprintf("%s out of %s",n,d)
                    })).append($("<div/>",{
                        text:m
                    }));
                } else {
                    return cell;
                }
            },
            insertTemplate: function(i){return ""},
            editTemplate: function(i){return ""},
            insertValue: function(){return ""},
            editValue: function(){return ""}
        });


        jsGrid.fields.dateField = DateField;
        jsGrid.fields.editConversationField = EditConversationField;
        jsGrid.fields.joinConversationField = JoinConversationField;
        jsGrid.fields.conversationSharingField = ConversationSharingField;

        conversationsDataGrid = $("#conversationsDataGrid");

        conversationsDataGrid.jsGrid({
            width:"100%",
            height:"auto",
            inserting: false,
            editing: false,
            //filtering:true,
            sorting: true,
            paging: true,
            //                          pageSize:10,
            //                          pageButtonCount:5,
            noDataContent: "No conversations match your query",
            rowClick:function(obj){
                if ("jid" in obj.item && !("importing" in obj.item)){
                    window.location.href = sprintf("/board?conversationJid=%s&unique=true",obj.item.jid);
                }
            },
            controller: {
                loadData: function(filter){
                    if ("sortField" in filter){
                        var sorted = _.sortBy(dataGridItems,function(a){
                            return a[filter.sortField];
                        });
                        if(onlyMyConversations){
                            sorted = _.filter(sorted,function(a){
                                return a.author == username;
                            });
                        }
                        if ("sortOrder" in filter && filter.sortOrder == "desc"){
                            sorted = _.reverse(sorted);
                        }
                        return sorted;
                    } else {
                        return dataGridItems;
                    }
                }/*,
                  insertItem: $.noop,
                  updateItem: $.noop,
                  deleteItem: $.noop
                  */
            },
            pageLoading:false,
            fields: [
                { name:"lifecycle", type:"text", title:"Lifecycle", readOnly:true, itemTemplate:function(lifecycle,conv){
                    var elem = $("<span/>");
                    switch (lifecycle) {
                    case "deleted":
                        elem.addClass("deletedConversationTag").text("archived");
                        break;
                    case "new":
                        elem.addClass("newConversationTag").text("new");
                        break;
                    default:
                        elem.text("");
                        break;
                    }
                    return elem;
                }},
                { name:"title", type:"text", title:"Title", readOnly:true },
                {name:"creation",type:"dateField",title:"Created"},
                {name:"author",type:"text",title:"Author",readOnly:true},
                {name:"subject",type:"conversationSharingField",title:"Sharing",readOnly:true},
                {name:"edit",type:"editConversationField",title:"Edit",sorting:false,width:30,css:"gridAction"}
            ]
        });
        conversationsDataGrid.jsGrid("sort",{
            field:"creation",
            order:"desc"
        });
        $('#activeImportsListing').hide();
        $("#importConversationInputElementContainer").hide();
        $("#showImportConversationWorkflow").click(function(){
            $('#importConversationInputElement').click();
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
        searchBox.on("keydown",function(e){
            var q = $(this).val();
            query = q;
            if (e.keyCode == 13){
                searchFunc(q);
            }
        });
        var createConversationButton = $("#createConversationButton");
        createConversationButton.on("click",function(){
            var title = sprintf("%s at %s",username,new Date().toString());
            createFunc(title);
        });
        var searchButton = $("#searchButton");
        searchButton.on("click",function(){
            searchFunc(query);
        });
    });
    var shouldModifyConversation = function(details){
        return (details.author == username);
    };
    var shouldDisplayConversation = function(details){
				var subject = details.subject.toLowerCase().trim();
				var title = details.title.toLowerCase().trim();
				var author = details.author;
        return ((currentQuery == author || title.indexOf(currentQuery) > -1) && (subject != "deleted" || (includeDeleted && author == username)) && (author == username || _.some(userGroups,function(g){
					return (g.ouType == "special" && g.name == "superuser") || g.name.toLowerCase().trim() == subject;
        })));
    };

    var newTag = function(title){
        var tag = $("<span/>");
        tag.append($("<span/>",{
            class:"newConversation",
            text: "new"
        }));
        tag.append($("<span/>",{
            html:title
        }));
        console.log("newTag:",tag.html().toString());
        return tag;
    };

    var reRender = function(){
        var mutatedImports = _.filter(_.map(currentImports,function(cid){
            if ("result" in cid && "a" in cid.result){
                return {
                    importing:true,
                    title:sprintf("%s - %s - %s","import failure",cid.name,cid.a),
                    author:cid.author,
                    jid:cid.id,
                    newConversation:true,
                    creation:new Date().getTime(),
                    overallProgress:cid.overallProgress,
                    stageProgress:cid.stageProgress
                };
            } else if ("result" in cid && "b" in cid.result){
                var conv = cid.result.b
                if (!("creation" in conv) && "created" in conv && _.isNumber(conv.created)){
                    conv.creation = conv.created;
                    conv.created = new Date(conv.creation);
                };
                conv.newConversation = true;
                return conv;
            } else {
                return {
                    lifecycle:"new",
                    importing:true,
                    title:cid.name,
                    author:cid.author,
                    jid:cid.id,
                    newConversation:true,
                    creation:new Date().getTime(),
                    overallProgress:cid.overallProgress,
                    stageProgress:cid.stageProgress
                };
            }
        }),function(cid){
            return (("importing" in cid && cid.importing == true) || !_.some(currentSearchResults,function(conv){return conv.jid == cid.jid;}));
        });
        var newThreshold = new Date().getTime() - (30 * 60 * 1000); // last 30 minutes
        dataGridItems = _.map(_.concat(mutatedImports,_.filter(currentSearchResults,shouldDisplayConversation)),function(conv){
            if (conv.subject == "deleted"){
                conv.lifecycle = "deleted"
            } else if (conv.creation > newThreshold){
                conv.lifecycle = "new"
            } else {
                conv.lifecycle = "available"
            }
            return conv;
        });
        if (conversationsDataGrid != undefined){
            conversationsDataGrid.jsGrid("loadData");
            var sortObj = conversationsDataGrid.jsGrid("getSorting");
            if ("field" in sortObj){
                conversationsDataGrid.jsGrid("sort",sortObj);
            }
        }
        var convs = dataGridItems;//currentSearchResults;
        var convCount = sprintf("%s result%s",convs.length,convs.length == 1 ? "" : "s");
        $("#conversationListing").find(".aggregateContainer").find(".count").text(convCount);
    };
    var searchFunc = function(query){
        currentQuery = query.toLowerCase().trim();
        getSearchResult(currentQuery); //injected from Lift
    };
    var createFunc = function(title){
        createConversation(title); //injected from Lift
    };
    var receiveUsernameFunc = function(user){
        username = user;
    };
		var getUsernameFunc = function(){
			return username;
		};
    var receiveUserGroupsFunc = function(groups){
        userGroups = groups;
    };
		var getUserGroupsFunc = function(){
			return userGroups
		};
    var receiveConversationDetailsFunc = function(details){
        currentSearchResults = _.uniq(_.concat([details],_.filter(currentSearchResults,function(conv){return conv.jid != details.jid;})));
				console.log("currentSearchResults:",currentSearchResults,details);
        reRender();
    };
    var receiveSearchResultsFunc = function(results){
        currentSearchResults = results;
        reRender();
    };
    var receiveNewConversationDetailsFunc = function(details){
        details.newConversation = true;
        currentSearchResults.push(details);
        reRender();
    };
    var receiveImportDescriptionFunc = function(importDesc){
        currentImports = _.filter(currentImports,function(id){return id.id != importDesc.id;});
        currentImports.push(importDesc);
        reRender();
    };
    var receiveImportDescriptionsFunc = function(importDescs){
        currentImports = importDescs;
        reRender();
    };
    var receiveQueryFunc = function(q){
        currentQuery = q.toLowerCase().trim();
        searchBox.val(currentQuery);
        reRender();
    };
    var getConversationListingFunc = function(){
        return dataGridItems;
    };
    var getImportListingFunc = function(){
        return currentImports;
    };
    var getQueryFunc = function(){
        return currentQuery;
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
				getUsername:getUsernameFunc,
				getUserGroups:getUserGroupsFunc,
        search:searchFunc,
        create:createFunc
    };
})();

function serverResponse(response){ //invoked by Lift
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
