package com.metl.metl2011

import com.metl.data._
import com.metl.utils._
import java.io.ByteArrayInputStream

import net.liftweb.util._
import org.apache.commons.io.IOUtils
import net.liftweb.common.Logger

class MeTL2011CachedConversations(config:ServerConfiguration, http:HttpProvider, messageBusProvider:MessageBusProvider, onConversationDetailsUpdated:(Conversation) => Unit) extends MeTL2011Conversations(config,"",http,messageBusProvider,onConversationDetailsUpdated) with Logger {
  override val mbDef = new MessageBusDefinition("global","conversationUpdating",
    (m:MeTLStanza)=>{
      trace("Message received from the conversationUpdater's message bus: %s".format(m))
      receiveConversationDetailsUpdated(m)
    },
    ()=>{
      debug("MeTL2011CachedConversations connection lost")
    },
    ()=>{
      debug("MeTL2011CachedConversations connection regained")
      precacheConversations
    }
  )
  val conversations = scala.collection.mutable.HashMap.empty[Int,Conversation]

  private def precacheConversations = Stopwatch.time("MeTL2011CachedConversations.precacheConversations", {
    try {
      trace("recaching conversations")
      val directoryUrl = "%s/Structure/".format(rootAddress)
      http.getClient.get(directoryUrl)
      val stream = new ByteArrayInputStream(http.getClient.getAsBytes("%s/all.zip".format(directoryUrl)))
      Unzipper.unzip(stream).map(x => {
        serializer.toConversation(x)
      }).filterNot(_ == Conversation.empty).foreach(c => conversations.put(c.jid,c))
      trace("recaching conversations completed")
    } catch {
      case e:Throwable => {
        error("recaching conversations failed",e)
      }
    }
  })
  override lazy val isReady:Boolean = {
    mb
    precacheConversations
    true
  }

  override def getAll:List[Conversation] = Stopwatch.time("CachedConversations.getAll",{
    conversations.map(_._2).toList
  })
  override def search(query:String):List[Conversation] = Stopwatch.time("CachedConversations.search",{
    if(query == null || query.length == 0) List.empty[Conversation]
    else{
      val lq = query.toLowerCase
      conversations.filter{
        case (jid,c)=>{
          c.title.toLowerCase.contains(lq) || c.author.toLowerCase == lq || c.jid.toString == lq
        }
      }.map(_._2).toList
    }
  })
  override def searchByCourse(courseId:String):List[Conversation] = Stopwatch.time("CachedConversations.searchByCourse",List.empty[Conversation])
  override def detailsOf(conversationJid:Int) =
  {
    try {
      conversations(conversationJid)
    }
    catch {
      case e:NoSuchElementException => super.detailsOf(conversationJid)
      case e:Throwable => Conversation.empty
    }
  }
  override def pushConversationToServer(conversation:Conversation):Conversation = {
    //debug("pushConversationToServer (cache): %s".format(conversation))
    val pushedConv = super.pushConversationToServer(conversation)
    conversations.put(pushedConv.jid,pushedConv)
    //debug("pushConversationToServer (cache): %s".format(pushedConv))
    pushedConv
  }
  /*
   // this is the correct way of doing it, but too slow for the large bulk of conversations in the prod dataset
   override def conversationFor(slide:Int):Int = Stopwatch.time("CachedConversations.conversationFor", {
   conversations.find{
   case (jid,c) => {
   c.slides.exists(s => s.id == slide)
   }
   }.map(ce => ce._1).getOrElse(-1)
   });
   */
  override def receiveConversationDetailsUpdated(m:MeTLStanza):Option[Conversation] = {
    //debug("receiveConversationDetailsUpdated (cache): %s".format(m))
    m match {
      case c:MeTLCommand if c.command == "/UPDATE_CONVERSATION_DETAILS" && c.commandParameters.length == 1 => {
        try{
          val conversation = super.detailsOf(c.commandParameters(0).toInt)
          //trace("updating cache: %s".format(conversation))
          conversations.put(conversation.jid,conversation)
          onConversationDetailsUpdated(conversation)
          Some(conversation)
        }
        catch {
          case e:Throwable =>{
            error("receiveConversationDetailsUpdated threw exception",e)
            None
          }
        }
      }
      case _ => {
        None
      }
    }
  }
  override def notifyXmpp(remote:Conversation) = {
    conversations.put(remote.jid,remote)
    super.notifyXmpp(remote)
  }
}

class MeTL2011Conversations(config:ServerConfiguration, val searchBaseUrl:String, http:HttpProvider,messageBusProvider:MessageBusProvider,onConversationDetailsUpdated:(Conversation) => Unit) extends ConversationRetriever(config,onConversationDetailsUpdated) with Logger {
  lazy val utils = new MeTL2011Utils(config)
  lazy val serializer = new MeTL2011XmlSerializer(config)
  lazy val rootAddress = "https://%s:1188".format(config.host)
  protected val mbDef = new MessageBusDefinition("global","conversationUpdating",
    (m:MeTLStanza)=>{
      trace("Message received from the conversationUpdater's message bus: %s".format(m))
      receiveConversationDetailsUpdated(m)
    },
    ()=>{
      debug("MeTL2011Conversations connection lost")
    },
    ()=>{
      debug("MeTL2011Conversations connection regained")
    }
  )
  lazy val mb = messageBusProvider.getMessageBus(mbDef)

  override lazy val isReady:Boolean = {
    mb
    true
  }

  def receiveConversationDetailsUpdated(m:MeTLStanza):Option[Conversation] = {
    m match {
      case c:MeTLCommand if c.command == "/UPDATE_CONVERSATION_DETAILS" && c.commandParameters.length == 1 => {
        try{
          val conversation = internalDetailsOf(c.commandParameters(0).toInt)
          onConversationDetailsUpdated(conversation)
          Some(conversation)
        }
        catch {
          case e:Throwable =>{
            error("receiveConversationDetailsUpdated threw exception",e)
            None
          }
        }
      }
      case _ => {
        None
      }
    }
  }

  override def getAll:List[Conversation] = Stopwatch.time("Conversations.getAll",{
    (scala.xml.XML.loadString(http.getClient.get(searchBaseUrl + "search?query=")) \\ "conversation").map(c => serializer.toConversation(c)).toList
  })
  override def search(query:String):List[Conversation] = Stopwatch.time("Conversations.search",{
    (scala.xml.XML.loadString(http.getClient.get(searchBaseUrl + "search?query=" + Helpers.urlEncode(query))) \\ "conversation").map(c => serializer.toConversation(c)).toList
  })
  override def searchByCourse(courseId:String):List[Conversation] = Stopwatch.time("Conversations.search",List.empty[Conversation])
  override def conversationFor(slide:Int):Int = Stopwatch.time("Conversations.conversationFor",{
    config.name match {
      case "reifier" => ((slide / 1000) * 1000) + 400
      case "deified" => ((slide / 1000) * 1000) + 400
      case "standalone" => (((slide - 400) / 1000) * 1000) + 400
      case _ => (slide /1000) * 1000
    }
  })
  override def detailsOf(jid:Int):Conversation = Stopwatch.time("Conversations.detailsOf",internalDetailsOf(jid))
  private def internalDetailsOf(jid:Int):Conversation = Stopwatch.time("Conversations.internalDetailsOf",{
    try{
      (scala.xml.XML.loadString(http.getClient.get("https://"+config.host+":1188/Structure/"+utils.stem(jid.toString)+"/"+jid.toString+"/details.xml")) \\ "conversation").headOption.map(c => serializer.toConversation(c)).getOrElse(Conversation.empty)
    }
    catch{
      case e:Exception => {
        Conversation.empty
      }
    }
  })
  override def createConversation(title:String,author:String):Conversation = {
    val jid = getNewJid
    val now = new java.util.Date()
    val local = Conversation(config,author,now.getTime,List(Slide(config,author,jid + 1,0)),"unrestricted","",jid,title,now.getTime,Permissions.default(config))
    pushConversationToServer(local)
  }
  override def deleteConversation(jid:String):Conversation = {
    val conv = detailsOf(jid.toInt)
    val now = new java.util.Date()
    val local = Conversation(config,conv.author,now.getTime,conv.slides,"deleted",conv.tag,conv.jid,conv.title,conv.created,conv.permissions)
    pushConversationToServer(local)
  }
  override def renameConversation(jid:String,newTitle:String):Conversation = {
    val conv = detailsOf(jid.toInt)
    val now = new java.util.Date()
    val local = Conversation(config,conv.author,now.getTime,conv.slides,conv.subject,conv.tag,conv.jid,newTitle,conv.created,conv.permissions)
    pushConversationToServer(local)
  }
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = {
    val conv = detailsOf(jid.toInt)
    val now = new java.util.Date()
    val local = Conversation(config,conv.author,now.getTime,conv.slides,conv.subject,conv.tag,conv.jid,conv.title,conv.created,newPermissions)
    pushConversationToServer(local)
  }
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = {
    val conv = detailsOf(jid.toInt)
    val now = new java.util.Date()
    val local = Conversation(config,conv.author,now.getTime,conv.slides,newSubject,conv.tag,conv.jid,conv.title,conv.created,conv.permissions)
    pushConversationToServer(local)
  }
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = {
    val conv = detailsOf(jid.toInt)
    val slides = conv.slides
    val currentMaxJid = slides.map(s => s.id) match {
      case l:List[Int] if (l.length > 0) => l.max
      case _ => jid.toInt
    }
    val newSlides = slides.map(s => {
      val newIndex = s.index match {
        case i:Int if (i < index) => i
        case i:Int => i + 1
      }
      Slide(config,s.author,s.id,newIndex,s.defaultHeight,s.defaultWidth,s.exposed,s.slideType)
    })
    val newSlide = Slide(config,conv.author,currentMaxJid + 1, index)
    val now = new java.util.Date()
    val local = Conversation(config,conv.author,now.getTime,newSlide :: newSlides,conv.subject,conv.tag,conv.jid,conv.title,conv.created,conv.permissions)
    pushConversationToServer(local)
  }
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:com.metl.data.GroupSet):Conversation = {
    val conv = detailsOf(jid.toInt)
    val slides = conv.slides
    val currentMaxJid = slides.map(s => s.id) match {
      case l:List[Int] if (l.length > 0) => l.max
      case _ => jid.toInt
    }
    val newSlides = slides.map(s => {
      val newIndex = s.index match {
        case i:Int if (i < index) => i
        case i:Int => i + 1
      }
      Slide(config,s.author,s.id,newIndex,s.defaultHeight,s.defaultWidth,s.exposed,s.slideType,List(grouping))
    })
    val newSlide = Slide(config,conv.author,currentMaxJid + 1, index)
    val now = new java.util.Date()
    val local = Conversation(config,conv.author,now.getTime,newSlide :: newSlides,conv.subject,conv.tag,conv.jid,conv.title,conv.created,conv.permissions)
    pushConversationToServer(local)
  }
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = {
    val conv = detailsOf(jid.toInt)
    val now = new java.util.Date()
    val local = Conversation(config,conv.author,now.getTime,newSlides,conv.subject,conv.tag,conv.jid,conv.title,conv.created,conv.permissions)
    pushConversationToServer(local)
  }
  override def updateConversation(jid:String,conversation:Conversation):Conversation = {
    if (jid == conversation.jid.toString){
      pushConversationToServer(conversation.copy(lastAccessed = new java.util.Date().getTime))
    } else {
      conversation
    }
  }
  protected def pushConversationToServer(conversation:Conversation):Conversation = {
    //trace("pushConversationToServer (proposed): %s".format(conversation))
    val jid = conversation.jid
    val bytes = serializer.fromConversation(conversation).toString.getBytes("UTF-8")
    val url = "%s/upload_nested.yaws?overwrite=true&path=%s&filename=details.xml".format(rootAddress,Helpers.urlEncode("Structure/%s/%s".format(utils.stem(jid.toString),jid.toString)))
    http.getClient.postBytes(url,bytes)
    val remote = internalDetailsOf(jid)
    notifyXmpp(remote)
    //trace("pushConversationToServer (confirmed): %s".format(remote))
    remote
  }
  protected def notifyXmpp(newConversation:Conversation) = {
    val stanza = MeTLCommand(config,newConversation.author,new java.util.Date().getTime,"/UPDATE_CONVERSATION_DETAILS",List(newConversation.jid.toString))
    trace("conversationUpdater sent message: %s".format(stanza))
    mb.sendStanzaToRoom(stanza)
  }
  private def getNewJid:Int = http.getClient.get("https://"+config.host+":1188/primarykey.yaws").trim.toInt
}
