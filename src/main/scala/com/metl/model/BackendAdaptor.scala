package com.metl.model

import com.metl.liftAuthenticator._
import com.metl.data._
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
  def setupServersFromFile(filePath:String) = {
    LocalH2ServerConfiguration.initialize
    setupCachesFromFile(filePath)
    ServerConfiguration.loadServerConfigsFromFile(
      path = filePath,
      onConversationDetailsUpdated = updateGlobalFunc
    )
    val servers = ServerConfiguration.getServerConfigurations
    servers.foreach(_.isReady)
    configs = Map(servers.map(c => (c.name,(c,getRoomProvider(c.name,filePath)))):_*)
  }
  def initializeSystem = {
    Globals
    // Setup RESTful endpoints (these are in view/Endpoints.scala)
    LiftRules.statelessDispatch.prepend(SystemRestHelper)
    LiftRules.statelessDispatch.prepend(MeTLRestHelper)
    LiftRules.statelessDispatch.prepend(WebMeTLRestHelper)

    LiftRules.dispatch.append(MeTLStatefulRestHelper)
    LiftRules.dispatch.append(WebMeTLStatefulRestHelper)

    setupAuthorizersFromFile(Globals.configurationFileLocation)
    setupServersFromFile(Globals.configurationFileLocation)
    configs.values.foreach(c => LiftRules.unloadHooks.append(c._1.shutdown _))

    configs.values.foreach(c => {
      getRoom("global",c._1.name,GlobalRoom,true)
      debug("%s is now ready for use (%s)".format(c._1.name,c._1.isReady))
    })
    setupExternalGradebooksFromFile(Globals.configurationFileLocation)
    S.addAnalyzer((req,timeTaken,_entries) => {
      req.foreach(r => SecurityListener.maintainIPAddress(r))
    })
    setupMetlingPotsFromFile(Globals.configurationFileLocation)
    LiftRules.unloadHooks.append(() => {
      Globals.metlingPots.foreach(_.shutdown)
      SecurityListener.cleanupAllSessions
    })
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
