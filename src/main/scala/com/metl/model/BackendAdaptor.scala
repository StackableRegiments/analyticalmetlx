package com.metl.model

import com.metl.liftAuthenticator._
import monash.SAML._

import com.metl.cas._
import com.metl.data._
import com.metl.metl2011._
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
import com.metl.cas._
import com.metl.auth._
import com.metl.h2._

import net.liftweb.util.Props
import com.mongodb._
import net.liftweb.mongodb._

import scala.xml._

object StackConfiguration {
  def setup = {
    val mo = new MongoOptions
    mo.socketTimeout = 10000
    mo.socketKeepAlive = true
    val srvr = new ServerAddress("127.0.0.1", 27017)

    MongoDB.defineDb(DefaultMongoIdentifier, new Mongo(srvr, mo), "stack")

    //construct standingCache from DB
    com.metl.model.Reputation.populateStandingMap
    //construct LiftActors for topics with history from DB
    com.metl.model.TopicManager.preloadAllTopics
    //ensure that the default topic is available
    com.metl.model.Topic.getDefaultValue
  }
}


object MeTLXConfiguration extends PropertyReader {
  protected var configs:Map[String,Tuple2[ServerConfiguration,RoomProvider]] = Map.empty[String,Tuple2[ServerConfiguration,RoomProvider]]
  val updateGlobalFunc = (c:Conversation) => {
    getRoom("global",c.server.name) ! ServerToLocalMeTLStanza(MeTLCommand(c.server,c.author,new java.util.Date().getTime,"/UPDATE_CONVERSATION_DETAILS",List(c.jid.toString)))
  }
  def getRoomProvider(name:String) = {
    new HistoryCachingRoomProvider(name)
  }
  def getSAMLconfiguration(propertySAML:NodeSeq) = {
//    val propertySAML = readNode(properties, "saml")

    val serverScheme = readMandatoryText(propertySAML, "serverScheme")
    val serverName = readMandatoryText(propertySAML, "serverName")
    val serverPort = readMandatoryText(propertySAML, "serverPort")

    val samlCallbackUrl = readMandatoryText(propertySAML, "callbackUrl")
    val idpMetadataFileName = readMandatoryText(propertySAML, "idpMetadataFileName")

    val maximumAuthenticationLifetime = readMandatoryText(propertySAML, "maximumAuthenticationLifetime")

    val optionOfSettingsForADFS = tryo{ maximumAuthenticationLifetime.toInt } match {
      case Full(number:Int) => Some(SettingsForADFS(maximumAuthenticationLifetime = number.toInt))
      case _ => None
    }

    val nodeProtectedRoutes = readNodes(readNode(propertySAML, "protectedRoutes"),"route")
    val protectedRoutes = nodeProtectedRoutes.map(nodeProtectedRoute => {
      nodeProtectedRoute.text :: Nil
    }).toList

    val attrTransformers = Map(readNodes(readNode(propertySAML, "informationAttributes"),"informationAttribute").flatMap(elem => elem match {
      case e:Elem => Some((readMandatoryAttribute(e,"samlAttribute"),readMandatoryAttribute(e,"attributeType")))
      case _ => None
    }).toList:_*)
    val groupMap = Map(readNodes(readNode(propertySAML, "eligibleGroups"),"eligibleGroup").flatMap(elem => elem match {
      case e:Elem => Some((readMandatoryAttribute(e,"samlAttribute"),readMandatoryAttribute(e,"groupType")))
      case _ => None
    }).toList:_*)

    SAMLconfiguration(
      idpMetaDataPath = idpMetadataFileName, //getClass.getResource("/%s".format(idpMetadataFileName)).getPath,
      serverScheme = serverScheme,
      serverName = serverName,
      serverPort = serverPort.toInt,
      callBackUrl = samlCallbackUrl,
      protectedRoutes = protectedRoutes,
      optionOfSettingsForADFS = optionOfSettingsForADFS,
      eligibleGroups = groupMap,
      attributeTransformers = attrTransformers
    )
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
      if (!oneIsConfigured){
        ifConfigured(in,element,(n:NodeSeq) => {
          oneIsConfigured = true
          elementToAction(element)(n)
        })
      } else {
        throw new Exception("too many elements in configuration file: %s".format(elementToAction))
      }
    })
  }
  def setupAuthorizersFromFile(filePath:String) = {
    val propFile = XML.load(filePath)
    val authorizationNodes = propFile \\ "properties" \\ "groupsProvider"
    ifConfigured(authorizationNodes,"selfGroups",(n:NodeSeq) => {
      Globals.groupsProviders = new SelfGroupsProvider() :: Globals.groupsProviders
    },false)
    ifConfigured(authorizationNodes,"flatFileGroups",(n:NodeSeq) => {
      Globals.groupsProviders = GroupsProvider.createFlatFileGroups(n) :: Globals.groupsProviders
    },true)
  }
  def setupAuthenticatorsFromFile(filePath:String) = {
    val propFile = XML.load(filePath)
    val authenticationNodes = propFile \\ "properties" \\ "authentication"
    ifConfiguredFromGroup(authenticationNodes,Map(
      "saml" -> {(n:NodeSeq) => {
        def setupUserWithSamlState(la: LiftAuthStateData): Unit = {
          if ( la.authenticated ) {
            Globals.currentUser(la.username)
            Globals.casState.set(new LiftAuthStateData(true,la.username,(la.eligibleGroups.toList ::: Globals.groupsProviders.flatMap(_.getGroupsFor(la.username))).distinct,la.informationGroups))
          }
        }
        LiftAuthAuthentication.attachAuthenticator(
          new SAMLAuthenticationSystem(
            new SAMLAuthenticator(
              alreadyLoggedIn = () => Globals.casState.authenticated,
              onSuccess = setupUserWithSamlState _,
              samlConfiguration = getSAMLconfiguration(n)
            )
          )
        )
      }},
      "cas" -> {(n:NodeSeq) => {
//        CASAuthentication.attachCASAuthenticator( 
        LiftAuthAuthentication.attachAuthenticator(
          new CASAuthenticationSystem(
            new CASAuthenticator(
              (n \\ "@realm").text,
              (n \\ "@baseUrl").text,
              None,
              alreadyLoggedIn = () => Globals.casState.authenticated,
              onSuccess = (la:LiftAuthStateData) => {
                if ( la.authenticated ) {
                  Globals.currentUser(la.username)
                  Globals.casState.set(new LiftAuthStateData(true,la.username,(la.eligibleGroups.toList ::: Globals.groupsProviders.flatMap(_.getGroupsFor(la.username))).distinct,la.informationGroups))
                }
              }
            ){
              //this is where we do the appropriate setup for being behind a reverse proxy and not detecting our own location correctly.  This should be set from the xml element.
              protected override val overrideHost:Box[String] = Empty
              protected override val overridePort:Box[Int] = Empty
              protected override val overrideScheme:Box[String] = Empty              
            }
          )
        )
    // )
      }}/*,
      "openIdAuthenticator" -> {(n:NodeSeq) => {
        OpenIdAuthenticator.attachOpenIdAuthenticator(
          new OpenIdAuthenticator(
            alreadyLoggedIn = () => Globals.casState.authenticated,
            onSuccess = (la:LiftAuthStateData) => {
              if ( la.authenticated ) {
                Globals.currentUser(la.username)
                Globals.casState.set(new CASStateData(true,la.username,(la.eligibleGroups.toList ::: Globals.groupsProviders.flatMap(_.getGroupsFor(la.username))).distinct,la.informationGroups))
              }
            },
            (n \\ "openIdEndpoint").map(eXml => OpenIdEndpoint((eXml \\ "@name").text,(s) => (eXml \\ "@formattedEndpoint").text.format(s),(eXml \\ "@imageSrc").text,Empty)) match {
              case Nil => Empty
              case specificEndpoints => Full(specificEndpoints)
            }
          )
        )
      }}
      */
    ))
  }
  def setupServersFromFile(filePath:String) = {
    MeTL2011ServerConfiguration.initialize
    LocalH2ServerConfiguration.initialize
    ServerConfiguration.loadServerConfigsFromFile(filePath,updateGlobalFunc)
    val servers = ServerConfiguration.getServerConfigurations
    configs = Map(servers.map(c => (c.name,(c,getRoomProvider(c.name)))):_*)
  }
  def initializeSystem = {
    Props.mode match {
      case Props.RunModes.Production => Globals.isDevMode = false
      case _ => Globals.isDevMode = true
    }
    setupServersFromFile(Globals.configurationFileLocation)
    // Setup RESTful endpoints (these are in view/Endpoints.scala)
    LiftRules.statelessDispatchTable.prepend(MeTLRestHelper)
    LiftRules.dispatch.append(MeTLStatefulRestHelper)
    LiftRules.statelessDispatchTable.prepend(WebMeTLRestHelper)
    LiftRules.dispatch.append(WebMeTLStatefulRestHelper)
    configs.values.foreach(c => {
      getRoom("global",c._1.name)
      println("%s is now ready for use (%s)".format(c._1.name,c._1.isReady))
    })
    setupAuthorizersFromFile(Globals.configurationFileLocation)
    setupAuthenticatorsFromFile(Globals.configurationFileLocation)
    configs.values.foreach(c => LiftRules.unloadHooks.append(c._1.shutdown _))
    StackConfiguration.setup
    println(configs)
  }
  def getRoom(jid:String,configName:String) = {
    configs(configName)._2.get(jid)
  }
}

class TransientLoopbackAdaptor(configName:String,onConversationDetailsUpdated:Conversation=>Unit) extends ServerConfiguration(configName,"no_host",onConversationDetailsUpdated){
  val serializer = new PassthroughSerializer
  val messageBusProvider = new LoopbackMessageBusProvider
  override def getMessageBus(d:MessageBusDefinition) = messageBusProvider.getMessageBus(d)
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
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = Conversation.empty
  override def getImage(jid:String,identity:String) = MeTLImage.empty
  override def getResource(url:String) = Array.empty[Byte]
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = ""
}
