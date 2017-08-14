package com.metl.persisted

import com.metl.data._
import com.metl.utils._

import net.liftweb.util.Helpers._
import net.liftweb.common._
import java.util.Date

class PersistedHistory(config:ServerConfiguration,dbInterface:PersistenceInterface) extends HistoryRetriever(config) {
  def getMeTLHistory(jid:String) = Stopwatch.time("EmbeddedHistory.getMeTLHistory", {
    dbInterface.getHistory(jid)
  })
  def getProfiles(ids:String *):List[Profile] = dbInterface.getProfiles(ids:_*)
  def createProfile(name:String,attrs:Map[String,String]):Profile = dbInterface.createProfile(name,attrs)
  def updateProfile(id:String,profile:Profile):Profile = dbInterface.updateProfile(id,profile)
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = dbInterface.getProfileIds(accountName,accountProvider)
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = dbInterface.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)
}
