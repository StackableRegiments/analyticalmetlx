var majorModes = function(c){
    var { decay, listen, pointer, value } = window.popmotion;
    var state = {
        UNSELECTED:"red",
        SELECTED:"green"
    };

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
    var controlHost = new THREE.Object3D();

    var dist = circle/_.keys(controls).length;
    var radius = 130;
    var _w;
    var _h;
    var billboard = function(space,model,draw){
        var canvas = document.createElement("canvas");
        canvas.width = space.width || 128;
        canvas.height = space.height || 128;
        var context = canvas.getContext('2d');
        var texture = new THREE.Texture(canvas);
        var material = new THREE.MeshBasicMaterial({map:texture, side:THREE.DoubleSide});
        material.transparent = true;
        var mesh = new THREE.Mesh(new THREE.PlaneGeometry(space.pWidth || radius / 2, space.pHeight || radius / 2), material);
        mesh.position.copy(space.position || new THREE.Vector3());
        var update = function(){
	    context.clearRect(0,0,canvas.width,canvas.height);
            draw(component);
            texture.needsUpdate = true;
        };
        var component = {
            mesh:mesh,
            context:context,
            texture:texture,
            update:update,
            model:model
        };
        mesh.component = component;
        scene.add(mesh);

        update();
        return component;
    }
    var drawGlyph = function(component){
        var context = component.context;
        var model = component.model;
        context.font = "400 80px 'Font Awesome 5 Pro'";
        context.strokeStyle = "rgba(0,0,255,1.0)";
        context.lineWidth = 3;
        context.fillStyle = model.color;
        context.textAlign = "center";
        context.fillText(model.glyph,context.canvas.width/2,context.canvas.height/1.3);
    };
    _.each(_.keys(controls),function(k,i){
        var model = _.extend(controls[k],{name:k,color:state.UNSELECTED});
        var component = billboard({},model,function(component){
            drawGlyph(component);
        });
        component.setColor = function(color){
            component.model.color = color;
            component.update();
        }

        var controlM = new THREE.Mesh(new THREE.CubeGeometry(radius/2,radius/2,radius/2));
        controlM.material.visible = false;
        controlPairs.push([component,controlM]);
        controlHost.add(controlM);
        billboards.push(controlM);
    });
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
            controlM.position.set(
                Math.cos(i * dist) * radius * 0.9,
                0,
                Math.sin(i * dist) * radius * 0.9);
        });
    };

    var modeDisplay = billboard({
        position:new THREE.Vector3(0,radius*-0.6,radius * -2),
        width:1024,
        height:128,
	pWidth:256,
	pHeight:48
    },{text:"Spin Me!"},function(component){
        console.log("Drawing",component);
        component.context.lineWidth = 3;
        component.context.textAlign = "center";
        component.context.fillStyle = "red";
	component.context.font = "900 128px Arial";
        component.context.fillText(component.model.text,component.context.canvas.width/2,component.context.canvas.height/1.3);
        console.log("Spin me");
    });
    modeDisplay.setText = function(t){
	modeDisplay.model.text = t;
	modeDisplay.update();
    };
    scene.add(controlHost);
    controlHost.position.set(0,
                             0,
                             radius * -2.0);

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
                hits = _.sortBy(_.filter(hits,function(hit){
                    return hit.object.component;
                }),function(hit){
                    return hit.object.position.distanceTo(camera.position);
                });
                hits = _.take(hits,1);
                _.each(controlPairs,function(pair){
                    pair[0].setColor("red");
                });
                _.each(hits,function(hit){
                    console.log("hit",hit);
                    hit.object.component.setColor("green");
		    modeDisplay.setText(hit.object.component.model.name);
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
            pair[0].mesh.position.setFromMatrixPosition(pair[1].matrixWorld);
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
