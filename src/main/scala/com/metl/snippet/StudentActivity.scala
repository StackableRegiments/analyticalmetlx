package com.metl.snippet

import java.io.StringReader
import java.text.SimpleDateFormat

import com.github.tototoshi.csv.CSVReader
import com.metl.liftAuthenticator.OrgUnit
import com.metl.model.{D2LGroupsProvider, Globals}
import com.metl.view.ReportHelper
import net.liftweb.common.{Empty, Logger}
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmd
import net.liftweb.json.JsonAST.JString
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
    println("Student Activity: " + headers)
    stringReader.close()

    Call("updateActivity", JString(createHtmlTable(headers))).cmd
  }

  def render: CssBindFunc = {
    "#course" #> ajaxSelect(getOptions, Empty, handler)
  }

  def createHtmlTable(results: (List[String], List[Map[String, String]])): String = {
    "<table>" +
      "<tr>" +
      results._1.map(m => "<th>" + m + "</th>").mkString +
      "</tr>" +
      results._2.map(l => "<tr>" + results._1.map(m => "<td>" + l.getOrElse(m, "") + "</td>") + "</tr>").mkString +
      "</table>"
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
