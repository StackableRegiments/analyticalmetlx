var thumbNav = function(c){
    var scene = new THREE.Scene();
    var camera = new THREE.PerspectiveCamera( 75, 4/3, 0.1, 1000);
    var resize = function(w,h){
	camera.setViewOffset(
	    w,h,
	    w * 0.5,
	    h * -3,
	    w*4,h*4
	);
    };
    c.shoots.push({
	scene:scene,
	camera:camera,
	resize:resize
    });
};
