var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var LoginPage = require("../page/login.page");
var ConversationsPage = require("../page/conversations.page");
var ConversationPage = require("../page/conversation.page");

describe('When the class breaks into groups,', function() {
    var users = {};
    var tT = board(teacher);
    for(var i = 0; i < 3;i++){
        var name = sprintf("student%s",String.fromCharCode(i+97).toUpperCase());
        users[name] = board(global[name]);
    }
    var join = function(user,label){
        console.log(label);
        var login = LoginPage(user);
        var search = ConversationsPage(user);
        var username = sprintf(label);
        login.username.setValue(username);
        login.submit();
        assert(search.waitForSearchBox());
        switch(label){
        case 'teacher':
            assert(search.waitForCreateButton());
            var previousConversations = search.getConversations();
            user.click("#createConversationButton");
            var newConversations = search.getNewConversations(previousConversations);
            assert.ok(newConversations.length > 0,"expected there to be at least 1 new conversation");
            user.click(".newConversationTag");
            break;
        default:
            user.setValue("#conversationSearchBox > input",'teacher');
            user.click("#searchButton");
            user.pause(100);
            user.click(".newConversationTag");
            break;
        }
        user.waitForExist("#board");
    };
    it('given that everybody is in the application', function () {
        browser.url('/board');
        join(teacher,"teacher");
        _.each(users,function(user,name){
            join(user.driver,name);
        });
        _.each(users,function(user){
            user.inkMode.click();
        })
        for(var i = 0; i < 3; i++){
            var j = 0;
            _.each(users,function(user){
                j++;
                user.handwrite(_.map(_.range(0,20,5),function(k){
                    var x = 20 * i + k;
                    var y = 25 * j + k;
                    return {
                        x:x,
                        y:y
                    }
                }));
            });
        }
    });
    it('given that there is a group slide', function () {
        tT.addGroupSlide.click();
        teacher.waitUntil(function(){
            return tT.currentSlide.index == 1;
        });
    });
    it("generate content",function(){
        _.each(users,function(user){
            user.driver.waitUntil(function(){
                return user.currentSlide.index == 1;
            });
        });
        for(var i = 0; i < 50; i++){
            var j = 0;
            if(i == 5){
                browser.debug();
            }
            _.each(users,function(user){
                j++;
                user.handwrite(_.map(_.range(0,20,5),function(k){
                    var x = 20 * i + k;
                    var y = 25 * j + k;
                    return {
                        x:x,
                        y:y
                    }
                }));
            });
        }
    });
});
