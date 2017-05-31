package com.metl.persisted

import com.metl.data._
import com.metl.utils._
import net.liftweb.common.Logger

class PersistingMessageBusProvider(config:ServerConfiguration,dbInterface:PersistenceInterface) extends OneBusPerRoomMessageBusProvider {
  override def createNewMessageBus(d:MessageBusDefinition) = Stopwatch.time("EmbeddedPersistingMessageBusProvider.createNewMessageBus",{
    new PersistingLoopbackMessageBus(config,d,dbInterface,this)
  })
}

class PersistingLoopbackMessageBus(config:ServerConfiguration,d:MessageBusDefinition,dbInterface:PersistenceInterface,provider:MessageBusProvider) extends MessageBus(d,provider) with Logger{
  val jid = d.location
  override def sendStanzaToRoom[A <: MeTLStanza](stanza:A,shouldUpdateTimestamp:Boolean = true) = Stopwatch.time("EmbeddedPersistingMessageBusProvider.sendStanzaToRoom",{
    trace("sendStanzaToRoom: %s".format(stanza))
    val newMessage = if (shouldUpdateTimestamp) {
      stanza.adjustTimestamp(new java.util.Date().getTime)
    } else {
      stanza
    }
    trace("sendStanzaToRoom adjusted for timestamp?: %s %s".format(shouldUpdateTimestamp, newMessage))
    storeStanzaInDB(newMessage).map(m => {
      // this shares the message between all busses that reach this location, which aren't this bus, because that's already taken care of, because in this case, they aren't connected to a higher level shared bus.
      provider.sendMessageToBus(b => b.location == d.location && b != d,m)
      recieveStanzaFromRoom(m)
      true
    }).getOrElse(false)
  })
  private def storeStanzaInDB[A <: MeTLStanza](stanza:A):Option[A] = Stopwatch.time("EmbeddedPersistingMessageBusProvider.storeStanzaInDB",{
    trace("dbInterface storeStanzaInDB: %s".format(stanza))
    dbInterface.storeStanza(jid,stanza)
  })
}
