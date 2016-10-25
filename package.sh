#!/bin/sh

VER=$1
if [ -n "$VER" ]
then
	SRC_WAR="target/scala-2.11/analyticalmetlx_2.11-$VER.war"
	echo "Building $SRC_WAR"
	
	./sbt.sh clean
	./sbt.sh compile
	./sbt.sh package

	REV=$(git log -1 --format="%H")
	echo "Uploading metlx-$REV.war to S3"
#	aws --region=us-east-1 s3 cp $SRC_WAR s3://stackable-artifacts/metlx-$REV.war	
else
	echo "Please provide the version as the first parameter, eg 0.8.5"
	exit 1
fi