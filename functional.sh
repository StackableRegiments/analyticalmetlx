MODE=${1:-multi}
#npm -v
#node -v

echo "$MODE player mode"
#rm debug.log
#touch debug.log

if [ "$SNAP_CI" == "true" ] then
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

    echo "Starting WDIO"
    #java -jar -Dwebdriver.chome.driver=/usr/local/bin/chromedriver ./tools/selenium-2.53.1-server.jar &
    ./node_modules/wdio/node_modules/webdriverio/bin/wdio wdio.${MODE}.conf.js
	
    echo "stopping server: $(cat running.pid)"
    pkill -KILL -P $(cat running.pid)
    kill -KILL $(cat running.pid)
    rm running.pid
    ps -ef | grep java
else
    echo "Running on local"
    wmic process where "name like '%java%'" delete

    java -jar -Dwebdriver.chrome.driver=./tools/chromedriver/2.24-x64-chromedriver -Djava.util.logging.config.file=logging.properties ./tools/selenium-2.53.1-server.jar &

    ./sbt.sh container:launch &
    { tail -n +1 -f debug.log & } | sed -n '/bootstrap.liftweb.Boot - started/q'
    ./node_modules/.bin/wdio wdio.${MODE}.conf.js

    wmic process where "name like '%java%'" delete
fi
