var layouts = (function(){
    return {
	clumps:{
        force:function(parent,nodes,links){
	    var scale = 0.5;
            var width = parent.width() * scale,
                height = parent.height() * scale;
	    var offset = parent.width() - width;

            var color = d3.scale.category20();

            var force = d3.layout.force().charge(-30).linkDistance(20).gravity(0.2).size([width, height]);

	    parent.find("svg").remove();
            var svg = d3.select("#"+parent.attr("id")).append("svg").attr("width", width).attr("height", height);
            parent.find("svg").css({
                position:"absolute",
                top:px(offset/2),
                left:px(offset/2)
            });

            force.nodes(nodes).links(links);
            force.start();

            var link = svg.selectAll("line.link").data(links).enter().append("line").attr("class", "link").style("stroke-width", function(d) {
                return Math.sqrt(d.value);
            }).style("stroke","white");

            var node = svg.selectAll("circle.node").data(nodes).enter().append("circle").attr("class", "node").attr("r", 5).style("fill", function(d) {
                return color(d.group);
            }).call(force.drag);

            node.append("title").text(function(d) {
                return d.name;
            });

            force.on("tick", function() {
                link.attr("x1", function(d) {
                    return d.source.x;
                }).attr("y1", function(d) {
                    return d.source.y;
                }).attr("x2", function(d) {
                    return d.target.x;
                }).attr("y2", function(d) {
                    return d.target.y;
                });

                node.attr("cx", function(d) {
                    return d.x;
                }).attr("cy", function(d) {
                    return d.y;
                });
            });
        }
	    }
    }
})();