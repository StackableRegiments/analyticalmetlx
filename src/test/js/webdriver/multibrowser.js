describe('Multiple users', function() {
    it('should go to application', function () {
        browser.url('/board');
    });
    it('should login', function () {
        teacher.setValue('input[type=text]', 'teacher');
        student.setValue('input[type=text]', 'student');
    });
    it('should submit the login form', function () {
        browser.click('input[type=submit]');
	browser.waitForExist("#conversationSearchBox",5000);
    });
    it('should end the session', function () {
        browser.pause(5000);
    });
});
