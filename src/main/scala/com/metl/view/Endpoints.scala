package com.metl.view

import com.metl.data._
import com.metl.utils._
import com.metl.renderer.SlideRenderer
import javax.xml.bind.DatatypeConverter;

import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.http.provider._
import Helpers._
import com.metl.model._
import Http._
import scala.xml.XML
import org.apache.commons.io._

object MeTLRestHelper extends RestHelper {
  println("MeTLRestHelper inline")
  val serializer = new GenericXmlSerializer("rest")
  val crossDomainPolicy = {
    <cross-domain-policy>
    <allow-access-from domain="*" />
    </cross-domain-policy>
  }
  serve {
    case Req("verifyUserCredentialsForm" :: Nil,_,_) => () => {
      Full(InMemoryResponse(
        ( <html>
          <body>
          <form action="/verifyUserCredentials" method="POST">
          <div>
          <label for="username">Username</label>
          <input type="text" name="username" id="username"/>
          </div>
          <div>
          <label for="password">Password</label>
          <input type="password" name="password" id="password"/>
          </div>
          <input type="submit"/>
          </form>
          </body>
          </html> ).toString.getBytes("UTF-8"),Nil,Nil,200
      ))
    }
    case Req("verifyUserCredentials" :: Nil,_,_) => () => {
      for (
        u <- S.param("username");
        p <- S.param("password");
        cp <- MeTLXConfiguration.configurationProvider
      ) yield {
        PlainTextResponse(cp.checkPassword(u,p).toString,Nil,200)
      }
    }
    case Req("serverStatus" :: Nil,_,_) =>
      () => Stopwatch.time("MeTLRestHelper.serverStatus", () => Full(PlainTextResponse("OK", List.empty[Tuple2[String,String]], 200)))
    case Req(List("probe","index"),"html",_) =>
      () => Stopwatch.time("MeTLRestHelper.serverStatus", () => Full(PlainTextResponse("OK", List.empty[Tuple2[String,String]], 200)))
    case Req(List("crossdomain"),"xml",_) =>
      () => Stopwatch.time("MeTLRestHelper.crossDomainPolicy", () => Full(XmlResponse(crossDomainPolicy,200)))
    case r @ Req(List("summaries"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.summaries", () => StatelessHtml.summaries(r))
    case r @ Req(List("appcache"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.appcache", () => StatelessHtml.appCache(r))
    case r @ Req(List("history"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.history", () => StatelessHtml.history(r))
    case r @ Req(List("mergedHistory"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.mergedHistory", () => StatelessHtml.mergedHistory(r))
    case r @ Req(List("fullHistory"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.fullHistory", () => StatelessHtml.fullHistory(r))
    case r @ Req(List("fullClientHistory"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.fullClientHistory", () => StatelessHtml.fullClientHistory(r))
    case r @ Req("describeHistory" :: _,_,_) => 
      () => Stopwatch.time("MeTLRestHelper.describeHistory",() => StatelessHtml.describeHistory(r))
    case r @ Req(List("themes"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.themes", () => StatelessHtml.themes(r))
    case r @ Req(List("words",jid),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.words", () => Full(XmlResponse(StatelessHtml.words(jid))))
    case r @ Req(List("details",jid),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.details", () => StatelessHtml.details(jid))
    case r @ Req(List("setUserOptions"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.details", () => StatelessHtml.setUserOptions(r))
    case r @ Req(List("getUserOptions"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.details", () => StatelessHtml.getUserOptions(r))
    case Req("search" :: Nil,_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.search",() => {
        val query = S.params("query").head
        val server = ServerConfiguration.default
        Full(XmlResponse(<conversations>{server.searchForConversation(query).map(c => serializer.fromConversation(c))}</conversations>))
      })
    case Req("render" :: jid :: height :: width :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.render", () => {
      val server = ServerConfiguration.default
      val history = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid)).getHistory
      val image = SlideRenderer.render(history,new com.metl.renderer.RenderDescription(width.toInt,height.toInt),"presentationSpace")
      Full(InMemoryResponse(image,List("Content-Type" -> "image/jpeg"),Nil,200))
    })

    case Req("render" :: configName :: jid :: height :: width :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.render", () => {
      val server = ServerConfiguration.configForName(configName)
      val history = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid)).getHistory
      val image = SlideRenderer.render(history,new com.metl.renderer.RenderDescription(width.toInt,height.toInt),"presentationSpace")
      Full(InMemoryResponse(image,List("Content-Type" -> "image/jpeg"),Nil,200))
    })
    case Req("thumbnail" :: jid :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.thumbnail",() => {
      val server = ServerConfiguration.default
      val image = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid)).getThumbnail
      Full(InMemoryResponse(image,List("Content-Type" -> "image/jpeg"),Nil,200))
    })
    case Req("thumbnail" :: configName :: jid :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.thumbnail", () => {
      val server = ServerConfiguration.configForName(configName)
      val image = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid)).getThumbnail
      Full(InMemoryResponse(image,List("Content-Type" -> "image/jpeg"),Nil,200))
    })
    case Req("thumbnailDataUri" :: configName :: jid :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.thumbnailDataUri", () => {
      val server = ServerConfiguration.configForName(configName)
      val image = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid)).getThumbnail
      val dataUri = "data:image/jpeg;base64," + DatatypeConverter.printBase64Binary(image)
      Full(InMemoryResponse(IOUtils.toByteArray(dataUri),Nil,Nil,200))
    })
  }
}
object WebMeTLRestHelper extends RestHelper {
  println("WebMeTLRestHelper inline")
  serve {
    case Req("application" :: "snapshot" :: Nil,_,_) => () => {
      val server = S.param("server").openOr("")
      val slide = S.param("slide").openOr("")
      Full(HttpResponder.snapshot(server,slide,"small"))
    }
  }
}
object MeTLStatefulRestHelper extends RestHelper {
  println("MeTLStatefulRestHelper inline")
  val DEMO_TEACHER = "Mr Roboto"
  val serializer = new GenericXmlSerializer("rest")
  serve {
    case Req(List("conversationExport",conversation),_,_) => () => Stopwatch.time("MeTLStatefulRestHelper.exportConversation",() => StatelessHtml.exportConversation(Globals.currentUser.is,conversation))
    case Req(List("conversationExportForMe",conversation),_,_) => () => Stopwatch.time("MeTLStatefulRestHelper.exportConversation",() => StatelessHtml.exportMyConversation(Globals.currentUser.is,conversation))
    case r@Req(List("conversationImport"),_,_) => () => Stopwatch.time("MeTLStatefulRestHelper.importConversation",() => StatelessHtml.importConversation(r))
    case r@Req(List("foreignConversationImport"),_,_) => () => Stopwatch.time("MeTLStatefulRestHelper.foreignConversationImport",() => StatelessHtml.foreignConversationImport(r))
    case r@Req(List("conversationImportAsMe"),_,_) => () => Stopwatch.time("MeTLStatefulRestHelper.importConversation",() => StatelessHtml.importConversationAsMe(r))
    case Req(List("createConversation",title),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.createConversation",() => StatelessHtml.createConversation(Globals.currentUser.is,title))
    case r@Req(List("updateConversation",jid),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.updateConversation",() => StatelessHtml.updateConversation(Globals.currentUser.is,jid,r))
    case Req(List("addSlideAtIndex",jid,index),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.addSlideAtIndex",() => StatelessHtml.addSlideAtIndex(Globals.currentUser.is,jid,index))
    case Req(List("duplicateSlide",slide,conversation),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.duplicateSlide",() => StatelessHtml.duplicateSlide(Globals.currentUser.is,slide,conversation))
    case Req(List("duplicateConversation",conversation),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.duplicateConversation",() => StatelessHtml.duplicateConversation(Globals.currentUser.is,conversation))
    case Req(List("requestMaximumSizedGrouping",conversation,slide,groupSize),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.requestMaximumSizedGrouping",() => StatelessHtml.addGroupTo(Globals.currentUser.is,conversation,slide,GroupSet(ServerConfiguration.default,nextFuncName,slide,ByMaximumSize(groupSize.toInt),Nil,Nil)))
    case Req(List("requestClassroomSplitGrouping",conversation,slide,numberOfGroups),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.requestClassroomSplitGrouping",() => StatelessHtml.addGroupTo(Globals.currentUser.is,conversation,slide,GroupSet(ServerConfiguration.default,nextFuncName,slide,ByTotalGroups(numberOfGroups.toInt),Nil,Nil)))
    case Req(List("proxyDataUri",slide,source),_,_) =>
      ()=> Stopwatch.time("MeTLStatefulRestHelper.proxyDataUri",() => StatelessHtml.proxyDataUri(slide,source))
    case Req(List("proxy",slide,source),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.proxy",() => StatelessHtml.proxy(slide,source))
    case r@Req(List("proxyImageUrl",slide),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.proxyImageUrl",() => StatelessHtml.proxyImageUrl(slide,r.param("source").getOrElse("")))
    case Req(List("quizProxy",conversation,identity),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.quizProxy", () => StatelessHtml.quizProxy(conversation,identity))
    case Req(List("submissionProxy",conversation,author,identity),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.submissionProxy", () => StatelessHtml.submissionProxy(conversation,author,identity))
    case r @ Req(List("resourceProxy",identity),_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.resourceProxy", () => StatelessHtml.resourceProxy(Helpers.urlDecode(identity)))
    case r@Req("join" :: Nil, _, _) => {
      for {
        conversationJid <- r.param("conversation");
        slide <- r.param("slide");
        sess <- S.session
      } yield {
        val serverConfig = ServerConfiguration.default
        val c = serverConfig.detailsOfConversation(conversationJid)
        println("Forced to join conversation %s".format(conversationJid))
        CurrentConversation(Full(c))
        if (c.slides.exists(s => slide.toLowerCase.trim == s.id.toString.toLowerCase.trim)){
          println("Forced move to slide %s".format(slide))
          CurrentSlide(Full(slide))
        }
      }
      RedirectResponse("/board")
    }
    case r @ Req("projector" :: conversationJid :: Nil, _, _) => {
      S.session match {
        case Full(sess) => {
          val serverConfig = ServerConfiguration.default
          val c = serverConfig.detailsOfConversation(conversationJid)
          if ((c.subject.toLowerCase.trim == "unrestricted" || Globals.getUserGroups.exists((ug:Tuple2[String,String]) => ug._2.toLowerCase.trim == c.subject.toLowerCase.trim)) && c != Conversation.empty){
            IsInteractiveUser(Full(false))
            CurrentConversation(Full(c))
            c.slides.sortBy(s => s.index).headOption.map(s => CurrentSlide(Full(s.id.toString)))
          }
        }
        case _ => {}
      }
      RedirectResponse("/board")
    }
    case r @ Req(List("upload"),_,_) =>{
      println("Upload registered in MeTLStatefulRestHelper")
      println(r.body)
        () => Stopwatch.time("MeTLStatefulRestHelper.upload",() => {
          r.body.map(bytes => {
            val filename = S.params("filename").head
            val jid = S.params("jid").head
            val server = ServerConfiguration.default
            XmlResponse(<resourceUrl>{server.postResource(jid,filename,bytes)}</resourceUrl>)
          })
        })
    }
    case r @ Req(List("uploadDataUri"),_,_) =>{
      println("UploadDataUri registered in MeTLStatefulRestHelper")
      println(r.body)
        () => Stopwatch.time("MeTLStatefulRestHelper.upload",() => {
          r.body.map(dataUriBytes => {
            val dataUriString = IOUtils.toString(dataUriBytes)
            val b64Bytes = dataUriString.split(",")(1)
            val bytes = net.liftweb.util.SecurityHelpers.base64Decode(b64Bytes)
            val filename = S.params("filename").head
            val jid = S.params("jid").head
            val server = ServerConfiguration.default
            XmlResponse(<resourceUrl>{server.postResource(jid,filename,bytes)}</resourceUrl>)
          })
        })
    }
    case r @ Req(List("logDevice"),_,_) => () => {
      r.userAgent.map(ua => {
        println("UserAgent:"+ua)
        PlainTextResponse("loggedUserAgent")
      })
    }
  }
}
object WebMeTLStatefulRestHelper extends RestHelper {
  println("WebMeTLStatefulRestHelper inline")
  serve {
    case Req(server :: "slide" :: jid :: size :: Nil,_,_) => () => Full(HttpResponder.snapshot(server,jid,size))
    case Req(server :: "quizImage" :: jid :: id :: Nil,_,_) => () => Full(HttpResponder.quizImage(server,jid,id))
    case Req(server :: "quizResponse" :: conversation :: quiz :: response :: Nil,_,_)
        if (List(server,conversation,quiz,response).filter(_.length == 0).isEmpty) => () => {
          val slide = S.param("slide").openOr("")
          Full(QuizResponder.handleResponse(server,conversation,slide,quiz,response))
        }
  }
}
