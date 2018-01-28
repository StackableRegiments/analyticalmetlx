@echo off
rem echo "execute this from the src/main/webapp/static/js directory"
setlocal ENABLEDELAYEDEXPANSION
for %%f in (*.*) do (
  set FILE_NAME=%%f
  rem echo !FILE_NAME:~-1! !FILE_NAME:~-3! !FILE_NAME:~-7!
  if "!FILE_NAME:~-1!" == "~" (
    echo not processing backup %%f
  ) else (
    if "!FILE_NAME:~-7!" == ".min.js" (
      echo not processing minified %%f
    ) else (
      if "!FILE_NAME:~-3!" == ".js" (
        java -jar ../../../../../tools/closure-compiler-v20160713.jar %%f --js_output_file=min/%%f
        echo processing %%f
      ) else (
        echo not processing non-js %%f
      )
    )
  )
)
