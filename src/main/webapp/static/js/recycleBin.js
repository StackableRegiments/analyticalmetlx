var RecycleBin = (function(){
    var deletedContent = [];
    var undeletedContent = [];
    var recycleBinDatagrid = {};
    var actionButtonsTemplate = {};
    var reRenderDatagrid = function(){
        if( WorkQueue != undefined ) {
            WorkQueue.enqueue(function () {
                recycleBinDatagrid.jsGrid("loadData");
                var sortObj = recycleBinDatagrid.jsGrid("getSorting");
                if ("field" in sortObj) {
                    recycleBinDatagrid.jsGrid("sort", sortObj);
                }
            });
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

	var cellStyle = "max-height:50px;max-width:100%;cursor:zoom-in";
        var gridFields = [
            {
                name:"url",
                type:"text",
                title:"Preview",
                readOnly:true,
                sorting:false,
                itemTemplate:function(thumbnailUrl,stanza){
                    var defaultElem = $("<span/>",{text:"no preview"});
                    if ("type" in stanza){
                        var popupTitle = sprintf("Deleted %s from %s at %s on page %s",stanza.type,stanza.author,new Date(stanza.timestamp),stanza.slide);
                        if (stanza.type == "ink"){
                            if ("canvas" in stanza){
                                var imgSrc = stanza.canvas.toDataURL("image/png");
                                var img = $("<img/>",{src:imgSrc,class:"stanzaThumbnail",style:cellStyle},function(){
                                    $.jAlert({
                                        title:popupTitle,
                                        closeOnClick:true,
                                        width:"90%",
                                        content:$("<img/>",{src:imgSrc})[0].outerHTML
                                    });
                                });
                                return img;
                            } else {
                                return defaultElem;
                            }
                        } else if (stanza.type == "image"){
                            var imgSrc = calculateImageSource(stanza);
                            var img = $("<img/>",{src:imgSrc,class:"stanzaThumbnail",style:cellStyle}).on("click",function(){
                                $.jAlert({
                                    title:popupTitle,
                                    closeOnClick:true,
                                    width:"90%",
                                    content:$("<img/>",{src:imgSrc})[0].outerHTML
                                });
                            });
                            return img;
                        } else if (stanza.type == "text"){
                            return defaultElem;
                        } else if (stanza.type == "multiWordText"){
                            var textElem = $("<span/>");
                            var fontSizeMax = _.maxBy(stanza.words,function(w){return w.size;}).size;
                            var scalingFactor = 100 / fontSizeMax;
                            _.forEach(stanza.words,function(word){
                                var run = $("<span/>",{
                                    text:word.text
                                }).css({
                                    "color":word.color[0],
                                    "font-family":word.font,
                                    "font-style":word.italic ? "italic" : "normal",
                                    "font-weight":word.bold ? "bold" : "normal",
                                    "text-decoration":word.underline ? "underline" : "normal",
                                    "font-size":sprintf("%s%%",word.size * scalingFactor)
                                });
                                textElem.append(run);
                            });
                            return textElem;
                        } else {
                            return defaultElem;
                        }
                    } else return defaultElem;
                }
            },
            //{name:"slide",type:"number",title:"Page",readOnly:true},
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
											undeleteStanza(stanza);
										});
										rootElem.find(".rowIdentity").text(identity);
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
        var content = _.reject(deletedContent,function(dc){
            return _.some(undeletedContent,function(udc){
                return udc.elementType == dc.type && udc.oldIdentity == dc.identity && udc.timestamp > dc.timestamp;
            });
        });
        if (Conversations.shouldModifyConversation()){
            return content;
        }       else {
            var me = UserSettings.getUsername();
            return _.filter(content,function(stanza){
                return stanza.author == me;
            });
        }
    };
    var clearState = function(){
        deletedContent = [];
        undeletedContent = [];
    };
    var historyReceivedFunction = function(history){
        try {
            if ("type" in history && history.type == "history"){
                clearState();
                _.forEach(history.deletedCanvasContents,function(stanza){
                    onCanvasContentDeleted(stanza,true);
                });
                _.forEach(history.undeletedCanvasContents,function(stanza){
                    onStanzaReceived(stanza);
                });
                reRenderDatagrid();
            }
        }
        catch (e){
            console.log("RecycleBin.historyReceivedFunction",e);
        }
    };
    var onCanvasContentDeleted = function(stanza,skipRender){
        try {
            if ("type" in stanza){
                switch(stanza.type){
                case "ink":
                    prerenderInk(stanza);
                    break;
                case "text":
                    break;
                case "image":
                    /*
                     var image = stanza;
                     var dataImage = new Image();
                     image.imageData = dataImage;
                     dataImage.onload = function(){
                     if(image.width == 0){
                     image.width = dataImage.naturalWidth;
                     }
                     if(image.height == 0){
                     image.height = dataImage.naturalHeight;
                     }
                     image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
                     prerenderImage(image);
                     }
                     dataImage.src = calculateImageSource(image);
                     */
                    break;
                case "multiWordText":
                    if ("doc" in stanza){
                        stanza = richTextEditorToStanza(stanza);
                    }
                    break;
                case "video":
                    //prerenderVideo(stanza);
                    break;
                default:
                    break;
                }
            }
            if ("identity" in stanza && "type" in stanza){
                deletedContent.push(stanza);
                reRenderDatagrid();
            }
        } catch (e) {
            console.log("RecycleBin.onCanvasContentDeleted",e,stanza);
        }
    };
    var onStanzaReceived = function(stanza){
        if (stanza != undefined && "type" in stanza && stanza.type == "undeletedCanvasContent" && "elementType" in stanza && "oldIdentity" in stanza){
            undeletedContent.push(stanza);
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
        getAllDeletedContent:function(){
            return filteredRecycleBin();
        },
        getRawDeletedContent:function(){
            return deletedContent;
        },
        getUndeletedContent:function(){
            return undeletedContent;
        },
        reRender:reRenderDatagrid
    };
})();
