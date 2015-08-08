var categories = {}
function pushCategory(label){
    trunk.pushAll(label,_.keys(categories[label]),label)
}
var categoriesOfWork = (function(){
    var categorizer = {}
    var categoriesContainer = $("#workCategorizer")
    var ticker = function(label){
        var categoryContainer = $(sprintf("<div><a href='#'>%s</a> <span id='%sCount'></span></div>",label,label))
        categoryContainer.click(function(){
            pushCategory(label)
        })
        categoriesContainer.append(categoryContainer)
        categories[label] = {}
        categorizer[label] = function(id,item){
            types[id] = label;
            categories[label][id] = item;
            categoriesContainer.find(sprintf("#%sCount",label)).html(Object.keys(categories[label]).length)
        }
    }
    categoriesContainer.append($("<a />",{
        text:"all",
        href:"#",
        click:function(){
            vjq(renderAll(),"All content")
        }
    }))
    ticker("author")
    ticker("topic")
    ticker("question")
    ticker("answer")
    ticker("comment")
    ticker("vote")
    return categorizer;
})()
