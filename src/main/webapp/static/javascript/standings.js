function standingBands(standing){
    var cont = $("<span />")
        band(cont,false,standing,"Lower")
    band(cont,true,standing,"Higher")
    return cont;
}
var availableStandings = ["P","C","D"]
function standing(author){
    if(!(author in standings)){
        return "NA"
    }
    var s = standings[author]
    var projectedGrade = "D"
    if(s < 150){
        projectedGrade = "C"
    }
    if(s < 50){
        projectedGrade = "P"
    }
    return projectedGrade;
}