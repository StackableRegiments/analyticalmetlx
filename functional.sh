MODE=${1:-multi}

echo "$MODE player mode"
rm debug.log
touch debug.log

if [[ "$SNAP_CI" == "true" ]]
then
    echo "Running in CI"

    sbt -Xms1536m -Xmx1536m -Dlogback.configurationFile=/var/snap-ci/repo/config/logback.xml -Dmetlx.configurationFile=/var/snap-ci/repo/config/configuration.ci.xml container:launch &
    sleep 150
    #{ tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'

    echo $! > running.pid
    sleep 30
    ps -ef | grep java
    echo "serverStarted: $(cat running.pid)"
    curl -vvv http://localhost:8080/serverStatus

    npm install wdio
    npm install lodash
    npm install sprintf-js
    npm install wdio-mocha-framework
    npm install wdio-spec-reporter
    npm install wdio-selenium-standalone-service
    #npm install wdio-sauce-service
    #npm install sauce-connect
    #npm install mocha-sauce-notifying-reporter

    java -jar -Dwebdriver.chrome.driver=./tools/chromedriver/2.24-x64-chromedriver -Djava.util.logging.config.file=logging.properties ./tools/selenium-2.53.1-server.jar &
    sleep 30

    echo "Starting WDIO"
    #java -jar -Dwebdriver.chome.driver=/usr/local/bin/chromedriver ./tools/selenium-2.53.1-server.jar &
    ./node_modules/wdio/node_modules/webdriverio/bin/wdio wdio.${MODE}.conf.js

    echo "stopping server: $(cat running.pid)"
    pkill -KILL -P $(cat running.pid)
    kill -KILL $(cat running.pid)
    rm running.pid
    ps -ef | grep java
else
    ARGS=$@
    echo "Running on local: ${ARGS}"
    function reset {
        wmic process where "name like '%java%'" delete
        wmic process where "name like '%tail.exe%'" delete
        rm debug.log
        touch debug.log
    }
    function launch {
        java -jar -Dwebdriver.chrome.driver=./tools/chromedriver/2.24-x64-chromedriver -Djava.util.logging.config.file=logging.properties ./tools/selenium-2.53.1-server.jar &
        ./sbt.sh -Dstackable.spending=$1 -Dmetlingpot.chunking.timeout=7000 container:launch & { tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'
        ./node_modules/.bin/wdio --suite=$2 wdio.${MODE}.conf.js
    }
    reset
    launch disabled learning
    reset
    #launch enabled analyzing
    reset
fi
