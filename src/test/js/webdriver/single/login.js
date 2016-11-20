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
        user.click("#createConversationButton");
        userConversationsPage.waitForNewConversation();
        user.click(".newConversationTag");
        user.waitForExist("#board");
        user.pause(1000);
        userT.inkMode.click();
        browser.debug();
        var count = 20;
        for(var i = 1; i < count; i++){
            user.execute("$('#addSlideButton').click()");
        }
        for(var j = count; j > 20; j--){
            userT.handwrite(_.map(_.range(0,60,5),function(k){
                var x = 50 + 5 * k + j * 20;
                var y = 50 + 5 * k;
                return {
                    x:x,
                    y:y
                }
            }));
	    user.pause(1000);
            userT.prevSlide.click();
	    user.pause(1000);
        }
        browser.inkMode.click();
    });
});
