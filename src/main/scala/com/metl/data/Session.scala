package com.metl.data

object SessionRecordAction {
  val Started = "started"
  val Terminated = "terminated"
  val ChangedProfile = "profile_change"
  val ChangedIP = "ip_change"
  val ChangedUserAgent = "user_agent_change"
  val Unknown = "unknown"
}

case class SessionRecord(sid:String, accountProvider:String,accountName:String,profileId:String,ipAddress:String,userAgent:String,action:String,timestamp:Long){
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
