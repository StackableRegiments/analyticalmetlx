var MeTLText = (function(){
  var textBoxCount = 0;
  var px = function(i){
    return ""+i+"px";
  };
  return {
    append:function(x,y){
      $(".Squire-UI").hide();
      var el = document.elementFromPoint(x,y);
      var tg = el.tagName.toLowerCase();
      if(tg == "body" || tg == "html"){
        var id = "t_"+textBoxCount++;
        var c = $("<div />",{
          class:"metltext",
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
      else{
        var parent = el.closest(".metltext");
        if(parent != null){
          parent.find(".Squire-UI").show();
        }
      }
    }
  }
})();
