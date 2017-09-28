var timedQueue = function(period){
    var timedDoubleLinkedCell = function(item,instant){
        var prev, next;
        return {
            next:function(){return next;},
            prev:function(){return prev;},
            setNext:function(n){
                next = n;
            },
            setPrev:function(p){
                prev = p;
            },
            item:function(){return item;},
            instant:function(){return instant;}
        };
    };
    var start,end;
    var clearExpired = function(){
        var now = new Date().getTime();
        var threshold = now - period;
        while (end != undefined && end.instant() < threshold){
            var tempEnd = end;
            end = end.prev();
            tempEnd.setPrev(undefined);
            if (end != undefined){
                end.setNext(undefined);
            }
        }
        if (end == undefined){
            start = undefined;
        }
    };
    var enqFunc = function(item){
        var c = timedDoubleLinkedCell(item,new Date().getTime());
        if (start == undefined){
            start = c;
            if (end == undefined){
                end = c;
            }
        } else {
            c.setNext(start);
            start.setPrev(c);
            start = c;
        }
        clearExpired();
    };
    var deqFunc = function(){
        clearExpired();
        if (end != undefined){
            var tempEnd = end;
            var item = end.item();
            end = end.prev();
	    tempEnd.setPrev(undefined);
            end.setNext(undefined);
            return item;
        } else {
            return undefined;
        }
    };
    var peekFunc = function(){
        clearExpired();
        return end != undefined ? end.item() : undefined;
    };
    var itemsFunc = function(){
        clearExpired();
        var i = end;
        var items = [];
        while (i != undefined){
            items.push(i.item());
            i = i.prev();
        }
        return items;
    };
    var oldestFunc = function(){
        return end;
    };
    var newestFunc = function(){
        return start;
    };
    return {
        enqueue:enqFunc,
        dequeue:deqFunc,
        peek:peekFunc,
        items:itemsFunc,
        oldest:oldestFunc,
        newest:newestFunc
    };
};
