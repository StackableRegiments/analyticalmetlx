var Vidyo = (function(){
	function onVidyoClientLoaded(status) {
		console.log("Status: " + status.state + "Description: " + status.description);
		switch (status.state) {
			case "READY":    // The library is operating normally
				$("#connectionStatus").html("Ready");
				$("#helper").addClass("hidden");
				// After the VidyoClient is successfully initialized a global VC object will become available 
				// All of the VidyoConnector gui and logic is implemented in VidyoConnector.js
				StartVidyoConnector(VC);
				break;
			case "RETRYING": // The library operating is temporarily paused
				$("#connectionStatus").html("Temporarily unavailable retrying in " + status.nextTimeout/1000 + " seconds");
				break;
			case "FAILED":   // The library operating has stopped
				ShowFailed(status); 
				$("#connectionStatus").html("Failed: " + status.description);
				break;
			case "FAILEDVERSION":   // The library operating has stopped
				UpdateHelperPaths(status); 
				ShowFailedVersion(status); 
				$("#connectionStatus").html("Failed: " + status.description);
				break;
			case "NOTAVAILABLE": // The library is not available
				UpdateHelperPaths(status); 
				$("#connectionStatus").html(status.description);
				break;
		}
		return true; // Return true to reload the plugins if not available
	}
	function UpdateHelperPaths(status) {
		$("#helperPlugInDownload").attr("href", status.downloadPathPlugIn);
		$("#helperAppDownload").attr("href", status.downloadPathApp);
	}
	function ShowFailed(status) {
		var helperText = '';	
		 // Display the error
		helperText += '<h2>An error occurred, please reload</h2>';
		helperText += '<p>' + status.description + '</p>';
		
		$("#helperText").html(helperText);
		$("#failedText").html(helperText);
		$("#failed").removeClass("hidden");	
	}
	function ShowFailedVersion(status) {
		var helperText = '';	
		 // Display the error
		helperText += '<h4>Please Download a new plugIn and restart the browser</h4>';
		helperText += '<p>' + status.description + '</p>';
		
		$("#helperText").html(helperText);
	}
	
	function loadVidyoClientLibrary(webrtc, plugin) {
		//We need to ensure we're loading the VidyoClient library and listening for the callback.
		var script = document.createElement('script');
		script.type = 'text/javascript';
		script.src = 'https://static.vidyo.io/4.1.8.1/javascript/VidyoClient/VidyoClient.js?onload=onVidyoClientLoaded&webrtc=' + webrtc + '&plugin=' + plugin;    
		document.getElementsByTagName('head')[0].appendChild(script);
	}
	function joinViaBrowser() {
		$("#helperText").html("Loading...");
		$("#helperPicker").addClass("hidden");
		loadVidyoClientLibrary(true, false);
	}
	
	function joinViaPlugIn() {
		$("#helperText").html("Don't have the PlugIn?");
		$("#helperPicker").addClass("hidden");
		$("#helperPlugIn").removeClass("hidden");
		loadVidyoClientLibrary(false, true);
	}
	
	function joinViaApp() {
		$("#helperText").html("Don't have the app?");
		$("#helperPicker").addClass("hidden");
		$("#helperApp").removeClass("hidden");
		var protocolHandlerLink = 'vidyoconnector://' + window.location.search;
		/* launch */
		$("#helperAppLoader").attr('src', protocolHandlerLink);
		loadVidyoClientLibrary(false, false);
	}
	
	function joinViaOtherApp() {
		$("#helperText").html("Don't have the app?");
		$("#helperPicker").addClass("hidden");
		$("#helperOtherApp").removeClass("hidden");
		var protocolHandlerLink = 'vidyoconnector://' + window.location.search;
		/* launch */
		$("#helperOtherAppLoader").attr('src', protocolHandlerLink);
		loadVidyoClientLibrary(false, false);
	}
	
	function loadHelperOptions() {
		var userAgent = navigator.userAgent || navigator.vendor || window.opera;

		// Opera 8.0+
		var isOpera = (userAgent.indexOf("Opera") || userAgent.indexOf('OPR')) != -1 ;
		// Firefox
		var isFirefox = userAgent.indexOf("Firefox") != -1;
		// Chrome 1+
		var isChrome = userAgent.indexOf("Chrome") != -1;
		// Safari 
		var isSafari = !isChrome && userAgent.indexOf("Safari") != -1;
		// AppleWebKit 
		var isAppleWebKit = !isSafari && !isChrome && userAgent.indexOf("AppleWebKit") != -1;
		// Internet Explorer 6-11
		var isIE = (userAgent.indexOf("MSIE") != -1 ) || (!!document.documentMode == true );
		// Edge 20+
		var isEdge = !isIE && !!window.StyleMedia;
		// Check if Mac
		var isMac = navigator.platform.indexOf('Mac') > -1;
		// Check if Windows
		var isWin = navigator.platform.indexOf('Win') > -1;
		// Check if Linux
		var isLinux = navigator.platform.indexOf('Linux') > -1;
		// Check if Android
		var isAndroid = userAgent.indexOf("android") > -1;

		if (!isMac && !isWin && !isLinux) {
			/* Mobile App*/
			if (isAndroid) {
				$("#joinViaApp").removeClass("hidden");
			} else {
				$("#joinViaOtherApp").removeClass("hidden");
			}
			if (isChrome) {
				/* Supports WebRTC */
				$("#joinViaBrowser").removeClass("hidden");
			}
		} else {
			/* Desktop App */
			$("#joinViaApp").removeClass("hidden");
			
			if (isChrome || isFirefox) {
				/* Supports WebRTC */
				$("#joinViaBrowser").removeClass("hidden");
			}
			if (isSafari || isFirefox || (isAppleWebKit && isMac) || (isIE && !isEdge)) {
				/* Supports Plugins */
				$("#joinViaPlugIn").removeClass("hidden");
			}
		}
	}
	// Runs when the page loads
	$(function() {
		joinViaBrowser();
		/*
		var connectorType = getUrlParameterByName("connectorType");
		if (connectorType == "app") {
			joinViaApp();
		} else if (connectorType == "browser") {
			joinViaBrowser();
		} else if (connectorType == "plugin") {
			joinViaPlugIn();
		} else if (connectorType == "other") {
			joinViaOtherApp();
		} else {
			loadHelperOptions();
		}
		*/
	});
	return {
	};
})();

var receiveVidyoEnabled = function(isEnabled){
	console.log("vidyoEnabled:",isEnabled);
}
var receiveVidyoSessionToken = function(sessionToken){
	console.log("vidyoSessionToken:",sessionToken);
}

//injected by lift
/*
var getVidyoSession = function(roomId){
	receiveVidyoSessionToken(token);
}
*/
