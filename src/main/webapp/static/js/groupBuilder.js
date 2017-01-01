var GroupBuilder = (function(){
    var initialGroups = {};
    var externalGroups = {};
    var iteratedGroups = [];
    var _strategy = "byMaximumSize";
    var _presentStudentsOnly = false;
    var _parameters = {
        byTotalGroups:5,
        byMaximumSize:4
    };
    var renderMember = function(member){
        return $("<div />",{
            class:"groupBuilderMember",
            text:member
        });
    };
    var groupSetKey = function(groupSet){
        var rel = groupSet.foreignRelationship;
        if(rel){
            return sprintf("%s@%s",rel.key,rel.system);
        }
        return groupSet.name;
    }
    var flatInitialGroups = function(){
        return _.flatMap(initialGroups,function(groupSet){
            return _.map(groupSet.groups,function(group){
                var r = {};
                _.each(group.members,function(m){
                    r[m.name] = true;
                });
                return r;
            });
        });
    };
    var renderExternalGroups = function(){
        var container = $(".jAlert .groupSlideDialog");
        var importV = container.find(".importGroups").empty();

        $("<input />",{
            type:"radio",
            name:"groupSetSelector",
            id:"clearGroups"
        }).on("click",function(){
            initialGroups = {};
	    iteratedGroups = [];
            doSimulation();
        }).appendTo(importV).prop("checked",true);
        $("<label />",{
            for:"clearGroups"
                }).append($("<span />",{
                    class:"icon-txt",
                    text:"No long-term groups"
                })).appendTo(importV);

        var groupId = 0;
        _.each(externalGroups,function(orgUnit){
            _.each(orgUnit,function(groupCat){
                var ou = groupCat.orgUnit;
                if(ou){
                    var ouV = $("<div />",{
                    }).appendTo(importV);
                    var groupSet = groupCat.groupSet;
                    var groupSetV = $("<div />",{
                    }).appendTo(ouV);
                    var groupSetHeader = $("<div />",{
                        class:"flex-container-responsive"
                    }).appendTo(groupSetV);
                    var inputId = sprintf("structuralGroup_%s",groupId);
                    var cacheKey = groupSetKey(groupSet);
                    var inputV = $("<input />",{
                        type:"radio",
                        name:"groupSetSelector",
                        id:inputId
                    }).on("click",function(){
                        if(cacheKey in initialGroups){
			    initialGroups = {};
                        }
                        else{
			    initialGroups = {};
			    initialGroups[cacheKey] = groupSet;
                        }
			iteratedGroups = [];
                        doSimulation(flatInitialGroups());
                    }).appendTo(groupSetHeader);
                    inputV.prop("checked",cacheKey in initialGroups);
                    $("<label />",{
                        for:inputId
                    }).append($("<span />",{
                        class:"icon-txt",
                        text:sprintf("Copy %s from %s",groupSet.name,ou.name)
                    })).appendTo(groupSetHeader);

                    _.each(groupCat.groups,function(group){
                        var groupV = $("<div />",{
                            class:"groupBuilderGroup"
                        }).appendTo(groupSetV);
                        _.each(group.members,function(member){
                            renderMember(member.name).appendTo(groupV);
                        });
                    });
                    groupId++;
                }
            });
        });
    }
    var simulate = function(strategy,parameter,presentStudentsOnly){
        console.log("Simulate",strategy,parameter,presentStudentsOnly);
        var groups = flatInitialGroups();
        console.log("flatInitialGroups",groups);
        var participants;
        switch(presentStudentsOnly){
        case "allPresent": participants = Participants.getParticipants();
            break;
        default: participants = Participants.getPossibleParticipants();
            break;
        }
        var attendees = _.without(participants, Conversations.getCurrentConversation().author);
        attendees = _.omitBy(attendees,function(k){
            return _.some(groups,function(g){
                return k in g;
            });
        });
        var filler;
        switch(strategy){
        case "byTotalGroups":
            for(var i = groups.length; i < parseInt(parameter);i++){
                groups.push({});
            }
            filler = function(p){
                var targetG = _.sortBy(groups,function(g){return _.keys(g).length})
                targetG[0][p] = true;
            }
            break;
        case "byMaximumSize":
            filler = function(p){
                var targetG = _.find(groups,function(g){
                    return _.keys(g).length < parseInt(parameter);
                });
                if(!targetG){
                    targetG = {};
                    groups.push(targetG);
                }
                targetG[p] = true;
            }
            break;
        }
        _.each(attendees,filler);
        console.log(groups);
        return _.map(groups,_.keys);
    }
    var renderGroupScopes = function(container){
        _.each([
            ["Show me all my enrolled students","allEnrolled"],
            ["Only show me students who are here right now","allPresent"]],function(params){
                $("<option />",{
                    text:params[0],
                    value:params[1]
                }).appendTo(container);
            });
    }
    var renderStrategies = function(container){
        _.each([
            ["there are","byTotalGroups"],
            ["each has","byMaximumSize"]],function(params){
                $("<option />",{
                    text:params[0],
                    value:params[1]
                }).appendTo(container);
            });
    }
    var refreshToolState = function(){
        var menuButton = $("#menuGroups");
        if(Conversations.shouldModifyConversation()){
            menuButton.parent().show();
        }
        else{
            menuButton.parent().hide();
        }
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
                        renderMember(member).appendTo(g).draggable();
                    });
                });
            });
        }
    };
    var doSimulation = function(simulated){
        var container = $(".jAlert .groupSlideDialog");
        var groupsV = container.find(".groups");
        simulated = simulated || simulate(_strategy,_parameters[_strategy],_presentStudentsOnly);
        groupsV.empty();
        _.each(simulated,function(group){
            var g = $("<div />",{
                class:"groupBuilderGroup ghost"
            });
            _.each(group,function(member){
                renderMember(member).draggable().appendTo(g);
            });
            g.droppable({
                drop:function(e,ui){
                    var member = $(ui.draggable).text();
                    _.each(simulated,function(gr){
                        if(_.includes(gr,member)){
                            gr.splice(gr.indexOf(member),1);
                        }
                    });
                    group.push(member);
                    iteratedGroups = simulated;
                    doSimulation(simulated);
                    e.preventDefault();
                }
            });
            g.appendTo(groupsV);
        });
        iteratedGroups = simulated;
    };
    var showAddGroupSlideDialogFunc = function(){
        getGroupsProviders();
        var container = $("#groupSlideDialog").clone().show();
        var jAlert = $.jAlert({
            title:"Add Group page",
            width:"75%",
            content:container[0].outerHTML,
            btns:[{
                text:"Add page",
                theme:'green',
                closeAlert:true,
                onClick:function(){
                    var seed = iteratedGroups.length > 0 ? iteratedGroups : _.map(flatInitialGroups(),_.keys);
                    Conversations.addGroupSlide(_strategy, parseInt(_parameters[_strategy]), seed);
                    initialGroups = {};
                    iteratedGroups = [];
                    externalGroups = {};
                }
            }]
        });
        container = $(".jAlert .groupSlideDialog");
        var strategySelect = container.find(".strategySelect");
        var parameterSelect = container.find(".parameterSelect");
        var groupScope = container.find(".presentStudentsOnly");
        var groupsV = container.find(".groups");
        renderStrategies(strategySelect);
        renderGroupScopes(groupScope);

        container.on("change",".presentStudentsOnly",function(){
            _presentStudentsOnly = $(this).val();
            console.log(_presentStudentsOnly);
            doSimulation();
        });
        container.on("change",".strategySelect",function(){
            _strategy = $(this).val();
            console.log("Strategy set:",_strategy);
            parameterSelect.empty();
            switch(_strategy){
            case "byTotalGroups":
                _.each(_.range(2,10),function(i){
                    $("<option />",{
                        text:sprintf("%s groups in total",i),
                        value:i.toString()
                    }).appendTo(parameterSelect);
                });
                parameterSelect.val(_parameters[_strategy]).change();
                break;
            case "byMaximumSize":
                _.each(_.range(1,10),function(i){
                    $("<option />",{
                        text:i == 1 ? "only one member" : sprintf("at most %s members",i),
                        value:i.toString()
                    }).appendTo(parameterSelect);
                });
                parameterSelect.val(_parameters[_strategy]).change();
                break;
            }
        });
        container.on("change",".parameterSelect",function(){
            console.log("change parameter",_parameters);
            _parameters[_strategy] = $(this).val();
            doSimulation();
        });
        strategySelect.val(_strategy).change();
    };
    var blockGroups = function(blocked){
        $(".groupsOu.blocker").toggle(blocked);
    }
    var statusReport = function(msg){
        console.log(msg);
        $("#groupSlideDialog .importGroups").text(msg);
    }
    Progress.groupProvidersReceived["GroupBuilder"] = function(args){
        var select = $(".jAlert .ouSelector").empty();
        $("<option />",{
            text:"no starting groups",
            value:"NONE",
            selected:true
        }).appendTo(select);
        _.each(args.groupsProviders,function(provider){
            $("<option />",{
                text:provider,
                value:provider
            }).appendTo(select);
        });
        select.on("change",function(){
            var choice = $(this).val();
            if(choice != "NONE"){
                blockGroups(true);
                getOrgUnitsFromGroupProviders(choice);
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
            statusReport(sprintf("No Group Sets found in Org Unit %s",groupSets.groupSets.name));
        }
    };
    Progress.groupsReceived["GroupBuilder"] = function(args){
        var byOrgUnit = externalGroups[args.orgUnit.name];
        if (byOrgUnit === undefined){
            byOrgUnit = {};
            externalGroups[args.orgUnit.name] = byOrgUnit;
        }
        byOrgUnit[args.groupSet.name] = args;
        renderExternalGroups();
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
    return {
        showAddGroupSlideDialog:showAddGroupSlideDialogFunc,
        getExternalGroups:function(){
            return externalGroups;
        }
    };
})();
