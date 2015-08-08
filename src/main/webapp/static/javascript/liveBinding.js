function metl_custom_liveBind(id){
    var intervalId = setInterval(function(){
        var s = $('#'+id);
        var v = parseInt(s.val());
        if(v==parseInt(s.attr('max')))
        {
            clearInterval(intervalId);
        }
        else
        {
            s.val(v+1);
            s.change();
        }
    },100);
}