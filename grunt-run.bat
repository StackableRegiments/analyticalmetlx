call npm install -g grunt-cli
set "BUILD_DIR=src\main\assets\build"
call npm install %BUILD_DIR%
call grunt --gruntfile=%BUILD_DIR%\Gruntfile.js --theme=neutral
call grunt --gruntfile=%BUILD_DIR%\Gruntfile.js --theme=slu