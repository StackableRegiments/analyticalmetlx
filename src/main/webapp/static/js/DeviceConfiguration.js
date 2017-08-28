var DeviceConfiguration = (function(){
    var gutterHeight = 10;
    var gutterWidth = 2;
    var identity = Date.now();
    var currentDevice = "browser";
    var orientation = "landscape";
    var px = function(i){
        return sprintf("%spx",i);
    }
		var screenWidth = 0; screenHeight = 0;
    var returnCurrentDeviceFunction = function(){
        return currentDevice;
    };
    var alterCurrentDeviceFunction = function(newDevice){
        currentDevice = newDevice;
        actOnCurrentDevice();
    };
    var tryToDetermineCurrentDevice = function(){
        if ((navigator.userAgent.match(/iPhone/i) != null) || (navigator.userAgent.match(/iPod/i) != null)) {
            currentDevice = "iPhone";
        } else if (navigator.userAgent.match(/iPad/i) != null){
            currentDevice = "iPad";
        } else if (navigator.userAgent.match("Trident\/7\.0") != null){
            currentDevice = "IE11+";
        } else {
            currentDevice = "browser";
        }
    };
    function getOrientation() {
        var landscape;
        if('orientation' in window) {
            // Mobiles
            var orientation = window.orientation;
            landscape = (orientation == 90 || orientation == -90);
        }
        else {
            // Desktop browsers
            landscape = window.innerWidth > window.innerHeight;
        }
        return landscape ? 'landscape' : 'portrait';
    }
    var getDeviceDimensions = function(){
        var screen_width = screen.width,
            screen_height = screen.height;
        if(getOrientation() == 'landscape' && screen_width < screen_height) {
            screen_width = screen.height;
            screen_height = screen.width;
        }
        var w = window.innerWidth,
            h = window.innerHeight;
        if(!w || !h || w > screen_width || h > screen_height || w == 980) { //uh, what's 980 about?  Is that an iPad specific measurement?
            w = window.outerWidth;
            h = window.outerHeight;
        }
        if(!w || !h || w > screen_width || h > screen_height) {
            w = screen.availWidth;
            h = screen.availHeight;
        }
        return {width: w, height: h};
    };
		var windowUpdateSpeed = 100;
		var updateMeasurements = _.throttle(function(){
			var dims = getDeviceDimensions();
			var o = getOrientation();
			screenWidth = dims.width;
			screenHeight = dims.height;
			orientation = o;
			MeTLBus.call("layoutUpdated",[getMeasurementsFunc()]);
		},windowUpdateSpeed,{leading:true,trailing:true});
		var getMeasurementsFunc = function(){
			return {
				width:screenWidth,
				height:screenHeight,
				orientation:orientation
			};
		};
    $(function(){
			// set up orientation and resize handlers
			var w = $(window);
			if (window.orientation){
					w.on("orientationchange",updateMeasurements);
			}
			w.resize(updateMeasurements);
			tryToDetermineCurrentDevice();
			updateMeasurements();
    });
    return {
			getCurrentDevice:returnCurrentDeviceFunction,
			setCurrentDevice:alterCurrentDeviceFunction,
			getIdentity:function(){
				return identity;
			},
			getMeasurements:getMeasurementsFunc
		};
})();
