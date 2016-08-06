var Attachments = (function(){
    var attachments = [];
    var attachmentContainer = {};
    var attachmentTemplate = {};
    $(function(){
        attachmentContainer = $("#attachmentListing");
        attachmentTemplate = attachmentContainer.find(".attachmentItem").clone();
        attachmentContainer.empty();
        $("#attachments").click(function(){
            showBackstage("attachments");
            updateActiveMenu(this);
        });
        var attachmentCount = $("<div />",{
            id:"attachmentCount",
            class:"icon-txt"
        });
        $("#feedbackStatus").prepend(attachmentCount);
        attachmentCount.click(function(){
            showBackstage("attachments");
            updateActiveMenu(this);
        });
        $("#menuAttachments").on("click",function(){
            showBackstage("attachments");
            updateActiveMenu(this);
        });
        refreshAttachmentCount();
    });
    var refreshAttachmentCount = function(){
        var attachmentCount = _.size(attachments);
        if (attachmentCount > 0){
            if(attachmentCount == 1){
                $("#attachmentCount").text(sprintf("%s attachment",attachmentCount));
                $("#dedicatedAttachmentCount").text("This conversation has 1 attachment");
            }else {
                $("#attachmentCount").text(sprintf("%s attachments",attachmentCount));
                $("#dedicatedAttachmentCount").text(sprintf("This conversation has %s attachments",attachmentCount));
            }
        } else {
            $("#attachmentCount").text("");
            $("#dedicatedAttachmentCount").text("This conversation has no attachments");
        }
    };
    var resetUpload = function(){
        var attachmentFileChoice = $("#attachmentFileChoice"),
            attachmentUploadFormContainer = $("#attachmentUploadFormContainer");
        attachmentFileChoice.wrap("<form>").closest("form").get(0).reset();
        attachmentFileChoice.unwrap();
        attachmentUploadFormContainer.hide();
        if (Conversations.shouldModifyConversation()){
            $("#attachmentCreationButton").unbind("click").on("click",function(){
                attachmentUploadFormContainer.show();
            }).show();
            attachmentFileChoice.unbind("change").on("change",function(eventArgs){
                WorkQueue.pause();
                var jid = Conversations.getCurrentConversationJid();
                //var filename = $(this).val();
								var filename = $(this).val().replace(/.*(\/|\\)/, '');
								console.log("filename for upload:",filename);
                var reader = new FileReader();
                var files = eventArgs.target.files || eventArgs.dataTransfer.files;
                var file = files[0];
                if (file != undefined){
                    var reader = new FileReader();
                    reader.onload = function(fileByteStringEventHandler){
                        var b64Bytes = fileByteStringEventHandler.target.result;
                        var url = sprintf("/uploadDataUri?filename=%s&jid=%s",encodeURI(filename),jid);
                        console.log("uploading",url,b64Bytes.length,b64Bytes);
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
        attachmentContainer.empty();
        $("#attachmentCount").text("");
        resetUpload();
    };
    var renderAttachmentsInPlace = function(){
        try{
            attachmentContainer.empty();
            if(attachments.length > 0) {
                $.map(attachments,function(attachment){ renderAttachment(attachment,attachmentContainer,attachmentTemplate.clone());});
            }
            refreshAttachmentCount();
        }
        catch(e){
            console.log("renderAttachments exception",e,attachments);
        }
    };
    var renderAttachment = function(attachment,targetContainer,template){
        var uniq = function(label){return sprintf("attachment_%s_%s",label,attachment.id);};
        var rootElem = template.find(".attachmentItem");
        rootElem.attr("id",uniq("container"));
        var linkElem = template.find(".attachmentDownloadLink");
        var href = sprintf("/attachmentProxy/%s/%s",Conversations.getCurrentConversationJid(),encodeURI(attachment.id));
        linkElem.attr("href",href);
        var linkNameElem = template.find(".attachmentDownloadLinkText");
        linkNameElem.text(attachment.name);
        targetContainer.append(template);
    };
    var actOnAttachment = function(newAttachment){
        attachments.push(newAttachment);
    };
    var historyReceivedFunction = function(history){
        try {
            if ("type" in history && history.type == "history"){
                clearState();
                _.forEach(history.files,doStanzaReceivedFunction);
                renderAttachmentsInPlace();
            }
        }
        catch (e){
            console.log("Attachments.historyReceivedFunction",e);
        }
    };
    var stanzaReceivedFunction = function(input){
        doStanzaReceivedFunction(input);
        renderAttachmentsInPlace();
    };
    var doStanzaReceivedFunction = function(possibleAttachment){
        try {
            if ("type" in possibleAttachment && possibleAttachment.type == "file"){
                actOnAttachment(possibleAttachment);
            }
        }
        catch(e){
            console.log("Attachments.stanzaReceivedFunction exception",e);
        }
    };
    var receiveAttachmentsFromLiftFunction = function(newAttachments){
        try{
            if (_.size(newAttachments) > 0){
                $.each(newAttachments,function(unusedAttachmentName,attachment){
                    if ("type" in attachment && attachment.type == "file"){
                        attachments.push(attachment);
                    }
                });
            }
            renderAttachmentsInPlace();
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
