package com.metl.snippet

import com.metl.comet.JArgUtils
import com.metl.data.ServerConfiguration
import com.metl.model.Globals
import net.liftweb.common.Logger
import net.liftweb.http.{S, SHtml}
import net.liftweb.http.SHtml.ajaxButton
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmds.{OnLoad, Script}
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.util.Helpers._
import net.liftweb.util._

import scala.xml.NodeSeq

//object ReportProblem extends ReportProblem

class ReportProblem extends Logger with JArgUtils {

//  var problemReport = ""

/*
  def render: CssBindFunc = {
    val context = getProblemContext
    "#name" #> SHtml.text(problemReport, report => {
      problemReport = report
    }) &
    "#reportLoader" #> Script(OnLoad(Call("updateContext", context).cmd)) &
      "#submitReportButton" #> ajaxButton("Send", () => {
        Call("displaySent", sendReport(context)).cmd
      })
  }
*/
//  private val jid = getArgAsString(args(0))

  // capture from whence the user came so we can send them back
  private val whence = S.referer openOr "/"

  def report (xhtml : NodeSeq) : NodeSeq = {
    var reporter = Globals.currentUser.is
    var context = "Doin' Stuff"
//    var report = ServerConfiguration.default.detailsOfConversation(conversationJid)
    var report = ""

    val searchParam = S.request.flatMap(_.param("search"))
    error("Search param: %s".format(searchParam))
    def process (): Unit = {
      error("Problem reported. User: " + reporter + ", Context: " + context + ", Search: " + searchParam.getOrElse("unset") + ", Report: " + report)
      S.notice("Thanks for reporting a problem. The support team has been notified and will investigate.")
      S.redirectTo(whence)
    }

    bind("problem", xhtml,
      "reporter" -> SHtml.text(reporter, reporter = _, "readonly" -> "true"),
      "context" -> SHtml.text(context, context = _, "readonly" -> "true"),
      "report" -> SHtml.textarea(report, report = _),
      "submit" -> SHtml.submit("Send", process))
  }

/*
  protected def getUsername: String = {
    Globals.currentUser.is
  }

  private def getCurrentContext = {
    "Doin' Stuff"
  }

  protected def getProblemContext: JObject = {
    JObject(List(JField("username", JString(getUsername)),
      JField("context", JString(getCurrentContext))))
  }
*/

/*
  protected def sendReport(problemContext: JObject): String = {
    error("Problem reported. User: " + problemContext.values.getOrElse("username", "") + ", Context: " + problemContext.values.getOrElse("context", "") + ", Report: " + problemReport)
    "Thanks for reporting a problem. The support team has been notified and will investigate."
  }
*/
}
