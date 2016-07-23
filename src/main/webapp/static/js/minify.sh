for file in toolbar carota-debug board
do
    java -jar /c/lang/closureCompiler/closure-compiler-v20160713.jar $file.js --js_output_file=$file.min.js
done
