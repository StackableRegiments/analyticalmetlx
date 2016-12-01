package com.metl.persisted

import com.metl.data._
import com.metl.utils._

class PersistedResourceProvider(config:ServerConfiguration,dbInterface:PersistenceInterface,commonBucket:String = "commonBucket"){
  def getResource(identity:String):Array[Byte] = dbInterface.getResource(identity)
  def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = dbInterface.postResource(jid,userProposedId,data)
  def getResource(jid:String,identity:String):Array[Byte] = dbInterface.getResource(jid,identity)
  def insertResource(data:Array[Byte],jid:String = commonBucket):String = dbInterface.insertResource(jid,data)
  def upsertResource(identity:String,data:Array[Byte],jid:String = commonBucket):String = dbInterface.upsertResource(jid,identity,data)
}
