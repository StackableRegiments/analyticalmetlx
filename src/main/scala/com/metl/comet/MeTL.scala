package com.metl.comet

import com.metl.data._
import com.metl.utils._
import com.metl.liftExtensions._

import net.liftweb._
import common._
import http._
import util._
import Helpers._
import HttpHelpers._
import actor._
import scala.xml._
import com.metl.model._
import SHtml._

import js._
import JsCmds._
import JE._
import net.liftweb.http.js.jquery.JqJsCmds._

import net.liftweb.http.js.jquery.JqJE._

import java.util.Date
import com.metl.renderer.SlideRenderer

import json.JsonAST._

import com.metl.snippet.Metl._
/*
case class StylableRadioButtonInteractableMessage(messageTitle:String,body:String,radioOptions:Map[String,()=>Boolean],defaultOption:Box[String] = Empty, customError:Box[()=>Unit] = Empty,override val role:Box[String] = Empty) extends InteractableMessage((i)=>{
  var answerProvided = false
  <div>
  <div>{body}</div>
  <div>
  {
    radio(radioOptions.toList.map(optTuple => optTuple._1),defaultOption,(chosen:String) => {
      if (!answerProvided && radioOptions(chosen)()){
        answerProvided = true
        i.done
      } else {
        customError.map(ce => ce())
      }
    },("class","simpleRadioButtonInteractableMessageButton")).items.foldLeft(NodeSeq.Empty)((acc,choiceItem) => {
      val inputElem = choiceItem.xhtml
      val id = nextFuncName
      acc ++ ("input [id]" #> id).apply((choiceItem.xhtml \\ "input")) ++ <label for={id}>{Text(choiceItem.key.toString)}</label>
    })
  }
  <div>
  {submit("Submit", ()=> Noop) }
  </div>
  </div>
  </div>
},role,Full(messageTitle))
*/
/*
object TemplateHolder{
  val useClientMessageTemplate = getClientMessageTemplate
  def getClientMessageTemplate = Templates(List("_s2cMessage")).openOr(NodeSeq.Empty)
  def clientMessageTemplate = if (Globals.isDevMode) getClientMessageTemplate else useClientMessageTemplate
}
*/

case class RoomJoinRequest(jid:String,username:String,server:String,uniqueId:String,metlActor:LiftActor)
case class RoomLeaveRequest(jid:String,username:String,server:String,uniqueId:String,metlActor:LiftActor)
case class JoinThisSlide(slide:String)

object RoomJoiner extends LiftActor with Logger {
  override def messageHandler = {
    case RoomJoinRequest(j,u,s,i,a) => MeTLXConfiguration.getRoom(j,s) ! JoinRoom(u,i,a)
    case RoomLeaveRequest(j,u,s,i,a) => MeTLXConfiguration.getRoom(j,s) ! LeaveRoom(u,i,a)
    case other => warn("RoomJoiner received strange request: %s".format(other))
  }
}

object MeTLActorManager extends LiftActor with ListenerManager with Logger {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case _ => warn("MeTLActorManager received unknown message")
  }
}

object MeTLConversationSearchActorManager extends LiftActor with ListenerManager with Logger {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case m:MeTLCommand => sendListenersMessage(m)
    case id:ImportDescription => sendListenersMessage(id)
    case _ => warn("MeTLConversationSearchActorManager received unknown message")
  }
}
object MeTLSlideDisplayActorManager extends LiftActor with ListenerManager with Logger {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case m:MeTLCommand => sendListenersMessage(m)
    case _ => warn("MeTLSlideDisplayActorManager received unknown message")
  }
}
object MeTLEditConversationActorManager extends LiftActor with ListenerManager with Logger {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case m:MeTLCommand => sendListenersMessage(m)
    case _ => warn("MeTLEditConversationActorManager received unknown message")
  }
}

trait ConversationFilter {
  protected def conversationFilterFunc(c:Conversation,me:String,myGroups:List[Tuple2[String,String]],includeDeleted:Boolean = false):Boolean = {
      val subject = c.subject.trim.toLowerCase
      val author = c.author.trim.toLowerCase
      com.metl.snippet.Metl.shouldDisplayConversation(c,includeDeleted)
//      ((subject != "deleted" || (includeDeleted && author == me)) && (author == me || myGroups.exists(_._2.toLowerCase.trim == subject)))
  }
  def filterConversations(in:List[Conversation],includeDeleted:Boolean = false):List[Conversation] = {
    lazy val me = Globals.currentUser.is.toLowerCase.trim
    lazy val myGroups = Globals.casState.is.eligibleGroups.toList
    in.filter(c => conversationFilterFunc(c,me,myGroups,includeDeleted))
  }
}

class MeTLSlideDisplayActor extends CometActor with CometListener with Logger {
  import com.metl.snippet.Metl._
  override def registerWith = MeTLSlideDisplayActorManager
  protected var currentConversation:Option[Conversation] = None
  protected var currentSlide:Option[Int] = None
  override def lifespan = Full(2 minutes)
  override def localSetup = {
    super.localSetup
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      com.metl.snippet.Metl.getConversationFromName(nameString).foreach(convJid => {
        currentConversation = Some(serverConfig.detailsOfConversation(convJid.toString))
      })
      com.metl.snippet.Metl.getSlideFromName(nameString).map(slideJid => {
        currentSlide = Some(slideJid)
        slideJid
      }).getOrElse({
        currentConversation.foreach(cc => {
          cc.slides.sortWith((a,b) => a.index < b.index).headOption.map(firstSlide => {
            currentSlide = Some(firstSlide.id)
          })
        })
      })
    })
    warn("setup slideDisplay: %s %s".format(currentConversation,currentSlide))
  }
  protected var username = Globals.currentUser.is
  protected lazy val serverConfig = ServerConfiguration.default
  override def render = {
    "#slidesContainer2 *" #> {
      ".slideContainer2" #> currentConversation.map(_.slides).getOrElse(Nil).map(slide => {
        currentConversation.map(cc => {
          ".slideAnchor [href]" #> boardFor(cc.jid,slide.id) &
          ".slideIndex *" #> slide.index &
          ".slideId *" #> slide.id &
          ".slideActive *" #> currentSlide.exists(_ == slide.id)
        }).getOrElse({
          ".slideAnchor" #> NodeSeq.Empty
        })
      })
    } &
    "#addSlideButtonContainer" #> currentConversation.filter(cc => shouldModifyConversation(username,cc)).map(cc => {
      "#addSlideButtonContainer [onclick]" #> {
        ajaxCall(Jq("#this"),(j:String) => {
          warn("add slide button clicked: %s".format(j))
          val index = currentSlide.flatMap(cs => cc.slides.find(_.id == cs).map(_.index)).getOrElse(0)
          serverConfig.addSlideAtIndexOfConversation(cc.jid.toString,index)
          reRender
          Noop
        })
      }
    }).getOrElse({
      "#addSlideButtonContainer" #> NodeSeq.Empty
    })
  }
  override def lowPriority = {
    case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") => {
      val newJid = c.commandParameters(0).toInt
      val newConv = serverConfig.detailsOfConversation(newJid.toString)
      if (currentConversation.exists(_.jid == newConv.jid)){
        if (!shouldDisplayConversation(newConv)){
          warn("sendMeTLStanzaToPage kicking this cometActor(%s) from the conversation because it's no longer permitted".format(name))
          currentConversation = Empty
          currentSlide = Empty
          reRender
          partialUpdate(RedirectTo(noBoard))
        } else {
          currentConversation = Some(newConv)
          debug("updating conversation to: %s".format(newConv))
          reRender
        }
      }
    }
    case c:MeTLCommand if (c.command == "/SYNC_MOVE") => {
      warn("incoming syncMove: %s".format(c))
      val newJid = c.commandParameters(0).toInt
      currentConversation.filter(cc => currentSlide.exists(_ != newJid)).map(cc => {
        cc.slides.find(_.id == newJid).foreach(slide => {
          warn("moving to: %s".format(slide))
          currentSlide = Some(slide.id)
          reRender
          partialUpdate(RedirectTo(boardFor(cc.jid,slide.id)))
        })
      })
    }
    case c:MeTLCommand if (c.command == "/TEACHER_IN_CONVERSATION") => {
      //not relaying teacherInConversation to page
    }
    case _ => warn("MeTLSlideDisplayActor received unknown message")
  }
}

class RemotePluginConversationChooserActor extends MeTLConversationChooserActor {
  protected val ltiIntegration:BrightSparkIntegration = RemotePluginIntegration
  protected var ltiToken:Option[String] = None
  protected var ltiSession:Option[RemotePluginSession] = None
  override def localSetup = {
    super.localSetup
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      ltiToken = com.metl.snippet.Metl.getLtiTokenFromName(nameString)
      ltiSession = ltiToken.flatMap(token => ltiIntegration.sessionStore.is.get(token))
    })
  }
  override def perConversationAction(conv:Conversation) = {
    ".conversationAnchor [href]" #> ltiToken.map(lti => remotePluginChoseConversation(lti,conv.jid)) &
    ".conversationTitle *" #> conv.title &
    ".conversationAuthor *" #> conv.author &
    ".conversationJid *" #> conv.jid &
    ".conversationEditingContainer" #> {
      shouldModifyConversation(Globals.currentUser.is,conv) match {
        case true => ".editConversationLink [href]" #> editConversation(conv.jid)
        case false => ".conversationEditingContainer" #> NodeSeq.Empty
      }
    } /*&
       ".conversationChoosingContainer" #> {
       ".quickLinkButton [onclick]" #> {
       ajaxCall(JsRaw("this"),(s:String) => {
       Alert("clicked quickLink: (%s) => %s \r\n%s".format(s,conv,ltiSession))
       })
       } &
       ".iFrameButton [onclick]" #> {
       ajaxCall(JsRaw("this"),(s:String) => {
       Alert("clicked iFrame: (%s) => %s \r\n%s".format(s,conv,ltiSession))
       })
       }
       }
       */
  }
  override def perImportAction(conv:Conversation) = {
    ".importSuccess [href]" #> ltiToken.map(lti => remotePluginChoseConversation(lti,conv.jid))
  }
}
class MeTLConversationSearchActor extends MeTLConversationChooserActor {
  override def perConversationAction(conv:Conversation) = {
    ".conversationAnchor [href]" #> boardFor(conv.jid) &
    ".conversationTitle *" #> conv.title &
    ".conversationAuthor *" #> conv.author &
    ".conversationJid *" #> conv.jid &
    ".conversationEditingContainer" #> {
      shouldModifyConversation(Globals.currentUser.is,conv) match {
        case true => ".editConversationLink [href]" #> editConversation(conv.jid)
        case false => ".conversationEditingContainer" #> NodeSeq.Empty
      }
    } &
    ".slidesContainer" #> {
      ".slide" #> conv.slides.sortWith((a,b) => a.index < b.index).map(slide => {
        ".slideIndex *" #> slide.index &
        ".slideId *" #> slide.id &
        ".slideAnchor [href]" #> boardFor(conv.jid,slide.id)
      })
    }
  }
  override def perImportAction(conv:Conversation) = {
    ".importSuccess [href]" #> boardFor(conv.jid)
  }
}

class MeTLJsonConversationChooserActor extends StronglyTypedJsonActor with CometListener with Logger with JArgUtils with ConversationFilter {
  private val serializer = new JsonSerializer("frontend")

  implicit def jeToJsCmd(in:JsExp):JsCmd = in.cmd
  override def autoIncludeJsonCode = true

  private lazy val RECEIVE_USERNAME = "receiveUsername"
  private lazy val RECEIVE_CONVERSATIONS = "receiveConversations"
  private lazy val RECEIVE_IMPORT_DESCRIPTION = "receiveImportDescription"
  private lazy val RECEIVE_IMPORT_DESCRIPTIONS = "receiveImportDescriptions"
  private lazy val RECEIVE_USER_GROUPS = "receiveUserGroups"
  private lazy val RECEIVE_CONVERSATION_DETAILS = "receiveConversationDetails"
  private lazy val RECEIVE_NEW_CONVERSATION_DETAILS = "receiveNewConversationDetails"
  private lazy val RECEIVE_QUERY = "receiveQuery"

  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => JObject(List(JField("type",JString(eg._1)),JField("value",JString(eg._2))))).toList)
  override lazy val functionDefinitions = List(
    ClientSideFunctionDefinition("getUserGroups",List.empty[String],(args) => getUserGroups,Full(RECEIVE_USER_GROUPS)),
    ClientSideFunctionDefinition("getUser",List.empty[String],(unused) => JString(username),Full(RECEIVE_USERNAME)),
    ClientSideFunctionDefinition("getSearchResult",List("query"),(args) => {
      val q = args(0).toString.toLowerCase.trim
      query = Some(q)
//      partialUpdate(Call(RECEIVE_QUERY,JString(q)))
      val foundConversations = serverConfig.searchForConversation(q)
      listing = filterConversations(foundConversations,true)
      println("searchingWithQuery: %s => %s : %s".format(query,foundConversations.length,listing.length))
      serializer.fromConversationList(listing)
    },Full(RECEIVE_CONVERSATIONS)),
    ClientSideFunctionDefinition("createConversation",List("title"),(args) => {
      val title = getArgAsString(args(0))
      val newConv = serverConfig.createConversation(title,username)
      listing = newConv :: listing
      serializer.fromConversation(newConv)
    },Full(RECEIVE_NEW_CONVERSATION_DETAILS))
  )

  protected var query:Option[String] = None
  protected var listing:List[Conversation] = Nil
  protected var imports:List[ImportDescription] = Nil

  override def registerWith = MeTLConversationSearchActorManager
  override def lifespan = Full(5 minutes)
  protected val username = Globals.currentUser.is
  protected lazy val serverConfig = ServerConfiguration.default

  override def localSetup = {
    query = Some(username.toLowerCase.trim)
    listing = query.toList.flatMap(q => filterConversations(serverConfig.searchForConversation(q),true))
    super.localSetup
  }
  override def render = OnLoad(
    Call(RECEIVE_USERNAME,JString(username)) &
      Call(RECEIVE_USER_GROUPS,getUserGroups) &
      Call(RECEIVE_QUERY,JString(query.getOrElse(""))) &
      Call(RECEIVE_CONVERSATIONS,serializer.fromConversationList(listing)) &
      Call(RECEIVE_IMPORT_DESCRIPTIONS,JArray(imports.map(serialize _)))
  )
  protected def serialize(id:ImportDescription):JValue = net.liftweb.json.Extraction.decompose(id)(net.liftweb.json.DefaultFormats);

  protected def queryApplies(in:Conversation):Boolean = query.map(q => in.title.toLowerCase.trim.contains(q) || in.author.toLowerCase.trim == q).getOrElse(false)

  override protected def conversationFilterFunc(c:Conversation,me:String,myGroups:List[Tuple2[String,String]],includeDeleted:Boolean = false):Boolean = super.conversationFilterFunc(c,me,myGroups,includeDeleted) && queryApplies(c)

  override def lowPriority = {
    case id:ImportDescription => {
      if (id.author == username){
        trace("received importDescription: %s".format(id))
        if (imports.exists(_.id == id.id)){
          imports = imports.map{
            case i:ImportDescription if (i.id == id.id && (i.timestamp.getTime() < id.timestamp.getTime() || id.result.isDefined)) => id
            case other => other
          }
        } else {
          imports = id :: imports
        }
        partialUpdate(Call(RECEIVE_IMPORT_DESCRIPTION,serialize(id)))
      }
    }
    case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") => {
      trace("receivedCommand: %s".format(c))
      val newJid = c.commandParameters(0).toInt
      val newConv = serverConfig.detailsOfConversation(newJid.toString)
      listing = filterConversations(List(newConv) ::: listing.filterNot(_.jid == newConv.jid))
      partialUpdate(Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(newConv)))
    }
    case _ => warn("MeTLConversationSearchActor received unknown message")
  }
}


abstract class MeTLConversationChooserActor extends StronglyTypedJsonActor with CometListener with Logger with JArgUtils with ConversationFilter {
  protected def perConversationAction(conv:Conversation):CssSel
  protected def perImportAction(conv:Conversation):CssSel
  protected def aggregateConversationAction(convs:Seq[Conversation]):CssSel = {
    ".count *" #> (convs.length match {
      case 1 => "0 search result"
      case n => "%s search results".format(n)
    })
  }
  private val serializer = new JsonSerializer("frontend")
  implicit def jeToJsCmd(in:JsExp):JsCmd = in.cmd
  override def autoIncludeJsonCode = true

  private lazy val RECEIVE_USERNAME = "receiveUsername"
  private lazy val RECEIVE_CONVERSATIONS = "receiveConversations"
  private lazy val RECEIVE_IMPORT_DESCRIPTION = "receiveImportDescription"
  private lazy val RECEIVE_USER_GROUPS = "receiveUserGroups"
  private lazy val RECEIVE_CONVERSATION_DETAILS = "receiveConversationDetails"
  private lazy val RECEIVE_NEW_CONVERSATION_DETAILS = "receiveNewConversationDetails"

  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => JObject(List(JField("type",JString(eg._1)),JField("value",JString(eg._2))))).toList)
  override lazy val functionDefinitions = List(
    ClientSideFunctionDefinition("getUserGroups",List.empty[String],(args) => getUserGroups,Full(RECEIVE_USER_GROUPS)),
    ClientSideFunctionDefinition("getUser",List.empty[String],(unused) => JString(username),Full(RECEIVE_USERNAME)),
    ClientSideFunctionDefinition("getSearchResult",List("query"),(args) => {
      serializer.fromConversationList(filterConversations(serverConfig.searchForConversation(args(0).toString)))
    },Full(RECEIVE_CONVERSATIONS))
  )

  override def registerWith = MeTLConversationSearchActorManager
  override def lifespan = Full(5 minutes)
  protected val username = Globals.currentUser.is
  protected lazy val serverConfig = ServerConfiguration.default
  protected var query:Option[String] = None
  protected var listing:List[Conversation] = Nil
  override def localSetup = {
    super.localSetup
    query = Some(Globals.currentUser.is)
    listing = query.map(q => filterConversations(serverConfig.searchForConversation(q))).getOrElse(Nil)
  }
  protected def queryApplies(c:Conversation):Boolean = {
    listing.exists(_.jid == c.jid) || c.author == Globals.currentUser.is || query.map(_.toLowerCase.trim).exists(q => c.author.toLowerCase.trim == q || c.title.toLowerCase.trim.contains(q))
  }
  protected var imports:List[ImportDescription] = Nil
  override def render = {
    "#createConversationContainer" #> {
      "#createConversationButton [onclick]" #> ajaxCall(JsNull,(_s:String) => {
        val title = "%s at %s".format(Globals.currentUser.is,new java.util.Date())
        val newConv = serverConfig.createConversation(title,Globals.currentUser.is)
        reRender
      })
    } &
    "#conversationSearchBox *" #> ajaxText(query.getOrElse(""),(q:String) => {
      query = Some(q)
      listing = query.map(q => filterConversations(serverConfig.searchForConversation(q))).getOrElse(Nil)
      reRender
    }) &
    "#activeImportsListing *" #> imports.groupBy(_.id).values.flatMap(_.sortWith((a,b) => a.timestamp.getTime > b.timestamp.getTime).headOption).map(imp => {
      ".importContainer" #> {
        ".importId *" #> Text(imp.id) &
        ".importName *" #> Text(imp.name) &
        ".importAuthor *" #> Text(imp.author) &
        {
          imp.result match {
            case None => {
              ".importProgressContainer" #> {
                ".importOverallProgressContainer *" #> {
                  ".importProgressDescriptor *" #> imp.overallProgress.map(_.name) &
                  ".importProgressProgressBar [style]" #> imp.overallProgress.map(p => "width: %s%%".format((p.numerator * 100) / p.denominator))
                } &
                ".importStageProgressContainer *" #> {
                  ".importProgressDescriptor *" #> imp.stageProgress.map(_.name) &
                  ".importProgressProgressBar [style]" #> imp.stageProgress.map(p => "width: %s%%".format((p.numerator * 100) / p.denominator))
                }
              } &
              ".importResultContainer" #> NodeSeq.Empty
            }
            case Some(Left(e)) => {
              ".importProgressContainer" #> NodeSeq.Empty &
              ".importResultContainer" #> {
                ".importError *" #> e.getMessage &
                ".importSuccess" #> NodeSeq.Empty
              }
            }
            case Some(Right(conv)) => {
              ".importProgressContainer" #> NodeSeq.Empty &
              ".importError" #> NodeSeq.Empty &
              perImportAction(conv)
            }
          }
        }
      }
    }) &
    "#conversationListing *" #> {
      ".aggregateContainer *" #> aggregateConversationAction(listing) &
      ".conversationContainer" #> listing.sortWith((a,b) => a.created > b.created).map(conv => {
        perConversationAction(conv)
      })
    }
  }
  override def lowPriority = {
    case id:ImportDescription => {
      if (id.author == username){
        warn("received importDescription: %s".format(id))
        imports = id :: imports
        reRender
      }
    }
    case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") => {
      warn("receivedCommand: %s".format(c))
      val newJid = c.commandParameters(0).toInt
      val newConv = serverConfig.detailsOfConversation(newJid.toString)
      if (queryApplies(newConv) && shouldDisplayConversation(newConv)){
        //listing = query.map(q => filterConversations(serverConfig.searchForConversation(q))).getOrElse(Nil)
        listing = newConv :: listing.filterNot(_.jid == newConv.jid)//query.map(q => filterConversations(serverConfig.searchForConversation(q))).getOrElse(Nil)
          reRender
      } else if (listing.exists(_.jid == newConv.jid)){
        listing = listing.filterNot(_.jid == newConv.jid)
        reRender
      }
    }
    case _ => warn("MeTLConversationSearchActor received unknown message")
  }
}

class MeTLEditConversationActor extends StronglyTypedJsonActor with CometListener with Logger with JArgUtils with ConversationFilter {
  import com.metl.view._
  private val serializer = new JsonSerializer("frontend")
  implicit def jeToJsCmd(in:JsExp):JsCmd = in.cmd
  override def autoIncludeJsonCode = true
  private lazy val RECEIVE_USERNAME = "receiveUsername"
  private lazy val RECEIVE_USER_GROUPS = "receiveUserGroups"
  private lazy val RECEIVE_CONVERSATION_DETAILS = "receiveConversationDetails"
  private lazy val RECEIVE_NEW_CONVERSATION_DETAILS = "receiveNewConversationDetails"
  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => JObject(List(JField("type",JString(eg._1)),JField("value",JString(eg._2))))).toList)
  override lazy val functionDefinitions = List(
    ClientSideFunctionDefinition("reorderSlidesOfCurrentConversation",List("jid","newSlides"),(args) => {
      val jid = getArgAsString(args(0))
      val newSlides = getArgAsJArray(args(1))
      val c = serverConfig.detailsOfConversation(args(0).toString)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => {
          (newSlides.arr.length == c.slides.length) match {
            case true => {
              val deserializedSlides = newSlides.arr.map(i => serializer.toSlide(i)).toList
              serverConfig.reorderSlidesOfConversation(c.jid.toString,deserializedSlides)
            }
            case false => c
          }
        }
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("deleteConversation",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => {
          println("deleting conversation %s".format(c.jid))
          serverConfig.deleteConversation(c.jid.toString)
        }
        case _ => {
          println("refusing to delete conversation %s".format(c.jid))
          c
        }
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("renameConversation",List("jid","newTitle"),(args) => {
      val jid = getArgAsString(args(0))
      val newTitle = getArgAsString(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => serverConfig.renameConversation(c.jid.toString,newTitle)
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("changeSubjectOfConversation",List("conversationJid","newSubject"),(args) => {
      val jid = getArgAsString(args(0))
      val newSubject = getArgAsString(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation((shouldModifyConversation(c) && Globals.getUserGroups.exists(_._2 == newSubject)) match {
        case true => serverConfig.updateSubjectOfConversation(c.jid.toString.toLowerCase,newSubject)
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("addSlideToConversationAtIndex",List("jid","index"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("duplicateSlideById",List("jid","slideId"),(args) => {
      val jid = getArgAsString(args(0))
      val slideId = getArgAsInt(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => StatelessHtml.duplicateSlideInternal(username,slideId.toString,c.jid.toString).getOrElse(c)
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("duplicateConversation",List("jid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => StatelessHtml.duplicateConversationInternal(username,c.jid.toString).openOr(c)
        case _ => c
      })
    },Full(RECEIVE_NEW_CONVERSATION_DETAILS))
  )
  override def registerWith = MeTLEditConversationActorManager
  override def lifespan = Full(5 minutes)
  protected lazy val serverConfig = ServerConfiguration.default
  protected var conversation:Option[Conversation] = None
  protected val username = Globals.currentUser.is
  override def localSetup = {
    super.localSetup
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      conversation = com.metl.snippet.Metl.getConversationFromName(nameString).map(jid => serverConfig.detailsOfConversation(jid.toString))
    })
  }

  override def render = {
    OnLoad(conversation.filter(c => shouldModifyConversation(c)).map(c => {
      Call(RECEIVE_USERNAME,JString(username)) &
      Call(RECEIVE_USER_GROUPS,getUserGroups) &
      Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(c))
    }).getOrElse(RedirectTo(conversationSearch())))
  }
  override def lowPriority = {
    case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") && c.commandParameters.headOption.exists(cid => conversation.exists(_.jid.toString == cid.toString))  => {
      conversation.foreach(c => {
        val newConv = serverConfig.detailsOfConversation(c.jid.toString)
        warn("receivedUpdatedConversation: %s => %s".format(c,newConv))
        conversation = Some(newConv)
        reRender
      })
    }
    case _ => warn("MeTLEditConversationActor received unknown message")
  }
  protected def shouldModifyConversation(c:Conversation = conversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldModifyConversation(username,c)
  protected def shouldDisplayConversation(c:Conversation = conversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldDisplayConversation(c)
  protected def shouldPublishInConversation(c:Conversation = conversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldPublishInConversation(username,c)
}


trait JArgUtils {
  protected def getArgAsBool(input:Any):Boolean = input match {
    case JBool(bool) => bool
    case s:String if (s.toString.trim == "false") => false
    case s:String if (s.toString.trim == "true") => true
    case other => false
  }
  protected def getArgAsString(input:Any):String = input match {
    case JString(js) => js
    case s:String => s
    case other => other.toString
  }
  protected def getArgAsInt(input:Any):Int = input match {
    case JInt(i) => i.toInt
    case i:Int => i
    case JNum(n) => n.toInt
    case d:Double => d.toInt
    case s:String => s.toInt
    case other => other.toString.toInt
  }
  protected def getArgAsJValue(input:Any):JValue = input match {
    case jv:JValue => jv
    case other => JNull
  }
  protected def getArgAsJArray(input:Any):JArray = input match {
    case l:List[JValue] => JArray(l)
    case ja:JArray => ja
    case other => JArray(List.empty[JValue])
  }

}

class MeTLActor extends StronglyTypedJsonActor with Logger with JArgUtils with ConversationFilter {
  implicit def jeToJsCmd(in:JsExp):JsCmd = in.cmd
  private val userUniqueId = nextFuncName

  // javascript functions to fire
  private lazy val RECEIVE_SYNC_MOVE = "receiveSyncMove"
  private lazy val RECEIVE_CURRENT_CONVERSATION = "receiveCurrentConversation"
  private lazy val RECEIVE_CURRENT_SLIDE = "receiveCurrentSlide"
  private lazy val RECEIVE_CONVERSATION_DETAILS = "receiveConversationDetails"
  private lazy val RECEIVE_NEW_CONVERSATION_DETAILS = "receiveNewConversationDetails"
  private lazy val RECEIVE_METL_STANZA = "receiveMeTLStanza"
  private lazy val RECEIVE_USERNAME = "receiveUsername"
  private lazy val RECEIVE_CONVERSATIONS = "receiveConversations"
  private lazy val RECEIVE_USER_GROUPS = "receiveUserGroups"
  private lazy val RECEIVE_HISTORY = "receiveHistory"
  private lazy val RECEIVE_USER_OPTIONS = "receiveUserOptions"
  private lazy val RECEIVE_QUIZZES = "receiveQuizzes"
  private lazy val RECEIVE_QUIZ_RESPONSES = "receiveQuizResponses"
  private lazy val RECEIVE_IS_INTERACTIVE_USER = "receiveIsInteractiveUser"

  override lazy val functionDefinitions = List(
    ClientSideFunctionDefinition("refreshClientSideState",List.empty[String],(args) => {
      partialUpdate(refreshClientSideStateJs)
      JNull
    },Empty),
    ClientSideFunctionDefinition("getHistory",List("slide"),(args)=> {
      val jid = getArgAsString(args(0))
      debug("getHistory requested")
      getSlideHistory(jid)
    },Full(RECEIVE_HISTORY)),
    /*
     ClientSideFunctionDefinition("getRoomPopulations",List.empty[String],(args) => {
     JArray(rooms.map(kv => JObject(List(JField("server",JString(kv._1._1)),JField("jid",JString(kv._1._2)),JField("room",JString(kv._2.toString)),JField("population",JArray(kv._2().getChildren.map(cu => JString(cu._1)).toList))))).toList)
     },Full("receiveRoomPopulations")),
     */
    ClientSideFunctionDefinition("getSearchResult",List("query"),(args) => {
      serializer.fromConversationList(filterConversations(serverConfig.searchForConversation(args(0).toString)))
    },Full(RECEIVE_CONVERSATIONS)),
    ClientSideFunctionDefinition("getIsInteractiveUser",List.empty[String],(args) => isInteractiveUser.map(iu => JBool(iu)).openOr(JBool(true)),Full(RECEIVE_IS_INTERACTIVE_USER)),
    ClientSideFunctionDefinition("setIsInteractiveUser",List("isInteractive"),(args) => {
      val isInteractive = getArgAsBool(args(0))
      isInteractiveUser = Full(isInteractive)
      isInteractiveUser.map(iu => JBool(iu)).openOr(JBool(true))
    },Full(RECEIVE_IS_INTERACTIVE_USER)),
    ClientSideFunctionDefinition("getUserOptions",List.empty[String],(args) => JString("not yet implemented"),Full(RECEIVE_USER_OPTIONS)),
    ClientSideFunctionDefinition("setUserOptions",List("newOptions"),(args) => JString("not yet implemented"),Empty),
    ClientSideFunctionDefinition("getUserGroups",List.empty[String],(args) => getUserGroups,Full(RECEIVE_USER_GROUPS)),
    ClientSideFunctionDefinition("getResource",List("source"),(args) => JString("not yet implemented"),Empty),
    ClientSideFunctionDefinition("moveToSlide",List("where"),(args) => {
      val where = getArgAsString(args(0))
      debug("moveToSlideRequested(%s)".format(where))
      moveToSlide(where)
      JNull
    },Empty),
    ClientSideFunctionDefinition("joinRoom",List("where"),(args) => {
      val where = getArgAsString(args(0))
      joinRoomByJid(where)
      joinRoomByJid(where+username)
      JNull
    },Empty),
    ClientSideFunctionDefinition("leaveRoom",List("where"),(args) => {
      val where = getArgAsString(args(0))
      leaveRoomByJid(where)
      leaveRoomByJid(where+username)
      JNull
    },Empty),
    ClientSideFunctionDefinition("sendStanza",List("stanza"),(args) => {
      val stanza = getArgAsJValue(args(0))
      sendStanzaToServer(stanza)
      JNull
    },Empty),
    ClientSideFunctionDefinition("sendTransientStanza",List("stanza"),(args) => {
      val stanza = getArgAsJValue(args(0))
      sendStanzaToServer(stanza,"loopback")
      JNull
    },Empty),
    ClientSideFunctionDefinition("changeUser",List("username"),(args) => {
      val newUsername = getArgAsString(args(0))
      if (Globals.isDevMode){
        //Can't change the username anymore.
        //Globals.currentUser(newUsername)
      }
      JString(username)
    }, Full(RECEIVE_USERNAME)),
    ClientSideFunctionDefinition("getRooms",List.empty[String],(unused) => JArray(rooms.map(kv => JObject(List(JField("server",JString(kv._1._1)),JField("jid",JString(kv._1._2)),JField("room",JString(kv._2.toString))))).toList),Full("recieveRoomListing")),
    ClientSideFunctionDefinition("getUser",List.empty[String],(unused) => JString(username),Full(RECEIVE_USERNAME)),
    /*
    ClientSideFunctionDefinition("joinConversation",List("where"),(args) => {
      val where = getArgAsString(args(0))
      joinConversation(where).map(c => serializer.fromConversation(c)).openOr(JNull)
    },Full(RECEIVE_CONVERSATION_DETAILS)),

    ClientSideFunctionDefinition("leaveConversation",List.empty[String],(args) => {
      leaveAllRooms()
      currentConversation = None
      JNull
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("createConversation",List("title"),(args) => {
      val title = getArgAsString(args(0))
      serializer.fromConversation(serverConfig.createConversation(title,username))
    },Full(RECEIVE_NEW_CONVERSATION_DETAILS)),
  */
    /*
     ClientSideFunctionDefinition("deleteConversation",List("jid"),(args) => {
     val jid = getArgAsString(args(0))
     val c = serverConfig.detailsOfConversation(jid)
     serializer.fromConversation(shouldModifyConversation(c) match {
     case true => serverConfig.deleteConversation(c.jid.toString)
     case _ => c
     })
     },Full(RECEIVE_CONVERSATION_DETAILS)),
     */
    /*
    ClientSideFunctionDefinition("importConversation",List.empty[String],(unused) => {
      val im = InteractableMessage((i) => {
        val uploadId = nextFuncName
        val progressId = nextFuncName
        val progressBarId = nextFuncName
        val script = """$('#'+'%s').fileupload({
                dataType: 'json',
                add: function (e,data) {
                  $('#'+'%s').css('width', '0%%');
                  $('#'+'%s').show();
                  data.submit();
                },
                progressall: function (e, data) {
                  var progress = parseInt(data.loaded / data.total * 100, 10) + '%%';
                  $('#'+'%s').css('width', progress);
                },
                done: function (e, data) {
                  $.each(data.files, function (index, file) {
                    $('<p/>').text(file.name).appendTo(document.body);
                  });
                  $('#'+'%s').fadeOut();
                }
              });
            """.format(uploadId,progressId,progressBarId,progressBarId,progressId)

        val nodes = <div>
        <label for={uploadId}>{Text("Select your file")}</label>
        <div>{Text("* The conversation will appear quickly, but may take a minute or two to fill with content.")}</div>
        <input id={uploadId} type="file" name="files[]" data-url="/conversationImportEndpoint"></input>
        <div id={progressId} style="width:20em; border: 1pt solid silver; display: none">
        <div id={progressBarId} style="background: green; height: 1em; width:0%"></div>
        </div>
        <script>{script}</script>
        </div>
        nodes
      },Full("conversationImport"),Full("Import conversation"))
      this ! im
      JNull
    },Empty),
    ClientSideFunctionDefinition("requestDeleteConversationDialogue",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      this ! SimpleMultipleButtonInteractableMessage("Archive conversation","Are you sure you would like to archive this conversation?  Have you considered whether students have important content on this conversation?",
        Map(
          "yes" -> {() => {
            if (shouldModifyConversation(c)){
              serverConfig.deleteConversation(c.jid.toString)
              S.redirectTo("/conversationSearch")
              true
            } else {
              false
            }
          }},
          "no" -> {() => true}
        ),Full(()=> this ! SpamMessage(Text("You are not permitted to archive this conversation"))),false,Full("conversations"))
      JNull
    },Empty),
  */
    /*
     ClientSideFunctionDefinition("renameConversation",List("jid","newTitle"),(args) => {
     val jid = getArgAsString(args(0))
     val newTitle = getArgAsString(args(1))
     val c = serverConfig.detailsOfConversation(jid)
     serializer.fromConversation(shouldModifyConversation(c) match {
     case true => serverConfig.renameConversation(c.jid.toString,newTitle)
     case _ => c
     })
     },Full(RECEIVE_CONVERSATION_DETAILS)),
     */
    /*
    ClientSideFunctionDefinition("requestRenameConversationDialogue",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      this ! SimpleTextAreaInteractableMessage("Rename conversation","What would you like to rename this conversation?",c.title,(renamed) => {
        if (renamed.length > 0 && shouldModifyConversation(c)){
          val newConv = serverConfig.renameConversation(c.jid.toString,renamed)
          true
        } else false
      },Full(() => this ! SpamMessage(Text("An error occurred while attempting to rename the conversation"))),Full("conversations"))
      JNull
    },Empty),
  */
    ClientSideFunctionDefinition("changePermissionsOfConversation",List("jid","newPermissions"),(args) => {
      val jid = getArgAsString(args(0))
      val newPermissions = getArgAsJValue(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => serverConfig.changePermissions(c.jid.toString,serializer.toPermissions(newPermissions))
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("changeBlacklistOfConversation",List("jid","newBlacklist"),(args) => {
      val jid = getArgAsString(args(0))
      val rawBlacklist = args(1) match {
        case l:List[String] => l
        case JArray(bl) => bl.flatMap{
          case JString(s) => Some(s)
          case other => {
            warn("unknown internal JValue: [%s]".format(other))
            None
          }
        }
        case other => {
          warn("unknown JValue: [%s]".format(other))
          Nil
        }
      }
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => serverConfig.updateConversation(c.jid.toString,c.copy(blackList = rawBlacklist))//newBlacklist))
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("banContent",List("conversationJid","slideJid","inkIds","textIds","multiWordTextIds","imageIds"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val slideJid = getArgAsInt(args(1))
      val inkIds = args(2) match {
        case l:List[String] => l
        case _ => Nil
      }
      val textIds = args(3) match {
        case l:List[String] => l
        case _ => Nil
      }
      val multiWordTextIds = args(4) match {
        case l:List[String] => l
        case _ => Nil
      }
      val imageIds = args(5) match {
        case l:List[String] => l
        case _ => Nil
      }
      val now = new Date().getTime
      val pubHistory = rooms.get((server,slideJid.toString)).map(r => r().getHistory).getOrElse(History.empty)

      val title = "submission%s%s.jpg".format(username,now.toString)

      val inks = pubHistory.getInks.filter(elem => inkIds.contains(elem.identity))
      val images = pubHistory.getImages.filter(elem => imageIds.contains(elem.identity))
      val texts = pubHistory.getTexts.filter(elem => textIds.contains(elem.identity))
      val multiWordTexts = pubHistory.getMultiWordTexts.filter(elem => multiWordTextIds.contains(elem.identity))
      val highlighters = pubHistory.getHighlighters.filter(elem => inkIds.contains(elem.identity))

      val authors = (inks ::: images ::: texts ::: highlighters ::: multiWordTexts).map(_.author).distinct
      val conv = serverConfig.detailsOfConversation(conversationJid)
      if (shouldModifyConversation(conv)){
        serverConfig.updateConversation(conv.jid.toString,conv.copy(blackList = (conv.blackList ::: authors).distinct.toList))
        this ! SpamMessage(<div />,Full("submissions"),Full("Blacklisted users: %s".format(authors)))

        def getColorForAuthor(name:String):Color = {
          new Color(128,128,128,128)
        }
        val thickness = 5
        val coloredAuthors = Map(authors.map(a => (a,SubmissionBlacklistedPerson(a,getColorForAuthor(a)))):_*)

        val annotationHistory = new History("annotation")

        inks.foreach(ink => {
          val color = coloredAuthors(ink.author).highlight
          annotationHistory.addStanza(ink.copy(color = color,thickness=ink.thickness * 2,author="blacklist"))
        })
        images.foreach(image => {
          val color = coloredAuthors(image.author).highlight
          val bounds = List(Point(image.left,image.top,thickness),Point(image.right,image.top,thickness),Point(image.right,image.bottom,thickness),Point(image.left,image.bottom,thickness),Point(image.left,image.top,thickness))
          val newStanza = MeTLInk(serverConfig,"blacklist",-1,0.0,0.0,bounds,color,thickness,true,"presentationSpace",Privacy.PUBLIC,slideJid.toString,"",Nil,1.0,1.0)
          annotationHistory.addStanza(newStanza)

        })
        texts.foreach(text => {
          val color = coloredAuthors(text.author).highlight
          val bounds = List(Point(text.left,text.top,thickness),Point(text.right,text.top,thickness),Point(text.right,text.bottom,thickness),Point(text.left,text.bottom,thickness),Point(text.left,text.top,thickness))
          val newStanza = MeTLInk(serverConfig,"blacklist",-1,0.0,0.0,bounds,color,thickness,true,"presentationSpace",Privacy.PUBLIC,slideJid.toString,"",Nil,1.0,1.0)
          annotationHistory.addStanza(newStanza)
        })
        multiWordTexts.foreach(text => {
          val color = coloredAuthors(text.author).highlight
          val bounds = List(Point(text.left,text.top,thickness),Point(text.right,text.top,thickness),Point(text.right,text.bottom,thickness),Point(text.left,text.bottom,thickness),Point(text.left,text.top,thickness))
          val newStanza = MeTLInk(serverConfig,"blacklist",-1,0.0,0.0,bounds,color,thickness,true,"presentationSpace",Privacy.PUBLIC,slideJid.toString,"",Nil,1.0,1.0)
          annotationHistory.addStanza(newStanza)
        })

        val mergedHistory = pubHistory.merge(annotationHistory)

        val width = (mergedHistory.getRight - mergedHistory.getLeft).toInt
        val height = (mergedHistory.getBottom - mergedHistory.getTop).toInt
          (width,height) match {
          case (a:Int,b:Int) if a > 0 && b > 0 => {
            val blacklistedPeople = coloredAuthors.values.toList
            val imageBytes = SlideRenderer.render(mergedHistory,width,height)
            val uri = serverConfig.postResource(conversationJid,title,imageBytes)
            val submission = MeTLSubmission(serverConfig,username,now,title,slideJid,uri,Full(imageBytes),blacklistedPeople,"bannedcontent")
            debug("banned with the following: %s".format(submission))
            rooms.get((server,conversationJid)).map(r =>{
              r() ! LocalToServerMeTLStanza(submission)
            });
            this ! SpamMessage(<div />,Full("submissions"),Full("Blacklist record created and added for authors: %s".format(authors)))
          }
          case _ => {
            this ! SpamMessage(<div />,Full("submissions"),Full("blacklist record creation failed.  Your canvas is empty."))
          }
        }
        val deleterId = nextFuncName
        val deleter = MeTLMoveDelta(serverConfig,username,now,"presentationSpace",Privacy.PUBLIC,slideJid.toString,deleterId,0.0,0.0,inkIds,textIds,multiWordTextIds,imageIds,0.0,0.0,0.0,0.0,Privacy.NOT_SET,true)
        rooms.get((server,slideJid.toString)).map(r =>{
          r() ! LocalToServerMeTLStanza(deleter)
        })
      }
      JNull
    },Empty),
    /*
    ClientSideFunctionDefinition("requestChangeSubjectOfConversationDialogue",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      this ! StylableRadioButtonInteractableMessage("Change sharing","How would you like to share this conversation?",
        Map(Globals.getUserGroups.map(eg => (eg._2.toLowerCase, ()=>{
          if (shouldModifyConversation(c)){
            serverConfig.updateSubjectOfConversation(c.jid.toString.toLowerCase,eg._2)
            true
          } else false
        })).toList:_*),
        Full(c.subject.toLowerCase),Full(()=> this ! SpamMessage(Text("An error occurred while attempting to rename the conversation"))),Full("conversations"))
      JNull
    },Empty),
    */
    ClientSideFunctionDefinition("addSlideToConversationAtIndex",List("jid","index"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("addImageSlideToConversationAtIndex",List("jid","index","resourceId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val resourceId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            val bytes = serverConfig.getResource(resourceId)
            val now = new java.util.Date().getTime
            val identity = "%s%s".format(username,now.toString)
            val tempSubImage = MeTLImage(serverConfig,username,now,identity,Full(resourceId),Full(bytes),Empty,Double.NaN,Double.NaN,10,10,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
            val dimensions = SlideRenderer.measureImage(tempSubImage)
            val subImage = MeTLImage(serverConfig,username,now,identity,Full(resourceId),Full(bytes),Empty,dimensions.width,dimensions.height,dimensions.left,dimensions.top,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
            slideRoom ! LocalToServerMeTLStanza(subImage)
          })
          newC
        }
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),

    ClientSideFunctionDefinition("addSubmissionSlideToConversationAtIndex",List("jid","index","submissionId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val submissionId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            MeTLXConfiguration.getRoom(jid,server).getHistory.getSubmissions.find(sub => sub.identity == submissionId).map(sub => {
              val now = new java.util.Date().getTime
              val identity = "%s%s".format(username,now.toString)
              val tempSubImage = MeTLImage(serverConfig,username,now,identity,Full(sub.url),sub.imageBytes,Empty,Double.NaN,Double.NaN,10,10,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
              val dimensions = SlideRenderer.measureImage(tempSubImage)
              val subImage = MeTLImage(serverConfig,username,now,identity,Full(sub.url),sub.imageBytes,Empty,dimensions.width,dimensions.height,dimensions.left,dimensions.top,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
              slideRoom ! LocalToServerMeTLStanza(subImage)
            })
          })
          newC
        }
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("addQuizViewSlideToConversationAtIndex",List("jid","index","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val quizId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            val convHistory = MeTLXConfiguration.getRoom(jid,server).getHistory
            convHistory.getQuizzes.filter(q => q.id == quizId && !q.isDeleted).sortBy(q => q.timestamp).reverse.headOption.map(quiz => {
              val now = new java.util.Date().getTime
              val identity = "%s%s".format(username,now.toString)
              val genText = (text:String,size:Double,offset:Double,identityModifier:String) => MeTLText(serverConfig,username,now,text,size * 2,320,0,10,10 + offset,identity+identityModifier,"Normal","Arial","Normal",size,"none",identity+identityModifier,"presentationSpace",Privacy.PUBLIC,ho.id.toString,Color(255,0,0,0))
              val quizTitle = genText(quiz.question,16,0,"title")
              val questionOffset = quiz.url match{
                case Full(_) => 340
                case _ => 100
              };
              val quizOptions = quiz.options.foldLeft(List.empty[MeTLText])((acc,item) => {
                acc ::: List(genText("%s: %s".format(item.name,item.text),10,(acc.length * 10) + questionOffset,"option:"+item.name))
              })
              val allStanzas = quiz.url.map(u => List(MeTLImage(serverConfig,username,now,identity+"image",Full(u),Empty,Empty,320,240,10,50,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"image"))).getOrElse(List.empty[MeTLStanza]) ::: quizOptions ::: List(quizTitle)
              allStanzas.foreach(stanza => slideRoom ! LocalToServerMeTLStanza(stanza))
            })
          })
          newC
        }
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("addQuizResultsViewSlideToConversationAtIndex",List("jid","index","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val quizId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            val convHistory = MeTLXConfiguration.getRoom(jid,server).getHistory
            convHistory.getQuizzes.filter(q => q.id == quizId && !q.isDeleted).sortBy(q => q.timestamp).reverse.headOption.map(quiz => {
              val now = new java.util.Date().getTime
              val answers = convHistory.getQuizResponses.filter(qr => qr.id == quiz.id).foldLeft(Map.empty[String,MeTLQuizResponse])((acc,item) => {
                acc.get(item.answerer).map(qr => {
                  if (acc(item.answerer).timestamp < item.timestamp){
                    acc.updated(item.answerer,item)
                  } else {
                    acc
                  }
                }).getOrElse(acc.updated(item.answerer,item))
              }).foldLeft(Map(quiz.options.map(qo => (qo,List.empty[MeTLQuizResponse])):_*))((acc,item) => {
                quiz.options.find(qo => qo.name == item._2.answer).map(qo => acc.updated(qo,item._2 :: acc(qo))).getOrElse(acc)
              })
              val identity = "%s%s".format(username,now.toString)
              def genText(text:String,size:Double,offset:Double,identityModifier:String,maxHeight:Option[Double] = None) = MeTLText(serverConfig,username,now,text,maxHeight.getOrElse(size * 2),640,0,10,10 + offset,identity+identityModifier,"Normal","Arial","Normal",size,"none",identity+identityModifier,"presentationSpace",Privacy.PUBLIC,ho.id.toString,Color(255,0,0,0))
              val quizTitle = genText(quiz.question,32,0,"title",Some(100))

              val graphWidth = 640
              val graphHeight = 480
              val bytes = com.metl.renderer.QuizRenderer.renderQuiz(quiz,answers.flatMap(_._2).toList,new com.metl.renderer.RenderDescription(graphWidth,graphHeight))
              val quizGraphIdentity = serverConfig.postResource(jid,"graphResults_%s_%s".format(quizId,now),bytes)
              val quizGraph = MeTLImage(serverConfig,username,now,identity+"resultsGraph",Full(quizGraphIdentity),Empty,Empty,graphWidth,graphHeight,10,100,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"resultsGraph")
              val questionOffset = graphHeight + 100
              val quizOptions = quiz.options.foldLeft(List.empty[MeTLText])((acc,item) => {
                acc ::: List(genText(
                  "%s: %s (%s)".format(item.name,item.text,answers.get(item).map(as => as.length).getOrElse(0)),
                  24,
                  (acc.length * 30) + questionOffset,
                  "option:"+item.name))
              })
              val allStanzas = quiz.url.map(u => List(MeTLImage(serverConfig,username,now,identity+"image",Full(u),Empty,Empty,320,240,330,100,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"image"))).getOrElse(List.empty[MeTLStanza]) ::: quizOptions ::: List(quizTitle,quizGraph)
              allStanzas.foreach(stanza => {
                slideRoom ! LocalToServerMeTLStanza(stanza)
              })
            })
          })
          newC
        }
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("reorderSlidesOfCurrentConversation",List("jid","newSlides"),(args) => {
      val jid = getArgAsString(args(0))
      val newSlides = getArgAsJArray(args(1))
      val c = serverConfig.detailsOfConversation(args(0).toString)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => {
          (newSlides.arr.length == c.slides.length) match {
            case true => serverConfig.reorderSlidesOfConversation(c.jid.toString,newSlides.arr.map(i => serializer.toSlide(i)).toList)
            case false => c
          }
        }
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("getQuizzesForConversation",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val quizzes = getQuizzesForConversation(jid).map(q => serializer.fromMeTLQuiz(q)).toList
      JArray(quizzes)
    },Full(RECEIVE_QUIZZES)),
    ClientSideFunctionDefinition("getResponsesForQuizInConversation",List("conversationJid","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val quizId = getArgAsString(args(1))
      JArray(getQuizResponsesForQuizInConversation(jid,quizId).map(q => serializer.fromMeTLQuizResponse(q)).toList)
    },Full(RECEIVE_QUIZ_RESPONSES)),
    ClientSideFunctionDefinition("answerQuiz",List("conversationJid","quizId","chosenOptionName"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val quizId = getArgAsString(args(1))
      val chosenOptionName = getArgAsString(args(2))
      val response = MeTLQuizResponse(serverConfig,username,new Date().getTime,chosenOptionName,username,quizId)
      rooms.get((server,conversationJid)).map(r => r() ! LocalToServerMeTLStanza(response))
      JNull
    },Empty)
    /*
     ClientSideFunctionDefinition("createQuiz",List("conversationJid","newQuiz"),(args) => {
     JNull
     },Empty),
     */
    /*
    ClientSideFunctionDefinition("requestCreateQuizDialogue",List("conversationJid"),(args) => {
      if (shouldModifyConversation()){
        val conversationJid = getArgAsString(args(0))
        val now = new Date().getTime
        val quiz = MeTLQuiz(serverConfig,username,now,now,"",now.toString,Empty,Empty,false,List("A","B","C").map(o => QuizOption(o,"",false,QuizOption.colorForName(o))))
        this ! editableQuizNodeSeq(quiz,Some("quizId_%s_question".format(now.toString)))
      } else {
        this ! SpamMessage(Text("You are not permitted to create a quiz in this conversation"),Full("quizzes"))
      }
      JNull
    },Empty),
    ClientSideFunctionDefinition("requestDeleteQuizDialogue",List("conversationJid","quizId"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(conversationJid)
      if (shouldModifyConversation(c)){
        val quizId = getArgAsString(args(1))
        rooms.get((server,conversationJid.toString)).map(room => {
          room().getHistory.getQuizByIdentity(quizId).map(quiz => {
            this ! SimpleMultipleButtonInteractableMessage("Delete quiz","Are you sure you would like to delete this poll? \r\n(%s)".format(quiz.question),
              Map(
                "yes" -> {() => {
                  if (shouldModifyConversation(c)){
                    rooms.get((server,conversationJid.toString)).map(rf => {
                      val r = rf()
                      r.getHistory.getQuizByIdentity(quizId).map(q => {
                        val deletedQuiz = q.delete
                        r ! LocalToServerMeTLStanza(deletedQuiz)
                      })
                    })
                    true
                  } else {
                    false
                  }
                }},
                "no" -> {() => true}
              ),Full(()=> this ! SpamMessage(Text("You are not permitted to delete this conversation"))),false,Full("quizzes"))
          })
        })
      }
      JNull
    },Empty),
    ClientSideFunctionDefinition("updateQuiz",List("conversationJid","quizId","updatedQuiz"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(conversationJid)
      if (shouldModifyConversation(c)){
        val quizId = getArgAsString(args(1))
        val newQuizJValue = getArgAsJValue(args(2))
        rooms.get((server,conversationJid.toString)).map(rf => {
          val r = rf()
          r.getHistory.getQuizByIdentity(quizId).map(oq => {
            val newQuiz = serializer.toMeTLQuiz(newQuizJValue)
            val deletedOldQuiz = oq.delete
            if (oq.id == newQuiz.id){
              r ! LocalToServerMeTLStanza(deletedOldQuiz)
              r ! LocalToServerMeTLStanza(newQuiz)
            }
          })
        })
      }
      JNull
    },Empty),
    ClientSideFunctionDefinition("requestUpdateQuizDialogue",List("conversationJid","quizId"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val quizId = getArgAsString(args(1))
      rooms.get((server,conversationJid)).map(r => r().getHistory.getQuizByIdentity(quizId).map(q => this ! editableQuizNodeSeq(q,Some("quizId_%s_question".format(quizId))))).getOrElse({this ! SpamMessage(Text("The quiz you've requested cannot be found at this time"),Full("quizzes"))})
      JNull
    },Empty),
    ClientSideFunctionDefinition("submitScreenshotSubmission",List("conversationJid","slideJid"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val slideJid = getArgAsInt(args(1))
      val now = new Date().getTime
      val pubHistory = rooms.get((server,slideJid.toString)).map(r => r().getHistory).getOrElse(History.empty)
      val privHistory = rooms.get((server,slideJid.toString+username)).map(r => r().getHistory).getOrElse(History.empty)
      val mergedHistory = pubHistory.merge(privHistory)
      val title = "submission%s%s.jpg".format(username,now.toString)

      val width = (mergedHistory.getRight - mergedHistory.getLeft).toInt
      val height = (mergedHistory.getBottom - mergedHistory.getTop).toInt
        (width,height) match {
        case (a:Int,b:Int) if a > 0 && b > 0 => {
          val imageBytes = SlideRenderer.render(mergedHistory,width,height)
          val uri = serverConfig.postResource(conversationJid,title,imageBytes)
          val submission = MeTLSubmission(serverConfig,username,now,title,slideJid,uri)
          rooms.get((server,conversationJid)).map(r =>{
            r() ! LocalToServerMeTLStanza(submission)
          });
          this ! SpamMessage(<div />,Full("submissions"),Full("Screenshot submitted"))
        }
        case _ => {
          this ! SpamMessage(<div />,Full("submissions"),Full("Screenshot was not submitted.  Your canvas is empty."))
        }
      }
      JNull
    },Empty)
    */
  )
  /*
  protected def editableQuizNodeSeq(quiz:MeTLQuiz,nextFocusId:Option[String] = None):InteractableMessage = {
    InteractableMessage(scope = (i) => {
      val quizId = "quizId_%s".format(quiz.id)
      val questionId = "%s_%s".format(quizId,"question")
      var tempQuiz = quiz
      var answerProvided = false
      var errorMessages = List.empty[SpamMessage]
      <div id="createQuizForm">
      <label for="quizQuestion">Question</label>
      <div>
      {
        ajaxTextarea(tempQuiz.question,(input:String) => {
          if (input.length > 0){
            tempQuiz = tempQuiz.replaceQuestion(input)
          } else {
            errorMessages = SpamMessage(Text("Please ensure this poll has a question"),Full("quizzes")) :: errorMessages
          }
        },("class","quizQuestion"),("id",questionId))
      }
      </div>
      {
        tempQuiz.url.map(quizUrl => {
          val imageUrl = "/resourceProxy/%s".format(Helpers.urlEncode(quizUrl))
          <img class="quizImagePreview" src={imageUrl}>This poll has an image</img>
        }).openOr(NodeSeq.Empty)
      }
      <div>
      {
        tempQuiz.options.sortBy(o => o.name).map(qo => {
          <div class="quizOption">
          <label class="quizName">
          {
            qo.name
          }
          </label>
          <div class="flex-container-responsive">
          {
            val quizAnswerId = "%s_%s".format(quizId,qo.name)
            ajaxTextarea(qo.text, (input:String) => {
              if (input.length > 0){
                tempQuiz = tempQuiz.replaceOption(qo.name,input)
              } else {
                errorMessages = SpamMessage(Text("Please ensure that quizOption %s has a description".format(qo.name)),Full("quizzes")) :: errorMessages
              }
            },("class","quizText"),("id",quizAnswerId))
          }
          {
            ajaxButton(<span>{Text("Delete this option")}</span>, () => {
              if (tempQuiz.options.length > 2){
                tempQuiz = tempQuiz.removeOption(qo.name)
                this ! editableQuizNodeSeq(tempQuiz,nextFocusId)
                i.done
              } else {
                this ! SpamMessage(Text("Please ensure that this poll has at least two options"),Full("quizzes"))
                Noop
              }
            },("class","quizRemoveOptionButton toolbar btn-icon fa fa-trash np"))
          }
          </div>
          </div>
        })
      }
      </div>
      {
        ajaxButton(<span>{Text("Add an option")}</span>, ()=>{
          val oldOptions = tempQuiz.options
          tempQuiz = tempQuiz.addOption(QuizOption("",""))
          val newOptionId = tempQuiz.options.find(o => !oldOptions.exists(_.name == o.name)).map(o => "%s_%s".format(quizId,o.name))
          this ! editableQuizNodeSeq(tempQuiz,newOptionId)
          i.done
        },("class","quizAddOptionButton toolbar btn-icon fa fa-plus np"))
      }
      </div>
      <div class="quizCreationControls">
      {
        val quizImageButtonText = tempQuiz.url.map(u => "Update poll image with current slide").openOr("Attach current slide")
        ajaxButton(<span>{Text(quizImageButtonText)}</span>, () => {
          for (
            conversation <- currentConversation;
            slideJid <- currentSlide
          ) yield {
            val conversationJid = conversation.jid.toString
            val now = new Date().getTime
            val mergedHistory = rooms.get((server,slideJid.toString)).map(r => r().getHistory).getOrElse(History.empty)
            val title = "submission%s%s.jpg".format(username,now.toString)
            val width = (mergedHistory.getRight - mergedHistory.getLeft).toInt
            val height = (mergedHistory.getBottom - mergedHistory.getTop).toInt
            val uriBox = (width,height) match {
              case (a:Int,b:Int) if a > 0 && b > 0 => {
                val imageBytes = SlideRenderer.render(mergedHistory,width,height)
                val uri = serverConfig.postResource(conversationJid,title,imageBytes)
                Full(uri)
              }
              case _ => Empty
            }
            val newTempQuiz = tempQuiz.replaceImage(uriBox)
            this ! editableQuizNodeSeq(newTempQuiz,nextFocusId)
            i.done
          }
          Noop
        },("class","quizAttachImageButton toolbar btn-icon fa fa-paperclip"))
      }
      {
        ajaxButton(<span>{Text("Delete this poll")}</span>, ()=>{
          var deletedQuiz = tempQuiz.delete
          sendStanzaToServer(deletedQuiz,server)
          i.done
        },("class","quizDeleteButton toolbar btn-icon fa fa-trash"))
      }
      {ajaxSubmit("Submit", ()=>{
        if (errorMessages.length > 0){
          errorMessages.foreach(em => this ! em)
          errorMessages = List.empty[SpamMessage]
          Noop
        } else {
          sendStanzaToServer(tempQuiz,server)
          i.done
        }
      },("class","quizSubmitButton toolbar button-transparent-border"))}
      </div>
    },role = Full("quizzes"),incomingTitle = Full("Define this poll"),afterLoad = nextFocusId.map(nid => CustomJsCmds.ScrollAndFocus(nid)))
  }
  */
  private def getQuizResponsesForQuizInConversation(jid:String,quizId:String):List[MeTLQuizResponse] = {
    rooms.get((server,jid)).map(r => r().getHistory.getQuizResponses.filter(q => q.id == quizId)).map(allQuizResponses => {
      val conversation = serverConfig.detailsOfConversation(jid)
      shouldModifyConversation(conversation) match {
        case true => allQuizResponses
        case _ => allQuizResponses.filter(qr => qr.answerer == username)
      }
    }).getOrElse(List.empty[MeTLQuizResponse])
  }
  private def getQuizzesForConversation(jid:String):List[MeTLQuiz] = {
    val roomOption = rooms.get((server,jid))
    val res = roomOption.map(r => r().getHistory.getQuizzes).getOrElse(List.empty[MeTLQuiz])
    res
  }

  private var rooms = Map.empty[Tuple2[String,String],() => MeTLRoom]
  private lazy val serverConfig = ServerConfiguration.default
  private lazy val server = serverConfig.name
  debug("serverConfig: %s -> %s".format(server,serverConfig))
  private def username = (for (
    nameString <- name;
    user <- com.metl.snippet.Metl.getUserFromName(nameString)
  ) yield {
    user
  }).getOrElse(Globals.currentUser.is)
  private val serializer = new JsonSerializer("frontend")
  def registerWith = MeTLActorManager
  override def render = {
    OnLoad(refreshClientSideStateJs)
  }
  /*
  private val defaultContainerId  = "s2cMessageContainer"
  private val clientMessageBroker = new ClientMessageBroker(TemplateHolder.clientMessageTemplate,".s2cMessage",".s2cLabel",".s2cContent",".s2cClose",
    (cm) => {
      partialUpdate(SetHtml(defaultContainerId,cm.renderMessage) & Show(defaultContainerId) & Call("reapplyStylingToServerGeneratedContent",JString(cm.uniqueId)) & cm.afterLoad.getOrElse(Noop))
    },
    (cm) => {
      partialUpdate(Hide(defaultContainerId) & cm.done)
    }
  )
  */
  override def lowPriority = {
    case roomInfo:RoomStateInformation => Stopwatch.time("MeTLActor.lowPriority.RoomStateInformation", updateRooms(roomInfo))
    case metlStanza:MeTLStanza => Stopwatch.time("MeTLActor.lowPriority.MeTLStanza", sendMeTLStanzaToPage(metlStanza))
    /*
    case c:ClientMessage => {
      clientMessageBroker.processMessage(c)
    }
    */
    case JoinThisSlide(slide) => moveToSlide(slide)
    case HealthyWelcomeFromRoom => {}
    case other => warn("MeTLActor received unknown message: %s".format(other))
  }
  override def autoIncludeJsonCode = true
  protected var currentConversation:Box[Conversation] = Empty
  protected var currentSlide:Box[String] = Empty
  protected var isInteractiveUser:Box[Boolean] = Empty
  override def localSetup = Stopwatch.time("MeTLActor.localSetup(%s,%s)".format(username,userUniqueId), {
    super.localSetup()
    debug("created metlactor: %s".format(name))
    joinRoomByJid("global")
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      com.metl.snippet.Metl.getConversationFromName(nameString).foreach(convJid => {
        joinConversation(convJid.toString)
      })
      com.metl.snippet.Metl.getSlideFromName(nameString).map(slideJid => {
        moveToSlide(slideJid.toString)
        slideJid
      }).getOrElse({
        currentConversation.foreach(cc => {
          cc.slides.sortWith((a,b) => a.index < b.index).headOption.map(firstSlide => {
            moveToSlide(firstSlide.id.toString)
          })
        })
      })
      isInteractiveUser = Full(com.metl.snippet.Metl.getShowToolsFromName(nameString).getOrElse(true))
    })
  })
  private def joinRoomByJid(jid:String,serverName:String = server) = Stopwatch.time("MeTLActor.joinRoomByJid(%s)".format(jid),{
    rooms.get((serverName,jid)) match {
      case None => RoomJoiner ! RoomJoinRequest(jid,username,serverName,userUniqueId,this)
      case _ => {}
    }
  })
  private def leaveRoomByJid(jid:String,serverName:String = server) = Stopwatch.time("MeTLActor.leaveRoomByJid(%s)".format(jid),{
    rooms.get((serverName,jid)) match {
      case Some(s) => RoomJoiner ! RoomLeaveRequest(jid,username,serverName,userUniqueId,this)
      case _ => {}
    }
  })
  override def localShutdown = Stopwatch.time("MeTLActor.localShutdown(%s,%s)".format(username,userUniqueId),{
    debug("shutdown metlactor: %s".format(name))
    leaveAllRooms(true)
    super.localShutdown()
  })
  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => JObject(List(JField("type",JString(eg._1)),JField("value",JString(eg._2))))).toList)
  private def refreshClientSideStateJs = {
    currentConversation.map(cc => {
      if (!shouldDisplayConversation(cc)){
        warn("refreshClientSideState kicking this cometActor(%s) from the conversation because it's no longer permitted".format(name))
        currentConversation = Empty
        currentSlide = Empty
        partialUpdate(RedirectTo(noBoard))
      }
      val conversationJid = cc.jid.toString
      joinRoomByJid(conversationJid)
      currentSlide.map(cs => {
        joinRoomByJid(cs)
        joinRoomByJid(cs+username)
      })
    })
    debug("Refresh client side state: %s, %s".format(currentConversation,currentSlide))
    val receiveUsername:Box[JsCmd] = Full(Call(RECEIVE_USERNAME,JString(username)))
    debug(receiveUsername)
    val receiveUserGroups:Box[JsCmd] = Full(Call(RECEIVE_USER_GROUPS,getUserGroups))
    debug(receiveUserGroups)
    val receiveCurrentConversation:Box[JsCmd] = currentConversation.map(cc => Call(RECEIVE_CURRENT_CONVERSATION,JString(cc.jid.toString))) match {
      case Full(cc) => Full(cc)
      case _ => Full(Call("showBackstage",JString("conversations")))
    }
    debug(receiveCurrentConversation)
    val receiveConversationDetails:Box[JsCmd] = currentConversation.map(cc => Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(cc)))
    debug(receiveConversationDetails)
    val receiveCurrentSlide:Box[JsCmd] = currentSlide.map(cc => Call(RECEIVE_CURRENT_SLIDE, JString(cc)))
    debug(receiveCurrentSlide)
    val receiveLastSyncMove:Box[JsCmd] = currentConversation.map(cc => {
      debug("receiveLastSyncMove attempting to get room %s, %s".format(cc,server))
      val room = MeTLXConfiguration.getRoom(cc.jid.toString,server)
      debug("receiveLastSyncMove: %s".format(room))
      val history = room.getHistory
      debug("receiveLastSyncMove: %s".format(history))
      history.getLatestCommands.get("/SYNC_MOVE") match{
        case Some(lastSyncMove) =>{
          debug("receiveLastSyncMove found move: %s".format(lastSyncMove))
          Call(RECEIVE_SYNC_MOVE,JString(lastSyncMove.commandParameters(0).toString))
        }
        case _ =>{
          debug("receiveLastSyncMove no move found")
          Noop
        }
      }
    })
    debug(receiveLastSyncMove)
    val receiveHistory:Box[JsCmd] = currentSlide.map(cc => Call(RECEIVE_HISTORY,getSlideHistory(cc)))
    val receiveInteractiveUser:Box[JsCmd] = isInteractiveUser.map(iu => Call(RECEIVE_IS_INTERACTIVE_USER,JBool(iu)))
    debug(receiveInteractiveUser)

    val jsCmds:List[Box[JsCmd]] = List(receiveUsername,receiveUserGroups,receiveCurrentConversation,receiveConversationDetails,receiveCurrentSlide,receiveLastSyncMove,receiveHistory,receiveInteractiveUser)
    jsCmds.foldLeft(Noop)((acc,item) => item.map(i => acc & i).openOr(acc))
  }
  private def joinConversation(jid:String):Box[Conversation] = {
    val details = serverConfig.detailsOfConversation(jid)
    leaveAllRooms()
    debug("joinConversation: %s".format(details))
    if (shouldDisplayConversation(details)){
      debug("conversation available")
      currentConversation = Full(details)
      val conversationJid = details.jid.toString
      joinRoomByJid(conversationJid)
      //      rooms.get((server,"global")).foreach(r => r ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,conversationJid,true,Nil)))
      //joinRoomByJid(conversationJid,"loopback")
      currentConversation
    } else {
      debug("conversation denied: %s, %s.".format(jid,details.subject))
      warn("joinConversation kicking this cometActor(%s) from the conversation because it's no longer permitted".format(name))
      currentConversation = Empty
      currentSlide = Empty
      reRender// partialUpdate(RedirectTo(noBoard))
      Empty
    }
  }
  private def getSlideHistory(jid:String):JValue = {
    debug("GetSlideHistory %s".format(jid))
    val convHistory = currentConversation.map(cc => MeTLXConfiguration.getRoom(cc.jid.toString,server).getHistory).openOr(History.empty)
    debug("conv %s".format(jid))
    val pubHistory = MeTLXConfiguration.getRoom(jid,server).getHistory
    debug("pub %s".format(jid))
    val privHistory = isInteractiveUser.map(iu => if (iu){
      MeTLXConfiguration.getRoom(jid+username,server).getHistory
    } else {
      History.empty
    }).openOr(History.empty)
    debug("priv %s".format(jid))
    val finalHistory = pubHistory.merge(privHistory).merge(convHistory)
    debug("final %s".format(jid))
    serializer.fromHistory(finalHistory)
  }
  private def conversationContainsSlideId(c:Conversation,slideId:Int):Boolean = c.slides.exists((s:Slide) => s.id == slideId)
  private def moveToSlide(jid:String):Unit = {
    debug("moveToSlide {0}".format(jid))
    debug("CurrentConversation".format(currentConversation))
    debug("CurrentSlide".format(currentSlide))
    val slideId = jid.toInt
    currentSlide.filterNot(_ == jid).map(cs => {
      currentConversation.filter(cc => conversationContainsSlideId(cc,slideId)).map(cc => {
        debug("Don't have to leave conversation, current slide is in it")
        //        rooms.get((server,cc.jid.toString)).foreach(r => r ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,cs,false,Nil)))
      }).getOrElse({
        debug("Joining conversation for: %s".format(slideId))
        joinConversation(serverConfig.getConversationForSlide(jid))
      })
      leaveRoomByJid(cs)
      leaveRoomByJid(cs+username)
    })
    currentConversation.getOrElse({
      debug("Joining conversation for: %s".format(slideId))
      joinConversation(serverConfig.getConversationForSlide(jid))
    })
    currentConversation.map(cc => {
      debug("checking to see that current conv and current slide now line up")
      if (conversationContainsSlideId(cc,slideId)){
        debug("conversation contains slide")
        currentSlide = Full(jid)
        if (cc.author.trim.toLowerCase == username.trim.toLowerCase && isInteractiveUser.map(iu => iu == true).getOrElse(true)){
          val syncMove = MeTLCommand(serverConfig,username,new Date().getTime,"/SYNC_MOVE",List(jid))
          rooms.get((server,cc.jid.toString)).map(r => r() ! LocalToServerMeTLStanza(syncMove))
        }
        joinRoomByJid(jid)
        joinRoomByJid(jid+username)
      }
    })
    partialUpdate(refreshClientSideStateJs)
  }
  private def leaveAllRooms(shuttingDown:Boolean = false) = {
    debug("leaving all rooms: %s".format(rooms))
    rooms.foreach(r => {
      if (shuttingDown || (r._1._2 != username && r._1._2 != "global")){
        debug("leaving room: %s".format(r))
        r._2() ! LeaveRoom(username,userUniqueId,this)
      }
    })
  }
  override def lifespan = Full(2 minutes)

  private def updateRooms(roomInfo:RoomStateInformation):Unit = Stopwatch.time("MeTLActor.updateRooms",{
    debug("roomInfo received: %s".format(roomInfo))
    debug("updateRooms: %s".format(roomInfo))
    roomInfo match {
      case RoomJoinAcknowledged(s,r) => {
        debug("joining room: %s".format(r))
        rooms = rooms.updated((s,r),() => MeTLXConfiguration.getRoom(r,s))
        try {
          val slideNum = r.toInt
          val conv = serverConfig.getConversationForSlide(r)
          debug("trying to send truePresence to room: %s %s".format(conv,slideNum))
          if (conv != r){
            val room = MeTLXConfiguration.getRoom(conv.toString,s,ConversationRoom(server,conv.toString))
            room !  LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,slideNum.toString,true,Nil))
          } else {
            val room = MeTLXConfiguration.getRoom("global",s,GlobalRoom(server))
            room ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,conv.toString,true,Nil))
          }
        } catch {
          case e:Exception => {
          }
        }
      }
      case RoomLeaveAcknowledged(s,r) => {
        debug("leaving room: %s".format(r))
        try {
          val slideNum = r.toInt
          val conv = serverConfig.getConversationForSlide(r)
          debug("trying to send falsePresence to room: %s %s".format(conv,slideNum))
          if (conv != r){
            val room = MeTLXConfiguration.getRoom(conv.toString,s,ConversationRoom(server,conv.toString))
            room !  LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,slideNum.toString,false,Nil))
          } else {
            val room = MeTLXConfiguration.getRoom("global",s,GlobalRoom(server))
            room ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,conv.toString,false,Nil))
          }
        } catch {
          case e:Exception => {
          }
        }
        rooms = rooms.filterNot(rm => rm._1 == (s,r))
      }
      case _ => {}
    }
  })
  private def sendStanzaToServer(jVal:JValue,serverName:String = server):Unit  = Stopwatch.time("MeTLActor.sendStanzaToServer (jVal) (%s)".format(serverName),{
    val metlData = serializer.toMeTLData(jVal)
    metlData match {
      case m:MeTLStanza => sendStanzaToServer(m,serverName)
      case notAStanza => error("Not a stanza at sendStanzaToServer %s".format(notAStanza))
    }
  })
  private def sendStanzaToServer(stanza:MeTLStanza,serverName:String):Unit  = Stopwatch.time("MeTLActor.sendStanzaToServer (MeTLStanza) (%s)".format(serverName),{
    trace("OUT -> %s".format(stanza))
    stanza match {
      case m:MeTLMoveDelta => {
        val publicRoom = rooms.getOrElse((serverName,m.slide),() => EmptyRoom)()
        val privateRoom = rooms.getOrElse((serverName,m.slide+username),() => EmptyRoom)()
        val publicHistory = publicRoom.getHistory
        val privateHistory = privateRoom.getHistory
        val (sendToPublic,sendToPrivates) = m.adjustTimestamp(List(privateHistory.getLatestTimestamp,publicHistory.getLatestTimestamp).max + 1).generateChanges(publicHistory,privateHistory)
        sendToPublic.map(pub => {
          trace("OUT TO PUB -> %s".format(pub))
          publicRoom ! LocalToServerMeTLStanza(pub)
        })
        sendToPrivates.foreach(privTup => {
          val privateAuthor = privTup._1
          if (username == privateAuthor || shouldModifyConversation()){
            val privRoom = MeTLXConfiguration.getRoom(m.slide+privateAuthor,server) // rooms.getOrElse((serverName,m.slide+privateAuthor),() => EmptyRoom)()
              privTup._2.foreach(privStanza => {
                trace("OUT TO PRIV -> %s".format(privStanza))
                privRoom ! LocalToServerMeTLStanza(privStanza)
              })
          }
        })
      }
      case s:MeTLSubmission => {
        if (s.author == username) {
          currentConversation.map(cc => {
            val roomId = cc.jid.toString
            rooms.get((serverName,roomId)).map(r =>{
              debug("sendStanzaToServer sending submission: "+r)
              r() ! LocalToServerMeTLStanza(s)
            })
          })
        }
      }
      case qr:MeTLQuizResponse => {
        if (qr.author == username) {
          currentConversation.map(cc => {
            val roomId = cc.jid.toString
            rooms.get((serverName,roomId)).map(r => r() ! LocalToServerMeTLStanza(qr))
          })
        }
      }
      case q:MeTLQuiz => {
        if (q.author == username) {
          currentConversation.map(cc => {
            if (shouldModifyConversation(cc)){
              debug("sending quiz: %s".format(q))
              val roomId = cc.jid.toString
              rooms.get((serverName,roomId)).map(r => r() ! LocalToServerMeTLStanza(q))
            } else this ! SpamMessage(Text("You are not permitted to create quizzes in this conversation"),Full("quizzes"))
          })
        }
      }
      case c:MeTLCanvasContent => {
        if (c.author == username){
          currentConversation.map(cc => {
            val (shouldSend,roomId,finalItem) = c.privacy match {
              case Privacy.PRIVATE => (true,c.slide+username,c)
              case Privacy.PUBLIC => {
                if (shouldPublishInConversation(cc)){
                  (true,c.slide,c)
                } else {
                  (true,c.slide+username,c match {
                    case i:MeTLInk => i.alterPrivacy(Privacy.PRIVATE)
                    case t:MeTLText => t.alterPrivacy(Privacy.PRIVATE)
                    case i:MeTLImage => i.alterPrivacy(Privacy.PRIVATE)
                    case i:MeTLMultiWordText => i.alterPrivacy(Privacy.PRIVATE)
                    case di:MeTLDirtyInk => di.alterPrivacy(Privacy.PRIVATE)
                    case dt:MeTLDirtyText => dt.alterPrivacy(Privacy.PRIVATE)
                    case di:MeTLDirtyImage => di.alterPrivacy(Privacy.PRIVATE)
                    case other => other
                  })
                }
              }
              case other => {
                warn("unexpected privacy found in: %s".format(c))
                (false,c.slide,c)
              }
            }
            if (shouldSend){
              rooms.get((serverName,roomId)).map(targetRoom => targetRoom() ! LocalToServerMeTLStanza(finalItem))
            }
          })
        } else warn("attemped to send a stanza to the server which wasn't yours: %s".format(c))
      }
      case c:MeTLCommand => {
        if (c.author == username){
          val conversationSpecificCommands = List("/SYNC_MOVE","/TEACHER_IN_CONVERSATION")
          val slideSpecificCommands = List("/TEACHER_VIEW_MOVED")
          val roomTarget = c.command match {
            case s:String if (conversationSpecificCommands.contains(s)) => currentConversation.map(_.jid.toString).getOrElse("global")
            case s:String if (slideSpecificCommands.contains(s)) => currentSlide.getOrElse("global")
            case _ => "global"
          }
          rooms.get((serverName,roomTarget)).map(r => {
            trace("sending MeTLStanza to room: %s <- %s".format(r,c))
            r() ! LocalToServerMeTLStanza(c)
          })
        }
      }
      case f:MeTLFile => {
        if (f.author == username){
          currentConversation.map(cc => {
            val roomTarget = cc.jid.toString
            rooms.get((serverName,roomTarget)).map(r => {
              trace("sending MeTLFile to conversation room: %s <- %s".format(r,f))
              r() ! LocalToServerMeTLStanza(f)
            })
          })
        }
      }
      case other => {
        warn("sendStanzaToServer's toMeTLStanza returned unknown type when deserializing: %s".format(other))
      }
    }
  })
  private def sendMeTLStanzaToPage(metlStanza:MeTLStanza):Unit = Stopwatch.time("MeTLActor.sendMeTLStanzaToPage",{
    trace("IN -> %s".format(metlStanza))
    metlStanza match {
      case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") => {
        val newJid = c.commandParameters(0).toInt
        val newConv = serverConfig.detailsOfConversation(newJid.toString)
        if (currentConversation.exists(_.jid == newConv.jid)){
          if (!shouldDisplayConversation(newConv)){
            warn("sendMeTLStanzaToPage kicking this cometActor(%s) from the conversation because it's no longer permitted".format(name))
            currentConversation = Empty
            currentSlide = Empty
            reRender
            partialUpdate(RedirectTo(noBoard))
          } else {
            currentConversation = currentConversation.map(cc => {
              if (cc.jid == newJid){
                newConv
              } else cc
            })
            debug("updating conversation to: %s".format(newConv))
            partialUpdate(Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(newConv)))
          }
        }
      }
      case c:MeTLCommand if (c.command == "/SYNC_MOVE") => {
        debug("incoming syncMove: %s".format(c))
        val newJid = c.commandParameters(0).toInt
        partialUpdate(Call(RECEIVE_SYNC_MOVE,newJid))
      }
      case c:MeTLCommand if (c.command == "/TEACHER_IN_CONVERSATION") => {
        //not relaying teacherInConversation to page
      }
      case a:Attendance => {
        //not relaying to page yet, because we're not using them in the webmetl client yet
      }
      case _ => {
        trace("receiving: %s".format(metlStanza))
        val response = serializer.fromMeTLData(metlStanza) match {
          case j:JValue => j
          case other => JString(other.toString)
        }
        partialUpdate(Call(RECEIVE_METL_STANZA,response))
      }
    }
  })
  private def shouldModifyConversation(c:Conversation = currentConversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldModifyConversation(username,c)
  private def shouldDisplayConversation(c:Conversation = currentConversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldDisplayConversation(c)
  private def shouldPublishInConversation(c:Conversation = currentConversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldPublishInConversation(username,c)
}
