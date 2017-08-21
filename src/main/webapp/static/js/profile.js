var Profiles = (function(){
	var myProfile = {};
	var knownProfiles = {};
	var onProfileUpdatedFuncs = [];
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
		_.forEach(onProfileUpdatedFuncs,function(f){
			try {
				f(prof);
			} catch(e){
				console.log("failed to fire func on prof",f,prof);
			}
		});
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
		if (userId in knownProfiles){
			return knownProfiles[userId].name;
		} else {
			return userId;
		}
	};
	var attachProfileUpdatedFunc = function(newFunc){
		onProfileUpdatedFuncs.push(newFunc);
	};

	MeTLBus.subscribe("receiveProfile","profiles",function(profile){ //invoked by Lift
		receiveProfileFunc(profile);
	});
	MeTLBus.subscribe("receiveProfiles","profiles",function(profiles){ //invoked by Lift
		receiveProfilesFunc(profiles);
	});
	return {
		getCurrentProfile:getCurrentProfileFunc,
		getAllKnownProfiles:getAllKnownProfilesFunc,
		getProfileForId:getProfileForIdFunc,
		getUsernameFor:getUsernameForFunc,
		attachProfileUpdated:attachProfileUpdatedFunc
	};
})();

