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

class QuizzesSnippet {
  object server extends RequestVar[ServerConfiguration](ServerConfiguration.default)
  object conversationId extends RequestVar[String](S.param("conversation").openOr(""))
  object slideId extends RequestVar[String](S.param("slide").openOr(""))
  object conversation extends RequestVar[Conversation](server.detailsOfConversation(conversationId.is))

  def title(in:NodeSeq):NodeSeq = conversation.is match {
    case c:Conversation if c != Conversation.empty => Text(c.title)
    case _ => NodeSeq.Empty
  }
  def navigation:NodeSeq = (conversationId) match {
    case (cid) if (cid.length>0) => {
      Utils.navLinks(List(
        Link("slidesLink","/slide?conversation=%s&slide=%s".format(cid,slideId.is),"View slides"),
        Link("quizzesLink","/quizzes?conversation=%s&slide=%s".format(cid,slideId.is),"Quizzes")
      ))
    }
    case _ => NodeSeq.Empty
  }

  def list = handleParamErrors.openOr(renderList)

  private def renderList = "#quizList *" #> {
    MeTLXConfiguration.getRoom(conversationId.is,server.is.name).getHistory.getQuizzes.sortBy(q => q.id).map(quiz => {
      <div class="quizItem">
      <a href={"/quiz?conversation=%s&slide=%s&quiz=%s".format(conversationId.is,slideId.is,quiz.id)}>
      <span>{quiz.question}</span>
      </a>
      </div>
    })
  }

  private def renderListError(message:String) = "#quizListError *" #> Text(message)
  private def handleParamErrors:Box[CssSel] ={
    (conversationId.is) match {
      case (cid) if (cid.length>0) => {
        MeTLXConfiguration.getRoom(conversationId.is,server.is.name).getHistory match {
          case h:History if (h == History.empty) => Full(renderListError("couldn't get conversation history"))
          case h:History => Empty
        }
      }
      case _ => Full(renderListError("need conversation param"))
    }
  }
}
