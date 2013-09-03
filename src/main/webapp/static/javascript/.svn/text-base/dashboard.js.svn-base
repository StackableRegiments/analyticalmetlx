//All the activities to be represented in the overall graph, in left to right order
var labels = ["W1","Lab 1","W2","Lab 2","W3","Lab 3","W4","Lab 4","W5","Lab 5"]
//How many marks overall, out of how many
var overallProgress = [48,60]
//Every attempt made at each exercise, each exercise having an array of 0+ elements, in left to right order
var allFormatives = [[68,70,75],[],[78],[],[58,61,63],[],[58,61,64],[],[58,60,62],[]]
//Diagnostics for each exercise, in left to right order
var diagnostics = [61,0,70,0,51,0,55,0,54,0];
//The average of all the formative attempts for each exercise, with no attempts being 0, in left to right order
var formatives = [68,0,78,0,58,0,58,0,58,0];
//Summative scores for each exercise, in left to right order
var summatives = [70,80,80,80,81,80,85,60,84,80];
//Average diagnostics scores for the whole class, one for each exercise, in left to right order
var avg_diagnostics = [51,0,60,0,55,0,51,0,52,0];
//Average formative scores for the whole class, one for each exercise, in left to right order
var avg_formatives = [63,0,72,0,52,0,52,0,52,0];
//Average summative scores for the whole class, one for each exercise, in left to right order
var avg_summatives = [71,80,89,60,89,60,81,60,81,80];
//Plotting data for the class curves
//Mock data is currently in place, in the form of curve() simulations, and must be replaced
/*
  Data is in the form of {x:(A column index between 0 and 100), y:(A frequency of users)}
  [[..The class diagnostic score distribution..],[..The class average formative score distribution..]]
 */
var nonSummativeDistributions = 
[
  [curve(45,30),curve(48,12)],
  [curve(45,30),curve(58,12)],
  [curve(45,30),curve(68,12)],
  [curve(45,30),curve(55,12)],
  [curve(45,30),curve(54,12)],
  [curve(45,30),curve(53,12)],
  [curve(45,30),curve(52,12)],
  [curve(45,30),curve(51,12)],
  [curve(45,30),curve(68,12)],
  [curve(45,30),curve(78,12)]
];
/*
  Distributions are in the form of {x:(A column index between 0 and 100), y:(A frequency of users)}
  [[..People who didn't use MPL..],[..People who used MPL..]]
 */
var summativeDistributions = 
[
  [curve(45,30),curve(48,12)],
  [curve(45,30),curve(58,12)],
  [curve(45,30),curve(68,12)],
  [curve(45,30),curve(55,12)],
  [curve(45,30),curve(54,12)],
  [curve(45,30),curve(53,12)],
  [curve(45,30),curve(52,12)],
  [curve(45,30),curve(51,12)],
  [curve(45,30),curve(68,12)],
  [curve(45,30),curve(78,12)]
];

var data = diagnostics.map(function(x,i){
  return [x, formatives[i], summatives[i]];
});
var avg_data = avg_diagnostics.map(function(x,i){
  return [x,avg_formatives[i],avg_summatives[i]];
});
function curve(mean,sd){
  var variance = Math.pow(sd,2)
  var seed = 1 / Math.sqrt(2 * Math.PI * variance);
  function normalize(x){
    return 1 - (seed * Math.exp( -1 * (Math.pow((x - mean), 2) / (2 * variance))))
  }
  var res = []
  for (var i=0; i <= 100; i++){      
    res.push({x:i,y:normalize(i)});
  }
  return res;
}
$(function(){
  $("#accumulated").html(overallProgress[0].toString());
  $("#possibleAccumulated").html(overallProgress[1].toString());
  var colors = pv.Colors.category10().range();
  function drawGrouped(where,w,h,averages,personal,clicked,text){
    var n = averages.length
    var m = averages[0].length
    var x = pv.Scale.ordinal(pv.range(n)).splitBanded(0,w,2/5)
    var y = pv.Scale.linear(0,100).range(0,h)
    var vis = new pv.Panel()
      .canvas(where)
      .width(w)
      .height(h)
      .lineWidth(2)
      .strokeStyle("black")
    var peers = vis.add(pv.Panel)
      .data(averages)
      .left(function(){return x(this.index)})
      .width(x.range().band)
      .bottom(0)
      .event("click",clicked)
      .add(pv.Bar)
        .data(function(d){return d})
        .left(function(){return this.index * x.range().band / m})
        .width(function(){return x.range().band / m})
        .bottom(0)
        .height(y)
        .fillStyle(function(){return colors[this.index]})
    var me = vis.add(pv.Panel)
      .data(personal)
      .left(function(){return x(this.index)})
      .width(x.range().band)
      .bottom(0)
      .add(pv.Dot)
        .data(function(d){return d})
        .visible(function(d){return d > 0})
        .left(function(){return this.index * x.range().band / m + 5})
        .width(10)
        .bottom(y)
        .fillStyle("red")
        .strokeStyle("red")
    var legend = vis.add(pv.Dot)
      .data(function(){
          return [
            [colors[0],"Diagnostic"],
            [colors[1],"Formative"],
            [colors[2],"Summative"],
            ["red","My grade"]
          ];
      })
      .top(function(){return (this.index * 12) + 10})
      .left(10)
      .shape("circle")
      .fillStyle(function(d){return d[0]})
      .strokeStyle(function(d){return d[0]})
      .anchor("right")
      .add(pv.Label)
        .text(function(d){return d[1]})
    vis.render();
  }
  function curves(where,w,h,series,markers,style,strokeStyles,legends){
    function flatPluck(vals,key){
      return $.map(vals,function(serie){
        return serie.map(function(p){return p[key]})
      });
    }
    var ys = flatPluck(series,"y")
    var minY = Math.min.apply(Math,ys)
    var maxY = Math.max.apply(Math,ys)
    var x = pv.Scale.linear(0,100).range(0,w);
    var y = pv.Scale.linear(minY,maxY).range(0,h);
    var vis = new pv.Panel()
      .canvas(where)
      .width(w)
      .height(h)
      .lineWidth(2)
      .strokeStyle("black")
    var legend = vis.add(pv.Dot)
      .data(legends)
      .top(function(){
          return (this.index * 12) + 10
      })
      .left(10)
      .shape("circle")
      .fillStyle(function(){return strokeStyles[this.index]})
      .strokeStyle(function(){return strokeStyles[this.index]})
      .anchor("right")
      .add(pv.Label)
    $.each(series,function(i,serie){
      vis.add(pv.Line)
        .data(serie)
        .left(function(d){return x(d.x)})
        .top(function(d){return y(d.y)})
        .lineWidth(2)
        .strokeStyle(function(){return strokeStyles[i]})
      vis.add(pv.Dot)
        .data(function(){
          return markers[i].map(function(x){
            var data = series[i][x];
            return data;
          })
        })
        .left(function(d){return x(d.x)})
        .lineWidth(2)
        .strokeStyle(function(){return strokeStyles[i]})
        .top(function(d){return y(d.y)})
    });
    vis.render();
  }
  function label(){
    return labels[this.index]
  }
  var w = 400;
  var subWidth = w;
  var h = 200;
  var drawByGroups = function(){
    drawGrouped("overview",subWidth,h,avg_data,data,function(){
      var quizIndex = this.index;
      function pluck(index){
        return data[quizIndex].map(function(attempt){
          return attempt[index];
        });
      }
      leftData = [[diagnostics[quizIndex]],allFormatives[quizIndex]];
      rightData = [[],[summatives[quizIndex]]];
      curves("perQuiz",w/2,h,nonSummativeDistributions[quizIndex],leftData,[0,0],colors.slice(0,2),["Diagnostic", "Formative"]);
      curves("projection",w/2,h,summativeDistributions[quizIndex],rightData,[2,0],colors.slice(2,4),["No MPL","MPL"])
    });
  }
  drawByGroups();
  var x = pv.Scale.ordinal(pv.range(labels.length)).splitBanded(0,subWidth,2/5)
  var labelPane = new pv.Panel()
    .canvas("quizLabels")
    .width(subWidth)
    .height(20)
  var mpl = labelPane.add(pv.Label)
      .data(labels)
      .left(function(){return x(this.index)})
      .bottom(0)
      .textStyle("black")
  labelPane.render()
});

