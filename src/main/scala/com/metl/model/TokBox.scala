package com.metl.model

import com.opentok.{OpenTok,MediaMode,ArchiveMode,Session,SessionProperties,TokenOptions,Role,Archive,ArchiveProperties}

import net.liftweb.json._
import net.liftweb.json.DefaultFormats
import net.liftweb.json.JsonDSL._
import com.ning.http.client.Response
import java.util.concurrent.Future
import net.liftweb.common._

object TokRole extends Enumeration {
  type TokRole = Value
  val Subscriber,Publisher,Moderator = Value
}

case class TokBoxSession(apiKey:Int,sessionId:String,token:String,description:String,username:String)

object TokBroadcastLayout {
  val bestFit = "bestFit"
  val horizontalPresentation = "horizontalPresentation"
  val pip = "pip"
  val verticalPresentation = "verticalPresentation"
}
case class TokBoxBroadcastUrl(hls:Option[String])
case class TokBoxBroadcast(id:String,sessionId:String,partnerId:String,createdAt:Long,updatedAt:Long,broadcastUrls:Option[TokBoxBroadcastUrl],status:Option[String])

case class TokBoxArchive(session:TokBoxSession,id:String,name:String,hasAudio:Boolean,hasVideo:Boolean,composed:Boolean,url:Option[String] = None,size:Option[Int] = None, duration:Option[Int] = None,createdAt:Option[Long] = None,status:Option[String] = None,reason:Option[String] = None)

class TokBox(apiKey:Int,secret:String) extends Logger {
  val openTok = new OpenTok(apiKey,secret)
  val client = new com.opentok.util.HttpClient.Builder(apiKey,secret).build()
  protected var sessions:Map[String,Session] = Map.empty[String,Session]
  protected var broadcasts:Map[String,TokBoxBroadcast] = Map.empty[String,TokBoxBroadcast]
  def getSessionToken(description:String,role:TokRole.Value = TokRole.Subscriber):Either[Exception,TokBoxSession] = {
    try {
      val session = sessions.get(description).getOrElse({
        val newSession = openTok.createSession(
          new SessionProperties.Builder()
            .mediaMode(MediaMode.ROUTED)
    //        .location(description)
            .archiveMode(ArchiveMode.ALWAYS)
            .build()
          )
  //      startBroadcast(newSession,TokBroadcastLayout.bestFit)
        sessions = sessions.updated(description,newSession)
        newSession
      })
      val sessionId = session.getSessionId()
      val sessionToken = session.generateToken(
        new TokenOptions.Builder()
          .role(role match {
            case TokRole.Moderator => Role.MODERATOR
            case TokRole.Publisher => Role.PUBLISHER
            case _ => Role.SUBSCRIBER
          })
          .expireTime((System.currentTimeMillis() / 1000L) + (7 * 24 * 60 * 60)) // 1 week from now
          .data("name=%s&description=%s".format(Globals.currentUser.is,description))
        .build()
      )
      Right(TokBoxSession(apiKey,sessionId,sessionToken,description,Globals.currentUser.is))
    } catch {
      case e:Exception => Left(e)
    }
  }
  def startArchive(session:TokBoxSession):TokBoxArchive = {
    val archiveName = "%s_%s_%s".format(session.description,session.username,new java.util.Date().getTime().toString())
    val outputMode = Archive.OutputMode.COMPOSED // Archive.OutputMode.INDIVIDUAL
    val hasAudio = true
    val hasVideo = true
    val archive = openTok.startArchive(session.sessionId,new ArchiveProperties.Builder().name(archiveName).hasAudio(hasAudio).hasVideo(hasVideo).outputMode(outputMode).build())
    TokBoxArchive(session,archive.getId,archiveName,hasAudio,hasVideo,outputMode == Archive.OutputMode.COMPOSED)
  }
  def stopArchive(session:TokBoxSession,archive:TokBoxArchive):TokBoxArchive = {
    val a = openTok.stopArchive(archive.id)
    archive
  }
  def getArchives(session:TokBoxSession):List[TokBoxArchive] = {
    openTok.listArchives().toArray().toList.flatMap(aObj => {
      aObj match {
        case a:Archive => Some(TokBoxArchive(session,a.getId,a.getName,a.hasAudio,a.hasVideo,a.getOutputMode == Archive.OutputMode.COMPOSED,Some(a.getUrl),Some(a.getSize),Some(a.getDuration),Some(a.getCreatedAt)))
        case _ => None
      }
    })
  }
  class HttpException(code:Int,response:Response) extends Exception("%s : %s".format(code,response.getResponseBody))
  //REST API calls - not included in the java sdk
  protected def interpretResponse(req:Future[Response]):Either[Exception,String] = {
    try {
      val response = req.get()
      response.getStatusCode match {
        case i:Int if (i >= 200 && i < 300) => Right(response.getResponseBody())
        case code => Left(new HttpException(code,response))
      }
    } catch {
      case e:Exception => Left(e)
    }
  }
  protected def performGet(url:String,headers:List[Tuple2[String,String]] = Nil):Either[Exception,String] = {
    interpretResponse(headers.foldLeft(client.prepareGet(url))((acc,item) => acc.setHeader(item._1,item._2)).execute())
  }
  protected def performPost(url:String,body:String,headers:List[Tuple2[String,String]] = Nil):Either[Exception,String] = {
    interpretResponse(headers.foldLeft(client.preparePost(url).setBody(body))((acc,item) => acc.setHeader(item._1,item._2)).execute())
  }
  protected def performPut(url:String,body:String,headers:List[Tuple2[String,String]] = Nil):Either[Exception,String] = {
    interpretResponse(headers.foldLeft(client.preparePut(url).setBody(body))((acc,item) => acc.setHeader(item._1,item._2)).execute())
  }
  protected def performDelete(url:String,headers:List[Tuple2[String,String]] = Nil):Either[Exception,String] = {
    interpretResponse(headers.foldLeft(client.prepareDelete(url))((acc,item) => acc.setHeader(item._1,item._2)).execute())
  }
  implicit val formats = DefaultFormats
  protected val baseUrl = "https://api.opentok.com"
  def getArchive(session:TokBoxSession,archiveId:String):Option[TokBoxArchive] = {
    performGet("%s/v2/partner/%s/archive/%s".format(baseUrl,session.apiKey,archiveId)) match {
      case Right(s) => {
        Some(parse(s).extract[TokBoxArchive])
      }
      case Left(e) => {
        error("tokBox getArchive(%s,%s)".format(session,archiveId),e)
        None
      }
    }
  } 
  def removeArchive(session:TokBoxSession,archiveId:String):Boolean = {
    performDelete("%s/v2/partner/%s/archive/%s".format(baseUrl,session.apiKey,archiveId)) match {
      case Right(s) => true
      case Left(e) => {
        error("tokBox removeArchive(%s,%s)".format(session,archiveId),e)
        false
      }
    }
  }
  def getBroadcast(session:TokBoxSession):Option[TokBoxBroadcast] = {
    broadcasts.get(session.sessionId)
  }
  def startBroadcast(session:TokBoxSession,layout:String):TokBoxBroadcast = {
    getBroadcast(session).getOrElse({
      val body = pretty(render(
        ("sessionId" -> session.sessionId) ~ ("layout" -> {
          ("type" -> layout)
        })
      ))
      performPost("%s/v2/partner/%s/broadcast".format(baseUrl,session.apiKey),body,List(("Content-Type","application/json"))) match {
        case Right(s) => {
          val newBroadcast = parse(s).extract[TokBoxBroadcast]
          broadcasts = broadcasts.updated(session.sessionId,newBroadcast)
          newBroadcast
        }
        case Left(e) => throw e
      }
    })
  }
  def updateBroadcast(session:TokBoxSession,broadcastId:String,layout:String):TokBoxBroadcast = {
    val body = pretty(render(
      ("type" -> layout)
    ))
    performPut("%s/v2/partner/%s/broadcast/%s/layout".format(baseUrl,session.apiKey,broadcastId),body,List(("Content-Type","application/json"))) match {
      case Right(s) => parse(s).extract[TokBoxBroadcast]
      case Left(e) => throw e
    }
  }
  def getBroadcast(session:TokBoxSession,broadcastId:String):TokBoxBroadcast = {
    performGet("%s/v2/partner/%s/broadcast/%s".format(baseUrl,session.apiKey,broadcastId)) match {
      case Right(s) => parse(s).extract[TokBoxBroadcast]
      case Left(e) => throw e
    }
  }
  def stopBroadcast(session:TokBoxSession,broadcastId:String):TokBoxBroadcast = {
    val body = ""
    performPost("%s/v2/partner/%s/broadcast/%s/stop".format(baseUrl,session.apiKey,broadcastId),body,List(("Content-Type","application/json"))) match {
      case Right(s) => parse(s).extract[TokBoxBroadcast]
      case Left(e) => throw e
    }
  }
}
