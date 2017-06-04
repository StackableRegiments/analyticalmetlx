var RichText = (function(){
    var boxes = {};
    var ergonomics = {
        cursorWidth:3,
        cursorOverlap:1.5,
        comfortableColumnWidth:28,
        lineHeightRatio:1.8
    };
    var cursor = {
        selected:[]
    };
    var measureCanvas = $("<canvas />")[0].getContext("2d");
    var fontHeight = function(char){
        return Math.floor(parseFloat(char.fontSize));
    };
    var measureChar = function(char,preceding){
        measureCanvas.font = fontString(char.fontFamily,char.fontSize);
        var width;
        var height = fontHeight(char);
        if(preceding){
            width = measureCanvas.measureText([preceding.char,char.char].join("")).width  - measureCanvas.measureText(preceding.char).width;
        }
        else{
            width = measureCanvas.measureText(char.char).width;
        }
        switch(char.char){
        case " ": break;
        case "\n": width = 0; break;
        default:;
        }
        return {
            width:width,
            height:height
        }
    }
    var measureLine = function(y,box){
        y = y || cursor.y;
        var width = 0,height = 0;
        var i,char;
        var chars = _.concat(box.head,box.tail);
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
    var fontString = function(fontFamily,fontSize){
        return sprintf("%spt %s",Math.floor(fontSize),fontFamily);
    };
    var setCursorFont = function(fontFamily,fontSize){
        cursor.fontFamily = fontFamily || cursor.fontFamily;
        cursor.fontSize = fontSize || cursor.fontSize;
    }
    var charRenderer = function(context){
        return function(char){
            context.font = fontString(char.fontFamily,scaleWorldToScreen(char.fontSize));
            var screenPos = worldToScreen(char.x,char.y);
            var screenWidth = scaleWorldToScreen(char.width);
            var screenHeight = scaleWorldToScreen(char.height);
            if(_.includes(cursor.selected,char)){
                context.fillStyle = "yellow";
                context.fillRect(screenPos.x,screenPos.y-screenHeight,screenWidth,screenHeight);
            }
            context.fillStyle = char.color;
            context.fillText(char.char, screenPos.x, screenPos.y);
        };
    }
    var render = function(context,box){
        var renderChar = charRenderer(context);
        _.each(box.head,renderChar);
        _.each(box.tail,renderChar);
        if(box == cursor.box){
            context.fillStyle = cursor.color;
            var char = box.head.length ? box.head[box.head.length-1] : {
                x:box.x,
                y:box.y,
                width:0,
                height:cursor.fontSize
            };
            var cursorPos = worldToScreen(char.x,char.y);
            var cursorWidth = scaleWorldToScreen(ergonomics.cursorWidth);
            var charWidth = scaleWorldToScreen(char.width);
            var cursorHeight = scaleWorldToScreen(char.height);
            context.fillRect(
                cursorPos.x+charWidth,
                cursorPos.y - cursorHeight,
                cursorWidth,
                cursorHeight * ergonomics.cursorOverlap);
        }
    };
    var wrap = function(box){
        var leftMargin = box.x;
        var cursorX = leftMargin;
        var cursorY = box.y;
        var cursorChar = cursor.box.head[cursor.box.head.length-1];
        var startOfLine = 0;
        var wordStart = 0;
        var lineDimension;
        var chars = _.concat(box.head,box.tail);
        var char;
	var lastInterestingBreakPoint = 0;
	var breakPointToSync = lastInterestingBreakPoint;
	var cursorPos = chars.length - 1;
        for(var i = 0;i<chars.length;i++){
            char = chars[i];
            if(char.identity == cursorChar.identity){
		cursorPos = i;
		breakPointToSync = lastInterestingBreakPoint;
            }
            char.x = cursorX;
            char.y = cursorY;
            char.bounds = [char.x,char.y - char.height,char.x + char.width,char.y];
            if(char.char == " "){
                wordStart = i;
            }
            if(char.char == "\n"){
                startOfLine = wordStart;
                cursorX = leftMargin;
                cursorY = cursorY + measureLine(char.y,box).height * ergonomics.lineHeightRatio;
                char.x = cursorX;
                char.y = cursorY;
            }
            else if((i - startOfLine) > ergonomics.comfortableColumnWidth && wordStart != startOfLine){
                startOfLine = wordStart;
                cursorY = cursorY + measureLine(char.y,box).height * ergonomics.lineHeightRatio;
                cursorX = leftMargin;
                i = wordStart;
                lastInterestingBreakPoint = Math.min(i,cursorPos);
            }
            else{
                cursorX += char.width;
            }
        }
	var res = chars.slice(breakPointToSync);
	return res;
    };
    var toggleSelected = function(char){
        if(_.includes(cursor.selected,char)){
            _.pull(cursor.selected,char);
        }
        else{
            cursor.selected.push(char);
        }
    };
    var addBox = function(x,y,author,id){
        var box = {
            identity:id,
            author:author,
            head:[],
            tail:[],
            x:x,
            y:y
        };
        boxes[box.identity] = box;
        return box;
    }
    var removeChar = function(box,char){
        var predicate = function(c){
            return c.identity == char.identity;
        };
        _.remove(box.head,predicate);
        _.remove(box.tail,predicate);
        return box;
    }
    var newIdentity = function(){
        return sprintf("%s_%s_%s",UserSettings.getUsername(),Date.now(),_.uniqueId());
    }
    return {
        scaleSelection:function(factor){
            _.each(cursor.selected,function(char){
                char.fontSize = char.fontSize * factor;
            });
            wrap();
            blit();
        },
        setAttributes:function(attributes){
            setCursorFont(attributes.fontFamily,attributes.fontSize);
            cursor.color = attributes.color;
            cursor.bold = attributes.bold;
            cursor.italic = attributes.italic;
            cursor.underline = attributes.underline;
            blit();
        },
        click:function(worldPos){
            delete cursor.box;
            var radius = 1;
            var ray = [worldPos.x - radius,worldPos.y - radius,worldPos.x + radius,worldPos.y + radius];
            _.each(boxes,function(box){
                var chars = _.concat(box.head,box.tail);
                var hit = false;
                _.each(chars,function(char,i){
                    if(intersectRect(ray,char.bounds)){
                        cursor.box = box;
                        box.head = chars.slice(0,i+1);
                        box.tail = chars.slice(i+1,chars.length);
                        hit = true;
                        blit();
                    }
                    return !hit;
                });
                return !hit;
            });
            if(!cursor.box){
                var box = addBox(worldPos.x,worldPos.y,UserSettings.getUsername(),newIdentity());
                cursor.box = box;
                cursor.x = box.x;
                cursor.y = box.y
                blit();
            }
        },
        incorporate:function(char){
	    if(_.isArray(char.color)) char.color = char.color[0];
            var box = boxes[char.box];
            if(!box){
                box = addBox(char.x,char.y,char.author,char.box);
                box.head = [];
                box.tail = [];
            }
            if(box != cursor.box){
                removeChar(box,char);
                box.tail.push(char);
            }
            blit();
        },
        listen:function(context){
            $("#textInputInvisibleHost").off("keydown").on("keydown",function(e){
                var chars = cursor.chars;
                var typed = e.key;
                var charSize;
                var tip, pretip, underCursor, charsToSync;
                switch(typed){
                case "Shift":break;
                case "Alt":break;
                case "Control":break;
                case "CapsLock":break;
                case "ArrowLeft":
                    if(cursor.box.head.length){
                        underCursor = cursor.box.head.pop();
                        cursor.box.tail.unshift(underCursor);
                        if(e.shiftKey){
                            toggleSelected(underCursor);
                        }
                        blit();
                    }
                    break;
                case "ArrowRight":
                    if(cursor.box.tail.length){
                        underCursor = cursor.box.tail.shift();
                        cursor.box.head.push(underCursor);
                        if(e.shiftKey){
                            toggleSelected(underCursor);
                        }
                        blit();
                    }
                    break;
                case "Backspace":
                    if(cursor.box.head.length){
                        cursor.box.head.pop();
                        charsToSync = wrap(cursor.box);
                        blit();
                        sendChars(charsToSync,cursor.box.identity);
                    }
                    break;
                case "Escape":
                    cursor.selected = [];
                    blit();
                    break;
                case "Delete":
                    if(cursor.box.tail.length){
                        cursor.box.tail.shift();
                        charsToSync = wrap(cursor.box);
                        blit();
                        sendChars(charsToSync,cursor.box.identity);
                    }
                    break;
                default:
                    typed = typed == "Enter" ? "\n" : typed;
                    if(typed.length > 1) return;/*F keys, Control codes etc.*/
                    var char = {
                        char:typed,
                        fontSize:cursor.fontSize,
                        fontFamily:cursor.fontFamily,
                        color:cursor.color,
                        x:cursor.x,
                        y:cursor.y,
                        box:cursor.box.identity
                    };
                    char.identity = sprintf("%s@%s,%s",char.char,char.x,char.y);
                    var previous = cursor.box.head[cursor.box.head.length-1];
                    cursor.box.head.push(char);
                    charSize = measureChar(char,previous);
                    char.width = charSize.width;
                    char.height = charSize.height;
                    charsToSync = wrap(cursor.box);
                    cursor.x = char.bounds[2];
                    cursor.y = char.bounds[3];
                    blit();
                    sendChars(charsToSync,cursor.box.identity);
                }
            }).focus();
        },
        clear:function(){
            boxes = {};
            blit();
        },
        render:function(canvasContext){
            _.each(boxes,function(box){
                render(canvasContext,box);
            });
        },
        cursor:function(){
            return cursor;
        }
    };
})();
