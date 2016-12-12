var GroupBuilder = (function(){
    var render = function(){
        var container = $("#groupsPopup");
        var composition = $("#groupComposition").empty();
        var doAllocation = $("#doAllocation").prop("disabled",true);
        var slide = Conversations.getCurrentSlide();
        _.each([
            ["A set number of groups","byTotalGroups",[3,4,5]],
            ["A maximum number of people","byMaximumSize",[3,4,5]],
            ["Everybody in their own group","onePersonPerGroup",["One group each"]],
            ["Everybody in the same group","everyoneInOneGroup",["Only one group"]]
        ],function(params){
            var strategyContainer = $("<fieldset />")
                    .append($("<label />",{text:params[0]}));
            _.each(params[2],function(p){
                var strategy = $("<div />",{
		    class:sprintf("strategy%s",params[1])
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
                        switch(currentStrategy.name){
                        case "byTotalGroups": if(currentStrategy.groupCount == p) input.prop("checked",true); break;
                        case "byMaximumSize": if(currentStrategy.groupSize == p) input.prop("checked",true); break;
                        default: input.prop("checked",true);break;
                        }
                    }
                }
                strategy.on('click',function(){
                    input.prop('checked',true);
                    doAllocation.prop("disabled",false);
                    doAllocation.off("click").on("click",function(){
                        if(slide.groupSets.length){
                            var replacement = {
                                name:params[1]
                            };
                            switch(params[1]){
                            case "byTotalGroups": replacement.groupCount = p; break;
                            case "byMaximumSize": replacement.groupSize = p; break;
                            }
                            slide.groupSets[0].groupingStrategy = replacement;
                            Conversations.overrideAllocation(slide);
                        }
                        else{
                            Conversations.addGroupSlide(params[1],p);
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
            _.each(slide.groupSets,function(groupSet){
                _.each(groupSet.groups,function(group){
                    var g = $("<div />",{
                        class:"groupBuilderGroup"
                    }).droppable({
                        drop:function(e,ui){
                            var member = $(ui.draggable).text();
                            if(! _.includes(group.members,member)){
                                _.each(groupSet.groups,function(g){
                                    g.members = _.without(g.members,member);
                                });
                                group.members.push(member);
                                render();
                                Conversations.overrideAllocation(slide);
                            }
                        }
                    });
                    _.each(group.members,function(member){
                        delete unallocatedMembers[member];
                        allocatedMembers[member] = 1;
                        $("<div />",{
                            text:member
                        }).draggable().appendTo(g);
                    });
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
    };
    Progress.currentSlideJidReceived["GroupBuilder"] = render;
    Progress.conversationDetailsReceived["GroupBuilder"] = render;
    return {};
})();
