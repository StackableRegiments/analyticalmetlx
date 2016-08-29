var Analytics = (function(){
    Chart.defaults.global.defaultFontColor = "#FFF";
    var displays = {};
    var status = function(msg,key){
        var parent = $("#status");
        if(!(key in displays)){
            displays[key] = {
                element:$("<div />").appendTo(parent),
                touches:0
            };
        }
        displays[key].touches += 1;
        var text = sprintf("%s %s %s",_.repeat("..",displays[key].touches),msg,key)
        displays[key].element.html($("<div />",{
            text:text
        }));
    };
    var events = [];
    var authors = [];
    var attendances = []
    var activity = []
    var handwriting = {};
    var keyboarding = {}
    var images = {};
    var charts = {};
    var dFormat = function(mString){
        return moment(parseInt(mString)).format("MMMM Do YYYY, h:mm:ss a");
    };
    var shortFormat = function(mString){
        return moment(parseInt(mString)).format("HH:mm MM/DD");
    };
    var MINUTE = 60 * 1000;
    var chartAttendance = function(attendances){
        var WIDTH = 640;
        var HEIGHT = 240;
        /*Over time*/
        _.forEach({
            locationOverTime:{
                dataset:_.groupBy(attendances,"location"),
                title:"Page attendance",
                yLabel:"Visits to page"
            },
            authorsOverTime:{
                dataset:_.groupBy(_.map(attendances,function(attendance){
                    return {
                        timestamp:Math.floor(attendance.timestamp / MINUTE * MINUTE),
                        author:attendance.author,
                        location:attendance.location
                    };
                }),"author"),
                title:"Authors",
                yLabel:"Pages visited"
            }
        },function(config,key){
            var color = d3.scaleOrdinal(d3.schemeCategory10);
            var dataset = _.map(config.dataset,function(xs,location){
                var rounded = _.groupBy(xs,function(x){
                    return Math.floor(x.timestamp / MINUTE)
                });
                var data = _.map(_.sortBy(_.keys(rounded)),function(minute){
                    return {
                        x:minute * MINUTE,
                        y:rounded[minute].length
                    }
                });
                return {
                    label:location,
                    tension:0.1,
                    borderColor:color(location),
                    data:data
                }
            });
            if(!(key in charts)){
                charts[key] = new Chart(
                    $("<canvas />").attr({
                        width:WIDTH,
                        height:HEIGHT
                    }).appendTo($("#displayOverTime"))[0].getContext("2d"),
                    {
                        type:"line",
                        data:{
                            datasets:dataset
                        },
                        options:{
                            title:{
                                display:true,
                                text:config.title
                            },
                            showLines:false,
                            scales:{
                                yAxes:[{
                                    stacked:true,
                                    scaleLabel:{
                                        display:true,
                                        labelString:config.yLabel
                                    }
                                }],
                                xAxes:[{
                                    type:"linear",
                                    scaleLabel:{
                                        display:true,
                                        labelString:"Time"
                                    },
                                    position:"bottom",
                                    ticks:{
                                        callback:function(value){
                                            return shortFormat(value);
                                        }
                                    }
                                }]
                            }
                        }
                    }
                );
            }
            else{
                charts[key].data.datasets = dataset;
                charts[key].update();
            }
        });
        /*Over pages*/
        _.forEach({
            distinctUsersOverSlides:{
                dataset:_.groupBy(attendances,"location"),
                title:"Furthest page reached",
                yLabel:"Users who reached this page",
                xLabel:"Page"
            }
        },function(config,key){
            var color = d3.scaleOrdinal(d3.schemeCategory10);
            var dataset = [
                {
                    label:"Distinct users",
                    tension:0.1,
                    borderColor:color(0),
                    data:_.map(config.dataset,function(xs,location){
                        return {
                            x:location,
                            y:_.uniqBy(xs,"author").length
                        }
                    })
                }
            ];
            if(!(key in charts)){
                charts[key] = new Chart(
                    $("<canvas />").attr({
                        width:WIDTH,
                        height:HEIGHT
                    }).appendTo($("#displayOverPages"))[0].getContext("2d"),
                    {
                        type:"line",
                        data:{
                            datasets:dataset
                        },
                        options:{
                            title:{
                                display:true,
                                text:config.title
                            },
                            showLines:true,
                            scales:{
                                yAxes:[{
                                    stacked:true,
                                    scaleLabel:{
                                        display:true,
                                        labelString:config.yLabel
                                    }
                                }],
                                xAxes:[{
                                    type:"linear",
                                    scaleLabel:{
                                        display:true,
                                        labelString:"Page"
                                    },
                                    position:"bottom"
                                }]
                            }
                        }
                    });
            }
            else{
                charts[key].data.datasets = dataset;
                charts[key].update();
            }
        });
    }
    var incorporate = function(xHistory,details){
        var history = $(xHistory);
        _.forEach(history.find("message"),function(message){
            var m = $(message);
            var timestamp = m.attr("timestamp");
            var author = m.find("author").text();
            var slide = m.find("slide").text();
            if(slide == ""){
                slide = m.find("location").text();
            }
            events.push(timestamp);
            authors.push(author);
            if(slide != ""){
                activity.push({
                    author:author,
                    timestamp:parseInt(timestamp),
                    location:parseInt(slide)
                });
            }
            _.forEach(m.find("attendance"),function(attendance){
                attendances.push({
                    author:author,
                    timestamp:new Date(parseInt(timestamp)),
                    location: $(attendance).find("location").text()
                });
            });
        });
        var min = _.min(events);
        var max = _.max(events);
        status(sprintf("Anchored %s",dFormat(min)),"earliest event");
        status(sprintf("Anchored %s",dFormat(max)),"latest event");
        status(events.length,"events scoped");
        console.log(events.length);
        status(_.uniq(authors).length,"authors scoped");
        adherenceToTeacher(_.sortBy(activity,"timestamp"),details);
        chartAttendance(attendances);
    };
    var adherenceToTeacher = function(attendances,details){
        $("#vis").empty();
        attendances = _.map(attendances,function(attendance){
            attendance.timestamp = Math.floor(attendance.timestamp / MINUTE) * MINUTE;
            return attendance;
        });
        var author = $(details).find("author:first").text();
        var owner = _.groupBy(attendances,function(attendance){
            return attendance.author == author;
        });
        var data = owner[false];
        data = _.groupBy(data,"location");
        data = _.mapValues(data,function(xs){
            return _.groupBy(xs,"timestamp");
        });
        var studentLocations = [];
        _.each(data,function(bucket,location){
            _.each(bucket,function(xs,timestamp){
                studentLocations.push({
                    timestamp:timestamp,
                    location:location,
                    attendances:xs
                });
            });
        });
        var teacherLocations = owner[true];

        var slides = _.map(data, function(d){
            return parseInt(d.location);
        });
        var margin = {
            top: 10,
            right: 25,
            bottom: 15,
            left: 35
        }
        var width = 800;
        var height = 300;
        var masterHeight = 100;

        var x = d3.scaleTime().range([0 + margin.right, width - margin.left]),
            xM = d3.scaleTime().range([0 + margin.right, width - margin.left]),
            y = d3.scaleLinear().range([margin.top, height - margin.bottom - margin.top]),
            yM = d3.scaleLinear().range([masterHeight,margin.top]),
            r = d3.scaleLinear().range([5,25]);

        x.domain(d3.extent(attendances,function(d){
            return d.timestamp;
        }));
        xM.domain(d3.extent(attendances,function(d){
            return d.timestamp;
        }));

        y.domain(d3.extent(attendances,function(d){
            return parseInt(d.location);
        }));
        var masterData = _.toPairs(_.groupBy(attendances,"timestamp"));
        yM.domain(d3.extent(masterData,function(d){
            return parseInt(d[1].length);
        }));

        r.domain(d3.extent(studentLocations,function(d){
            return d.attendances.length;
        }));

        var detailX = d3.axisBottom(x)
                .tickSize(-height, 0)

        var detailY = d3.axisLeft(y)
                .tickSize(1)

        var masterX = d3.axisBottom(xM)
        var masterY = d3.axisLeft(yM)

        var svg = d3.select("#vis").append("svg")
                .attr("width", width + margin.left + margin.right)
                .attr("height", height + margin.top * 2 + margin.bottom + masterHeight);

        var context = svg.append("g")
                .attr("class", "context")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

        var detail = context.append("g")
        var teacherPath = d3.line()
                .x(function(d){
                    return x(d.timestamp);
                })
                .y(function(d){
                    return y(d.location);
                });

        var brushed = function(){
            var selection = d3.event.selection;
            x.domain(selection.map(xM.invert,xM));
            detail.select(".teacherPath").attr("d", teacherPath(teacherLocations));
            detail.select(".x.axis").call(detailX);
            detail.selectAll(".circ")
                .attr("cx", function(d) {
                    return x(d.timestamp);
                })
                .attr("cy", function(d) {
                    return y(parseInt(d.location));
                });
        }
        var brush = d3.brushX().on("brush",brushed);

        var circles = detail.append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")")
                .selectAll(".circ")
                .data(studentLocations)
                .enter()
                .append("circle")
                .attr("class", "circ")
                .attr("cx", function(d) {
                    return x(d.timestamp);
                })
                .attr("cy", function(d) {
                    return y(parseInt(d.location));
                })
                .attr("r", function(d){
                    return r(d.attendances.length);
                });
        var teacher;
        if(teacherLocations){
            teacher = detail.append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")")
                .append("path")
                .attr("class","teacherPath")
                .attr("d",teacherPath(teacherLocations));
        }
        detail.append("g")
            .attr("class", "x axis")
            .attr("transform", "translate(" + margin.left + "," + (margin.top + (height - margin.bottom)) + ")")
            .call(detailX);
        detail.append("g")
            .attr("class", "y axis")
            .attr("transform", "translate(" + margin.left + "," + margin.top + ")")
            .call(detailY);

        var masterTop = masterHeight + height - margin.top;
        var masterArea = d3.area()
                .x(function(d) {
                    return xM(d[0]);
                })
                .y0(masterHeight - margin.top)
                .y1(function(d) {
                    return yM(d[1].length) - margin.top;
                });

        var master = context.append("g")
                .attr("transform", "translate(" + margin.left + "," + height + ")")
        master.append("g")
            .attr("transform", "translate(" + 0 + "," + (masterHeight) + ")")
            .call(masterX);
        master.append("g")
            .attr("transform", "translate(" + 0 + "," + ( margin.top) + ")")
            .attr("class", "masterArea")
            .call(brush)
            .append("path")
            .attr("d",masterArea(masterData));
        master.append("g")
            .call(masterY)

    };
    return {
        prime:function(conversation){
            status("Retrieving",conversation);
            $.get(sprintf("/details/%s",conversation),function(details){
                status("Retrieved",conversation);
                var slides = $(details).find("slide");
                _.forEach(slides.find("id"),function(el){
                    var slide = $(el).text();
                    status("Retrieving",slide);
                    $.get(sprintf("/fullClientHistory?source=%s",slide),function(slideHistory){
                        status("Retrieved",slide);
                        incorporate(slideHistory,details);
                        status(sprintf("Incorporated %s",slides.length),"slide(s)");
                    });
                });
            });
        }
    };
})();
