var GroupBuilder = (function(){
    var render = function(){
        var container = $("#groupsPopup");
        var allocatedV = container.find(".allocatedMembers").empty();
        var unallocatedV = container.find(".unallocatedMembers").empty();
        var groupsV = container.find(".groups").empty();
        var unallocatedMembers = _.clone(Participants.getParticipants());
	delete unallocatedMembers[Conversations.getCurrentConversation.author];
        var allocatedMembers = {};
        var slide = Conversations.getCurrentSlide();
        if(slide){
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
