MODE=${1:-multi}
echo "$MODE player mode"
: >debug.log

npm install sprintf-js
npm install wdio-mocha-framework
npm install wdio-sauce-service
npm install wdio-spec-reporter
npm install lodash
npm install wdio

wmic process where "name like '%java%'" delete
java -jar -Dwebdriver.chrome.driver=./tools/chromedriver/2.24-x64-chromedriver ./tools/selenium-2.53.1-server.jar &
./sbt.sh container:launch &
{ tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'
echo "Server launched"
./node_modules/.bin/wdio wdio.${MODE}.conf.js
wmic process where "name like '%java%'" delete
