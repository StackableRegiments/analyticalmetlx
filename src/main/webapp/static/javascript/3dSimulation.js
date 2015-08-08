function simulateHistory(containerId,root,width){
    var ss = _.values(standings);
    var min = d3.min(ss);
    var max = d3.max(ss);
    var yScale = d3.scale.linear().domain([min,max]).range([0,200])
    var students = {};
    var student = function(_id){
        var id = clean(_id);
        if(!(id in students)){
            var color = "black";
            var s = standing(id);
            if(s == "D") color = 0x00ff00;
            else if(s == "C") color = 0x0000ff;
            else if(s == "P") color = 0xff0000;
            if(id == "wmck") color = 0xfdd017;
            var side = 20;
            var mesh = new THREE.Mesh(
                new THREE.CubeGeometry(20,20,20),
                new THREE.MeshBasicMaterial({
                    color:color,
                    wireframe:false
                }));
            mesh.position.y = yScale(standings[id]);
            students[id] = mesh;
            scene.add(mesh);
        }
        return students[id];
    };
    var billboard = function(text,size){
        var x = document.createElement( "canvas" );
        var xc = x.getContext("2d");
        x.width = text.length * size;
        x.height = size * 2;
        xc.fillStyle = "white"
        xc.fillRect(0,0,x.width,x.height);
        xc.font = sprintf("%spt arial bold",size);
        xc.fillStyle = "black";
        xc.textAlign = "center";
        xc.fillText(text,x.width/2,x.height/2);

        var xm = new THREE.MeshBasicMaterial( { map: new THREE.Texture( x ) } );
        xm.map.needsUpdate = true;

        var mesh = new THREE.Mesh( new THREE.PlaneGeometry(x.width,x.height), xm );
        mesh.doubleSided = true;
        mesh.updateMatrix();
        return mesh;
    }
    var resetView = function(){
        camera.lookAt(scene.position);
        spotlight.position.copy(camera.position);
        spotlight.rotation.copy(camera.rotation);
    }
    var meta = $("<div />",{
        class:"cameraControl"
    }).appendTo($(containerId));
    var addCameraRails = function(){
        var theta = circRads / _keywords.length;
        var left = function(){
            var x = camera.position.x;
            var z = camera.position.z;
            var newX = x * Math.cos(theta) + z * Math.sin(theta);
            var newZ = z * Math.cos(theta) - x * Math.sin(theta);
            var tween = new TWEEN.Tween(camera.position).to({x:newX,z:newZ},interval).onUpdate(resetView).start();
            _.values(keywords).forEach(function(mesh){
                new TWEEN.Tween(mesh.rotation).to({y:mesh.rotation.y + theta},interval).start();
            });
        }
        var right = function(){
            var x = camera.position.x;
            var z = camera.position.z;
            var newX = x * Math.cos(theta) - z * Math.sin(theta);
            var newZ = z * Math.cos(theta) + x * Math.sin(theta);
            var tween = new TWEEN.Tween(camera.position).to({x:newX,z:newZ},interval).onUpdate(resetView).start();
            _.values(keywords).forEach(function(mesh){
                new TWEEN.Tween(mesh.rotation).to({y:mesh.rotation.y - theta},interval).start();
            });
        }
        var yaw = 200;
        var up = function(){
            new TWEEN.Tween(camera.position).to({y:camera.position.y + yaw},interval).onUpdate(resetView).start()
        }
        var down = function(){
            new TWEEN.Tween(camera.position).to({y:camera.position.y - yaw},interval).onUpdate(resetView).start();
        }
        meta.append($("<input />",{
            type:"button",
            value:"Left",
            click:left
        }));
        meta.append($("<input />",{
            type:"button",
            value:"Right",
            click:right
        }));
        meta.append($("<input />",{
            type:"button",
            value:"Down",
            click:down
        }));
        meta.append($("<input />",{
            type:"button",
            value:"Up",
            click:up
        }))
        $(document).keydown(function(event){
            switch(event.which){
            case 37 : left();
                event.preventDefault();
                break;
            case 39 : right();
                event.preventDefault();
                break;
            case 38 : up();
                event.preventDefault();
                break;
            case 40 : down();
                event.preventDefault();
                break;
            }
        });
        console.log("Added camera rails");
    }

    var height = width;
    var VIEW_ANGLE = 45;
    var NEAR = 0.1;
    var FAR = 10000;
    var camera = new THREE.PerspectiveCamera(VIEW_ANGLE,1.0,NEAR,FAR);
    var renderer = new THREE.WebGLRenderer();
    renderer.shadowMapEnabled = true;
    renderer.shadowMapSoft = true;

    var sceneContainer = $(containerId);

    var scene = new THREE.Scene();
    scene.add(camera);

    var radius = 400;
    var base  = new THREE.Mesh(
        new THREE.CylinderGeometry(radius,radius,10,32,32,false),
        new THREE.MeshLambertMaterial({
            color:0xffffff
        }));
    base.receiveShadow = true;
    scene.add(base)

    camera.position.z = 800;
    camera.position.y = 800;

    camera.lookAt(new THREE.Vector3());

    renderer.setSize(width,height);
    sceneContainer.append(renderer.domElement);

    var week = inferWeek(root);
    var __keywords = keywordObjectives.keywords[week];
    var _keywords = __keywords.terms;
    var keywords = {};
    _keywords.forEach(function(word,i){
        var mesh = billboard(word,20);
        keywords[word] = mesh;
        var angle = circRads * i / _keywords.length;
        mesh.position.x = Math.sin(angle) * radius;
        mesh.position.z = Math.cos(angle) * radius;
        mesh.position.y = 11;
        scene.add(mesh);
    });

    addCameraRails();

    var animationLoop = function(){
        requestAnimationFrame(animationLoop);
        TWEEN.update();
        renderer.render(scene,camera);
    };
    _.defer(animationLoop);

    var narration = genDiv();
    var narrate = function(message){
        narration.prepend($("<div />",{
            text:message
        }));
    }
    var interval = 400;
    var meshes = [];
    var colors = d3.scale.category10();
    var showSemantics = function(){
        var contexts = [presentationTextForWeek(week),notesTextForWeek(week),metlTextForWeek(week),stackTextForWeek(week),lectureTextForWeek(week)];
        var contextIncs = contexts.map(function(context){
            return countIncidences(context,_keywords);
        });
        var radiusMax = d3.max(_.flatten(contextIncs.map(function(inc){
            return _.pluck(inc,"score");
        })));
        var side = 30;
        var radiusScale = d3.scale.log().domain([1,radiusMax]).range([0,radius]);
        var collision = function(mesh){
            var origin = new THREE.Vector2(mesh.position.x,mesh.position.z);
            return _.any(meshes,function(otherMesh){
                var dist = origin.distanceTo(new THREE.Vector2(otherMesh.position.x,otherMesh.position.z));
                return (mesh.position.y == otherMesh.homePosition.y) && (dist < mesh.boundRadius || dist < otherMesh.boundRadius);
            });
        }
        var drop = function(mesh,minimum){
            minimum = minimum || side;
            mesh.position.y = minimum;
            var limit = 100;
            var count = 0;
            while(collision(mesh) && count++ < limit){
                mesh.position.y += side;
            }
            mesh.homePosition = mesh.position.clone();
            scene.add(mesh);
            meshes.push(mesh);
        }
        var cube = function(angle,extent,color,contextIndex){
            var mesh = new THREE.Mesh(new THREE.CubeGeometry(side,side,side),new THREE.MeshLambertMaterial({
                color:color
            }));
            mesh.position.x = Math.sin(angle) * extent;
            mesh.position.z = Math.cos(angle) * extent;
            mesh.altPosition = new THREE.Vector3(mesh.position.x,side + (4 * side * contextIndex),mesh.position.z)
            mesh.castShadow = true;
            return mesh;
        }
        contextIncs.map(function(incs,contextIndex){
            var _color = d3.rgb(colors(contextIndex));
            var color = new THREE.Color().setRGB(_color.r/255,_color.g/255,_color.b/255).getHex();
            incs.map(function(inc,i){
                var angle = circRads * i / _keywords.length;
                var extent = radiusScale(inc.score);
                for(var j = side; j < extent; j += side){
                    drop(cube(angle,j,color,contextIndex));
                }
                drop(cube(angle,extent,color,contextIndex));

                inc.partials.forEach(function(partial){
                    drop(cube(angle,radiusScale(partial),color,contextIndex));
                });
            })
        });
        if("synonyms" in __keywords){
            var synonyms = __keywords.synonyms;
            var wedge = circRads / synonyms.length;
            contexts.forEach(function(context,contextIndex){
                var _color = d3.rgb(colors(contextIndex));
                var color = new THREE.Color().setRGB(_color.r/255,_color.g/255,_color.b/255).getHex();
                synonyms.forEach(function(synonymList,i){
                    var baseAngle = wedge * i;
                    synonymList.forEach(function(synonym,synonymIndex){
                        countIncidences(context,[synonym]).forEach(function(incs){
                            if(incs.score > 0){
                                console.log("Dropping for",synonym,synonymIndex,synonymList)

                                var subAngle = wedge / synonymList.length;
                                var centeringOffset = wedge / 2;
                                var angle = (baseAngle - centeringOffset) + (subAngle * synonymIndex);
                                var extent = radiusScale(radiusMax/2)

                                if(!(synonym in keywords)){
				    var billboardExtent = (radius * 1.2) + (synonymIndex * 40);
                                    var mesh = billboard(synonym,20);
                                    keywords[synonym] = mesh;
                                    mesh.position.x = Math.sin(angle) * billboardExtent;
                                    mesh.position.z = Math.cos(angle) * billboardExtent;
                                    mesh.position.y = 11 * synonymIndex;
                                    scene.add(mesh);
                                }

                                for(var score = 0;score < incs.score;score++){
                                    drop(cube(angle,extent,color,i));
                                }
                            }
                        });
                    });
                });
            });
        }
    }
    var legend = [
        "Lecture source material",
        "Student private notes",
        "MeTL during masterclass",
        "Stack collaboration",
        "Transcribed audio during masterclass"
    ]
    legend.forEach(function(label,i){
        var cont = $("<div />").prependTo($(containerId));
        cont.append($("<span />").css({
            "background-color":colors(i),
            width:px(25),
            height:px(25),
            display:"inline-block",
            "margin-right":"1em"
        }));
        cont.append($("<span />",{
            text:label
        }));
    });
    var join = $("<input />",{
        type:"button",
        value:"Join",
        click:function(){
            join.hide();
            separate.show();
            meshes.forEach(function(mesh){
                new TWEEN.Tween(mesh.position).to(mesh.homePosition,interval).start();
            });
        }
    }).prependTo(meta).hide();

    var separate = $("<input />",{
        type:"button",
        value:"Separate",
        click:function(){
            separate.hide();
            join.show();
            meshes.forEach(function(mesh){
                new TWEEN.Tween(mesh.position).to(mesh.altPosition,interval).start();
            });
        }
    }).prependTo(meta);

    var spotlight = new THREE.SpotLight(0xffffff);
    spotlight.castShadow = true;
    scene.add(spotlight);

    resetView();
    showSemantics();

    $(containerId).parent().append(narration);
}
var circRads = Math.PI * 2;
