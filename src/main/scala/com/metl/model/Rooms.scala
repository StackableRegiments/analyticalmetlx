package com.metl.model

import com.metl.data.{Group => MeTLGroup, _}
import com.metl.utils._
import com.metl.renderer.SlideRenderer

import scala.xml._
import net.liftweb._
import actor._
import common._
import http._
import util._
import util.TimeHelpers
import Helpers._
import java.util.Date

import com.metl.external.{KVP, MeTLingPotItem}
import com.metl.renderer.RenderDescription

import collection.JavaConverters._
import scala.collection.mutable.Queue

import com.metl.external.{MeTLingPotAdaptor,GroupsProvider => ExternalGroupsProvider}

abstract class RoomProvider(config:ServerConfiguration) {
  def get(room:String):MeTLRoom = get(room,RoomMetaDataUtils.fromJid(room,config),false)
  def get(room:String,eternal:Boolean):MeTLRoom = get(room,RoomMetaDataUtils.fromJid(room,config),false)
  def get(room:String,roomDefinition:RoomMetaData):MeTLRoom = get(room,roomDefinition,false)
  def get(jid:String,roomMetaData:RoomMetaData,eternal:Boolean):MeTLRoom
  def removeMeTLRoom(room:String):Unit
  def exists(room:String):Boolean
  def list:List[MeTLRoom]
}

object EmptyRoomProvider extends RoomProvider(ServerConfiguration.empty) {
  override def get(jid:String,roomDefinition:RoomMetaData,eternal:Boolean) = EmptyRoom
  override def removeMeTLRoom(room:String) = {}
  override def exists(room:String) = false
  override def list = Nil
}

object MeTLRoomType extends Enumeration {
  type MeTLRoomType = Value
  val Conversation,Slide,PrivateSlide,PersonalChatroom,Global,Unknown = Value
}

abstract class RoomMetaData(val config:ServerConfiguration, val roomType:MeTLRoomType.Value) {
  def getJid:String
}

case class ConversationRoom(override val config:ServerConfiguration,jid:String) extends RoomMetaData(config,MeTLRoomType.Conversation){
  def cd:Conversation = config.detailsOfConversation(jid.toString)
  override def getJid = jid
}
case class SlideRoom(override val config:ServerConfiguration, jid:String, slideId:Int) extends RoomMetaData(config,MeTLRoomType.Slide){
  def cd:Conversation = config.detailsOfConversation(jid.toString)
  def s:Slide = cd.slides.find(_.id == slideId).getOrElse(Slide.empty)
  override def getJid = slideId.toString
}
case class PrivateSlideRoom(override val config:ServerConfiguration, jid:String, slideId:Int, owner:String) extends RoomMetaData(config,MeTLRoomType.PrivateSlide){
  def cd:Conversation = config.detailsOfConversation(jid.toString)
  def s:Slide = cd.slides.find(_.id == slideId).getOrElse(Slide.empty)
  override def getJid = slideId.toString + owner
}
case class PersonalChatRoom(override val config:ServerConfiguration,owner:String) extends RoomMetaData(config,MeTLRoomType.PersonalChatroom){
  override def getJid = owner
}
case class GlobalRoom(override val config:ServerConfiguration) extends RoomMetaData(config,MeTLRoomType.Global){
  override def getJid = "global"
}
case object UnknownRoom extends RoomMetaData(EmptyBackendAdaptor,MeTLRoomType.Unknown){
  override def getJid = ""
}
object RoomMetaDataUtils {
  protected val numerics = Range.inclusive('0','9').toList
  protected def splitJid(in:String):Tuple2[Int,String] = {
    val first = in.takeWhile(i => numerics.contains(i)).toString
    val second = in.dropWhile(i => numerics.contains(i)).toString
    val jid = try {
      first.toInt
    } catch {
      case e:Exception => 0
    }
    (jid,second)
  }
  def fromJid(jid:String,config:ServerConfiguration = ServerConfiguration.default):RoomMetaData = {
    splitJid(jid) match {
      case (0,"") => UnknownRoom
      case (0,"global") => GlobalRoom(config)
      case (conversationJid,"") if (conversationJid % 1000 == 0) => ConversationRoom(config,conversationJid.toString)
      case (slideJid,"") => SlideRoom(config,(slideJid - (slideJid % 1000)).toString,slideJid)
      case (0,user) => PersonalChatRoom(config,user)
      case (slideJid,user) => PrivateSlideRoom(config,(slideJid - (slideJid % 1000)).toString,slideJid,user)
      case _ => UnknownRoom
    }
  }
}
object ImporterActor {
  val backgroundWorkerName = "serverSideBackgroundWorker"
}

class HistoryCachingRoomProvider(config:ServerConfiguration,idleTimeout:Option[Long]) extends RoomProvider(config) with Logger {
  protected lazy val metlRooms = new java.util.concurrent.ConcurrentHashMap[String,MeTLRoom]
  override def list = metlRooms.values.asScala.toList
  override def exists(room:String):Boolean = Stopwatch.time("Rooms.exists", list.contains(room))
  override def get(room:String,roomDefinition:RoomMetaData,eternal:Boolean) = Stopwatch.time("Rooms.get",metlRooms.computeIfAbsent(room, new java.util.function.Function[String,MeTLRoom]{
    override def apply(r:String) = createNewMeTLRoom(room,roomDefinition,eternal)
  }))
  protected def createNewMeTLRoom(room:String,roomDefinition:RoomMetaData,eternal:Boolean = false) = Stopwatch.time("Rooms.createNewMeTLRoom(%s)".format(room),{
    //val r = new HistoryCachingRoom(configName,room,this,roomDefinition,idleTimeout.filterNot(it => eternal))
    val start = new java.util.Date().getTime
    val a = new java.util.Date().getTime - start
    val r = new XmppBridgingHistoryCachingRoom(config,room,this,roomDefinition,idleTimeout.filterNot(it => eternal))
    val b = new java.util.Date().getTime - start
    r.localSetup
    val c = new java.util.Date().getTime - start
    info("created room %s (%s, %s, %s) %s".format(room,a,b,c,roomDefinition))
    r
  })
  override def removeMeTLRoom(room:String) = Stopwatch.time("Rooms.removeMeTLRoom(%s)".format(room),{
    val r = metlRooms.get(room)
    if (r != null){
      r.localShutdown
      metlRooms.remove(room)
    }
  })
}

case class ServerToLocalMeTLStanza[A <: MeTLStanza](stanza:A)
case class LocalToServerMeTLStanza[A <: MeTLStanza](stanza:A)
case class ArchiveToServerMeTLStanza[A <: MeTLStanza](stanza:A)
abstract class RoomStateInformation
case class RoomJoinAcknowledged(server:String,room:String) extends RoomStateInformation
case class RoomLeaveAcknowledged(server:String,room:String) extends RoomStateInformation
case class JoinRoom(username:String,cometId:String,actor:LiftActor)
case class LeaveRoom(username:String,cometId:String,actor:LiftActor)
case class UpdateThumb(slide:String)
case class ConversationParticipation(roomName:String,currentParticipants:List[String],possibleParticipants:List[String])
case class UpdateConversationDetails(roomName:String)
case class MotherMessage(html:NodeSeq,audiences:List[Audience])

case object HealthyWelcomeFromRoom
case object Ping
case object CheckChunks

abstract class MeTLRoom(configIn:ServerConfiguration,val location:String,creator:RoomProvider,val roomMetaData:RoomMetaData,val idleTimeout:Option[Long],chunker:Chunker = new ChunkAnalyzer) extends LiftActor with ListenerManager with Logger {
  lazy val slideRenderer = new SlideRenderer
  lazy val config = configIn
  protected var shouldBacklog = false
  protected var backlog = Queue.empty[Tuple2[MeTLStanza,Boolean]]
  protected def onConnectionLost:Unit = {
    debug("MeTLRoom(%s):onConnectionLost".format(location))
    shouldBacklog = true
  }
  protected def onConnectionRegained:Unit = {
    debug("MeTLRoom(%s):onConnectionRegained".format(location))
    initialize
    processBacklog
    shouldBacklog = false
  }
  protected def processBacklog:Unit = {
    debug("MeTLRoom(%s):sendToServer.processingBacklog".format(location))
    while (!backlog.isEmpty){
      val (item,shouldUpdateTimestamp) = backlog.dequeue
      trace("MeTLRoom(%s):sendToServer.processingBacklog.dequeue(%s)".format(location,item))
      if (shouldUpdateTimestamp){
        this ! LocalToServerMeTLStanza(item)
      } else {
        this ! ArchiveToServerMeTLStanza(item)
      }
    }
  }
  protected def initialize:Unit = {}
  protected val messageBusDefinition = new MessageBusDefinition(location,"unicastBackToOwner",(s:MeTLStanza) => this ! ServerToLocalMeTLStanza(s),onConnectionLost _,onConnectionRegained _)
  protected val messageBus = config.getMessageBus(messageBusDefinition)

  def getHistory:History
  def getThumbnail:Array[Byte]
  def getSnapshot(size:RenderDescription):Array[Byte]

  protected var conversationCache:Option[Conversation] = roomMetaData match {
    case cr:ConversationRoom => {
      Some(cr.cd)
    }
    case _ => None
  }
  protected def getGroupsProvider(system:String):Option[ExternalGroupsProvider] = Globals.getGroupsProvider(system)
  protected var attendanceCache:List[String] = Nil
  protected def updatePossibleAttendance = {
    var startingAttendanceCache = attendanceCache
    attendanceCache = roomMetaData match {
      case cr:ConversationRoom => {
        conversationCache.flatMap(conv => {
          conv.foreignRelationship.map(fr => {
            (for {
              gp <- getGroupsProvider(fr.system).filter(_.canQuery).toList
              ou <- gp.getOrgUnit(fr.key).toList
            } yield {
              gp.getMembersFor(ou).map(_.name)
            }).flatten
          })
        }).getOrElse({
          (getAttendances.map(_.author) ::: getAttendance).distinct
        })
      }
      case _ => (getAttendances.map(_.author) ::: getAttendance).distinct
    }
    trace("UPDATED POSSIBLE ATTENDANCE!; %s => %s".format(startingAttendanceCache,attendanceCache))
  }
  def getPossibleAttendance:List[String] = attendanceCache.toList
  def getAttendance:List[String] = joinedUsers.map(_._1).distinct
  def getAttendances:List[Attendance] = {
    getHistory.getAttendances
  }
  def getGroupSets:List[GroupSet] = {
    roomMetaData match {
      case cr:ConversationRoom => cr.cd.slides.flatMap(s => s.groupSet).toList
      case s:SlideRoom => s.s.groupSet.toList
      case _ => Nil
    }
  }
  def updateGroupSets:Option[Conversation] = {
    roomMetaData match {
      case cr:ConversationRoom => {
        trace("updating conversationRoom: %s".format(cr))
        val details = cr.cd
        var shouldUpdateConversation = false;
        val a = getAttendances.map(_.author).distinct.filterNot(_ == details.author)
        val newSlides = details.slides.map(slide => {
          slide.copy(groupSet = slide.groupSet.map(gs => {
            val grouped = gs.groups.flatMap(g => g.members).distinct
            val ungrouped = a.filterNot(m => grouped.contains(m))
            if (ungrouped.length > 0){
              trace("ungrouped: %s".format(ungrouped))
              shouldUpdateConversation = true
              ungrouped.foldLeft(gs.copy())((groupSet,person) => {
                if(person == details.author){
                  groupSet
                }
                else{
                  groupSet.groupingStrategy.addNewPerson(groupSet,person)
                }
              })
            }
            else{
              gs
            }
          }))
        })
        trace("newSlides: %s".format(newSlides))
        if (shouldUpdateConversation){
          warn("pushing conversation update at Rooms::updateGroupSets")
          Some(cr.cd.copy(slides = newSlides))
        } else {
          None
        }
      }
      case _ => None
    }
  }
  protected val pollInterval = new TimeSpan(2 * 60 * 1000)  // 2 minutes
  protected val chunkExpiry = new TimeSpan(5 * 1000)  // 5 seconds
  protected var joinedUsers = List.empty[Tuple3[String,String,LiftActor]]
  def createUpdate = HealthyWelcomeFromRoom
  protected var lastInterest:Long = new Date().getTime
  protected def scheduleMessageForMe[T](message: => T,delay:TimeSpan) = {
    if (possiblyCloseRoom){
    } else {
      Schedule.schedule(this,message,delay)
    }
  }
  protected def heartbeat = scheduleMessageForMe(Ping,pollInterval)
  protected def checkChunkExpiry = scheduleMessageForMe(CheckChunks,chunkExpiry)
  def localSetup = {
    info("MeTLRoom(%s):localSetup".format(location))
    heartbeat
    checkChunkExpiry
  }
  def localShutdown = {
    info("MeTLRoom(%s):localShutdown".format(location))
    messageBus.release
  }
  initialize
  case object IrrelevantMatch
  protected def overrideableLowPriority:PartialFunction[Any,Unit] = {
    case IrrelevantMatch => {}
  }
  override def lowPriority:PartialFunction[Any,Unit] = coreLowPriority orElse overrideableLowPriority orElse catchAll
  protected def coreLowPriority:PartialFunction[Any,Unit] = {
    case j:JoinRoom => Stopwatch.time("MeTLRoom.lowPriority.JoinRoom",addConnection(j))
    case l:LeaveRoom => Stopwatch.time("MeTLRoom.lowPriority.LeaveRoom",removeConnection(l))
    case sl@ServerToLocalMeTLStanza(s) => Stopwatch.time("MeTLRoom.lowPriority.ServerToLocalMeTLStanza",sendToChildren(s))
    case CheckChunks => {
      chunker.check(this)
      if (possiblyCloseRoom){
      } else {
        checkChunkExpiry
      }
    }
    case Ping => Stopwatch.time("MeTLRoom.ping",{
      if (possiblyCloseRoom){
      } else {
        heartbeat
      }
    })
    case ls@LocalToServerMeTLStanza(s) => Stopwatch.time("MeTLRoom.lowPriority.LocalToServerMeTLStanza",{
      trace("received stanza to send to server: %s %s".format(ls, s))
      sendStanzaToServer(s)
    })
    case ls@ArchiveToServerMeTLStanza(s) => Stopwatch.time("MeTLRoom.lowPriority.ArchiveToServerMeTLStanza",{
      trace("received archived stanza to send to server: %s %s".format(ls, s))
      sendStanzaToServer(s,false)
    })
    case UpdateConversationDetails(jid) if location == jid => {
      roomMetaData match {
        case cr:ConversationRoom => {
          val currentConvCache = conversationCache.getOrElse(Conversation.empty)
          val newConv = cr.cd
          conversationCache = Some(newConv)
          if (newConv.subject != currentConvCache.subject){
            updatePossibleAttendance
          }
        }
        case _ => None
      }
    }
    case u@UpdateThumb(slide) => joinedUsers.foreach(_._3 ! u)
    case m@MotherMessage(html,audiences) => roomMetaData match {
      case sl:SlideRoom => {
        joinedUsers.foreach(j => j._3 ! MeTLChatMessage(config,"| mother |",new Date().getTime,nextFuncName,"html",html.toString,sl.cd.jid.toString,audiences))
      }
      case _ => None
    }
  }
  protected def catchAll:PartialFunction[Any,Unit] = {
    case _ => warn("MeTLRoom received unknown message")
  }
  def getChildren:List[Tuple3[String,String,LiftActor]] = Stopwatch.time("MeTLRoom.getChildren",{
    joinedUsers.toList
  })
  protected def sendToChildren(a:MeTLStanza):Unit = Stopwatch.time("MeTLRoom.sendToChildren",{
    trace("stanza received: %s".format(a));
    (a,roomMetaData) match {
      case (m:MeTLCommand,_) if m.command == "/UPDATE_CONVERSATION_DETAILS" => {
        com.metl.comet.MeTLConversationSearchActorManager ! m
        com.metl.comet.MeTLSlideDisplayActorManager ! m
        com.metl.comet.MeTLEditConversationActorManager ! m
        m.commandParameters.headOption.foreach(convJid => {
          MeTLXConfiguration.getRoom(convJid,config.name,ConversationRoom(config,convJid)) ! UpdateConversationDetails(convJid)
        })
      }
      case (m:MeTLCommand,cr:ConversationRoom) if List("/SYNC_MOVE","/TEACHER_IN_CONVERSATION").contains(m.command) => {
        com.metl.comet.MeTLSlideDisplayActorManager ! m
      }
      case (m:Attendance,cr:ConversationRoom) => {
        updateGroupSets.foreach(c => {
          trace("Updating %s because of calculating groups based on %s => %s".format(c.jid,m.author,
            (for(
              slide <- c.slides;
              groupSet <- slide.groupSet;
              group <- groupSet.groups;
              member <- group.members) yield member).mkString(",")))
          config.updateConversation(c.jid.toString,c)
        })
      }
      case (c:MeTLCanvasContent ,_) => chunker.add(c,this)
      case _ => {}
    }
    trace("%s s->l %s".format(location,a))
    joinedUsers.foreach(j => j._3 ! a)
  })
  def addTheme(theme:Theme) = {
    sendStanzaToServer(MeTLTheme(config,theme.author,new java.util.Date().getTime,location,theme,Nil))
  }
  protected def sendStanzaToServer(s:MeTLStanza,updateTimestamp:Boolean = true):Unit = Stopwatch.time("MeTLRoom.sendStanzaToServer",{
    warn("%s l->s %s".format(location,s))
    showInterest
    if (shouldBacklog) {
      warn("MeTLRoom(%s):sendToServer.backlogging".format(location))
      backlog.enqueue((s,updateTimestamp))
    } else {
      warn("sendingStanzaToServer: %s".format(s))
      messageBus.sendStanzaToRoom(s,updateTimestamp)
    }
  })
  protected def emitAttendance(username: String, targetRoom: RoomMetaData, present: Boolean) = {
    val room = MeTLXConfiguration.getRoom(targetRoom.getJid, config.name, targetRoom)
    room ! LocalToServerMeTLStanza(Attendance(config, username, -1L, location, present, Nil))
  }
  protected def formatConnection(username:String,uniqueId:String):String = "%s_%s".format(username,uniqueId)
  protected def getMetlingPots:List[MeTLingPotAdaptor] = Globals.metlingPots
  protected def addConnection(j:JoinRoom):Unit = Stopwatch.time("MeTLRoom.addConnection(%s)".format(j),{
    val oldMembers = getChildren.map(_._1)
    val username = j.username
    joinedUsers = ((username,j.cometId,j.actor) :: joinedUsers).distinct
    j.actor ! RoomJoinAcknowledged(config.name,location)
    warn("%s joining room %s".format(username,roomMetaData))
    roomMetaData match {
      case cr:ConversationRoom if !ImporterActor.backgroundWorkerName.equals(username) => {
        conversationCache.foreach(conv => {
          if (joinedUsers.exists(_._1 == conv.author)){
            // the author's in the room, so don't perform any automatic action. 
          } else {
            // the author's not in the room, so check whether the conv's permissions include the mustFollowTeacher behaviour, and disable it if it is.
            if (conv.permissions.usersAreCompulsorilySynced){
              config.changePermissions(conv.jid.toString,conv.permissions.copy(usersAreCompulsorilySynced = false))
            }
          }
        })
        if (!oldMembers.contains(username)){
          updatePossibleAttendance
          val u = ConversationParticipation(location,getAttendance,getPossibleAttendance)
          joinedUsers.foreach(_._3 ! u)
          getMetlingPots.foreach(mp => {
            mp.postItems(List(
              MeTLingPotItem("metlRoom",new java.util.Date().getTime(),KVP("metlUser",username),KVP("informalAcademic","joined"),Some(KVP("room",location)),None,None)
            ))
          })

          try {
            trace("trying to send truePresence for conversation room: %s".format(location))
            emitAttendance(username, GlobalRoom(config), present = true)
          } catch {
            case e:Exception => {}
          }
        }
        j.actor ! ConversationParticipation(location,getAttendance,getPossibleAttendance) //why are we sending J two messages of the same content?  Won't he always get the other message if he's new?  Why if he's new should he get two of them?
      }
      case sr:SlideRoom if !ImporterActor.backgroundWorkerName.equals(username) => {
        if (!oldMembers.contains(username)) {
          try {
            trace("trying to send truePresence for slide room: %s".format(location))
            emitAttendance(username, ConversationRoom(config, sr.cd.jid.toString), present = true)
          } catch {
            case e:Exception => {}
          }
        }
      }
      case _ => {}
    }
    showInterest
  })
  protected def removeConnection(l:LeaveRoom):Unit = Stopwatch.time("MeTLRoom.removeConnection(%s)".format(l),{
    val oldMembers = getChildren.map(_._1)
    val username = l.username
    joinedUsers = joinedUsers.filterNot(i => i._1 == username && i._2 == l.cometId)
    val newMembers = getChildren.map(_._1)
    l.actor ! RoomLeaveAcknowledged(config.name,location)
    trace("%s leaving room %s".format(username,roomMetaData))
    roomMetaData match {
      case cr:ConversationRoom if !ImporterActor.backgroundWorkerName.equals(username) => {
        conversationCache.foreach(conv => {
          if (joinedUsers.exists(_._1 == conv.author)){
            // the author's still in the room 
          } else {
            // the author's left, so check whether the conv's permissions include the mustFollowTeacher behaviour.
            if (conv.permissions.usersAreCompulsorilySynced){
              config.changePermissions(conv.jid.toString,conv.permissions.copy(usersAreCompulsorilySynced = false))
            }
          }
        })
        if (oldMembers.contains(username) && !newMembers.contains(username)) {
          val u = ConversationParticipation(location,getAttendance,getPossibleAttendance)
          joinedUsers.foreach(_._3 ! u)
          getMetlingPots.foreach(mp => {
            mp.postItems(List(
              MeTLingPotItem("metlRoom",new java.util.Date().getTime(),KVP("metlUser",username),KVP("informalAcademic","left"),Some(KVP("room",location)),None,None)
            ))
          })

          try {
            trace("trying to send falsePresence for conversation room: %s".format(location))
            emitAttendance(username, GlobalRoom(config), present = false)
          } catch {
            case e:Exception => {}
          }
        }
      }
      case sr:SlideRoom if !ImporterActor.backgroundWorkerName.equals(username) => {
        if (oldMembers.contains(username)) {
          try {
            trace("trying to send falsePresence for slide room: %s".format(location))
            emitAttendance(username, ConversationRoom(config, sr.cd.jid.toString), present = false)
          } catch {
            case e:Exception => {}
          }
        }
      }
      case _ => {}
    }
  })
  protected def possiblyCloseRoom:Boolean = Stopwatch.time("MeTLRoom.possiblyCloseRoom",{
    if (location != "global" && joinedUsers.length == 0 && !recentInterest) {
      trace("MeTLRoom(%s):heartbeat.closingRoom".format(location))
      chunker.close(this)
      trace("MeTLRoom(%s):closing final chunks".format(location))
      creator.removeMeTLRoom(location)
      true
    } else {
      false
    }
  })
  protected def showInterest:Unit = lastInterest = new Date().getTime
  protected def recentInterest:Boolean = Stopwatch.time("MeTLRoom.recentInterest",{
    idleTimeout.map(it => (new Date().getTime - lastInterest) < it).getOrElse(true) // if no interest timeout is specified, then don't expire the room
  })
  override def toString = "MeTLRoom(%s,%s,%s)".format(config.name,location,creator)
}

object EmptyRoom extends MeTLRoom(ServerConfiguration.empty,"empty",EmptyRoomProvider,UnknownRoom,None) {
  override def getHistory = History.empty
  override def getThumbnail = Array.empty[Byte]
  override def getSnapshot(size:RenderDescription) = Array.empty[Byte]
  override protected def sendToChildren(s:MeTLStanza) = {}
  override protected def sendStanzaToServer(s:MeTLStanza,updateTimestamp:Boolean = true) = {}
}

object ThumbnailSpecification {
  val height = 240
  val width = 320
}

class NoCacheRoom(config:ServerConfiguration,override val location:String,creator:RoomProvider,override val roomMetaData:RoomMetaData,override val idleTimeout:Option[Long]) extends MeTLRoom(config,location,creator,roomMetaData,idleTimeout) {
  override def getHistory = config.getHistory(location)
  override def getThumbnail = {
    roomMetaData match {
      case s:SlideRoom => {
        slideRenderer.render(getHistory,ThumbnailSizes.ThumbnailSize,"presentationSpace")
      }
      case _ => {
        Array.empty[Byte]
      }
    }
  }
  override def getSnapshot(size:RenderDescription) = {
    roomMetaData match {
      case s:SlideRoom => {
        slideRenderer.render(getHistory,size,"presentationSpace")
      }
      case _ => {
        Array.empty[Byte]
      }
    }
  }
}

class StartupInformation {
  protected var isFirstTime = true
  def setHasStarted(value:Boolean):Unit = {
    isFirstTime = value
  }
  def getHasStarted:Boolean = {
    if (isFirstTime)
      true
    else false
  }
}

class HistoryCachingRoom(config:ServerConfiguration,override val location:String,creator:RoomProvider,override val roomMetaData:RoomMetaData,override val idleTimeout:Option[Long]) extends MeTLRoom(config,location,creator,roomMetaData,idleTimeout) {
  protected var history:History = History.empty
  protected val isPublic = tryo(location.toInt).map(l => true).openOr(false)
  protected var snapshots:Map[RenderDescription,Array[Byte]] = Map.empty[RenderDescription,Array[Byte]]
  protected lazy val starting = new StartupInformation
  protected def firstTime = initialize
  override def initialize = Stopwatch.time("HistoryCachingRoom.initialize",{
    if (starting.getHasStarted) {
      trace("initialize: %s (first time)".format(roomMetaData))
      starting.setHasStarted(false)
    } else {
      trace("initialize: %s (subsequent time)".format(roomMetaData))
      showInterest
      history = config.getHistory(location).attachRealtimeHook((s) => {
        trace("ROOM %s sending %s to children %s".format(location,s,joinedUsers))
        super.sendToChildren(s)
      })
      updateSnapshots
    }
  })
  firstTime
  protected var lastRender = 0L
  protected val acceptableRenderStaleness = 0L
  override def getHistory:History = {
    showInterest
    history
  }
  protected def updateSnapshots = Stopwatch.time("HistoryCachingRoom.updateSnapshots",{
    if (history.lastVisuallyModified > lastRender){
      snapshots = makeSnapshots
      lastRender = history.lastVisuallyModified
      roomMetaData match {
        case s:SlideRoom => {
          trace("Snapshot update")
          MeTLXConfiguration.getRoom(s.cd.jid.toString,config.name) ! UpdateThumb(history.jid)
        }
        case _ => {}
      }
    }
  })
  protected def makeSnapshots = Stopwatch.time("HistoryCachingRoom_%s@%s makingSnapshots".format(location,config.name),{
    roomMetaData match {
      case s:SlideRoom => {
        val thisHistory = isPublic match {
          case true => history.filterCanvasContents(cc => cc.privacy == Privacy.PUBLIC)
          case false => history
        }
        trace("rendering snapshots for: %s %s".format(history.jid,ThumbnailSizes.snapshotSizes))
        val result = slideRenderer.renderMultiple(thisHistory,ThumbnailSizes.snapshotSizes)
        trace("rendered snapshots for: %s %s".format(history.jid,result.map(tup => (tup._1,tup._2.length))))
        result
      }
      case _ => {
        Map.empty[RenderDescription,Array[Byte]]
      }
    }
  })
  override def getSnapshot(size:RenderDescription) = {
    showInterest
    snapshots.get(size).getOrElse({
      roomMetaData match {
        case s:SlideRoom => {
          slideRenderer.render(getHistory,size,"presentationSpace")
        }
        case _ => {
          Array.empty[Byte]
        }
      }
    })
  }
  override def getThumbnail = {
    getSnapshot(ThumbnailSizes.ThumbnailSize)
  }
  override protected def sendToChildren(s:MeTLStanza):Unit = Stopwatch.time("HistoryCachingMeTLRoom.sendToChildren",{
    history.addStanza(s)
    s match {
      case c:MeTLCanvasContent if (history.lastVisuallyModified > lastRender) => {
        updateSnapshots
      }
      case _ => {}
    }
  })
  override def toString = "HistoryCachingRoom(%s,%s,%s)".format(config.name,location,creator)
}

class XmppBridgingHistoryCachingRoom(config:ServerConfiguration,override val location:String,creator:RoomProvider,override val roomMetaData:RoomMetaData,override val idleTimeout:Option[Long]) extends HistoryCachingRoom(config,location,creator,roomMetaData,idleTimeout) {
  protected var stanzasToIgnore = List.empty[MeTLStanza]
  def sendMessageFromBridge(s:MeTLStanza):Unit = Stopwatch.time("XmppBridgedHistoryCachingRoom.sendMessageFromBridge",{
    trace("XMPPBRIDGE (%s) sendToServer: %s".format(location,s))
    sendStanzaToServer(s)
  })
  protected def sendMessageToBridge(s:MeTLStanza):Unit = Stopwatch.time("XmppBridgedHistoryCachingROom.sendMessageToBridge",{
    trace("XMPPBRIDGE (%s) sendToBridge: %s".format(location,s))
    MeTLXConfiguration.xmppServer.foreach(_.relayMessageToXmppMuc(location,s))
  })
  override protected def sendToChildren(s:MeTLStanza):Unit = Stopwatch.time("XmppBridgedHistoryCachingRoom.sendToChildren",{
    trace("XMPPBRIDGE (%s) sendToChildren: %s".format(location,s))
    val (matches,remaining) = stanzasToIgnore.partition(sti => sti.equals(s))
    matches.length match {
      case 1 => stanzasToIgnore = remaining
      case i:Int if i > 1 => stanzasToIgnore = remaining ::: matches.drop(1)
      case _ => sendMessageToBridge(s)
    }
    super.sendToChildren(s)
  })
}
