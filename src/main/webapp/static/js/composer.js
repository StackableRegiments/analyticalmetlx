var composer = function(){
    var host = $(".scene");
    var w = host.width();
    var h = host.height();

    var renderer = new THREE.WebGLRenderer();
    renderer.autoClear = false;

    renderer.setSize(w,h);
    var dom = renderer.domElement;
    host.append(dom);

    var renderTicks = {};
    var physicsTicks = {};
    var shoots = [];

    var renderShoot = function(shoot){
        renderer.render(shoot.scene,shoot.camera);
        renderer.clearDepth();
    }
    var resizeShoots = function(){
        _.each(shoots,function(shoot){
            shoot.resize(w,h);
        });
    };
    var render = function(){
        _.each(renderTicks,function(f){
            f();
        });
        renderer.clear();
        _.each(shoots,renderShoot);
        requestAnimationFrame(render);
    };
    return {
        renderTicks:renderTicks,
        physicsTicks:physicsTicks,
        shoots:shoots,
        action:function(){
            window.onresize = function(){
                w = host.width();
                h = host.height();
		console.log("Resize",w,h);
		var c = host.find("canvas");
                c.attr("width",w);
                c.attr("height",h);
		c.css({
		    width:""+w+"px",
		    height:""+h+"px"
		});
		var ctx = c[0].getContext("webgl");
		ctx.width = w;
		ctx.height = h;
		ctx.viewport(0,0,w,h);
                resizeShoots(w,h);
            };
            resizeShoots(w,h);
            render();
        }
    }
};
