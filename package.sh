#!/bin/sh

VER=$(head -n 1 src/main/resources/version)
if [ -n "$VER" ]
then
	SRC_WAR="target/scala-2.11/analyticalmetlx_2.11-$VER.war"
	echo "Building $SRC_WAR"
	
	./sbt.sh clean
	./sbt.sh compile
	./sbt.sh package

	REV=$(git log -1 --format="%H")
	echo "Uploading metlx-$REV.war to S3"
	aws --region=us-east-1 s3 cp $SRC_WAR s3://stackable-artifacts/metlx-$REV.war
else
	echo "Could not locate src/main/resources/version, or it was empty."
	exit 1
fi