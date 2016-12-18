var Grades = (function(){
	var gradesDatagrid = {};
	var grades = {};
	var gradeValues = {};
	var gradeCreateButton = {};
	var gradeEditButtonTemplate = {};
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
						gradeValues.push(stanza);
					break;
					case "booleanGradeValue":
						gradeValues.push(stanza);
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
		gradeEditButtonTemplate = gradesDatagrid.find(".editGradeButtonContainer").clone();
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
			{name:"author",type:"text",title:"Who",readOnly:true,sorting:true},
			{
				name:"identity",
				type:"text",
				title:"actions",
				readOnly:true,
				sorting:false,
				itemTemplate:function(identity,submission){
					if (submission.author == UserSettings.getUsername()){
						var rootElem = gradeEditButtonTemplate.clone();
						rootElem.find(".editGradeButton").on("click",function(){
							console.log("tried to edit the grade");
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
		reRender:reRenderFunc
	};
})();
