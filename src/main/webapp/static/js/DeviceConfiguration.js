var DeviceConfiguration = (function(){
    var gutterHeight = 10;
    var gutterWidth = 2;
    var identity = Date.now();
    var currentDevice = "browser";
    var orientation = "landscape";
    var px = function(i){
        return sprintf("%spx",i);
    }
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
        keyboard:false,
        footer:false
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
                    DeviceConfiguration.setFooter(true);
                } else {
                    DeviceConfiguration.setHeader(false);
                    DeviceConfiguration.setTools(false);
                    DeviceConfiguration.setSlides(false);
                    DeviceConfiguration.setFooter(false);
                }
            }
        } catch(e){
            console.log("error while trying to fix the layout:",e);
        }
        zoomToPage(true);
        fitFunction();
    };
    var setProjectorOptions = function(){
        Conversations.enableSyncMove();
        UserSettings.setIsInteractive(false);
        currentDevice = "projector";
        setSectionVisibility("tools",false);
        setSectionVisibility("slides",false);
        setSectionVisibility("header",false);
        setSectionVisibility("footer",false);
        fitFunction = projectorFitFunction;
        zoomToFit(true);
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
        if(!w || !h || w > screen_width || h > screen_height || w == 980) {
            w = window.outerWidth;
            h = window.outerHeight;
        }
        if(!w || !h || w > screen_width || h > screen_height) {
            w = screen.availWidth;
            h = screen.availHeight;
        }
        return {width: w, height: h};
    };
    var defaultFitFunction = function(){
        customizableFitFunction(sectionsVisible.header,sectionsVisible.tools,sectionsVisible.slides,sectionsVisible.keyboard);
    };
    var fitFunction = defaultFitFunction;
    var projectorFitFunction = function(){customizableFitFunction(false,false,false,false);};
    var dropPx = function(str){
        var value = parseInt(str.split("px")[0]);
        return isNaN(value) ? 0 : value;
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
    var components = {};
    var comp = function(selector){
        if(!(selector in components)){
            var val = $(selector);
            if(val && val.length){
                components[selector] = val;
            }
            return val;
        }
        return components[selector];
    };
    var customizableFitFunction = function(_showHeader,_showTools,_showSlides,_showKeyboard){
        if(typeof(boardContext) != "undefined"){
            var showHeader = sectionsVisible.header;
            var showTools = sectionsVisible.tools;
            var showSlides = sectionsVisible.slides;
            var showKeyboard = sectionsVisible.keyboard;
            var showFooter = sectionsVisible.footer;
            var toolsColumn = comp("#toolsColumn");
            var tools = comp("#ribbon").find(".toolbar");
            var subTools = comp(".modeSpecificTool");

            var flexContainer = comp("#masterLayout");

            var applicationMenu = comp("#applicationMenu");
            var container = comp("#boardContainer");
            var boardColumn = comp("#boardColumn");

            var thumbsColumn = comp("#thumbsColumn");
            var slideContainer = comp("#slideContainer");
            var thumbScrollContainer = comp("#thumbScrollContainer");
            var thumbs = $(".thumbnail");
            var slideControls = comp("#slideControls");

            var masterFooter = comp("#masterFooter");

            var deviceDimensions = getDeviceDimensions();
            var width = deviceDimensions.width;
            var height = deviceDimensions.height;

            var performRemeasure = function(){
                if (showHeader == true){
                    applicationMenu.show();
                } else {
                    applicationMenu.hide();
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
                if(Modes.currentMode == Modes.select){
                    Modes.select.updateAdministerContentVisualState(Conversations.getCurrentConversation());
                }
                if (showSlides == true){
                    thumbsColumn.show();
                    slideControls.show()
                } else {
                    thumbsColumn.hide();
                    slideControls.hide();
                }
                if (showFooter == true){
                    masterFooter.show();
                } else {
                    masterFooter.hide();
                }
                var boardContainer = comp("#boardContainer");
                var board = comp("#board");
                var masterHeader = comp("#masterHeader");
                var headerHeight = masterHeader.height();
                var footerHeight = masterFooter.height();
                DeviceConfiguration.headerHeight = headerHeight;
                DeviceConfiguration.footerHeight = footerHeight;
                thumbs.attr("width",px(comp("#thumbColumnWidth").val()));
                thumbs.attr("height",px(showSlides ? DeviceConfiguration.preferredSizes.thumbColumn.height : 0));

                var bwidth = boardContainer.width();
                var bheight = boardContainer.height();
                var flexDirection = flexContainer.css("flex-direction");
                if (flexDirection == "row"){
                    bwidth = width - (showTools ? toolsColumn.width() : 0) - (showSlides ? thumbsColumn.width(): 0) - marginsFor([toolsColumn,thumbsColumn,boardColumn]).x - gutterWidth;
                    bheight = height - headerHeight - footerHeight - marginsFor([masterHeader,masterFooter,boardColumn]).y;
                } else {
                    bwidth = comp("#masterLayout").width() - marginsFor([boardColumn]).x;
                    bheight = bheight - gutterHeight;
                    if(showKeyboard){
                        var keyboardSize = (currentDevice == "iPad"? DeviceConfiguration.preferredSizes.keyboard.iphone : DeviceConfiguration.preferredSizes.keyboard.ipad);
                        bheight -= bwidth;
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
                var selectionAdorner = comp("#selectionAdorner");
                var radar = comp("#radar");
                var marquee = comp("#marquee");
                var textAdorner = comp("#textAdorner");
                var imageAdorner = comp("#imageAdorner");
                masterFooter.width(width - thumbsColumn.width() - toolsColumn.width() - gutterWidth * 10).css({
                    "margin-left":sprintf("%spx",toolsColumn.width() + gutterWidth * 4.5)
                });
                board.width(bwidth);
                board.height(bheight);
                toolsColumn.height(bheight - headerHeight);
                thumbsColumn.height(bheight - headerHeight);
                boardColumn.height(bheight);

                boardContext.canvas.width = bwidth;
                boardContext.canvas.height = bheight;
                boardContext.width = bwidth;
                boardContext.height = bheight;
                boardWidth = bwidth;
                boardHeight = bheight;
            };
            performRemeasure();
            IncludeView.default();
            blit();
            window.scrollTo(1,1);
            window.scrollTo(0,0);
        }
    };
    var innerFit = function(){
        if (fitFunction){
            fitFunction();
        }
    }
    var outerFit = innerFit;
    var initialized = false;
    Progress.onLayoutUpdated["DeviceConfiguration"] = outerFit;
    Progress.historyReceived["DeviceConfiguration_showChrome"] = function(){
        try{
            if("UserSettings" in window && UserSettings.getIsInteractive()){
                DeviceConfiguration.setSlides(true);
                DeviceConfiguration.setTools(true);
                if(!initialized && "Modes" in window){
                    Modes.select.activate();
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
            console.log("Progress.historyReceived.DeviceConfiguration_showChrome",e);
        }
        tryToDetermineCurrentDevice();
        actOnCurrentDevice();
        outerFit();
    };

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
        $("#thumbColumnWidth")
            .val(DeviceConfiguration.preferredSizes.thumbColumn.width)
            .on("input change",function(){
                var newValue = comp("#thumbColumnWidth").val();
                $(".thumbnail:not(.groupSlide)")
                    .width(newValue)
                    .height(newValue * 0.75);
                fitFunction();
                Conversations.refreshSlideDisplay();
            });
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
        tempFit:function(showHeader,showTools,showSlides,showFooter){
            setSectionVisibility("header",showHeader);
            setSectionVisibility("tools",showTools);
            setSectionVisibility("slides",showSlides);
            setSectionVisibility("footer",showFooter);
            defaultFitIfMissing();
            fitFunction();
        },
        setHeader:sectionSetter("header"),
        setTools:sectionSetter("tools"),
        setFooter:sectionSetter("footer"),
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
        toggleFooter:sectionToggler("footer"),
        toggleSlides:sectionToggler("slides"),
        getIdentity:function(){
            return identity;
        },
        resetCurrentDevice:function(){
            tryToDetermineCurrentDevice();
            actOnCurrentDevice();
        },
        hasOnScreenKeyboard:function(){
            return getDeviceDimensions().width <= 640 || currentDevice == "iPad";
        },
        preferredSizes:{
            handles:50,
            thumbColumn:{
                width:100,
                height:75
            },
            toolsColumn:100,
            keyboard:{
                iphone:236,
                ipad:352
            }
        }
    };
})();
