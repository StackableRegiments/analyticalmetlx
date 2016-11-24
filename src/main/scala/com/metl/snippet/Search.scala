package com.metl.snippet

import com.metl.data._
import com.metl.utils._

import com.metl.model._
import scala.xml._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.http._
import SHtml._
import Helpers._
import S._

object SearchTemplates {
  def searchResult = Templates(List("_searchResult")).openOr(NodeSeq.Empty)
  def searchResultQuizLink = Templates(List("_searchResultQuizLink")).openOr(NodeSeq.Empty)
}

class SearchSnippet extends Logger {
  object server extends RequestVar[ServerConfiguration](ServerConfiguration.default)
  object query extends RequestVar[String](S.param("q").openOr(""))
  object page extends RequestVar[Int](S.param("page").map(_.toInt).openOr(0))

  private val resultsPerPage = 10

  def searchResults = {
    query.is match {
      case q:String if (q.length > 0) => {
        val rawResults = server.searchForConversation(q)
        val results = rawResults.filter(c => c.shouldDisplayFor(Globals.currentUser.is,Globals.getUserGroups.map(eg => eg.name)))
        trace("rawResults: %s\r\nfilteredResults: %s".format(rawResults,results))
        "#searchResultsMetaTerms *" #> Text(q) & {
          results.length match {
            case 0 =>
              "#searchResultsMetaCount *" #> Text("No results.")
            case _ => {
              val first = page.is*resultsPerPage
              val pageResults = results.splitAt(first)._2.take(resultsPerPage)
              val countText = "Showing %d-%d of %d result%s".format(first+1, first+pageResults.length, results.length, if (results.length > 1) "s" else "")
              "#searchResultsMetaCount *" #> Text(countText) &
              "#searchResultsMetaNavigationFirst [href]" #> "/?q=%s".format(Helpers.urlEncode(query.is)) &
              "#searchResultsMetaNavigationPrevious [href]" #> "/?q=%s&page=%d".format(Helpers.urlEncode(query.is),Math.max(page.is-1,0)) &
              "#searchResultsMetaNavigationPrevious [href]" #> "/?q=%s&page=%d".format(Helpers.urlEncode(query.is),Math.min(page.is+1,results.length/resultsPerPage)) &
              "#searchResultsMetaNavigationPrevious [href]" #> "/?q=%s&page=%d".format(Helpers.urlEncode(query.is),results.length/resultsPerPage) &
              "#searchResults *" #> pageResults.foldLeft(NodeSeq.Empty)((acc,item) => acc ++ renderResult(server,item).apply(SearchTemplates.searchResult))
            }
          } }
      }
      case _ => ClearClearable
    }
  }

  private def renderResult(server:ServerConfiguration,result:Conversation):CssSel = {
    ".searchResultAnchor [href]" #> "/slide?conversation=%s".format(result.jid) &
    ".searchResultTitle *" #> result.title &
    ".searchResultAuthor *" #> result.author &
    //              ".searchResultQuizLinks *" #> renderQuizLinks(server,result.jid.toString) &
    ".searchResultSlideLinks *" #> renderSlideLinks(server,result.jid.toString,result.slides)
  }

  private def renderQuizLinks(server:ServerConfiguration,conversationId:String):NodeSeq =
    MeTLXConfiguration.getRoom(conversationId,server.name).getHistory.getQuizzes match {
      case quizzes:List[MeTLQuiz] if (quizzes.length <= 2) => quizzes.foldLeft(NodeSeq.Empty)((acc,quiz) => acc ++ renderQuizLink(quiz,conversationId).apply(SearchTemplates.searchResultQuizLink))
      case quizzes:List[MeTLQuiz] if (quizzes.length > 2) => renderAllQuizLinks(conversationId).apply(SearchTemplates.searchResultQuizLink)
      case _ => NodeSeq.Empty
    }

  private def renderQuizLink(quiz:MeTLQuiz,conversationId:String):CssSel =
    ".searchResultQuizLinkAnchor [href]" #> "/quiz?conversation=%s&quiz=%s".format(conversationId,quiz.id) &
  ".searchResultQuizLinkText *" #> quiz.question

  private def renderAllQuizLinks(conversationId:String):CssSel =
    ".searchResultQuizLinkAnchor [href]" #> "/quizzes?conversation=%s".format(conversationId) &
  ".searchResultQuizLinkText *" #> "Quizzes"

  private val numSlideLinks = 20
  private val numSlideEndLinks = 3

  private def renderSlideLinks(server:ServerConfiguration,conversationId:String,slides:List[Slide]) =
    slides.length match {
      case len:Int if (len > 0) => {
        val sorted = slides.sortBy(s => s.index)
        Text("Slides: ") ++ { len match {
          case len:Int if (len < numSlideLinks) => sorted.map(s => renderSlideLink(server,conversationId,s))
          case len:Int => {
            val (start, rest) = sorted.splitAt(numSlideEndLinks)
            val (mid, end) = rest.splitAt(rest.length-numSlideEndLinks)

            val interval = mid.length / (numSlideLinks-numSlideEndLinks*2)

            val split = mid.foldLeft((List.empty[Slide],interval/2))((acc,item) => acc._2 match {
              case 0 => (acc._1 ::: List(item), interval)
              case _ => (acc._1, acc._2-1)
            })._1

            start.map(s => renderSlideLink(server,conversationId,s)) ++
            split.map(s => renderSlideLink(server,conversationId,s)).foldLeft(Text("..").asInstanceOf[NodeSeq])((acc,item) => acc ++ item ++ Text("..")) ++
            end.map(s => renderSlideLink(server,conversationId,s))
          } }
        }
      }
      case _ => NodeSeq.Empty
    }
  private def renderSlideLink(server:ServerConfiguration,conversationId:String,slide:Slide) =
    <a class="slide-link" href={"/slide?conversation=%s&slide=%s".format(conversationId,slide.id.toString)}>{slide.index+1}</a>
}
