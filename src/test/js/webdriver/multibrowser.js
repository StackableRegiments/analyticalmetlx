describe('Multiple users', function() {
    it('should go to application', function () {
        browser.url('/board');
    });
    it('should login', function () {
        browserA.setValue('input[type=text]', 'primus');
        browserB.setValue('input[type=text]', 'secundus');
        browser.sync();
    });
    it('should submit the login form', function () {
        browser.click('input[type=submit]');
    });
    it('should end the session', function () {
        browser.pause(5000);
        browser.end();
    });
});
