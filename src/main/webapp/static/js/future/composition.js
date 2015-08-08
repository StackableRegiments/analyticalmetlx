var Composition = (function(){
    var capabilities = ["Draw","Insert","Select","Pan","Zoom"];
    var drawToggles = function(){
        var container = $("#clientCapabilities");
        capabilities.map(function(t){
            $("<div />",{
                class:"toggleLink",
                text:sprintf("Toggle %s tools on main palette",t)
            }).click(
                function(){
                    LabsLayout.toggleTool(t.toLowerCase());
                    Modes.pan.activate();
                })
                .appendTo(container);
        });
    };
    var setPedagogy = function(level){
        return function(){
            switch(level){
            case 1:
                LabsLayout.setTools({});
                Modes.pan.activate();
                Modes.currentMode.deactivate();
                break;
            case 2:
                LabsLayout.setTools({});
                Modes.pan.activate();
                break;
            case 3:
                LabsLayout.setTools({
                    pan:1,
                    quiz:1
                });
                Modes.pan.activate();
                break;
            case 4:
                LabsLayout.setTools({
                    quiz:1,
                    draw:1,
                    select:1,
                    insert:1
                });
                Modes.draw.activate();
                break;
            default:
                LabsLayout.setTools({
                    quiz:1,
                    draw:1,
                    select:1,
                    insert:1,
                    groups:1
                });
                Modes.groups.activate();
                break;
            }
        };
    };
    var drawPedagogicometer = function(){
        var w = LabsLayout.sizes.paletteWidth;
        var b = LabsLayout.sizes.smallButtonWidth;
        var left = [];
        var right = [
            Canvas.button(b,{glyph:"?",glyphColor:"black"},"Legend").click(function(){
                var l = "pedagogicometry";
                if(window.currentBackstage == l){
                    hideBackstage();
                }
                else{
                    showBackstage(l);
                }
            }),
            Canvas.button(b,{glyph:"1",glyphColor:"black",color:"yellow"},"Freedom to leave").click(setPedagogy(1)),
            Canvas.button(b,{glyph:"2",glyphColor:"black",color:"yellow"},"Freedom of motion").click(setPedagogy(2)),
            Canvas.button(b,{glyph:"3",glyphColor:"black",color:"lightgreen"},"Freedom of response").click(setPedagogy(3)),
            Canvas.button(b,{glyph:"4",glyphColor:"black",color:"lightgreen"},"Freedom of action").click(setPedagogy(4)),
            Canvas.button(b,{glyph:"5",glyphColor:"black",color:"red"},"Freedom of community").click(setPedagogy(5))
        ];
        var pp = $("<div />").palette("pedagogicometerPalette",w,{
            left:left,
            right:right,
            position:{
                left:100,
                top:0
            }
        });
        pp.activate();
    };
    return {
        activate : function(){
            drawToggles();
            drawPedagogicometer();
        }
    }
})();
