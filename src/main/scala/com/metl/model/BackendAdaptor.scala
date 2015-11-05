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
import com.metl.formAuthenticator._
//import com.metl.auth._
import com.metl.h2._

import net.liftweb.util.Props
import com.mongodb._
import net.liftweb.mongodb._

import scala.xml._

class Gen2FormAuthenticator(loginPage:NodeSeq, formSelector:String, usernameSelector:String, passwordSelector:String, verifyCredentials:Tuple2[String,String]=>LiftAuthStateData, alreadyLoggedIn:() => Boolean,onSuccess:(LiftAuthStateData) => Unit) extends FormAuthenticator(loginPage,formSelector,usernameSelector,passwordSelector,verifyCredentials,alreadyLoggedIn,onSuccess) {
  println("Gen2FormAuthenticator: %s\r\n%s %s %s %s".format(loginPage,formSelector,usernameSelector,passwordSelector,verifyCredentials))
  override def constructResponseWithMessages(req:Req,additionalMessages:List[String] = List.empty[String]) = Stopwatch.time("FormAuthenticator.constructReq",() => {
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
      println("constructed: %s".format(loginPageNode))
      LiftRules.convertResponse(
        (loginPageNode,200),
        S.getHeaders(LiftRules.defaultHeaders((loginPageNode,req))),
        S.responseCookies,
        req
      )
  })
}

object MeTLXConfiguration extends PropertyReader {
  protected var configs:Map[String,Tuple2[ServerConfiguration,RoomProvider]] = Map.empty[String,Tuple2[ServerConfiguration,RoomProvider]]
  var clientConfig:Option[ClientConfiguration] = None
  var configurationProvider:Option[ConfigurationProvider] = None
  val updateGlobalFunc = (c:Conversation) => {
    println("serverSide updateGlobalFunc: %s".format(c))
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
            println("configuring: %s".format(element))
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
        for (
          lp <- (n \ "@localPort").headOption.map(_.text.toInt);
          ls <- (n \ "@localScheme").headOption.map(_.text);
          rbh <- (n \ "@remoteBackendHost").headOption.map(_.text);
          rbp <- (n \ "@remoteBackendPort").headOption.map(_.text.toInt)
        ) yield {
          configurationProvider = Some(new StableKeyConfigurationProvider(ls,lp,rbh,rbp))
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
        xh <- (n \ "xmppHost").headOption.map(_.text);
        xp <- (n \ "xmppPort").headOption.map(_.text.toInt);
        xd <- (n \ "xmppDomain").headOption.map(_.text);
        xuser = "";
        xpass = "";
        csu <- (n \ "conversationSearchUrl").headOption.map(_.text);
        wau <- (n \ "webAuthenticationUrl").headOption.map(_.text);
        tu <- (n \ "thumbnailUrl").headOption.map(_.text);
        ru <- (n \ "resourceUrl").headOption.map(_.text);
        hu <- (n \ "historyUrl").headOption.map(_.text);
        httpU = "";
        httpP = "";
        sd <- (n \ "structureDirectory").headOption.map(_.text);
        rd <- (n \ "resourceDirectory").headOption.map(_.text);
        up <- (n \ "uploadPath").headOption.map(_.text);
        pkg <- (n \ "primaryKeyGenerator").headOption.map(_.text);
        ck <- (n \ "cryptoKey").headOption.map(_.text);
        civ <- (n \ "cryptoIV").headOption.map(_.text);
        iu <- (n \ "imageUrl").headOption.map(_.text)
      ) yield {
        ClientConfiguration(xh,xp,xd,xuser,xpass,csu,wau,tu,ru,hu,httpU,httpP,sd,rd,up,pkg,ck,civ,iu)
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
  def setupAuthenticatorsFromFile(filePath:String) = {
    val propFile = XML.load(filePath)
    val authenticationNodes = propFile \\ "serverConfiguration" \\ "authentication"
    ifConfiguredFromGroup(authenticationNodes,Map(
      "saml" -> {(n:NodeSeq) => {
        def setupUserWithSamlState(la: LiftAuthStateData): Unit = {
          println("saml step 1: %s".format(la))
          if ( la.authenticated ) {
          println("saml step 2: authed")
            Globals.currentUser(la.username)
          println("saml step 3: set user")
            var existingGroups:List[Tuple2[String,String]] = Nil
            if (Globals.groupsProviders != null){
            println("saml step 4: groupsProviders not null")
              Globals.groupsProviders.foreach(gp => {
                println("saml step 5: groupProvider: %s".format(gp))
                val newGroups = gp.getGroupsFor(la.username)
                if (newGroups != null){
                  println("saml step 6: newGroups: %s".format(newGroups))
                  existingGroups = existingGroups ::: newGroups
                }
              })
            }
            println("saml step 7: allGroups %s".format(existingGroups))
            Globals.casState.set(new LiftAuthStateData(true,la.username,(la.eligibleGroups.toList ::: existingGroups).distinct,la.informationGroups))
          println("saml step 8: completed %s".format(Globals.casState.is))
          }
        }
        val samlConf = getSAMLconfiguration(n)
        println("samlConf: %s".format(samlConf))
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
                  LiftAuthStateData(true,username,Nil,Nil)
                }
              },
              alreadyLoggedIn = () => Globals.casState.authenticated,
              onSuccess = (la:LiftAuthStateData) => {
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
    //EmbeddedXmppServer.initialize
    MeTL2011ServerConfiguration.initialize
    MeTL2015ServerConfiguration.initialize
    LocalH2ServerConfiguration.initialize
    ServerConfiguration.loadServerConfigsFromFile(
      path = filePath,
      onConversationDetailsUpdated = updateGlobalFunc,
      messageBusCredentailsFunc = () => {
        (for (
          cc <- configurationProvider;
          creds <- cc.getPasswords("metlxMessageBus_"+new java.util.Date().getTime.toString)
        ) yield {
          println("vending msgBusCreds: %s".format(creds))
          (creds._1,creds._2)
        }).getOrElse(("",""))
      },
      conversationListenerCredentialsFunc = () => {
        (for (
          cc <- configurationProvider;
          creds <- cc.getPasswords("metlxConversationListener_"+new java.util.Date().getTime.toString)
        ) yield {
          println("vending convCreds: %s".format(creds))
          (creds._1,creds._2)
        }).getOrElse(("",""))
      },
      httpCredentialsFunc = () => {
        (for (
          cc <- configurationProvider;
          creds <- cc.getPasswords("metlxHttp_"+new java.util.Date().getTime.toString)
        ) yield {
          println("vending httpCreds: %s".format(creds))
          (creds._3,creds._4)
        }).getOrElse(("",""))
      }
    )
    val servers = ServerConfiguration.getServerConfigurations
    configs = Map(servers.map(c => (c.name,(c,getRoomProvider(c.name)))):_*)
  }
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
      println("%s is now ready for use (%s)".format(c._1.name,c._1.isReady))
    })
    setupStackAdaptorFromFile(Globals.configurationFileLocation)
    println(configs)
  }
  def getRoom(jid:String,configName:String):MeTLRoom = getRoom(jid,configName,RoomMetaDataUtils.fromJid(jid))
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
}
