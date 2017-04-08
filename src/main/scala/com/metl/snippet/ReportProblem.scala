package com.metl.snippet

import java.util.Date

import com.metl.comet.JArgUtils
import com.metl.data.ServerConfiguration
import com.metl.model.Globals
import net.liftweb.common.{Full, Logger}
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmd
import net.liftweb.http.{S, SHtml}
import net.liftweb.util.Helpers._

import scala.xml.{NodeSeq, Text}

class ReportProblem extends Logger with JArgUtils {

  def report(xhtml: NodeSeq): NodeSeq = {
    val searchParam = S.request.flatMap(_.param("search"))
    val queryParam = S.request.flatMap(_.param("query"))
    val conversationParam = S.request.flatMap(_.param("conversation"))
    val slideParam = S.request.flatMap(_.param("slide"))

    var reporter = Globals.currentUser.is
    var context =
      searchParam match {
        case Full("true") => queryParam.getOrElse("no query")
        case other =>
          val jid = conversationParam.getOrElse("noConversation")
          val title = conversationParam.map(j => ServerConfiguration.default.detailsOfConversation(j).title)
          val slide = slideParam.getOrElse("noSlide")
          f"$title%s ($jid%s.$slide%s)"
      }
    var report = ""

    val nextRandom = nextFuncName(new Date().getTime)
    val reportId = nextRandom.substring(nextRandom.length - 7, nextRandom.length - 1)

    def process() = {
      error("Report = " + report)

      val msg = searchParam match {
        case Full("true") => "Problem reported at search (#" + reportId +
          "). User: " + reporter +
          ", Query: " + context +
          ", Report: " + report
        case other => "Problem reported in conversation (#" + reportId +
          "). User: " + reporter +
          ", Conversation.Slide: " + context +
          ", Report: " + report
      }
      error(msg)
    }

    def respond(): JsCmd = {
      val message = "Thanks for reporting this problem, " + reporter + ". <br/>" +
        "The support team has been notified and will investigate. <br/>" +
        "Your reference is " + reportId + "."
      Call("formSubmitted", message).cmd
    }

    (for {
      r <- S.request
      if r.post_?
    } yield {
      <div class="directlyOnBackground">
        <h1>Problem Reported</h1>
        {Text("Thanks for reporting this problem, " + reporter + ".")}
        <br/>
        {Text("The support team has been notified and will investigate.")}
        <br/>
        {Text("Your reference is " + reportId + ".")}
      </div>
    }).openOr({
      bind("problem", xhtml,
        "reporter" -> SHtml.text(reporter, reporter = _, "class" -> "hideOnSubmit"),
        "context" -> SHtml.text(context, context = _, "readonly" -> "true", "class" -> "hideOnSubmit"),
        "report" -> SHtml.textarea(report, report = _, "class" -> "hideOnSubmit", "rows" -> "5"),
        "submit" -> SHtml.submit("Send", () => process(), "class" -> "hideOnSubmit"))
    })
  }
}
