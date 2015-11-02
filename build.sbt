name := "web-container-metlx"
version := "0.2.0"
organization := "io.github.stackableregiments"

scalaVersion := "2.11.5"

resolvers ++= Seq(
  "snapshots"     at "http://oss.sonatype.org/content/repositories/snapshots",
  "releases"        at "http://oss.sonatype.org/content/repositories/releases"
)

unmanagedResourceDirectories in Test <+= (baseDirectory) { _ / "src/main/webapp" }

scalacOptions ++= Seq("-deprecation", "-unchecked")

//seq(webSettings :_*)

jetty()

javaOptions in container ++= Seq(
  "-Dmetlx.configurationFile=config/configuration.local.xml",
  "-Dlogback.configurationFile=config/logback.xml",
  "-XX:+UseConcMarkSweepGC",
  "-XX:+CMSClassUnloadingEnabled"
)

libraryDependencies ++= {
    Seq(
//              "net.liftweb"       %% "lift-webkit" % liftVersion % "compile",
 //             "net.liftmodules"   %% "lift-jquery-module" % (liftVersion + "-2.2"),
              "org.eclipse.jetty" % "jetty-webapp"        % "8.1.7.v20120910"  % "container,test",
              "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container,test" artifacts Artifact("javax.servlet", "jar", "jar")
//              "ch.qos.logback" % "logback-classic" % "1.0.6"
    )
}

libraryDependencies ++= {
  val liftVersion = "2.6.2"
  val scalaVersionString = "2.11.5"
//  val logbackVersion = "1.0.1"
  Seq(
//    "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
//    "org.eclipse.jetty" % "jetty-webapp" % "9.1.5.v20140505",
//    "org.eclipse.jetty" % "jetty-plus" % "9.1.5.v20140505",
//    "org.slf4j" % "slf4j-simple" % "1.6.2",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
//    changing to logback-core as per Chris's changes
//    "ch.qos.logback" % "logback-core" % logbackVersion,
    "org.scala-lang" % "scala-library" % scalaVersionString,
    "org.scalatest" %% "scalatest" % "2.2.5" % "test",
    "org.scalaz.stream" %% "scalaz-stream" % "0.7.+",
		"org.specs2" %% "specs2" % "3.3.1" % "test",
		"org.mockito" % "mockito-core" % "1.9.0" % "test",
    "commons-io" % "commons-io" % "1.4",
    "org.apache.vysper" % "vysper-core" % "0.7" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "org.apache.vysper.extensions" % "xep0045-muc" % "0.7" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "org.pac4j" % "pac4j-saml" % "1.6.0",
    "javax.mail" % "mail" % "1.4",
    "net.liftweb" %% "lift-mapper" % liftVersion,
    "net.liftweb" %% "lift-webkit" % liftVersion,
    "net.liftweb" %% "lift-mongodb" % liftVersion,
    "net.liftweb" %% "lift-mongodb-record" % liftVersion,
    "org.seleniumhq.selenium" % "selenium-java" % "2.8.0",
    "io.github.stackableregiments" %% "common-utils" % "0.1.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "io.github.stackableregiments" %% "metldata" % "2.0.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "io.github.stackableregiments" %% "lift-authentication" % "0.2.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "io.github.stackableregiments" %% "ldap-authentication" % "0.2.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "io.github.stackableregiments" %% "form-authentication" % "0.2.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "io.github.stackableregiments" %% "cas-authentication" % "0.2.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
//    "io.github.stackableregiments" %% "metl2011" % "3.1.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"), //3.1.0 brings smack v4.1.4, but it's currently got issues.
    "io.github.stackableregiments" %% "metl2011" % "2.1.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "io.github.stackableregiments" %% "metl-h2" % "2.0.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "io.github.stackableregiments" %% "lift-extensions" % "0.1.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    "io.github.stackableregiments" %% "slide-renderer" % "0.2.+" exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri")
  )
}

// enable the in-sbt jettyContainer for testing

//enablePlugins(JettyPlugin)

//containerPort := 8080

// increase the time between polling for file changes when using continuous execution
pollInterval := 1000

// append several options to the list of options passed to the Java compiler
javacOptions ++= Seq("-source", "1.5", "-target", "1.5")

// append -deprecation to the options passed to the Scala compiler
scalacOptions += "-deprecation"

// define the repository to publish to
publishTo := Some("sonatype" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

// set Ivy logging to be at the highest level
ivyLoggingLevel := UpdateLogging.Full

// disable updating dynamic revisions (including -SNAPSHOT versions)
offline := false

// set the prompt (for this build) to include the project id.
shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }

// set the prompt (for the current project) to include the username
shellPrompt := { state => System.getProperty("user.name") + "> " }

// disable printing timing information, but still print [success]
showTiming := true

// disable printing a message indicating the success or failure of running a task
showSuccess := true

// change the format used for printing task completion time
timingFormat := {
    import java.text.DateFormat
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
}

testOptions in Test += Tests.Argument("-eI")

// add a JVM option to use when forking a JVM for 'run'
javaOptions += "-Xmx2G"

// don't aggregate clean (See FullConfiguration for aggregation details)
aggregate in clean := false

// only show warnings and errors on the screen for compilations.
//  this applies to both test:compile and compile and is Info by default
logLevel in compile := Level.Warn

// only show warnings and errors on the screen for all tasks (the default is Info)
//  individual tasks can then be more verbose using the previous setting
logLevel := Level.Warn

// only store messages at info and above (the default is Debug)
//   this is the logging level for replaying logging with 'last'
//persistLogLevel := Level.Debug

// only show 10 lines of stack traces
traceLevel := 10

// only show stack traces up to the first sbt stack frame
traceLevel := 0

credentials += Credentials(Path.userHome / ".ivy2" / "ivy-credentials")

// Exclude transitive dependencies, e.g., include log4j without including logging via jdmk, jmx, or jms.
/*
libraryDependencies += "log4j" % "log4j" % "1.2.15" excludeAll(
  ExclusionRule(organization = "com.sun.jdmk"),
  ExclusionRule(organization = "com.sun.jmx"),
  ExclusionRule(organization = "javax.jms")
)
*/
