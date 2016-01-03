package com.metl.snippet

import com.metl.data._
import com.metl.utils._

import com.metl.model._
import com.metl.snippet.Utils._
import scala.xml._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.http._
import Helpers._
import S._

object ConversationTemplates {
  def conversationSlide = Templates(List("_conversationSlide")).openOr(NodeSeq.Empty)
}

class ConversationSnippet {
  object server extends RequestVar[ServerConfiguration](ServerConfiguration.default)
  object conversationId extends RequestVar[String](S.param("conversation").openOr(""))

  object conversation extends RequestVar[Conversation](server.detailsOfConversation(conversationId.is))
  def title(in:NodeSeq):NodeSeq = conversation.is match {
    case c:Conversation if c != Conversation.empty => Text(c.title)
    case _ => NodeSeq.Empty
  }
  def navigation(in:NodeSeq):NodeSeq = NodeSeq.Empty

  def conversation(in:NodeSeq):NodeSeq = handleParamErrors.openOr(renderConversation(in)).apply(in)

  private def renderConversation(in:NodeSeq) =
    "#conversationSlideList *" #>
  conversation.is.slides.sortBy(s => s.index).foldLeft(NodeSeq.Empty)((acc,item) => acc ++ renderConversationSlide(item).apply(ConversationTemplates.conversationSlide))

  private def renderConversationSlide(slide:Slide) =
    ".conversationSlideImageAnchor [href]" #> "/slide?conversation=%s&slide=%s".format(conversationId.is,slide.id) &
  ".conversationSlideImage [src]" #> "/slide/%s/small".format(slide.id)

  private def renderConversationError(message:String) = "#conversationError *" #> Text(message)
  private def handleParamErrors:Box[CssSel] ={
    (conversationId.is) match {
      case (cid) if (cid.length>0) => {
        conversation.is match {
          case d:Conversation if (d == Conversation.empty) => Full(renderConversationError("couldn't get conversation"))
          case _ => Empty
        }
      }
      case _ => Full(renderConversationError("need conversation param"))
    }
  }
}
