(function($){
    var k = "explanationText";
    $.fn.explanationText = function(text){
        this.data(k,text);
        return this;
    };
    $.fn.unexplain = function(){
        this.find(".explanationLabel").remove();
        return this;
    };
    $.fn.explain = function(positioningParent,textOrigin){
        var e = "showExplanation";
        var base = this;
        base.addClass("explicable");
        if(arguments.length > 0){
	    base.unbind(e);
            base.bind(e,function(){
                var explanation = $("<div />",{
                    text:base.data(k)
                }).addClass("explanationLabel").click(function(){
                    base.parent().unexplain();
		    $("#explainAll").flash();
                });
                explanation.css({
                    "background-color":"white",
                    "white-space":"nowrap",
                    padding:"1em",
                    border:"1px solid black",
                    position:"absolute"
                });
                positioningParent.append(explanation);
                var w = explanation.width();
                var xOffset = w / 2;
                if(textOrigin.x <= positioningParent.width() / 2){
                    xOffset -= w * 2;
                }
                else{
                }
                explanation.css({
                    left:px(textOrigin.x + xOffset),
                    top:px(textOrigin.y)
                });
            });
        }
        else {
            base.trigger(e);
        }
        return base;
    };
})(jQuery);
window.Canvas = (function(){
    return {
        swirl:function(context,width,height){
            context.strokeStyle = brush.color;
            context.lineWidth = brush.width;
            var centerx = (width / 2) - 3;
            var centery = (height / 2) + 6;
            var a = 1;
            var b = 4;

            context.moveTo(centerx, centery);
            context.beginPath();
            for (var i = 0; i < 60; i++) {
                var angle = 0.1 * i;
                var x = centerx + (a + b * angle) * Math.cos(angle);
                var y = centery + (a + b * angle) * Math.sin(angle);

                context.lineTo(x, y);
            }
            context.stroke();
        },
        circle:function(color,diameter){
            var dot = $("<canvas />");
            var offset = diameter / 2;
            dot.attr("width",diameter);
            dot.attr("height",diameter);
            dot.css({
                width:px(diameter),
                height:px(diameter)
            });
            var dotC = dot[0].getContext("2d");
            dotC.fillStyle = color;
            dotC.strokeStyle = color;
            dotC.beginPath();
            dotC.arc(offset,offset,offset,0,Math.PI*2);
            dotC.closePath();
            dotC.fill();
            return dot;
        },
        button:function(diameter,opts,text){
            var clipCircle = function(radius){
                context.beginPath();
                context.arc(
                    diameter/2,
                    diameter/2,
                    radius,
                    0,Math.PI * 2);
                context.clip();
            }
            var strokeCircle = function(radius,color){
                color = color || "black";
                context.lineWidth = radius / 12;
                context.strokeStyle = color;
                context.beginPath();
                context.arc(
                    diameter/2,
                    diameter/2,
                    radius - context.lineWidth / 2,
                    0,Math.PI*2);
                context.stroke();
            }
            var fillCircle = function(radius,color){
                color = color || "white";
                context.lineWidth = 3;
                context.fillStyle = color;
                context.beginPath();
                context.arc(
                    diameter/2,
                    diameter/2,
                    radius - context.lineWidth / 2,
                    0,Math.PI*2);
                context.fill();
            }
            var shadeCircle = function(radius,start,end){
                var d = radius * 2;
                var gradient = context.createLinearGradient(0,d,d,0);
                gradient.addColorStop(0,"black");
                gradient.addColorStop(0.3,"white");
                gradient.addColorStop(1,"black");
                context.fillStyle = gradient;
                var inset = (diameter - d) / 2;
                context.fillRect(inset,inset,d,d);
            }
            var iconWidth = diameter / 2;
            var c = Canvas.circle("black",diameter);
	    c.attr("title",text);
            var context = c[0].getContext("2d");
            var radius = diameter / 2;
            clipCircle(radius);
            if(opts.transparent){
                context.clearRect(0,0,diameter,diameter);
            }
            else{
                fillCircle(radius);
            }
            strokeCircle(radius);
            radius = diameter / 2.5;
            if(opts.transparent){}
            else{
                clipCircle(radius);
                fillCircle(radius,opts.color);
            }
            if(opts.icon){
                var iconInset = (diameter - iconWidth) / 2;
                var img = new Image();
                img.onload = function(){
                    context.drawImage(img,iconInset,iconInset,iconWidth,iconWidth);
                    strokeCircle(radius);
                };
                img.src = sprintf("/static/images/%s",opts.icon);
            }
            if(opts.glyph){
                var fontSize = diameter / 2;
                context.fillStyle = opts.glyphColor;
                context.font = sprintf("bold %spt Arial",fontSize);
                var glyphWidth = context.measureText(opts.glyph).width;
                context.fillText(opts.glyph,diameter/2 - glyphWidth / 2,diameter/2 + fontSize / 2);
            }
            if(opts.draw){
                opts.draw(context,diameter,diameter);
            }
            return c.explanationText(text);
        }
    }
})();