import com.typesafe.sbt.SbtStartScript
import SbtStartScript.StartScriptKeys._
import com.earldouglas.xsbtwebplugin.WebPlugin

name := "web-container-metlx"
version := "0.2.0"
organization := "io.github.stackableregiments"

val scalaVersionString = "2.11.5"

scalaVersion := scalaVersionString

resolvers ++= Seq(
  "snapshots"     at "http://oss.sonatype.org/content/repositories/snapshots",
  "releases"        at "http://oss.sonatype.org/content/repositories/releases"
)

seq(webSettings :_*)

startScriptJettyVersion in Compile := "9.2.10.v20150310"

startScriptJettyChecksum := "45b03a329990cff2719d1d7a1d228f3b7f6065e8"

startScriptJettyURL in Compile <<= (startScriptJettyVersion in Compile) { (version) => "http://refer.adm.monash.edu/jetty-distribution-" + version + ".zip" }

startScriptJettyContextPath := "/"

startScriptJettyHome in Compile <<= (streams, target, startScriptJettyURL in Compile, startScriptJettyChecksum in Compile) map startScriptJettyHomeTask

startScriptForWar in Compile <<= (streams, startScriptBaseDirectory, startScriptFile in Compile, com.earldouglas.xsbtwebplugin.PluginKeys.packageWar in Compile, startScriptJettyHome in Compile, startScriptJettyContextPath in Compile) map startScriptForWarTask

startScript in Compile <<= startScriptForWar in Compile

seq(genericStartScriptSettings:_*)

unmanagedResourceDirectories in Test <+= (baseDirectory) { _ / "src/main/webapp" }

scalacOptions ++= Seq("-deprecation", "-unchecked")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.+"

libraryDependencies ++= {
  val liftVersion = "2.6.2"
  Seq(
    "org.eclipse.jetty" % "jetty-webapp"        % "8.1.7.v20120910"  % "container,test",
  "org.eclipse.jetty"           %  "jetty-plus"               % "8.1.7.v20120910"     % "container,test", // _for _jetty _config
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container,test" artifacts Artifact("javax.servlet", "jar", "jar"),
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
    "org.scala-lang" % "scala-library" % scalaVersionString,
    "org.scalatest" %% "scalatest" % "2.2.5" % "test",
    "org.scalaz.stream" %% "scalaz-stream" % "0.7.+",
    "org.specs2" %% "specs2" % "3.3.1" % "test",
    "org.mockito" % "mockito-core" % "1.9.0" % "test",
    "commons-io" % "commons-io" % "1.4",
    "org.apache.vysper" % "vysper" % "0.7",
    "org.apache.vysper" % "vysper-core" % "0.7",
    "org.apache.vysper" % "vysper-server" % "0.7",
    "org.apache.vysper.extensions" % "xep0045-muc" % "0.7",
    "org.pac4j" % "pac4j-saml" % "1.6.0",
    "javax.mail" % "mail" % "1.4",
    "net.liftweb" %% "lift-mapper" % liftVersion,
    "net.liftweb" %% "lift-webkit" % liftVersion,
    "net.liftweb" %% "lift-mongodb" % liftVersion,
    "net.liftweb" %% "lift-mongodb-record" % liftVersion,
    "org.seleniumhq.selenium" % "selenium-java" % "2.8.0",
    "org.apache.poi" % "poi" % "3.13",
    "org.apache.poi" % "poi-ooxml" % "3.13",
    "org.apache.poi" % "poi-ooxml-schemas" % "3.13",
    "org.apache.poi" % "poi-scratchpad" % "3.13",
    "io.github.stackableregiments" %% "common-utils" % "0.2.+",
    "io.github.stackableregiments" %% "metldata" % "3.4.+",
    "io.github.stackableregiments" %% "lift-authentication" % "0.2.+",
    "io.github.stackableregiments" %% "ldap-authentication" % "0.2.+",
    "io.github.stackableregiments" %% "form-authentication" % "0.2.+",
    "io.github.stackableregiments" %% "cas-authentication" % "0.2.+",
    "io.github.stackableregiments" %% "openid-connect-authentication" % "0.2.+",
    "io.github.stackableregiments" %% "metl2011" % "3.9.+",
    "io.github.stackableregiments" %% "metl-h2" % "3.6.+",
//    "io.github.stackableregiments" %% "slide-renderer" % "1.3.+",
    "io.github.stackableregiments" %% "lift-extensions" % "0.1.+"
  )
}.map(_.excludeAll(ExclusionRule(organization = "org.slf4j")).exclude("com.sun.jdmk","jmxtools").exclude("javax.jms","jms").exclude("com.sun.jmx","jmxri"))

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
