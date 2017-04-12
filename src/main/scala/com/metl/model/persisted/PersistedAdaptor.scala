package com.metl.persisted

import com.metl.utils._
import com.metl.data._

abstract class PersistedAdaptor(name:String,host:String,onConversationUpdated:Conversation=>Unit) extends ServerConfiguration(name,host,onConversationUpdated){
  protected val dbInterface:PersistenceInterface
  protected lazy val messageBusProvider = new PersistingMessageBusProvider(this,dbInterface)
  protected lazy val history = new PersistedHistory(this,dbInterface)
  protected lazy val conversations = new PersistedConversations(this,dbInterface,onConversationUpdated)
  protected lazy val resourceProvider = new PersistedResourceProvider(this,dbInterface)
  override def shutdown = {
    dbInterface.shutdown
    super.shutdown
  }
  override def isReady = {
    dbInterface.isReady
    super.isReady
  }
  override def getMessageBus(d:MessageBusDefinition) = messageBusProvider.getMessageBus(d)
  override def getHistory(jid:String) = history.getMeTLHistory(jid)
  override def getConversationForSlide(slideJid:String) = conversations.conversationFor(slideJid.toInt).toString
  override def getAllConversations = conversations.getAll
  override def searchForConversation(query:String) = conversations.search(query)
  override def searchForConversationByCourse(courseId:String) = conversations.searchByCourse(courseId)
  override def detailsOfConversation(jid:String) = conversations.detailsOf(jid.toInt)
  override def createConversation(title:String,author:String) = conversations.createConversation(title,author)
  override def deleteConversation(jid:String):Conversation = conversations.deleteConversation(jid)
  override def renameConversation(jid:String,newTitle:String):Conversation = conversations.renameConversation(jid,newTitle)
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = conversations.changePermissions(jid,newPermissions)
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = conversations.updateSubjectOfConversation(jid,newSubject)
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = conversations.addSlideAtIndexOfConversation(jid,index)
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation = conversations.addGroupSlideAtIndexOfConversation(jid,index,grouping)
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = conversations.reorderSlidesOfConversation(jid,newSlides)
  override def updateConversation(jid:String,conversation:Conversation):Conversation = conversations.updateConversation(jid,conversation)
  override def getImage(jid:String,identity:String) = history.getMeTLHistory(jid).getImageByIdentity(identity).getOrElse(MeTLImage.empty)
  override def getResource(jid:String,url:String) = resourceProvider.getResource(url)
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = resourceProvider.postResource(jid,userProposedId,data)
  override def insertResource(jid:String,data:Array[Byte]):String = resourceProvider.insertResource(data,jid)
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = resourceProvider.upsertResource(identifier,data,jid)
  override def getImage(identity:String):MeTLImage = MeTLImage.empty
  override def getResource(identifier:String):Array[Byte] = resourceProvider.getResource(identifier)
  override def insertResource(data:Array[Byte]):String = resourceProvider.insertResource(data)
  override def upsertResource(identifier:String,data:Array[Byte]):String = resourceProvider.upsertResource(identifier,data)
}
