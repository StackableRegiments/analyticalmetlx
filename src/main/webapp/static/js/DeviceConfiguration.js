var DeviceConfiguration = (function(){
    var identity = Date.now();
    var currentDevice = "browser";
    var orientation = "landscape";
    var returnCurrentDeviceFunction = function(){
        return currentDevice;
    };
    var allowedToHideHeader = false;
    // the default states of the various sections
    var sectionsVisible = {//All false because the application's start state is the search screen.  onHistory will restore them.
        tools:false,
        slides:false,
        header:false
    };
    var setSectionVisibility = function(section,visible){
        if (currentDevice != "projector"){
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
            DeviceConfiguration.setHeader(true);
            DeviceConfiguration.setTools(true);
            DeviceConfiguration.setSlides(true);
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
            return (navigator.standalone && _.any(_.filter(document.getElementsByTagName("meta"),function(i){return i.name.toLowerCase().trim() == metaName.toLowerCase().trim();}),function(i){return i.content.toLowerCase().trim() == metaValue.toLowerCase().trim();}));
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
    var defaultFitFunction = function(){customizableFitFunction(sectionsVisible.header,sectionsVisible.tools,sectionsVisible.slides);};
    var fitFunction = defaultFitFunction;
    var projectorFitFunction = function(){customizableFitFunction(false,false,false);};
    var customizableFitFunction = function(showHeader,showTools,showSlides){
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

        try{
            var performRemeasure = function(){
                var toolSize = 0;
                var subSize = 0;
                var subSizeOffset = 0;
                var toolWidth = 0;
                var preferredToolModeSize = parseInt(UserSettings.getUserPref("toolModeSize"));
                var xOffset = 0;
                var yOffset = 0;

                if (showHeader == true){
                    boardHeader.show();
                    //boardHeader.width(width);
                    applicationMenu.show();
                    //applicationMenu.width(preferredToolModeSize);
                } else {
                    //boardHeader.width(0);
                    //applicationMenu.width(0);
                    applicationMenu.hide();
                    boardHeader.hide();
                }

                var height = deviceDimensions.height - $(".shrinkWrappedRow").height();
                if (showTools == true){
                    toolSize = preferredToolModeSize;
                    subSize = parseInt(UserSettings.getUserPref("subModeSize"));
                    subSizeOffset = 10; //Woo!  Magic number!  It's the left offset of the submode button
                    toolWidth = Math.max(toolSize,subSize + subSizeOffset);
                    //toolsColumn.width(toolWidth).css("max-width",px(208)).height(height);
                    tools.show();
										subTools.show();
                    //subTools.height(subSize).css("min-width",px(toolWidth - subSizeOffset)).show();
                    toolsColumn.show();
                } else {
                    xOffset += 4;
                    //subTools.height(0).css("min-width",px(0));
                    subTools.hide();
                    tools.hide();
                    //toolsColumn.width(0).height(0);
                    toolsColumn.hide();
                }
                var thumbWidth = 0;
                var thumbHeight = 0;
                if (showSlides == true){
                    thumbsColumn.show();
                    thumbWidth = parseInt(UserSettings.getUserPref("thumbnailSize"));
                    thumbHeight = thumbWidth * 0.75;
                } else {
                    xOffset += 4;
                    thumbsColumn.hide();
                }
                var thumbScrollOffset = 35;
                var thumbContainerWidth = thumbWidth;
                if (thumbWidth > 0){
                    thumbContainerWidth = thumbContainerWidth + thumbScrollOffset;
                }
								/*
                thumbsColumn.width(thumbContainerWidth).height(height).css("display","block");
                thumbScrollContainer.width(thumbContainerWidth).height(height);
                slideContainer.width(thumbContainerWidth).height(height);
                $(".slideButtonContainer").width(thumbWidth).height(thumbHeight).css("margin",px(10));
                thumbs.width(thumbWidth).height(thumbHeight);
                $("#addSlideButton").css("margin",px(10));
								*/
                var gestureWiggleRoomWidth = 0; // magic number to create a bit of wiggle room for the gestures
                var gestureWiggleRoomHeight = 0; // magic number to create a bit of wiggle room for the gesture
                if (currentDevice != "projector"){
                    gestureWiggleRoomHeight = 15;
                    height -= yOffset / 2;
                    width -= xOffset;
                }

                $("#masterLayout").height(height).width(width).css({
                    "margin-left":px(xOffset / 2),
                    "margin-top":px(yOffset / 2)
                });

                var actualToolsWidth = toolsColumn.width();
                var actualThumbsWidth = thumbsColumn.width();
                var padding = 0;
                var containerWidth = width - (actualToolsWidth + actualThumbsWidth + gestureWiggleRoomWidth);
                var containerHeight = height - gestureWiggleRoomHeight;
                $("#notices").height(gestureWiggleRoomHeight);
                var container = $("#boardContainer");

                //$("#boardColumn").width(containerWidth).height(containerHeight);
                //container.width(containerWidth).height(containerHeight);
								/*
                board.attr("width",px(containerWidth - padding));
                board.attr("height",px(containerHeight - padding));
                board.width(containerWidth - padding);
                board.height(containerHeight - padding);
								*/
                boardWidth = containerWidth - padding;
                boardHeight = containerHeight - padding;
								/*
                if ("documentElement" in document){
                    $(document.documentElement).width(deviceDimensions.width).css("min-width",deviceDimensions.width).css("max-width",deviceDimensions.width).height(deviceDimensions.height).css("min-height",deviceDimensions.height).css("max-height",deviceDimensions.height);
                }
                $(document.body).width(deviceDimensions.width).css("min-width",deviceDimensions.width).css("max-width",deviceDimensions.width).height(deviceDimensions.height).css("min-height",deviceDimensions.height).css("max-height",deviceDimensions.height);
                $(document).width(deviceDimensions.width).css("min-width",deviceDimensions.width).css("max-width",deviceDimensions.width).height(deviceDimensions.height).css("min-height",deviceDimensions.height).css("max-height",deviceDimensions.height);
								*/
            }
            performRemeasure();
            //performRemeasure();
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
    Progress.historyReceived.DeviceConfiguration_showChrome = function(){
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
            }
            else{
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
    }

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
            outerFit();
        }));
        $("#slidesToggleButton").on("click",bounceAnd(function(){
            setSectionVisibility("slides",!sectionsVisible.slides);
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
