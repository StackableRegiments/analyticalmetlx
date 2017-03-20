# Configuration

An example config file which uses a local database and no authentication can be found in [config/configuration.sample.xml](config/configuration.sample.xml).

Of particular interest for end-user customisation are:

```
<serverConfiguration>
```

## Database 
We use [H2](http://www.h2database.com) as an in-memory database for local development.

```
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
```

##Caching
Tune server caching. 
```
	<caches>
		<roomLifetime miliseconds="3600000"/>
		<resourceCache heapSize="100" heapUnits="MEGABYTES" evictionPolicy="LeastRecentlyUsed" />
	</caches>
```

##Groups
See sample files for format.
```
  <groupsProvider>
		<selfGroups/>
		<flatFileGroups format="globalOverrides" location="config/globalOverrides.txt" refreshPeriod="5 minutes"/>
		<flatFileGroups format="specificOverrides" location="config/specificOverrides.txt" refreshPeriod="5 minutes"/>
		<flatFileGroups format="xmlSpecificOverrides" location="config/specificOverrides.xml" refreshPeriod="5 minutes"/>
	</groupsProvider>
```

## Service Providers
Upstream foreign document import into images, if required:
```
	<cloudConverterApiKey>anExampleApiKey</cloudConverterApiKey>
```

Upstream text analysis, if required:
```
	<textAnalysisApiKey>anExampleApiKey</textAnalysisApiKey>
```

Upstream theme analysis, if required:
```
	<themeAnalysisApiKey>anExampleApiKey</themeAnalysisApiKey>
```

## Google Analytics
Add your tracking ID here.
```
	<googleAnalytics>anExampleGoogleAnalytics</googleAnalytics>
```

```
</serverConfiguration>
```