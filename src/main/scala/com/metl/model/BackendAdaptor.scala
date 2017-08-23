package com.metl.model

import com.metl.liftAuthenticator._
import com.metl.data._
import com.metl.metl2011._
import com.metl.auth._
import com.metl.utils._
import _root_.net.liftweb.util._
import Helpers._
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.http.rest._
import _root_.net.liftweb.http.provider._
import _root_.net.liftweb.sitemap._
import _root_.net.liftweb.sitemap.Loc._
import com.metl.snippet._
import com.metl.view._
import com.metl.h2._
import net.liftweb.util.Props
import com.mongodb._
import net.liftweb.mongodb._
import net.liftweb.util
import net.sf.ehcache.config.PersistenceConfiguration

import scala.xml._

object SecurityListener extends Logger {
  import java.util.Date
  def config = ServerConfiguration.default
  // this is still the right spot to run the first immediate caching behaviour of this stuff, because this is the server who'd know about this particular profile
  class AggregatedSessionRecord(val sid:String,val accountProvider:String,val accountName:String, var profileId:String = "",var ipAddress:String = "unknown",var lastActivity:Long = new Date().getTime,var userAgent:Box[String] = Empty){
    val started:Long = new Date().getTime
  }
  val ipAddresses = new scala.collection.mutable.HashMap[String,AggregatedSessionRecord]()
  object SafeSessionIdentifier extends SessionVar[String](nextFuncName)
  object LocalSessionRecord extends SessionVar[Option[AggregatedSessionRecord]](None)
  def activeSessions = ipAddresses.values.toList
  protected def sessionId:String = {
    SafeSessionIdentifier.is
  }
  protected def accountProvider:String = {
    Globals.currentAccount.provider
  }
  protected def accountName:String = {
    Globals.currentAccount.name
  }
  protected def user:String = {
    Globals.currentUser.is
  }
  protected def emitSessionRecord(in:AggregatedSessionRecord,action:String):Unit = {
    val s = SessionRecord(
      sid = in.sid,
      accountProvider = in.accountProvider,
      accountName = in.accountName,
      profileId = in.profileId,
      ipAddress = in.ipAddress,
      userAgent = in.userAgent.getOrElse(""),
      action = action,
      timestamp = in.lastActivity
    )
    config.updateSession(s)
  }
  def ensureSessionRecord:Unit = {
    var oldState = LocalSessionRecord.is.map(sr => (sr.profileId,sr.ipAddress,sr.userAgent))
    LocalSessionRecord.is.map(rec => {
      rec.lastActivity = now.getTime
    }).getOrElse({
      val newRecord = new AggregatedSessionRecord(sessionId,accountProvider,accountName)
      LocalSessionRecord(Some(newRecord))
      newRecord.userAgent = S.userAgent
      newRecord.profileId = user
      S.request.foreach(r => {
        newRecord.ipAddress = r.remoteAddr
      })
      ipAddresses += ((sessionId, newRecord))
      S.session.foreach(_.addSessionCleanup((s) => {
        SecurityListener.cleanupSession(s)
      }))
      oldState = Some((newRecord.profileId,newRecord.ipAddress,newRecord.userAgent))
      emitSessionRecord(newRecord,SessionRecordAction.Started) 
    })
    LocalSessionRecord.is.map(newRecord => {
      S.request.foreach(r => {
        newRecord.ipAddress = r.remoteAddr
      })
      newRecord.userAgent = S.userAgent
      newRecord.profileId = user

      if (oldState.map(_._1).getOrElse("") != newRecord.profileId){
        emitSessionRecord(newRecord,SessionRecordAction.ChangedProfile) 
      }
      if (oldState.map(_._2).getOrElse("") != newRecord.ipAddress){
        emitSessionRecord(newRecord,SessionRecordAction.ChangedIP)
      }
      if (oldState.map(_._3).getOrElse("") != newRecord.userAgent){
        emitSessionRecord(newRecord,SessionRecordAction.ChangedUserAgent)
      }
    })
  }
  def cleanupAllSessions:Unit = {
    ipAddresses.values.foreach(rec => {
      if (rec.profileId != "forbidden"){
        emitSessionRecord(rec,SessionRecordAction.Terminated)
      }
    })
    ipAddresses.clear
  }
  def cleanupSession(in:LiftSession):Unit = {
    LocalSessionRecord(None)
    val thisSessionId = sessionId
    ipAddresses.remove(thisSessionId).foreach(rec => {
      if (rec.profileId != "forbidden"){
        emitSessionRecord(rec,SessionRecordAction.Terminated)
      }
    })
  }
  def maintainIPAddress(r:Req):Unit = {
    try {
      ensureSessionRecord
    } catch {
      case e:StateInStatelessException => {}
      case e:Exception => {}
    }
  }
}

import java.nio.file.attribute.UserPrincipal
import javax.security.auth._
case class MeTLPrincipal(name:String) extends UserPrincipal{
  override def getName = name
}
case class MeTLRolePrincipal(name:String) extends java.security.Principal{
  override def getName = name
}

object MeTLXConfiguration extends PropertyReader with Logger {
  protected var configs:Map[String,Tuple2[ServerConfiguration,RoomProvider]] = Map.empty[String,Tuple2[ServerConfiguration,RoomProvider]]
  var clientConfig:Option[ClientConfiguration] = None
  var configurationProvider:Option[ConfigurationProvider] = None
  val updateGlobalFunc = (c:Conversation) => {
    debug("serverSide updateGlobalFunc: %s".format(c))
    getRoom("global",ServerConfiguration.default.name,GlobalRoom) ! LocalToServerMeTLStanza(MeTLCommand(c.author,new java.util.Date().getTime,"/UPDATE_CONVERSATION_DETAILS",List(c.jid.toString)))
  }
  def getRoomProvider(name:String,filePath:String) = {
    val idleTimeout:Option[Long] = (XML.load(filePath) \\ "caches" \\ "roomLifetime" \\ "@miliseconds").headOption.map(_.text.toLong)// Some(30L * 60L * 1000L)
    val safetiedIdleTimeout = Some(idleTimeout.getOrElse(30 * 60 * 1000L))
    trace("creating history caching room provider with timeout: %s".format(safetiedIdleTimeout))
    new HistoryCachingRoomProvider(name,safetiedIdleTimeout)
  }
  protected def ifConfigured(in:NodeSeq,elementName:String,action:NodeSeq=>Unit, permitMultipleValues:Boolean = false):Unit = {
    (in \\ elementName).theSeq match {
      case Nil => {}
      case element :: Nil => action(element)
      case elements if permitMultipleValues => elements.foreach(element => action(element))
      case _ => throw new Exception("too many %s elements in configuration file".format(elementName))
    }
  }
  protected def ifConfiguredFromGroup(in:NodeSeq,elementToAction:Map[String,NodeSeq=>Unit]):Unit = {
    var oneIsConfigured = false
    elementToAction.keys.foreach(element => {
      if ((in \\ element).theSeq != Nil){
        if (!oneIsConfigured){
          ifConfigured(in,element,(n:NodeSeq) => {
            debug("configuring: %s".format(element))
            oneIsConfigured = true
            elementToAction(element)(n)
          })
        } else {
          throw new Exception("too many elements in configuration file: %s".format(elementToAction))
        }
      }
    })
  }
  def setupStackAdaptorFromFile(filePath:String) = {
    val propFile = XML.load(filePath)
    ifConfigured(propFile,"stackAdaptor",(n:NodeSeq) => {
      for (
        enabled <- tryo((n \ "@enabled").text.toBoolean);
        if enabled;
        mongoHost <- tryo((n \ "@mongoHost").text);
        mongoPort <- tryo((n \ "@mongoPort").text.toInt);
        mongoDb <- tryo((n \ "@mongoDb").text)
      ) yield {  
        val mo = MongoClientOptions.builder()
          .socketTimeout(10000)
          .socketKeepAlive(true)
          .build()
        val srvr = new ServerAddress(mongoHost,mongoPort)
        MongoDB.defineDb(util.DefaultConnectionIdentifier, new MongoClient(srvr, mo), mongoDb)
        //construct standingCache from DB
        com.metl.model.Reputation.populateStandingMap
        //construct LiftActors for topics with history from DB
        com.metl.model.TopicManager.preloadAllTopics
        //ensure that the default topic is available
        com.metl.model.Topic.getDefaultValue
      }
    })
  }

  def setupClientConfigFromFile(filePath:String) = {
    val propFile = XML.load(filePath)
    val configurationProviderNodes = propFile \\ "serverConfiguration" \\ "securityProvider"
    ifConfiguredFromGroup(configurationProviderNodes,Map(
      "stableKeyProvider" -> {(n:NodeSeq) => {
        configurationProvider = Some(new StableKeyConfigurationProvider())
      }},
      "stableKeyWithRemoteCheckerProvider" -> {(n:NodeSeq) => {
        for (
          lp <- (n \ "@localPort").headOption.map(_.text.toInt);
          ls <- (n \ "@localScheme").headOption.map(_.text);
          rbh <- (n \ "@remoteBackendHost").headOption.map(_.text);
          rbp <- (n \ "@remoteBackendPort").headOption.map(_.text.toInt)
        ) yield {
          configurationProvider = Some(new StableKeyWithRemoteCheckerConfigurationProvider(ls,lp,rbh,rbp))
        }
      }},
      "staticKeyProvider" -> {(n:NodeSeq) => {
        for (
          ep <- (n \ "@ejabberdPassword").headOption.map(_.text);
          yu = (n \ "@yawsUsername").headOption.map(_.text);
          yp <- (n \ "@yawsPassword").headOption.map(_.text)
        ) yield {
          val eu = (n \ "@ejabberdUsername").headOption.map(_.text)
          configurationProvider = Some(new StaticKeyConfigurationProvider(eu,ep,yu,yp))
        }
      }}
    ));
    ifConfigured(propFile,"clientConfig",(n:NodeSeq) => {
      clientConfig = for (
        xd <- (n \ "xmppDomain").headOption.map(_.text);
        iu <- (n \ "imageUrl").headOption.map(_.text);
        xuser = "";
        xpass = ""
      ) yield {
        ClientConfiguration(xd,xuser,xpass,iu)
      }
    })
  }
  def setupExternalGradebooksFromFile(filePath:String) = {
    val nodes = XML.load(filePath) \\ "externalGradebooks"
    Globals.gradebookProviders = ExternalGradebooks.configureFromXml(nodes)
  }
  def setupAuthorizersFromFile(filePath:String) = {
    val propFile = XML.load(filePath)
    val authorizationNodes = propFile \\ "serverConfiguration" \\ "groupsProvider"
    Globals.groupsProviders = GroupsProvider.constructFromXml(authorizationNodes)
    info("configured groupsProviders: %s".format(Globals.groupsProviders))
  }
  def setupClientAdaptorsFromFile(filePath:String) = {
    xmppServer = (for (
      cc <- clientConfig;
      propFile = XML.load(filePath);
      clientAdaptors <- (propFile \\ "clientAdaptors").headOption;
      exs <- (clientAdaptors \ "embeddedXmppServer").headOption;
      keystorePath <- (exs \ "@keystorePath").headOption.map(_.text);
      keystorePassword <- (exs \ "@keystorePassword").headOption.map(_.text) 
    ) yield {
      val exs = new EmbeddedXmppServer(cc.xmppDomain,keystorePath,keystorePassword)
      exs.initialize
      LiftRules.unloadHooks.append( () => exs.shutdown)
      exs
    })
  }
  def setupMetlingPotsFromFile(filePath:String) = {
    Globals.metlingPots = MeTLingPot.configureFromXml(XML.load(filePath) \\ "metlingPotAdaptors")
  }
  def setupCachesFromFile(filePath:String) = {
    import net.sf.ehcache.config.{MemoryUnit}
    import net.sf.ehcache.store.{MemoryStoreEvictionPolicy}
    def getCacheConfig(xmlIn:NodeSeq):Option[CacheConfig] = {
      for {
        heapSize <- (xmlIn \\ "@heapSize").headOption.map(_.text.toLowerCase.trim.toInt)
        heapUnits <- (xmlIn \\ "@heapUnits").headOption.map(_.text.toLowerCase.trim match {
          case "bytes" => MemoryUnit.BYTES
          case "kilobytes" => MemoryUnit.KILOBYTES
          case "megabytes" => MemoryUnit.MEGABYTES
          case "gigabytes" => MemoryUnit.GIGABYTES
          case _ => MemoryUnit.MEGABYTES
        })
        evictionPolicy <- (xmlIn \\ "@evictionPolicy").headOption.map(_.text.toLowerCase.trim match {
          case "clock" => MemoryStoreEvictionPolicy.CLOCK
          case "fifo" => MemoryStoreEvictionPolicy.FIFO
          case "lfu" => MemoryStoreEvictionPolicy.LFU
          case "lru" => MemoryStoreEvictionPolicy.LRU
          case _ => MemoryStoreEvictionPolicy.LRU
        })
      } yield {
        CacheConfig(heapSize,heapUnits,evictionPolicy)
      }
    }
    for {
      scn <- (XML.load(filePath) \\ "caches").headOption
      resourceCacheConfig = getCacheConfig((scn \ "resourceCache").headOption.getOrElse(Nil))
      profileCacheConfig = getCacheConfig((scn \ "profileCache").headOption.getOrElse(Nil))
      sessionCacheConfig = getCacheConfig((scn \ "sessionCache").headOption.getOrElse(Nil))
      themeCacheConfig = getCacheConfig((scn \ "themeCache").headOption.getOrElse(Nil))
    } yield {
      warn("setting up caches with configs: resource:%s profile:%s session:%s theme:%s".format(resourceCacheConfig,profileCacheConfig,sessionCacheConfig,themeCacheConfig))
      ServerConfiguration.setServerConfMutator(sc => new CachingServerAdaptor(
        config = sc,
        themeCacheConfig = themeCacheConfig,
        profileCacheConfig = profileCacheConfig,
        resourceCacheConfig = resourceCacheConfig,
        sessionCacheConfig = sessionCacheConfig
      ))
    }
  }
  def setupUserProfileProvidersFromFile(filePath:String) = {
    val fileXml = XML.load(filePath)
    ifConfiguredFromGroup((fileXml \\ "userProfileProvider").headOption.getOrElse(NodeSeq.Empty),Map(
      "inMemoryUserProfileProvider" -> {
        (n:NodeSeq) => Globals.userProfileProvider = Some(new CachedInMemoryProfileProvider())
      },
      "diskCachedProfileProvider" -> {
        (n:NodeSeq) => Globals.userProfileProvider = for {
          path <- (n \ "@path").headOption.map(_.text)
        } yield {
          new DiskCachedProfileProvider(path)
        }
      },
      "dbProfileProvider" -> {
        (n:NodeSeq) => Globals.userProfileProvider = for {
          o <- Some(true)
        } yield {
          net.liftweb.mapper.Schemifier.schemify(true,net.liftweb.mapper.Schemifier.infoF _,MappedUserProfile)
          new DBBackedProfileProvider()
        }
      }
    ))
    ifConfigured((fileXml \\ "userProfileSeeds").headOption.getOrElse(NodeSeq.Empty),"userProfileSeed",(n:NodeSeq) => {
      for {
        path <- (n \\ "@filename").headOption.map(_.text)
        format <- (n \\ "@format").headOption.map(_.text)
      } yield {
        Globals.userProfileProvider.map(upp => {
          format match {
            case "xml" => {
              val seed = new XmlUserProfileSeed(path)
              seed.getValues.foreach(p => {
                upp.updateProfile(p)
              })
            }
            case "csv" => {
              val seed = new CsvUserProfileSeed(path)
              seed.getValues.foreach(p => {
                upp.updateProfile(p)
              })
            }
            case _ => {}
          }
        })
      }
    },true)
  }

  def setupServersFromFile(filePath:String) = {
    MeTL2011ServerConfiguration.initialize
    MeTL2015ServerConfiguration.initialize
    LocalH2ServerConfiguration.initialize
    setupCachesFromFile(filePath)
    ServerConfiguration.loadServerConfigsFromFile(
      path = filePath,
      onConversationDetailsUpdated = updateGlobalFunc,
      messageBusCredentailsFunc = () => {
        (for (
          cc <- configurationProvider;
          creds <- cc.getPasswords("metlxMessageBus_"+new java.util.Date().getTime.toString)
        ) yield {
          debug("vending msgBusCreds: %s".format(creds))
          (creds._1,creds._2)
        }).getOrElse(("",""))
      },
      conversationListenerCredentialsFunc = () => {
        (for (
          cc <- configurationProvider;
          creds <- cc.getPasswords("metlxConversationListener_"+new java.util.Date().getTime.toString)
        ) yield {
          debug("vending convCreds: %s".format(creds))
          (creds._1,creds._2)
        }).getOrElse(("",""))
      },
      httpCredentialsFunc = () => {
        (for (
          cc <- configurationProvider;
          creds <- cc.getPasswords("metlxHttp_"+new java.util.Date().getTime.toString)
        ) yield {
          debug("vending httpCreds: %s".format(creds))
          (creds._3,creds._4)
        }).getOrElse(("",""))
      }
    )
    val servers = ServerConfiguration.getServerConfigurations
    servers.foreach(_.isReady)
    configs = Map(servers.map(c => (c.name,(c,getRoomProvider(c.name,filePath)))):_*)
  }
  var xmppServer:Option[EmbeddedXmppServer] = None
  def initializeSystem = {
    Globals
    /*
    Props.mode match {
      case Props.RunModes.Production => Globals.isDevMode = false
      case _ => Globals.isDevMode = true
    }
    */
    // Setup RESTful endpoints (these are in view/Endpoints.scala)
    LiftRules.statelessDispatch.prepend(SystemRestHelper)
    LiftRules.statelessDispatch.prepend(MeTLRestHelper)
    LiftRules.statelessDispatch.prepend(WebMeTLRestHelper)

    LiftRules.dispatch.append(MeTLStatefulRestHelper)
    LiftRules.dispatch.append(WebMeTLStatefulRestHelper)

    setupAuthorizersFromFile(Globals.configurationFileLocation)
    setupClientConfigFromFile(Globals.configurationFileLocation)
    setupServersFromFile(Globals.configurationFileLocation)
    configs.values.foreach(c => LiftRules.unloadHooks.append(c._1.shutdown _))

    configs.values.foreach(c => {
      getRoom("global",c._1.name,GlobalRoom,true)
      debug("%s is now ready for use (%s)".format(c._1.name,c._1.isReady))
    })
    //setupStackAdaptorFromFile(Globals.configurationFileLocation)
    setupClientAdaptorsFromFile(Globals.configurationFileLocation)

    setupExternalGradebooksFromFile(Globals.configurationFileLocation)
    S.addAnalyzer((req,timeTaken,_entries) => {
      req.foreach(r => SecurityListener.maintainIPAddress(r))
    })
    setupMetlingPotsFromFile(Globals.configurationFileLocation)
    LiftRules.unloadHooks.append(() => {
      Globals.metlingPots.foreach(_.shutdown)
      SecurityListener.cleanupAllSessions
    })
    setupUserProfileProvidersFromFile(Globals.configurationFileLocation)
    LiftRules.dispatch.append(new BrightSparkIntegrationDispatch)
    LiftRules.statelessDispatch.append(new BrightSparkIntegrationStatelessDispatch)
    Globals.metlingPots.foreach(_.init)
    info(configs)
  }
  def listRooms(configName:String):List[MeTLRoom] = configs(configName)._2.list
  def checkRoom(jid:String,configName:String):Boolean = configs(configName)._2.exists(jid)
  def getRoom(jid:String,configName:String):MeTLRoom = getRoom(jid,configName,RoomMetaDataUtils.fromJid(jid),false)
  def getRoom(jid:String,configName:String,roomMetaData:RoomMetaData):MeTLRoom = getRoom(jid,configName,roomMetaData,false)
  def getRoom(jid:String,configName:String,roomMetaData:RoomMetaData,eternal:Boolean):MeTLRoom = {
    configs(configName)._2.get(jid,roomMetaData,eternal)
  }
}

class TransientLoopbackAdaptor(configName:String,onConversationDetailsUpdated:Conversation=>Unit) extends ServerConfiguration(configName,"no_host",onConversationDetailsUpdated){
  val serializer = new PassthroughSerializer
  override val messageBusProvider = new LoopbackMessageBusProvider
  override def getHistory(jid:String) = History.empty
  override def getAllConversations = List.empty[Conversation]
  override def getAllSlides = List.empty[Slide]
  override def getConversationsForSlideId(jid:String) = Nil
  override def searchForConversation(query:String) = List.empty[Conversation]
  override def searchForConversationByCourse(courseId:String) = List.empty[Conversation]
  override def detailsOfConversation(jid:String) = Conversation.empty
  override def detailsOfSlide(jid:String) = Slide.empty
  override def createConversation(title:String,author:String) = Conversation.empty
  override def createSlide(author:String,slideType:String = "SLIDE",grouping:List[com.metl.data.GroupSet] = Nil):Slide = Slide.empty
  override def deleteConversation(jid:String):Conversation = Conversation.empty
  override def renameConversation(jid:String,newTitle:String):Conversation = Conversation.empty
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = Conversation.empty
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = Conversation.empty
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = Conversation.empty
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:com.metl.data.GroupSet):Conversation = Conversation.empty
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = Conversation.empty
  override def updateConversation(jid:String,conversation:Conversation):Conversation = Conversation.empty
  override def getImage(jid:String,identity:String) = MeTLImage.empty
  override def getResource(url:String) = Array.empty[Byte]
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = ""
  override def getResource(jid:String,identifier:String):Array[Byte] = Array.empty[Byte]
  override def insertResource(jid:String,data:Array[Byte]):String = ""
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = ""
  override def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = Profile.empty
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

class ConversationCache(config:ServerConfiguration) {
  def startup:Unit = {}
  def shutdown:Unit = {}
  protected lazy val conversationCache:scala.collection.mutable.Map[String,Conversation] = scala.collection.mutable.Map(config.getAllConversations.map(c => (c.jid,c)):_*)
  protected lazy val slideCache:scala.collection.mutable.Map[String,Slide] = scala.collection.mutable.Map(config.getAllSlides.map(s => (s.id,s)):_*)

  def getAllConversations:List[Conversation] = conversationCache.values.toList
  def getAllSlides:List[Slide] = slideCache.values.toList
  def getConversationsForSlideId(jid:String):List[String] = getAllConversations.flatMap(c => c.slides.find(_.id == jid).map(s => c.jid))
  def searchForConversation(query:String):List[Conversation] = getAllConversations.filter(c => c.title.toLowerCase.trim.contains(query.toLowerCase.trim) || c.author.toLowerCase.trim == query.toLowerCase.trim).toList
  def searchForConversationByCourse(courseId:String):List[Conversation] = getAllConversations.filter(c => c.subject.toLowerCase.trim.equals(courseId.toLowerCase.trim) || c.foreignRelationship.exists(_.key.toLowerCase.trim == courseId.toLowerCase.trim)).toList
  def detailsOfConversation(jid:String):Conversation = conversationCache.get(jid).getOrElse(Conversation.empty)
  def detailsOfSlide(jid:String):Slide = slideCache.get(jid).getOrElse(Slide.empty)
  protected def updateConversation(c:Conversation):Conversation = {
    conversationCache.update(c.jid,c)
    c.slides.foreach(s => {
      slideCache.update(s.id,s)
    })
    c
  }
  def createConversation(title:String,author:String):Conversation = {
    updateConversation(config.createConversation(title,author))
  }
  def createSlide(author:String,slideType:String = "SLIDE",grouping:List[com.metl.data.GroupSet] = Nil):Slide = {
    val s = config.createSlide(author,slideType,grouping)
    slideCache.update(s.id,s)
    s
  }
  def deleteConversation(jid:String):Conversation = updateConversation(config.deleteConversation(jid))
  def renameConversation(jid:String,newTitle:String):Conversation = updateConversation(config.renameConversation(jid,newTitle))
  def changePermissions(jid:String,newPermissions:Permissions):Conversation = updateConversation(config.changePermissions(jid,newPermissions))
  def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = updateConversation(config.updateSubjectOfConversation(jid,newSubject))
  def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = updateConversation(config.addSlideAtIndexOfConversation(jid,index))
  def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:com.metl.data.GroupSet):Conversation = updateConversation(config.addGroupSlideAtIndexOfConversation(jid,index,grouping))
  def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = updateConversation(config.reorderSlidesOfConversation(jid,newSlides))
  def updateConversation(jid:String,conversation:Conversation):Conversation = updateConversation(config.updateConversation(jid,conversation))
  def getConversationsByAuthor(author:String):List[Conversation] = getAllConversations.filter(_.author == author)
}
class ThemeCache(config:ServerConfiguration,cacheConfig:CacheConfig) {
  protected val themesByAuthorCache = new ManagedCache[String,List[Theme]]("themesByAuthor",(author) => config.getThemesByAuthor(author),cacheConfig)
  protected val authorsByThemeCache = new ManagedCache[String,List[String]]("authorByTheme",(theme) => config.getAuthorsByTheme(theme),cacheConfig)
  protected val locationsByThemeCache = new ManagedCache[String,List[String]]("locationsByTheme",(theme) => config.getSlidesByThemeKeyword(theme) ::: config.getConversationsByTheme(theme),cacheConfig)
  protected val attendancesByAuthorCache = new ManagedCache[String,List[Attendance]]("attendancesByAuthor",(author) => config.getAttendancesByAuthor(author),cacheConfig)
  def startup:Unit = {
    themesByAuthorCache.startup
    authorsByThemeCache.startup
    locationsByThemeCache.startup
  }
  def shutdown:Unit = {
    themesByAuthorCache.shutdown
    authorsByThemeCache.shutdown
    locationsByThemeCache.shutdown
  }

  def getThemesByAuthor(author:String):List[Theme] = themesByAuthorCache.get(author).getOrElse(Nil)
  def getAuthorsByTheme(theme:String):List[String] =  authorsByThemeCache.get(theme).getOrElse(Nil)
  def getSlidesByThemeKeyword(theme:String):List[String] = locationsByThemeCache.get(theme).getOrElse(Nil)
  def getConversationsByTheme(theme:String):List[String] = locationsByThemeCache.get(theme).getOrElse(Nil)
  def getAttendancesByAuthor(author:String):List[Attendance] = attendancesByAuthorCache.get(author).getOrElse(Nil)

  def addAttendance(a:Attendance):Unit = {
    if (attendancesByAuthorCache.contains(a.author)){
      attendancesByAuthorCache.update(a.author,a :: attendancesByAuthorCache.get(a.author).getOrElse(Nil))
    }
  }
  def addTheme(t:MeTLTheme):Unit = {
    if (themesByAuthorCache.contains(t.author)){
      themesByAuthorCache.update(t.author,t.theme :: themesByAuthorCache.get(t.author).getOrElse(Nil))
    }
    if (authorsByThemeCache.contains(t.theme.text)){
      authorsByThemeCache.update(t.theme.text,(t.author :: authorsByThemeCache.get(t.theme.text).getOrElse(Nil)).distinct)
    }
    if (locationsByThemeCache.contains(t.theme.text)){
      locationsByThemeCache.update(t.theme.text,(t.location :: locationsByThemeCache.get(t.theme.text).getOrElse(Nil)).distinct)
    }
  }
}

class SessionCache(config:ServerConfiguration,cacheConfig:CacheConfig){
  protected val sessionCache = new ManagedCache[String,List[SessionRecord]]("sessionsByProfileId",(pid) => config.getSessionsForProfile(pid),cacheConfig)
  def startup:Unit = {
    sessionCache.startup
  }
  def shutdown:Unit = {
    sessionCache.shutdown
  }
  def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord] = {
    getCurrentSessions.filter(sr => sr.accountName == accountName && sr.accountProvider == accountProvider)
  }
  def getSessionsForProfile(profileId:String):List[SessionRecord] = {
    sessionCache.get(profileId).getOrElse(Nil)
  }
  def updateSession(sessionRecord:SessionRecord):SessionRecord = {
    val updated = config.updateSession(sessionRecord)
    sessionCache.update(sessionRecord.profileId,sessionCache.get(sessionRecord.profileId).getOrElse(Nil) ::: List(updated))
    updated
  }
  def getCurrentSessions:List[SessionRecord] = {
    Nil
    //sessionCache.getAll(sessionCache.keys).toList.flatMap(_._2)
  }
}
class ResourceCache(config:ServerConfiguration,cacheConfig:CacheConfig) {
  protected val imageCache = new ManagedCache[Tuple2[Option[String],String],MeTLImage]("imageByIdentityAndJid",(ji) => {
    ji._1.map(jid => config.getImage(jid,ji._2)).getOrElse(config.getImage(ji._2))
  },cacheConfig)
  protected val resourceCache = new ManagedCache[Tuple2[Option[String],String],Array[Byte]]("resourceByIdentityAndJid",(ji) => {
    ji._1.map(jid => config.getResource(jid,ji._2)).getOrElse(config.getResource(ji._2))
  },cacheConfig)

  def startup:Unit = {
    imageCache.startup
    resourceCache.startup
  }
  def shutdown:Unit = {
    imageCache.shutdown
    resourceCache.shutdown
  }
  def getImage(jid:String,identity:String):MeTLImage = {
    imageCache.get((Some(jid),identity)).getOrElse(MeTLImage.empty)
  }
  def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = {
    val res = config.postResource(jid,userProposedId,data)
    resourceCache.update((Some(jid),res),data)
    res
  }
  def getResource(jid:String,identifier:String):Array[Byte] = {
    resourceCache.get((Some(jid),identifier)).getOrElse(Array.empty[Byte])
  }
  def insertResource(jid:String,data:Array[Byte]):String = {
    val res = config.insertResource(jid,data)
    resourceCache.update((Some(jid),res),data)
    res
  }
  def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = {
    val res = config.upsertResource(jid,identifier,data)
    resourceCache.update((Some(jid),res),data)
    res
  }
  def getImage(identity:String):MeTLImage = {
    imageCache.get((None,identity)).getOrElse(MeTLImage.empty)
  }
  def getResource(identifier:String):Array[Byte] = {
    resourceCache.get((None,identifier)).getOrElse(Array.empty[Byte])
  }
  def insertResource(data:Array[Byte]):String = {
    val res = config.insertResource(data)
    resourceCache.update((None,res),data)
    res
  }
  def upsertResource(identifier:String,data:Array[Byte]):String = {
    val res = config.upsertResource(identifier,data)
    resourceCache.update((None,res),data)
    res
  }
}
class ProfileCache(config:ServerConfiguration,cacheConfig:CacheConfig) {
  protected val profileStore = new ManagedCache[String,Profile]("profilesById",(id) => config.getProfiles(id).headOption.getOrElse(Profile.empty),cacheConfig)
  protected val accountStore = new ManagedCache[Tuple2[String,String],Tuple2[List[String],String]]("profilesByAccount",(acc) => config.getProfileIds(acc._1,acc._2),cacheConfig)
  
  def startup:Unit = {
    profileStore.startup
    accountStore.startup
  }
  def shutdown:Unit = {
    profileStore.shutdown
    accountStore.shutdown
  }
  def getProfiles(ids:String *):List[Profile] = {
    ids.flatMap(id => profileStore.get(id)).toList
    /*
    val id = nextFuncName
    println("%s called getProfiles: %s".format(id,ids))
    val (cachedKeys,uncachedKeys) = ids.toList.partition(i => profileStore.contains(i))
    val uncached = config.getProfiles(uncachedKeys:_*)
    val cached = ids.map(i => profileStore.get(i))//All(cachedKeys)
    //profileStore.updateAll(Map(uncached.map(uc => (uc.id,uc)):_*))
    uncached.foreach(uc => profileStore.update(uc.id,uc))
    val result = uncached ::: cached.toList.flatten//map(_._2)
    println("%s completed getProfiles: %s => %s".format(id,ids,result.length))
    result
    */
  }
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = {
    val newP = config.createProfile(name,attrs,audiences)
    profileStore.update(newP.id,newP)
    newP
  }
  def updateProfile(id:String,profile:Profile):Profile = {
    val uP = config.updateProfile(id,profile)
    profileStore.update(uP.id,uP)
    uP
  }
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = {
    val nar = config.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)
    val id = (accountName,accountProvider)
    val current = accountStore.get(id).getOrElse((Nil,""))
    val currentList = current._1
    val currentDefault = current._2
    val updatedValue = {
      (disabled,default) match {
        case (true,_) if profileId == currentDefault => (currentList.filterNot(_ == profileId),"")
        case (true,_) => (currentList.filterNot(_ == profileId),currentDefault)
        case (_,true) => ((profileId :: currentList).distinct,profileId)
        case (_,false) => ((profileId :: currentList).distinct,currentDefault)
      }
    }
    accountStore.update(id,updatedValue)
  }
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = {
    accountStore.get((accountName,accountProvider)).getOrElse((Nil,""))
  }
}

class CachingServerAdaptor(
  config:ServerConfiguration,
  themeCacheConfig:Option[CacheConfig] = None,
  profileCacheConfig:Option[CacheConfig] = None,
  resourceCacheConfig:Option[CacheConfig] = None,
  sessionCacheConfig:Option[CacheConfig] = None
) extends PassThroughAdaptor(config) {
  protected val conversationCache = Some(new ConversationCache(config))
  protected val themeCache = themeCacheConfig.map(c => new ThemeCache(config,c))
  protected val profileCache = profileCacheConfig.map(c => new ProfileCache(config,c))
  protected val resourceCache = resourceCacheConfig.map(c => new ResourceCache(config,c))
  protected val sessionCache = sessionCacheConfig.map(c => new SessionCache(config,c))
 
  override val messageBusProvider = new TappingMessageBusProvider(config.messageBusProvider,s => {
    println("message going up!: %s".format(s))
  },
  s => {
    println("message going down!: %s".format(s))
    s match {
      case a:Attendance => themeCache.foreach(tc => tc.addAttendance(a))
      case t:MeTLTheme => themeCache.foreach(tc => tc.addTheme(t))
      case _ => {}
    }
  })

  override def getAllConversations = conversationCache.map(_.getAllConversations).getOrElse(config.getAllConversations)
  override def getAllSlides = conversationCache.map(_.getAllSlides).getOrElse(config.getAllSlides)
  override def getConversationsForSlideId(jid:String) = conversationCache.map(_.getConversationsForSlideId(jid)).getOrElse(config.getConversationsForSlideId(jid))
  override def searchForConversation(query:String) = conversationCache.map(_.searchForConversation(query)).getOrElse(config.searchForConversation(query))
  override def searchForConversationByCourse(courseId:String) = conversationCache.map(_.searchForConversationByCourse(courseId)).getOrElse(config.searchForConversationByCourse(courseId))
  override def detailsOfConversation(jid:String) = conversationCache.map(_.detailsOfConversation(jid)).getOrElse(config.detailsOfConversation(jid))
  override def detailsOfSlide(jid:String) = conversationCache.map(_.detailsOfSlide(jid)).getOrElse(config.detailsOfSlide(jid))
  override def createConversation(title:String,author:String) = conversationCache.map(_.createConversation(title,author)).getOrElse(config.createConversation(title,author))
  override def createSlide(author:String,slideType:String = "SLIDE",grouping:List[com.metl.data.GroupSet] = Nil):Slide = conversationCache.map(_.createSlide(author,slideType,grouping)).getOrElse(config.createSlide(author,slideType,grouping))
  override def deleteConversation(jid:String):Conversation = conversationCache.map(_.deleteConversation(jid)).getOrElse(config.deleteConversation(jid))
  override def renameConversation(jid:String,newTitle:String):Conversation = conversationCache.map(_.renameConversation(jid,newTitle)).getOrElse(config.renameConversation(jid,newTitle))
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = conversationCache.map(_.changePermissions(jid,newPermissions)).getOrElse(config.changePermissions(jid,newPermissions))
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = conversationCache.map(_.updateSubjectOfConversation(jid,newSubject)).getOrElse(config.updateSubjectOfConversation(jid,newSubject))
  override def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = conversationCache.map(_.addSlideAtIndexOfConversation(jid,index)).getOrElse(config.addSlideAtIndexOfConversation(jid,index))
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:com.metl.data.GroupSet):Conversation = conversationCache.map(_.addGroupSlideAtIndexOfConversation(jid,index,grouping)).getOrElse(config.addGroupSlideAtIndexOfConversation(jid,index,grouping))
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = conversationCache.map(_.reorderSlidesOfConversation(jid,newSlides)).getOrElse(config.reorderSlidesOfConversation(jid,newSlides))
  override def updateConversation(jid:String,conversation:Conversation):Conversation = conversationCache.map(_.updateConversation(jid,conversation)).getOrElse(config.updateConversation(jid,conversation))
  override def getConversationsByAuthor(author:String):List[Conversation] = conversationCache.map(_.getConversationsByAuthor(author)).getOrElse(config.getConversationsByAuthor(author))
  override def getImage(identity:String) = resourceCache.map(_.getImage(identity)).getOrElse(config.getImage(identity))
  override def getImage(jid:String,identity:String) = resourceCache.map(_.getImage(jid,identity)).getOrElse(config.getImage(jid,identity))
  override def getResource(url:String) = resourceCache.map(_.getResource(url)).getOrElse(config.getResource(url))
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = resourceCache.map(_.postResource(jid,userProposedId,data)).getOrElse(config.postResource(jid,userProposedId,data))
  override def getResource(jid:String,identifier:String):Array[Byte] = resourceCache.map(_.getResource(jid,identifier)).getOrElse(config.getResource(jid,identifier))
  override def insertResource(jid:String,data:Array[Byte]):String = resourceCache.map(_.insertResource(jid,data)).getOrElse(config.insertResource(jid,data))
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = resourceCache.map(_.upsertResource(jid,identifier,data)).getOrElse(config.upsertResource(jid,identifier,data)) 
  override def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = profileCache.map(_.createProfile(name,attrs,audiences)).getOrElse(config.createProfile(name,attrs,audiences))
  override def getProfiles(ids:String *):List[Profile] = profileCache.map(_.getProfiles(ids.toList:_*)).getOrElse(config.getProfiles(ids.toList:_*))
  override def updateProfile(id:String,profile:Profile):Profile = profileCache.map(_.updateProfile(id,profile)).getOrElse(config.updateProfile(id,profile))
  override def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = profileCache.map(_.getProfileIds(accountName,accountProvider)).getOrElse(config.getProfileIds(accountName,accountProvider))
  override def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = profileCache.map(_.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)).getOrElse(config.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default))
  override def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord] = sessionCache.map(_.getSessionsForAccount(accountName,accountProvider)).getOrElse(config.getSessionsForAccount(accountName,accountProvider))
  override def getSessionsForProfile(profileId:String):List[SessionRecord] = sessionCache.map(_.getSessionsForProfile(profileId)).getOrElse(config.getSessionsForProfile(profileId))
  override def updateSession(sessionRecord:SessionRecord):SessionRecord = sessionCache.map(_.updateSession(sessionRecord)).getOrElse(config.updateSession(sessionRecord))
  override def getCurrentSessions:List[SessionRecord] = sessionCache.map(_.getCurrentSessions).getOrElse(config.getCurrentSessions)
  override def getThemesByAuthor(author:String):List[Theme] = themeCache.map(_.getThemesByAuthor(author)).getOrElse(config.getThemesByAuthor(author))
  override def getSlidesByThemeKeyword(theme:String):List[String] = themeCache.map(_.getSlidesByThemeKeyword(theme)).getOrElse(config.getSlidesByThemeKeyword(theme))
  override def getConversationsByTheme(theme:String):List[String] = themeCache.map(_.getConversationsByTheme(theme)).getOrElse(config.getConversationsByTheme(theme))
  override def getAttendancesByAuthor(author:String):List[Attendance] = themeCache.map(_.getAttendancesByAuthor(author)).getOrElse(config.getAttendancesByAuthor(author))
  override def getAuthorsByTheme(theme:String):List[String] = themeCache.map(_.getAuthorsByTheme(theme)).getOrElse(config.getAuthorsByTheme(theme))

  override def shutdown:Unit = {
    super.shutdown
    resourceCache.foreach(_.shutdown)
    profileCache.foreach(_.shutdown)
    sessionCache.foreach(_.shutdown)
    conversationCache.foreach(_.shutdown)
    themeCache.foreach(_.shutdown)
  }
  protected lazy val initialize = {
    resourceCache.foreach(_.startup)
    profileCache.foreach(_.startup)
    sessionCache.foreach(_.startup)
    conversationCache.foreach(_.startup)
    themeCache.foreach(_.startup)
  }
  override def isReady:Boolean = {
    initialize
    super.isReady
  }

}

case class CacheConfig(heapSize:Int,heapUnits:net.sf.ehcache.config.MemoryUnit,memoryEvictionPolicy:net.sf.ehcache.store.MemoryStoreEvictionPolicy,timeToLiveSeconds:Option[Int]=None)

class ManagedCache[A <: Object,B <: Object](name:String,creationFunc:A=>B,cacheConfig:CacheConfig) extends Logger {
  import net.sf.ehcache.{Cache,CacheManager,Element,Status,Ehcache}
  import net.sf.ehcache.loader.{CacheLoader}
  import net.sf.ehcache.config.{CacheConfiguration,MemoryUnit,SizeOfPolicyConfiguration}
  import net.sf.ehcache.store.{MemoryStoreEvictionPolicy}
  import java.util.Collection
  import scala.collection.JavaConversions._
  protected val cm = CacheManager.getInstance()
  val cacheName = "%s_%s".format(name,nextFuncName)
  val sizeOfPolicy:SizeOfPolicyConfiguration = {
    val p = new SizeOfPolicyConfiguration()
    p.setMaxDepth(1024 * 1024 * 1024)
    val c = SizeOfPolicyConfiguration.MaxDepthExceededBehavior.CONTINUE
    p.setMaxDepthExceededBehavior(c.name)
    p
  }
  val cacheConfiguration = new CacheConfiguration()
    .name(cacheName)
    .maxBytesLocalHeap(cacheConfig.heapSize,cacheConfig.heapUnits)
    .eternal(false)
    .memoryStoreEvictionPolicy(cacheConfig.memoryEvictionPolicy)
    .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.NONE))
    .logging(false)
    .copyOnRead(false)
    .copyOnWrite(false)
//    .sizeOfPolicy(sizeOfPolicy)
  cacheConfig.timeToLiveSeconds.foreach(s => cacheConfiguration.timeToLiveSeconds(s))
  val cache = new Cache(cacheConfiguration)
  cm.addCache(cache)
  class FuncCacheLoader extends CacheLoader {
    override def clone(cache:Ehcache):CacheLoader = new FuncCacheLoader 
    def dispose:Unit = {}
    def getName:String = getClass.getSimpleName
    def getStatus:Status = cache.getStatus
    def init:Unit = {}
    def load(key:Object):Object = {
      if (key == null || key == ""){
        throw new Exception("cache %s loading passed empty or null key")
        null
      } else {
        warn("cache %s loading (%s)".format(name,key))
        key match {
          case k:A => {
            creationFunc(k).asInstanceOf[Object]
          }
          case other => {
            warn("cache %s loading (%s) supplied other type of key [%s]".format(name,key,other))
            null
          }
        }
      }
    }
    def load(key:Object,arg:Object):Object = load(key) // not yet sure what to do with this argument in this case
    def loadAll(keys:Collection[_]):java.util.Map[Object,Object] = Map(keys.toArray.toList.map(k => (k,load(k))):_*)
    def loadAll(keys:Collection[_],argument:Object):java.util.Map[Object,Object] = Map(keys.toArray.toList.map(k => (k,load(k,argument))):_*)
  }
  val loader = new FuncCacheLoader
  def keys:List[A] = cache.getKeys.toList.map(_.asInstanceOf[A])
  def contains(key:A):Boolean = key != null && cache.isKeyInCache(key)
  def get(key:A):Option[B] = {
    cache.getWithLoader(key,loader,null) match {
      case null => {
        warn("getWithLoader(%s) returned null".format(key))
        None
      }
      case e:Element => e.getObjectValue match {
        case i:B => {
          warn("getWithLoader(%s) returned %s".format(key,i))
          Some(i)
        }
        case other => {
          warn("getWithLoader(%s) returned %s cast to type".format(key,other))
          Some(other.asInstanceOf[B])
        }
      }
    }
  }
  def update(key:A,value:B):Unit = {
    cache.put(new Element(key,value))
    println("put keys in cache[%s]: %s (%s)".format(name,keys.length,cache.getSize))
  }
  def startup = try {
    if (cache.getStatus == Status.STATUS_UNINITIALISED){
      cache.initialise
    }
  } catch {
    case e:Exception => {
      warn("exception initializing ehcache: %s".format(e.getMessage))
    }
  }
  def shutdown = cache.dispose()
}
