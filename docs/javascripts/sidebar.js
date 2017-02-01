(function () {
    var cookieName = 'sidebar';
    var defaultState = {a0: true};
    var sidebar = function () {
        var json = Cookies.getJSON(cookieName);
        if (json != undefined) {
            return json;
        } else {
            Cookies.set(cookieName, defaultState);
            return defaultState;
        }
    }();

    var cookieToCheckbox = function (checkbox) {
        checkbox.prop('checked', sidebar[checkbox.attr('id')]);
        return checkbox;
    };

    var checkboxToCookie = function (checkbox) {
        var id = checkbox.attr('id');
        if (checkbox.is(':checked')) {
            sidebar[id] = true;
        }
        else {
            delete sidebar[id];
        }
        Cookies.set(cookieName, sidebar);
    };

    var checkAllBoxes = function (selector) {
        $(selector).each(function (index, elem) {
            var checkbox = $(elem);
            cookieToCheckbox(checkbox).on('click', function () {
                checkboxToCookie(checkbox);
            });
        });
    };

    $(function () {
        checkAllBoxes('#sidebar input[type=checkbox]')
    });
})();