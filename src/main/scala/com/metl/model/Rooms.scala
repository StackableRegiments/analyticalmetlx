package com.metl.model

import com.metl.data._
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

import scala.collection.mutable.Queue

abstract class RoomProvider {
  def get(jid:String):MeTLRoom
  def removeMeTLRoom(room:String):Unit
  def exists(room:String):Boolean
}

object EmptyRoomProvider extends RoomProvider {
  override def get(jid:String) = EmptyRoom
  override def removeMeTLRoom(room:String) = {}
  override def exists(room:String) = false
}

class HistoryCachingRoomProvider(configName:String) extends RoomProvider {
  private lazy val metlRooms = new SynchronizedWriteMap[String,MeTLRoom](scala.collection.mutable.HashMap.empty[String,MeTLRoom],true,(k:String) => createNewMeTLRoom(k))
  override def exists(room:String):Boolean = Stopwatch.time("Rooms.exists", () => metlRooms.keys.exists(k => k == room))
  override def get(room:String) = Stopwatch.time("Rooms.get", () => metlRooms.getOrElseUpdate(room, createNewMeTLRoom(room)))
  protected def createNewMeTLRoom(room:String) = Stopwatch.time("Rooms.createNewMeTLRoom(%s)".format(room), () => {
    val r = new HistoryCachingRoom(configName,room,this)
    r.localSetup
    r
  })
  override def removeMeTLRoom(room:String) = Stopwatch.time("Rooms.removeMeTLRoom(%s)".format(room), () => {
    if (exists(room)){
      metlRooms(room).localShutdown
      metlRooms.remove(room)
    }
  })
}

case class ServerToLocalMeTLStanza(stanza:MeTLStanza)
case class LocalToServerMeTLStanza(stanza:MeTLStanza)
abstract class RoomStateInformation
case class RoomJoinAcknowledged(server:String,room:String) extends RoomStateInformation
case class RoomLeaveAcknowledged(server:String,room:String) extends RoomStateInformation
case class JoinRoom(username:String,cometId:String,actor:LiftActor)
case class LeaveRoom(username:String,cometId:String,actor:LiftActor)

case object HealthyWelcomeFromRoom
case object Ping

abstract class MeTLRoom(configName:String,location:String,creator:RoomProvider) extends LiftActor with ListenerManager {
  lazy val config = ServerConfiguration.configForName(configName)
  private var shouldBacklog = false
  private var backlog = Queue.empty[MeTLStanza]
  private def onConnectionLost:Unit = {
    //println("MeTLRoom(%s):onConnectionLost".format(location))
    shouldBacklog = true
  }
  private def onConnectionRegained:Unit = {
    //println("MeTLRoom(%s):onConnectionRegained".format(location))
    initialize
    processBacklog
    shouldBacklog = false
  }
  private def processBacklog:Unit = {
    //println("MeTLRoom(%s):sendToServer.processingBacklog".format(location))
    while (!backlog.isEmpty){
      val item = backlog.dequeue
      //println("MeTLRoom(%s):sendToServer.processingBacklog.dequeue(%s)".format(location,item))
      this ! LocalToServerMeTLStanza(item)
    }
  }
  protected def initialize:Unit = {}
  protected val messageBusDefinition = new MessageBusDefinition(location,"unicastBackToOwner",(s:MeTLStanza) => this ! ServerToLocalMeTLStanza(s),onConnectionLost _,onConnectionRegained _)
  protected val messageBus = config.getMessageBus(messageBusDefinition)
  def getHistory:History
  def getThumbnail:Array[Byte]
  def getSnapshot(size:SnapshotSize.Value):Array[Byte]
  private val pollInterval = new TimeSpan(120000)
  private var joinedUsers = List.empty[Tuple3[String,String,LiftActor]]
  def createUpdate = HealthyWelcomeFromRoom
  private var lastInterest:Long = new Date().getTime
  private var interestTimeout:Long = 60000
  private def heartbeat = ActorPing.schedule(this,Ping,pollInterval)
  def localSetup = {
    heartbeat
  }
  def localShutdown = {
    //println("MeTLRoom(%s):localShutdown".format(location))
    messageBus.release
  }
  initialize
  case object IrrelevantMatch
  protected def overrideableLowPriority:PartialFunction[Any,Unit] = {
    case IrrelevantMatch => {}
  }
  override def lowPriority:PartialFunction[Any,Unit] = coreLowPriority orElse overrideableLowPriority orElse catchAll
  protected def coreLowPriority:PartialFunction[Any,Unit] = {
    case j:JoinRoom => Stopwatch.time("MeTLRoom.lowPriority.JoinRoom", () => addConnection(j))
    case l:LeaveRoom => Stopwatch.time("MeTLRoom.lowPriority.LeaveRoom", () => removeConnection(l))
    case sl@ServerToLocalMeTLStanza(s) => Stopwatch.time("MeTLRoom.lowPriority.ServerToLocalMeTLStanza", () => sendToChildren(s))
    case Ping => Stopwatch.time("MeTLRoom.ping", () => {
      if (possiblyCloseRoom){

      } else {
        heartbeat
      }
    })
    case ls@LocalToServerMeTLStanza(s) => Stopwatch.time("MeTLRoom.lowPriority.LocalToServerMeTLStanza", () => sendStanzaToServer(s))
  }
  protected def catchAll:PartialFunction[Any,Unit] = {
    case _ => println("MeTLRoom recieved unknown message")
  }
  def getChildren:List[Tuple3[String,String,LiftActor]] = Stopwatch.time("MeTLRoom.getChildren",() => {
    joinedUsers.toList
  })
  protected def sendToChildren(a:MeTLStanza):Unit = Stopwatch.time("MeTLRoom.sendToChildren",() => {
    //println("%s s->l %s".format(location,a))
    //println("MeTLRoom(%s):sendToChildren(%s)".format(location,a))
    joinedUsers.foreach(j => j._3 ! a)
  })
  protected def sendStanzaToServer(s:MeTLStanza):Unit = Stopwatch.time("MeTLRoom.sendStanzaToServer", () => {
    //println("%s l->s %s".format(location,s))
    //println("MeTLRoom(%s):sendToServer(%s)".format(location,s))
    showInterest
    if (shouldBacklog) {
      //println("MeTLRoom(%s):sendToServer.backlogging".format(location))
      backlog.enqueue(s)
    } else {
      messageBus.sendStanzaToRoom(s)
    }
  })
  private def formatConnection(username:String,uniqueId:String):String = "%s_%s".format(username,uniqueId)
  private def addConnection(j:JoinRoom):Unit = Stopwatch.time("MeTLRoom.addConnection(%s)".format(j),() => {
    joinedUsers = ((j.username,j.cometId,j.actor) :: joinedUsers).distinct
    j.actor ! RoomJoinAcknowledged(configName,location)
    showInterest
  })
  private def removeConnection(l:LeaveRoom):Unit = Stopwatch.time("MeTLRoom.removeConnection(%s)".format(l), () => {
    joinedUsers = joinedUsers.filterNot(i => i._1 == l.username && i._2 == l.cometId)
    l.actor ! RoomLeaveAcknowledged(configName,location)
  })
  private def possiblyCloseRoom:Boolean = Stopwatch.time("MeTLRoom.possiblyCloseRoom", () => {
    if (location != "global" && joinedUsers.length == 0 && !recentInterest) {
      //println("MeTLRoom(%s):heartbeat.closingRoom".format(location))
      creator.removeMeTLRoom(location)
      true
    } else {
      false
    }
  })
  protected def showInterest:Unit = lastInterest = new Date().getTime
  private def recentInterest:Boolean = Stopwatch.time("MeTLRoom.recentInterest", () => {
    (new Date().getTime - lastInterest) < interestTimeout
  })
  override def toString = "MeTLRoom(%s,%s,%s)".format(configName,location,creator)
}

object EmptyRoom extends MeTLRoom("empty","empty",EmptyRoomProvider) {
  override def getHistory = History.empty
  override def getThumbnail = Array.empty[Byte]
  override def getSnapshot(size:SnapshotSize.Value) = Array.empty[Byte]
  override protected def sendToChildren(s:MeTLStanza) = {}
  override protected def sendStanzaToServer(s:MeTLStanza) = {}
}

object ThumbnailSpecification {
  val height = 240
  val width = 320
}

class NoCacheRoom(configName:String,location:String,creator:RoomProvider) extends MeTLRoom(configName,location,creator) {
  override def getHistory = config.getHistory(location)
  override def getThumbnail = SlideRenderer.render(getHistory,ThumbnailSpecification.width,ThumbnailSpecification.height)
  override def getSnapshot(size:SnapshotSize.Value) = {
    val d = Globals.snapshotSizes(size)
    SlideRenderer.render(getHistory,d.width,d.height)
  }
}

case object ThumbnailRenderRequest

class StartupInformation {
  private var isFirstTime = true
  def setHasStarted(value:Boolean):Unit = {
    isFirstTime = value
  }
  def getHasStarted:Boolean = {
    if (isFirstTime)
      true
    else false
  }
}

class HistoryCachingRoom(configName:String,location:String,creator:RoomProvider) extends MeTLRoom(configName,location,creator) {
  private var history:History = History.empty
  private val isPublic = tryo(location.toInt).map(l => true).openOr(false)
  private var snapshots:Map[SnapshotSize.Value,Array[Byte]] = Map.empty[SnapshotSize.Value,Array[Byte]]
  private lazy val starting = new StartupInformation
  private def firstTime = initialize
  override def initialize = Stopwatch.time("HistoryCachingRoom.initalize",() => {
    if (starting.getHasStarted) {
      starting.setHasStarted(false)
    } else {
      showInterest
      history = config.getHistory(location).attachRealtimeHook((s) => super.sendToChildren(s))
      updateSnapshots
    }
  })
  firstTime
  private var lastRender = 0L
  private val acceptableRenderStaleness = 10000L
  override def getHistory:History = {
    showInterest
    history
  }
  private def updateSnapshots = Stopwatch.time("HistoryCachingRoom.updateSnapshots", () => {
    snapshots = makeSnapshots
    lastRender = history.lastVisuallyModified
  })
  private def makeSnapshots = Stopwatch.time("HistoryCachingRoom_%s@%s makingSnapshots".format(location,configName), () => {
    val thisHistory = isPublic match {
      case true => history.filterCanvasContents(cc => cc.privacy == Privacy.PUBLIC)
      case false => history
    }
    SlideRenderer.renderMultiple(thisHistory,Globals.snapshotSizes.map(ss => (ss._1.toString.asInstanceOf[String],ss._2.width,ss._2.height)).toList).map(ri => (SnapshotSize.parse(ri._1.toLowerCase) -> ri._2._3))
  })
  override def getSnapshot(size:SnapshotSize.Value) = {
    showInterest
    snapshots(size)
  }
  override def getThumbnail = {
    getSnapshot(SnapshotSize.Thumbnail)
  }
  private var renderInProgress = false;
  override def overrideableLowPriority = {
    case ThumbnailRenderRequest => {
      if ((new Date().getTime - lastRender) > acceptableRenderStaleness){
        renderInProgress = false
        updateSnapshots
      } else if (!renderInProgress){
        renderInProgress = true
        ActorPing.schedule(this,ThumbnailRenderRequest,acceptableRenderStaleness)
      }
    }
  }
  override protected def sendToChildren(s:MeTLStanza):Unit = Stopwatch.time("HistoryCachingMeTLRoom.sendToChildren",() => {
    history.addStanza(s)
    s match {
      case c:MeTLCanvasContent if (history.lastVisuallyModified > lastRender) => {
        this ! ThumbnailRenderRequest
      }
      case _ => {}
    }
  })
  override def toString = "HistoryCachingRoom(%s,%s,%s)".format(configName,location,creator)
}
