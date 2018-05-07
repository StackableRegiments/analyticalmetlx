var majorModes = function(c){
    var { decay, listen, pointer, value } = window.popmotion;

    var scene = new THREE.Scene();
    var host = $(".scene");
    var camera = new THREE.PerspectiveCamera( 75, 4/3 , 0.1, 1000 );
    var resize = function(w,h){
        camera.setViewOffset(
            w,h,
	    w/-2,
            h * -3,
            w*4,h*4);
    };
    var billboards = [];

    var circle = Math.PI*2;
    var controls = {
        ink:{},
        image:{},
        settings:{},
        profile:{},
        cam:{},
        select:{},
        summative:{}
    };
    var controlPairs = [];
    var controlHost = new THREE.Object3D();

    var dist = circle/_.keys(controls).length;
    var radius = host.height()/5;
    _.each(_.keys(controls),function(k,i){
        var v = controls[k];
        var controlM = new THREE.Mesh(new THREE.PlaneGeometry());
        var visibleM = new THREE.Mesh(new THREE.PlaneGeometry(radius / 2,radius / 2));
        controlPairs.push([visibleM,controlM]);
        controlM.position.set(Math.cos(i * dist) * radius,0,Math.sin(i * dist) * radius);
        controlHost.add(controlM);

        scene.add(visibleM);
        billboards.push(controlM);
    });
    controlHost.visible = false;
    scene.add(controlHost);
    controlHost.position.z = radius * -1.5;


    var sensor = document.querySelector('.scene');
    var updater = value({x:0}, function(pt){
        controlHost.rotation.y = (pt.x / 600) % circle
    });

    listen(sensor, 'mousedown touchstart')
        .start(() => {
            pointer(updater.get())
                .start(updater);
        });

    listen(document, 'mouseup touchend')
        .start(() => {
            decay({
                from: updater.get().x,
                velocity: updater.getVelocity(),
                power: 0.8,
                timeConstant: 350
            })
                .start(updater);
        });
    c.renderTicks["majorModes"] = function(){
        _.each(controlPairs,function(pair){
            pair[0].position.setFromMatrixPosition(pair[1].matrixWorld);
        });
        _.each(billboards,function(obj){
            obj.lookAt(camera.position);
        });
    };
    c.shoots.push({
        camera:camera,
        scene: scene,
        resize:resize
    });
};
