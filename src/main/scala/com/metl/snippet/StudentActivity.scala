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
    blankOption ::
      getCoursesForAllConversations.map(c => (c._2, c._3))
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

  protected def getCoursesForAllConversations: List[(String, String, String)] = {
    println("Loading all conversations")
    val start = new Date().getTime
    val allConversations = serverConfig.getAllConversations

    val conversations = allConversations.filter(c => c.foreignRelationship.nonEmpty).sortBy(c => c.lastAccessed).map(c => {
      val relationship = c.foreignRelationship.get
      relationship.displayName match {
        case Some(s) => Some((relationship.system, relationship.key, s))
        case None => Some((relationship.system, relationship.key, c.subject))
        case _ => None
      }
    })
    println("Loaded all " + conversations.length + " conversations from MeTL in " + (new Date().getTime - start) / 1000 + "s")

    conversations.filter(o => o.nonEmpty).map(o => o.get)
  }
}
