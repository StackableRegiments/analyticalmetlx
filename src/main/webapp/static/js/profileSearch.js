var ProfileSearch = (function(){
    var username = "";
    var userGroups = [];
    var profileTemplate = {};
    var profilesListing = undefined;

		var profiles = {};
		var myProfile = {};
    var currentQuery = "";
    var currentSearchResults = [];
    var searchBox = {};

    var profilesDataGrid = undefined;

    var dataGridItems = [];

    var searchPermitted = false;

		MeTLBus.subscribe("receiveProfiles","profileSearch",function(newProfiles){
			profiles = _.merge(profiles,newProfiles);
		});
		MeTLBus.subscribe("receiveCurrentProfile","profileSearch",function(profile){
			myProfile = profile;
		});
    $(function(){
        var DateField = function(config){
            jsGrid.Field.call(this,config);
        };
        DateField.prototype = new jsGrid.Field({
            sorter: function(a,b){
                return new Date(a) - new Date(b);
            },
            itemTemplate: function(i){
		// console.log("New date",i);
                return moment(i).format('MMM Do YYYY, h:mm a');
            },
            insertTemplate: function(i){return ""},
            editTemplate: function(i){return ""},
            insertValue: function(){return ""},
            editValue: function(){return ""}
        });

        jsGrid.fields.dateField = DateField;

        profilesDataGrid = $("#profilesDataGrid");

        profilesDataGrid.jsGrid({
            width:"100%",
            height:"auto",
            inserting: false,
            editing: false,
            //filtering:true,
            sorting: true,
            paging: true,
            //                          pageSize:10,
            //                          pageButtonCount:5,
            noDataContent: "No profiles match your query",
            rowClick:function(obj){
                if ("id" in obj.item){
                    window.location.href = sprintf("/profileSummary?profileId=%s&unique=true",obj.item.jid);
                }
            },
            controller: {
                loadData: function(filter){
                    if ("sortField" in filter){
                        var sorted = _.sortBy(dataGridItems,function(a){
                            return a[filter.sortField];
                        });
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
                {name:"name", type:"text", title:"Name", readOnly:true },
                {name:"timestamp",type:"dateField",title:"Last Updated"},
								{name:"attributes.createdByUser",type:"text",title:"createdByUser", readOnly:true }, 
								{name:"attributes.createdByProvider",type:"text",title:"createdByProvider", readOnly:true } 
            ]
        });
        profilesDataGrid.jsGrid("sort",{
            field:"timestamp",
            order:"desc"
        });
        profilesListing = $("#profileContainerListing");
        profileTemplate = profilesListing.find(".profileContainer").clone();
        profilesListing.empty();
        var searchBoxContainer = $("#profileSearchBox");
        searchBox = $("<input/>",{
            type:"text",
            val:getQueryFunc()
        });
        searchBoxContainer.append(searchBox);
        var searchBoxChangeFunc = function(e){
            var q = $(this).val().trim();
            currentQuery = q;
            if (e.keyCode == 13 && searchPermitted){
                searchFunc(getQueryFunc());
            }
        };
        searchBox.on("keyup",searchBoxChangeFunc);
        permitOneSearch();
    });
    var permitOneSearch = function(){
        searchPermitted = true;
        $("#searchButton").off("click").attr("disabled",false).on("click",function(){
            searchFunc(getQueryFunc());
        });
    }
    var shouldDisplayProfile = function(prof){
			return true;
		};

    var newTag = function(title){
        var tag = $("<span/>");
        tag.append($("<span/>",{
            class:"newProfile",
            text: "new"
        }));
        tag.append($("<span/>",{
            html:title
        }));
        return tag;
    };

    var reRender = function(){
        var newThreshold = new Date().getTime() - (30 * 60 * 1000); // last 30 minutes
        dataGridItems = _.clone(_.filter(_.uniqBy(_.reverse(_.orderBy(currentSearchResults,"lastAccessed")),"id"),shouldDisplayProfile));
        if (profilesDataGrid != undefined){
            profilesDataGrid.jsGrid("loadData");
            var sortObj = profilesDataGrid.jsGrid("getSorting");
            if ("field" in sortObj){
                profilesDataGrid.jsGrid("sort",sortObj);
            }
        }
        var convs = dataGridItems;
        var convCount = sprintf("%s result%s",convs.length,convs.length == 1 ? "" : "s");
        $("#profileListing").find(".aggregateContainer").find(".count").text(convCount);
    };
    var searchFunc = function(query){
        $("#searchButton").attr("disabled",true).off("click");
        searchPermitted = false;
        currentQuery = query.trim();
        updateQueryParams();
        searchForProfiles(getQueryFunc()); //injected from Lift
    };
    MeTLBus.subscribe("receiveUsername","profileSearch",function(user){
			username = user;
    });
    var getUsernameFunc = function(){
			return username;
    };
    MeTLBus.subscribe("receiveUserGroups","profileSearch",function(groups){
			userGroups = groups;
    });
    var getUserGroupsFunc = function(){
			return userGroups
    };
    MeTLBus.subscribe("receiveProfileDetails","profileSearch",function(details){
			currentSearchResults.push(details);
			reRender();
    });
		MeTLBus.subscribe("receiveProfileSearchResults","profileSearch",function(results){
			console.log("received search results:",results);
			currentSearchResults = _.map(results,function(r){ return r.profile; });
			updateQueryParams();
			permitOneSearch();
			reRender();
		});
    var updateQueryParams = function(){
        // console.log("updating queryparams:",getQueryFunc(),window.location);
        if (window != undefined && "history" in window && "pushState" in window.history){
            var l = window.location;
            var q = getQueryFunc();
            var newUrl = sprintf("%s//%s%s",l.protocol,l.host,l.pathname);
            if (q != undefined){
                newUrl = sprintf("%s?query=%s",newUrl,q);
            }
            window.history.replaceState({
                path:newUrl,
                url:newUrl
            },newUrl,newUrl);
        }
    };

    MeTLBus.subscribe("receiveQuery","profileSearch",function(q){
        currentQuery = q.trim();
        updateQueryParams();
        searchBox.val(getQueryFunc());
        reRender();
    });
    var getProfileListingFunc = function(){
        return dataGridItems;
    };
    var getImportListingFunc = function(){
        return currentImports;
    };
    var getQueryFunc = function(){
        return currentQuery;
    };
    return {
        getProfileListing:getProfileListingFunc,
        getQuery:getQueryFunc,
        getUsername:getUsernameFunc,
        getUserGroups:getUserGroupsFunc,
        search:searchFunc,
        getUserGroups:function(){return userGroups;},
        getUsername:function(){return username;},
    };
})();
