var Page = require('./page');
var assert = require('assert');
var sprintf = require('sprintf-js').sprintf;
var BoardPage = function(user){
    return Object.create(Page, {
        texts: { get: function () { return user.execute("return _.map(boardContent.multiWordTexts,function(w){var _w = _.cloneDeep(w);delete _w.doc;return _w;})").value } },
        textMode: { get: function() { return user.element("#insertText"); } },
        textStanzas: { get: function() { return user.execute("return _.map(boardContent.multiWordTexts, richTextEditorToStanza)").value } },
        mode: {get:function(){ return user.execute("return Modes.currentMode").value; } },
        interactables: { get: function(){ return user.execute("return Modes.getCanvasInteractables()").value } },
        keyboard: {value:function(x,y,text){
            user.moveToObject("#board",x,y);
            user.leftClick();
            user.keys(text.split());
        }},
        worldToScreen: { value: function(x,y){
            return user.execute(sprintf("return worldToScreen(%s,%s)",x,y)).value;
        }}
    });
}
module.exports = BoardPage
