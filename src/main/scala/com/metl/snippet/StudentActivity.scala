package com.metl.snippet

import java.io.StringReader
import java.text.SimpleDateFormat

import com.github.tototoshi.csv.CSVReader
import com.metl.view.ReportHelper
import net.liftweb.common.{Empty, Logger}
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmd
import net.liftweb.json.JsonAST.JString
import net.liftweb.util.Helpers._
import net.liftweb.util._

class StudentActivity extends Logger {

  val format = new SimpleDateFormat("yyyyMMdd")
  val startDate = "20160801"

  val blankOption: (String, String) = "" -> ""

  def getOptions: List[(String, String)] = {
    blankOption ::
      List(("6678", "Bob (6678)"))
  }

  def handler(courseId: String): JsCmd = {
    println("CourseId = " + courseId)

    val csv = ReportHelper.studentActivity(courseId)
    val stringReader = new StringReader(csv)
    val reader = CSVReader.open(stringReader)
    val headers = reader.allWithOrderedHeaders()
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
}
