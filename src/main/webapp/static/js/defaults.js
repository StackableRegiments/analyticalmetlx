var Colors = (function(){
    var colors = [
        ["black","#000000",255],
        ["red","#ff0000",255],
        ["blue","#0000ff",255],
        ["green","#00ff00",255],
        ["orange","#ff8000",255],
        ["cyan","#00ffff",255],
        ["yellow","#ffff00",255],
        ["white","#ffffff",255],
				["dark red","#660000",255],
				["dark blue","#000099",255],
				["dark green","#006600",255],
        ["brown","#663300",255],
				["dark purple","#660066",255],
        ["dark grey","#666666",255]
    ];
    var defaultColor = colors[0];
    var toHex = function(inputNumber){
        var n = parseInt(inputNumber,10);
        if (isNaN(n)) return "00";
        var nValue = Math.max(0,Math.min(n,255));
        var toHexChar = function(inputN){
            var internalN = Math.max(0,Math.min(inputN,15));
            return "0123456789ABCDEF".charAt(internalN);
        }
        return (toHexChar((n-n%16)/16) + toHexChar(n%16)).toLowerCase();
    };
    var get8BitHexFromString = function(string,position){
        return parseInt(string.substring(position,position+2).toUpperCase(),16);
    }
    var getAllNamedColorsFunction = function(){
        return _.map(colors,function(color){
            return {
                name:color[0],
                rgb:color[1],
                alpha:color[2],
                red:get8BitHexFromString(color[1],1),
                green:get8BitHexFromString(color[1],3),
                blue:get8BitHexFromString(color[1],5)
            };
        });
    };
    var getNameForColorFunction = function(color){
        var rgb = color[0];
        var opacity = color[1];
        var lookedUpName = _.find(colors,function(c){return (c[1] == rgb && c[2] == opacity);});
        if (lookedUpName){
            return lookedUpName[0];
        } else {
            return "#"+toHex(opacity)+rgb.substring(1,7);
        }
    };
		var getColorForColorPartsFunction = function(a,r,g,b){
			return ["#"+toHex(r)+toHex(g)+toHex(b),a];
		};
    var getColorForNameFunction = function(name){
        if (name.indexOf("#") == 0 && name.length == 7){
            var a = 255;
            var r = get8BitHexFromString(name,1);
            var g = get8BitHexFromString(name,3);
            var b = get8BitHexFromString(name,5);
            return [toHex(r)+toHex(g)+toHex(b),a];
        } else if (name.indexOf("#") == 0 && name.length == 9){
            var a = get8BitHexFromString(name,1);
            var r = get8BitHexFromString(name,3);
            var g = get8BitHexFromString(name,5);
            var b = get8BitHexFromString(name,7);
            return [toHex(r)+toHex(g)+toHex(b),a];
        } else {
            var color = _.find(colors,function(c){return (c[0] == name);});
            if (color){
                return [color[1],color[2]];
            } else {
                return [defaultColor[1],defaultColor[2]];
            }
        }
    };
    var getColorObjForHexFunction = function(hex){
			var name = hex;
			if (name.indexOf("#") == 0){
				name = name.substring(1);
			}
			if (name.length == 6){
					var a = 255;
					var r = get8BitHexFromString(name,0);
					var g = get8BitHexFromString(name,2);
					var b = get8BitHexFromString(name,4);
					return [toHex(r)+toHex(g)+toHex(b),a];
			} else if (name.length == 8){
					var a = get8BitHexFromString(name,0);
					var r = get8BitHexFromString(name,2);
					var g = get8BitHexFromString(name,4);
					var b = get8BitHexFromString(name,6);
					return [toHex(r)+toHex(g)+toHex(b),a];
			} else return [defaultColor[1],defaultColor[2]];
    };
		var niceColours = [
			"#D4C2FC",
			"#7B287D",
			"#F19A3E",
			"#DB324D",
			"#8EFF72",
			"#C8AD55",
			"#0FA3B1",
			"#88A09E",
			"#E7BB41",
			"#393E41",
			"#FF5A5F",
			"#03B954",
			"#D10000",
			"#CD9FCC",
			"#8895B3",
			"#C5D86D",
			"#0D1321",
			"#B8D8BA",
			"#034748",
			"#A57548",
			"#7CFEF0",
			"#6C809A",
			"#574B60",
			"#8D6346",
			"#4C2C69"
		];

		var colorFor = function(seed){
			var rawScore = _.reduce(seed.substring(0,4),function(acc,item){
				return acc + item.charCodeAt(0);
			},0);
			var score = rawScore % 25;
			var alpha = 255
			var rgb = niceColours[score];
			// console.log("colorFor",seed,rawScore,score,rgb);
			return [rgb,alpha];	
		};
    return {
        getAllNamedColors:getAllNamedColorsFunction,
        getNameForColor:getNameForColorFunction,
				getColorForColorParts:getColorForColorPartsFunction,
        getColorForName:getColorForNameFunction,
				getColorObjForHex:getColorObjForHexFunction,
				getColorForSeed:colorFor,
				getDefaultColor:function(){return defaultColor;},
				getDefaultColorObj:function(){return [defaultColor[1],defaultColor[2]];}
    }
})();

var Fonts = (function(){
    var families = [
	"sans-serif",
        "serif"
    ];
    var sizes = [12,14,16,18,20,30,42,60];
    return {
        getAllFamilies:function(){return _.map(families,function(f){return f;})},
        getAllSizes:function(){return _.map(sizes,function(s){return s;})}
    };
})();

var Brushes = (function(){
    var brushes = [
        {
            id:1,
            width:2.0,
            color:"#000000",
            isHighlighter:false
        },
        {
            id:2,
            width:5.0,
            color:"#ff0000",
            isHighlighter:false
        },
        {
            id:3,
            width:8.0,
            color:"#00ff00",
            isHighlighter:false
        },
        {
            id:4,
            width:10.0,
            color:"#666666",
            isHighlighter:false
        },
        {
            id:5,
            width:30.0,
            color:"#ffff00",
            isHighlighter:true
        }
    ];
    var brushSizes = [2,4,5,8,10,16,30];
    return {
        getDefaultBrushes:function(){return _.map(brushes,function(b){return b;})},
        getAllBrushSizes:function(){return _.map(brushSizes,function(b){return b;})}
    };
})();
