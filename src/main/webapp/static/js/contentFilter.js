var ContentFilter = (function(){
    var audiences = [];
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
                return "author" in stanza && _.includes(members,stanza.author);
            },
            type:"group",
            enabled:true
        };
    };
    var isIsolatedFromFunction = function(stanza,isProjector){
        if(isProjector && stanza.audiences.length){
            return true;
        }
        return audiences.length &&
            stanza.audiences &&
            stanza.audiences.length && !(_.some(stanza.audiences,function(audience){
                return _.includes(audiences,audience.name) || _.includes(audiences,audience);
            }));
    };
    var applyFilters = function(stanza){
        var observed = Participants.getParticipants()[stanza.author];
        var isProjector = !UserSettings.getIsInteractive();
        if(observed && !observed.following) return false;
        if(isIsolatedFromFunction(stanza,isProjector)){
            return false;
        }
        return _.some(filters,function(filter){
            return filter.enabled && filter.filterStanza(stanza);
        });
    };
    var filtered = function(func){
        return function(stanza,a,b,c,d,e,f,g){
            if (applyFilters(stanza)){
                func(stanza,a,b,c,d,e,f,g);
            }
        };
    };
    var filters = [];
    var contentFilterContainer = {};
    var contentFilterTemplate = {};

    $(function(){
        var internalDrawInk = drawInk;
        var internalDrawText = drawText;
        var internalDrawImage = drawImage;
        var internalDrawMultiwordText = drawMultiwordText;

        drawInk = filtered(internalDrawInk);
        drawText = filtered(internalDrawText);
        drawImage = filtered(internalDrawImage);
        drawMultiwordText = filtered(internalDrawMultiwordText);

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
        var currentSlide = Conversations.getCurrentSlide();
        if(currentSlide){
            var referencedGroup = _.find(Conversations.getCurrentGroups(),function(group){
                return group.id == filter.id;
            });
            if(referencedGroup){
                labelText.text(sprintf("Group %s: %s",referencedGroup.title,_.join(referencedGroup.members,",")));
            }
            else {
                labelText.text(filter.name);
            }
        }
        else{
            labelText.text(filter.name);
        }
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
    var getFiltersFromGroups = function(groups){
        return _.map(Conversations.isAuthor() ? Conversations.getCurrentGroups() : Conversations.getCurrentGroup(),generateGroupFilter);
    };
    var getDefaultFilters = function(){
        var fs = [myPrivate,myPublic];
        if(Conversations.isAuthor()){
        }
        else{
            fs = fs.concat(owner);
        }
        if(! Conversations.getCurrentGroups().length){
            fs = fs.concat(myPeers);
        }
        return fs;
    };
    var generateFilters = function(){
        var cs = Conversations.getCurrentSlide();
        filters = getDefaultFilters().concat(cs ? getFiltersFromGroups() : []);
        renderContentFilters();
        blit();
    };
    var setFilterFunction = function(id,enabled){
        _.each(getFiltersFunction(),function(fil){
            if (fil.id == id){
                fil.enabled = enabled;
            }
        });
        renderContentFilters();
        blit();
    };
    var getAudiencesFunction = function(){
        return audiences;
    };
    var setAudienceFunction = function(audience){
        audiences = [audience];
    };
    var clearAudiencesFunction = function(){
        audiences = [];
    }
    Progress.currentSlideJidReceived["ContentFilter"] = function(){
        audiences = [];
        generateFilters();
    }
    Progress.onConversationJoin["ContentFilter"] = function(){
        audiences = [];
        generateFilters();
    };
    Progress.afterJoiningSlide["ContentFilter"] = function(){
        audiences = [];
        generateFilters();
    };
    Progress.conversationDetailsReceived["ContentFilter"] = generateFilters;
    return {
        getFilters:getFiltersFunction,
        setFilter:setFilterFunction,
        getAudiences:getAudiencesFunction,
        setAudience:setAudienceFunction,
        clearAudiences:clearAudiencesFunction,
	    exposes:applyFilters
    };
})();
