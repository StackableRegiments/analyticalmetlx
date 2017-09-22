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
    EmptyBackendAdaptorConfigurator
  ).foreach(addServerConfigurator _)

  def loadServerConfigsFromFile(path:String,onConversationDetailsUpdated:Conversation=>Unit) = {
    val xml = XML.load(path)
    (xml \\ "server").foreach(sc => interpret(sc,onConversationDetailsUpdated))
    (xml \\ "defaultServerConfiguration").text match {
      case s:String if (s.length > 0) => setDefaultServerConfiguration(() => configForName(s))
      case _ => {}
    }
  }
  protected def interpret(n:Node,onConversationDetailsUpdated:Conversation=>Unit) = parser.interpret(n,onConversationDetailsUpdated).map(s => addServerConfiguration(s))
}

class ServerConfigurationParser {
  protected var serverConfigurators:List[ServerConfigurator] = Nil
  def addServerConfigurator(sc:ServerConfigurator) = serverConfigurators = serverConfigurators ::: List(sc)
  def interpret(n:Node,onConversationDetailsUpdated:Conversation=>Unit):List[ServerConfiguration] = serverConfigurators.filter(sc => sc.matchFunction(n)).flatMap(sc => sc.interpret(n,onConversationDetailsUpdated))
}

class ServerConfigurator{
  def matchFunction(e:Node) = false
  def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit):Option[ServerConfiguration] = None
}

abstract class ServerConfiguration(incomingName:String,incomingHost:String,onConversationDetailsUpdatedFunc:Conversation=>Unit) {
  val commonLocation = "commonBucket"
  val name = incomingName
  val host = incomingHost
  val onConversationDetailsUpdated:Conversation=>Unit = onConversationDetailsUpdatedFunc
  val messageBusProvider:MessageBusProvider
  def getMessageBus(d:MessageBusDefinition):MessageBus = messageBusProvider.getMessageBus(d)
  def getHistory(jid:String):History
  def getAllConversations:List[Conversation]
  def getAllSlides:List[Slide]
  def getConversationsForSlideId(jid:String):List[String]
  def searchForConversation(query:String):List[Tuple2[Conversation,SearchExplanation]]
  def searchForConversationByCourse(courseId:String):List[Conversation]
  def searchForSlide(query:String):List[Tuple2[Slide,SearchExplanation]]
  def queryAppliesToConversation(query:String,conversation:Conversation):Boolean
  def queryAppliesToSlide(query:String,slide:Slide):Boolean
  def detailsOfConversation(jid:String):Conversation
  def detailsOfSlide(jid:String):Slide
  def createConversation(title:String,author:String):Conversation
  def createSlide(author:String,slideType:String = "SLIDE",grouping:List[GroupSet] = Nil):Slide
  def deleteConversation(jid:String):Conversation
  def renameConversation(jid:String,newTitle:String):Conversation
  def changePermissions(jid:String,newPermissions:Permissions):Conversation
  def updateSubjectOfConversation(jid:String,newSubject:String):Conversation
  def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation
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
  //profile information
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile
  def getAllProfiles:List[Profile]
  def searchForProfile(query:String):List[Tuple2[Profile,SearchExplanation]] 
  def queryAppliesToProfile(query:String,profile:Profile):Boolean 
  def getProfiles(ids:String *):List[Profile]
  def updateProfile(id:String,profile:Profile):Profile
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = (Nil,"")  
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = {}

  //session handling
  def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord]
  def getSessionsForProfile(profileId:String):List[SessionRecord]
  def updateSession(sessionRecord:SessionRecord):SessionRecord
  def getCurrentSessions:List[SessionRecord]

  def getThemesByAuthor(author:String):List[Theme]
  def getSlidesByThemeKeyword(theme:String):List[String]
  def getConversationsByTheme(theme:String):List[String]
  def getAttendancesByAuthor(author:String):List[Attendance]
  def getConversationsByAuthor(author:String):List[Conversation]
  def getAuthorsByTheme(theme:String):List[String]

  //shutdown is a function to be called when the serverConfiguration is to be disposed
  def shutdown:Unit = {}
  def isReady:Boolean = true

  def getMockHistory:History = getHistory("mock|data") //deliberately providing an endpoint which can only return some mock data.  Individual backend adaptors can choose where to source this from, or how to store it.
}

trait ResourceLoader {
  import java.nio.charset.StandardCharsets
  import org.apache.commons.io._
  def readResourceAsString(resourcePath: String): String = {
    val url = this.getClass().getClassLoader().getResource(resourcePath)
    val file = new java.io.File(url.getFile())
    FileUtils.readFileToString(file, StandardCharsets.UTF_8.toString)
  }
  def readResourceAsBytes(resourcePath: String): Array[Byte] = {
    val url = this.getClass().getClassLoader().getResource(resourcePath)
    val file = new java.io.File(url.getFile())
    FileUtils.readFileToByteArray(file)
  }
}

object MockData extends ResourceLoader {
  import net.liftweb.common._
  def mockHistoryValue(c:ServerConfiguration) = {
    val a = "mock|author"
    val h = new History("mock|data")
    val t = "presentationSpace"
    val p = Privacy.PUBLIC
    val s = "mock|data"
    val black = Color(255,0,0,0)
    val red = Color(255,255,0,0)
    val green = Color(255,0,255,0)
    val blue = Color(255,0,0,255)
    val j = "LEFT"
    val f = "Arial"
    var id = 4
    val textId = "mockText_%s".format(id)
    id += 1
    val inkId = "mockInk_%s".format(id)
    id += 1
    val imageId = "mockImage_%s".format(id)
    id += 1
    val mdId = "mockMoveDelta_%s".format(id)
    def imageBytes(resourcePath:String):Box[Array[Byte]] = try {
      val bytes = readResourceAsBytes(resourcePath)
      Full(bytes)
    } catch {
      case e:Exception => Failure("could not get resource jpg",Full(e),Empty)
    }
    List(
      MeTLMultiWordText(a,1L,100.0,200.0,200.0,100.0,100.0,"mockTag",textId,t,p,s,List(
        MeTLTextWord("test data ",false,false,false,j,black,f,12.0),
        MeTLTextWord("must",true,false,false,j,red,f,10.0),
        MeTLTextWord(" be ",false,false,false,j,green,f,10.0),
        MeTLTextWord("simple",false,false,true,j,blue,f,10.0),
        MeTLTextWord(" and ",false,false,false,j,black,f,8.0),
        MeTLTextWord("small",false,true,false,j,red,f,10.0)
      )),
      MeTLInk(a,2L,1.0,1.0,List(
        Point(100,250,128),
        Point(300,250,255),
        Point(300,300,128),
        Point(100,300,255),
        Point(100,250,128)
      ),red,8.0,false,t,p,s,inkId + "_not_moved"),
      MeTLInk(a,3L,1.0,1.0,List(
        Point(100,250,128),
        Point(300,250,255),
        Point(300,300,128),
        Point(100,300,255),
        Point(100,250,128)
      ),black,8.0,false,t,p,s,inkId),
      MeTLMultiWordText(a,4L,100.0,200.0,200.0,100.0,350.0,"mockTag",textId + "_not_moved",t,p,s,List(
        MeTLTextWord("test data ",false,false,false,j,black,f,12.0),
        MeTLTextWord("must",true,false,false,j,red,f,10.0),
        MeTLTextWord(" be ",false,false,false,j,green,f,10.0),
        MeTLTextWord("simple",false,false,true,j,blue,f,10.0),
        MeTLTextWord(" and ",false,false,false,j,black,f,8.0),
        MeTLTextWord("small",false,true,false,j,red,f,10.0)
      )),
      MeTLImage(a,5L,"",Full("mockSource_1"),imageBytes("engineering.jpg"),Empty,200,150,350,100,t,p,s,imageId),
      MeTLImage(a,6L,"",Full("mockSource_1"),imageBytes("engineering.jpg"),Empty,200,150,350,100,t,p,s,imageId + "_not_moved"),
      MeTLMoveDelta(a,7L,t,p,s,mdId,100.0,100.0,
        List(inkId),
        Nil,
        List(textId),
        List(imageId),
        Nil,
        -50.0,
        -50.0,
        1.0,
        1.0,
        Privacy.NOT_SET,
        false
      )
    ).foreach(s => h.addStanza(s))
    h
  }

}

object EmptyBackendAdaptor extends ServerConfiguration("empty","empty",(c)=>{}){
  val serializer = new PassthroughSerializer
  override val messageBusProvider:MessageBusProvider = EmptyMessageBusProvider
  override def getHistory(jid:String) = History.empty
  override def getAllConversations = List.empty[Conversation]
  override def getAllSlides:List[Slide] = List.empty[Slide]
  override def getConversationsForSlideId(jid:String):List[String] = List.empty[String]
  override def searchForConversation(query:String) = List.empty[Tuple2[Conversation,SearchExplanation]]
  override def searchForConversationByCourse(courseId:String) = List.empty[Conversation]
  override def searchForSlide(query:String) = List.empty[Tuple2[Slide,SearchExplanation]]
  override def queryAppliesToConversation(query:String,conversation:Conversation):Boolean = false
  override def queryAppliesToSlide(query:String,slide:Slide):Boolean = false
  override def detailsOfConversation(jid:String) = Conversation.empty
  override def detailsOfSlide(jid:String) = Slide.empty
  override def createConversation(title:String,author:String) = Conversation.empty
  override def createSlide(author:String,slideType:String = "SLIDE",grouping:List[GroupSet] = Nil):Slide = Slide.empty
  override def deleteConversation(jid:String):Conversation = Conversation.empty
  override def renameConversation(jid:String,newTitle:String):Conversation = Conversation.empty
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = Conversation.empty
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = Conversation.empty
  override def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation = Conversation.empty
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
  override def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = Profile.empty
  override def getAllProfiles:List[Profile] = Nil
  override def searchForProfile(query:String):List[Tuple2[Profile,SearchExplanation]] = Nil
  override def queryAppliesToProfile(query:String,profile:Profile):Boolean = false
  override def getProfiles(ids:String *):List[Profile] = Nil
  override def updateProfile(id:String,profile:Profile):Profile = Profile.empty
  override def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = (Nil,"")  
  override def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = {}

  override def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord] = Nil
  override def getSessionsForProfile(profileId:String):List[SessionRecord] = Nil
  override def updateSession(sessionRecord:SessionRecord):SessionRecord = SessionRecord.empty
  override def getCurrentSessions:List[SessionRecord] = Nil

  override def getThemesByAuthor(author:String):List[Theme] = Nil
  override def getSlidesByThemeKeyword(theme:String):List[String] = Nil
  override def getConversationsByTheme(theme:String):List[String] = Nil
  override def getAttendancesByAuthor(author:String):List[Attendance] = Nil
  override def getConversationsByAuthor(author:String):List[Conversation] = Nil
  override def getAuthorsByTheme(theme:String):List[String] = Nil
}

object EmptyBackendAdaptorConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").text == "empty"
  override def interpret(e:Node,o:Conversation=>Unit) = Some(EmptyBackendAdaptor)
}

class PassThroughAdaptor(sc:ServerConfiguration) extends ServerConfiguration(sc.name,sc.host,sc.onConversationDetailsUpdated){
  override val messageBusProvider:MessageBusProvider = sc.messageBusProvider
  override def getHistory(jid:String) = sc.getHistory(jid)
  override def getAllConversations = sc.getAllConversations
  override def getAllSlides:List[Slide] = List.empty[Slide]
  override def getConversationsForSlideId(jid:String):List[String] = sc.getConversationsForSlideId(jid)
  override def searchForConversation(query:String) = sc.searchForConversation(query)
  override def searchForConversationByCourse(courseId:String) = sc.searchForConversationByCourse(courseId)
  override def searchForSlide(query:String) = sc.searchForSlide(query)
  override def queryAppliesToConversation(query:String,conversation:Conversation):Boolean = sc.queryAppliesToConversation(query,conversation)
  override def queryAppliesToSlide(query:String,slide:Slide):Boolean = sc.queryAppliesToSlide(query,slide)
  override def detailsOfConversation(jid:String) = sc.detailsOfConversation(jid)
  override def detailsOfSlide(jid:String) = sc.detailsOfSlide(jid)
  override def createConversation(title:String,author:String) = sc.createConversation(title,author)
  override def createSlide(author:String,slideType:String = "SLIDE",grouping:List[GroupSet] = Nil):Slide = sc.createSlide(author,slideType,grouping)
  override def deleteConversation(jid:String):Conversation = sc.deleteConversation(jid)
  override def renameConversation(jid:String,newTitle:String):Conversation = sc.renameConversation(jid,newTitle)
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = sc.changePermissions(jid,newPermissions)
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = sc.updateSubjectOfConversation(jid,newSubject)
  override def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation = sc.addSlideAtIndexOfConversation(jid,index,slideType)
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
  override def getMockHistory:History = sc.getMockHistory
  override def searchForProfile(query:String):List[Tuple2[Profile,SearchExplanation]] = sc.searchForProfile(query)
  override def queryAppliesToProfile(query:String,profile:Profile):Boolean = sc.queryAppliesToProfile(query,profile)
  override def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = sc.createProfile(name,attrs,audiences)
  override def getAllProfiles:List[Profile] = sc.getAllProfiles
  override def getProfiles(ids:String *):List[Profile] = sc.getProfiles(ids:_*)
  override def updateProfile(id:String,profile:Profile):Profile = sc.updateProfile(id,profile)
  override def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = sc.getProfileIds(accountName,accountProvider)
  override def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = sc.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)
  override def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord] = sc.getSessionsForAccount(accountName,accountProvider)
  override def getSessionsForProfile(profileId:String):List[SessionRecord] = sc.getSessionsForProfile(profileId)
  override def updateSession(sessionRecord:SessionRecord):SessionRecord = sc.updateSession(sessionRecord)
  override def getCurrentSessions:List[SessionRecord] = sc.getCurrentSessions

  override def getThemesByAuthor(author:String):List[Theme] = sc.getThemesByAuthor(author)
  override def getSlidesByThemeKeyword(theme:String):List[String] = sc.getSlidesByThemeKeyword(theme)
  override def getConversationsByTheme(theme:String):List[String] = sc.getConversationsByTheme(theme)
  override def getAttendancesByAuthor(author:String):List[Attendance] = sc.getAttendancesByAuthor(author)
  override def getConversationsByAuthor(author:String):List[Conversation] = sc.getConversationsByAuthor(author)
  override def getAuthorsByTheme(theme:String):List[String] = sc.getAuthorsByTheme(theme)
}
