package com.metl.model
		
import com.metl.data._		
import com.metl.utils._		
import com.metl.liftExtensions._		
		
import net.liftweb._		
import common._		
import http._		
import util._		
import Helpers._		
import HttpHelpers._		
import actor._		
import scala.xml._		
import com.metl.model._		
import SHtml._		
		
import js._		
import JsCmds._		
import JE._		
import net.liftweb.http.js.jquery.JqJsCmds._		
		
import net.liftweb.http.js.jquery.JqJE._		
		
import java.util.Date		
import com.metl.renderer.SlideRenderer		
		
import json._		
import json.JsonAST._		
		
import com.metl.snippet.Metl._		
		
import java.io.IOException;		
import java.util.concurrent.ConcurrentHashMap;		
		
import org.kurento.client.{Event,EventListener,IceCandidate,IceCandidateFoundEvent,KurentoClient,KurentoConnectionListener,MediaPipeline,WebRtcEndpoint,Properties,Composite,HubPort,DispatcherOneToMany,RecorderEndpoint,ConnectionStateChangedEvent,ConnectionState,DataChannelOpenEvent,DataChannelCloseEvent,MediaStateChangedEvent,MediaState,MediaType}		
import org.kurento.jsonrpc.JsonUtils;		
import com.google.gson.Gson;		
import com.google.gson.GsonBuilder;		
import com.google.gson.JsonObject;		
		
case class KurentoOffer(userId:String,id:String,sdpOffer:String)		
case class KurentoAnswer(userId:String,id:String,response:String,sdpAnswer:String,message:String = "")		
case class KurentoServerSideIceCandidate(userId:String,id:String,iceCandidateJsonString:String)		
		
case class KurentoChannelDefinition(userId:String,id:String,sdpAnswer:String,candidates:List[KurentoServerSideIceCandidate])		
		
case class KurentoUserSession(userId:String,userActor:LiftActor,sdpOffer:KurentoOffer) extends Logger {		
  val accepted = "accepted"		
  val rejected = "rejected"		
		
  protected var pipeline:Option[KurentoPipeline] = None		
  protected var webRtcEndpoint:Option[WebRtcEndpoint] = None		
		
  def getId:String = sdpOffer.id		
  def setMediaPipeline(p:KurentoPipeline):KurentoUserSession = {		
    webRtcEndpoint.foreach(_.release())		
    pipeline = Some(p)		
    val pipeId = p.name		
    val nwrtc = p.buildRtcEndpoint		
    nwrtc.addIceCandidateFoundListener(new KurentoEventListener[IceCandidateFoundEvent]((event:IceCandidateFoundEvent) => {		
      val response:JsonObject = new JsonObject()		
      response.addProperty("id","iceCandidate")		
      response.add("candidate",JsonUtils.toJsonObject(event.getCandidate()))		
      userActor ! KurentoServerSideIceCandidate(userId,pipeId,response.toString)		
    }))		
    val sdpAnswer = nwrtc.processOffer(sdpOffer.sdpOffer)		
    val responseSuccess = accepted		
    userActor ! KurentoAnswer(userId,pipeId,responseSuccess,sdpAnswer)		
    nwrtc.gatherCandidates()		
    webRtcEndpoint = Some(nwrtc)		
    trace("settingPipeline: %s, endpoint: %s".format(pipeline,webRtcEndpoint))		
    this		
  }		
  def getPipeline:Option[KurentoPipeline] = pipeline		
  def addIceCandidate(candidate:IceCandidate):KurentoUserSession = {		
    trace("addingIceCandidate: %s".format(candidate))		
    webRtcEndpoint.foreach(_.addIceCandidate(candidate))		
    this		
  }		
  def getWebRtcEndpoint:Option[WebRtcEndpoint] = webRtcEndpoint		
  def shutdown:Unit = {		
    pipeline.foreach(_.shutdown(webRtcEndpoint))		
  }		
}		
		
trait KurentoPipelineType {		
  def generatePipeline(client:KurentoManager,pipeline:MediaPipeline,name:String):KurentoPipeline		
}		
object Loopback extends KurentoPipelineType {		
  override def generatePipeline(client:KurentoManager,pipeline:MediaPipeline,name:String):KurentoPipeline = LoopbackPipeline(client,pipeline,name)		
}		
object Broadcast extends KurentoPipelineType {		
  override def generatePipeline(client:KurentoManager,pipeline:MediaPipeline,name:String):KurentoPipeline = BroadcastPipeline(client,pipeline,name)		
}		
object Roulette extends KurentoPipelineType {		
  override def generatePipeline(client:KurentoManager,pipeline:MediaPipeline,name:String):KurentoPipeline = RoulettePipeline(client,pipeline,name)		
}		
object GroupRoom extends KurentoPipelineType {		
  override def generatePipeline(client:KurentoManager,pipeline:MediaPipeline,name:String):KurentoPipeline = GroupRoomPipeline(client,pipeline,name)		
}		
object LargeGroupRoom extends KurentoPipelineType {		
  override def generatePipeline(client:KurentoManager,pipeline:MediaPipeline,name:String):KurentoPipeline = LargeGroupRoomPipeline(client,pipeline,name,2)		
}		
		
class KurentoEventListener[T <: Event](onStateChanged:T => Unit) extends EventListener[T]{
  override def onEvent(a:T) = onStateChanged(a)
}

class KurentoPipeline(val kurentoManager:KurentoManager,pipeline:MediaPipeline,val name:String) extends Logger {		
  protected val videoKbps = 256 // max send rate		
  protected val audioKbps = 10 // max send rate		
  def getGStreamerDot:String = {
    pipeline.getGstreamerDot()
  }
  def buildRtcEndpoint:WebRtcEndpoint = {		
    val wre = new WebRtcEndpoint.Builder(pipeline).build()		
    // setting video bandwidth doesn't appear to work in firefox etc		
//    wre.setMaxVideoSendBandwidth(videoKbps)		
//    wre.setMaxVideoRecvBandwidth(videoKbps)		
//    wre.setOutputBitrate((videoKbps + audioKbps) * 1024) // measured in bps		
//  setting the audio bandwidth doesn't appear implemented in Kurento, or maybe I'm using the wrong method signatures		
//    wre.setMaxAudioSendBandwidth(audioKbps)		
//    wre.setMaxAudioRecvBandwidth(audioKbps)		
    wre.addConnectionStateChangedListener(new KurentoEventListener[ConnectionStateChangedEvent]((stateChangedEvent:ConnectionStateChangedEvent) => {
      trace("connection (%s) state changed: %s => %s ::: %s".format(name,stateChangedEvent.getOldState,stateChangedEvent.getNewState,wre))
      (stateChangedEvent.getOldState(),stateChangedEvent.getNewState()) match {
        case (ConnectionState.CONNECTED,ConnectionState.DISCONNECTED) => {
        //  shutdown this connection when the connection state drops, so that the server can recover resources or relayout composites, etc.
          shutdown(Some(wre))
        }
        case _ => {}
      }
    }))
    wre.addMediaStateChangedListener(new KurentoEventListener[MediaStateChangedEvent]((stateChangedEvent:MediaStateChangedEvent) => {
      trace("mediaState (%s) state changed: %s => %s ::: %s".format(name,stateChangedEvent.getOldState(),stateChangedEvent.getNewState(),wre))
      (stateChangedEvent.getOldState(),stateChangedEvent.getNewState()) match {
        case (MediaState.CONNECTED,MediaState.DISCONNECTED) => {
//  shutdown this connection when the connection state drops, so that the server can recover resources or relayout composites, etc.
          trace("removing endpoint (%s): %s".format(name,wre))
          shutdown(Some(wre))
        }
        case _ => {}
      }
    }))
    // we're not using data channels!
    /*
    wre.addDataChannelOpenListener(new KurentoEventListener[DataChannelOpenEvent]((dce:DataChannelOpenEvent) => {
      trace("dataChannelOpen (%s) state changed: %s ::: %s".format(name,dce,wre))
    }))
    wre.addDataChannelCloseListener(new KurentoEventListener[DataChannelCloseEvent]((dce:DataChannelCloseEvent) => {
      trace("dataChannelClose (%s) state changed: %s ::: %s".format(name,dce,wre))
    }))
    */
    wre		
  }		
  def getPipeline:MediaPipeline = pipeline		
  def shutdown(rtc:Option[WebRtcEndpoint] = None):Unit = {		
    pipeline.release()		
  }		
}		
		
case class LoopbackPipeline(override val kurentoManager:KurentoManager,pipeline:MediaPipeline,override val name:String) extends KurentoPipeline(kurentoManager,pipeline,name) {		
  var thisVideo:Option[WebRtcEndpoint] = None		
  override def buildRtcEndpoint:WebRtcEndpoint = {		
    val newEndpoint = super.buildRtcEndpoint		
    newEndpoint.connect(newEndpoint)		
    thisVideo = Some(newEndpoint)		
    newEndpoint		
  }		
  override def shutdown(rtc:Option[WebRtcEndpoint] = None):Unit = {		
    kurentoManager.removePipeline(name,Loopback)		
    thisVideo.foreach(_.release())		
    super.shutdown(rtc)		
  }		
}		
		
case class RoulettePipeline(override val kurentoManager:KurentoManager,pipeline:MediaPipeline,override val name:String) extends KurentoPipeline(kurentoManager,pipeline,name) {		
  var a:Option[WebRtcEndpoint] = None		
  var b:Option[WebRtcEndpoint] = None		
  override def buildRtcEndpoint:WebRtcEndpoint = {		
    val newEndpoint = super.buildRtcEndpoint		
    if (a == None){		
      a = Some(newEndpoint)		
    } else if (b == None){		
      b = Some(newEndpoint)		
      a.foreach(e => {		
        e.connect(newEndpoint)		
        newEndpoint.connect(e)		
      })		
    } else {		
      a.foreach(_.release())		
      a = b		
      b = Some(newEndpoint)		
      a.foreach(e => {		
        e.connect(newEndpoint)		
        newEndpoint.connect(e)		
      })		
    }		
    newEndpoint		
  }		
  override def shutdown(rtc:Option[WebRtcEndpoint] = None):Unit = {		
    if (a.exists(_ == rtc)){		
      a.foreach(e => {		
        e.release()		
        a = b		
      })		
    }		
    if (b.exists(_ == rtc)){		
      b.foreach(e => {		
        e.release()		
      })		
    }		
    if (a == None && b == None){		
      kurentoManager.removePipeline(name,Roulette)		
      super.shutdown(rtc)		
    }		
  }		
}		
		
case class GroupRoomPipeline(override val kurentoManager:KurentoManager,pipeline:MediaPipeline,override val name:String) extends KurentoPipeline(kurentoManager,pipeline,name) {		
  protected var members:Map[WebRtcEndpoint,HubPort] = Map.empty[WebRtcEndpoint,HubPort]		
  protected val hub = new Composite.Builder(pipeline).build()		
  override def buildRtcEndpoint:WebRtcEndpoint = {		
    val newEndpoint = super.buildRtcEndpoint		
    val hubPort = new HubPort.Builder(hub).build()		
    hubPort.connect(newEndpoint)		
    newEndpoint.connect(hubPort)		
    members = members.updated(newEndpoint,hubPort)		

    println("<---")
    println(pipeline.getGstreamerDot())
    println("--->")

    newEndpoint		
  }		
  override def shutdown(rtc:Option[WebRtcEndpoint] = None):Unit = {		
    trace("removing from groupRoom (%s) rtc (%s)".format(name,rtc))
    rtc.foreach(r => {
      members.get(r).foreach(hubPort => {		
        trace("removing from groupRoom (%s) rtc (%s) (%s)".format(name,r,hubPort))
        r.release		
        hubPort.release		
      })		
      members = members - r	
    })
    if (members.keys.toList == Nil){		
      trace("removing groupRoom (%s)".format(name))
      kurentoManager.removePipeline(name,GroupRoom)		
      super.shutdown(rtc)		
    }		
  }		
}		

case class LargeGroupRoomPipeline(override val kurentoManager:KurentoManager,pipeline:MediaPipeline,override val name:String,lowerMaxSize:Int) extends KurentoPipeline(kurentoManager,pipeline,name) {
  import scala.collection.mutable.{ListBuffer => MList,HashMap => HMap}
  protected val masterHub = new Composite.Builder(pipeline).build()		

  protected val hubs = new HMap[Composite,Tuple2[List[HubPort],HMap[WebRtcEndpoint,List[HubPort]]]]()
  protected val members = new HMap[WebRtcEndpoint,Composite]()

  override def buildRtcEndpoint:WebRtcEndpoint = {		
    val m = super.buildRtcEndpoint		
  
    val lowerHub = hubs.toList.find(_._2._2.keys.toList.length < Math.max(1,lowerMaxSize)).map(_._1).getOrElse({
      val lowerHub = new Composite.Builder(pipeline).build()
      val vusp = new HubPort.Builder(masterHub).build()
      val dsp = new HubPort.Builder(lowerHub).build()
      dsp.connect(vusp)
      val ports = List(vusp,dsp)
      hubs.put(lowerHub,(ports,new HMap[WebRtcEndpoint,List[HubPort]]()))
      println("constructed new hub: %s ] : %s".format(lowerHub,ports))
      lowerHub
    })
    members.put(m,lowerHub)
    hubs.get(lowerHub).foreach(hcTup => {
      val hc = hcTup._2
      val ausp = new HubPort.Builder(masterHub).build()
      val dsp = new HubPort.Builder(lowerHub).build()
      m.connect(dsp,MediaType.VIDEO)
      m.connect(ausp,MediaType.AUDIO)
      val ousp = new HubPort.Builder(masterHub).build()
      ousp.connect(m)
      val ports = List(ausp,dsp)
      println("connected (%s) to (%s ]: %s)".format(m,hc,ports))
      hc.put(m,ports)
    })
    println("newState\r\nmembers: %s\r\nhubs: %s\r\n".format(members.keys.toList.length,hubs.keys.toList.length))
    m		
  }		
  override def shutdown(rtc:Option[WebRtcEndpoint] = None):Unit = {		
    println("removing from groupRoom (%s) rtc (%s)".format(name,rtc))
    rtc.map(r => {
      members.get(r).foreach(comp => {
        hubs.get(comp).foreach(hubConnectorsTup => {
          val hubConnectors = hubConnectorsTup._2
          hubConnectors.get(r).foreach(portsToShutdown => {
            println("shutting down ports (%s)".format(portsToShutdown))
            portsToShutdown.foreach(_.release)
          })
          hubConnectors - r
          if (hubConnectors.keys.toList.length == 0){
            println("releasing hub: %s".format(comp))
            hubConnectorsTup._1.foreach(_.release)
            hubs - comp
            comp.release
          }
        })
      })
      members - r
      if (members.keys.toList == Nil){
        masterHub.release
        println("removing groupRoom (%s)".format(name))
        kurentoManager.removePipeline(name,GroupRoom)		
        super.shutdown(rtc)		
      }
    }).getOrElse({
      //full shutdown of the pipeline
      hubs.foreach(ht => {
        val lowerHub = ht._1
        val lowerHubPorts = ht._2._1
        val lowerHubMembers = ht._2._2
        lowerHubMembers.toList.foreach(lhm => {
          lhm._2.foreach(_.release)
          lhm._1.release
        })
        lowerHubPorts.foreach(_.release)
        lowerHub.release
      })
      masterHub.release
      println("removing groupRoom (%s)".format(name))
      kurentoManager.removePipeline(name,GroupRoom)		
      super.shutdown(rtc)		

    })
  }		
}	


case class MeTLGroupRoomPipeline(override val kurentoManager:KurentoManager,pipeline:MediaPipeline,override val name:String, val recorderUrl:String) extends KurentoPipeline(kurentoManager,pipeline,name) {		
  protected var members:Map[WebRtcEndpoint,HubPort] = Map.empty[WebRtcEndpoint,HubPort]		
  protected val hub = new Composite.Builder(pipeline).build()		
  override def buildRtcEndpoint:WebRtcEndpoint = {		
    val newEndpoint = super.buildRtcEndpoint		
    val hubPort = new HubPort.Builder(hub).build()		
    hubPort.connect(newEndpoint)		
    newEndpoint.connect(hubPort)
    val archiveEndpoint = new RecorderEndpoint.Builder(pipeline,recorderUrl).build()
    newEndpoint.connect(archiveEndpoint)
    members = members.updated(newEndpoint,hubPort)		
    newEndpoint		
  }		
  override def shutdown(rtc:Option[WebRtcEndpoint] = None):Unit = {		
    rtc.foreach(r => {
      members.get(r).foreach(hubPort => {		
        r.release		
        hubPort.release		
      })		
      members = members - r		
    })
    if (members.keys.toList.length < 1){		
      kurentoManager.removePipeline(name,GroupRoom)		
      super.shutdown(rtc)		
    }		
  }		
}		
case class BroadcastPipeline(override val kurentoManager:KurentoManager,pipeline:MediaPipeline,override val name:String) extends KurentoPipeline(kurentoManager,pipeline,name) {		
  protected var sender:Option[WebRtcEndpoint] = None		
//  protected var dispatcher:Option[DispatcherOneToMany] = None		
  protected var receivers:List[WebRtcEndpoint] = Nil		
  override def buildRtcEndpoint:WebRtcEndpoint = {		
    val newEndpoint = super.buildRtcEndpoint		
    /*		
    dispatcher.map(s => {		
      //s.connect(newEndpoint)		
      val ePort = new HubPort.Builder(s).build()		
      ePort.connect(newEndpoint)		
      newEndpoint.connect(ePort)		
      receivers = newEndpoint :: receivers		
      trace("adding listener to broadcast: %s".format(name))		
    }).getOrElse({		
      trace("adding sender to broadcast: %s".format(name))		
      sender = Some(newEndpoint)		
      val d = new DispatcherOneToMany.Builder(pipeline).build()		
      val sourcePort = new HubPort.Builder(d).build()		
      sourcePort.connect(newEndpoint)		
      newEndpoint.connect(sourcePort)		
      d.setSource(sourcePort)		
      //newEndpoint.connect(d)		
      dispatcher = Some(d)		
    })		
    */		
    sender.map(s => {		
      s.connect(newEndpoint)		
      receivers = newEndpoint :: receivers		
      trace("adding listener to broadcast: %s".format(name))		
    }).getOrElse({		
      trace("adding sender to broadcast: %s".format(name))		
      sender = Some(newEndpoint)		
    })		
    newEndpoint		
  }		
  override def shutdown(rtc:Option[WebRtcEndpoint] = None):Unit = {		
    trace("removing from broadcast (%s) rtc (%s)".format(name,rtc))
    if (sender == rtc){		
      sender.foreach(_.release())		
      sender = None		
    }		
    val (toClose,remaining) = receivers.partition(r => rtc.exists(_ == r))		
    toClose.foreach(_.release())		
    receivers = remaining		
    if (receivers == Nil){		
      kurentoManager.removePipeline(name,Broadcast)		
      super.shutdown(rtc)		
    }		
  }		
}		
		
trait KurentoManager {
  def getPipeline(name:String,pipeType:KurentoPipelineType):Option[KurentoPipeline]
  def removePipeline(name:String,pipeType:KurentoPipelineType)		
  def getPipelines:List[KurentoPipeline]
  def shutdown
}

class RemoteKurentoManager(kmsUrl:String) extends KurentoManager with Logger {
  protected lazy val client:KurentoClient = {
    val kurento:KurentoClient = KurentoClient.create(kmsUrl, new KurentoConnectionListener() {		
      override def reconnected(sameServer:Boolean):Unit = {		
        warn("kurento reconnected: %s".format(sameServer));		
      }		
      override def disconnected:Unit = {		
        warn("kurento disconnected");		
      }		
      override def connectionFailed:Unit = {		
        warn("kurento connectionFailed");		
      }		
      override def connected:Unit = {		
        warn("kurento connected") 		
      }		
    })		
    kurento
  }		
  protected val pipelines = new java.util.concurrent.ConcurrentHashMap[Tuple2[KurentoPipelineType,String],KurentoPipeline]		
  override def getPipelines:List[KurentoPipeline] = pipelines.entrySet.toArray.toList.flatMap{
    case pTup:java.util.Map.Entry[Tuple2[KurentoPipelineType,String],KurentoPipeline] => Some(pTup.getValue)
    case _ => None
  }
  override def getPipeline(name:String,pipeType:KurentoPipelineType):Option[KurentoPipeline] = {
    val thisKm = this
    Some(pipelines.computeIfAbsent((pipeType,name),new java.util.function.Function[Tuple2[KurentoPipelineType,String],KurentoPipeline]{		
      override def apply(k:Tuple2[KurentoPipelineType,String]):KurentoPipeline = {		
        val newPipeline = k._1.generatePipeline(thisKm,client.createMediaPipeline(),k._2)		
        info("generated new pipeline: %s => %s".format(k,newPipeline))		
        newPipeline		
      }		
    }))
  }
  override def removePipeline(name:String,pipeType:KurentoPipelineType) = {		
    info("removing pipeline: %s, %s".format(name,pipeType))		
    pipelines.remove((name,pipeType))		
  }		
  override def shutdown = {
    pipelines.entrySet.toArray.foreach{
      case pTup:java.util.Map.Entry[Tuple2[KurentoPipelineType,String],KurentoPipeline] => {
        pTup.getValue.shutdown()
        pipelines.remove(pTup.getKey)
      }
    }
  }
}		
 	
object EmptyKurentoManager extends KurentoManager {
  override def getPipeline(name:String,pipeType:KurentoPipelineType):Option[KurentoPipeline] = None
  override def removePipeline(name:String,pipeType:KurentoPipelineType) = {}		
  override def getPipelines:List[KurentoPipeline] = Nil
  override def shutdown = {}
}

trait KurentoUtils {		
  lazy implicit val kurentoFormats = Serialization.formats(NoTypeHints)		
  protected def kurentoManager = Globals.kurentoManager		
  def candidateFromJValue(jObj:JValue):Option[IceCandidate] = {		
    try {		
      val candidateId = (jObj \ "candidate").extract[String]		
      val sdpMid = (jObj \ "sdpMid").extract[String]		
      val sdpMLineIndex = (jObj \ "sdpMLineIndex").extract[Int]		
      Some(new IceCandidate(candidateId,sdpMid,sdpMLineIndex))		
    } catch {		
      case e:Exception => None		
    }		
  }		
  def getPipeline(name:String,pipeType:String):Option[KurentoPipeline] = {		
    (pipeType match {		
        case "loopback" => Some(Loopback)		
        case "broadcast" => Some(Broadcast)		
        case "roulette" => Some(Roulette)		
        case "groupRoom" => Some(GroupRoom)		
        case "largeGroupRoom" => Some(LargeGroupRoom)
        case _ => None		
    }).flatMap(pipelineType => kurentoManager.getPipeline(name,pipelineType))		
  }		
}
