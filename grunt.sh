BUILD_DIR=src/main/assets/build
npm install $BUILD_DIR
GRUNT=node_modules/grunt/bin/grunt
$GRUNT --gruntfile=$BUILD_DIR/Gruntfile.js --theme=neutral
$GRUNT --gruntfile=$BUILD_DIR/Gruntfile.js --theme=slu