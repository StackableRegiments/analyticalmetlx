package com.metl.model

import
  _root_.net.liftweb._
import util._
import Helpers._
import common._
import http._
import provider.servlet._

// for EmbeddedXmppServer
import org.apache.vysper.mina.TCPEndpoint
import org.apache.vysper.storage.StorageProviderRegistry
import org.apache.vysper.storage.inmemory.MemoryStorageProviderRegistry
import org.apache.vysper.xmpp.addressing.{Entity,EntityImpl}
import org.apache.vysper.xmpp.authorization.AccountManagement
import org.apache.vysper.xmpp.modules.extension.xep0054_vcardtemp.VcardTempModule
import org.apache.vysper.xmpp.modules.extension.xep0092_software_version.SoftwareVersionModule
import org.apache.vysper.xmpp.modules.extension.xep0119_xmppping.XmppPingModule
import org.apache.vysper.xmpp.modules.extension.xep0202_entity_time.EntityTimeModule
import org.apache.vysper.xmpp.modules.extension.xep0077_inbandreg.InBandRegistrationModule
import org.apache.vysper.xmpp.server.XMPPServer

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

class EmbeddedXmppServerRoomAdaptor(serverRuntimeContext:ServerRuntimeContext,conference:Conference) {
  val domainString = "local.temp"
  val conferenceString = "conference.%s".format(domainString)
  val config = ServerConfiguration.default
  val configName = config.name
  val serializer = new MeTL2011XmlSerializer(configName)
  val converter = new VysperXMLUtils
  protected val xmppMessageDeliveryStrategy = new IgnoreFailureStrategy()
  def relayMessageToMeTLRoom(message:Stanza):Unit = {
    val to:Entity = message.getTo()
    val location:String = to.getNode()
    val payloads:JavaList[XMLFragment] = message.getInnerFragments()
    //this is about to be a problem - how do I choose the appropriate serverConfiguration, I wonder?
    MeTLXConfiguration.getRoom(location,configName) match {
      case r:XmppBridgingHistoryCachingRoom => {
        JavaListUtils.foreach(payloads,(payload:XMLFragment) => {
          serializer.toMeTLData(converter.toScala(payload)) match {
            case m:MeTLStanza => r.sendMessageFromBridge(m)
            case _ => {}
          }
        })
      }
      case _ => {}
    }
  }
  def relayMessageToXmppMuc(location:String,message:MeTLStanza):Unit = serializer.fromMeTLData(message) match {
    case n:Node => relayMessageNodeToXmppMuc(location,n)
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
            println("presence relaying failed %s".format(e))
          }
          case other => throw other
        }
      })
    }
  }
}
/*
class EmbeddedTlsContext(keystore:java.io.File,storePass:String,keyPass:String) extends org.apache.vysper.xmpp.cryptography.TLSContextFactory {
  import java.io.{IOException,InputStream}
  import java.security.{GeneralSecurityException, KeyStore, Security}
  import javax.net.ssl.{KeyManagerFactory,SSLContext}
  override def getSSLContext:javax.net.ssl.SSLContext = {

        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream in = null;
        try {
            in = getCertificateInputStream();
            ks.load(in, storePass.toCharArray());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    ;
                }
            }
        }

        // Set up key manager factory to use our key store
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KEY_MANAGER_FACTORY_ALGORITHM);
        kmf.init(ks, keyPass.toCharArray());

        // Initialize the SSLContext to work with our key managers.
        SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
        sslContext.init(kmf.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

        return sslContext;
  }
}
*/
object EmbeddedXmppServer {
  protected var privateServer:Box[XMPPServer] = Empty
  protected var mucModule:Box[MeTLMucModule] = Empty
  protected var roomAdaptor:Box[EmbeddedXmppServerRoomAdaptor] = Empty

  def initialize = {
    println("embedded xmpp server start handler")
    val domain = "local.temp"
    val providerRegistry = new MemoryStorageProviderRegistry()

    val accountManagement = providerRegistry.retrieve(classOf[AccountManagement]).asInstanceOf[AccountManagement]
    List("dave","test","chris").foreach(u => {
    val user = EntityImpl.parse(u + "@" + domain);
      if (!accountManagement.verifyAccountExists(user)){
        accountManagement.addUser(user, "fred")
      }
    })
    privateServer = Full(new XMPPServer(domain))
    privateServer.map(p => {
      p.addEndpoint(new TCPEndpoint())
      p.setStorageProviderRegistry(providerRegistry)
      p.setTLSCertificateInfo(new java.io.File("/stackable/analyticalmetlx/config/metl.jks"),"helpme")
      try {
        p.start()
        println("embedded xmpp server started")
        val metlMuc = new MeTLMucModule()
        p.addModule(new SoftwareVersionModule())
        p.addModule(new EntityTimeModule())
        p.addModule(new VcardTempModule())
        p.addModule(new XmppPingModule())
        p.addModule(new InBandRegistrationModule())
        p.addModule(metlMuc)
        mucModule = Full(metlMuc)
        roomAdaptor = metlMuc.getRoomAdaptor
        println("embedded xmpp default modules loaded")
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

class MeTLMucModule(subdomain:String = "conference",conference:Conference = new Conference("Conference")) extends DefaultDiscoAwareModule with Component with ComponentInfoRequestListener with ItemRequestListener {
  protected var fullDomain:Entity = null
  //      override final val logger:Logger = LoggerFactory.getLogger(classOf[MUCModule])
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
      println("No room storage provider found, using the default (in memory)");
    } else {
      conference.setRoomStorageProvider(roomStorageProvider);
    }
    if (occupantStorageProvider == null) {
      //logger.warn("No occupant storage provider found, using the default (in memory)");
      println("No occupant storage provider found, using the default (in memory)");
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
      case other => throw other
    }
  }
  def getSubdomain:String = subdomain
  def getStanzaProcessor:StanzaProcessor = stanzaProcessor
}

class MeTLMUCMessageHandler(conference:Conference,moduleDomain:Entity,mucModule:MeTLMucModule,useXmppHistory:Boolean = false) extends DefaultMessageHandler {

  //final Logger logger = LoggerFactory.getLogger(MUCMessageHandler.class);

  override protected def verifyNamespace(stanza:Stanza):Boolean = true
  private def createMessageErrorStanza(from:Entity,to:Entity,id:String, typeName:StanzaErrorType, errorCondition:StanzaErrorCondition, stanza:Stanza):Stanza = {
    MUCHandlerHelper.createErrorStanza("message", NamespaceURIs.JABBER_CLIENT, from, to, id, typeName.value(), errorCondition.value(), stanza.getInnerElements())
  }
  override protected def executeMessageLogic(stanza:MessageStanza, serverRuntimeContext:ServerRuntimeContext, sessionContext:SessionContext) = {
    println("Received message for MUC")
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
        println("Received groupchat message to %s".format(roomJid))
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
                println("Relaying message to all room occupants")
                JavaListUtils.foreach(room.getOccupants(),(occupant:Occupant) => {
                  println("Relaying message to %s".format(occupant))
                  val replaceAttributes:JavaList[Attribute] = new JavaArrayList[Attribute]()
                  replaceAttributes.add(new Attribute("from", roomAndSendingNick.getFullQualifiedName()))
                  replaceAttributes.add(new Attribute("to",occupant.getJid().getFullQualifiedName()))
                  val finalStanza:Stanza = StanzaBuilder.createClone(stanza, true, replaceAttributes).build()
                  relayStanza(occupant.getJid(), finalStanza, serverRuntimeContext)
                })
                mucModule.getRoomAdaptor.map(ra => ra.relayMessageToMeTLRoom(stanza))
                if (useXmppHistory)
                  room.getHistory().append(stanza, sendingOccupant)
                null
              }
            } else {
              createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY,StanzaErrorCondition.FORBIDDEN, stanza)
            }
          } else {
            createMessageErrorStanza(room.getJID(), from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.NOT_ACCEPTABLE, stanza)
          }
        } else {
          createMessageErrorStanza(moduleDomain, from, stanza.getID(), StanzaErrorType.MODIFY, StanzaErrorCondition.ITEM_NOT_FOUND, stanza)
        }
      }
    } else if (typeName == null || typeName == MessageStanzaType.CHAT || typeName == MessageStanzaType.NORMAL) {
      //private message
      println("Received direct message to %s".format(roomWithNickJid))
      val room:Room = conference.findRoom(roomJid)
      if (room != null) {
        val sendingOccupant:Occupant = room.findOccupantByJID(from)
        // sender must be participant in room
        if (roomWithNickJid.equals(roomJid)) {
          // check x element
          if (stanza.getVerifier().onlySubelementEquals("x", NamespaceURIs.JABBER_X_DATA)) {
            // void requests
            println("Received voice request for room %s".format(roomJid))
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
              println("Relaying message to %s".format(receivingOccupant))
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
          case other => throw other
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
        case other => throw other
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
        println("presence relaying failed %s".format(e))
      }
      case other => throw other
    }
  }
}

class MeTLMUCPresenceHandler(conference:Conference,mucModule:MeTLMucModule,useXmppHistory:Boolean = false) extends DefaultPresenceHandler {

  //    final Logger logger = LoggerFactory.getLogger(MUCPresenceHandler.class);

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
      case other => throw other
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
      println("%s has requested to change nick in room %s".format(newOccupantJid, roomJid))
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
        }
      }
    } else {
      println("%s has requested to enter room %s".format(newOccupantJid, roomJid))
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
        case other => throw other
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
      // send discussion history to user
      if (useXmppHistory){
        val includeJid:Boolean = room.isRoomType(RoomType.NonAnonymous)
        val history:JavaList[Stanza] = room.getHistory().createStanzas(newOccupant,includeJid,History.fromStanza(stanza))
        relayStanzas(newOccupantJid,history,serverRuntimeContext)
      }
      println("%s successfully entered room %s".format(newOccupantJid, roomJid))
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
        // relay presence of the newly added occupant to all existing occupants
        JavaListUtils.foreach(allOccupants,(occupant:Occupant) => {
          //                                      for (occupant:Occupant <= allOccupants) {
          sendExitRoomPresenceToExisting(exitingOccupant, occupant, room, statusMessage, serverRuntimeContext)
        })
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
    println("Room presence from %s sent to %s".format(newOccupant,roomAndOccupantNick))
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
    println("Room presence from %s sent to %s".format(roomAndNewUserNick, existingOccupant))
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
    println("Room presence from %s sent to %s".format(roomAndOldNick, receiver))
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
    println("Room presence from %s sent to %s".format(roomAndNick, receiver))
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
        println("presence relaying failed %s".format(e))
      }
      case other => throw other
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

class VysperXMLUtils {
  def vRender(input:XMLFragment):String = {
    input match {
      case t:XMLText => t.getText
      case e:XMLElement => new vXmlRenderer(e).getComplete()
      case other => "unable to render: %s".format(other)
    }
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
        val namespace = e.getNamespace(prefix)
        val label = e.label
        val attributes = toAttributes(e.attributes)
        val children:JavaList[XMLFragment] = JavaListUtils.toJavaList((e.child.map{
          case g:Group => g.nodes.map(n => toVysper(n))
          case other => List(toVysper(other))
        }).toList.flatten)
        new XMLElement(namespace,label,prefix,attributes,children)
      }
      case other => {
        println("other found: %s (%s)".format(other, other.getClass.toString))
        null.asInstanceOf[XMLFragment]
      }
    }
    println("toVysper: %s -> %s".format(scalaNode,vRender(output)))
    output
  }
  def toScala(vysperNode:XMLFragment):Node = {
    val output = vysperNode match {
      case t:XMLText => Text(t.getText)
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
          case l:List[Node] if l.length == 0 => Text("")
          case l:List[Node] if l.length == 1 => l.head
          case l:List[Node] => Group(l)
        }
        Elem(prefix,label,scalaAttributes,scope,child)
      }
      case other => {
        println("other found: %s (%s)".format(other, other.getClass.toString))
        null.asInstanceOf[Node]
      }
    }
    println("toScala: %s -> %s".format(vRender(vysperNode),output))
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
  protected def toAttributes(metaData:MetaData):JavaList[Attribute] = JavaListUtils.toJavaList(toAttributesList(metaData))
  protected def toAttributesList(metaData:MetaData):List[Attribute] = {
    metaData match {
      case u:UnprefixedAttribute => new Attribute(u.key, scalaAttributeValue(u.value)) :: toAttributesList(metaData.next)
      case p:PrefixedAttribute => new Attribute(p.key, scalaAttributeValue(p.value)) :: toAttributesList(metaData.next)
      case _ => List.empty[Attribute]
    }
  }
  protected def scalaAttributeValue(nodes:Seq[Node]):String = nodes.foldLeft("")((acc,item) => acc + item.text)
}
