package com.metl.metl2011

import com.metl.data._
import com.metl.utils._
import scala.xml._
import net.liftweb.common.Logger

object MeTL2011ServerConfiguration{
  def initialize = List(
    MeTL2011BackendAdaptorConfigurator,
    TransientMeTL2011BackendAdaptorConfigurator
  ).foreach(sc => ServerConfiguration.addServerConfigurator(sc))
}
object MeTL2015ServerConfiguration{
  def initialize = List(
    MeTL2015BackendAdaptorConfigurator,
    TransientMeTL2015BackendAdaptorConfigurator
  ).foreach(sc => ServerConfiguration.addServerConfigurator(sc))
}

class MeTL2011BackendAdaptor(name:String,hostname:String,xmppDomainName:String,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentialFunc:()=>Tuple2[String,String],conversationBusCredentialFunc:()=>Tuple2[String,String],httpCredentialFunc:()=>Tuple2[String,String]) extends ServerConfiguration(name,hostname,onConversationDetailsUpdated) with Logger {
  protected val http:HttpProvider = new DynamicallyAuthedHttpProvider(httpCredentialFunc)
  protected lazy val history = new MeTL2011History(this,http)
  protected lazy val messageBusProvider = new PooledXmppProvider(this,hostname,messageBusCredentialFunc,xmppDomainName)
  protected lazy val conversationsMessageBusProvider = new XmppProvider(this,hostname,conversationBusCredentialFunc,xmppDomainName)
  protected val conversations = new MeTL2011CachedConversations(this,http,conversationsMessageBusProvider,onConversationDetailsUpdated)
  lazy val serializer = new MeTL2011XmlSerializer(this)
  override def isReady = {
    conversations.isReady
  }
  protected val resourceProvider = new MeTL2011Resources(this,http)
  override def getMessageBus(d:MessageBusDefinition) = messageBusProvider.getMessageBus(d)
  override def getHistory(jid:String) = history.getMeTLHistory(jid)
  override def getConversationForSlide(slideJid:String) = conversations.conversationFor(slideJid.toInt).toString
  override def getAllConversations = conversations.getAll
  override def searchForConversation(query:String) = conversations.search(query)
  override def searchForConversationByCourse(courseId:String) = List.empty[Conversation]
  override def detailsOfConversation(jid:String) = conversations.detailsOf(jid.toInt)
  override def createConversation(title:String,author:String) = conversations.createConversation(title,author)
  override def deleteConversation(jid:String):Conversation = conversations.deleteConversation(jid)
  override def renameConversation(jid:String,newTitle:String):Conversation = conversations.renameConversation(jid,newTitle)
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = conversations.changePermissions(jid,newPermissions)
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = conversations.updateSubjectOfConversation(jid,newSubject)
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = conversations.addSlideAtIndexOfConversation(jid,index)
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:com.metl.data.GroupSet):Conversation = conversations.addGroupSlideAtIndexOfConversation(jid,index,grouping)
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = conversations.reorderSlidesOfConversation(jid,newSlides)
  override def updateConversation(jid:String,conversation:Conversation):Conversation = conversations.updateConversation(jid,conversation)
  override def getImage(jid:String,identity:String) = history.getMeTLHistory(jid).getImageByIdentity(identity).getOrElse(MeTLImage.empty)
  override def getResource(url:String) = http.getClient.getAsBytes(url)
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = resourceProvider.postResource(jid,userProposedId,data)

  //these are the new overwrite endpoints, which aren't quite the same as they should be.
  protected val rootAddress = resourceProvider.rootAddress
  override def getResource(jid:String,identifier:String):Array[Byte] = http.getClient.getAsBytes("%s/%s/%s".format(rootAddress,generatePath(jid),generateFilename(identifier)))
  override def insertResource(jid:String,data:Array[Byte]):String = postResource(jid,net.liftweb.util.Helpers.nextFuncName,false,data)
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = postResource(jid,identifier,true,data)

  protected def generatePath(jid:String):String = {
    net.liftweb.util.Helpers.urlEncode("Resource/%s/%s".format(resourceProvider.utils.stem(jid.toString),jid.toString))
  }
  protected def generateFilename(identifier:String):String = {
    net.liftweb.util.Helpers.urlEncode(identifier)
  }
  protected def postResource(jid:String,userGeneratedId:String,overwrite:Boolean,data:Array[Byte]):String = {
		val uri = "%s/upload_nested.yaws?path=%s&overwrite=%s&filename=%s".format(rootAddress,generatePath(jid),overwrite,generateFilename(userGeneratedId))	
		val response = http.getClient.postBytes(uri,data)
		val responseString = new String(response)
		debug("postedResource response: %s".format(responseString))
		((XML.loadString(responseString) \\ "resource").head \ "@url").text
	}
}

object MeTL2011BackendAdaptorConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").headOption.exists(_.text == "MeTL2011")
  override def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = {
    for (
      name <- (e \\ "name").headOption.map(_.text);
      host <- (e \\ "host").headOption.map(_.text);
      xmppDomainName = (e \\ "xmppDomainName").headOption.map(_.text).filter(_.length > 0);
      httpUsername <- (e \\ "httpUsername").headOption.map(_.text);
      httpPassword <- (e \\ "httpPassword").headOption.map(_.text);
      messageBusUsernamePrefix <- (e \\ "messageBusUsernamePrefix").headOption.map(_.text);
      messageBusPassword <- (e \\ "messageBusPassword").headOption.map(_.text);
      conversationListenerUsernamePrefix <- (e \\ "conversationListenerUsernamePrefix").headOption.map(_.text);
      conversationListenerPassword <- (e \\ "conversationListenerPassword").headOption.map(_.text)
    ) yield {
      new MeTL2011BackendAdaptor(name,host,xmppDomainName.getOrElse(host),onConversationDetailsUpdated,() => (messageBusUsernamePrefix,messageBusPassword),() => (conversationListenerUsernamePrefix,conversationListenerPassword),() => (httpUsername,httpPassword))
    }
  }
}

object MeTL2015BackendAdaptorConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").headOption.exists(_.text == "MeTL2015")
  override def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = {
    for (
      name <- (e \\ "name").headOption.map(_.text);
      host <- (e \\ "host").headOption.map(_.text);
      xmppDomainName = (e \\ "xmppDomainName").headOption.map(_.text).filter(_.length > 0);
      xmppPassword = (e \\ "xmppPassword").headOption.map(_.text).filter(_.length > 0);
      httpUsername = (e \\ "httpUsername").headOption.map(_.text).filter(_.length > 0);
      httpPassword = (e \\ "httpPassword").headOption.map(_.text).filter(_.length > 0)
    ) yield {
      new MeTL2011BackendAdaptor(
        name,
        host,
        xmppDomainName.getOrElse(host),
        onConversationDetailsUpdated,
        (for (
          xp <- xmppPassword
        ) yield {
          () => ("metlxUser",xp)
        }).getOrElse(messageBusCredentailsFunc),
        (for (
          xp <- xmppPassword
        ) yield {
          () => ("metlxConversationUser",xp)
        }).getOrElse(conversationListenerCredentialsFunc),
        (for (
          hu <- httpUsername;
          hp <- httpPassword
        ) yield {
          () => (hu,hp)
        }).getOrElse(httpCredentialsFunc)
      )
    }
  }
}


class TransientMeTL2011BackendAdaptor(name:String,hostname:String,onConversationDetailsUpdated:Conversation=>Unit,httpCredentialFunc:()=>Tuple2[String,String]) extends ServerConfiguration(name,hostname,onConversationDetailsUpdated) with Logger {
  protected val http = new DynamicallyAuthedHttpProvider(httpCredentialFunc)
  protected val history = new MeTL2011History(this,http)
  protected val messageBusProvider = new LoopbackMessageBusProvider
  protected val conversations = new MeTL2011CachedConversations(this,http,messageBusProvider,onConversationDetailsUpdated)
  val serializer = new MeTL2011XmlSerializer(this)
  protected val resourceProvider = new MeTL2011Resources(this,http)
  override def getMessageBus(d:MessageBusDefinition) = messageBusProvider.getMessageBus(d)
  override def getHistory(jid:String) = history.getMeTLHistory(jid)
  override def getConversationForSlide(slideJid:String) = conversations.conversationFor(slideJid.toInt).toString
  override def getAllConversations = conversations.getAll
  override def searchForConversation(query:String) = conversations.search(query)
  override def searchForConversationByCourse(courseId:String) = conversations.searchByCourse(courseId)
  override def detailsOfConversation(jid:String) = conversations.detailsOf(jid.toInt)
  override def createConversation(title:String,author:String) = Conversation.empty
  override def deleteConversation(jid:String):Conversation = Conversation.empty
  override def renameConversation(jid:String,newTitle:String):Conversation = Conversation.empty
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = Conversation.empty
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = Conversation.empty
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = Conversation.empty
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:com.metl.data.GroupSet):Conversation = Conversation.empty
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = Conversation.empty
  override def updateConversation(jid:String,conversation:Conversation):Conversation = Conversation.empty
  override def getImage(jid:String,identity:String) = history.getMeTLHistory(jid).getImageByIdentity(identity).getOrElse(MeTLImage.empty)
  override def getResource(url:String) = http.getClient.getAsBytes(url)
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = "not yet implemented"

  //these are the new overwrite endpoints, which aren't quite the same as they should be.
  protected val rootAddress = resourceProvider.rootAddress
  override def getResource(jid:String,identifier:String):Array[Byte] = http.getClient.getAsBytes("%s/%s/%s".format(rootAddress,generatePath(jid),generateFilename(identifier)))
  override def insertResource(jid:String,data:Array[Byte]):String = postResource(jid,net.liftweb.util.Helpers.nextFuncName,false,data)
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = postResource(jid,identifier,true,data)

  protected def generatePath(jid:String):String = {
    net.liftweb.util.Helpers.urlEncode("Resource/%s/%s".format(resourceProvider.utils.stem(jid.toString),jid.toString))
  }
  protected def generateFilename(identifier:String):String = {
    net.liftweb.util.Helpers.urlEncode(identifier)
  }
  protected def postResource(jid:String,userGeneratedId:String,overwrite:Boolean,data:Array[Byte]):String = {
		val uri = "%s/upload_nested.yaws?path=%s&overwrite=%s&filename=%s".format(rootAddress,generatePath(jid),overwrite,generateFilename(userGeneratedId))	
		val response = http.getClient.postBytes(uri,data)
		val responseString = new String(response)
		debug("postedResource response: %s".format(responseString))
		((XML.loadString(responseString) \\ "resource").head \ "@url").text
	}

}

object TransientMeTL2011BackendAdaptorConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").headOption.exists(_.text == "TransientMeTL2011")
  override def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = {
    for (
      name <- (e \\ "name").headOption.map(_.text);
      host <- (e \\ "host").headOption.map(_.text);
      httpUsername <- (e \\ "httpUsername").headOption.map(_.text);
      httpPassword <- (e \\ "httpPassword").headOption.map(_.text);
      messageBusUsernamePrefix <- (e \\ "messageBusUsernamePrefix").headOption.map(_.text);
      messageBusPassword <- (e \\ "messageBusPassword").headOption.map(_.text);
      conversationListenerUsernamePrefix <- (e \\ "conversationListenerUsernamePrefix").headOption.map(_.text);
      conversationListenerPassword <- (e \\ "conversationListenerPassword").headOption.map(_.text)
    ) yield {
      new TransientMeTL2011BackendAdaptor(name,host,onConversationDetailsUpdated,() => (httpUsername,httpPassword))
    }
  }
}

object TransientMeTL2015BackendAdaptorConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").headOption.exists(_.text == "TransientMeTL2015")
  override def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = {
    for (
      name <- (e \\ "name").headOption.map(_.text);
      host <- (e \\ "host").headOption.map(_.text)
    ) yield {
      new TransientMeTL2011BackendAdaptor(name,host,onConversationDetailsUpdated,httpCredentialsFunc)
    }
  }
}
