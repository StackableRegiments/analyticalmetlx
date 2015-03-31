var Guilds = (function(){
    var guilds = [];
})();
var Peers = (function(){
    var peers = [];
    var population = 932;
    var generatePeer = function(){
        var speed = Math.random() * 100;
        var location = {
            x:0,
            y:0
        };
        var move = function(){
            var delta = Math.random() * speed;
            location.x += delta;
            location.y += delta;
        }
        return {
            act:function(){
                var roll = Math.random();
                move();
                //var stanza = Modes.insert.createBlankText(location.x,location.y);
            },
            react:function(stimulus){
            }
        };
    }
    for(var i = 0; i < 100; i++){
        peers.push(generatePeer());
    }
    var redraw = function(){
        $("#peerTallySummary").text(sprintf("%s of %s peers online",peers.length,population));
    };
    /*
     setInterval(function(){
     $.each(peers,function(i,p){
     p.act();
     });
     redraw();
     },10000);
     $(function(){
     $("#peers").dialog()
     .prev('.ui-dialog-titlebar')
     .find('a')
     .hide();
     });
     */
    return {};
})();
function dialog(element,opts){
    element.dialog(opts)
        .prev('.ui-dialog-titlebar')
        .find('a')
        .hide();
}