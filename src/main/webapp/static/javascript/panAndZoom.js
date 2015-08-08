var MeTLCanvas = (function(){
  var svgns = "http://www.w3.org/2000/svg";
  var zoomRectId = "#zoomRect";
  var minWidth = 30;
  var minHeight = 40;
  function svg(){
    return $('svg');
  }
  function rect(){
    return $(zoomRectId);
  }
  function getX(e){
    return Math.round(e.pageX - svg().offset().left);
  }
  function getY(e){
    return Math.round(e.pageY - svg().offset().top);
  }
  function stop(){
    svg().unbind("mousedown").unbind("mousemove").unbind("mouseup").attr("style","cursor:inherit;")
  }
  function rectCenter(){
    var r = rect();
    return {
      x:parseInt(r.attr("x")) + parseInt(r.attr("width")) / 2,
      y:parseInt(r.attr("y")) + parseInt(r.attr("height")) / 2
    };
  }
  function append(nodeName,attributes){
    var node = document.createElementNS(svgns, nodeName);
    $.each(attributes,function(k,v){
      node.setAttributeNS(null,k,v);
    });
    svg().append(node);
  }
  function startRect(e){
    stop();
    append("rect",{
      x:getX(e),
      y:getY(e),
      width:minWidth,
      height:minHeight,
      fill:"green",
      stroke:"red",
      'stroke-width':5,
      id:zoomRectId.slice(1)
    });
    svg().bind("mousemove",updateZoom).bind("mouseup",zoomSpecified);
  }
  function updateZoom(e){
    var x = getX(e);
    var y = getY(e);
    var center = rectCenter();
    var width = Math.max(minWidth,Math.abs((center.x - x) * 2));
    var height = Math.max(minHeight,Math.abs((center.y - y) * 2));
    rect().attr({
      x:x,
      y:y,
      width:width,
      height:height
    });
  }
  function zoomSpecified(){
    var r = rect();
    svg().attr("viewBox",sprintf("%s %s %s %s",r.attr("x"),r.attr("y"),r.attr("width"),r.attr("height")))
    rect().remove();
  }
  function pan(x,y){
    var delta = [-x,-y,0,0];//We don't modify the size
    return svg().attr("viewBox").split(' ').map(parseFloat).map(function(coord,i){
      return coord + delta[i];
    }).join(" ");
  }
  function startPanning(eDown){
    var referencePoint = eDown;
    function enactPan(eMove){
      var deltaX = eMove.clientX - referencePoint.clientX;
      var deltaY = eMove.clientY - referencePoint.clientY;
      referencePoint = eMove;
      svg().attr("viewBox",pan(deltaX,deltaY));
    }
    function removePan(){
      svg().unbind("mousemove").unbind("mouseup");
    }
    svg().bind("mousemove",enactPan).bind("mouseup",removePan);
  }
  function startDrawing(eDown){
    var points = [];
    var canvas = $("<canvas style='position:absolute;top:0;left:0;width:100%;height:100%;' />");
    canvas.bind("mousemove",updateDrawing).bind("mouseup",stopDrawing);
    svg().after(canvas);
    var context = canvas[0].getContext("2d");
    function x(pt){
			return pt;
      return pt - canvas.offset().left;
    }
    function y(pt){
			return y;
      return pt - canvas.offset().top;
    }
    context.strokeStyle = "black";
    context.moveTo(x(eDown.clientX), y(eDown.clientY));
    function updateDrawing(eMove){
      context.lineTo(x(eMove.clientX),y(eMove.clientY));
      context.stroke();
    }
    function stopDrawing(){
      canvas.remove();
    }
  }
  return {
    zoom:
    {
      start:function(){
        stop();
        svg().bind("mousedown",startRect);
      },
      stop:stop
    },
    pan:{
      start:function(){
        stop();
        svg().attr("style","cursor:move;");
        svg().bind("mousedown",startPanning);
      },
      stop:stop
    },
    draw:{
      start:function(){
        stop();
        svg().attr("style","cursor:crosshair;");
        svg().bind("mousedown",startDrawing);
      },
      stop:stop
    },
    append:append
  }
})();
$(function(){
  MeTLCanvas.zoom.start();
});
