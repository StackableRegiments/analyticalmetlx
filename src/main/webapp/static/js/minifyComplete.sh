echo "execute this from the src/main/webapp directory"
#grep -oe "static\/js\/[^[\/]*\.js" board.html | xargs | sed -e 's/ / --js=/g' | xargs -t java -jar ../../../tools/closure-compiler-v20160713.jar --js_output_file=minified/board.js
#grep -oe "static\/js\/[^[\/]*\.js" board.html | xargs | sed -e 's/ / --js=/g' | xargs -t java -jar ../../../tools/closure-compiler-v20160713.jar --compilation_level ADVANCED_OPTIMIZATIONS --js_output_file=minified/advanced_board.js

#grep -oe "static\/js\/[^[\/]*\.js" editConversation.html | xargs | sed -e 's/ / --js=/g' | xargs -t java -jar ../../../tools/closure-compiler-v20160713.jar --js_output_file=minified/editConversation.js
#grep -oe "static\/js\/[^[\/]*\.js" conversationSearch.html | xargs | sed -e 's/ / --js=/g' | xargs -t java -jar ../../../tools/closure-compiler-v20160713.jar --js_output_file=minified/conversationSearch.js
#grep -oe "static\/js\/[^[\/]*\.js" printConversation.html | xargs | sed -e 's/ / --js=/g' | xargs -t java -jar ../../../tools/closure-compiler-v20160713.jar --js_output_file=minified/printConversation.js

grep -oe "static\/js\/.*\.js" board.html | xargs | sed -e 's/ / --js=/g' | xargs -t java -jar ../../../tools/closure-compiler-v20160713.jar --js_output_file=minified/board.js 
sed -i "/\bstatic\/js\/.*\.js\b/d" board.html
sed -i "/<span class=\"minifiedScript\"><\/span>/c\<script data-lift=\"with-resource-id\" src=\"minified\/board.js\"><\/script>" board.html
