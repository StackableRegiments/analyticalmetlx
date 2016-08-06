var HealthChecker = (function(){
	var serverStatusInterval = 20000; //every 20 seconds
	var store = {};
	var queueSizeReached = {};
	var catLength = 50; //keep a rolling window of the last n items of each category
	var healthChecking = true;

	var addMeasureFunc = function(category,success,duration){
		if (!(category in store)){
			store[category] = [];
		}
		var catStore = store[category];
		if (!(category in queueSizeReached)){
		 	if (catStore.length >= catLength){
				queueSizeReached[category] = true;
				catStore.shift();
			}
		} else {
			catStore.shift();
		}
		catStore.push({
			instant:new Date().getTime(),
			duration:duration,
			success:success
		});
	};
	var check = function(){
		var clientStart = new Date().getTime();
		$.ajax( "/latency",{
			method:"GET",
			success:function(time){
				var serverWorkTime = parseInt(time);
				var totalTime = new Date().getTime() - clientStart;
				var latency = (totalTime - serverWorkTime) / 2;
				console.log("serverStatus",time,clientStart,serverWorkTime,totalTime,latency);
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
	var resumeHealthCheckFunc = function(){
		healthChecking = true;
		_.delay(check,serverStatusInterval);
	};
	var pauseHealthCheckFunc = function(){
		healthChecking = false;
	};
	var describeHealthFunction = function(){
		return _.map(store,function(v,k){
			var count = v.length;
			var durations = _.map(v,"duration");
			if (count > 0){
				return {
					name:k,
					count:count,
					max:_.max(durations),
					min:_.min(durations),
					average:_.mean(durations),
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
		getMeasures:function(){
			return store;
		},
		describeHealth:describeHealthFunction
	}
})();

var serverResponse = function(responseObj){
	HealthChecker.addMeasure(responseObj.command,responseObj.success,responseObj.duration);
	if ("instant" in responseObj){
		var startTime = responseObj.instant;
		var totalTime = new Date().getTime() - startTime;
		var latency = (totalTime - responseObj.duration) / 2;
		HealthChecker.addMeasure("latency",responseObj.success,latency);
	}
}

var HealthCheckViewer = (function(){
	var refreshRate = 5000; //every 5 seconds
	var viewing = false;
	var healthCheckContainer = {};
	$("#healthCheckListing");
	var healthCheckItemTemplate = {};
	var charts = {};
	$(function(){
		healthCheckContainer = $("#healthCheckListing");
		healthCheckItemTemplate = $("#healthCheckListing").clone();
		healthCheckContainer.empty();
	});
	var pauseFunc = function(){
		viewing = false;
	};
	var resumeFunc = function(){
		var identity = _.uniqueId();
		viewing = identity;
		var checkData = HealthChecker.getMeasures();
		healthCheckContainer.html(_.map(checkData,function(category,categoryName){
			var rootElem = healthCheckItemTemplate.clone();
			rootElem.attr("id","healthCheck_"+categoryName);
			var canvas = $("<canvas />").addClass("healthCheckCanvas");
			_.defer(function(){
				var options = {
					title: {
						display: true,
						text: categoryName
					},
					scales: {
						yAxes: [{
							stacked: true,
							ticks: {
							}
						}],
						xAxes: [{
							type: "linear",
							position: "bottom",
							ticks: {
								stepSize:1
							}
						}]
					},
					legend:{
						display:false
					}
				};
				var data = {
					labels: _.map(category,"instant"),
					datasets:[
						{
							label:"duration",
							data:_.map(category,function(sample){
								return {
									y:sample.duration,
									x:sample.instant
								}
							}),
							borderColor:["black"],
							backgroundColor:["gray"],
							borderWidth:1
						}/*,
						{
							data:_.map(category,"success"),
							borderColor:["black"],
							backgroundColor:["gray"],
							borderWidth:1
						}
						*/
					]
				};
				var chartDesc = {
					type:"line",
					data: data,
					options: options
				};
				var chart = new Chart(canvas[0].getContext("2d"),chartDesc);
				charts[categoryName] = chart;
			});
			rootElem.html(canvas);
			return rootElem;
		}));
		_.delay(updateView,refreshRate,identity);
	};
	var updateView = function(identity){
		if ("HealthChecker" in window && viewing == identity){
			console.log("updating charts");
			var checkData = HealthChecker.getMeasures();
			_.forEach(checkData,function(category,categoryName){
				var chart = charts[categoryName];
				if (chart){
					chart.data.datasets[0].data = _.map(category,function(sample){
						return {
							y:sample.duration,
							x:sample.instant
						};
					});
					chart.update();
				}
			});
			_.delay(updateView,refreshRate,identity);
		}
	};
	return {
		resume:resumeFunc,
		pause:pauseFunc
	};
})();
