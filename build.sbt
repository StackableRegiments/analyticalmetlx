import com.typesafe.sbt.SbtStartScript
import SbtStartScript.StartScriptKeys._

import collection.JavaConversions._
import com.stackableregiments.Minifier

name := "analyticalmetlx"
organization := "com.stackableregiments"
version := "1.4.6"

val scalaVersionString = "2.11.5"

scalaVersion := scalaVersionString

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.metl",
    buildInfoOptions += BuildInfoOption.BuildTime
  )

resolvers ++= Seq(
  "snapshots"     at "https://oss.sonatype.org/content/repositories/snapshots",
  "releases"        at "https://oss.sonatype.org/content/repositories/releases"
)

enablePlugins(JettyPlugin)

val jettyVersion = "9.4.0.v20161208"

containerLibs in Jetty := Seq("org.eclipse.jetty" % "jetty-runner" % jettyVersion intransitive())

containerPort := 8080

javaOptions in Jetty := Seq("-Dmetlx.configurationFile=./config/configuration.local.xml", "-Dlogback.configurationFile=config/logback.xml", "-Drun.mode=development", "-Dmetl.stopwatch.enabled=true", "-Dmetl.stopwatch.minimumLog=1000")

fork in Jetty := true

//target in webappPrepare := (sourceDirectory in Compile).value / "webapp"
target in webappPrepare := (baseDirectory).value / "target/webapp"

watchSources := watchSources.value.filterNot { x => x.isDirectory || x.getAbsolutePath.contains("webapp") }

unmanagedResourceDirectories in Test <+= {(baseDirectory) { _ / "src/main/webapp" }}

scalacOptions ++= Seq("-deprecation", "-unchecked")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.+"

libraryDependencies ++= {
  val liftVersion = "2.6.2"
  Seq(
    "org.eclipse.jetty" % "jetty-webapp"  % jettyVersion % "container,test",
    "org.eclipse.jetty" %  "jetty-server"   % jettyVersion % "container,test", // _for _jetty _config
    "org.eclipse.jetty" %  "jetty-util"   % jettyVersion % "container,test", // _for _jetty _config
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % "container,test", // artifacts Artifact("javax.servlet", "jar", "jar"),
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
    "org.scala-lang" % "scala-library" % scalaVersionString,
    "org.scalatest" %% "scalatest" % "2.2.5" % "test",
    "org.scalaz.stream" %% "scalaz-stream" % "0.7.+",
    "org.specs2" %% "specs2" % "3.3.1" % "test",
    "org.mockito" % "mockito-core" % "1.9.0" % "test",
    "commons-io" % "commons-io" % "1.4",
    "org.apache.httpcomponents" % "httpmime" % "4.5.2",
    "org.apache.vysper" % "vysper" % "0.7",
    "org.apache.vysper" % "vysper-core" % "0.7",
    "org.apache.vysper" % "vysper-server" % "0.7",
    "org.apache.vysper.extensions" % "xep0045-muc" % "0.7",
    "org.pac4j" % "pac4j-saml" % "1.6.0",
    "javax.mail" % "mail" % "1.4",
    "net.liftweb" %% "lift-mapper" % liftVersion,
    "net.liftweb" %% "lift-webkit" % liftVersion,
    "net.liftweb" %% "lift-testkit" % liftVersion,
    "net.liftweb" %% "lift-mongodb" % liftVersion,
    "net.liftweb" %% "lift-mongodb-record" % liftVersion,
    "org.seleniumhq.selenium" % "selenium-java" % "2.8.0",
    "org.apache.poi" % "poi" % "3.13",
    "org.apache.poi" % "poi-ooxml" % "3.13",
    "org.apache.poi" % "poi-ooxml-schemas" % "3.13",
    "org.apache.poi" % "poi-scratchpad" % "3.13",
    "net.sf.ehcache" % "ehcache" % "2.10.1",
    "io.github.stackableregiments" %% "xmpp" % "3.5.+" ,
    "com.h2database" % "h2" % "1.4.192",
    "mysql" % "mysql-connector-java" % "5.1.38",
    "org.apache.shiro" % "shiro-core" % "1.2.4",
    "org.apache.shiro" % "shiro-web" % "1.2.4",
    "org.apache.commons" % "commons-compress" % "1.1",
    "org.imsglobal" % "basiclti-util" % "1.1.1",
    //for google openId connect authentication
    "com.google.api-client" % "google-api-client" % "1.22.0",
    "io.github.stackableregiments" %% "ldap" % "0.3.+",
    // for brightspark integration
    "commons-codec" % "commons-codec" % "1.7",
    "commons-fileupload" % "commons-fileupload" % "1.3.2",
    "com.github.tototoshi" %% "scala-csv" % "1.3.3",
    //for videoconferencing
    "org.kurento" % "kurento-client" % "6.5.0",
    "org.kurento" % "kurento-utils-js" % "6.5.0",
    //for tokbox
    "com.tokbox" % "opentok-server-sdk" % "2.3.2",
    "com.google.apis" % "google-api-services-vision" % "v1-rev23-1.22.0",
    "com.github.tototoshi" %% "scala-csv" % "1.3.3",
    //for batik (svg)
    "org.apache.xmlgraphics" % "batik-transcoder" % "1.6.1",
    //for AWS API Gateway interaction
	  "com.amazonaws" % "aws-java-sdk-opensdk" % "1.11.72"
  )
}.map(_.excludeAll(ExclusionRule(organization = "org.slf4j")).exclude("com.sun.jdmk","jmxtools").exclude("javax.jms","jms").exclude("com.sun.jmx","jmxri"))

javacOptions ++= Seq("-source", "8", "-target", "8")

// append -deprecation to the options passed to the Scala compiler
scalacOptions += "-deprecation"

// define the repository to publish to
publishTo := Some("sonatype" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

// set Ivy logging to be at the highest level
ivyLoggingLevel := UpdateLogging.Full

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

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
//testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-f", "target/test-report")
//testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-report")
//testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-report")

// don't buffer test output, as ScalaTest does a better job
logBuffered in Test := false

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

val functionalSingleTests = taskKey[Unit]("functional single-player tests")
val functionalMultiTests = taskKey[Unit]("functional multi-player tests")

lazy val library = (project in file("library")).
  settings(
    functionalSingleTests := {
      Process(List("./node_modules/wdio/node_modules/.bin/wdio wdio.single.conf.js", ".")) #>> file("functionalSingleTests.log") !
    },
    functionalMultiTests := {
      Process(List("./node_modules/wdio/node_modules/.bin/wdio wdio.multi.conf.js", ".")) #>> file("functionalMultiTests.log") !
    }
  )

unmanagedResourceDirectories in Compile <+= (baseDirectory) { _ / "target/extra-resources" }

webappPostProcess := { (webappDir:File) => {
  val targetWebappDir = baseDirectory.value / "target" / "webapp"
  val htmlSubDir = "hmin"
  val jsRelativePath = { "static/js-min" }
  val htmlExtension = ".html"
  val javascriptExtension = ".js"

  println("Minifying javascript")
  val minifier = new Minifier(targetWebappDir.getPath, (targetWebappDir / htmlSubDir).getPath, jsRelativePath, htmlExtension);
  val minifyFiles = List("admin", "board", "clientSidePrintConversation", "conversationSearch", "conversations", "dashboard", "editConversation", "enterprise", "mobile", "remotePluginConversationChooser")
  minifier.minify(minifyFiles)

  println("Replacing original html and javascript with minified")
  IO.createDirectory(targetWebappDir / jsRelativePath)

  minifyFiles.foreach(fn => {
    IO.copyFile(targetWebappDir / htmlSubDir / (fn + htmlExtension), targetWebappDir / (fn + htmlExtension))
    IO.copyFile(targetWebappDir / htmlSubDir / jsRelativePath / (fn + javascriptExtension), targetWebappDir / jsRelativePath / (fn + javascriptExtension))
    IO.delete( targetWebappDir / htmlSubDir / (fn + htmlExtension))
    IO.delete( targetWebappDir / htmlSubDir / jsRelativePath / (fn + javascriptExtension))
  })

  println("Cleaning up minified html and javascript")
  IO.delete(targetWebappDir / htmlSubDir / jsRelativePath)
  IO.delete(targetWebappDir / htmlSubDir)
}
}