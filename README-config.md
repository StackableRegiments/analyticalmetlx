# Configuration

An example config file which uses a local database and no authentication might look like:

```
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
```

- Provide cloudConverterApiKey to enable upstream foreign document import into images, if required.
- Provide textAnalysisApiKey to enable upstream text analysis, if required.
- Provide themeAnalysisApiKey to enable upstream theme analysis, if required.