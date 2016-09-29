module.exports = {
    'Login as test user': function(browser) {
        browser
            .url('http://localhost:8080/board')
            .setValue('input[type=text]','nightwatch')
            .click('input[type=submit]')
            .waitForElementVisible('#conversationSearchBox',5000)
    },
    'Search page lists no conversations', function(browser){
        browser
            .assert.containsText('.jsgrid-nodata-row','No conversations');
    },
    after: function(browser){
        browser.end()
    }
};
