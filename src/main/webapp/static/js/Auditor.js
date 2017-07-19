var queryArgs = {};
$(function(){
    var query = _.split(window.location.search,"?")[1];
    if (query !== undefined){
        _.forEach(_.split(query,"&"),function(item){
            var itemParts = _.split(item,"=");
            queryArgs[decodeURIComponent(itemParts[0])] = decodeURIComponent(_.drop(itemParts,1).join("="));
        });
    }
});

/** This is a bridge between JS client actions and MeTLActors (specifically TrainerActor). */
var Auditor = (function(){
    var trainerId = undefined;
    var detectTrainerId = function(){
        return queryArgs["trainerId"];
    };
    $(function(){
        trainerId = detectTrainerId();
        if (trainerId !== undefined){
            Progress.onSelectionChanged["auditor"] = function(selection){
                sendMessageUpstream("selectionChanged",selection);
            };
        }
    });
    var sendMessageUpstream = function(action,params){
        if (trainerId !== undefined && action !== undefined){
            fireTrainerAudit(action,params);
        }
    };
    return {
        isTrainer:function(){
            return trainerId !== undefined;
        },
        sendAuditMessage:sendMessageUpstream
    }
})();

//injected by lift
//function fireTrainerAudit(trainerId,action,params);