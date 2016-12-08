var config = {
    exclude: [],
    maxInstances: 10,
    sync: true,
    logLevel: 'verbose',
    logOutput: './wdio.log',
    coloredLogs: true,
    baseUrl: 'http://localhost:8080',
    waitforTimeout: 10000,
    connectionRetryTimeout: 10000,
    connectionRetryCount: 3,
    services: [],
    framework: 'mocha',
    reporters: ['spec'],
    mochaOpts: {
        ui: 'bdd',
        timeout:99999999
    }
}

//if(process.env.CI) {
if(false) {
    config.services.push('sauce');
    config.user = process.env.SAUCE_USERNAME;
    config.key = process.env.SAUCE_ACCESS_KEY,
    config.sauceConnect = true;
}
else
{
    //config.services.push('selenium-standalone');
}
exports.config = config;
