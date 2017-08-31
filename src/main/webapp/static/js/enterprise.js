var Enterprise = (function(){
    var displays = {};
    var status = function(msg,key){
        var parent = $("#statusLog");
        if(!(key in displays)){
            displays[key] = {
                element:$("<div />").prependTo(parent),
                touches:0
            };
        }
        displays[key].touches += 1;
        var text = sprintf("%s %s %s",_.repeat(".",displays[key].touches),msg,key)
        displays[key].element.html($("<div />",{
            text:text
        }));
    };

    var DAY = 1000 * 60 * 60 * 24;
    var toDay = function(timestamped){
        var rounded = _.clone(timestamped);
        rounded.timestamp = Math.floor(rounded.timestamp / DAY) * DAY;
        return rounded;
    };
    var perDay = function(timestamped){
        var days = _.groupBy(timestamped,function(d){
            return toDay(d).timestamp;
        });
        var times = _.map(_.keys(days),function(k){
            return parseInt(k);
        });
        var earliestDay = _.min(times);
        var latestDay = _.max(times);
        for(var time = earliestDay; time < latestDay; time += DAY){
            if(!(time in days)){
                days[time] = [];
            }
        }
        return _.sortBy(_.toPairs(days),function(d){
            return d[0];
        });
    }

    var showUpdates = function(updates){
        var sel = "#vis";
        $(sel).empty();
        var margin = {
            top: 10,
            right: 25,
            bottom: 15,
            left: 35
        };
        var width = $(sel).width() - margin.left - margin.right;
        var height = 300;
        var masterHeight = 150;


        var txl = margin.left;
        var txr = width - margin.right;
        var tx = [txl,txr];
        var x = d3.scaleTime().range(tx),
            xM = d3.scaleTime().range(tx),
            xB = d3.scaleBand().range(tx).padding(0.1).align(0),
            y = d3.scaleLinear().range([margin.top, height - margin.bottom - margin.top]),
            yM = d3.scaleLinear().range([masterHeight,margin.bottom]),
            totalsY = d3.scaleLinear().range([masterHeight,margin.bottom]),
            r = d3.scaleLinear().range([0,35]);


        var postMigration = _.groupBy(updates,function(d){
            return parseInt(d.timestamp) > new Date(2014,6,15).getTime();
        });

        function arrayIdentity(d) {
            var a = d.parameters;
            if (_.isArray(a))
                return a[0];
            return a;
        }

        var preMigration = _.keys(_.groupBy(postMigration[false],arrayIdentity)).length;
        updates = postMigration[true];

        var conversations = _.groupBy(updates,arrayIdentity);
        var creationSeparated = _.sortBy(_.flatMap(conversations,function(cs){
            var rounded = _.map(cs,toDay);
            return [
                {
                    timestamp:rounded[0].timestamp,
                    creation:[rounded[0]],
                    update:[]
                }].concat(_.map(rounded.slice(1),function(c){
                    return {
                        timestamp:c.timestamp,
                        creation:[],
                        update:[c]
                    }
                }));
        }),"timestamp");

        var total = 0;
        var stackable = _.reduce(creationSeparated,function(acc,v){
            var k = v.timestamp;

            if(!(k in acc)){
                acc[k] = {
                    timestamp:k,
                    creation:0,
                    update:0
                }
            }
            var day = acc[k];
            day.creation += v.creation.length;
            day.update += v.update.length;
            return acc;
        },{});
        var stacked = d3.stack()
            .keys(["creation","update"])(_.values(stackable));
        var reduced = _.reduce(creationSeparated,function(acc, v){
            var k = v.timestamp;
            acc.total += v.creation.length;
            acc[k] = {
                timestamp:k,
                conversations:acc.total
            };
            return acc;
        },{
            total:0
        });
        delete reduced.total;
        var totalConversations = _.toPairs(reduced);
        var axisTimes = _.sortBy(_.map(stackable,"timestamp"));
        var ts = d3.extent(axisTimes);
        x.domain(ts);
        xM.domain(ts);
        var masterData = perDay(updates);
        yM.domain([0,_.max(masterData.map(function(d){
            return d[1].length;
        }))]);
        var maxConversations = totalConversations[totalConversations.length-1][1].conversations;
        totalsY.domain([0,maxConversations]);
        y.domain([_.max(_.map(masterData,function(d){
            return d[1].length;
        })),0]);
        var authorsPerDay = _.map(masterData,function(d){
            return _.uniq(_.map(d[1],"author"));
        });
        var authorsCount = _.map(authorsPerDay,"length");
        r.domain(d3.extent(authorsCount));

        var authorLevel = d3.select("#ep")
        var authorLine = authorLevel.selectAll(".authorLine")
            .data(_.sortBy(_.toPairs(_.groupBy(updates,"author")),function(d){
                return d[1].length;
            }).reverse())
            .enter()
            .append("tr")
            .attr("class","authorLine")
            .attr("y",function(d,i){
                return i * 25;
            });

        authorLine.append("th")
            .text(function(d){
                return d[0];
            });
        authorLine.append("td")
            .text(function(d){
                return d[1].length;
            });
        var span = authorLine.append("td")
            .append("svg:svg")
            .attr("width", width + margin.left + margin.right)
            .attr("height",20)
            .append("g")
            .attr("transform", "translate(" + 10 + "," + 0 + ")");
        span.selectAll(".span")
            .data(function(d){
                return [_.map(d[1],function(x){
                    return parseInt(x.timestamp);
                })];
            })
            .enter()
            .append("rect")
            .attr("class","span")
            .attr("width",function(d){
                console.log(d);
                return xM(_.max(d)) - xM(_.min(d));
            })
            .attr("height",3)
            .attr("x",function(d){
                return xM(_.min(d));
            })
            .attr("y",8);
        span.selectAll(".circ")
            .data(function(d){
                return d[1];
            })
            .enter()
            .append("circle")
            .attr("class","circ")
            .attr("cx",function(d){
                return xM(d.timestamp);
            })
            .attr("cy",9)
            .attr("r",6);

        var formatMillisecond = d3.timeFormat(".%L"),
            formatSecond = d3.timeFormat(":%S"),
            formatMinute = d3.timeFormat("%I:%M"),
            formatHour = d3.timeFormat("%I %p"),
            formatDay = d3.timeFormat("%a %d"),
            formatWeek = d3.timeFormat("%b %d"),
            formatMonth = d3.timeFormat("%b"),
            formatYear = d3.timeFormat("%Y");

        function multiFormat(date) {
            return (d3.timeSecond(date) < date ? formatMillisecond
                    : d3.timeMinute(date) < date ? formatSecond
                    : d3.timeHour(date) < date ? formatMinute
                    : d3.timeDay(date) < date ? formatHour
                    : d3.timeMonth(date) < date ? (d3.timeWeek(date) < date ? formatDay : formatWeek)
                    : d3.timeYear(date) < date ? formatMonth
                    : formatYear)(date);
        }
        var detailX = d3.axisBottom(x)
            .tickSize(-height, 0)
            .tickFormat(multiFormat)

        var detailY = d3.axisLeft(y)
            .tickSizeInner(-width)

        var masterX = d3.axisBottom(xM)
            .tickFormat(multiFormat)
        var masterY = d3.axisLeft(yM)
            .tickSizeInner(0)

        var svg = d3.select(sel).append("svg")
            .attr("width", width + margin.left + margin.right)
            .attr("height", height + margin.top * 2 + margin.bottom + masterHeight);

        var context = svg.append("g")
            .attr("class", "context")
            .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

        var detail = context.append("g")

        var brushed = function(){
            var selection = d3.event.selection;
            x.domain(selection.map(xM.invert,xM));
            var xd = x.domain();
            xB.domain(_.filter(_.map(masterData,"0"),function(d){
                return d >= xd[0] - DAY / 2 && d <= xd[1] + DAY / 2;
            }));
            detail.select(".x.axis").call(detailX);
            d3.selectAll(".tick").selectAll("text")
                .call(function(d){
                    console.log("tick",d);
                })
                .attr("y",9);
            detail.selectAll("rect")
                .attr("x",function(d){
                    return (d.data.timestamp);
                })
                .attr("y",function(d){
                    return y(d[1]) + margin.top;
                })
                .attr("width",xB.bandwidth());

        }
        var brush = d3.brushX().on("brush",brushed);

        xB.domain(_.map(masterData,"0"));
        var c10 = d3.scaleOrdinal(d3.schemeCategory10);
        var createdBar = detail.append("g")
            .selectAll("serie")
            .data(stacked)
            .enter()
            .append("g")
            .attr("class", "serie")
            .attr("fill",function(d,i){
                return c10(i);
            })
            .selectAll("rect")
            .data(function(d) { return d; })
            .enter().append("rect")
            .attr("x",function(d){
                return x(d.data.timestamp) - xB.bandwidth() / 2;
            })
            .attr("y",function(d){
                return y(d[1]) + margin.top;
            })
            .attr("height",function(d){
                return y(d[0]) - y(d[1]);
            })
            .attr("width",xB.bandwidth());

        detail.append("g")
            .attr("class", "x axis")
            .attr("transform", "translate(" + margin.left + "," + (margin.top + (height - margin.bottom)) + ")")
            .call(detailX);
        detail.append("g")
            .attr("class", "y axis")
            .attr("transform", "translate(" + margin.left + "," + margin.top + ")")
            .call(detailY);

        var legendLines = detail.append("g")
            .attr("class","detailLegend")
            .attr("transform","translate("+50+","+20+")")
            .selectAll(".legendLine")
            .data([
                {
                    text:"New conversations",
                    color:c10(0)
                },
                {
                    text:"Conversation edits",
                    color:c10(1)
                },
                {
                    text:"Total conversations",
                    color:c10(2)
                },
                {
                    text:"Total activity",
                    color:"blue"
                }
            ])
            .enter()
            .append("g")
            .attr("class","legendLine")
            .attr("transform",function(d,i){
                return "translate("+0+","+(i * 35)+")";
            });

        legendLines.append("rect")
            .attr("height",30)
            .attr("width",30)
            .attr("fill",function(d){
                return d.color;
            });
        legendLines.append("text")
            .text(function(d){
                return d.text;
            })
            .attr("x",35)
            .attr("y",20);

        var masterGraphic = d3.area()
            .x(function(d){
                return xM(d[0]);
            })
            .y0(yM(0))
            .y1(function(d){
                return yM(d[1].length);
            });
        var totalsAxis = d3.axisRight(totalsY);
        var totalsGraphic = d3.area()
            .x(function(d){
                return xM(d[0]);
            })
            .y0(totalsY(preMigration))
            .y1(function(d){
                return totalsY(preMigration + d[1].conversations);
            });
        var master = context.append("g");
        master.attr("transform","translate("+0+","+height+")");
        master.append("g")
            .attr("transform", "translate(" + margin.left + "," + (masterHeight) + ")")
            .call(masterX);
        master.append("g")
            .attr("class", "totalsArea")
            .append("path")
            .attr("d",totalsGraphic(totalConversations));
        master.append("g")
            .attr("class", "masterArea")
            .call(brush)
            .append("path")
            .attr("d",masterGraphic(masterData));
        master.append("g")
            .attr("class","teachers")
            .selectAll(".circ")
            .data(masterData)
            .enter()
            .append("circle")
            .attr("class","circ")
            .attr("cx",function(d){
                return xM(d[0]);
            })
            .attr("cy",function(d){
                return yM(d[1].length);
            })
            .attr("r",function(d){
                return r(_.uniq(_.map(d[1],"author")).length);
            });
        master.append("g")
            .attr("transform", "translate(" + (margin.left) + "," + 0 + ")")
            .call(masterY)
        master.append("g")
            .attr("transform", "translate(" + (width - margin.right) + "," + 0 + ")")
            .call(totalsAxis)
        var masterLegend = master.append("g")
            .attr("transform","translate("+50+","+20+")")
            .selectAll(".legendLine")
            .data([
                {
                    text:"Distinct authors",
                    class:"circ"
                }
            ])
            .enter()
            .append("g")
            .attr("transform",function(d,i){
                return "translate("+0+","+(35 * i)+")";
            });
        masterLegend.append("circle")
            .attr("class","circ")
            .attr("cx",15)
            .attr("cy",15)
            .attr("r",15)
        masterLegend.append("text")
            .text(function(d){
                return d.text;
            })
            .attr("class","legendLine")
            .attr("x",35)
            .attr("y",20);
    }

    return {
        prime:function(){
            $.get("/describeConversations?query=&format=json",function(response){
                var updates = _.flatMap(response.conversations,function(c){
                    var results = _.map(c.edits,function(et){
                        return {
                            timestamp:et,
                            author:c.author,
                            command:"/UPDATE_CONVERSATION_DETAILS",
                            parameters:[c.jid]
                        };
                    });
                    var sortedResults = _.sortBy(results,"timestamp");
                    console.log(c,_.map(sortedResults,function(r){return new Date(r.timestamp);}));
                    return sortedResults;
                });
                // console.log(conversations,updates);
                showUpdates(updates);
            });
        }
    };
})();
$(Enterprise.prime);
