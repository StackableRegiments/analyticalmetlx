function clearSidebar() {
    Cookies.set('sidebar', []);
}

function getSidebar() {
    if (!$.isArray(Cookies.getJSON('sidebar'))) {
        clearSidebar();
    }
    return Cookies.getJSON('sidebar');
}

function isExpanded(checkbox) {
    var id = $(checkbox).attr('id');

    var sidebar = getSidebar();
    for (var i = 0; i < sidebar.length; i++) {
        if (sidebar[i].name == id && sidebar[i].value == true)
            return 'checked';
    }
    return '';
}

function setExpanded(checkbox) {
    var id = $(checkbox).attr('id');
    var checked = $(checkbox).is(':checked');
    // alert('id = ' + id + ", checked = " + checked );

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

// alert(Cookies.get('sidebar'));
