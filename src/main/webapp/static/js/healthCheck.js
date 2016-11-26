var HealthChecker = (function(){
    var storeLifetime = 5 * 60 * 1000; //1 minute
    var serverStatusInterval = 20000; //every 20 seconds
    var store = {};
    var queueSizeReached = {};
    var catLength = 50; //keep a rolling window of the last n items of each category
    var healthChecking = true;

    var addMeasureFunc = function(category,success,duration){
        if (!(category in store)){
            store[category] = timedQueue(storeLifetime);
        }
        var catStore = store[category];
        catStore.enqueue({
            instant:new Date().getTime(),
            duration:duration,
            success:success
        });
        updateGraph();
    };
    var check = function(){
        var clientStart = new Date().getTime();
        var reportableHealthObj = describeHealthFunction();
        var url = "/reportLatency"
        if ("latency" in reportableHealthObj){
            var reportableHealth = reportableHealthObj.latency;
            url = sprintf("%s?minLatency=%s&maxLatency=%s&meanLatency=%s&sampleCount=%s",url,reportableHealth.min,reportableHealth.max,reportableHealth.average,reportableHealth.count)
        }
        $.ajax(url,{
            method:"GET",
            success:function(time){
                var serverWorkTime = parseInt(time);
                var totalTime = new Date().getTime() - clientStart;
                var latency = (totalTime - serverWorkTime) / 2;
                addMeasureFunc("serverResponse",true,serverWorkTime);
                addMeasureFunc("latency",true,latency);
                _.delay(check,serverStatusInterval);
            },
            dataType:"text",
            error:function(){
                addMeasure("latency",false,(new Date().getTime() - clientStart) / 2);
                _.delay(check,serverStatusInterval);
            }
        });
    };
    var updateGraph = _.throttle(function(){
        var checkData = getAggregatedMeasuresFunc(1000);
        var describedData = describeHealthFunction();
        HealthCheckViewer.refreshDisplays(checkData,describedData);
    },500);
    var resumeHealthCheckFunc = function(){
        healthChecking = true;
        _.delay(check,serverStatusInterval);
    };
    var pauseHealthCheckFunc = function(){
        healthChecking = false;
    };
    var getMeasuresFunc = function(){
        return _.mapValues(store,function(v){return v.items();});
    };
    var getAggregatedMeasuresFunc = function(granularity){
        return _.mapValues(store,function(v){
            var set = {};
            _.forEach(v.items(),function(item){
                var sample = (Math.floor(item.instant / granularity) * granularity);
                var oldValue = set[sample];
                var obj = oldValue ? oldValue : {
                    count:0,
                    avg:undefined,
                    successCount:0,
                    min:undefined,
                    max:undefined,
                    instant:sample
                };
                obj.count += 1;
                if (item.success){
                    obj.successCount += 1;
                }
                if (obj.min == undefined || obj.min > item.duration){
                    obj.min = item.duration;
                }
                if (obj.max == undefined || obj.max < item.duration){
                    obj.max = item.duration;
                }
                if (obj.avg == undefined){
                    obj.avg = item.duration;
                } else {
                    obj.avg = ((item.duration - obj.avg) / obj.count) + obj.avg;
                }
                set[sample] = obj;
            });
            return _.values(set);
        });
    };
    var describeHealthFunction = function(){
        return _.mapValues(store,function(catStore,k){
            var v = catStore.items();
            var count = v.length;
            var durations = _.map(v,"duration");
            if (count > 0){
                return {
                    name:k,
                    count:count,
                    max:_.max(durations),
                    min:_.min(durations),
                    average:_.mean(durations),
                    recent:_.mean(_.takeRight(durations,10)),
                    successRate:_.countBy(v,"success")[true] / count
                };
            }
        });
    };
    $(function(){
        resumeHealthCheckFunc();
    });
    return {
        check:check,
        resumeHealthCheck:resumeHealthCheckFunc,
        pauseHealthCheck:pauseHealthCheckFunc,
        addMeasure:addMeasureFunc,
        getMeasures:getMeasuresFunc,
        getAggregatedMeasures:getAggregatedMeasuresFunc,
        describeHealth:describeHealthFunction
    }
})();

var augmentArguments = function(args){
    args[_.size(args)] = new Date().getTime();
    return args;
};

var serverResponse = function(responseObj){
    HealthChecker.addMeasure(responseObj.command,responseObj.success,responseObj.duration);
    if ("instant" in responseObj){
        var startTime = responseObj.instant;
        var totalTime = new Date().getTime() - startTime;
        var latency = (totalTime - responseObj.duration) / 2;
        HealthChecker.addMeasure("latency",responseObj.success,latency);
    }
    if ("success" in responseObj && responseObj.success == false){
	console.log(responseObj);
        errorAlert(sprintf("error in %s",responseObj.command),responseObj.response || "Error encountered");
    }
}

var HealthCheckViewer = (function(){
    var refreshRate = 1000; //every 1 second
    var viewing = false;
    var healthCheckContainer = {};
    $("#healthCheckListing");
    var healthCheckItemTemplate = {};
    var charts = {};
    $(function(){
        healthCheckContainer = $("#healthCheckListing");
        healthCheckItemTemplate = healthCheckContainer.find(".healthCheckItem").clone();
        healthCheckContainer.empty();
    });
    var pauseFunc = function(){
        viewing = false;
    };
    // data extractors
    var errorDataFunc = function(samples){
        return _.filter(_.map(samples,function(d){
            return {
                x:d.instant,
                y:d.count - d.successCount
            };
        }),function(d){return d.y > 0;});
    };
    var averageDataFunc = function(samples){
        return _.map(samples,function(d){
            return {
                x:d.instant,
                y:d.avg //- d.min
            };
        });
    };
    var minDataFunc = function(samples){
        return _.map(samples,function(d){
            return {
                x:d.instant,
                y:d.min
            };
        });
    };
    var maxDataFunc = function(samples){
        return _.map(samples,function(d){
            return {
                x:d.instant,
                y:d.max //- d.avg
            };
        });
    };

    var adjustTimeFunc = function(samples){
        var now = new Date().getTime();
        return _.map(samples,function(d){
            d.instant = (d.instant - now) / 1000;
            return d;
        });
    };

    var generateGraphFromCategory = function(categoryName,rawCategory){
        if (!(categoryName in charts)){
            var category = adjustTimeFunc(rawCategory);
            var rootElem = healthCheckItemTemplate.clone();
            var canvas = $("<canvas />").addClass("healthCheckCanvas");
            rootElem.html(canvas);
            healthCheckContainer.append(rootElem);
            var options = {
                title: {
                    display: true,
                    text: categoryName
                },
                scales: {
                    yAxes: [
                        {
                            id:"durationAxis",
                            scaleLabel:{
                                display:true,
                                labelString:"time taken (ms)"
                            },
                            type: "linear",
                            stacked: false,
                            display:true,
                            position:"left",
                            ticks: {
                            },
                            labels: {
                                show: true
                            }
                        },
                        {
                            id:"errorAxis",
                            scaleLabel:{
                                display:true,
                                labelString:"errors"
                            },
                            type: "linear",
                            stacked: true,
                            display:true,
                            position:"right",
                            ticks: {
                                beginAtZero:true,
                                min:0,
                                stepSize:1
                            },
                            labels: {
                                show: true
                            }
                        }
                    ],
                    xAxes: [{
                        type: "linear",
                        position: "bottom",
                        scaleLabel:{
                            display:true,
                            labelString:"time (seconds)"
                        },
                        ticks: {
                            beginAtZero:true,
                        }
                    }]
                },
                hover:{
                    mode:"x-axis"
                },
                elements:{
                    line: {
                        fill: false
                    },
                    bar: {
                        fill:true
                    }
                },
                legend:{
                    display:true
                }
            };

            var data = {
                labels: _.map(category,"instant"),
                datasets:[
                    {
                        type:"line",
                        label:"error count",
                        data:errorDataFunc(category),
                        fill:true,
                        borderColor:"rgba(0,0,0,0.5)",
                        backgroundColor:"rgba(255,0,0,0.3)",
                        borderWidth:1,
                        pointRadius:0,
                        pointHoverRadius:3,
                        pointHitRadius:5,
                        lineTension:0,
                        stepped:true,
                        yAxisID: "errorAxis"
                    },
                    {
                        label:"min",
                        type:"line",
                        data:minDataFunc(category),
                        fill:true,
                        pointRadius:0,
                        pointHoverRadius:3,
                        pointHitRadius:5,
                        borderColor:"rgba(155,197,61,1)",
                        backgroundColor:"rgba(155,197,61,0.3)",
                        borderWidth:1,
                        lineTension:0.1,
                        yAxisID: "durationAxis"
                    },
                    {
                        label:"avg",
                        type:"line",
                        data:averageDataFunc(category),
                        fill:true,
                        pointRadius:0,
                        pointHoverRadius:3,
                        pointHitRadius:5,
                        borderColor:"rgba(250,121,33,1)",
                        backgroundColor:"rgba(250,121,33,0.3)",
                        borderWidth:1,
                        lineTension:0.1,
                        yAxisID: "durationAxis"
                    },
                    {
                        label:"max",
                        type:"line",
                        data:maxDataFunc(category),
                        fill:true,
                        pointRadius:0,
                        pointHoverRadius:3,
                        pointHitRadius:5,
                        borderColor:"rgba(229,89,52,1)",
                        backgroundColor:"rgba(229,89,52,0.3)",
                        borderWidth:1,
                        lineTension:0.1,
                        yAxisID: "durationAxis"
                    }

                ]
            };
            var chartDesc = {
                type:"bar",
                data: data,
                options: options
            };
            var chart = new Chart(canvas[0].getContext("2d"),chartDesc);
            charts[categoryName] = chart;
        }
    };
    var resumeFunc = function(){
        viewing = true;
        var checkData = HealthChecker.getAggregatedMeasures(1000);
        var descriptionData = HealthChecker.describeHealth();
        refreshFunc(checkData,descriptionData);
        _.forEach(checkData,function(rawCategory,categoryName){
            generateGraphFromCategory(categoryName,rawCategory);
        });
    };
    var summarizeHealth = function(data){
        var health = 10;
        if(data.latency){
            health -= Math.min(8,data.latency.recent / 100);
        }
        if(data.render){
            health -= Math.min(8,data.render.recent / 20);
        }
        $("#healthStatus").prop({
            max:10,
            min:0,
            value:health,
            low:0,
            optimum:8,
            high:6
        });
    }
    var refreshFunc = function(checkData,descriptionData){
        var start = new Date().getTime();
        var overallLatencyData = descriptionData["latency"];
        if (overallLatencyData != undefined){
            summarizeHealth(descriptionData);
            $(".healthCheckSummaryLatencyContainer").show();
            $(".latencyAverage").text(overallLatencyData.average);
            $(".worstLatency").text(overallLatencyData.min);
            $(".bestLatency").text(overallLatencyData.max);
            $(".successRate").text(overallLatencyData.successRate * 100);
            if (overallLatencyData.average > 200){
                $(".latencyHumanReadable").text("poor");
            } else if (overallLatencyData.average > 50){
                $(".latencyHumanReadable").text("moderate");
            } else if (overallLatencyData.average > 20){
                $(".latencyHumanReadable").text("good");
            }
        } else {
            $(".healthCheckSummaryLatencyContainer").hide();
        }
        var overallServerResponseData = descriptionData["serverResponse"];
        if (overallServerResponseData != undefined){
            $(".heathCheckSummaryServerResponseContainer").show();
            var serverHealthData = overallServerResponseData.average;
            $(".serverResponseAverage").text(serverHealthData);
            var humanReadableServerHealthData = serverHealthData > 2 ? "poor" : "good";
            $(".serverResponseHumanReadable").text(humanReadableServerHealthData);
        } else {
            $(".heathCheckSummaryServerResponseContainer").hide();
        }
        _.forEach(checkData,function(rawCategory,categoryName){
            var category = adjustTimeFunc(rawCategory);
            if (!(categoryName in charts)){
                generateGraphFromCategory(categoryName,rawCategory);
            }
            if (viewing == true){
                var chart = charts[categoryName];
                chart.data.datasets[0].data = errorDataFunc(category);
                chart.data.datasets[1].data = minDataFunc(category);
                chart.data.datasets[2].data = averageDataFunc(category);
                chart.data.datasets[3].data = maxDataFunc(category);
                chart.update();
            }
        });
    };
    return {
        resume:resumeFunc,
        pause:pauseFunc,
        refreshDisplays:refreshFunc
    };
})();
