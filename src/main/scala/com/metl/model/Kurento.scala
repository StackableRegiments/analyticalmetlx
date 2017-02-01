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
		
import org.kurento.client.{EventListener,IceCandidate,IceCandidateFoundEvent,KurentoClient,KurentoConnectionListener,MediaPipeline,WebRtcEndpoint,Properties,Composite,HubPort,DispatcherOneToMany,RecorderEndpoint}		
import org.kurento.jsonrpc.JsonUtils;		
/*		
 -import org.springframework.beans.factory.annotation.Autowired;		
 -import org.springframework.web.socket.CloseStatus;		
 -import org.springframework.web.socket.TextMessage;		
 -import org.springframework.web.socket.WebSocketSession;		
 -import org.springframework.web.socket.handler.TextWebSocketHandler;		
*/		
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
    //var candidates:List[KurentoServerSideIceCandidate] = Nil		
    //var lastCandidateAdded = new java.util.Date().getTime()		
    nwrtc.addIceCandidateFoundListener(new EventListener[IceCandidateFoundEvent](){		
      override def onEvent(event:IceCandidateFoundEvent):Unit = {		
        val response:JsonObject = new JsonObject()		
        response.addProperty("id","iceCandidate")		
        response.add("candidate",JsonUtils.toJsonObject(event.getCandidate()))		
        userActor ! KurentoServerSideIceCandidate(userId,pipeId,response.toString)		
        //candidates = candidates ::: List(KurentoServerSideIceCandidate(userId,pipeId,response.toString))		
        //lastCandidateAdded = new java.util.Date().getTime()		
      }		
    })		
    val sdpAnswer = nwrtc.processOffer(sdpOffer.sdpOffer)		
    val responseSuccess = accepted		
    userActor ! KurentoAnswer(userId,pipeId,responseSuccess,sdpAnswer)		
    nwrtc.gatherCandidates()		
    /*		
    var threshold = 3 * 1000		
    while (lastCandidateAdded > (new java.util.Date().getTime() - threshold)){		
    }		
    userActor ! KurentoChannelDefinition(userId,pipeId,sdpAnswer,candidates)		
    */		
    webRtcEndpoint = Some(nwrtc)		
    //println("settingPipeline: %s, endpoint: %s".format(pipeline,webRtcEndpoint))		
    this		
  }		
  def getPipeline:Option[KurentoPipeline] = pipeline		
  def addIceCandidate(candidate:IceCandidate):KurentoUserSession = {		
    //println("addingIceCandidate: %s".format(candidate))		
    webRtcEndpoint.foreach(_.addIceCandidate(candidate))		
    this		
  }		
  def getWebRtcEndpoint:Option[WebRtcEndpoint] = webRtcEndpoint		
  def shutdown:Unit = {		
    webRtcEndpoint.foreach(rtc => {		
      pipeline.foreach(_.shutdown(rtc))		
      //rtc.release()		
    })		
  }		
}		
		
trait KurentoPipelineType {		
  def generatePipeline(name:String):KurentoPipeline		
}		
object Loopback extends KurentoPipelineType {		
  override def generatePipeline(name:String):KurentoPipeline = LoopbackPipeline(name)		
}		
object Broadcast extends KurentoPipelineType {		
  override def generatePipeline(name:String):KurentoPipeline = BroadcastPipeline(name)		
}		
object Roulette extends KurentoPipelineType {		
  override def generatePipeline(name:String):KurentoPipeline = RoulettePipeline(name)		
}		
object GroupRoom extends KurentoPipelineType {		
  override def generatePipeline(name:String):KurentoPipeline = GroupRoomPipeline(name)		
}		
		
class KurentoPipeline(val name:String) extends Logger {		
  protected val videoKbps = 500 // max send rate		
  protected val audioKbps = 10 // max send rate		
  protected val pipeline = KurentoManager.client.createMediaPipeline()		
  def buildRtcEndpoint:WebRtcEndpoint = {		
    val wre = new WebRtcEndpoint.Builder(pipeline).build()		
    // setting video bandwidth doesn't appear to work in firefox etc		
//    wre.setMaxVideoSendBandwidth(videoKbps)		
//    wre.setMaxVideoRecvBandwidth(videoKbps)		
//  setting the audio bandwidth doesn't appear implemented in Kurento, or maybe I'm using the wrong method signatures		
//    wre.setMaxAudioSendBandwidth(audioKbps)		
//    wre.setMaxAudioRecvBandwidth(audioKbps)		
    wre		
  }		
  def getPipeline:MediaPipeline = pipeline		
  def shutdown(rtc:WebRtcEndpoint):Unit = {		
    pipeline.release()		
  }		
}		
		
case class LoopbackPipeline(override val name:String) extends KurentoPipeline(name) {		
  var thisVideo:Option[WebRtcEndpoint] = None		
  override def buildRtcEndpoint:WebRtcEndpoint = {		
    val newEndpoint = super.buildRtcEndpoint		
    newEndpoint.connect(newEndpoint)		
    thisVideo = Some(newEndpoint)		
    newEndpoint		
  }		
  override def shutdown(rtc:WebRtcEndpoint):Unit = {		
    KurentoManager.removePipeline(name,Loopback)		
    thisVideo.foreach(_.release())		
    super.shutdown(rtc)		
  }		
}		
		
case class RoulettePipeline(override val name:String) extends KurentoPipeline(name) {		
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
  override def shutdown(rtc:WebRtcEndpoint):Unit = {		
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
      KurentoManager.removePipeline(name,Roulette)		
      super.shutdown(rtc)		
    }		
  }		
}		
		
case class GroupRoomPipeline(override val name:String) extends KurentoPipeline(name) {		
  protected var members:Map[WebRtcEndpoint,HubPort] = Map.empty[WebRtcEndpoint,HubPort]		
  protected val hub = new Composite.Builder(pipeline).build()		
  override def buildRtcEndpoint:WebRtcEndpoint = {		
    val newEndpoint = super.buildRtcEndpoint		
    val hubPort = new HubPort.Builder(hub).build()		
    hubPort.connect(newEndpoint)		
    newEndpoint.connect(hubPort)		
    members = members.updated(newEndpoint,hubPort)		
    newEndpoint		
  }		
  override def shutdown(rtc:WebRtcEndpoint):Unit = {		
    members.get(rtc).foreach(hubPort => {		
      rtc.release		
      hubPort.release		
    })		
    members = members - rtc		
    if (members.keys.toList.length < 1){		
      KurentoManager.removePipeline(name,GroupRoom)		
      super.shutdown(rtc)		
    }		
  }		
}		
case class MeTLGroupRoomPipeline(override val name:String, val recorderUrl:String) extends KurentoPipeline(name) {		
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
  override def shutdown(rtc:WebRtcEndpoint):Unit = {		
    members.get(rtc).foreach(hubPort => {		
      rtc.release		
      hubPort.release		
    })		
    members = members - rtc		
    if (members.keys.toList.length < 1){		
      KurentoManager.removePipeline(name,GroupRoom)		
      super.shutdown(rtc)		
    }		
  }		
}		
case class BroadcastPipeline(override val name:String) extends KurentoPipeline(name) {		
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
      println("adding listener to broadcast: %s".format(name))		
    }).getOrElse({		
      println("adding sender to broadcast: %s".format(name))		
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
      println("adding listener to broadcast: %s".format(name))		
    }).getOrElse({		
      println("adding sender to broadcast: %s".format(name))		
      sender = Some(newEndpoint)		
    })		
    newEndpoint		
  }		
  override def shutdown(rtc:WebRtcEndpoint):Unit = {		
    if ((sender.toList ::: receivers).filterNot(_ == rtc) == Nil){		
      KurentoManager.removePipeline(name,Broadcast)		
      super.shutdown(rtc)		
    }		
    if (sender.exists(_ == rtc)){		
      sender.foreach(_.release())		
      sender = None		
    }		
    val (toClose,remaining) = receivers.partition(_ == rtc)		
    toClose.foreach(_.release())		
    receivers = remaining		
  }		
}		
		
object KurentoManager extends KurentoManager("ws://kurento.stackableregiments.com:8888/kurento")		
//object KurentoManager extends KurentoManager("ws://52.20.87.206:8888/kurento")		
class KurentoManager(kmsUrl:String) extends Logger {		
  lazy val client = {		
    val kurento:KurentoClient = KurentoClient.create(kmsUrl, new KurentoConnectionListener() {		
      override def reconnected(sameServer:Boolean):Unit = {		
        println("kurento reconnected: %s".format(sameServer));		
      }		
      override def disconnected:Unit = {		
        println("kurento disconnected");		
      }		
      override def connectionFailed:Unit = {		
        println("kurento connectionFailed");		
      }		
      override def connected:Unit = {		
        println("kurento connected") 		
      }		
    });		
    kurento		
  }		
  protected val pipelines = new java.util.concurrent.ConcurrentHashMap[Tuple2[KurentoPipelineType,String],KurentoPipeline]		
  def getPipeline(name:String,pipeType:KurentoPipelineType):KurentoPipeline = pipelines.computeIfAbsent((pipeType,name),new java.util.function.Function[Tuple2[KurentoPipelineType,String],KurentoPipeline]{		
    override def apply(k:Tuple2[KurentoPipelineType,String]):KurentoPipeline = {		
      val newPipeline = k._1.generatePipeline(k._2)		
      println("generated new pipeline: %s => %s".format(k,newPipeline))		
      newPipeline		
    }		
  })		
  def removePipeline(name:String,pipeType:KurentoPipelineType) = {		
    println("removing pipeline: %s, %s".format(name,pipeType))		
    pipelines.remove((name,pipeType))		
  }		
}		
 	
trait KurentoUtils {		
  lazy implicit val kurentoFormats = Serialization.formats(NoTypeHints)		
  protected def kurentoClient = KurentoManager.client		
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
        case _ => None		
    }).map(pipelineType => KurentoManager.getPipeline(name,pipelineType))		
  }		
}
