var majorModes = function(c){
    var { decay, listen, pointer, value } = window.popmotion;

    var scene = new THREE.Scene();
    var camera = new THREE.PerspectiveCamera( 75, 4/3 , 0.1, 1000 );

    var billboards = [];

    var circle = Math.PI*2;
    var controls = {
        ink:{
            glyph:'\uF000'
        },
        image:{
            glyph:'\uF047'
        },
        settings:{
            glyph:'\uF200'
        },
        profile:{
            glyph:'\uf303'
        },
        cam:{
            glyph:'\uF303'
        },
        select:{
            glyph:'\uf303'
        },
        summative:{
            glyph:'\uf303'
        }
    };
    var controlPairs = [];
    var surfaces = {};
    var controlHost = new THREE.Object3D();

    var dist = circle/_.keys(controls).length;
    var radius = 130;
    var _w;
    var _h;
    var drawGlyph = function(context){
        context.font = "400 80px 'Font Awesome 5 Pro'";
        context.strokeStyle = "rgba(0,0,255,1.0)";
        context.lineWidth = 3;
        context.fillStyle = context.model.color;
        context.textAlign = "center";
        context.fillText(context.model.glyph,context.canvas.width/2,context.canvas.height/1.3);
    };
    var resize = function(w,h){
        _w = w;
        _h = h;
        radius = Math.min(w,h)/5;
        camera.setViewOffset(
            w,h,
            w * -0.5,
            h * -3,
            w*4,h*4);
        _.each(controlPairs,function(pair,i){
            var controlM = pair[1];
            controlM.position.set(Math.cos(i * dist) * radius,0,Math.sin(i * dist) * radius);
            drawGlyph(surfaces[controlM.uuid]);
        });
    };
    _.each(_.keys(controls),function(k,i){
        var control = controls[k];
        var canvas = document.createElement("canvas");
        canvas.width = 128;
        canvas.height = 128;
        var context = canvas.getContext('2d');
        var v = controls[k];
        context.model = v;
        var texture = new THREE.Texture(canvas);
        var material = new THREE.MeshBasicMaterial({map:texture, side:THREE.DoubleSide});
        material.transparent = true;

        var controlM = new THREE.Mesh(new THREE.CubeGeometry(radius/2,radius/2,radius/2));
        var visibleM = new THREE.Mesh(new THREE.PlaneGeometry(radius / 2,radius / 2,), material);
        controlM.material.visible = false;

        controlPairs.push([visibleM,controlM]);
        controlHost.add(controlM);

        scene.add(visibleM);
        billboards.push(controlM);
        var surface = surfaces[controlM.uuid] = context;
        surface.setColor = function(color){
            v.color = color;
            drawGlyph(surface);
            texture.needsUpdate = true;
        }
        surface.setColor("red");
    });
    scene.add(controlHost);
    controlHost.position.z = radius * -1.5;

    var sensor = document.querySelector('.scene');
    var updater = value({x:0,y:0}, function(pt){
        controlHost.rotation.y = (pt.x / 600) % circle
    });

    var start = {};
    var clickThreshold = 15;
    var drag = false;
    listen(sensor, 'click')
        .start(p => {
            if(!drag){
                var ray = new THREE.Raycaster();
                var _p = {x:p.offsetX/_w,y:p.offsetY/_h};
                _p.x = _p.x * 2 - 1;
                _p.y = -(_p.y * 2) + 1;
                ray.setFromCamera(_p,camera);
                var hits = ray.intersectObjects(scene.children,true);
                hits = _.filter(hits,function(hit){
                    return hit.object.uuid in surfaces;
                });
                hits = _.sortBy(hits,function(hit){
                    return hit.object.position.distanceTo(camera.position);
                });
		hits = _.take(hits,1);
                _.each(_.values(surfaces),function(surface){
                    surface.setColor("red");
                });
                console.log(hits.length);
                _.each(hits,function(hit){
                    var obj = hit.object;
                    obj.position.y = hit.object.position.y + 20;
                    surfaces[obj.uuid].setColor("green");
                });
            }
        });

    listen(sensor, 'mousedown touchstart')
        .start(() => {
            var u = updater.get();
            start.x = u.x;
            start.y = u.y;
            pointer(u).start(updater);
            drag = true;
        });
    listen(document, 'mouseup touchend')
        .start(() => {
            var u = updater.get();
            var end = {
                x:u.x,
                y:u.y
            };
            start.x = start.x || end.x;
            start.y = start.y || end.y;
            var dist = Math.sqrt(Math.pow(end.x-start.x,2)+Math.pow(end.y-start.y,2));
            console.log("dist",dist);
            if(dist <= clickThreshold){
                updater.stop();
                drag = false;
            }
            if(drag){
                decay({
                    from: u.x,
                    velocity: updater.getVelocity(),
                    power: 0.8,
                    timeConstant: 350
                }).start(updater);
            }
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
