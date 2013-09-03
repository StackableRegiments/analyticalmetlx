#! /bin/sh

mvn -U jetty:run -Djetty.port=8088 -Dmetl.backend=standalone -Drun.mode=production 1>log/output.log 2>&1 & 
