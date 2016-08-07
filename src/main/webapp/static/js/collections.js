var queue = function(){
	var doubleLinkedCell = function(item){
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
			item:function(){return item;}	
		};
	}
	var start,end;
	var enqFunc = function(item){
		var c = doubleLinkedCell(item);
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
	};
	var deqFunc = function(){
		if (end != undefined){
			var item = end.item();
			end = end.prev();
			if (end != undefined){
				end.setNext(undefined);
			} else {
				start = undefined;
			}
			return item;
		} else {
			if (start != undefined){
				start = undefined;
			}
			return undefined;
		}
	};
	var peekFunc = function(){
		return end != undefined ? end.item() : undefined;
	};
	var itemsFunc = function(){
		var i = end;
		var items = [];
		while (i != undefined){
			items.push(i.item());
			i = i.prev();
		}
		return items;
	};
	return {
		enqueue:enqFunc,
		dequeue:deqFunc,
		peek:peekFunc,
		items:itemsFunc
	};
};

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
	}
	var start,end;
	var clearExpired = function(){
		var now = new Date().getTime();
		var threshold = now - period;
		while (end != undefined && end.instant() < threshold){
			end = end.prev();
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
			var item = end.item();
			end = end.prev();
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
	return {
		enqueue:enqFunc,
		dequeue:deqFunc,
		peek:peekFunc,
		items:itemsFunc
	};
};
