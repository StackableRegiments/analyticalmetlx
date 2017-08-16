package com.metl.persisted

import com.metl.data._
import com.metl.utils._

abstract class PersistenceInterface(config:ServerConfiguration) {
  def isReady:Boolean 
  def shutdown:Boolean
  //stanzas
  def storeStanza[A <: MeTLStanza](jid:String,stanza:A):Option[A]
  def getHistory(jid:String):History

  //conversations
  def getAllConversations:List[Conversation]
  def searchForConversation(query:String):List[Conversation]
  def searchForConversationByCourse(courseId:String):List[Conversation]
  def conversationFor(slide:Int):Int
  def detailsOfConversation(jid:Int):Conversation
  def createConversation(title:String,author:String):Conversation
  def deleteConversation(jid:String):Conversation
  def renameConversation(jid:String,newTitle:String):Conversation
  def changePermissionsOfConversation(jid:String,newPermissions:Permissions):Conversation
  def updateSubjectOfConversation(jid:String,newSubject:String):Conversation
  def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation
  def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation
  def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation
  def updateConversation(jid:String,conversation:Conversation):Conversation

  //resources
  def getResource(identity:String):Array[Byte]
  def postResource(jid:String,userProposedId:String,data:Array[Byte]):String
  def getResource(jid:String,identity:String):Array[Byte]
  def insertResource(jid:String,data:Array[Byte]):String
  def upsertResource(jid:String,identity:String,data:Array[Byte]):String

  //profiles
  def getProfiles(ids:String *):List[Profile]
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile
  def updateProfile(id:String,profile:Profile):Profile
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String]
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit
  def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord]
  def getSessionsForProfile(profileId:String):List[SessionRecord]
  def updateSession(sessionRecord:SessionRecord):SessionRecord
  def getCurrentSessions:List[SessionRecord]
}
