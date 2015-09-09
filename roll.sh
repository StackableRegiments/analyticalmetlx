#!/bin/bash
export SCRIPT_DIR=/c/sandbox/saintLeo/jettycontainer
cp target/webmetl-3.0.war $SCRIPT_DIR/apps
$SCRIPT_DIR/stop.sh
$SCRIPT_DIR/start.sh
echo "Rolled system to $SCRIPT_DIR"
