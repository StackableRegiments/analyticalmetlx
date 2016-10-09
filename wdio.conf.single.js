exports.config = {
    specs: [
        './src/test/js/webdriver/teacherPath.js'
    ],
    exclude: [],
    maxInstances: 10,
    capabilities: {
        teacher:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'teacher',
                project:'single'
            }
        }
    },
    sync: true,
    logLevel: 'verbose',
    coloredLogs: true,
    screenshotPath: './errorShots/',
    baseUrl: 'http://localhost:8080',
    waitforTimeout: 999999,
    connectionRetryTimeout: 90000,
    connectionRetryCount: 3,
    services: [],
    framework: 'mocha',
    reporters: ['dot'],
    mochaOpts: {
        ui: 'bdd',
	timeout:999999
    }
}
