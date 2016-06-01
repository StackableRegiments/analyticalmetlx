var Participants = (function(){
    $(function(){
        $("#menuParticipants").click(function(){
            showBackstage("participants");
            updateActiveMenu(this);
            var participantsList = $("#participantsListingContainer")
            var p = participantsList.find(".participation")
            var attendees = boardContent.attendances;
            _.each(_.groupBy(attendees,"author"),function(attendancesByAuthor){
                p.clone()
                    .find(".user").text(attendancesByAuthor[0].author)
                    .find(".attendanceCount").text(attendancesByAuthor.length)
		    .addClass(_.last(attendancesByAuthor).present ? "presentAttendee" : "absentAttendee")
                    .appendTo(participantsList);
            });
        });
    });
})();
