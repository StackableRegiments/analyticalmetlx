echo "execute this from the src/main/webapp/static/js directory"
for file in $(ls)
do
    case $file in
        *~) ;;
        *.min.js) ;;
##        *.js) java -jar /c/lang/closureCompiler/closure-compiler-v20160713.jar $file --js_output_file=min/$file ;;
        *.js) java -jar ../../../../../tools/closure-compiler-v20160713.jar $file --js_output_file=min/$file ;;
    esac
done
