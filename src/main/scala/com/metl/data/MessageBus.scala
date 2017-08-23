package com.metl.data

import java.util.concurrent.ConcurrentHashMap

import com.metl.utils._
import net.liftweb.common.Logger

import scala.collection.JavaConversions

// the feedback name should be bound to a particular onReceive function, so that we can use that feedbackName to match particular behaviours (given that the anonymous functions won't work that way for us)
class MessageBusDefinition(val location:String, val feedbackName:String, val onReceive:(MeTLStanza) => Unit = (s:MeTLStanza) => {}, val onConnectionLost:() => Unit = () => {}, val onConnectionRegained:() => Unit = () => {}){
  override def equals(other:Any):Boolean = {
    other match {
      case omb:MessageBusDefinition => omb.location == location && omb.feedbackName == feedbackName
      case _ => false
    }
  }
  override def hashCode = (location+feedbackName).hashCode
  override def toString = "MBD(%s,%s)".format(location,feedbackName)
}

abstract class MessageBusProvider extends Logger {
  def getMessageBus(definition:MessageBusDefinition):MessageBus
  def releaseMessageBus(definition:MessageBusDefinition):Unit = {}
  def sendMessageToBus(busFilter:MessageBusDefinition => Boolean, message:MeTLStanza):Unit = {}
}
abstract class OneBusPerRoomMessageBusProvider extends MessageBusProvider {
  protected lazy val busses = JavaConversions.mapAsScalaConcurrentMap(new ConcurrentHashMap[MessageBusDefinition,MessageBus]())
  protected def createNewMessageBus(definition:MessageBusDefinition):MessageBus
  override def getMessageBus(definition:MessageBusDefinition) = Stopwatch.time("OneBusPerRoomMessageBusProvider",busses.getOrElseUpdate(definition,createNewMessageBus(definition)))
  override def releaseMessageBus(definition:MessageBusDefinition) = Stopwatch.time("OneBusPerRoomMessageBusProvider",busses.remove(definition))
  override def sendMessageToBus(busFilter:MessageBusDefinition => Boolean,message:MeTLStanza):Unit = {
    busses.foreach(b => {
      if (busFilter(b._1)){
        b._2.recieveStanzaFromRoom(message)
      }
    })
  }
}
class LoopbackMessageBusProvider extends OneBusPerRoomMessageBusProvider {
  override def createNewMessageBus(definition:MessageBusDefinition) = Stopwatch.time("LoopbackMessageBusProvider",new LoopbackBus(definition,this))
}

class TappingMessageBusProvider(mbp:MessageBusProvider,upTap:MeTLStanza=>Unit = s => {},downTap:MeTLStanza=>Unit = s => {}) extends MessageBusProvider {
  override def getMessageBus(definition:MessageBusDefinition):MessageBus = {
    val newDef = new MessageBusDefinition(definition.location,definition.feedbackName,(s:MeTLStanza) => {
      try {
        downTap(s)
      } catch {
        case e:Exception => {
          error("tapbus threw exception: %s".format(e.getMessage),e)
        }
      }
      definition.onReceive(s)
    },definition.onConnectionLost,definition.onConnectionRegained)
    warn("creating messageBus from definition: %s => %s".format(definition,newDef))
    new TabBus(mbp.getMessageBus(newDef),upTap)
  }
  override def releaseMessageBus(definition:MessageBusDefinition):Unit = {
    mbp.releaseMessageBus(definition)
  }
  override def sendMessageToBus(busFilter:MessageBusDefinition => Boolean, message:MeTLStanza):Unit = {
    mbp.sendMessageToBus(busFilter,message)
  }
}

object EmptyMessageBusProvider extends MessageBusProvider{
  def getMessageBus(definition:MessageBusDefinition) = EmptyMessageBus
}

abstract class MessageBus(definition:MessageBusDefinition, creator:MessageBusProvider) extends Logger {
  def getDefinition:MessageBusDefinition = definition
  def getCreator:MessageBusProvider = creator
  def sendStanzaToRoom[A <: MeTLStanza](stanza:A,updateTimestamp:Boolean = true):Boolean
  def recieveStanzaFromRoom[A <: MeTLStanza](stanza:A) = definition.onReceive(stanza)
  def notifyConnectionLost = definition.onConnectionLost()
  def notifyConnectionResumed = definition.onConnectionRegained()
  def release = creator.releaseMessageBus(definition)
}

class TabBus(mb:MessageBus,upTap:MeTLStanza => Unit = s => {}) extends MessageBus(mb.getDefinition,mb.getCreator){
  override def getDefinition:MessageBusDefinition = mb.getDefinition
  override def getCreator:MessageBusProvider = mb.getCreator
  override def sendStanzaToRoom[A <: MeTLStanza](stanza:A,updateTimestamp:Boolean = true):Boolean = {
    upTap(stanza)
    mb.sendStanzaToRoom(stanza,updateTimestamp)
  } 
  override def notifyConnectionLost = mb.notifyConnectionLost
  override def notifyConnectionResumed = mb.notifyConnectionResumed
  override def release = mb.release
}

class LoopbackBus(definition:MessageBusDefinition,creator:MessageBusProvider) extends MessageBus(definition,creator) with Logger {
  override def sendStanzaToRoom[A <: MeTLStanza](stanza:A,updateStanza:Boolean = true):Boolean = {
    val newMessage = if (updateStanza) {
      stanza.adjustTimestamp(new java.util.Date().getTime)
    } else {
      stanza
    }
    debug("LoopbackBus: %s returning: %s -> %s".format(definition,stanza,newMessage))
    recieveStanzaFromRoom(newMessage)
    true
  }
}
object EmptyMessageBus extends MessageBus(new MessageBusDefinition("empty","throwaway"),EmptyMessageBusProvider){
  override def sendStanzaToRoom[A <: MeTLStanza](stanza:A,updateStanza:Boolean = true):Boolean = false
  override def recieveStanzaFromRoom[A <: MeTLStanza](stanza:A) = {}
}
