var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var LoginPage = require("../page/login.page");
var ConversationsPage = require("../page/conversations.page");
var ConversationPage = require("../page/conversation.page");

var ANIMATION_DELAY = 1000;
var debugUnless = function(condF,fail){
    if(!(condF())){
        browser.debug();
    }
    else{
        console.log(fail);
    }
};
describe('When a teacher presents,', function() {
    var teacherT = board(teacher);
    var studentT = board(student);
    it('the teacher and student should find the application', function () {
        browser.url('/board');
    });

    var teacherLoginPage = LoginPage(teacher);
    var studentLoginPage = LoginPage(student);
    var teacherName = 'test.teacher.' + Math.floor(Math.random() * 10000);
    var studentName = 'test.student.' + Math.floor(Math.random() * 10000);

    var teacherConversationsPage = ConversationsPage(teacher);
    var studentConversationsPage = ConversationsPage(student);

    it('the teacher should successfully login', function () {
        teacherLoginPage.username.setValue(teacherName);
        teacherLoginPage.submit();
        assert(teacherConversationsPage.waitForSearchBox());
    });
    it('the student should successfully login', function () {
        studentLoginPage.username.setValue(studentName);
        studentLoginPage.submit();
        assert(studentConversationsPage.waitForSearchBox());
    });

    var importFilename = 'Test.ppt';

    it("should be able to import and join a conversation", function() {
        assert(teacherConversationsPage.waitForImportButton());
        var previousConversations = teacherConversationsPage.getConversations();
        teacherConversationsPage.importConversation(importFilename);
        teacherConversationsPage.waitForImportedConversation(teacherName,importFilename);
        var newConversations = teacherConversationsPage.getNewConversations(previousConversations);
        assert.ok(newConversations.length > 0,"expected there to be at least 1 new conversation");
    });
    it("the teacher should join the conversation",function(){
        teacher.click(".newConversationTag");
        teacher.waitForExist("#board");
    });
    it("the student should find and join the conversation",function(){
        student.setValue("#conversationSearchBox > input",teacherName);
        student.click("#searchButton");
        student.pause(1000);
        student.click(".newConversationTag");
        student.waitForExist("#board");
    });
});
