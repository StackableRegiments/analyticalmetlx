var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var LoginPage = require("../page/login.page");
var ConversationsPage = require("../page/conversations.page");
var ConversationPage = require("../page/conversation.page");

var ANIMATION_DELAY = 1000;

describe('When the MeTLing pot processes content,', function() {
    var teacherT = board(teacher);
    var teacherLoginPage = LoginPage(teacher);
    var teacherName = 'test.teacher.' + Math.floor(Math.random() * 10000);
    var teacherConversationsPage = ConversationsPage(teacher);

    it('given that the author is in the application', function () {browser.url('/board');});
    it('the author should successfully login', function () {
        teacherLoginPage.username.setValue(teacherName);
        teacherLoginPage.submit();
        assert(teacherConversationsPage.waitForSearchBox());
    });
    it("given that the author is in a conversation", function() {
        assert(teacherConversationsPage.waitForCreateButton());
        var previousConversations = teacherConversationsPage.getConversations();
        teacher.click("#createConversationButton");
        var newConversations = teacherConversationsPage.getNewConversations(previousConversations);
        assert.ok(newConversations.length > 0,"expected there to be at least 1 new conversation");
        teacher.click(".newConversationTag");
        teacher.waitForExist("#board");
        teacher.pause(ANIMATION_DELAY);
    });
    it("given that handwriting has been submitted",function(){
        teacherT.inkMode.click();
        teacherT.letters(['c','a','t']);
        teacher.waitUntil(function(){
            return _.keys(teacherT.inkStanzas).length == 5;
        });
    });
    it("given that text has been submitted",function(){
        var corpus = "Interesting content which is largely but not completely devoid of stopwords and has repeating content to show that repeating content repeated.  Frequently contented frequently.";
        teacherT.textMode.click();
        teacherT.keyboard(100,100,corpus);
    });
    it("given that an image has been submitted",function(){
        teacherT.imageMode.click();
        teacher.click("#board");
        teacher.chooseFile("#imageFileChoice","testMaterials/mapleLeaf.jpg");
        teacher.waitUntil(function(){
            return _.keys(teacherT.imageStanzas).length == 1;
        });
    });
    it("it should emit text fragments",function(){
        teacherT.openParticipants();
        teacher.waitUntil(function(){return teacherT.cloudData.length > 1;});
        assert(_.includes(_.map(teacherT.cloudData,"key"),"interesting"));
    });
    it("it should trim punctuation",function(){
	console.log(teacherT.cloudData);
        assert(_.includes(_.map(teacherT.cloudData,"key"),"frequently"));
    });
    it("it should emit image classifications",function(){
        assert(_.includes(_.map(teacherT.cloudData,"key"),"maple"));
    });
    it("it should emit recognized fragments",function(){
        teacher.waitUntil(function(){
            return _.includes(_.map(teacherT.themes,"text"),"CAT");
        });
    });
    it("it should be able to filter displayed themes by origin",function(){
        assert(_.some(teacherT.visibleThemes(),function(t){return t.text == "cat";}));
        teacherT.toggleFilter("handwriting");
        assert(! _.some(teacherT.visibleThemes(),function(t){return t.text == "cat";}));
        teacherT.toggleFilter("handwriting");
        assert(_.some(teacherT.visibleThemes(),function(t){return t.text == "cat";}));

        assert(_.some(teacherT.visibleThemes(),function(t){return t.text == "plant";}));
        teacherT.toggleFilter("imageRecognition");
        assert(! _.some(teacherT.visibleThemes(),function(t){return t.text == "plant";}));
        teacherT.toggleFilter("imageRecognition");
        assert(_.some(teacherT.visibleThemes(),function(t){return t.text == "plant";}));
    });
    it("it should scale words according to frequency",function(){
        var cd = teacherT.cloudData;
        assert.equal(1,_.filter(cd,function(c){return c.key == "plant" && c.value == 4;}).length);
        assert.equal(1,_.filter(cd,function(c){return c.key == "content" && c.value == 3;}).length);
        assert.equal(1,_.filter(cd,function(c){return c.key == "maple" && c.value == 2}).length);
        assert.equal(1,_.filter(cd,function(c){return c.key == "cat" && c.value == 1;}).length);
        var display = teacherT.visibleThemes();
        var size = function(key){
            return parseInt(_.find(display,function(t){return t.text == key}).size);
        };
        var gt = function(bigger,smaller){
            return  size(bigger)> size(smaller);
        };
        assert(gt("plant","content"));
        assert(gt("content","maple"));
        assert(gt("maple","cat"));
    });
    it("it should permit the user to toggle standard conjugation",function(){
        assert.equal(1,_.filter(teacherT.cloudData,function(c){return c.key == "repeat" && c.value == 3}).length);
	teacherT.toggleFilter("conjugate");
        assert.equal(1,_.filter(teacherT.cloudData,function(c){return c.key == "repeating" && c.value == 2;}).length);
    });
});
