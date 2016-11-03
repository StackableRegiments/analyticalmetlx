# Metl

## Description

This project provides the primary metl server.  It provides the server-rendered low-interaction metlviewer product, the high interaction comet-enabled javascript product (available at /board), and endpoints for the native windows client to connect.  This is developed in scala, and deploys as a WAR directly into a java web container.  We've been using Jetty. 

## Development

We use Grunt to prepare js and css for packaging.  Details can be found in [README-grunt.md](README-grunt.md). 

We use SBT to compile and publish this app.  To run a localserver of this app, use:

    sbt
    > container:restart

## Configuration

When running the application, there are a few configuration settings you'll need to set.  These are set by the files outside the WAR, and examples of the files are available in the config directory.  The location of the files need to be specified to the WAR with a java system property (```-Dmetlx.configurationFile="/etc/metl/"```).  Logging uses logback, and that can be overridden to use a config file from outside the WAR with a java system property (```-Dlogback.configurationFile="config/logback.xml"```).

An example config file which uses a local database and no authentication might look like:

    <serverConfiguration>
      <liftConfiguration>
        <cometRequestTimeout>25</cometRequestTimeout>
        <maxMimeSize>1048576000</maxMimeSize>
        <maxMimeFileSize>524288000</maxMimeFileSize>
        <bufferUploadsOnDisk>true</bufferUploadsOnDisk>
        <maxConcurrentRequestsPerSession>100</maxConcurrentRequestsPerSession>
        <allowParallelSnippets>true</allowParallelSnippets>
      </liftConfiguration>
      <defaultServerConfiguration>sqlAdaptor</defaultServerConfiguration>
      <serverConfigurations>
        <server>
          <type>sql</type>
          <name>sqlAdaptor</name>
          <driver>org.h2.Driver</driver>
          <url>jdbc:h2:./testdb.h2;AUTO_SERVER=TRUE;MVCC=TRUE</url>
        </server>
        <server>
          <type>transientLoopback</type>
        </server>
        <server>
          <type>frontend</type>
        </server>
      </serverConfigurations>
      <caches>
        <roomLifetime miliseconds="3600000"/>
        <resourceCache heapSize="100" heapUnits="MEGABYTES" evictionPolicy="LeastRecentlyUsed" />
      </caches>
      <importerPerformance parallelism="8"/>
      <clientConfig>
        <xmppDomain>local.temp</xmppDomain>
        <imageUrl><![CDATA[https://avatars3.githubusercontent.com/u/14121932?v=3&s=460]]></imageUrl>
      </clientConfig>
      <securityProvider>
        <stableKeyProvider/> 
      </securityProvider>
      <authenticationConfiguration>
        <requestUriStartWith value="/comet_request"/>
        <requestUriStartWith value="/ajax_request"/>
        <requestUriStartWith value="/favicon.ico"/>
        <requestUriStartWith value="/serverStatus"/>
        <requestUriStartWith value="/static"/>
        <requestUriStartWith value="/classpath"/>
      </authenticationConfiguration>
      <authentication>
        <mock/>
      </authentication>
      <groupsProvider>
        <selfGroups/>
        <flatFileGroups format="globalOverrides" location="config/globalOverrides.txt" refreshPeriod="5 minutes"/>
        <flatFileGroups format="specificOverrides" location="config/specificOverrides.txt" refreshPeriod="5 minutes"/>
        <flatFileGroups format="xmlSpecificOverrides" location="config/specificOverrides.xml" refreshPeriod="5 minutes"/>
      </groupsProvider>
      <cloudConverterApiKey>anExampleApiKey</cloudConverterApiKey>
      <textAnalysisApiKey>anExampleApiKey</textAnalysisApiKey>
      <themeAnalysisApiKey>anExampleApiKey</themeAnalysisApiKey>
    </serverConfiguration>

- Provide cloudConverterApiKey to enable upstream foreign document import into images, if required.
- Provide textAnalysisApiKey to enable upstream text analysis, if required.
- Provide themeAnalysisApiKey to enable upstream theme analysis, if required.

## Deployment

This project publishes to a WAR file.  Use sbt's deploy goal to create the warfile.

    sbt deploy

and then copy the warfile from ```/target/scala-2.10/``` to the container of your choice.
