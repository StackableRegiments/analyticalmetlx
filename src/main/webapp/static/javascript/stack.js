$('.toggleDetail').live('click',function(){
    var root = $(this);
    var comments = root.parent().find('.commentText');
    comments.toggle();
});
$('.toggleCloud').live('click',function(){
    $(this).parent().find('.cloud').toggle();
});
$('.commentText')
    .live('mouseover',function(){
        $(this).css('z-index','100');
    })
    .live('mouseout',function(){
        $(this).css('z-index','0');
    });
$('.authorName')
    .live('mouseover',function(){
        var marker = $('.locationContainer .'+this.innerHTML);
        marker.css({width:"60px",height:"60px"});
    })
    .live('mouseout',function(){
        var marker = $('.locationContainer .'+this.innerHTML);
        marker.css({width:"30px",height:"30px"});
    });
function metlOffset(pos){
    var offset = $('#mainSlide').offset();
    return [Math.round(pos[0]-offset.left),Math.round(pos[1]-offset.top)];
}
function receiveInputBox(idArray){
    var selectorId = idArray[0]
    var submitId = idArray[1]
    $(selectorId).focus().keydown(function(e){
        if (e.keyCode == '13'){
            e.preventDefault();
            $(submitId).click();
        }
    });
}
function bob(selector,direction){
    $(selector).find(".numeric").effect("bounce",{direction:direction,times:3})
    refreshFilterAndSort()
}
function bobUp(selector){ bob(selector,"up") }
function bobDown(selector){ bob(selector,"down") }
function divJiggle(selector){ $(selector).effect("bounce",{direction:"up",times:1}) }
function emerge(selector){
    $(selector).stop(true,true).hide().slideDown()
    refreshFilterAndSort()
}
function fadeOutAndRemove(selector){
    $(selector).delay(1000).fadeOut(1000, function () { $(this).remove(); });
}
function pongLatency(from){
    var latencyPong = new Date().getTime() - from;
    var status = "green"
    if(latencyPong > 300)
        status = "red"
    if(latencyPong > 100)
        status = "yellow"
    $('#latencyGauge').html(latencyPong).css("background-color",status);
    setTimeout(pingLatency,20000);
}
function ensureId(id){
    if($('#'+id).length == 0){
        $('#stackQuestions table').prepend($(sprintf("<tr id='%s' class='%s'/>",id,id)));
    }
}
function jiggle(id){ emerge("#"+id) }
function getResponseInformation(){ return getMetaDataForCollection($(".responseItem"))}
function getSummaryInformation(){ return getMetaDataForCollection($(".questionSummaryItem"))}
function getMetaDataForCollection(selector){
    return selector.map(function(i){
        var self = $(this)
        var branchId = self.attr("id")
        var branchIdSelector = sprintf("#%s",branchId)
        var summaryObj = {
            id :branchIdSelector,
            depth : $("#branchDepth_"+branchId).text(),
            author : $("#branchAuthor_"+branchId).text(),
            lastModified : $("#branchLastModified_"+branchId).text(),
            rating : $("#branchRating_"+branchId).text()
        };
        return summaryObj
    })
}
function filterCollectionByAuthor(collection,incomingAuthorSelector){
    collection.map(function(i){
        var itemAuthor = this.author.toLowerCase()
        if (incomingAuthorSelector.map(function(j){return $(this).html().toLowerCase().trim()}).filter(function(k){return (this == itemAuthor)}).length > 0){ $(this.id).show() }
        else { $(this.id).hide() }
    })
}
function filterSummariesByTeacher(){ filterCollectionByAuthor(getSummaryInformation(),$(".teacher")) }
function filterSummariesByMe(){ filterCollectionByAuthor(getSummaryInformation(),$("#repAuthor")) }
function filterSummariesByAll(){ getSummaryInformation().map(function(i){ $(this.id).show() }) }
function filterResponsesByTeacher(){ filterCollectionByAuthor(getResponseInformation(),$(".teacher")) }
function filterResponsesByMe(){ filterCollectionByAuthor(getResponseInformation(),$("#repAuthor")) }
function filterResponsesByAll(){ getResponseInformation().map(function(i){      $(this.id).show()       }) }
function sortResponsesByRecent(){ sortByMetaData($(".responseItem"),".branchLastModified")}
function sortSummariesByRecent(){ sortByMetaData($(".questionSummaryItem"),".branchLastModified") }
function sortResponsesByAuthor(){ sortByMetaData($(".responseItem"),".branchAuthor") }
function sortSummariesByAuthor(){ sortByMetaData($(".questionSummaryItem"),".branchAuthor") }
function sortResponsesByPopular(){ sortByMetaData($(".responseItem"),".branchRating") }
function sortSummariesByPopular(){ sortByMetaData($(".questionSummaryItem"),".branchRating") }
function sortResponsesByDepth(){ sortByMetaData($(".responseItem"),".branchDepth") }
function sortSummariesByDepth(){ sortByMetaData($(".questionSummaryItem"),".branchDepth") }
function sortByMetaData(collection,criteria){
    var getValue = function(item){
        var newVal =  $(item.e).find(criteria).text()
        return parseInt(newVal) || newVal
    }
    collection.tsort('',{sortFunction:function(a,b){
        var aValue = getValue(a)
        var bValue = getValue(b)
        return (aValue === bValue)?0:((aValue > bValue)?-1:1)
    }})
}
function refreshFilterAndSort(){
    refreshResponseSort()
    refreshSummarySort()
    refreshQuestionFilter()
    refreshResponseFilter()
    refreshActiveTopic()
    refreshCommentExpansion(false)
    refreshHelpTooltips()
}
function setActiveResponseSort(sort){
    $("#currentResponseSort").html(sort)
    refreshResponseSort()
}
function refreshResponseSort(){
    var sort = $("#currentResponseSort").html()
    if ($("#responseSorterSelectBox").val() != sort){
        $("#responseSorterSelectBox").val(sort)
    }
    if(sort ==  "active"){
        $("#currentResponseListingSorter").html("active")
        sortResponsesByDepth()
    }
    else if(sort == "popular"){
        $("#currentResponseListingSorter").html("popular")
        sortResponsesByPopular()
    }
    else if(sort == "recent"){
        $("#currentResponseListingSorter").html("recent")
        sortResponsesByRecent()
    }
}
function setActiveQuestionSort(sort){
    $("#currentSummarySort").html(sort)
    refreshSummarySort()
}
function refreshSummarySort(){
    var sort = $("#currentSummarySort").html()
    if ($("#summarySorterSelectBox").val() != sort){
        $("#summarySorterSelectBox").val(sort)
    }
    if(sort ==  "active"){
        $("#currentListingSort").html("active")
        sortSummariesByDepth()
    }
    else if(sort == "popular"){
        $("#currentListingSort").html("popular")
        sortSummariesByPopular()
    }
    else if(sort == "recent"){
        $("#currentListingSort").html("recent")
        sortSummariesByRecent()
    }
}
function setActiveTopic(topic){
    $("#stackId").html(topic)
    refreshActiveTopic()
}
function refreshActiveTopic(){
    var topicId = $("#stackId").text()
    $(".topicName").removeClass("activeTopic")
    $(sprintf("#topic_%s",topicId)).addClass("activeTopic")
}
function setRepForUser(rep){
    var who = rep[0]
    var form = rep[1]
    var summ = rep[2]
    var authorString = ".author_"+who
    if (form != undefined)
        $(authorString+" .authorScore").text(new Number(form).toString())
    if (summ != undefined)
        $(authorString+" .authorMark").text(new Number(summ).toString())
}
function setActiveResponseFilter(filter){
    $("#currentResponseFilter").html(filter)
    refreshResponseFilter()
}
function refreshResponseFilter(){
    var filter = $("#currentResponseFilter").text()
    if($("#responseFilterSelectBox").val() != filter){
        $("#responseFilterSelectBox").val(filter)
    }
    if(filter ==  "all"){
        $("#currentResponseListingFilter").html("All")
        filterResponsesByAll()
    }
    else if(filter == "my"){
        $("#currentResponseListingFilter").html("My")
        filterResponsesByMe()
    }
    else if(filter == "teacher"){
        $("#currentResponseListingFilter").html("Teacher's")
        filterResponsesByTeacher()
    }
}
function setActiveQuestionFilter(filter){
    $("#currentSummaryFilter").html(filter)
    refreshQuestionFilter()
}
function refreshQuestionFilter(){
    var filter = $("#currentSummaryFilter").text()
    if ($("#summaryFilterSelectBox").val() != filter){
        $("#summaryFilterSelectBox").val(filter)
    }
    if(filter ==  "all"){
        $("#currentListingFilter").html("All")
        filterSummariesByAll()
    }
    else if(filter == "my"){
        $("#currentListingFilter").html("My")
        filterSummariesByMe()
    }
    else if(filter == "teacher"){
        $("#currentListingFilter").html("Teacher's")
        filterSummariesByTeacher()
    }
}

var commentExpansionState = [];
function setCommentExpansionState (state) {
    commentExpansionState = state;
    refreshCommentExpansion(true);
}
function refreshCommentExpansion (wantSlide) {
    var commentsFor = function (elem) {
        return $("#commentsFor_"+idFor(elem));
    }
    var updateCommentExpanderText = function (elem, expanded) {
        var text = expanded ? "-" : "+";
        $(elem).text("["+text+"]");
    }
    var doHide = function (jq) {
        if (wantSlide)
            jq.filter(":visible").slideUp();
        else
            jq.hide();
    }
    var doShow = function (jq) {
        if (wantSlide)
            jq.not(":visible").slideDown();
        else
            jq.show();
    }
    var shouldShow = function (elem) {
        return $.inArray(idFor(elem),commentExpansionState) > -1
    }
    var idFor = function (elem) {
        return elem.id.split("_")[1];
    }
    $(".serverInformingExpander").each(function () {
        if (shouldShow(this)){
            var id = idFor(this)
            doShow($("#commentsFor_"+id));
            updateCommentExpanderText("#serverInformingExpander_"+id, true);
        }
        else {
            var id = idFor(this)
            doHide($("#commentsFor_"+id))
            updateCommentExpanderText("#serverInformingExpander_"+id, false);
        }
    })
}
$(function(){
    $("#help").colorbox({href:"/help",initialHeight:900});
})
function refreshHelpTooltips () {
    $('.helpContainer').map(function(){$(this).attr('title',$(this).find('.helpAnnotation').text())});
}
function hideSearchResultsContainer(){
    $('#searchResultsContainer').hide();
    return void(0);
}
function multiSubmit(){
    var box = $(".inputDialogContentBox").attr("name")
    var submit = $(".inputDialogSubmitButton").attr("name")
    var time = new Date()
    for(var i = 0;i < 100;i++){
        console.log("Forging",i)
        var text = "Forged "+i+" @ "+time
        var params = {}
        params[box] = text
        params[submit] = "_"
        $.post("http://localhost:8080", params)
    }
}
