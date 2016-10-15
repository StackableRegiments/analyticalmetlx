var Page = require('./page');
var assert = require('assert');
var _ = require('lodash');
var sprintf = require('sprintf-js').sprintf;
var BoardPage = function(user) {
    var worldToScreen = function(x,y){
        return user.execute(sprintf("return worldToScreen(%s,%s)",x,y)).value;
    };
    return Object.create(Page, {
        privacy: { get:function(){ return user.execute("return Privacy.getCurrentPrivacy()").value; } },
	privateMode: { get:function(){ return user.element("#privateMode") } },
	publicMode: { get:function(){ return user.element("#publicMode") } },
	
        mode: { get:function(){ return user.execute("return Modes.currentMode").value; } },
        interactables: { get: function(){ return user.execute("return Modes.getCanvasInteractables()").value } },
        drag: { value:function(handle,delta){
            var dragPos = worldToScreen(handle.bounds[0],handle.bounds[1]);
            user.moveToObject("#board",dragPos.x,dragPos.y);
            user.buttonDown();
            user.moveToObject("#board",dragPos.x + delta.x, dragPos.y + delta.y);
            if(delta.debug){
                user.debug();
            }
            user.buttonUp();
            return handle;
        } },
        swipeUp: {value: function(){
            user.moveToObject("#board",200,10);
            user.buttonDown();
            user.moveToObject("#board",200,-10);
            user.buttonUp();
        } },
        swipeLeft: {value: function(){
            user.moveToObject("#board",10,10);
            user.buttonDown();
            user.moveToObject("#board",-10,10);
            user.buttonUp();
        } },
        worldToScreen: {value: worldToScreen },
        screenToWorld: {value: function(x,y){return user.execute(sprintf("return screenToWorld(%s,%s)",x,y)).value; }},
        menuButton: {get: function(){return user.element("#applicationMenuButton");}},
        recycleBinMenu: {get: function(){return user.element("#menuRecycleBin");}},

        themes: {get: function(){ return user.execute("return boardContent.themes").value; }},
        cloudData: {get: function(){ return user.execute("return Analytics.word.cloudData()").value; } },

        selectMode: { get: function() { return user.element("#selectMode"); } },
        selection: {get: function(){ return user.execute("return (function(){var s = _.cloneDeep(Modes.select.selected);s.multiWordTexts = _.map(s.multiWordTexts,function(w){var _w=_.cloneDeep(w);delete _w.doc;return _w;}); return s;})()").value; } },
        deleteSelection: {get: function(){return user.element("#delete");}},

        username: { get: function(){ return user.execute("return UserSettings.getUsername()").value; } },

        texts: { get: function () { return user.execute("return _.map(boardContent.multiWordTexts,function(w){var _w = _.cloneDeep(w);delete _w.doc;return _w;})").value } },
        textMode: { get: function() { return user.element("#insertText"); } },
        textStanzas: { get: function() { return user.execute("return _.map(boardContent.multiWordTexts, richTextEditorToStanza)").value } },
        keyboard: { value:function(x,y,text){
            user.moveToObject("#board",x,y);
            user.leftClick();
            user.keys(text.split());
        } },

        inkMode: { get: function() { return user.element("#drawMode"); } },
        inkStanzas: { get: function() { return user.execute("return boardContent.inks").value; } },
        handwrite: { value:function(pts){
            user.moveToObject("#board",pts[0].x,pts[0].y);
            user.buttonDown();
            _.each(pts,function(pt){
                user.moveToObject("#board",pt.x,pt.y);
            });
            user.buttonUp();
        } },

        imageMode: { get: function() { return user.element("#imageMode"); } },
        imageStanzas: {get: function(){ return user.execute("return boardContent.images").value } }
    });
}
module.exports = BoardPage
