var DeviceConfiguration = (function(){
    var identity = Date.now();
    var currentDevice = "browser";
    var orientation = "landscape";
    var returnCurrentDeviceFunction = function(){
        return currentDevice;
    };
    var allowedToHideHeader = false;
    // the default states of the various sections
		var allowShowingChrome = function(){
			var isInConversation = false;
			try {
				isInConversation = "jid" in Conversations.getCurrentConversation();
			} catch(e) {
				isInConversation = false;
			}
			return currentDevice != "projector" && isInConversation; 
		}
    var sectionsVisible = {//All false because the application's start state is the search screen.  onHistory will restore them.
        tools:false,
        slides:false,
        header:false,
    };
    var setSectionVisibility = function(section,visible){
			if (allowShowingChrome){
					if ((allowedToHideHeader || section != "header") && (visible == true || visible == false)){
							sectionsVisible[section] = visible;
					}
			}
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
        } else {
            currentDevice = "browser";
        }
    };
    var setDefaultOptions = function(){
        tryToDetermineCurrentDevice();
        $("#absoluteCloseButton").removeClass("closeButton").text("").click(bounceAnd(function(){}));
        $("#applicationMenuButton").show();
        fitFunction = defaultFitFunction;
				try {
					if (UserSettings.getIsInteractive() && "jid" in Conversations.getCurrentConversation()){
						DeviceConfiguration.setHeader(true);
						DeviceConfiguration.setTools(true);
						DeviceConfiguration.setSlides(true);
					} else {
						DeviceConfiguration.setHeader(false);
						DeviceConfiguration.setTools(false);
						DeviceConfiguration.setSlides(false);
					}
				} catch(e){
					console.log("error while trying to fix the layout:",e);
				}
        zoomToPage();
        fitFunction();
    };
    var setProjectorOptions = function(){
        Conversations.enableSyncMove();
        UserSettings.setIsInteractive(false);
        currentDevice = "projector";
        fitFunction = projectorFitFunction;
        zoomToFit();
        $("#absoluteCloseButton").addClass("closeButton").text("X").click(bounceAnd(function(){
					UserSettings.setIsInteractive(true);
					setDefaultOptions();
        }));
        $("#applicationMenuButton").hide();
        Modes.none.activate();
        fitFunction();
    };
    var setIPhoneOptions = function(){
        setDefaultOptions();
    };
    var setIPadOptions = function(){
        setDefaultOptions();
    };
    var getDeviceDimensions = function(){
        var deviceHeight = 0;
        var deviceWidth = 0;
        var matchMetaTag = function(metaName,metaValue){
            return (navigator.standalone && _.some(_.filter(document.getElementsByTagName("meta"),function(i){return i.name.toLowerCase().trim() == metaName.toLowerCase().trim();}),function(i){return i.content.toLowerCase().trim() == metaValue.toLowerCase().trim();}));
        }
        switch (currentDevice){
        case "iPhone":
            deviceHeight += screen.height;
            deviceWidth += screen.width;
            if (window.orientation && (window.orientation == 90 || window.orientation == 270 || window.orientation == -90 || window.orientation == -270)){
                orientation = "landscape";
                var temp = deviceHeight;
                deviceHeight = deviceWidth;
                deviceWidth = temp;
            } else if (window.orientation && (window.orientation == 0 || window.orientation == 180 || window.orientation == -180 || window.orientation == 360 || window.orientation == -360)){
                orientation = "portrait";
            }
            if (!(matchMetaTag("apple-mobile-web-app-capable","yes"))){
                if (window.innerHeight > window.innerWidth){
                    deviceHeight -= 44;
                }       else {
                    deviceHeight -= 32;
                }
            }
            if (!(matchMetaTag("apple-mobile-web-app-status-bar-style","black-translucent"))){
                deviceHeight -= 20;
            }
            // as additional gesture wiggle-room
            deviceHeight -= 20;
            break;
        case "iPad":
            deviceHeight += screen.height;
            deviceWidth += screen.width;
            if (window.orientation && (window.orientation == 90 || window.orientation == 270 || window.orientation == -90 || window.orientation == -270)){
                orientation = "landscape";
                var temp = deviceHeight;
                deviceHeight = deviceWidth;
                deviceWidth = temp;
            } else if (window.orientation && (window.orientation == 0 || window.orientation == 180 || window.orientation == -180 || window.orientation == 360 || window.orientation == -360)){
                orientation = "portrait";
            }
            if (window.innerHeight == 768 && window.innerWidth == 1024){
                orientation = "landscape";
            } else if (window.innerWidth == 768 && window.innerHeight == 1024){
                orientation = "portrait";
            }
            if (!(matchMetaTag("apple-mobile-web-app-capable","yes"))){
                deviceHeight -= 58;
                // as additional gesture wiggle-room
                deviceHeight -= 20;
            }
            if (!(matchMetaTag("apple-mobile-web-app-status-bar-style","black-translucent"))){
                deviceHeight -= 20;
            }
            break;
        default:
            deviceHeight = window.innerHeight;
            deviceWidth = window.innerWidth;
            break;
        }
        return {height:deviceHeight,width:deviceWidth};
    };
    var defaultFitFunction = function(){
			customizableFitFunction(sectionsVisible.header,sectionsVisible.tools,sectionsVisible.slides);
		};
    var fitFunction = defaultFitFunction;
    var projectorFitFunction = function(){customizableFitFunction(false,false,false);};
    var customizableFitFunction = function(showHeader,showTools,showSlides){
			console.log("refiring fit:",showHeader,showTools,showSlides);
        var toolsColumn = $("#toolsColumn");
        var tools = $("#ribbon").find(".toolbar");
        var subTools = $(".modeSpecificTool");

        var boardHeader = $("#boardHeader");
        var applicationMenu = $("#applicationMenu");
        var container = $("#boardContainer");

        var thumbsColumn = $("#thumbsColumn");
        var slideContainer = $("#slideContainer");
        var thumbScrollContainer = $("#thumbScrollContainer");
        var thumbs = $(".thumbnail");

        var deviceDimensions = getDeviceDimensions();
        var width = deviceDimensions.width;
				var height = deviceDimensions.height;
        try{
            var performRemeasure = function(){
                if (showHeader == true){
                    boardHeader.show();
                    applicationMenu.show();
                } else {
                    applicationMenu.hide();
                    boardHeader.hide();
                }

                if (showTools == true){
                    tools.show();
										subTools.show();
                    toolsColumn.show();
                } else {
                    subTools.hide();
                    tools.hide();
                    toolsColumn.hide();
                }
                if (showSlides == true){
                    thumbsColumn.show();
                } else {
                    thumbsColumn.hide();
                }
								var boardContainer = $("#boardContainer");
								var board = $("#board");
								//var bwidth = board.width();
								//var bheight = board.height();
								var bwidth = boardContainer.width();
								var bheight = boardContainer.height();
								var selectionAdorner = $("#selectionAdorner");
								var radar = $("#radar");
								var marquee = $("#marquee");									
								var textAdorner = $("#textAdorner");
								var imageAdorner = $("#imageAdorner");
								boardContext.canvas.width = bwidth;
								boardContext.canvas.height = bheight;
								boardContext.width = bwidth;
								boardContext.height = bheight;
								boardWidth = bwidth; 
								boardHeight = bheight;
								console.log("refiring fit:",bwidth,bheight);
            }
            performRemeasure();
            IncludeView.default();
        }
        catch(e){
            console.log("exception in fit",e);
        }
        window.scrollTo(1,1);
        window.scrollTo(0,0);
    };
    var innerFit = function(){
        if (fitFunction){
            fitFunction();
        }
    }
    var outerFit = function(){
        innerFit();
        //                      _.throttle(_.debounce(innerFit,150,false),100)();
    };
    var initialized = false;
    Progress.onLayoutUpdated["DeviceConfiguration"] = outerFit;
    Progress.historyReceived["DeviceConfiguration_showChrome"] = function(){
        try{
            if(UserSettings.getIsInteractive()){
							DeviceConfiguration.setSlides(true);
							DeviceConfiguration.setTools(true);
							if(!initialized){
									Modes.draw.activate();
									if(DeviceConfiguration.getCurrentDevice() == "iPad"){
											$("#panMode").remove();
									}
							}
            } else {
							DeviceConfiguration.setSlides(false);
							DeviceConfiguration.setTools(false);
							if(!initialized){
									Modes.none.activate();
							}
            }
            initialized = true;
        }
        catch(e){
            console.log("Progress.historyRedceived.DeviceConfiguration_showChrome",e);
        }
				tryToDetermineCurrentDevice();
				actOnCurrentDevice();
    }

		var updateToolsToggleButton = function(){
			var button = $("#slidesToggleButton");
			if (sectionsVisible.slides){
				button.removeClass("disabledButton");
			} else {
				button.addClass("disabledButton");
			}
		};
		var updateSlidesToggleButton = function(){
			var button = $("#toolsToggleButton");
			if (sectionsVisible.tools){
				button.removeClass("disabledButton");
			} else {
				button.addClass("disabledButton");
			}
		};
			
    $(function(){
        // set up orientation and resize handlers
        var w = $(window);
        if (window.orientation){
            w.on("orientationchange",outerFit);
        }
        w.resize(outerFit);
        /* $.get(
         //this is a quick and dirty workaround to ensure that the devices log their useragent in a stateful manner on lift's end
         "/logDevice",
         {},
         function(data){
         }
         );
         */
        $("#toolsToggleButton").on("click",bounceAnd(function(){
            setSectionVisibility("tools",!sectionsVisible.tools);
						updateToolsToggleButton();
            outerFit();
        }));
        $("#slidesToggleButton").on("click",bounceAnd(function(){
            setSectionVisibility("slides",!sectionsVisible.slides);
						updateSlidesToggleButton();
            outerFit();
        }));
    });
    var actOnCurrentDevice = function(){
        switch (currentDevice){
        case "browser":
            setDefaultOptions();
            break;
        case "projector":
            setProjectorOptions();
            break;
        case "iPhone":
            setIPhoneOptions();
            break;
        case "iPad":
            setIPadOptions();
            break;
        default:
            setDefaultOptions();
            break;
        }
    };
    var defaultFitIfMissing = function(){
        if(!fitFunction){
            fitFunction = defaultFitFunction;
        }
    }
    var sectionToggler = function(section){
        return function(){
            setSectionVisibility(section,!sectionsVisible[section]);
            defaultFitIfMissing();
        };
    }
    var sectionSetter = function(section){
        return function(state){
            setSectionVisibility(section,state);
            defaultFitIfMissing();
        }
    }
    return {
        getCurrentDevice:returnCurrentDeviceFunction,
        setCurrentDevice:alterCurrentDeviceFunction,
        applyFit:function(){
            defaultFitIfMissing();
            fitFunction();
        },
        tempFit:function(showHeader,showTools,showSlides){
            setSectionVisibility("header",showHeader);
            setSectionVisibility("tools",showTools);
            setSectionVisibility("slides",showSlides);
            defaultFitIfMissing();
            fitFunction();
        },
        setHeader:sectionSetter("header"),
        setTools:sectionSetter("tools"),
        setSlides:sectionSetter("slides"),
        toggleHeader:sectionToggler("header"),
        toggleTools:sectionToggler("tools"),
        toggleSlides:sectionToggler("slides"),
        getIdentity:function(){
            return identity;
        },
        resetCurrentDevice:function(){
            tryToDetermineCurrentDevice();
            actOnCurrentDevice();
        }
    };
})();
