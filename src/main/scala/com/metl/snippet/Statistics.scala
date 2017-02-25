package com.metl.snippet

import java.text.SimpleDateFormat
import java.util.Date

import com.metl.data.ServerConfiguration
import net.liftweb.common.Logger
import net.liftweb._
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.SHtml._
import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JsCommands._
import net.liftweb.json.JsonAST.{JArray, JString}
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.mapper._

object Statistics extends Statistics

class Statistics extends Logger {

  val format = new SimpleDateFormat("yyyyMMdd")
  val startDate = "20160801"

  def render: CssBindFunc = {
    "#refreshButton" #> ajaxButton("Refresh", () => {
      Call("updateResults", JString(createHtmlTable(runAllQueries))).cmd
    }) &
      "#loader" #> Script(OnLoad(Call("updateResults", JString(createHtmlTable(runAllQueries))).cmd))
  }

  def runQuery(name: String, sql: String, params: List[Any]): List[String] = {
    val results = DB.runQuery(sql, params)
    // results: (List[String] headers, List[List[String]] data)
    List(name, results._2.head.head)
  }

  def runAllQueries: List[List[String]] = {
    def toUnixTimestamp(s: String): String = {
      val second = format.parse(s).toInstant.getEpochSecond
      f"$second%d"
    }

    val informalStanzas = runQuery("Informal (ink, text, image) stanzas created",
      "select sum(" +
        "( select count(id) from h2ink where timestamp_c > ? ) + " +
        "( select count(id) from h2text where timestamp_c > ? ) + " +
        "( select count(id) from h2image where timestamp_c > ? ) " +
        ");",
      List(toUnixTimestamp(startDate),
        toUnixTimestamp(startDate),
        toUnixTimestamp(startDate)))
    val formalStanzas = runQuery("Formal (poll answer) stanzas created",
      "select count(id) from h2quizresponse where timestamp_c > ?;",
      List(toUnixTimestamp(startDate)))
    val allStanzas = informalStanzas(1).toInt + formalStanzas(1).toInt

    List(runQuery("Conversation count",
      "select count(*) from h2conversation where creation > ?;",
      List(toUnixTimestamp(startDate))),
      runQuery("Unique conversation authors",
        "select count(distinct author) from h2conversation where creation > ?;",
        List(toUnixTimestamp(startDate))),
      runQuery("Unique conversation attendees",
        "select count(distinct author) from h2attendance where timestamp_c > ?;",
        List(toUnixTimestamp(startDate))),
      List("All stanzas created", allStanzas.toString),
      informalStanzas,
      formalStanzas,
      runQuery("Unique stanza creators",
        "select count(a) from ( " +
          "select distinct author as a from h2ink where timestamp_c > ? " +
          "union " +
          "select distinct author as a from h2text where timestamp_c > ? " +
          "union " +
          "select distinct author as a from h2image where timestamp_c > ? " +
          "union " +
          "select distinct author as a from h2quizresponse where timestamp_c > ? " +
          ") as bob;",
        List(toUnixTimestamp(startDate),
          toUnixTimestamp(startDate),
          toUnixTimestamp(startDate),
          toUnixTimestamp(startDate))),
      runQuery("Unique informal (ink, text, image) stanza creators",
        "select count(a) from (" +
          "select distinct author as a from h2ink where timestamp_c > ? " +
          "union " +
          "select distinct author as a from h2text where timestamp_c > ? " +
          "union " +
          "select distinct author as a from h2image where timestamp_c > ? " +
          ") as bob;",
        List(toUnixTimestamp(startDate),
          toUnixTimestamp(startDate),
          toUnixTimestamp(startDate))),
      runQuery("Unique formal (poll response) stanza creators",
        "select count(distinct author) from h2quizresponse where timestamp_c > ?;",
        List(toUnixTimestamp(startDate))))
  }

  def createHtmlTable(results: List[List[String]]): String = {
    "<table>" + results.map(m => "<tr><td>" + m.head + "</td><td>&nbsp;</td><td class='result'>" + m(1) + "<td>").mkString + "</table>"
  }
}
