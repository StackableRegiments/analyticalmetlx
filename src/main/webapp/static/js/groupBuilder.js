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
        externalGroups = args;
        $(".findExisting").removeClass("fa-spin");
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
        var doAllocation = $("#doAllocation").prop("disabled",true);
        var importV = container.find(".importGroups").empty();
        var slide = Conversations.getCurrentSlide();
        var findExisting = $(".findExisting").off("click").on("click",function(){
            $(".findExisting").addClass("fa-spin");
            getGroupsProviders();
        });
        _.each([
            ["there are","byTotalGroups",[3,4,5]],
            ["each has","byMaximumSize",[3,4,5]],
            ["everyone has their own","groupsOfOne",["One group each"]]
        ],function(params){
            var strategyContainer = $("<div />")
                    .append($("<label />",{text:params[0]}));
            _.each(params[2],function(p){
                var strategy = $("<div />",{
                    class:sprintf("strategy strategy%s",params[1])
                }).appendTo(strategyContainer);
                var id = _.uniqueId('strategy');
                var input = $("<input />",{
                    type:'radio',
                    name:'strategy',
                    id:id
                }).appendTo(strategy);
                $("<label />",{
                    for:"#"+id
                }).append($("<span />",{
                    text:p,
                    class:"icon-txt"
                })).appendTo(strategy);
                if(slide && slide.groupSets.length){
                    var currentStrategy = slide.groupSets[0].groupingStrategy;
                    if(currentStrategy.name == params[1]){
                        strategyContainer.show();
                        switch(currentStrategy.name){
                        case "byTotalGroups": if(currentStrategy.groupCount == p) input.prop("checked",true); break;
                        case "byMaximumSize": if(currentStrategy.groupSize == p) input.prop("checked",true); break;
                        default: input.prop("checked",true);break;
                        }
                    }
                    else{
                        strategyContainer.hide();
                    }
                    input.prop("disabled",true);
                }
                strategy.on('click',function(){
                    input.prop('checked',true);
                    doAllocation.prop("disabled",slide.groupSets.length != 0);
                    doAllocation.off("click").on("click",function(){
                        if(slide.groupSets.length){
                        }
                        else{
                            Conversations.addGroupSlide(params[1],p,_.map(initialGroups,function(group){
				return _.map(group.members,"name");
                            }));
                            initialGroups = [];
                        }
                    });
                });
            });
            strategyContainer.appendTo(composition);
        });
        var allocatedV = container.find(".allocatedMembers").empty();
        var unallocatedV = container.find(".unallocatedMembers").empty();
        var groupsV = container.find(".groups").empty();
        var unallocatedMembers = _.clone(Participants.getParticipants());
        delete unallocatedMembers[Conversations.getCurrentConversation.author];
        var allocatedMembers = {};
        if(slide){
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
        renderExternalGroups(externalGroups);
    };
    Progress.groupsReceived["GroupBuilder"] = renderExternalGroups;
    Progress.currentSlideJidReceived["GroupBuilder"] = render;
    Progress.conversationDetailsReceived["GroupBuilder"] = render;
    return {};
})();
