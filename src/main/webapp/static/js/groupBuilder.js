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
    var renderExternalGroups = function(args){
        $(".findExisting").removeClass("fa-spin");
        externalGroups[args.orgUnit] = args;
        var ou = args.orgUnit;
        var container = $("#groupsPopup");
        var importV = container.find(".importGroups").empty();
        if(ou){
            if(displayCache[ou.name]){
                displayCache[ou.name].remove();
            }
            var ouV = displayCache[ou.name] = $("<div />",{
                text:ou.name
            }).appendTo(importV);
            var groupId = 0;
            _.each(ou.groupSets,function(groupSet){
                var groupSetV = $("<div />",{
                }).appendTo(ouV);
                _.each(groupSet.groups,function(group){
                    groupId++;
                    var groupV = $("<div />",{
                        class:"groupBuilderGroup"
                    }).appendTo(groupSetV);
                    var inputId = sprintf("structuralGroup_%s",groupId);
                    var inputV = $("<input />",{
                        type:"checkbox",
                        id:inputId
                    }).on("click",function(){
                        initialGroups.push(group);
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
            });
        }
    }
    var render = function(){
        var container = $("#groupsPopup");
        var composition = $("#groupComposition").empty();
        var importV = container.find(".importGroups").empty();
        var slide = Conversations.getCurrentSlide();
        var findExisting = $(".findExisting").off("click").on("click",function(){
            $(".findExisting").addClass("fa-spin");
            getGroupsProviders();
        });
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
            ["each has","byMaximumSize"],
            ["everyone has their own","groupsOfOne"]],function(params){
                $("<option />",{
                    text:params[0],
                    value:params[1]
                }).appendTo(strategySelect);
            });
        strategySelect.on("change",function(){
            var strategy = $(this).val();
            parameterSelect.empty();
            switch(strategy){
            case "byTotalGroups":
                _.each(_.range(1,10),function(i){
                    $("<option />",{
                        text:i,
                        value:i
                    }).appendTo(parameterSelect);
                });
                parameterSelect.val(5);
                break;
            case "byMaximumSize":
                _.each(_.range(1,10),function(i){
                    $("<option />",{
                        text:i,
                        value:i
                    }).appendTo(parameterSelect);
                });
                parameterSelect.val(4);
                break;
            case "groupsOfOne":
                parameterSelect.append($("<option />",{
                    text:"-",
                    value:"-"
                }));
                break;
            }
        });
        strategySelect.val("byMaximumSize").change();
        var allocatedV = container.find(".allocatedMembers").empty();
        var unallocatedV = container.find(".unallocatedMembers").empty();
        var groupsV = container.find(".groups").empty();
        var unallocatedMembers = _.clone(Participants.getParticipants());
        delete unallocatedMembers[Conversations.getCurrentConversation.author];
        var allocatedMembers = {};
        if(slide){
	    if(slide.groupSets.length){
		parameterSelect.prop("disabled",true);
		strategySelect.prop("disabled",true);
		doAllocation.prop("disabled",true);
	    }
            var strategy;
            var parameter;
            _.each(initialGroups,function(group){
                var g = $("<div />",{
                    class:"groupBuilderGroup ghost"
                });
                _.each(group.members,function(member){
                    renderMember(member.name).appendTo(g);
                });
                g.appendTo(groupsV);
            });
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
	_.each(externalGroups,renderExternalGroups);
    };
    Progress.groupsReceived["GroupBuilder"] = renderExternalGroups;
    Progress.currentSlideJidReceived["GroupBuilder"] = render;
    Progress.conversationDetailsReceived["GroupBuilder"] = render;
    return {};
})();
