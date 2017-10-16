var Blacklist = (function(){
    var blacklistSummaryListing = {};
    var blacklistSummaryTemplate = {};
    var currentBlacklistTemplate = {};
    var currentBlacklistContainer = {};
    var blacklistAuthorsContainer = {};
    var blacklistAuthorTemplate = {};

    var blacklistDatagrid = {};
    var blacklistPopupTemplate = {};
    var blacklists = [];
    var blacklistAuthors = [];
    var currentBlacklist = {};
    $(function(){
        blacklistDatagrid = $("#blacklistDatagrid");
        blacklistPopupTemplate = blacklistDatagrid.find(".blacklistRecord").clone();
        blacklistDatagrid.empty();
        blacklistAuthorsContainer = $("#currentBlacklistAuthorList");
        blacklistAuthorTemplate = blacklistAuthorsContainer.find(".blacklistAuthorContainer").clone();
        blacklistAuthorsContainer.empty();
        refreshToolState();

        var DateField = function(config){
            jsGrid.Field.call(this,config);
        };
        DateField.prototype = new jsGrid.Field({
            sorter: function(a,b){
                return new Date(a) - new Date(b);
            },
            itemTemplate: function(i){
                return moment(i).format('MMM Do YYYY, h:mm a');
            },
            insertTemplate: function(i){return ""},
            editTemplate: function(i){return ""},
            insertValue: function(){return ""},
            editValue: function(){return ""}
        });
        jsGrid.fields.dateField = DateField;

        var gridFields = [
            {
                name:"url",
                type:"text",
                title:"Preview",
                readOnly:true,
                sorting:false,
                itemTemplate:function(thumbnailUrl,submission){
                    var url = sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),submission.author,submission.identity);
                    var img = $("<img/>",{src:url,class:"submissionThumbnail",style:"width:100%;height:160px;cursor:zoom-in"}).on("click",function(){
                        var url = sprintf("/submissionProxy/%s/%s/%s",Conversations.getCurrentConversationJid(),submission.author,submission.identity);
                        var title = sprintf("Ban record at %s on page %s",new Date(submission.timestamp),submission.slide);
                        var rootElem = blacklistPopupTemplate.clone();
                        var authorContainer = rootElem.find(".blacklistLegend");
                        var authorTemplate = authorContainer.find(".blacklistAuthor").clone();
                        authorContainer.empty();
                        if ("blacklist" in submission){
                            _.each(submission.blacklist,function(ba){
                                if ("username" in ba && "highlight" in ba){
                                    var authorElem = authorTemplate.clone();
                                    authorElem.find(".blacklistAuthorName").text(ba.username);
                                    var color = ba.highlight[0];
                                    var opacity = ba.highlight[1];
                                    authorElem.find(".blacklistAuthorColor").css({"background-color":color,"opacity":opacity});
                                    authorContainer.append(authorElem);
                                }
                            });
                        }
                        rootElem.find(".blacklistImage").attr("src",url).css({"max-width":"100%"});
                        $.jAlert({
                            title:title,
                            closeOnClick:true,
                            width:"90%",
                            content:rootElem[0].outerHTML
                        });
                    });
                    return img;
                }
            },
            {name:"slide",type:"number",title:"Page",readOnly:true},
            {name:"timestamp",type:"dateField",title:"When",readOnly:true},
            {name:"userCount",type:"number",title:"Who",readOnly:true,itemTemplate:function(v,o){
                return _.map(o.blacklist,"username").join(", ");
            }}
        ];
        blacklistDatagrid.jsGrid({
            width:"100%",
            height:"auto",
            inserting:false,
            editing:false,
            sorting:true,
            paging:true,
            noDataContent: "No ban records",
            controller: {
                loadData: function(filter){
                    var richLists = _.map(blacklists,function(bl){
                        bl.userCount = bl.blacklist.length;
                        return bl;
                    });
                    if ("sortField" in filter){
                        var sorted = _.sortBy(richLists,function(sub){
                            return sub[filter.sortField];
                        });
                        if ("sortOrder" in filter && filter.sortOrder == "desc"){
                            sorted = _.reverse(sorted);
                        }
                        return sorted;
                    } else {
                        return richLists;
                    }
                }
            },
            pageLoading:false,
            fields: gridFields
        });
        blacklistDatagrid.jsGrid("sort",{
            field:"timestamp",
            order:"desc"
        });
        renderBlacklistsInPlace();
    });
    var filteredBlacklists = function(){
        return _.filter(blacklists,filterBlacklist);
    };
    var filterBlacklist = function(sub){
        return Conversations.shouldModifyConversation();
    };
    var updateAuthorList = function(conversation){
        if ("blacklist" in conversation && "jid" in conversation && Conversations.getCurrentConversationJid() == conversation.jid){
            blacklistAuthors = conversation.blacklist;
            renderBlacklistAuthorsInPlace();
            refreshToolState(conversation);
        }
    };
    var renderBlacklistAuthorsInPlace = function(){
        if( WorkQueue != undefined ) {
            WorkQueue.enqueue(function(){
                blacklistAuthorsContainer.empty();
                var unbanAllButton = $("#unbanAll");
                if (blacklistAuthors.length > 0){
                    unbanAllButton.show();
                    unbanAllButton.unbind("click");
                    unbanAllButton.on("click",function(){
                        changeBlacklistOfConversation(Conversations.getCurrentConversationJid(),[]);
                    });
                } else {
                    unbanAllButton.unbind("click");
                    unbanAllButton.hide();
                }
                blacklistAuthors.map(function(author){
                    var rootElem = blacklistAuthorTemplate.clone();
                    rootElem.find(".blacklistAuthorName").text(author);
                    rootElem.find(".blacklistAuthorUnbanButton").on("click",function(){
                        blacklistAuthors = _.filter(blacklistAuthors,function(a){return a != author;});
                        changeBlacklistOfConversation(Conversations.getCurrentConversationJid(),blacklistAuthors);
                    });
                    blacklistAuthorsContainer.append(rootElem);
                });
            });
        }
    };
    var refreshToolState = function(conversation){
        if( WorkQueue != undefined ) {
            WorkQueue.enqueue(function () {
                if (Conversations.shouldModifyConversation(conversation)) {
                    $("#ban").show();
                    $("#administerContent").show();
                    $("#menuBlacklist").show();
                } else {
                    $("#ban").hide();
                    $("#administerContent").hide();
                    $("#menuBlacklist").hide();
                    $("#blacklistPopup").hide();
                }
            });
        }
    };
    var clearState = function(conversation){
        refreshToolState(conversation);
        blacklists = [];
        currentBlacklist = {};
    };
    var renderBlacklistsInPlace = function(){
        if( WorkQueue != undefined ) {
            WorkQueue.enqueue(function () {
                blacklistDatagrid.jsGrid("loadData");
                var sortObj = blacklistDatagrid.jsGrid("getSorting");
                if ("field" in sortObj) {
                    blacklistDatagrid.jsGrid("sort", sortObj);
                }
            });
        }
    };
    var historyReceivedFunction = function(history){
        try {
            if ("type" in history && history.type == "history"){
                clearState();
                _.forEach(history.submissions,function(blacklist){
                    onBlacklistReceived(blacklist,true);
                });
                renderBlacklistsInPlace();
            }
        }
        catch (e){
            console.log("Blacklists.historyReceivedFunction",e);
        }
    };
    var onBlacklistReceived = function(blacklist,skipRender){
        try {
            if ("target" in blacklist && blacklist.target == "bannedcontent"){
                if (filterBlacklist(blacklist)){
                    blacklists.push(blacklist);
                    if (!skipRender){
                        renderBlacklistsInPlace();
                    }
                }
            }
        }
        catch (e){
            console.log("Blacklists.stanzaReceivedFunction",e);
        }
    };
    var serverSideBanSelectionFunc = function(conversationJid,slideId,inks,texts,multiWordTexts,images){
        var inkIds = _.uniq(_.map(inks,function(e){return e.identity;}));
        var textIds = _.uniq(_.map(texts,function(e){return e.identity;}));
        var multiWordTextIds = _.uniq(_.map(multiWordTexts,function(e){return e.identity;}));
        var imageIds = _.uniq(_.map(images,function(e){return e.identity;}));
        banContent(conversationJid,slideId,inkIds,textIds,multiWordTextIds,imageIds,[]);
    };
    var clientSideBanSelectionFunc = function(conversationJid,slideId,inks,texts,multiWordTexts,images){
        WorkQueue.pause();
        var bannedAuthors = _.uniq(_.map(_.flatMap([inks,texts,multiWordTexts,images],_.values),"author"));

        var cc = Conversations.getCurrentConversation();
        changeBlacklistOfConversation(cc.jid.toString(),_.uniq(_.flatten([cc.blacklist,bannedAuthors])));

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

        var nextColour = function(author){
            return [Colors.getColorForSeed(author)[0],128]
        };
        var colouredAuthors = _.map(bannedAuthors,function(author){
            return {
                username:author,
                highlight:nextColour(author)
            };
        });
        _.forEach(_.values(inks),function(ink){
            var inkShadow = _.cloneDeep(ink);
            inkShadow.thickness = inkShadow.thickness * 3;
	    var highlight = _.find(colouredAuthors,{username:ink.author});
            inkShadow.color = highlight ? highlight.highlight : "red";
            inkShadow.isHighlighter = true;
            inkShadow.identity = inkShadow.identity + "_banning";
            prerenderInk(inkShadow);
            drawInk(inkShadow,tempCtx);
        });
        _.forEach(images,function(image){
            var sBounds = screenBounds(image.bounds);
            if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
                tempCtx.strokeStyle = _.find(colouredAuthors,{username:image.author}).highlight;
                var s = sBounds.screenPos;
                tempCtx.lineWidth = 4;
                tempCtx.rect(s.x - 2,s.y - 2,sBounds.screenWidth + 4,sBounds.screenHeight + 4);
                tempCtx.stroke();
            }
        });
        _.forEach(multiWordTexts,function(text){
            var sBounds = screenBounds(text.bounds);
            if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
                tempCtx.strokeStyle = _.find(colouredAuthors,{username:text.author}).highlight;
                var s = sBounds.screenPos;
                tempCtx.lineWidth = 4;
                tempCtx.rect(s.x - 2,s.y - 2,sBounds.screenWidth + 4,sBounds.screenHeight + 4);
                tempCtx.stroke();
            }
        });
        _.forEach(texts,function(text){
            var sBounds = screenBounds(text.bounds);
            if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
                tempCtx.strokeStyle = _.find(colouredAuthors,{username:text.author}).highlight;
                var s = sBounds.screenPos;
                tempCtx.lineWidth = 4;
                tempCtx.rect(s.x - 2,s.y - 2,sBounds.screenWidth + 4,sBounds.screenHeight + 4);
                tempCtx.stroke();
            }
        });

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
                    blacklist:colouredAuthors,
                    identity:identity,
                    privacy:"PUBLIC",
                    slide:currentSlide,
                    target:"bannedcontent",
                    timestamp:t,
                    title:title,
                    type:"submission",
                    url:newIdentity
                };
                sendStanza(submissionStanza);

                var deleter = batchTransform();
                deleter.inkIds = _.map(_.values(inks),"identity");
                deleter.textIds = _.map(_.values(texts),"identity");
                deleter.multiWordTextIds = _.map(_.values(multiWordTexts),"identity");
                deleter.imageIds = _.map(_.values(images),"identity");
                deleter.newPrivacy = "private";
                sendStanza(deleter);

                WorkQueue.gracefullyResume();
                successAlert("Banned content",sprintf("You have banned: %s", bannedAuthors.join(", ")));
            },
            error: function(e){
                console.log(e);
                errorAlert("Banning failed","This image cannot be processed, either because of image protocol issues or because it exceeds the maximum image size.");
                WorkQueue.gracefullyResume();
            },
            data: imageData,
            cache: false,
            contentType: false,
            processData: false
        });
    };
    Progress.conversationDetailsReceived["blacklist"] = updateAuthorList;
    Progress.onConversationJoin["blacklist"] = clearState;
    Progress.historyReceived["blacklist"] = historyReceivedFunction;
    Progress.stanzaReceived["blacklist"] = onBlacklistReceived;
    return {
        getAllBlacklists:function(){return Conversations.shouldModifyConversation() ? filteredBlacklists() : [];},
        getCurrentBlacklist:function(){return Conversations.shouldModifyConversation() ? currentBlacklist : {};},
        processBlacklist:onBlacklistReceived,
        getBlacklistedAuthors:function(){return Conversations.shouldModifyConversation() ? blacklistAuthors : [];},
        banSelection:clientSideBanSelectionFunc,
        reRender:renderBlacklistsInPlace
    };
})();
