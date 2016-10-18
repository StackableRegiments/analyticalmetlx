MODE=${1:-multi}
npm -v
node -v

echo "$MODE player mode"
rm debug.log
touch debug.log

if [[ "$SNAP_CI" ]]; then
    echo "Running in CI"
    npm install -g sprintf-js
    npm install -g wdio-mocha-framework
    npm install -g wdio-spec-reporter
    npm install -g lodash
    npm install -g wdio

    ls node_modules
    ls node_modules/wdio

    snap-shell

    java -jar -Dwebdriver.chrome.driver=/usr/local/bin/chromedriver ./tools/selenium-2.53.1-server.jar &

    sbt compile
    sbt -Xms1536m -Xmx1536m -Dlogback.configurationFile="config/logback.xml" -Dmetlx.configurationFile=/var/snap-ci/repo/config/configuration.ci.xml container:launch &
    { tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'
    ./node_modules/.bin/wdio wdio.${MODE}.conf.js
else
    echo "Running on local"
    wmic process where "name like '%java%'" delete

    java -jar -Dwebdriver.chrome.driver=./tools/chromedriver/2.24-x64-chromedriver -Djava.util.logging.config.file=logging.properties ./tools/selenium-2.53.1-server.jar &

    ./sbt.sh container:launch &
    { tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'
    ./node_modules/.bin/wdio wdio.${MODE}.conf.js
    wmic process where "name like '%java%'" delete
fi
