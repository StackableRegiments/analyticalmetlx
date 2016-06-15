function board(container,options){
	var getPref = function(name,defaultValue){
		return (name in options) ? options[name] : defaultValue;
	}
	var boardContext = {};
	var boardContent = {};

	var boardContent = {
			images:{},
			highlighters:{},
			texts:{},
			multiWordTexts:{},
			inks:{}
	};

	var currentSlide = undefined;

	var pressureSimilarityThreshold = getPref("pressureSimilarityThreshold",32),
    viewboxX = 0,
    viewboxY = 0,
    viewboxWidth = 80,
    viewboxHeight = 60,
    contentOffsetX = 0,
    contentOffsetY = 0,
    boardWidth = 0,
    boardHeight = 0;
	
	var renderOffsetX = 0;
	var renderOffsetY = 0;
	var loadSlidesAtNativeZoom;
	var startMark;
	var requestedViewboxX = 0;
	var requestedViewboxY = 0;
	var requestedViewboxWidth = 320;
	var requestedViewboxHeight = 240;
	var precision = getPref("inkCoordinatePrecision",Math.pow(10,3));
	var lineDrawingThreshold = getPref("lineDrawingThreshold",25);

	var maxX = getPref("maxX",2147483647);
	var maxY = getPref("maxY",2147483647);
	var visibleBounds = [];

	var incorporateBoardBounds = function(bounds){
    if (!isNaN(bounds[0])){
        boardContent.minX = Math.min(boardContent.minX,bounds[0]);
    }
    if (!isNaN(bounds[1])){
        boardContent.minY = Math.min(boardContent.minY,bounds[1]);
    }
    if (!isNaN(bounds[2])){
        boardContent.maxX = Math.max(boardContent.maxX,bounds[2]);
    }
    if (!isNaN(bounds[3])){
        boardContent.maxY = Math.max(boardContent.maxY,bounds[3]);
    }
    boardContent.width = boardContent.maxX - boardContent.minX;
    boardContent.height = boardContent.maxY - boardContent.minY;
	}
	var mergeBounds = function(b1,b2){
    var b = {};
    b.minX = Math.min(b1[0],b2[0]);
    b.minY = Math.min(b1[1],b2[1]);
    b.maxX = Math.max(b1[2],b2[2]);
    b.maxY = Math.max(b1[3],b2[3]);
    b.width = b.maxX - b.minX;
    b.height = b.maxY - b.minY;
    b.centerX = b.minX + b.width / 2;
    b.centerY = b.minY + b.height / 2;
    b[0] = b.minX;
    b[1] = b.minY;
    b[2] = b.maxX;
    b[3] = b.maxY;
    return b;
	};
	var boardLimit = 10000;
	var isUsable = function(element){
    var boundsOk = !(_.some(element.bounds,function(p){
        return isNaN(p) || p > boardLimit || p < -boardLimit;
    }));
    var sizeOk = "size" in element? !isNaN(element.size) : true
    var textOk =  "text" in element? element.text.length > 0 : true;
    return boundsOk && sizeOk && textOk;
	};
	var leftPoint = function(xDelta,yDelta,l,x2,y2,bulge){
    var px = yDelta * l * bulge;
    var py = xDelta * l * -bulge;
    return {
        x:px + x2,
        y:py + y2
    }
	}
	var rightPoint = function(xDelta,yDelta,l,x2,y2,bulge){
    var px = yDelta * l * -bulge;
    var py = xDelta * l * bulge;
    return {
        x:px + x2,
        y:py + y2
    }
	}
	var renderHull = function(ink){
    if(ink.points.length < 6){
        return;
    }
    var context = ink.canvas.getContext("2d");
    var b = ink.bounds;
    var minX = Infinity;
    var minY = Infinity;
    var pts = ink.points;
    var ps = [];
    var i;
    var p;
    for(i = 0; i < pts.length; i += 3){
        minX = Math.min(minX,pts[i]);
        minY = Math.min(minY,pts[i+1]);
    }
    minX -= ink.thickness / 2;
    minY -= ink.thickness / 2;
    for(i = 0; i < pts.length; i += 3){
        ps.push(pts[i] - minX);
        ps.push(pts[i+1] - minY);
        ps.push(pts[i+2]);
    }
    var v1,v2,v3,v4;
    var x1,y1,x2,y2;
    var p1,p2;
    var bulge1,bulge2;
    var left = [];
    var right = [];
    try{
        for(i = 0; i < ps.length - 3; i += 3){
            x1 = ps[i];
            y1 = ps[i+1];
            p1 = ps[i+2];
            x2 = ps[i+3];
            y2 = ps[i+4];
            p2 = ps[i+5];
            var xDelta = x2 - x1;
            var yDelta = y2 - y1;
            if(xDelta == 0 || yDelta == 0){}
            else{
                var normalDistance = 1 / Math.sqrt(xDelta*xDelta + yDelta*yDelta);
                bulge1 = ink.thickness / p1 * 64;
                bulge2 = ink.thickness / p2 * 64;
                v1 = leftPoint(xDelta,yDelta,normalDistance,x2,y2,bulge2);
                v2 = rightPoint(xDelta,yDelta,normalDistance,x2,y2,bulge2);
                v3 = leftPoint(-xDelta,-yDelta,normalDistance,x1,y1,bulge1);
                v4 = rightPoint(-xDelta,-yDelta,normalDistance,x1,y1,bulge1);
                left.push(v4);
                left.push(v1);
                right.push(v3);
                right.push(v2);
            }
        }
        var l = ps.length;
        context.fillStyle = ink.color[0];
        context.globalAlpha = ink.color[1] / 255;
        context.beginPath();
        var head = left[0];
        context.moveTo(head.x,head.y);
        for(i = 0; i < left.length; i++){
            p = left[i];
            context.lineTo(p.x,p.y);
        }
        for(i = right.length - 1; i >= 0; i--){
            p = right[i];
            context.lineTo(p.x,p.y);
        }
        context.fill();
        context.closePath();
        /*
         var dot = function(p){
         context.beginPath();
         context.arc(p.x,p.y,5,0,Math.PI*2);
         context.fill();
         context.closePath();
         }
         context.fillStyle = "red";
         left.map(dot);
         context.fillStyle = "blue";
         right.map(dot);
         */
    }
    catch(e){
        console.log("Couldn't render hull for",ink.points);
    }
	}
	var historyReceivedFunction = function(json){
    try{
			var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
        var historyDownloadedMark, prerenderInkMark, prerenderImageMark, prerenderHighlightersMark,prerenderTextMark,imagesLoadedMark,renderMultiWordMark, historyDecoratorsMark, blitMark;
        historyDownloadedMark = Date.now();
        boardContent = json;
        boardContent.minX = 0;
        boardContent.minY = 0;
        boardContent.maxX = boardWidth;
        boardContent.maxY = boardHeight;
        $.each(boardContent.inks,function(i,ink){
            prerenderInk(ink);
        });
        prerenderInkMark = Date.now();
        $.each(boardContent.highlighters,function(i,ink){
            prerenderInk(ink);
        });
        prerenderHighlightersMark = Date.now();
        $.each(boardContent.texts,function(i,text){
            if(isUsable(text)){
                prerenderText(text);
            }
            else{
                delete boardContent.texts[text.id];
            }
        });
        prerenderTextMark = Date.now();
        _.each(boardContent.multiWordTexts,function(text,i){
            Modes.text.editorFor(text).doc.load(text.words);
        });
        renderMultiWordMark = Date.now();

        boardContent.width = boardContent.maxX - boardContent.minX;
        boardContent.height = boardContent.maxY - boardContent.minY;
        var startRender = function(){
					console.log("rendering:",json,boardContent,canvasContext);
            imagesLoadedMark = Date.now();
            Progress.call("historyReceived",[json]);
            historyDecoratorsMark = Date.now();
            if(boardContent.minX == Infinity){
                boardContent.minX = 0;
            }
            if (boardContent.minY == Infinity){
                boardContent.minY = 0;
            }
            if(loadSlidesAtNativeZoom){
                requestedViewboxX = 0;
                requestedViewboxY = 0;
                requestedViewboxWidth = boardWidth;
                requestedViewboxHeight = boardHeight;
                IncludeView.default();
            }
            else{
                requestedViewboxX = boardContent.minX;
                requestedViewboxY = boardContent.minY;
                requestedViewboxWidth = boardContent.width;
                requestedViewboxHeight = boardContent.height;
                IncludeView.default();
            }
						console.log("startRender",requestedViewboxX,requestedViewboxY,requestedViewboxWidth,requestedViewboxHeight);
            hideBackstage();
						clearBoard(canvasContext,{x:0,y:0,w:boardWidth,h:boardHeight});
            render(boardContent,canvasContext);
            blitMark = Date.now();
						if (afterFunc != undefined){
							afterFunc();
						}
        }
        if(_.keys(boardContent.images).length == 0){
            _.defer(startRender);
        }
        else{
            var loaded = 0;
            var limit = _.keys(boardContent.images).length;
            $.each(boardContent.images,function(i,image){
                image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
                incorporateBoardBounds(image.bounds);
                var dataImage = new Image();
                image.imageData = dataImage;
                var url = calculateImageSource(image,true);
                dataImage.onload = function(data){
                    var shouldReCalcBounds = false;
                    if(image.width == 0){
                        image.width = dataImage.naturalWidth;
                        shouldReCalcBounds = true;
                    }
                    if(image.height == 0){
                        image.height = dataImage.naturalHeight;
                        shouldReCalcBounds = true;
                    }
                    if(shouldReCalcBounds){
                        image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
                        incorporateBoardBounds(image.bounds);
                    }
                    loaded += 1;
                    prerenderImage(image);
                    if(loaded >= limit){
                        _.defer(startRender);
                    }
                };
                dataImage.onerror = function(e){
                    console.log("Data image load error",image,e);
                    limit -= 1;
                    console.log(sprintf("Preloaded %s/%s images",loaded,limit));
                    if(loaded >= limit){
                        _.defer(startRender);
                    }
                }
                dataImage.src = url;
            });
        }
    }
    catch(e){
        console.log("receiveHistory exception",e);
    }
		/*
		 //disabling until I work out how the interaction works.
		if (!UserSettings.getIsInteractive()){
			//projector mode should always start viewing the entire slide
			zoomToFit();
		}
		*/
	};

	var determineCanvasConstants = _.once(function(){
		return {x:maxX,y:maxY};
	};
	var determineScaling = function(inX,inY){
    var outputX = inX;
    var outputY = inY;
    var outputScaleX = 1.0;
    var outputScaleY = 1.0;
    var canvasConstants = determineCanvasConstants();
    var maxX = canvasConstants.x;
    var maxY = canvasConstants.y;
    if (inX > maxX){
        outputScaleX = maxX / inX;
        outputX = inX * outputScaleX;
    }
    if (inY > maxY){
        outputScaleY = maxY / inY;
        outputY = inY * outputScaleY;
    }
    return {
        width:outputX,
        height:outputY,
        scaleX:outputScaleX,
        scaleY:outputScaleY,
    };
	}
	var prerenderInk = function(ink){
    if(!isUsable(ink)){
        if(ink.identity in boardContent.inks){
            delete boardContent.inks[ink.identity];
        }
        if(ink.identity in boardContent.highlighters){
            delete boardContent.highlighters[ink.identity];
        }
        return false;
    }
    calculateInkBounds(ink);
    incorporateBoardBounds(ink.bounds);
    var canvas = $("<canvas />")[0];
    ink.canvas = canvas;
    var context = canvas.getContext("2d");
    var privacyOffset = 0;
    var isPrivate = ink.privacy.toUpperCase() == "PRIVATE";
    if(isPrivate){
        privacyOffset = 3;
    }
    var rawWidth = ink.bounds[2] - ink.bounds[0] + ink.thickness + privacyOffset * 2;
    var rawHeight = ink.bounds[3] - ink.bounds[1] + ink.thickness + privacyOffset * 2;

    var scaleMeasurements = determineScaling(rawWidth,rawHeight);
    canvas.width = scaleMeasurements.width;
    canvas.height = scaleMeasurements.height;
    $(canvas).css({
        width:px(rawWidth),
        height:px(rawHeight)
    });
    var rawPoints = ink.points;
    var points = [];
    for (p = 0; p < rawPoints.length; p += 3){
        points.push(rawPoints[p] * scaleMeasurements.scaleX);
        points.push(rawPoints[p + 1] * scaleMeasurements.scaleY);
        points.push(rawPoints[p + 2]);
    }
    var contentOffsetX = -1 * ((ink.minX - ink.thickness / 2) - privacyOffset) * scaleMeasurements.scaleX;
    var contentOffsetY = -1 * ((ink.minY - ink.thickness / 2) - privacyOffset) * scaleMeasurements.scaleY;
    var x,y,pr,newPr,p;
    if(isPrivate){
        x = points[0] + contentOffsetX;
        y = points[1] + contentOffsetY;
        pr = points[2];
        context.lineWidth = ink.thickness + privacyOffset;
        context.strokeStyle = ink.color[0];
        context.fillStyle = ink.color[0];
        context.moveTo(x,y);
        context.beginPath();
        for(p = 0; p < points.length; p += 3){
            context.moveTo(x,y);
            x = points[p]+contentOffsetX;
            y = points[p+1]+contentOffsetY;
            context.lineTo(x,y);
        }
        context.lineCap = "round";
        context.stroke();
        context.closePath();
        context.strokeStyle = "white";
        context.fillStyle = "white";
    }
    else{
        context.strokeStyle = ink.color[0];
        context.fillStyle = ink.color[0];
    }
    x = points[0] + contentOffsetX;
    y = points[1] + contentOffsetY;
    pr = points[2];

    context.moveTo(x,y);
    context.beginPath();
    var x = points[0] + contentOffsetX;
    var y = points[1] + contentOffsetY;
    _.each(_.chunk(points,3),function(point){
        context.beginPath();
        context.moveTo(x,y);
        x = point[0] + contentOffsetX;
        y = point[1] + contentOffsetY;
        context.lineTo(x,y);
        context.lineWidth = ink.thickness * (point[2] / 256);
        context.lineCap = "round";
        context.stroke();
    });
    return true;
	}
	var alertCanvas = function(canvas,label){
    var url = canvas.toDataURL();
    window.open(url,label,sprintf("width=%s, height=%s",canvas.width,canvas.height));
	}
	var round = function(n){
    return Math.round(n * precision) / precision;
	}
	var calculateImageBounds = function(image){
    image.bounds = [image.x,image.y,image.x + image.width,image.y + image.height];
	}
	var calculateImageSource = function(image){
    var slide = image.privacy.toUpperCase() == "PRIVATE" ? sprintf("%s%s",image.slide,image.author) : image.slide;
    return sprintf("/proxyImageUrl/%s?source=%s",slide,encodeURIComponent(image.source));
	}
	var calculateTextBounds = function(text){
    text.bounds = [text.x,text.y,text.x + text.width, text.y + (text.runs.length * text.size * 1.25)];
	}
	var calculateInkBounds = function(ink){
    var minX = Infinity;
    var minY = Infinity;
    var maxX = -Infinity;
    var maxY = -Infinity;
    var widths = [];
    var points = ink.points;
    var places = 4;
    for(var cindex = 0; cindex < points.length; cindex += 3){
        var x = round(points[cindex]);
        var y = round(points[cindex+1]);
        points[cindex] = x;
        points[cindex+1] = y;
        widths.push(points[cindex+2]);
        minX = Math.min(x,minX);
        minY = Math.min(y,minY);
        maxX = Math.max(x,maxX);
        maxY = Math.max(y,maxY);
    }
    ink.minX = minX;
    ink.minY = minY;
    ink.maxX = maxX;
    ink.maxY = maxY;
    ink.width = maxX - minX;
    ink.height = maxY - minY;
    ink.centerX = minX + ink.width / 2;
    ink.centerY = minY + ink.height / 2;
    ink.bounds=[minX,minY,maxX,maxY];
    ink.widths=widths;
	}
	var scale = function(){
    return Math.min(boardWidth / viewboxWidth, boardHeight / viewboxHeight);
	}
	var prerenderImage = function(image) {
    var canvas = $("<canvas/>")[0];
    image.canvas = canvas;
    canvas.width = image.width;
    canvas.height = image.height;
    var borderW = canvas.width * 0.10;
    var borderH = canvas.height * 0.10;
    canvas.width = image.width + borderW;
    canvas.height = image.height + borderH;
    var context = canvas.getContext("2d");
    context.drawImage(image.imageData,borderW / 2,borderH / 2,image.width, image.height);
    if(image.privacy.toUpperCase() == "PRIVATE"){
        context.globalAlpha = 0.2;
        context.fillStyle = "red";
        context.fillRect(
            0,0,
            canvas.width,
            canvas.height);
        context.globalAlpha = 1.0;
    }
    delete image.imageData;
	}
	var prerenderText = function(text){
    var canvas = $("<canvas />")[0];

    text.canvas = canvas;
    var context = canvas.getContext("2d");
    context.strokeStyle = text.color;
    context.font = text.font;
    var newline = /\n/;
    if(!text.width){
        text.width = Math.max.apply(Math,text.text.split(newline).map(
            function(subtext){
                return context.measureText(subtext).width;
            }));
    }
    var run = "";
    var yOffset = 0;
    var runs = [];
    var breaking = false;
    $.each(text.text.split(''),function(i,c){
        if(c.match(newline)){
            runs.push(""+run);
            run = "";
            return;
        }
        else if(breaking && c == " "){
            runs.push(run);
            run = "";
            return;
        }
        var w = context.measureText(run).width;
        breaking = w >= text.width - 80;
        run += c;
    });
    runs.push(run);
    runs = runs.map(function(r){
        return r.trim();
    });
    text.runs = runs;
    calculateTextBounds(text);
    var rawWidth = text.bounds[2] - text.bounds[0];
    var rawHeight = text.bounds[3] - text.bounds[1];
    var scaleMeasurements = determineScaling(rawWidth,rawHeight);
    canvas.width = scaleMeasurements.width;
    canvas.height = scaleMeasurements.height;

    text.height = rawHeight;
    if(text.privacy.toUpperCase() == "PRIVATE"){
        context.globalAlpha = 0.2;
        context.fillStyle = "red";
        context.fillRect(
            0,0,
            scaleMeasurements.width,
            scaleMeasurements.height);
        context.globalAlpha = 1.0;
    }
    context.fillStyle = text.color[0];
    context.textBaseline = "top";
    function generateTextFont(text) {
        var font = text.font;
        if(text.weight == "bold")
            font = font + ' bold';
        if(text.style == "italic")
            font = font + ' italic';

        return font;
    }

    $.each(text.runs,function(ri,run){
        var underline = function(){
            var lines = text.height/(text.size * 1.25);
            var range = _.range(text.size, text.height, text.height/lines);
            _.each(range, function(y){
                context.beginPath();
                context.strokeStyle = text.color[0];
                var underlineY = contentOffsetY + y;
                context.moveTo(contentOffsetX, underlineY);
                var underlineEndX = contentOffsetX + scaleMeasurements.width;
                context.lineTo(underlineEndX, underlineY);
                context.stroke();
            });
        };
        var _yOffset = ri * text.size * 1.25;
        context.font = generateTextFont(text);
        context.fillText(run,
                         contentOffsetX * scaleMeasurements.scaleX,
                         (contentOffsetY + _yOffset) * scaleMeasurements.scaleY,
                         scaleMeasurements.width);
        if(text.decoration == "underline")
            underline();

    });
    incorporateBoardBounds(text.bounds);
	}
	var render = function(content,incCanvasContext,incViewBounds){
		var canvasContext = incCanvasContext;
		if (canvasContext == undefined){
			canvasContext = boardContext;
		}
    if(content){
        var startMark = Date.now();
        var fitMark,imagesRenderedMark,highlightersRenderedMark,textsRenderedMark,richTextsRenderedMark,inksRenderedMark,renderDecoratorsMark;
        try{
            var viewBounds = incViewBounds == undefined ? [viewboxX,viewboxY,viewboxX+viewboxWidth,viewboxY+viewboxHeight] : incViewBounds;
						//console.log("viewbounds",viewboxX,viewboxY,viewboxWidth,viewboxHeight);
            visibleBounds = [];
            var scale = content.maxX / viewboxWidth;
            var renderInks = function(inks){
                if (inks != undefined){
                    $.each(inks,function(i,ink){
                        try{
                            if(intersectRect(ink.bounds,viewBounds)){
                                drawInk(ink,canvasContext);
                            }
                        }
                        catch(e){
                            console.log("ink render failed for",e,ink.canvas,ink.identity,ink);
                        }
                    });
                }
            }
            var renderRichTexts = function(texts){
                if(texts){
                    $.each(texts,function(i,text){
                        if(!text.bounds){
                            text.bounds = [text.x,text.y,text.x + text.width,text.y + text.height];
                        }
                        if(intersectRect(text.bounds,viewBounds)){
                            Modes.text.draw(text);
                        }
                    });
                }
            }
            var renderImmediateContent = function(){
                renderInks(content.highlighters);
                highlightersRenderedMark = Date.now();
                $.each(content.texts,function(i,text){
                    if(intersectRect(text.bounds,viewBounds)){
                        drawText(text,canvasContext);
                    }
                });
                textsRenderedMark = Date.now();
                renderRichTexts(content.multiWordTexts);
                richTextsRenderedMark = Date.now();
                renderInks(content.inks);
                inksRenderedMark = Date.now();
                Progress.call("postRender");
                renderDecoratorsMark = Date.now();
            }
            var loadedCount = 0;
            var loadedLimit = Object.keys(content.images).length;
            //clearBoard();
						clearBoard(canvasContext,{x:0,y:0,w:boardWidth,h:boardHeight});
            fitMark = Date.now();
            $.each(content.images,function(id,image){
                try{
                    if(intersectRect(image.bounds,viewBounds)){
                        drawImage(image,canvasContext);
                    }
                }
                catch(e){
                    console.log("image render failed for",e,image.identity,image);
                }
            });
            imagesRenderedMark = Date.now();
            renderImmediateContent();
        }
        catch(e){
            console.log("Render exception",e);
        }
        Progress.call("onViewboxChanged");
    }
	}
	var blit = function(canvasContext,content){
		try {
			render(content == undefined ? boardContent : content,canvasContext == undefined ? boardContext : canvasContext);
		} catch(e){
			console.log("exception in render:",e);
		}
	};
	var pica = function(value){
    return value / 128;
	}
	var unpica = function(value){
    return Math.floor(value * 128);
	}
	var px = function(value){
    return sprintf("%spx",value);
	}
	var unpix = function(str){
    return str.slice(0,str.length-2);
	}
	var clearBoard = function(incContext,rect){
		try {
			var ctx = incContext == undefined ? boardContext : incContext;
			var r = rect == undefined ? {x:0,y:0,w:boardWidth,h:boardHeight} : rect;
			ctx.clearRect(r.x,r.y,r.w,r.h);
		} catch(e){
			console.log("exception while clearing board:",e,incContext,rect);
		}
	}
	var IncludeView = (function(){
    var fitToRequested = function(incX,incY,incW,incH){//Include at least this much content in your view
        var shouldUpdateRequestedViewbox = false;
        var x = incX;
        if (x == undefined){
            x = requestedViewboxX;
        }       else {
            shouldUpdateRequestedViewbox = true;
            requestedViewboxX = x;
        }
        var y = incY;
        if (y == undefined){
            y = requestedViewboxY;
        } else {
            shouldUpdateRequestedViewbox = true;
            requestedViewboxY = y;
        }
        var w = incW;
        if (w == undefined){
            w = requestedViewboxWidth;
        } else {
            shouldUpdateRequestedViewbox = true;
            requestedViewboxWidth = w;
        }
        var h = incH;
        if (h == undefined){
            h = requestedViewboxHeight;
        } else {
            shouldUpdateRequestedViewbox = true;
            requestedViewboxHeight = h;
        }
        var constrained = Zoom.constrainRequestedViewbox({width:w,height:h,x:x,y:y});
        var hr = boardHeight / constrained.height;
        var wr = boardWidth / constrained.width;
        var targetHeight,targetWidth;
        if(wr > hr){
            targetHeight = constrained.height;
            targetWidth = constrained.width * wr / hr;
        }
        else{
            targetHeight = constrained.height / wr * hr;
            targetWidth = constrained.width;
        }
        TweenController.zoomAndPanViewbox(constrained.x,constrained.y,targetWidth,targetHeight,undefined,!shouldUpdateRequestedViewbox);
        Progress.call("onViewboxChanged");
    };
    return {
        specific:function(x,y,w,h){
            return fitToRequested(x,y,w,h);
        },
        "default":function(){
            return fitToRequested();
        }
    };
	})();
	var strokeCollected = function(spoints){
    if(spoints.length > 0){
        var points = spoints.split(" ").map(function(p){
            return parseFloat(p);
        });

        var ink = {
            thickness : scaleScreenToWorld(Modes.draw.drawingAttributes.width),
            color:[Modes.draw.drawingAttributes.color,255],
            type:"ink",
            author:UserSettings.getUsername(),
            timestamp:Date.now(),
            target:"presentationSpace",
            privacy:Privacy.getCurrentPrivacy(),
            slide:currentSlide.toString(),
            isHighlighter:Modes.draw.drawingAttributes.isHighlighter
        };
        var scaledPoints = [];
        var x;
        var y;
        var worldPos;
        for(var p = 0; p < points.length; p += 3){
            x = points[p];
            y = points[p+1];
            worldPos = screenToWorld(x,y);
            scaledPoints = scaledPoints.concat([worldPos.x,worldPos.y,points[p+2]]);
        }
        ink.points = scaledPoints;
        ink.checksum = ink.points.reduce(function(a,b){return a+b},0);
        ink.startingSum = ink.checksum;
        ink.identity = ink.checksum.toFixed(1);
        calculateInkBounds(ink);
        if(ink.isHighlighter){
            boardContent.highlighters[ink.identity] = ink;
        }
        else{
            boardContent.inks[ink.identity] = ink;
        }
        sendInk(ink);
    }
	}
	var batchTransform = function(){
    return {
        type:"moveDelta",
        identity:Date.now().toString(),
        author:UserSettings.getUsername(),
        slide:currentSlide.toString(),
        target:"presentationSpace",
        privacy:Privacy.getCurrentPrivacy(),
        timestamp:Date.now(),
        inkIds:[],
        textIds:[],
        multiWordTextIds:[],
        imageIds:[],
        xOrigin:0,
        yOrigin:0,
        xTranslate:0,
        yTranslate:0,
        xScale:1.0,
        yScale:1.0,
        isDeleted:false,
        newPrivacy:"not_set"
    }
	}
	var sendDirtyInk = function(ink){
    sendStanza({
        type:"dirtyInk",
        identity:ink.identity,
        author:UserSettings.getUsername(),
        timestamp:Date.now(),
        slide:currentSlide.toString(),
        target:"presentationSpace",
        privacy:ink.privacy
    });
	}
	var sendInk = function(ink){
    updateStrokesPending(1,ink.identity);
    sendStanza(ink);
	}
	var hexToRgb = function(hex) {
    if(typeof hex == "string") hex = [hex,255];
    var result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex[0]);
    return {
        alpha: hex[1],
        red: parseInt(result[1], 16),
        green: parseInt(result[2], 16),
        blue: parseInt(result[3], 16)
    };
	}
	var partToStanza = function(p){
    var defaults = carota.runs.defaultFormatting;
    var color = hexToRgb(p.color || defaults.color);
    return {
        text:p.text,
        color:color,
        size:p.size || defaults.size,
        font:p.font || defaults.font,
        justify:p.align || defaults.align,
        bold:p.bold === true,
        underline:p.underline === true,
        italic:p.italic === true
    };
	}
	var richTextEditorToStanza = function(t){
    var bounds = t.doc.calculateBounds();
    var text = t.doc.save();
    return {
        author:t.author,
        timestamp:-1,
        target:t.target,
        tag:"_",
        privacy:t.privacy,
        slide:t.slide,
        identity:t.identity,
        type:t.type,
        x:bounds[0],
        y:bounds[1],
        requestedWidth:bounds[2]-bounds[0],
        width:bounds[2]-bounds[0],
        height:bounds[3]-bounds[1],
        words:text.map(partToStanza)
    }
	}
	var sendRichText = function(t){
    if(t.doc){
        Modes.text.echoesToDisregard[t.identity] = true;
        sendStanza(richTextEditorToStanza(t));
    }
	}
	var stanzaHandlers = {
    ink:inkReceived,
    dirtyInk:dirtyInkReceived,
    move:moveReceived,
    moveDelta:transformReceived,
    image:imageReceived,
    text:textReceived,
    multiWordText:richTextReceived,
    command:commandReceived,
    submission:submissionReceived,
    attendance:attendanceReceived,
    file:fileReceived
	};
	var fileReceived = function(file){
    //doing nothing with files yet.
	}
	var attendanceReceived = function(attendance){
    //doing nothing with attendances for the moment.
	}
	var submissionReceived = function(submission){
    Submissions.processSubmission(submission);
	}
	var commandReceived = function(c){
    if(c.command == "/TEACHER_VIEW_MOVED"){
        if(c.parameters[5] != Conversations.getCurrentSlideJid()){
            return;
        }
        var ps = c.parameters.map(parseFloat);
        if(_.some(ps,isNaN)){
            console.log("Can't follow teacher to",c);
            return;
        }
        if(ps[4] == DeviceConfiguration.getIdentity()){
            return;
        }
        if(Conversations.getIsSyncedToTeacher()){
            var f = function(){
                zoomToPage();
                TweenController.zoomAndPanViewbox(ps[0],ps[1],ps[2],ps[3],function(){},false,true);
            };
            if(UserSettings.getIsInteractive()){
                // interactive users don't chase the teacher's viewbox, only projectors do.
                //    WorkQueue.enqueue(f);
            }
            else{
                f();
            }
        }
        else{
        }
    }
	}
	var richTextReceived = function(t){
    if(t.identity in Modes.text.echoesToDisregard) return;
    if(isUsable(t)){
        WorkQueue.enqueue(function(){
            Modes.text.editorFor(t).doc.load(t.words);
            blit();
        });
    }
	}
	var textReceived = function(t){
    try{
        if(isUsable(t)){
            boardContent.texts[t.identity] = t;
            prerenderText(t);
            incorporateBoardBounds(t.bounds);
            WorkQueue.enqueue(function(){
                if(isInClearSpace(t.bounds)){
                    drawText(t);
                    return false;
                }
                else{
                    return true;
                }
            });
        }
        else{
            if(t.identity in boardContent.texts){
                delete boardContent.texts[t.identity];
            }
        }
    }
    catch(e){
        console.log("textReceived exception:",e);
    }
	}
	var receiveMeTLStanza = function(stanza){
    Progress.call("stanzaReceived",[stanza]);
	}
	var actOnReceivedStanza = function(stanza){
    try{
        if(stanza.type in stanzaHandlers){
            stanzaHandlers[stanza.type](stanza);
            Progress.call("onBoardContentChanged");
        }
        else{
            console.log(sprintf("Unknown stanza: %s %s",stanza.type,stanza));
        }
    }
    catch(e){
        console.log("Exception in receiveMeTLStanza",e,stanza);
    }
	}
	var transformReceived = function(transform){
    console.log("transformReceived",transform);
    var op = "";
    var transformBounds = (function(){
        var myBounds = [undefined,undefined,undefined,undefined]; //minX,minY,maxX,maxY
        var incBounds = function(bounds){
            var max = function(count){
                var reference = myBounds[count];
                if (reference != undefined && !isNaN(reference)){
                    myBounds[count] = Math.max(reference,bounds[count]);
                } else {
                    myBounds[count] = bounds[count];
                }
            };
            var min = function(count){
                var reference = myBounds[count];
                if (reference != undefined && !isNaN(reference)){
                    myBounds[count] = Math.min(reference,bounds[count]);
                } else {
                    myBounds[count] = bounds[count];
                }
            };
            min(0);
            min(1);
            max(2);
            max(3);
        };
        var getBounds = function(){
            return myBounds;
        };
        var incBoardBounds = function(){
            var thisBounds = getBounds();
            if (thisBounds[0] != undefined && thisBounds[1] != undefined && thisBounds[2] != undefined && thisBounds[3] != undefined){
                incorporateBoardBounds(thisBounds);
            }
        };
        var setMinX = function(input){
            safelySet(input,0);
        };
        var setMinY = function(input){
            safelySet(input,1);
        };
        var setMaxX = function(input){
            safelySet(input,2);
        };
        var setMaxY = function(input){
            safelySet(input,3);
        };
        var safelySet = function(input,reference){
            if (input != undefined && !isNaN(input)){
                myBounds[reference] = input;
            }
        };
        return {
            "minX":getBounds[0],
            "setMinX":setMinX,
            "minY":getBounds[1],
            "setMinY":setMinY,
            "maxX":getBounds[2],
            "setMaxX":setMaxX,
            "maxY":getBounds[3],
            "setMaxY":setMaxY,
            "incorporateBounds":incBounds,
            "getBounds":getBounds,
            "incorporateBoardBounds":incBoardBounds
        };
    })();
    if(transform.newPrivacy != "not_set" && !transform.isDeleted){
        var p = transform.newPrivacy;
        op += "Became "+p;
        var setPrivacy = function(ink){
            if(ink){
                ink.privacy = p;
            }
        }
        $.each(transform.inkIds,function(i,id){
            setPrivacy(boardContent.inks[id]);
            setPrivacy(boardContent.highlighters[id]);
        });
        $.each(transform.imageIds,function(i,id){
            boardContent.images[id].privacy = p;
        });
        $.each(transform.textIds,function(i,id){
            boardContent.texts[id].privacy = p;
        });
        $.each(transform.multiWordTextIds,function(i,id){
            boardContent.multiWordTextIds[id].privacy = p;
        });
    }
    if(transform.isDeleted){
        op += "deleted";
        var p = transform.privacy;
        $.each(transform.inkIds,function(i,id){
            deleteInk("highlighters",p,id);
            deleteInk("inks",p,id);
        });
        $.each(transform.imageIds,function(i,id){
            deleteImage(p,id);
        });
        $.each(transform.textIds,function(i,id){
            deleteText(p,id);
        });
        $.each(transform.multiWordTextIds,function(i,id){
            deleteMultiWordText(p,id);
        });
    }
    if(transform.xScale != 1 || transform.yScale != 1){
        op += sprintf("scale (%s,%s)",transform.xScale,transform.yScale);
        var relevantInks = [];
        var relevantTexts = [];
        var relevantMultiWordTexts = [];
        var relevantImages = [];
        $.each(transform.inkIds,function(i,id){
            relevantInks.push(boardContent.inks[id]);
            relevantInks.push(boardContent.highlighters[id]);
        });
        $.each(transform.imageIds,function(i,id){
            relevantImages.push(boardContent.images[id]);
        });
        $.each(transform.textIds,function(i,id){
            relevantTexts.push(boardContent.texts[id]);
        });
        $.each(transform.multiWordTextIds,function(i,id){
            relevantMultiWordTexts.push(boardContent.multiWordTexts[id]);
        });
        var point = function(x,y){return {"x":x,"y":y};};
        var totalBounds = point(0,0);
        if ("xOrigin" in transform && "yOrigin" in transform){
            totalBounds.x = transform.xOrigin;
            totalBounds.y = transform.yOrigin;
        } else {
            var first = true;
            var updateRect = function(point){
                if (first){
                    totalBounds.x = point.x;
                    totalBounds.y = point.y;
                    first = false;
                } else {
                    if (point.x < totalBounds.x){
                        totalBounds.x = point.x;
                    }
                    if (point.y < totalBounds.y){
                        totalBounds.y = point.y;
                    }
                }
            };
            $.each(relevantInks,function(i,ink){
                if (ink != undefined && "bounds" in ink && _.size(ink.bounds) > 1){
                    updateRect(point(ink.bounds[0],ink.bounds[1]));
                }
            });
            $.each(relevantTexts,function(i,text){
                if (text != undefined && "x" in text && "y" in text){
                    updateRect(point(text.x,text.y));
                }
            });
            $.each(relevantMultiWordTexts,function(i,text){
                if (text != undefined && "x" in text && "y" in text){
                    updateRect(point(text.x,text.y));
                }
            });
            $.each(relevantImages,function(i,image){
                if (image != undefined && "x" in image && "y" in image){
                    updateRect(point(image.x,image.y));
                }
            });
        }
        transformBounds.setMinX(totalBounds.x);
        transformBounds.setMinY(totalBounds.y);
        var transformInk = function(index,ink){
            if(ink && ink != undefined){
                var ps = ink.points;
                var xPos = ink.bounds[0];
                var yPos = ink.bounds[1];
                var xp, yp;

                var internalX = xPos - totalBounds.x;
                var internalY = yPos - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));

                for(var p = 0; p < ps.length; p += 3){
                    xp = ps[p] - xPos;
                    yp = ps[p + 1] - yPos;
                    ps[p] = (xPos + xp * transform.xScale) + offsetX;
                    ps[p+1] = (yPos + yp * transform.yScale) + offsetY;
                }
                calculateInkBounds(ink);
                transformBounds.incorporateBounds(ink.bounds);
            }
        };
        var transformImage = function(index,image){
            if (image != undefined){
                image.width = image.width * transform.xScale;
                image.height = image.height * transform.yScale;

                var internalX = image.x - totalBounds.x;
                var internalY = image.y - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));
                image.x = image.x + offsetX;
                image.y = image.y + offsetY;

                calculateImageBounds(image);
                transformBounds.incorporateBounds(image.bounds);
            }
        };
        var transformText = function(index,text){
            if (text != undefined){
                text.width = text.width * transform.xScale;
                text.height = text.height * transform.yScale;

                var internalX = text.x - totalBounds.x;
                var internalY = text.y - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));
                text.x = text.x + offsetX;
                text.y = text.y + offsetY;

                text.size = text.size * transform.yScale;
                text.font = sprintf("%spx %s",text.size,text.family);
                if(isUsable(text)){
                    prerenderText(text);
                    calculateTextBounds(text);
                }
                else{
                    if(text.identity in boardContent.texts){
                        delete boardContent.texts[text.identity];
                    }
                }
                transformBounds.incorporateBounds(text.bounds);
            }
        };
        var transformMultiWordText = function(index,text){
            if (text != undefined){
                text.width = text.width * transform.xScale;
                text.height = text.height * transform.yScale;

                var internalX = text.x - totalBounds.x;
                var internalY = text.y - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));
                text.x = text.x + offsetX;
                text.y = text.y + offsetY;

                text.size = text.size * transform.yScale;
                text.font = sprintf("%spx %s",text.size,text.family);
                if(text.identity in boardContent.multiWordTexts){
                    delete boardContent.multiWordTexts[text.identity];
                }
                transformBounds.incorporateBounds(text.bounds);
            }
        };
        $.each(relevantInks,transformInk);
        $.each(relevantImages,transformImage);
        $.each(relevantTexts,transformText);
        $.each(relevantMultiWordTexts,transformMultiWordText);
    }
    if(transform.xTranslate || transform.yTranslate){
        var deltaX = transform.xTranslate;
        var deltaY = transform.yTranslate;
        op += sprintf("translate (%s,%s)",deltaX,deltaY);
        var translateInk = function(ink){
            if(ink){
                var ps = ink.points;
                for(var p = 0; p < ps.length; p += 3){
                    ps[p] += deltaX;
                    ps[p+1] += deltaY;
                }
                calculateInkBounds(ink);
                transformBounds.incorporateBounds(ink.bounds);
            }
        }
        $.each(transform.inkIds,function(i,id){
            translateInk(boardContent.inks[id]);
            translateInk(boardContent.highlighters[id]);
        });
        $.each(transform.imageIds,function(i,id){
            var image = boardContent.images[id];
            image.x += transform.xTranslate;
            image.y += transform.yTranslate;
            calculateImageBounds(image);
            transformBounds.incorporateBounds(image.bounds);
        });
        $.each(transform.textIds,function(i,id){
            var text = boardContent.texts[id];
            text.x += transform.xTranslate;
            text.y += transform.yTranslate;
            calculateTextBounds(text);
            transformBounds.incorporateBounds(text.bounds);
        });
        $.each(transform.multiWordTextIds,function(i,id){
            var text = boardContent.multiWordTexts[id];
	    var doc = text.doc;
            doc.position.x += transform.xTranslate;
            doc.position.y += transform.yTranslate;
	    text.bounds = doc.calculateBounds();
            transformBounds.incorporateBounds(text.bounds);
        });
    }
    transformBounds.incorporateBoardBounds();
    updateStatus(sprintf("%s %s %s %s %s",
                         op,
                         transform.imageIds.length,
                         transform.textIds.length,
                         transform.multiWordTextIds.length,
                         transform.inkIds.length));
    blit();
	}
	var moveReceived = function(move){
    updateStatus(sprintf("Moving %s, %s, %s",
                         Object.keys(move.images).length,
                         Object.keys(move.texts).length,
                         Object.keys(move.inks).length));
    $.each(move.inks,function(id,ink){
        boardContent.inks[id] = ink;
    });
    $.each(move.images,function(id,image){
        boardContent.images[id] = image;
    });
    $.each(move.texts,function(id,text){
        boardContent.texts[id] = text;
    });
    $.each(move.multiWordTexts,function(id,text){
        boardContent.multiWordTexts[id] = text;
    });
    blit();
	}
	var deleteInk = function(inks,privacy,id){
    if(id in boardContent[inks]){
        var ink = boardContent[inks][id];
        if(ink.privacy.toUpperCase() == privacy.toUpperCase()){
            delete boardContent[inks][id];
        }
    }
	}
	var deleteImage = function(privacy,id){
    var image = boardContent.images[id];
    if(image.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.images[id];
    }
	}
	var deleteText = function(privacy,id){
    var text = boardContent.texts[id];
    if(text.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.texts[id];
    }
	}
	var deleteMultiWordText = function(privacy,id){
    var text = boardContent.multiWordTexts[id];
    if(text.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.multiWordTexts[id];
    }
	}
	var dirtyInkReceived = function(dirtyInk){
    var id = dirtyInk.identity;
    var deletePrivacy = dirtyInk.privacy;
    deleteInk("highlighters",deletePrivacy,id);
    deleteInk("inks",deletePrivacy,id);
    updateStatus(sprintf("Deleted ink %s",id));
    blit();
	}
	var isInClearSpace = function(bounds){
    return !_.some(visibleBounds,function(onscreenElement){
        return intersectRect(onscreenElement,bounds);
    });
	}
	var screenBounds = function(worldBounds){
    var screenPos = worldToScreen(worldBounds[0],worldBounds[1]);
    var screenLimit = worldToScreen(worldBounds[2],worldBounds[3]);
    var screenWidth = screenLimit.x - screenPos.x;
    var screenHeight = screenLimit.y - screenPos.y;
    return {
        screenPos:screenPos,
        screenLimit:screenLimit,
        screenWidth:screenWidth,
        screenHeight:screenHeight
    };
	}
	var drawImage = function(image,incCanvasContext){
		var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
    try{
        if (image.canvas != undefined){
            var sBounds = screenBounds(image.bounds);
            visibleBounds.push(image.bounds);
            var borderW = sBounds.screenWidth * 0.10;
            var borderH = sBounds.screenHeight * 0.10;
            canvasContext.drawImage(image.canvas, sBounds.screenPos.x - (borderW / 2), sBounds.screenPos.y - (borderH / 2), sBounds.screenWidth + borderW ,sBounds.screenHeight + borderH);
        }
    }
    catch(e){
        console.log("drawImage exception",e);
    }
}
function drawText(text,incCanvasContext){
	var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
    try{
        var sBounds = screenBounds(text.bounds);
        visibleBounds.push(text.bounds);
        canvasContext.drawImage(text.canvas,
			       sBounds.screenPos.x,
			       sBounds.screenPos.y,
			       sBounds.screenWidth,
			       sBounds.screenHeight);
    }
    catch(e){
        console.log("drawText exception",e);
    }
}
function drawInk(ink,incCanvasContext){
	var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
    var sBounds = screenBounds(ink.bounds);
    visibleBounds.push(ink.bounds);
    canvasContext.drawImage(ink.canvas,
                           sBounds.screenPos.x,sBounds.screenPos.y,
                           sBounds.screenWidth,sBounds.screenHeight);
}
function imageReceived(image){
    var dataImage = new Image();
    image.imageData = dataImage;
    dataImage.onload = function(){
        if(image.width == 0){
            image.width = dataImage.naturalWidth;
        }
        if(image.height == 0){
            image.height = dataImage.naturalHeight;
        }
        image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
        incorporateBoardBounds(image.bounds);
        boardContent.images[image.identity]  = image;
        updateTracking(image.identity);
        prerenderImage(image);
        WorkQueue.enqueue(function(){
            if(isInClearSpace(image.bounds)){
                try {
                    drawImage(image);
                } catch(e){
                    console.log("drawImage exception",e);
                }
                return false;
            }
            else{
                console.log("Rerendering image in contested space");
                return true;
            }
        });
    }
    dataImage.src = calculateImageSource(image);
}
function inkReceived(ink){
    calculateInkBounds(ink);
    updateStrokesPending(-1,ink.identity);
    if(prerenderInk(ink)){
        incorporateBoardBounds(ink.bounds);
        if(ink.isHighlighter){
            boardContent.highlighters[ink.identity] = ink;
        }
        else{
            boardContent.inks[ink.identity] = ink;
        }
        WorkQueue.enqueue(function(){
            if(isInClearSpace(ink.bounds)){
                drawInk(ink);
                return false;
            }
            else{
                return true;
            }
        });
    }
}
function takeControlOfViewbox(){
    delete Progress.onBoardContentChanged.autoZooming;
    UserSettings.setUserPref("followingTeacherViewbox",true);
}
function zoomToFit(){
    Progress.onBoardContentChanged.autoZooming = zoomToFit;
    requestedViewboxWidth = boardContent.width;
    requestedViewboxHeight = boardContent.height;
    IncludeView.specific(boardContent.minX,boardContent.minY,boardContent.width,boardContent.height);
}
function zoomToOriginal(){
    takeControlOfViewbox();
    var oldReqVBH = requestedViewboxHeight;
    var oldReqVBW = requestedViewboxWidth;
    requestedViewboxWidth = boardWidth;
    requestedViewboxHeight = boardHeight;
    IncludeView.specific(0,0,boardWidth,boardHeight);
}
function zoomToPage(){
    takeControlOfViewbox();
    var oldReqVBH = requestedViewboxHeight;
    var oldReqVBW = requestedViewboxWidth;
    requestedViewboxWidth = boardWidth;
    requestedViewboxHeight = boardHeight;
    var xPos = viewboxX + ((oldReqVBW - requestedViewboxWidth) / 2);
    var yPos = viewboxY + ((oldReqVBH - requestedViewboxHeight) / 2);
    IncludeView.specific(xPos,yPos,boardWidth,boardHeight);
}

	return {
		setZoomMode:function(zoomMode){
		},
		historyReceived:function(json){
			historyReceivedFunction(json);
		},
		stanzaReceived:function(stanza){
			actOnReceivedStanza(stanza);
		}
	};
}
