var renderOffsetX = 0;
var renderOffsetY = 0;
var loadSlidesAtNativeZoom;
var startMark;
var requestedViewboxWidth = 320;
var requestedViewboxHeight = 240;

function loadSlide(jid){
    console.log("loadSlide",jid);
    startMark = Date.now();
    $("#targetSlide").text(sprintf("Loading slide %s",jid));
    showBackstage("loadingSlide");
    moveToSlide(jid.toString());
}
function receiveHistory(json){
    try{
        var historyDownloadedMark, prerenderInkMark, prerenderHighlightersMark,prerenderTextMark,imagesLoadedMark, historyDecoratorsMark, blitMark;
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
        $.each(postits(),function(i,g){
            measurePostit(g);
        });
        prerenderTextMark = Date.now();
        boardContent.width = boardContent.maxX - boardContent.minX;
        boardContent.height = boardContent.maxY - boardContent.minY;
        var startRender = function(){
            fit();
            imagesLoadedMark = Date.now();
            historyDecoratorsMark = Date.now();
            if(boardContent.minX == Infinity || boardContent.minY == Infinity){
                boardContent.minX = 0;
                boardContent.minY = 0;
            }
            if(loadSlidesAtNativeZoom){
                requestedViewboxWidth = boardWidth;
                requestedViewboxHeight = boardHeight;
                includeView(0,0,boardWidth,boardHeight);
            }
            else{
                requestedViewboxWidth = boardWidth;
                requestedViewboxHeight = boardHeight;
                includeView(boardContent.minX,boardContent.minY,boardContent.width,boardContent.height);
            }
            hideBackstage();
            clearBoard();
            render(boardContent);
            blitMark = Date.now();
            $("#timingLog").text(sprintf("receiveHistory %s timing:\n%s milis download\n%s milis prerender ink\n%s milis prerender highlighters\n%s milis prerender text\n%s milis imagesLoaded\n%s milis history decorators\n%s milis blit\n%s milis total\n%s milis bench",
                                         Date.now(),
                                         historyDownloadedMark - startMark,
                                         prerenderInkMark - historyDownloadedMark,
                                         prerenderHighlightersMark - prerenderInkMark,
                                         prerenderTextMark - prerenderHighlightersMark,
                                         imagesLoadedMark - prerenderTextMark,
                                         historyDecoratorsMark - imagesLoadedMark,
                                         blitMark - prerenderTextMark,
                                         blitMark - startMark,
                                         blitMark - historyDownloadedMark
                                        ));
            Progress.call("historyReceived",[json]);
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
                    if(image.width == 0){
                        image.width = dataImage.naturalWidth;
                    }
                    if(image.height == 0){
                        image.height = dataImage.naturalHeight;
                    }
                    loaded += 1;
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
}
var lineDrawingThreshold = 25;
function incorporateBoardBounds(bounds){
    boardContent.minX = Math.min(boardContent.minX,bounds[0]);
    boardContent.minY = Math.min(boardContent.minY,bounds[1]);
    boardContent.maxX = Math.max(boardContent.maxX,bounds[2]);
    boardContent.maxY = Math.max(boardContent.maxY,bounds[3]);
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
    var boundsOk = !(_.any(element.bounds,function(p){
        return isNaN(p) || p > boardLimit || p < -boardLimit;
    }));
    var sizeOk = "size" in element? !isNaN(element.size) : true
    var textOk =  "text" in element? element.text.length > 0 : true;
    return boundsOk && sizeOk && textOk;
}
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
function prerenderInk(ink){
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
    canvas.width = ink.bounds[2] - ink.bounds[0] + ink.thickness + privacyOffset * 2;
    canvas.height = ink.bounds[3] - ink.bounds[1] + ink.thickness + privacyOffset * 2;
    $(canvas).css({
        width:px(canvas.width),
        height:px(canvas.height)
    });
    var points = ink.points;
    var contentOffsetX = -1 * ((ink.minX - ink.thickness / 2) - privacyOffset);
    var contentOffsetY = -1 * ((ink.minY - ink.thickness / 2) - privacyOffset);
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
    context.lineWidth = ink.thickness * pica(pr);
    if(ink.thickness > lineDrawingThreshold){
        renderHull(ink);
        return true;
    }
    else{
        for(p = 0; p < points.length; p += 3){
            newPr = points[p+2];
            if(Math.abs(newPr - pr) < pressureSimilarityThreshold){
                context.moveTo(x,y);
                x = points[p]+contentOffsetX;
                y = points[p+1]+contentOffsetY;
                context.lineTo(x,y);
            }
            else{
                context.stroke();
                context.beginPath();
                context.moveTo(x,y);
                x = points[p]+contentOffsetX;
                y = points[p+1]+contentOffsetY;
                context.lineWidth = pica(newPr * ink.thickness)
                pr = points[p+2];
                context.lineTo(x,y);
            }
        }
        context.stroke();
        return true;
    }
    return false;
}
function alertCanvas(canvas,label){
    var url = canvas.toDataURL();
    window.open(url,label,sprintf("width=%s, height=%s",canvas.width,canvas.height));
}
var precision = Math.pow(10,3);
var round = function(n){
    return Math.round(n * precision) / precision;
}
function calculateImageBounds(image){
    image.bounds = [image.x,image.y,image.x + image.width,image.y + image.height];
}
function calculateImageSource(image){
    var slide = image.privacy.toUpperCase() == "PRIVATE" ? sprintf("%s%s",image.slide,image.author) : image.slide;
    return sprintf("/proxy/%s/%s",slide,encodeURIComponent(image.identity));
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
function scale(){
    return Math.min(boardWidth / viewboxWidth, boardHeight / viewboxHeight);
}
function measurePostit(postit){
    var bs = _.pluck(_.filter(postit.inkIds.map(function(id){
        return boardContent.inks[id] || boardContent.highlighters[id];
    }),_.identity),"bounds");
    var ls = _.pluck(bs,"0");
    var ts = _.pluck(bs,"1");
    var rs = _.pluck(bs,"2");
    var bottoms = _.pluck(bs,"3");
    var x = average(ls);
    var y = average(ts);
    var x2 = average(rs);
    var y2 = average(bottoms);
    var minX = Math.min.apply(Math,ls);
    var minY = Math.min.apply(Math,ts);
    var maxX = Math.max.apply(Math,rs);
    var maxY = Math.max.apply(Math,bottoms);
    var b = [x,y,x2,y2];
    if(!(_.any(b,isNaN))){
        b.width = maxX - minX;
        b.height = maxY - minY;
        b.centerX = (x2 + x) / 2;
        b.centerY = (y2 + y) / 2;
        b.minX = minX;
        b.minY = minY;
        b.maxX = maxX;
        b.maxY = maxY;
        postit.bounds = b;
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
    $.each(text.text,function(i,c){
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
    canvas.width = text.bounds[2] - text.bounds[0];
    canvas.height = text.bounds[3] - text.bounds[1];
    text.height = canvas.height;
    if(text.privacy.toUpperCase() == "PRIVATE"){
        var b = text.bounds;
        context.globalAlpha = 0.2;
        context.fillStyle = "red";
        context.fillRect(
            0,0,
            canvas.width,
            canvas.height);
        context.globalAlpha = 1.0;
    }
    context.fillStyle = text.color[0];
    context.textBaseline = "top";
    context.font = text.font;
    $.each(text.runs,function(ri,run){
        var _yOffset = ri * text.size * 1.25;
        context.fillText(run,
                         contentOffsetX,
                         contentOffsetY + _yOffset,
                         text.width);
    });
    incorporateBoardBounds(text.bounds);
}
var boardContent = {
    images:{},
    texts:{},
    inks:{}
};
var pressureSimilarityThreshold = 32,
    viewboxX = 0,
    viewboxY = 0,
    viewboxWidth = 80,
    viewboxHeight = 60,
    contentOffsetX = 0,
    contentOffsetY = 0,
    boardWidth = 0,
    boardHeight = 0;

var visibleBounds = [];
function render(content){
    if(content){
        var startMark = Date.now();
        var fitMark,imagesRenderedMark,highlightersRenderedMark,textsRenderedMark,inksRenderedMark,renderDecoratorsMark;
        try{
            var visibleElements = {
                images:{},
                highlighters:{},
                texts:{},
                inks:{}
            };
            var viewBounds = [viewboxX,viewboxY,viewboxX+viewboxWidth,viewboxY+viewboxHeight];
            visibleBounds = [];
            var scale = boardContent.maxX / viewboxWidth;
            var renderInks = function(inks,category){
                $.each(inks,function(i,ink){
                    try{
                        if(intersectRect(ink.bounds,viewBounds)){
                            visibleElements[category][ink.identity] = ink;
                            drawInk(ink);
                        }
                    }
                    catch(e){
                        console.log("ink render failed for",e,ink.canvas,ink.identity,ink);
                    }
                });
            }
            var renderImmediateContent = function(){
                renderInks(boardContent.highlighters,"highlighters");
                highlightersRenderedMark = Date.now();
                $.each(content.texts,function(i,text){
                    if(intersectRect(text.bounds,viewBounds)){
                        visibleElements["texts"][text.identity] = text;
                        drawText(text);
                    }
                });
                textsRenderedMark = Date.now();
                renderInks(boardContent.inks,"inks");
                inksRenderedMark = Date.now();
                Progress.call("postRender");
                renderDecoratorsMark = Date.now();
            }
            var loadedCount = 0;
            var loadedLimit = Object.keys(content.images).length;
            clearBoard();
            fitMark = Date.now();
            $.each(content.images,function(id,image){
                try{
                    if(intersectRect(image.bounds,viewBounds)){
                        visibleElements["images"][image.identity] = image;
                        drawImage(image);
                    }
                }
                catch(e){
                    console.log("image render failed for",e,image.identity,image);
                }
            });
            imagesRenderedMark = Date.now();
            renderImmediateContent();
            boardContext.strokeStyle = "red";
            var p1 = worldToScreen(viewBounds[0],viewBounds[1]);
            var p2 = worldToScreen(viewBounds[2],viewBounds[3]);
            boardContext.strokeRect(p1.x,p1.y,p2.x,p2.y);
            $("#renderTimingLog").text(sprintf("Render %s timing:\n%s milis fit\n%s milis images\n%s milis highlighters\n%s milis texts\n%s milis inks\n%s milis renderDecorators\n%s milis total",
                                               Date.now(),
                                               fitMark - startMark,
                                               imagesRenderedMark - fitMark,
                                               highlightersRenderedMark - imagesRenderedMark,
                                               textsRenderedMark - highlightersRenderedMark,
                                               inksRenderedMark - textsRenderedMark,
                                               renderDecoratorsMark - inksRenderedMark,
                                               renderDecoratorsMark - startMark));
            boardContent.visibleElements = visibleElements;
        }
        catch(e){
            console.log("Render exception",e);
        }
    }
}
function lightBlueGradient(context,width,height){
    var bgd = context.createLinearGradient(0,0,0,height);
    bgd.addColorStop(0,"#F5FAFF");
    bgd.addColorStop(0.61,"#D0DEEF");
    bgd.addColorStop(0.40,"#CADAED");
    bgd.addColorStop(1,"#E7F2FF");
    return bgd;
}
function monashBlueGradient(context,width,height){
    var bgd = context.createLinearGradient(0,0,0,height);
    bgd.addColorStop(1-0,"#C5D5F6");
    bgd.addColorStop(1-0.35,"#87ACF2");
    bgd.addColorStop(1-0.40,"#7AA3F4");
    bgd.addColorStop(1-1,"#C5D5F6");
    return bgd;
}
var blit = function(){
    render(boardContent);
}
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
    $("#heading").text(Conversations.getCurrentConversation().title);
}
var defaultPrefs = {
    toolModeSize:60,
    subModeSize:80,
    thumbnailSize:100,
    loadSlidesAtNativeZoom:false
};
function getUserPref(key){
    if(localStorage){
        return localStorage[key] || defaultPrefs[key];
    }
}
function setUserPref(key,value){
    if(localStorage){
        localStorage[key] = value;
    }
}
function clearBoard(){
    boardContext.clearRect(0,0,boardWidth,boardHeight);
}
var includeView = (function(){
    var prevX, prevY, prevWidth, prevHeight;
    return function(x,y,w,h){//Include at least this much content in your view
        prevWidth = prevWidth || w;
        prevHeight = prevWidth || h;
        var hr = boardHeight / h;
        var wr = boardWidth / w;
        if(wr > hr){
            viewboxHeight = h;
            viewboxWidth = w * wr / hr;
        }
        else{
            viewboxHeight = h / wr * hr;
            viewboxWidth = w;
        }
        viewboxX = x;
        viewboxY = y;
        prevX = x;
        prevY = y;
        prevWidth = w;
        prevHeight = h;
        reportViewboxMoved();
        console.log(sprintf("included %s,%s %s,%s -> %s,%s %s,%s",x,y,w,h,viewboxX,viewboxY,viewboxWidth,viewboxHeight));
    }
})();
