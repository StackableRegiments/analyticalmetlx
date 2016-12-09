// Load default configs
var defaults = require("./wdio.conf.js").config;
var _ = require("lodash");

// Create overrides
var overrides = {
    specs: [
        './src/test/js/webdriver/analytics/**'
    ],
    suites:{
        learning:['./src/test/js/webdriver/analytics/*.js']
    },
    maxInstances:1,
    capabilities: {
        teacher:{
            desiredCapabilities:{
                browserName: 'chrome',
                name:'teacher'
            }
        }
    }
}
for(var i = 0; i < 3;i++){
    var name = "student"+String.fromCharCode(i+97).toUpperCase();
    overrides.capabilities[name] = {
	desiredCapabilities:{
	    browserName:"chrome",
	    name:name
	}
    };
}

// Send the merged config to wdio
exports.config = _.defaultsDeep(overrides, defaults);
