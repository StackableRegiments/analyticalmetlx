module.exports = {
    'Login as test user': browser => {
        browser
            .url('http://localhost:8080/board')
            .setValue('input[type=text]','nightwatch')
            .click('input[type=submit]')
            .waitForElementVisible('#conversationSearchBox',5000)
    },
    'Search page lists no conversations': browser => {
        browser
            .assert.containsText('.jsgrid-nodata-row','No conversations');
    },
    after: browser => browser.end()
};
