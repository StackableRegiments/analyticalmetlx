var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var LoginPage = require("../page/login.page");
var ConversationsPage = require("../page/conversations.page");
var ConversationPage = require("../page/conversation.page");

var ANIMATION_DELAY = 1000;
var within = function(a,b,tolerance){
    return Math.abs(a - b) <= tolerance;
};
describe('A plugins section is available so,', function() {
    var teacherT = board(teacher);
    var studentT = board(student);

    var w = 1024;
    var h = 850;
    teacher.setViewportSize({width:w,height:h});
    teacher.waitUntil(function(){
        var s = teacher.getViewportSize();
        return s.state != "pending";
    });

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
    it("the teacher should be able to create and join a conversation", function() {
        assert(teacherConversationsPage.waitForCreateButton());
        var previousConversations = teacherConversationsPage.getConversations();
        teacher.click("#createConversationButton");
        var newConversations = teacherConversationsPage.getNewConversations(previousConversations);
        assert.ok(newConversations.length > 0,"expected there to be at least 1 new conversation");
        teacher.click(".newConversationTag");
        teacher.waitForExist("#board");
        teacher.pause(ANIMATION_DELAY);
    });
    it("the student should find and join the conversation",function(){
        student.setValue("#conversationSearchBox > input",teacherName);
        student.click("#searchButton");
        student.pause(1000);
        student.click(".newConversationTag");
        student.waitForExist("#board");
    });
    it("both should see a plugins bar", function(){
	assert(teacherT.pluginBar != null);
	assert(studentT.pluginBar != null);
    });
});
