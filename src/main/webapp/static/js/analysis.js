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
        console.log(text);
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
    var chartAttendance = function(_data){
        var MINUTE = 60 * 1000;
        var color = d3.scale.category10();
        var datasets = _.map(_data,function(xs,location){
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
        if(!("attendance" in charts)){
            charts.attendance = new Chart(
                $("<canvas />").attr({
                    width:800,
                    height:300
                }).appendTo($("#display"))[0].getContext("2d"),
                {
                    type:"line",
                    data:{
                        datasets:datasets
                    },
                    options:{
                        scales:{
                            yAxes:[{
                                stacked:true,
                                scaleLabel:{
                                    display:true,
                                    labelString:"Moving to page"
                                }
                            }],
                            xAxes:[{
                                type:"linear",
                                label:"Entries per minute",
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
            console.log(charts.attendance.data);
            charts.attendance.data.datasets = datasets;
            charts.attendance.update();
        }
    };
    var incorporate = function(xHistory){
        console.log(xHistory);
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
        chartAttendance(_.groupBy(attendances,"location"));
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
