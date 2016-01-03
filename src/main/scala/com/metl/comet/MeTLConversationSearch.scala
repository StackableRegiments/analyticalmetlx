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

import java.util.Date
import com.metl.renderer.SlideRenderer

import json.JsonAST._

object MeTLConversationActorManager extends LiftActor with ListenerManager {
  def createUpdate = HealthyWelcomeFromRoom
  override def lowPriority = {
    case _ => println("MeTLActorManager received unknown message")
  }
}
class MeTLConversationActor extends StronglyTypedJsonActor{
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
      getSlideHistory(jid)
    },Full(RECEIVE_HISTORY)),
    ClientSideFunctionDefinition("getSearchResult",List("query"),(args) => {
      serializer.fromConversationList(serverConfig.searchForConversation(args(0).toString))
    },Full(RECEIVE_CONVERSATIONS)),
    ClientSideFunctionDefinition("getIsInteractiveUser",List.empty[String],(args) => IsInteractiveUser.map(iu => JBool(iu)).openOr(JBool(true)),Full(RECEIVE_IS_INTERACTIVE_USER)),
    ClientSideFunctionDefinition("setIsInteractiveUser",List("isInteractive"),(args) => {
      val isInteractive = getArgAsBool(args(0))
      IsInteractiveUser(Full(isInteractive))
      IsInteractiveUser.map(iu => JBool(iu)).openOr(JBool(true))
    },Full(RECEIVE_IS_INTERACTIVE_USER)),
    ClientSideFunctionDefinition("getUserOptions",List.empty[String],(args) => JString("not yet implemented"),Full(RECEIVE_USER_OPTIONS)),
    ClientSideFunctionDefinition("setUserOptions",List("newOptions"),(args) => JString("not yet implemented"),Empty),
    ClientSideFunctionDefinition("getUserGroups",List.empty[String],(args) => getUserGroups,Full(RECEIVE_USER_GROUPS)),
    ClientSideFunctionDefinition("getResource",List("source"),(args) => JString("not yet implemented"),Empty),
    ClientSideFunctionDefinition("moveToSlide",List("where"),(args) => {
      val where = getArgAsString(args(0))
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
    ClientSideFunctionDefinition("getRooms",List.empty[String],(unused) => JArray(rooms.map(kv => JObject(List(JField("server",JString(kv._1._1)),JField("jid",JString(kv._1._2)),JField("room",JString(kv._2.toString))))).toList),Full("recieveRoomListing")),
    ClientSideFunctionDefinition("getUser",List.empty[String],(unused) => JString(username),Full(RECEIVE_USERNAME)),
    ClientSideFunctionDefinition("joinConversation",List("where"),(args) => {
      val where = getArgAsString(args(0))
      joinConversation(where).map(c => serializer.fromConversation(c)).openOr(JNull)
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("leaveConversation",List.empty[String],(args) => {
      leaveAllRooms()
      CurrentConversation(Empty)
      JNull
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("createConversation",List("title"),(args) => {
      val title = getArgAsString(args(0))
      serializer.fromConversation(serverConfig.createConversation(title,username))
    },Full(RECEIVE_NEW_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("requestDeleteConversationDialogue",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      this ! SimpleMultipleButtonInteractableMessage("Delete conversation","Are you sure you would like to delete this conversation",
        Map(
          "yes" -> {() => {
            if (shouldModifyConversation(c)){
              serverConfig.deleteConversation(c.jid.toString)
              true
            } else {
              false
            }
          }},
          "no" -> {() => true}
        ),Full(()=> this ! SpamMessage(Text("You are not permitted to delete this conversation"))),false,Full("conversations"))
      JNull
    },Empty),
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
    ClientSideFunctionDefinition("changePermissionsOfConversation",List("jid","newPermissions"),(args) => {
      val jid = getArgAsString(args(0))
      val newPermissions = getArgAsJValue(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => serverConfig.changePermissions(c.jid.toString,serializer.toPermissions(newPermissions))
        case _ => c
      })
    },Full(RECEIVE_CONVERSATION_DETAILS)),
    ClientSideFunctionDefinition("requestChangeSubjectOfConversationDialogue",List("conversationJid"),(args) => {
      val jid = getArgAsString(args(0))
      val c = serverConfig.detailsOfConversation(jid)
      this ! SimpleRadioButtonInteractableMessage("Change sharing","How would you like to share this conversation?",
        Map(Globals.getUserGroups.map(eg => (eg._2.toLowerCase, ()=>{
          if (shouldModifyConversation(c)){
            serverConfig.updateSubjectOfConversation(c.jid.toString.toLowerCase,eg._2)
            true
          } else false
        })).toList:_*),
        Full(c.subject.toLowerCase),Full(()=> this ! SpamMessage(Text("An error occurred while attempting to rename the conversation"))),Full("conversations"))
      JNull
    },Empty),
    ClientSideFunctionDefinition("addSlideToConversationAtIndex",List("jid","index"),(args) => {
      val jid = getArgAsString(args(0))
      val index = getArgAsInt(args(1))
      val c = serverConfig.detailsOfConversation(jid)
      serializer.fromConversation(shouldModifyConversation(c) match {
        case true => serverConfig.addSlideAtIndexOfConversation(c.jid.toString,index)
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
    },Full(RECEIVE_QUIZ_RESPONSES))
  )
  private def getQuizResponsesForQuizInConversation(jid:String,quizId:String):List[MeTLQuizResponse] = {
    rooms.get((server,jid)).map(r => r.getHistory.getQuizResponses.filter(q => q.id == quizId)).map(allQuizResponses => {
      val conversation = serverConfig.detailsOfConversation(jid)
      shouldModifyConversation(conversation) match {
        case true => allQuizResponses
        case _ => allQuizResponses.filter(qr => qr.answerer == username)
      }
    }).getOrElse(List.empty[MeTLQuizResponse])
  }
  private def getQuizzesForConversation(jid:String):List[MeTLQuiz] = {
    val roomOption = rooms.get((server,jid))
    val res = roomOption.map(r => r.getHistory.getQuizzes).getOrElse(List.empty[MeTLQuiz])
    res
  }
  private def getArgAsBool(input:Any):Boolean = input match {
    case JBool(bool) => bool
    case s:String if (s.toString.trim == "false") => false
    case s:String if (s.toString.trim == "true") => true
    case other => false
  }
  private def getArgAsString(input:Any):String = input match {
    case JString(js) => js
    case s:String => s
    case other => other.toString
  }
  private def getArgAsInt(input:Any):Int = input match {
    case JInt(i) => i.toInt
    case i:Int => i
    case JNum(n) => n.toInt
    case d:Double => d.toInt
    case s:String => s.toInt
    case other => other.toString.toInt
  }
  private def getArgAsJValue(input:Any):JValue = input match {
    case jv:JValue => jv
    case other => JNull
  }
  private def getArgAsJArray(input:Any):JArray = input match {
    case l:List[JValue] => JArray(l)
    case ja:JArray => ja
    case other => JArray(List.empty[JValue])
  }

  private var rooms = Map.empty[Tuple2[String,String],MeTLRoom]
  private lazy val serverConfig = ServerConfiguration.default
  private lazy val server = serverConfig.name
  //println("serverConfig: %s -> %s".format(server,serverConfig))
  private def username = Globals.currentUser.is
  private val serializer = new JsonSerializer("frontend")
  def registerWith = MeTLConversationActorManager
  override def render = {
    OnLoad(refreshClientSideStateJs)
  }
  private val defaultContainerId  = "s2cMessageContainer"
  private val clientMessageBroker = new ClientMessageBroker(TemplateHolder.clientMessageTemplate,".s2cMessage",".s2cLabel",".s2cContent",".s2cClose",
    (cm) => {
      partialUpdate(SetHtml(defaultContainerId,cm.renderMessage) & Show(defaultContainerId) & Call("reapplyStylingToServerGeneratedContent",JString(cm.uniqueId)))
    },
    (cm) => {
      partialUpdate(Hide(defaultContainerId) & cm.done)
    }
  )
  override def lowPriority = {
    case roomInfo:RoomStateInformation => Stopwatch.time("MeTLConversationActor.lowPriority.RoomStateInformation", () => updateRooms(roomInfo))
    case metlStanza:MeTLStanza => Stopwatch.time("MeTLConversationActor.lowPriority.MeTLStanza", () => sendMeTLStanzaToPage(metlStanza))
    case c:ClientMessage => clientMessageBroker.processMessage(c)
    case JoinThisSlide(slide) => moveToSlide(slide)
    case HealthyWelcomeFromRoom => {}
    case other => println("MeTLActor received unknown message: %s".format(other))
  }
  override def autoIncludeJsonCode = true
  override def localSetup = Stopwatch.time("MeTLConversationActor.localSetup(%s,%s)".format(username,userUniqueId), () => {
    super.localSetup()
    println("created conversations metlactor")
    //joinRoomByJid(username)
    joinRoomByJid("global")
    // joinRoomByJid("global","loopback")
  })
  private def joinRoomByJid(jid:String,serverName:String = server) = Stopwatch.time("MeTLConversationActor.joinRoomByJid(%s)".format(jid),() => {
    rooms.get((serverName,jid)) match {
      //case None => RoomJoiner ! RoomJoinRequest(jid,username,serverName,userUniqueId,this)
      case _ => {}
    }
  })
  private def leaveRoomByJid(jid:String,serverName:String = server) = Stopwatch.time("MeTLConversationActor.leaveRoomByJid(%s)".format(jid),() => {
    rooms.get((serverName,jid)) match {
      //case Some(s) => RoomJoiner ! RoomLeaveRequest(jid,username,serverName,userUniqueId,this)
      case _ => {}
    }
  })
  override def localShutdown = Stopwatch.time("MeTLConversationActor.localShutdown(%s,%s)".format(username,userUniqueId), () => {
    leaveAllRooms(true)
    super.localShutdown()
  })
  private def getUserGroups = JArray(Globals.getUserGroups.map(eg => JObject(List(JField("type",JString(eg._1)),JField("value",JString(eg._2))))).toList)
  private def refreshClientSideStateJs = {
    CurrentConversation.map(cc => {
      val conversationJid = cc.jid.toString
      joinRoomByJid(conversationJid)
      CurrentSlide.map(cs => {
        joinRoomByJid(cs)
        joinRoomByJid(cs+username)
      })
    })
    //println("Refresh client side state: %s, %s".format(CurrentConversation,CurrentSlide))
    val receiveUsername:Box[JsCmd] = Full(Call(RECEIVE_USERNAME,JString(username)))
    //println(receiveUsername)
    val receiveUserGroups:Box[JsCmd] = Full(Call(RECEIVE_USER_GROUPS,getUserGroups))
    //println(receiveUserGroups)
    val receiveCurrentConversation:Box[JsCmd] = CurrentConversation.map(cc => Call(RECEIVE_CURRENT_CONVERSATION,JString(cc.jid.toString))) match{
      case Full(cc) => Full(cc)
      case _ => Full(Call("showBackstage",JString("conversations")))
    }
    //println(receiveCurrentConversation)
    val receiveConversationDetails:Box[JsCmd] = CurrentConversation.map(cc => Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(cc)))
    //println(receiveConversationDetails)
    val receiveCurrentSlide:Box[JsCmd] = CurrentSlide.map(cc => Call(RECEIVE_CURRENT_SLIDE, JString(cc)))
    //println(receiveCurrentSlide)
    val receiveLastSyncMove:Box[JsCmd] = CurrentConversation.map(cc => {
      //println("receiveLastSyncMove attempting to get room %s, %s".format(cc,server))
      val room = MeTLXConfiguration.getRoom(cc.jid.toString,server)
      //println("receiveLastSyncMove: %s".format(room))
      val history = room.getHistory
      //println("receiveLastSyncMove: %s".format(history))
      history.getLatestCommands.get("/SYNC_MOVE") match{
        case Some(lastSyncMove) =>{
          //println("receiveLastSyncMove found move: %s".format(lastSyncMove))
          Call(RECEIVE_SYNC_MOVE,JString(lastSyncMove.commandParameters(0).toString))
        }
        case _ =>{
          //println("receiveLastSyncMove no move found")
          Noop
        }
      }
    })
    //println(receiveLastSyncMove)
    val receiveHistory:Box[JsCmd] = CurrentSlide.map(cc => Call(RECEIVE_HISTORY,getSlideHistory(cc)))
    val receiveInteractiveUser:Box[JsCmd] = IsInteractiveUser.map(iu => Call(RECEIVE_IS_INTERACTIVE_USER,JBool(iu)))
    //println(receiveInteractiveUser)

    val jsCmds:List[Box[JsCmd]] = List(receiveUsername,receiveUserGroups,receiveCurrentConversation,receiveConversationDetails,receiveCurrentSlide,receiveLastSyncMove,receiveHistory,receiveInteractiveUser)
    jsCmds.foldLeft(Noop)((acc,item) => item.map(i => acc & i).openOr(acc))
  }
  private def joinConversation(jid:String):Box[Conversation] = {
    val details = serverConfig.detailsOfConversation(jid)
    leaveAllRooms()
    //println("joinConversation: %s".format(details))
    if (shouldDisplayConversation(details)){
      //println("conversation available")
      CurrentConversation(Full(details))
      val conversationJid = details.jid.toString
      joinRoomByJid(conversationJid)
//      rooms.get((server,"global")).foreach(r => r ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,conversationJid,true,Nil)))
      //joinRoomByJid(conversationJid,"loopback")
      CurrentConversation
    } else{
      //println("conversation denied: %s, %s.".format(jid,details.subject))
      Empty
    }
  }
  private def getSlideHistory(jid:String):JValue = {
    //println("GetSlideHistory %s".format(jid))
    val convHistory = CurrentConversation.map(cc => MeTLXConfiguration.getRoom(cc.jid.toString,server).getHistory).openOr(History.empty)
    //println("conv %s".format(jid))
    val pubHistory = MeTLXConfiguration.getRoom(jid,server).getHistory
    //println("pub %s".format(jid))
    val privHistory = IsInteractiveUser.map(iu => if (iu){
      MeTLXConfiguration.getRoom(jid+username,server).getHistory
    } else {
      History.empty
    }).openOr(History.empty)
    //println("priv %s".format(jid))
    val finalHistory = pubHistory.merge(privHistory).merge(convHistory)
    //println("final %s".format(jid))
    serializer.fromHistory(finalHistory)
  }
  private def conversationContainsSlideId(c:Conversation,slideId:Int):Boolean = c.slides.exists((s:Slide) => s.id == slideId)
  private def moveToSlide(jid:String):Unit = {
    //println("moveToSlide".format(jid))
    //println("CurrentConversation".format(CurrentConversation.is))
    //println("CurrentSlide".format(CurrentSlide.is))
    val slideId = jid.toInt
    CurrentSlide.filterNot(_ == jid).map(cs => {
      CurrentConversation.filter(cc => conversationContainsSlideId(cc,slideId)).map(cc => {
        //println("Don't have to leave conversation, current slide is in it")
//        rooms.get((server,cc.jid.toString)).foreach(r => r ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,cs,false,Nil)))
      }).getOrElse({
        //println("Joining conversation for",slideId)
        joinConversation(serverConfig.getConversationForSlide(jid))
      })
      leaveRoomByJid(cs)
      leaveRoomByJid(cs+username)
    })
    CurrentConversation.is.getOrElse({
      //println("Joining conversation for",slideId)
      joinConversation(serverConfig.getConversationForSlide(jid))
    })
    CurrentConversation.map(cc => {
      //println("checking to see that current conv and current slide now line up")
      if (conversationContainsSlideId(cc,slideId)){
        //println("conversation contains slide")
        CurrentSlide(Full(jid))
        if (cc.author.trim.toLowerCase == username.trim.toLowerCase && IsInteractiveUser.map(iu => iu == true).getOrElse(true)){
          val syncMove = MeTLCommand(serverConfig,username,new Date().getTime,"/SYNC_MOVE",List(jid))
          rooms.get((server,cc.jid.toString)).map(r => r ! LocalToServerMeTLStanza(syncMove))
        }
        joinRoomByJid(jid)
        joinRoomByJid(jid+username)
        //println("looking for attendance room")
        rooms.get((server,cc.jid.toString)).foreach(r => {
          //println("sending command")
//          r ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,jid,true,Nil))
        })
        //joinRoomByJid(jid,"loopback")
      }
    })
    partialUpdate(refreshClientSideStateJs)
  }
  private def leaveAllRooms(shuttingDown:Boolean = false) = {
    //println("leaving all rooms: %s".format(rooms))
    rooms.foreach(r => {
      if (shuttingDown || (r._1._2 != username && r._1._2 != "global")){
//        CurrentConversation.filter(cc => cc.jid.toString == r._1._2).foreach(cc => r._2 ! LocalToServerMeTLStanza(Attendance(serverConfig,username,-1L,cc.jid.toString,false,Nil)))
        //println("leaving room: %s".format(r))
        r._2 ! LeaveRoom(username,userUniqueId,this)
      }
    })
  }
  override def lifespan = Full(5 minutes)

  private def updateRooms(roomInfo:RoomStateInformation):Unit = Stopwatch.time("MeTLConversationActor.updateRooms", () => {
    //println("roomInfo received: %s".format(roomInfo))
    //println("updateRooms: %s".format(roomInfo))
    roomInfo match {
      case RoomJoinAcknowledged(s,r) => {
        //println("joining room: %s".format(r))
        rooms = rooms.updated((s,r),MeTLXConfiguration.getRoom(r,s))
        try {
          val slideNum = r.toInt
          val conv = serverConfig.getConversationForSlide(r)
          //println("trying to send truePresence to room: %s %s".format(conv,slideNum))
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
        //println("leaving room: %s".format(r))
        try {
          val slideNum = r.toInt
          val conv = serverConfig.getConversationForSlide(r)
          //println("trying to send falsePresence to room: %s %s".format(conv,slideNum))
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
  private def sendStanzaToServer(jVal:JValue,serverName:String = server):Unit  = Stopwatch.time("MeTLConversationActor.sendStanzaToServer (jVal) (%s)".format(serverName), () => {
    serializer.toMeTLData(jVal) match {
      case m:MeTLStanza => sendStanzaToServer(m,serverName)
      case _ => {}
    }
  })
  private def sendStanzaToServer(stanza:MeTLStanza,serverName:String):Unit  = Stopwatch.time("MeTLConversationActor.sendStanzaToServer (MeTLStanza) (%s)".format(serverName), () => {
    //println("OUT -> %s".format(stanza))
    stanza match {
      case m:MeTLMoveDelta => {
        val publicRoom = rooms.getOrElse((serverName,m.slide),EmptyRoom)
        val publicHistory = publicRoom.getHistory
        val privateRoom = rooms.getOrElse((serverName,m.slide+username),EmptyRoom)
        val privateHistory = privateRoom.getHistory
        val (sendToPublic,sendToPrivate) = m.adjustTimestamp(List(privateHistory.getLatestTimestamp,publicHistory.getLatestTimestamp).max + 1).generateChanges(publicHistory,privateHistory)
        sendToPublic.map(pub => {
          //println("OUT TO PUB -> %s".format(pub))
          publicRoom ! LocalToServerMeTLStanza(pub)
        })
        sendToPrivate.map(priv => {
          //println("OUT TO PRIV -> %s".format(priv))
          privateRoom ! LocalToServerMeTLStanza(priv)
        })
      }
      case s:MeTLSubmission => {
        if (s.author == username) {
          CurrentConversation.map(cc => {
            val roomId = cc.jid.toString
            rooms.get((serverName,roomId)).map(r =>{
              //println("sendStanzaToServer sending submission: "+r)
              r ! LocalToServerMeTLStanza(s)
            })
          })
        }
      }
      case qr:MeTLQuizResponse => {
        if (qr.author == username) {
          CurrentConversation.map(cc => {
            val roomId = cc.jid.toString
            rooms.get((serverName,roomId)).map(r => r ! LocalToServerMeTLStanza(qr))
          })
        }
      }
      case q:MeTLQuiz => {
        if (q.author == username) {
          CurrentConversation.map(cc => {
            if (shouldModifyConversation(cc)){
              //println("sending quiz: %s".format(q))
              val roomId = cc.jid.toString
              rooms.get((serverName,roomId)).map(r => r ! LocalToServerMeTLStanza(q))
            } else this ! SpamMessage(Text("You are not permitted to create quizzes in this conversation"),Full("quizzes"))
          })
        }
      }
      case c:MeTLCanvasContent => {
        if (c.author == username){
          CurrentConversation.map(cc => {
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
                    case di:MeTLDirtyInk => di.alterPrivacy(Privacy.PRIVATE)
                    case dt:MeTLDirtyText => dt.alterPrivacy(Privacy.PRIVATE)
                    case di:MeTLDirtyImage => di.alterPrivacy(Privacy.PRIVATE)
                    //case c:MeTLCanvasContent => c.alterPrivacy(Privacy.PRIVATE)
                    case other => other
                  })
                }
              }
              case other => {
                //println("unexpected privacy found in: %s".format(c))
                (false,c.slide,c)
              }
            }
            if (shouldSend){
              rooms.get((serverName,roomId)).map(targetRoom => targetRoom ! LocalToServerMeTLStanza(finalItem))
            }
          })
        } else println("attemped to send a stanza to the server which wasn't yours: %s".format(c))
      }
      case c:MeTLCommand => {
        if (c.author == username){
          val conversationSpecificCommands = List("/SYNC_MOVE","/TEACHER_IN_CONVERSATION")
          val slideSpecificCommands = List("/TEACHER_VIEW_MOVED")
          val roomTarget = c.command match {
            case s:String if (conversationSpecificCommands.contains(s)) => CurrentConversation.map(cc => cc.jid.toString).getOrElse("global")
            case s:String if (slideSpecificCommands.contains(s)) => CurrentSlide.map(cs => cs).getOrElse("global")
            case _ => "global"
          }
          rooms.get((serverName,roomTarget)).map(r => {
            //println("sending MeTLStanza to room: %s <- %s".format(r,c))
            r ! LocalToServerMeTLStanza(c)
          })
        }
      }
      /*
       case s:MeTLStanza => {
       if (s.author == username){
       rooms.get((serverName,"global")).map(r => r ! LocalToServerMeTLStanza(s))
       }
       }
       */
      case other => {
        println("sendStanzaToServer's toMeTLStanza returned unknown type when deserializing: %s".format(other))
      }
    }
  })
  private def sendMeTLStanzaToPage(metlStanza:MeTLStanza):Unit = Stopwatch.time("MeTLConversationActor.sendMeTLStanzaToPage", () => {
    //println("IN -> %s".format(metlStanza))
    metlStanza match {
      case c:MeTLCommand if (c.command == "/UPDATE_CONVERSATION_DETAILS") => {
        val newJid = c.commandParameters(0).toInt
        val newConv = serverConfig.detailsOfConversation(newJid.toString)
        CurrentConversation(CurrentConversation.map(cc => {
          if (cc.jid == newJid){
            newConv
          } else cc
        }))
        //                              println("updating conversation to: %s".format(newConv))
        partialUpdate(Call(RECEIVE_CONVERSATION_DETAILS,serializer.fromConversation(newConv)))
      }
      case c:MeTLCommand if (c.command == "/SYNC_MOVE") => {
        //println("incoming syncMove: %s".format(c))
        val newJid = c.commandParameters(0).toInt
        partialUpdate(Call(RECEIVE_SYNC_MOVE,newJid))
      }
      case c:MeTLCommand if (c.command == "/TEACHER_IN_CONVERSATION") => {
        //not relaying teacherInConversation to page
      }
      case _ => {
        //println("receiving: %s".format(metlStanza))
        val response = serializer.fromMeTLData(metlStanza) match {
          case j:JValue => j
          case other => JString(other.toString)
        }
        partialUpdate(Call(RECEIVE_METL_STANZA,response))
      }
    }
  })
  private def shouldModifyConversation(c:Conversation = CurrentConversation.map(cc => cc).getOrElse(Conversation.empty)):Boolean = {
    username.toLowerCase.trim == c.author.toLowerCase.trim && c != Conversation.empty
  }
  private def shouldDisplayConversation(c:Conversation = CurrentConversation.map(cc => cc).getOrElse(Conversation.empty)):Boolean = {
    (c.subject.toLowerCase == "unrestricted" || Globals.getUserGroups.exists((ug:Tuple2[String,String]) => ug._2.toLowerCase.trim == c.subject.toLowerCase.trim)) && c != Conversation.empty
  }
  private def shouldPublishInConversation(c:Conversation = CurrentConversation.map(cc => cc).getOrElse(Conversation.empty)):Boolean = {
    (shouldModifyConversation(c) || c.permissions.studentsCanPublish) && c != Conversation.empty
  }
}
