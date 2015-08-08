username = "auser";
userGroups = [];
userOptions = {};

function receiveUsername(newName){
    username = newName;
    $("#username").text(username);
}

function receiveUserOptions(newOptions){
    //console.log("receivingUserOptions: ",newOptions);
    userOptions = newOptions;
}

function receiveUserGroups(newGroups){
    //console.log("receivingUserGroups: ",newGroups);
    userGroups = newGroups;
}

// these will be injected by lift
//function changeUser(newName) //only to be used in staging
//function getUser()
//function changeUser(username)
//function getUserOptions()
//function setUserOptions(newOptions)
