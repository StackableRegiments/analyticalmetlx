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
describe('When a teacher presents,', function() {
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
    it("both should see a blank board", function(){
        assert.equal(_.keys(teacherT.texts).length,0);
        assert.equal(_.keys(studentT.texts).length,0);
    });
    it("the teacher should insert a paragraph",function(){
        teacherT.textMode.click();
        teacherT.keyboard(50,50,"This is a paragraph of text which is being typed programatically.  It runs over multiple lines.");
        assert.equal(_.keys(teacherT.texts).length,1);
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length,[
            "Consistently sized run"].length);
    });
    it("the teacher should highlight a word and enlarge it",function(){
        teacherT.doubleClickWorld(100,100);
        teacher.waitUntil(function(){
            var r = teacherT.selectedRanges[0];
            return r.start == 10 && r.end == 19;
        });

        teacher.click("#fontLarger");
        teacher.waitUntil(function(){
            return teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length ==
                ["Before","Enlarged","After"].length;
        });

        teacherT.doubleClickWorld(100,300);
        teacher.waitUntil(function(){
            var r = teacherT.selectedRanges[0];
            return r.start == 49 && r.end == 65;
        });

        teacher.click("#redText");
        teacher.waitUntil(function(){
            return teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length ==
                ["Before","Enlarged","After","Red","After"].length;
        });

        teacherT.doubleClickWorld(100,400);
        teacher.waitUntil(function(){
            var r = teacherT.selectedRanges[0];
            return r.start == 80 && r.end == 88;
        });

        teacher.click("#fontLarger");
        teacher.waitUntil(function(){
            return teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length ==
                ["Before","Enlarged","After","Red","After","Enlarged","After"].length;
        });
    });
    it("the teacher should create another textbox",function(){
        teacherT.keyboard(400,400,"This is a second paragraph.  It exists to be differentiated from the first paragraph.");
        assert.equal(_.keys(teacherT.texts).length,2);
        assert.equal(_.keys(teacherT.textStanzas).length,2);
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[1]].words.length,[
            "Consistently sized run"].length);
    });
    it("the teacher should drag their new textbox",function(){
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert(within(active.x,505,1));
        assert(within(active.y,505,1));
        assert.equal(teacherT.interactables.manualMove.length,1);
        var handle = teacherT.interactables.manualMove[0];
        teacherT.drag(handle,{x:-400,y:-150});
        active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert(within(active.x,106,1));
        assert(within(active.y,355,1));
    });
    it("the teacher should rescale all the font in their new textbox",function(){
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert(within(active.x,106,1));
        assert(within(active.width,303,1));

        var handle = teacherT.interactables.resizeAspectLocked[0];
        teacherT.drag(handle,{x:200,y:0});

        active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        console.log(active);
        assert(within(active.x,106,1));
        assert(within(active.width,520,1));
        assert(within(active.words[0].size,65,1));
    });
    it("the teacher should be able to reselect their box",function(){
        teacherT.clickWorld(50,300);
        assert.equal(_.keys(teacherT.selection.multiWordTexts).length,1);
        assert.equal(teacherT.interactables.resizeFree.length,1);
    });
    it("the teacher should resize their box rewrapping instead of rescaling the text",function(){
        var handle = teacherT.interactables.resizeFree[0];
        teacher.pause(ANIMATION_DELAY);
        teacherT.drag(handle,{x:200,y:0});
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        console.log(active);
        assert(within(active.x,106,1));
        assert(active.words.length > 0);
        assert(within(active.width,736,1));
        assert(within(active.words[0].size, 65,1));
    });
    it("the teacher should be able to pan their view by swiping up",function(){
        teacherT.swipeUp();
    });
    it("the teacher should be able to draw ink", function(){
        teacherT.inkMode.click();

        var inkStanzasBefore = _.filter(teacherT.inkStanzas,function(inkStanza){return inkStanza.author == teacherT.username;}).length;
        teacherT.handwrite(_.map(_.range(280,630,5), function(i){
            return {x:i,y:i};
        }));
        teacher.waitUntil(function(){
            return _.filter(teacherT.inkStanzas,function(inkStanza){return inkStanza.author == teacherT.username;}).length >= (inkStanzasBefore + 1);
        },5000,"expected new ink to appear in inkStanzas after looping through server");
        for(var i = 2; i < 5; i++){
            var len = 35;
            var root = (len + 20) * i;
            teacher.click(sprintf("#pen%sButton",i));
            var pts = _.map(_.range(0,5), function(j){
                return {
                    x: root + Math.cos(j) * len--,
                    y: root + Math.sin(j) * len--
                };
            });
            teacherT.handwrite(pts);
        }
        var v = teacherT.viewport;
        teacher.waitUntil(function(){
            return _.keys(teacherT.inkStanzas).length == 4;
        });
    });
    it("the teacher should add an image",function(){
        assert.equal(_.keys(teacherT.imageStanzas).length,0);
        teacherT.imageMode.click();
        teacher.click("#board");
        teacher.chooseFile("#imageFileChoice","testMaterials/mapleLeaf.jpg");
        teacher.waitUntil(function(){
            return _.keys(teacherT.imageStanzas).length == 1;
        },5000);
        teacher.pause(ANIMATION_DELAY);
    });
    it("the teacher should have their new image selected when it appears",function(){
        assert.equal(_.keys(teacherT.selection.images).length,1);
    });
    it("the teacher should clear their selection by clicking on an empty spot on the canvas",function(){
        teacherT.selectMode.click();
        teacherT.clickScreen(20,670);
        var sel = teacherT.selection;
        assert.equal(_.keys(sel.inks).length,0);
        assert.equal(_.keys(sel.texts).length,0);
        assert.equal(_.keys(sel.multiWordTexts).length,0);
        assert.equal(_.keys(sel.videos).length,0);
        assert.equal(_.keys(sel.images).length,0);
    });
    it("the teacher should select all the items that are under their mouse when they click the board",function(){
        teacherT.clickScreen(140,117);
        var sel = teacherT.selection;
        assert.equal(_.keys(sel.inks).length, 1);
        assert.equal(_.keys(sel.texts).length, 0);
        assert.equal(_.keys(sel.multiWordTexts).length, 1);
        assert.equal(_.keys(sel.videos).length, 0);
        assert.equal(_.keys(sel.images).length, 1);
    });
    it("the student should see all public teacher-created elements",function(){
        student.waitUntil(function(){
            return _.keys(studentT.textStanzas).length == 2 &&
                _.keys(studentT.inkStanzas).length == 4 &&
                _.keys(studentT.imageStanzas).length == 1;
        });
    });
    it("the teacher should delete their selected elements",function(){
        teacherT.deleteSelection.click();
    });
    it("the student should see all remaining public teacher-created elements",function(){
        student.waitUntil(function(){
            return _.keys(studentT.imageStanzas).length == 0;
        });
        assert.equal(_.keys(studentT.textStanzas).length,1);
        assert.equal(_.keys(studentT.inkStanzas).length,3);
    });
    it("the teacher should create a private image",function(){
        teacherT.privateMode.click();
        teacher.waitUntil(function(){
            return teacherT.privacy == "PRIVATE";
        });
        teacherT.imageMode.click();
        teacher.click("#board");
        teacher.chooseFile("#imageFileChoice","testMaterials/mapleLeaf.jpg");
        teacher.waitUntil(function(){
            var keys = _.keys(teacherT.imageStanzas).length;
            return keys == 1;
        },5000);
        assert.equal(_.keys(studentT.imageStanzas).length,0);
    });
    it("the student should not see private teacher-created content",function(){
        assert.equal(_.keys(studentT.imageStanzas).length,0);
    });
    it("the teacher should be able to open the application menu",function(){
        assert.ok(!teacherT.applicationMenu.value);
        teacherT.menuButton.click();
        assert.ok(teacherT.applicationMenu.value);
    });
    it("the teacher should see deleted content in the recycle bin",function(){
        teacherT.recycleBinMenu.click();
        teacher.waitUntil(function(){
            return teacherT.recycleables.length == 3;
        });
    });
    it("the teacher should be able to close the application menu",function(){
        teacherT.menuButton.click();
        assert.ok(!teacherT.applicationMenu.value);
    });
    it("the teacher should be able to add a new page",function(){
        assert.equal(teacherT.currentSlide.index,0);
        teacherT.newSlide.click();
        browser.waitUntil(function(){
            return teacherT.currentSlide.index == 1;
        });
    });
    it("the teacher should be able to go back to the first page",function(){
        teacherT.prevSlide.click();
        browser.waitUntil(function(){
            return teacherT.currentSlide.index == 0;
        });
    });
    it("the teacher should be able to go forward again",function(){
        teacherT.nextSlide.click();
        browser.waitUntil(function(){
            return teacherT.currentSlide.index == 1;
        });
    });
    it("handwriting should be recognised",function(){
        teacherT.publicMode.click();
        teacherT.inkMode.click();
        teacherT.letters(['c','a','t']);
        teacher.waitUntil(function(){
            return _.keys(teacherT.inkStanzas).length > 0;
        });
    });
    it("textboxes should have the same wrap when they first appear as when they come back out of history",function(){
        teacherT.textMode.click();
        teacherT.keyboard(50,50,"This is a paragraph of text which is being typed programatically.  It runs over multiple lines.");
        teacher.waitUntil(function(){
            return _.keys(teacherT.texts).length == 1;
        });
        var text = _.values(teacherT.textStanzas)[0];
        assert.equal(text.width,240);
        var liveLines = teacherT.selectedLines;
        teacherT.prevSlide.click();
        browser.waitUntil(function(){return teacherT.currentSlide.index == 0;});
        teacherT.nextSlide.click();
        browser.waitUntil(function(){
            return teacherT.currentSlide.index == 1 && (_.keys(teacherT.texts).length == 1);
        });
        text = _.values(teacherT.textStanzas)[0];
        assert.equal(text.width,240);
        var wireLines = teacherT.selectedLines;
        assert.deepEqual(liveLines,wireLines);
    });
});
