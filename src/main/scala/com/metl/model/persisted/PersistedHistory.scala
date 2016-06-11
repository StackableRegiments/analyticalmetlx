package com.metl.persisted

import com.metl.data._
import com.metl.utils._

import net.liftweb.util.Helpers._
import net.liftweb.common._
import java.util.Date

class PersistedHistory(serverName:String,dbInterface:PersistenceInterface) extends HistoryRetriever(serverName) {
  def getMeTLHistory(jid:String) = Stopwatch.time("EmbeddedHistory.getMeTLHistory", {
    dbInterface.getHistory(jid)
  })
}
