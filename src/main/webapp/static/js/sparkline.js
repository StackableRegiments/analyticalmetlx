/*Based on http://bl.ocks.org/benjchristensen/1148374, updated for d3.v4*/
/* thanks to 'barrym' for examples of transform: https://gist.github.com/1137131 */
var SparkLine = (function(){
    function invert(i){
	return 0 - i;
    }
    function svgLine(container, data, width, height, updateDelay, transitionDelay) {
        var graph = d3.select(container[0]).append("svg:svg").attr("class","sparkline").attr("width", width).attr("height", height);
        var x = d3.scaleLinear().domain([0,data[0].length]).range([0, width]);
        var y = d3.scaleLinear().domain(d3.extent(data)).range([0, height]);

        var line = d3.line()
                .x(function(d,i) {
                    return x(i);
                })
                .y(function(d) {
                    return height - y(d);
                })
                .curve(d3.curveBasis);

        graph.selectAll("path").data(data).enter().append("svg:path").attr("d", line);

        function redraw(data) {
	    data[0] = _.clone(data[0]);
	    data[1] = _.map(data[1],invert);
            var extents = _.map(data,function(datum){
                datum.unshift(0);
                datum.push(0);
		return d3.extent(datum);
            });
            y.domain([Math.min(extents[0][0],extents[1][0]),
		      Math.max(extents[0][1],extents[1][1])]);
            graph.selectAll("path")
                .data(data)
                .attr("d", line)
		.attr("fill",function(d,i){
		    return ["green","red"][i];
		})
        }
        return redraw;
    };
    return {
        svg:svgLine
    };
})();
