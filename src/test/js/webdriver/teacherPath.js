var _ = require('lodash');
var assert = require('assert');
var board = require('./page/board.page');

describe('Single teacher running', function() {
    var user = board(teacher);
    it('should go to application', function () {
        browser.url('/board');
    });
    it('should login', function () {
        browser.setValue('input[type=text]', 'teacher');
    });
    it('should submit the login form', function () {
        browser.click('input[type=submit]');
        browser.waitForExist("#conversationSearchBox");
    });
    it("should join a new conversation", function() {
        browser.click("#createConversationButton");
        browser.waitForExist(".newConversationTag",5000);
        browser.click(".newConversationTag");
        browser.waitForExist("#board");
        assert.equal(teacher.execute("return document.readyState").value,"complete");
    });
    it("should be able to check content and it should be empty", function(){
        assert.equal(_.keys(user.texts).length,0);
    });
    it("should insert a paragraph",function(){
        user.textMode.click();
        user.keyboard(50,50,"This is a paragraph of text which is being typed programatically.  It runs over multiple lines.");
        console.log(user.textStanzas[_.keys(user.texts)[0]]);
        assert.equal(_.keys(user.texts).length,1);
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Consistently sized run"].length);
    });
    it("should highlight a word and enlarge it",function(){
        browser.moveToObject("#board",100,100);
        browser.leftClick();
        browser.doDoubleClick();
        browser.click("#fontLarger");
        assert.equal(_.keys(user.texts).length,1);
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After"].length);
        browser.moveToObject("#board",100,300);
        browser.doDoubleClick();
        browser.click("#redText");
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After","Red","After"].length);
        browser.moveToObject("#board",100,400);
        browser.doDoubleClick();
        browser.click("#fontLarger");
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After","Red","After","Enlarged","After"].length);
    });
    it("should create another textbox",function(){
        user.keyboard(600,500,"This is a second paragraph.  It exists to be differentiated from the first paragraph.");
        assert.equal(_.keys(user.texts).length,2);
        assert.equal(_.keys(user.textStanzas).length,2);
        console.log(user.textStanzas[_.keys(user.texts)[1]]);
        assert.equal(user.textStanzas[_.keys(user.texts)[1]].words.length,[
            "Consistently sized run"].length);
    });
    it("should have selection handles",function(){
        console.log(user.interactables);
        assert.equal(user.interactables.manualMove.length,1);
        var handle = user.interactables.manualMove[0];
        var dragPos = user.worldToScreen(handle.bounds[0],handle.bounds[1]);
        console.log(user.interactables);
        browser.moveToObject("#board",dragPos.x,dragPos.y);
        browser.buttonDown();
        browser.moveToObject("#board",dragPos.x - 200,dragPos.y);
        browser.buttonUp();
        console.log(user.interactables);
        browser.debug();
        assert.equal(dragPos,{x:600,y:300});
    });
});
