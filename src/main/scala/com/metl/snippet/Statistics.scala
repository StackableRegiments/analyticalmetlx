package com.metl.snippet

import java.text.SimpleDateFormat

import com.metl.model.{CacheConfig, ManagedCache}
import net.liftweb.common.Logger
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.JsonAST.JString
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import net.liftweb.util._
import net.sf.ehcache.config.MemoryUnit
import net.sf.ehcache.store.MemoryStoreEvictionPolicy

object Statistics extends Statistics

class Statistics extends Logger {
  protected val config = CacheConfig(10, MemoryUnit.MEGABYTES, MemoryStoreEvictionPolicy.LRU, Some(10))
  protected val reportCache = new ManagedCache[String, List[List[String]]]("statistics", (key: String) => runAllQueries, config)

  val format = new SimpleDateFormat("yyyyMMdd")
  val startDate = "20160801"

  def render: CssBindFunc = {
    "#statisticsButton" #> ajaxButton("Refresh", () => {
      Call("updateStatistics", JString(createHtmlTable(reportCache.get("enterprise")))).cmd
    }) &
      "#loaderStats" #> Script(OnLoad(Call("updateStatistics", JString(createHtmlTable(reportCache.get("enterprise")))).cmd))
  }

  def runQuery(name: String, sql: String, params: List[Any]): List[String] = {
    // results: (List[String] headers, List[List[String]] data)
    val results = DB.runQuery(sql, params)
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
        "( select count(id) from h2multiwordtext where timestamp_c > ? ) + " +
        "( select count(id) from h2image where timestamp_c > ? ) " +
        ");",
      List(toUnixTimestamp(startDate),
        toUnixTimestamp(startDate),
        toUnixTimestamp(startDate)))
    val formalStanzas = runQuery("Formal (poll response) stanzas created",
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
          "select distinct author as a from h2multiwordtext where timestamp_c > ? " +
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
          "select distinct author as a from h2multiwordtext where timestamp_c > ? " +
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
    "<table>" + results.map(m => "<tr><td>" + m.head + "</td><td>&nbsp;</td><td class='result'>" + m(1) + "</td></tr>").mkString + "</table>"
  }
}
