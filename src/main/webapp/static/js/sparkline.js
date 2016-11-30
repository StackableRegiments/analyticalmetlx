/*Based on http://bl.ocks.org/benjchristensen/1148374, updated for d3.v4*/
/* thanks to 'barrym' for examples of transform: https://gist.github.com/1137131 */
var SparkLine = (function(){
    function svgLine(container, data, width, height, updateDelay, transitionDelay) {
        var graph = d3.select(container[0]).append("svg:svg").attr("class","sparkline").attr("width", width).attr("height", height);
        var x = d3.scaleLinear().domain([0,data.length]).range([0, width]);
        var y = d3.scaleLinear().domain(d3.extent(data)).range([0, height]);

        var line = d3.line()
                .x(function(d,i) {
                    return x(i);
                })
                .y(function(d) {
                    return height - y(d);
                })
                .curve(d3.curveBasis);

        graph.selectAll("path").data([data]).enter().append("svg:path").attr("d", line);

        function redraw(data) {
            WorkQueue.enqueue(function(){
                y.domain(d3.extent(data));
                data.unshift(0);
                data.push(0);
                graph.selectAll("path")
                    .data([data])
                    .attr("d", line)
            });
        }
        return redraw;
    };
    return {
        svg:svgLine,
        sparkline:function(elemId, data) {
            $(elemId).sparkline(data,{type:"line"});
        }
    };
})();
