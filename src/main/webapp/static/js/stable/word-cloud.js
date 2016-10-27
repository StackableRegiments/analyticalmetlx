var WordCloud = function(data,options){
    console.log("wordcloud",data);
    var fill = d3.scaleOrdinal(d3.schemeCategory20b);
    options = options || {};
    /*
    data = _.flatten(
        _.map(data,function(d){
            return _.map(_.range(2,50),function(i){
                return {key:d.key+i, value:d.value * i};
            })
        })
    );
     */

    var w = options.w || 400;
    var h = options.h || 300;
    var lw = options.lw || w;
    var lh = options.lh || h;
    var target = options.target || "#lang";
    var contexts = options.contexts || {};

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

    $(target).empty();
    var svg = d3.select(target).append("svg")
    svg.attr("viewBox","0 0 "+lw+" "+lh)
        .attr("width", w)
        .attr("height", h);

    var vis = svg.append("g").attr("transform", "translate(" + [w >> 1, h >> 1] + ")");

    update();

    window.onresize = function(event) {
        update();
    };

    function draw() {
        var text = vis.selectAll(".word")
                .data(data, function(d) {
                    return d.key.toLowerCase();
                })
                .enter()
                .append("g")
                .attr("class","word")
                .attr("transform", function(d) {
                    return "translate(" + [d.x, d.y] + ")rotate(" + d.rotate + ")";
                });
        text.append("text")
            .text(function(d) {
                return d.key;
            })
            .attr("stroke",function(d){
                var context = contexts[d.key];
                if(!context){
                    return "none";
                }
                if(context.keyboarding || context.handwriting){
                    if(context.imageTranscription || context.imageRecognition){
                        return "purple";
                    }
                    return "none";
                }
                return "red";
            })
            .attr("stroke-width",function(d){
                return fontSize(d.value) / 70;
            })
            .attr("text-anchor", "middle")
            .style("fill","black")
            .style("font-size", function(d) {
                return d.size + "px";
            })
            .style("opacity", 1)
            .style("font-family", function(d) {
                return d.font;
            })
    }

    function update() {
        layout.font('impact').spiral('archimedean');
        fontSize = d3.scaleLinear().range([8, 50]);
        if (data.length){
            fontSize.domain(d3.extent(_.map(data,"value")));
        }
        layout.stop().words(data).start();
    }
};
