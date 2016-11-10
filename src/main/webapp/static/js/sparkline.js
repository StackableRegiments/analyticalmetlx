var SparkLine = (function(){
    return {
        sparkline:function(elemId, data) {
            $(elemId).sparkline(data,{type:"line"});
        }
    };
})();
