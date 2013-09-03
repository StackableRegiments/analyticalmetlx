function springRelations(containerId,root,width){
    var w = width || 960,
        h = w,
        fill = d3.scale.category20(),
        nodes = [],
        links = [];

    var vis = d3.select(containerId).append("svg:svg")
            .attr("width", w)
            .attr("height", h);

    vis.append("svg:rect")
        .attr("width", w)
        .attr("height", h);

    var force = d3.layout.force()
            .nodes(nodes)
            .links(links)
            .size([w, h]);

    var cursor = vis.append("svg:circle")
            .attr("r", 30)
            .attr("transform", "translate(-100,-100)")
            .attr("class", "cursor");

    force.on("tick", function() {
        vis.selectAll("line.link")
            .attr("x1", function(d) { return d.source.x; })
            .attr("y1", function(d) { return d.source.y; })
            .attr("x2", function(d) { return d.target.x; })
            .attr("y2", function(d) { return d.target.y; });

        vis.selectAll("circle.node")
            .attr("cx", function(d) { return d.x; })
            .attr("cy", function(d) { return d.y; });
    });

    vis.on("mousemove", function() {
        cursor.attr("transform", "translate(" + d3.svg.mouse(this) + ")");
    });

    var sequence = chronoWalk(items[root]).sequence;
    var _items = {};
    sequence.forEach(function(item){
        _items[item.id] = item;
    });
    console.log(sequence);
    var i = 0;
    var authorNodes = {
        undefined:{
            x: Math.floor(w / 2),
            y: Math.floor(h / 2),
            index:0,
            weight:2
        }//Anchor parentless nodes to the center of the graph
    };
    var a = function(d){
        if(!d){
            var r = d;
        }
        else if(d.item && d.item.discussion){
            var r = d.item.discussion.author;
        }
        else if(d.author){
            var r = d.author;
        }
        return r;
    }
    var timeout = function(){
        var r = function(){
            return Math.floor(Math.random() * w);
        }
        if(i < sequence.length){
            var d = sequence[i++];
            var newNode = {
                x:r(),
                y:r(),
                index:i,
                weight:2
            };
            var link = {
                source:newNode,
                target:authorNodes[a(_items[d.parent])]
            };
            console.log("node,link",newNode,link);
            authorNodes[a(d)] = newNode;
            nodes.push(newNode);
            links.push(link);
            restart();
            setTimeout(timeout,1000);
        }
    }

    vis.on("mousedown", function() {
        var point = d3.svg.mouse(this),
            node = {x: point[0], y: point[1]},
            n = nodes.push(node);
        // add links to any nearby nodes
        nodes.forEach(function(target) {
            var x = target.x - node.x,
                y = target.y - node.y;
            if (Math.sqrt(x * x + y * y) < 30) {
                links.push({source: node, target: target});
            }
        });
        console.log(nodes);
        restart();
    });
    timeout();
    restart();

    function restart() {
        console.log("Restart",nodes)
        vis.selectAll("line.link")
            .data(links)
            .enter().insert("svg:line", "circle.node")
            .attr("class", "link")
            .attr("x1", function(d) { return d.source.x; })
            .attr("y1", function(d) { return d.source.y; })
            .attr("x2", function(d) { return d.target.x; })
            .attr("y2", function(d) { return d.target.y; });

        vis.selectAll("circle.node")
            .data(nodes)
            .enter().insert("svg:circle", "circle.cursor")
            .attr("class", "node")
            .attr("cx", function(d) { return d.x; })
            .attr("cy", function(d) { return d.y; })
            .attr("r", 5)
            .call(force.drag);

        force.start();
    }
}