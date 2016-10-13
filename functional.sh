MODE=${1:-multi}
echo "$MODE player mode"
: >debug.log
wmic process where "name like '%java%'" delete
java -jar -Dwebdriver.chrome.driver=./tools/chromedriver/2.24-x64-chromedriver ./tools/selenium-2.53.1-server.jar &
./sbt.sh container:launch &
{ tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'
echo "Server launched"
./node_modules/.bin/wdio wdio.${MODE}.conf.js
wmic process where "name like '%java%'" delete
