var Profiles = (function(){
    var list = function(username,image,caption){
        return {
            username:username,
            image:image,
            caption:caption
        };
    }
    var listing = {};
    var profiles = [
        list("forbidden","forbidden.png","The forbidden role is assumed by any developer who is running his server in dev mode.  If you can see it there's something wrong."),
        list("chagan","chagan.jpg","Chris built this software.  Everything that's wrong with it his fault.  Everything good about it is to his manager's credit though."),
        list("gsanson","gsanson.png",'Gordon "Gordon Sanson" Sanson is the only person in the western world allowed to use his own full name as a nickname.')
    ]
    $.each(profiles,function(i,profile){
        listing[profile.username] = profile;
    });
    return {
        list:listing
    }
})();