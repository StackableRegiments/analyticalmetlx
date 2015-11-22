var MeTLText = (function(){
  var textBoxCount = 0;
  var px = function(i){
    return ""+i+"px";
  };
  return {
    append:function(x,y){
      var id = "t_"+textBoxCount++;
      var c = $("<div />",{
        id:id
      }).appendTo("body").css(
        {
          position:"absolute",
          left:px(x),
          top:px(y)
        }
      );
      new SquireUI({div:"#"+id,buildPath:"/static/"});
    }
  };
})();
