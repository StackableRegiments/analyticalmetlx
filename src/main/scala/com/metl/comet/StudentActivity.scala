package com.metl.comet

import java.util.Date

import com.metl.data.ServerConfiguration
import com.metl.liftExtensions.StronglyTypedJsonActor
import com.metl.view.StudentActivityReportHelper
import net.liftweb.actor.LiftActor
import net.liftweb.common.{Empty, Full, Logger, SimpleActor}
import net.liftweb.http.{ListenerManager, SHtml}
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmds.{OnLoad, Script}
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.util._

class StudentActivity extends StronglyTypedJsonActor with JArgUtils {

  protected lazy val serverConfig: ServerConfiguration = ServerConfiguration.default

  protected val blankOption: (String, String) = "" -> ""

  def getAllOptions: List[(String, String)] = {
    blankOption :: StudentActivityServer.getCoursesForAllConversations
  }

  def calculateResults(courseId: String, from: Date, to: Date): JObject = {
    val studentActivity = StudentActivityReportHelper.studentActivity(courseId)
    val headers = studentActivity.head

//    error("From: " + jQuery("#activityFrom"))

    createHtmlTable(courseId, (headers, studentActivity.tail))
  }

  override def render: CssBindFunc = {
    "#courses" #> selectElem[(String,String)](getAllOptions, Empty, BasicElemAttr("bob", "fred")) &
      "#loaderJs" #> Script(OnLoad(Call("init").cmd))
  }

  def createHtmlTable(courseId: String, results: (List[String], List[List[String]])): JObject = {
    JObject(List(
      JField("courseId", JString(courseId)),
      JField("headers", JArray(results._1.map(h => JString(h)))),
//      JField("data", JArray(results._2.filter(r => r.get("ConversationID").nonEmpty || r.get("D2LStudentID").nonEmpty).map(r => {
//        JObject(r.toList.map(kv => JField(kv._1, JString(kv._2))))
      JField("data", JArray(results._2.map(r => {
        JArray(r.map(value => JString(value)))
      })))
    ))
  }

  override protected val functionDefinitions: List[ClientSideFunction] = {
    List(ClientSideFunction("getStudentActivity", List("courseId", "from", "to"), (args) => {
      val courseId = getArgAsString(args.head).toLowerCase.trim
      val fromDate = getArgAsInt(args(1)) // Timestamp
      val toDate  = getArgAsInt(args(2)) // Timestamp
      calculateResults(courseId,new Date(fromDate),new Date(toDate))
      }, Full("updateActivity")))
  }

  override protected def registerWith: SimpleActor[Any] = {
    StudentActivityServer
  }
}

object StudentActivityServer extends LiftActor with ListenerManager with Logger {

  protected lazy val serverConfig: ServerConfiguration = ServerConfiguration.default

  def getCoursesForAllConversations: List[(String, String)] = {
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

  override protected def createUpdate: Any = {
    getCoursesForAllConversations
  }
}
