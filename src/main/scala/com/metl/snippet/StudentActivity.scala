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

    Call("updateActivity", createHtmlTable(courseId, headers)).cmd
  }

  def render: CssBindFunc = {
    "#courses" #> ajaxSelect(getAllOptions, Empty, handler) &
      "#loaderSelectize" #> Script(OnLoad(Call("selectize").cmd))
  }

  def createHtmlTable(courseId: String, results: (List[String], List[Map[String, String]])): JObject = {
    JObject(List(
      JField("courseId", JString(courseId)),
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

    val tuples = allConversations.filter(c => c.foreignRelationship.nonEmpty).sortBy(c => c.lastAccessed).map(c => {
      val relationship = c.foreignRelationship.get
      ((relationship.system, relationship.key), relationship.displayName.getOrElse(c.subject))
    })
    val courses: Map[(String, String), String] = Map(tuples: _*)

    info("Loaded " + allConversations.length + " conversations (" + courses.size + " courses) from MeTL in " + (new Date().getTime - start) / 1000 + "s")
    courses.map(c => {
      val key = c._1._2
      (key, c._2 + " (" + key + ")")
    }).toList.sortBy(c => c._2.toLowerCase)
  }
}
