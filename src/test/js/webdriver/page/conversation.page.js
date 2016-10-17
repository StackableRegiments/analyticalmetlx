var Page = require('./page');
var ConversationPage = function(user){
  return Object.create(Page, {
    // Page methods
    isNamed: { value: function(name) {
      return null != user.element('#currentConversationTitle*=' + name + ' at');
    } },
    joinConversation: { value: function(name) {
      user.click('td*=' + name);
    } }
  });
}
module.exports = ConversationPage;