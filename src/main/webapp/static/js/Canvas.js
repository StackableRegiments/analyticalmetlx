window.Canvas = {
    swirl:function(color,lineWidth,buttonWidth,highlighter){
        var c = $("<canvas />");
        var context = c[0].getContext("2d");
        var padding = 5;
        c.attr("width",px(buttonWidth)),
        c.attr("height",px(buttonWidth))
        c.css({
            "margin-top":"-0.5em",
            "border-radius":px(5)
        });
        context.fillStyle = "white";
        context.fillRect(0,0,buttonWidth,buttonWidth);
        var doSwirl = function(){
            context.strokeStyle = color;
            context.lineWidth = lineWidth;
            var centerX = 3;
            var centerY = (buttonWidth / 4) + 6;
            var a = 1;
            var b = 4;

            context.moveTo(centerX, centerY);
            context.beginPath();
            for (var i = 0; i < buttonWidth; i++) {
                var angle = 0.2 * i;
                var x = centerX + i;//(a + b * angle) * Math.cos(angle);
                var y = centerY + ((a + b * angle) * Math.sin(angle) * 0.8);

                context.lineTo(x, y);
            }
            context.stroke();
        };
        doSwirl();
        if(highlighter){
            lineWidth = 1;
            color = "black";
            context.fillStyle = color;
            doSwirl();
        }
        return c;
    },
    circle:function(color,diameter,canvasSize){
	canvasSize = canvasSize || diameter;
        var dot = $("<canvas />");
        var offset = canvasSize / 2;
        dot.attr("width",canvasSize);
        dot.attr("height",canvasSize);
        dot.css({
            width:px(canvasSize),
            height:px(canvasSize)
        });
        var dotC = dot[0].getContext("2d");
        dotC.fillStyle = color;
        dotC.strokeStyle = color;
        dotC.beginPath();
        dotC.arc(offset,offset,diameter / 2,0,Math.PI*2);
        dotC.closePath();
        dotC.fill();
        return dot;
    }
};
