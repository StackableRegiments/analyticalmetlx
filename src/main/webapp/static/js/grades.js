var Grades = (function(){
	var gradesDatagrid = {};
	var grades = {};
	var gradeValues = {};
	var gradeCreateButton = {};
	var gradeActionButtonsTemplate = {};
	var gradeEditTemplate = {};
	var gradeAssessTemplate = {};
	var gradebooks = [];
	var reRenderFunc = function(){
		WorkQueue.enqueue(function(){
			gradesDatagrid.jsGrid("loadData");
			var sortObj = gradesDatagrid.jsGrid("getSorting");
			if ("field" in sortObj){
				gradesDatagrid.jsGrid("sort",sortObj);
			}
			gradeCreateButton.unbind("click")
			if (Conversations.shouldModifyConversation()){
				gradeCreateButton.on("click",function(){
					console.log("clicked createButton");
					if (Conversations.shouldModifyConversation()){
					
						var loc = Conversations.getCurrentSlideJid();
						var user = UserSettings.getUsername();
						var newGrade = {
							type:"grade",
							name:"",
							description:"",
							audiences:[],
							author:user,
							location:loc,
							id:sprintf("%s_%s_%s",loc,user,new Date().getTime().toString()),
							gradeType:"numeric",
							numericMinimum:0,
							numericMaximum:100,
							visible:false,
							timestamp:0						
						};
						sendStanza(newGrade);
					}
				}).show();
			} else {
				gradeCreateButton.hide();
			}
		});
	};
	var clearState = function(){
		grades = {};
		gradeValues = {};
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
						}
					break;
					case "numericGradeValue":
						var gradeColl = gradeValues[stanza.gradeId];
						if (gradeColl == undefined){
							gradeColl = {};
							gradeValues[stanza.gradeId] = gradeColl;
						}
						var oldGrade = gradeColl[stanza.gradedUser];
						if (oldGrade == undefined || oldGrade.timestamp < stanza.timestamp){
							gradeColl[stanza.gradedUser] = stanza;
						}
					break;
					case "booleanGradeValue":
						var gradeColl = gradeValues[stanza.gradeId];
						if (gradeColl == undefined){
							gradeColl = {};
							gradeValues[stanza.gradeId] = gradeColl;
						}
						var oldGrade = gradeColl[stanza.gradedUser];
						if (oldGrade == undefined || oldGrade.timestamp < stanza.timestamp){
							gradeColl[stanza.gradedUser] = stanza;
						}
					break;
					case "textGradeValue":
						var gradeColl = gradeValues[stanza.gradeId];
						if (gradeColl == undefined){
							gradeColl = {};
							gradeValues[stanza.gradeId] = gradeColl;
						}
						var oldGrade = gradeColl[stanza.gradedUser];
						if (oldGrade == undefined || oldGrade.timestamp < stanza.timestamp){
							gradeColl[stanza.gradedUser] = stanza;
						}
					break;
					default:
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
			{name:"timestamp",type:"dateField",title:"When",readOnly:true},
			{name:"gradeType",type:"text",title:"Type",readOnly:true,sorting:true},
			{name:"author",type:"text",title:"Who",readOnly:true,sorting:true},
			{
				name:"identity",
				type:"text",
				title:"actions",
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
								title:"edit grade",
								width:"50%",
								content:outer[0].outerHTML
							});
							var innerRoot = gradeEditTemplate.clone();
							var nameId = sprintf("gradeName_%s",uniqId);
							var nameInputBox = innerRoot.find(".gradeNameInputBox");
							var changeNameFunc = function(ev){
								newGrade.name = nameInputBox.val();
							};
							nameInputBox.attr("id",nameId).on("blur",changeNameFunc).val(newGrade.name);
							innerRoot.find(".gradeNameLabel").attr("for",nameId);
							var descId = sprintf("gradeDesc_%s",uniqId);
							var changeDescFunc = function(ev){
								newGrade.description = descInputBox.val();
							};
							var descInputBox = innerRoot.find(".gradeDescriptionInputBox");
							descInputBox.attr("id",descId).on("blur",changeDescFunc).val(newGrade.description);
							innerRoot.find(".gradeDescriptionLabel").attr("for",descId);
							var selectId = sprintf("gradeType_%s",uniqId);
							var typeSelect = innerRoot.find(".gradeTypeSelect");
							var minTextbox = innerRoot.find(".numericMinTextbox");
							var maxTextbox = innerRoot.find(".numericMaxTextbox");
							var changeMinFunction = function(ev){
								if (newGrade.gradeType == "numeric"){
									newGrade.numericMinimum = parseFloat(maxTextbox.val());
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
							minTextbox.on("blur",changeMinFunction).attr("id",minId);
							maxTextbox.on("blur",changeMaxFunction).attr("id",maxId);
							var reRenderGradeTypeOptions = function(){
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
							typeSelect.attr("id",selectId).on("change",function(){
								newGrade.gradeType = typeSelect.val();
								reRenderGradeTypeOptions();
							}).val(newGrade.gradeType);
							reRenderGradeTypeOptions();
							innerRoot.find(".gradeTypeLabel").attr("for",selectId);
							var visibleId = sprintf("gradeVisible_%s",uniqId);
							innerRoot.find(".gradeVisibleLabel").attr("for",visibleId);
							var visibleCheckbox = innerRoot.find(".gradeVisibleCheckbox");
							visibleCheckbox.attr("id",visibleId).prop("checked",newGrade.visible).on("change",function(ev){
								newGrade.visible = visibleCheckbox.prop("checked");
							});
							var wantsToAssociate = undefined;
							var chosenGradebook = undefined;
							var orgUnits = [];
							var chosenOrgUnit = undefined;
							var associatedGrade = undefined;
							var reRenderAssociations = function(){
								var aNodes = innerRoot.find(".associateController");
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
										$.getJSON(sprintf("/getExternalGrade/%s/%s/%s",system,orgUnit,gradeId),function(remoteGrade){
											newGrade.description = remoteGrade.description;
											newGrade.name = remoteGrade.name;
											newGrade.gradeType = remoteGrade.gradeType;
											newGrade.numericMinimum = remoteGrade.numericMinimum;
											newGrade.numericMaximum = remoteGrade.numericMaximum;
											jAlert.closeAlert();
											renderEditGradeAlert();
										}).fail(function(jqxhr,textStatus,error){
											alert(sprintf("error: %s \r\n %s",textStatus,error));
										});
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
												chosenGradebook = gradebooks[0];
											}
											reRenderAssociations();
										});
									} else if (chosenGradebook == undefined){
										chosenGradebook = gradebooks[0];
										aNodes.find(".chooseGradebook").html(_.map(gradebooks,function(gb){

											return $("<option/>",{
												value:gb,
												text:gb
											});
										})).unbind("change").on("change",function(ev){
											chosenGradebook = $(this).val();
										});
										aNodes.find(".commitGradebook").unbind("click").on("click",function(){
											reRenderAssociations();
										});
										aNodes.find(".requestAssocPhase2").show();
									} else if (chosenOrgUnit === undefined){
										$.getJSON(sprintf("/getExternalGradebookOrgUnits/%s",chosenGradebook),function(data){
											console.log("requestedOrgUnits:",data);
											chosenOrgUnit = data[0].foreignRelationship.key;
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
										}).fail(function(jqxhr,textStatus,error){
											alert(sprintf("error: %s \r\n %s",textStatus,error));
										});
									} else {

										aNodes.find(".requestAssocPhase4").show();
										aNodes.find(".createGrade").unbind("click").on("click",function(){
											$.ajax({
												type:"POST",
												url:sprintf("/createExternalGrade/%s/%s",chosenGradebook,chosenOrgUnit),
												data:JSON.stringify(newGrade),
												success:function(data){
													console.log("createdGrades:",newGrade,data);
													newGrade.foreignRelationship = {
														sys:data.foreignRelationship.sys,
														key:data.foreignRelationship.key
													}
													sendStanza(newGrade);
													reRenderAssociations();
												},
												contentType:"application/json",
												dataType:'json'
											}).fail(function(jqxhr,textStatus,error){
												alert(sprintf("error: %s \r\n %s",textStatus,error));
											});
										});
									}
								}
							};
							reRenderAssociations();
							innerRoot.find(".cancelGradeEdit").on("click",function(){
								jAlert.closeAlert();
							});
							innerRoot.find(".submitGradeEdit").on("click",function(){
								sendStanza(newGrade);
								jAlert.closeAlert();
							});
							$("#"+uniqId).append(innerRoot);
						}
						rootElem.find(".editGradeButton").on("click",renderEditGradeAlert);
						rootElem.find(".assessGradeButton").on("click",function(){
							var uniqId = _.uniqueId();
							var outer = $("<div/>",{
								id:uniqId
							});
							var jAlert = $.jAlert({
								title:"assess grade",
								width:"auto",
								content:outer[0].outerHTML,
								onClose:function(){
									reRenderFunc();
								}
							});
							var innerRoot = gradeAssessTemplate.clone();
							var gradebookDatagrid	= innerRoot.find(".gradebookDatagrid");
							var assessUserTemplate = gradebookDatagrid.find(".gradeUserContainer").clone();
							gradebookDatagrid.empty();
							var generateData = function(andThen){
								var data = gradeValues[grade.id];
								if (data == undefined){
									gradeValues[grade.id] = {};
									data = {};
								};
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
											if (oldValue == undefined){
												data[name] = {
													type:gradeType,
													gradeId:grade.id,
													gradedUser:name,
													author:grade.author,
													timestamp:0,
													audiences:[]
												};
											}
										});
										console.log("possibleParticipants:",possibleParticipants,data);
										data = _.values(data);
										data = _.filter(data,function(d){
											return d.type == gradeType;
										});
										andThen(data);
									}).fail(function(jqxhr,textStatus,error){
										console.log("error",textStatus,error);
									});
								} else {
									_.forEach(possibleParticipants,function(name){
										var oldValue = data[name];
										if (oldValue == undefined){
											data[name] = {
												type:gradeType,
												gradeId:grade.id,
												gradedUser:name,
												author:grade.author,
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
								var gradebookFields = [
									{name:"gradedUser",type:"text",title:"Who",readOnly:true,sorting:true},
									{name:"timestamp",type:"dateField",title:"When",readOnly:true},
									{
										name:"gradeValue",
										type:"text",
										title:"Score",
										readOnly:true,
										sorting:true,
										itemTemplate:function(score,gradeValue){
											var scoringRoot = assessUserTemplate.clone();
											var numericScore = scoringRoot.find(".numericScore");
											var booleanScore = scoringRoot.find(".booleanScore");
											var booleanScoreLabel = scoringRoot.find(".booleanScoreLabel");
											var textScore = scoringRoot.find(".textScore");
											switch (grade.gradeType){
												case "numeric":
													var changeScoreFunc = function(ev){
														gradeValue.gradeValue = parseFloat(numericScore.val());
														sendStanza(gradeValue);			
													};
													numericScore.val(gradeValue.gradeValue).attr("min",grade.numericMinimum).attr("max",grade.numericMaximum).on("blur",changeScoreFunc);
													booleanScore.remove();
													booleanScoreLabel.remove();
													textScore.remove();
												break;
												case "text":
													numericScore.remove();
													var changeScoreFunc = function(ev){
														gradeValue.gradeValue = textScore.val();
														sendStanza(gradeValue);
													};	
													textScore.val(gradeValue.gradeValue).on("blur",changeScoreFunc);
													booleanScoreLabel.remove();
													booleanScore.remove();
												break;
												case "boolean":
													numericScore.remove();
													var booleanScoreId = sprintf("booleanScoreId_%s",_.uniqueId());
													var changeScoreFunc = function(ev){
														gradeValue.gradeValue = booleanScore.prop("checked");
														sendStanza(gradeValue);			
													};
													booleanScore.on("change",changeScoreFunc).prop("checked",gradeValue.gradeValue).attr("id",booleanScoreId);
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
											return scoringRoot;	
										}
									}
								];

								if ("foreignRelationship" in grade){
									gradebookFields.push(
										{name:"remoteGrade",type:"text",title:"Remote score",readOnly:true,sorting:true}
									);
								}
								$("#"+uniqId).append(innerRoot);
								gradebookDatagrid.jsGrid({
									width:"100%",
									height:"auto",
									inserting:false,
									editing:false,
									sorting:true,
									paging:true,
									noDataContent: "No gradeable users",
									controller: {
										loadData: function(filter){
											if ("sortField" in filter){
												var sorted = _.sortBy(data,function(gv){
													return gv[filter.sortField];
												});
												if ("sortOrder" in filter && filter.sortOrder == "desc"){
													sorted = _.reverse(sorted);
												}
												return sorted
											} else {
												return data;
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
									innerRoot.find(".getRemoteData").on("click",function(){
										$.getJSON(sprintf("/getExternalGradeValues/%s/%s/%s",system,orgUnit,gradeId),function(remoteGrades){
											generateData(function(data){
												var modifiedData = data;
												_.forEach(modifiedData,function(datum){
													var thisRemoteGrade = _.find(remoteGrades,function(rg){
														return rg.gradedUser == datum.gradedUser;
													});
													if (thisRemoteGrade !== undefined){
														datum.remoteGrade = thisRemoteGrade.gradeValue;
													}
												});
												return withData(modifiedData);
											});
										}).fail(function(jqxhr,textStatus,error){
											console.log("error",textStatus,error);
										});
									});
									innerRoot.find(".sendGradesToRemote").on("click",function(){
										var gradesToSend = _.filter(gradeValues[grade.id],function(g){
											return g.gradeValue != undefined;
										});
										$.ajax({
											type:"POST",
											data:JSON.stringify(gradesToSend),
											dataType:"json",
											success:function(remoteGrades){
												generateData(function(data){
													var modifiedData = data;
													_.forEach(modifiedData,function(datum){
														var thisRemoteGrade = _.find(remoteGrades,function(rg){
															return rg.gradedUser == datum.gradedUser;
														});
														if (thisRemoteGrade !== undefined){
															datum.remoteGrade = thisRemoteGrade.gradeValue;
														}
													});
													return withData(modifiedData);
												})
											},
											url:sprintf("/updateExternalGradeValues/%s/%s/%s",system,orgUnit,gradeId),
											contentType:"application/json"
										}).fail(function(jqxhr,textStatus,error){
											console.log("error",textStatus,error);
										});
									});
								} else {
									innerRoot.find(".gradeSyncActions").remove();
								}
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
		];
		if (Conversations.shouldModifyConversation()){
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
		reRenderFunc();
	});
	return {
		getGrades:function(){return grades;},
		getGradeValues:function(){return gradeValues;},	
		reRender:reRenderFunc
	};
})();
