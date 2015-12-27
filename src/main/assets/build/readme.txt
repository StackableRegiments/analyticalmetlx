REQUIREMENT
===========
To build the source javascript and less script  you'll need "nodejs" installed on your machine.

* To install node.js on windows, please go to:
http://nodejs.org/download/

download the windows installer (.msi)
during the installation, at the step "Custom Setup", remember to uncheck the Online documentation shortcuts (click on the item and select Entire feature will be unavailable)

As well as these nodejs modules

npm install -g less
npm install -g grunt

It also uses grunt plugins, run 'npm install' in the build directory to install

grunt-contrib-copy
grunt-contrib-concat
grunt-contrib-uglify
grunt-contrib-less


BUILD
=====
To build run the following command line under the build directory:

  grunt

The build tool will 
- create a 'build/source' directory (if it doesn't exist)
- copy files across that will remain the same (eg css images)
- concatenate the combinable javascript files into a core file minimal-core.js (this file can be used for dev debugging)
- minify the combinable javascripts into minimal-core.min.js
- minify and copy standalone javascript into the build/source directory 
- compile the 'less' script to css.  Less files may be removed and just the css file used in production

CODE QUALITY CHECK
==================
You can check your javascript files againts jslint. To do so run:
  grunt lint
on your console under the build directory

CONFIGURATION
=============
package.json sets the plugins required
Gruntfile.js configures the tasks to be performed

