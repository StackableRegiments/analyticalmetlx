var MeTLText = (function(){
  var textBoxCount = 0;
  var px = function(i){
    return ""+i+"px";
  };
  var l = function(s){
    return function(){
      alert(s);
    };
  }
  return {
    live:function(){
      $("body").click(function(e){
        MeTLText.append(e.pageX,e.pageY);
      });
    },
    append:function(x,y){
      var el = document.elementFromPoint(x,y);
      var tg = el.tagName.toLowerCase();
      if(tg == "body" || tg == "html"){
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
        return id;
      }
      return el.id
    }
  }
})();
