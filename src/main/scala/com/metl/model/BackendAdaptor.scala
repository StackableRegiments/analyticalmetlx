package com.metl.model

import com.metl.liftAuthenticator._
import monash.SAML._

import com.metl.cas._
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
import com.metl.cas._
import com.metl.formAuthenticator._
//import com.metl.auth._
import com.metl.h2._

import net.liftweb.util.Props
import com.mongodb._
import net.liftweb.mongodb._

import scala.xml._

class Gen2FormAuthenticator(loginPage:NodeSeq, formSelector:String, usernameSelector:String, passwordSelector:String, verifyCredentials:Tuple2[String,String]=>LiftAuthStateData, alreadyLoggedIn:() => Boolean,onSuccess:(LiftAuthStateData) => Unit) extends FormAuthenticator(loginPage,formSelector,usernameSelector,passwordSelector,verifyCredentials,alreadyLoggedIn,onSuccess) with Logger {
  debug("Gen2FormAuthenticator: %s\r\n%s %s %s %s".format(loginPage,formSelector,usernameSelector,passwordSelector,verifyCredentials))
  override def constructResponseWithMessages(req:Req,additionalMessages:List[String] = List.empty[String]) = Stopwatch.time("FormAuthenticator.constructReq",{
      val loginPageNode = (
        "%s [method]".format(formSelector) #> "POST" &
        "%s [action]".format(formSelector) #> "/formLogon" &
        "%s *".format(formSelector) #> {(formNode:NodeSeq) => {
          <input type="hidden" name="path" value={makeUrlFromReq(req)}></input> ++ 
          additionalMessages.foldLeft(NodeSeq.Empty)((acc,am) => {
            acc ++ <div class="loginError">{am}</div>
          }) ++ (
// these next two lines aren't working, and I'm not sure why not
            "%s [name]".format(usernameSelector) #> "username" &
            "%s [name]".format(passwordSelector) #> "password"
          ).apply(formNode) 
        }} 
      ).apply(loginPage)
      debug("constructed: %s".format(loginPageNode))
      LiftRules.convertResponse(
        (loginPageNode,200),
        S.getHeaders(LiftRules.defaultHeaders((loginPageNode,req))),
        S.responseCookies,
        req
      )
  })
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
    getRoom("global",c.server.name,GlobalRoom(c.server.name)) ! ServerToLocalMeTLStanza(MeTLCommand(c.server,c.author,new java.util.Date().getTime,"/UPDATE_CONVERSATION_DETAILS",List(c.jid.toString)))
  }
  def getRoomProvider(name:String) = {
    new HistoryCachingRoomProvider(name)
  }

  def getSAMLconfiguration(propertySAML:NodeSeq) = {
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
    val optionOfKeyStoreInfo = for (
      keystore <- (propertySAML \ "keystorePath").headOption.map(_.text);
      password <- (propertySAML \ "keystorePassword").headOption.map(_.text);
      privateKeyPassword <- (propertySAML \ "keystorePrivateKeyPassword").headOption.map(_.text)
    ) yield {
      keyStoreInfo(keystore,password,privateKeyPassword)
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
      attributeTransformers = attrTransformers,
      optionOfKeyStoreInfo = optionOfKeyStoreInfo
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
        val mo = new MongoOptions
        mo.socketTimeout = 10000
        mo.socketKeepAlive = true
        val srvr = new ServerAddress(mongoHost,mongoPort)
        MongoDB.defineDb(DefaultMongoIdentifier, new Mongo(srvr, mo), mongoDb)
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
  def setupAuthorizersFromFile(filePath:String) = {
    val propFile = XML.load(filePath)
    val authorizationNodes = propFile \\ "serverConfiguration" \\ "groupsProvider"
    ifConfigured(authorizationNodes,"selfGroups",(n:NodeSeq) => {
      Globals.groupsProviders = new SelfGroupsProvider() :: Globals.groupsProviders
    },false)
    ifConfigured(authorizationNodes,"flatFileGroups",(n:NodeSeq) => {
      Globals.groupsProviders = GroupsProvider.createFlatFileGroups(n) :: Globals.groupsProviders
    },true)
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
  def setupAuthenticatorsFromFile(filePath:String) = {
    val propFile = XML.load(filePath)
    val authenticationNodes = propFile \\ "serverConfiguration" \\ "authentication"
    def setUserPrincipal(username:String):Unit = {
      try {
        S.containerRequest.foreach{
          case sr:org.eclipse.jetty.server.Request => {
            println("jetty request")
            if (sr.getUserPrincipal() == null){
              val principal = new MeTLPrincipal(username)
              val s1 = new Subject()
              val principalSet = s1.getPrincipals()
              principalSet.add(principal)
              val s2 = new Subject(true,principalSet,new java.util.HashSet[Object](),new java.util.HashSet[Object]())
              val roles = List("USER").toArray
              val authMethod = ""
              val authentication = new org.eclipse.jetty.security.UserAuthentication(authMethod,
                new org.eclipse.jetty.security.DefaultUserIdentity(
                  s2,
                  principal,
                  roles
                )
              )
              sr.setAuthentication(authentication)
            }
            println("authenticated: %s\r\n%s".format(sr.getUserPrincipal()))
          }
          case sr:net.liftweb.http.provider.servlet.HTTPRequestServlet => {
            val authSubjectAttr = "javax.security.auth.subject" 
            println("generic request")
            val session:net.liftweb.http.provider.servlet.HTTPServletSession = sr.session
            var subject:Subject = session.attribute(authSubjectAttr).asInstanceOf[Subject]
            if (subject == null){
              subject = new Subject()
            }
            val principals = subject.getPrincipals()
            principals.add(new MeTLPrincipal(username))
            principals.add(new MeTLRolePrincipal("USER"))
            session.setAttribute(authSubjectAttr,subject)
            //val req:javax.servlet.http.HttpServletRequest = sr.req
            //req.login(username,"no password")
            println("authenticated: %s\r\n%s".format(subject,sr.req.getUserPrincipal()))
          }
          case other => {
            warn("type of containerRequest not of the type expected: %s".format(other))
          }
        }
      } catch {
        case e:Throwable => {
          error("exception while setting userPrincipal",e)
        }
      }
    }
    ifConfiguredFromGroup(authenticationNodes,Map(
      "saml" -> {(n:NodeSeq) => {
        def setupUserWithSamlState(la: LiftAuthStateData): Unit = {
          trace("saml step 1: %s".format(la))
          if ( la.authenticated ) {
            trace("saml step 2: authed")
            setUserPrincipal(la.username)
            Globals.currentUser(la.username)
            trace("saml step 3: set user")
            var existingGroups:List[Tuple2[String,String]] = Nil
            if (Globals.groupsProviders != null){
            trace("saml step 4: groupsProviders not null")
              Globals.groupsProviders.foreach(gp => {
                trace("saml step 5: groupProvider: %s".format(gp))
                val newGroups = gp.getGroupsFor(la.username)
                if (newGroups != null){
                  trace("saml step 6: newGroups: %s".format(newGroups))
                  existingGroups = existingGroups ::: newGroups
                }
              })
            }
            trace("saml step 7: allGroups %s".format(existingGroups))
            Globals.casState.set(new LiftAuthStateData(true,la.username,(la.eligibleGroups.toList ::: existingGroups).distinct,la.informationGroups))
          trace("saml step 8: completed %s".format(Globals.casState.is))
          }
        }
        val samlConf = getSAMLconfiguration(n)
        debug("samlConf: %s".format(samlConf))
        LiftAuthAuthentication.attachAuthenticator(
          new SAMLAuthenticationSystem(
            new SAMLAuthenticator(
              alreadyLoggedIn = () => Globals.casState.authenticated,
              onSuccess = setupUserWithSamlState _,
              samlConfiguration = samlConf
            )
          )
        )
      }},
      "mock" -> {(n:NodeSeq) => {
        val template = (n \\ "template" \ "_")
        val legalCharacters = (Range.inclusive('a','z').toList ::: Range.inclusive('A','Z').toList ::: Range.inclusive('0','9').toList) ::: List('.','_','-')
        LiftAuthAuthentication.attachAuthenticator(
          new FormAuthenticationSystem(
            new Gen2FormAuthenticator(
              loginPage = template,
              formSelector = (n \ "@formSelector").text,
              usernameSelector = (n \ "@usernameSelector").text,
              passwordSelector = (n \ "@passwordSelector").text,
              verifyCredentials = (cred:Tuple2[String,String]) => {
                val username = cred._1
                val _password = cred._2
                if (username.exists(c => !legalCharacters.contains(c))){
                  throw new Exception("username contains illegal characters.  Please use only alphanumeric characters")
                } else {
                  LiftAuthStateData(true,username.trim.toLowerCase,Nil,Nil)
                }
              },
              alreadyLoggedIn = () => Globals.casState.authenticated,
              onSuccess = (la:LiftAuthStateData) => {
                setUserPrincipal(la.username)
                Globals.currentUser(la.username)
                var existingGroups:List[Tuple2[String,String]] = Nil
                if (Globals.groupsProviders != null){
                  Globals.groupsProviders.foreach(gp => {
                    val newGroups = gp.getGroupsFor(la.username)
                    if (newGroups != null){
                      existingGroups = existingGroups ::: newGroups
                    }
                  })
                }
                Globals.casState.set(new LiftAuthStateData(true,la.username,(la.eligibleGroups.toList ::: existingGroups).distinct,la.informationGroups))
              }
            )
          )
        )
      }},
      "google" -> {(n:NodeSeq) => {
        LiftAuthAuthentication.attachAuthenticator(
          new OpenIdConnectAuthenticationSystem(
            googleClientId = (n \\ "@clientId").text,
            googleAppDomainName = (n \\ "@appDomain").headOption.map(_.text),
            alreadyLoggedIn = () => Globals.casState.authenticated,
            onSuccess = (la:LiftAuthStateData) => {
              if ( la.authenticated ) {
                setUserPrincipal(la.username)
                Globals.currentUser(la.username)
                var existingGroups:List[Tuple2[String,String]] = Nil
                if (Globals.groupsProviders != null){
                  Globals.groupsProviders.foreach(gp => {
                    val newGroups = gp.getGroupsFor(la.username)
                    if (newGroups != null){
                      existingGroups = existingGroups ::: newGroups
                    }
                  })
                }
                Globals.casState.set(new LiftAuthStateData(true,la.username,(la.eligibleGroups.toList ::: existingGroups).distinct,la.informationGroups))
              }
            }
          )
        )
      }},
      "cas" -> {(n:NodeSeq) => {
        LiftAuthAuthentication.attachAuthenticator(
          new CASAuthenticationSystem(
            new CASAuthenticator(
              (n \\ "@realm").text,
              (n \\ "@baseUrl").text,
              None,
              alreadyLoggedIn = () => Globals.casState.authenticated,
              onSuccess = (la:LiftAuthStateData) => {
                if ( la.authenticated ) {
                  setUserPrincipal(la.username)
                  Globals.currentUser(la.username)
                  var existingGroups:List[Tuple2[String,String]] = Nil
                  if (Globals.groupsProviders != null){
                    Globals.groupsProviders.foreach(gp => {
                      val newGroups = gp.getGroupsFor(la.username)
                      if (newGroups != null){
                        existingGroups = existingGroups ::: newGroups
                      }
                    })
                  }
                  Globals.casState.set(new LiftAuthStateData(true,la.username,(la.eligibleGroups.toList ::: existingGroups).distinct,la.informationGroups))
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
    MeTL2015ServerConfiguration.initialize
    LocalH2ServerConfiguration.initialize
    ServerConfiguration.setServerConfMutator(sc => new ResourceCachingAdaptor(sc))
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
    configs = Map(servers.map(c => (c.name,(c,getRoomProvider(c.name)))):_*)
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
    LiftRules.statelessDispatchTable.prepend(MeTLRestHelper)
    LiftRules.dispatch.append(MeTLStatefulRestHelper)
    LiftRules.statelessDispatchTable.prepend(WebMeTLRestHelper)
    LiftRules.dispatch.append(WebMeTLStatefulRestHelper)
    setupAuthorizersFromFile(Globals.configurationFileLocation)
    setupAuthenticatorsFromFile(Globals.configurationFileLocation)
    setupClientConfigFromFile(Globals.configurationFileLocation)
    setupServersFromFile(Globals.configurationFileLocation)
    configs.values.foreach(c => LiftRules.unloadHooks.append(c._1.shutdown _))
    configs.values.foreach(c => {
      getRoom("global",c._1.name,GlobalRoom(c._1.name))
      debug("%s is now ready for use (%s)".format(c._1.name,c._1.isReady))
    })
    setupStackAdaptorFromFile(Globals.configurationFileLocation)
    setupClientAdaptorsFromFile(Globals.configurationFileLocation)
    info(configs)
  }
  def getRoom(jid:String,configName:String):MeTLRoom = getRoom(jid,configName,RoomMetaDataUtils.fromJid(jid))
  def listRooms(configName:String):List[String] = configs(configName)._2.list
  def getRoom(jid:String,configName:String,roomMetaData:RoomMetaData):MeTLRoom = {
    configs(configName)._2.get(jid,roomMetaData)
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
  override def updateConversation(jid:String,conversation:Conversation):Conversation = Conversation.empty
  override def getImage(jid:String,identity:String) = MeTLImage.empty
  override def getResource(url:String) = Array.empty[Byte]
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = ""
  override def getResource(jid:String,identifier:String):Array[Byte] = Array.empty[Byte]
  override def insertResource(jid:String,data:Array[Byte]):String = ""
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = ""
}

class ManagedCache[A <: Object,B <: Object](name:String,creationFunc:A=>B,cacheSizeInMB:Int = 100) {
  import net.sf.ehcache.{Cache,CacheManager,Element,Status,Ehcache}
  import net.sf.ehcache.loader.{CacheLoader}
  import net.sf.ehcache.config.{CacheConfiguration,MemoryUnit}
  import net.sf.ehcache.store.{MemoryStoreEvictionPolicy}
  import java.util.Collection
  import scala.collection.JavaConversions._
  protected val cm = CacheManager.getInstance()
  val cacheName = "%s_%s".format(name,nextFuncName)
  val cacheConfiguration = new CacheConfiguration().name(cacheName).maxBytesLocalHeap(cacheSizeInMB,MemoryUnit.MEGABYTES).eternal(false).memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LRU).diskPersistent(false).logging(true)
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
        println("%s MISS %s".format(cacheName,key))
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
  def startup = cache.initialise
  def shutdown = cache.dispose()
}

class ResourceCachingAdaptor(sc:ServerConfiguration) extends PassThroughAdaptor(sc){
  val imageCache = new ManagedCache[String,MeTLImage]("imageByIdentiity",((i:String)) => super.getImage(i))
  val imageWithJidCache = new ManagedCache[Tuple2[String,String],MeTLImage]("imageByIdentityAndJid",(ji) => super.getImage(ji._1,ji._2))
  val resourceCache = new ManagedCache[String,Array[Byte]]("resourceByIdentity",(i:String) => super.getResource(i))
  val resourceWithJidCache = new ManagedCache[Tuple2[String,String],Array[Byte]]("resourceByIdentityAndJid",(ji) => super.getResource(ji._1,ji._2))
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
