(function () {
    var cookieName = 'sidebar';
    var defaultState = {a0: true};
    var sidebar = function () {
        var json = Cookies.getJSON(cookieName);
        if (!_.isObject(json)) {
            Cookies.set(cookieName, defaultState);
            return defaultState;
        }
        return json;
    }();

    function cookieToCheckbox(checkbox) {
        checkbox.prop("checked", sidebar[checkbox.attr('id')]);
        return checkbox;
    }

    function checkboxToCookie(checkbox) {
        var id = checkbox.attr('id');
        if (checkbox.is(':checked')) {
            sidebar[id] = true;
        }
        else {
            delete sidebar[id];
        }
        Cookies.set('sidebar', sidebar);
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
})();