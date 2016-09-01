var WordCloud = function(data){
    var fill = d3.schemeCategory20b;

    var w = 400;
    var h = 300;

    var max;
    var fontSize;

    var layout = d3.layout.cloud()
            .timeInterval(Infinity)
            .size([w, h])
            .fontSize(function(d) {
                return fontSize(+d.value);
            })
            .text(function(d) {
                return d.key;
            })
            .on("end", draw);

    $("#lang").empty();
    var svg = d3.select("#lang").append("svg")
            .attr("width", w)
            .attr("height", h);

    var vis = svg.append("g").attr("transform", "translate(" + [w >> 1, h >> 1] + ")");

    update();

    window.onresize = function(event) {
        update();
    };

    function draw() {
        console.log("drawing",data);
        var text = vis.selectAll("text")
                .data(data, function(d) {
                    return d.key.toLowerCase();
                });
        text.transition()
            .duration(1000)
            .attr("transform", function(d) {
                return "translate(" + [d.x, d.y] + ")rotate(" + d.rotate + ")";
            })
            .style("font-size", function(d) {
                return d.size + "px";
            });
        text.enter().append("text")
            .text(function(d) {
                return d.key;
            })
            .attr("text-anchor", "middle")
            .attr("transform", function(d) {
                return "translate(" + [d.x, d.y] + ")rotate(" + d.rotate + ")";
            })
            .style("font-size", function(d) {
                return d.size + "px";
            })
            .style("opacity", 1e-6)
            .transition()
            .duration(1000)
            .style("opacity", 1)
            .style("font-family", function(d) {
                return d.font;
            })
    }

    function update() {
        layout.font('impact').spiral('archimedean');
        fontSize = d3.scaleSqrt().range([10, 100]);
        if (data.length){
            fontSize.domain([+data[data.length - 1].value || 1, +data[0].value]);
        }
        layout.stop().words(data).start();
    }
};
