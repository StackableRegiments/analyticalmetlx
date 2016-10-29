package com.metl.data

import com.metl.utils._
import scala.xml._

object ServerConfiguration{
  val empty = EmptyBackendAdaptor
  protected var serverConfigs:List[ServerConfiguration] = List(EmptyBackendAdaptor)
  protected var defaultConfigFunc = () => serverConfigs(0)
  protected var serverConfMutator:ServerConfiguration=>ServerConfiguration = (sc) => sc
  def setServerConfMutator(scm:ServerConfiguration=>ServerConfiguration) = serverConfMutator = scm
  def getServerConfMutator:ServerConfiguration=>ServerConfiguration = serverConfMutator
  def setServerConfigurations(sc:List[ServerConfiguration]) = serverConfigs = sc
  def getServerConfigurations = serverConfigs
  def setDefaultServerConfiguration(f:() => ServerConfiguration) = defaultConfigFunc = f
  def addServerConfiguration(sc:ServerConfiguration) = serverConfigs = serverConfigs ::: List(serverConfMutator(sc))
  def configForName(name:String) = serverConfigs.find(c => c.name == name).getOrElse(default)
  def configForHost(host:String) = serverConfigs.find(c => c.host == host).getOrElse(default)
  def default = {
    defaultConfigFunc()
  }
  val parser = new ServerConfigurationParser
  def addServerConfigurator(sc:ServerConfigurator) = parser.addServerConfigurator(sc)
  List(
    EmptyBackendAdaptorConfigurator,
    FrontendSerializationAdaptorConfigurator
  ).foreach(addServerConfigurator _)

  def loadServerConfigsFromFile(path:String,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = {
    val xml = XML.load(path)
    (xml \\ "server").foreach(sc => interpret(sc,onConversationDetailsUpdated,messageBusCredentailsFunc,conversationListenerCredentialsFunc,httpCredentialsFunc))
    (xml \\ "defaultServerConfiguration").text match {
      case s:String if (s.length > 0) => setDefaultServerConfiguration(() => configForName(s))
      case _ => {}
    }
  }
  protected def interpret(n:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = parser.interpret(n,onConversationDetailsUpdated,messageBusCredentailsFunc,conversationListenerCredentialsFunc,httpCredentialsFunc).map(s => addServerConfiguration(s))
}

class ServerConfigurationParser {
  protected var serverConfigurators:List[ServerConfigurator] = Nil
  def addServerConfigurator(sc:ServerConfigurator) = serverConfigurators = serverConfigurators ::: List(sc)
  def interpret(n:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]):List[ServerConfiguration] = serverConfigurators.filter(sc => sc.matchFunction(n)).flatMap(sc => sc.interpret(n,onConversationDetailsUpdated,messageBusCredentailsFunc,conversationListenerCredentialsFunc,httpCredentialsFunc))
}

class ServerConfigurator{
  def matchFunction(e:Node) = false
  def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]):Option[ServerConfiguration] = None
}

abstract class ServerConfiguration(incomingName:String,incomingHost:String,onConversationDetailsUpdatedFunc:Conversation=>Unit) {
  val commonLocation = "commonBucket"
  val name = incomingName
  val host = incomingHost
  val onConversationDetailsUpdated:Conversation=>Unit = onConversationDetailsUpdatedFunc
  def getMessageBus(d:MessageBusDefinition):MessageBus
  def getHistory(jid:String):History
  def getConversationForSlide(slideJid:String):String
  def searchForConversation(query:String):List[Conversation]
  def detailsOfConversation(jid:String):Conversation
  def createConversation(title:String,author:String):Conversation
  def deleteConversation(jid:String):Conversation
  def renameConversation(jid:String,newTitle:String):Conversation
  def changePermissions(jid:String,newPermissions:Permissions):Conversation
  def updateSubjectOfConversation(jid:String,newSubject:String):Conversation
  def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation
  def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation
  def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation
  def updateConversation(jid:String,newConversation:Conversation):Conversation
  def getImage(jid:String,identity:String):MeTLImage
  def getResource(jid:String,identifier:String):Array[Byte]
  def postResource(jid:String,userProposedId:String,data:Array[Byte]):String
  def insertResource(jid:String,data:Array[Byte]):String
  def upsertResource(jid:String,identifier:String,data:Array[Byte]):String 
  def getImage(identity:String):MeTLImage = getImage(commonLocation,identity)
  def getResource(identifier:String):Array[Byte] = getResource(commonLocation,identifier)
  def insertResource(data:Array[Byte]):String = insertResource(commonLocation,data)
  def upsertResource(identifier:String,data:Array[Byte]):String = upsertResource(commonLocation,identifier,data)

  //shutdown is a function to be called when the serverConfiguration is to be disposed
  def shutdown:Unit = {}
  def isReady:Boolean = true
}

object EmptyBackendAdaptor extends ServerConfiguration("empty","empty",(c)=>{}){
  val serializer = new PassthroughSerializer
  override def getMessageBus(d:MessageBusDefinition) = EmptyMessageBus
  override def getHistory(jid:String) = History.empty
  override def getConversationForSlide(slideJid:String):String = ""
  override def searchForConversation(query:String) = List.empty[Conversation]
  override def detailsOfConversation(jid:String) = Conversation.empty
  override def createConversation(title:String,author:String) = Conversation.empty
  override def deleteConversation(jid:String):Conversation = Conversation.empty
  override def renameConversation(jid:String,newTitle:String):Conversation = Conversation.empty
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = Conversation.empty
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = Conversation.empty
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = Conversation.empty
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation = Conversation.empty
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = Conversation.empty
  override def updateConversation(jid:String,newConversation:Conversation):Conversation = Conversation.empty
  override def getImage(jid:String,identity:String) = MeTLImage.empty
  override def getResource(jid:String,identifier:String):Array[Byte] = Array.empty[Byte]
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = ""
  override def insertResource(jid:String,data:Array[Byte]):String = ""
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = ""
  override def getImage(identity:String) = MeTLImage.empty
  override def getResource(identifier:String):Array[Byte] = Array.empty[Byte]
  override def insertResource(data:Array[Byte]):String = ""
  override def upsertResource(identifier:String,data:Array[Byte]):String = ""
}

object EmptyBackendAdaptorConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").text == "empty"
  override def interpret(e:Node,o:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = Some(EmptyBackendAdaptor)
}

object FrontendSerializationAdaptor extends ServerConfiguration("frontend","frontend",(c)=>{}){
  val serializer = new GenericXmlSerializer("frontend")
  override def getMessageBus(d:MessageBusDefinition) = EmptyMessageBus
  override def getHistory(jid:String) = History.empty
  override def getConversationForSlide(slideJid:String):String = ""
  override def searchForConversation(query:String) = List.empty[Conversation]
  override def detailsOfConversation(jid:String) = Conversation.empty
  override def createConversation(title:String,author:String) = Conversation.empty
  override def deleteConversation(jid:String):Conversation = Conversation.empty
  override def renameConversation(jid:String,newTitle:String):Conversation = Conversation.empty
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = Conversation.empty
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = Conversation.empty
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = Conversation.empty
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation = Conversation.empty
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = Conversation.empty
  override def updateConversation(jid:String,newConversation:Conversation):Conversation = Conversation.empty
  override def getImage(jid:String,identity:String) = MeTLImage.empty
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = ""
  override def getResource(jid:String,identifier:String):Array[Byte] = Array.empty[Byte]
  override def insertResource(jid:String,data:Array[Byte]):String = ""
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = ""
  override def getImage(identity:String) = MeTLImage.empty
  override def getResource(identifier:String):Array[Byte] = Array.empty[Byte]
  override def insertResource(data:Array[Byte]):String = ""
  override def upsertResource(identifier:String,data:Array[Byte]):String = ""
}

object FrontendSerializationAdaptorConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").text == "frontend"
  override def interpret(e:Node,o:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = Some(FrontendSerializationAdaptor)
}

class PassThroughAdaptor(sc:ServerConfiguration) extends ServerConfiguration(sc.name,sc.host,sc.onConversationDetailsUpdated){
  override def getMessageBus(d:MessageBusDefinition) = sc.getMessageBus(d)
  override def getHistory(jid:String) = sc.getHistory(jid)
  override def getConversationForSlide(slideJid:String):String = sc.getConversationForSlide(slideJid)
  override def searchForConversation(query:String) = sc.searchForConversation(query)
  override def detailsOfConversation(jid:String) = sc.detailsOfConversation(jid)
  override def createConversation(title:String,author:String) = sc.createConversation(title,author)
  override def deleteConversation(jid:String):Conversation = sc.deleteConversation(jid)
  override def renameConversation(jid:String,newTitle:String):Conversation = sc.renameConversation(jid,newTitle)
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = sc.changePermissions(jid,newPermissions)
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = sc.updateSubjectOfConversation(jid,newSubject)
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = sc.addSlideAtIndexOfConversation(jid,index)
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation = sc.addGroupSlideAtIndexOfConversation(jid,index,grouping)
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = sc.reorderSlidesOfConversation(jid,newSlides)
  override def updateConversation(jid:String,newConversation:Conversation):Conversation = sc.updateConversation(jid,newConversation)
  override def getImage(jid:String,identity:String) = sc.getImage(jid,identity)
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = sc.postResource(jid,userProposedId,data)
  override def getResource(jid:String,identifier:String):Array[Byte] = sc.getResource(jid,identifier)
  override def insertResource(jid:String,data:Array[Byte]):String = sc.insertResource(jid,data)
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = sc.upsertResource(jid,identifier,data)
  override def getImage(identity:String) = sc.getImage(identity)
  override def getResource(identifier:String):Array[Byte] = sc.getResource(identifier)
  override def insertResource(data:Array[Byte]):String = sc.insertResource(data)
  override def upsertResource(identifier:String,data:Array[Byte]):String = sc.upsertResource(identifier,data)
  override def shutdown:Unit = sc.shutdown
  override def isReady:Boolean = sc.isReady
}
