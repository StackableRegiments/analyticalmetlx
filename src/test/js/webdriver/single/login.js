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

    it('the user should successfully login', function () {
        userLoginPage.username.setValue(userName);
        userLoginPage.submit();
        assert(userConversationsPage.waitForSearchBox());
    });
});
