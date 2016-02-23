var Participants = (function(){
    $(function(){
        $("#menuParticipants").click(function(){
            showBackstage("participants");
            updateActiveMenu(this);
            var participantsList = $("#participantsListingContainer")
            var p = participantsList.find(".participation")
            _.each(_.groupBy(boardContent.attendances,"author"),function(attendancesByAuthor){
                p.clone()
                    .find(".user").text(attendancesByAuthor[0].author)
                    .appendTo(participantsList);
            });
        });
    });
})();
