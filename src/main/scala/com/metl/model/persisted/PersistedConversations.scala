package com.metl.persisted

import com.metl.data._
import com.metl.utils._

class PersistedConversations(config:ServerConfiguration,dbInterface:PersistenceInterface,onConversationDetailsUpdated:Conversation=>Unit) extends ConversationRetriever(config,onConversationDetailsUpdated) {
  override def getAll = dbInterface.getAllConversations
  override def search(query:String) = dbInterface.searchForConversation(query)
  override def searchByCourse(courseId:String) = dbInterface.searchForConversationByCourse(courseId)
  override def conversationFor(slide:Int):Int = dbInterface.conversationFor(slide)
  override def detailsOf(jid:Int) = dbInterface.detailsOfConversation(jid)
  override def createConversation(title:String,author:String):Conversation = dbInterface.createConversation(title,author)
  override def deleteConversation(jid:String):Conversation = dbInterface.deleteConversation(jid)
  override def renameConversation(jid:String,newTitle:String):Conversation = dbInterface.renameConversation(jid,newTitle)
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = dbInterface.changePermissionsOfConversation(jid,newPermissions)
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = dbInterface.updateSubjectOfConversation(jid,newSubject)
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = dbInterface.addSlideAtIndexOfConversation(jid,index)
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation = dbInterface.addGroupSlideAtIndexOfConversation(jid,index,grouping)
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = dbInterface.reorderSlidesOfConversation(jid,newSlides)
  override def updateConversation(jid:String,conversation:Conversation):Conversation = dbInterface.updateConversation(jid,conversation)
}
