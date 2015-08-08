var Color = {
    complement:function(hexValue){
        if(hexValue.length == 7){
            hexValue = hexValue.slice(1);
        }
        var reqHex = "";
        for(var i=0;i<6;i++){
            reqHex = reqHex + (15-parseInt(hexValue[i],16)).toString(16);
        }
        return "#"+reqHex;
    }
};
