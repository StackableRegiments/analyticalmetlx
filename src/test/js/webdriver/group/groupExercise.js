var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var LoginPage = require("../page/login.page");
var ConversationsPage = require("../page/conversations.page");
var ConversationPage = require("../page/conversation.page");

describe('When the class breaks into groups,', function() {
    var teacherName;
    var tT = board(teacher);
    var sA = board(studentA);
    var sB = board(studentB);

    var join = function(user,label){
        var login = LoginPage(user);
        var search = ConversationsPage(user);
        var username = sprintf("%s.user.%s",label,Math.floor(Math.random() * 10000));
        login.username.setValue(username);
        login.submit();
        assert(search.waitForSearchBox());
        switch(label){
        case 'teacher':
            teacherName = username;
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
        assert.equal(groupSet.groups.length,2);
        var groupsByUser = _.reduce(groupSet.groups,function(acc,item){
            _.each(item.members,function(member){
                if(!(member in acc)){
                    acc[member] = 0;
                }
                acc[member] += 1;
            })
            return acc;
        },{});
        assert(_.every(groupsByUser,function(memberships){
            return memberships == 1;
        }));
    });
    it("the teacher does not join any group",function(){
        assert(_.every(tT.currentSlide.groupSet.groups,function(group){
            return !(_.some(group.members,teacherName));
        }));
    });
    it("students see the teacher work but not other groups",function(){
        _.each([tT,sA,sB],function(client,i){
            client.textMode.click();
            client.keyboard(50,100 + i * 100,"Phrase "+(i+1));
        });
        browser.pause(1500);//Let everything synchronize

        assert(_.includes(sA.plainTexts,"Phrase 1"));
        assert(_.includes(sB.plainTexts,"Phrase 1"));

        assert(_.includes(sA.plainTexts,"Phrase 2"));
        assert(!(_.includes(sB.plainTexts,"Phrase 2")));

        assert(! (_.includes(sA.plainTexts,"Phrase 3")));
        assert(_.includes(sB.plainTexts,"Phrase 3"));
    });
    it("the teacher can see all groups",function(){
        assert(_.includes(tT.plainTexts,"Phrase 1"));
        assert(_.includes(tT.plainTexts,"Phrase 2"));
        assert(_.includes(tT.plainTexts,"Phrase 3"));
    });
    it("the teacher can filter out groups but the students cannot",function(){
        tT.menuButton.click();
        sA.menuButton.click();
        sB.menuButton.click();
        browser.waitUntil(function(){return browser.isVisible("#roomToolbar");});

        tT.learning.click();
        sA.learning.click();
        sB.learning.click();
        browser.waitUntil(function(){return browser.isVisible("#menuContentFilter");});

        tT.contentFilter.click();
        sA.contentFilter.click();
        sB.contentFilter.click();

        var groups = tT.currentSlide.groupSet.groups;
        assert(teacher.isExisting("#contentFilter_"+groups[0].id));
        assert(teacher.isExisting("#contentFilter_"+groups[1].id));

        assert(! studentA.isExisting("#contentFilter_"+groups[0].id));
        assert(! studentA.isExisting("#contentFilter_"+groups[1].id));

        assert(! studentB.isExisting("#contentFilter_"+groups[0].id));
        assert(! studentB.isExisting("#contentFilter_"+groups[1].id));
    });
    it("connection health should be a visible metric",function(){
        assert(browser.isExisting("#healthStatus"));
        assert(browser.isExisting("#participationStatus"));
        assert(browser.isExisting("#complexityStatus"));
    });
});
