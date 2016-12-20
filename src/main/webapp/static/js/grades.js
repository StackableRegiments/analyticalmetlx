var Grades = (function(){
	var gradesDatagrid = {};
	var grades = {};
	var gradeValues = {};
	var gradeCreateButton = {};
	var gradeActionButtonsTemplate = {};
	var gradeEditTemplate = {};
	var gradeAssessTemplate = {};
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
						rootElem.find(".editGradeButton").on("click",function(){
							var newGrade = _.cloneDeep(grade);
							var uniqId = _.uniqueId();
							var outer = $("<div/>",{
								id:uniqId
							});
							var jAlert = $.jAlert({
								title:"edit grade",
								width:"auto",
								content:outer[0].outerHTML
							});
							var innerRoot = gradeEditTemplate.clone();
							var nameId = sprintf("gradeName_%s",uniqId);
							var nameInputBox = innerRoot.find(".gradeNameInputBox");
							var changeNameFunc = function(ev){
								newGrade.name = nameInputBox.val();
							};
							nameInputBox.attr("id",nameId).on("blur",changeNameFunc).val(grade.name);
							innerRoot.find(".gradeNameLabel").attr("for",nameId);
							var descId = sprintf("gradeDesc_%s",uniqId);
							var changeDescFunc = function(ev){
								newGrade.description = descInputBox.val();
							};
							var descInputBox = innerRoot.find(".gradeDescriptionInputBox");
							descInputBox.attr("id",descId).on("blur",changeDescFunc).val(grade.description);
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
										if (grade.numericMinimum === undefined){
											newGrade.numericMinimum = 0;
										};
										if (grade.numericMaximum === undefined){
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
							console.log("starting value:",grade.gradeType);
							typeSelect.attr("id",selectId).on("change",function(){
								newGrade.gradeType = typeSelect.val();
								reRenderGradeTypeOptions();
							}).val(grade.gradeType);
							reRenderGradeTypeOptions();
							innerRoot.find(".gradeTypeLabel").attr("for",selectId);
							var visibleId = sprintf("gradeVisible_%s",uniqId);
							innerRoot.find(".gradeVisibleLabel").attr("for",visibleId);
							var visibleCheckbox = innerRoot.find(".gradeVisibleCheckbox");
							visibleCheckbox.attr("id",visibleId).prop("checked",grade.visible).on("change",function(ev){
								newGrade.visible = visibleCheckbox.prop("checked");
							});
							innerRoot.find(".cancelGradeEdit").on("click",function(){
								jAlert.closeAlert();
							});
							innerRoot.find(".submitGradeEdit").on("click",function(){
								sendStanza(newGrade);
								jAlert.closeAlert();
							});
							$("#"+uniqId).append(innerRoot);
						});
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
							var data = gradeValues[grade.id];
							if (data == undefined){
								gradeValues[grade.id] = {};
								data = {};
							};
							var gradeType = sprintf("%sGradeValue",grade.gradeType);
							_.forEach(Participants.getPossibleParticipants(),function(name){
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
							console.log("data:",data);
							data = _.values(data);
							data = _.filter(data,function(d){
								return d.type == gradeType;
							});
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
						});
						return rootElem;
					} else {
						return $("<span/>");
					}
				}
			}
		];
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
					if ("sortField" in filter){
						var sorted = _.sortBy(grades,function(sub){
							return sub[filter.sortField];
						});
						if ("sortOrder" in filter && filter.sortOrder == "desc"){
							sorted = _.reverse(sorted);
						}
						return sorted;
					} else {
						return grades;
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
