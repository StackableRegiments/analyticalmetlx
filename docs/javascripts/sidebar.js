(function () {
    var cookieName = 'sidebar';
    var sidebar = function(){
        if (!$.isArray(Cookies.getJSON(cookieName))) {
            clearSidebar();
        }
        return Cookies.getJSON(cookieName);
    }();

    function clearSidebar() {
        Cookies.set(cookieName, []);
    }

    function cookieToCheckbox(checkbox) {
        var id = $(checkbox).attr('id');
        checkbox.prop("checked", _.find(sidebar, function (item) {
                return item.name == id && item.value
            }) != undefined);
        return checkbox;
    }

    function checkboxToCookie(checkbox) {
        var id = $(checkbox).attr('id');
        var checked = $(checkbox).is(':checked');
        var checkedArray = sidebar;
        var cookie = _.find(checkedArray, function (item) {
            return item.name == id
        });
        if (cookie != undefined) {
            cookie.value = checked;
        }
        else {
            checkedArray.push({name: id, value: checked});
        }
        Cookies.set('sidebar', checkedArray);
    }

    function checkAllBoxes(selector) {
        $(selector).each(function (index, elem) {
            var checkbox = $(elem);
            cookieToCheckbox(checkbox).on("click", function () {
                checkboxToCookie(checkbox);
            });
        });
    }

    $(function () {
        checkAllBoxes("#sidebar input[type=checkbox]")
    });

    // return {clear: clearSidebar};
})();