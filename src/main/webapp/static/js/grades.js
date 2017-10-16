var Grades = (function(){
    var gradesDatagrid = {};
    var grades = {};
    var gradeValues = {};
    var remoteGradeValuesCache = {};
    var gradeCreateButton = {};
    var gradeActionButtonsTemplate = {};
    var gradeEditTemplate = {};
    var gradeAssessTemplate = {};
    var gradebooks = [];
    var reRenderFunc = function(){};
		var gradebookReRenderFunc = function(){};
    var spin = function(el,off){
        $(el).prop("disabled",off);
        $(".grades.blocker").toggle(off);
    }
    var clearState = function(){
        grades = {};
        gradeValues = {};
        remoteGradeValuesCache = {};
        reRenderFunc();
    };
    var historyReceivedFunc = function(history){
        try {
            if ("type" in history && history.type == "history"){
                clearState();
                _.forEach(history.grades,function(gradeStanza){
                    stanzaReceived(gradeStanza,true);
                });
                _.forEach(history.gradeValues,function(gradeValueStanza){
                    stanzaReceived(gradeValueStanza,true);
                });
                reRenderFunc();
            }
        } catch (e){
            console.log("Grades.historyReceivedFunction",e);
        }
    };
    var stanzaReceived = function(stanza,skipRender){
        try {
            if ("type" in stanza){
                switch (stanza.type){
                case "grade":
                    var oldGrade = grades[stanza.id];
                    if (oldGrade == undefined || oldGrade.timestamp < stanza.timestamp){
                        grades[stanza.id] = stanza;
                        if (!skipRender){
                            if (oldGrade){
                                if ("visible" in oldGrade && oldGrade.visible == false && "visible" in stanza && stanza.visible == true){ // when a slide is made visible, the student may not necessarily have all their grade values already, so they'll be unable to see them until they've fetched a new history.  So, if it was visible before, then this change shouldn't trigger a history load.
                                    getHistory(Conversations.getCurrentSlideJid());
                                } else {
                                }
                            }
                        }
                    }
                    break;
                case "numericGradeValue":
                case "booleanGradeValue":
                case "textGradeValue":
                    var gradeColl = gradeValues[stanza.gradeId] || {};
                    gradeValues[stanza.gradeId] = gradeColl;
                    var oldGradeValue = gradeColl[stanza.gradedUser];
                    if (!oldGradeValue || oldGradeValue.timestamp < stanza.timestamp){
                        gradeColl[stanza.gradedUser] = stanza;
                        Progress.call("gradeValueReceived",[stanza]);
                    }
                    break;
                }
                if (!skipRender){
                    reRenderFunc();
                }
            }
        } catch (e){
            console.log("Grades.stanzaReceived",e);
        }
    };
    Progress.onConversationJoin["Grades"] = clearState;
    Progress.historyReceived["Grades"] = historyReceivedFunc;
    Progress.stanzaReceived["Grades"] = stanzaReceived;
    $(function(){

        $.getJSON("/getExternalGradebooks",function(data){
            gradebooks = data;
        });
        console.log("Conv state:",Conversations.getCurrentConversationJid(),Conversations.shouldModifyConversation(),Conversations.getCurrentConversation());
        var setupGrades = function(){
            gradesDatagrid = $("#gradesDatagrid");
            gradeActionButtonsTemplate = gradesDatagrid.find(".gradeActionsContainer").clone();
            gradeEditTemplate = gradesDatagrid.find(".gradeEditContainer").clone();
            gradeAssessTemplate = gradesDatagrid.find(".gradeAssessContainer").clone();
            gradesDatagrid.empty();
            gradeCreateButton = $("#createGradeButton")
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
                    name:"name",
                    type:"text",
                    title:"Name",
                    readOnly:true,
                    sorting:true
                },
                {name:"description",type:"text",title:"Description",readOnly:true,sorting:true},
                {name:"location",type:"text",title:"Location",readOnly:true,sorting:true},
                {name:"timestamp",type:"dateField",title:"When",readOnly:true,itemTemplate:function(t){
                    if(t == 0){
                        return "";
                    }
                    return moment(t).format('MMM Do YYYY, h:mm a');
                }}
            ];
            var teacherFields = [
                {name:"gradeType",type:"text",title:"Type",readOnly:true,sorting:true},
                {
                    name:"identity",
                    type:"text",
                    title:"Actions",
                    readOnly:true,
                    sorting:false,
                    itemTemplate:function(identity,grade){
                        if (grade.author == UserSettings.getUsername()){
                            var rootElem = gradeActionButtonsTemplate.clone();
                            var newGrade = _.cloneDeep(grade);
                            var renderEditGradeAlert = function(){
                                var uniqId = _.uniqueId();
                                var outer = $("<div/>",{
                                    id:uniqId
                                });
                                var jAlert = $.jAlert({
                                    title:"Edit grade",
                                    width:"50%",
                                    content:outer[0].outerHTML
                                });
                                var innerRoot = gradeEditTemplate.clone();
                                var nameId = sprintf("gradeName_%s",uniqId);
                                var nameInputBox = innerRoot.find(".gradeNameInputBox");
                                var changeNameFunc = function(ev){
                                    newGrade.name = nameInputBox.val();
                                };
                                nameInputBox.attr("id",nameId).unbind("blur").on("blur",changeNameFunc).val(newGrade.name);
                                innerRoot.find(".gradeNameLabel").attr("for",nameId);
                                var descId = sprintf("gradeDesc_%s",uniqId);
                                var changeDescFunc = function(ev){
                                    newGrade.description = descInputBox.val();
                                };
                                var descInputBox = innerRoot.find(".gradeDescriptionInputBox");
                                descInputBox.attr("id",descId).unbind("blur").on("blur",changeDescFunc).val(newGrade.description);
                                innerRoot.find(".gradeDescriptionLabel").attr("for",descId);
                                var selectId = sprintf("gradeType_%s",uniqId);
                                var typeSelect = innerRoot.find(".gradeTypeSelect");
                                var minTextbox = innerRoot.find(".numericMinTextbox");
                                var maxTextbox = innerRoot.find(".numericMaxTextbox");
                                var changeMinFunction = function(ev){
                                    if (newGrade.gradeType == "numeric"){
                                        newGrade.numericMinimum = parseFloat(minTextbox.val());
                                    } else {
                                        delete newGrade.numericMinimum;
                                    }
                                };
                                var changeMaxFunction = function(ev){
                                    if (newGrade.gradeType == "numeric"){
                                        newGrade.numericMaximum = parseFloat(maxTextbox.val());
                                    } else {
                                        delete newGrade.numericMaximum;
                                    }
                                };
                                var minId = sprintf("numericMin_%s",uniqId);
                                var maxId = sprintf("numericMax_%s",uniqId);
                                innerRoot.find(".numericMinLabel").attr("for",minId);
                                innerRoot.find(".numericMaxLabel").attr("for",maxId);
                                minTextbox.unbind("blur").on("blur",changeMinFunction).attr("id",minId);
                                maxTextbox.unbind("blur").on("blur",changeMaxFunction).attr("id",maxId);


                                var reRenderGradeTypeOptions = function(){
                                    if ("foreignRelationship" in newGrade){
                                        minTextbox.prop("disabled",true);
                                        maxTextbox.prop("disabled",true);
                                        typeSelect.prop("disabled",true);
                                    }
                                    typeSelect.val(newGrade.gradeType);
                                    switch (newGrade.gradeType){
                                    case "numeric":
                                        innerRoot.find(".numericOptions").show();
                                        if (newGrade.numericMinimum === undefined){
                                            newGrade.numericMinimum = 0;
                                        };
                                        if (newGrade.numericMaximum === undefined){
                                            newGrade.numericMaximum = 100;
                                        };
                                        minTextbox.val(newGrade.numericMinimum);
                                        maxTextbox.val(newGrade.numericMaximum);
                                        break;
                                    default:
                                        innerRoot.find(".numericOptions").hide();
                                        break;
                                    }
                                };
                                typeSelect.attr("id",selectId).unbind("change").on("change",function(){
                                    newGrade.gradeType = typeSelect.val();
                                    reRenderGradeTypeOptions();
                                }).val(newGrade.gradeType);
                                innerRoot.find(".gradeTypeLabel").attr("for",selectId);
                                var visibleId = sprintf("gradeVisible_%s",uniqId);
                                innerRoot.find(".gradeVisibleLabel").attr("for",visibleId);
                                var visibleCheckbox = innerRoot.find(".gradeVisibleCheckbox");
                                visibleCheckbox.attr("id",visibleId).prop("checked",newGrade.visible).unbind("change").on("change",function(ev){
                                    newGrade.visible = visibleCheckbox.prop("checked");
                                });
                                var wantsToAssociate = undefined;
                                var chosenGradebook = undefined;
                                var orgUnits = [];
                                var chosenOrgUnit = undefined;
                                var associatedGrade = undefined;
                                var reRenderAssociations = function(){
                                    var aNodes = innerRoot.find(".associateController");
                                    spin(aNodes,false);
                                    if ("foreignRelationship" in newGrade){
                                        aNodes.find(".createAssociation").hide();
                                        var system = newGrade.foreignRelationship.sys;
                                        var parts = newGrade.foreignRelationship.key.split("_");
                                        var orgUnit = parts[0];
                                        var gradeId = parts[1];
                                        aNodes.find(".associationSystem").text(system);
                                        aNodes.find(".associationOrgUnit").text(orgUnit);
                                        aNodes.find(".associationGradeId").text(gradeId);
                                        aNodes.find(".requestRefreshAssociation").unbind("click").on("click",function(){
                                            spin(aNodes,true);
                                            $.getJSON(sprintf("/getExternalGrade/%s/%s/%s",system,orgUnit,gradeId),function(remoteGrade){
                                                newGrade.description = remoteGrade.description;
                                                newGrade.name = remoteGrade.name;
                                                newGrade.gradeType = remoteGrade.gradeType;
                                                newGrade.numericMinimum = remoteGrade.numericMinimum;
                                                newGrade.numericMaximum = remoteGrade.numericMaximum;
                                                jAlert.closeAlert();
                                                renderEditGradeAlert();
                                                spin(this,false);
                                            }).fail(function(jqxhr,textStatus,error){
                                                spin(aNodes,false);
                                                console.log(textStatus,error);
                                                alert(sprintf("Error: %s \r\n %s",textStatus,error));
                                            });
                                        });
                                        aNodes.find(".disassociateGrade").unbind("click").on("click",function(){
                                            delete newGrade.foreignRelationship;
                                            newGrade.timestamp = 0;
                                            sendStanza(newGrade);
                                            jAlert.closeAlert();
                                            renderEditGradeAlert();
                                        });
                                        aNodes.find(".refreshAssociation").show();
                                    } else {
                                        aNodes.find(".refreshAssociation").hide();
                                        aNodes.find(".createAssociation").show();
                                        aNodes.find(".associationPhase").hide();
                                        if (wantsToAssociate === undefined){
                                            aNodes.find(".requestAssocPhase1").show();
                                            aNodes.find(".requestAssociation").unbind("click").on("click",function(){
                                                wantsToAssociate = true;
                                                if (gradebooks.length == 1){
                                                    chosenGradebook = gradebooks[0].id;
                                                }
                                                reRenderAssociations();
                                            });
                                        } else if (chosenGradebook == undefined){
                                            chosenGradebook = gradebooks[0].id;
                                            aNodes.find(".chooseGradebook").html(_.map(gradebooks,function(gb){
                                                return $("<option/>",{
                                                    value:gb.id,
                                                    text:gb.name
                                                });
                                            })).unbind("change").on("change",function(ev){
                                                chosenGradebook = $(this).val();
                                            });
                                            aNodes.find(".commitGradebook").unbind("click").on("click",function(){
                                                spin(this,true);
                                                reRenderAssociations();
                                            });
                                            aNodes.find(".requestAssocPhase2").show();
                                        } else if (chosenOrgUnit === undefined){
                                            spin(aNodes,true);
                                            $.getJSON(sprintf("/getExternalGradebookOrgUnits/%s",chosenGradebook),function(data){
                                                console.log("requestedOrgUnits:",data);
                                                if (data && data.length){
                                                    chosenOrgUnit = data[0].foreignRelationship["key"];
                                                    aNodes.find(".chooseOrgUnit").html(_.map(data,function(ou){
                                                        var ouId = ou.foreignRelationship.key;
                                                        return $("<option/>",{
                                                            value:ouId,
                                                            text:ou.name
                                                        });
                                                    })).unbind("change").on("change",function(ev){
                                                        chosenOrgUnit = $(this).val();
                                                    });
                                                    aNodes.find(".commitOrgUnit").unbind("click").on("click",function(){
                                                        reRenderAssociations();
                                                    });
                                                    aNodes.find(".requestAssocPhase3").show();
                                                } else {
                                                    console.log("found no data:",data);
                                                    aNodes.text("No gradebooks found");
                                                }
                                                spin(aNodes,false);
                                            }).fail(function(jqxhr,textStatus,error){
                                                spin(aNodes,false);
                                                console(sprintf("error: %s \r\n %s",textStatus,error));
                                                alert(sprintf("error: %s \r\n %s",textStatus,error));
                                            });
                                        } else {
                                            aNodes.find(".requestAssocPhase4").show();
                                            spin(aNodes,true);
                                            var linkGradeButton = aNodes.find(".linkGrade");
                                            var preExistingGrades = [];
                                            var existingGradesSelectBox = aNodes.find("#chooseExistingGradeSelectBox");
                                            var chosenPreExistingGrade = undefined;
                                            linkGradeButton.unbind("click").on("click",function(){
                                                if (chosenPreExistingGrade !== undefined && "foreignRelationship" in chosenPreExistingGrade && "sys" in chosenPreExistingGrade.foreignRelationship && "key" in chosenPreExistingGrade.foreignRelationship){
                                                    newGrade.foreignRelationship = {
                                                        sys:chosenPreExistingGrade.foreignRelationship.sys,
                                                        key:chosenPreExistingGrade.foreignRelationship.key
                                                    }

                                                    //clone the values from the remote system
                                                    newGrade.gradeType = chosenPreExistingGrade.gradeType;
                                                    newGrade.numericMinimum = chosenPreExistingGrade.numericMinimum;
                                                    newGrade.numericMaximum = chosenPreExistingGrade.numericMaximum;
                                                    newGrade.name = chosenPreExistingGrade.name;
                                                    nameInputBox.val(newGrade.name);
                                                    newGrade.description = chosenPreExistingGrade.description;
                                                    descInputBox.val(newGrade.description);

                                                    sendStanza(newGrade);
                                                    reRenderAssociations();
                                                    reRenderGradeTypeOptions();
                                                } else {
                                                    alert("no pre-existing grade chosen");
                                                }
                                            }).prop("disabled",true);
                                            existingGradesSelectBox.unbind("change").on("change",function(ev){
                                                var chosenGrade = $(this).val();
                                                if (chosenGrade !== undefined && chosenGrade !== "no-choice"){
                                                    chosenPreExistingGrade = _.find(preExistingGrades,function(peg){
                                                        return "foreignRelationship" in peg && "key" in peg.foreignRelationship && peg.foreignRelationship.key == chosenGrade;
                                                    });
                                                    if (chosenPreExistingGrade !== undefined){
                                                        linkGradeButton.prop("disabled",false);
                                                    } else {
                                                        linkGradeButton.prop("disabled",true);
                                                    }
                                                } else {
                                                    chosenPreExistingGrade = undefined;
                                                    linkGradeButton.prop("disabled",true);
                                                }
                                            });
                                            $.ajax({
                                                type:"GET",
                                                url:sprintf("/getExternalGrades/%s/%s",chosenGradebook,chosenOrgUnit),
                                                success:function(data){
                                                    //console.log("found external grades:",data);
                                                    preExistingGrades = data;
                                                    if (data.length){
                                                        existingGradesSelectBox.html(_.map([
                                                            {
                                                                text:"",
                                                                foreignRelationship:{
                                                                    system:"no-system",
                                                                    key:"no-choice"
                                                                }
                                                            }].concat(data),function(eg){
                                                                return $("<option/>",{
                                                                    text:eg.name,
                                                                    value:eg.foreignRelationship.key
                                                                });
                                                            }));
                                                    } else {
                                                        existingGradesSelectBox.hide();
                                                        linkGradeButton.prop("disabled",true);
                                                        linkGradeButton.hide();
                                                    }
                                                    spin(aNodes,false);
                                                },
                                                dataType:"json"
                                            }).fail(function(jqxhr,textStatus,error){
                                                spin(aNodes,false);
                                                alert(sprintf("error - could not fetch existing grades from remote gradebook: %s \r\n %s",textStatus,error));
                                            });
                                            aNodes.find(".createGrade").unbind("click").on("click",function(){
                                                spin(aNodes,true);
                                                $.ajax({
                                                    type:"POST",
                                                    url:sprintf("/createExternalGrade/%s/%s",chosenGradebook,chosenOrgUnit),
                                                    data:JSON.stringify(newGrade),
                                                    success:function(data){
                                                        //console.log("createdGrades:",newGrade,data);
                                                        newGrade.foreignRelationship = {
                                                            sys:data.foreignRelationship.sys,
                                                            key:data.foreignRelationship.key
                                                        }
                                                        sendStanza(newGrade);
                                                        reRenderAssociations();
                                                        spin(this,false);
                                                    },
                                                    contentType:"application/json",
                                                    dataType:'json'
                                                }).fail(function(jqxhr,textStatus,error){
                                                    spin(aNodes,false);
                                                    alert("Could not create remote grade.  Please ensure that the grade has a non-blank name which will be unique within the remote system");
                                                });
                                            });
                                        }
                                    }
                                };
                                var gradeFixesContainer = innerRoot.find(".fixGradeTypeErrorsContainer");
                                var gradeFixesContainerTemplate = gradeFixesContainer.clone();
                                gradeFixesContainer.empty();

                                reRenderAssociations();
                                innerRoot.find(".cancelGradeEdit").unbind("click").on("click",function(){
                                    jAlert.closeAlert();
                                });
                                innerRoot.find(".submitGradeEdit").unbind("click").on("click",function(){
                                    sendStanza(newGrade);
                                    var newGradeType = sprintf("%sGradeValue",newGrade.gradeType);
                                    console.log("gradeFilter:",newGradeType,gradeValues[newGrade.id]);
                                    var badGradeValues = _.filter(gradeValues[newGrade.id],function(gv){
                                        return gv.type != newGradeType && "gradeValue" in gv;
                                    });
                                    jAlert.closeAlert();
                                    if (_.size(badGradeValues) > 0){
                                        var gradeFixUniqId = _.uniqueId();
                                        var outer = $("<div/>",{
                                            id:gradeFixUniqId
                                        });
                                        var gradeFixJAlert = $.jAlert({
                                            title:"Fix grade values after grade type change",
                                            width:"auto",
                                            content:outer[0].outerHTML,
                                            onClose:function(){
                                                reRenderFunc();
                                            }
                                        });
                                        var gradeFixInnerRoot = gradeFixesContainerTemplate.clone();
                                        $("#"+gradeFixUniqId).append(gradeFixInnerRoot);
                                        gradeFixesContainer.show();
                                        var indivFixesContainer = gradeFixInnerRoot.find(".individualFixesContainer");
                                        var indivFixTemplate = indivFixesContainer.find(".individualFix").clone();
                                        indivFixesContainer.html(_.map(badGradeValues,function(bgvi){
                                            var bgv = _.cloneDeep(bgvi);
                                            var bgvf = indivFixTemplate.clone();
                                            bgvf.find(".individualFixGradedUser").text(bgv.gradedUser.toString());
                                            bgvf.find(".individualFixOldValue").text(bgv.gradeValue.toString());
                                            var newValue = undefined;
                                            switch (newGrade.gradeType){
                                            case "numeric":
                                                switch (bgv.type){
                                                case "textGradeValue":
                                                    var candidate = parseInt(bgv.gradeValue);
                                                    if ("numericMinimum" in newGrade && !isNaN(newGrade.numericMinimum) && "numericMaximum" in newGrade && !isNaN(newGrade.numericMaximum)){
                                                        if (isNaN(candidate)){
                                                            newValue = newGrade.numericMinimum;
                                                        } else {
                                                            newValue = Math.min(newGrade.numericMaximum,Math.max(candidate,newGrade.numericMinimum));
                                                        }
                                                    } else {
                                                        if (isNaN(candidate)){
                                                            newValue = 0;
                                                        } else {
                                                            newValue = candidate;
                                                        }
                                                    }
                                                    break;
                                                case "booleanGradeValue":
                                                    newValue = bgv.gradeValue ? newGrade.numericMaximum : newGrade.numericMinimum;
                                                    break;
                                                default:
                                                    break;
                                                }
                                                break;
                                            case "text":
                                                switch (bgv.type){
                                                case "numericGradeValue":
                                                    newValue = bgv.gradeValue.toString();
                                                    break;
                                                case "booleanGradeValue":
                                                    newValue = bgv.gradeValue.toString();
                                                    break;
                                                default:
                                                    break;
                                                }
                                                break;
                                            case "boolean":
                                                switch (bgv.type){
                                                case "numericGradeValue":
                                                    newValue = bgv.gradeValue != 0;
                                                    break;
                                                case "textGradeValue":
                                                    var candidate = bgv.gradeValue.toLowerCase().trim();
                                                    newValue = !(candidate == "false" || candidate == "0" || candidate == "no" || candidate == "" || candidate == "n"); // general falsey
                                                    break;
                                                default:
                                                    break;
                                                }
                                                break;
                                            default:
                                                break;
                                            }
                                            bgvf.find(".individualFixNewValue").text(newValue.toString());
                                            bgvf.find(".commitIndividualFix").unbind("click").on("click",function(){
                                                bgv.type = newGradeType;
                                                bgv.gradeValue = newValue;
                                                sendStanza(bgv);
                                                badGradeValues = _.filter(badGradeValues,function(tbgvi){
                                                    return tbgvi.gradedUser != bgv.gradedUser;
                                                });
                                                bgvf.unbind("click");
                                                bgvf.remove();
                                                if (_.size(badGradeValues) < 1){
                                                    gradeFixJAlert.closeAlert();
                                                }
                                            });
                                            return bgvf;
                                        }));
                                        gradeFixInnerRoot.find(".closeIndividualFixesPopup").on("click",function(){
                                            gradeFixJAlert.closeAlert();
                                        });
                                    }
                                });
                                $("#"+uniqId).append(innerRoot);
                            }
                            rootElem.find(".editGradeButton").unbind("click").on("click",renderEditGradeAlert);
                            rootElem.find(".assessGradeButton").unbind("click").on("click",function(){
                                var uniqId = _.uniqueId();
                                var outer = $("<div/>",{
                                    id:uniqId
                                });
                                var jAlert = $.jAlert({
                                    title:"Assess grade",
                                    width:"auto",
                                    content:outer[0].outerHTML,
                                    onClose:function(){
																				gradebookReRenderFunc = function(){};
                                        reRenderFunc();
                                    }
                                });
                                var changeGvPopupTemplate = {};
                                var innerRoot = gradeAssessTemplate.clone();
                                $("#"+uniqId).append(innerRoot);
                                spin(innerRoot,true);
                                var gradebookDatagrid   = innerRoot.find(".gradebookDatagrid");
                                var changeGvPopupTemplate = innerRoot.find(".gradeValueEditPopup").clone();
                                var assessUserTemplate = gradebookDatagrid.find(".gradeUserContainer").clone();
                                gradebookDatagrid.empty();
                                var generateData = function(andThen){
                                    var candidateData = gradeValues[grade.id];
                                    var data = {};
                                    if (candidateData === undefined){
                                        gradeValues[grade.id] = data;
                                    } else {
                                        data = _.cloneDeep(candidateData);
                                    }
                                    var gradeType = sprintf("%sGradeValue",grade.gradeType);
                                    var possibleParticipants = Participants.getPossibleParticipants();
                                    if ("foreignRelationship" in grade){
                                        var system = grade.foreignRelationship["sys"];
                                        var parts = grade.foreignRelationship["key"].split("_");
                                        var orgUnit = parts[0];
                                        var gradeId = parts[1];
                                        $.getJSON(sprintf("/getExternalGradebookOrgUnitClasslist/%s/%s",system,orgUnit),function(members){
                                            _.forEach(members,function(m){
                                                var u = m["UserName"];
                                                if (u !== undefined){
                                                    possibleParticipants.push(u);
                                                }
                                            });
                                            possibleParticipants = _.uniq(possibleParticipants);
                                            _.forEach(possibleParticipants,function(name){
                                                var oldValue = data[name];
                                                if (oldValue === undefined || oldValue.type != gradeType){
                                                    data[name] = {
                                                        type:gradeType,
                                                        gradeId:grade.id,
                                                        gradedUser:name,
                                                        gradePrivateComment:"",
                                                        gradeComment:"",
                                                        author:grade.author,
                                                        timestamp:0,
                                                        audiences:[]
                                                    };
                                                }
                                            });
                                            //console.log("possibleParticipants:",possibleParticipants,data);
                                            data = _.values(data);
                                            data = _.filter(data,function(d){
                                                return d.type == gradeType;
                                            });
                                            andThen(data);
                                        }).fail(function(jqxhr,textStatus,error){
                                            spin(innerRoot,false);
                                            console.log("error",textStatus,error);
                                        });
                                    } else {
                                        _.forEach(possibleParticipants,function(name){
                                            var oldValue = data[name];
                                            if (oldValue === undefined || oldValue.type != gradeType){
                                                data[name] = {
                                                    type:gradeType,
                                                    gradeId:grade.id,
                                                    gradedUser:name,
                                                    author:grade.author,
                                                    gradePrivateComment:"",
                                                    gradeComment:"",
                                                    timestamp:0,
                                                    audiences:[]
                                                };
                                            }
                                        });
                                        data = _.values(data);
                                        data = _.filter(data,function(d){
                                            return d.type == gradeType;
                                        });
                                        andThen(data);
                                    }
                                };
                                var withData = function(data){
                                    var changeGradeFunc = function(gv){
                                        var changeGvId = sprintf("changeGvPopup_%s",_.uniqueId());
                                        var changeGvContainer = $("<div/>",{
                                            id:changeGvId
                                        });
                                        //console.log("gvPopup",gv);
                                        var changeGvAlert = $.jAlert({
                                            type:"modal",
                                            content:changeGvContainer[0].outerHTML,
                                            title:sprintf("Change score for %s",gv.gradedUser)
                                        });
                                        var gvActualContainer = $("#"+changeGvId);
                                        var gvChangeElem = changeGvPopupTemplate.clone();
                                        var scoringRoot = gvChangeElem.find(".changeGradeContainer");
                                        var numericScore = scoringRoot.find(".numericScore");
                                        var booleanScore = scoringRoot.find(".booleanScore");
                                        var booleanScoreLabel = scoringRoot.find(".booleanScoreLabel");
                                        var textScore = scoringRoot.find(".textScore");
                                        var newGv = _.cloneDeep(gv);
                                        switch (grade.gradeType){
                                        case "numeric":
                                            var changeScoreFunc = function(ev){
                                                newGv.gradeValue = parseFloat(numericScore.val());
                                            };
                                            numericScore.val(gv.gradeValue).attr("min",grade.numericMinimum).attr("max",grade.numericMaximum).unbind("blur").on("blur",changeScoreFunc);
                                            booleanScore.remove();
                                            booleanScoreLabel.remove();
                                            textScore.remove();
                                            break;
                                        case "text":
                                            numericScore.remove();
                                            var changeScoreFunc = function(ev){
                                                newGv.gradeValue = textScore.val();
                                            };
                                            textScore.val(gv.gradeValue).unbind("blur").on("blur",changeScoreFunc);
                                            booleanScoreLabel.remove();
                                            booleanScore.remove();
                                            break;
                                        case "boolean":
                                            numericScore.remove();
                                            var booleanScoreId = sprintf("booleanScoreId_%s",_.uniqueId());
                                            var changeScoreFunc = function(ev){
                                                newGv.gradeValue = booleanScore.prop("checked");
                                            };
                                            booleanScore.unbind("change").on("change",changeScoreFunc).prop("checked",gv.gradeValue).attr("id",booleanScoreId);
                                            booleanScoreLabel.attr("for",booleanScoreId);
                                            textScore.remove();
                                            break;
                                        default:
                                            numericScore.remove();
                                            booleanScore.remove();
                                            booleanScoreLabel.remove();
                                            textScore.remove();
                                            break;
                                        }
                                        var cbId = sprintf("privateComment_%s",_.uniqueId);
                                        var commentBox = gvChangeElem.find(".gradeValueCommentTextbox").val(gv.gradeComment).attr("id",cbId);
                                        commentBox.unbind("blur").on("blur",function(){
                                            newGv.gradeComment = $(this).val();
                                        });
                                        gvChangeElem.find(".gradeValueCommentTextboxLabel").attr("for",cbId);
                                        var pvcbId = sprintf("privateComment_%s",_.uniqueId);
                                        var privateCommentBox = gvChangeElem.find(".gradeValuePrivateCommentTextbox").val(gv.gradePrivateComment).attr("id",pvcbId);
                                        privateCommentBox.unbind("blur").on("blur",function(){
                                            newGv.gradePrivateComment = $(this).val();
                                        });
                                        gvChangeElem.find(".gradeValuePrivateCommentTextboxLabel").attr("for",pvcbId);
                                        var gvChangeSubmit = gvChangeElem.find(".submitGradeValueChange");
                                        gvChangeSubmit.unbind("click").on("click",function(){
                                            var stanzaToSend = _.cloneDeep(newGv);
                                            delete stanzaToSend.remoteGrade;
                                            delete stanzaToSend.remoteComment;
                                            delete stanzaToSend.remotePrivateComment;
                                            sendStanza(stanzaToSend);
                                            gv.gradeValue = newGv.gradeValue;
                                            gv.gradeComment = newGv.gradeComment;
                                            gv.gradePrivateComment = newGv.gradePrivateComment;
                                            gradeValues[grade.id][newGv.gradedUser] = newGv;
                                            changeGvAlert.closeAlert();
                                            generateData(withData);
                                        });
                                        var gvChangeCancel = gvChangeElem.find(".cancelGradeValueChange");
                                        gvChangeCancel.unbind("click").on("click",function(){
                                            changeGvAlert.closeAlert();
                                        });
                                        gvActualContainer.append(gvChangeElem);
                                    };
                                    var gradebookFields = [
                                        {name:"gradedUser",type:"text",title:"Who",readOnly:true,sorting:true},
                                        {name:"timestamp",type:"dateField",title:"When",readOnly:true,itemTemplate:function(t){
                                            if(t == 0){
                                                return "";
                                            }
                                            return moment(t).format('MMM Do YYYY, h:mm a');
                                        }},
                                        {name:"gradeValue",type:"text",title:"Score",readOnly:true, sorting:true },
                                        {name:"gradeComment",type:"text",title:"Comment",readOnly:true,sorting:true},
                                        {name:"gradePrivateComment",type:"text",title:"Private comment",readOnly:true,sorting:true}
                                    ];
                                    if ("foreignRelationship" in grade){
                                        gradebookFields.push(
                                            {name:"remoteGrade",type:"text",title:"Remote score",readOnly:true,sorting:true},
                                            {name:"remoteComment",type:"text",title:"Remote comment",readOnly:true,sorting:true},
                                            {name:"remotePrivateComment",type:"text",title:"Remote private comment",readOnly:true,sorting:true}
                                        );
                                    }
                                    gradebookDatagrid.jsGrid({
                                        width:"100%",
                                        height:"auto",
                                        inserting:false,
                                        editing:false,
                                        sorting:true,
                                        paging:true,
                                        rowClick: function(obj){
                                            changeGradeFunc(obj.item);
                                        },
                                        noDataContent: "No gradeable users",
                                        controller: {
                                            loadData: function(filter){
                                                var enriched = data;
                                                _.forEach(data,function(d){
                                                    if ("foreignRelationship" in grade && grade.id in remoteGradeValuesCache){
                                                        var remoteGradeValues = remoteGradeValuesCache[grade.id];
                                                        var rgv = _.find(remoteGradeValues,function(rgval){
                                                            return rgval.gradedUser == d.gradedUser;
                                                        });
                                                        if (rgv != undefined){
                                                            if ("gradeValue" in rgv && !("remoteGradeValue" in d)){
                                                                d.remoteGrade = rgv.gradeValue;
                                                            }
                                                            if ("gradeComment" in rgv && !("remoteComment" in d)){
                                                                d.remoteComment = rgv.gradeComment;
                                                            }
                                                            if ("gradePrivateComment" in rgv && !("remotePrivateComment" in d)){
                                                                d.remotePrivateComment = rgv.gradePrivateComment;
                                                            }
                                                        }
                                                    }
                                                });
                                                //console.log("loading data",grade,remoteGradeValuesCache,data,enriched);
                                                if ("sortField" in filter){
                                                    var sorted = _.sortBy(enriched,function(gv){
                                                        return gv[filter.sortField];
                                                    });
                                                    if ("sortOrder" in filter && filter.sortOrder == "desc"){
                                                        sorted = _.reverse(sorted);
                                                    }
                                                    return sorted
                                                } else {
                                                    return enriched;
                                                }
                                            }
                                        },
                                        pageLoading:false,
                                        fields:gradebookFields
                                    });
                                    gradebookDatagrid.jsGrid("loadData");
                                    gradebookDatagrid.jsGrid("sort",{
                                        field:"gradedUser",
                                        order:"desc"
                                    });
                                    if ("foreignRelationship" in grade){
                                        var system = grade.foreignRelationship["sys"];
                                        var parts = grade.foreignRelationship["key"].split("_");
                                        var orgUnit = parts[0];
                                        var gradeId = parts[1];
                                        innerRoot.find(".getRemoteData").unbind("click").on("click",function(){
                                            var b = this;
                                            spin(b,true);
                                            $.getJSON(sprintf("/getExternalGradeValues/%s/%s/%s",system,orgUnit,gradeId),function(remoteGrades){
                                                remoteGradeValuesCache[grade.id] = remoteGrades;
                                                generateData(function(data){
                                                    var modifiedData = data;
                                                    _.forEach(modifiedData,function(datum){
                                                        var thisRemoteGrade = _.find(remoteGrades,function(rg){
                                                            return rg.gradedUser == datum.gradedUser;
                                                        });
                                                        if (thisRemoteGrade !== undefined){
                                                            if ("gradeValue" in thisRemoteGrade){
                                                                datum.remoteGrade = thisRemoteGrade.gradeValue;
                                                            }
                                                            if ("gradeComment" in thisRemoteGrade){
                                                                datum.remoteComment = thisRemoteGrade.gradeComment;
                                                            }
                                                            if ("gradePrivateComment" in thisRemoteGrade){
                                                                datum.remotePrivateComment = thisRemoteGrade.gradePrivateComment;
                                                            }
                                                        }
                                                        spin(b,false);
                                                    });
                                                    return withData(modifiedData);
                                                });
                                            }).fail(function(jqxhr,textStatus,error){
                                                spin(b,false);
                                                console.log("error",textStatus,error);
                                            });
                                        });
                                        innerRoot.find(".sendGradesToRemote").unbind("click").on("click",function(){
                                            var b = this;
                                            spin(b,true,function(e){
                                                return $(e).find("span");
                                            });
                                            var gradesToSend = _.filter(gradeValues[grade.id],function(g){
                                                return g.gradeValue != undefined;
                                            });
                                            $.ajax({
                                                type:"POST",
                                                data:JSON.stringify(gradesToSend),
                                                dataType:"json",
                                                success:function(remoteGrades){
                                                    remoteGradeValuesCache[grade.id] = remoteGrades;
                                                    var failedGrades = _.filter(gradesToSend,function(gts){
                                                        var matchingGrade = _.find(remoteGrades,function(rg){
                                                            return rg.gradedUser == gts.gradedUser && rg.gradeValue == gts.gradeValue && (gts.gradeComment === undefined || rg.gradeComment == gts.gradeComment) && (gts.gradePrivateComment === undefined || rg.gradePrivateComment == gts.gradePrivateComment);
                                                        });
                                                        return matchingGrade === undefined;
                                                    });
                                                    if (failedGrades.length){
                                                        errorAlert("External grade synchronization failed",sprintf("<div><div>Some grades failed to synchronize to the external gradebook:</div><div><ul>%s</ul></div><div>This might be because these users aren't available in the external gradebook to be assessed.</div><div>These grades are still available in this gradebook, but may not be available in the external gradebook.</div>",_.map(failedGrades,function(fg){
                                                            return sprintf("<li>%s</li>",fg.gradedUser);
                                                        }).join("")));
                                                    }
                                                    generateData(function(data){
                                                        var modifiedData = data;
                                                        _.forEach(modifiedData,function(datum){
                                                            var thisRemoteGrade = _.find(remoteGrades,function(rg){
                                                                return rg.gradedUser == datum.gradedUser;
                                                            });
                                                            if (thisRemoteGrade !== undefined){
                                                                if ("gradeValue" in thisRemoteGrade){
                                                                    datum.remoteGrade = thisRemoteGrade.gradeValue;
                                                                }
                                                                if ("gradeComment" in thisRemoteGrade){
                                                                    datum.remoteComment = thisRemoteGrade.gradeComment;
                                                                }
                                                                if ("gradePrivateComment" in thisRemoteGrade){
                                                                    datum.remotePrivateComment = thisRemoteGrade.gradePrivateComment;
                                                                }
                                                            }
                                                        });
                                                        spin(b,false);
                                                        return withData(modifiedData);
                                                    })
                                                },
                                                url:sprintf("/updateExternalGradeValues/%s/%s/%s",system,orgUnit,gradeId),
                                                contentType:"application/json"
                                            }).fail(function(jqxhr,textStatus,error){
                                                spin(b,false);
                                                console.log("error",textStatus,error);
                                            });
                                        });
                                    } else {
                                        innerRoot.find(".gradeSyncActions").remove();
                                    }
                                    spin(innerRoot,false);
                                };
																gradebookReRenderFunc = function(){
																	generateData(withData)
																};
                                generateData(withData);
                            });
                            return rootElem;
                        } else {
                            return $("<span/>");
                        }
                    }
                }
            ];
            var studentFields = [
                {
                    name:"myGradeValue",
                    type:"text",
                    title:"Score",
                    readOnly:true,
                    sorting:true
                },
                {
                    name:"myGradeComment",
                    type:"text",
                    title:"Comment",
                    readOnly:true,
                    sorting:true
                }
            ];
            if (Conversations.shouldModifyConversation()){
                gridFields = _.concat(gridFields,teacherFields);
            } else {
                gridFields = _.concat(gridFields,studentFields);
            };
            gradesDatagrid.jsGrid({
                width:"100%",
                height:"auto",
                inserting:false,
                editing:false,
                sorting:true,
                paging:true,
                noDataContent: "No grades",
                controller: {
                    loadData: function(filter){
                        var shouldModifyConversation = Conversations.shouldModifyConversation();
                        var filteredGrades = _.map(_.filter(grades,function(grade){
                            return shouldModifyConversation || grade.visible;
                        }),function(grade){
                            var thisGrade = gradeValues[grade.id];
                            if (thisGrade !== undefined){
                                var myGradeValue = thisGrade[UserSettings.getUsername()];
                                if (myGradeValue !== undefined){
                                    grade.myGradeValue = myGradeValue.gradeValue;
                                    grade.myGradeComment = myGradeValue.gradeComment;
                                }
                            }
                            return grade;
                        });
                        if ("sortField" in filter){
                            var sorted = _.sortBy(filteredGrades,function(sub){
                                return sub[filter.sortField];
                            });
                            if ("sortOrder" in filter && filter.sortOrder == "desc"){
                                sorted = _.reverse(sorted);
                            }
                            return sorted;
                        } else {
                            return filteredGrades;
                        }
                    }
                },
                pageLoading:false,
                fields: gridFields
            });
            gradesDatagrid.jsGrid("sort",{
                field:"timestamp",
                order:"desc"
            });
            reRenderFunc = function() {
                if (WorkQueue != undefined) {
                    WorkQueue.enqueue(function () {
                        gradesDatagrid.jsGrid("loadData");
                        var sortObj = gradesDatagrid.jsGrid("getSorting");
                        if ("field" in sortObj) {
                            gradesDatagrid.jsGrid("sort", sortObj);
                        }
                        gradeCreateButton.unbind("click")
                        if (Conversations.shouldModifyConversation()) {
                            gradeCreateButton.unbind("click").on("click", function () {
                                console.log("clicked createButton");
                                if (Conversations.shouldModifyConversation()) {

                                    var loc = Conversations.getCurrentSlideJid();
                                    var user = UserSettings.getUsername();
                                    var newGrade = {
                                        type: "grade",
                                        name: "",
                                        description: "",
                                        audiences: [],
                                        author: user,
                                        location: loc,
                                        id: sprintf("%s_%s_%s", loc, user, new Date().getTime().toString()),
                                        gradeType: "numeric",
                                        numericMinimum: 0,
                                        numericMaximum: 100,
                                        visible: false,
                                        timestamp: 0
                                    };
                                    sendStanza(newGrade);
                                }
                            }).show();
                        } else {
                            gradeCreateButton.hide();
                        }
                        gradebookReRenderFunc();
                    });
                }
            }
            reRenderFunc();
        }
        var loadAfterConversationsLoaded = function(){
            if ("jid" in Conversations.getCurrentConversation()){
                setupGrades();
            } else {
                _.delay(loadAfterConversationsLoaded,500);
            }
        };
        loadAfterConversationsLoaded();
    });
    return {
        getGrades:function(){return grades;},
        getGradeValues:function(){return gradeValues;},
        reRender:reRenderFunc
    };
})();
