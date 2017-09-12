package com.metl.comet

import com.metl.data._
import com.metl.utils._
import com.metl.liftAuthenticator._
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
import net.liftweb.json.Extraction

import com.metl.snippet.Metl._

trait ProfileJsonHelpers {
  val config = ServerConfiguration.default
  val mandatoryAttributes = List("avatarUrl","biography")
  def translateProfileIds(profileIds:List[String]):JObject = {
    JObject(config.getProfiles(profileIds:_*).map(p => {
      JField(p.id,renderProfile(p))
    }))
  }
  def renderProfile(p:Profile):JObject = {
    val providedAttrs = p.attributes.toList
    val additionalAttrs = mandatoryAttributes.filterNot(a => providedAttrs.exists(_._1 == a)).map(a => (a,""))
    JObject(List(
      JField("id",JString(p.id)),
      JField("name",JString(p.name)),
      JField("attributes",JObject((providedAttrs ::: additionalAttrs).map(a => JField(a._1,JString(a._2)))))
    ))
  }
}

case class JoinThisSlide(slide:String)

object MeTLActorManager extends LiftActor with ListenerManager with Logger {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case _ => warn("MeTLActorManager received unknown message")
  }
}
object MeTLProfileActorManager extends LiftActor with ListenerManager with Logger {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case p:Profile => sendListenersMessage(p)
    case _ => warn("MeTLProfileActorManager received unknown message")
  }
}
object MeTLAccountActorManager extends LiftActor with ListenerManager with Logger {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case p:Profile => sendListenersMessage(p)
    case _ => warn("MeTLAccountActorManager received unknown message")
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
object ConversationSummaryActorManager extends LiftActor with ListenerManager with Logger {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case m:MeTLCommand => sendListenersMessage(m)
    case _ => warn("ConversationSummaryActorManager received unknown message")
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

trait ReturnToMeTLBus[T <: StronglyTypedJsonActor] extends StronglyTypedJsonActor with JArgUtils with Logger {
  self: T =>
  implicit def jeToJsCmd(in:JsExp):JsCmd = in.cmd
  protected lazy val METLBUS_CALL = "MeTLBus.call"
  override protected def serverResponse(v:JValue):JsCmd = Call(METLBUS_CALL,JString("serverResponse"),JArray(List(v)))
  def busArgs(queue:String,args:JValue *):List[JValue] = List(JString(queue),JArray(args.toList))
  def busCall(queue:String,args:JValue *):Call = Call(METLBUS_CALL,busArgs(queue,args:_*).map(jv => JsExp.jValueToJsExp(jv)):_*)
}

object MeTLActorBaseHelper {
  val serverConfig = ServerConfiguration.default
  val formats = net.liftweb.json.DefaultFormats
  val serializer = new JsonSerializer(serverConfig)
}

trait MeTLActorBase[T <: ReturnToMeTLBus[T]] extends ReturnToMeTLBus[T] with ProfileJsonHelpers with ConversationFilter {
  self: T =>

  import com.metl.view.StatelessHtml
  protected implicit val formats = MeTLActorBaseHelper.formats
  protected lazy val serverConfig = MeTLActorBaseHelper.serverConfig
  override def autoIncludeJsonCode = true
  protected lazy val serializer = MeTLActorBaseHelper.serializer

  protected lazy val RECEIVE_PROFILE = "receiveProfile"
  protected lazy val RECEIVE_PROFILES = "receiveProfiles"
  protected lazy val RECEIVE_ACTIVE_PROFILE = "receiveCurrentProfile"
  protected lazy val RECEIVE_DEFAULT_PROFILE = "receiveDefaultProfile"
  protected lazy val RECEIVE_ACCOUNT = "receiveAccount"
  protected lazy val RECEIVE_SESSION_HISTORY = "receiveSessionHistory"
  protected lazy val RECEIVE_THEMES = "receiveThemes"
  protected lazy val RECEIVE_ATTENDANCES = "receiveAttendances"
  protected lazy val RECEIVE_CONVERSATIONS = "receiveConversations"

  protected lazy val RECEIVE_USERNAME = "receiveUsername"
  protected lazy val RECEIVE_IMPORT_DESCRIPTION = "receiveImportDescription"
  protected lazy val RECEIVE_IMPORT_DESCRIPTIONS = "receiveImportDescriptions"
  protected lazy val RECEIVE_USER_GROUPS = "receiveUserGroups"
  protected lazy val RECEIVE_CONVERSATION_DETAILS = "receiveConversationDetails"
  protected lazy val RECEIVE_SLIDE_DETAILS = "receiveSlideDetails"
  protected lazy val RECEIVE_NEW_CONVERSATION_DETAILS = "receiveNewConversationDetails"
  protected lazy val RECEIVE_QUERY = "receiveQuery"

  protected lazy val RECEIVE_METL_STANZA = "receiveMeTLStanza"
  protected lazy val RECEIVE_SYNC_MOVE = "receiveSyncMove"
  protected lazy val RECEIVE_SHOW_CONVERSATION_LINKS = "receiveShowConversationLinks"
  protected lazy val RECEIVE_HISTORY = "receiveHistory"
  protected lazy val RECEIVE_USER_OPTIONS = "receiveUserOptions"

  protected def username:String = Globals.currentUser.is
  protected def userGroups:List[OrgUnit] = Globals.getUserGroups
  protected def profile:Profile = Globals.currentProfile.is
  protected def account:Tuple2[String,String] = (Globals.currentAccount.name,Globals.currentAccount.provider)
  protected def jUsername = JString(username)
  protected def jUserGroups = JArray(userGroups.map(eg => Extraction.decompose(eg)))
  protected def jProfile = renderProfile(profile)
  protected def jAccount = JObject(List(
    JField("accountName",JString(account._1)),
    JField("accountProvider",JString(account._2))
  ))

  protected def shouldModifyConversation(c:Conversation):Boolean = com.metl.snippet.Metl.shouldModifyConversation(username,c)
  protected def shouldDisplayConversation(c:Conversation):Boolean = com.metl.snippet.Metl.shouldDisplayConversation(c)
  protected def shouldPublishInConversation(c:Conversation):Boolean = com.metl.snippet.Metl.shouldPublishInConversation(username,c)

  object TokBoxFunctions {
     lazy val RECEIVE_TOK_BOX_ENABLED = "receiveTokBoxEnabled"
     lazy val RECEIVE_TOK_BOX_SESSION_TOKEN = "receiveTokBoxSessionToken"
     lazy val REMOVE_TOK_BOX_SESSIONS = "removeTokBoxSessions"
     lazy val RECEIVE_TOK_BOX_ARCHIVES = "receiveTokBoxArchives"
     lazy val RECEIVE_TOK_BOX_BROADCAST = "receiveTokBoxBroadcast"
     def getTokBoxArchives(tokSessions:() => scala.collection.mutable.HashMap[String,Option[TokBoxSession]]) = ClientSideFunction("getTokBoxArchives",List.empty[String],(args) => {
      val jArchives = JArray(for {
        tb <- Globals.tokBox.toList
        s <- tokSessions().toList.flatMap(_._2)
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
      busArgs(RECEIVE_TOK_BOX_ARCHIVES,jArchives)
    },Full(METLBUS_CALL))
    def getTokBoxArchive(tokSessions:() => scala.collection.mutable.HashMap[String,Option[TokBoxSession]]) = ClientSideFunction("getTokBoxArchive",List("id"),(args) => {
      val id = getArgAsString(args(0))
      val jArchives = JArray((for {
        tb <- Globals.tokBox
        s <- tokSessions().toList.flatMap(_._2).headOption
        a <- tb.getArchive(s,id)
      } yield {
        Extraction.decompose(a)
      }).toList)
      busArgs(RECEIVE_TOK_BOX_ARCHIVES,jArchives)
    },Full(METLBUS_CALL))
    def removeTokBoxArchive(tokSessions:() => scala.collection.mutable.HashMap[String,Option[TokBoxSession]]) = ClientSideFunction("removeTokBoxArchive",List("id"),(args) => {
      val id = getArgAsString(args(0))
      val jArchives = JArray((for {
        tb <- Globals.tokBox
        s <- tokSessions().toList.flatMap(_._2).headOption
      } yield {
        val a = tb.removeArchive(s,id)
        JObject(List(
          JField("session",Extraction.decompose(s)),
          JField("success",JBool(a))
        ))
      }).toList)
      busArgs("",jArchives)
    },None)
    def startBroadcast(tokSessions:() => scala.collection.mutable.HashMap[String,Option[TokBoxSession]],conversationAccessor:() => Conversation) = ClientSideFunction("startBroadcast",List("layout"),(args) => {
      val layout = getArgAsString(args(0))
        val jBroadcast = JArray((for {
          tb <- Globals.tokBox
          if (shouldModifyConversation(conversationAccessor()))
          s <- tokSessions().toList.flatMap(_._2).headOption
          b = tb.startBroadcast(s,layout)
        } yield {
          List(Extraction.decompose(b))
        }).getOrElse(Nil))
      busArgs(RECEIVE_TOK_BOX_BROADCAST,jBroadcast)
    },Full(METLBUS_CALL))
    def updateBroadcastLayout(tokSessions:() => scala.collection.mutable.HashMap[String,Option[TokBoxSession]],conversationAccessor:() => Conversation) = ClientSideFunction("updateBroadcastLayout",List("id","newLayout"),(args) => {
      val id = getArgAsString(args(0))
      val layout = getArgAsString(args(1))
      val jBroadcast = JArray((for {
          tb <- Globals.tokBox
          if (shouldModifyConversation(conversationAccessor()))
          s <- tokSessions().toList.flatMap(_._2).headOption
          a = tb.updateBroadcast(s,id,layout)
        } yield {
          List(Extraction.decompose(a))
        }).getOrElse(Nil))
      busArgs(RECEIVE_TOK_BOX_BROADCAST,jBroadcast)
    },Full(METLBUS_CALL))
    def stopBroadcast(tokSessions:() => scala.collection.mutable.HashMap[String,Option[TokBoxSession]],conversationAccessor:() => Conversation) = ClientSideFunction("stopBroadcast",List.empty[String],(args) => {
      val jBroadcast = JArray((for {
        tb <- Globals.tokBox
        if (shouldModifyConversation(conversationAccessor()))
        s <- tokSessions().toList.flatMap(_._2).headOption
        b <- tb.getBroadcast(s)
        a = tb.stopBroadcast(s,b.id)
      } yield {
        List(Extraction.decompose(a))
      }).getOrElse(Nil))
      busArgs(RECEIVE_TOK_BOX_BROADCAST,jBroadcast) 
    },Full(METLBUS_CALL))
    def getBroadcast(tokSessions:() => scala.collection.mutable.HashMap[String,Option[TokBoxSession]]) = ClientSideFunction("getBroadcast",List.empty[String],(args) => {
      val jBroadcast = JArray((for {
        tb <- Globals.tokBox
        s <- tokSessions().toList.flatMap(_._2).headOption
        a <- tb.getBroadcast(s)
      } yield {
        List(Extraction.decompose(a))
      }).getOrElse(Nil))
      busArgs(RECEIVE_TOK_BOX_BROADCAST,jBroadcast) 
    },Full(METLBUS_CALL))
  }

  object CommonFunctions {
    def sendStanza(stanzaFunc:MeTLStanza=>Unit):ClientSideFunction = ClientSideFunction("sendStanza",List("stanza"),(args) => {
      val jVal = getArgAsJValue(args(0))
      val stanza = serializer.toMeTLData(jVal)
      stanza match {
        case m:MeTLStanza => {
          trace("sendStanza: %s".format(stanza.toString))
          stanzaFunc(m)
        }
        case notAStanza => {}
      }
      Nil
    },Empty)

    def getActiveProfile = ClientSideFunction("getActiveProfile",List(),(args) => {
      busArgs(RECEIVE_ACTIVE_PROFILE,jProfile)
    },Full(METLBUS_CALL))
    def getAccount = ClientSideFunction("getAccount",List(),(args) => {
      busArgs(RECEIVE_ACCOUNT,jAccount)
    },Full(METLBUS_CALL))
    def getProfile(profileAccessor:()=>Profile) = ClientSideFunction("getProfile",List(),(args) => {
      busArgs(RECEIVE_PROFILE,renderProfile(profileAccessor()))
    },Full(METLBUS_CALL))
    def getProfiles = ClientSideFunction("getProfiles",List(),(args) => {
      busArgs(RECEIVE_PROFILES,JArray(Globals.availableProfiles.is.map(renderProfile _)))
    },Full(METLBUS_CALL))
    def getDefaultProfile = ClientSideFunction("getDefaultProfile",List(),(args) => {
      busArgs(RECEIVE_DEFAULT_PROFILE,JString(serverConfig.getProfileIds(Globals.currentAccount.name,Globals.currentAccount.provider)._2))
    },Full(METLBUS_CALL))
    def getProfilesById:ClientSideFunction = ClientSideFunction("getProfilesByIds",List("profileIds"),(args) => {
      busArgs(RECEIVE_PROFILES,translateProfileIds(getArgAsListOfStrings(args(0))))
    },Full(METLBUS_CALL))
    def createProfile:ClientSideFunction = ClientSideFunction("createProfile",List(),(args) => {
      val orig = Globals.currentProfile.is
      val newName = "%s_%s_%s".format(Globals.currentAccount.provider,Globals.currentAccount.name,nextFuncName)
      val prof = serverConfig.createProfile(newName,Map(
            "createdByUser" -> Globals.currentAccount.name,
            "createdByProvider" -> Globals.currentAccount.provider,
            "autocreatedProfile" -> "false",
            "avatarUrl" -> ""))
      serverConfig.updateAccountRelationship(Globals.currentAccount.name,Globals.currentAccount.provider,prof.id,false,false)
      val allProfiles = prof :: Globals.availableProfiles.is
      Globals.availableProfiles(allProfiles)
      debug("created new profile: %s".format(prof))
      busArgs(RECEIVE_PROFILES,JArray(allProfiles.map(renderProfile _)))
    },Full(METLBUS_CALL))
    def switchToProfile:ClientSideFunction = ClientSideFunction("switchToProfile",List("profileId"),(args) => {
      val profId = getArgAsString(args(0)).trim
      val prof = Globals.availableProfiles.find(_.id == profId).map(profile => {
        Globals.currentProfile(profile)
        renderProfile(profile)
      }).getOrElse({
        renderProfile(Globals.currentProfile.is)
      })
      busArgs(RECEIVE_ACTIVE_PROFILE,prof)
    },Full(METLBUS_CALL))
    def setDefaultProfile:ClientSideFunction = ClientSideFunction("setDefaultProfile",List("profileId"),(args) => {
      val profId = getArgAsString(args(0)).trim
      val updatedProfId = Globals.availableProfiles.find(_.id == profId).map(profile => {
        serverConfig.updateAccountRelationship(Globals.currentAccount.name,Globals.currentAccount.provider,profile.id,false,true)
        profId
      }).getOrElse({
        serverConfig.getProfileIds(Globals.currentAccount.name,Globals.currentAccount.provider)._2 
      })
      busArgs(RECEIVE_DEFAULT_PROFILE,JString(updatedProfId))
    },Full(METLBUS_CALL))
    def changeProfileNickname(profileAccessor:() => Profile, updateProfile:Profile => Profile):ClientSideFunction = ClientSideFunction("changeProfileNickname",List("newName"),(args) => {
      val newName = getArgAsString(args(0)).trim
      val thisProfile = profileAccessor()
      busArgs(RECEIVE_PROFILE,renderProfile(updateProfile(thisProfile.copy(name = newName))))
    },Full(METLBUS_CALL))
    def changeProfileAttribute(profileAccessor:() => Profile, updateProfile:Profile => Profile):ClientSideFunction = ClientSideFunction("changeAttribute",List("key","value"),(args) => {
      val k = getArgAsString(args(0)).trim
      val v = getArgAsString(args(1)).trim
      val thisProfile = profileAccessor()
      busArgs(RECEIVE_PROFILE,renderProfile(updateProfile(thisProfile.copy(attributes = thisProfile.attributes.updated(k,v)))))
    },Full(METLBUS_CALL))
    def getProfileSessionHistory(profileAccessor:() => Profile):ClientSideFunction = ClientSideFunction("getProfileSessionHistory",Nil,(args) => {
      busArgs(RECEIVE_SESSION_HISTORY,Extraction.decompose(serverConfig.getSessionsForProfile(profileAccessor().id)))
    },Full(METLBUS_CALL))
    def getAttendancesForProfile(profileAccessor:() => Profile):ClientSideFunction = ClientSideFunction("getAttendances",Nil,(args) => {
      busArgs(RECEIVE_ATTENDANCES,JArray(serverConfig.getAttendancesByAuthor(profileAccessor().id).map(serializer.fromMeTLAttendance _)))
    },Full(METLBUS_CALL))
    def getThemesForProfile(profileAccessor:() => Profile):ClientSideFunction = ClientSideFunction("getThemes",Nil,(args) => {
      busArgs(RECEIVE_THEMES,JArray(serverConfig.getThemesByAuthor(profileAccessor().id).map(t => Extraction.decompose(t))))
    },Full(METLBUS_CALL))
    def getConversationsCreatedByCurrentProfile(profileAccessor:() => Profile):ClientSideFunction = ClientSideFunction("getConversations",Nil,(args) => {
      busArgs(RECEIVE_CONVERSATIONS,JArray(serverConfig.getConversationsByAuthor(profileAccessor().id).map(serializer.fromConversation _)))
    },Full(METLBUS_CALL))
    def getUserGroups:ClientSideFunction = ClientSideFunction("getUserGroups",List.empty[String],(args) => busArgs(RECEIVE_USER_GROUPS,jUserGroups),Full(METLBUS_CALL))
    def getUsername:ClientSideFunction = ClientSideFunction("getUsername",List.empty[String],(unused) => busArgs(RECEIVE_USERNAME,jUsername),Full(METLBUS_CALL))
    def getSlide = ClientSideFunction("getSlide",List("jid"),(args) => {
      val jid = getArgAsString(args(0))
      busArgs(RECEIVE_SLIDE_DETAILS,serializer.fromSlide(serverConfig.detailsOfSlide(jid)))
    },Full(METLBUS_CALL))
    def getConversation = ClientSideFunction("getConversation",List("jid"),(args) => {
      val jid = getArgAsString(args(0))
      busArgs(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(serverConfig.detailsOfConversation(jid)))
    },Full(METLBUS_CALL))
    def searchForConversation(updateListing:List[Conversation] => List[Conversation],queryUpdater:Option[String]=>Option[String]):ClientSideFunction = ClientSideFunction("getSearchResult",List("query"),(args) => {
      val q = getArgAsString(args(0)).toLowerCase.trim
      queryUpdater(Some(q))
      val foundConversations = serverConfig.searchForConversation(q)
      val results = updateListing(foundConversations)
      busArgs(RECEIVE_CONVERSATIONS,serializer.fromConversationList(results))
    },Full(METLBUS_CALL))
    def createConversation(listingAccessor:() => List[Conversation],updateListing:List[Conversation] => List[Conversation]):ClientSideFunction = ClientSideFunction("createConversation",List("title"),(args) => {
      val title = getArgAsString(args(0))
      debug("Creating conversation: %s".format(title))
      val newConv = serverConfig.createConversation(title,username)
      updateListing((newConv :: listingAccessor()).distinct)
      busArgs(RECEIVE_NEW_CONVERSATION_DETAILS,serializer.fromConversation(newConv))
    },Full(METLBUS_CALL))
    def reorderSlidesOfConversation:ClientSideFunction = ClientSideFunction("reorderSlidesOfCurrentConversation",List("jid","newSlides"),(args) => { //rename this!
      val jid = getArgAsString(args(0))
      val newSlides = getArgAsJArray(args(1))
      trace("reorderSlidesOfConversation(%s,%s)".format(jid,newSlides))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
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
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL)) 

    def deleteConversation:ClientSideFunction = ClientSideFunction("deleteConversation",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          trace("deleting conversation %s".format(c.jid))
          serverConfig.deleteConversation(c.jid.toString)
        }
        case _ => {
          trace("refusing to delete conversation %s".format(c.jid))
          c
        }
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL))
    def renameConversation:ClientSideFunction = ClientSideFunction("renameConversation",List("jid","newTitle"),(args) => {
      val jid = getArgAsString(args(0))
      val newTitle = getArgAsString(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      var jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.renameConversation(c.jid.toString,newTitle)
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL))
    def changeSubjectOfConversation:ClientSideFunction = ClientSideFunction("changeSubjectOfConversation",List("conversationJid","newSubject","newRelationshipSystem","newRelationshipKey"),(args) => {
      val jid = getArgAsString(args(0))
      val newSubject = getArgAsString(args(1))
      val newRelationshipSystem = tryo(getArgAsString(args(2)))
      val newRelationshipKey = tryo(getArgAsString(args(3)))
      var c = serverConfig.detailsOfConversation(jid)
      busArgs(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(if (shouldModifyConversation(c) && (newSubject.toLowerCase == "unrestricted" || newSubject.toLowerCase == username || userGroups.exists(_.name == newSubject))){
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
      },username,userGroups)))
    },Full(METLBUS_CALL))
    def addSlideToConversationAtIndex:ClientSideFunction = ClientSideFunction("addSlideToConversationAtIndex",List("jid","index","slideType"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val slideType = args.drop(2).headOption.map(a => getArgAsString(a)).getOrElse("SLIDE")
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.addSlideAtIndexOfConversation(c.jid,index,slideType)
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL))
    def duplicateSlideById = ClientSideFunction("duplicateSlideById",List("jid","slideId"),(args) => {
      val jid = getArgAsString(args(0))
      val slideId = getArgAsString(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => StatelessHtml.duplicateSlideInternal(username,slideId.toString,c.jid.toString).getOrElse(c)
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL))
    def duplicateConversation = ClientSideFunction("duplicateConversation",List("jid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => StatelessHtml.duplicateConversationInternal(username,c.jid.toString).openOr(c)
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_NEW_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL))
    def changeExposureOfSlide = ClientSideFunction("changeExposureOfSlide",List("jid","slideId","exposed"),(args) => {
      val jid = getArgAsString(args(0))
      val slideId = getArgAsString(args(1))
      val exposed = getArgAsBool(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          serverConfig.updateConversation(c.jid.toString,c.copy(slides = c.slides.find(_.id == slideId).map(_.copy(exposed = exposed)).toList ::: c.slides.filterNot(_.id == slideId)))
        }
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL))
    def getHistory(historyFunc:String => History) = ClientSideFunction("getHistory",List("slide"),(args)=> {
      val jid = getArgAsString(args(0))
      trace("getHistory requested")
      busArgs(RECEIVE_HISTORY,serializer.fromHistory(historyFunc(jid)))
    },Full(METLBUS_CALL))
    def getUserOptions = ClientSideFunction("getUserOptions",List.empty[String],(args) => {
      busArgs(RECEIVE_USER_OPTIONS,JString("not yet implemented"))
    },Full(METLBUS_CALL))
    def setUserOptions = ClientSideFunction("setUserOptions",List("newOptions"),(args) => {
      busArgs(RECEIVE_USER_OPTIONS,JString("not yet implemented"))
    },Full(METLBUS_CALL))
  }
}

class MeTLAccount extends MeTLActorBase[MeTLAccount]{
  import net.liftweb.json.Extraction
  override def registerWith = MeTLAccountActorManager
  override lazy val functionDefinitions = List(
    CommonFunctions.getAccount,
    CommonFunctions.getProfiles,
    CommonFunctions.getDefaultProfile,
    CommonFunctions.getActiveProfile,
    CommonFunctions.createProfile,
    CommonFunctions.switchToProfile,
    CommonFunctions.setDefaultProfile
  )
  override def lifespan = Globals.searchActorLifespan
  override def localSetup = {
    warn("localSetup for MeTLAccount [%s]".format(name))
    super.localSetup
  }
  override def render = OnLoad(
    Call("getAccount") &
    Call("getProfiles") &
    Call("getDefaultProfile") &
    Call("getActiveProfile")
  )
  override def lowPriority = {
    case p:Profile if Globals.availableProfiles.is.exists(_.id == p.id) => {
      partialUpdate(busCall(RECEIVE_PROFILES,JArray(Globals.availableProfiles.is.map(renderProfile _))) & {
        if (serverConfig.getProfileIds(Globals.currentAccount.name,Globals.currentAccount.provider)._2 == p.id){
          busCall(RECEIVE_ACTIVE_PROFILE,renderProfile(p))
          busCall(RECEIVE_DEFAULT_PROFILE,JString(p.id))
        } else {
          Noop
        }
      })
    }
    case _ => warn("MeTLAccountActor received unknown message")
  }
}

class MeTLProfile extends MeTLActorBase[MeTLProfile] {
  import net.liftweb.json.Extraction
  override def registerWith = MeTLProfileActorManager
  protected var internalProfile:Option[Profile] = None
  protected def thisProfile = internalProfile.getOrElse(profile)
  def profileAccessor:Profile = thisProfile
  protected def updateProfile(p:Profile):Profile = {
    warn("updating profile: %s".format(p))
    val orig = thisProfile
    val prof = serverConfig.updateProfile(p.id,p)
    internalProfile = internalProfile.map(_p => prof)
    if (Globals.availableProfiles.exists(_.id == prof.id)){
      warn("updating availables with this new one: %s".format(prof))
      Globals.availableProfiles(prof :: (Globals.availableProfiles.is.filterNot(_.id == prof.id)))
    }
    if (Globals.currentProfile.is.id == prof.id){
      warn("updating current with this new one: %s".format(prof))
      Globals.currentProfile(prof)
    }
    warn("comparing: %s => %s".format(orig,prof))
    if (prof.name != orig.name || prof.attributes != orig.attributes){
      warn("notifying other actors of this new one: %s".format(prof))
      MeTLProfileActorManager ! prof
      MeTLAccountActorManager ! prof
    }
    prof
  }
  override lazy val functionDefinitions = List(
    CommonFunctions.getAccount,
    CommonFunctions.getProfiles,
    CommonFunctions.getDefaultProfile,
    CommonFunctions.getActiveProfile,
    CommonFunctions.getProfile(() => thisProfile),
    CommonFunctions.changeProfileNickname(profileAccessor _,updateProfile _),
    CommonFunctions.changeProfileAttribute(profileAccessor _,updateProfile _),
    CommonFunctions.getProfileSessionHistory(profileAccessor _),
    CommonFunctions.getAttendancesForProfile(profileAccessor _),
    CommonFunctions.getThemesForProfile(profileAccessor _),
    CommonFunctions.getConversationsCreatedByCurrentProfile(profileAccessor _)
  )

  override def lifespan = Globals.searchActorLifespan
  override def localSetup = {
    internalProfile = name.flatMap(n => getProfileIdFromName(n)).flatMap(pid => Globals.availableProfiles.is.find(_.id == pid))
    warn("localSetup for MeTLProfile [%s]".format(name))
    super.localSetup
  }
  override def render = OnLoad(
    Call("getAccount") &
    Call("getProfiles") &
    Call("getDefaultProfile") &
    Call("getActiveProfile") &
    Call("getProfile") & 
    Call("getProfileSessionHistory") &
    Call("getConversations") &
    Call("getAttendances") &
    Call("getThemes")
  )
  override def lowPriority = {
    case p:Profile if Globals.currentProfile.is.id == p.id => {
      println("profile received and it's current: %s".format(p))
      partialUpdate(
        busCall(RECEIVE_ACTIVE_PROFILE,renderProfile(p)) &
        { if (p.id == thisProfile.id){
            internalProfile = internalProfile.map(_p => p)
            busCall(RECEIVE_PROFILE,renderProfile(thisProfile)) &
            busCall(RECEIVE_SESSION_HISTORY,Extraction.decompose(serverConfig.getSessionsForProfile(thisProfile.id)))
          } else {
            Noop
          }
        })
    }
    case p:Profile if thisProfile.id == p.id => {
      println("profile received and it's this one: %s".format(p))
      partialUpdate(
        busCall(RECEIVE_PROFILE,renderProfile(thisProfile)) &
        busCall(RECEIVE_SESSION_HISTORY,Extraction.decompose(serverConfig.getSessionsForProfile(thisProfile.id)))
      )
    }
    case _ => warn("MeTLProfileActor received unknown message")
  }
}

class MeTLJsonConversationChooserActor extends MeTLActorBase[MeTLJsonConversationChooserActor] {
  protected var query:Option[String] = None
  protected var listing:List[Conversation] = Nil
  protected var imports:List[ImportDescription] = Nil
  
  protected def updateListing(convs:List[Conversation]):List[Conversation] = {
    listing = filterConversations(convs,true)
    trace("searchingWithQuery: %s => %s : %s".format(query,convs.length,listing.length))
    listing
  }
  protected def listingAccessor:List[Conversation] = listing
  protected def updateQuery(q:Option[String]):Option[String] = {
    query = q
    query
  }
  override lazy val functionDefinitions = List(
    CommonFunctions.getAccount,
    CommonFunctions.getProfiles,
    CommonFunctions.getDefaultProfile,
    CommonFunctions.getActiveProfile,
    CommonFunctions.getUserGroups,
    CommonFunctions.getUsername,
    CommonFunctions.searchForConversation(updateListing _,updateQuery _),
    CommonFunctions.createConversation(listingAccessor _,updateListing _)
  )

  override def registerWith = MeTLConversationSearchActorManager
  override def lifespan = Globals.searchActorLifespan

  override def localSetup = {
    val s = new Date().getTime
    warn("localSetup for ConversationSearch [%s] started".format(name))
    query = Some(name.flatMap(nameString => {
      com.metl.snippet.Metl.getQueryFromName(nameString)
    }).getOrElse(Globals.currentProfile.is.name.toLowerCase.trim))
    listing = query.toList.flatMap(q => filterConversations(serverConfig.searchForConversation(q),true))
    warn("localSetup for ConversationSearch [%s] (%sms)".format(name,new Date().getTime - s))
    super.localSetup
  }
  override def render = {
    val s = new Date().getTime
    var last = s
    def printSince(m:String):Unit = {
      val now = new Date().getTime
      println("%s [%sms]".format(m,now - last))
      last = now
    }
    val importDescriptions = JArray(imports.map(serialize _))
    printSince("imports")
    val startingList = listing.map(c => refreshForeignRelationship(c,username,userGroups))
    printSince("listing, mapped by foreignRelationship (%s)".format(startingList.length))
    val serList = serializer.fromConversationList(startingList)
    printSince("listing, serialized")
    val thisProfile = renderProfile(Globals.currentProfile.is)
    printSince("profile, serialized")
    val thisGroups = jUserGroups
    printSince("groups, serialized")
    val cmd = OnLoad(
      Call("getAccount") &
      Call("getProfiles") &
      Call("getDefaultProfile") &
      Call("getActiveProfile") &
      busCall(RECEIVE_USERNAME,jUsername) &
      busCall(RECEIVE_USER_GROUPS,thisGroups) &
      busCall(RECEIVE_QUERY,JString(query.getOrElse(""))) &
      busCall(RECEIVE_CONVERSATIONS,serList) &
      busCall(RECEIVE_IMPORT_DESCRIPTIONS,importDescriptions)
    )
    val end = new Date().getTime
    warn("render for ConversationSearch (%sms)".format(end - s))
    cmd
  }
  protected def serialize(id:ImportDescription):JValue = Extraction.decompose(id)

  protected def queryApplies(in:Conversation):Boolean = query.map(q => in.title.toLowerCase.trim.contains(q) || in.author.toLowerCase.trim == q || in.jid.toString == q).getOrElse(false)

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
        partialUpdate(busCall(RECEIVE_IMPORT_DESCRIPTION,serialize(id)))
      }
    }
    case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") => {
      trace("receivedCommand: %s".format(c))
      val newJid = c.commandParameters(0)
      val newConv = refreshForeignRelationship(serverConfig.detailsOfConversation(newJid.toString),username,userGroups)
      listing = filterConversations(List(newConv) ::: listing.filterNot(_.jid == newConv.jid))
      partialUpdate(busCall(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(newConv)))
    }
    case _ => warn("MeTLConversationSearchActor received unknown message")
  }
}

class MeTLEditConversationActor extends MeTLActorBase[MeTLEditConversationActor] {
  import com.metl.view._
  
  override lazy val functionDefinitions = List(
    CommonFunctions.getAccount,
    CommonFunctions.getProfiles,
    CommonFunctions.getDefaultProfile,
    CommonFunctions.getActiveProfile,
    CommonFunctions.getProfilesById,
    CommonFunctions.reorderSlidesOfConversation,
    CommonFunctions.deleteConversation,
    CommonFunctions.renameConversation,
    CommonFunctions.changeSubjectOfConversation,
    CommonFunctions.addSlideToConversationAtIndex,
    CommonFunctions.duplicateSlideById,
    CommonFunctions.duplicateConversation,
    CommonFunctions.changeExposureOfSlide
  )
  override def registerWith = MeTLEditConversationActorManager
  override def lifespan = Globals.editConversationActorLifespan
  protected var conversation:Option[Conversation] = None
  protected var showLinks = true
  override def localSetup = {
    super.localSetup
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      conversation = com.metl.snippet.Metl.getConversationFromName(nameString).map(jid => refreshForeignRelationship(serverConfig.detailsOfConversation(jid),username,userGroups))
      warn("editConversationActor has: %s".format(conversation))
      showLinks = com.metl.snippet.Metl.getLinksFromName(nameString).getOrElse(true)
    })
  }

  override def render = {
    OnLoad(conversation.filter(c => {
      val shouldModify = shouldModifyConversation(c)
      warn("shouldModify returned: %s for %s".format(shouldModify,c))
      shouldModify
    }).map(c => {
      warn("editConversationRendering")
      Call("getAccount") &
      Call("getProfiles") &
      Call("getDefaultProfile") &
      Call("getActiveProfile") &
      busCall(RECEIVE_USERNAME,jUsername) &
      busCall(RECEIVE_USER_GROUPS,jUserGroups) &
      busCall(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(c,username,userGroups))) &
      busCall(RECEIVE_SHOW_CONVERSATION_LINKS,JBool(showLinks))
    }).getOrElse({
      warn("editConversation kicking: %s from %s".format(username,conversation))
      RedirectTo(conversationSearch())
    }))
  }
  override def lowPriority = {
    case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") && c.commandParameters.headOption.exists(cid => conversation.exists(_.jid == cid))  => {
      conversation.foreach(c => {
        val newConv = refreshForeignRelationship(serverConfig.detailsOfConversation(c.jid),username,userGroups)
        trace("receivedUpdatedConversation: %s => %s".format(c,newConv))
        conversation = Some(newConv)
        reRender
      })
    }
    case _ => warn("MeTLEditConversationActor received unknown message")
  }
}

class ConversationSummaryActor extends MeTLActorBase[ConversationSummaryActor] {
  import com.metl.view._
  
  override lazy val functionDefinitions = List(
    CommonFunctions.getAccount,
    CommonFunctions.getProfiles,
    CommonFunctions.getDefaultProfile,
    CommonFunctions.getActiveProfile,
    CommonFunctions.getProfilesById,
    CommonFunctions.reorderSlidesOfConversation,
    CommonFunctions.deleteConversation,
    CommonFunctions.renameConversation,
    CommonFunctions.changeSubjectOfConversation,
    CommonFunctions.addSlideToConversationAtIndex,
    CommonFunctions.duplicateSlideById,
    CommonFunctions.duplicateConversation,
    CommonFunctions.changeExposureOfSlide
  )
  override def registerWith = ConversationSummaryActorManager
  override def lifespan = Globals.editConversationActorLifespan
  protected var conversation:Option[Conversation] = None
  override def localSetup = {
    super.localSetup
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      conversation = com.metl.snippet.Metl.getConversationFromName(nameString).map(jid => refreshForeignRelationship(serverConfig.detailsOfConversation(jid.toString),username,userGroups))
    })
  }

  override def render = {
    OnLoad(conversation.filter(c => shouldDisplayConversation(c)).map(c => {
      Call("getAccount") &
      Call("getProfiles") &
      Call("getDefaultProfile") &
      Call("getActiveProfile") &
      busCall(RECEIVE_USERNAME,jUsername) &
      busCall(RECEIVE_USER_GROUPS,jUserGroups) &
      busCall(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(c,username,userGroups)))
    }).getOrElse(RedirectTo(conversationSearch())))
  }
  override def lowPriority = {
    case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") && c.commandParameters.headOption.exists(cid => conversation.exists(_.jid.toString == cid.toString))  => {
      conversation.foreach(c => {
        val newConv = refreshForeignRelationship(serverConfig.detailsOfConversation(c.jid.toString),username,userGroups)
        trace("receivedUpdatedConversation: %s => %s".format(c,newConv))
        conversation = Some(newConv)
        reRender
      })
    }
    case _ => warn("ConversationSummaryActor received unknown message")
  }
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

class ActivityActor extends MeTLActorBase[ActivityActor]{
  import net.liftweb.json.Extraction
  import net.liftweb.json.DefaultFormats
  private val actorUniqueId = nextFuncName

  override lazy val functionDefinitions = List(
    CommonFunctions.getAccount,
    CommonFunctions.getProfiles,
    CommonFunctions.getDefaultProfile,
    CommonFunctions.getActiveProfile,
    CommonFunctions.getProfilesById,
    CommonFunctions.getUsername,
    CommonFunctions.sendStanza(sendStanzaToServer _),
    ClientSideFunction("joinRoom",List("jid"),(args) => {
      val jid = getArgAsString(args(0))
      joinRoomByJid(jid)
      joinRoomByJid(jid+username)
      busArgs(RECEIVE_HISTORY,serializer.fromHistory(getHistory(jid)))
    },Full(METLBUS_CALL)),
    ClientSideFunction("leaveRoom",List("jid"),(args) => {
      val jid = getArgAsString(args(0))
      leaveRoomByJid(jid)
      leaveRoomByJid(jid+username)
      Nil
    },Empty),
    CommonFunctions.getSlide,
    CommonFunctions.getConversation,
    ClientSideFunction("getCurrentSlide",List(),(args) => {
      busArgs(RECEIVE_SLIDE_DETAILS,serializer.fromSlide(currentSlide.getOrElse(Slide.empty)))
    },Full(METLBUS_CALL)),
    ClientSideFunction("getCurrentConversation",List(),(args) => {
      busArgs(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(currentConversation.getOrElse(Conversation.empty)))
    },Full(METLBUS_CALL)),
    CommonFunctions.addSlideToConversationAtIndex,
    CommonFunctions.getHistory(getHistory _),
    ClientSideFunction("moveToSlide",List("jid"),(args) => {
      val jid = getArgAsString(args(0))
      currentSlide = Some(serverConfig.detailsOfSlide(jid)).filterNot(_ == Slide.empty)
      busArgs(RECEIVE_SLIDE_DETAILS,serializer.fromSlide(currentSlide.getOrElse(Slide.empty)))
    },Full(METLBUS_CALL))
  )
  protected val profiles = scala.collection.mutable.HashMap.empty[String,Profile]
  protected var rooms = Map.empty[Tuple2[String,String],() => MeTLRoom]
  protected lazy val serverName = serverConfig.name
  def registerWith = MeTLActorManager
  
  override def render = {
    OnLoad(
      Call("getUsername") &
      Call("getCurrentConversation") &
      Call("getCurrentSlide") 
    )
  }
  protected def updateProfilesToPage(authors:String *):Unit = {
    val knownAuthors = profiles.keys.toList
    authors.toList.filterNot(a => knownAuthors.contains(a)) match {
      case Nil => {}
      case unknownAuthors => {
        println("updating local profile cache with: %s".format(unknownAuthors))
        val foundAuthors = serverConfig.getProfiles(unknownAuthors:_*)
        profiles ++= foundAuthors.map(a => (a.id,a))
        partialUpdate(busCall(RECEIVE_PROFILES,JObject(foundAuthors.map(a => JField(a.id,renderProfile(a))))))
      }
    }
  }
  override def lowPriority = {
    case roomInfo:RoomStateInformation => updateRooms(roomInfo)
    case metlStanza:MeTLStanza => {
      updateProfilesToPage(metlStanza.author)
      sendMeTLStanzaToPage(metlStanza)
    }
    case HealthyWelcomeFromRoom => {}
    case other => warn("ActivityActor %s received unknown message: %s".format(name,other))
  }
  override def autoIncludeJsonCode = true
  protected var currentConversation:Option[Conversation] = None
  protected var currentSlide:Option[Slide] = None
  override def localSetup = {
    super.localSetup()
    name.foreach(nameString => {
      com.metl.snippet.Metl.getConversationFromName(nameString).foreach(convJid => {
        currentConversation = Some(serverConfig.detailsOfConversation(convJid)).filterNot(_ == Conversation.empty)
        warn("activityActor conversation: %s".format(convJid))
      })
      com.metl.snippet.Metl.getSlideFromName(nameString).map(slideJid => {
        warn("joining specified slide: %s".format(slideJid))
        currentSlide = Some(serverConfig.detailsOfSlide(slideJid)).filterNot(_ == Slide.empty)
      })
    })
  }
  override def localShutdown = {
    rooms.foreach(r => {
      r._2() ! LeaveRoom(username,actorUniqueId,this)
    })
    super.localShutdown()
  }
  protected def getHistory(jid:String):History = {
    val history = RoomMetaDataUtils.fromJid(jid) match {
      case SlideRoom(sJid) => getSlideHistory(sJid)
      case ConversationRoom(cJid) => getConvHistory(cJid)
      case PrivateSlideRoom(psJid,psUser) => MeTLXConfiguration.getRoom(jid,serverName).getHistory
      case other => History.empty
    }
    val authors = history.getAll.map(_.author).distinct
    updateProfilesToPage(authors:_*)
    history
  }
  protected def getConvHistory(jid:String):History = {
    val convHistory = currentConversation.map(cc => MeTLXConfiguration.getRoom(cc.jid,serverName).getHistory).getOrElse(History.empty)
    val allGrades = Map(convHistory.getGrades.groupBy(_.id).values.toList.flatMap(_.sortWith((a,b) => a.timestamp > b.timestamp).headOption.map(g => (g.id,g)).toList):_*)
    val finalHistory = convHistory.filter{
      case g:MeTLGrade => true
      case gv:MeTLGradeValue if shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) => true
      case gv:MeTLGradeValue if allGrades.get(gv.getGradeId).exists(_.visible == true) && gv.getGradedUser == username => true
      case gv:MeTLGradeValue => false
      case qr:MeTLQuizResponse if (qr.author != username && !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))) => false
      case s:MeTLSubmission if (s.author != username && !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))) => false
      case _ => true
    }
    finalHistory
  }
  protected def getSlideHistory(jid:String):History = {
    val pubHistory = MeTLXConfiguration.getRoom(jid,serverName).getHistory
    val privHistory = MeTLXConfiguration.getRoom(jid+username,serverName).getHistory
    val finalHistory = pubHistory.merge(privHistory).filter{
      case g:MeTLGrade => true
      case qr:MeTLQuizResponse if (qr.author != username && !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))) => false
      case s:MeTLSubmission if (s.author != username && !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))) => false
      case _ => true
    }
    debug("final %s".format(jid))
    finalHistory
  }
  protected def joinRoomByJid(jid:String):Unit = {
    var room = MeTLXConfiguration.getRoom(jid,serverName)
    room ! JoinRoom(username,actorUniqueId,this)
  }
  protected def leaveRoomByJid(jid:String):Unit = {
    rooms.find(r => r._1._2 == jid).foreach(r => {
      r._2() ! LeaveRoom(username,actorUniqueId,this)
    })
  }
  protected def updateRooms(roomInfo:RoomStateInformation):Unit = Stopwatch.time("MeTLActor.updateRooms",{
    warn("roomInfo received: %s".format(roomInfo))
    trace("updateRooms: %s".format(roomInfo))
    roomInfo match {
      case RoomJoinAcknowledged(s,r) => {
        trace("joining room: %s".format(r))
        if (rooms.contains((s,r))){
          //don't do anything - you're already in the room
        } else {
          rooms = rooms.updated((s,r),() => MeTLXConfiguration.getRoom(r,s))
          try {
            RoomMetaDataUtils.fromJid(r) match {
              case SlideRoom(sJid) if currentConversation.exists(c => c.slides.exists(_.id == sJid)) => {
                currentConversation.map(c => {
                  warn("trying to send truePresence for slideRoom to conversationRoom: %s %s".format(c.jid,sJid))
                  val room = MeTLXConfiguration.getRoom(c.jid,serverName,ConversationRoom(c.jid))
                  room !  LocalToServerMeTLStanza(Attendance(username,-1L,sJid,true,Nil))
                })
              }
              case ConversationRoom(cJid) => {
                warn("trying to send truePresence for conversationRoom to global: %s".format(cJid))
                val room = MeTLXConfiguration.getRoom("global",s,GlobalRoom)
                room ! LocalToServerMeTLStanza(Attendance(username,-1L,cJid,true,Nil))
              }
              case _ => {}
            }
          } catch {
            case e:Exception => {
              error("failed to send arrivingAttendance to room: (%s,%s) => %s".format(s,r,e.getMessage),e)
            }
          }
        }
      }
      case RoomLeaveAcknowledged(s,r) => {
        if (rooms.contains((s,r))){
          trace("leaving room: %s".format(r))
          try {
            RoomMetaDataUtils.fromJid(r) match {
              case SlideRoom(sJid) if currentConversation.exists(c => c.slides.exists(_.id == sJid)) => {
                currentConversation.map(c => {
                  warn("trying to send falsePresence for slideRoom to conversationRoom: %s %s".format(c.jid,sJid))
                  val room = MeTLXConfiguration.getRoom(c.jid,serverName,ConversationRoom(c.jid))
                  room !  LocalToServerMeTLStanza(Attendance(username,-1L,sJid,false,Nil))
                })
              }
              case ConversationRoom(cJid) => {
                warn("trying to send falsePresence for conversationRoom to global: %s".format(cJid))
                val room = MeTLXConfiguration.getRoom("global",s,GlobalRoom)
                room ! LocalToServerMeTLStanza(Attendance(username,-1L,cJid,false,Nil))
              }
              case _ => {}
            }
          } catch {
            case e:Exception => {
              error("failed to send leavingAttendance to room: (%s,%s) => %s".format(s,r,e.getMessage),e)
            }
          }
          rooms = rooms.filterNot(rm => rm._1 == (s,r))
        } else {
          // don't do anything - you're not in the roo
        }
      }
      case _ => {}
    }
  })
  protected def emit(source:String,value:String,domain:String) = {
    trace("emit triggered by %s: %s,%s,%s".format(username,source,value,domain))
    currentConversation.map(cc => {
      MeTLXConfiguration.getRoom(cc.jid,serverName) ! LocalToServerMeTLStanza(MeTLTheme(username,-1L,cc.jid.toString,Theme(source,value,domain),Nil))
    })
  }
  protected def sendStanzaToServer(stanza:MeTLStanza):Unit  = Stopwatch.time("ActivityActor.sendStanzaToServer (MeTLStanza) (%s)".format(serverName),{
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
          if (username == privateAuthor || shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))){
            val privRoom = MeTLXConfiguration.getRoom(m.slide+privateAuthor,serverName) // rooms.getOrElse((serverName,m.slide+privateAuthor),() => EmptyRoom)()
              privTup._2.foreach(privStanza => {
                trace("OUT TO PRIV -> %s".format(privStanza))
                privRoom ! LocalToServerMeTLStanza(privStanza)
              })
          }
        })
      }
      case fp:ForumPost => {
        if (fp.author == username){
          val roomId = fp.slideId
          rooms.get((serverName,roomId)).map(r => {
            r() ! LocalToServerMeTLStanza(fp)
            Globals.metlingPots.foreach(mp => {
              mp.postItems(List(
                MeTLingPotItem("metlActor",new java.util.Date().getTime(),KVP("metlUser",fp.author),KVP("informalAcademic","forumPost"),Some(KVP("room",fp.slideId)),None,None)
              ))
            })
          })
        }
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
                r() ! LocalToServerMeTLStanza(s.adjustAudience(List(Audience("metl", cc.author, "user", "read"))))
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
          currentSlide.map(cs => {
            val roomId = cs.id
            rooms.get((serverName,roomId)).map(r => r() ! LocalToServerMeTLStanza(qr))
            Globals.metlingPots.foreach(mp => {
              mp.postItems(List(
                MeTLingPotItem("metlActor",new java.util.Date().getTime(),KVP("metlUser",qr.author),KVP("informalAcademic","quizResponse"),Some(KVP("room",cs.id)),Some(KVP("quiz",qr.id)),None)
              ))
            })
          })
        }
      }
      case q:MeTLQuiz => {
        if (q.author == username) {
          currentSlide.map(cs => {
            if (cs.author == username){
              trace("sending quiz: %s".format(q))
              val roomId = cs.id
              rooms.get((serverName,roomId)).map(r => r() ! LocalToServerMeTLStanza(q))
            } else {
              //errorScreen("quiz creation","You are not permitted to create quizzes in this conversation")
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
        if (c.author == username && shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))){
          val conversationSpecificCommands = List("/SYNC_MOVE","/TEACHER_IN_CONVERSATION")
          val slideSpecificCommands = List("/TEACHER_VIEW_MOVED")
          val roomTarget = c.command match {
            case s:String if (conversationSpecificCommands.contains(s)) => currentConversation.map(_.jid).getOrElse("global")
            case s:String if (slideSpecificCommands.contains(s)) => currentSlide.map(_.id).getOrElse("global")
            case _ => "global"
          }
          val alteredCommand = c match {
            case MeTLCommand(author,timestamp,"/SYNC_MOVE",List(jid),audiences) => MeTLCommand(author,timestamp,"/SYNC_MOVE",List(jid,uniqueId),audiences)
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
        val newJid = c.commandParameters(0)
        val newConv = serverConfig.detailsOfConversation(newJid)
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
            partialUpdate(busCall(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(newConv,username,userGroups))))
          }
        }
      }
      case c:MeTLCommand if (c.command == "/SYNC_MOVE") => {
        trace("incoming syncMove: %s".format(c))
        val newJid = c.commandParameters(0)
        val signature = c.commandParameters(1)
        if(uniqueId != signature){//Don't respond to moves that started at this actor
          partialUpdate(busCall(RECEIVE_SYNC_MOVE,JString(newJid),JString(signature)))
        }
      }
      case c:MeTLCommand if (c.command == "/TEACHER_IN_CONVERSATION") => {
        //not relaying teacherInConversation to page
      }
      //case a:Attendance if (shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))) => getAttendance.map(attendances => partialUpdate(busCall(RECEIVE_ATTENDANCE,attendances)))
      case s:MeTLSubmission if !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) && s.author != username => {
        //not sending the submission to the page, because you're not the author and it's not yours
      }
      case qr:MeTLQuizResponse if !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) && qr.author != username => {
        //not sending the quizResponse to the page, because you're not the author and it's not yours
      }
      /*
       case g:MeTLGrade if !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) && !g.visible => {
       //not sending a grade to the page because you're not the author, and this one's not visible
       }
       */
      case gv:MeTLGradeValue => {
        currentConversation.foreach(cc => {
          if (shouldModifyConversation(cc)){
            partialUpdate(busCall(RECEIVE_METL_STANZA,serializer.fromMeTLData(gv)))
          } else {
            if (gv.getGradedUser == username){
              val roomTarget = cc.jid.toString
              rooms.get((serverConfig.name,roomTarget)).map(r => {
                val convHistory = r().getHistory
                val allGrades = Map(convHistory.getGrades.groupBy(_.id).values.toList.flatMap(_.sortWith((a,b) => a.timestamp > b.timestamp).headOption.map(g => (g.id,g)).toList):_*)
                val thisGrade = allGrades.get(gv.getGradeId)
                if (thisGrade.exists(_.visible)){
                  partialUpdate(busCall(RECEIVE_METL_STANZA,serializer.fromMeTLData(gv)))
                }
              })
            }
          }
        })
      }
      case _ => {
        trace("receiving: %s".format(metlStanza))
        val response = serializer.fromMeTLData(metlStanza) match {
          case j:JValue => j
          case other => JString(other.toString)
        }
        partialUpdate(busCall(RECEIVE_METL_STANZA,response))
      }
    }
  })

}


class MeTLActor extends MeTLActorBase[MeTLActor]{
  import net.liftweb.json.Extraction
  import net.liftweb.json.DefaultFormats
  private val actorUniqueId = nextFuncName

  // javascript functions to fire
  private lazy val RECEIVE_CURRENT_CONVERSATION = "receiveCurrentConversation" //tick
  private lazy val RECEIVE_CURRENT_SLIDE = "receiveCurrentSlide"
  private lazy val RECEIVE_QUIZZES = "receiveQuizzes"
  private lazy val RECEIVE_QUIZ_RESPONSES = "receiveQuizResponses"
  private lazy val RECEIVE_IS_INTERACTIVE_USER = "receiveIsInteractiveUser"
  private lazy val RECEIVE_ATTENDANCE = "receiveAttendance"
  private lazy val UPDATE_THUMB = "updateThumb"

  protected var tokSessions:scala.collection.mutable.HashMap[String,Option[TokBoxSession]] = new scala.collection.mutable.HashMap[String,Option[TokBoxSession]]()
  protected var tokSlideSpecificSessions:scala.collection.mutable.HashMap[String,Option[TokBoxSession]] = new scala.collection.mutable.HashMap[String,Option[TokBoxSession]]()
  override lazy val functionDefinitions = List(
    CommonFunctions.getAccount,
    CommonFunctions.getProfiles,
    CommonFunctions.getDefaultProfile,
    CommonFunctions.getActiveProfile,
    CommonFunctions.getProfilesById,
    TokBoxFunctions.getTokBoxArchives(() => tokSessions),
    TokBoxFunctions.getTokBoxArchive(() => tokSessions),
    TokBoxFunctions.removeTokBoxArchive(() => tokSessions),
    TokBoxFunctions.startBroadcast(() => tokSessions,() => currentConversation.getOrElse(Conversation.empty)),
    TokBoxFunctions.updateBroadcastLayout(() => tokSessions,() => currentConversation.getOrElse(Conversation.empty)),
    TokBoxFunctions.stopBroadcast(() => tokSessions,() => currentConversation.getOrElse(Conversation.empty)),
    TokBoxFunctions.getBroadcast(() => tokSessions),
    ClientSideFunction("refreshClientSideState",List.empty[String],(args) => {
      partialUpdate(refreshClientSideStateJs(true))
      Nil
    },Empty),
    CommonFunctions.getHistory(getSlideHistory _),
    CommonFunctions.addSlideToConversationAtIndex,
    CommonFunctions.searchForConversation(cs => cs,q => q),
    ClientSideFunction("getIsInteractiveUser",List.empty[String],(args) => busArgs(RECEIVE_IS_INTERACTIVE_USER,isInteractiveUser.map(iu => JBool(iu)).openOr(JBool(true))),Full(METLBUS_CALL)),
    ClientSideFunction("setIsInteractiveUser",List("isInteractive"),(args) => {
      val isInteractive = getArgAsBool(args(0))
      isInteractiveUser = Full(isInteractive)
      busArgs(RECEIVE_IS_INTERACTIVE_USER,isInteractiveUser.map(iu => JBool(iu)).openOr(JBool(true)))
    },Full(METLBUS_CALL)),
    //CommonFunctions.getUserOptions,
    //CommonFunctions.setUserOptions,
    CommonFunctions.getUserGroups,
    ClientSideFunction("moveToSlide",List("where"),(args) => {
      val where = getArgAsString(args(0))
      debug("moveToSlideRequested(%s)".format(where))
      moveToSlide(where)
      partialUpdate(refreshClientSideStateJs(true))
      Nil
    },Empty),
    ClientSideFunction("joinRoom",List("where"),(args) => {
      val where = getArgAsString(args(0))
      joinRoomByJid(where)
      joinRoomByJid(where+username)
      Nil
    },Empty),
    ClientSideFunction("leaveRoom",List("where"),(args) => {
      val where = getArgAsString(args(0))
      leaveRoomByJid(where)
      leaveRoomByJid(where+username)
      Nil
    },Empty),
    ClientSideFunction("sendStanza",List("stanza"),(args) => {
      val stanza = getArgAsJValue(args(0))
      trace("sendStanza: %s".format(stanza.toString))
      sendStanzaToServer(stanza)
      Nil
    },Empty),
    ClientSideFunction("undeleteStanza",List("stanza"),(args) => {
      val stanza = getArgAsJValue(args(0))
      trace("undeleteStanza: %s".format(stanza.toString))
      val metlData = serializer.toMeTLData(stanza)
      metlData match {
        case m:MeTLCanvasContent => {
          if (m.author == username || shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))){
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
                  val newUDM = MeTLUndeletedCanvasContent(username,0L,dc.target,dc.privacy,dc.slide,"%s_%s_%s".format(new Date().getTime(),dc.slide,username),(stanza \ "type").extract[Option[String]].getOrElse("unknown"),dc.identity,newIdentity,Nil)
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
      Nil
    },Empty),
    ClientSideFunction("sendTransientStanza",List("stanza"),(args) => {
      val stanza = getArgAsJValue(args(0))
      sendStanzaToServer(stanza,"loopback")
      Nil
    },Empty),
    ClientSideFunction("getRooms",List.empty[String],(unused) => List(JString("receiveRoomListing"),JArray(rooms.map(kv => JObject(List(JField("server",JString(kv._1._1)),JField("jid",JString(kv._1._2)),JField("room",JString(kv._2.toString))))).toList)),Full(METLBUS_CALL)),
    ClientSideFunction("getUser",List.empty[String],(unused) => List(JString(RECEIVE_USERNAME),JString(username)),Full(METLBUS_CALL)),
    ClientSideFunction("changePermissionsOfConversation",List("jid","newPermissions"),(args) => {
      val jid = getArgAsString(args(0))
      val newPermissions = getArgAsJValue(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.changePermissions(c.jid.toString,serializer.toPermissions(newPermissions))
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL)),
    ClientSideFunction("changeBlacklistOfConversation",List("jid","newBlacklist"),(args) => {
      val jid = getArgAsString(args(0))
      val rawBlacklist = getArgAsListOfStrings(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.updateConversation(c.jid.toString,c.copy(blackList = rawBlacklist))
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL)),
    ClientSideFunction("banContent",List("conversationJid","slideJid","inkIds","textIds","multiWordTextIds","imageIds","videoIds"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val slideJid = getArgAsString(args(1))
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
          val newStanza = MeTLInk("blacklist",-1,0.0,0.0,bounds,color,thickness,true,"presentationSpace",Privacy.PUBLIC,slideJid.toString,"",Nil,1.0,1.0)
          annotationHistory.addStanza(newStanza)

        })
        texts.foreach(text => {
          val color = coloredAuthors(text.author).highlight
          val bounds = List(Point(text.left,text.top,thickness),Point(text.right,text.top,thickness),Point(text.right,text.bottom,thickness),Point(text.left,text.bottom,thickness),Point(text.left,text.top,thickness))
          val newStanza = MeTLInk("blacklist",-1,0.0,0.0,bounds,color,thickness,true,"presentationSpace",Privacy.PUBLIC,slideJid.toString,"",Nil,1.0,1.0)
          annotationHistory.addStanza(newStanza)
        })
        multiWordTexts.foreach(text => {
          val color = coloredAuthors(text.author).highlight
          val bounds = List(Point(text.left,text.top,thickness),Point(text.right,text.top,thickness),Point(text.right,text.bottom,thickness),Point(text.left,text.bottom,thickness),Point(text.left,text.top,thickness))
          val newStanza = MeTLInk("blacklist",-1,0.0,0.0,bounds,color,thickness,true,"presentationSpace",Privacy.PUBLIC,slideJid.toString,"",Nil,1.0,1.0)
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
            val submission = MeTLSubmission(username,now,title,slideJid,uri,Full(imageBytes),blacklistedPeople,"bannedcontent")
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
        val deleter = MeTLMoveDelta(username,now,"presentationSpace",Privacy.PUBLIC,slideJid.toString,deleterId,0.0,0.0,inkIds,textIds,multiWordTextIds,imageIds,videoIds,0.0,0.0,0.0,0.0,Privacy.NOT_SET,true)
        rooms.get((server,slideJid.toString)).map(r =>{
          r() ! LocalToServerMeTLStanza(deleter)
        })
      }
      Nil
    },Empty),
    ClientSideFunction("overrideAllocation",List("conversationJid","slideObject"),(args) => {
      debug("Override allocation: %s".format(args))
      val newSlide = serializer.toSlide(getArgAsJValue(args(1)))
      val c = serverConfig.detailsOfConversation(getArgAsString(args(0)))
      debug("Parsed values: %s".format(newSlide,c))
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.updateConversation(c.jid.toString,c.copy(slides = newSlide :: c.slides.filterNot(_.id == newSlide.id)))
        case false => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL)),
    ClientSideFunction("addGroupSlideToConversationAtIndex",List("jid","index","grouping","initialGroups","parameter"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val initialGroups = args(3) match {
        case JArray(groups) => groups.map(getArgAsListOfStrings _).map(members => com.metl.data.Group(nextFuncName,jid,new Date().getTime,members))
        case _ => Nil
      }
      val c = serverConfig.detailsOfConversation(jid)
      val grouping = getArgAsString(args(2)) match {
        case "byTotalGroups" => com.metl.data.GroupSet(nextFuncName,jid,ByTotalGroups(getArgAsInt(args(4))),initialGroups)
        case "byMaximumSize" => com.metl.data.GroupSet(nextFuncName,jid,ByMaximumSize(getArgAsInt(args(4))),initialGroups)
        case "groupsOfOne" => com.metl.data.GroupSet(nextFuncName,jid,OnePersonPerGroup,initialGroups)
        case _ => com.metl.data.GroupSet(nextFuncName,jid,ByMaximumSize(4),initialGroups)
      }
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => serverConfig.addGroupSlideAtIndexOfConversation(c.jid.toString,index,grouping)
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL)),
    ClientSideFunction("addImageSlideToConversationAtIndex",List("jid","index","resourceId","caption"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val resourceId = getArgAsString(args(2))
      val captionArg = getArgAsJValue(args(3))
      val caption = serializer.toMeTLMultiWordText(captionArg)
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid,index,"SLIDE")
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            val bytes = serverConfig.getResource(resourceId)
            val now = new java.util.Date().getTime
            val identity = "%s%s".format(username,now.toString)
            val tempSubImage = MeTLImage(username,now,identity,Full(resourceId),Full(bytes),Empty,Double.NaN,Double.NaN,10,10,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
            val dimensions = slideRoom.slideRenderer.measureImage(tempSubImage)
            val subImage = MeTLImage(username,now,identity,Full(resourceId),Full(bytes),Empty,dimensions.width,dimensions.height,dimensions.left,dimensions.top,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
            slideRoom ! LocalToServerMeTLStanza(subImage)
            slideRoom ! LocalToServerMeTLStanza(caption.copy(slide=ho.id.toString))
          })
          newC
        }
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL)),

    ClientSideFunction("addSubmissionSlideToConversationAtIndex",List("jid","index","submissionId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val submissionId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid,index,"SLIDE")
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            MeTLXConfiguration.getRoom(jid,server).getHistory.getSubmissions.find(sub => sub.identity == submissionId).map(sub => {
              val now = new java.util.Date().getTime
              val identity = "%s%s".format(username,now.toString)
              val tempSubImage = MeTLImage(username,now,identity,Full(sub.url),sub.imageBytes,Empty,Double.NaN,Double.NaN,10,10,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
              val dimensions = slideRoom.slideRenderer.measureImage(tempSubImage)
              val subImage = MeTLImage(username,now,identity,Full(sub.url),sub.imageBytes,Empty,dimensions.width,dimensions.height,dimensions.left,dimensions.top,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
              slideRoom ! LocalToServerMeTLStanza(subImage)
            })
          })
          newC
        }
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL)),
    ClientSideFunction("addQuizViewSlideToConversationAtIndex",List("jid","index","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val quizId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid,index,"SLIDE")
          newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
            val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
            val convHistory = MeTLXConfiguration.getRoom(jid,server).getHistory
            convHistory.getQuizzes.filter(q => q.id == quizId && !q.isDeleted).sortBy(q => q.timestamp).reverse.headOption.map(quiz => {
              val now = new java.util.Date().getTime
              val identity = "%s%s".format(username,now.toString)
              val genText = (text:String,size:Double,offset:Double,identityModifier:String) => MeTLText(username,now,text,size * 2,320,0,10,10 + offset,identity+identityModifier,"Normal","Arial","Normal",size,"none",identity+identityModifier,"presentationSpace",Privacy.PUBLIC,ho.id.toString,Color(255,0,0,0))
              val quizTitle = genText(quiz.question,16,0,"title")
              val questionOffset = quiz.url match{
                case Full(_) => 340
                case _ => 100
              };
              val quizOptions = quiz.options.foldLeft(List.empty[MeTLText])((acc,item) => {
                acc ::: List(genText("%s: %s".format(item.name,item.text),10,(acc.length * 10) + questionOffset,"option:"+item.name))
              })
              val allStanzas = quiz.url.map(u => List(MeTLImage(username,now,identity+"image",Full(u),Empty,Empty,320,240,10,50,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"image"))).getOrElse(List.empty[MeTLStanza]) ::: quizOptions ::: List(quizTitle)
              allStanzas.foreach(stanza => slideRoom ! LocalToServerMeTLStanza(stanza))
            })
          })
          newC
        }
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv)
    },Full(METLBUS_CALL)),
    ClientSideFunction("addQuizResultsViewSlideToConversationAtIndex",List("jid","index","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val quizId = getArgAsString(args(2))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          val newC = serverConfig.addSlideAtIndexOfConversation(c.jid,index,"SLIDE")
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
              def genText(text:String,size:Double,offset:Double,identityModifier:String,maxHeight:Option[Double] = None) = MeTLText(username,now,text,maxHeight.getOrElse(size * 2),640,0,10,10 + offset,identity+identityModifier,"Normal","Arial","Normal",size,"none",identity+identityModifier,"presentationSpace",Privacy.PUBLIC,ho.id.toString,Color(255,0,0,0))
              val quizTitle = genText(quiz.question,32,0,"title",Some(100))

              val graphWidth = 640
              val graphHeight = 480
              val bytes = com.metl.renderer.QuizRenderer.renderQuiz(quiz,answers.flatMap(_._2).toList,new com.metl.renderer.RenderDescription(graphWidth,graphHeight))
              val quizGraphIdentity = serverConfig.postResource(jid,"graphResults_%s_%s".format(quizId,now),bytes)
              val quizGraph = MeTLImage(username,now,identity+"resultsGraph",Full(quizGraphIdentity),Empty,Empty,graphWidth,graphHeight,10,100,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"resultsGraph")
              val questionOffset = graphHeight + 100
              val quizOptions = quiz.options.foldLeft(List.empty[MeTLText])((acc,item) => {
                acc ::: List(genText(
                  "%s: %s (%s)".format(item.name,item.text,answers.get(item).map(as => as.length).getOrElse(0)),
                  24,
                  (acc.length * 30) + questionOffset,
                  "option:"+item.name))
              })
              val allStanzas = quiz.url.map(u => List(MeTLImage(username,now,identity+"image",Full(u),Empty,Empty,320,240,330,100,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"image"))).getOrElse(List.empty[MeTLStanza]) ::: quizOptions ::: List(quizTitle,quizGraph)
              allStanzas.foreach(stanza => {
                slideRoom ! LocalToServerMeTLStanza(stanza)
              })
            })
          })
          newC
        }
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv) 
    },Full(METLBUS_CALL)),
    ClientSideFunction("reorderSlidesOfCurrentConversation",List("jid","newSlides"),(args) => {
      val jid = getArgAsString(args(0))
      val newSlides = getArgAsJArray(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      val jConv = serializer.fromConversation(refreshForeignRelationship(shouldModifyConversation(c) match {
        case true => {
          (newSlides.arr.length == c.slides.length) match {
            case true => serverConfig.reorderSlidesOfConversation(c.jid.toString,newSlides.arr.map(i => serializer.toSlide(i)).toList)
            case false => c
          }
        }
        case _ => c
      },username,userGroups))
      busArgs(RECEIVE_CONVERSATION_DETAILS,jConv) 
    },Full(METLBUS_CALL)),
    ClientSideFunction("getQuizzesForConversation",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val quizzes = getQuizzesForConversation(jid).map(q => serializer.fromMeTLQuiz(q)).toList
      busArgs(RECEIVE_QUIZZES,JArray(quizzes))
    },Full(METLBUS_CALL)),
    ClientSideFunction("getResponsesForQuizInConversation",List("conversationJid","quizId"),(args) => {
      val jid = getArgAsString(args(0))
      val quizId = getArgAsString(args(1))
      busArgs(RECEIVE_QUIZ_RESPONSES,JArray(getQuizResponsesForQuizInConversation(jid,quizId).map(q => serializer.fromMeTLQuizResponse(q)).toList))
    },Full(METLBUS_CALL)),
    ClientSideFunction("answerQuiz",List("conversationJid","quizId","chosenOptionName"),(args) => {
      val conversationJid = getArgAsString(args(0))
      val quizId = getArgAsString(args(1))
      val chosenOptionName = getArgAsString(args(2))
      val response = MeTLQuizResponse(username,new Date().getTime,chosenOptionName,username,quizId)
      rooms.get((server,conversationJid)).map(r => r() ! LocalToServerMeTLStanza(response))
      Nil
    },Empty),
    ClientSideFunction("getGroupsProviders",Nil,(args) => {
      busArgs("receiveGroupsProviders",JObject(List(
        JField("groupsProviders",JArray(Globals.getGroupsProviders.filter(_.canQuery).map(gp => JObject(List(JField("storeId",JString(gp.storeId)),JField("displayName",JString(gp.name)))))))
      )))
    },Full(METLBUS_CALL)),
    ClientSideFunction("getOrgUnitsFromGroupProviders",List("storeId"),(args) => {
      val sid = getArgAsString(args(0))
      val gp = JObject(List(JField("storeId",JString(sid))) ::: Globals.getGroupsProviders.find(_.storeId == sid).toList.map(gp => JField("displayName",JString(gp.name))))
      busArgs("receiveOrgUnitsFromGroupsProviders",JObject(List(
        JField("groupsProvider",gp),
        JField("orgUnits",JArray(Globals.getGroupsProvider(sid).toList.flatMap(gp => {
          gp.getGroupsFor(Globals.casState.is).map(g => Extraction.decompose(g))
        }).toList))
      )))
    },Full(METLBUS_CALL)),
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
      busArgs("receiveGroupSetsForOrgUnit",JObject(List(
        JField("groupsProvider",gp),
        JField("orgUnit",orgUnitJValue),
        JField("groupSets",groupSets)
      )))
    },Full(METLBUS_CALL)),
    ClientSideFunction("getGroupsForGroupSet",List("storeId","orgUnit","groupSet"),(args) => {
      val sid = getArgAsString(args(0))
      val orgUnitJValue = getArgAsJValue(args(1))
      val orgUnit = orgUnitJValue.extract[OrgUnit]
      val groupSetJValue = getArgAsJValue(args(2))
      val groupSet = groupSetJValue.extract[com.metl.liftAuthenticator.GroupSet]
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
      busArgs("receiveGroupsForGroupSet",JObject(List(
        JField("groupsProvider",gp),
        JField("orgUnit",orgUnitJValue),
        JField("groupSet",groupSetJValue),
        JField("groups",groups)
      )))
    },Full(METLBUS_CALL))
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
      MeTLXConfiguration.getRoom(cc.jid.toString,server) ! LocalToServerMeTLStanza(MeTLTheme(username,-1L,cc.jid.toString,Theme(source,value,domain),Nil))
    })
  }
  private var rooms = Map.empty[Tuple2[String,String],() => MeTLRoom]
  private lazy val server = serverConfig.name
  trace("serverConfig: %s -> %s".format(server,serverConfig))
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
      partialUpdate(busCall(UPDATE_THUMB,JString(slide)))
    }
    case JoinThisSlide(slide) => {
      moveToSlide(slide)
      partialUpdate(refreshClientSideStateJs(true))
    }
    case HealthyWelcomeFromRoom => {}
    case cp@ConversationParticipation(jid,currentMembers,possibleMembers) if shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) => {
      trace("CONVERSATION PARTICIPATION: %s".format(cp))
      partialUpdate(busCall(RECEIVE_ATTENDANCE,JObject(List(
        JField("location",JString(jid)),
        JField("currentMembers",JArray(currentMembers.map(cm => JString(cm)))),
        JField("possibleMembers",JArray(possibleMembers.map(pm => JString(pm))))
      ))))
    }
    case ConversationParticipation(jid,currentMembers,possibleMembers) => {}
    case other => warn("MeTLActor %s received unknown message: %s".format(name,other))
  }
  override def autoIncludeJsonCode = true
  protected var currentConversation:Box[Conversation] = Empty
  protected var currentSlide:Box[String] = Empty
  protected var isInteractiveUser:Box[Boolean] = Empty

  override def localSetup = Stopwatch.time("MeTLActor.localSetup(%s,%s)".format(username,actorUniqueId), {
    super.localSetup()
    debug("created metlactor: %s => %s".format(name,S.session))
    joinRoomByJid("global")
    name.foreach(nameString => {
      warn("localSetup for [%s]".format(name))
      com.metl.snippet.Metl.getConversationFromName(nameString).foreach(convJid => {
        joinConversation(convJid)
      })
      com.metl.snippet.Metl.getSlideFromName(nameString).map(slideJid => {
        warn("joining specified slide: %s".format(slideJid))
        moveToSlide(slideJid)
        slideJid
      }).getOrElse({
        currentConversation.foreach(cc => {
          warn("joining default slide of conversation: %s".format(cc))
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
    val room = MeTLXConfiguration.getRoom(jid,serverName)
    debug("joiningRoom by Jid: %s => %s".format(jid,room.roomMetaData))
    room ! JoinRoom(username,actorUniqueId,this)
  })
  private def leaveRoomByJid(jid:String,serverName:String = server) = Stopwatch.time("MeTLActor.leaveRoomByJid(%s)".format(jid),{
    MeTLXConfiguration.getRoom(jid,serverName) ! LeaveRoom(username,actorUniqueId,this)
  })
  override def localShutdown = Stopwatch.time("MeTLActor.localShutdown(%s,%s)".format(username,actorUniqueId),{
    trace("shutdown metlactor: %s".format(name))
    leaveAllRooms(true)
    super.localShutdown()
  })
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
    val receiveUsername:Box[JsCmd] = Full(busCall(RECEIVE_USERNAME,jUsername))
    val receiveUserProfile:Box[JsCmd] = Full(busCall(RECEIVE_PROFILE,jProfile))
    trace(receiveUsername)
    val receiveUserGroups:Box[JsCmd] = Full(busCall(RECEIVE_USER_GROUPS,jUserGroups))
    trace(receiveUserGroups)
    val receiveCurrentConversation:Box[JsCmd] = currentConversation.map(cc => busCall(RECEIVE_CURRENT_CONVERSATION,JString(cc.jid.toString)))
    trace(receiveCurrentConversation)
    val receiveConversationDetails:Box[JsCmd] = if(refreshDetails) currentConversation.map(cc => busCall(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(cc,username,userGroups)))) else Empty
    trace(receiveConversationDetails)
    val receiveCurrentSlide:Box[JsCmd] = currentSlide.map(cc => busCall(RECEIVE_CURRENT_SLIDE, JString(cc)))
    trace(receiveCurrentSlide)
    val receiveLastSyncMove:Box[JsCmd] = if(shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))){
      Empty
    } else {
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
              case List(cp0) => busCall(RECEIVE_SYNC_MOVE,JString(cp0))
              case List(cp0,cp1) => busCall(RECEIVE_SYNC_MOVE,JString(cp0),JString(cp1.toString))
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
    val receiveTokBoxEnabled:Box[JsCmd] = Full(busCall(TokBoxFunctions.RECEIVE_TOK_BOX_ENABLED,JBool(Globals.tokBox.isDefined)))
    def receiveTokBoxSessionsFunc(tokSessionCol:scala.collection.mutable.HashMap[String,Option[TokBoxSession]]):List[Box[JsCmd]] = tokSessionCol.toList.map(tokSessionTup => {
      val sessionName = tokSessionTup._1
      val tokSession = tokSessionTup._2
      (for {
        cc <- currentConversation
        tb <- Globals.tokBox
        role = shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) match {
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
        val j:JsCmd = busCall(TokBoxFunctions.RECEIVE_TOK_BOX_SESSION_TOKEN,JObject(List(
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
      val j:JsCmd = busCall(TokBoxFunctions.RECEIVE_TOK_BOX_BROADCAST,Extraction.decompose(a))
      j
    })
    trace(receiveLastSyncMove)
    val receiveHistory:Box[JsCmd] = currentSlide.map(cc => busCall(RECEIVE_HISTORY,serializer.fromHistory(getSlideHistory(cc))))
    val receiveInteractiveUser:Box[JsCmd] = isInteractiveUser.map(iu => busCall(RECEIVE_IS_INTERACTIVE_USER,JBool(iu)))
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
    val baseCmds:JsCmd = Call("getAccount") &
    Call("getProfiles") &
    Call("getDefaultProfile") &
    Call("getActiveProfile")

    val jsCmds:List[Box[JsCmd]] = List(receiveUserProfile,receiveUsername,receiveUserGroups,receiveCurrentConversation,receiveCurrentSlide,receiveConversationDetails,receiveLastSyncMove,receiveHistory,receiveInteractiveUser,receiveTokBoxEnabled) ::: receiveTokBoxSlideSpecificSessions ::: receiveTokBoxSessions ::: List(receiveTokBoxBroadcast,loadComplete)
    jsCmds.foldLeft(baseCmds)((acc,item) => item.map(i => acc & i).openOr(acc))
  }
  private def joinConversation(jid:String):Box[Conversation] = {
    val details = serverConfig.detailsOfConversation(jid)
    leaveAllRooms()
    debug("joinConversation: %s".format(details))
    if (shouldDisplayConversation(details)){
      debug("conversation available")
      currentConversation = Full(details)
      val conversationJid = details.jid
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
  private def getSlideHistory(jid:String):History = {
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
      case gv:MeTLGradeValue if shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) => true
      case gv:MeTLGradeValue if allGrades.get(gv.getGradeId).exists(_.visible == true) && gv.getGradedUser == username => true
      case gv:MeTLGradeValue => false
      case qr:MeTLQuizResponse if (qr.author != username && !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))) => false
      case s:MeTLSubmission if (s.author != username && !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))) => false
      case _ => true
    }
    debug("final %s".format(jid))
    trace("final found %s".format(finalHistory.getGradeValues))
    finalHistory
  }
  private def conversationContainsSlideId(c:Conversation,slideId:String):Boolean = c.slides.exists((s:Slide) => s.id == slideId)
  private def moveToSlide(jid:String):Unit = {
    trace("moveToSlide {0}".format(jid))
    trace("CurrentConversation".format(currentConversation))
    trace("CurrentSlide".format(currentSlide))
    val slideId = jid
    currentSlide.filterNot(_ == jid).map(cs => {
      currentConversation.filter(cc => conversationContainsSlideId(cc,slideId)).map(cc => {
        trace("Don't have to leave conversation, current slide is in it")
        //        rooms.get((server,cc.jid.toString)).foreach(r => r ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,cs,false,Nil)))
      }).getOrElse({
        throw new Exception("not in a conversation, but moving to a slide!")
        // why are you not already in a conversation?  I can't guess what you were aiming for
        /*

        trace("Joining conversation for: %s".format(slideId))
        joinConversation(serverConfig.getConversationForSlide(jid))
        */
      })
      leaveRoomByJid(cs)
      leaveRoomByJid(cs+username)
    })
    currentConversation.getOrElse({
      throw new Exception("not in a conversation, but moving to a slide!")
      // why are you not already in a conversation?  I can't guess what you were aiming for
      /*
      trace("Joining conversation for: %s".format(slideId))
      joinConversation(serverConfig.getConversationForSlide(jid))
      */
    })
    currentConversation.map(cc => {
      trace("checking to see that current conv and current slide now line up")
      if (conversationContainsSlideId(cc,slideId)){
        trace("conversation contains slide")
        currentSlide = Full(jid)
        if (cc.author.trim.toLowerCase == username.trim.toLowerCase && isInteractiveUser.map(iu => iu == true).getOrElse(true)){
          val syncMove = MeTLCommand(username,new Date().getTime,"/SYNC_MOVE",List(jid,uniqueId))
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
            partialUpdate(busCall(TokBoxFunctions.REMOVE_TOK_BOX_SESSIONS,JArray(toClose)))
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
        r._2() ! LeaveRoom(username,actorUniqueId,this)
      }
    })
  }
  override def lifespan = Globals.metlActorLifespan

  private def updateRooms(roomInfo:RoomStateInformation):Unit = Stopwatch.time("MeTLActor.updateRooms",{
    warn("roomInfo received: %s".format(roomInfo))
    trace("updateRooms: %s".format(roomInfo))
    roomInfo match {
      case RoomJoinAcknowledged(s,r) => {
        trace("joining room: %s".format(r))
        if (rooms.contains((s,r))){
          //don't do anything - you're already in the room
        } else {
          rooms = rooms.updated((s,r),() => MeTLXConfiguration.getRoom(r,s))
          try {
            RoomMetaDataUtils.fromJid(r) match {
              case SlideRoom(sJid) if currentConversation.exists(c => c.slides.exists(_.id == sJid)) => {
                currentConversation.map(c => {
                  warn("trying to send truePresence for slideRoom to conversationRoom: %s %s".format(c.jid,sJid))
                  val room = MeTLXConfiguration.getRoom(c.jid,server,ConversationRoom(c.jid))
                  room !  LocalToServerMeTLStanza(Attendance(username,-1L,sJid,true,Nil))
                })
              }
              case ConversationRoom(cJid) => {
                warn("trying to send truePresence for conversationRoom to global: %s".format(cJid))
                val room = MeTLXConfiguration.getRoom("global",s,GlobalRoom)
                room ! LocalToServerMeTLStanza(Attendance(username,-1L,cJid,true,Nil))
              }
              case _ => {}
            }
          } catch {
            case e:Exception => {
              error("failed to send arrivingAttendance to room: (%s,%s) => %s".format(s,r,e.getMessage),e)
            }
          }
        }
      }
      case RoomLeaveAcknowledged(s,r) => {
        if (rooms.contains((s,r))){
          trace("leaving room: %s".format(r))
          try {
            RoomMetaDataUtils.fromJid(r) match {
              case SlideRoom(sJid) if currentConversation.exists(c => c.slides.exists(_.id == sJid)) => {
                currentConversation.map(c => {
                  warn("trying to send falsePresence for slideRoom to conversationRoom: %s %s".format(c.jid,sJid))
                  val room = MeTLXConfiguration.getRoom(c.jid,server,ConversationRoom(c.jid))
                  room !  LocalToServerMeTLStanza(Attendance(username,-1L,sJid,false,Nil))
                })
              }
              case ConversationRoom(cJid) => {
                warn("trying to send falsePresence for conversationRoom to global: %s".format(cJid))
                val room = MeTLXConfiguration.getRoom("global",s,GlobalRoom)
                room ! LocalToServerMeTLStanza(Attendance(username,-1L,cJid,false,Nil))
              }
              case _ => {}
            }
          } catch {
            case e:Exception => {
              error("failed to send leavingAttendance to room: (%s,%s) => %s".format(s,r,e.getMessage),e)
            }
          }
          rooms = rooms.filterNot(rm => rm._1 == (s,r))
        } else {
          // don't do anything - you're not in the roo
        }
      }
      case _ => {}
    }
  })
  protected def alertScreen(heading:String,message:String):Unit = {
    partialUpdate(busCall("infoAlert",JString(heading),JString(message)))
  }
  protected def errorScreen(heading:String,message:String):Unit = {
    partialUpdate(busCall("errorAlert",JString(heading),JString(message)))
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
          if (username == privateAuthor || shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))){
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
                r() ! LocalToServerMeTLStanza(s.adjustAudience(List(Audience("metl", cc.author, "user", "read"))))
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
        if (c.author == username && shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))){
          val conversationSpecificCommands = List("/SYNC_MOVE","/TEACHER_IN_CONVERSATION")
          val slideSpecificCommands = List("/TEACHER_VIEW_MOVED")
          val roomTarget = c.command match {
            case s:String if (conversationSpecificCommands.contains(s)) => currentConversation.map(_.jid.toString).getOrElse("global")
            case s:String if (slideSpecificCommands.contains(s)) => currentSlide.getOrElse("global")
            case _ => "global"
          }
          val alteredCommand = c match {
            case MeTLCommand(author,timestamp,"/SYNC_MOVE",List(jid),audiences) => MeTLCommand(author,timestamp,"/SYNC_MOVE",List(jid,uniqueId),audiences)
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
        val newJid = c.commandParameters(0)
        val newConv = serverConfig.detailsOfConversation(newJid)
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
            partialUpdate(busCall(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(refreshForeignRelationship(newConv,username,userGroups))))
          }
        }
      }
      case c:MeTLCommand if (c.command == "/SYNC_MOVE") => {
        trace("incoming syncMove: %s".format(c))
        val newJid = c.commandParameters(0)
        val signature = c.commandParameters(1)
        if(uniqueId != signature){//Don't respond to moves that started at this actor
          partialUpdate(busCall(RECEIVE_SYNC_MOVE,JString(newJid),JString(signature)))
        }
      }
      case c:MeTLCommand if (c.command == "/TEACHER_IN_CONVERSATION") => {
        //not relaying teacherInConversation to page
      }
      //case a:Attendance if (shouldModifyConversation(currentConversation.getOrElse(Conversation.empty))) => getAttendance.map(attendances => partialUpdate(busCall(RECEIVE_ATTENDANCE,attendances)))
      case s:MeTLSubmission if !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) && s.author != username => {
        //not sending the submission to the page, because you're not the author and it's not yours
      }
      case qr:MeTLQuizResponse if !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) && qr.author != username => {
        //not sending the quizResponse to the page, because you're not the author and it's not yours
      }
      /*
       case g:MeTLGrade if !shouldModifyConversation(currentConversation.getOrElse(Conversation.empty)) && !g.visible => {
       //not sending a grade to the page because you're not the author, and this one's not visible
       }
       */
      case gv:MeTLGradeValue => {
        currentConversation.foreach(cc => {
          if (shouldModifyConversation(cc)){
            partialUpdate(busCall(RECEIVE_METL_STANZA,serializer.fromMeTLData(gv)))
          } else {
            if (gv.getGradedUser == username){
              val roomTarget = cc.jid.toString
              rooms.get((serverConfig.name,roomTarget)).map(r => {
                val convHistory = r().getHistory
                val allGrades = Map(convHistory.getGrades.groupBy(_.id).values.toList.flatMap(_.sortWith((a,b) => a.timestamp > b.timestamp).headOption.map(g => (g.id,g)).toList):_*)
                val thisGrade = allGrades.get(gv.getGradeId)
                if (thisGrade.exists(_.visible)){
                  partialUpdate(busCall(RECEIVE_METL_STANZA,serializer.fromMeTLData(gv)))
                }
              })
            }
          }
        })
      }
      case _ => {
        trace("receiving: %s".format(metlStanza))
        val response = serializer.fromMeTLData(metlStanza) match {
          case j:JValue => j
          case other => JString(other.toString)
        }
        partialUpdate(busCall(RECEIVE_METL_STANZA,response))
      }
    }
  })
}
