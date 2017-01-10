/*Based on http://bl.ocks.org/benjchristensen/1148374, updated for d3.v4*/
/* thanks to 'barrym' for examples of transform: https://gist.github.com/1137131 */
var SparkLine = (function(){
    function invert(i){
        return 0 - i;
    }
    function svgLine(container, data, width, height, updateDelay, transitionDelay) {
        var graph = d3.select(container[0]).append("svg:svg").attr("class","sparkline").attr("width", width).attr("height", height);
        var x = d3.scaleLinear().domain([0,data[0].length]).range([width,0]);
        var y = d3.scaleLinear().domain(d3.extent(data)).range([0, height]);

        var line = d3.line()
                .x(function(d,i) {
                    return x(i);
                })
                .y(function(d) {
                    return height / 2 - y(d);
                })
                .curve(d3.curveBasis);

        var underLine = d3.line()
                .x(function(d,i) {
                    return x(i);
                })
                .y(function(d) {
                    return y(d) + height / 2;
                })
                .curve(d3.curveBasis);

        graph.selectAll("path").data(data).enter().append("svg:path");

        return function(data) {
            data = _.clone(data);
            var extents = _.map(data,function(datum){
                datum.unshift(0);
                datum.push(0);
                return d3.extent(datum);
            });
            switch(extents.length){
            case 2: y.domain([Math.min(extents[0][0],extents[1][0]),
                              Math.max(extents[0][1],extents[1][1])]);
                break;
            case 1: y.domain(extents[0]);
                break;
            }
            graph.selectAll("path")
                .data(data)
                .attr("d", function(d,i){
                    return (i == 0 ? line : underLine)(d);
                })
                .attr("stroke",function(d,i){
                    return ["green","red"][i];
                })
                .attr("fill",function(d,i){
                    return ["green","red"][i];
                })
        };
    };
    return {
        svg:svgLine
    };
})();
