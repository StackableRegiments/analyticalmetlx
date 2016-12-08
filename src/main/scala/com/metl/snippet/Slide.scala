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

class SlideSnippet {
  object server extends RequestVar[ServerConfiguration](ServerConfiguration.default)
  object conversationId extends RequestVar[String](S.param("conversation").openOr(""))
  object slideId extends RequestVar[String](S.param("slide").openOr(""))

  object conversation extends RequestVar[Conversation](server.detailsOfConversation(conversationId.is))
  def title(in:NodeSeq):NodeSeq = {
    S.skipXmlHeader = true
    S.skipDocType = true
    conversationId.is match {
      case s:String if (s.length > 0) => {
        conversation.is match {
          case c:Conversation if c != Conversation.empty => {
            val prefix = c.slides.find(s => s.id.toString == slideId.is) match {
              case Some(slide) => "Slide %d: ".format(slide.index+1)
              case _ => ""
            }
            Text("%s%s".format(prefix,c.title))
          }
          case _ => NodeSeq.Empty
        }
      }
      case _ => NodeSeq.Empty
    }
  }

  def navigation:NodeSeq = {
    S.skipXmlHeader = true
    S.skipDocType = true
      (conversationId.is,slideId.is) match {
      case (cid,sid) if (cid.length>0 && sid.length>0) => {
        MeTLXConfiguration.getRoom(conversationId.is,server.is.name).getHistory match {
          case h:History if (h != History.empty) => {
            h.getQuizzes.length match {
              case 0 => NodeSeq.Empty
              case 1 => Utils.navLinks(List(Link("quizLink","/quiz?conversation=%s&slide=%s&quiz=%s".format(cid,sid,h.getQuizzes.head.id),"Quiz 1")))
              case _ => Utils.navLinks(List(Link("quizzesLink","/quizzes?conversation=%s&slide=%s".format(cid,sid),"Quizzes")))
            }
          }
          case _ => NodeSeq.Empty
        }
      }
      case _ => NodeSeq.Empty
    }
  }

  def previous(in:NodeSeq):NodeSeq = {
    S.skipXmlHeader = true
    S.skipDocType = true
    getNearbySlideId(-1).map(previousId => renderPrevious(previousId).apply(in)).openOr(in)
  }
  def next(in:NodeSeq):NodeSeq = {
    S.skipXmlHeader = true
    S.skipDocType = true
    getNearbySlideId(1).map(nextId => renderNext(nextId).apply(in)).openOr(in)
  }

  private def renderPrevious(previousId:String) =
    "#slideNavigationPrevious *" #> <a href={"/slide?conversation=%s&slide=%s".format(conversationId.is,previousId)}><span>Previous page</span></a>

  private def renderNext(nextId:String) =
    "#slideNavigationNext *" #> <a href={"/slide?conversation=%s&slide=%s".format(conversationId.is,nextId)}><span>Next page</span></a>

  private def getNearbySlideId(offset:Int):Box[String] = (conversationId.is,slideId.is) match {
    case (cid,sid) if (cid.length>0 && sid.length>0) => {
      MeTLXConfiguration.getRoom(conversationId.is,server.is.name).getHistory match {
        case h:History if (h != History.empty) => {
          val slides = conversation.is.slides
          slides.length match {
            case 0 => Empty
            case 1 => Full(sid)
            case _ => slides.find(s => s.id.toString == slideId.is) match {
              case Some(currentSlide) => {
                val wantedIndex = (currentSlide.index+offset) match {
                  case i:Int if i >= slides.length => i % slides.length
                  case i:Int if i < 0 => slides.length + (i % slides.length)
                  case i:Int => i
                }
                slides.find(s => s.index == wantedIndex) match {
                  case Some(nearbySlide) => Full(nearbySlide.id.toString)
                  case _ => Empty
                }
              }
              case _ => Empty
            }
          }
        }
        case _ => Empty
      }
    }
    case _ => Empty
  }

  def slide = handleParamErrors.openOr(renderSlide)

  private def renderSlide = "#slideImage *" #> <img src={"/slide/%s/medium".format(slideId.is)}/>

  private def renderSlideError(message:String) = "#slideError *" #> Text(message)
  private def handleParamErrors:Box[CssSel] ={
    (conversationId.is,slideId.is) match {
      case (cid,sid) if (cid.length>0 && sid.length>0) => Empty
      case (cid,sid) if (cid.length>0) => {
        conversation.is match {
          case d:Conversation if (d == Conversation.empty) => Full(renderSlideError("couldn't get conversation"))
          case d:Conversation => {
            d.slides.sortBy(s => s.index).headOption match {
              case Some(slide) => Full(S.redirectTo("/slide?conversation=%s&slide=%s".format(cid,slide.asInstanceOf[Slide].id.toString)))
              case _ => Full(renderSlideError("conversation has no slides"))
            }
          }
        }
      }
      case _ => Full(renderSlideError("need conversation or slide param"))
    }
  }
}
