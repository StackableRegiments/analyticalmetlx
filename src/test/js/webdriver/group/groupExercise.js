var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var LoginPage = require("../page/login.page");
var ConversationsPage = require("../page/conversations.page");
var ConversationPage = require("../page/conversation.page");

describe('When the class breaks into groups,', function() {
    var tT = board(teacher);
    var sA = board(studentA);
    var sB = board(studentB);

    var join = function(user,label){
        var login = LoginPage(user);
        var search = ConversationsPage(user);
        login.username.setValue(sprintf("%s.user.%s",label,Math.floor(Math.random() * 10000)));
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
            user.pause(1000);
            user.click(".newConversationTag");
            break;
        }
        user.waitForExist("#board");
    };
    it('given that everybody is in the application', function () {
        browser.url('/board');
        join(teacher,'teacher');
        join(studentA,'studentA');
        join(studentB,'studentB');
    });
    it("given that the teacher gives public instructions on the first slide",function(){
        tT.textMode.click();
        tT.keyboard(50,50,"Break into groups and discuss without sharing with other groups.  What is the best thing in life?");
    });
    it("the students should all see the instructions",function(){
        studentA.waitUntil(function(){
            return _.keys(sA.textStanzas).length == 1;
        });
        studentB.waitUntil(function(){
            return _.keys(sB.textStanzas).length == 1;
        });
    });
    it("given that the teacher adds a group slide",function(){
        assert.equal(tT.currentSlide.index,0);
        tT.addGroupSlide.click();
        teacher.waitUntil(function(){
            return tT.currentSlide.index == 1;
        });
    });
    it("the teacher should be taken to it",function(){
        assert.equal(tT.currentSlide.index,1);
    });
    it("the students should all follow to it",function(){
        assert.equal(sA.currentSlide.index,1);
        assert.equal(sB.currentSlide.index,1);
    });
    it("the students should all be split into groups",function(){
        var groupSet = tT.currentSlide.groupSet;
        console.log(_.map(groupSet.groups,"members"));
        assert.equal(groupSet.groups.length,2);
        var userGroups = _.reduce(groupSet.groups,function(acc,item){
            _.each(item.members,function(member){
                if(!(member in acc)){
                    acc[member] = 0;
                }
                acc[member] += 1;
            })
	    return acc;
        },{});
	console.log("Usergroups",userGroups);
	assert(_.every(userGroups,function(memberships){
	    return memberships == 1;
	}));
    });
});
