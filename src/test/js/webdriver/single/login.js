var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var LoginPage = require("../page/login.page");
var ConversationsPage = require("../page/conversations.page");

describe('When the application starts,', function() {
    var userT = board(user);

    it('the user should find the application', function () {
        browser.url('/board');
    });

    var userLoginPage = LoginPage(user);
    var userName = 'test.user.' + Math.floor(Math.random() * 10000);
    var userConversationsPage = ConversationsPage(user);

    var delay = 2000;
    it('the user should successfully login', function () {
        userLoginPage.username.setValue(userName);
        userLoginPage.submit();
        assert(userConversationsPage.waitForSearchBox());
        user.click("#createConversationButton");
        userConversationsPage.waitForNewConversation();
        user.click(".newConversationTag");
        user.waitForExist("#board");
        user.pause(delay);
        userT.textMode.click();
        userT.keyboard(50,50,"This is a");
        user.pause(delay);
        var handle = userT.interactables.manualMove[0];
        userT.drag(handle,{x:20,y:0});
        user.pause(delay);
        user.keys(" paragraph of text");
        user.pause(delay);
        user.keys(" typed with pauses in between");
        user.pause(delay);
        userT.newSlide.click();
        user.pause(delay);
        userT.nextSlide.click();
        user.pause(delay);
        userT.prevSlide.click();
        user.pause(delay);
        console.log(userT.plainTexts);
    });
});
