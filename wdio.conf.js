var config = {
    exclude: [],
    maxInstances: 10,
    sync: true,
    logLevel: 'result',
    logOutput: 'wdio.log',
    coloredLogs: true,
    baseUrl: 'http://localhost:8080',
    waitforTimeout: 2000,
    connectionRetryTimeout: 10000,
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
 }
 */
exports.config = config;
