#!/bin/sh
SCRIPT_DIR=`dirname $0`
echo "Script dir: $SCRIPT_DIR"
IVY_HOME=$HOME/.ivy2/
echo "Ivy home: $IVY_HOME"
java -Xmx1024M -XX:MaxPermSize=128m -XX:+UseConcMarkSweepGC -XX:+CMSPermGenSweepingEnabled -XX:+CMSClassUnloadingEnabled -Dsbt.boot.directory="$IVY_HOME/.sbt-boot" -Dsbt.global.home="$IVY_HOME/.sbt" -Dsbt.home="$IVY_HOME/.sbt" -Dsbt.ivy.home="$IVY_HOME/.ivy2" -Dsbt.global.staging="$IVY_HOME/.sbt-staging" -Dmetlx.configurationFile="config/configuration.local.xml" -Dlogback.configurationFile="config/logback.xml" -Drun.mode="development" -jar $SCRIPT_DIR/sbt-launch.jar "$@"
