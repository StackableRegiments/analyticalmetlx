var _ = require('lodash');
var assert = require('assert');
var board = require('./page/board.page');

describe('Single teacher running', function() {
    browser.windowHandleSize({width: 750, height: 800});
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
        assert.equal(_.keys(user.texts).length,1);
    });
    it("should highlight a word and enlarge it",function(){
        browser.moveToObject("#board",100,100);
        browser.leftClick();
        browser.doDoubleClick();
        browser.click("#fontLarger");
        assert.equal(_.keys(user.texts).length,1);
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After","Newline"].length);
        browser.moveToObject("#board",100,300);
        browser.doDoubleClick();
        browser.click("#redText");
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After","Red","After","Newline"].length);
        browser.moveToObject("#board",100,400);
        browser.doDoubleClick();
        browser.click("#fontLarger");
        assert.equal(user.textStanzas[_.keys(user.texts)[0]].words.length,[
            "Before","Enlarged","After","Red","After","Enlarged","After","Newline"].length);
    });
});
