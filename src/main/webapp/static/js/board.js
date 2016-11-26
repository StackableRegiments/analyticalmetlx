function setupStatus(){
    pending = {};
    var display = $("#strokesPending");
    var latency = $("#latency");
    var recentLatency = 0;
    window.progressFuncs = {};
    var cancelFuncs = {};
    window.updateStrokesPending = function(delta,identity){
        if(delta > 0){
            pending[identity] = Date.now();
        }
        else if(identity in pending){
            recentLatency = Date.now() - pending[identity];
            delete pending[identity];
        }
        display.text(Object.keys(pending).length);
        latency.text(recentLatency);
    }
    window.registerTracker = function(id,progressFunc){
        if(progressFunc){
            progressFuncs[id] = progressFunc;
        }
        else{
            console.log("No tracker provided against",id);
        }
    }
    window.updateTracking = function(id){
        if(id in progressFuncs){
            var func = progressFuncs[id];
            delete progressFuncs[id];
            func();
        }
        else{
            console.log("updateTracking problem: Nobody is listening for ",id);
        }
    }
    window.stopTracking = function(id){
        if(id in cancelFuncs){
            cancelFuncs[id]();
        }
        delete progressFuncs[id];
        delete cancelFuncs[id];
    }
    window.trackerFrom = function(phrase){
        return _.filter(_.keys(progressFuncs), function(key){
            return _.endsWith(phrase,sprintf("_from:%s",key));
        });
    }
}
function strokeCollected(points){
    if(points.length > 0) {
        var currentSlide = Conversations.getCurrentSlideJid();
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
        ink.audiences = Conversations.getCurrentGroup().map(audienceToStanza);

        calculateInkBounds(ink);
        prerenderInk(ink);
        if(ink.isHighlighter){
            boardContent.highlighters[ink.identity] = ink;
        }
        else{
            boardContent.inks[ink.identity] = ink;
        }
        sendInk(ink);
    }
}
function batchTransform(){
    var currentSlide = Conversations.getCurrentSlideJid();
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
        videoIds:[],
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
function sendDirtyInk(ink){
    var currentSlide = Conversations.getCurrentSlideJid();
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
function sendInk(ink){
    updateStrokesPending(1,ink.identity);
    sendStanza(ink);
}
function hexToRgb(hex) {
    if(typeof hex == "object" && hex.alpha) {
        return Colors.getColorForColorParts(hex.alpha,hex.red,hex.green,hex.blue);
    } else if (typeof hex == "object" && hex[0] && hex[1] && typeof hex[0] == "string" && typeof hex[1] == "number"){
        return hex;
    } else if(typeof hex == "string") {
        return Colors.getColorObjForHex(hex);
    } else if(typeof hex == "array") {
        return hex;
    } else {
        return Colors.getDefaultColorObj();
    }
}
function audienceToStanza(a){
    return {
        domain:"slide",
        'type':"groupWork",
        action:"whitelist",
        name:a
    };
}
function partToStanza(p){
    var defaults = carota.runs.defaultFormatting;
    var color = hexToRgb(p.color || defaults.color);
    return {
        text:p.text,
        color:color,
        size:parseFloat(p.size) || parseFloat(defaults.size),
        font:p.font || defaults.font,
        justify:p.align || defaults.align,
        bold:p.bold === true,
        underline:p.underline === true,
        italic:p.italic === true
    };
}

function richTextEditorToStanza(t){
    if(!t.bounds) t.doc.invalidateBounds();
    var bounds = t.bounds;
    var text = t.doc.save();
    if (t.slide == undefined){
        t.slide = Conversations.getCurrentSlideJid();
    };
    if (t.author == undefined){
        t.author = UserSettings.getUsername();
    };
    if (t.target == undefined){
        t.target = "presentationSpace";
    };
    if (t.privacy == undefined){
        t.privacy = Privacy.getCurrentPrivacy();
    };

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
        requestedWidth:t.doc.width(),
        width:t.doc.width(),
        height:bounds[3]-bounds[1],
        words:text.map(partToStanza),
        audiences:Conversations.getCurrentGroup().map(audienceToStanza)
    }
}
function sendRichText(t){
    Modes.text.echoesToDisregard[t.identity] = true;
    var stanza = richTextEditorToStanza(t);
    sendStanza(stanza);
}
var stanzaHandlers = {
    ink:inkReceived,
    dirtyInk:dirtyInkReceived,
    move:moveReceived,
    moveDelta:transformReceived,
    image:imageReceived,
    video:videoReceived,
    text:textReceived,
    multiWordText:richTextReceived,
    command:commandReceived,
    submission:submissionReceived,
    attendance:attendanceReceived,
    file:fileReceived,
    theme:themeReceived
};
function themeReceived(theme){
    boardContent.themes.push(theme);
    Progress.call("themeReceived");
}
function fileReceived(file){
    //doing nothing with files yet.
}
function attendanceReceived(attendance){
    //doing nothing with attendances for the moment.
}
function submissionReceived(submission){
    Submissions.processSubmission(submission);
}
function commandReceived(c){
    if(c.command == "/TEACHER_VIEW_MOVED"){
        if(c.parameters[5] != Conversations.getCurrentSlide().id.toString()){
            return;
        }
        var ps = _.slice(c.parameters,0,6).map(parseFloat);
        if(_.some(ps,isNaN)){
            console.log("Can't follow teacher to",c);
            return;
        }
        if(ps[4] == DeviceConfiguration.getIdentity()){
            return;
        }
        if(Conversations.getIsSyncedToTeacher()){
            console.log("teacherViewMoved",c);
            var f = function(){
                var controllerIdentity = c.parameters[4];
                var slide = ps[5];
                var autoZoomingMode = c.parameters[6];
                if (slide == Conversations.getCurrentSlide().id.toString()){
                    if (autoZoomingMode == "true"){
                        zoomToFit();
                    } else {
                        zoomToPage();
                        TweenController.zoomAndPanViewbox(ps[0],ps[1],ps[2],ps[3],function(){},false,true);
                    }
                }
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
function richTextReceived(t){
    if(t.identity in Modes.text.echoesToDisregard) return;
    if(isUsable(t)){
        WorkQueue.enqueue(function(){
            Modes.text.editorFor(t).doc.load(t.words);
            blit();
        });
    }
}
function textReceived(t){
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
function receiveMeTLStanza(stanza){
    Progress.call("stanzaReceived",[stanza]);
}
function actOnReceivedStanza(stanza){
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
function transformReceived(transform){
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
        $.each(transform.videoIds,function(i,id){
            boardContent.videos[id].privacy = p;
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
        $.each(transform.videoIds,function(i,id){
            deleteVideo(p,id);
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
        var relevantVideos = [];
        $.each(transform.inkIds,function(i,id){
            relevantInks.push(boardContent.inks[id]);
            relevantInks.push(boardContent.highlighters[id]);
        });
        $.each(transform.imageIds,function(i,id){
            relevantImages.push(boardContent.images[id]);
        });
        $.each(transform.videoIds,function(i,id){
            relevantVideos.push(boardContent.videos[id]);
        });
        $.each(transform.textIds,function(i,id){
            relevantTexts.push(boardContent.texts[id]);
        });
        $.each(transform.multiWordTextIds,function(i,id){
            if(id in Modes.text.echoesToDisregard) return;
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
            $.each(relevantVideos,function(i,video){
                if (video != undefined && "x" in video && "y" in video){
                    updateRect(point(video.x,video.y));
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
        var transformVideo = function(index,video){
            if (video != undefined){
                video.width = video.width * transform.xScale;
                video.height = video.height * transform.yScale;

                var internalX = video.x - totalBounds.x;
                var internalY = video.y - totalBounds.y;
                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));
                video.x = video.x + offsetX;
                video.y = video.y + offsetY;

                calculateVideoBounds(video);
                transformBounds.incorporateBounds(video.bounds);
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
                var newWidth = (text.width || text.requestedWidth) * transform.xScale;
                text.requestedWidth = newWidth;
                text.width = text.requestedWidth;
                text.doc.width(text.width);
                _.each(text.words,function(word){
                    word.size = word.size * transform.xScale;
                });

                var internalX = text.x - totalBounds.x;
                var internalY = text.y - totalBounds.y;

                var offsetX = -(internalX - (internalX * transform.xScale));
                var offsetY = -(internalY - (internalY * transform.yScale));
                text.doc.position = {x:text.x + offsetX,y:text.y + offsetY};
                text.doc.load(text.words);
                transformBounds.incorporateBounds(text.bounds);
            }
        };
        $.each(relevantInks,transformInk);
        $.each(relevantImages,transformImage);
        $.each(relevantVideos,transformVideo);
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
        $.each(transform.videoIds,function(i,id){
            var video = boardContent.videos[id];
            console.log("Shifting video",video.x,video.y);
            video.x += transform.xTranslate;
            video.y += transform.yTranslate;
            calculateVideoBounds(video);
            console.log("Shifting video",video.x,video.y);
            transformBounds.incorporateBounds(video.bounds);
        });
        $.each(transform.imageIds,function(i,id){
            var image = boardContent.images[id];
            console.log("Shifting image",image.x,image.y);
            image.x += transform.xTranslate;
            image.y += transform.yTranslate;
            calculateImageBounds(image);
            console.log("Shifting image",image.x,image.y);
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
            if(id in Modes.text.echoesToDisregard) return;
            var text = boardContent.multiWordTexts[id];
            var doc = text.doc;
            doc.position.x += transform.xTranslate;
            doc.position.y += transform.yTranslate;
            text.x = doc.position.x;
            text.y = doc.position.y;
            text.doc.invalidateBounds();
            transformBounds.incorporateBounds(text.bounds);
        });
    }
    transformBounds.incorporateBoardBounds();
    updateStatus(sprintf("%s %s %s %s %s %s",
                         op,
                         transform.imageIds.length,
                         transform.textIds.length,
                         transform.multiWordTextIds.length,
                         transform.inkIds.length,
                         transform.videoIds.length));
    _.each(trackerFrom(transform.identity),function(tracker){
        updateTracking(tracker);
    });
    blit();
}
function moveReceived(move){
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
function deleteInk(inks,privacy,id){
    if(id in boardContent[inks]){
        var ink = boardContent[inks][id];
        if(ink.privacy.toUpperCase() == privacy.toUpperCase()){
            delete boardContent[inks][id];
            Progress.call("onCanvasContentDeleted",[ink]);
        }
    }
}
function deleteImage(privacy,id){
    var image = boardContent.images[id];
    if(image.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.images[id];
        Progress.call("onCanvasContentDeleted",[image]);
    }
}
function deleteVideo(privacy,id){
    var video = boardContent.videos[id];
    if(video.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.videos[id];
        Progress.call("onCanvasContentDeleted",[video]);
    }
}
function deleteText(privacy,id){
    var text = boardContent.texts[id];
    if(text.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.texts[id];
        Progress.call("onCanvasContentDeleted",[text]);
    }
}
function deleteMultiWordText(privacy,id){
    var text = boardContent.multiWordTexts[id];
    if(text.privacy.toUpperCase() == privacy.toUpperCase()){
        delete boardContent.multiWordTexts[id];
        Progress.call("onCanvasContentDeleted",[text]);
    }
}
function dirtyInkReceived(dirtyInk){
    var id = dirtyInk.identity;
    var deletePrivacy = dirtyInk.privacy;
    deleteInk("highlighters",deletePrivacy,id);
    deleteInk("inks",deletePrivacy,id);
    updateStatus(sprintf("Deleted ink %s",id));
    blit();
}
function isInClearSpace(bounds){
    return !_.some(visibleBounds,function(onscreenElement){
        return intersectRect(onscreenElement,bounds);
    });
}
function screenBounds(worldBounds){
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
function scaleCanvas(incCanvas,w,h,disableImageSmoothing){
    if (w >= 1 && h >= 1){
        var canvas = $("<canvas />");
        canvas.width = w;
        canvas.height = h;
        canvas.attr("width",w);
        canvas.attr("height",h);
        canvas.css({
            width:px(w),
            height:px(h)
        });
        var ctx = canvas[0].getContext("2d");
        /*
         ctx.mozImageSmoothingEnabled = !disableImageSmoothing;
         ctx.webkitImageSmoothingEnabled = !disableImageSmoothing;
         ctx.msImageSmoothingEnabled = !disableImageSmoothing;
         ctx.imageSmoothingEnabled = !disableImageSmoothing;
         */
        ctx.drawImage(incCanvas,0,0,w,h);
        return canvas[0];
    } else {
        return incCanvas;
    }
}
var mipMappingEnabled = true;
function multiStageRescale(incCanvas,w,h,stanza){
    if (mipMappingEnabled){
        stanza = stanza == undefined ? {} : stanza;
        if (!("mipMap" in stanza)){
            stanza.mipMap = {};
        }
        var mm = stanza.mipMap;
        var sf = 0.5;
        var iw = incCanvas.width;
        var ih = incCanvas.height;
        var save = true;

        var iwSize = Math.floor(iw);

        if (w >= 1 && iw >= 1 && w < iw){ //shrinking
            var sdw = iw * sf;
            var sdh = ih * sf;
            if (sdw < w){
                return incCanvas;
            } else {
                var key = Math.floor(sdw);
                if (!(key in mm)){
                    var newCanvas = scaleCanvas(incCanvas,sdw,sdh);
                    mm[key] = newCanvas;
                }
                return multiStageRescale(mm[key],w,h,stanza);
            }
        } else {
            return incCanvas;
        }

        /*
         if (w >= 1 && h >= 1 && iw != w && ih != h && w < iw && h < ih){
         var sdw = iw;
         var sdh = ih;
         if (w > sdw){ // growing x
         sdw = w;
         } else if (w < sdw){ // shrinking x
         sdw = sdw * sf;
         if (w > sdw){
         save = false;
         sdw = w;
         }
         }
         if (h > sdh){ // growing y
         sdh = h;
         } else if (h < sdh){ // shrinking y
         sdh = sdh * sf;
         if (h > sdh){
         save = false;
         sdh = h;
         }
         }
         var key = Math.floor(sdw);
         console.log("rescaling:",w,h,iw,ih,sdw,sdh,key,iwSize);
         if (key == iwSize){
         return incCanvas;
         } else {
         if (key in mm){
         console.log("returning cached value:",key);
         return multiStageRescale(mm[key],w,h,stanza);
         } else {
         var newCanvas = scaleCanvas(incCanvas,sdw,sdh);
         if (save){
         mm[key] = newCanvas;
         }
         console.log("generating mipmap:",w,h,sdw,sdh,key);
         return multiStageRescale(newCanvas,w,h,stanza);
         }
         }
         } else {
         console.log("returning:",w,h,iwSize);
         return incCanvas;
         }
         */
    } else {
        return incCanvas;
    }
}

function drawImage(image,incCanvasContext){
    var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
    try{
        if (image.canvas != undefined){
            var sBounds = screenBounds(image.bounds);
            visibleBounds.push(image.bounds);
            if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
                var borderW = sBounds.screenWidth * 0.10;
                var borderH = sBounds.screenHeight * 0.10;
                canvasContext.drawImage(multiStageRescale(image.canvas,sBounds.screenWidth,sBounds.screenHeight,image), sBounds.screenPos.x - (borderW / 2), sBounds.screenPos.y - (borderH / 2), sBounds.screenWidth + borderW ,sBounds.screenHeight + borderH);
            }
        }
    }
    catch(e){
        console.log("drawImage exception",e);
    }
}

function drawMultiwordText(item){
    Modes.text.draw(item);
}
function drawText(text,incCanvasContext){
    var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
    try{
        var sBounds = screenBounds(text.bounds);
        visibleBounds.push(text.bounds);
        if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
            canvasContext.drawImage(multiStageRescale(text.canvas,sBounds.screenWidth,sBounds.screenHeight,text),
                                    sBounds.screenPos.x,
                                    sBounds.screenPos.y,
                                    sBounds.screenWidth,
                                    sBounds.screenHeight);
        }
    }
    catch(e){
        console.log("drawText exception",e);
    }
}
function drawInk(ink,incCanvasContext){
    var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
    var sBounds = screenBounds(ink.bounds);
    visibleBounds.push(ink.bounds);
    if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
        canvasContext.drawImage(multiStageRescale(ink.canvas,sBounds.screenWidth,sBounds.screenHeight,ink),
                                sBounds.screenPos.x,sBounds.screenPos.y,
                                sBounds.screenWidth,sBounds.screenHeight);
    }
}
function drawVideo(video,incCanvasContext){
    var canvasContext = incCanvasContext == undefined ? boardContext : incCanvasContext;
    var sBounds = screenBounds(video.bounds);
    visibleBounds.push(video.bounds);
    if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
        canvasContext.drawImage(video.video,
                                sBounds.screenPos.x,sBounds.screenPos.y,
                                sBounds.screenWidth,sBounds.screenHeight);
    }
}
function videoReceived(video){
    if(isUsable(video)){
        calculateVideoBounds(video);
        incorporateBoardBounds(video.bounds);
        boardContent.videos[video.identity] = video;
        prerenderVideo(video);
        WorkQueue.enqueue(function(){
            if(isInClearSpace(video.bounds)){
                try {
                    drawVideo(video);
                    Modes.pushCanvasInteractable("videos",videoControlInteractable(video));
                } catch(e){
                    console.log("drawVideo exception",e);
                }
                return false;
            }
            else{
                console.log("Rerendering video in contested space");
                return true;
            }
        });
    }
}
function imageReceived(image){
    if(isUsable(image)){
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
}
function inkReceived(ink){
    if(isUsable(ink)){
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
}
function takeControlOfViewbox(){
    delete Progress.onBoardContentChanged.autoZooming;
    UserSettings.setUserPref("followingTeacherViewbox",true);
}
function zoomToFit(followable){
    Progress.onBoardContentChanged.autoZooming = zoomToFit;
    requestedViewboxWidth = boardContent.width;
    requestedViewboxHeight = boardContent.height;
    IncludeView.specific(boardContent.minX,boardContent.minY,boardContent.width,boardContent.height,followable);
}
function zoomToOriginal(followable){
    takeControlOfViewbox();
    var oldReqVBH = requestedViewboxHeight;
    var oldReqVBW = requestedViewboxWidth;
    requestedViewboxWidth = boardWidth;
    requestedViewboxHeight = boardHeight;
    IncludeView.specific(0,0,boardWidth,boardHeight,followable);
}
function zoomToPage(followable){
    takeControlOfViewbox();
    var oldReqVBH = requestedViewboxHeight;
    var oldReqVBW = requestedViewboxWidth;
    requestedViewboxWidth = boardWidth;
    requestedViewboxHeight = boardHeight;
    var xPos = viewboxX + ((oldReqVBW - requestedViewboxWidth) / 2);
    var yPos = viewboxY + ((oldReqVBH - requestedViewboxHeight) / 2);
    IncludeView.specific(xPos,yPos,boardWidth,boardHeight,followable);
}
function receiveS2C(id,markup){
    try{
        var m = $(unescape(markup));
        m.addClass("s2cMessage").appendTo("body");
    }
    catch(e){
        console.log("receiveS2C exception:",e);
    }
}
