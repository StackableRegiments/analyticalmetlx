var GroupBuilder = (function(){
    var displayCache = {};
    var initialGroups = [];
    var externalGroups = {};
    var renderMember = function(member){
        return $("<div />",{
            class:"groupBuilderMember",
            text:member
        });
    };
    var renderExternalGroups = function(){
        var container = $("#groupsPopup");
        var importV = container.find(".importGroups").empty();
        _.each(externalGroups,function(orgUnit){
            _.each(orgUnit,function(groupCat){
                var ou = groupCat.orgUnit;
                if(ou){
                    if(displayCache[ou.name]){
                        displayCache[ou.name].remove();
                    }
                    var ouV = displayCache[ou.name] = $("<div />",{
                        text:ou.name
                    }).appendTo(importV);
                    var groupId = 0;
                    var groupSet = groupCat.groupSet;
                    var groupSetV = $("<div />",{
                    }).appendTo(ouV);
                    groupSetV.append("<div />",{
                        class:"groupCatName",
                        text:groupSet.name
                    });
                    _.each(groupCat.groups,function(group){
                        groupId++;
                        var groupV = $("<div />",{
                            class:"groupBuilderGroup",
                            text:group.name
                        }).appendTo(groupSetV);
                        var inputId = sprintf("structuralGroup_%s",groupId);
                        var inputV = $("<input />",{
                            type:"checkbox",
                            id:inputId
                        }).on("click",function(){
                            if(_.includes(initialGroups,group)){
                                initialGroups = _.without(initialGroups,group);
                            }
                            else{
                                initialGroups.push(group);
                            }
                            render();
                        }).appendTo(groupV);
                        inputV.prop("checked",_.includes(initialGroups,group));
                        $("<label />",{
                            for:inputId
                        }).append($("<span />",{
                            class:"icon-txt",
                            text:"Copy"
                        })).appendTo(groupV);
                        _.each(group.members,function(member){
                            renderMember(member.name).appendTo(groupV);
                        });
                    });
                }
            });
        });
    }
    var simulate = function(strategy,parameter){
        var attendees = _.without(Participants.getPossibleParticipants(),Conversations.getCurrentConversation().author);
        var groups = _.map(initialGroups,function(g){
	    return g.members;
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
    var render = function(){
        var container = $("#groupsPopup");
        var composition = $("#groupComposition").empty();
        var importV = container.find(".importGroups").empty();
        var groupsV = container.find(".groups").empty();
        var slide = Conversations.getCurrentSlide();
        var strategySelect = $("<select />",{
            id:"strategySelect"
        }).appendTo(composition);
        var parameterSelect = $("<select />",{
            id:"parameterSelect"
        }).appendTo(composition);
        var doAllocation = $("#doAllocation").off("click").on("click",function(){
            Conversations.addGroupSlide(
                strategySelect.val(),
                parseInt(parameterSelect.val()),
                _.map(initialGroups,function(group){return _.map(group.members,"name")}));
            initialGroups = [];
        });
        _.each([
            ["there are","byTotalGroups"],
            ["each has","byMaximumSize"]],function(params){
                $("<option />",{
                    text:params[0],
                    value:params[1]
                }).appendTo(strategySelect);
            });
        var doSimulation = function(){
            var simulated = simulate(strategySelect.val(),parameterSelect.val());
            groupsV.empty();
            _.each(simulated,function(group){
                var g = $("<div />",{
                    class:"groupBuilderGroup ghost"
                });
                _.each(group,function(member){
                    renderMember(member).appendTo(g);
                });
                g.appendTo(groupsV);
            });
        };
        parameterSelect.on("change",function(){
            doSimulation()
        });

        strategySelect.on("change",function(){
            var strategy = $(this).val();
            parameterSelect.empty();
            switch(strategy){
            case "byTotalGroups":
                _.each(_.range(2,10),function(i){
                    $("<option />",{
                        text:sprintf("%s groups in total",i),
                        value:i
                    }).appendTo(parameterSelect);
                });
                parameterSelect.val(5).change();
                break;
            case "byMaximumSize":
                _.each(_.range(1,10),function(i){
                    $("<option />",{
                        text:i == 1 ? "only one member" : sprintf("at most %s members",i),
                        value:i
                    }).appendTo(parameterSelect);
                });
                parameterSelect.val(4).change();
                break;
            }
        });
        strategySelect.val("byMaximumSize").change();
        var allocatedV = container.find(".allocatedMembers").empty();
        var unallocatedV = container.find(".unallocatedMembers").empty();
        var unallocatedMembers = _.clone(Participants.getParticipants());
        delete unallocatedMembers[Conversations.getCurrentConversation.author];
        var allocatedMembers = {};
        if(slide){
            if(slide.groupSets.length){
                parameterSelect.prop("disabled",true);
                strategySelect.prop("disabled",true);
                doAllocation.prop("disabled",true);
                $("#importContainer").hide();
            }
            else{
                $("#importContainer").show();
            }
            var strategy;
            var parameter;
            _.each(slide.groupSets,function(groupSet){
                _.each(_.sortBy(groupSet.groups,"title"),function(group){
                    var g = $("<div />",{
                        class:"groupBuilderGroup"
                    }).droppable({
                        drop:function(e,ui){
                            var members = $(ui.draggable).find(".groupBuilderMember");
                            _.each(members,function(memberV){
                                var member = $(memberV).text();
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
                    _.each(group.members,function(member){
                        delete unallocatedMembers[member];
                        allocatedMembers[member] = 1;
                        renderMember(member).draggable().appendTo(g);
                    });
                    $("<div />",{
                        class:"title",
                        text:sprintf("Group %s",group.title)
                    }).prependTo(g);
                    g.appendTo(groupsV);
                });
            });
        }
        _.each(allocatedMembers,function(member,name){
            $("<div />",{
                text:name,
                class:"member"
            }).appendTo(allocatedV);
        });
        _.each(unallocatedMembers,function(member,name){
            $("<div />",{
                text:name,
                class:"member"
            }).appendTo(unallocatedV);
        });
        renderExternalGroups();
    };
    Progress.groupProvidersReceived["GroupBuilder"] = function(args){
        var select = $("#ouSelector").empty();
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
                getOrgUnitsFromGroupProviders(choice);
            }
        });
    };
    Progress.groupsReceived["GroupBuilder"] = function(args){
        var byOrgUnit = externalGroups[args.orgUnit.name];
        if (byOrgUnit === undefined){
            byOrgUnit = {};
            externalGroups[args.orgUnit.name] = byOrgUnit;
        }
        byOrgUnit[args.groupSet.name] = args;
        renderExternalGroups();
    };
    Progress.currentSlideJidReceived["GroupBuilder"] = render;
    Progress.conversationDetailsReceived["GroupBuilder"] = function(){
        getGroupsProviders();
        render();
    }
    return {};
})();
