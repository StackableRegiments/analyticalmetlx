package com.metl.model

import com.metl.data.{Group=>MeTLGroup,_}
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
import com.metl.renderer.RenderDescription

import scala.collection.JavaConversions._
import scala.collection.mutable.Queue

class RoomsSynchronizedWriteMap[A,B](collection:scala.collection.mutable.HashMap[A,B] = scala.collection.mutable.HashMap.empty[A,B],updateOnDefault:Boolean = false,defaultFunction:Function[A,B] = (k:A) => null.asInstanceOf[B]) extends Synched{
  private var coll = collection
  private var defaultFunc:Function[A,B] = defaultFunction
  def += (kv: (A,B)):RoomsSynchronizedWriteMap[A,B] = syncWrite[RoomsSynchronizedWriteMap[A,B]](()=>{
    coll.+=(kv)
    this
  })
  def -= (k:A):RoomsSynchronizedWriteMap[A,B] = syncWrite[RoomsSynchronizedWriteMap[A,B]](()=>{
    coll.-=(k)
    this
  })
  def put(k:A,v:B):Option[B] = syncWrite[Option[B]](()=>coll.put(k,v))
  def update(k:A,v:B):Unit = syncWrite(()=>coll.update(k,v))
  def updated(k:A,v:B):RoomsSynchronizedWriteMap[A,B] = syncRead[RoomsSynchronizedWriteMap[A,B]](()=>{
    val newColl = this.clone
    newColl.update(k,v)
    newColl
  })
  def default(k:A):B = {
    val newValue = defaultFunc(k)
    if (updateOnDefault)
      syncWrite(()=>{
        coll += ((k,newValue))
      })
    newValue
  }
  def remove(k:A):Option[B] = syncWrite(()=>coll.remove(k))
  def clear:Unit = syncWrite(()=>coll.clear)
  def getOrElseUpdate(k:A, default: => B):B = {
    syncRead(() => coll.get(k)) match {
      case Some(v) => v
      case None => {
        val newVal = default
        syncWrite(()=> coll.getOrElseUpdate(k,newVal))
      }
    }
    //syncWrite(()=>coll.getOrElseUpdate(k,default))
  }
  def transform(f: (A,B) => B):RoomsSynchronizedWriteMap[A,B] = syncWrite[RoomsSynchronizedWriteMap[A,B]](()=>{
    coll.transform(f)
    this
  })
  def retain(p: (A,B) => Boolean):RoomsSynchronizedWriteMap[A,B] = syncWrite[RoomsSynchronizedWriteMap[A,B]](()=>{
    coll.retain(p)
    this
  })
  def iterator: Iterator[(A, B)] = syncRead(()=>coll.iterator)
  def values: scala.collection.Iterable[B] = syncRead(()=>coll.map(kv => kv._2))
  def valuesIterator: Iterator[B] = syncRead(()=>coll.valuesIterator)
  def foreach[U](f:((A, B)) => U) = syncRead(()=>coll.foreach(f))
  def apply(k: A): B = syncReadUnlessPredicateThenWrite(()=>isDefinedAt(k),()=>coll.apply(k),()=>default(k))
  def withDefault(f:(A) => B):RoomsSynchronizedWriteMap[A,B] = {
    this.defaultFunc = f
    syncWrite(()=>{
      this
    })
  }
  def keySet: scala.collection.Set[A] = syncRead(()=>coll.keySet)
  def keys: scala.collection.Iterable[A] = syncRead(()=>coll.map(kv => kv._1))
  def keysIterator: Iterator[A] = syncRead(()=>coll.keysIterator)
  def isDefinedAt(k: A) = syncRead(()=>coll.isDefinedAt(k))
  def size:Int = syncRead(()=>coll.size)
  def isEmpty: Boolean = syncRead(()=>coll.isEmpty)
  def toList:List[(A,B)] = syncRead(()=>coll.toList)
  def toArray:Array[(A,B)] = syncRead(()=>coll.toArray)
  def map(f:((A, B)) => Any):scala.collection.mutable.Iterable[Any] = syncRead(()=>coll.map(f))
  override def clone: RoomsSynchronizedWriteMap[A,B] = syncRead(()=>new RoomsSynchronizedWriteMap[A,B](coll.clone,updateOnDefault,defaultFunction))
  override def toString = "RoomsSynchronizedWriteMap("+coll.map(kv => "(%s -> %s)".format(kv._1,kv._2)).mkString(", ")+")"
  override def equals(other:Any) = (other.isInstanceOf[RoomsSynchronizedWriteMap[A,B]] && other.asInstanceOf[RoomsSynchronizedWriteMap[A,B]].toList == this.toList)
}



abstract class RoomProvider {
  def get(jid:String):MeTLRoom
  def get(jid:String,roomMetaData:RoomMetaData):MeTLRoom
  def removeMeTLRoom(room:String):Unit
  def exists(room:String):Boolean
  def list:List[String]
}

object EmptyRoomProvider extends RoomProvider {
  override def get(jid:String) = EmptyRoom
  override def get(jid:String,roomDefinition:RoomMetaData) = EmptyRoom
  override def removeMeTLRoom(room:String) = {}
  override def exists(room:String) = false
  override def list = Nil
}

object MeTLRoomType extends Enumeration {
  type MeTLRoomType = Value
  val Conversation,Slide,PrivateSlide,PersonalChatroom,Global,Unknown = Value
}

abstract class RoomMetaData(val server:String, val roomType:MeTLRoomType.Value) {
  def getJid:String
}

case class ConversationRoom(override val server:String,jid:String) extends RoomMetaData(server,MeTLRoomType.Conversation){
  def cd:Conversation = ServerConfiguration.configForName(server).detailsOfConversation(jid.toString)
  override def getJid = jid
}
case class SlideRoom(override val server:String,jid:String,slideId:Int) extends RoomMetaData(server,MeTLRoomType.Slide){
  def cd:Conversation = ServerConfiguration.configForName(server).detailsOfConversation(jid.toString)
  def s:Slide = cd.slides.find(_.id == slideId).getOrElse(Slide.empty)
  override def getJid = slideId.toString
}
case class PrivateSlideRoom(override val server:String,jid:String,slideId:Int,owner:String) extends RoomMetaData(server,MeTLRoomType.PrivateSlide){
  def cd:Conversation = ServerConfiguration.configForName(server).detailsOfConversation(jid.toString)
  def s:Slide = cd.slides.find(_.id == slideId).getOrElse(Slide.empty)
  override def getJid = slideId.toString + owner
}
case class PersonalChatRoom(override val server:String,owner:String) extends RoomMetaData(server,MeTLRoomType.PersonalChatroom){
  override def getJid = owner
}
case class GlobalRoom(override val server:String) extends RoomMetaData(server,MeTLRoomType.Global){
  override def getJid = "global"
}
case object UnknownRoom extends RoomMetaData(EmptyBackendAdaptor.name,MeTLRoomType.Unknown){
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
  def fromJid(jid:String,server:String = ServerConfiguration.default.name):RoomMetaData = {
    splitJid(jid) match {
      case (0,"") => UnknownRoom
      case (0,"global") => GlobalRoom(server)
      case (conversationJid,"") if (conversationJid % 1000 == 0) => ConversationRoom(server,conversationJid.toString)
      case (slideJid,"") => SlideRoom(server,(slideJid - (slideJid % 1000)).toString,slideJid)
      case (0,user) => PersonalChatRoom(server,user)
      case (slideJid,user) => PrivateSlideRoom(server,(slideJid - (slideJid % 1000)).toString,slideJid,user)
      case _ => UnknownRoom
    }
  }
}

class HistoryCachingRoomProvider(configName:String) extends RoomProvider with Logger {
  private lazy val metlRooms = new java.util.concurrent.ConcurrentHashMap[String,MeTLRoom]
    //new RoomsSynchronizedWriteMap[String,MeTLRoom](scala.collection.mutable.HashMap.empty[String,MeTLRoom],true,(k:String) => createNewMeTLRoom(k,UnknownRoom))
  override def list = metlRooms.keys.toList
  override def exists(room:String):Boolean = Stopwatch.time("Rooms.exists", metlRooms.keys.exists(k => k == room))
  override def get(room:String) = Stopwatch.time("Rooms.get",metlRooms.getOrElseUpdate(room, createNewMeTLRoom(room,UnknownRoom)))
  override def get(room:String,roomDefinition:RoomMetaData) = Stopwatch.time("Rooms.get",metlRooms.getOrElseUpdate(room, createNewMeTLRoom(room,roomDefinition)))
  protected def createNewMeTLRoom(room:String,roomDefinition:RoomMetaData) = Stopwatch.time("Rooms.createNewMeTLRoom(%s)".format(room),{
    //val r = new HistoryCachingRoom(configName,room,this,roomDefinition)
    val start = new java.util.Date().getTime
    val a = new java.util.Date().getTime - start
    val r = new XmppBridgingHistoryCachingRoom(configName,room,this,roomDefinition)
    val b = new java.util.Date().getTime - start
    r.localSetup
    val c = new java.util.Date().getTime - start
    info("created room %s (%s, %s, %s) %s".format(room,a,b,c,roomDefinition))
    r
  })
  override def removeMeTLRoom(room:String) = Stopwatch.time("Rooms.removeMeTLRoom(%s)".format(room),{
    if (exists(room)){
      metlRooms(room).localShutdown
      metlRooms.remove(room)
    }
  })
}


case class ServerToLocalMeTLStanza[A <: MeTLStanza](stanza:A)
case class LocalToServerMeTLStanza[A <: MeTLStanza](stanza:A)
abstract class RoomStateInformation
case class RoomJoinAcknowledged(server:String,room:String) extends RoomStateInformation
case class RoomLeaveAcknowledged(server:String,room:String) extends RoomStateInformation
case class JoinRoom(username:String,cometId:String,actor:LiftActor)
case class LeaveRoom(username:String,cometId:String,actor:LiftActor)

case object HealthyWelcomeFromRoom
case object Ping

abstract class MeTLRoom(configName:String,val location:String,creator:RoomProvider,val roomMetaData:RoomMetaData) extends LiftActor with ListenerManager with Logger {
  lazy val config = ServerConfiguration.configForName(configName)
  private var shouldBacklog = false
  private var backlog = Queue.empty[MeTLStanza]
  private def onConnectionLost:Unit = {
    debug("MeTLRoom(%s):onConnectionLost".format(location))
    shouldBacklog = true
  }
  private def onConnectionRegained:Unit = {
    debug("MeTLRoom(%s):onConnectionRegained".format(location))
    initialize
    processBacklog
    shouldBacklog = false
  }
  private def processBacklog:Unit = {
    debug("MeTLRoom(%s):sendToServer.processingBacklog".format(location))
    while (!backlog.isEmpty){
      val item = backlog.dequeue
      trace("MeTLRoom(%s):sendToServer.processingBacklog.dequeue(%s)".format(location,item))
      this ! LocalToServerMeTLStanza(item)
    }
  }
  protected def initialize:Unit = {}
  protected val messageBusDefinition = new MessageBusDefinition(location,"unicastBackToOwner",(s:MeTLStanza) => this ! ServerToLocalMeTLStanza(s),onConnectionLost _,onConnectionRegained _)
  protected val messageBus = config.getMessageBus(messageBusDefinition)
  def getHistory:History
  def getThumbnail:Array[Byte]
  def getSnapshot(size:RenderDescription):Array[Byte]

  // this is the bit which needs tweaking now - it looks rather a lot like the members are already in the history, and what I should be checking is the slides, which may mean checking conversationDetails a little more frequently.  Fortunately, they're cached, so it shouldn't be expensive.
  def getAttendance:List[String] = {
    roomMetaData match {
      case cr:ConversationRoom => {
        val attendance = cr.cd.slides.flatMap(_.groupSet.flatMap(_.groups.flatMap(_.members)))
        debug("known members: %s".format(attendance))
        attendance
      }
      case _ => List.empty[String]
    }
  }
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
        debug("updating conversationRoom: %s".format(cr))
        val details = cr.cd
        var shouldUpdateConversation = false;
        val a = getAttendances.map(_.author)
        val newSlides = details.slides.map(slide => {
          slide.copy(groupSet = slide.groupSet.map(gs => {
            val grouped = gs.groups.flatMap(g => g.members)
            val ungrouped = a.filterNot(m => grouped.contains(m))
            if (ungrouped.length > 0){
              shouldUpdateConversation = true
            }
            ungrouped.foldLeft(gs.copy())((groupSet,person) => groupSet.groupingStrategy.addNewPerson(groupSet,person))
          }))
        })
        debug("newSlides: %s".format(newSlides))
        if (shouldUpdateConversation){
          Some(cr.cd.copy(slides = newSlides))
        } else {
          None
        }
      }
      case _ => None
    }
  }
  protected val pollInterval = new TimeSpan(2 * 60 * 1000)  // 2 minutes
  protected var joinedUsers = List.empty[Tuple3[String,String,LiftActor]]
  def createUpdate = HealthyWelcomeFromRoom
  protected var lastInterest:Long = new Date().getTime
  //protected var interestTimeout:Long = 60000
  protected var interestTimeout:Long = 30 * 60 * 1000 // 30 minutes -- rooms are shutting down too quickly, and they're too costly to start up
  protected def heartbeat = ActorPing.schedule(this,Ping,pollInterval)
  def localSetup = {
    info("MeTLRoom(%s):localSetup".format(location))
    heartbeat
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
  }
  protected def catchAll:PartialFunction[Any,Unit] = {
    case _ => warn("MeTLRoom recieved unknown message")
  }
  def getChildren:List[Tuple3[String,String,LiftActor]] = Stopwatch.time("MeTLRoom.getChildren",{
    joinedUsers.toList
  })
  protected def sendToChildren(a:MeTLStanza):Unit = Stopwatch.time("MeTLRoom.sendToChildren",{
    trace("stanza received: %s".format(a))
      (a,roomMetaData) match {
      case (m:Attendance,cr:ConversationRoom) => {
        trace("attendance received: %s".format(m))
        //      if (!getAttendance.exists(_ == m.author)){
        updateGroupSets.foreach(c => {
          trace("updated conversation postGroupsUpdate: %s".format(c))
          config.updateConversation(c.jid.toString,c)
        })
        //      }
      }
      /*
       case (m:MeTLCommand,cr:GlobalRoom) if m.command == "/UPDATE_CONVERSATION_DETAILS" => {
       trace("updating conversation details")
       cr.cd = config.detailsOfConversation(cr.jid)
       }
       */
      case _ => {}
    }
    trace("%s s->l %s".format(location,a))
    joinedUsers.foreach(j => j._3 ! a)
  })
  protected def sendStanzaToServer(s:MeTLStanza):Unit = Stopwatch.time("MeTLRoom.sendStanzaToServer",{
    trace("%s l->s %s".format(location,s))
    showInterest
    if (shouldBacklog) {
      debug("MeTLRoom(%s):sendToServer.backlogging".format(location))
      backlog.enqueue(s)
    } else {
      trace("sendingStanzaToServer: %s".format(s))
      messageBus.sendStanzaToRoom(s)
    }
  })
  private def formatConnection(username:String,uniqueId:String):String = "%s_%s".format(username,uniqueId)
  private def addConnection(j:JoinRoom):Unit = Stopwatch.time("MeTLRoom.addConnection(%s)".format(j),{
    joinedUsers = ((j.username,j.cometId,j.actor) :: joinedUsers).distinct
    j.actor ! RoomJoinAcknowledged(configName,location)
    showInterest
  })
  private def removeConnection(l:LeaveRoom):Unit = Stopwatch.time("MeTLRoom.removeConnection(%s)".format(l),{
    joinedUsers = joinedUsers.filterNot(i => i._1 == l.username && i._2 == l.cometId)
    l.actor ! RoomLeaveAcknowledged(configName,location)
  })
  private def possiblyCloseRoom:Boolean = Stopwatch.time("MeTLRoom.possiblyCloseRoom",{
    if (location != "global" && joinedUsers.length == 0 && !recentInterest) {
      debug("MeTLRoom(%s):heartbeat.closingRoom".format(location))
      creator.removeMeTLRoom(location)
      true
    } else {
      false
    }
  })
  protected def showInterest:Unit = lastInterest = new Date().getTime
  private def recentInterest:Boolean = Stopwatch.time("MeTLRoom.recentInterest",{
    (new Date().getTime - lastInterest) < interestTimeout
  })
  override def toString = "MeTLRoom(%s,%s,%s)".format(configName,location,creator)
}

object EmptyRoom extends MeTLRoom("empty","empty",EmptyRoomProvider,UnknownRoom) {
  override def getHistory = History.empty
  override def getThumbnail = Array.empty[Byte]
  override def getSnapshot(size:RenderDescription) = Array.empty[Byte]
  override protected def sendToChildren(s:MeTLStanza) = {}
  override protected def sendStanzaToServer(s:MeTLStanza) = {}
}

object ThumbnailSpecification {
  val height = 240
  val width = 320
}

class NoCacheRoom(configName:String,override val location:String,creator:RoomProvider,override val roomMetaData:RoomMetaData) extends MeTLRoom(configName,location,creator,roomMetaData) {
  override def getHistory = config.getHistory(location)
  override def getThumbnail = {
    roomMetaData match {
      case s:SlideRoom => {
        SlideRenderer.render(getHistory,Globals.ThumbnailSize,"presentationSpace")
      }
      case _ => {
        Array.empty[Byte]
      }
    }
  }
  override def getSnapshot(size:RenderDescription) = {
    roomMetaData match {
      case s:SlideRoom => {
        SlideRenderer.render(getHistory,size,"presentationSpace")
      }
      case _ => {
        Array.empty[Byte]
      }
    }
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

class HistoryCachingRoom(configName:String,override val location:String,creator:RoomProvider,override val roomMetaData:RoomMetaData) extends MeTLRoom(configName,location,creator,roomMetaData) {
  private var history:History = History.empty
  private val isPublic = tryo(location.toInt).map(l => true).openOr(false)
  private var snapshots:Map[RenderDescription,Array[Byte]] = Map.empty[RenderDescription,Array[Byte]]
  private lazy val starting = new StartupInformation
  private def firstTime = initialize
  override def initialize = Stopwatch.time("HistoryCachingRoom.initalize",{
    if (starting.getHasStarted) {
      debug("initialize: %s (first time)".format(roomMetaData))
      starting.setHasStarted(false)
    } else {
      debug("initialize: %s (subsequent time)".format(roomMetaData))
      showInterest
      history = config.getHistory(location).attachRealtimeHook((s) => {
        trace("ROOM %s sending %s to children %s".format(location,s,joinedUsers))
        super.sendToChildren(s)
      })
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
  private def updateSnapshots = Stopwatch.time("HistoryCachingRoom.updateSnapshots",{
    if (history.lastVisuallyModified > lastRender){
      snapshots = makeSnapshots
      lastRender = history.lastVisuallyModified
    }
  })
  private def makeSnapshots = Stopwatch.time("HistoryCachingRoom_%s@%s makingSnapshots".format(location,configName),{
    roomMetaData match {
      case s:SlideRoom => {
        val thisHistory = isPublic match {
          case true => history.filterCanvasContents(cc => cc.privacy == Privacy.PUBLIC)
          case false => history
        }
        debug("rendering snapshots for: %s %s".format(history.jid,Globals.snapshotSizes))
        val result = SlideRenderer.renderMultiple(thisHistory,Globals.snapshotSizes)
        debug("rendered snapshots for: %s %s".format(history.jid,result.map(tup => (tup._1,tup._2.length))))
        result
      }
      case _ => {
        Map.empty[RenderDescription,Array[Byte]]
      }
    }
  })
  override def getSnapshot(size:RenderDescription) = {
    showInterest
    snapshots.get(size).getOrElse(Array.empty[Byte])
  }
  override def getThumbnail = {
    getSnapshot(Globals.ThumbnailSize)
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
  override protected def sendToChildren(s:MeTLStanza):Unit = Stopwatch.time("HistoryCachingMeTLRoom.sendToChildren",{
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

class XmppBridgingHistoryCachingRoom(configName:String,override val location:String,creator:RoomProvider,override val roomMetaData:RoomMetaData) extends HistoryCachingRoom(configName,location,creator,roomMetaData) {
  protected var stanzasToIgnore = List.empty[MeTLStanza]
  def sendMessageFromBridge(s:MeTLStanza):Unit = Stopwatch.time("XmppBridgedHistoryCachingRoom.sendMessageFromBridge",{
    //stanzasToIgnore = stanzasToIgnore ::: List(s)
    //trace("xmppBridge, sending to server: %s".format(s))
    trace("XMPPBRIDGE (%s) sendToServer: %s".format(location,s))
    sendStanzaToServer(s)
  })
  protected def sendMessageToBridge(s:MeTLStanza):Unit = Stopwatch.time("XmppBridgedHistoryCachingROom.sendMessageFromBridge",{
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
