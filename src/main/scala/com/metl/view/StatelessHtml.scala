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
    Full(MeTLXConfiguration.getRoom(slideJid,config.name).getHistory.getImageByIdentity(identity).map(image => {
      image.imageBytes.map(bytes => {
        println("found bytes: %s (%s)".format(bytes,bytes.length))
        val dataUri = "data:image/png;base64,"+net.liftweb.util.SecurityHelpers.base64Encode(bytes)
        InMemoryResponse(IOUtils.toByteArray(dataUri),Boot.cacheStrongly,Nil,200)
      }).openOr(NotFoundResponse("image bytes not available"))
    }).getOrElse(NotFoundResponse("image not available"))))

  def proxy(slideJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxy(%s)".format(identity), () => {
    Full(MeTLXConfiguration.getRoom(slideJid,config.name).getHistory.getImageByIdentity(identity).map(image => {
      image.imageBytes.map(bytes => {
        println("found bytes: %s (%s)".format(bytes,bytes.length))
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        InMemoryResponse(bytes,headers,Nil,200)
      }).openOr(NotFoundResponse("image bytes not available"))
    }).getOrElse(NotFoundResponse("image not available")))
  })
  def quizProxy(conversationJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.quizProxy()".format(conversationJid,identity), () => {
    Full(MeTLXConfiguration.getRoom(conversationJid,config.name).getHistory.getQuizByIdentity(identity).map(quiz => {
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
    Full(MeTLXConfiguration.getRoom(conversationJid,config.name).getHistory.getSubmissionByAuthorAndIdentity(author,identity).map(sub => {
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
    <history>{serializer.fromRenderableHistory(MeTLXConfiguration.getRoom(jid,config.name).getHistory)}</history>
  })
  def loadMergedHistory(jid:String,username:String):Node = Stopwatch.time("StatelessHtml.loadMergedHistory(%s)".format(jid),() => {
    <history>{serializer.fromRenderableHistory(MeTLXConfiguration.getRoom(jid,config.name).getHistory.merge(MeTLXConfiguration.getRoom(jid+username,config.name).getHistory))}</history>
  })
  def history(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.history(%s)".format(req.param("source")), () => {
    req.param("source").map(jid=> XmlResponse(loadHistory(jid)))
  })
  def fullHistory(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.fullHistory(%s)".format(req.param("source")), () => {
    req.param("source").map(jid => XmlResponse(<history>{MeTLXConfiguration.getRoom(jid,config.name).getHistory.getAll.map(s => serializer.fromMeTLData(s))}</history>))
  })
  def mergedHistory(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.history(%s)".format(req.param("source")), () => {
    req.param("source").map(jid=> {
      req.param("username").map(user => {
        XmlResponse(loadMergedHistory(jid,user))
      }).getOrElse(NotFoundResponse("username not provided"))
    })
  })
}
