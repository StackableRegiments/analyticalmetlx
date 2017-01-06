var _ = require('lodash');
var assert = require('assert');
var board = require('../page/board.page');
var sprintf = require('sprintf-js').sprintf;

var LoginPage = require("../page/login.page");
var ConversationsPage = require("../page/conversations.page");
var ConversationPage = require("../page/conversation.page");

var TEXT_DELAY = 5000;
var debug = function(msg){
    console.log(msg);
    browser.debug();
}
var assertSameContent = function(a,b){
    _.each(a.inkStanzas,function(aStanza,k,i){
        var comparison = JSON.stringify(aStanza);
        var comparatee = JSON.stringify(b.inkStanzas[k]);
        if(!(comparison == comparatee)){
            console.log(comparison);
            console.log("!!!!!!!!!!!==============");
            console.log(comparatee);
        }
        assert.equal(comparison,comparatee);
    });
    _.each(a.imageStanzas,function(aStanza,k){
        var comparison = JSON.stringify(aStanza);
        var comparatee = JSON.stringify(b.imageStanzas[k]);
        if(!(comparison == comparatee)){
            console.log(comparison);
            console.log("!!!!!!!!!!!==============");
            console.log(comparatee);
        }
        assert.equal(comparison,comparatee);
    });
    _.each(a.textStanzas,function(aStanza){
        var bStanza = _.find(b.textStanzas,function(stanza){
            return aStanza.identity == stanza.identity;
        })
        assert.equal(JSON.stringify(aStanza),JSON.stringify(bStanza));
    });
};
var assertNotSameContent = function(a,b){
    assert(_.some(a.inkStanzas,function(aStanza,k,i){
        return JSON.stringify(aStanza) != JSON.stringify(b.inkStanzas[k]);
    }));
    assert(_.some(a.imageStanzas,function(aStanza,k){
        return JSON.stringify(aStanza) != JSON.stringify(b.imageStanzas[k]);
    }));
    assert(_.some(a.textStanzas,function(aStanza){
        var bStanza = _.find(b.textStanzas,function(stanza){
            return aStanza.identity == stanza.identity;
        })
        return JSON.stringify(aStanza) != JSON.stringify(bStanza);
    }));
};

describe('When the class breaks into groups,', function() {
    var teacherName;
    var tT = board(teacher);
    var sA = board(studentA);
    var sB = board(studentB);
    var sC = board(studentC);
    var sD = board(studentD);
    var users = [tT,sA,sB,sC,sD];

    var join = function(user,label){
        var login = LoginPage(user);
        var search = ConversationsPage(user);
        var username = sprintf(label);
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
    it("given that the teacher adds a group slide, importing an external group and specifying a strategy,",function(){
        assert.equal(tT.currentSlide.index,0);
        tT.addGroupSlide.click();
        teacher.pause(1000);
        teacher.execute("$('.jAlert .ouSelector').val('xmlUserOverrides_from_testMaterials/orgData.xml').change()");
        browser.waitUntil(function(){
            var visible = teacher.execute("return $('.jAlert #structuralGroup_2').length > 0;").value;
            return visible;
        });
        teacher.execute("$('.jAlert #structuralGroup_2').click()");
        teacher.execute("$('.jAlert .strategySelect').val('byTotalGroups').change()");
        teacher.pause(500);
        teacher.execute("$('.jAlert .parameterSelect').val('3').change()");
        teacher.execute("$(\"a:contains('Add page')\").click()");
        teacher.waitUntil(function(){
            return tT.currentSlide.index == 1;
        });
    });
    it("the teacher should be taken to it",function(){
        assert.equal(tT.currentSlide.index,1);
    });
    it("the students should all follow to it",function(){
        browser.pause(TEXT_DELAY);
        assert.equal(sA.currentSlide.index,1);
        assert.equal(sB.currentSlide.index,1);
    });
    it("all groups should be listed on its thumb",function(){
        browser.waitUntil(function(){
            var groupSlide = teacher.execute("return $('.activeSlide.groupSlide').length").value;
            return groupSlide == 1;
        });
        assert.equal(teacher.execute("return $('.activeSlide.groupSlide').length").value,1);
    });
    it("a student show see their current group",function(){
        assert(sB.boardTitle.trim().startsWith("studentB working in Group 1 of teacher at"));
    });
    it("the students should all be split into groups",function(){
        browser.pause(TEXT_DELAY);
        var groupSet = tT.currentSlide.groupSets[0];
        console.log(groupSet.groups);
        assert.equal(groupSet.groups.length,3);
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
    it("while the teacher does not join any group",function(){
        assert(_.every(tT.currentSlide.groupSets[0].groups,function(group){
            return !(_.some(group.members,teacherName));
        }));
    });
    it("students see the teacher work but not other groups",function(){
        _.each([tT,sA,sB],function(client,i){
            client.driver.pause(500);
            client.textMode.click();
            client.keyboard(50,100 + i * 100,"Phrase "+(i+1));
        });
        browser.pause(TEXT_DELAY);//Let everything synchronize

        console.log("SB",sB.plainTexts);
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
    it("the teacher can filter out all groups but the students can only filter out their own",function(){
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

        var groups = tT.currentSlide.groupSets[0].groups;
        assert(teacher.isExisting("#contentFilter_"+groups[0].id));
        assert(teacher.isExisting("#contentFilter_"+groups[1].id));

        assert(!studentA.isExisting("#contentFilter_"+groups[0].id));
        assert(studentA.isExisting("#contentFilter_"+groups[1].id));

        assert(studentB.isExisting("#contentFilter_"+groups[0].id));
        assert(!studentB.isExisting("#contentFilter_"+groups[1].id));
    });
    it("connection health should be a visible metric",function(){
        assert(browser.isExisting("#healthStatus"));
        assert(tT.connectionHealth > 0);
    });
    it("participant presence should not be an active metric when the class is not restricted",function(){
        assert(browser.isExisting("#participationStatus"));
        assert.equal(tT.currentConversation.subject,"unrestricted");
        assert.equal(tT.participationHealth,3);
    });
    it("given that the teacher restricts the conversation",function(){
        tT.homeTab.click();
        tT.conversationSearch.click();
        teacher.click("=Edit");
        browser.waitUntil(function(){
            return teacher.isVisible(".conversationSharingCollapser.course");
        });
        teacher.click(".conversationSharingCollapser.course");
        teacher.click("label=Org Unit A");
        teacher.click(".joinConversation");
        teacher.waitForExist("#board");
        teacher.waitForExist("#nextSlideButton");
        tT.nextSlide.click();
        teacher.waitUntil(function(){
            return tT.currentSlide.index == 1;
        });
    });
    it("participant presence should be measured against potential participants",function(){
        assert.equal(tT.currentConversation.subject,"Org Unit A");
        assert.equal(tT.participationHealthMax,26);
        assert.equal(tT.participationHealth,3);
        join(studentC,'studentC');
        studentC.waitForExist("#board");
        assert.equal(tT.participationHealth,4);
    });
    it("group restriction should apply to new entrants",function(){
        studentC.waitUntil(function(){
            return sC.currentSlide.index == 1;
        });
        var groups = sC.currentSlide.groupSets[0].groups;
        assert.equal(groups.length,3);
        sC.menuButton.click();
        studentC.waitUntil(function(){return studentC.isVisible("#roomToolbar");});
        sC.learning.click();
        studentC.waitUntil(function(){return studentC.isVisible("#menuContentFilter");});
        sC.contentFilter.click();

        assert(studentC.isExisting("#contentFilter_"+groups[0].id));
        assert(!studentC.isExisting("#contentFilter_"+groups[1].id));

        assert(_.includes(sC.plainTexts,"Phrase 1"));
        assert(!(_.includes(sC.plainTexts,"Phrase 2")));
        assert(_.includes(sC.plainTexts,"Phrase 3"));

        sC.menuButton.click();
        sC.textMode.click();
        sC.keyboard(50,4,"Phrase 4");
        browser.pause(TEXT_DELAY);//Let everything synchronize
        assert(!(_.includes(sA.plainTexts,"Phrase 4")));
        assert(_.includes(sB.plainTexts,"Phrase 4"));
        assert(_.includes(sC.plainTexts,"Phrase 4"));
        assert(_.includes(tT.plainTexts,"Phrase 4"));
    });
    it("further entrants should be allocated into groups",function(){
        join(studentD,'studentD');
        browser.waitForExist("#board");
        browser.waitUntil(function(){
            return sD.currentSlide.groupSets.length && sD.currentSlide.groupSets[0].groups.length == 3;
        });
        assert(_.includes(sD.plainTexts,"Phrase 1"));
        assert(!(_.includes(sD.plainTexts,"Phrase 2")));
        assert(!(_.includes(sD.plainTexts,"Phrase 3")));
        assert(!(_.includes(sD.plainTexts,"Phrase 4")));
    });
    it("all content types should be group restricted",function(){
        _.each(users,function(user,ui){//Close all backstages
            if(user.applicationMenu.value != null){
                user.menuButton.click();
            }
            user.driver.waitUntil(function(){
                return user.driver.isVisible("#drawMode")
            });
            user.inkMode.click();
            user.handwrite(_.map(_.range(200,400,15), function(i){
                return {x:ui*10+i,y:i};
            }));
            user.addImage("testMaterials/mapleLeaf.jpg");
        });
        browser.pause(TEXT_DELAY);//Let everything synchronize
        assert.equal(_.keys(tT.inkStanzas).length,5);
        assert.equal(_.keys(sA.inkStanzas).length,2);
        assert.equal(_.keys(sB.inkStanzas).length,3);
        assert.equal(_.keys(sC.inkStanzas).length,3);
        assert.equal(_.keys(sD.inkStanzas).length,2);

        assert.equal(_.keys(tT.imageStanzas).length,5);
        assert.equal(_.keys(sA.imageStanzas).length,2);
        assert.equal(_.keys(sB.imageStanzas).length,3);
        assert.equal(_.keys(sC.imageStanzas).length,3);
        assert.equal(_.keys(sD.imageStanzas).length,2);
    });
    it("group peers should see me move content",function(){
        var user = sB;
        var peer = sC;
        var nonPeer = sA;
        user.inkMode.click();
        assert.equal(_.keys(user.selection.images).length,0);
        assert.equal(_.keys(user.selection.inks).length,0);
        assert.equal(_.keys(user.selection.multiWordTexts).length,0);
        user.selectMode.click();
        user.clickScreen(200,250);
        assert.equal(_.keys(user.selection.images).length,1);
        assert.equal(_.keys(user.selection.inks).length,1);
        assert.equal(_.keys(user.selection.multiWordTexts).length,1);
        var imageStartX = _.values(user.selection.images)[0].x;
        var textStartX = _.find(user.selection.multiWordTexts,function(text){
            return text.author == "studentB";
        }).x;
        assert(_.every(peer.imageStanzas,function(image){
            return image.x == imageStartX;
        }));
        assert(_.some(peer.textStanzas,function(text){
            return text.x == textStartX;
        }));
        user.handwrite(_.map(_.range(200,400,20), function(i){
            return {x:i,y:i};
        }));
        user.driver.waitUntil(function(){
            return _.some(user.selection.images,function(image){
                return image.x != imageStartX;
            }) && _.some(user.textStanzas,function(text){
                return text.x != textStartX;
            });
        });
        var textEndX = _.find(user.textStanzas,function(text){
            return text.author == "studentB";
        }).x;
        var imageEndX = _.values(user.selection.images)[0].x;
        assert.notEqual(imageStartX,imageEndX);
        peer.driver.waitUntil(function(){
            return _.some(peer.imageStanzas,function(image){
                return image.x == imageEndX;
            }) && _.some(peer.textStanzas,function(text){
                return text.x == textEndX;
            });
        });
        assertSameContent(user,peer);
        assertNotSameContent(user,nonPeer);
    });
    it("the Groups plugin should enable the teacher to isolate publication to a particular group",function(){
        tT.driver.execute("$('#isolateGroup_1').click()");
        tT.textMode.click();
        tT.keyboard(130,300,"RESPONSE TO GROUP 1 ONLY");
        tT.inkMode.click();
        tT.handwrite(_.map(_.range(200,400,15), function(i){
            return {x:50,y:i};
        }));
        tT.addImage("testMaterials/stormtrooper.jpg");
        browser.pause(TEXT_DELAY * 2);//Let everything synchronize
        var peer = sA;
        var nonPeer = sC;
        assert.equal(_.keys(sB.inkStanzas).length,4);
        assert.equal(_.keys(sA.inkStanzas).length,2);
        assert.equal(_.keys(sB.textStanzas).length,4);
        assert.equal(_.keys(sA.textStanzas).length,2);
        assert.equal(_.keys(sB.imageStanzas).length,4);
        assert.equal(_.keys(sA.imageStanzas).length,2);
        tT.driver.execute("$('#isolateGroup_2').click()");
        tT.textMode.click();
        tT.keyboard(330,500,"RESPONSE TO GROUP 2 ONLY");
        browser.pause(TEXT_DELAY);//Let everything synchronize
    });
    it("groups should not persist beyond the slide",function(){
        tT.newSlide.click();
        browser.waitUntil(function(){
            return tT.currentSlide.index == 2;
        });
        assert.equal(tT.currentSlide.index,2);
        assert.equal(tT.currentSlide.groupSets.length,0);
        _.each(users,function(user,ui){//Close all backstages
            if(user.applicationMenu.value != null){
                user.menuButton.click();
            }
            user.inkMode.click();
            user.handwrite(_.map(_.range(200,400,15), function(i){
                return {x:ui*10+i,y:i};
            }));
            user.addImage("testMaterials/mapleLeaf.jpg");
        });
        browser.pause(TEXT_DELAY);//Let everything synchronize
        assert.equal(_.keys(tT.inkStanzas).length,5);
        assert.equal(_.keys(sA.inkStanzas).length,5);
        assert.equal(_.keys(sB.inkStanzas).length,5);
        assert.equal(_.keys(sC.inkStanzas).length,5);
        assert.equal(_.keys(sD.inkStanzas).length,5);

        assert.equal(_.keys(tT.imageStanzas).length,5);
        assert.equal(_.keys(sA.imageStanzas).length,5);
        assert.equal(_.keys(sB.imageStanzas).length,5);
        assert.equal(_.keys(sC.imageStanzas).length,5);
        assert.equal(_.keys(sD.imageStanzas).length,5);
    });
    it("the teacher should not have group filters on the non group slide",function(){
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

        assert.equal(teacher.execute("return $('.contentFilterItem').length").value,3);
    });
    it("the teacher should regain their group filters on returning to the group slide",function(){
        tT.menuButton.click();
        tT.prevSlide.click();
        browser.waitUntil(function(){
            return tT.currentSlide.index == 1;
        });
        tT.menuButton.click();
        teacher.waitUntil(function(){return teacher.isVisible("#roomToolbar");});

        tT.learning.click();
        teacher.waitUntil(function(){return teacher.isVisible("#menuContentFilter");});

        tT.contentFilter.click();
    });
    it("content sharing should be reestablished on return to the slide",function(){
        _.each([sB,sC],function(user,ui){//Close all backstages
            if(user.applicationMenu.value != null){
                user.menuButton.click();
            }
            user.driver.waitUntil(function(){
                return user.driver.isVisible("#drawMode")
            });
            user.inkMode.click();
            user.driver.waitUntil(function(){
                return user.driver.isVisible(sprintf("#pen%sButton",ui+1));
            });
            user.driver.click(sprintf("#pen%sButton",ui+1));
            user.handwrite(_.map(_.range(200,400,15), function(i){
                return {x:ui*10+i,y:i};
            }));
            user.addImage("testMaterials/stormtrooper.jpg");
            browser.pause(500);
            user.textMode.click();
            user.keyboard(200,50 * (ui + 3),sprintf("Stormtrooper %s",ui));
        });
        browser.pause(TEXT_DELAY * 3);//Let everything synchronize
        console.log("SB",sB.plainTexts);
        console.log("SC",sC.plainTexts);
        console.log("SA",sA.plainTexts);
        assert.equal(_.keys(sB.inkStanzas).length,6);
        assert.equal(_.keys(sB.textStanzas).length,6);
        assert.equal(_.keys(sB.imageStanzas).length,6);

        assert.equal(_.keys(sC.inkStanzas).length,6);
        assert.equal(_.keys(sC.textStanzas).length,6);
        assert.equal(_.keys(sC.imageStanzas).length,6);

        assert.equal(_.keys(sA.inkStanzas).length,2);
        assert.equal(_.keys(sA.textStanzas).length,2);
        assert.equal(_.keys(sA.imageStanzas).length,2);
    });
});
