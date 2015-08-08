function radians(degrees){
    return degrees * Math.PI / 180;
}
function cos(angle){
    return Math.cos(radians(angle));
}
function sin(angle){
    return Math.sin(radians(angle));
}
function radiate(root,elements){
    var radius = 70;
    var yOffset = 80;
    var point = root.position();
    var slice = 360 / elements.length;
    elements.each(function(index){
        var element = $(this);
        var angle = slice * (index + 1);
        var x = point.left + (radius * cos(angle));
        var y = point.top + (radius * sin(angle)) + yOffset;
        element.css({left:x+'px',top:y+'px'});
    });
}