: >debug.log
wmic process where "name like '%java%'" delete
java -jar -Dwebdriver.chrome.driver=./tools/chromedriver/2.24-x64-chromedriver ./tools/selenium-2.53.1-server.jar &
./sbt.sh container:launch &
{ tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'
./node_modules/.bin/wdio wdio.conf.single.js
wmic process where "name like '%java%'" delete
