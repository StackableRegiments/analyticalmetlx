var RecycleBin = (function(){
    var deletedContent = [];
		var recycleBinDatagrid = {};
		var actionButtonsTemplate = {};
		var reRenderDatagrid = function(){
			recycleBinDatagrid.jsGrid("loadData");
			var sortObj = recycleBinDatagrid.jsGrid("getSorting");
			if ("field" in sortObj){
					recycleBinDatagrid.jsGrid("sort",sortObj);
			}
		};
    $(function(){
				recycleBinDatagrid = $("#recycleBinDatagrid");
				actionButtonsTemplate = recycleBinDatagrid.find(".actionButtons").clone();
				recycleBinDatagrid.empty();
        var DateField = function(config){
            jsGrid.Field.call(this,config);
        };
        DateField.prototype = new jsGrid.Field({
            sorter: function(a,b){
                return new Date(a) - new Date(b);
            },
            itemTemplate: function(i){
                return new Date(i).toLocaleString();
            },
            insertTemplate: function(i){return ""},
            editTemplate: function(i){return ""},
            insertValue: function(){return ""},
            editValue: function(){return ""}
        });
        jsGrid.fields.dateField = DateField;

				var gridFields = [
					{
						name:"url",
						type:"text",
						title:"Preview",
						readOnly:true,
						sorting:false,
						itemTemplate:function(thumbnailUrl,stanza){
							var img = $("<img/>",{src:stanza.canvas,class:"stanzaThumbnail",style:"width:100%;height:160px;cursor:zoom-in"}).on("click",function(){
								var title = sprintf("Deleted content from %s at %s on slide %s",stanza.author,new Date(stanza.timestamp),stanza.slide);
								$.jAlert({
									title:title,
									closeOnClick:true,
									width:"90%",
									content:$("<img/>",{src:stanza.canvas})[0].outerHTML
								});
							});							
							return img;
						}
					},
					{name:"slide",type:"number",title:"Slide",readOnly:true},
					{name:"timestamp",type:"dateField",title:"When",readOnly:true},
					{name:"author",type:"text",title:"Who",readOnly:true},
					{name:"privacy",type:"text",title:"Privacy",readOnly:true},
					{
						name:"identity",
						type:"text",
						title:"actions",
						readOnly:true,
						sorting:false,
						itemTemplate:function(identity,stanza){
							var rootElem = actionButtonsTemplate.clone();
							var button = rootElem.find(".restoreContent");
							button.on("click",function(){
								var newStanza = stanza.clone();
								var newIdentity = sprintf("%s_%s",new Date().getTime(),stanza.identity).substr(0,64);
								newStanza.identity = newIdentity;
								var newUndeletedContentItem = {
									type:"undeletedCanvasContent",
									author:UserSettings.getUsername(),
									timestamp:new Date().getTime(),
									slide:stanza.slide,
									privacy:stanza.privacy,
									target:stanza.target,
									elementType:stanza.type,
									oldIdentity:stanza.identity,
									newIdentity:newIdentity	
								};
								sendStanza(newStanza);
								sendStanza(newUndeletedContentItem);
							});
							return rootElem;
						}
					}
				];
				recycleBinDatagrid.jsGrid({
					width:"100%",
					height:"auto",
					inserting:false,
					editing:false,
					sorting:true,
					paging:true,
					noDataContent: "No deleted content",
				 	controller: {
						loadData: function(filter){
							if ("sortField" in filter){
								var sorted = _.sortBy(filteredRecycleBin(),function(sub){
									return sub[filter.sortField];
								});
								if ("sortOrder" in filter && filter.sortOrder == "desc"){
									sorted = _.reverse(sorted);
								}
								return sorted;
							} else {
								return filteredRecycleBin();
							}
						}
					},
					pageLoading:false,
					fields: gridFields	
				});
				recycleBinDatagrid.jsGrid("sort",{
					field:"timestamp",
					order:"desc"
				});
				reRenderDatagrid();
    });
		var filteredRecycleBin = function(){
			if (Conversations.shouldModifyConversation()){
				return deletedContent;
			}	else {
				var me = UserSettings.getUsername();
				_.filter(deletedContent,function(stanza){
					return stanza.author = me;
				});
			}
		};
    var clearState = function(){
        deletedContent = [];
    };
    var historyReceivedFunction = function(history){
        try {
            if ("type" in history && history.type == "history"){
                clearState();
                _.forEach(history.deletedCanvasContents,function(stanza){
									onCanvasContentDeleted(stanza,true);
								});
                reRenderDatagrid();
            }
        }
        catch (e){
            console.log("RecycleBin.historyReceivedFunction",e);
        }
    };
    var onCanvasContentDeleted = function(stanza,skipRender){
			deletedContent.push(stanza);
			reRenderDatagrid();
    };
		var onStanzaReceived = function(stanza){
			if (stanza != undefined && "type" in stanza && stanza.type == "undeletedCanvasContent"){
				deletedContent = _.filter(deletedContent,function(dc){
					return "elementType" in stanza && "oldIdentity" in stanza && stanza.elementType != dc.type && stanza.oldIdentity != dc.identity;
				});
				reRenderDatagrid();
			}
		};
    Progress.onConversationJoin["RecycleBin"] = clearState;
    Progress.historyReceived["RecycleBin"] = historyReceivedFunction;
		Progress.onCanvasContentDeleted["RecycleBin"] = onCanvasContentDeleted;
		Progress.stanzaReceived["RecycleBin"] = onStanzaReceived;
		$(function(){
			$("#menuRecycleBin").on("click",function(){
				showBackstage("recycleBin");
				reRenderDatagrid();
			});
		});
    return {
			getAllDeletedContent:function(){return filteredRecycleBin();},
			reRender:reRenderDatagrid
    };
})();
