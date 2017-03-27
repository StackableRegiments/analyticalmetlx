package com.metl.snippet

import java.io.StringReader
import java.util.Date

import com.github.tototoshi.csv.CSVReader
import com.metl.data.ServerConfiguration
import com.metl.view.StudentActivityReportHelper
import net.liftweb.common.{Empty, Logger}
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds.{OnLoad, Script}
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.util.Helpers._
import net.liftweb.util._

class StudentActivity extends Logger {

  protected lazy val serverConfig: ServerConfiguration = ServerConfiguration.default

  protected val blankOption: (String, String) = "" -> ""

  def getAllOptions: List[(String, String)] = {
    blankOption :: getCoursesForAllConversations
  }

  def handler(courseId: String): JsCmd = {
    val stringReader = new StringReader(StudentActivityReportHelper.studentActivity(courseId))
    val headers = CSVReader.open(stringReader).allWithOrderedHeaders()
    stringReader.close()

    Call("updateActivity", createHtmlTable(headers)).cmd
  }

  def render: CssBindFunc = {
    "#courses" #> ajaxSelect(getAllOptions, Empty, handler) &
      "#loaderSelectize" #> Script(OnLoad(Call("selectize").cmd))
  }

  def createHtmlTable(results: (List[String], List[Map[String, String]])): JObject = {
    JObject(List(
      JField("headers", JArray(results._1.map(h => JString(h)))),
      JField("data", JArray(results._2.filter(r => r.get("ConversationID").nonEmpty || r.get("D2LStudentID").nonEmpty).map(r => {
        JObject(r.toList.map(kv => JField(kv._1, JString(kv._2))))
      })))
    ))
  }

  protected def getCoursesForAllConversations: List[(String, String)] = {
    info("Loading all conversations")
    val start = new Date().getTime
    val allConversations = serverConfig.getAllConversations

    var courses = Map[(String, String), String]()
    allConversations.filter(c => c.foreignRelationship.nonEmpty).sortBy(c => c.lastAccessed).foreach(c => {
      val relationship = c.foreignRelationship.get
      courses = courses + ((relationship.system, relationship.key) -> relationship.displayName.getOrElse(c.subject))
    })
    info("Loaded " + allConversations.length + " conversations (" + courses.size + " courses) from MeTL in " + (new Date().getTime - start) / 1000 + "s")
    courses.map(c => (c._1._2, c._2 + " (" + c._1._2 + ")")).toList.sortBy(c => c._2.toLowerCase)
  }
}
