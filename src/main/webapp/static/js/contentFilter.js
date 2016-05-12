var ContentFilter = (function(){
    var owner = {
        id:"owner",
        name:"owner",
        filterStanza:function(stanza){
            return "author" in stanza && stanza.author == Conversations.getCurrentConversation().author;
        },
        enabled:true
    };
    var myPrivate = {
        id:"myPrivate",
        name:"My private content",
        filterStanza:function(stanza){
            return "author" in stanza && stanza.author == UserSettings.getUsername() && "privacy" in stanza && stanza.privacy == "PRIVATE";
        },
        enabled:true
    };
    var myPublic = {
        id:"myPublic",
        name:"My public content",
        filterStanza:function(stanza){
            return "author" in stanza && stanza.author == UserSettings.getUsername() && "privacy" in stanza && stanza.privacy == "PUBLIC";
        },
        enabled:true
    };
    var myPeers = {
        id:"peer",
        name:"My peers' content",
        filterStanza:function(stanza){
            return "author" in stanza && stanza.author != Conversations.getCurrentConversation().author && stanza.author != UserSettings.getUsername();
        },
        enabled:true
    };
    var generateGroupFilter = function(group){
        return {
            id:group.id,
            name:group.name,
            filterStanza:function(stanza){
                var members = "members" in group ? group.members : [];
                return "author" in stanza && _.contains(members,stanza.author);
            },
            enabled:true
        };
    };
    var applyFilters = function(stanza){
        return _.any(filters,function(filter){
            return ("enabled" in filter && filter.enabled == true) ? filter.filterStanza(stanza) : false ;
        });
    };
    var filtered = function(func){
        return function(stanza){
            if (applyFilters(stanza)){
                //console.log("showed:",stanza);
                func(stanza);
            } else {
                console.log("hiding:",stanza);
            };
        };
    };
    var filters = [];
    var contentFilterContainer = {};
    var contentFilterTemplate = {};

    $(function(){
        var internalDrawInk = drawInk;
        var internalDrawText = drawText;
        var internalDrawImage = drawImage;

        drawInk = filtered(internalDrawInk);
        drawText = filtered(internalDrawText);
        drawImage = filtered(internalDrawImage);

        $("#menuContentFilter").on("click",function(){
            showBackstage("contentFilter");
            updateActiveMenu(this);
        });
        contentFilterContainer = $("#contentFilterListing");
        contentFilterTemplate = contentFilterContainer.find(".contentFilterItem").clone();
        contentFilterContainer.empty();
    });
    var renderContentFilter = function(filter){
        var root = contentFilterTemplate.clone();
        var cb = root.find(".contentFilterCheckbox");
        var label = root.find(".contentFilterCheckboxLabel");
        var labelText = root.find(".contentFilterCheckboxLabelText");
        var id = sprintf("contentFilter_%s",filter.id);
        labelText.text(filter.name);
        label.attr("for",id);
        cb.prop("checked",filter.enabled);
        cb.attr("id",id);
        cb.on("change",function(){
            filter.enabled = cb.is(":checked");
            renderContentFilters();
            blit();
        });
        return root;
    };
    var renderContentFilters = function(){
        contentFilterContainer.html(_.map(filters,renderContentFilter));
    };
    var getFiltersFunction = function(){
        return filters;
    };
    var setFiltersFromGroups = function(groups){
        if (Conversations.isAuthor()){
            filters = _.concat([myPrivate,myPublic],_.map(groups,generateGroupFilter));
        } else {
            filters = _.concat([owner,myPrivate,myPublic],_.map(_.filter(groups,function(g){
                return _.contains(g.members,UserSettings.getUsername());
            }),generateGroupFilter));
        }
        blit();
    };
    var setDefaultFilters = function(){
        if (Conversations.isAuthor()){
            filters = [myPrivate,myPublic,myPeers];
        } else {
            filters = [owner,myPrivate,myPublic,myPeers];
        }
        blit();
    };
    var conversationJoined = function(){
        var cs = Conversations.getCurrentSlide();
        if (cs != undefined && "groupSet" in cs){
            setFiltersFromGroups(cs.groupSet.groups);
        } else {
            setDefaultFilters();
        }
        renderContentFilters();
    };
    var setFilterFunction = function(){
        blit();
    };
    Progress.conversationDetailsReceived["ContentFilter"] = conversationJoined;
    Progress.onConversationJoin["ContentFilter"] = conversationJoined;
    return {
        getFilters:getFiltersFunction,
        setFilter:setFilterFunction
    };
})();
