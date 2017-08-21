var Profiles = (function(){
	var myProfile = {};
	var myCurrentProfile = {};
	var knownProfiles = {};
	var onProfileUpdatedFuncs = [];
	var getCurrentProfileFunc = function(){
		return myCurrentProfile;
	};
	var getAllKnownProfilesFunc = function(){
		return knownProfiles;
	};
	var getProfileForIdFunc = function(id){
		return knownProfiles[id];
	};
	var receiveActiveProfileFunc = function(prof){
		myCurrentProfile = prof;
		knownProfiles[prof.id] = prof;
		if ("attributes" in prof && "avatarUrl" in prof.attributes){
			$(".currentProfileImage").attr("src",prof.attributes.avatarUrl);
		}
		if ("name" in prof){
			$(".currentProfileName").text(prof.name);
		}
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
				myProfile = candidateMyNewProfile;
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
	MeTLBus.subscribe("receiveCurrentProfile","profiles",function(profile){ //invoked by Lift
		receiveActiveProfileFunc(profile);
	});
	return {
		getCurrentProfile:getCurrentProfileFunc,
		getAllKnownProfiles:getAllKnownProfilesFunc,
		getProfileForId:getProfileForIdFunc,
		getUsernameFor:getUsernameForFunc,
		attachProfileUpdated:attachProfileUpdatedFunc
	};
})();

