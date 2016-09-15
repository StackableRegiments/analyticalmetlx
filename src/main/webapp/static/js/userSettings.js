var UserSettings = (function(){
    var username = "auser";
    var userGroups = [];
    var userOptions = {};

    // userOptions come from the server
    // userPrefs come from the browser
    // maybe we'll have them interact at some point down the line

    var isInteractiveUser = true;

    var setUsernameFunction = function(newName){
        username = newName;
        $("#username").text(username);
        $("#currentUsername").text(username);
    };
    var setUserGroupsFunction = function(newGroups){
        userGroups = newGroups;
    };
    var setUserOptionsFunction = function(newOptions){
        userOptions = newOptions;
    };
    var setIsInteractiveUserFunction = function(isInteractive){
        if (isInteractive != isInteractiveUser){
            isInteractiveUser = isInteractive;
            setIsInteractiveUser(isInteractive);
            if (isInteractiveUser == false){
                DeviceConfiguration.setCurrentDevice("projector");
            } else {
                DeviceConfiguration.resetCurrentDevice();
            }
        }
    };

    var defaultPrefs = {
        toolModeSize:60,
        subModeSize:30,
        thumbnailSize:100,
        loadSlidesAtNativeZoom:false,
        followingTeacherViewbox:false
    };
		var shouldUseLocalStorageFunc = function(){
			return false;
		}
    var setUserPrefFunction = function(prefKey,prefValue){
        if(shouldUseLocalStorageFunc()){
            localStorage[prefKey] = prefValue;
        }
    };
    var getUserPrefFunction = function(prefKey){
        if(shouldUseLocalStorageFunc()){
            return localStorage[prefKey] || defaultPrefs[prefKey];
        }
    };

    Progress.usernameReceived["UserSettings"] = setUsernameFunction;
    Progress.userGroupsReceived["UserSettings"] = setUserGroupsFunction;
    Progress.userOptionsReceived["UserSettings"] = setUserOptionsFunction;
    return {
        getUsername:function(){return username;},
        getUserOptions:function(){return userOptions;},
        getUserGroups:function(){return userGroups;},
        getIsInteractive:function(){return isInteractiveUser == true;},
        setIsInteractive:setIsInteractiveUserFunction,
        getUserPref:getUserPrefFunction,
        setUserPref:setUserPrefFunction
    };
})();

function receiveUsername(newName){
    Progress.call("usernameReceived",[newName]);
}

function receiveUserOptions(newOptions){
    Progress.call("userOptionsReceived",[newOptions]);
}

function receiveUserGroups(newGroups){
    Progress.call("userGroupsReceived",[newGroups]);
}
function receiveIsInteractiveUser(isInteractive){
    UserSettings.setIsInteractive(isInteractive);
}

// these will be injected by lift
//function changeUser(newName) //only to be used in staging
//function getUser()
//function changeUser(username)
//function getUserOptions()
//function setUserOptions(newOptions)
//function getIsInteractiveUser()
//function setIsInteractiveUser(bool)
