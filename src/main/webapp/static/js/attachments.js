var Attachments = (function(){
    var attachments = [];
    var attachmentsDatagrid = {};
    var deleteButtonTemplate = {};
    $(function(){
        attachmentsDatagrid = $("#attachmentsDatagrid");
        $("#menuAttachments").on("click",function(){
            showBackstage("attachments");
            updateActiveMenu(this);
            reRenderAttachments();
        });
        deleteButtonTemplate = attachmentsDatagrid.find(".deleteButtonContainer").clone();
        attachmentsDatagrid.empty();
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
                name:"id",
                type:"text",
                title:"Download",
                readOnly:true,
                sorting:true,
                itemTemplate:function(thumbnailUrl,attachment){
                    var href = sprintf("/attachmentProxy/%s/%s",Conversations.getCurrentConversationJid(),encodeURI(attachment.id));
                    return $("<a />",{href:href,text:attachment.name});
                }
            },
            {name:"timestamp",type:"dateField",title:"When",readOnly:true},
            {name:"author",type:"text",title:"Who",readOnly:true},
            {
                name:"identity",
                type:"text",
                title:"actions",
                readOnly:true,
                sorting:false,
                itemTemplate:function(identity,attachment){
                    if (Conversations.shouldModifyConversation()){
                        var rootElem = deleteButtonTemplate.clone();
                        rootElem.find(".deleteButton").on("click",function(){
                            attachment.deleted = true;
                            sendStanza(attachment);
                        });
                        return rootElem;
                    } else {
                        return $("<span/>");
                    }
                }
            }
        ];
        attachmentsDatagrid.jsGrid({
            width:"100%",
            height:"auto",
            inserting:false,
            editing:false,
            sorting:true,
            paging:true,
            noDataContent: "No attachments",
            controller: {
                loadData: function(filter){
                    var sorted = attachments;
                    if ("sortField" in filter){
                        sorted = _.sortBy(sorted,function(sub){
                            return sub[filter.sortField];
                        });
                        if ("sortOrder" in filter && filter.sortOrder == "desc"){
                            sorted = _.reverse(sorted);
                        }
                    }
                    return _.filter(sorted,function(attachment){return !attachment.deleted;});
                }
            },
            pageLoading:false,
            fields: gridFields
        });
        attachmentsDatagrid.jsGrid("sort",{
            field:"timestamp",
            order:"desc"
        });
        reRenderAttachments();
    });
    var resetUpload = function(){
        var attachmentFileChoice = $("#attachmentFileChoice"),
            attachmentUploadFormContainer = $("#attachmentUploadFormContainer");
        attachmentFileChoice.wrap("<form>").closest("form").get(0).reset();
        attachmentFileChoice.unwrap();
        attachmentUploadFormContainer.hide();
        if (Conversations.shouldModifyConversation()){
            $("#attachmentCreationButton").unbind("click").on("click",function(){
                attachmentFileChoice.click();
            }).show();
            attachmentFileChoice.unbind("change").on("change",function(eventArgs){
                WorkQueue.pause();
                var jid = Conversations.getCurrentConversationJid();
                var filename = $(this).val().replace(/.*(\/|\\)/, '');
                var reader = new FileReader();
                var files = eventArgs.target.files || eventArgs.dataTransfer.files;
                var file = files[0];
                if (file != undefined){
                    var reader = new FileReader();
                    reader.onload = function(fileByteStringEventHandler){
                        var b64Bytes = fileByteStringEventHandler.target.result;
                        var url = sprintf("/uploadDataUri?filename=%s&jid=%s",encodeURI(filename),jid);
                        $.ajax({
                            url:url,
                            type:'POST',
                            data:b64Bytes,
                            success:function(e){
                                var newId = $(e).find("resourceUrl").text();
                                var me = UserSettings.getUsername();
                                var newTimestamp = Date.now();
                                var newAttachment = {
                                    type:"file",
                                    id:newId,
                                    name:filename,
                                    deleted:false,
                                    url:newId,
                                    author:me,
                                    timestamp:newTimestamp
                                };
                                createAttachmentFunction(newAttachment);
                                resetUpload();
                                WorkQueue.gracefullyResume();
                            },
                            error:function(e){
                                errorAlert("upload failed");
                                console.log("file upload failed",e);
                                resetUpload();
                                WorkQueue.gracefullyResume();
                            },
                            cache: false,
                            contentType: false,
                            processData: false
                        });
                    };
                    reader.readAsDataURL(file);
                } else {
                    resetUpload();
                    WorkQueue.gracefullyResume();
                }
            });
        } else {
            $("#attachmentCreationButton").unbind("click").hide();
            attachmentFileChoice.unbind("change").hide();
        }

    };
    var clearState = function(){
        attachments = [];
        resetUpload();
        reRenderAttachments();
    };
    var reRenderAttachments = function(){
        if( WorkQueue != undefined ) {
            WorkQueue.enqueue(function () {
                attachmentsDatagrid.jsGrid("loadData");
                var sortObj = attachmentsDatagrid.jsGrid("getSorting");
                if ("field" in sortObj) {
                    attachmentsDatagrid.jsGrid("sort", sortObj);
                }
            });
        }
    };
    var actOnAttachment = function(newAttachment){
        var partitioned = _.partition(attachments,function(attachment){
            return attachment.id == newAttachment.id;
        });
        attachments = _.concat(partitioned[1],_.reverse(_.sortBy(_.concat([newAttachment],partitioned[0]),"timestamp"))[0]);
    };
    var historyReceivedFunction = function(history){
        try {
            if ("type" in history && history.type == "history"){
                clearState();
                _.forEach(history.files,doStanzaReceivedFunction);
                reRenderAttachments();
            }
        }
        catch (e){
            console.log("Attachments.historyReceivedFunction",e);
        }
    };
    var stanzaReceivedFunction = function(input){
        doStanzaReceivedFunction(input);
    };
    var doStanzaReceivedFunction = function(possibleAttachment){
        try {
            if ("type" in possibleAttachment && possibleAttachment.type == "file"){
                actOnAttachment(possibleAttachment);
            }
            reRenderAttachments();
        }
        catch(e){
            console.log("Attachments.stanzaReceivedFunction exception",e);
        }
    };
    var receiveAttachmentsFromLiftFunction = function(newAttachments){
        try{
            if (_.size(newAttachments) > 0){
                $.each(newAttachments,function(unusedAttachmentName,attachment){
                    doStanzaReceivedFunction(attachment);
                });
            }
            reRenderAttachments();
        }
        catch(e){
            console.log("Attachments.receiveAttachmentsFromLift exception",e);
        }
    };
    var createAttachmentFunction = function(newAttachment){
        if (Conversations.shouldModifyConversation()){
            sendStanza(newAttachment);
        }
    };
    Progress.onConversationJoin["Attachments"] = clearState;
    Progress.historyReceived["Attachments"] = historyReceivedFunction;
    Progress.stanzaReceived["Attachments"] = stanzaReceivedFunction;
    return {
        getAllAttachments:function(){
            return attachments;
        }
    };
})();
