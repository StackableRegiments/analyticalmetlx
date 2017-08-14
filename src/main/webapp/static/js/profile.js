var Profiles = (function(){
	console.log("created Profiles:");
	var myProfile = {};
	var knownProfiles = {};
	var getCurrentProfileFunc = function(){
		return myProfile;
	};
	var getAllKnownProfilesFunc = function(){
		return knownProfiles;
	};
	var getProfileForIdFunc = function(id){
		return knownProfiles[id];
	};
	var receiveProfileFunc = function(prof){
		myProfile = prof;
		knownProfiles[prof.id] = prof;
	};
	var receiveProfilesFunc = function(profs){
		knownProfiles = _.merge(myProfile,knownProfiles,profs);
		if ("id" in myProfile){
			var candidateMyNewProfile = knownProfiles[myProfile.id];
			if (candidateMyNewProfile){
				myProfile = candidateNewProfile;
			}
		}
	};
	var getUsernameForFunc = function(userId){
		console.log("looking for user in profiles",userId,knownProfiles);
		if (userId in knownProfiles){
			return knownProfiles[userId].name;
		} else {
			return userId;
		}
	};
	return {
		getCurrentProfile:getCurrentProfileFunc,
		getAllKnownProfiles:getAllKnownProfilesFunc,
		getProfileForId:getProfileForIdFunc,
		receiveProfile:receiveProfileFunc,
		receiveProfiles:receiveProfilesFunc,
		getUsernameFor:getUsernameForFunc
	};
})();

console.log("what the hell!");

function receiveProfile(profile){ //invoked by Lift
	console.log("receiveUserProfile",profile);
	Profiles.receiveProfile(profile);
};
function receiveProfiles(profiles){ //invoked by Lift
	console.log("receiveProfiles",profiles);
	Profiles.receiveProfiles(profiles);
}
