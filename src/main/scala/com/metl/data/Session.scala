package com.metl.data

object SessionRecordAction extends Enumeration {
  type SessionRecordAction = Value
  val Started,Terminated,ChangedProfile,ChangedIP,ChangedUserAgent,Unknown = Value
  def serialize(in:SessionRecordAction.Value):String = in match {
    case SessionRecordAction.Started => "started"
    case SessionRecordAction.Terminated => "terminated"
    case SessionRecordAction.ChangedProfile => "profile_change"
    case SessionRecordAction.ChangedIP => "ip_change"
    case SessionRecordAction.ChangedUserAgent => "user_agent_change"
    case SessionRecordAction.Unknown => "unknown"
  }
  def parse(in:String):SessionRecordAction.Value = in.toLowerCase.trim match {
    case "started" => SessionRecordAction.Started  
    case "terminated" => SessionRecordAction.Terminated  
    case "profile_change" => SessionRecordAction.ChangedProfile  
    case "ip_change" => SessionRecordAction.ChangedIP  
    case "user_agent_change" => SessionRecordAction.ChangedUserAgent  
    case _ => SessionRecordAction.Unknown  
  }
}

case class SessionRecord(sid:String, accountProvider:String,accountName:String,profileId:String,ipAddress:String,userAgent:String,action:SessionRecordAction.Value,timestamp:Long){
  def isUpdateOf(other:SessionRecord):Boolean = {
    other.sid == sid && sid != "" && (other.profileId != profileId || other.ipAddress != ipAddress || other.userAgent != userAgent || other.action != action)
  }
}
object SessionRecord {
  def empty:SessionRecord = SessionRecord("","","","","","",SessionRecordAction.Unknown,0L)
}

trait SessionManager {
  def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord]
  def updateSession(sessionRecord:SessionRecord):SessionRecord
  def getCurrentSessions:List[SessionRecord]
}
/*
// note that this isn't cluster-safe yet

class CachingSessionManager(sessionManager:SessionManager) extends SessionManager {
  protected val store:scala.collection.mutable.HashMap[Tuple2[String,String],List[SessionRecord]] = new scala.collection.mutable.HashMap[Tuple2[String,String],List[SessionRecord]]()
  override def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord] = {
    val id = (accountName,accountProvider)
    store.get(id).getOrElse({
      val sessions = sessionManager.getSessionsForAccount(accountName,accountProvider)
      store.put(id,sessions)
      sessions
    })
  }
  override def updateSession(sessionRecord:SessionRecord):SessionRecord = {
    val currentSessions = getSessionsForAccount(sessionRecord.accountName,sessionRecord.accountProvider).filter(_.sid == sessionRecord.sid).sortBy(_.timestamp).reverse.headOption
    if (currentSessions.exists(_.isUpdateOf(sessionRecord))){
      val id = (sessionRecord.accountName,sessionRecord.accountProvider)
      val terminated = sessionRecord.action == SessionRecordAction.Terminated
      store.put(id,(store.get(id).getOrElse(Nil) ::: List(sessionRecord)).filterNot(s => terminated && s.sid == sessionRecord.sid))
      sessionManager.updateSession(sessionRecord)
    }
    sessionRecord
  }
  override def getCurrentSessions:List[SessionRecord] = {
    store.values.toList.flatten
  }
}
*/
