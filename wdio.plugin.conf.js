// Load default configs
var defaults = require("./wdio.conf.js").config;
var _ = require("lodash");

// Create overrides
var overrides = {
    specs: [
        './src/test/js/webdriver/plugins/**'
    ],
    suites:{
        learning:['./src/test/js/webdriver/plugins/plugins.js']
    },
    capabilities: {
        teacher:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'teacher'
            }
        },
        student:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'student'
            }
        }
    }
}

// Send the merged config to wdio
exports.config = _.defaultsDeep(overrides, defaults);
