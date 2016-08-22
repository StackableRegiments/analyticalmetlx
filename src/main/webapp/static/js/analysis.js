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
    var chartAttendance = function(attendances){
        var MINUTE = 60 * 1000;
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
            var color = d3.scale.category10();
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
            var color = d3.scale.category10();
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
    var incorporate = function(xHistory){
        var history = $(xHistory);
        _.forEach(history.find("message"),function(message){
            var m = $(message);
            var timestamp = m.attr("timestamp");
            var author = m.find("author").text();
            events.push(timestamp);
            authors.push(author);
            _.forEach(m.find("attendance"),function(attendance){
                attendances.push({
                    author:author,
                    timestamp:timestamp,
                    location: $(attendance).find("location").text()
                });
            });
        });
        var min = _.min(events);
        var max = _.max(events);
        status(sprintf("Anchored %s",dFormat(min)),"earliest event");
        status(sprintf("Anchored %s",dFormat(max)),"latest event");
        status(events.length,"events scoped");
        status(_.uniq(authors).length,"authors scoped");
        chartAttendance(attendances);
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
                        incorporate(slideHistory);
                        status(sprintf("Incorporated %s",slides.length),"slide(s)");
                    });
                });
            });
        }
    };
})();
