package com.metl.metl2011

import com.metl.data.{Group=>MeTLGroup,_}
import com.metl.utils._
import com.metl.xmpp._

import java.util.Random
import net.liftweb.util.Helpers._
import net.liftweb.common.Logger
import scala.xml._
import java.util.Date
import scala.collection.mutable.HashMap

class XmppProvider(config:ServerConfiguration,hostname:String,credentialsFunc:()=>Tuple2[String,String],domainName:String) extends OneBusPerRoomMessageBusProvider with Logger {
  override def createNewMessageBus(d:MessageBusDefinition) = Stopwatch.time("XmppProvider.createNewMessageBus",{
    new XmppMessageBus(config,hostname,credentialsFunc,domainName,d,this)
  })
  def getHostname = hostname
  def getDomainName = domainName
}

class PooledXmppProvider(config:ServerConfiguration,hostname:String,credentialsFunc:()=>Tuple2[String,String],domainName:String) extends OneBusPerRoomMessageBusProvider with Logger {
  protected val connMgr = new XmppConnProvider(config,hostname,credentialsFunc,domainName)
  override def createNewMessageBus(d:MessageBusDefinition) = Stopwatch.time("PooledXmppProvider.createNewMessageBus",{
    val conn = connMgr.getConn
    val bus = new XmppSharedConnMessageBus(config,hostname,credentialsFunc,domainName,d,this)
    bus.addConn(conn)
    conn.addMessageBus(d,bus)
    bus
  })
  def getHostname = hostname
  def getDomainName = domainName
}

class XmppConnProvider(config:ServerConfiguration,hostname:String,credentialsFunc:()=>Tuple2[String,String],domainName:String) extends Logger {
  protected var conns = List.empty[MeTL2011XmppMultiConn]
  protected val maxCount = 20
  def getConn:MeTL2011XmppMultiConn = {
    debug("XMPPConnProvider:getConn")
    conns.find(c => c.getCount < maxCount).getOrElse({
      val now = new Date().getTime.toString
      val newConn = new MeTL2011XmppMultiConn(credentialsFunc,"metlxConnector_"+now,hostname,domainName,config,this)
      conns = newConn :: conns
      debug("XMPPConnProvider:getConn.createConn(%s)".format(newConn))
      newConn
    })
  }
  def releaseConn(c:MeTL2011XmppMultiConn) = {
    debug("XMPPConnProvider:releaseConn")
    if (c.getCount < 1){
      conns = conns.filterNot(conn => conn == c)
      trace("XMPPConnProvider:releaseConn.disconnectingConn(%s)".format(c))
      c.disconnectFromXmpp
    }
  }
}

class MeTL2011XmppMultiConn(cf:()=>Tuple2[String,String],r:String,h:String,d:String,config:ServerConfiguration,creator:XmppConnProvider) extends XmppConnection[MeTLData](cf,r,h,d,None) with Logger {
  protected lazy val serializer = new MeTL2011XmlSerializer(config,true)

  //  override lazy val debug = true

  private val subscribedBusses = new HashMap[String,HashMap[MessageBusDefinition,MessageBus]]

  private var subscriptionCount = 0

  def getCount = subscriptionCount

  override def onConnLost = {
    subscribedBusses.values.foreach(sbl => sbl.keys.foreach(mbd => {
      debug("connLost: %s -> %s".format(mbd.location,mbd.feedbackName))
      mbd.onConnectionLost()
    }))
  }
  override def onConnRegained = {
    subscribedBusses.values.foreach(sbl => sbl.keys.foreach(mbd => {
      debug("connRegained: %s -> %s".format(mbd.location,mbd.feedbackName))
      mbd.onConnectionRegained()
    }))
  }

  def addMessageBus(d:MessageBusDefinition,m:MessageBus) = {
    debug("XMPPMultiConn(%s):addMessageBus(%s)".format(this,d))
    val oldLocMap = subscribedBusses.get(d.location).getOrElse(HashMap.empty[MessageBusDefinition,MessageBus])
    oldLocMap.put(d,m) match {
      case Some(_) => {}
      case None => subscriptionCount += 1
    }
    subscribedBusses.put(d.location,oldLocMap)
  }
  def removeMessageBus(d:MessageBusDefinition) = {
    debug("XMPPMultiConn(%s):removeMessageBus(%s)".format(this,d))
    subscriptionCount -= 1
    subscribedBusses(d.location).remove(d)
    creator.releaseConn(this)
  }
  override def onMessageRecieved(room:String, messageType:String, message:MeTLData) = {
    trace("XMPPMultiConn(%s):onMessageReceived(%s,%s)".format(this,room,messageType))
    val targets = subscribedBusses(room).values
    trace("XMPPMultiConn(%s):onMessageReceived.sendTo(%s)".format(this,targets))
    message match {
      case s:MeTLStanza => targets.foreach(_.recieveStanzaFromRoom(s))
      case _ => {}
    }
    //targets.foreach(mb => mb.recieveStanzaFromRoom(message))
  }
  override def onUntypedMessageRecieved(room:String,message:String) = {
    val parts = message.split(" ")
    trace("XMPPMultiConn(%s):onUntypedMessageReceived(%s,%s,%s)".format(this,room,message))
    val targets = subscribedBusses(room).values
    trace("XMPPMultiConn(%s):onUntypedMessageReceived.sendTo(%s)".format(this,targets))
    targets.foreach(mb => mb.recieveStanzaFromRoom(MeTLCommand(config,"unknown",new java.util.Date().getTime,parts.head,parts.tail.toList)))
  }
  override lazy val ignoredTypes = List("metlMetaData")
  override lazy val subscribedTypes = List("ink","textbox","image","dirtyInk","dirtyText","dirtyImage","screenshotSubmission","submission","quiz","quizResponse","command","moveDelta","teacherstatus","attendance").map(item => {
    val ser = (i:MeTLData) => {
      val xml = serializer.fromMeTLData(i)
      val messages = xml
      val head = messages.headOption
      head.map{
        case g:Group => g.nodes.headOption.getOrElse(NodeSeq.Empty)
        case e:Elem => e.child.headOption.getOrElse(NodeSeq.Empty)
      }.getOrElse(NodeSeq.Empty)
    }
    val deser = (s:NodeSeq) => {
      serializer.toMeTLData(s)
    }
    XmppDataType[MeTLData](item,ser,deser)
  })
}

class MeTL2011XmppConn(cf:()=>Tuple2[String,String],r:String,h:String,d:String,config:ServerConfiguration,bus:MessageBus) extends XmppConnection[MeTLData](cf,r,h,d,None,bus.notifyConnectionLost _,bus.notifyConnectionResumed _) with Logger {
  protected lazy val serializer = new MeTL2011XmlSerializer(config,true)

  //    override lazy val debug = true

  override def onConnLost = {
    val mbd = bus.getDefinition
    debug("singleConnLost: %s -> %s".format(mbd.location,mbd.feedbackName))
    mbd.onConnectionLost()
  }
  override def onConnRegained = {
    val mbd = bus.getDefinition
    debug("singleConnRegained: %s -> %s".format(mbd.location,mbd.feedbackName))
    mbd.onConnectionRegained()
  }

  override def onMessageRecieved(room:String, messageType:String, message:MeTLData) = {
    message match {
      case s:MeTLStanza => bus.recieveStanzaFromRoom(s)
      case _ => {}
    }
  }
  override def onUntypedMessageRecieved(room:String,message:String) = {
    val parts = message.split(" ")
    bus.recieveStanzaFromRoom(MeTLCommand(config,"unknown",new java.util.Date().getTime,parts.head,parts.tail.toList))
  }
  override lazy val ignoredTypes = List("metlMetaData")
  override lazy val subscribedTypes = List("ink","textbox","image","dirtyInk","dirtyText","dirtyImage","screenshotSubmission","submission","quiz","quizResponse","command","moveDelta","teacherstatus","attendance").map(item => {
    val ser = (i:MeTLData) => {
      val xml = serializer.fromMeTLData(i)
      val messages = xml
      val head = messages.headOption
      head.map{
        case g:Group => g.nodes.headOption.getOrElse(NodeSeq.Empty)
        case e:Elem => e.child.headOption.getOrElse(NodeSeq.Empty)
      }.getOrElse(NodeSeq.Empty)
    }
    val deser = (s:NodeSeq) => {
      serializer.toMeTLData(s)
    }
    XmppDataType[MeTLData](item,ser,deser)
  })
}

class XmppSharedConnMessageBus(config:ServerConfiguration,hostname:String,credentialsFunc:()=>Tuple2[String,String],domain:String,d:MessageBusDefinition,creator:MessageBusProvider) extends MessageBus(d,creator) with Logger {
  val jid = d.location
  protected var xmpp:Option[MeTL2011XmppMultiConn] = None
  def addConn(conn:MeTL2011XmppMultiConn) = {
    debug("XMPPSharedConnMessageBus(%s):addConn(%s)".format(d,conn))
    xmpp = Some(conn)
    xmpp.map(x => x.joinRoom(jid,this.hashCode.toString))
  }
  override def sendStanzaToRoom[A <: MeTLStanza](stanza:A,shouldUpdateTimestamp:Boolean = true):Boolean = Stopwatch.time("XmppSharedConnMessageBus.sendStanzaToRoom",{
    debug("XMPPSharedConnMessageBus(%s):sendStanzaToRoom(%s)".format(d,xmpp))
    stanza match {
      case i:MeTLInk =>{
        xmpp.map(x => x.sendMessage(jid,"ink",i))
        true}
      case t:MeTLText =>{
        xmpp.map(x => x.sendMessage(jid,"textbox",t))
        true}
      case i:MeTLImage =>{
        xmpp.map(x => x.sendMessage(jid,"image",i))
        true}
      case di:MeTLDirtyInk => {
        xmpp.map(x => x.sendMessage(jid,"dirtyInk",di))
        true}
      case dt:MeTLDirtyText =>{
        xmpp.map(x => x.sendMessage(jid,"dirtyText",dt))
        true}
      case di:MeTLDirtyImage =>{
        xmpp.map(x => x.sendMessage(jid,"dirtyImage",di))
        true}
      case q:MeTLQuiz =>{
        xmpp.map(x => x.sendMessage(jid,"quiz",q))
        true}
      case qr:MeTLQuizResponse =>{
        xmpp.map(x => x.sendMessage(jid,"quizResponse",qr))
        true}
      case s:MeTLSubmission =>{
        xmpp.map(x => x.sendMessage(jid,"submission",s))
        true}
      case c:MeTLCommand =>{
        xmpp.map(x => x.sendSimpleMessage(jid,(c.command :: c.commandParameters).mkString(" ")))
        true}
      case d:MeTLMoveDelta =>{
        xmpp.map(x => x.sendMessage(jid,"moveDelta",d))
        true}
      case a:Attendance => {
        xmpp.map(x => x.sendMessage(jid,"attendance",a))
        true
      }
      case t:MeTLTheme => {
        xmpp.map(x => x.sendMessage(jid,"theme",t))
        true
      }
      case _ => {
        false
      }
    }
  })
  override def release = {
    debug("XMPPSharedConnMessageBus(%s):release".format(d))
    xmpp.map(x => x.leaveRoom(jid,this.hashCode.toString))
    xmpp.map(x => x.removeMessageBus(d))
    super.release
  }
}
class XmppMessageBus(config:ServerConfiguration,hostname:String,credentialsFunc:()=>Tuple2[String,String],domain:String,d:MessageBusDefinition,creator:MessageBusProvider) extends MessageBus(d,creator) with Logger {
  val jid = d.location
  lazy val xmpp = new MeTL2011XmppConn(credentialsFunc,"metlxConnector_%s".format(new Date().getTime.toString),hostname,domain,config,this)
  xmpp.joinRoom(jid,this.hashCode.toString)
  override def sendStanzaToRoom[A <: MeTLStanza](stanza:A,shouldUpdateStanza:Boolean = true):Boolean = stanza match {
    case i:MeTLInk =>{
      xmpp.sendMessage(jid,"ink",i)
      true}
    case t:MeTLText =>{
      xmpp.sendMessage(jid,"textbox",t)
      true}
    case i:MeTLImage =>{
      xmpp.sendMessage(jid,"image",i)
      true}
    case di:MeTLDirtyInk => {
      xmpp.sendMessage(jid,"dirtyInk",di)
      true}
    case dt:MeTLDirtyText =>{
      xmpp.sendMessage(jid,"dirtyText",dt)
      true}
    case di:MeTLDirtyImage =>{
      xmpp.sendMessage(jid,"dirtyImage",di)
      true}
    case q:MeTLQuiz =>{
      xmpp.sendMessage(jid,"quiz",q)
      true}
    case qr:MeTLQuizResponse =>{
      xmpp.sendMessage(jid,"quizResponse",qr)
      true}
    case s:MeTLSubmission =>{
      xmpp.sendMessage(jid,"submission",s)
      true}
    case c:MeTLCommand =>{
      xmpp.sendSimpleMessage(jid,(c.command :: c.commandParameters).mkString(" "))
      true}
    case d:MeTLMoveDelta =>{
      xmpp.sendMessage(jid,"moveDelta",d)
      true}
    case a:Attendance => {
      xmpp.sendMessage(jid,"attendance",a)
      true
    }
    case t:MeTLTheme => {
      xmpp.sendMessage(jid,"theme",t)
      true
    }
    case _ => {
      false
    }
  }
  override def release = {
    xmpp.disconnectFromXmpp
    super.release
  }
}
