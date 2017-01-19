(function () {
    var cookieName = 'sidebar';

    function clearSidebar() {
        Cookies.set(cookieName, []);
    }

    function getSidebar() {
        if (!$.isArray(Cookies.getJSON(cookieName))) {
            clearSidebar();
        }
        return Cookies.getJSON(cookieName);
    }

    function cookieToCheckbox(checkbox) {
        var id = $(checkbox).attr('id');
        var sidebar = getSidebar();
        for (var i = 0; i < sidebar.length; i++) {
            if (sidebar[i].name == id && sidebar[i].value) {
                checkbox.prop("checked", true);
                return checkbox;
            }
        }
        checkbox.prop("checked", false);
        return checkbox;
    }

    function checkboxToCookie(checkbox) {
        var id = $(checkbox).attr('id');
        var checked = $(checkbox).is(':checked');
        var found = false;
        var checkedArray = getSidebar();
        for (var i = 0; i < checkedArray.length; i++) {
            if (checkedArray[i].name == id) {
                checkedArray[i].value = checked;
                found = true;
                break;
            }
        }
        if (!found) {
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