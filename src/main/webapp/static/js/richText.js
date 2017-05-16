var RichText = (function(){
    var ergonomics = {
        cursorWidth:3,
        cursorOverlap:1.5,
        comfortableColumnWidth:28,
        lineHeightRatio:1.8,
        letterSpacing:String.fromCharCode(8202)
    };
    var boxes = {};
    var cursor = {
        head:[],
        tail:[]
    };
    var measureCanvas = $("<canvas />")[0].getContext("2d");
    var measureChar = function(char,preceding){
        measureCanvas.font = char.font;
        var width;
        if(preceding){
            width = measureCanvas.measureText([preceding.char,char.char].join("")).width  - measureCanvas.measureText(preceding.char).width;
        }
        else{
            width = measureCanvas.measureText(char.char).width;
        }
        switch(char.char){
        case " ": break;
        default: width += measureCanvas.measureText(ergonomics.letterSpacing).width;
        }
        return {
            width:width,
            height:Math.floor(parseFloat(char.font))
        }
    }
    var measureLine = function(y){
        y = y || cursor.y;
        var width = 0,height = 0;
        var i,char;
        var chars = _.concat(cursor.head,cursor.tail);
        var line = [];
        for(i = 0;i<chars.length;i++){
            char = chars[i];
            if(char.y < cursor.y) continue;
            if(char.y > cursor.y) break;
            line.push(char);
        }
        for(i = 0;i<line.length;i++){
            char = line[i];
            height = Math.max(height,char.height);
            width += char.width;
        }
        return {
            x:width > 0 ? line[0].x : 0,
            y:y,
            height:height,
            width:width,
            chars:line
        };
    }
    var setCursorPos = function(x,y){
        cursor.x = x;
        cursor.y = y;
    }
    var setCursorFont = function(family,size){
        cursor.family = family || cursor.family;
        cursor.size = size || cursor.size;
        cursor.font = sprintf("%spt %s",Math.floor(cursor.size),cursor.family);
    }
    var render = function(context){
        var char;
        var i;
        var screenPos;
        for(i = 0; i < cursor.head.length; i++){
            char = cursor.head[i];
            context.font = char.font;
            screenPos = worldToScreen(char.x,char.y);
            context.fillText(char.char, screenPos.x, screenPos.y);
        }
        for(i = 0; i < cursor.tail.length; i++){
            char = cursor.tail[i];
            context.font = char.font;
            screenPos = worldToScreen(char.x,char.y);
            context.fillText(char.char, screenPos.x, screenPos.y);
        }
        char = cursor.head.length ? cursor.head[cursor.head.length-1] : {
            x:cursor.x,
            y:cursor.y,
            width:0,
            height:cursor.size
        };
        var cursorPos = worldToScreen(char.x,char.y);
        var cursorWidth = scaleWorldToScreen(ergonomics.cursorWidth);
        var charWidth = scaleWorldToScreen(char.width);
        var cursorHeight = char.height;
        context.fillRect(
            cursorPos.x+charWidth,
            cursorPos.y - cursorHeight,
            cursorWidth,
            cursorHeight * ergonomics.cursorOverlap);
    };
    var wrap = function(){
        var leftMargin = cursor.x;
        var cursorX = leftMargin;
        var cursorY = cursor.y;
        var startOfLine = 0;
        var wordStart = 0;
        var lineDimension;
        var chars = _.concat(cursor.head,cursor.tail);
        var char;
        for(var i = 0;i<chars.length;i++){
            char = chars[i];
            char.x = cursorX;
            char.y = cursorY;
            if(char.char == " "){
                wordStart = i;
            }
            if(wordStart != startOfLine && (i - startOfLine) > ergonomics.comfortableColumnWidth){
                startOfLine = wordStart;
                i = wordStart;
                cursorY = cursorY + measureLine(char.y).height * ergonomics.lineHeightRatio;
                cursorX = leftMargin;
            }
            else{
                cursorX += char.width;
            }
        }
    };
    return {
        newIdentity:function(){
            return sprintf("%s_%s_%s",UserSettings.getUsername(),Date.now(),_.uniqueId());
        },
        create:function(worldPos){
            if(!cursor.font){
                setCursorFont("Arial",Math.floor(scaleScreenToWorld(25)));
            }
            var id = RichText.newIdentity();
            var box = boxes[id] = {
                identity:id,
                author:UserSettings.getUsername()
            };
            cursor.head = [];
            cursor.tail = [];
            setCursorPos(worldPos.x,worldPos.y);
            blit();
        },
        listen:function(context){
            $("#textInputInvisibleHost").off("keydown").on("keydown",function(e){
                var chars = cursor.chars;
                var typed = e.key;
                var charSize;
                var tip, pretip;
                switch(typed){
                case "Shift":break;
                case "Alt":break;
                case "Control":break;
                case "CapsLock":break;
                case "Enter":
                    var currentLine = measureLine();
                    setCursorPos(currentLine.x,cursor.y + currentLine.height * ergonomics.lineHeightRatio);
                    blit();
                    break;
                case "ArrowLeft":
                    if(cursor.head.length){
                        cursor.tail.unshift(cursor.head.pop());
                        blit();
                    }
                    break;
                case "ArrowRight":
                    if(cursor.tail.length){
                        cursor.head.push(cursor.tail.shift());
                        blit();
                    }
                    break;
                case "Backspace":
                    if(cursor.head.length){
                        cursor.head.pop();
                        wrap();
                        blit();
                    }
                    break;
                case "Delete":
                    if(cursor.tail.length){
                        cursor.tail.shift();
                        wrap();
                        blit();
                    }
                    break;
                default:
                    if(typed.length > 1) return;/*F keys, Control codes etc.*/
                    var char = {
                        char:typed,
                        font:cursor.font,
                        x:cursor.x,
                        y:cursor.y
                    };
                    var previous = cursor.head[cursor.head.length-1];
                    cursor.head.push(char);
                    charSize = measureChar(char,previous);
                    char.width = charSize.width;
                    char.height = charSize.height;
                    wrap();
                    blit();
                }
            }).focus();
        },
        add:function(){},
        clear:function(){
            boxes = {};
        },
        render:function(canvasContext){
            render(canvasContext);
        },
        cursor:function(){
            return cursor;
        },
        measureChar:function(i){
            return measureChar(cursor.box.chars[i]);
        }
    };
})();
