$(document).ready( function(){
    $(".popout-open").click( function() {
      $(this).toggleClass('active');
      $(this).next("#textDropdowns").toggle();
    });
});