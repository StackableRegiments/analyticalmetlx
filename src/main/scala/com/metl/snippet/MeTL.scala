package com.metl.snippet

import com.metl.data._
import net.liftweb._
import http._
import common._
import util._
import Helpers._

import scala.xml._
import com.metl.model._
import com.metl.ReadOnlyMetlInterface
import com.metl.external.OrgUnit
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.JsonAST._

object Metl extends Metl
class Metl extends Logger with ReadOnlyMetlInterface {
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
  def specificTestComet(cometName:String = nextFuncName):NodeSeq => NodeSeq = {
    (n:NodeSeq) => <span class={"lift:comet?type=TestActor;name=%s".format(cometName)}/>
  }
  def specificTestComet(n:NodeSeq):NodeSeq = {
    specificTestComet()(n)
  }
  def boardFor():String = {
    "/board"
  }
  override def boardFor(conversationJid:Int):String = {
    "/board?conversationJid=%s".format(conversationJid)
  }
  override def boardFor(conversationJid:Int,slideId:Int):String = {
    "/board?conversationJid=%s&slideId=%s".format(conversationJid,slideId)
  }
  def projectorFor(conversationJid:Int):String = {
    "/board?conversationJid=%s&showTools=false".format(conversationJid)
  }
  def projectorFor(conversationJid:Int,slideId:Int):String = {
    "/board?conversationJid=%s&slideId=%s&showTools=false".format(conversationJid,slideId)
  }
  def thumbnailFor(conversationJid:Int,slideId:Int):String = {
    "/thumbnail/%s".format(slideId.toString)
  }
  def thumbnailWithPrivateFor(conversationJid:Int,slideId:Int):String = {
    "/thumbnailWithPrivate/%s".format(slideId.toString)
  }
  def printSlideFor(conversationJid:Int,slideId:Int):String = {
    "/printableImage/%s".format(slideId.toString)
  }
  def printSlideWithPrivateFor(conversationJid:Int,slideId:Int):String = {
    "/printableImageWithPrivateFor/%s".format(slideId.toString)
  }
  override def remotePluginConversationChooser(ltiId:String,ltiToken:String):String = {
    "/remotePluginConversationChooser?ltiId=%s&ltiToken=%s".format(ltiId,ltiToken)
  }
  def remotePluginChoseConversation(prefix:String,ltiToken:String,conversationJid:Int):String = {
    "/%s/remotePluginConversationChosen?ltiToken=%s&conversationJid=%s".format(prefix,ltiToken,conversationJid.toString)
  }
  override def noBoard:String = {
    conversationSearch()
  }
  def editConversation(conversationJid:Int):String = {
    "/editConversation?conversationJid=%s&unique=true".format(conversationJid.toString)
  }
  def conversationSearch():String = {
    "/conversationSearch?unique=true"
  }

  lazy val serverConfig = ServerConfiguration.default
  protected def generateName(showDeleted:Boolean = false):String = {
    var name = "USERNAME:%s".format(Globals.currentUser.is)
    S.param("conversationJid").foreach(cj => {
      try {
        name += "_CONVERSATION:%s".format(cj.toInt)
        val conversation = serverConfig.detailsOfConversation(cj)
        if (!shouldDisplayConversation(conversation,showDeleted)){
          warn("snippet.Metl is kicking the user from this conversation")
          S.redirectTo(noBoard)
        }
        S.param("slideId").foreach(sid => {
          try {
            name += "_SLIDE:%s".format(sid.toInt)
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
      name += "_LINKS:%s".format(links.toLowerCase.trim == "false" match {
        case true => "false"
        case false => "true"
      })
    }).getOrElse({
      name += "_LINKS:true"
    })
    S.param("unique").foreach(uniq => {
      try {
        if (uniq.toLowerCase.trim == "true"){
          name += "_UNIQUE:%s".format(nextFuncName)
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
          name += "_SHOWTOOLS:%s".format(true)
        } else {
          name += "_SHOWTOOLS:%s".format(false)
        }
      } catch {
        case e:Exception => {
          error("invalid argument passed in showTools: %s".format(tools),e)
        }
      }
    })
    S.param("ltiId").foreach(ltiId => {
      name += "_LTIID:%s".format(ltiId)
    })
    S.param("ltiToken").foreach(ltiToken => {
      name += "_LTITOKEN:%s".format(ltiToken)
    })
    S.param("query").foreach(query => {
      name += "_QUERY:%s".format(query)
    })
    name
  }
  def getLtiIdFromName(in:String):Option[String] = {
    in.split("_").map(_.split(":")).find(_(0) == "LTIID").map(_.drop(1).mkString(":"))
  }
  def getLtiTokenFromName(in:String):Option[String] = {
    in.split("_").map(_.split(":")).find(_(0) == "LTITOKEN").map(_.drop(1).mkString(":"))
  }
  def getShowToolsFromName(in:String):Option[Boolean] = {
    in.split("_").map(_.split(":")).find(_(0) == "SHOWTOOLS").map(_.drop(1).mkString(":")).flatMap(showToolsString => {
      try {
        Some(showToolsString.toBoolean)
      } catch {
        case e:Exception => {
          error("invalid argument passed in showToolsString: %s".format(showToolsString),e)
          None
        }
      }
    })
  }
  def getLinksFromName(in:String):Option[Boolean] = {
    in.split("_").map(_.split(":")).find(_(0) == "LINKS").map(_.drop(1).mkString(":")).flatMap(linksString => {
      try {
        Some(linksString.toBoolean)
      } catch {
        case e:Exception => {
          error("invalid argument passed in linksString: %s".format(linksString),e)
          None
        }
      }
    })

  }
  def getQueryFromName(in:String):Option[String] = {
    in.split("_").map(_.split(":")).find(_(0) == "QUERY").map(_.drop(1).mkString(":"))
  }
  def getConversationFromName(in:String):Option[Int] = {
    in.split("_").map(_.split(":")).find(_(0) == "CONVERSATION").map(_.drop(1).mkString(":")).flatMap(convString => {
      try {
        Some(convString.toInt)
      } catch {
        case e:Exception => {
          error("invalid argument passed in convString: %s".format(convString),e)
          None
        }
      }
    })
  }
  def getSlideFromName(in:String):Option[Int] = {
    in.split("_").map(_.split(":")).find(_(0) == "SLIDE").map(_.drop(1).mkString(":")).flatMap(slideString => {
      try {
        Some(slideString.toInt)
      } catch {
        case e:Exception => {
          error("invalid argument passed in slideString: %s".format(slideString),e)
          None
        }
      }
    })
  }
  def getUserFromName(in:String):Option[String] = {
    in.split("_").map(_.split(":")).find(_(0) == "USERNAME").map(_.drop(1).mkString(":"))
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
  def remotePluginConversationChooser(in:NodeSeq):NodeSeq = {
    val name = generateName()
    val clazz = "lift:comet?type=RemotePluginConversationChooserActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    //warn("generating comet html: %s".format(output))
    output
  }
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
