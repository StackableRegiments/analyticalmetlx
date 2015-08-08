var Colors = (function(){
	var colors = [
		["Black","#000000",255],
		["Red","#ff0000",255],
		["Blue","#0000ff",255],
		["Cyan","#00ffff",255],
		["Green","#00ff00",255],
		["White","#ffffff",255],
		["Dark Grey","#666666",255]	
	];
	var defaultColor = colors[0];
	var toHex = function(inputNumber){
		n = parseInt(inputNumber,10);
		if (isNaN(n)) return "00";
		nValue = Math.max(0,Math.min(n,255));
		var toHexChar = function(inputN){
			internalN = Math.max(0,Math.min(inputN,15));
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
	return {
		getAllNamedColors:getAllNamedColorsFunction,
		getNameForColor:getNameForColorFunction,
		getColorForName:getColorForNameFunction
	}
})();

var Fonts = (function(){
	var families = [
		"Arial",
		"Helvetica",
		"Times New Roman"
	];
	var sizes = [8,10,14,20,30];
	return {
		getAllFamilies:function(){return _.map(families,function(f){return f;})},
		getAllSizes:function(){return _.map(sizes,function(s){return s;})}
	};	
})();

var Brushes = (function(){
	var brushes	= [
		{
			id:1,
			width:2.0,
			color:"#000000",
			isHighlighter:false
		},
		{
			id:2,
			width:5.0,
			color:"#FF0000",
			isHighlighter:false
		},
		{
			id:3,
			width:30.0,
			color:"#FFFF00",
			isHighlighter:true
		}
	];
	var brushSizes = [2,4,5,8,10,16,30];
	return {
		getDefaultBrushes:function(){return _.map(brushes,function(b){return b;})},
		getAllBrushSizes:function(){return _.map(brushSizes,function(b){return b;})}
	};
})();
