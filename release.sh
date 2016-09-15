#!/bin/sh
REV=$(git log -1 --format="%H")
SRC_WAR="target/scala-2.11/web-container-metlx_2.11-0.2.0.war"
./sbt.sh clean
./sbt.sh compile
./sbt.sh package
aws --region=us-east-1 s3 cp $SRC_WAR s3://stackable-artifacts/metlx-$REV.war
