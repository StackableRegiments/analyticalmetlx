function serverResponse(response){
    console.log("serverResponse:",response);
}
function receiveUsername(username){
    console.log("receiveUsername:",username);
}
function receiveUserGroups(userGroups){
    console.log("receiveUserGroups:",userGroups);
}
function receiveConversationDetails(details){
    console.log("receiveConversationDetails:",details);
}
function receiveConversations(conversations){
    console.log("receiveConversations:",conversations);
}
function receiveNewConversationDetails(details){
    console.log("receiveNewConversationDetails:",details);
}
$(function(){
    $('#activeImportsListing').hide();
    $('#importConversationInputElement').fileupload({
        dataType: 'json',
        add: function (e,data) {
            $('#importConversationProgress').css('width', '0%');
            $('#importConversationProgressBar').show();
            $('#activeImportsListing').show();
            data.submit();
        },
        progressall: function (e, data) {
            var progress = parseInt(data.loaded / data.total * 100, 10) + '%';
            $('#importConversationProgressBar').css('width', progress);
        },
        done: function (e, data) {
            $.each(data.files, function (index, file) {
                $('<p/>').text(file.name).appendTo(document.body);
            });
            $('#importConversationProgress').fadeOut();
        }
    });
});
