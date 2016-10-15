MODE=${1:-multi}
echo "$MODE player mode"
: >debug.log

if [[ "$SNAP_CI" ]]; then
    echo "Running in CI"
    npm install sprintf-js
    npm install wdio-mocha-framework
    npm install wdio-spec-reporter
    npm install lodash
    npm install wdio

    java -jar -Dwebdriver.chrome.driver=/usr/local/bin/chromedriver ./tools/selenium-2.53.1-server.jar &

    sbt compile
    sbt -Xms1536m -Xmx1536m -logback.configurationFile="config/logback.xml" -Dmetlx.configurationFile=/var/snap-ci/repo/config/configuration.ci.xml container:launch &
else
    echo "Running on local"
    wmic process where "name like '%java%'" delete

    java -jar -Dwebdriver.chrome.driver=./tools/chromedriver/2.24-x64-chromedriver ./tools/selenium-2.53.1-server.jar &

    ./sbt.sh container:launch &
fi

echo "Waiting for boot"
{ tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'
echo "Boot complete"
./node_modules/.bin/wdio wdio.${MODE}.conf.js

if [[ "$SNAP_CI" ]] ; then
    echo "Closing on CI"
else
    echo "Closing on local"
    wmic process where "name like '%java%'" delete
fi
