// Chrome allow/deny fix OPENTOK-1741
// This fix is included in TB.js as well as fanEmbed.js and embed.js for TokShow and the basic embed.

(function(window) {
    var chromeVersion = /chrome\/([0-9]+)\.([0-9]+)\.([0-9]+)\.([0-9]+)/.exec(navigator.userAgent.toLowerCase());

    var browser = function() {
        var userAgent = window.navigator.userAgent.toLowerCase(),
     navigatorVendor;

        if (userAgent.indexOf('firefox') > -1)   {
            return 'Firefox';
        }
        if (userAgent.indexOf('opera') > -1)   {
            return 'Opera';
        }
        else if (userAgent.indexOf("msie") > -1) {
            return "IE";
        }
        else if (userAgent.indexOf("chrome") > -1) {
            return "Chrome";
        }

        if ((navigatorVendor = window.navigator.vendor) && navigatorVendor.toLowerCase().indexOf("apple") > -1) {
            return "Safari";
        }

        return "unknown";
    };

    window.TB_ChromeAllowFix = {
        allowDenyBugPresent: browser() === "Chrome" && parseInt(chromeVersion[1], 10) >= 21
    };

    var roundToDecimalPlaces = function(number, places) {
        return Math.round(number * Math.pow(10, places))/Math.pow(10, places);
    };

    // Poll the position of an element at regular intervals and fix it if it needs fixing
    window.TB_ChromeAllowFix.AllowFixer = function(element) {
        var elPositions = {},
     restorePositions = {},
     lastElAdjustments = {},
     positionTimeout;

        var checkPosition = function() {
            if (!element || !element.parentNode) {
                // If the element doesn't exist or has been removed from the DOM then we stop polling
                this.stop();
                return;
            }

            checkAndFixElement('parent', element.offsetParent);
            checkAndFixElement('element', element);

            // Every 500 milliseconds we check again
            positionTimeout = setTimeout(checkPosition, 500);
        };

        var checkAndFixElement = function(type, element) {
            if (!element) return;

            if (!lastElAdjustments[type]) lastElAdjustments[type] = {};

            if (!restorePositions[type]) {
                restorePositions[type] = {
                    marginLeft: element.style.marginLeft
                };
            }
            else {
                // If the current marginLeft is not what we set it to, track it so
                // that we can restore it later. Do the same for the top.
                if (element.style.marginLeft && parseFloat(element.style.marginLeft) !== lastElAdjustments[type].marginLeft) {
                    restorePositions[type].marginLeft = element.style.marginLeft;
                }
            }

            var currPosition = element.getBoundingClientRect();
            if (!elPositions[type] || elPositions[type].left != currPosition.left || elPositions[type].top != currPosition.top) {
                fixElPosition(element);

                lastElAdjustments[type].marginLeft = element.style.marginLeft && element.style.marginLeft.length ? parseFloat(element.style.marginLeft) : null;
                elPositions[type] = element.getBoundingClientRect();
            }
        };

        var fixElPosition = function(el) {
            // Check the left and top positions of the element to make sure they're whole numbers
            var elPosition = el.getBoundingClientRect(),
         elLeft = elPosition.left,
         leftDiff = roundToDecimalPlaces(Math.round(elLeft) - elLeft, 3);       // Rounding to 3 decimal places

            if (leftDiff != 0) {
                // Left is a fraction, round it
                var margLeft = getCSSPosition(el, 'marginLeft');
                el.style.marginLeft = margLeft + leftDiff + "px";
            }

            var elTop = elPosition.top,
         topDiff = roundToDecimalPlaces(Math.round(elTop) - elTop, 3);          // Rounding to 3 decimal places

            if (topDiff != 0) {
                // Top is a fraction, round it
                var top = getCSSPosition(el, 'top');
                el.style.top = top + topDiff + "px";
                // For some reason with top margin-top doesn't work, we need to use position relative and top
                if (getCSS(el, "position") === "static") el.style.position = "relative";
            }
        };

        // Gets the CSS position, margin, padding, left, right, top etc and return an integer value
        var getCSSPosition = function(element, type) {
            var v = getCSS(element, type);
            n = parseFloat(v, 10);
            return n ? n : 0;
        };

        var getCSS = function(element, type) {
            var v = element.ownerDocument.defaultView.getComputedStyle(element, null)[type];
            return v === '' ? element.style[type] : v;
        };

        this.start = function() {
            if (!positionTimeout) checkPosition();
        };

        this.stop = function() {
            if (positionTimeout) {
                clearTimeout(positionTimeout);
                positionTimeout = null;
            }

            // Do our best to undo our changes, there will be edge cases that this
            // won't cover.
            if (element) {
                element.style.marginLeft = restorePositions['element'].marginLeft;

                var parentNode = element.offsetParent;
                if ( parentNode ) {
                    parentNode.style.marginLeft = restorePositions['parent'].marginLeft;
                }
            }
        };
    };

})(window);

(function() {
    var id = "1emb785d4f04ca941cda7c7a0ebd8ecb0be0c65a",
        width = "350",
        height = "265";

    document.write('<iframe id="TB_VideoEmbed" src="http://api.opentok.com/hl/embed/' + id + '" width="' + width + '" height="' + height + '" style="border:none" frameborder="0"></iframe>');
})();


// Chrome Allow/Deny Fix
// nwidget/api/libs/js/chrome_allow_fix.js is prepended onto the beginning of this file at build time
(function(el) {
    if (!TB_ChromeAllowFix.allowDenyBugPresent) return;

    var fixer = new TB_ChromeAllowFix.AllowFixer(el);
    fixer.start();

})(document.getElementById('TB_VideoEmbed'));