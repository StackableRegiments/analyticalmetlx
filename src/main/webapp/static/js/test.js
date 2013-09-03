var average = function(xs){
    return _.reduce(xs,function(x,acc){return x + acc;},0) / xs.length;
}
var RenderLoad = (function(){
    var slides = {
        "Medium slide":1931005,
        "Large slide":1931007
    };
    var runs = 3;
    $(function(){
	var testId = "test";
        var testControls = $("<div />",{
            id:testId+"Popup",
            class:"backstage"
        });
        $.each(slides,function(k,v){
            $("<input />",{
                type:"button",
                value:k,
                click:function(){
                    loadSlide(v);
                }
            }).css({
                display:"block"
            }).appendTo(testControls);
        });
        $("<input />",{
            type:"button",
            value:"Replay current ink"
        }).click(RenderLoad.receiveInks).appendTo(testControls);
        $("#backstageContainer").append(testControls);
        /*$("#applicationMenuPopup").append($("<input />",{
            type:"button",
            value:"Developer options"
        }).click(function(){
            showBackstage(testId)
        }));*/
        $("<pre />",{
            id:"timingLog"
        }).appendTo(testControls);
        $("<pre />",{
            id:"devLog"
        }).appendTo(testControls);
        $("<pre />",{
            id:"renderTimingLog"
        }).appendTo(testControls);
    });
    return {
        times:_.range(runs).map(function(){
            return [0,0];
        }),
        receiveInks:function(){
            hideBackstage();
            var inks = _.values(boardContent.inks);
            var limit = inks.length;
            boardContent.inks = {};
            var processingTimes = [];
            var fid = "receiveInks";
            var index = 0;
            var reprocessInk = function(i){
                if(i < limit){
                    var start = Date.now();
                    inkReceived(inks[i]);
                    processingTimes.push(Date.now() - start);
                    _.defer(function(){
                        reprocessInk(i+1);
                    });
                }
                else{
                    alert(sprintf("Replaying %s inks averaged %s milis per stroke\n",
                                  _.keys(inks).length,
                                  average(processingTimes).toFixed(2)));
                }
            }
            reprocessInk(0);
        }
    };
})();
