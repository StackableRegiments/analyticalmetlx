var Query = function () {

    var resultsTemplate = {};
    var resultsListing = undefined;

    var currentQuery = "";
    var currentSearchResults = [];
    var searchBox = {};

    var resultsDataGrid = undefined;
    var dataGridItems = [];

    var searchPermitted = false;

    $(function () {
        resultsDataGrid = $("#resultsDataGrid");

        resultsDataGrid.jsGrid({
            width: "100%",
            height: "auto",
            inserting: false,
            editing: false,
            //filtering:true,
            sorting: true,
            paging: true,
            //                          pageSize:10,
            //                          pageButtonCount:5,
            noDataContent: "No results for your query",
            controller: {
                loadData: function (filter) {
                    if ("sortField" in filter) {
                        var sorted = _.sortBy(dataGridItems, function (a) {
                            return a[filter.sortField];
                        });
                        if ("sortOrder" in filter && filter.sortOrder == "desc") {
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
            pageLoading: false,
            fields: [
                {name: "lifecycle", type: "text", title: "Lifecycle", readOnly: true},
                {name: "title", type: "text", title: "Title", readOnly: true},
                {name: "creation", type: "dateField", title: "Created"},
                {name: "author", type: "text", title: "Author", readOnly: true}
            ]
        });
        resultsDataGrid.jsGrid("sort", {
            field: "creation",
            order: "desc"
        });
        resultsListing = $("#conversationContainerListing");
        resultsTemplate = resultsListing.find(".conversationContainer").clone();
        resultsListing.empty();
        var searchBoxContainer = $("#conversationSearchBox");
        searchBox = $("<input/>", {
            type: "text",
            val: getQueryFunc()
        });
        searchBoxContainer.append(searchBox);
        var searchBoxChangeFunc = function (e) {
            currentQuery = $(this).val().toLowerCase().trim();
            if (e.keyCode == 13 && searchPermitted) {
                searchFunc(getQueryFunc());
            }
        };
        searchBox.on("keyup", searchBoxChangeFunc);
        permitOneSearch();
    });

    var permitOneSearch = function () {
        searchPermitted = true;
        $("#searchButton").off("click").attr("disabled", false).on("click", function () {
            searchFunc(getQueryFunc());
        });
    };

    var reRender = function(){
        dataGridItems = _.uniqBy(_.reverse(_.orderBy(_.map(candidates,function(conv){
            if (conv.subject == "deleted"){
                conv.lifecycle = "deleted";
            } else if (conv.creation > newThreshold){
                conv.lifecycle = "new";
            } else {
                conv.lifecycle = "available";
            }
            return conv;
        }),"lastAccessed")),"jid");
        console.log("rendering",dataGridItems);
        if (resultsDataGrid != undefined){
            resultsDataGrid.jsGrid("loadData");
            var sortObj = resultsDataGrid.jsGrid("getSorting");
            if ("field" in sortObj){
                resultsDataGrid.jsGrid("sort",sortObj);
            }
        }
        var results = dataGridItems;
        var resultCount = sprintf("%s result%s",results.length,results.length == 1 ? "" : "s");
        $("#conversationListing").find(".aggregateContainer").find(".count").text(resultCount);
    };

    var searchFunc = function (query) {
        $("#searchButton").attr("disabled", true).off("click");
        searchPermitted = false;
        currentQuery = query.toLowerCase().trim();
        updateQueryParams();
        getSearchResult(getQueryFunc()); //injected from Lift
    };

    var receiveSearchResultsFunc = function(results){
        console.log("receiveSearchResults",results);
        currentSearchResults = results;
        permitOneSearch();
        updateQueryParams();
        reRender();
    };

    var updateQueryParams = function () {
        console.log("updating queryparams:", getQueryFunc(), window.location);
        if (window != undefined && "history" in window && "pushState" in window.history) {
            var l = window.location;
            var q = getQueryFunc();
            var newUrl = sprintf("%s//%s%s", l.protocol, l.host, l.pathname);
            if (q != undefined) {
                newUrl = sprintf("%s?query=%s", newUrl, q);
            }
            window.history.replaceState({
                path: newUrl,
                url: newUrl
            }, newUrl, newUrl);
        }
    };

    var receiveQueryFunc = function (q) {
        currentQuery = q.toLowerCase().trim();
        updateQueryParams();
        searchBox.val(getQueryFunc());
        reRender();
    };

    var getResultListingFunc = function(){
        return dataGridItems;
    };

    var getQueryFunc = function () {
        return currentQuery;
    };

    return {
        receiveSearchResults: receiveSearchResultsFunc,
        receiveQuery: receiveQueryFunc,
        getResultListing: getResultListingFunc,
        getQuery: getQueryFunc,
        search: searchFunc
    };
};

// $(Query);

function serverResponse(response) { //invoked by Lift
}

function receiveResults(results) { //invoked by Lift
    Query.receiveSearchResults(results);
}

function receiveQuery(query) { //invoked by Lift
    Query.receiveQuery(query);
}
