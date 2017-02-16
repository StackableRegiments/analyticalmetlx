var GroupBuilder = (function(){
    var strategy = "byTotalGroups";
    var groupScope = "allInHistory";
    var parameters = {
        byTotalGroups:5,
        byMaximumSize:4
    };
    var availableGroupSets = {};
    /*Name -> {name,enrolled,group@{name,groupSet,type}}*/
    var participants = {};
    var renderMember = function(member){
        var view =  $("<div />",{
            class:"groupBuilderMember",
            text:member.name
        });
        if(member.group && member.group.type == "external"){
            view.css({
                "color":"red"
            });
        }
        return view;
    };
    var doShuffle = function(){
        participants = _.shuffle(participants);
        clearRandomGroups();
        allocate();
        renderAllocations();
    };
    var seedParticipants = function(){
        console.log("Clearing %s participants",_.keys(participants).length);
        participants = {};
        console.log("Cleared participants: %s",_.keys(participants).length);
        var seed = function(name,enrolled){
            if(name != Conversations.getCurrentConversation().author && name != "mother"){
                participants[name] = {
                    name:name,
                    enrolled:enrolled,
                    present:false,
                    participating:false
                }
                console.log(sprintf("%s seeding as enrolled: %s (%s)",name,enrolled,_.keys(participants).length));
            }
            else{
                console.log(sprintf("%s not seeding because AUTHOR AND MOTHER DON'T GROUP",name));
            }
        }
        _.each(Participants.getPossibleParticipants(),function(name){
            seed(name,true);
        });
        _.each(_.map(Participants.getParticipants(),"name"),function(name){
            if(name != Conversations.getCurrentConversation().author){
                if(!(name in participants)){
                    seed(name,false);
                }
                participants[name].participating = true;
            }
        });
        _.each(Participants.getCurrentParticipants(),function(name){
            if(name != Conversations.getCurrentConversation().author){
                if(!(name in participants)){
                    seed(name,false);
                }
                participants[name].present = true;
            }
        });
        doShuffle();
        console.log("Seeded %s participants",_.keys(participants).length);
    };
    var allocationsFor = function(cohort){
        return _.values(_.groupBy(cohort,function(p){
            return p.group ? p.group.name : "unallocated";
        }));
    }
    var inScope = function(p){
        return (groupScope == "allEnrolled" && p.enrolled) || (groupScope == "allInHistory" && p.participating) || (groupScope == "allPresent" && p.present);
    }
    var allocate = function(){
        var allocatable = _.filter(participants,inScope);
        var byAllocated = _.partition(allocatable,function(p){
            return "group" in p;
        });
        var allocated = byAllocated[0];
        var unallocated = byAllocated[1];
        if(unallocated && unallocated.length > 0){
            var allocatee = unallocated[0];
            var allocations = allocationsFor(allocated);
            var spareGroup = {
                name:sprintf("random_%s",allocations.length),
                type:"random"
            };
            switch(strategy){
            case "byTotalGroups":
                var targetCount = parseInt(parameters[strategy]);
                if(allocations.length < targetCount){
                    allocatee.group = spareGroup;
                    console.log(sprintf("%s started a new group: %s",allocatee.name,allocatee.group.name));
                }
                else {
                    var leastPopulousGroup = _.sortBy(allocations,"length");
                    allocatee.group = _.clone(leastPopulousGroup[0][0].group);
                    allocatee.group.type = "random";
                    console.log(sprintf("%s allocated to group %s of length %s",allocatee.name,allocatee.group.name,leastPopulousGroup[0].length));
                }
                break;
            case "byMaximumSize":
                var targetSize = parseInt(parameters[strategy]);
                var leastFullValidGroup = _.find(allocations,function(group){
                    return group.length < targetSize;
                });
                if(leastFullValidGroup){
                    allocatee.group = _.clone(leastFullValidGroup[0].group);
                    allocatee.group.type = "random";
                    console.log("%s allocated to group %s of length %s",allocatee.name,allocatee.group.name,leastFullValidGroup.length);
                }
                else{
                    allocatee.group = spareGroup;
                    console.log(sprintf("%s started a new group: %s",allocatee.name,allocatee.group.name));
                }
                break;
            }
            allocate();
        }
        else {
            console.log("No more unallocated members");
        }
    }
    var renderGroupScopes = function(container){
        var subject = Conversations.getCurrentConversation().subject;
        var scopes = [
            ["anyone who has ever been here","allInHistory"],
            ["anyone here right now","allPresent"]
        ];
        if(subject != "unrestricted"){
            scopes.push([sprintf("anyone enrolled in %s",subject),"allEnrolled"]);
        }
        _.each(scopes,function(params){
            $("<option />",{
                text:params[0],
                value:params[1]
            }).prop("selected",params[1] == groupScope).appendTo(container);
        });
    }
    var renderStrategies = function(container){
        _.each([
            ["there are","byTotalGroups"],
            ["each has","byMaximumSize"]],function(params){
                $("<option />",{
                    text:params[0],
                    value:params[1]
                }).prop("selected",params[1] == strategy).appendTo(container);
            });
    }
    var render = function(){
        var container = $("#groupsPopup");
        var composition = $("#groupComposition");
        var importV = container.find(".importGroups").empty();
        var groupsV = container.find(".groups").empty();
        var slide = Conversations.getCurrentSlide();
        if(slide){
            _.each(slide.groupSets,function(groupSet){
                _.each(_.sortBy(groupSet.groups,"title"),function(group){
                    var g = $("<div />",{
                        class:"groupBuilderGroup"
                    }).appendTo(groupsV).droppable({
                        drop:function(e,ui){
                            var members = $(ui.draggable).find(".groupBuilderMember").addBack(".groupBuilderMember");
                            console.log("Dropped",$(ui.draggable),members);
                            _.each(members,function(memberV){
                                var member = $(memberV).text();
                                console.log("Drop member",member);
                                if(! _.includes(group.members,member)){
                                    _.each(groupSet.groups,function(g){
                                        g.members = _.without(g.members,member);
                                    });
                                    group.members.push(member);
                                    render();
                                    Conversations.overrideAllocation(slide);
                                }
                            });
                            e.preventDefault();
                        }
                    });
                    $("<div />",{
                        class:"title",
                        text:sprintf("Group %s",group.title)
                    }).appendTo(g);
                    _.each(group.members,function(member){
                        renderMember({
                            name:member
                        }).appendTo(g).draggable();
                    });
                });
            });
        }
    };
    var groupSetKey = function(groupSet){
        var rel = groupSet.foreignRelationship;
        if(rel){
            return sprintf("%s@%s",rel.key,rel.system);
        }
        return groupSet.name;
    }
    var clearExternalGroups = function(){
        _.each(participants,clearExternalGroup);
    }
    var clearRandomGroups = function(){
        _.each(participants,clearRandomGroup);
    }
    var clearExternalGroup = function(p){
        if(("group" in p) && p.group.type == "external"){
            delete p.group;
        }
    }
    var clearRandomGroup = function(p){
        if(("group" in p) && p.group.type == "random"){
            delete p.group;
        }
    }
    var renderAvailableGroupSets = function(){
        var container = $(".jAlert .groupSlideDialog");
        var importV = container.find(".importGroups").empty();

        $("<input />",{
            type:"radio",
            name:"groupSetSelector",
            id:"clearGroups"
        }).on("click",function(){
            clearExternalGroups();
            clearRandomGroups();
            allocate();
            renderAllocations();
        }).prop("checked",true).appendTo(importV);
        $("<label />",{
            for:"clearGroups"
                }).append($("<span />",{
                    class:"icon-txt",
                    text:"No Smart Groups"
                })).appendTo(importV);

        _.each(availableGroupSets,function(orgUnit){
            _.each(orgUnit,function(groupCat,groupSetId){
                var ou = groupCat.orgUnit;
                if(ou){
                    var ouV = $("<div />",{
                    }).appendTo(importV);
                    var groupSet = groupCat.groupSet;
                    var groupSetV = $("<div />",{
                    });
                    var groupSetHeader = $("<div />",{
                        class:"flex-container-responsive"
                    }).appendTo(groupSetV);
                    var inputId = sprintf("structuralGroup_%s",groupSetId);
                    var cacheKey = groupSetKey(groupSet);
                    var inputV = $("<input />",{
                        type:"radio",
                        name:"groupSetSelector",
                        id:inputId
                    }).on("click",function(){
                        clearExternalGroups();
                        _.each(participants,function(p){
                            _.each(groupCat.groups,function(group,zi){
                                var i = zi + 1;
                                if(_.includes(_.map(group.members,"name"),p.name)){
                                    p.group = {
                                        groupSet:groupSet.name,
                                        name:i,
                                        type:"external"
                                    };
                                }
                            });
                        });
                        clearRandomGroups();
                        allocate();
                        renderAllocations();
                    }).appendTo(groupSetHeader);
                    inputV.prop("checked",typeof(_.find(participants,function(p){
                        return p.group && p.group.type == "external" && p.group.groupSet == groupSet.name
                    })) != "undefined");
                    $("<label />",{
                        for:inputId
                    }).append($("<span />",{
                        class:"icon-txt",
                        text:sprintf("Copy %s from %s",groupSet.name,ou.name)
                    })).appendTo(groupSetHeader);

                    var author = Conversations.getCurrentConversation().author;
                    _.each(groupCat.groups,function(group){
                        var groupV = $("<div />",{
                            class:"groupBuilderGroup"
                        }).appendTo(groupSetV);
                        var validMembers = _.filter(group.members,function(m) {
                            return m.name != author;
                        });
                        if(validMembers.length > 0){
                            groupSetV.appendTo(ouV);
                            _.each(validMembers,function(obj){
                                var name = obj.name;
                                if(!(name in participants)){
                                    /*If this person were present, enrolled or had acted we would have registered them at seeding*/
                                    participants[name] = {
                                        name:name,
                                        enrolled:false,
                                        present:false
                                    };
                                }
                                renderMember(participants[name]).appendTo(groupV);
                            });
                        }
                    });
                }
            });
        });
    };
    var renderAllocations = function(){
        var container = $(".jAlert .groupSlideDialog");
        var renderable = _.filter(participants,inScope);
        var groups = allocationsFor(renderable);
        console.log("rendering allocations:",participants,renderable,groups);
        var groupsV = container.find(".groups");
        groupsV.empty();
        _.each(groups,function(group){
            var g = $("<div />",{
                class:"groupBuilderGroup ghost"
            });
            _.each(group,function(member){
                var memberV = renderMember(member).draggable();
                if(member.group && member.group.type == "external"){
                    memberV.prependTo(g);
                }
                else{
                    memberV.appendTo(g);
                }
            });
            g.droppable({
                drop:function(e,ui){
                    e.preventDefault();
                    var name = $(ui.draggable).text();
                    var member = participants[name];
                    member.group = {
                        type:"random",
                        name:group[0].group.name
                    };
                    renderAllocations();
                }
            });
            g.appendTo(groupsV);
        });
    };
    var showAddGroupSlideDialogFunc = function(){
        seedParticipants();
        getGroupsProviders();
        var container = $("#groupSlideDialog").clone().show();
        var jAlert = $.jAlert({
            title:"Add group page",
            width:"75%",
            content:container[0].outerHTML,
            btns:[{
                text:"Add page",
                theme:'green',
                closeAlert:true,
                onClick:function(){
                    var sendable = _.filter(participants,function(p){
                        return inScope(p) && p.name != Conversations.getCurrentConversation().author;
                    });
                    var calculatedGroups = allocationsFor(sendable);
                    var sendableGroups = _.map(_.values(calculatedGroups),function(members){
                        return _.map(members,"name");
                    });
                    Conversations.addGroupSlide(strategy, parseInt(parameters[strategy]), sendableGroups);
                }
            }]
        });
        container = $(".jAlert .groupSlideDialog");
        var strategySelect = container.find(".strategySelect");
        var parameterSelect = container.find(".parameterSelect");
        var groupScopeV = container.find(".groupScope");
        var groupsV = container.find(".groups");
        renderStrategies(strategySelect);
        renderGroupScopes(groupScopeV);
        container.find("#randomizeGroups").off("click").on("click",doShuffle);

        container.on("change",".groupScope",function(){
            groupScope = $(this).val();
            console.log("Changing group scope",groupScope);
            clearRandomGroups();
            allocate();
            renderAllocations();
        });
        container.on("change",".strategySelect",function(){
            strategy = $(this).val();
            console.log("Strategy set:",strategy);
            parameterSelect.empty();
            switch(strategy){
            case "byTotalGroups":
                _.each(_.range(2,10),function(i){
                    $("<option />",{
                        text:sprintf("%s groups in total",i),
                        value:i.toString()
                    }).appendTo(parameterSelect);
                });
                parameterSelect.val(parameters[strategy]).change();
                break;
            case "byMaximumSize":
                _.each(_.range(1,10),function(i){
                    $("<option />",{
                        text:i == 1 ? "only one member" : sprintf("at most %s members",i),
                        value:i.toString()
                    }).appendTo(parameterSelect);
                });
                parameterSelect.val(parameters[strategy]).change();
                break;
            }
        });
        container.on("change",".parameterSelect",function(){
            parameters[strategy] = $(this).val();
            clearRandomGroups();
            allocate();
            renderAllocations();
        });
        strategySelect.val(strategy).change();
    };

    var blockGroups = function(blocked){
        $(".groupsOu.blocker").toggle(blocked);
    }
    var statusReport = function(msg){
        console.log(msg);
        $("#groupSlideDialog .importGroups").prepend("<div />",{
            text:msg
        });
    }
    Progress.groupProvidersReceived["GroupBuilder"] = function(args){
        var select = $(".jAlert .ouSelector").empty();
        $("<option />",{
            text:"no groups",
            value:"NONE",
            selected:true
        }).appendTo(select);
        _.each(args.groupsProviders,function(provider){
            $("<option />",{
                text:provider.displayName,
                value:provider.storeId
            }).appendTo(select);
        });
        select.on("change",function(){
            var choice = $(this).val();
            if(choice != "NONE"){
                blockGroups(true);
                var conv = Conversations.getCurrentConversation();
                console.log("finding specific groups",conv);
                if ("foreignRelationship" in conv && "system" in conv.foreignRelationship && "key" in conv.foreignRelationship){
                    var gp = conv.foreignRelationship.system;
                    var k = conv.foreignRelationship.key;
                    console.log("finding specific groups from foreignRelationship",conv.foreignRelationship,gp,k);
                    if (gp == choice){
                        getGroupSetsForOrgUnit(gp,{
                            ouType:"orgUnit",
                            name:"displayName" in conv.foreignRelationship ? conv.foreignRelationship.displayName : conv.foreignRelationship.key,
                            members:[],
                            groupSets:[],
                            foreignRelationship:{
                                system:gp,
                                key:k
                            }
                        },"async");
                    } else {
                        getOrgUnitsFromGroupProviders(choice,"async");
                    }
                } else {
                    getOrgUnitsFromGroupProviders(choice,"async");
                }
            }
        });
    };
    Progress.orgUnitsReceived["GroupBuilder"] = function(orgUnits){
        if ("orgUnits" in orgUnits && orgUnits.orgUnits.length){
        }
        else{
            blockGroups(false);
            statusReport(sprintf("No Org Units found for user %s",UserSettings.getUsername()));
        }
    };
    Progress.groupSetsReceived["GroupBuilder"] = function(groupSets){
        if ("groupSets" in groupSets && groupSets.groupSets.length){
        }
        else{
            blockGroups(false);
            statusReport(sprintf("No Group Sets found in Org Unit %s",groupSets.orgUnit.name));
        }
    };
    Progress.groupsReceived["GroupBuilder"] = function(args){
        var byOrgUnit = availableGroupSets[args.orgUnit.name];
        if (byOrgUnit === undefined){
            byOrgUnit = {};
            availableGroupSets[args.orgUnit.name] = byOrgUnit;
        }
        var author = Conversations.getCurrentConversation().author;
        if(_.some(args.groups,function(group){
            return _.some(group.members,function(member){
                return member.name != author;
            });
        })){
            byOrgUnit[args.groupSet.name] = args;
            renderAvailableGroupSets();
        }
        blockGroups(false);
    };
    Progress.onBackstageShow["GroupBuilder"] = function(backstage){
        if(backstage == "groups"){
            render();
        }
        refreshToolState();
    };
    Progress.currentSlideJidReceived["GroupBuilder"] = function(){
        if(currentBackstage == "groups"){
            render();
        }
    };
    Progress.conversationDetailsReceived["GroupBuilder"] = function(){
        if(currentBackstage == "groups"){
            render();
        }
    };
    var refreshToolState = function(){
        var menuButton = $("#menuGroups");
        if($("#roomToolbar.active").length && Conversations.shouldModifyConversation()){
            menuButton.parent().show();
        }
        else{
            menuButton.parent().hide();
        }
    }
    return {
        showAddGroupSlideDialog:showAddGroupSlideDialogFunc,
        allocate:allocate,
        seedParticipants:seedParticipants,
        renderAllocations:renderAllocations,
        getParticipants:function(){
            return participants;
        }
    };
})();
