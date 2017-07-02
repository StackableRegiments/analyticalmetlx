package com.metl.comet

import java.util.Date

import com.metl.data.ServerConfiguration
import com.metl.liftExtensions.StronglyTypedJsonActor
import com.metl.view.StudentActivityReportHelper
import net.liftweb.util.Helpers._
import net.liftweb.actor.LiftActor
import net.liftweb.common.{Full, Logger, SimpleActor}
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds.{OnLoad, Script}
import net.liftweb.http.{ListenerManager, RenderOut}
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}

class StudentActivity extends StronglyTypedJsonActor with JArgUtils {

  protected lazy val serverConfig: ServerConfiguration = ServerConfiguration.default

  protected val blankOption: (String, String) = "" -> "All"

  def getAllOptions: List[(String, String)] = {
    blankOption :: StudentActivityServer.getCoursesForAllConversations
  }

  def calculateResults(courseId: Option[String], from: Option[Date], to: Option[Date]): JObject = {
    val (sortedFrom,sortedTo) = (for {
      sf <- from
      st <- to
      if sf.after(st)
    } yield (to,from)).getOrElse((from,to))

    val studentActivity = StudentActivityReportHelper.studentActivity(courseId,sortedFrom,sortedTo)
    val headers = studentActivity.head
    createJResults(courseId, (headers, studentActivity.tail))
  }

  override def render: RenderOut = {
      "#loaderJs" #> Script(OnLoad(Call("init").cmd))
  }

  def createJCourses(courses: (List[(String, String)])): JArray = {
    JArray(courses.map(c => JObject(List(JField("id",JString(c._1)),
      JField("name",JString(c._2))))))
  }

  def createJResults(courseId: Option[String], results: (List[String], List[List[String]])): JObject = {
    JObject(List(
      JField("courseId", JString(courseId.getOrElse(""))),
      JField("headers", JArray(results._1.map(h => JString(h)))),
      JField("data", JArray(results._2.map(r => {
        JArray(r.map(value => JString(value)))
      })))
    ))
  }

  override lazy val functionDefinitions: List[ClientSideFunction] =
    List(ClientSideFunction("getCourses", List(), (_) => {
      val options = getAllOptions
      createJCourses(options)
    }, Full("updateCourses")),
      ClientSideFunction("getStudentActivity", List("from", "to", "courseId"), (args) => {
      val fromDate = tryo(new Date(getArgAsLong(args.head))) // Timestamp
      val toDate = tryo(new Date(getArgAsLong(args(1)))) // Timestamp
      val courseId = tryo(getArgAsString(args(2)).toLowerCase.trim).filter(c => c.nonEmpty)
      calculateResults(courseId, fromDate, toDate)
    }, Full("updateActivity")))

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
      val id = c._1._2
      val name = c._2
      (id, name)
    }).toList.sortBy(c => c._2.toLowerCase)
  }

  override protected def createUpdate: Any = {
    Nil
  }
}
