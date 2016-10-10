var _ = require('lodash');
var assert = require('assert');
var board = require('./page/board.page');

var ANIMATION_DELAY = 300;

describe('Single author presenting', function() {
    var user = board(teacher);
    it('should go to application', function () {
        teacher.url('/board');
    });
    it('should login', function () {
        teacher.setValue('input[type=text]', 'teacher');
    });
    it('should submit the login form', function () {
        teacher.click('input[type=submit]');
        teacher.waitForExist("#conversationSearchBox");
    });
    it("should join a new conversation", function() {
        teacher.click("#createConversationButton");
        teacher.pause(1000);
        teacher.click(".newConversationTag");
        teacher.waitForExist("#board");
        assert.equal(teacher.execute("return document.readyState").value,"complete");
    });
    it("should be able to check content and it should be empty", function(){
        assert.equal(_.keys(user.texts).length,0);
    });
    it("should insert a paragraph",function(){
        user.textMode.click();
        user.keyboard(50,50,"This is a paragraph of text which is being typed programatically.  It runs over multiple lines.");
        assert.equal(_.keys(user.texts).length,1);
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Consistently sized run"].length);
    });
    it("should highlight a word and enlarge it",function(){
        teacher.moveToObject("#board",100,100);
        teacher.leftClick();
        teacher.doDoubleClick();
        teacher.click("#fontLarger");
        assert.equal(_.keys(user.texts).length,1);
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After"].length);
        teacher.moveToObject("#board",100,300);
        teacher.doDoubleClick();
        teacher.click("#redText");
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After","Red","After"].length);
        teacher.moveToObject("#board",100,400);
        teacher.doDoubleClick();
        teacher.click("#fontLarger");
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After","Red","After","Enlarged","After"].length);
    });
    it("should create another textbox",function(){
        user.keyboard(600,500,"This is a second paragraph.  It exists to be differentiated from the first paragraph.");
        assert.equal(_.keys(user.texts).length,2);
        assert.equal(_.keys(user.textStanzas).length,2);
        assert.equal(user.textStanzas[_.keys(user.texts)[1]].words.length,[
            "Consistently sized run"].length);
    });
    it("should be draggable",function(){
        var active = user.textStanzas[_.keys(user.texts)[1]];
        assert.equal(active.x,600);
        assert.equal(active.y,500);
        assert.equal(user.interactables.manualMove.length,1);
        var handle = user.interactables.manualMove[0];
        user.drag(handle,{x:-500,y:-250});
        active = user.textStanzas[_.keys(user.texts)[1]];
        assert.equal(active.x,100);
        assert.equal(active.y,250);
    });
    it("should scale aspect locked",function(){
        var active = user.textStanzas[_.keys(user.texts)[1]];
        assert.equal(active.width,240);
        assert.equal(active.x,100);

        var handle = user.interactables.resizeAspectLocked[0];
        user.drag(handle,{x:200,y:0});

        active = user.textStanzas[_.keys(user.texts)[1]];
        assert.equal(active.x,100);
        assert.equal(active.width,437);
        assert.equal(active.words[0].size, 55);
    });
    it("should scroll up on swipe out",function(){
        user.swipeUp();
    });
    if("should reselect box",function(){
        teacher.moveToObject("#board",200,300);
        teacher.leftClick();
        assert.equal(user.interactables.resizeFree.length,1);
    });
    it("should rewrap text retaining font size",function(){
        var handle = user.interactables.resizeFree[0];
        teacher.pause(ANIMATION_DELAY);
        user.drag(handle,{x:200,y:0});
        var active = user.textStanzas[_.keys(user.texts)[1]];
        assert.equal(active.x,100);
        assert.equal(active.width,638);
        assert.equal(active.words[0].size, 55);
    });
    it("should draw ink", function(){
        user.inkMode.click();
        var len = 100;
	var root = 200;
        user.handwrite(_.map(_.range(40), function(i){
            return {
                x: root++ + Math.cos(i) * len--,
                y: root + Math.sin(i) * len--
            };
        }));
        assert.equal(_.keys(user.inkStanzas).length,1);
    });
    it("should add an image",function(){
	user.imageMode.click();
    });
});
