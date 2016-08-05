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
}
