package com.metl.persisted

import com.metl.data._
import com.metl.utils._

class PersistedConversations(configName:String,dbInterface:PersistenceInterface,onConversationDetailsUpdated:Conversation=>Unit) extends ConversationRetriever(configName,onConversationDetailsUpdated) {
	override def search(query:String) = dbInterface.searchForConversation(query)
	override def conversationFor(slide:Int):Int = dbInterface.conversationFor(slide)
	override def detailsOf(jid:Int) = dbInterface.detailsOfConversation(jid)
	override def createConversation(title:String,author:String):Conversation = dbInterface.createConversation(title,author)
	override def deleteConversation(jid:String):Conversation = dbInterface.deleteConversation(jid)
	override def renameConversation(jid:String,newTitle:String):Conversation = dbInterface.renameConversation(jid,newTitle)
	override def changePermissions(jid:String,newPermissions:Permissions):Conversation = dbInterface.changePermissionsOfConversation(jid,newPermissions)
	override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = dbInterface.updateSubjectOfConversation(jid,newSubject)
	override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = dbInterface.addSlideAtIndexOfConversation(jid,index)
	override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = dbInterface.reorderSlidesOfConversation(jid,newSlides)
  override def updateConversation(jid:String,conversation:Conversation):Conversation = dbInterface.updateConversation(jid,conversation)
}

