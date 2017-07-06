package com.metl.snippet

import com.metl.utils.ExceptionUtils
import com.metl.BuildInfo
import net.liftweb.http._
import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.util.Helpers._

import scala.xml._
import org.apache.commons.io.IOUtils

object VersionFacts extends Logger {
  def getResourceAsString(resourceName:String):String = {
    try {
      IOUtils.toString(this.getClass.getClassLoader.getResourceAsStream(resourceName))
    } catch {
      case e:Exception => {
        error("exception getting resourceAsString(%s): %s\r\n%s".format(resourceName,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
        ""
      }
    }
  }
  val releaseNotes:List[List[String]] = {
    val sections = getResourceAsString("release-notes").split("(?m)^\\s*$")
    sections.map(s => { s.trim.split("\r").flatMap(_.trim.split("\n").toList).toList.map(_.trim)}).toList
  }
}

object VersionDescriber extends VersionDescriber

class VersionDescriber {
  def render = {
    ".version" #> {
      ".versionNumber *" #> Text(BuildInfo.version)
    } &
    ".version" #> {
      ".scalaVersion *" #> Text(BuildInfo.scalaVersion)
    } &
    ".version" #> {
      ".sbtVersion *" #> Text(BuildInfo.sbtVersion)
    } &
    "#releaseNotesContainer" #> VersionFacts.releaseNotes.map(s => {
        ".releaseNotesSection *" #> <div>{
          <p><strong>{s.head}</strong></p>
          <ul>{
            s.tail.map(rn => {
            <li>{Text(rn)}</li>
          })}</ul>
        }</div>
      })
  }
}
