var Page = require('./page');
var ConversationsPage = function(user){
  return Object.create(Page, {
    // Page methods
    open: { value: function() {
      Page.open.call(this, 'conversationSearch');
    } },
    submit: { value: function() {
      user.element('form').submitForm();
    } },
    waitForSearchBox: { value: function() {
      return user.waitForExist('#conversationSearchBox',2000);
    } },
    createConversation: { value: function() {
      user.click('#createConversationButton');
    } },
    importConversation: { value: function(filename) {
//      console.log('Current directory: ' + process.cwd());
//      user.click('#showImportConversationWorkflow');
      user.chooseFile('#importConversationInputElement','src/test/resources/' + filename);
    } },
    searchForConversation: { value: function(name) {
      user.element('#conversationSearchBox>input[type=text]').setValue(name);
      user.click('#searchButton');
    } },
    waitForConversation: { value: function(name) {
      return user.waitForExist('td*=' + name,15000);
    } },
    hasConversation: { value: function(name) {
      return user.waitForExist('td*=' + name,10000);
    } },
    joinConversation: { value: function(name) {
      user.click('td*=' + name);
    } }
  });
}
module.exports = ConversationsPage;