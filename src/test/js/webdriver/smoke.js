describe('smokeTest', function() {
    it('returns the page title', function() {
        browser.url('http://localhost:8080/');
        var title = browser.getTitle();
        console.log('Title is: ' + title);
    });
});
