var RichText = (function(){
    var ergonomics = {
        comfortableColumnWidth:28,
        lineHeightRatio:1.8,
        letterSpacing:String.fromCharCode(8202)
    };
    var boxes = {};
    var cursor = {
        after:0,
        target:false,
        selection:false,
        visible:false
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
        return {
            width:char == " " ? width : width+measureCanvas.measureText(ergonomics.letterSpacing).width,
            height:Math.floor(parseFloat(char.font))
        };
    }
    var measureCurrentLine = function(){
        var y = cursor.y;
        var width = 0,height = 0;
        var i,char,prevChar,charSize;
        var chars = cursor.box.chars;
        var line = [];
        for(i = 0;i<cursor.box.chars.length;i++){
            char = chars[i];
            if(char.y < cursor.y) continue;
            if(char.y > cursor.y) break;
            line.push(char);
        }
        for(i = 0;i<line.length;i++){
            char = line[i];
            charSize = measureChar(char,prevChar);
            height = Math.max(height,charSize.height);
            width += charSize.width;
            prevChar = char;
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
    var render = function(box,context){
        for(var i = 0; i < box.chars.length; i++){
            var char = box.chars[i];
            context.fillStyle = char.fill;
            context.font = char.font;
            var screenPos = worldToScreen(char.x,char.y);
            context.fillText(char.char, screenPos.x, screenPos.y);
        }
    };
    var dropLine = function(){
        var currentLine = measureCurrentLine();
        setCursorPos(currentLine.x,cursor.y + currentLine.height * ergonomics.lineHeightRatio);
    }
    return {
        newIdentity:function(){
            return sprintf("%s_%s_%s",UserSettings.getUsername(),Date.now(),_.uniqueId());
        },
        create:function(worldPos){
            var id = RichText.newIdentity();
            var box = boxes[id] = {
                identity:id,
                author:UserSettings.getUsername(),
                chars:[]
            };
            cursor.box = box;
            cursor.x = worldPos.x;
            cursor.y = worldPos.y;
            cursor.visible = true;
            if(!cursor.font){
                setCursorFont("Arial",Math.floor(scaleScreenToWorld(25)));
            }
        },
        listen:function(context){
            $("#textInputInvisibleHost").off("keydown").on("keydown",function(e){
                var chars = cursor.box.chars;
                var typed = e.key;
                switch(typed){
                case "Shift":break;
                case "Alt":break;
                case "Control":break;
                case "Enter":
                    dropLine();
                    blit();
                    break;
                case "Backspace":
                    var removed = chars.pop();
                    if(chars.length == 0){
                        setCursorPos(removed.x,removed.y);
                    }
                    else{
                        var l = chars.length;
                        var tip = chars[l-1];
                        var pretip = chars[l-2];
                        var charSize = measureChar(tip,pretip);
                        setCursorPos(tip.x + charSize.width,tip.y);
                    }
                    blit();
                    break;
                default:
                    var char = {
                        char:typed,
                        font:cursor.font,
                        x:cursor.x,
                        y:cursor.y
                    };
                    chars.push(char);
                    var charSize = measureChar(char,chars[chars.length-2]);
                    var currentLine = measureCurrentLine();
                    if(currentLine.chars.length >= ergonomics.comfortableColumnWidth){
                        var charsToDrop = [];
			var drop = currentLine.height * ergonomics.lineHeightRatio;
                        var c,i,preC;
                        for(i = currentLine.chars.length-1;i > 0;i--){
                            c = currentLine.chars[i];
                            if(c.char == " ") break;
                            charsToDrop.unshift(c);
                        }
                        console.log("drop",charsToDrop);
                        if(charsToDrop.length != currentLine.chars.length){/*We wont't drop a word which takes the whole line*/
                            var x = currentLine.x;
                            for(i = 0;i < charsToDrop.length;i++){
                                c = charsToDrop[i];
                                preC = charsToDrop[i-1];
                                c.y = c.y + drop;
                                c.x = x;
                                x += measureChar(c,preC).width;
                            }
                            setCursorPos(x,cursor.y + drop);
                        }
                    }
                    else{
                        setCursorPos(cursor.x+charSize.width,cursor.y);
                    }
                    blit();
                }
            }).focus();
        },
        add:function(){},
        clear:function(){
            boxes = {};
        },
        render:function(canvasContext){
            _.each(boxes,function(box,identity){
                render(box,canvasContext);
            });
        }
    };
})();
