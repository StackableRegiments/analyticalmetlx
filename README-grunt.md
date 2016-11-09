# Running Grunt

## Prerequisites

To build the source javascript and less script you'll need "nodejs" installed on your machine.

To install node.js on windows:
- Go to: http://nodejs.org/download/
- Download the windows installer (.msi)
- During the installation, at the step "Custom Setup", remember to uncheck the Online documentation shortcuts (click on the item and select Entire feature will be unavailable)

Install nodejs modules:

    npm install -g less
    npm install -g grunt-cli
    npm install grunt

Install grunt plugins:

    npm install grunt-contrib-copy
    npm install grunt-contrib-concat
    npm install grunt-contrib-uglify
    npm install grunt-contrib-less
    npm install grunt-closure-tools

## Build

Run the following command from the project root:

    grunt

The build tool will: 
- create a 'build/source' directory (if it doesn't exist)
- copy files across that will remain the same (eg css images)
- concatenate the combinable javascript files into a core file minimal-core.js (this file can be used for dev debugging)
- uglify (minify) the combinable javascripts into minimal-core.min.js
- uglify (minify) and copy standalone javascript into the build/source directory 
- compile the 'less' script to css.  Less files may be removed and just the css file used in production

## Code Quality Check

Javascript files can be checked against jslint:

    grunt lint

## Configuration

Specify required plugins in package.json. Configure tasks in Gruntfile.js.