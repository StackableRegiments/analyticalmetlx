var Bench = (function(){
    var executions = {};
    return {
	instrumented:function(){
	    return _.keys(executions);
	},
	average:function(label){
	    var xs = Bench.trail(label);
	    return _.reduce(xs,function(acc,item){
		return acc + item[1];
	    },0) / xs.length;
	},
	trail:function(label){
	    return executions[label];
	},
        track:function(label,func){
            var wrapper = function(){
                if(label in executions){}
                else {executions[label] = []}
                var init = Date.now();
                var result = func();
                executions[label].push([init,Date.now() - init]);
                return result;
            };
            return wrapper;
        }
    };
})();
