package com.metl.snippet

import com.metl.comet.JArgUtils
import com.metl.data.ServerConfiguration
import com.metl.model.Globals
import net.liftweb.common.{Full, Logger}
import net.liftweb.http.S
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._

class ReportProblem extends Logger with JArgUtils {

  def render: CssSel = {
    val searchParam = S.request.flatMap(_.param("search"))
    val queryParam = S.request.flatMap(_.param("query"))
    val context =
      searchParam match {
        case Full("true") => "query '%s'".format(queryParam.getOrElse("no query"))
        case other =>
          val conversationParam = S.request.flatMap(_.param("conversation"))
          val jid = conversationParam.getOrElse("noConversation")
          val title = conversationParam.map(j => ServerConfiguration.default.detailsOfConversation(j).title).getOrElse("noTitle")
          val slideParam = S.request.flatMap(_.param("slide"))
          val slide = slideParam.getOrElse("noSlide")
          f"conversation '$title%s' ($jid%s.$slide%s)"
      }
    val reporter = Globals.currentUser.is
    "#hiddenFields" #> List(<input type="hidden" name="reporter" value={reporter}/><input type="hidden" name="context" value={context}/>)
  }
}
