package com.metl.comet

import com.metl.data.{GroupSet => MeTLGroupSet, _}
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

import com.metl.external._
import com.metl.renderer.SlideRenderer
import json.JsonAST._
import com.metl.snippet.Metl._

case class JoinThisSlide(slide:String)

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
  protected def refreshForeignRelationship(c:Conversation,me:String,myGroups:List[OrgUnit]):Conversation = {
    (for {
      fr <- c.foreignRelationship
      newFr <- Globals.casState.is.eligibleGroups.find(g => g.foreignRelationship.exists(gfr => gfr.key == fr.key && gfr.system == fr.system)).flatMap(_.foreignRelationship)
    } yield {
      c.copy(foreignRelationship = Some(newFr))
    }).getOrElse(c)
  }
  protected def conversationFilterFunc(c:Conversation,me:String,myGroups:List[OrgUnit],includeDeleted:Boolean = false):Boolean = {
    val subject = c.subject.trim.toLowerCase
    val author = c.author.trim.toLowerCase
    com.metl.snippet.Metl.shouldDisplayConversation(c,includeDeleted)
  }
  def filterConversations(in:List[Conversation],includeDeleted:Boolean = false):List[Conversation] = {
    lazy val me = Globals.currentUser.is.toLowerCase.trim
    lazy val myGroups = Globals.casState.is.eligibleGroups.toList
    in.groupBy(_.jid).flatMap{
      case (jid,result :: _) => Some(result)
      case _ => None
    }.toList.filter(c => conversationFilterFunc(c,me,myGroups,includeDeleted))
  }
}

class RemotePluginConversationChooserActor extends MeTLConversationChooserActor {
  protected var ltiIntegration:Option[LtiIntegration] = None
  protected var prefix:String = "remotePlugin"
  protected var ltiToken:Option[String] = None
  protected var ltiSession:Option[RemotePluginSession] = None
  override def lifespan = Globals.remotePluginConversationChooserActorLifespan
  override def localSetup = {
    super.localSetup
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      getLtiIdFromName(nameString).map(ltiId => ltiIntegration = Globals.ltiIntegrationPlugins.find(_.id == ltiId))
      ltiIntegration.map(lti => prefix = lti.getEndpointPrefix)
      ltiToken = com.metl.snippet.Metl.getLtiTokenFromName(nameString)
      ltiSession = ltiToken.flatMap(token => ltiIntegration.flatMap(_.sessionStore.is.get(token)))
    })
  }
  override def perConversationAction(conv:Conversation) = {
    ".conversationAnchor [href]" #> ltiToken.map(lti => remotePluginChoseConversation(prefix,lti,conv.jid)) &
    ".conversationTitle *" #> conv.title &
    ".conversationAuthor *" #> conv.author &
    ".conversationJid *" #> conv.jid &
    ".conversationEditingContainer" #> {
      shouldModifyConversation(Globals.currentUser.is,conv) match {
        case true => ".editConversationLink [href]" #> editConversation(conv.jid)
        case false => ".conversationEditingContainer" #> NodeSeq.Empty
      }
    } 
  }
  override def perImportAction(conv:Conversation) = {
    ".importSuccess [href]" #> ltiToken.map(lti => remotePluginChoseConversation(prefix,lti,conv.jid))
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
  private val serializer = new JsonSerializer(ServerConfiguration.default)

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

  protected implicit val formats = net.liftweb.json.DefaultFormats
  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => net.liftweb.json.Extraction.decompose(eg)))//JObject(List(JField("type",JString(eg.ouType)),JField("value",JString(eg.name))))).toList)
  override lazy val functionDefinitions = List(
    /*
     ClientSideFunction("getPresentUsers",List.empty[String],(args) => {
     rooms.getOrElse((serverName,m.slide),() => EmptyRoom)()
     },Full(RECEIVE_PRESENT_USERS)),
     */
    ClientSideFunction("getUserGroups",List.empty[String],(args) => getUserGroups,Full(RECEIVE_USER_GROUPS)),
    ClientSideFunction("getUser",List.empty[String],(unused) => JString(username),Full(RECEIVE_USERNAME)),
    ClientSideFunction("getSearchResult",List("query"),(args) => {
      val q = getArgAsString(args(0)).toLowerCase.trim
      query = Some(q)
      val foundConversations = serverConfig.searchForConversation(q)
      listing = filterConversations(foundConversations,true)
      //debug(listing.toString())
      trace("searchingWithQuery: %s => %s : %s".format(query,foundConversations.length,listing.length))
      serializer.fromConversationList(listing)
    },Full(RECEIVE_CONVERSATIONS)),
    ClientSideFunction("createConversation",List("title"),(args) => {
      val title = getArgAsString(args(0))
      warn("Creating conversation: %s".format(title))
      val newConv = serverConfig.createConversation(title,username)
      listing = (newConv :: listing).distinct
      serializer.fromConversation(newConv)
    },Full(RECEIVE_NEW_CONVERSATION_DETAILS))/*,
    ClientSideFunction("sleepAndThenAlert",List("delay","message"),(args) => {
      val delay = getArgAsInt(args(0))
      val message = getArgAsString(args(1))
      Thread.sleep(delay)
      JString(message)
    },Full("alert"))*/
  )

  protected var query:Option[String] = None
  protected var listing:List[Conversation] = Nil
  protected var imports:List[ImportDescription] = Nil

  override def registerWith = MeTLConversationSearchActorManager
  override def lifespan = Globals.searchActorLifespan
  protected val username = Globals.currentUser.is
  protected lazy val serverConfig = ServerConfiguration.default

  override def localSetup = {
    warn("localSetup for ConversationSearch [%s]".format(name))
    query = Some(name.flatMap(nameString => {
      com.metl.snippet.Metl.getQueryFromName(nameString)
    }).getOrElse(username.toLowerCase.trim))
    listing = query.toList.flatMap(q => filterConversations(serverConfig.searchForConversation(q),true))
    super.localSetup
  }
  override def render = OnLoad(
    Call(RECEIVE_USERNAME,JString(username)) &
      Call(RECEIVE_USER_GROUPS,getUserGroups) &
      Call(RECEIVE_QUERY,JString(query.getOrElse(""))) &
      Call(RECEIVE_CONVERSATIONS,serializer.fromConversationList(listing.map(c => refreshForeignRelationship(c,username,Globals.getUserGroups)))) &
      Call(RECEIVE_IMPORT_DESCRIPTIONS,JArray(imports.map(serialize _)))
  )
  protected def serialize(id:ImportDescription):JValue = net.liftweb.json.Extraction.decompose(id)(net.liftweb.json.DefaultFormats);

  protected def queryApplies(in:Conversation):Boolean = query.map(q => q.toLowerCase.trim).map(q => in.title.toLowerCase.trim.contains(q) || in.author.toLowerCase.trim == q || in.jid.toString == q).getOrElse(false)

  override protected def conversationFilterFunc(c:Conversation,me:String,myGroups:List[OrgUnit],includeDeleted:Boolean = false):Boolean = super.conversationFilterFunc(c,me,myGroups,includeDeleted) && queryApplies(c)

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
      val newConv = refreshForeignRelationship(serverConfig.detailsOfConversation(newJid.toString),username,Globals.getUserGroups)
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
  private val serializer = new JsonSerializer(ServerConfiguration.default)
  implicit def jeToJsCmd(in:JsExp):JsCmd = in.cmd
  override def autoIncludeJsonCode = true

  private lazy val RECEIVE_USERNAME = "receiveUsername"
  private lazy val RECEIVE_CONVERSATIONS = "receiveConversations"
  private lazy val RECEIVE_IMPORT_DESCRIPTION = "receiveImportDescription"
  private lazy val RECEIVE_USER_GROUPS = "receiveUserGroups"
  private lazy val RECEIVE_CONVERSATION_DETAILS = "receiveConversationDetails"
  private lazy val RECEIVE_NEW_CONVERSATION_DETAILS = "receiveNewConversationDetails"

  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => JObject(List(
    JField("type",JString(eg.ouType)),
    JField("value",JString(eg.name))
  ) ::: eg.foreignRelationship.toList.map(fr => {
    JField("foreignRelationship",JObject(List(
      JField("system",JString(fr.system)),
      JField("key",JString(fr.key))
    )))
  }))).toList)
  override lazy val functionDefinitions = List(
    ClientSideFunction("getUserGroups",List.empty[String],(args) => getUserGroups,Full(RECEIVE_USER_GROUPS)),
    ClientSideFunction("getUser",List.empty[String],(unused) => JString(username),Full(RECEIVE_USERNAME)),
    ClientSideFunction("getSearchResult",List("query"),(args) => {
      serializer.fromConversationList(filterConversations(serverConfig.searchForConversation(getArgAsString(args(0)).toLowerCase.trim)))
    },Full(RECEIVE_CONVERSATIONS))
  )

  override def registerWith = MeTLConversationSearchActorManager
  override def lifespan = Globals.conversationChooserActorLifespan
  protected val username = Globals.currentUser.is
  protected lazy val serverConfig = ServerConfiguration.default
  protected var query:Option[String] = None
  protected var listing:List[Conversation] = Nil
  override def localSetup = {
    super.localSetup
    query = Some(Globals.currentUser.is)
    listing = query.map(q => filterConversations(serverConfig.searchForConversation(q)).map(c => refreshForeignRelationship(c,username,Globals.getUserGroups))).getOrElse(Nil)
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
      trace("receivedCommand: %s".format(c))
      val newJid = c.commandParameters(0).toInt
      val newConv = refreshForeignRelationship(serverConfig.detailsOfConversation(newJid.toString),username,Globals.getUserGroups)
      if (queryApplies(newConv) && shouldDisplayConversation(newConv)){
        listing = newConv :: listing.filterNot(_.jid == newConv.jid)
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
  import net.liftweb.json.Extraction
  import net.liftweb.json.DefaultFormats
  private val serializer = new JsonSerializer(ServerConfiguration.default)
  implicit def jeToJsCmd(in:JsExp):JsCmd = in.cmd
  override def autoIncludeJsonCode = true
  private lazy val RECEIVE_USERNAME = "receiveUsername"
  private lazy val RECEIVE_USER_GROUPS = "receiveUserGroups"
  private lazy val RECEIVE_CONVERSATION_DETAILS = "receiveConversationDetails"
  private lazy val RECEIVE_NEW_CONVERSATION_DETAILS = "receiveNewConversationDetails"
  private lazy val RECEIVE_SHOW_LINKS = "receiveShowLinks"
  implicit val formats = net.liftweb.json.DefaultFormats
  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => net.liftweb.json.Extraction.decompose(eg)))
  override lazy val functionDefinitions = List(
    ClientSideFunction("reorderSlidesOfCurrentConversation",List("jid","newSlides"),(args) => {
      val jid = getArgAsString(args(0))
      val newSlides = getArgAsJArray(args(1))
      trace("reorderSlidesOfConversation(%s,%s)".format(jid,newSlides))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
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
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("deleteConversation",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          trace("deleting conversation %s".format(c.jid))
          serverConfig.deleteConversation(c.jid.toString)
        }
        case _ => {
          trace("refusing to delete conversation %s".format(c.jid))
          c
        }
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("renameConversation",List("jid","newTitle"),(args) => {
      val jid = getArgAsString(args(0))
      val newTitle = getArgAsString(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.renameConversation(c.jid.toString,newTitle)
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("changeSubjectOfConversation",List("conversationJid","newSubject","newRelationshipSystem","newRelationshipKey"),(args) => {
      val jid = getArgAsString(args(0))
      val newSubject = getArgAsString(args(1))
      val newRelationshipSystem = tryo(getArgAsString(args(2)))
      val newRelationshipKey = tryo(getArgAsString(args(3)))
      var c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(if (shouldModifyConversation(c) && (newSubject.toLowerCase == "unrestricted" || newSubject.toLowerCase == username || Globals.getUserGroups.exists(_.name == newSubject))){
        var newRelationship = for {
          sys <- newRelationshipSystem
          key <- newRelationshipKey
          if (sys != null && sys != "")
          if (key != null && key != "")
          newFr <- Globals.casState.is.eligibleGroups.find(g => g.foreignRelationship.exists(fr => fr.key == key && fr.system == sys)).flatMap(_.foreignRelationship)
        } yield {
          newFr
        }
        var newC = c.replaceSubject(newSubject).setForeignRelationship(newRelationship)
        serverConfig.updateConversation(c.jid.toString,newC)
      } else {
        c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("addSlideToConversationAtIndex",List("jid","index"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("duplicateSlideById",List("jid","slideId"),(args) => {
      val jid = getArgAsString(args(0))
      val slideId = getArgAsInt(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => StatelessHtml.duplicateSlideInternal(username,slideId.toString,c.jid.toString).getOrElse(c)
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("duplicateConversation",List("jid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => StatelessHtml.duplicateConversationInternal(username,c.jid.toString).openOr(c)
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_NEW_CONVERSATION_DETAILS)),
    ClientSideFunction("changeExposureOfSlide",List("jid","slideId","exposed"),(args) => {
      val jid = getArgAsString(args(0))
      val slideId = getArgAsInt(args(1))
      val exposed = getArgAsBool(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          serverConfig.updateConversation(c.jid.toString,c.copy(slides = c.slides.find(_.id == slideId).map(_.copy(exposed = exposed)).toList ::: c.slides.filterNot(_.id == slideId)))
        }
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS))
  )
  override def registerWith = MeTLEditConversationActorManager
  override def lifespan = Globals.editConversationActorLifespan
  protected lazy val serverConfig = ServerConfiguration.default
  protected var conversation:Option[Conversation] = None
  protected val username = Globals.currentUser.is
  protected var showLinks = true
  override def localSetup = {
    super.localSetup
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      conversation = com.metl.snippet.Metl.getConversationFromName(nameString).map(jid => refreshForeignRelationship(serverConfig.detailsOfConversation(jid.toString),username,Globals.getUserGroups))
      showLinks = com.metl.snippet.Metl.getLinksFromName(nameString).getOrElse(true)
    })
  }

  override def render = {
    OnLoad(conversation.filter(c => shouldModifyConversation(c)).map(c => {
      Call(RECEIVE_USERNAME,JString(username)) &
      Call(RECEIVE_USER_GROUPS,getUserGroups) &
      Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(c,username,Globals.getUserGroups))) &
      Call(RECEIVE_SHOW_LINKS,showLinks)
    }).getOrElse(RedirectTo(conversationSearch())))
  }
  override def lowPriority = {
    case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") && c.commandParameters.headOption.exists(cid => conversation.exists(_.jid.toString == cid.toString))  => {
      conversation.foreach(c => {
        val newConv = refreshForeignRelationship(serverConfig.detailsOfConversation(c.jid.toString),username,Globals.getUserGroups)
        trace("receivedUpdatedConversation: %s => %s".format(c,newConv))
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
  protected def getArgAsLong(input:Any):Long = input match {
    case JInt(i) => i.toLong
    case i:Int => i.toLong
    case JNum(n) => n.toLong
    case d:Double => d.toLong
    case s:String => s.toLong
    case other => other.toString.toLong
  }
  protected def getArgAsJValue(input:Any):JValue = input match {
    case jv:JValue => jv
    case other => JNull
  }
  protected def getArgAsJArray(input:Any):JArray = input match {
    case l:List[JValue] => JArray(l)
    case ja:JArray => ja
    case other => {
      trace("getArgAsJArray(%s) => %s".format(input,other))
      JArray(List.empty[JValue])
    }
  }
  protected def getArgAsListOfStrings(input:Any):List[String] = input match {
    case JArray(items) => items.flatMap{
      case JString(s) => Some(s)
      case _ => None
    }
    case _ => Nil
  }
}

class MeTLActor extends StronglyTypedJsonActor with Logger with JArgUtils with ConversationFilter {
  import net.liftweb.json.Extraction
  import net.liftweb.json.DefaultFormats
  implicit val formats = net.liftweb.json.DefaultFormats
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
  private lazy val RECEIVE_ATTENDANCE = "receiveAttendance"
  private lazy val UPDATE_THUMB = "updateThumb"
  private lazy val RECEIVE_TOK_BOX_ENABLED = "receiveTokBoxEnabled"
  private lazy val RECEIVE_TOK_BOX_SESSION_TOKEN = "receiveTokBoxSessionToken"
  private lazy val REMOVE_TOK_BOX_SESSIONS = "removeTokBoxSessions"
  private lazy val RECEIVE_TOK_BOX_ARCHIVES = "receiveTokBoxArchives"
  private lazy val RECEIVE_TOK_BOX_BROADCAST = "receiveTokBoxBroadcast"
  protected var tokSessions:scala.collection.mutable.HashMap[String,Option[TokBoxSession]] = new scala.collection.mutable.HashMap[String,Option[TokBoxSession]]()
  protected var tokSlideSpecificSessions:scala.collection.mutable.HashMap[String,Option[TokBoxSession]] = new scala.collection.mutable.HashMap[String,Option[TokBoxSession]]()
  override lazy val functionDefinitions = List(
    ClientSideFunction("getTokBoxArchives",List.empty[String],(args) => {
      JArray(for {
        tb <- Globals.tokBox.toList
        s <- tokSessions.toList.flatMap(_._2)
        a <- tb.getArchives(s)
      } yield {
        JObject(
          List(
            JField("id",JString(a.id)),
            JField("name",JString(a.name))
          ) :::
            a.url.toList.map(u => JField("url",JString(u))) :::
            a.size.toList.map(s => JField("size",JInt(s))) :::
            a.duration.toList.map(d => JField("size",JInt(d))) :::
            a.createdAt.toList.map(c => JField("created",JInt(c)))
        )
      })
    },Full(RECEIVE_TOK_BOX_ARCHIVES)),
    ClientSideFunction("getTokBoxArchive",List("id"),(args) => {
      val id = getArgAsString(args(0))
      JArray((for {
        tb <- Globals.tokBox
        s <- tokSessions.toList.flatMap(_._2).headOption
        a <- tb.getArchive(s,id)
      } yield {
        Extraction.decompose(a)
      }).toList)
    },Full(RECEIVE_TOK_BOX_ARCHIVES)),
    ClientSideFunction("removeTokBoxArchive",List("id"),(args) => {
      val id = getArgAsString(args(0))
      JArray((for {
        tb <- Globals.tokBox
        s <- tokSessions.toList.flatMap(_._2).headOption
      } yield {
        val a = tb.removeArchive(s,id)
        JObject(List(
          JField("session",Extraction.decompose(s)),
          JField("success",JBool(a))
        ))
      }).toList)
    },None),
    ClientSideFunction("startBroadcast",List("layout"),(args) => {
      val layout = getArgAsString(args(0))
        (for {
          tb <- Globals.tokBox
          if (shouldModifyConversation())
          s <- tokSessions.toList.flatMap(_._2).headOption
          b = tb.startBroadcast(s,layout)
        } yield {
          Extraction.decompose(b)
        }).getOrElse(JNull)
    },Full(RECEIVE_TOK_BOX_BROADCAST)),
    ClientSideFunction("updateBroadcastLayout",List("id","newLayout"),(args) => {
      val id = getArgAsString(args(0))
      val layout = getArgAsString(args(1))
        (for {
          tb <- Globals.tokBox
          if (shouldModifyConversation())
          s <- tokSessions.toList.flatMap(_._2).headOption
          a = tb.updateBroadcast(s,id,layout)
        } yield {
          Extraction.decompose(a)
        }).getOrElse(JNull)
    },Full(RECEIVE_TOK_BOX_BROADCAST)),
    ClientSideFunction("stopBroadcast",List.empty[String],(args) => {
      (for {
        tb <- Globals.tokBox
        if (shouldModifyConversation())
        s <- tokSessions.toList.flatMap(_._2).headOption
        b <- tb.getBroadcast(s)
        a = tb.stopBroadcast(s,b.id)
      } yield {
        Extraction.decompose(a)
      }).getOrElse(JNull)
    },Full(RECEIVE_TOK_BOX_BROADCAST)),
    ClientSideFunction("getBroadcast",List.empty[String],(args) => {
      (for {
        tb <- Globals.tokBox
        s <- tokSessions.toList.flatMap(_._2).headOption
        a <- tb.getBroadcast(s)
      } yield {
        Extraction.decompose(a)
      }).getOrElse(JNull)
    },Full(RECEIVE_TOK_BOX_BROADCAST)),
    ClientSideFunction("refreshClientSideState",List.empty[String],(args) => {
      partialUpdate(refreshClientSideStateJs(true))
      JNull
    },Empty),
    ClientSideFunction("getHistory",List("slide"),(args)=> {
      val jid = getArgAsString(args(0))
      trace("getHistory requested")
      getSlideHistory(jid)
    },Full(RECEIVE_HISTORY)),
    ClientSideFunction("getSearchResult",List("query"),(args) => {
      serializer.fromConversationList(filterConversations(serverConfig.searchForConversation(getArgAsString(args(0)).toLowerCase.trim)))
    },Full(RECEIVE_CONVERSATIONS)),
    ClientSideFunction("getIsInteractiveUser",List.empty[String],(args) => isInteractiveUser.map(iu => JBool(iu)).openOr(JBool(true)),Full(RECEIVE_IS_INTERACTIVE_USER)),
    ClientSideFunction("setIsInteractiveUser",List("isInteractive"),(args) => {
      val isInteractive = getArgAsBool(args(0))
      isInteractiveUser = Full(isInteractive)
      isInteractiveUser.map(iu => JBool(iu)).openOr(JBool(true))
    },Full(RECEIVE_IS_INTERACTIVE_USER)),
    ClientSideFunction("getUserOptions",List.empty[String],(args) => JString("not yet implemented"),Full(RECEIVE_USER_OPTIONS)),
    ClientSideFunction("setUserOptions",List("newOptions"),(args) => JString("not yet implemented"),Empty),
    ClientSideFunction("getUserGroups",List.empty[String],(args) => getUserGroups,Full(RECEIVE_USER_GROUPS)),
    ClientSideFunction("getResource",List("source"),(args) => JString("not yet implemented"),Empty),
    ClientSideFunction("moveToSlide",List("where"),(args) => {
      val where = getArgAsString(args(0))
      debug("moveToSlideRequested(%s)".format(where))
      moveToSlide(where)
      partialUpdate(refreshClientSideStateJs(true))
      JNull
    },Empty),
    ClientSideFunction("joinRoom",List("where"),(args) => {
      val where = getArgAsString(args(0))
      joinRoomByJid(where)
      joinRoomByJid(where+username)
      JNull
    },Empty),
    ClientSideFunction("leaveRoom",List("where"),(args) => {
      val where = getArgAsString(args(0))
      leaveRoomByJid(where)
      leaveRoomByJid(where+username)
      JNull
    },Empty),
    ClientSideFunction("sendStanza",List("stanza"),(args) => {
      val stanza = getArgAsJValue(args(0))
      trace("sendStanza: %s".format(stanza.toString))
      sendStanzaToServer(stanza)
      JNull
    },Empty),
    ClientSideFunction("undeleteStanza",List("stanza"),(args) => {
      val stanza = getArgAsJValue(args(0))
      trace("undeleteStanza: %s".format(stanza.toString))
      val metlData = serializer.toMeTLData(stanza)
      metlData match {
        case m:MeTLCanvasContent => {
          if (m.author == username || shouldModifyConversation()){
            currentConversation.map(cc => {
              val t = m match {
                case i:MeTLInk => "ink"
                case i:MeTLImage => "img"
                case i:MeTLMultiWordText => "txt"
                case _ => "_"
              }
              val p = m.privacy match {
                case Privacy.PRIVATE => "private"
                case Privacy.PUBLIC => "public"
                case _ => "_"
              }
              emit(p,m.identity,t)
              val roomId = m.privacy match {
                case Privacy.PRIVATE => {
                  m.slide+username
                }
                case Privacy.PUBLIC => {
                    m.slide
                }
                case other => {
                  warn("unexpected privacy found in: %s".format(m))
                  m.slide
                }
              }
              rooms.get((server,roomId)).foreach(targetRoom => {
                val room = targetRoom() 
                room.getHistory.getDeletedCanvasContents.find(dc => {
                  dc.identity == m.identity && dc.author == m.author
                }).foreach(dc => {
                  val newIdentitySeed = "%s_%s".format(new Date().getTime(),dc.identity).take(64)
                  val newM = dc.generateNewIdentity(newIdentitySeed)
                  val newIdentity = newM.identity
                  val newUDM = MeTLUndeletedCanvasContent(config,username,0L,dc.target,dc.privacy,dc.slide,"%s_%s_%s".format(new Date().getTime(),dc.slide,username),(stanza \ "type").extract[Option[String]].getOrElse("unknown"),dc.identity,newIdentity,Nil)
                  trace("created newUDM: %s".format(newUDM))
                  room ! LocalToServerMeTLStanza(newUDM)
                  
                  room ! LocalToServerMeTLStanza(newM)
                })
              })
            })
          }
        }
        case notAStanza => error("Not a stanza at undeleteStanza %s".format(notAStanza))
      }
      JNull
    },Empty),
    ClientSideFunction("sendTransientStanza",List("stanza"),(args) => {
      val stanza = getArgAsJValue(args(0))
      sendStanzaToServer(stanza,"loopback")
      JNull
    },Empty),
    ClientSideFunction("getRooms",List.empty[String],(unused) => JArray(rooms.map(kv => JObject(List(JField("server",JString(kv._1._1)),JField("jid",JString(kv._1._2)),JField("room",JString(kv._2.toString))))).toList),Full("recieveRoomListing")),
    ClientSideFunction("getUser",List.empty[String],(unused) => JString(username),Full(RECEIVE_USERNAME)),
    ClientSideFunction("changePermissionsOfConversation",List("jid","newPermissions"),(args) => {
      val jid = getArgAsString(args(0))
      val newPermissions = getArgAsJValue(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.changePermissions(c.jid.toString,serializer.toPermissions(newPermissions))
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("changeBlacklistOfConversation",List("jid","newBlacklist"),(args) => {
      val jid = getArgAsString(args(0))
      val rawBlacklist = getArgAsListOfStrings(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.updateConversation(c.jid.toString,c.copy(blackList = rawBlacklist))
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("banContent",List("conversationJid","slideJid","inkIds","textIds","multiWordTextIds","imageIds","videoIds"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val slideJid = getArgAsInt(args(1))
      val inkIds = getArgAsListOfStrings(args(2))
      val textIds = getArgAsListOfStrings(args(3))
      val multiWordTextIds = getArgAsListOfStrings(args(4))
      val imageIds = getArgAsListOfStrings(args(5))
      val videoIds = getArgAsListOfStrings(args(6))
      val now = new Date().getTime
      val pubRoom = rooms.get((server,slideJid.toString)).map(_())
      val pubHistory = pubRoom.map(_.getHistory).getOrElse(History.empty)

      val title = "submission%s%s.jpg".format(username,now.toString)

      val inks = pubHistory.getInks.filter(elem => inkIds.contains(elem.identity))
      val images = pubHistory.getImages.filter(elem => imageIds.contains(elem.identity))
      val texts = pubHistory.getTexts.filter(elem => textIds.contains(elem.identity))
      val videos = pubHistory.getVideos.filter(elem => videoIds.contains(elem.identity))
      val multiWordTexts = pubHistory.getMultiWordTexts.filter(elem => multiWordTextIds.contains(elem.identity))
      val highlighters = pubHistory.getHighlighters.filter(elem => inkIds.contains(elem.identity))

      val authors = (inks ::: images ::: texts ::: highlighters ::: multiWordTexts ::: videos).map(_.author).distinct
      val conv = serverConfig.detailsOfConversation(conversationJid)
      if (shouldModifyConversation(conv)){
        serverConfig.updateConversation(conv.jid.toString,conv.copy(blackList = (conv.blackList ::: authors).distinct.toList))
        alertScreen("banning users","Blacklisted users: %s".format(authors))
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
            val imageBytes = pubRoom.map(_.slideRenderer.render(mergedHistory,width,height)).getOrElse(Array.empty[Byte])
            val uri = serverConfig.postResource(conversationJid,title,imageBytes)
            val submission = MeTLSubmission(serverConfig,username,now,title,slideJid,uri,Full(imageBytes),blacklistedPeople,"bannedcontent")
            trace("banned with the following: %s".format(submission))
            rooms.get((server,conversationJid)).map(r =>{
              r() ! LocalToServerMeTLStanza(submission)
            });
            alertScreen("banning users","Blacklist record created and added for authors: %s".format(authors))
          }
          case _ => {
            errorScreen("banning users","blacklist record creation failed.  Your canvas is empty.")
          }
        }
        val deleterId = nextFuncName
        val deleter = MeTLMoveDelta(serverConfig,username,now,"presentationSpace",Privacy.PUBLIC,slideJid.toString,deleterId,0.0,0.0,inkIds,textIds,multiWordTextIds,imageIds,videoIds,0.0,0.0,0.0,0.0,Privacy.NOT_SET,true)
        rooms.get((server,slideJid.toString)).map(r =>{
          r() ! LocalToServerMeTLStanza(deleter)
        })
      }
      JNull
    },Empty),
    ClientSideFunction("overrideAllocation",List("conversationJid","slideObject"),(args) => {
      debug("Override allocation: %s".format(args))
      val newSlide = serializer.toSlide(getArgAsJValue(args(1)))
      val c = serverConfig.detailsOfConversation(getArgAsString(args(0)))
      debug("Parsed values: %s".format(newSlide,c))
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.updateConversation(c.jid.toString,c.copy(slides = newSlide :: c.slides.filterNot(_.id == newSlide.id)))
        case false => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("addGroupSlideToConversationAtIndex",List("jid","index","grouping","initialGroups","parameter"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val initialGroups = args(3) match {
        case JArray(groups) => groups.map(getArgAsListOfStrings _).map(members => com.metl.data.Group(serverConfig,nextFuncName,jid,new Date().getTime,members))
        case _ => Nil
      }
      val c = serverConfig.detailsOfConversation(jid)
      val grouping = getArgAsString(args(2)) match {
        case "byTotalGroups" => com.metl.data.GroupSet(serverConfig,nextFuncName,jid,ByTotalGroups(getArgAsInt(args(4))),initialGroups)
        case "byMaximumSize" => com.metl.data.GroupSet(serverConfig,nextFuncName,jid,ByMaximumSize(getArgAsInt(args(4))),initialGroups)
        case "groupsOfOne" => com.metl.data.GroupSet(serverConfig,nextFuncName,jid,OnePersonPerGroup,initialGroups)
        case _ => com.metl.data.GroupSet(serverConfig,nextFuncName,jid,ByMaximumSize(4),initialGroups)
      }
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.addGroupSlideAtIndexOfConversation(c.jid.toString,index,grouping)
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("addSlideToConversationAtIndex",List("jid","index"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("addImageSlideToConversationAtIndex",List("jid","index","resourceId","caption"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val resourceId = getArgAsString(args(2))
      val captionArg = getArgAsJValue(args(3))
      val caption = serializer.toMeTLMultiWordText(captionArg)
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            val bytes = serverConfig.getResource(resourceId)
            val now = new java.util.Date().getTime
            val identity = "%s%s".format(username,now.toString)
            val tempSubImage = MeTLImage(serverConfig,username,now,identity,Full(resourceId),Full(bytes),Empty,Double.NaN,Double.NaN,10,10,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
            val dimensions = slideRoom.slideRenderer.measureImage(tempSubImage)
            val subImage = MeTLImage(serverConfig,username,now,identity,Full(resourceId),Full(bytes),Empty,dimensions.width,dimensions.height,dimensions.left,dimensions.top,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
            slideRoom ! LocalToServerMeTLStanza(subImage)
            slideRoom ! LocalToServerMeTLStanza(caption.copy(slide=ho.id.toString))
          })
          newC
        }
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),

    ClientSideFunction("addSubmissionSlideToConversationAtIndex",List("jid","index","submissionId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val submissionId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            MeTLXConfiguration.getRoom(jid,server).getHistory.getSubmissions.find(sub => sub.identity == submissionId).map(sub => {
              val now = new java.util.Date().getTime
              val identity = "%s%s".format(username,now.toString)
              val tempSubImage = MeTLImage(serverConfig,username,now,identity,Full(sub.url),sub.imageBytes,Empty,Double.NaN,Double.NaN,10,10,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
              val dimensions = slideRoom.slideRenderer.measureImage(tempSubImage)
              val subImage = MeTLImage(serverConfig,username,now,identity,Full(sub.url),sub.imageBytes,Empty,dimensions.width,dimensions.height,dimensions.left,dimensions.top,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
              slideRoom ! LocalToServerMeTLStanza(subImage)
            })
          })
          newC
        }
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("addQuizViewSlideToConversationAtIndex",List("jid","index","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val quizId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
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
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("addQuizResultsViewSlideToConversationAtIndex",List("jid","index","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val quizId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
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
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("reorderSlidesOfCurrentConversation",List("jid","newSlides"),(args) => {
      val jid = getArgAsString(args(0))
      val newSlides = getArgAsJArray(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          (newSlides.arr.length == c.slides.length) match {
            case true => serverConfig.reorderSlidesOfConversation(c.jid.toString,newSlides.arr.map(i => serializer.toSlide(i)).toList)
            case false => c
          }
        }
        case _ => c
      },username,Globals.getUserGroups))
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunction("getQuizzesForConversation",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val quizzes = getQuizzesForConversation(jid).map(q => serializer.fromMeTLQuiz(q)).toList
      JArray(quizzes)
    },Full(RECEIVE_QUIZZES)),
    ClientSideFunction("getResponsesForQuizInConversation",List("conversationJid","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val quizId = getArgAsString(args(1))
      JArray(getQuizResponsesForQuizInConversation(jid,quizId).map(q => serializer.fromMeTLQuizResponse(q)).toList)
    },Full(RECEIVE_QUIZ_RESPONSES)),
    ClientSideFunction("answerQuiz",List("conversationJid","quizId","chosenOptionName"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val quizId = getArgAsString(args(1))
      val chosenOptionName = getArgAsString(args(2))
      val response = MeTLQuizResponse(serverConfig,username,new Date().getTime,chosenOptionName,username,quizId)
      rooms.get((server,conversationJid)).map(r => r() ! LocalToServerMeTLStanza(response))
      JNull
    },Empty),
    ClientSideFunction("getGroupsProviders",Nil,(args) => {
      JObject(List(
        JField("groupsProviders",JArray(Globals.getGroupsProviders.filter(_.canQuery).map(gp => JObject(List(JField("storeId",JString(gp.storeId)),JField("displayName",JString(gp.name)))))))
      ))
    },Full("receiveGroupsProviders")),
    ClientSideFunction("getOrgUnitsFromGroupProviders",List("storeId"),(args) => {
      val sid = getArgAsString(args(0))
      val gp = JObject(List(JField("storeId",JString(sid))) ::: Globals.getGroupsProviders.find(_.storeId == sid).toList.map(gp => JField("displayName",JString(gp.name))))
      JObject(List(
        JField("groupsProvider",gp),
        JField("orgUnits",JArray(Globals.getGroupsProvider(sid).toList.flatMap(gp => {
          gp.getGroupsFor(Globals.casState.is).map(g => Extraction.decompose(g))
        }).toList))
      ))
    },Full("receiveOrgUnitsFromGroupsProviders")),
    ClientSideFunction("getGroupSetsForOrgUnit",List("storeId","orgUnit"),(args) => {
      val sid = getArgAsString(args(0))
      val orgUnitJValue = getArgAsJValue(args(1))
      val orgUnit = orgUnitJValue.extract[OrgUnit]
      val members = for {
        cc <- currentConversation.toList
        r <- rooms.get((server,cc.jid.toString)).toList
        a <- r().getPossibleAttendance
      } yield {
        Member(a,Nil,None)
      }
      val groupSets = JArray(Globals.getGroupsProvider(sid).toList.flatMap(gp => {
        gp.getGroupSetsFor(orgUnit,members).map(gs => Extraction.decompose(gs))
      }).toList)
      val gp = JObject(List(JField("storeId",JString(sid))) ::: Globals.getGroupsProviders.find(_.storeId == sid).toList.map(gp => JField("displayName",JString(gp.name))))
      JObject(List(
        JField("groupsProvider",gp),
        JField("orgUnit",orgUnitJValue),
        JField("groupSets",groupSets)
      ))
    },Full("receiveGroupSetsForOrgUnit")),
    ClientSideFunction("getGroupsForGroupSet",List("storeId","orgUnit","groupSet"),(args) => {
      val sid = getArgAsString(args(0))
      val orgUnitJValue = getArgAsJValue(args(1))
      val orgUnit = orgUnitJValue.extract[OrgUnit]
      val groupSetJValue = getArgAsJValue(args(2))
      val groupSet = groupSetJValue.extract[GroupSet]
      val members = for {
        cc <- currentConversation.toList
        r <- rooms.get((server,cc.jid.toString)).toList
        a <- r().getPossibleAttendance
      } yield {
        Member(a,Nil,None)
      }
      val groups = JArray(Globals.getGroupsProvider(sid).toList.flatMap(gp => {
        gp.getGroupsFor(orgUnit,groupSet,members).map(gs => Extraction.decompose(gs))
      }).toList)
      val gp = JObject(List(JField("storeId",JString(sid))) ::: Globals.getGroupsProviders.find(_.storeId == sid).toList.map(gp => JField("displayName",JString(gp.name))))
      JObject(List(
        JField("groupsProvider",gp),
        JField("orgUnit",orgUnitJValue),
        JField("groupSet",groupSetJValue),
        JField("groups",groups)
      ))
    },Full("receiveGroupsForGroupSet"))
  )
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

  def emit(source:String,value:String,domain:String) = {
    trace("emit triggered by %s: %s,%s,%s".format(username,source,value,domain))
    currentConversation.map(cc => {
      MeTLXConfiguration.getRoom(cc.jid.toString,server) ! LocalToServerMeTLStanza(MeTLTheme(serverConfig,username,-1L,cc.jid.toString,Theme(source,value,domain),Nil))
    })
  }
  private var rooms = Map.empty[Tuple2[String,String],() => MeTLRoom]
  private lazy val serverConfig = ServerConfiguration.default
  private lazy val server = serverConfig.name
  trace("serverConfig: %s -> %s".format(server,serverConfig))
  private def username = (for (
    nameString <- name;
    user <- com.metl.snippet.Metl.getUserFromName(nameString)
  ) yield {
    user
  }).getOrElse(Globals.currentUser.is)
  private val serializer = new JsonSerializer(ServerConfiguration.default)
  def registerWith = MeTLActorManager
  val scriptContainerId = "scriptContainer_%s".format(nextFuncName)
  override def render = {
    OnLoad(refreshClientSideStateJs(true))
  }
  def hideLoader:JsCmd = Hide("loadingSpinner")

  override def lowPriority = {
    case roomInfo:RoomStateInformation => Stopwatch.time("MeTLActor.lowPriority.RoomStateInformation", updateRooms(roomInfo))
    case metlStanza:MeTLStanza => Stopwatch.time("MeTLActor.lowPriority.MeTLStanza", sendMeTLStanzaToPage(metlStanza))
    case UpdateThumb(slide) => {
      trace("Updating thumb %s for actor: %s".format(slide,name))
      partialUpdate(Call(UPDATE_THUMB,JString(slide)))
    }
    case JoinThisSlide(slide) => {
      moveToSlide(slide)
      partialUpdate(refreshClientSideStateJs(true))
    }
    case HealthyWelcomeFromRoom => {}
    case cp@ConversationParticipation(jid,currentMembers,possibleMembers) if shouldModifyConversation() => {
      trace("CONVERSATION PARTICIPATION: %s".format(cp))
      currentConversation.foreach(cc => {
        if( cc.jid.toString.equals(jid)) {
          partialUpdate(Call(RECEIVE_ATTENDANCE,JObject(List(
            JField("location",JString(jid)),
            JField("currentMembers",JArray(currentMembers.filter(pm => !ImporterActor.backgroundWorkerName.equals(pm)).map(cm => JString(cm)))),
            JField("possibleMembers",JArray(possibleMembers.filter(pm => !ImporterActor.backgroundWorkerName.equals(pm)).map(pm => JString(pm))))
          ))))
        }
      })
    }
    case ConversationParticipation(jid,currentMembers,possibleMembers) => {}
    case other => warn("MeTLActor %s received unknown message: %s".format(name,other))
  }
  override def autoIncludeJsonCode = true
  protected var currentConversation:Box[Conversation] = Empty
  protected var currentSlide:Box[String] = Empty
  protected var isInteractiveUser:Box[Boolean] = Empty

  override def localSetup = Stopwatch.time("MeTLActor.localSetup(%s,%s)".format(username,userUniqueId), {
    super.localSetup()
    debug("created metlactor: %s => %s".format(name,S.session))
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
    debug("completedWorker: %s".format(name))
  })
  private def joinRoomByJid(jid:String,serverName:String = server) = Stopwatch.time("MeTLActor.joinRoomByJid(%s)".format(jid),{
    MeTLXConfiguration.getRoom(jid,serverName) ! JoinRoom(username,userUniqueId,this)
  })
  private def leaveRoomByJid(jid:String,serverName:String = server) = Stopwatch.time("MeTLActor.leaveRoomByJid(%s)".format(jid),{
    MeTLXConfiguration.getRoom(jid,serverName) ! LeaveRoom(username,userUniqueId,this)
  })
  override def localShutdown = Stopwatch.time("MeTLActor.localShutdown(%s,%s)".format(username,userUniqueId),{
    trace("shutdown metlactor: %s".format(name))
    leaveAllRooms(true)
    super.localShutdown()
  })
  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => JObject(List(JField("type",JString(eg.ouType)),JField("value",JString(eg.name))))).toList)
  private def refreshClientSideStateJs(refreshDetails:Boolean) = {
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
    val receiveUsername:Box[JsCmd] = Full(Call(RECEIVE_USERNAME,JString(username)))
    trace(receiveUsername)
    val receiveUserGroups:Box[JsCmd] = Full(Call(RECEIVE_USER_GROUPS,getUserGroups))
    trace(receiveUserGroups)
    val receiveCurrentConversation:Box[JsCmd] = currentConversation.map(cc => Call(RECEIVE_CURRENT_CONVERSATION,JString(cc.jid.toString)))
    trace(receiveCurrentConversation)
    val receiveConversationDetails:Box[JsCmd] = if(refreshDetails) currentConversation.map(cc => Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(cc,username,Globals.getUserGroups)))) else Empty
    trace(receiveConversationDetails)
    val receiveCurrentSlide:Box[JsCmd] = currentSlide.map(cc => Call(RECEIVE_CURRENT_SLIDE, JString(cc)))
    trace(receiveCurrentSlide)
    val receiveLastSyncMove:Box[JsCmd] = if(shouldModifyConversation()){
      Empty
    }
    else {
      currentConversation.map(cc => {
        trace("receiveLastSyncMove attempting to get room %s, %s".format(cc,server))
        val room = MeTLXConfiguration.getRoom(cc.jid.toString,server)
        trace("receiveLastSyncMove: %s".format(room))
        val history = room.getHistory
        trace("receiveLastSyncMove: %s".format(history))
        history.getLatestCommands.get("/SYNC_MOVE") match {
          case Some(lastSyncMove) =>{
            trace("receiveLastSyncMove found move: %s".format(lastSyncMove))
            lastSyncMove.commandParameters match {
              case List(cp0) => Call(RECEIVE_SYNC_MOVE,JInt(cp0.toInt))
              case List(cp0,cp1) => Call(RECEIVE_SYNC_MOVE,JInt(cp0.toInt),JString(cp1.toString))
              case _ => Noop
            }
          }
          case _ =>{
            trace("receiveLastSyncMove no move found")
            Noop
          }
        }
      })
    }
    val receiveTokBoxEnabled:Box[JsCmd] = Full(Call(RECEIVE_TOK_BOX_ENABLED,JBool(Globals.tokBox.isDefined)))
    def receiveTokBoxSessionsFunc(tokSessionCol:scala.collection.mutable.HashMap[String,Option[TokBoxSession]]):List[Box[JsCmd]] = tokSessionCol.toList.map(tokSessionTup => {
      val sessionName = tokSessionTup._1
      val tokSession = tokSessionTup._2
      (for {
        cc <- currentConversation
        tb <- Globals.tokBox
        role = shouldModifyConversation() match {
          case true => TokRole.Moderator
          case false => TokRole.Publisher
        }
        session <- synchronized { tokSession.map(s => Some(s)).getOrElse({
          val newSession = tb.getSessionToken(sessionName,role).left.map(e => {
            error("exception initializing tokboxSession:",e)
          }).right.toOption
          trace("generating tokBox session in %s for: %s %s".format(name,sessionName,newSession))
          tokSessionCol += ((sessionName,newSession))
          newSession
        })
        }
      } yield {
        val j:JsCmd = Call(RECEIVE_TOK_BOX_SESSION_TOKEN,JObject(List(
          JField("sessionId",JString(session.sessionId)),
          JField("token",JString(session.token)),
          JField("apiKey",JInt(session.apiKey))
        )))
        j
      })
    })
    val receiveTokBoxSessions:List[Box[JsCmd]] = receiveTokBoxSessionsFunc(tokSessions)
    val receiveTokBoxSlideSpecificSessions:List[Box[JsCmd]] = receiveTokBoxSessionsFunc(tokSlideSpecificSessions)
    val receiveTokBoxBroadcast:Box[JsCmd] = (for {
      tb <- Globals.tokBox
      s <- tokSessions.toList.flatMap(_._2).headOption
      a <- tb.getBroadcast(s)
    } yield {
      val j:JsCmd = Call(RECEIVE_TOK_BOX_BROADCAST,Extraction.decompose(a))
      j
    })
    trace(receiveLastSyncMove)
    val receiveHistory:Box[JsCmd] = currentSlide.map(cc => Call(RECEIVE_HISTORY,getSlideHistory(cc)))
    val receiveInteractiveUser:Box[JsCmd] = isInteractiveUser.map(iu => Call(RECEIVE_IS_INTERACTIVE_USER,JBool(iu)))
    trace(receiveInteractiveUser)

    val loadComplete = for {
      h <- receiveHistory
      u <- receiveUsername
      c <- receiveCurrentConversation
      cd <- receiveConversationDetails
      s <- receiveCurrentSlide
    } yield {
      hideLoader
    }
    val jsCmds:List[Box[JsCmd]] = List(receiveUsername,receiveUserGroups,receiveCurrentConversation,receiveCurrentSlide,receiveConversationDetails,receiveLastSyncMove,receiveHistory,receiveInteractiveUser,receiveTokBoxEnabled) ::: receiveTokBoxSlideSpecificSessions ::: receiveTokBoxSessions ::: List(receiveTokBoxBroadcast,loadComplete)
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
      tokSessions += ((conversationJid,None))
      joinRoomByJid(conversationJid)
      /*
       if (shouldModifyConversation(username,details)){
       MeTLXConfiguration.getRoom(cj,serverConfig.name) ! LocalToServerMeTLStanza(MeTLCommand(serverConfig,Globals.currentUser.is,-1L,"/SYNC_MOVE",List(sid)))
       }
       */
      //      rooms.get((server,"global")).foreach(r => r ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,conversationJid,true,Nil)))
      //joinRoomByJid(conversationJid,"loopback")
      currentConversation
    } else {
      debug("conversation denied: %s, %s.".format(jid,details.subject))
      trace("joinConversation kicking this cometActor(%s) from the conversation because it's no longer permitted".format(name))
      currentConversation = Empty
      currentSlide = Empty
      tokSessions -= details.jid.toString
      reRender
      partialUpdate(RedirectTo(noBoard))
      Empty
    }
  }
  private def getSlideHistory(jid:String):JValue = {
    trace("GetSlideHistory %s".format(jid))
    val convHistory = currentConversation.map(cc => MeTLXConfiguration.getRoom(cc.jid.toString,server).getHistory).openOr(History.empty)
    trace("conv %s".format(jid))
    val pubHistory = MeTLXConfiguration.getRoom(jid,server).getHistory
    trace("pub %s".format(jid))
    val privHistory = isInteractiveUser.map(iu => if (iu){
      MeTLXConfiguration.getRoom(jid+username,server).getHistory
    } else {
      History.empty
    }).openOr(History.empty)
    trace("priv %s".format(jid))
    val allGrades = Map(convHistory.getGrades.groupBy(_.id).values.toList.flatMap(_.sortWith((a,b) => a.timestamp > b.timestamp).headOption.map(g => (g.id,g)).toList):_*)
    trace("conv found %s".format(convHistory.getGradeValues))
    val finalHistory = pubHistory.merge(privHistory).merge(convHistory).filter{
      case g:MeTLGrade => true
      case gv:MeTLGradeValue if shouldModifyConversation() => true
      case gv:MeTLGradeValue if allGrades.get(gv.getGradeId).exists(_.visible == true) && gv.getGradedUser == username => true
      case gv:MeTLGradeValue => false
      case qr:MeTLQuizResponse if (qr.author != username && !shouldModifyConversation()) => false
      case s:MeTLSubmission if (s.author != username && !shouldModifyConversation()) => false
      case _ => true
    }
    debug("final %s".format(jid))
    trace("final found %s".format(finalHistory.getGradeValues))
    serializer.fromHistory(finalHistory)
  }
  private def conversationContainsSlideId(c:Conversation,slideId:Int):Boolean = c.slides.exists((s:Slide) => s.id == slideId)
  private def moveToSlide(jid:String):Unit = {
    trace("moveToSlide {0}".format(jid))
    trace("CurrentConversation".format(currentConversation))
    trace("CurrentSlide".format(currentSlide))
    val slideId = jid.toInt
    currentSlide.filterNot(_ == jid).map(cs => {
      currentConversation.filter(cc => conversationContainsSlideId(cc,slideId)).map(cc => {
        trace("Don't have to leave conversation, current slide is in it")
        //        rooms.get((server,cc.jid.toString)).foreach(r => r ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,cs,false,Nil)))
      }).getOrElse({
        trace("Joining conversation for: %s".format(slideId))
        joinConversation(serverConfig.getConversationForSlide(jid))
      })
      leaveRoomByJid(cs)
      leaveRoomByJid(cs+username)
    })
    currentConversation.getOrElse({
      trace("Joining conversation for: %s".format(slideId))
      joinConversation(serverConfig.getConversationForSlide(jid))
    })
    currentConversation.map(cc => {
      trace("checking to see that current conv and current slide now line up")
      if (conversationContainsSlideId(cc,slideId)){
        trace("conversation contains slide")
        currentSlide = Full(jid)
        if (cc.author.trim.toLowerCase == username.trim.toLowerCase && isInteractiveUser.map(iu => iu == true).getOrElse(true)){
          val syncMove = MeTLCommand(serverConfig,username,new Date().getTime,"/SYNC_MOVE",List(jid,uniqueId))
          rooms.get((server,cc.jid.toString)).map(r => r() ! LocalToServerMeTLStanza(syncMove))
        }
        joinRoomByJid(jid)
        joinRoomByJid(jid+username)
        val sessionsToClose = for {
          sessionTup <- tokSlideSpecificSessions.toList
          tokSess <- sessionTup._2
        } yield {
          tokSess
        }
        sessionsToClose.map(tokSess => JString(tokSess.sessionId)) match {
          case Nil => {}
          case toClose => {
            trace("shutting down tokSessions: %s".format(toClose))
            partialUpdate(Call(REMOVE_TOK_BOX_SESSIONS,JArray(toClose)))
          }
        }
        tokSlideSpecificSessions.clear()
        tokSlideSpecificSessions ++= (for {
          slide <- cc.slides
          if slide.id == slideId
          groupSet <- slide.groupSet
          group <- groupSet.groups
          if (group.members.contains(username) || shouldModifyConversation(cc))
            } yield {
          (group.id,None)
        })
      }
    })
  }
  private def leaveAllRooms(shuttingDown:Boolean = false) = {
    trace("leaving all rooms: %s".format(rooms))
    rooms.foreach(r => {
      if (shuttingDown || (r._1._2 != username && r._1._2 != "global")){
        trace("leaving room: %s".format(r))
        r._2() ! LeaveRoom(username,userUniqueId,this)
      }
    })
  }
  override def lifespan = Globals.metlActorLifespan

  private def updateRooms(roomInfo:RoomStateInformation):Unit = Stopwatch.time("MeTLActor.updateRooms",{
    trace("roomInfo received: %s".format(roomInfo))
    trace("updateRooms: %s".format(roomInfo))
    roomInfo match {
      case RoomJoinAcknowledged(s,r) => {
        trace("joining room: %s".format(r))
        rooms = rooms.updated((s,r),() => MeTLXConfiguration.getRoom(r,s))
      }
      case RoomLeaveAcknowledged(s,r) => {
        trace("leaving room: %s".format(r))
        rooms = rooms.filterNot(rm => rm._1 == (s,r))
      }
      case _ => {}
    }
  })
  protected def alertScreen(heading:String,message:String):Unit = {
    partialUpdate(Call("infoAlert",JString(heading),JString(message)))
  }
  protected def errorScreen(heading:String,message:String):Unit = {
    partialUpdate(Call("errorAlert",JString(heading),JString(message)))
  }
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
              trace("sendStanzaToServer sending submission: "+r)
              r() ! LocalToServerMeTLStanza(s)
              Globals.metlingPots.foreach(mp => {
                mp.postItems(List(
                  MeTLingPotItem("metlActor",new java.util.Date().getTime(),KVP("metlUser",s.author),KVP("informalAcademic","submission"),Some(KVP("room",s.slideJid.toString)),None,None)
                ))
              })
            })
          })
        }
      }
      case s:MeTLChatMessage => {
        if (s.author == username) {
          currentConversation.map(cc => {
            val roomId = cc.jid.toString
            rooms.get((serverName,roomId)).map(r =>{
              debug("sendStanzaToServer sending chatMessage: "+r)
              if( cc.blackList.contains(username)) {
                // Banned students can only whisper the teacher.
                r() ! LocalToServerMeTLStanza(s.adjustAudience(List(Audience(cc.server, "metl", cc.author, "user", "read"))))
              }
              else
              {
                r() ! LocalToServerMeTLStanza(s)
              }
            })
          })
        }
      }
      case qr:MeTLQuizResponse => {
        if (qr.author == username) {
          currentConversation.map(cc => {
            val roomId = cc.jid.toString
            rooms.get((serverName,roomId)).map(r => r() ! LocalToServerMeTLStanza(qr))
            Globals.metlingPots.foreach(mp => {
              mp.postItems(List(
                MeTLingPotItem("metlActor",new java.util.Date().getTime(),KVP("metlUser",qr.author),KVP("informalAcademic","quizResponse"),Some(KVP("room",cc.jid.toString)),Some(KVP("quiz",qr.id)),None)
              ))
            })
          })
        }
      }
      case q:MeTLQuiz => {
        if (q.author == username) {
          currentConversation.map(cc => {
            if (shouldModifyConversation(cc)){
              trace("sending quiz: %s".format(q))
              val roomId = cc.jid.toString
              rooms.get((serverName,roomId)).map(r => r() ! LocalToServerMeTLStanza(q))
            } else {
              errorScreen("quiz creation","You are not permitted to create quizzes in this conversation")
            }
          })
        }
      }
      case c:MeTLCanvasContent => {
        if (c.author == username){
          currentConversation.map(cc => {
            val t = c match {
              case i:MeTLInk => "ink"
              case i:MeTLImage => "img"
              case i:MeTLMultiWordText => "txt"
              case _ => "_"
            }
            val p = c.privacy match {
              case Privacy.PRIVATE => "private"
              case Privacy.PUBLIC => "public"
              case _ => "_"
            }
            emit(p,c.identity,t)
            val (shouldSend,roomId,finalItem) = c.privacy match {
              case Privacy.PRIVATE => {
                (true,c.slide+username,c)
              }
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
        if (c.author == username && shouldModifyConversation()){
          val conversationSpecificCommands = List("/SYNC_MOVE","/TEACHER_IN_CONVERSATION")
          val slideSpecificCommands = List("/TEACHER_VIEW_MOVED")
          val roomTarget = c.command match {
            case s:String if (conversationSpecificCommands.contains(s)) => currentConversation.map(_.jid.toString).getOrElse("global")
            case s:String if (slideSpecificCommands.contains(s)) => currentSlide.getOrElse("global")
            case _ => "global"
          }
          val alteredCommand = c match {
            case MeTLCommand(config,author,timestamp,"/SYNC_MOVE",List(jid),audiences) => MeTLCommand(config,author,timestamp,"/SYNC_MOVE",List(jid,uniqueId),audiences)
            case other => other
          }
          rooms.get((serverName,roomTarget)).map(r => {
            trace("sending MeTLStanza to room: %s <- %s".format(r,alteredCommand))
            r() ! LocalToServerMeTLStanza(alteredCommand)
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
      case g:MeTLGrade => {
        if (g.author == username){
          currentConversation.map(cc => {
            if (cc.author == g.author){
              val roomTarget = cc.jid.toString
              rooms.get((serverName,roomTarget)).map(r => {
                r() ! LocalToServerMeTLStanza(g)
              })
            }
          })
        }
      }
      case g:MeTLGradeValue => {
        if (g.author == username){
          currentConversation.map(cc => {
            if (cc.author == g.author){
              val roomTarget = cc.jid.toString
              rooms.get((serverName,roomTarget)).map(r => {
                r() ! LocalToServerMeTLStanza(g)
                Globals.metlingPots.foreach(mp => {
                  mp.postItems(List(
                    MeTLingPotItem("metlActor",new java.util.Date().getTime(),KVP("metlUser",g.author),KVP("formalAcademic","graded"),Some(KVP("grade",g.getGradeId)),Some(KVP("metlUser",g.getGradedUser)),None)
                  ))
                })
              })
            }
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
        trace("comet.MeTL /UPDATE_CONVERSATION_DETAILS for %s".format(name))
        val newJid = c.commandParameters(0).toInt
        val newConv = serverConfig.detailsOfConversation(newJid.toString)
        if (currentConversation.exists(_.jid == newConv.jid)){
          if (!shouldDisplayConversation(newConv)){
            debug("sendMeTLStanzaToPage kicking this cometActor(%s) from the conversation because it's no longer permitted".format(name))
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
            trace("updating conversation to: %s".format(newConv))
            partialUpdate(Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(newConv,username,Globals.getUserGroups))))
          }
        }
      }
      case c:MeTLCommand if (c.command == "/SYNC_MOVE") => {
        trace("incoming syncMove: %s".format(c))
        val newJid = c.commandParameters(0).toInt
        val signature = c.commandParameters(1)
        if(uniqueId != signature){//Don't respond to moves that started at this actor
          partialUpdate(Call(RECEIVE_SYNC_MOVE,JInt(newJid),JString(signature)))
        }
      }
      case c:MeTLCommand if (c.command == "/TEACHER_IN_CONVERSATION") => {
        //not relaying teacherInConversation to page
      }
      //case a:Attendance if (shouldModifyConversation()) => getAttendance.map(attendances => partialUpdate(Call(RECEIVE_ATTENDANCE,attendances)))
      case s:MeTLSubmission if !shouldModifyConversation() && s.author != username => {
        //not sending the submission to the page, because you're not the author and it's not yours
      }
      case qr:MeTLQuizResponse if !shouldModifyConversation() && qr.author != username => {
        //not sending the quizResponse to the page, because you're not the author and it's not yours
      }
      /*
       case g:MeTLGrade if !shouldModifyConversation() && !g.visible => {
       //not sending a grade to the page because you're not the author, and this one's not visible
       }
       */
      case gv:MeTLGradeValue => {
        currentConversation.foreach(cc => {
          if (shouldModifyConversation(cc)){
            partialUpdate(Call(RECEIVE_METL_STANZA,serializer.fromMeTLData(gv)))
          } else {
            if (gv.getGradedUser == username){
              val roomTarget = cc.jid.toString
              rooms.get((serverConfig.name,roomTarget)).map(r => {
                val convHistory = r().getHistory
                val allGrades = Map(convHistory.getGrades.groupBy(_.id).values.toList.flatMap(_.sortWith((a,b) => a.timestamp > b.timestamp).headOption.map(g => (g.id,g)).toList):_*)
                val thisGrade = allGrades.get(gv.getGradeId)
                if (thisGrade.exists(_.visible)){
                  partialUpdate(Call(RECEIVE_METL_STANZA,serializer.fromMeTLData(gv)))
                }
              })
            }
          }
        })
      }
      case a:Attendance if currentConversation.map(cc => {
        val relevantIds = cc.jid :: cc.slides.map(_.id.toString)
        !relevantIds.contains(a.location)
      }).getOrElse(true) => {
        //swallow this message - it's not for this actor.  We're not interested in it.
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
  /*
   protected var expectedAttendanceCache:Option[List[Member]] = None
   def getAttendance = {
   val expectedAttendance = expectedAttendanceCache.getOrElse({
   val calced = (for(
   c <- currentConversation.toList;
   gp <- Globals.groupsProviders.filter(_.canQuery);
   ou <- gp.getOrgUnit(c.subject).toList) yield gp.getMembersFor(ou)).flatten
   expectedAttendanceCache = Some(calced)
   calced
   })
   val actualAttendance = (for(
   conversation <- currentConversation;
   room <- rooms.get(server,conversation.jid.toString)) yield {
   room().getAttendances
   .filter(_.present)
   .map(_.author)
   .distinct
   .map(JString(_))
   }).getOrElse(Nil)
   trace("actualAttendance: %s".format(actualAttendance.toString))
   currentSlide.map(slideJid => JObject(List(
   JField("val",JInt(actualAttendance.length)),
   JField("max",JInt(expectedAttendance.length)))))
   }
   */
  private def shouldModifyConversation(c:Conversation = currentConversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldModifyConversation(username,c)
  private def shouldDisplayConversation(c:Conversation = currentConversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldDisplayConversation(c)
  private def shouldPublishInConversation(c:Conversation = currentConversation.getOrElse(Conversation.empty)):Boolean = com.metl.snippet.Metl.shouldPublishInConversation(username,c)
}
