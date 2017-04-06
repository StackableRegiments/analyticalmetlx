package com.metl.snippet


import com.metl.model.Globals
import net.liftweb.common.Logger
import net.liftweb.http.SHtml
import net.liftweb.http.SHtml.ajaxButton
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmds.{OnLoad, Script}
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.util.Helpers._
import net.liftweb.util._

object ReportProblem extends ReportProblem

class ReportProblem extends Logger {

  var problemReport = ""

  def render: CssBindFunc = {
    "#name" #> SHtml.text("", report => {
      problemReport = report
    }) &
    "#reportLoader" #> Script(OnLoad(Call("updateContext", getProblemContext).cmd)) &
      "#submitReportButton" #> ajaxButton("Send", () => {
        Call("displaySent", sendReport(getProblemContext)).cmd
      })
  }

  protected def getUsername: String = {
    Globals.currentUser.is
  }

  protected def getProblemContext: JObject = {
    JObject(List(JField("username", JString(getUsername)),
      JField("context", JString("Doin' Stuff"))))
  }

  protected def sendReport(problemContext: JObject): String = {
    error("Problem reported. User: " + problemContext.values.getOrElse("username", "") + ", Context: " + problemContext.values.getOrElse("context", "") + ", Report: " + problemReport)
    "Thanks for reporting a problem. The support team has been notified and will investigate."
  }
}
