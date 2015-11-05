package com.metl.view

import com.metl.data._
import com.metl.utils._


import _root_.net.liftweb._
import http._
import common._
import java.io.{ByteArrayOutputStream,ByteArrayInputStream,BufferedInputStream}
import javax.imageio._
import java.io.FileReader
import org.apache.commons.io.IOUtils
import scala.xml._
import java.util.zip.{ZipInputStream,ZipEntry}
import scala.collection.mutable.StringBuilder
import net.liftweb.util.Helpers._
import bootstrap.liftweb.Boot

import com.metl.model._

/**
  * Use Lift's templating without a session and without state
  */
object StatelessHtml {
  val serializer = new GenericXmlSerializer("rest")
  private val fakeSession = new LiftSession("/", "fakeSession", Empty)
  private val config = ServerConfiguration.default

  def summaries(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.summaries", () => {
    val xml: Box[NodeSeq] = S.init(req, fakeSession) {
      S.runTemplate(List("summaries"))
    }
    xml.map(ns => XhtmlResponse(ns(0), Empty, Nil, Nil, 200, false))
  })

  def appCache(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.appCache", () => {
    S.request.flatMap(_.request match {
      case hrs: net.liftweb.http.provider.servlet.HTTPRequestServlet =>{
        val file = hrs.req.getRealPath("static/offline/analytics.appcache")
        println(file)
        val reader = new FileReader(file)
        val content = IOUtils.toByteArray(reader)
        println(IOUtils.toString(content))
        reader.close
        Full(InMemoryResponse(content,List(("Content-Type","text/cache-manifest")),Nil,200))
      }
      case _ => Empty
    })
  })

  def loadSearch(query:String,config:ServerConfiguration = ServerConfiguration.default):Node = Stopwatch.time("StatelessHtml.loadSearch", () => {
    <conversations>{config.searchForConversation(query).map(c => serializer.fromConversation(c))}</conversations>
  })

  def search(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.search", () => {
    req.param("query").map(q=>XmlResponse(loadSearch(q)))
  })

  def proxyDataUri(slideJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxyDataUri(%s)".format(identity), () =>
    Full(MeTLXConfiguration.getRoom(slideJid,config.name,RoomMetaDataUtils.fromJid(slideJid)).getHistory.getImageByIdentity(identity).map(image => {
      image.imageBytes.map(bytes => {
        println("found bytes: %s (%s)".format(bytes,bytes.length))
        val dataUri = "data:image/png;base64,"+net.liftweb.util.SecurityHelpers.base64Encode(bytes)
        InMemoryResponse(IOUtils.toByteArray(dataUri),Boot.cacheStrongly,Nil,200)
      }).openOr(NotFoundResponse("image bytes not available"))
    }).getOrElse(NotFoundResponse("image not available"))))

  def proxy(slideJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxy(%s)".format(identity), () => {
    Full(MeTLXConfiguration.getRoom(slideJid,config.name,RoomMetaDataUtils.fromJid(slideJid)).getHistory.getImageByIdentity(identity).map(image => {
      image.imageBytes.map(bytes => {
        println("found bytes: %s (%s)".format(bytes,bytes.length))
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        InMemoryResponse(bytes,headers,Nil,200)
      }).openOr(NotFoundResponse("image bytes not available"))
    }).getOrElse(NotFoundResponse("image not available")))
  })
  def quizProxy(conversationJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.quizProxy()".format(conversationJid,identity), () => {
    Full(MeTLXConfiguration.getRoom(conversationJid,config.name,ConversationRoom(config.name,conversationJid)).getHistory.getQuizByIdentity(identity).map(quiz => {
      quiz.imageBytes.map(bytes => {
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        InMemoryResponse(bytes,headers,Nil,200)
      }).openOr(NotFoundResponse("quiz image bytes not available"))
    }).getOrElse(NotFoundResponse("quiz not available")))
  })
  def resourceProxy(identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.resourceProxy(%s)".format(identity), () => {
    val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
    Full(InMemoryResponse(config.getResource(identity),headers,Nil,200))
  })
  def submissionProxy(conversationJid:String,author:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.submissionProxy()".format(conversationJid,identity), () => {
    Full(MeTLXConfiguration.getRoom(conversationJid,config.name,ConversationRoom(config.name,conversationJid)).getHistory.getSubmissionByAuthorAndIdentity(author,identity).map(sub => {
      sub.imageBytes.map(bytes => {
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        InMemoryResponse(bytes,headers,Nil,200)
      }).openOr(NotFoundResponse("submission image bytes not available"))
    }).getOrElse(NotFoundResponse("submission not available")))
  })
  def loadDetails(jid:String,config:ServerConfiguration = ServerConfiguration.default):Node = Stopwatch.time("StatelessHtml.loadDetails", () => {
    serializer.fromConversation(config.detailsOfConversation(jid)).head
  })
  def details(jid:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.details", () => {
    Full(XmlResponse(loadDetails(jid)))
  })

  def loadHistory(jid:String):Node= Stopwatch.time("StatelessHtml.loadHistory(%s)".format(jid), () => {
    <history>{serializer.fromRenderableHistory(MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory)}</history>
  })
  def loadThemes(jid:String):Node = Stopwatch.time("StatelessHtml.loadThemes(%s)".format(jid), () => {
    val history = MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory
    val inks = history.getInks
    <userThemes>{
      inks.groupBy(_.author).map
      {
        case (author,inks@i :: is) => <userTheme>
          <user>{author}</user>
          {
            <themes>{ CanvasContentAnalysis.extract(inks).map(i => <theme>{i}</theme> ) }</themes>
          }
          </userTheme>
        case _ => None
      }
    }</userThemes>
  })
  def loadMergedHistory(jid:String,username:String):Node = Stopwatch.time("StatelessHtml.loadMergedHistory(%s)".format(jid),() => {
    <history>{serializer.fromRenderableHistory(MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory.merge(MeTLXConfiguration.getRoom(jid+username,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory))}</history>
  })
  def themes(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.themes(%s)".format(req.param("source")), () => {
    req.param("source").map(jid=> XmlResponse(loadThemes(jid)))
  })
  def history(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.history(%s)".format(req.param("source")), () => {
    req.param("source").map(jid=> XmlResponse(loadHistory(jid)))
  })
  def fullHistory(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.fullHistory(%s)".format(req.param("source")), () => {
    req.param("source").map(jid => XmlResponse(<history>{MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory.getAll.map(s => serializer.fromMeTLData(s))}</history>))
  })
  def mergedHistory(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.history(%s)".format(req.param("source")), () => {
    req.param("source").map(jid=> {
      req.param("username").map(user => {
        XmlResponse(loadMergedHistory(jid,user))
      }).getOrElse(NotFoundResponse("username not provided"))
    })
  })
  def addGroupTo(onBehalfOfUser:String,conversation:String,slideId:String,groupDef:GroupSet):Box[LiftResponse] = Stopwatch.time("StatelessHtml.addGroupTo(%s,%s)".format(conversation,slideId),() => {
    val conv = config.detailsOfConversation(conversation)
    for (
      slide <- conv.slides.find(_.id.toString == slideId);
      updatedConv = config.updateConversation(conversation,conv.copy(slides = {
        slide.copy(groupSet = groupDef :: slide.groupSet) :: conv.slides.filterNot(_.id.toString == slideId)
      }));
      node <- serializer.fromConversation(updatedConv).headOption
    ) yield {
      XmlResponse(node)
    }
  })
  def duplicateSlide(onBehalfOfUser:String,slide:String,conversation:String):Box[LiftResponse] = {
    val conv = config.detailsOfConversation(conversation)
    if (onBehalfOfUser == conv.author){
      conv.slides.find(_.id.toString == slide).map(slide => {
        val step1 = config.addSlideAtIndexOfConversation(conversation,slide.index + 1)
        step1.slides.find(_.index == slide.index + 1).foreach(newSlide => {
          val oldId = slide.id
          val newId = newSlide.id
          ServerSideBackgroundWorker ! CopyContent(
            config,
            SlideRoom(conv.server.name,conversation,oldId),
            SlideRoom(conv.server.name,conversation,newId),
            (s:MeTLStanza) => s.author == conv.author
          )
          ServerSideBackgroundWorker ! CopyContent(
            config,
            PrivateSlideRoom(conv.server.name,conversation,oldId,conv.author),
            PrivateSlideRoom(conv.server.name,conversation,newId,conv.author),
            (s:MeTLStanza) => s.author == conv.author
          )
        })
      })
      serializer.fromConversation(conv).headOption.map(n => XmlResponse(n))
    } else {
      Full(ForbiddenResponse("only the author may duplicate slides in a conversation"))
    }
  }
  def duplicateConversation(onBehalfOfUser:String,conversation:String):Box[LiftResponse] = {
    val oldConv = config.detailsOfConversation(conversation)
    if (onBehalfOfUser == oldConv.author){
      val newConv = config.createConversation(oldConv.title + " (copied at %s)".format(new java.util.Date()),oldConv.author)
      val newConvWithOldSlides = newConv.copy(
        lastAccessed = new java.util.Date().getTime,
        slides = oldConv.slides.map(s => s.copy(
          groupSet = Nil, 
          id = s.id - oldConv.jid + newConv.jid,
          audiences = Nil
        ))
      )
      println("newConvWithOldSlides: "+newConvWithOldSlides)
      val remoteConv = config.updateConversation(newConv.jid.toString,newConvWithOldSlides)
      println("remoteConv: "+remoteConv)
      remoteConv.slides.foreach(ns => {
        val newSlide = ns.id
        val slide = newSlide - newConv.jid + oldConv.jid
        val conv = remoteConv
        ServerSideBackgroundWorker ! CopyContent(
          config,
          SlideRoom(conv.server.name,conversation,slide),
          SlideRoom(conv.server.name,remoteConv.jid.toString,newSlide),
          (s:MeTLStanza) => s.author == conv.author
        )
        ServerSideBackgroundWorker ! CopyContent(
          config,
          PrivateSlideRoom(conv.server.name,conversation,slide,conv.author),
          PrivateSlideRoom(conv.server.name,remoteConv.jid.toString,newSlide,conv.author),
          (s:MeTLStanza) => s.author == conv.author
        )
      })
      ServerSideBackgroundWorker ! CopyContent(
        config,
        ConversationRoom(remoteConv.server.name,remoteConv.jid.toString),
        ConversationRoom(newConv.server.name,newConv.jid.toString),
        (s:MeTLStanza) => s.author == remoteConv.author && s.isInstanceOf[MeTLQuiz] // || s.isInstanceOf[Attachment]
      )
      val remoteConv2 = config.updateConversation(remoteConv.jid.toString,remoteConv)
      println("remoteConv2: "+remoteConv2)
      serializer.fromConversation(remoteConv2).headOption.map(n => XmlResponse(n))
    } else {
      Full(ForbiddenResponse("only the author may duplicate a conversation"))
    }
  }
}

case class CopyContent(server:ServerConfiguration,from:RoomMetaData,to:RoomMetaData,contentFilter:MeTLStanza=>Boolean)

object ServerSideBackgroundWorker extends net.liftweb.actor.LiftActor {
  val thisDuplicatorId = nextFuncName
  override def messageHandler = {
    case RoomJoinAcknowledged(server,room) => {}
    case RoomLeaveAcknowledged(server,room) => {}
    case CopyContent(config,oldLoc,newLoc,contentFilter) => {
      println("copying: %s => %s".format(oldLoc,newLoc))
      val oldContent = config.getHistory(oldLoc.getJid)
      val room = MeTLXConfiguration.getRoom(newLoc.getJid,config.name,newLoc)
      room ! JoinRoom("serverSideBackgroundWorker",thisDuplicatorId,this)
      oldContent.filter(contentFilter).getAll.foreach(stanza => {   
        room ! LocalToServerMeTLStanza(stanza match {
          case m:MeTLInk => m.copy(slide = newLoc.getJid)
          case m:MeTLImage => m.copy(slide = newLoc.getJid)
          case m:MeTLText => m.copy(slide = newLoc.getJid)
          case m:MeTLMoveDelta => m.copy(slide = newLoc.getJid)
          case m:MeTLDirtyInk => m.copy(slide = newLoc.getJid)
          case m:MeTLDirtyText => m.copy(slide = newLoc.getJid)
          case m:MeTLSubmission => tryo(newLoc.getJid.toInt).map(ns => m.copy(slideJid = ns)).getOrElse(m)
          case m:MeTLUnhandledCanvasContent[_] => m.copy(slide = newLoc.getJid)
          case s:MeTLStanza => s
        })
      })
      room ! LeaveRoom("serverSideBackgroundWorker",thisDuplicatorId,this)
      println("copied: %s => %s".format(oldLoc,newLoc))
    }
  }
}
