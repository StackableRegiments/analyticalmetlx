exports.config = {
    specs: [
        './src/test/js/webdriver/multibrowser.js'
    ],
    exclude: [
    ],
    maxInstances: 10,
    capabilities: {
        teacher:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'teacher',
                build:' : ',
                project:'simple'
            }
        },
        student:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'student',
                build:' : ',
                project:'simple'
            }
        }
    },
    sync: true,
    logLevel: 'verbose',
    coloredLogs: true,
    screenshotPath: './errorShots/',
    baseUrl: 'http://localhost:8080',
    waitforTimeout: 10000,
    connectionRetryTimeout: 90000,
    connectionRetryCount: 3,
    services: ['sauce'],
    user: process.env.SAUCE_USERNAME,
    key: process.env.SAUCE_ACCESS_KEY,
    sauceConnect: true,
    framework: 'mocha',
    reporters: ['dot'],
    mochaOpts: {
        ui: 'bdd'
    }
}
