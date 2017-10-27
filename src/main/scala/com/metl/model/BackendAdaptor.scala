package com.metl.model

import com.metl.data._
import com.metl.metl2011._
import com.metl.utils._
import _root_.net.liftweb.util._
import Helpers._
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import com.metl.external.ExternalSnapshotExportConfigurator
import com.metl.view._
import com.metl.h2._
import com.mongodb._
import net.liftweb.mongodb._
import net.liftweb.util
import net.sf.ehcache.config.PersistenceConfiguration

import scala.xml._

object SecurityListener extends Logger {
  import java.util.Date
  class SessionRecord(val sid:String,val authenticatedUser:String, var username:String = "",var ipAddress:String = "unknown",var lastActivity:Long = new Date().getTime,var userAgent:Box[String] = Empty){
    val started:Long = new Date().getTime
  }
  val ipAddresses = new scala.collection.mutable.HashMap[String,SessionRecord]()
  object SafeSessionIdentifier extends SessionVar[String](nextFuncName)
  object SessionRecord extends SessionVar[Option[SessionRecord]](None)
  def activeSessions = ipAddresses.values.toList
  protected def sessionId:String = {
    SafeSessionIdentifier.is
  }
  protected def authenticatedUser:String = {
    Globals.casState.authenticatedUsername
  }
  protected def user:String = {
    Globals.currentUser.is
  }
  def ensureSessionRecord:Unit = {
    SessionRecord.is.map(rec => {
      rec.lastActivity = now.getTime
    }).getOrElse({
      val currentCasState = Globals.casState.is
      val newRecord = new SessionRecord(sessionId,authenticatedUser)
      SessionRecord(Some(newRecord))
      newRecord.userAgent = S.userAgent
      newRecord.username = user
      S.request.foreach(r => {
        newRecord.ipAddress = r.remoteAddr
      })
      ipAddresses += ((sessionId, newRecord))
      S.session.foreach(_.addSessionCleanup((s) => {
        try {
          SecurityListener.cleanupSession(s)
        } catch {
          case e:Exception => {
            error("exception thrown while cleaning up session: %s".format(s),e)
          }
        }
      }))
      info("%s :: %s logged in, ipAddress %s, userAgent: %s".format(newRecord.sid,newRecord.authenticatedUser,newRecord.ipAddress,newRecord.userAgent))
    })
    val oldState = SessionRecord.is.map(sr => (sr.username,sr.ipAddress,sr.userAgent))
    SessionRecord.is.map(newRecord => {
      S.request.foreach(r => {
        newRecord.ipAddress = r.remoteAddr
      })
      newRecord.userAgent = S.userAgent
      newRecord.username = user
      if (oldState.map(_._1).getOrElse("") != newRecord.username){
        info("%s :: %s changed username %s => %s".format(sessionId,authenticatedUser,oldState.map(_._1),newRecord.username))
      }
      if (oldState.map(_._2).getOrElse("") != newRecord.ipAddress){
        info("%s :: %s changed IP address %s => %s".format(sessionId,authenticatedUser,oldState.map(_._2),newRecord.ipAddress))
      }
      if (oldState.map(_._3).getOrElse("") != newRecord.userAgent){
        info("%s :: %s changed userAgent %s => %s".format(sessionId,authenticatedUser,oldState.map(_._3),newRecord.userAgent))
      }
    })
  }
  def cleanupAllSessions:Unit = {
    ipAddresses.values.foreach(rec => {
      if (rec.username != "forbidden"){
        info("%s :: %s user session terminated by shutdown, user %s ipAddress %s, userAgent: %s".format(rec.sid,rec.authenticatedUser,rec.username,Some(rec.ipAddress),rec.userAgent))
      }
    })
    ipAddresses.clear
  }
  def cleanupSession(in:LiftSession):Unit = {
    SessionRecord(None)
    val thisSessionId = sessionId
    ipAddresses.remove(thisSessionId).foreach(rec => {
      if (rec.username != "forbidden"){
          info("%s :: %s user session expired, user %s, ipAddress %s, userAgent: %s".format(thisSessionId,rec.authenticatedUser,rec.username,Some(rec.ipAddress),rec.userAgent))
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

object MeTLXConfiguration extends PropertyReader with Logger with ReflectionUtil {
  protected var configs:Map[String,Tuple2[ServerConfiguration,RoomProvider]] = Map.empty[String,Tuple2[ServerConfiguration,RoomProvider]]
  var clientConfig:Option[ClientConfiguration] = None
  var configurationProvider:Option[ConfigurationProvider] = None
  val updateGlobalFunc = (c:Conversation) => {
    debug("serverSide updateGlobalFunc: %s".format(c))
    getRoom("global",c.server.name,GlobalRoom(c.server)) ! LocalToServerMeTLStanza(MeTLCommand(c.server,c.author,new java.util.Date().getTime,"/UPDATE_CONVERSATION_DETAILS",List(c.jid.toString)))
  }
  def getRoomProvider(config:ServerConfiguration,filePath:String) = {
    val idleTimeout:Option[Long] = (XML.load(filePath) \\ "caches" \\ "roomLifetime" \\ "@miliseconds").headOption.map(_.text.toLong)// Some(30L * 60L * 1000L)
    val safetiedIdleTimeout = Some(idleTimeout.getOrElse(30 * 60 * 1000L))
    trace("creating history caching room provider with timeout: %s".format(safetiedIdleTimeout))
    new HistoryCachingRoomProvider(config,safetiedIdleTimeout)
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
    Globals.groupsProviders = GroupsProviderConfigurator.constructFromXml(authorizationNodes)
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
    for (
      scn <- (XML.load(filePath) \\ "caches" \\ "resourceCache").headOption;
      heapSize <- (scn \\ "@heapSize").headOption.map(_.text.toLowerCase.trim.toInt);
      heapUnits <- (scn \\ "@heapUnits").headOption.map(_.text.toLowerCase.trim match {
        case "bytes" => MemoryUnit.BYTES
        case "kilobytes" => MemoryUnit.KILOBYTES
        case "megabytes" => MemoryUnit.MEGABYTES
        case "gigabytes" => MemoryUnit.GIGABYTES
        case _ => MemoryUnit.MEGABYTES
      });
      evictionPolicy <- (scn \\ "@evictionPolicy").headOption.map(_.text.toLowerCase.trim match {
        case "clock" => MemoryStoreEvictionPolicy.CLOCK
        case "fifo" => MemoryStoreEvictionPolicy.FIFO
        case "lfu" => MemoryStoreEvictionPolicy.LFU
        case "lru" => MemoryStoreEvictionPolicy.LRU
        case _ => MemoryStoreEvictionPolicy.LRU
      })
    ) yield {
      val cacheConfig = CacheConfig(heapSize,heapUnits,evictionPolicy)
      info("setting up resourceCaches with config: %s".format(cacheConfig))
      ServerConfiguration.setServerConfMutator(sc => new ResourceCachingAdaptor(sc,cacheConfig))
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
  def setupSnapshotExportersFromFile(xml:NodeSeq,conversationFunc:String=>Tuple2[String,Map[String,Array[Byte]]]) = {
    for {
      n <- (xml \\ "externalLibSnapshotExporterProvider").headOption
      className <- (n \ "@className").headOption.map(_.text)
    } yield {
      constructExternalClasses[Object,ExternalSnapshotExportConfigurator](className,(configurator:ExternalSnapshotExportConfigurator) => {
        configurator.configureFromXml(n,conversationFunc).right.toOption.getOrElse(Nil)
      }).left.map(e => {
        error("exception thrown while configuring an externalSnapshotExporter",e)
        e
      })
    }
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
    configs = Map(servers.map(c => (c.name,(c,getRoomProvider(c,filePath)))):_*)
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
      getRoom("global",c._1.name,GlobalRoom(c._1),true)
      debug("%s is now ready for use (%s)".format(c._1.name,c._1.isReady))
    })
    //setupStackAdaptorFromFile(Globals.configurationFileLocation)
    setupClientAdaptorsFromFile(Globals.configurationFileLocation)

    setupExternalGradebooksFromFile(Globals.configurationFileLocation)
    S.addAnalyzer((req,timeTaken,_entries) => {
      req.foreach(r => SecurityListener.maintainIPAddress(r))
    })
    LiftRules.unloadHooks.append(() => {
      Globals.metlingPots.foreach(_.shutdown)
      SecurityListener.cleanupAllSessions
    })
    setupUserProfileProvidersFromFile(Globals.configurationFileLocation)

    Globals.metlingPots.foreach(_.init)

    (scala.xml.XML.load(Globals.configurationFileLocation) \\ "externalSnapshotExporters" ).headOption.foreach(externalSnapshotNodes => setupSnapshotExportersFromFile(externalSnapshotNodes,(conversationJid:String) => {
      val details = ServerConfiguration.default.detailsOfConversation(conversationJid)
      val slideMap = Map(details.slides.sortBy(_.index).map(s => {
        (s.id.toString, HttpResponder.getSnapshotWithPrivate(s.id.toString, "medium"))
      }): _*)
      (details.title, slideMap)
    }))
    info(configs)
  }
  def listRooms(configName:String):List[MeTLRoom] = configs(configName)._2.list
  def getRoom(jid:String,configName:String):MeTLRoom = getRoom(jid,configName,RoomMetaDataUtils.fromJid(jid),false)
  def getRoom(jid:String,configName:String,roomMetaData:RoomMetaData):MeTLRoom = getRoom(jid,configName,roomMetaData,false)
  def getRoom(jid:String,configName:String,roomMetaData:RoomMetaData,eternal:Boolean):MeTLRoom = {
    configs(configName)._2.get(jid,roomMetaData,eternal)
  }
}

class TransientLoopbackAdaptor(configName:String,onConversationDetailsUpdated:Conversation=>Unit) extends ServerConfiguration(configName,"no_host",onConversationDetailsUpdated){
  val serializer = new PassthroughSerializer
  val messageBusProvider = new LoopbackMessageBusProvider
  override def getMessageBus(d:MessageBusDefinition) = messageBusProvider.getMessageBus(d)
  override def getHistory(jid:String) = History.empty
  override def getConversationForSlide(slideJid:String):String = ""
  override def getAllConversations = List.empty[Conversation]
  override def searchForConversation(query:String) = List.empty[Conversation]
  override def searchForConversationByCourse(courseId:String) = List.empty[Conversation]
  override def detailsOfConversation(jid:String) = Conversation.empty
  override def createConversation(title:String,author:String) = Conversation.empty
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
}

case class CacheConfig(heapSize:Int,heapUnits:net.sf.ehcache.config.MemoryUnit,memoryEvictionPolicy:net.sf.ehcache.store.MemoryStoreEvictionPolicy,timeToLiveSeconds:Option[Int]=None)

class ManagedCache[A <: Object,B <: Object](name:String,creationFunc:A=>B,cacheConfig:CacheConfig) extends Logger {
  import net.sf.ehcache.{Cache,CacheManager,Element,Status,Ehcache}
  import net.sf.ehcache.loader.{CacheLoader}
  import net.sf.ehcache.config.{CacheConfiguration,MemoryUnit}
  import net.sf.ehcache.store.{MemoryStoreEvictionPolicy}
  import java.util.Collection
  import scala.collection.JavaConversions._
  protected val cm = CacheManager.getInstance()
  val cacheName = "%s_%s".format(name,nextFuncName)
  val cacheConfiguration = new CacheConfiguration()
    .name(cacheName)
    .maxBytesLocalHeap(cacheConfig.heapSize,cacheConfig.heapUnits)
    .eternal(false)
    .memoryStoreEvictionPolicy(cacheConfig.memoryEvictionPolicy)
    .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.NONE))
    .logging(false)
  cacheConfig.timeToLiveSeconds.foreach(s => cacheConfiguration.timeToLiveSeconds(s))
  val cache = new Cache(cacheConfiguration)
  cm.addCache(cache)
  class FuncCacheLoader extends CacheLoader {
    override def clone(cache:Ehcache):CacheLoader = new FuncCacheLoader 
    def dispose:Unit = {}
    def getName:String = getClass.getSimpleName
    def getStatus:Status = cache.getStatus
    def init:Unit = {}
    def load(key:Object):Object = key match {
      case k:A => {
        creationFunc(k).asInstanceOf[Object]
      }
      case _ => null
    }
    def load(key:Object,arg:Object):Object = load(key) // not yet sure what to do with this argument in this case
    def loadAll(keys:Collection[_]):java.util.Map[Object,Object] = Map(keys.toArray.toList.map(k => (k,load(k))):_*)
    def loadAll(keys:Collection[_],argument:Object):java.util.Map[Object,Object] = Map(keys.toArray.toList.map(k => (k,load(k,argument))):_*)
  }
  val loader = new FuncCacheLoader
  def get(key:A):B = {
    cache.getWithLoader(key,loader,null).getObjectValue.asInstanceOf[B]
  }
  def update(key:A,value:B):Unit = {
    cache.put(new Element(key,value))
  }
  def startup = try {
    cache.initialise
  } catch {
    case e:Exception => {
      warn("exception initializing ehcache: %s".format(e.getMessage))
    }
  }
  def shutdown = cache.dispose()
}

class ResourceCachingAdaptor(sc:ServerConfiguration,cacheConfig:CacheConfig) extends PassThroughAdaptor(sc){
  val imageCache = new ManagedCache[String,MeTLImage]("imageByIdentity",((i:String)) => super.getImage(i),cacheConfig)
  val imageWithJidCache = new ManagedCache[Tuple2[String,String],MeTLImage]("imageByIdentityAndJid",(ji) => super.getImage(ji._1,ji._2),cacheConfig)
  val resourceCache = new ManagedCache[String,Array[Byte]]("resourceByIdentity",(i:String) => super.getResource(i),cacheConfig)
  val resourceWithJidCache = new ManagedCache[Tuple2[String,String],Array[Byte]]("resourceByIdentityAndJid",(ji) => super.getResource(ji._1,ji._2),cacheConfig)
  override def getImage(jid:String,identity:String) = {
    imageWithJidCache.get((jid,identity))
  }
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = {
    val res = super.postResource(jid,userProposedId,data)
    resourceWithJidCache.update((jid,res),data)
    res
  }
  override def getResource(jid:String,identifier:String):Array[Byte] = {
    resourceWithJidCache.get((jid,identifier))
  }
  override def insertResource(jid:String,data:Array[Byte]):String = {
    val res = super.insertResource(jid,data)
    resourceWithJidCache.update((jid,res),data)
    res
  }
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = {
    val res = super.upsertResource(jid,identifier,data)
    resourceWithJidCache.update((jid,res),data)
    res
  }
  override def getImage(identity:String) = {
    imageCache.get(identity)
  }
  override def getResource(identifier:String):Array[Byte] = {
    resourceCache.get(identifier)
  }
  override def insertResource(data:Array[Byte]):String = {
    val res = super.insertResource(data)
    resourceCache.update(res,data)
    res
  }
  override def upsertResource(identifier:String,data:Array[Byte]):String = {
    val res = super.upsertResource(identifier,data)
    resourceCache.update(res,data)
    res
  }
  override def shutdown:Unit = {
    super.shutdown
    imageCache.shutdown
    imageWithJidCache.shutdown
    resourceCache.shutdown
    resourceWithJidCache.shutdown
  }
  protected lazy val initialize = {
    resourceWithJidCache.startup
    resourceCache.startup
    imageWithJidCache.startup
    imageCache.startup
  }
  override def isReady:Boolean = {
    initialize
    super.isReady
  }
}
