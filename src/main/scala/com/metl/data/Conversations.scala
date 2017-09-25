package com.metl.data

import com.metl.utils._

import net.liftweb._
import http._
import common._
import util._
import Helpers._
import collection._

abstract class ConversationRetriever(config:ServerConfiguration,onConversationDetailsUpdated:(Conversation) => Unit) {
	val configName = config.name
	lazy val isReady:Boolean = true
	def getAllConversations:List[Conversation]
	def getAllSlides:List[Slide]
	def searchForConversation(query:String):List[Tuple2[Conversation,SearchExplanation]]
	def searchForSlide(query:String):List[Tuple2[Slide,SearchExplanation]]
  def queryAppliesToConversation(query:String,conversation:Conversation):Boolean
  def queryAppliesToSlide(query:String,slide:Slide):Boolean
	def detailsOf(jid:String):Conversation 
  def detailsOfSlide(jid:String):Slide
  def getConversationsForSlideId(jid:String):List[String]
	def createConversation(title:String,author:String):Conversation
  def createSlide(author:String,slideType:String = "SLIDE"):Slide
	def deleteConversation(jid:String):Conversation
	def renameConversation(jid:String,newTitle:String):Conversation
	def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation
	def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation
  def updateConversation(jid:String,conversation:Conversation):Conversation
}

object EmptyConversations extends ConversationRetriever(EmptyBackendAdaptor,(c) => {}){
	override def getAllConversations = List.empty[Conversation]
	override def getAllSlides = List.empty[Slide]
	override def searchForConversation(query:String) = List.empty[Tuple2[Conversation,SearchExplanation]]
	override def searchForSlide(query:String) = List.empty[Tuple2[Slide,SearchExplanation]]
  override def queryAppliesToConversation(query:String,conversation:Conversation) = false
  override def queryAppliesToSlide(query:String,slide:Slide) = false
	override def detailsOf(jid:String) = Conversation.empty
  override def detailsOfSlide(jid:String):Slide = Slide.empty
  override def getConversationsForSlideId(jid:String):List[String] = Nil
	override def createConversation(title:String,author:String):Conversation = Conversation.empty
  override def createSlide(author:String,slideType:String = "SLIDE"):Slide = Slide.empty
	override def deleteConversation(jid:String):Conversation = Conversation.empty	
	override def renameConversation(jid:String,newTitle:String):Conversation = Conversation.empty
	override def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation = Conversation.empty
	override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = Conversation.empty
  override def updateConversation(jid:String,conversation:Conversation):Conversation = Conversation.empty
}
