/*

package com.metl.model

import org.jivesoftware.smack._
import org.jivesoftware.smack.filter._
import org.jivesoftware.smack.packet._
import org.jivesoftware.smackx.muc._
import net.liftweb.actor._
import net.liftweb.common._
import java.util.Random
import com.metl.comet.{StackOverflow,RemoteQuestionRecieved,RemoteSilentQuestionRecieved,ReputationServer,TopicServer}
import net.liftweb.util.Helpers._


class FakeXmpp extends Xmpp("no_username","no_password","localFakeXmpp","no_room") {
  override def connectToXmpp:Unit = {}
  override def disconnectFromXmpp:Unit = {}
  override val resource:String = "no_resource"

  override def notifyRoomOfReputation(who:String,action:Int):Unit = updateLocalReputation(new XMPPReputationAnnouncer(who,action))
  override def notifyRoomOfQuestion(topic:String,questionId:String,silent:Boolean):Unit = updateLocalQuestions(new XMPPQuestionAnnouncer(questionId,topic,silent))
  override def notifyRoomOfTopic(topicId:String):Unit = updateLocalTopics(new XMPPTopicAnnouncer(topicId))
  override def initializeXmpp:Unit = {}
  override def register:Unit = {}
  override def mucFor(roomName:String) = Empty
  override def joinRoom(roomName:String):Box[MultiUserChat] = Empty
  override def leaveRoom(room:MultiUserChat):Unit = {}
}

class Xmpp(username:String,password:String,incomingHost:String, incomingRoomName:String) {
  val host = incomingHost
  val roomName = incomingRoomName
  var room:Box[MultiUserChat] = Empty
  protected val resource:String = "stackConnector_%s_%s".format(roomName,new java.util.Date().getTime.toString)
  private var conn:Box[XMPPConnection] = Empty
  private val config:ConnectionConfiguration ={
    val port = 5222
    val loadRosterAtLogin = false
    val sendPresence = false
    val acceptSelfSignedCerts = true
    val allowReconnects = true
    val allowCompression = false
    val debug = Globals.isDevMode

    val c = new ConnectionConfiguration(host,port)
    c.setRosterLoadedAtLogin(loadRosterAtLogin)
    c.setSendPresence(sendPresence)
    c.setSelfSignedCertificateEnabled(acceptSelfSignedCerts)
    c.setReconnectionAllowed(allowReconnects)
    c.setCompressionEnabled(allowCompression)
    c.setDebuggerEnabled(debug)
    c
  }
  protected def initializeXmpp:Unit = {
    connectToXmpp
    room = joinRoom(roomName)
    val filter = new AndFilter( new PacketTypeFilter(classOf[Message]), new MessageTypeFilter(List("questionAnnouncer","reputationAnnouncer","topicAnnouncer")))
    conn.map(c => c.addPacketListener(new RemoteSyncListener,filter))
  }
  initializeXmpp

  def connectToXmpp:Unit = {
    disconnectFromXmpp
    conn = tryo(new XMPPConnection(config))
    conn.map(c => c.connect)
    try {
      conn.map(c => c.login(username,password,resource))
    }
    catch {
      case e:XMPPException if (e.getMessage.contains("not-authorized")) => {
        disconnectFromXmpp
        conn = tryo(new XMPPConnection(config))
        conn.map(c => {
          c.connect
          register
          c.login(username,password,resource)
        })
      }
    }
  }

  def disconnectFromXmpp:Unit = conn.map(c => c.disconnect(new Presence(Presence.Type.unavailable)))

  protected def register:Unit ={
    conn.map(c => {
      val accountManager = c.getAccountManager
      accountManager.createAccount(username,password)
    })
  }

  protected def mucFor(room:String):Box[MultiUserChat] = {
    conn.map(c => {
      val roomJid = "%s@conference.%s".format(room,host)
      new MultiUserChat(c,roomJid)
    })
  }
  def joinRoom(room:String):Box[MultiUserChat] = {
    val roomJid = "%s@conference.%s".format(room,host)
    conn.map(c => {
      val muc = new MultiUserChat(c,roomJid)
      muc.join(resource)
      muc
    })
  }
  def notifyRoomOfReputation(who:String,action:Int):Unit = {
    room.map(r => {
      val message = r.createMessage
      message.addExtension(new XMPPReputationAnnouncer(who,action))
      r.sendMessage(message)
    })
  }
  def notifyRoomOfQuestion(topic:String,questionId:String,silent:Boolean):Unit = {
    room.map(r => {
      val message = r.createMessage
      message.addExtension(new XMPPQuestionAnnouncer(questionId,topic,silent))
      r.sendMessage(message)
    })
  }
  def notifyRoomOfTopic(topicId:String):Unit = {
    room.map(r => {
      val message = r.createMessage
      val ta = new XMPPTopicAnnouncer(topicId)
      message.addExtension(ta)
      r.sendMessage(message)
    })
  }
  def leaveRoom(room:MultiUserChat):Unit = {
    room.leave
  }
  class MessageTypeFilter(predicates:List[String]) extends PacketFilter{
    def accept(message:Packet)={
      var smackMessage = message.asInstanceOf[Message]
      List(smackMessage.getExtensions.toArray:_*).filter(ex => predicates.contains(ex.asInstanceOf[PacketExtension].getElementName)).length > 0
    }
  }
  class RemoteSyncListener extends PacketListener{
    def processPacket(packet:Packet)={
      List(packet.getExtensions.toArray:_*).map(e => {
        val ext = e.asInstanceOf[PacketExtension]
        ext.getElementName match {
          case "questionAnnouncer" => {
            val qa = XMPPQuestionAnnouncer.fromXMLString(ext.toXML)
            qa.map(updateLocalQuestions _)
          }
          case "reputationAnnouncer" => {
            val ra = XMPPReputationAnnouncer.fromXMLString(ext.toXML)
            ra.map(updateLocalReputation _)
          }
          case "topicAnnouncer" => {
            val ta = XMPPTopicAnnouncer.fromXMLString(ext.toXML)
            ta.map(updateLocalTopics _)
          }
          case other => println("Xmpp:QuestionAnnouncerListener:processPacket:getExtensions returned unknown extension: %s".format(other.toString))
        }
      })
    }
  }
  def updateLocalQuestions(qa:XMPPQuestionAnnouncer) = {
    qa.silent match {
      case true => StackOverflow.remoteMessageRecieved(RemoteSilentQuestionRecieved(qa.questionId),qa.topic)
      case false => StackOverflow.remoteMessageRecieved(RemoteQuestionRecieved(qa.questionId),qa.topic)
    }
  }
  def updateLocalReputation(ra:XMPPReputationAnnouncer) = com.metl.model.Reputation.addStanding(ra.user,GainAction.apply(ra.action))
  def updateLocalTopics(ta:XMPPTopicAnnouncer) = TopicManager.updateTopics(ta.topicId)
}
object XMPPQuestionAnnouncer {
  def fromXMLString(xString:String):Box[XMPPQuestionAnnouncer] = tryo({
    val xmlNode = scala.xml.XML.loadString(xString)
    val questionId = (xmlNode \\ "question").head.text
    val topic = (xmlNode \\ "topic").head.text
    val silent =(xmlNode \\ "silent").head.text.toBoolean
    new XMPPQuestionAnnouncer(questionId,topic,silent)
  })
}
class XMPPQuestionAnnouncer(incomingQuestionId:String,incomingTopic:String,incomingSilent:Boolean) extends PacketExtension{
  val topic = incomingTopic
  val questionId = incomingQuestionId
  val silent = incomingSilent
  override val getNamespace="monash:stack"
  override val getElementName="questionAnnouncer"
  override val toXML={
    val stanza = <questionAnnouncer xmlns={getNamespace}>
    <question>{questionId}</question>
    <topic>{topic}</topic>
    <silent>{silent}</silent>
    </questionAnnouncer>;
    stanza.toString;
  }
}
object XMPPTopicAnnouncer {
  def fromXMLString(xString:String):Box[XMPPTopicAnnouncer] = tryo({
    val xmlNode = scala.xml.XML.loadString(xString)
    val topicId = (xmlNode \\ "topicId").head.text
    new XMPPTopicAnnouncer(topicId)
  })
}
class XMPPTopicAnnouncer(incomingTopicId:String) extends PacketExtension{
  val topicId = incomingTopicId
  override val getNamespace = "monash:stack"
  override val getElementName = "topicAnnouncer"
  override val toXML = {
    val stanza = <topicAnnouncer xmlns={getNamespace}>
    <topicId>{topicId}</topicId>
    </topicAnnouncer>;
    stanza.toString;
  }
}

object XMPPReputationAnnouncer {
  def fromXMLString(xString:String):Box[XMPPReputationAnnouncer] = tryo({
    val xmlNode = scala.xml.XML.loadString(xString)
    val user = (xmlNode \\ "user").head.text
    val action = (xmlNode \\ "action").head.text.toInt
    new XMPPReputationAnnouncer(user,action)
  })
}
class XMPPReputationAnnouncer(incomingUser:String,incomingAction:Int) extends PacketExtension{
  val user = incomingUser
  val action = incomingAction
  override val getNamespace="monash:stack"
  override val getElementName="reputationAnnouncer"
  override val toXML={
    val stanza = <reputationAnnouncer xmlns={getNamespace}>
    <user>{user}</user>
    <action>{action}</action>
    </reputationAnnouncer>;
    stanza.toString;
  }
}

object XMPPRepSyncActor extends XMPPSyncActor
object XMPPQuestionSyncActor extends XMPPSyncActor

case class InitializeXMPP(server:String,username:String,password:String,room:String)
case object InitializeFakeXMPP
case class QuestionSyncRequest(topic:String,questionId:String,silent:Boolean)
case class ReputationSyncRequest(user:String,gainAction:Int)
case class TopicSyncRequest(topicId:String)
class XMPPSyncActor extends LiftActor {
  private var xmpp:Box[Xmpp] = Empty
  override def messageHandler ={
    case QuestionSyncRequest(topic,questionId,silent) => xmpp.map(x => x.notifyRoomOfQuestion(topic,questionId,silent))
    case ReputationSyncRequest(who,what) => xmpp.map(x => x.notifyRoomOfReputation(who,what))
    case TopicSyncRequest(topic) => xmpp.map(x => x.notifyRoomOfTopic(topic))
    case InitializeXMPP(server,username,password,room) => {
      xmpp match {
        case Full(x) => x.disconnectFromXmpp
        case _ => {}
      }
      xmpp = Full(new Xmpp(username,password,server,room))
      xmpp.map(x => println("starting XMPPActor for %s@%s".format(x.roomName,x.host)))
    }
    case InitializeFakeXMPP => {
      xmpp match {
        case Full(x) => x.disconnectFromXmpp
        case _ => {}
      }
      xmpp = Full(new FakeXmpp)
      xmpp.map(x => println("starting XMPPActor for fakeXMPP"))
    }
    case other => println("XMPPSyncActor (%s) received unknown message: %s".format(xmpp.map(x => x.roomName).openOr("unknown room"),other))
  }
}
*/
