package com.metl.snippet

import com.metl.data._
import com.metl.utils._
import com.metl.liftAuthenticator._


import net.liftweb._
import http._
import SHtml._
import common._
import util._
import Helpers._
import scala.xml._
import com.metl.comet._
import com.metl.model._
import Globals._

import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.JsonAST._

object Metl extends Metl
class Metl extends Logger {
  val config = ServerConfiguration.default
  def shouldModifyConversation(username:String, c:Conversation):Boolean = {
    (Globals.isSuperUser || username.toLowerCase.trim == c.author.toLowerCase.trim) && c != Conversation.empty
  }
  def shouldDisplayConversation(c:Conversation,showDeleted:Boolean = false,me:String = Globals.currentUser.is,groups:List[OrgUnit] = Globals.getUserGroups):Boolean = {
    val subject = c.subject.trim.toLowerCase
    var fr = c.foreignRelationship
    Globals.isSuperUser || (showDeleted && c.author == me) || (subject != "deleted" && (subject == "unrestricted" || groups.exists((ug:OrgUnit) => ug.name.toLowerCase.trim == subject || fr.exists(fri => ug.foreignRelationship.exists(ufr => ufr.key == fri.key && ufr.system == fri.system)))) && c != Conversation.empty)
  }
  def shouldPublishInConversation(username:String,c:Conversation):Boolean = {
    (Globals.isSuperUser || (shouldModifyConversation(username,c) || (c.permissions.studentsCanPublish && !c.blackList.contains(username)))) && c != Conversation.empty
  }
  def boardFor():String = {
    "/board"
  }
  def boardFor(conversationJid:String):String = {
    "/board?conversationJid=%s".format(conversationJid)
  }
  def boardFor(conversationJid:String,slideId:String):String = {
    "/board?conversationJid=%s&slideId=%s".format(conversationJid,slideId)
  }
  def projectorFor(conversationJid:String):String = {
    "/board?conversationJid=%s&showTools=false".format(conversationJid)
  }
  def projectorFor(conversationJid:String,slideId:String):String = {
    "/board?conversationJid=%s&slideId=%s&showTools=false".format(conversationJid,slideId)
  }
  def thumbnailFor(conversationJid:String,slideId:String):String = {
    "/thumbnail/%s".format(slideId.toString)
  }
  def thumbnailWithPrivateFor(conversationJid:String,slideId:String):String = {
    "/thumbnailWithPrivate/%s".format(slideId.toString)
  }
  def printSlideFor(conversationJid:String,slideId:String):String = {
    "/printableImage/%s".format(slideId.toString)
  }
  def printSlideWithPrivateFor(conversationJid:String,slideId:String):String = {
    "/printableImageWithPrivateFor/%s".format(slideId.toString)
  }
  def remotePluginConversationChooser(ltiToken:String):String = {
    "/remotePluginConversationChooser?ltiToken=%s".format(ltiToken)
  }
  def remotePluginChoseConversation(ltiToken:String,conversationJid:Int):String = {
    "/brightSpark/remotePluginConversationChosen?ltiToken=%s&conversationJid=%s".format(ltiToken,conversationJid.toString)
  }
  def noBoard:String = {
    conversationSearch()
  }
  def editConversation(conversationJid:String):String = {
    "/editConversation?conversationJid=%s&unique=true".format(conversationJid.toString)
  }
  def conversationSearch():String = {
    "/conversationSearch?unique=true"
  }


  lazy val serverConfig = ServerConfiguration.default
  protected def constructNameString(nameString:String,newKey:String,newValue:String):String = {
    "%s|%s:%s".format(nameString,newKey,newValue)
  }
  protected def generateName(showDeleted:Boolean = false):String = {
    var name = "USERNAME:%s".format(Globals.currentUser.is)
    S.param("conversationJid").foreach(cj => {
      try {
        name = constructNameString(name,"CONVERSATION",cj)
        val conversation = serverConfig.detailsOfConversation(cj)
        if (!shouldDisplayConversation(conversation,showDeleted)){
          warn("snippet.Metl is kicking the user from this conversation")
          S.redirectTo(noBoard)
        }
        S.param("slideId").foreach(sid => {
          try {
            name = constructNameString(name,"SLIDE",sid)
          } catch {
            case e:Exception => {
              error("invalid argument passed in slideId: %s".format(sid),e)
            }
          }
        })
      } catch {
        case redir:ResponseShortcutException => throw redir
        case e:Exception => {
          error("invalid argument passed in conversationJid: %s".format(cj),e)
        }
      }
    })
    S.param("links").map(links => {
      name = constructNameString(name,"LINKS",links.toLowerCase.trim == "false" match {
        case true => "false"
        case false => "true"
      })
    }).getOrElse({
      name = constructNameString(name,"LINKS","true")
    })
    S.param("unique").foreach(uniq => {
      try {
        if (uniq.toLowerCase.trim == "true"){
          name = constructNameString(name,"UNIQUE",nextFuncName)
        }
      } catch {
        case e:Exception => {
          error("invalid argument passed in unique: %s".format(uniq),e)
        }
      }
    })
    S.param("showTools").foreach(tools => {
      try {
        if (tools.toLowerCase.trim == "true"){
          name = constructNameString(name,"SHOWTOOLS","true")
        } else {
          name = constructNameString(name,"SHOWTOOLS","false")
        }
      } catch {
        case e:Exception => {
          error("invalid argument passed in showTools: %s".format(tools),e)
        }
      }
    })
    S.param("ltiToken").foreach(ltiToken => {
      name = constructNameString(name,"LTITOKEN",ltiToken)
    })
    S.param("query").foreach(query => {
      name = constructNameString(name,"QUERY",query)
    })
    name
  }
  def getLtiTokenFromName(in:String):Option[String] = extractValueByName(in,"LTITOKEN")
  def getUserFromName(in:String):Option[String] = extractValueByName(in,"USERNAME")
  def getShowToolsFromName(in:String):Option[Boolean] = extractValueByName(in,"SHOWTOOLS",_.toBoolean)
  def getLinksFromName(in:String):Option[Boolean] = extractValueByName(in,"LINKS",_.toBoolean)
  def getQueryFromName(in:String):Option[String] = extractValueByName(in,"QUERY")
  def getConversationFromName(in:String):Option[String] = extractValueByName(in,"CONVERSATION")
  def getSlideFromName(in:String):Option[String] = extractValueByName(in,"SLIDE")
  protected def extractValueByName[A](in:String,name:String,converter:String => A = (s:String) => s):Option[A] = {
    try {
        val sections = in.split('|').toList
        val brokenSections = sections.map(_.split(":").toList)
        val nonEmptySections = brokenSections.filterNot(_ == Nil)
        val matchingSection = nonEmptySections.find(_.headOption.contains(name))
        val result = matchingSection.map(s => converter(s.drop(1).mkString(":")))
        result
    } catch {
      case e:Exception => {
        error("invalid argument passed into extractValueByName [%s] => [%s] :: %s".format(in,name),e)
        None
      }
    }
  }
  def specificSimple(in:NodeSeq):NodeSeq = {
    val name = generateName()
    val clazz = "lift:comet?type=SinglePageMeTLActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    //warn("generating single page comet html: %s".format(output))
    output
  }
  def specificSlideDisplay(in:NodeSeq):NodeSeq = {
    val name = generateName()
    val clazz = "lift:comet?type=MeTLSlideDisplayActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    //warn("generating single page comet html: %s".format(output))
    output
  }
  def specificEditConversation(in:NodeSeq):NodeSeq = {
    val name = generateName(true)
    val clazz = "lift:comet?type=MeTLEditConversationActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    //warn("generating editConversation comet html: %s".format(output))
    output
  }
  def specific(in:NodeSeq):NodeSeq = {
    S.param("conversationJid").openOr(S.redirectTo(noBoard))
    val name = generateName()
    val clazz = "lift:comet?type=MeTLActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    //warn("generating comet html: %s".format(output))
    output
  }
  def specificConversationSearch(in:NodeSeq):NodeSeq = {
    val name = generateName()
    val clazz = "lift:comet?type=MeTLJsonConversationChooserActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    output
  }
  def profile(in:NodeSeq):NodeSeq = {
    val name = "%s_%s".format(Globals.currentUser.is,nextFuncName)
    val clazz = "lift:comet?type=MeTLProfile&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    output
  }
  def account(in:NodeSeq):NodeSeq = {
    Globals.currentUser.is // have to hit this first, always - will have to look at how to fix that.
    val name = "%s_%s_%s".format(Globals.currentAccount.provider,Globals.currentAccount.name,nextFuncName)
    val clazz = "lift:comet?type=MeTLAccount&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    output
  }
  /*
  def remotePluginConversationChooser(in:NodeSeq):NodeSeq = {
    val name = generateName()
    val clazz = "lift:comet?type=RemotePluginConversationChooserActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    //warn("generating comet html: %s".format(output))
    output
  }
  */
  def getPagesFromPageRange(pageRange:String,conversation:Conversation):List[Slide] = {
    pageRange.toLowerCase.trim match {
      case "all" => conversation.slides.sortWith((a,b) => a.index < b.index)
      case specificPageRange => {
        val pageIndexes = specificPageRange.split(",").flatMap(seq => {
          seq.split("-").toList match {
            case Nil => Nil
            case List(i) => {
              try {
                List(i.toInt)
              } catch {
                case e:Exception => Nil
              }
            }
            case l:List[String] => {
              try {
                val sorted = l.map(_.toInt).sortWith((a,b) => a < b)
                val start = sorted.head
                val end = sorted.reverse.head
                Range.inclusive(start,end).toList
              } catch {
                case e:Exception => Nil
              }
            }
          }
        }).distinct
        conversation.slides.filter(s => pageIndexes.exists(_ == s.index + 1)).toList.sortWith((a,b) => a.index < b.index)
      }
    }
  }
  protected def orEmpty(cond:Boolean,onSuccess: => NodeSeq):NodeSeq = {
    if (cond){
      onSuccess
    } else {
      NodeSeq.Empty
    }
  }
  def printConversation = {
    (for (
      jid <- S.param("conversationJid");
      pageRange <- S.param("pageRange");
      conv = config.detailsOfConversation(jid);
      pagesToPrint = getPagesFromPageRange(pageRange,conv)
    ) yield {
      val pageCount = conv.slides.length
      val includePrivate = S.param("includePrivateContent").map(_.toBoolean).getOrElse(false)
      val includeConvTitle = S.param("includeConversationTitle").map(_.toBoolean).getOrElse(false)
      val includePageCount = S.param("includePageCount").map(_.toBoolean).getOrElse(false)
      ".pagesContainer *" #> {
        ".pageContainer *" #> pagesToPrint.map(page => {
          ".pageHeader *" #> orEmpty(includeConvTitle,Text(conv.title)) &
          ".pageFooter *" #> orEmpty(includePageCount,Text("%s/%s".format(page.index + 1, pageCount))) &
          ".pageImageContainer *" #> {
            includePrivate match {
              case true => ".pageImage [src]" #> printSlideWithPrivateFor(conv.jid,page.id)
              case false => ".pageImage [src]" #> printSlideFor(conv.jid,page.id)
            }
          }
        })
      }
    }).getOrElse({
      ".pagesContainer *" #> Text("conversation and/or pageRange not specified")
    })
  }
  val serializer = new JsonSerializer(ServerConfiguration.default)
  def clientSidePrintConversation = {
    (for (
      jid <- S.param("conversationJid");
      pageRange <- S.param("pageRange");
      conv = config.detailsOfConversation(jid);
      pagesToPrint = getPagesFromPageRange(pageRange,conv)
    ) yield {
      val pageCount = conv.slides.length
      val includePrivate = S.param("includePrivateContent").map(_.toBoolean).getOrElse(false)
      val includeConvTitle = S.param("includeConversationTitle").map(_.toBoolean).getOrElse(false)
      val includePageCount = S.param("includePageCount").map(_.toBoolean).getOrElse(false)
      ".afterAllPagesScript *" #> Script(OnLoad(Call("registerPageCount",JInt(pagesToPrint.length)))) &
      ".pagesContainer *" #> {
        ".pageContainer *" #> pagesToPrint.map(page => {
          val uniqueId = page.id.toString
          val pageHistoryId = "history_%s".format(uniqueId)
          val pageCanvasId = "canvas_%s".format(uniqueId)
          ".pageHeader *" #> orEmpty(includeConvTitle,Text(conv.title)) &
          ".pageHeader [id]" #> "pageHeader_%s".format(uniqueId) &
          ".pageFooter *" #> orEmpty(includePageCount,Text("%s/%s".format(page.index + 1, pageCount))) &
          ".pageFooter [id]" #> "pageFooter_%s".format(uniqueId) &
          ".pageImageContainer [id]" #> "pageImageContainer_%s".format(uniqueId) &
          ".pageImageContainer *" #> {
            ".varContainer *" #> {
              val history = includePrivate match {
                case true => MeTLXConfiguration.getRoom(page.id.toString,config.name).getHistory.merge(MeTLXConfiguration.getRoom(page.id.toString+Globals.currentUser.is,config.name).getHistory)
                case false => MeTLXConfiguration.getRoom(page.id.toString,config.name).getHistory
              }
              Script(JsCrVar(pageHistoryId,serializer.fromHistory(history)))
            } &
            ".onLoadContainer *" #> Script(OnLoad(Call("renderCanvas",JsVar(pageHistoryId),JString(uniqueId)))) &
            ".pageImage [id]" #> "pageImage_%s".format(uniqueId)
          }
        })
      }
    }).getOrElse({
      ".pagesContainer *" #> Text("conversation and/or pageRange not specified")
    })
  }
}
