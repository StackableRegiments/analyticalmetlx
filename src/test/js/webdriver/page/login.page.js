var Page = require('./page');
var LoginPage = function(user){
  return Object.create(Page, {
    // Page elements
    form: {
      get: function() { return user.element('form'); }
    },
    username: {
      get: function () { return user.element('input[type=text]'); }
    },
    // Page methods
    open: { value: function() {
      Page.open.call(this, 'board');
    } },
    submit: { value: function() {
      this.form.submitForm();
    } }
  });
}
module.exports = LoginPage;