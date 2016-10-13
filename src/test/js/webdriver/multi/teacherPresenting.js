var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var ANIMATION_DELAY = 300;

teacher.windowHandlePosition({x:0,y:0});
student.windowHandlePosition({x:500,y:0});
describe('When a teacher presents, they', function() {
    var teacherT = board(teacher);
    var studentT = board(student);
    it('should go to application', function () {
        browser.url('/board');
    });
    it('should login', function () {
        teacher.setValue('input[type=text]', 'teacher');
        student.setValue('input[type=text]', 'student');
    });
    it('should submit the login form', function () {
        browser.click('input[type=submit]');
        browser.waitForExist("#conversationSearchBox");
    });
    it("should join a new conversation", function() {
        teacher.click("#createConversationButton");
        teacher.pause(1000);
        teacher.click(".newConversationTag");
        teacher.waitForExist("#board");
    });
    it("student should find the conversation",function(){
        student.setValue("#conversationSearchBox > input","teacher");
        student.click("#searchButton");
        student.pause(1000);
        student.click(".newConversationTag");
        student.waitForExist("#board");
    });
    it("should be able to check content and it should be empty", function(){
        assert.equal(_.keys(teacherT.texts).length,0);
        assert.equal(_.keys(studentT.texts).length,0);
    });
    it("should insert a paragraph",function(){
        teacherT.textMode.click();
        teacherT.keyboard(50,50,"This is a paragraph of text which is being typed programatically.  It runs over multiple lines.");
        assert.equal(_.keys(teacherT.texts).length,1);
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length,[
            "Consistently sized run"].length);
	browser.debug();
    });
    it("should highlight a word and enlarge it",function(){
        teacher.moveToObject("#board",100,100);
        teacher.leftClick();
        teacher.doDoubleClick();
        teacher.click("#fontLarger");
        assert.equal(_.keys(teacherT.texts).length,1);
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length,[
            "Before","Enlarged","After"].length);
        teacher.moveToObject("#board",100,300);
        teacher.doDoubleClick();
        teacher.click("#redText");
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length,[
            "Before","Enlarged","After","Red","After"].length);
        teacher.moveToObject("#board",100,400);
        teacher.doDoubleClick();
        teacher.click("#fontLarger");
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length,[
            "Before","Enlarged","After","Red","After","Enlarged","After"].length);
    });
    it("should create another textbox",function(){
        teacherT.keyboard(600,500,"This is a second paragraph.  It exists to be differentiated from the first paragraph.");
        assert.equal(_.keys(teacherT.texts).length,2);
        assert.equal(_.keys(teacherT.textStanzas).length,2);
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[1]].words.length,[
            "Consistently sized run"].length);
    });
    it("should be draggable",function(){
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.x,600);
        assert.equal(active.y,500);
        assert.equal(teacherT.interactables.manualMove.length,1);
        var handle = teacherT.interactables.manualMove[0];
        teacherT.drag(handle,{x:-500,y:-250});
        active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.x,100);
        assert.equal(active.y,250);
    });
    it("should scale aspect locked",function(){
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.width,240);
        assert.equal(active.x,100);

        var handle = teacherT.interactables.resizeAspectLocked[0];
        teacherT.drag(handle,{x:200,y:0});

        active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.x,100);
        assert.equal(active.width,437);
        assert.equal(active.words[0].size, 55);
    });
    it("should scroll up on swipe out",function(){
        teacherT.swipeUp();
    });
    if("should reselect box",function(){
        teacher.moveToObject("#board",200,300);
        teacher.leftClick();
        assert.equal(teacherT.interactables.resizeFree.length,1);
    });
    it("should rewrap text retaining font size",function(){
        var handle = teacherT.interactables.resizeFree[0];
        teacher.pause(ANIMATION_DELAY);
        teacherT.drag(handle,{x:200,y:0});
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.x,100);
        assert.equal(active.width,638);
        assert.equal(active.words[0].size, 55);
    });
    it("should draw ink", function(){
        teacherT.inkMode.click();
        for(var i = 2; i < 5; i++){
            var len = 35;
            var root = (len + 20) * i;
            teacher.click(sprintf("#pen%sButton",i));
            teacherT.handwrite(_.map(_.range(0,30,0.7), function(j){
                return {
                    x: root + Math.cos(j) * len--,
                    y: root + Math.sin(j) * len--
                };
            }));
        }
        assert.equal(_.keys(teacherT.inkStanzas).length,3);
    });
    it("should add an image",function(){
        teacherT.imageMode.click();
        teacher.click("#board");
        teacher.chooseFile("#imageFileChoice","testMaterials/mapleLeaf.jpg");
        teacher.pause(4000);
        assert.equal(_.keys(teacherT.imageStanzas).length,1);
    });
    it("should enter already selected",function(){
        assert.equal(_.keys(teacherT.selection.images).length,1);
    });
    it("should clear the selection if the empty canvas is clicked",function(){
        teacherT.selectMode.click();
        teacher.leftClick("#board",1,1);
        assert.equal(_.keys(teacherT.selection.inks).length,0);
        assert.equal(_.keys(teacherT.selection.texts).length,0);
        assert.equal(_.keys(teacherT.selection.multiWordTexts).length,0);
        assert.equal(_.keys(teacherT.selection.videos).length,0);
        assert.equal(_.keys(teacherT.selection.images).length,0);
    });
    it("should select all items under the mouse when clicked",function(){
        teacher.leftClick("#board",250,250);
        assert.equal(_.keys(teacherT.selection.inks).length,0);
        assert.equal(_.keys(teacherT.selection.texts).length,0);
        assert.equal(_.keys(teacherT.selection.multiWordTexts).length,1);
        assert.equal(_.keys(teacherT.selection.videos).length,0);
        assert.equal(_.keys(teacherT.selection.images).length,1);
	/*
	teacher.debug();
	student.debug();
	*/
    });
    it("should see all published elements",function(){
        assert.equal(_.keys(studentT.imageStanzas).length,1);
        assert.equal(_.keys(studentT.textStanzas).length,2);
        assert.equal(_.keys(studentT.inkStanzas).length,3);
    });
    it("should have analyzed the text contents",function(){
        assert.equal(teacherT.themes.length,1);
    });
});
