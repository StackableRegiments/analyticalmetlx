var _ = require('lodash');
var sprintf = require('sprintf-js').sprintf;

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
    waitForCreateButton: { value: function() {
      return user.waitForExist('#createConversationButton',2000);
    } },
    createConversation: { value: function() {
      user.click('#createConversationButton');
    } },
    waitForNewConversation: { value: function() {
      return user.waitForExist('.newConversationTag',2000);
    } },
    waitForImportButton: { value: function() {
      return user.waitForExist('#showImportConversationWorkflow',2000);
    } },
    importConversation: { value: function(filename) {
      user.chooseFile('#importConversationInputElement','src/test/resources/' + filename);
    } },
    waitForImportedConversation: { value: function(teacherName,filename) {
      return user.waitForExist(sprintf("td*=%s's (%s)",teacherName,filename),60000);
    } },
    searchForConversation: { value: function(name) {
      user.element('#conversationSearchBox>input[type=text]').setValue(name);
      user.click('#searchButton');
    } },
    getConversations: { value: function() {
      return teacher.execute("return Conversations.getConversationListing()").value;
    } },
    getNewConversations: { value: function(previousConversations) {
      teacher.waitUntil(function(){
          return teacher.execute("return Conversations.getConversationListing()").value.length == (previousConversations.length + 1);
      },5000,"expected a new conversation to appear");
      return newConversations = _.filter(teacher.execute("return Conversations.getConversationListing()").value,function(nc){
          return !_.some(previousConversations,function(pc){
              return pc == nc;
          });
      });
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