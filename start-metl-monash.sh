#!/bin/sh
mvn jetty:run -Djetty.port=8080 -Dmetl.backend=standalone -Drun.mode=production 1>logs/output.log 2>&1 &
