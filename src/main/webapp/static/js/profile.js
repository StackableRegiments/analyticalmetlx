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
	return {
		getCurrentProfile:getCurrentProfileFunc,
		getAllKnownProfiles:getAllKnownProfilesFunc,
		getProfileForId:getProfileForIdFunc,
		receiveProfile:receiveProfileFunc,
		receiveProfiles:receiveProfilesFunc
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
