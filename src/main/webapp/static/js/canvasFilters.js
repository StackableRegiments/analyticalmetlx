var CanvasFilter = (function(){

	var applyFilter = function(canvas,filterFunc){
		if (filterFunc != undefined){
			var startTime = new Date().getTime();
			var ctx = canvas.getContext("2d");
			var pixels = ctx.getImageData(0,0,canvas.width,canvas.height);
			ctx.putImageData(filterFunc(pixels),0,0);
			console.log("filtered:",new Date().getTime() - startTime);
		}
	};

	var perPixel = function(imageData,rgbaFunc){
		var pixels = imageData.data;
		for (var i=0; i < pixels.length; i += 4) {
			var r = pixels[i];
			var g = pixels[i + 1];
			var b = pixels[i + 2];
			var a = pixels[i + 3];
			var adjusted = rgbaFunc(r,g,b,a);
			pixels[i] = adjusted.r;
			pixels[i + 1] = adjusted.g;
			pixels[i + 2] = adjusted.b;
			pixels[i + 3] = adjusted.a;
		}
		return imageData;	
	};
	var convolute = function(matrix,opaque){
		return function(pixels){
			var side = Math.round(Math.sqrt(matrix.length));
			var halfSide = Math.floor(side / 2);
			var src = pixels.data;
			var sw = pixels.width;
			var sh = pixels.height;
			var w = sw;
			var h = sh;
			var output = $("<canvas/>")[0].getContext("2d").createImageData(w,h);
			var dst = output.data;
			var alphaFac = opaque ? 1 : 0;
			for (var y=0; y<h; y++) {
				for (var x=0; x<w; x++) {
					var sy = y;
					var sx = x;
					var dstOff = (y*w+x)*4;
					var r=0, g=0, b=0, a=0;
					for (var cy=0; cy<side; cy++) {
						for (var cx=0; cx<side; cx++) {
							var scy = sy + cy - halfSide;
							var scx = sx + cx - halfSide;
							if (scy >= 0 && scy < sh && scx >= 0 && scx < sw) {
								var srcOff = (scy*sw+scx)*4;
								var wt = matrix[cy*side+cx];
								r += src[srcOff] * wt;
								g += src[srcOff+1] * wt;
								b += src[srcOff+2] * wt;
								a += src[srcOff+3] * wt;
							}
						}
					}
					dst[dstOff] = r;
					dst[dstOff+1] = g;
					dst[dstOff+2] = b;
					dst[dstOff+3] = a + alphaFac*(255-a);
				}
			}
			return output;
		};
	};


	var combine = function(r,g,b,a){
		return r * 0.2120 + g * 0.7152 + b * 0.0722;
	};
	var greyscaleFunction = function(imageData){
		return perPixel(imageData,function(r,g,b,a){
			var v = combine(r,g,b,a);
			return {
				r:v,
				g:v,
				b:v,
				a:a
			};		
		});
	};
	var adjustToneFunction = function(amount){
		return function(pixels){
			return perPixel(pixels,function(r,g,b,a){
				return {
					r: r + amount,
					g: g + amount,
					b: b + amount,
					a:a
				};
			});
		};
	};
	var thresholdFunction = function(threshold){
		return function(pixels){
			return perPixel(pixels,function(r,g,b,a){
				var v = combine(r,g,b,a) >= threshold ? 255 : 0;
				return {
					r:v,
					g:v,
					b:v,
					a:a
				};
			});
		};
	};
	var adjustColorsFunction = function(red,green,blue){
		var ra = red == undefined ? 1 : red;
		var ga = green == undefined ? 1 : green;
		var ba = blue == undefined ? 1 : blue;
		return function(pixels){
			return perPixel(pixels,function(r,g,b,a){
				return {
					r: r * ra,
					g: g * ga,
					b: b * ba,
					a: a
		 		};
			});		
		};
	};
	var sharpenFunction = function(){
		return convolute([
			0,-1,0,
			-1,5,-1,
			0,-1,0
		]);
	};
	var blurFunction = function(radius){
		if (radius > 0){
			return convolute(Array.apply(null,Array(radius)).map(function(item,i){return 1/radius;}));
		}
	};
	return {
		greyscale:function(canvas){ applyFilter(canvas,greyscaleFunction); },
		threshold:function(canvas,value){ applyFilter(canvas,thresholdFunction(value)); },
		adjustTone:function(canvas,value){ applyFilter(canvas,adjustToneFunction(value)); },
		sharpen:function(canvas){ applyFilter(canvas,sharpenFunction()); },
		blur:function(canvas,radius){ applyFilter(canvas,blurFunction(radius)); },
		adjustColors:function(canvas,r,g,b){ applyFilter(canvas,adjustColorsFunction(r,g,b)); }
	};
})();
