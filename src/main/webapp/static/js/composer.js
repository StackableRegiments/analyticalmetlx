var composer = (function(){
    var host = $(".scene");
    var w = host.width();
    var h = host.height();

    var renderer = new THREE.WebGLRenderer();
    renderer.autoClear = false;
    renderer.depthTest = false;

    renderer.setSize(w,h);
    var dom = renderer.domElement;
    host.append(dom);

    var renderTicks = {};
    var physicsTicks = {};

    var render = function(){
        _.each(shoots,function(shoot){
            renderer.render(shoot.scene,shoot.camera);
        });
        requestAnimationFrame(render);
    };
    return {
        renderTicks:{},
        physicsTicks:{},
        shoots:[],
        action:function(){
	    console.log("Composer beginning render");
	    render();
        }
    }
})();
