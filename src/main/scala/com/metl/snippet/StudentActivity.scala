package com.metl.snippet

import java.io.StringReader

import com.github.tototoshi.csv.CSVReader
import com.metl.liftAuthenticator.OrgUnit
import com.metl.model.{D2LGroupsProvider, Globals}
import com.metl.view.ReportHelper
import net.liftweb.common.{Empty, Logger}
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmd
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.util.Helpers._
import net.liftweb.util._

class StudentActivity extends Logger {

  val blankOption: (String, String) = "" -> ""

  def getOptions: List[(String, String)] = {
    blankOption ::
      getCoursesForCurrentUser.getOrElse(List()).map(c => (c.foreignRelationship.get.key.toString, c.name))
  }

  def handler(courseId: String): JsCmd = {
    val stringReader = new StringReader(ReportHelper.studentActivity(courseId))
    val headers = CSVReader.open(stringReader).allWithOrderedHeaders()
    stringReader.close()

    Call("updateActivity", createHtmlTable(courseId, headers)).cmd
  }

  def render: CssBindFunc = {
    "#course" #> ajaxSelect(getOptions, Empty, handler)
  }

  def createHtmlTable(courseId: String, results: (List[String], List[Map[String, String]])): JObject = {
    JObject(List(
      JField("courseId", JString(courseId)),
      JField("headers", JArray(results._1.map(h => JString(h)))),
      JField("data", JArray(results._2.filter(r => r.get("ConversationID").nonEmpty || r.get("D2LStudentID").nonEmpty ).map(r => {
        JObject(r.toList.map(kv => JField(kv._1, JString(kv._2))))
      })))
    ))
  }

  /** Retrieve from D2L. */
  private def getCoursesForCurrentUser: Option[List[OrgUnit]] = {
    val m = Globals.getGroupsProviders.flatMap {
      case g: D2LGroupsProvider if g.canQuery && g.canRestrictConversations =>
        val groups = g.getGroupsFor(Globals.casState.is)
        println("Loaded " + groups.length + " groups from system " + g.storeId)
        groups
      case _ => None
    }
    Some(m)
  }
}
