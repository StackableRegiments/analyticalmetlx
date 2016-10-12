// Load default configs
var defaults = require("./wdio.conf.js").config;
var _ = require("lodash");

// Create overrides
var overrides = {
    specs: [
        './src/test/js/webdriver/multi/**'
    ],
    capabilities: {
        teacher:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'teacher',
                project:'single'
            }
        }
    },
    waitforTimeout: 999999,
    services: [],
	  timeout:999999
}

// Send the merged config to wdio
exports.config = _.defaultsDeep(overrides, defaults);