// Load default configs
var defaults = require("./wdio.conf.js").config;
var _ = require("lodash");

// Create overrides
exports.config = {
    specs: [
        './src/test/js/webdriver/single/**'
    ],
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
    }
}

// Send the merged config to wdio
exports.config = _.defaultsDeep(overrides, defaults);