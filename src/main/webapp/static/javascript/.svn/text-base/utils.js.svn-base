function random_matrix(size) {
    var matrix = [];
    for (var i=0; i<size; i++) {
        var row = [];
        for (var j=0; j<size; j++) {
            var num = Math.round(100*Math.pow(Math.random(),2)+1);
            row.push(num);
        }
        matrix.push(row);
    }
    return matrix;
};
function repeat(item,count){
    var repeats = []
    for(var i = 0;i<count;i++){
        repeats.push(item);
    }
    return repeats;
}
function squareMatrix(size){
    var sq = [];
    for(var i = 0;i<size;i++){
        sq.push(repeat(0,size));
    }
    return sq;
}
function setIfMissing(object,key,value){
    if(!(key in object)){
        object[key] = value;
    }
    return object[key];
}
function sortObject(o,key){
    return sortObjectOn(o,function(comp){
        return comp[key];
    })
}
function objectToArray(o){
    var arr = []
    for(var i in o){
        arr.push([i,o[i]]);
    }
    return arr;
}
function sortObjectOn(o,func){
    var sortable = objectToArray(o);
    return sortable.sort(function(a,b){
        return func(a[1]) - func(b[1]);
    })
}