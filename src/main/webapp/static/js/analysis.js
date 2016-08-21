var Analytics = (function(){
    var histories = {}
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
	parent.html(parent.children().sort(function(a,b){
	    return b.innerHTML < a.innerHTML;
	}));
    };
    return {
        prime:function(conversation){
            status("Retrieving",conversation);
            $.get(sprintf("/details/%s",conversation),function(details){
                status("Retrieved",conversation);
                _.forEach($(details).find("slide").find("id"),function(el){
		    var slide = $(el).text();
                    status("Retrieving",slide);
                    $.get(sprintf("/history?source=%s",slide),function(slideHistory){
                        status("Retrieved",slide);
                        histories[slide] = slideHistory;
                    });
                });
            });
        },
	histories:function(){
	    return histories;
	}
    };
})();
