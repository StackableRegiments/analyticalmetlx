var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var ANIMATION_DELAY = 1000;

var debugUnless = function(condF,fail){
    if(!(condF())){
        browser.debug();
    }
    else{
        console.log(fail);
    }
};
var within = function(a,b,tolerance){
    return Math.abs(a - b) <= tolerance;
}
var doubleClick = function(user,x,y){
    user.moveToObject("#board",x,y);
    /*On the theory that the wire protocol double click is problematic*/
    user.leftClick();
    user.leftClick();
}
describe('When a teacher presents, they', function() {
    var teacherT = board(teacher);
    var studentT = board(student);
    it('should find the application', function () {
        browser.url('/board');
    });
    it('should successfully login', function () {
        teacher.setValue('input[type=text]', 'teacher');
        student.setValue('input[type=text]', 'student');
    });
    it('should be able to submit the login form', function () {
        browser.click('input[type=submit]');
        browser.waitForExist("#conversationSearchBox");
    });
    it("should be able to create and join a conversation", function() {
        teacher.waitForExist("#createConversationButton");
        var previousConversations = teacher.execute("return Conversations.getConversationListing()").value;
        teacher.click("#createConversationButton");
        teacher.waitUntil(function(){
            return teacher.execute("return Conversations.getConversationListing()").value.length == (previousConversations.length + 1);
        },5000,"expected a new conversation to appear");
        var newConversations = _.filter(teacher.execute("return Conversations.getConversationListing()").value,function(nc){
            return !_.some(previousConversations,function(pc){
                return pc == nc;
            });
        });
        assert.ok(newConversations.length > 0,"expected there to be at least 1 new conversation");
        teacher.click(".newConversationTag");
        teacher.waitForExist("#board");
    });
    it("should have their students able to find the conversation",function(){
        student.setValue("#conversationSearchBox > input","teacher");
        student.click("#searchButton");
        student.pause(1000);
        student.click(".newConversationTag");
        student.waitForExist("#board");
    });
    it("should all be able to verify that the board is blank", function(){
        assert.equal(_.keys(teacherT.texts).length,0);
        assert.equal(_.keys(studentT.texts).length,0);
    });
    it("should insert a paragraph",function(){
        teacherT.textMode.click();
        teacherT.keyboard(50,50,"This is a paragraph of text which is being typed programatically.  It runs over multiple lines.");
        assert.equal(_.keys(teacherT.texts).length,1);
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length,[
            "Consistently sized run"].length);
    });
    it("should highlight a word and enlarge it",function(){
        doubleClick(teacher,100,100);
        teacher.waitUntil(function(){/*Paragraph*/
            var r = teacherT.selectedRanges[0];
            return r.start == 10 && r.end == 19;
        });

        teacher.click("#fontLarger");
        teacher.waitUntil(function(){
            return teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length ==
                ["Before","Enlarged","After"].length;
        });

	doubleClick(teacher,100,300);
        teacher.waitUntil(function(){/*Programatically*/
            var r = teacherT.selectedRanges[0];
            console.log(r);
            return r.start == 49 && r.end == 65;
        });

        teacher.click("#redText");
        teacher.waitUntil(function(){
            return teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length ==
                ["Before","Enlarged","After","Red","After"].length;
        });

	doubleClick(teacher,100,400);
        teacher.waitUntil(function(){/*multiple*/
            var r = teacherT.selectedRanges[0];
            console.log(r);
            return r.start == 80 && r.end == 88;
        });

        teacher.click("#fontLarger");
        teacher.waitUntil(function(){
            return teacherT.textStanzas[_.keys(teacherT.texts)[0]].words.length ==
                ["Before","Enlarged","After","Red","After","Enlarged","After"].length;
        });
    });
    it("should create another textbox",function(){
        teacherT.keyboard(600,500,"This is a second paragraph.  It exists to be differentiated from the first paragraph.");
        assert.equal(_.keys(teacherT.texts).length,2);
        assert.equal(_.keys(teacherT.textStanzas).length,2);
        assert.equal(teacherT.textStanzas[_.keys(teacherT.texts)[1]].words.length,[
            "Consistently sized run"].length);
    });
    it("should be able to drag their new textbox",function(){
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.x,600);
        assert.equal(active.y,500);
        assert.equal(teacherT.interactables.manualMove.length,1);
        var handle = teacherT.interactables.manualMove[0];
        teacherT.drag(handle,{x:-500,y:-250});
        active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.x,100);
        assert.equal(active.y,250);
    });
    it("be able to rescale all the font in their new textbox",function(){
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.width,240);
        assert.equal(active.x,100);

        var handle = teacherT.interactables.resizeAspectLocked[0];
        teacherT.drag(handle,{x:200,y:0});

        active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.x,100);
        assert(within(active.width,437,2));
        assert.equal(active.words[0].size, 55);
    });
    it("should scroll up on swipe out",function(){
        teacherT.swipeUp();
    });
    if("should be able to reselect their box",function(){
        teacher.moveToObject("#board",200,300);
        teacher.leftClick();
        assert.equal(teacherT.interactables.resizeFree.length,1);
    });
    it("should be able to resize their box rewrapping instead of rescaling the text",function(){
        var handle = teacherT.interactables.resizeFree[0];
        teacher.pause(ANIMATION_DELAY);
        teacherT.drag(handle,{x:200,y:0});
        var active = teacherT.textStanzas[_.keys(teacherT.texts)[1]];
        assert.equal(active.x,100);
        assert.equal(active.words[0].size, 55);
        teacher.waitUntil(function(){
            console.log(active.width);
            return within(active.width,638,2);
        });
    });
    it("should be able to draw ink", function(){
        teacherT.inkMode.click();

        var inkStanzasBefore = _.filter(teacherT.inkStanzas,function(inkStanza){return inkStanza.author == "teacher";}).length;
        teacherT.handwrite(_.map(_.range(300,600,5), function(i){
            return {x:i,y:i};
        }));
        teacher.waitUntil(function(){
            return _.filter(teacherT.inkStanzas,function(inkStanza){return inkStanza.author == teacherT.username;}).length == (inkStanzasBefore + 1);
        },5000,"expected new ink to appear in inkStanzas after looping through server");
        assert.equal(_.keys(teacherT.inkStanzas).length,1);
        for(var i = 2; i < 5; i++){
            var len = 35;
            var root = (len + 20) * i;
            teacher.click(sprintf("#pen%sButton",i));
            teacherT.handwrite(_.map(_.range(0,30,0.7), function(j){
                return {
                    x: root + Math.cos(j) * len--,
                    y: root + Math.sin(j) * len--
                };
            }));
        }
        assert.equal(_.keys(teacherT.inkStanzas).length,4);
    });
    it("should be able to add an image",function(){
        assert.equal(_.keys(teacherT.imageStanzas).length,0);
        teacherT.imageMode.click();
        teacher.click("#board");
        teacher.chooseFile("#imageFileChoice","testMaterials/mapleLeaf.jpg");
        teacher.waitUntil(function(){
            return _.keys(teacherT.imageStanzas).length == 1;
        },5000);
    });
    it("should have their new image selected when it appears",function(){
        assert.equal(_.keys(teacherT.selection.images).length,1);
    });
    it("should be able to clear their selection by clicking on an empty spot on the canvas",function(){
        teacherT.selectMode.click();
        teacher.leftClick("#board",1,1);
        assert.equal(_.keys(teacherT.selection.inks).length,0);
        assert.equal(_.keys(teacherT.selection.texts).length,0);
        assert.equal(_.keys(teacherT.selection.multiWordTexts).length,0);
        assert.equal(_.keys(teacherT.selection.videos).length,0);
        assert.equal(_.keys(teacherT.selection.images).length,0);
    });
    it("should select all the items that are under their mouse when they click the board",function(){
        teacher.leftClick("#board",250,250);
        teacher.waitUntil(function(){
            var sel = teacherT.selection;
            return _.keys(sel.inks).length == 1 &&
                _.keys(sel.texts).length == 0 &&
                _.keys(sel.multiWordTexts).length == 1 &&
                _.keys(sel.videos).length == 0 &&
                _.keys(sel.images).length == 1;
        });
    });
    it("should have their students see all their public elements",function(){
        student.waitUntil(function(){
            return _.keys(studentT.textStanzas).length == 2 &&
                _.keys(studentT.inkStanzas).length == 4 &&
                _.keys(studentT.imageStanzas).length == 1;
        });
    });
    it("should be able to delete their selected elements",function(){
        teacherT.deleteSelection.click();
    });
    it("should have their students see all their remaining public elements",function(){
        student.waitUntil(function(){
            return _.keys(studentT.imageStanzas).length == 0;
        });
        assert.equal(_.keys(studentT.textStanzas).length,1);
        assert.equal(_.keys(studentT.inkStanzas).length,3);
    });
    it("should have their participants view show the themes so far introduced in their conversation",function(){
        assert(teacherT.cloudData.length > 0);
    });
    it("should not have their students see their private content",function(){
        teacherT.privateMode.click();
        teacherT.imageMode.click();
        teacher.click("#board");
        teacher.chooseFile("#imageFileChoice","testMaterials/mapleLeaf.jpg");
        teacher.waitUntil(function(){
            return _.keys(teacherT.imageStanzas).length == 1;
        });
        assert.equal(_.keys(studentT.imageStanzas).length,0);
    });
    it("should be able to restore their deleted content from the recycle bin",function(){
        teacherT.menuButton.click();
        teacherT.recycleBinMenu.click();
        teacher.waitUntil(function(){
            return teacherT.recycleables.length == 3;
        });
    });
});
