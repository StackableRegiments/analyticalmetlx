package com.metl.persisted

import com.metl.data._
import com.metl.utils._

class PersistingMessageBusProvider(config:ServerConfiguration,dbInterface:PersistenceInterface) extends OneBusPerRoomMessageBusProvider {
  override def createNewMessageBus(d:MessageBusDefinition) = Stopwatch.time("EmbeddedPersistingMessageBusProvider.createNewMessageBus",{
    new PersistingLoopbackMessageBus(config,d,dbInterface,this)
  })
}

class PersistingLoopbackMessageBus(config:ServerConfiguration,d:MessageBusDefinition,dbInterface:PersistenceInterface,provider:MessageBusProvider) extends MessageBus(d,provider){
  val jid = d.location
  override def sendStanzaToRoom[A <: MeTLStanza](stanza:A,shouldUpdateTimestamp:Boolean = true) = Stopwatch.time("EmbeddedPersistingMessageBusProvider.sendStanzaToRoom",{
    val newMessage = if (shouldUpdateTimestamp) {
      stanza.adjustTimestamp(new java.util.Date().getTime)
    } else {
      stanza
    }
    storeStanzaInDB(newMessage).map(m => {
      // this shares the message between all busses that reach this location, which aren't this bus, because that's already taken care of, because in this case, they aren't connected to a higher level shared bus.
      provider.sendMessageToBus(b => b.location == d.location && b != d,m)
      recieveStanzaFromRoom(m)
      true
    }).getOrElse(false)
  })
  private def storeStanzaInDB[A <: MeTLStanza](stanza:A):Option[A] = Stopwatch.time("EmbeddedPersistingMessageBusProvider.storeStanzaInDB",{
    dbInterface.storeStanza(jid,stanza)
  })
}
