var renderOffsetX = 0;
var renderOffsetY = 0;
var loadSlidesAtNativeZoom;
var startMark;
var requestedViewboxX = 0;
var requestedViewboxY = 0;
var requestedViewboxWidth = 320;
var requestedViewboxHeight = 240;

function loadSlide(jid){
    startMark = Date.now();
    $("#targetSlide").text(sprintf("Loading %s",jid));
    showSpinner();
    moveToSlide(jid.toString());
}

function receiveHistory(json,incCanvasContext,afterFunc){
    try{
        var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
        var historyDownloadedMark, prerenderInkMark, prerenderImageMark, prerenderHighlightersMark,prerenderTextMark,imagesLoadedMark,renderMultiWordMark, historyDecoratorsMark, blitMark;
        historyDownloadedMark = Date.now();

        json.multiWordTexts = _.pickBy(json.multiWordTexts,isUsable);
        json.images = _.pickBy(json.images,isUsable);
        json.inks = _.pickBy(json.inks,isUsable);

        boardContent = json;
        boardContent.minX = 0;
        boardContent.minY = 0;
        boardContent.maxX = boardWidth;
        boardContent.maxY = boardHeight;
        /*Use 1:1 pixel size as a reasonable starting position.  This will keep textboxes from measuring themselves against a miniscule world and deciding that they are enormously wide*/
        viewboxWidth = boardContent.maxX - boardContent.minX;
        viewboxHeight = boardContent.maxY - boardContent.minY;
        $.each(boardContent.inks,function(i,ink){
            prerenderInk(ink,true);
        });
        prerenderInkMark = Date.now();
        $.each(boardContent.highlighters,function(i,ink){
            prerenderInk(ink,true);
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
        $.each(boardContent.videos,function(i,video){
            prerenderVideo(video);
        });
        prerenderTextMark = Date.now();
        _.each(boardContent.multiWordTexts,function(text){
            if(isUsable(text)){
                prerenderMultiwordText(text);
            }
            else{
                console.log("Not usable",text);
            }
        });
        renderMultiWordMark = Date.now();

        boardContent.width = boardContent.maxX - boardContent.minX;
        boardContent.height = boardContent.maxY - boardContent.minY;
        var startRender = function(){
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
            }
            else{
                requestedViewboxX = boardContent.minX;
                requestedViewboxY = boardContent.minY;
                requestedViewboxWidth = boardContent.width;
                requestedViewboxHeight = boardContent.height;
            }
            IncludeView.default();
            hideBackstage();

            blitMark = Date.now();
            if (!UserSettings.getIsInteractive()){
                //projector mode should always start viewing the entire slide
                zoomToFit();
            }
            if (afterFunc != undefined){
                afterFunc();
            }
        }
        if(_.keys(boardContent.images).length == 0){
            startRender();
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
                };
                dataImage.src = url;
            });
        }
    }
    catch(e){
        console.log("receiveHistory exception",e);
    }
}

var lineDrawingThreshold = 25;
function incorporateBoardBounds(bounds){
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
function mergeBounds(b1,b2){
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
}
var boardLimit = 10000;
function isUsable(element){
    var boundsOk = !(_.some(element.bounds,function(p){
        return isNaN(p);// || p > boardLimit || p < -boardLimit;
    }));
    var sizeOk = "size" in element? !isNaN(element.size) : true
    var textOk =  "text" in element? element.text.length > 0 : true;
    var myGroups = _.map(Conversations.getCurrentGroup(),"id");
    var forMyGroup = _.isEmpty(element.audiences) ||
        Conversations.isAuthor() ||
        _.some(element.audiences,function(audience){
            return audience.action == "whitelist" && _.includes(myGroups,audience.name);
        });
    var isMine = element.author == UserSettings.getUsername();
    var isDirectedToMe = _.some(element.audiences,function(audience){
        return audience.action == "direct" && audience.name == UserSettings.getUsername();
    });
    var availableToMe = isMine || isDirectedToMe || forMyGroup;
    return boundsOk && sizeOk && textOk && availableToMe;
}
function usableStanzas(){
    return _.map(boardContent.multiWordTexts).map(function(v){
        return {
            identity:v.identity,
            usable:isUsable(v)
        }
    });
}
var leftPoint = function(xDelta,yDelta,l,x2,y2,bulge){
    var px = yDelta * l * bulge;
    var py = xDelta * l * -bulge;
    return {
        x:px + x2,
        y:py + y2
    }
};
var rightPoint = function(xDelta,yDelta,l,x2,y2,bulge){
    var px = yDelta * l * -bulge;
    var py = xDelta * l * bulge;
    return {
        x:px + x2,
        y:py + y2
    }
};
var determineCanvasConstants = _.once(function(){
    var currentDevice = DeviceConfiguration.getCurrentDevice();
    var maxX = 32767;//2147483647;
    var maxY = 32767;//2147483647;
    if (currentDevice == "browser"){
        //      maxX = 500;
        //      maxY = 500;
    }
    else if (currentDevice == "iPad" ){
        maxX = 6144;
        maxY = 6144;
    } else if (currentDevice == "iPhone"){
        maxX = 2048;
        maxY = 2048;
    } else if (currentDevice == "IE9"){
        maxX = 8192;
        maxY = 8192;
    }
    return {x:maxX,y:maxY};
});

function determineScaling(inX,inY){
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
        outputScaleY = outputScaleX;
        outputY = inY * outputScaleX;
    }
    if (outputY > maxY){
        outputScaleY = maxY / outputY;
        outputY = outputY * outputScaleY;
        outputScaleX = outputScaleY;
        outputX = outputX * outputScaleY;
    }
    return {
        width:outputX,
        height:outputY,
        scaleX:outputScaleX,
        scaleY:outputScaleY
    };
}
function prerenderInk(ink,onBoard){
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
    if(onBoard){
        incorporateBoardBounds(ink.bounds);
    }
    var isPrivate = ink.privacy.toUpperCase() == "PRIVATE";
    var rawWidth = ink.bounds[2] - ink.bounds[0] + (ink.thickness);
    var rawHeight = ink.bounds[3] - ink.bounds[1] + (ink.thickness);

    var scaleMeasurements = determineScaling(rawWidth,rawHeight);
    var canvas = $("<canvas />",{
        width:scaleMeasurements.width,
        height:scaleMeasurements.height
    })[0];
    ink.canvas = canvas;
    var context = canvas.getContext("2d");
    canvas.width = scaleMeasurements.width;
    canvas.height = scaleMeasurements.height;
    var rawPoints = ink.points;
    var points = [];
    var x,y,pr,p;
    for (p = 0; p < rawPoints.length; p += 3){
        points.push(rawPoints[p] * scaleMeasurements.scaleX);
        points.push(rawPoints[p + 1] * scaleMeasurements.scaleY);
        points.push(rawPoints[p + 2] / 256);
    }
    var contentOffsetX = -1 * ((ink.minX - ink.thickness / 2)) * scaleMeasurements.scaleX;
    var contentOffsetY = -1 * ((ink.minY - ink.thickness / 2)) * scaleMeasurements.scaleY;
    var scaledThickness = ink.thickness * scaleMeasurements.scaleX;
    if(isPrivate){
        x = points[0] + contentOffsetX;
        y = points[1] + contentOffsetY;
        context.lineWidth = scaledThickness;
        context.lineCap = "round";
        context.strokeStyle = "red";
        context.globalAlpha = 0.3;
        context.moveTo(x,y);
        for(p = 0; p < points.length; p += 3){
            context.beginPath();
            context.moveTo(x,y);
            x = points[p]+contentOffsetX;
            y = points[p+1]+contentOffsetY;
            pr = scaledThickness * points[p+2];
            context.lineWidth = pr + 2;
            context.lineTo(x,y);
            context.stroke();
        }
        context.globalAlpha = 1.0;
    }
    context.strokeStyle = ink.color[0];
    context.fillStyle = ink.color[0];
    x = points[0] + contentOffsetX;
    y = points[1] + contentOffsetY;

    context.beginPath();
    context.moveTo(x,y);
    pr = scaledThickness * points[2];
    context.arc(x,y,pr/2,0,2 * Math.PI);
    context.fill();
    context.lineCap = "round";
    for(p = 0; p < points.length; p += 3){
        context.beginPath();
        context.moveTo(x,y);
        x = points[p+0] + contentOffsetX;
        y = points[p+1] + contentOffsetY;
        pr = scaledThickness * points[p+2];
        context.lineWidth = pr;
        context.lineTo(x,y);
        context.stroke();
    }
    return true;
}
function alertCanvas(canvas,label){
    var url = canvas.toDataURL();
    window.open(url,label,sprintf("width=%s, height=%s",canvas.width,canvas.height));
}
var precision = Math.pow(10,3);
var round = function(n){
    return Math.round(n * precision) / precision;
};
function calculateImageBounds(image){
    image.bounds = [image.x,image.y,image.x + image.width,image.y + image.height];
}
function calculateVideoBounds(video){
    video.bounds = [video.x,video.y,video.x + video.width,video.y + video.height];
}
function urlEncodeSlideName(slideName){
    var newSlideName = btoa(slideName);
    return newSlideName;
}
function calculateImageSource(image){
    var slide = image.privacy.toUpperCase() == "PRIVATE" ? sprintf("%s%s",image.slide,image.author) : image.slide;
    return sprintf("/proxyImageUrl/%s?source=%s",urlEncodeSlideName(slide),encodeURIComponent(image.source.trim()));
}
function calculateVideoSource(video){
    var slide = video.privacy.toUpperCase() == "PRIVATE" ? sprintf("%s%s",video.slide,video.author) : video.slide;
    return sprintf("/videoProxy/%s/%s",urlEncodeSlideName(slide),encodeURIComponent(video.identity.trim()));
}
function calculateTextBounds(text){
    text.bounds = [text.x,text.y,text.x + text.width, text.y + (text.runs.length * text.size * 1.25)];
}
function calculateInkBounds(ink){
    var minX = Infinity;
    var minY = Infinity;
    var maxX = -Infinity;
    var maxY = -Infinity;
    var widths = [];
    var points = ink.points;
    var hw = ink.thickness / 2;
    var hh = ink.thickness / 2;
    if(points.length == 6){
        minX = points[0] - hw;
        maxX = points[0] + hw;
        minY = points[1] - hh;
        maxY = points[1] + hh;
        widths.push(points[2]);
    }
    else{
        for(var cindex = 0; cindex < points.length; cindex += 3){
            var x = round(points[cindex]);
            var y = round(points[cindex+1]);
            points[cindex] = x;
            points[cindex+1] = y;
            widths.push(points[cindex+2]);
            minX = Math.min(x - hw,minX);
            minY = Math.min(y - hh,minY);
            maxX = Math.max(x + hw ,maxX);
            maxY = Math.max(y + hh,maxY);
        }
    }
    ink.minX = minX;
    ink.minY = minY;
    ink.maxX = maxX;
    ink.maxY = maxY;
    ink.width = maxX - minX;
    ink.height = maxY - minY;
    ink.centerX = minX + hw;
    ink.centerY = minY + hh;
    ink.bounds=[minX,minY,maxX,maxY];
    ink.widths=widths;
}
function scale(){
    return Math.min(boardWidth / viewboxWidth, boardHeight / viewboxHeight);
}
function prerenderMultiwordText(text){
    var editor = Modes.text.editorFor(text).doc;
    editor.load(text.words);
    editor.updateCanvas();
    incorporateBoardBounds(text.bounds);
}
function prerenderImage(image) {
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
function prerenderVideo(video){
    if (!("video" in video)){
        var vid = $("<video/>",{
            src:calculateVideoSource(video)
        });
        video.video = vid[0];
        video.getState = function(){
            return {
                paused:vid[0].paused,
                ended:vid[0].ended,
                currentTime:vid[0].currentTime,
                duration:vid[0].duration,
                muted:vid[0].muted,
                volume:vid[0].volume,
                readyState:vid[0].readyState,
                played:vid[0].played,
                buffered:vid[0].buffered,
                playbackRate:vid[0].playbackRate,
                loop:vid[0].loop
            };
        };
        video.seek = function(newPosition){
            vid[0].currentTime = Math.min(vid[0].duration,Math.max(0,newPosition));
            if (vid[0].paused){
                video.play();
            }
        };
        video.muted = function(newState){
            if (newState != undefined){
                vid[0].muted = newState;
            }
            return vid[0].muted;
        };
        video.play = function(){
            var paintVideoFunc = function(){
                if (video.video.paused || video.video.ended){
                    return false;
                } else {
                    requestAnimationFrame(function(){
                        blit();
                        paintVideoFunc();
                    });
                    return true;
                }
            };
            video.video.addEventListener("play",function(){
                paintVideoFunc();
            },false);
            if (video.video.paused || video.video.ended){
                video.video.play();
            }
        };
        video.destroy = function(){
            video.video.removeAttribute("src");
            video.video.load();
        };
        video.pause = function(){
            if (!video.video.paused){
                video.video.pause();
            }
        };
    }
    if (!("bounds" in video)){
        calculateVideoBounds(video);
    }
}
function prerenderText(text){
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
var boardContent = {
    images:{},
    highlighters:{},
    texts:{},
    multiWordTexts:{},
    inks:{},
    themes:[]
};
var pressureSimilarityThreshold = 32,
    viewboxX = 0,
    viewboxY = 0,
    viewboxWidth = 80,//why wouldnt this be device size
    viewboxHeight = 60,
    contentOffsetX = 0,
    contentOffsetY = 0,
    boardWidth = 0,
    boardHeight = 0;

var visibleBounds = [];
var renders = {};

var renderInks = function(inks,canvasContext,rendered,viewBounds){
    if (inks != undefined){
        $.each(inks,function(i,ink){
            try{
                if(intersectRect(ink.bounds,viewBounds)){
                    drawInk(ink,canvasContext);
                    rendered.push(ink);
                }
            }
            catch(e){
                console.log("ink render failed for",e,ink.canvas,ink.identity,ink);
            }
        });
    }
};
var renderRichTexts = function(texts,canvasContext,rendered,viewBounds){
    if(texts){
        $.each(texts,function(i,text){
            if(text.doc){
                if(!text.bounds){
                    text.doc.invalidateBounds();
                }
                if(intersectRect(text.bounds,viewBounds)){
                    drawMultiwordText(text);
                    rendered.push(text);
                }
            }
        });
    }
};
var renderVideos = function(videos,canvasContext,rendered,viewBounds){
    if (videos){
        Modes.clearCanvasInteractables("videos");
        $.each(videos,function(i,video){
            if (intersectRect(video.bounds,viewBounds)){
                drawVideo(video,canvasContext);
                Modes.pushCanvasInteractable("videos",videoControlInteractable(video));
                rendered.push(video);
            }
        });
    }
};

var renderCanvasInteractables = function(canvasContext){
    _.each(Modes.canvasInteractables,function(category){
        _.each(category,function(interactable){
            if (interactable != undefined && "render" in interactable){
                canvasContext.save();
                canvasContext.lineWidth = 1;
                interactable.render(canvasContext);
                canvasContext.restore();
            }
        });
    });
};
var renderTexts = function(texts,canvasContext,rendered,viewBounds){
    $.each(texts,function(i,text){
        if(intersectRect(text.bounds,viewBounds)){
            drawText(text,canvasContext);
            rendered.push(text);
        }
    });
};
var renderImmediateContent = function(canvasContext,content,rendered,viewBounds){
    renderVideos(content.videos,canvasContext,rendered,viewBounds);
    renderInks(content.highlighters,canvasContext,rendered,viewBounds);
    renderTexts(content.texts,canvasContext,rendered,viewBounds);
    renderRichTexts(content.multiWordTexts,canvasContext,rendered,viewBounds);
    renderInks(content.inks,canvasContext,rendered,viewBounds);
    Progress.call("postRender");
};
var renderSelectionOutlines = function(canvasContext){
    var size = Modes.select.resizeHandleSize;
    canvasContext.save();
    canvasContext.lineWidth = 1;
    var multipleItems = [];
    _.forEach(Modes.select.selected,function(category){
        _.forEach(category,function(item){
            var bounds = item.bounds;
            var tl = worldToScreen(bounds[0],bounds[1]);
            var br = worldToScreen(bounds[2],bounds[3]);
            multipleItems.push([tl,br]);
            if(bounds){
                canvasContext.setLineDash([5]);
                canvasContext.strokeStyle = "blue";
                canvasContext.strokeRect(tl.x,tl.y,br.x-tl.x,br.y-tl.y);
            }
        });
    });
    var tb = Modes.select.totalSelectedBounds();
    if(multipleItems.length > 0){
        canvasContext.strokeStyle = "blue";
        canvasContext.strokeWidth = 3;
        canvasContext.strokeRect(tb.tl.x,tb.tl.y,tb.br.x - tb.tl.x,tb.br.y - tb.tl.y);
    }
    canvasContext.restore();
};
var renderContentIdentification = function(canvasContext,rendered){
    canvasContext.save();
    if(Modes.select.isAdministeringContent()){
        var visibleUsers = _.groupBy(rendered,"author");
        var pad = 3;
        _.each(visibleUsers,function(content,user){
            var userBounds = _.reduce(_.map(content,"bounds"),mergeBounds);
            var tl = worldToScreen(userBounds[0],userBounds[1]);
            canvasContext.strokeStyle = "black";
            canvasContext.lineWidth = 0.1;
            _.each(content,function(c){
                canvasContext.beginPath();
                canvasContext.moveTo(tl.x,tl.y);
                var cB = worldToScreen(c.bounds[0],c.bounds[1]);
                canvasContext.lineTo(cB.x,cB.y);
                canvasContext.stroke();
            });
            canvasContext.fillStyle = "black";
            canvasContext.fillRect(tl.x - pad,tl.y,canvasContext.measureText(user).width + pad * 2,14);
            canvasContext.fillStyle = "white";
            canvasContext.fillText(user,tl.x,tl.y+10);
        });
    }
    canvasContext.restore();
};
var renderSelectionGhosts = function(canvasContext){
    var zero = Modes.select.marqueeWorldOrigin;
    if(Modes.select.dragging){
        canvasContext.save();
        var s = scale();
        var x = Modes.select.offset.x - zero.x;
        var y = Modes.select.offset.y - zero.y;
        var screenOffset = worldToScreen(x,y);
        var relativeOffset = worldToScreen(0,0);
        canvasContext.translate(
            screenOffset.x - relativeOffset.x,
            screenOffset.y - relativeOffset.y);
        canvasContext.globalAlpha = 0.7;
        _.forEach(Modes.select.selected,function(category,name){
            _.forEach(category,function(item){
                switch(name){
                case "images":
                    drawImage(item,canvasContext);
                    break;
                case "videos":
                    drawVideo(item,canvasContext);
                    break;
                case "texts":
                    drawText(item,canvasContext);
                    break;
                case "multiWordTexts":
                    drawMultiwordText(item);
                    break;
                case "inks":
                    drawInk(item,canvasContext);
                    break;
                }
            });
        });
        canvasContext.restore();
    }
    else if(Modes.select.resizing){
        var totalBounds = Modes.select.totalSelectedBounds();
        var originalWidth = totalBounds.x2 - totalBounds.x;
        var originalHeight = totalBounds.y2 - totalBounds.y;
        var requestedWidth = Modes.select.offset.x - totalBounds.x;
        var requestedHeight = Modes.select.offset.y - totalBounds.y;
        var xScale = requestedWidth / originalWidth;
        var yScale = requestedHeight / originalHeight;
        var transform = function(x,y,func){
            canvasContext.save();
            canvasContext.globalAlpha = 0.7;
            canvasContext.translate(x,y);
            canvasContext.scale(xScale,yScale);
            canvasContext.translate(-x,-y);
            func();
            canvasContext.restore();
        };
        var noop = function(){};
        _.forEach(Modes.select.selected,function(category,name){
            _.forEach(category,function(item){
                var bounds = item.bounds;
                var screenPos = worldToScreen(bounds[0],bounds[1]);
                var x = screenPos.x;
                var y = screenPos.y;
                switch(name){
                case "images":
                    transform(x,y,function(){
                        drawImage(item,canvasContext);
                    });
                    break;
                case "videos":
                    transform(x,y,function(){
                        drawVideo(item,canvasContext);
                    });
                    break;
                case "texts":
                    transform(x,y,function(){
                        drawText(item,canvasContext);
                    });
                    break;
                case "multiWordTexts":
                    if(Modes.select.aspectLocked){
                        transform(x,y,function(){
                            drawMultiwordText(item,canvasContext);
                        });
                    }
                    else{
                        canvasContext.save();
                        canvasContext.translate(x,y);
                        canvasContext.globalAlpha = 0.7;
                        var s = scale();
                        canvasContext.scale(s,s);
                        var scaledText = carota.editor.create({
                            querySelector:function(){
                                return {
                                    addEventListener:noop
                                }
                            },
                            handleEvent:noop
                        }, canvasContext, noop, _.cloneDeep(item));
                        scaledText.position = {x:bounds[0],y:bounds[1]};
                        scaledText.load(item.doc.save());
                        delete scaledText.canvas;
                        var fullRange = scaledText.documentRange();
                        var nominatedWidth = Math.max(
                            item.doc.width() * xScale,
                            Modes.text.minimumWidth / scale()
                        );
                        scaledText.width(nominatedWidth);
                        scaledText.updateCanvas();
                        carota.editor.paint(board[0],scaledText);
                        canvasContext.restore();
                    }
                    break;
                case "inks":
                    transform(x,y,function(){
                        drawInk(item,canvasContext);
                    });
                    break;
                }
            });
        });
    }
};

var renderImages = function(images,canvasContext,rendered,viewBounds){
    $.each(images,function(id,image){
        try{
            if(intersectRect(image.bounds,viewBounds)){
                drawImage(image,canvasContext);
                rendered.push(image);
            }
        }
        catch(e){
            console.log("image render failed for",e,image.identity,image);
        }
    });
};
function render(content,hq,incCanvasContext){
    var bucket = Math.floor(new Date() / 1000);
    if(!renders[bucket]){
        renders[bucket] = 0;
    }
    renders[bucket]++;
    try {
        var renderStart = new Date().getTime();
        var canvasContext = incCanvasContext || boardContext;
        clearBoard(canvasContext,{x:0,y:0,w:boardWidth,h:boardHeight});
        if(content){
            var startMark = Date.now();
            try{
                var viewBounds = TweenController.immediateView();
                visibleBounds = [];
                var rendered = [];
                var loadedCount = 0;
                var loadedLimit = Object.keys(content.images).length;
                renderImages(content.images,canvasContext,rendered,viewBounds);
                renderImmediateContent(canvasContext,content,rendered,viewBounds);
                renderSelectionOutlines(canvasContext);
                renderSelectionGhosts(canvasContext);
                renderContentIdentification(canvasContext,rendered);
                renderCanvasInteractables(canvasContext);
                renderTint(canvasContext,{x:0,y:0,w:boardWidth,h:boardHeight});
            }
            catch(e){
                console.log("Render exception",e);
            }
            Progress.call("onViewboxChanged");
        }
        if ("HealthChecker" in window){
            HealthChecker.addMeasure("render",true,new Date().getTime() - renderStart);
        }
    } catch(e){
        console.log(e);
        if ("HealthChecker" in window){
            HealthChecker.addMeasure("render",false,new Date().getTime() - renderStart);
        }
        throw e;
    }
}
var blit = function(canvasContext,content){
    try {
        render(content == undefined ? boardContent : content,false,canvasContext == undefined ? boardContext : canvasContext);
    } catch(e){
        console.log("exception in render:",e);
    }
};
blit = _.throttle(blit,30,{leading:false,trailing:true});
function pica(value){
    return value / 128;
}
function unpica(value){
    return Math.floor(value * 128);
}
function px(value){
    return sprintf("%spx",value);
}
function unpix(str){
    return str.slice(0,str.length-2);
}
function updateConversationHeader(){
    var groupV = $(".currentGroupTitle").empty();
    var group = Conversations.getCurrentGroup();
    if(group.length){
        groupV.text(sprintf("Group %s of",_.join(_.map(group,"title"),",")));
    }
    $(".currentConversationTitle").text(Conversations.getCurrentConversation().title);
    console.log("Updated conversation header");
}
function renderTint(canvasContext,rect){
    if ('HealthCheckViewer' in window && !HealthCheckViewer.healthy()) {
        var r = rect == undefined ? {x: 0, y: 0, w: boardWidth, h: boardHeight} : rect;
        canvasContext.save();
        canvasContext.fillStyle = "rgba(255, 0, 0, 0.1)";
        canvasContext.fillRect(r.x, r.y, r.w, r.h);
        canvasContext.restore();
    }
}
function clearBoard(incContext,rect){
    try {
        var ctx = incContext == undefined ? boardContext : incContext;
        var r = rect == undefined ? {x:0,y:0,w:boardWidth,h:boardHeight} : rect;
        ctx.clearRect(r.x,r.y,r.w,r.h);
    } catch(e){
        console.log("exception while clearing board:",e,incContext,rect);
    }
}
var IncludeView = (function(){
    var fitToRequested = function(incX,incY,incW,incH,notFollowable){//Include at least this much content in your view
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
        var dim = {width:w,height:h,x:x,y:y};
        var constrained = Zoom.constrainRequestedViewbox(dim);
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
        notFollowable = _.every([incX,incY,incW,incH],function(elem){
            return elem == undefined;
        }) || notFollowable;

        TweenController.zoomAndPanViewbox(constrained.x,constrained.y,targetWidth,targetHeight,undefined,!shouldUpdateRequestedViewbox,notFollowable);
        Progress.call("onViewboxChanged");
    };
    return {
        specific:function(x,y,w,h,notFollowable){
            return fitToRequested(x,y,w,h,notFollowable);
        },
        "default":function(){
            return fitToRequested();
        }
    };
})();
