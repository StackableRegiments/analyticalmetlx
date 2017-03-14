# Metl

## Description

This project provides the primary MeTL server, the high interaction comet-enabled javascript product (available at `/board`).  This is developed in Scala, and deploys as a WAR directly into a Java web container.  We've been using Jetty. 

## Run

We use SBT to compile and publish this app.  

To run a local server for this app, use:

    sbt
    > container:restart

## Configure

When running the application, there're a few configuration settings you'll need to set.  These are set by the files outside the WAR, and examples of the files are available in the config directory (eg `configuration.sample.xml`).  

The following Java system properties can be provided at the command line (see `sbt.bat` and `sbt.sh`):

### SBT

Set the location of SBT files and directories.

- `sbt.boot.directory="%IVY_HOME%\.sbt-boot"`
- `sbt.global.home="%IVY_HOME%\.sbt"`
- `sbt.home="%IVY_HOME%\.sbt"`
- `sbt.ivy.home="%IVY_HOME%\.ivy2\"`
- `sbt.global.staging="%IVY_HOME%\.sbt-staging"`

### MeTL

- `metlx.configurationFile="./config/configuration.local.xml"` sets the location of the MeTL config file.

See [README-config.md](README-config.md) for more detail.

### Logback 

MeTL uses Logback, which can be overridden to use a config file from outside the WAR.

- `logback.configurationFile="config/logback.xml"` sets the location of the Logback config file.

See [Logback](https://logback.qos.ch/manual/index.html) for more detail.

### Developer Options

- `run.mode="development"` instructs Lift to disable most caching (see [Simply Lift](https://simply.liftweb.net/index-3.1.html#toc-Subsection-3.1.2)).
- `metl.stopwatch.enabled="true"` enables duration logging of various actions. 
- `metl.stopwatch.minimumLog="1000"` requires that an action take longer than 1s before it is logged.
- `stackable.spending=enabled` enables use of third-party services defined in [README-config.md](README-config.md).  

## Deployment

This project publishes to a WAR file.  Use sbt's deploy goal to create the warfile:

    sbt deploy

and then copy it from `/target/scala-2.10/` to the container of your choice.
