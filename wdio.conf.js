var config = {
    exclude: [],
    maxInstances: 10,
    sync: true,
    logLevel: 'result',
    coloredLogs: true,
    screenshotPath: './errorShots/',
    baseUrl: 'http://localhost:8080',
    waitforTimeout: 2000,
    connectionRetryTimeout: 90000,
    connectionRetryCount: 3,
    services: [],
    framework: 'mocha',
    reporters: ['spec'],
    mochaOpts: {
        ui: 'bdd',
        timeout:999999
    }
}

/*
if(process.env.CI){
    config.services.push('sauce');
    config.user = process.env.SAUCE_USERNAME;
    config.key = process.env.SAUCE_ACCESS_KEY,
    config.sauceConnect = true;
    config.user = process.env.SAUCE_USERNAME;
    config.key = process.env.SAUCE_ACCESS_KEY;
}
*/
exports.config = config;
