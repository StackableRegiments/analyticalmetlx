package com.metl.persisted

import com.metl.data._
import com.metl.utils._

class PersistedConversations(config:ServerConfiguration,dbInterface:PersistenceInterface,onConversationDetailsUpdated:Conversation=>Unit) extends ConversationRetriever(config,onConversationDetailsUpdated) {
  override def getAllConversations = dbInterface.getAllConversations
  override def getAllSlides = dbInterface.getAllSlides
  override def searchForConversation(query:String) = dbInterface.searchForConversation(query)
  override def searchForSlide(query:String) = dbInterface.searchForSlide(query)
  override def queryAppliesToConversation(query:String,conversation:Conversation) = dbInterface.queryAppliesToConversation(query,conversation)
  override def queryAppliesToSlide(query:String,slide:Slide) = dbInterface.queryAppliesToSlide(query,slide)
  override def getConversationsForSlideId(jid:String):List[String] = dbInterface.getConversationsForSlideId(jid)
  override def detailsOf(jid:String) = dbInterface.detailsOfConversation(jid)
  override def detailsOfSlide(jid:String) = dbInterface.detailsOfSlide(jid)
  override def createConversation(title:String,author:String):Conversation = dbInterface.createConversation(title,author)
  override def createSlide(author:String,slideType:String = "SLIDE"):Slide = dbInterface.createSlide(author,slideType)
  override def deleteConversation(jid:String):Conversation = dbInterface.deleteConversation(jid)
  override def renameConversation(jid:String,newTitle:String):Conversation = dbInterface.renameConversation(jid,newTitle)
  override def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation = dbInterface.addSlideAtIndexOfConversation(jid,index,slideType)
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = dbInterface.reorderSlidesOfConversation(jid,newSlides)
  override def updateConversation(jid:String,conversation:Conversation):Conversation = dbInterface.updateConversation(jid,conversation)
}
