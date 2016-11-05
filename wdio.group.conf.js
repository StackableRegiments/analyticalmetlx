// Load default configs
var defaults = require("./wdio.conf.js").config;
var _ = require("lodash");

// Create overrides
var overrides = {
    specs: [
        './src/test/js/webdriver/group/**'
    ],
    maxInstances:1,
    capabilities: {
        teacher:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'teacher'
            }
        },
        studentA:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'studentA'
            }
        },
        studentB:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'studentB'
            }
        },
        studentC:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'studentC'
            }
        },
        studentD:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'studentD'
            }
        },
        studentE:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'studentE'
            }
        }
    }
}

// Send the merged config to wdio
exports.config = _.defaultsDeep(overrides, defaults);
