package com.metl.model

import _root_.net.liftweb._
import util._
import Helpers._
import common.{Logger=>LiftLogger,_}
import http._
import provider.servlet._
import com.metl.utils._

// for EmbeddedXmppServer
import org.apache.vysper.mina.TCPEndpoint
import org.apache.vysper.storage.StorageProviderRegistry
import org.apache.vysper.storage.inmemory.MemoryStorageProviderRegistry
import org.apache.vysper.storage.OpenStorageProviderRegistry
import org.apache.vysper.xmpp.addressing.{Entity,EntityImpl}
import org.apache.vysper.xmpp.authorization.AccountManagement
import org.apache.vysper.xmpp.modules.extension.xep0054_vcardtemp.VcardTempModule
import org.apache.vysper.xmpp.modules.extension.xep0092_software_version.SoftwareVersionModule
import org.apache.vysper.xmpp.modules.extension.xep0119_xmppping.XmppPingModule
import org.apache.vysper.xmpp.modules.extension.xep0202_entity_time.EntityTimeModule
import org.apache.vysper.xmpp.modules.extension.xep0077_inbandreg.InBandRegistrationModule
import org.apache.vysper.xmpp.server.XMPPServer
import org.apache.vysper.xmpp.authorization.UserAuthorization 
import org.apache.vysper.xmpp.modules.roster.persistence._
// for MeTLMucModule
import java.util.{ArrayList => JavaArrayList,List => JavaList,Collection => JavaCollection,Arrays => JavaArrays,Set => JavaSet,Iterator => JavaIterator}
import org.apache.vysper.xmpp.addressing.{Entity,EntityFormatException,EntityImpl,EntityUtils}
import org.apache.vysper.xmpp.modules.DefaultDiscoAwareModule
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.MUCModule
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.handler.{MUCIqAdminHandler,MUCMessageHandler,MUCPresenceHandler}
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.storage.{OccupantStorageProvider,RoomStorageProvider}
import org.apache.vysper.xmpp.modules.servicediscovery.management.{ComponentInfoRequestListener,InfoElement,InfoRequest,Item,ItemRequestListener,ServiceDiscoveryRequestException}
import org.apache.vysper.xmpp.protocol.{NamespaceURIs,StanzaProcessor}
import org.apache.vysper.xmpp.server.components.{Component,ComponentStanzaProcessor}
import org.apache.vysper.xmpp.stanza.{IQStanzaType,StanzaBuilder}
import org.slf4j.{Logger,LoggerFactory}

// for MeTLMUCMessageHandler
import org.apache.vysper.xml.fragment.{Attribute,XMLElement,XMLSemanticError,XMLText,XMLFragment}
import org.apache.vysper.xmpp.delivery.failure.{DeliveryException,IgnoreFailureStrategy}
import org.apache.vysper.xmpp.modules.core.base.handler.DefaultMessageHandler
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.MUCStanzaBuilder
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.dataforms.VoiceRequestForm
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.model.{Conference,Occupant,Role,Room,RoomType}
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.stanzas.{MucUserItem,X}
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.handler.MUCHandlerHelper
import org.apache.vysper.xmpp.server.{ServerRuntimeContext,SessionContext}
import org.apache.vysper.xmpp.stanza.{MessageStanza,MessageStanzaType,Stanza,StanzaBuilder,StanzaErrorCondition,StanzaErrorType}

// for MeTLMUCPresenceHandler
import org.apache.vysper.xmpp.modules.core.base.handler.DefaultPresenceHandler
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.model.{Affiliation,Affiliations}
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.stanzas.{History,Status}
import org.apache.vysper.xmpp.modules.extension.xep0045_muc.stanzas.Status.StatusCode
import org.apache.vysper.xmpp.modules.extension.xep0133_service_administration.ServerAdministrationService
import org.apache.vysper.xmpp.stanza.{PresenceStanza,PresenceStanzaType}

// for VysperXMLUtils
import scala.xml._
import org.apache.vysper.xml.fragment.{Renderer => vXmlRenderer}

// for MeTL integration
import com.metl.data.{Group=>MeTLGroup,_}
import com.metl.metl2011._

class VysperClientXmlSerializer(config:ServerConfiguration) extends GenericXmlSerializer(config) with LiftLogger {
  override def getValueOfNode(content:NodeSeq,name:String):String = {
    trace("getValueOfNode: %s %s".format(content,name))
    (content \\ name).headOption.map(_.text).getOrElse("")
  }
  override def toMeTLData(in:NodeSeq):MeTLData = {
    try {
    trace("toMeTLData started: %s".format(in))
    val result = super.toMeTLData(in)
    trace("toMeTLData: %s => %s".format(in,result))
    result
    } catch {
      case e:Exception => {
        error("EXCEPTION in super.toMeTLData",e)
        throw e
      }
    }
  }
  override def metlXmlToXml(rootName:String,additionalNodes:Seq[Node],wrapWithMessage:Boolean = false,additionalAttributes:List[(String,String)] = List.empty[(String,String)]) = Stopwatch.time("GenericXmlSerializer.metlXmlToXml", {
    /*
    val messageAttrs = List(("xmlns","jabber:client"),("to","nobody@nowhere.nothing"),("from","metl@local.temp"),("type","groupchat")).foldLeft(scala.xml.Null.asInstanceOf[scala.xml.MetaData])((acc,item) => {
      item match {
        case (k:String,v:String) => new UnprefixedAttribute(k,v,acc)
        case _ => acc
      }
    })
  */
    val attrs = (additionalAttributes ::: List(("xmlns","monash:metl"))).foldLeft(scala.xml.Null.asInstanceOf[scala.xml.MetaData])((acc,item) => {
      item match {
        case (k:String,v:String) => new UnprefixedAttribute(k,v,acc)
        case _ => acc
      }
    })
/*
    wrapWithMessage match {
      case true => {
        new Elem(null, "message", messageAttrs, TopScope, false, new Elem(null, rootName, attrs, TopScope, false, additionalNodes: _*))
      }
      
      case _ => */ new Elem(null, rootName, attrs, TopScope, false, additionalNodes:_*)
    //}
  })
}

class EmbeddedXmppServerRoomAdaptor(serverRuntimeContext:ServerRuntimeContext,conference:Conference) extends LiftLogger {
  val domainString = "local.temp"
  val conferenceString = "conference.%s".format(domainString)
  lazy val config = ServerConfiguration.default
  lazy val configName = config.name
  val serializer = new VysperClientXmlSerializer(config)
  val converter = new VysperXMLUtils
  protected val xmppMessageDeliveryStrategy = new IgnoreFailureStrategy()
  def relayMessageToMeTLRoom(message:Stanza):Unit = {
    trace("relaying message to room: %s".format(message))
    val to:Entity = message.getTo()
    val location:String = to.getNode()
    val payloads:JavaList[XMLFragment] = message.getInnerFragments()
    debug("room chosen: %s %s".format(location,configName))
    MeTLXConfiguration.getRoom(location,configName) match {
      case r:XmppBridgingHistoryCachingRoom => {
        JavaListUtils.foreach(payloads,(payload:XMLFragment) => {
          val nodes:NodeSeq = <message>{converter.toScala(payload)}</message>
          debug("nodes: %s".format(nodes))
          val md = serializer.toMeTLData(nodes) 
          trace("metlData: %s".format(md))
          md match {
            case m:MeTLStanza => {
              trace("sending metlStanza from bridge: %s %s".format(r,m))
              r.sendMessageFromBridge(m)
            }
            case unknownMessage => {
              warn("unknownMessage received: %s".format(unknownMessage))
            }
          }
        })
      }
      case otherRoom => {
        error("room found but not an XmppBridingRoom: %s".format(otherRoom))
      }
    }
  }
  def relayMessageToXmppMuc(location:String,message:MeTLStanza):Unit = serializer.fromMeTLData(message) match {
    case n:Node => {
      trace("relaying message to xmppMuc: %s\r\n%s\r\n%s".format(location,message.toString,n.toString))
      relayMessageNodeToXmppMuc(location,n)
    }
    case _ => {}
  }
  def relayMessageNodeToXmppMuc(location:String,message:Node):Unit = {
    val roomJid:Entity = EntityImpl.parse("%s@%s".format(location,conferenceString))
    val room:Room = conference.findRoom(roomJid)
    if (room != null){
      val from:Entity = room.getJID()
      val messageFunc = (sb:StanzaBuilder) => converter.toVysper(message) match {
        case t:XMLText => sb.addText(t.getText)
        case e:XMLElement => sb.addPreparedElement(e)
        case _ => sb
      }
      JavaListUtils.foreach(room.getOccupants(),(occupant:Occupant) => {
        val to:Entity = occupant.getJid()
        val request:Stanza = messageFunc(StanzaBuilder.createMessageStanza(from, to, null, null)).build()
        try {
          serverRuntimeContext.getStanzaRelay().relay(to, request, xmppMessageDeliveryStrategy)
        } catch {
          case e:DeliveryException => {
            warn("presence relaying failed %s".format(e))
          }
          case other:Throwable => throw other
        }
      })
    }
  }
}

class MeTLXAccountManagement extends AccountManagement with LiftLogger {
  override def addUser(entity:Entity,password:String):Unit = addUser(entity.getNode,password)
  def addUser(username:String,password:String):Unit = {
    debug("adding xmpp user: %s %s".format(username,password))
    MeTLXConfiguration.configurationProvider.map(cp => cp.keys.update(username,password))
  }
  override def changePassword(entity:Entity,password:String):Unit = changePassword(entity.getNode,password)
  def changePassword(username:String,password:String):Unit = {
    debug("changing xmpp password for: %s %s".format(username,password))
    MeTLXConfiguration.configurationProvider.map(cp => cp.keys.update(username,password))
  }
  override def verifyAccountExists(entity:Entity):Boolean = verifyAccountExists(entity.getNode)
  def verifyAccountExists(username:String):Boolean = {
    val result = MeTLXConfiguration.configurationProvider.map(cp => cp.keys.get(username).map(_ => true).getOrElse(false)).getOrElse(false)
    debug("checking xmpp existence of: %s %s".format(username,result))
    result
  }
}
class MeTLXAuthentication extends UserAuthorization with LiftLogger {
  override def verifyCredentials(jid:Entity,passwordCleartext:String,credentials:Object):Boolean = {
    debug("jid: %s\r\nnode: %s".format(jid,jid.getNode()))
    verifyCredentials(jid.getNode(),passwordCleartext,credentials)
  }
  override def verifyCredentials(username:String,passwordCleartext:String,credentials:Object):Boolean = {
    if (username.contains("@")){
      verifyCredentials(EntityImpl.parse(username),passwordCleartext,credentials)
    } else {
      MeTLXConfiguration.configurationProvider.map(cp => {
        val result = cp.checkPassword(username,passwordCleartext)
        debug("checked credentials: %s => %s".format(username,/*passwordCleartext,*/result))
        result
      }).getOrElse(false)
    }
  }
}

class EmbeddedXmppServer(val domain:String,keystorePath:String,keystorePassword:String) extends LiftLogger {
  protected var privateServer:Box[XMPPServer] = Empty
  protected var mucModule:Box[MeTLMucModule] = Empty
  protected var roomAdaptor:Box[EmbeddedXmppServerRoomAdaptor] = Empty

  def shutdown = {
    privateServer.map(p => {
      p.stop()
    })
  }
  def initialize = {
    info("embedded xmpp server start handler")
    //val providerRegistry = new MemoryStorageProviderRegistry()
    val providerRegistry = new OpenStorageProviderRegistry()

    val auther = new MeTLXAuthentication()
    providerRegistry.add(auther)
    providerRegistry.add(new MemoryRosterManager())
    providerRegistry.add(new MeTLXAccountManagement()) // I'm hoping this isn't necessary, and it doesn't appear to be.
    privateServer = Full(new XMPPServer(domain))
    privateServer.map(p => {
      p.addEndpoint(new TCPEndpoint())
      p.setStorageProviderRegistry(providerRegistry)
      //p.setTLSCertificateInfo(new java.io.File("/stackable/analyticalmetlx/config/metl.jks"),"helpme")
      p.setTLSCertificateInfo(new java.io.File(keystorePath),keystorePassword)
      try {
        p.start()

        def describeSslParams(params:javax.net.ssl.SSLParameters):String = {
          "ciphers: %s\r\nprotocols: %s".format(params.getCipherSuites().toList.mkString(", "),params.getProtocols().toList.mkString(", "))
        }
        val supportedParams = describeSslParams(p.getServerRuntimeContext().getSslContext().getSupportedSSLParameters())
        val defaultParams = describeSslParams(p.getServerRuntimeContext().getSslContext().getDefaultSSLParameters())
        info("initializing Vysper SSL\r\nsupportedParams: %s\r\ndefaultParams: %s".format(supportedParams,defaultParams))

        info("embedded xmpp server started")
        val metlMuc = new MeTLMucModule()
        p.addModule(new SoftwareVersionModule())
        p.addModule(new EntityTimeModule())
        p.addModule(new VcardTempModule())
        p.addModule(new XmppPingModule())
        p.addModule(new InBandRegistrationModule())
        p.addModule(metlMuc)
        mucModule = Full(metlMuc)
        roomAdaptor = metlMuc.getRoomAdaptor
        info("embedded xmpp default modules loaded")
      } catch {
        case e:Throwable => {
          throw e
        }
      }
    })
  }
  def relayMessageToRoom(message:Stanza):Unit = roomAdaptor.map(ra => ra.relayMessageToMeTLRoom(message))
  def relayMessageToXmppMuc(location:String,message:MeTLStanza):Unit = roomAdaptor.map(ra => ra.relayMessageToXmppMuc(location,message))
}

class MeTLMucModule(subdomain:String = "conference",conference:Conference = new Conference("Conference")) extends DefaultDiscoAwareModule with Component with ComponentInfoRequestListener with ItemRequestListener with LiftLogger {
  protected var fullDomain:Entity = null
  protected var serverRuntimeContext:ServerRuntimeContext = null
  protected var stanzaProcessor:ComponentStanzaProcessor = null

  protected var roomAdaptor:Box[EmbeddedXmppServerRoomAdaptor] = Empty

  override def initialize(serverRuntimeContext:ServerRuntimeContext):Unit = {
    super.initialize(serverRuntimeContext)
    this.serverRuntimeContext = serverRuntimeContext
    fullDomain = EntityUtils.createComponentDomain(subdomain, serverRuntimeContext)
    val processor:ComponentStanzaProcessor = new ComponentStanzaProcessor(serverRuntimeContext)
    processor.addHandler(new MeTLMUCPresenceHandler(conference,this));
    processor.addHandler(new MeTLMUCMessageHandler(conference, fullDomain,this));
    processor.addHandler(new MUCIqAdminHandler(conference));
    stanzaProcessor = processor;
    val roomStorageProvider:RoomStorageProvider = serverRuntimeContext.getStorageProvider(classOf[RoomStorageProvider]).asInstanceOf[RoomStorageProvider]
    val occupantStorageProvider:OccupantStorageProvider = serverRuntimeContext.getStorageProvider(classOf[OccupantStorageProvider]).asInstanceOf[OccupantStorageProvider]
    if (roomStorageProvider == null) {
      //logger.warn("No room storage provider found, using the default (in memory)");
      debug("No room storage provider found, using the default (in memory)");
    } else {
      conference.setRoomStorageProvider(roomStorageProvider);
    }
    if (occupantStorageProvider == null) {
      //logger.warn("No occupant storage provider found, using the default (in memory)");
      debug("No occupant storage provider found, using the default (in memory)");
    } else {
      conference.setOccupantStorageProvider(occupantStorageProvider);
    }
    this.conference.initialize();
    roomAdaptor = Full(new EmbeddedXmppServerRoomAdaptor(this.serverRuntimeContext,this.conference))
  }
  override def getName:String = "XEP-0045 Multi-user chat"
  override def getVersion:String = "1.24"
  def getRoomAdaptor:Box[EmbeddedXmppServerRoomAdaptor] = roomAdaptor

  override def addItemRequestListeners(itemRequestListeners:JavaList[ItemRequestListener]):Unit = {
    itemRequestListeners.add(this)
  }
  def getComponentInfosFor(request:InfoRequest):JavaList[InfoElement] = {
    //throws ServiceDiscoveryRequestException
    if (!fullDomain.getDomain().equals(request.getTo().getDomain())){
      null
    } else {
      if (request.getTo().getNode() == null) {
        conference.getServerInfosFor(request)
      } else {
        // might be an items request on a room
        val room:Room = conference.findRoom(request.getTo().getBareJID())
        if (room == null)
          null
        else {
          if (request.getTo().getResource() != null) {
            // request for an occupant
            val occupant:Occupant = room.findOccupantByNick(request.getTo().getResource())
            // request for occupant, relay
            if (occupant != null) {
              relayDiscoStanza(occupant.getJid(), request, NamespaceURIs.XEP0030_SERVICE_DISCOVERY_INFO);
            }
            null;
          } else {
            room.getInfosFor(request);
          }
        }
      }
    }
  }
  override def addComponentInfoRequestListeners(componentInfoRequestListeners:JavaList[ComponentInfoRequestListener]):Unit = {
    componentInfoRequestListeners.add(this)
  }

  def getItemsFor(request:InfoRequest):JavaList[Item] = {
    //throws ServiceDiscoveryRequestException
    val to:Entity = request.getTo()
    if (to.getNode() == null) {
      if (fullDomain.equals(to)) {
        val conferenceItems:JavaList[Item] = conference.getItemsFor(request)
        conferenceItems
      } else if (serverRuntimeContext.getServerEnitity().equals(to)) {
        val componentItem:JavaList[Item] = new JavaArrayList[Item]()
        componentItem.add(new Item(fullDomain))
        componentItem
      } else {
        null
      }
    } else if (fullDomain.getDomain().equals(to.getDomain())) {
      val room:Room = conference.findRoom(to.getBareJID())
      if (room != null) {
        if (to.getResource() != null) {
          val occupant:Occupant = room.findOccupantByNick(to.getResource())
          if (occupant != null) {
            relayDiscoStanza(occupant.getJid(), request, NamespaceURIs.XEP0030_SERVICE_DISCOVERY_ITEMS)
          }
          null
        } else {
          room.getItemsFor(request)
        }
      } else {
        null
      }
    } else {
      null
    }
  }
  protected def relayDiscoStanza(receiver:Entity, request:InfoRequest, ns:String):Unit = {
    val builder:StanzaBuilder = StanzaBuilder.createIQStanza(request.getFrom(), receiver, IQStanzaType.GET, request.getID())
    builder.startInnerElement("query", ns)
    if (request.getNode() != null) {
      builder.addAttribute("node", request.getNode())
    }
    try {
      serverRuntimeContext.getStanzaRelay().relay(receiver, builder.build(), new IgnoreFailureStrategy())
    } catch {
      case e:DeliveryException => {}
      case other:Throwable => throw other
    }
  }
  def getSubdomain:String = subdomain
  def getStanzaProcessor:StanzaProcessor = stanzaProcessor
}

class MeTLMUCMessageHandler(conference:Conference,moduleDomain:Entity,mucModule:MeTLMucModule,useXmppHistory:Boolean = false) extends DefaultMessageHandler with LiftLogger {
  override protected def verifyNamespace(stanza:Stanza):Boolean = true
  private def createMessageErrorStanza(from:Entity,to:Entity,id:String, typeName:StanzaErrorType, errorCondition:StanzaErrorCondition, stanza:Stanza):Stanza = {
    MUCHandlerHelper.createErrorStanza("message", NamespaceURIs.JABBER_CLIENT, from, to, id, typeName.value(), errorCondition.value(), stanza.getInnerElements())
  }
  override protected def executeMessageLogic(stanza:MessageStanza, serverRuntimeContext:ServerRuntimeContext, sessionContext:SessionContext) = {
    trace("Received message for MUC")
    val from:Entity = stanza.getFrom()
    val roomWithNickJid:Entity = stanza.getTo()
    val roomJid:Entity = roomWithNickJid.getBareJID()
    val typeName:MessageStanzaType = stanza.getMessageType()

    if (typeName != null && typeName == MessageStanzaType.GROUPCHAT) {
      // groupchat, message to a room
      // must not have a nick
      if (roomWithNickJid.getResource() != null) {
        createMessageErrorStanza(roomJid, from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.BAD_REQUEST, stanza)
      } else {
        trace("Received groupchat message to %s".format(roomJid))
        val room:Room = conference.findRoom(roomJid)
        if (room != null) {
          val sendingOccupant:Occupant = room.findOccupantByJID(from)
          // sender must be participant in room
          if (sendingOccupant != null) {
            val roomAndSendingNick:Entity = new EntityImpl(room.getJID(), sendingOccupant.getNick())
            if (sendingOccupant.hasVoice()) {
              // relay message to all occupants in room
              if (stanza.getSubjects() != null && !stanza.getSubjects().isEmpty()) {
                try {
                  // subject message
                  if (!room.isRoomType(RoomType.OpenSubject) && !sendingOccupant.isModerator()) {
                    // room only allows moderators to change the subject, and sender is not a moderator
                    createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.AUTH, StanzaErrorCondition.FORBIDDEN, stanza)
                  } else {
                    null
                  }
                } catch {
                  case e:XMLSemanticError => {
                    // not a subject message, ignore exception
                    null
                  }
                }
              } else {
                /* //commenting out the relay to all occupants, because in our app that should happpen AFTER it comes back from the MeTL system, and not before.  This was resulting in double-messaging.
                trace("Relaying message to all room occupants")
                JavaListUtils.foreach(room.getOccupants(),(occupant:Occupant) => {
                  trace("Relaying message to %s".format(occupant))
                  val replaceAttributes:JavaList[Attribute] = new JavaArrayList[Attribute]()
                  replaceAttributes.add(new Attribute("from", roomAndSendingNick.getFullQualifiedName()))
                  replaceAttributes.add(new Attribute("to",occupant.getJid().getFullQualifiedName()))
                  val finalStanza:Stanza = StanzaBuilder.createClone(stanza, true, replaceAttributes).build()
                  relayStanza(occupant.getJid(), finalStanza, serverRuntimeContext)
                })
                */
                mucModule.getRoomAdaptor.map(ra => ra.relayMessageToMeTLRoom(stanza))
                if (useXmppHistory)
                  room.getHistory().append(stanza, sendingOccupant)
                null
              }
            } else {
              warn("sending occupant doesn't have voice: %s".format(stanza))
              createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY,StanzaErrorCondition.FORBIDDEN, stanza)
            }
          } else {
            warn("sending occupant is null - I think that means it's not in the room: %s".format(stanza))
            createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.NOT_ACCEPTABLE, stanza)
          }
        } else {
          warn("room is null: %s".format(stanza))
          createMessageErrorStanza(moduleDomain, from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.ITEM_NOT_FOUND, stanza)
        }
      }
    } else if (typeName == null || typeName == MessageStanzaType.CHAT || typeName == MessageStanzaType.NORMAL) {
      //private message
      debug("Received direct message to %s".format(roomWithNickJid))
      val room:Room = conference.findRoom(roomJid)
      if (room != null) {
        val sendingOccupant:Occupant = room.findOccupantByJID(from)
        // sender must be participant in room
        if (roomWithNickJid.equals(roomJid)) {
          // check x element
          if (stanza.getVerifier().onlySubelementEquals("x", NamespaceURIs.JABBER_X_DATA)) {
            // void requests
            debug("Received voice request for room %s".format(roomJid))
            handleVoiceRequest(from, sendingOccupant, room, stanza, serverRuntimeContext)
            null
          } else if (stanza.getVerifier().onlySubelementEquals("x", NamespaceURIs.XEP0045_MUC_USER)){
            //invites/declines
            handleInvites(stanza, from, sendingOccupant, room, serverRuntimeContext)
          } else {
            //do something here
            null
          }
        } else if (roomWithNickJid.isResourceSet()){
          if (sendingOccupant != null){
            // got resource, private message for occupant
            val receivingOccupant:Occupant = room.findOccupantByNick(roomWithNickJid.getResource())
            // must be sent to an existing occupant in the room
            if (receivingOccupant != null) {
              val roomAndSendingNick:Entity = new EntityImpl(room.getJID(), sendingOccupant.getNick())
              trace("Relaying message to %s".format(receivingOccupant))
              val replaceAttributes:JavaList[Attribute] = new JavaArrayList[Attribute]()
              replaceAttributes.add(new Attribute("from", roomAndSendingNick.getFullQualifiedName()))
              replaceAttributes.add(new Attribute("to", receivingOccupant.getJid().getFullQualifiedName()))
              relayStanza(receivingOccupant.getJid(), StanzaBuilder.createClone(stanza, true, replaceAttributes).build(), serverRuntimeContext)
              null
            } else {
              // TODO correct error?
              createMessageErrorStanza(moduleDomain, from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.ITEM_NOT_FOUND, stanza)
            }
          } else {
            // user must be occupant to send direct message
            createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.NOT_ACCEPTABLE, stanza)
          }
        } else {
          createMessageErrorStanza(moduleDomain, from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.ITEM_NOT_FOUND, stanza)
        }
      } else {
        createMessageErrorStanza(moduleDomain, from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.ITEM_NOT_FOUND, stanza)
      }
    } else {
      null
    }
  }
  protected def handleInvites(stanza:MessageStanza, from:Entity, sendingOccupant:Occupant, room:Room, serverRuntimeContext:ServerRuntimeContext):Stanza = {
    val x:X = X.fromStanza(stanza)
    if (x != null && x.getInvite() != null) {
      if (sendingOccupant != null) {
        // invite, forward modified invite
        try {
          val invite:Stanza = MUCHandlerHelper.createInviteMessageStanza(stanza, room.getPassword());
          relayStanza(invite.getTo(), invite, serverRuntimeContext);
          null
        } catch {
          case e:EntityFormatException => {
            // invalid format of invite element
            createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.JID_MALFORMED, stanza)
          }
          case other:Throwable => throw other
        }
      } else {
        // user must be occupant to send invite
        createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.NOT_ACCEPTABLE, stanza)
      }
    } else if (x != null && x.getDecline() != null) {
      // invite, forward modified decline
      try {
        val decline:Stanza = MUCHandlerHelper.createDeclineMessageStanza(stanza);
        relayStanza(decline.getTo(), decline, serverRuntimeContext);
        null
      } catch {
        case e:EntityFormatException => {
          // invalid format of invite element
          createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.JID_MALFORMED, stanza)
        }
        case other:Throwable => throw other
      }
    } else {
      createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.UNEXPECTED_REQUEST, stanza)
    }
  }
  protected def handleVoiceRequest(from:Entity, sendingOccupant:Occupant, room:Room, stanza:Stanza, serverRuntimeContext:ServerRuntimeContext):Unit = {
    val dataXs:JavaList[XMLElement] = stanza.getInnerElementsNamed("x", NamespaceURIs.JABBER_X_DATA)
    val dataX:XMLElement = dataXs.get(0)
    //check if "request_allow" is set
    val fields:JavaList[XMLElement] = dataX.getInnerElementsNamed("field", NamespaceURIs.JABBER_X_DATA)
    val requestAllow:String = getFieldValue(fields, "muc#request_allow")
    if ("true".equals(requestAllow)) {
      //submitted voice grant, only allowed by moderators
      if (sendingOccupant.isModerator()) {
        val requestNick:String = getFieldValue(fields, "muc#roomnick")
        val requestor:Occupant = room.findOccupantByNick(requestNick)
        requestor.setRole(Role.Participant)
        //nofity remaining users that user got role updated
        val presenceItem:MucUserItem = new MucUserItem(requestor.getAffiliation(), requestor.getRole())
        JavaListUtils.foreach(room.getOccupants(), (occupant:Occupant) => {
          //                              for (occupant:Occupant <- room.getOccupants().toArray().toList.asInstanceOf[List[Occupant]]) {
          val presenceToRemaining:Stanza = MUCStanzaBuilder.createPresenceStanza(requestor.getJidInRoom(), occupant.getJid(), null, NamespaceURIs.XEP0045_MUC_USER, presenceItem)
          relayStanza(occupant.getJid(), presenceToRemaining, serverRuntimeContext)
        })
      }
    } else if (requestAllow == null) {
      // no request allow, treat as voice request
      val requestForm:VoiceRequestForm = new VoiceRequestForm(from, sendingOccupant.getNick())
      JavaListUtils.foreach(room.getModerators(),(moderator:Occupant) => {
        //for (moderator:Occupant <- room.getModerators().toArray().toList.asInstanceOf[List[Occupant]]) {
        val request:Stanza = StanzaBuilder.createMessageStanza(room.getJID(), moderator.getJid(), null, null).addPreparedElement(requestForm.createFormXML()).build()
        relayStanza(moderator.getJid(), request, serverRuntimeContext)
      })
    }
  }
  protected def getFieldValue(fields:JavaList[XMLElement], varName:String):String = {
    JavaListUtils.foreach(fields,(field:XMLElement) => {
      if (varName.equals(field.getAttributeValue("var"))) {
        try {
          return field.getSingleInnerElementsNamed("value", NamespaceURIs.JABBER_X_DATA).getInnerText().getText()
        } catch {
          case e:XMLSemanticError => {
            return null
          }
        }
      }
    })
    return null
  }
  protected def relayStanza(receiver:Entity, stanza:Stanza, serverRuntimeContext:ServerRuntimeContext):Unit = {
    try {
      serverRuntimeContext.getStanzaRelay().relay(receiver, stanza, new IgnoreFailureStrategy())
    } catch {
      case e:DeliveryException => {
        error("presence relaying failed",e)
      }
      case other:Throwable => throw other
    }
  }
}

class MeTLMUCPresenceHandler(conference:Conference,mucModule:MeTLMucModule,useXmppHistory:Boolean = false) extends DefaultPresenceHandler with LiftLogger {
  override protected def verifyNamespace(stanza:Stanza):Boolean = true

  protected def createPresenceErrorStanza(from:Entity, to:Entity, id:String, typeName:String, errorName:String):Stanza = {
    // "Note: If an error occurs in relation to joining a room, the service SHOULD include
    // the MUC child element (i.e., <x xmlns='http://jabber.org/protocol/muc'/>) in the
    // <presence/> stanza of type "error"."
    MUCHandlerHelper.createErrorStanza("presence", NamespaceURIs.JABBER_CLIENT, from, to, id, typeName, errorName, JavaArrays.asList(new X().asInstanceOf[XMLElement]))
  }
  override protected def executePresenceLogic(stanza:PresenceStanza, serverRuntimeContext:ServerRuntimeContext,sessionsContext:SessionContext):Stanza = {
    val roomAndNick:Entity = stanza.getTo()
    val occupantJid:Entity = stanza.getFrom()
    val roomJid:Entity = roomAndNick.getBareJID()
    val nick:String = roomAndNick.getResource()
    // user did not send nick name
    if (nick == null) {
      createPresenceErrorStanza(roomJid, occupantJid, stanza.getID(), "modify", "jid-malformed")
    } else {
      val typeName:String = stanza.getType()
      if (typeName == null) {
        available(stanza, roomJid, occupantJid, nick, serverRuntimeContext)
      } else if (typeName.equals("unavailable")) {
        unavailable(stanza, roomJid, occupantJid, nick, serverRuntimeContext)
      } else {
        throw new RuntimeException("Presence type not handled by MUC module: " + typeName)
      }
    }
  }

  protected def getInnerElementText(element:XMLElement,childName:String):String = {
    try {
      val childElm:XMLElement = element.getSingleInnerElementsNamed(childName)
      if (childElm != null && childElm.getInnerText() != null) {
        childElm.getInnerText().getText()
      } else {
        null
      }
    } catch {
      case e:XMLSemanticError => null
      case other:Throwable => throw other
    }
  }

  protected def available(stanza:PresenceStanza,roomJid:Entity,newOccupantJid:Entity,incomingNick:String, serverRuntimeContext:ServerRuntimeContext):Stanza = {
    var nick:String = incomingNick
    var newRoom:Boolean = false
    var room:Room = conference.findRoom(roomJid)
    var output:Stanza = null
    // TODO what to use for the room name?
    if (room == null) {
      room = conference.createRoom(roomJid, roomJid.getNode())
      newRoom = true
    }
    if (room.isInRoom(newOccupantJid)) {
      // user is already in room, change nick
      debug("%s has requested to change nick in room %s".format(newOccupantJid, roomJid))
      // occupant is already in room/
      val occupant:Occupant = room.findOccupantByJID(newOccupantJid)
      //                              val occupants:List[Occupant] = room.getOccupants().toArray().toList
      if (nick.equals(occupant.getNick())) {
        // nick unchanged, change show and status
        JavaListUtils.foreach(room.getOccupants(), (receiver:Occupant) => {
          //          for (val receiver:Occupant <- occupants) {
          sendChangeShowStatus(occupant, receiver, room, getInnerElementText(stanza, "show"), getInnerElementText(stanza, "status"), serverRuntimeContext)
        })
      } else {
        if (room.isInRoom(nick)) {
          // user with this nick is already in room
          return createPresenceErrorStanza(roomJid, newOccupantJid, stanza.getID(), "cancel", "conflict")
        } else {
          val oldNick:String = occupant.getNick();
          // update the nick
          occupant.setNick(nick);
          /* //not using presence, so disabling to improve traffic and reduce latency.
          // send out unavailable presences to all existing occupants
          val occupants = room.getOccupants()
          JavaListUtils.foreach(occupants,(receiver:Occupant) => {
            //                                              for (val receiver:Occupant <- occupants) {
            sendChangeNickUnavailable(occupant, oldNick, receiver, room, serverRuntimeContext);
          })

          // send out available presences to all existing occupants
          JavaListUtils.foreach(occupants,(receiver:Occupant) => {
            //                                              for (val receiver:Occupant <- room.getOccupants()) {
            sendChangeNickAvailable(occupant, receiver, room, serverRuntimeContext);
          })
          */
        }
      }
    } else {
      debug("%s has requested to enter room %s".format(newOccupantJid, roomJid))
      var nickConflict:Boolean = room.isInRoom(nick)
      var nickRewritten:Boolean = false
      var counter:Int = 1
      var maxNickChanges:Int = 100 // to avoid DoS attacks
      var rewrittenNick:String = null
      while (nickConflict && counter < maxNickChanges && room.rewritesDuplicateNick()) {
        rewrittenNick = nick + "_" + counter
        nickConflict = room.isInRoom(rewrittenNick)
        if (nickConflict)
          counter += 1
        else {
          nick = rewrittenNick
          nickRewritten = true
        }
      }
      if (nickConflict) {
        // user with this nick is already in room
        return createPresenceErrorStanza(roomJid, newOccupantJid, stanza.getID(), "cancel", "conflict")
      }
      // check password if password protected
      if (room.isRoomType(RoomType.PasswordProtected)) {
        val x:X = X.fromStanza(stanza)
        var password:String = null
        if (x != null) {
          password = x.getPasswordValue()
        }
        if (password == null || !password.equals(room.getPassword())) {
          // password missing or not matching
          return createPresenceErrorStanza(roomJid, newOccupantJid, stanza.getID(), "auth", "not-authorized")
        }
      }
      var newOccupant:Occupant = null
      try {
        newOccupant = room.addOccupant(newOccupantJid, nick)
      } catch {
        case e:RuntimeException => {
          return createPresenceErrorStanza(roomJid, newOccupantJid, stanza.getID(), "auth", e.getMessage())
        }
        case other:Throwable => throw other
      }
      if(newRoom) {
        room.getAffiliations().add(newOccupantJid, Affiliation.Owner)
        newOccupant.setRole(Role.Moderator)
      }
      // if the new occupant is a server admin, he will be for the room, too
      val adhocCommandsService:ServerAdministrationService = serverRuntimeContext.getServerRuntimeContextService(ServerAdministrationService.SERVICE_ID).asInstanceOf[ServerAdministrationService]
      if (adhocCommandsService != null && adhocCommandsService.isAdmin(newOccupantJid.getBareJID())) {
        val roomAffiliations:Affiliations = room.getAffiliations()
        // make new occupant an Admin, but do not downgrade from Owner
        // Admin affilitation implies Moderator role (see XEP-0045 5.1.2)
        if (roomAffiliations.getAffiliation(newOccupantJid) != Affiliation.Owner) {
          roomAffiliations.add(newOccupantJid, Affiliation.Admin)
          newOccupant.setRole(Role.Moderator)
        }
      }
      /* // we're not using presence, so let's turn it off entirely to improve performance and reduce traffic.
      // relay presence of all existing room occupants to the now joined occupant
      val occupants = room.getOccupants()
      JavaListUtils.foreach(occupants,(occupant:Occupant) => {
        //                                      for (occupant:Occupant <- occupants) {
        sendExistingOccupantToNewOccupant(newOccupant, occupant, room, serverRuntimeContext)
      })
      // relay presence of the newly added occupant to all existing occupants
      JavaListUtils.foreach(occupants,(occupant:Occupant) => {
        //                                      for (occupant:Occupant <- occupants) {
        sendNewOccupantPresenceToExisting(newOccupant, occupant, room, serverRuntimeContext, nickRewritten)
      })
      */
      /*
       // never send discussion history to user
      // send discussion history to user
      if (useXmppHistory){
        val includeJid:Boolean = room.isRoomType(RoomType.NonAnonymous)
        val history:JavaList[Stanza] = room.getHistory().createStanzas(newOccupant,includeJid,History.fromStanza(stanza))
        relayStanzas(newOccupantJid,history,serverRuntimeContext)
      }
      */
      debug("%s successfully entered room %s".format(newOccupantJid, roomJid))
    }
    return null;
  }
  protected def unavailable(stanza:PresenceStanza, roomJid:Entity, occupantJid:Entity, nick:String, serverRuntimeContext:ServerRuntimeContext):Stanza = {
    val room:Room = conference.findRoom(roomJid)
    // room must exist, or we do nothing
    if (room != null) {
      val exitingOccupant:Occupant = room.findOccupantByJID(occupantJid)
      // user must by in room, or we do nothing
      if (exitingOccupant != null) {
        val allOccupants:JavaSet[Occupant] = room.getOccupants()
        room.removeOccupant(occupantJid)
        // TODO replace with use of X
        var statusMessage:String = null
        try {
          val statusElement:XMLElement = stanza.getSingleInnerElementsNamed("status")
          if (statusElement != null && statusElement.getInnerText() != null) {
            statusMessage = statusElement.getInnerText().getText()
          }
        } catch {
          case e:XMLSemanticError => {}
        }
        /* // we're not using presences, so disable this for improved traffic.
        // relay presence of the newly added occupant to all existing occupants
        JavaListUtils.foreach(allOccupants,(occupant:Occupant) => {
          //                                      for (occupant:Occupant <= allOccupants) {
          sendExitRoomPresenceToExisting(exitingOccupant, occupant, room, statusMessage, serverRuntimeContext)
        })
        */
        if (room.isRoomType(RoomType.Temporary) && room.isEmpty()) {
          conference.deleteRoom(roomJid)
        }
      }
    }
    return null
  }
  protected def sendExistingOccupantToNewOccupant(newOccupant:Occupant, existingOccupant:Occupant, room:Room, serverRuntimeContext:ServerRuntimeContext):Unit = {
    //            <presence
    //            from='darkcave@chat.shakespeare.lit/firstwitch'
    //            to='hag66@shakespeare.lit/pda'>
    //          <x xmlns='http://jabber.org/protocol/muc#user'>
    //            <item affiliation='owner' role='moderator'/>
    //          </x>
    //        </presence>

    // do not send own presence
    if (existingOccupant.getJid().equals(newOccupant.getJid())) {
      return
    }
    val roomAndOccupantNick:Entity = new EntityImpl(room.getJID(), existingOccupant.getNick())
    val presenceToNewOccupant:Stanza = MUCStanzaBuilder.createPresenceStanza(roomAndOccupantNick, newOccupant.getJid(), null, NamespaceURIs.XEP0045_MUC_USER, new MucUserItem(existingOccupant.getAffiliation(), existingOccupant.getRole()))
    debug("Room presence from %s sent to %s".format(newOccupant,roomAndOccupantNick))
    relayStanza(newOccupant.getJid(), presenceToNewOccupant, serverRuntimeContext)
  }
  protected def sendNewOccupantPresenceToExisting(newOccupant:Occupant,existingOccupant:Occupant,room:Room,serverRuntimeContext:ServerRuntimeContext,nickRewritten:Boolean):Unit = {
    val roomAndNewUserNick:Entity = new EntityImpl(room.getJID(), newOccupant.getNick())
    val inner:JavaList[XMLElement] = new JavaArrayList[XMLElement]()
    // room is non-anonymous or semi-anonymous and the occupant a moderator, send full user JID
    val includeJid:Boolean = includeJidInItem(room,existingOccupant)//room.isRoomType(RoomType.NonAnonymous) || (room.isRoomType(SemiAnonymous) && existingOccupant.getRole() == Role.Moderator)
      inner.add(new MucUserItem(newOccupant, includeJid, false))
    if (existingOccupant.getJid().equals(newOccupant.getJid())) {
      if (room.isRoomType(RoomType.NonAnonymous)) {
        // notify the user that this is a non-anonymous room
        inner.add(new Status(StatusCode.ROOM_NON_ANONYMOUS))
      }
      // send status to indicate that this is the users own presence
      inner.add(new Status(StatusCode.OWN_PRESENCE))
      if (nickRewritten)
        inner.add(new Status(StatusCode.NICK_MODIFIED))
    }
    val presenceToExisting:Stanza = MUCStanzaBuilder.createPresenceStanza(roomAndNewUserNick, existingOccupant.getJid(), null, NamespaceURIs.XEP0045_MUC_USER, inner)
    debug("Room presence from %s sent to %s".format(roomAndNewUserNick, existingOccupant))
    relayStanza(existingOccupant.getJid(), presenceToExisting, serverRuntimeContext)
  }
  protected def sendChangeNickUnavailable(changer:Occupant, oldNick:String, receiver:Occupant, room:Room, serverRuntimeContext:ServerRuntimeContext):Unit = {
    val roomAndOldNick:Entity = new EntityImpl(room.getJID(), oldNick)
    val inner:JavaList[XMLElement] = new JavaArrayList[XMLElement]()
    val includeJid:Boolean = includeJidInItem(room, receiver)
    inner.add(new MucUserItem(changer, includeJid, true))
    inner.add(new Status(StatusCode.NEW_NICK))
    if (receiver.getJid().equals(changer.getJid())) {
      // send status to indicate that this is the users own presence
      inner.add(new Status(StatusCode.OWN_PRESENCE))
    }
    val presenceToReceiver:Stanza = MUCStanzaBuilder.createPresenceStanza(roomAndOldNick, receiver.getJid(), PresenceStanzaType.UNAVAILABLE, NamespaceURIs.XEP0045_MUC_USER, inner)
    debug("Room presence from %s sent to %s".format(roomAndOldNick, receiver))
    relayStanza(receiver.getJid(), presenceToReceiver, serverRuntimeContext)
  }
  protected def sendChangeShowStatus(changer:Occupant, receiver:Occupant, room:Room, show:String, status:String, serverRuntimeContext:ServerRuntimeContext):Unit = {
    val roomAndNick:Entity = new EntityImpl(room.getJID(), changer.getNick())
    val builder:StanzaBuilder = StanzaBuilder.createPresenceStanza(roomAndNick, receiver.getJid(), null, null, show, status)
    val includeJid:Boolean = includeJidInItem(room, receiver)
    //        if(receiver.getJid().equals(changer.getJid())) {
    //            // send status to indicate that this is the users own presence
    //            new Status(StatusCode.OWN_PRESENCE).insertElement(builder);
    //        }
    builder.addPreparedElement(new X(NamespaceURIs.XEP0045_MUC_USER, new MucUserItem(changer, includeJid, true)))
    debug("Room presence from %s sent to %s".format(roomAndNick, receiver))
    relayStanza(receiver.getJid(), builder.build(), serverRuntimeContext)
  }

  protected def includeJidInItem(room:Room, receiver:Occupant):Boolean = {
    // room is non-anonymous or semi-anonymous and the occupant a moderator, send full user JID
    room.isRoomType(RoomType.NonAnonymous) || (room.isRoomType(RoomType.SemiAnonymous) && receiver.getRole() == Role.Moderator)
  }

  protected def sendChangeNickAvailable(changer:Occupant, receiver:Occupant, room:Room, serverRuntimeContext:ServerRuntimeContext):Unit = {
    val roomAndOldNick:Entity = new EntityImpl(room.getJID(), changer.getNick())
    val inner:JavaList[XMLElement] = new JavaArrayList[XMLElement]()
    val includeJid:Boolean = includeJidInItem(room, receiver)
    inner.add(new MucUserItem(changer, includeJid, false))
    if (receiver.getJid().equals(changer.getJid())) {
      // send status to indicate that this is the users own presence
      inner.add(new Status(StatusCode.OWN_PRESENCE));
    }
    val presenceToReceiver:Stanza = MUCStanzaBuilder.createPresenceStanza(roomAndOldNick, receiver.getJid(), null, NamespaceURIs.XEP0045_MUC_USER, inner)
    relayStanza(receiver.getJid(), presenceToReceiver, serverRuntimeContext);
  }
  protected def sendExitRoomPresenceToExisting(exitingOccupant:Occupant, existingOccupant:Occupant, room:Room, statusMessage:String, serverRuntimeContext:ServerRuntimeContext):Unit = {
    val roomAndNewUserNick:Entity = new EntityImpl(room.getJID(), exitingOccupant.getNick())
    val inner:JavaList[XMLElement] = new JavaArrayList[XMLElement]()
    inner.add(new MucUserItem(null, null, existingOccupant.getAffiliation(), Role.None))
    // is this stanza to be sent to the exiting user himself?
    val ownStanza:Boolean = existingOccupant.getJid().equals(exitingOccupant.getJid())
    if (ownStanza || statusMessage != null) {
      val status:Status = ownStanza match {
        case true => new Status(StatusCode.OWN_PRESENCE, statusMessage)
        case false => new Status(statusMessage)
      }
      inner.add(status)
    }
    val presenceToExisting:Stanza = MUCStanzaBuilder.createPresenceStanza(roomAndNewUserNick, existingOccupant.getJid(), PresenceStanzaType.UNAVAILABLE, NamespaceURIs.XEP0045_MUC_USER, inner)
    relayStanza(existingOccupant.getJid(), presenceToExisting, serverRuntimeContext)
  }
  protected def relayStanzas(receiver:Entity, stanzas:JavaList[Stanza], serverRuntimeContext:ServerRuntimeContext):Unit = {
    JavaListUtils.foreach(stanzas,(stanza:Stanza) => {
      //      for (stanza:Stanza : stanzas.toArray().toList) {
      relayStanza(receiver, stanza, serverRuntimeContext)
    })
  }
  protected def relayStanza(receiver:Entity, stanza:Stanza, serverRuntimeContext:ServerRuntimeContext):Unit = {
    try {
      serverRuntimeContext.getStanzaRelay().relay(receiver, stanza, new IgnoreFailureStrategy())
    } catch {
      case e:DeliveryException => {
        error("presence relaying failed",e)
      }
      case other:Throwable => throw other
    }
  }
}

object JavaListUtils {
  def foreach[A](coll:JavaSet[A], function:A=>Unit) = {
    val iter = coll.iterator
    while (iter.hasNext)
      function(iter.next)
  }
  def foreach[A](coll:JavaList[A], function:A=>Unit) = {
    val iter = coll.iterator
    while (iter.hasNext)
      function(iter.next)
  }
  def map[A,B](coll:JavaList[A], function:A=>B):List[B] = {
    val iter = coll.iterator
    var output = List.empty[B]
    while (iter.hasNext)
      output = output ::: List(function(iter.next))
    output
  }
  def toList[A](coll:JavaList[A]):List[A] = {
    val iter = coll.iterator
    var output = List.empty[A]
    while (iter.hasNext)
      output = output ::: List(iter.next)
    output
  }
  def foldl[A,B](coll:JavaList[A], seed:B, function:(B,A) => B):B = {
    val iter = coll.iterator
    var acc = seed
    while (iter.hasNext)
      acc = function(acc,iter.next)
    acc
  }
  def toJavaList[A](list:List[A]):JavaList[A] = {
    val output = new JavaArrayList[A]()
    list.foreach(li => output.add(li))
    output
  }
}

class VysperXMLUtils extends LiftLogger {
  def vRender(input:XMLFragment):String = {
    val result = input match {
      case t:XMLText => t.getText
      case e:XMLElement => new vXmlRenderer(e).getComplete()
      case other => "unable to render: %s".format(other)
    }
    trace("vRender: %s".format(input.toString,result))
    result
  }
  def toVysper(scalaNode:Node):XMLFragment = {
    val output = scalaNode match {
      case a:Atom[String] => new XMLText(a.text)
      case a:Atom[Double] => new XMLText(a.text.toString)
      case a:Atom[Int] => new XMLText(a.text.toString)
      case a:Atom[Long] => new XMLText(a.text.toString)
      case a:Atom[Float] => new XMLText(a.text.toString)
      //case t:Text => new XMLText(t.text)
      case e:Elem => {
        val prefix = e.prefix
        val attributes = toAttributes(e.attributes)
        val namespace = e.prefix match {
          case "" | null => {
            toAttributeTupleList(e.attributes).find(_._1 == "xmlns").map(_._2).getOrElse("") // this might spray empty xmlns descriptions on all child nodes, we'll see.
          }
          case other => e.getNamespace(other)
        }
        val label = e.label
        val children:JavaList[XMLFragment] = JavaListUtils.toJavaList((e.child.map{
          case g:Group => g.nodes.map(n => toVysper(n))
          case other => List(toVysper(other))
        }).toList.flatten)
        new XMLElement(namespace,label,prefix,attributes,children)
      }
      case other => {
        warn("other found: %s (%s)".format(other, other.getClass.toString))
        null.asInstanceOf[XMLFragment]
      }
    }
    trace("toVysper: %s -> %s".format(scalaNode,vRender(output)))
    output
  }
  def toScala(vysperNode:XMLFragment):Node = {
    val output = vysperNode match {
      case t:XMLText => {
        trace("toScala found text: %s".format(t.getText))
        Text(t.getText)
      }
      case e:XMLElement => {
        val prefix = e.getNamespacePrefix match {
          case s:String if s.length > 0 => s
          case _ => null
        }
        val label = e.getName
        val innerElements:JavaList[XMLFragment] = e.getInnerFragments
        val scope = TopScope
        val attributes:JavaList[Attribute] = e.getAttributes
        val scalaAttributes = toMetaData(attributes)
        val child = JavaListUtils.map(innerElements, (fragment:XMLFragment) => toScala(fragment)) match {
          // I wonder whether I should pass a null or an empty Text()?
          case l:List[Node] if l.length == 0 => List(Text(""))
          case l:List[Node] => l
          //case l:List[Node] => Group(l)
        }
        Elem(prefix,label,scalaAttributes,scope,child:_*)
      }
      case other => {
        warn("other found: %s (%s)".format(other, other.getClass.toString))
        null.asInstanceOf[Node]
      }
    }
    trace("toScala: %s -> %s".format(vRender(vysperNode),output))
    output
  }
  protected def toMetaData(attributes:JavaList[Attribute]):MetaData = toMetaDataFromIterator(attributes.iterator)
  protected def toMetaDataFromIterator(iter:JavaIterator[Attribute]):MetaData = {
    iter.hasNext match {
      case true => {
        val current = iter.next
        val name = current.getName
        val value = current.getValue
        //              current.getNamespaceUri match {
        //                      case s:String if s > 0 => new PrefixedAttribute(s,name,value,toMetaDataFromIterator(iter))
        //                      case _ => new UnprefixedAttribute(name,value,toMetaDataFromIterator(iter))
        //              }
        new UnprefixedAttribute(name,value,toMetaDataFromIterator(iter))
      }
      case false => scala.xml.Null
    }
  }
  protected def toAttributes(metaData:MetaData):JavaList[Attribute] = {
    val result = JavaListUtils.toJavaList(toAttributesList(metaData))
    trace("toAttributes: %s".format(metaData))
    result
  }
  protected def toAttributesList(metaData:MetaData):List[Attribute] = {
    val result = toAttributeTupleList(metaData).map(a => new Attribute(a._1,a._2))
    trace("toAttributeList: %s => %s".format(metaData,result))
    result
  }
  protected def toAttributeTupleList(metaData:MetaData):List[Tuple2[String,String]] = {
    val result = metaData match {
      case u:UnprefixedAttribute => ((u.key, scalaAttributeValue(u.value)) :: toAttributeTupleList(metaData.next))
      case p:PrefixedAttribute => ((p.key, scalaAttributeValue(p.value)) :: toAttributeTupleList(metaData.next))
      case _ => List.empty[Tuple2[String,String]]
    }
    trace("toAttributeTupleList: %s => %s".format(metaData,result))
    result

  }
  protected def scalaAttributeValue(nodes:Seq[Node]):String = nodes.foldLeft("")((acc,item) => acc + {
    item.toString
  })
}
