var DeviceConfiguration = (function(){
    var currentDevice = "labs";
    var returnCurrentDeviceFunction = function(){
        return currentDevice;
    };
    var alterCurrentDeviceFunction = function(newDevice){
        currentDevice = newDevice;
        actOnCurrentDevice();
    };
    $(function(){
        tryToDetermineCurrentDevice();
        actOnCurrentDevice();
    });
    var originalFitFunction = fit;
    var tryToDetermineCurrentDevice = function(){
        if ((navigator.userAgent.match(/iPhone/i) != null) || (navigator.userAgent.match(/iPod/i) != null)) {
            currentDevice = "iPhone";
        } else if (navigator.userAgent.match(/iPad/i) != null){
            currentDevice = "iPad";
        } else {
        }
    };
    var setDefaultOptions = function(){
        console.log("Setting default options");
        $("#toolsColumn").show();
        $("#thumbsColumn").show();
        $("#boardHeader").show();
        zoomToPage();
        fit = function(){
            //parts of the original fit function run in a strange order, so shrinkWrappedRow's heights will be wrong the first time around.
            originalFitFunction();
            originalFitFunction();
        };
        fit();
        blit();
    };
    var setProjectorOptions = function(){
        Conversations.enableSyncMove();
        $("#toolsColumn").hide();
        $("#thumbsColumn").hide();
        $("#boardHeader").hide();
        zoomToFit();
        fit = function(){
            originalFitFunction();
            try {
                var height = window.innerHeight - $(".shrinkWrappedRow").height();
                var width = window.innerWidth;
                console.log("projector fit size",width,height);
                $("#masterLayout").height(height).width(width);
                $("#toolsColumn").width(0).height(0);
                $(".toolbar").height(0).css("min-width", 0);
                $(".modeSpecificTool").height(0).css("min-width", 0);
                $("#thumbsColumn").width(0);
                $("#thumbScrollContainer").width(0).height(0);
                $("#slideContainer").width(0);
                $(".thumbnail").width(0).height(0);
                $("#applicationMenu").width(0);
                var container = $("#boardContainer");
                $("#boardHeader").width(0);
                container.height(height);
                container.width(width);
                board.attr("width",px(width));
                board.attr("height",px(height));
                board.width(width);
                board.height(height);
                boardWidth = width;
                boardHeight = height;

            } catch (e){
                console.log("exception in projectorFit function",e);
            }
        };
        fit();
        blit();
    };
    var setIPhoneOptions = function(){
        setDefaultOptions();
    };
    var setIPadOptions = function(){
    };
    var actOnCurrentDevice = function(){
        console.log("Setting up for ",currentDevice);
        switch (currentDevice){
        case "labs":break;
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
    return {
        getCurrentDevice:returnCurrentDeviceFunction,
        setCurrentDevice:alterCurrentDeviceFunction
    };
})();
