var Hotkeys = (function(){
    var associations = [
        {
            keyCode:34,
            func:Conversations.goToNextSlide,
            desc:"Move to next page"
        },
        {
            keyCode:33,
            func:Conversations.goToPrevSlide,
            desc:"Move to previous page"
        }
    ];
    var getHotkeysFunction = function(){
        return _.map(associations,function(a){return _.clone(a);});
    };
    var addHotkeyFunction = function(keyTest,func,description){
        associations.push({test:keyTest,func:func,desc:description});
    };
    $(function(){
        $(document).keydown(function(e){
            _.each(associations,function(i){
                if (e.which == i.keyCode){
                    i.func()
                }
            });
        });
    });
    return {
        getHotkeys:getHotkeysFunction,
        addHotkey:addHotkeyFunction
    };
})();
