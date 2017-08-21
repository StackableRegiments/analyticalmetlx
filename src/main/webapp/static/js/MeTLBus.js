var createMeTLBus = function(){
		var queues = {};
    return {
				manifest:function(){
						var funcs = _.map(queues,function(v,k){
							return [k,_.keys(v).length];
						});
						_.each(_.sortBy(funcs,"1").reverse(),function(func){
							console.log(func);
						});
				},
				list:function(){
						return _.mapValues(queues,function(v,k){
							return _.map(v,function(f,vk){
								return vk;
							});
						});
				},
        call:function(key,args){
					console.log("MeTLBus.call",key,args);
            args = args || [];
						if (key in queues){
							$.each(queues[key],function(k,f){
									try{
											f.apply(f,args);
									}
									catch(e){
											console.log("exception",key,k,e);
									}
							});
						} else {
							console.log("MeTLBus called on non-existent endpoint",key,args);
						}
        },
				subscribe:function(eventName,subscriberId,f){
					if (!(eventName in queues)){
						queues[eventName] = {};
					}
					queues[eventName][subscriberId] = f;
				},
				unsubscribe:function(eventName,subscriberId){
					if (eventName in queues){
						delete queues[eventName][subscriberId];
					}
				},
				check:function(eventName,subscriber){
					return (eventName in queues && subscriber in queues[eventName]);
				}
		}
};
var MeTLBus = createMeTLBus();

var WorkQueue = (function(){
    var isAbleToWork = true;
    var work = [];
		var afterFuncs = [];
    var blitNeeded = false;
    var popState = function(){
        var f = work.pop();
        if(f){
            blitNeeded = blitNeeded || f();
						MeTLBus.call("blit")
            popState();
        }
        else{
            if(blitNeeded){
								MeTLBus.call("blit")
                blitNeeded = false;
            }
        }
    };
    var pauseFunction = function(){
        stopResume();
        canWorkFunction(false);
        MeTLBus.call("afterWorkQueuePause");
    };
    var canWorkFunction = function(state){
        isAbleToWork = state;
        if(state){
            popState();
        }
    };
    var stopResume = function(){
        if (gracefullyResumeTimeout){
            window.clearTimeout(gracefullyResumeTimeout);
            gracefullyResumeTimeout = undefined;
        }
    }
    var gracefullyResumeDelay = 1000;
    var gracefullyResumeTimeout = undefined;
    var gracefullyResumeFunction = function(){
        stopResume();
        gracefullyResumeTimeout = setTimeout(function(){canWorkFunction(true);},gracefullyResumeDelay);
        MeTLBus.call("beforeWorkQueueResume");
    };
		var attachAfterActionFunc = function(f){
			afterFuncs.push(f);
		};
    return {
        pause:pauseFunction,
        gracefullyResume:gracefullyResumeFunction,
        enqueue:function(func){//A function returning a bool, blit needed or not.
            if(isAbleToWork){
                if(func()){
									MeTLBus.call("blit")
                };
            }
            else{
                work.push(function(){
                    return func();
                });
            }
        },
				attachAfterAction:attachAfterActionFunc
    };
})();
