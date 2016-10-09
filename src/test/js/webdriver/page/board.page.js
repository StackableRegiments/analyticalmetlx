var Page = require('./page');
var assert = require('assert');
var BoardPage = function(user){
    return Object.create(Page, {
        texts: { get: function () { return user.execute("return _.map(boardContent.multiWordTexts,function(w){return _.pickBy(w,function(v,k){return k != 'doc'})})").value } },
        textMode: { get: function() { return user.element("#insertText"); } },
        textOptions: { get: function() { return user.element("fontOptions"); } },//Untried
        mode: {get:function(){ return user.execute("return Modes.currentMode").value; } },
        keyboard: {value:function(x,y,text){
            user.leftClick("#board",x,y);
            user.keys(text.split());
        }}
    });
}
module.exports = BoardPage
