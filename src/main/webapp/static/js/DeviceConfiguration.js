var DeviceConfiguration = (function(){
    var gutterHeight = 10;
    var gutterWidth = 2;
    var identity = Date.now();
    var currentDevice = "browser";
    var orientation = "landscape";
    var returnCurrentDeviceFunction = function(){
        return currentDevice;
    };
    var allowedToHideHeader = false;
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
	keyboard:false
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
        } else if (navigator.userAgent.match("Trident\/7\.0") != null){
            currentDevice = "IE11+";
        } else {
            currentDevice = "browser";
        }
        //console.log("device:",currentDevice);
    };
    var setDefaultOptions = function(){
        tryToDetermineCurrentDevice();
        $("#absoluteCloseButton").removeClass("closeButton").text("").click(bounceAnd(function(){}));
        $("#applicationMenuButton").show();
        fitFunction = defaultFitFunction;
        try {
            if ("Conversations" in window && "jid" in Conversations.getCurrentConversation()){
                if ("UserSettings" in window && UserSettings.getIsInteractive()){
                    DeviceConfiguration.setHeader(true);
                    DeviceConfiguration.setTools(true);
                    DeviceConfiguration.setSlides(true);
                } else {
                    DeviceConfiguration.setHeader(false);
                    DeviceConfiguration.setTools(false);
                    DeviceConfiguration.setSlides(false);
                }
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
        setSectionVisibility("tools",false);
        setSectionVisibility("slides",false);
        setSectionVisibility("header",false);
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
        return W.getViewportDimensions();
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
            /*
             case "IE11+":
             deviceHeight = window.innerHeight * (screen.logicalYDPI / screen.deviceYDPI);
             deviceWidth = window.innerWidth * (screen.logicalXDPI / screen.deviceXDPI);
             break;
             */
        default:
            //deviceHeight = window.innerHeight;
            //deviceWidth = window.innerWidth;
            deviceHeight = $(window).height();
            deviceWidth = $(window).width();
            break;
        }
        var resultantDimensions = {height:deviceHeight,width:deviceWidth};
        //console.log("gettingDeviceDimensions",resultantDimensions);
        return resultantDimensions;
    };
    var defaultFitFunction = function(){
        customizableFitFunction(sectionsVisible.header,sectionsVisible.tools,sectionsVisible.slides,sectionsVisible.keyboard);
    };
    var fitFunction = defaultFitFunction;
    var projectorFitFunction = function(){customizableFitFunction(false,false,false,false);};
    var customizableFitFunction = function(_showHeader,_showTools,_showSlides,_showKeyboard){
        var showHeader = sectionsVisible.header;
        var showTools = sectionsVisible.tools;
        var showSlides = sectionsVisible.slides;
        var showKeyboard = sectionsVisible.keyboard;
        var toolsColumn = $("#toolsColumn");
        var tools = $("#ribbon").find(".toolbar");
        var subTools = $(".modeSpecificTool");

        var dropPx = function(str){
            try {
                var value = parseInt(str.split("px")[0]);
                return isNaN(value) ? 0 : value;
            } catch(e){
                return 0;
            }
        };
        var marginsFor = function(items){
            return {
                x:_.sum(_.map(items,function(item){
                    var l = dropPx(item.css("margin-left")) + dropPx(item.css("padding-left")) + dropPx(item.css("border"));
                    var r = dropPx(item.css("margin-right")) + dropPx(item.css("padding-right"))  + dropPx(item.css("border"));
                    return l + r;
                })),
                y:_.sum(_.map(items,function(item){
                    var t = dropPx(item.css("margin-top")) + dropPx(item.css("padding-top")) + dropPx(item.css("border"));
                    var b = dropPx(item.css("margin-bottom")) + dropPx(item.css("padding-bottom")) + dropPx(item.css("border"));
                    return t + b;
                }))
            };
        };

        var flexContainer = $("#masterLayout");

        var boardHeader = $("#boardHeader");
        var applicationMenu = $("#applicationMenu");
        var container = $("#boardContainer");
        var boardColumn = $("#boardColumn");

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
                var masterHeader = $("#masterHeader");
                $("#thumbColumnDragHandle").width(DeviceConfiguration.preferredSizes.handles);
                thumbs.width(DeviceConfiguration.preferredSizes.thumbColumn.width);
                thumbs.height(DeviceConfiguration.preferredSizes.thumbColumn.height);

                var bwidth = boardContainer.width();
                var bheight = boardContainer.height();
                var flexDirection = flexContainer.css("flex-direction");
                if (flexDirection == "row"){
                    bwidth = width - toolsColumn.width() - thumbsColumn.width() - marginsFor([toolsColumn,thumbsColumn,boardColumn]).x - gutterWidth;
                    bheight = height - masterHeader.height() - marginsFor([masterHeader,boardColumn]).y - gutterHeight;
                } else {
                    bwidth = $("#masterLayout").width() - marginsFor([boardColumn]).x; 
                    bheight = bwidth - gutterHeight;
		    if(showKeyboard){
			bheight -= DeviceConfiguration.preferredSizes.keyboard;
			//Remove three bars including gutters
			bheight += (DeviceConfiguration.preferredSizes.handles + 2) * 3;
		    }
                }
                if (bheight < 0 || bwidth < 0){
                    throw {
                        message: "retrying because of negativeValues",
                        bheight:bheight,
                        bwidth:bwidth,
                        width:width,
                        height:height,
                        marginsForBoard:marginsFor([boardColumn]),
                        flexDirection:flexDirection
                    };
                }
                bwidth = Math.round(bwidth);
                bheight = Math.round(bheight);
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
            }
            performRemeasure();
            IncludeView.default();
            blit();
        }
        catch(e){
            console.log("exception in fit",e);
            _.defer(function(){
                _.delay(customizableFitFunction,250,(showHeader,showTools,showSlides,showKeyboard));
            });
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
    };
    var initialized = false;
    Progress.onLayoutUpdated["DeviceConfiguration"] = outerFit;
    Progress.historyReceived["DeviceConfiguration_showChrome"] = function(){
        try{
            if("UserSettings" in window && UserSettings.getIsInteractive()){
                //console.log("enabling tools and slides");
                DeviceConfiguration.setSlides(true);
                DeviceConfiguration.setTools(true);
                if(!initialized && "Modes" in window){
                    Modes.draw.activate();
                    if(DeviceConfiguration.getCurrentDevice() == "iPad"){
                        $("#panMode").remove();
                    }
                }
            } else {
                //console.log("disabling because it's not interactive");
                DeviceConfiguration.setSlides(false);
                DeviceConfiguration.setTools(false);
                if(!initialized){
                    Modes.none.activate();
                }
            }
            initialized = true;
        }
        catch(e){
            console.log("Progress.historyReceived.DeviceConfiguration_showChrome",e);
        }
        tryToDetermineCurrentDevice();
        actOnCurrentDevice();
        outerFit();
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
        var originalSize = DeviceConfiguration.preferredSizes.handles;
    });
    var actOnCurrentDevice = function(){
        switch (currentDevice){
        case "browser":
            setDefaultOptions();
            break;
        case "projector":
            if (ContentFilter != undefined && "setFilter" in ContentFilter){
                ContentFilter.setFilter("myPrivate",false);
            };
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
        setKeyboard:function(visible){
	    var chrome = !visible;
	    sectionSetter("keyboard")(visible);
	    DeviceConfiguration.applyFit();
	    $("#masterHeader").toggle(chrome);
	    $(".permission-states").toggle(chrome);
	    $("#majorModesColumn").toggle(chrome);
	},
        toggleHeader:sectionToggler("header"),
        toggleTools:sectionToggler("tools"),
        toggleSlides:sectionToggler("slides"),
        getIdentity:function(){
            return identity;
        },
        resetCurrentDevice:function(){
            tryToDetermineCurrentDevice();
            actOnCurrentDevice();
        },
	hasOnScreenKeyboard:function(){
	    return getDeviceDimensions().width <= 640;
	},
        preferredSizes:{
            handles:50,
            thumbColumn:{
                width:100,
                height:75
            },
            toolsColumn:100,
	    keyboard:236
        }
    };
})();
