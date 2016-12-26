var Admin = (function(){
    var conversations = {};
    var itemSelector = ".tile";
    var layoutMode = "packery";
    var dateFormat = "DD-MMM-YYYY, h:mm a";
    return {
        loadConversations:function(onSuccess){
            $.ajax("/search", {
                data:{
                    query:"",
                    format:"json"
                },
                success:function(jConversations){
                    _.each(jConversations.conversations.conversation,function(conversation){
                        conversations[conversation.jid] = conversation;
                    });
                    Admin.renderConversations(_.values(conversations));
                    if(onSuccess){
                        onSuccess();
                    }
                }
            });
        },
        renderConversations:function(renderableConversations){
            var container = $("#conversations");
            var conversationTemplate = container.find(".conversationTemplate");
            var expandedTemplate = container.find(".expandedTemplate");
            var detailTemplate = container.find(".detailTemplate");
            _.each(renderableConversations, function(conversation){
                var slides = conversation.slides.slide;
                if(!_.isArray(slides)){
                    slides = [slides];
                }
                var c = conversationTemplate.clone().removeClass("template").appendTo(container);
                c.find(".author").text(conversation.author);
                c.find(".slideCount").text(slides.length);
                c.find(".title").text(conversation.title);
                c.find(".activityCount").text(100000);
                c.find(".jid").text(conversation.jid);
                c.find(".progress").attr({
		    value:0,
		    max:1
		});
                var created = moment(conversation.created,
                                     [//Try American format first
                                         "M/d/YYYY H:m:s a",
                                         "d/M/YYYY H:m:s a",
                                         "ddd MMM D H:m:s YYYY"
                                     ]);
                //Override supplied created date for later consistency on sorting and range filtering
                conversation.created = created;
                c.find(".creation").text(created.format(dateFormat));
                c.find(".expand").click(function(){
                    var expanded = expandedTemplate.clone().removeClass("template").appendTo(c.find(".expandedContainer").empty());

                    var progress = 0;
                    var progressDisplay = c.find(".progress").attr("max",slides.length);
                    var updateProgress = function(){progressDisplay.attr("value",progress)};

                    var activityCount = 0;
                    var activityCountDisplay = expanded.find(".activityCount");
                    var updateActivityCount = function(){activityCountDisplay.text(activityCount)};

                    var uniqueAuthors = {};
                    var uniqueAuthorsDisplay = expanded.find(".uniqueAuthors");
                    var updateUniqueAuthorCount = function(){uniqueAuthorsDisplay.text(_.keys(uniqueAuthors).length)};

                    var uniqueObservers = {};
                    var uniqueObserversDisplay = expanded.find(".uniqueObservers");
                    var updateUniqueObserverCount = function(){uniqueObserversDisplay.text(_.keys(uniqueObservers).length)};

                    _.each(slides,function(slide){
                        $.ajax("/describeHistory",{
                            data:{
                                source:slide.id,
                                format:"json"
                            },
                            complete:function(){
                                progress += 1;
                                updateProgress();
                            },
                            success:function(description){
                                activityCount += parseInt(description.historyDescription.canvasContentCount);
                                _.each(description.historyDescription.uniquePublishers.publisher,function(publisher){
                                    uniqueAuthors[publisher.name] += publisher.activityCount;
                                });
                                _.each(description.historyDescription.uniqueOccupants.occupant,function(occupant){
                                    uniqueAuthors[occupant.name] += 1;
                                });

                                updateActivityCount();
                                updateUniqueAuthorCount();
                                updateUniqueObserverCount();

                                Admin.relayout();
                            }
                        });
                    });
                    expanded.find(".inspect").click(function(){
                        var detail = detailTemplate.clone().removeClass("template").appendTo(c.find(".detailContainer").empty());
                        progress = 0;
                        updateProgress();
                        var min = Date.now();
                        var max = 0;
                        var updateSpan = function(){
                            detail.find(".min").text(moment(min).format(dateFormat));
                            detail.find(".max").text(moment(max).format(dateFormat));
                            detail.find(".span").text(moment.duration(max-min).humanize());
                            Admin.relayout();
                        };
                        var timesOnSlide = [];
                        var graphContainer = function(id){
			    var g = "graph_"+(id || _.random(100000000));
                            $("<div />",{
                                class:"timeOnSlide",
                                id:g
                            }).appendTo(detail);
                            return "#"+g;
                        }
			var timesOnSlideContainer = graphContainer();
                        var updateTimesOnSlide = function(target){
                            MG.data_graphic({
                                title: "Activity by page",
                                data: timesOnSlide,
                                width: 400,
                                height: 200,
                                target: target
                            });
                        }
                        var aggregateTimes = [];
			var aggregateContainer = graphContainer();
                        var updateAggregateTimes = function(target){
                            var histogram = d3.layout.histogram()(aggregateTimes).map(function(bin){
                                return {
                                    date:new Date(bin.x),
                                    value:bin.y
                                };
                            });
                            MG.data_graphic({
                                title: "Activity over time",
                                data: histogram,
                                width: 400,
                                height: 200,
                                target: target
                            });
                        }
                        _.each(slides,function(slide){
                            $.ajax("/fullClientHistory",{
                                data:{
                                    source:slide.id
                                },
                                complete:function(){
                                    progress += 1;
                                    updateProgress();
                                },
                                success:function(xHistory){
                                    var times = [];
                                    $(xHistory).find("message").each(function(i,message){
                                        var t = $(message).attr("timestamp")
                                        var ti = parseInt(t);
                                        min = Math.min(ti,min);
                                        max = Math.max(ti,max);
                                        times.push({value:parseInt(slide.index),date:new Date(ti)});
                                        aggregateTimes.push(ti);
                                    });
                                    timesOnSlide.push(times);
                                    updateTimesOnSlide(timesOnSlideContainer);
                                    updateAggregateTimes(aggregateContainer);
                                    updateSpan();
                                }
                            });
                        });
                    });
                    Admin.relayout();
                });
            });
            container.isotope({
                itemSelector:itemSelector,
                layoutMode:"packery"
            });
        },
        initializeControls:function(){
            var filters = $("#conversationFilters");
            var authorFilter = filters.find(".author");
            var container = $("#conversations");
            authorFilter.find("input").on('input',function(){
                var criteria = $(this).val();
                container.isotope({
                    itemSelector:itemSelector,
                    layoutMode:layoutMode,
                    filter:function(){return $(this).find(".author").text() == criteria}
                });
            });
        },
        relayout:function(){
            $("#conversations").isotope('layout');
        }
    }
})();
$(function(){
    Admin.loadConversations();
    //Admin.loadConversations(function(){$(".expand").click()});
    Admin.initializeControls();
});
