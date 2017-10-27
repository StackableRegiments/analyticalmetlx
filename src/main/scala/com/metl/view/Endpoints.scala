package com.metl.view

import java.util.Date

import com.metl.data.{GroupSet=>MeTLGroupSet,_}
import com.metl.utils._
import com.metl.renderer.SlideRenderer
import net.liftweb.json._
import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest._
import Helpers._
import com.metl.external._
import com.metl.model._
import com.metl.utils.CasUtils._

import scala.xml.{Text, XML}

trait Stemmer {
  def stem(in:String):Tuple2[String,String] = {
    (("00000"+in).reverse.drop(3).take(2).reverse,in)
  }
}

object SystemRestHelper extends RestHelper with Stemmer with Logger {
  warn("SystemRestHelper inline")
  val jsonSerializer = new JsonSerializer(ServerConfiguration.default)
  val serializer = new GenericXmlSerializer(ServerConfiguration.default)
  serve {
    case r@Req("getRemoteUser" :: Nil,_,_) => () => Full(PlainTextResponse(S.containerRequest.map(r => (r.asInstanceOf[net.liftweb.http.provider.servlet.HTTPRequestServlet]).req.getRemoteUser).getOrElse("unknown")))
    case r@Req(List("api","v1","serverStatus"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.serverStatus", {
        Full(PlainTextResponse("OK", List.empty[Tuple2[String,String]], 200))
      })
    case r @ Req(List("api","v1","sample","global",jid),_,_) =>
      () => Stopwatch.time("SystemRestHelper.history", Full(XmlResponse(XML.load("global.xml"))))
    case r @ Req(List("api","v1","history","public",jid),_,_) =>
      () => Stopwatch.time("SystemRestHelper.history", StatelessHtml.history(jid))
    case r @ Req(List("api","v1","history","includePrivate",jid,onBehalfOf),_,_) =>
      () => Stopwatch.time("SystemRestHelper.mergedHistory", StatelessHtml.mergedHistory(jid,onBehalfOf))
    case r @ Req(List("api","v1","history","description",jid),_,_) =>
      () => Stopwatch.time("SystemRestHelper.describeHistory", StatelessHtml.describeHistory(jid))
    case r @ Req(List("api","v1","conversation","details",jid),_,_) =>
      () => Stopwatch.time("SystemRestHelper.details", StatelessHtml.details(jid))
    case Req(List("api","v1","conversation","search",query),_,_) =>
      () => Stopwatch.time("SystemRestHelper.search", {
        val server = ServerConfiguration.default
        val x = <conversations>{server.searchForConversation(query).map(c => serializer.fromConversation(c))}</conversations>
        Full(S.params("format") match {
          case List("json") => JsonResponse(net.liftweb.json.Xml.toJson(x))
          case _ => XmlResponse(x)
        })
      })
    case Req(List("api","v1","slide","thumbnail",jid),_,_) => Stopwatch.time("SystemRestHelper.thumbnail",  {
      HttpResponder.snapshot(jid,"thumbnail")
    })
  }
}

object MeTLRestHelper extends RestHelper with Stemmer with Logger{
  debug("MeTLRestHelper inline")
  val serializer = new GenericXmlSerializer(ServerConfiguration.default)
  val host = Globals.host
  val scheme = Globals.scheme
  val port = Globals.port
  val slideRenderer = new SlideRenderer()
  val crossDomainPolicy = {
    <cross-domain-policy>
    <allow-access-from domain="*" />
    </cross-domain-policy>
  }
  val browserconfig = {
    <browserconfig>
    <msapplication>
    <tile>
    <square70x70logo src="/ms-icon-70x70.png"/>
    <square150x150logo src="/ms-icon-150x150.png"/>
    <square310x310logo src="/ms-icon-310x310.png"/>
    <TileColor>#ffffff</TileColor>
    </tile>
    </msapplication>
    </browserconfig>
  }
  protected var id = 1000;
  serve {
    //security enforced security
    case r:Req if scheme.map(_ != r.request.scheme).getOrElse(false) => () => {
      val uri = r.request.url
      val transformed = "%s://%s:%s%s%s".format(scheme.getOrElse("http"),host.getOrElse(r.request.serverName),port.getOrElse(r.request.serverPort),r.uri,r.request.queryString.map(qs => "?%s".format(qs)).getOrElse(""))
      info("insecure: %s, redirecting to: %s".format(uri,transformed))
      Full(RedirectResponse(transformed,r))
    }
    case r@Req(Nil,_,_) => () => {
      Full(RedirectResponse(com.metl.snippet.Metl.conversationSearch()))
    }
    //metlx endpoints 8080
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
    case r@Req("latency" :: Nil,_,_) => {
      val start = new java.util.Date().getTime
        () => {
        Full(PlainTextResponse((new java.util.Date().getTime - start).toString,List.empty[Tuple2[String,String]], 200))
      }
    }
    case r@Req("serverStatus" :: Nil,_,_) =>
      () => Stopwatch.time("MeTLRestHelper.serverStatus", {
        r.param("latency").foreach(latency => info("[%s] miliseconds clientReportedLatency".format(latency)))
        Full(PlainTextResponse("OK", List.empty[Tuple2[String,String]], 200))
      })
    case Req(List("probe","index"),"html",_) =>
      () => Stopwatch.time("MeTLRestHelper.serverStatus", Full(PlainTextResponse("OK", List.empty[Tuple2[String,String]], 200)))
    case Req(List("browserconfig"),"xml",_) =>
      () => Stopwatch.time("MeTLRestHelper.browserconfig.xml", Full(XmlResponse(browserconfig,200)))
    case Req(List("crossdomain"),"xml",_) =>
      () => Stopwatch.time("MeTLRestHelper.crossDomainPolicy", Full(XmlResponse(crossDomainPolicy,200)))
    case r @ Req(List("summaries"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.summaries", StatelessHtml.summaries(r))
    case r @ Req(List("appcache"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.appcache", StatelessHtml.appCache(r))
    case r @ Req(List("details",jid),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.details", StatelessHtml.details(jid))
    case r @ Req(List("setUserOptions"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.details", StatelessHtml.setUserOptions(r))
    case r @ Req(List("getUserOptions"),_,_) =>
      () => Stopwatch.time("MeTLRestHelper.details", StatelessHtml.getUserOptions(r))
    case Req("search" :: Nil,_,_) =>
      () => Stopwatch.time("MeTLStatefulRestHelper.search", {
        val query = S.params("query").head
        val server = ServerConfiguration.default
        val x = <conversations>{server.searchForConversation(query).map(c => serializer.fromConversation(c))}</conversations>
        Full(S.params("format") match {
          case List("json") => JsonResponse(net.liftweb.json.Xml.toJson(x))
          case _ => XmlResponse(x)
        })
      })
    case Req("render" :: jid :: height :: width :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.render",  {
      val server = ServerConfiguration.default
      val room = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid))
      val history = room.getHistory
      val image = room.slideRenderer.render(history,new com.metl.renderer.RenderDescription(width.toInt,height.toInt),"presentationSpace")
      Full(InMemoryResponse(image,List("Content-Type" -> "image/jpeg"),Nil,200))
    })
    case Req("thumbnail" :: jid :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.thumbnail",  {
      HttpResponder.snapshot(jid,"thumbnail")
    })
    case Req("printableImage" :: jid :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.thumbnail",  {
      HttpResponder.snapshot(jid,"print")
    })
    case Req("thumbnailDataUri" :: jid :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.thumbnailDataUri", {
      HttpResponder.snapshotDataUri(jid,"thumbnail")
    })
    case Req("testFetchAndRender" :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.testFetchAndRender", {
      for {
        width <- S.param("width")
        height <- S.param("height")
      } yield {
        val config = ServerConfiguration.default
        val history = config.getMockHistory
        val image = slideRenderer.render(history,new com.metl.renderer.RenderDescription(Math.min(width.toInt,640),Math.min(height.toInt,480)),"presentationSpace")
        InMemoryResponse(image,List("Content-Type" -> "image/jpeg"),Nil,200)
      }
    })
    case Req("testFetchGlobalRoom" :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.testFetchGlobalRoom", {
      val room = MeTLXConfiguration.getRoom("global",ServerConfiguration.default.name,GlobalRoom(ServerConfiguration.default))
      Full(PlainTextResponse(room.roomMetaData.getJid, List.empty[Tuple2[String,String]], 200))
    })
    case Req("testCountConversations" :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.testCountConversations", {
      for {
        query <- S.param("query")
      } yield {
        val server = ServerConfiguration.default
        PlainTextResponse(server.searchForConversation(query).length.toString)
      }
    })
  }
}
object WebMeTLRestHelper extends RestHelper with Logger{
  debug("WebMeTLRestHelper inline")
  serve {
    case Req("application" :: "snapshot" :: Nil,_,_) => () => {
      val slide = S.param("slide").openOr("")
      val size = S.param("size").openOr("small")
      Full(HttpResponder.snapshot(slide,size))
    }
  }
}

trait ExternalGradeHelper {
  def toExternalGrade(grade:MeTLGrade):ExternalGrade = {
    val gradeType = grade.gradeType match {
      case MeTLGradeValueType.Boolean => ExternalGradeValueType.Boolean
      case MeTLGradeValueType.Text => ExternalGradeValueType.Text
      case MeTLGradeValueType.Numeric => ExternalGradeValueType.Numeric
      case _ => ExternalGradeValueType.Text
    }
    ExternalGrade(grade.author,grade.timestamp,grade.id,grade.location,grade.name,grade.description,gradeType,grade.visible,grade.foreignRelationship,grade.gradeReferenceUrl,grade.numericMaximum,grade.numericMinimum)
  }
  def fromExternalGrade(grade:ExternalGrade):MeTLGrade = {
    val gradeType = grade.gradeType match {
      case ExternalGradeValueType.Boolean => MeTLGradeValueType.Boolean
      case ExternalGradeValueType.Text => MeTLGradeValueType.Text
      case ExternalGradeValueType.Numeric => MeTLGradeValueType.Numeric
      case _ => MeTLGradeValueType.Text
    }
    MeTLGrade(ServerConfiguration.default,grade.author,grade.timestamp,grade.id,grade.location,grade.name,grade.description,gradeType,grade.visible,grade.foreignRelationship,grade.gradeReferenceUrl,grade.numericMaximum,grade.numericMinimum)
  }
  def toExternalGradeValue(in:MeTLGradeValue):ExternalGradeValue = {
    in match {
      case n:MeTLNumericGradeValue => toExternalNumericGradeValue(n)
      case t:MeTLTextGradeValue => toExternalTextGradeValue(t)
      case b:MeTLBooleanGradeValue => toExternalBooleanGradeValue(b)
    }
  }
  def toExternalNumericGradeValue(n:MeTLNumericGradeValue):ExternalNumericGradeValue = ExternalNumericGradeValue(n.author,n.timestamp,n.gradeId,n.gradedUser,n.gradeValue,n.gradeComment,n.gradePrivateComment)
  def toExternalTextGradeValue(t:MeTLTextGradeValue):ExternalTextGradeValue = ExternalTextGradeValue(t.author,t.timestamp,t.gradeId,t.gradedUser,t.gradeValue,t.gradeComment,t.gradePrivateComment)
  def toExternalBooleanGradeValue(b:MeTLBooleanGradeValue):ExternalBooleanGradeValue = ExternalBooleanGradeValue(b.author,b.timestamp,b.gradeId,b.gradedUser,b.gradeValue,b.gradeComment,b.gradePrivateComment)
  def fromExternalNumericGradeValue(n:ExternalNumericGradeValue):MeTLNumericGradeValue = MeTLNumericGradeValue(ServerConfiguration.default,n.author,n.timestamp,n.gradeId,n.gradedUser,n.gradeValue,n.gradeComment,n.gradePrivateComment)
  def fromExternalTextGradeValue(t:ExternalTextGradeValue):MeTLTextGradeValue = MeTLTextGradeValue(ServerConfiguration.default,t.author,t.timestamp,t.gradeId,t.gradedUser,t.gradeValue,t.gradeComment,t.gradePrivateComment)
  def fromExternalBooleanGradeValue(b:ExternalBooleanGradeValue):MeTLBooleanGradeValue = MeTLBooleanGradeValue(ServerConfiguration.default,b.author,b.timestamp,b.gradeId,b.gradedUser,b.gradeValue,b.gradeComment,b.gradePrivateComment)
}
object MeTLStatefulRestHelper extends RestHelper with Logger with Stemmer with ExternalGradeHelper {
  import java.io._
  debug("MeTLStatefulRestHelper inline")
  val serializer = new GenericXmlSerializer(ServerConfiguration.default)
  val jsonSerializer = new JsonSerializer(ServerConfiguration.default)
  serve {
    /*
    case Req("testCometCreate" :: Nil,_,_) => Stopwatch.time("MeTLRestHelper.testCometCreate", {
      warn("testCometCreate: %s".format(S.session))
      for {
        s <- S.session
        //c <- s.findOrCreateComet[TestActor](Full(nextFuncName),scala.xml.NodeSeq.empty,Map.empty)
      } yield {
        val cometName = nextFuncName
        val nodes = com.metl.snippet.Metl.specificTestComet(cometName)(scala.xml.NodeSeq.Empty)//<span class={"lift:comet?type=TestActor;name=%s".format(cometName)}/>
        val x = s.runTemplate("testPage",nodes)
        s.setupComet("TestComet",Full(cometName),true)
        val c = s.findComet("TestComet",Full(cometName))
        PlainTextResponse("created cometActor: %s => %s".format(c,x))
      }
    })
    */
    case Req("logout" :: Nil, _, _) => () =>
      Stopwatch.time("MeTLRestHelper.logout", {
        S.session.foreach(_.destroySession())
        //S.containerSession.foreach(s => s.terminate)
        Full(RedirectResponse(com.metl.snippet.Metl.conversationSearch()))
      })
    case r@Req(List("history"), _, _) =>
      () => r.param("source").flatMap(jid => StatelessHtml.history(jid))
    case r@Req(List("mergedHistory"), _, _) =>
      () => {
        for (
          source <- r.param("source");
          user <- r.param("username");
          resp <- StatelessHtml.mergedHistory(source, user)
        ) yield resp
      }
    case r@Req(List("fullHistory"), _, _) =>
      () => r.param("source").flatMap(jid => StatelessHtml.fullHistory(jid))
    case r@Req(List("fullClientHistory"), _, _) =>
      () => r.param("source").flatMap(jid => StatelessHtml.fullClientHistory(jid))
    case r@Req(List("fullJsonHistory"), _, _) =>
      () => r.param("source").flatMap(jid => StatelessHtml.jsonHistory(jid))
    case r@Req("describeHistory" :: _, _, _) =>
      () => r.param("source").flatMap(jid => StatelessHtml.describeHistory(jid,r.param("format")))
    case r@Req("describeConversations" :: _, _, _) =>
      () => r.param("query").flatMap(query => StatelessHtml.describeConversations(query,r.param("format")))
    case r@Req("describeConversation" :: _,_,_) =>
      () => r.param("jid").flatMap(jid => StatelessHtml.describeConversation(jid,r.param("format")))
    //yaws endpoints 1188
    case r@Req(List("upload_nested"), "yaws", PostRequest) => () => {
      for (
        path <- r.param("path");
        filename <- r.param("filename");
        overwrite <- r.param("overwrite").map(_.toBoolean);
        bytes <- r.body;
        resp <- StatelessHtml.yawsUploadNested(path, filename, overwrite, bytes)
      ) yield {
        resp
      }
    }
    case Req(List("primarykey"), "yaws", GetRequest) => () => {
      StatelessHtml.yawsPrimaryKey
    }
    case Req(List(rootElem, stemmedRoom, room, item), itemSuffix, GetRequest) if List("Structure", "Resource").contains(rootElem) && stem(room)._1 == stemmedRoom => () => {
      StatelessHtml.yawsResource(rootElem, room, item, itemSuffix)
    }
    //yaws endpoints 1749
    case Req(List(stemmedRoom, room, "all"), "zip", GetRequest) if stem(room)._1 == stemmedRoom => () => {
      StatelessHtml.yawsHistory(room)
    }
    //end yaws endpoints
    case req@Req("videoProxy" :: slideJidB64 :: identity :: Nil, _, _) => () =>
      Stopwatch.time("MeTLRestHelper.videoProxy", {
        val config = ServerConfiguration.default
        val slideJid = new String(base64Decode(slideJidB64))
        Full(MeTLXConfiguration.getRoom(slideJid, config.name, RoomMetaDataUtils.fromJid(slideJid)).getHistory.getVideoByIdentity(identity).map(video => {
          video.videoBytes.map(bytes => {
            val fis = new ByteArrayInputStream(bytes)
            val initialSize: Long = bytes.length - 1
            val (size, start, end) = {
              (for {
                rawRange <- req.header("Range")
                range = rawRange.substring(rawRange.indexOf("bytes=") + 6).split("-").toList.map(s => parseNumber(s))
              } yield {
                range match {
                  case List(s, e) => (e - s, s.toLong, e)
                  case List(s) => (initialSize - s, s.toLong, initialSize)
                  case _ => (initialSize, 0L, initialSize)
                }
              }).getOrElse((initialSize, 0L, initialSize))
            }
            fis.skip(start)
            val headers = List(
              ("Connection" -> "close"),
              ("Transfer-Encoding" -> "chunked"),
              ("Content-Type" -> "video/mp4"),
              ("Content-Range" -> "bytes %d-%d/%d".format(start, end, bytes.length))
            )
            StreamingResponse(
              data = fis,
              onEnd = fis.close,
              size = initialSize,
              headers = headers,
              cookies = Nil,
              code = 206
            )
          }).openOr(NotFoundResponse("video bytes not available"))
        }).getOrElse(NotFoundResponse("video not available")))
      })
    case r@Req("reportLatency" :: Nil, _, _) => {
      val start = new java.util.Date().getTime
        () =>
      Stopwatch.time("MeTLRestHelper.reportLatency", {
        val latencyMetrics = for {
          min <- r.param("minLatency")
          max <- r.param("maxLatency")
          mean <- r.param("meanLatency")
          samples <- r.param("sampleCount")
        } yield {
          info("[%s] miliseconds clientReportedLatency".format(mean))
          (min, max, mean, samples)
        }
        val now = new java.util.Date().getTime
        Full(JsonResponse(JObject(List(
          JField("serverWorkTime", JInt(now - start)),
          JField("serverTime", JInt(now))
        ) ::: latencyMetrics.map(lm => {
          List(
            JField("minLatency", JDouble(lm._1.toDouble)),
            JField("maxLatency", JDouble(lm._2.toDouble)),
            JField("meanLatency", JDouble(lm._3.toDouble)),
            JField("sampleCount", JDouble(lm._4.toInt))
          )
        }).getOrElse(Nil)), 200))
      })
    }
    case Req("printableImageWithPrivateFor" :: jid :: Nil, _, _) => Stopwatch.time("MeTLRestHelper.thumbnail", {
      HttpResponder.snapshotWithPrivate(jid, "print")
    })
    case Req("thumbnailWithPrivateFor" :: jid :: Nil, _, _) => Stopwatch.time("MeTLRestHelper.thumbnail", {
      HttpResponder.snapshotWithPrivate(jid, "thumbnail")
    })
    //gradebook integration
    case Req("getExternalGradebooks" :: Nil, _, _) => () => Full(JsonResponse(JArray(Globals.getGradebookProviders.map(gb => JObject(List(JField("name", JString(gb.name)), JField("id", JString(gb.id)))))), 200))
    case Req("getExternalGradebookOrgUnits" :: externalGradebookId :: Nil, _, _) => {
      for {
        gbp <- Globals.getGradebookProvider(externalGradebookId)
      } yield {
        gbp.getGradeContexts(Globals.currentUser.is) match {
          case Left(e) => JsonResponse(JObject(List(JField("error", JString(e.getMessage)))), 500)
          case Right(gcs) => JsonResponse(JArray(gcs.map(Extraction.decompose _)), 200)
        }
      }
    }
    case Req("getExternalGradebookOrgUnitClasslist" :: externalGradebookId :: orgUnitId :: Nil, _, _) => {
      for {
        gbp <- Globals.getGradebookProvider(externalGradebookId)
      } yield {
        gbp.getGradeContextClasslist(Globals.currentUser.is, orgUnitId) match {
          case Left(e) => JsonResponse(JObject(List(JField("error", JString(e.getMessage)))), 500)
          case Right(cls) => JsonResponse(Extraction.decompose(cls), 200)
        }
      }
    }
    case Req("getExternalGrade" :: externalGradebookId :: orgUnitId :: gradeId :: Nil, _, _) => {
      for {
        gbp <- Globals.getGradebookProvider(externalGradebookId)
      } yield {
        gbp.getGradeInContext(orgUnitId, Globals.currentUser.is, gradeId) match {
          case Left(e) => JsonResponse(JObject(List(JField("error", JString(e.getMessage)))), 500)
          case Right(gc) => JsonResponse(jsonSerializer.fromGrade(fromExternalGrade(gc)), 200)
        }
      }
    }
    case Req("getExternalGrades" :: externalGradebookId :: orgUnitId :: Nil, _, _) => {
      for {
        gbp <- Globals.getGradebookProvider(externalGradebookId)
      } yield {
        gbp.getGradesFromContext(orgUnitId,Globals.currentUser.is) match {
          case Left(e) => JsonResponse(JObject(List(JField("error", JString(e.getMessage)))), 500)
          case Right(gc) => JsonResponse(JArray(gc.map(go => jsonSerializer.fromGrade(fromExternalGrade(go)))), 200)
        }
      }
    }
    case r@Req("createExternalGrade" :: externalGradebookId :: orgUnitId :: Nil, _, _) => {
      for {
        gbp <- Globals.getGradebookProvider(externalGradebookId)
        json <- r.json
      } yield {
        val grade = jsonSerializer.toGrade(json)
        gbp.createGradeInContext(orgUnitId, Globals.currentUser.is, toExternalGrade(grade)) match {
          case Left(e) => JsonResponse(JObject(List(JField("error", JString(e.getMessage)))), 500)
          case Right(gc) => JsonResponse(jsonSerializer.fromGrade(fromExternalGrade(gc)), 200)
        }
      }
    }
    case r@Req("updateExternalGrade" :: externalGradebookId :: orgUnitId :: Nil, _, _) => {
      for {
        gbp <- Globals.getGradebookProvider(externalGradebookId)
        json <- r.json
      } yield {
        val grade = jsonSerializer.toGrade(json)
        gbp.updateGradeInContext(orgUnitId, Globals.currentUser.is, toExternalGrade(grade)) match {
          case Left(e) => JsonResponse(JObject(List(JField("error", JString(e.getMessage)))), 500)
        }
      }
    }
    case Req("getExternalGradeValues" :: externalGradebookId :: orgUnit :: gradeId :: Nil, _, _) => {
      for {
        gbp <- Globals.getGradebookProvider(externalGradebookId)
      } yield {
        gbp.getGradeValuesForGrade(orgUnit, Globals.currentUser.is, gradeId) match {
          case Left(e) => JsonResponse(JObject(List(JField("error", JString(e.getMessage)))), 500)
          case Right(gcs) => JsonResponse(JArray(gcs.map {
            case ngv: ExternalNumericGradeValue => jsonSerializer.fromNumericGradeValue(fromExternalNumericGradeValue(ngv))
            case bgv: ExternalBooleanGradeValue => jsonSerializer.fromBooleanGradeValue(fromExternalBooleanGradeValue(bgv))
            case tgv: ExternalTextGradeValue => jsonSerializer.fromTextGradeValue(fromExternalTextGradeValue(tgv))
          }), 200)
        }
      }
    }
    case r@Req("updateExternalGradeValues" :: externalGradebookId :: orgUnit :: gradeId :: Nil, _, _) => {
      for {
        gbp <- Globals.getGradebookProvider(externalGradebookId)
        json <- r.json
      } yield {
        val grades: List[MeTLGradeValue] = json match {
          case ja: JArray => json.children.map(jo => jsonSerializer.toMeTLData(jo)).filter(_.isInstanceOf[MeTLGradeValue]).map(_.asInstanceOf[MeTLGradeValue]).toList
          case jo: JObject => jsonSerializer.toMeTLData(jo) match {
            case gv: MeTLGradeValue => List(gv)
            case _ => Nil
          }
          case _ => Nil
        }
        gbp.updateGradeValuesForGrade(orgUnit, Globals.currentUser.is, gradeId, grades.map(toExternalGradeValue _)) match {
          case Left(e) => JsonResponse(JObject(List(JField("error", JString(e.getMessage)))), 500)
          case Right(gcs) => JsonResponse(JArray(gcs.map {
            case ngv: ExternalNumericGradeValue => jsonSerializer.fromNumericGradeValue(fromExternalNumericGradeValue(ngv))
            case bgv: ExternalBooleanGradeValue => jsonSerializer.fromBooleanGradeValue(fromExternalBooleanGradeValue(bgv))
            case tgv: ExternalTextGradeValue => jsonSerializer.fromTextGradeValue(fromExternalTextGradeValue(tgv))
          }), 200)
        }
      }
    }
    case r@Req(List("listGroups", username), _, _) if Globals.isSuperUser =>
      () => StatelessHtml.listGroups(username, r.params.flatMap(p => p._2.map(i => (p._1, i))).toList)
    case Req(List("listRooms"), _, _) if Globals.isSuperUser =>
      () => StatelessHtml.listRooms
    case Req(List("listUsersInRooms"), _, _) if Globals.isSuperUser =>
      () => StatelessHtml.listUsersInRooms
    case Req(List("listSessions"), _, _) if Globals.isSuperUser =>
      () => StatelessHtml.listSessions

    case Req("describeSessions" :: Nil, _, _) if Globals.isSuperUser => () => {
        for {
          swi <- com.metl.comet.SessionMonitor.getSessionInfo
        } yield {
          JsonResponse(JArray(swi.sessions.values.map(s => {
            JObject(List(
              JField("type",JString("session")),
              JField("lastAccess",JInt(s.lastAccess)),
              JField("lastAccessDate",JString(new java.util.Date(s.lastAccess).toString())),
              JField("requestCount",JInt(s.requestCnt)),
              JField("actors",JArray({
                List("MeTLActor","RemotePluginConversationChooserActor","MeTLConversationSearchActor","MeTLJsonConversationChooserActor","MeTLEditConversationActor").flatMap(typeName => {
                  s.session.findComet(typeName).map(ca => {
                    JObject(List(
                      JField("type",JString(typeName)),
                      JField("name",JString(ca.name.toString)),
                      JField("uniqueId",JString(ca.uniqueId))
                    ))
                  })
                })
              }))
            ) 
            ::: s.userAgent.map(ua => JField("userAgent",JString(ua))).toList
            ::: s.ipAddress.map(ip => JField("ipAddress",JString(ip))).toList
            ::: Globals.casState.getAuthStateForSession(s.session).map(lasd => {
              JField("authState",JObject(List(
                JField("username",JString(lasd.username)),
                JField("authenticated",JBool(lasd.authenticated)),
                JField("eligibleGroups",JArray(lasd.eligibleGroups.map(Extraction.decompose _).toList)),
                JField("personalDetails",JArray(lasd.informationGroups.map(Extraction.decompose _).toList))
              )))
            }).toList
          )
        }).toList),200)
      }
    }
    case Req("listMeTLingPots" :: Nil,_,_) if Globals.isSuperUser =>
      () => StatelessHtml.listMeTLingPots
    case r@Req(List("impersonate", newUsername), _, _) if Globals.isImpersonator =>
      () => StatelessHtml.impersonate(newUsername, r.params.flatMap(p => p._2.map(i => (p._1, i))).toList)
    case Req(List("deImpersonate"), _, _) if Globals.isImpersonator =>
      () => StatelessHtml.deImpersonate

    case Req(List("conversationExport", conversation), _, _) if Globals.isSuperUser =>
      () => StatelessHtml.exportConversation(Globals.currentUser.is, conversation)
    case r@Req(List("conversationImport"), _, _) if Globals.isSuperUser =>
      () => StatelessHtml.importExportedConversation(r)

    case Req(List("conversationExportForMe", conversation), _, _) =>
      () => StatelessHtml.exportMyConversation(Globals.currentUser.is, conversation)
    case r@Req(List("conversationImportAsMe"), _, _) =>
      () => {
        warn("conversationImportAsMe endpoint triggered: %s".format(r))
        StatelessHtml.importExportedConversation(r,Some(Globals.currentUser.is))
      }
    case r@Req(List("foreignConversationImportAsMe"), _, _) =>
      () => {
        warn("foreignConversationImportAsMe (formerly foreignConversationImport) endpoint triggered: %s".format(r))
        StatelessHtml.importConversationAsMe(r)
      }

    case r@Req(List("powerpointImport"), _, _) =>
      () => {
        warn("powerpointImport endpoint triggered: %s".format(r))
        StatelessHtml.powerpointImport(r)
      }

    case Req(List("createConversation", title), _, _) =>
      () => StatelessHtml.createConversation(Globals.currentUser.is, title)
    case r@Req(List("updateConversation", jid), _, _) =>
      () => StatelessHtml.updateConversation(Globals.currentUser.is, jid, r)
    case Req(List("addSlideAtIndex", jid, index), _, _) =>
      () => StatelessHtml.addSlideAtIndex(Globals.currentUser.is, jid, index)

    case Req(List("addQuizViewSlideToConversationAtIndex", jid, index, quizId), _, _) =>
      () => StatelessHtml.addQuizViewSlideToConversationAtIndex(jid, index.toInt, quizId)
    case Req(List("addQuizResultsViewSlideToConversationAtIndex", jid, index, quizId), _, _) =>
      () => StatelessHtml.addQuizResultsViewSlideToConversationAtIndex(jid, index.toInt, quizId)
    case Post(List("addSubmissionSlideToConversationAtIndex", jid, index), req) =>
      () => StatelessHtml.addSubmissionSlideToConversationAtIndex(jid, index.toInt, req)

    case Req(List("duplicateSlide", slide, conversation), _, _) =>
      () => StatelessHtml.duplicateSlide(Globals.currentUser.is, slide, conversation)
    case Req(List("duplicateConversation", conversation), _, _) =>
      () => StatelessHtml.duplicateConversation(Globals.currentUser.is, conversation)
    case Req(List("requestMaximumSizedGrouping", conversation, slide, groupSize), _, _) if Globals.isSuperUser =>
      () => StatelessHtml.addGroupTo(Globals.currentUser.is, conversation, slide, MeTLGroupSet(ServerConfiguration.default, nextFuncName, slide, ByMaximumSize(groupSize.toInt), Nil, Nil))
    case Req(List("requestClassroomSplitGrouping", conversation, slide, numberOfGroups), _, _) if Globals.isSuperUser =>
      () => StatelessHtml.addGroupTo(Globals.currentUser.is, conversation, slide, MeTLGroupSet(ServerConfiguration.default, nextFuncName, slide, ByTotalGroups(numberOfGroups.toInt), Nil, Nil))
    case Req(List("proxyDataUri", slide, source), _, _) =>
      () => StatelessHtml.proxyDataUri(slide, source)
    case Req(List("proxy", slide, source), _, _) =>
      () => StatelessHtml.proxy(slide, source)
    case r@Req(List("proxyImageUrl", slide), _, _) =>
      () => Full(r.param("source").flatMap(source => StatelessHtml.proxyImageUrl(new String(base64Decode(slide)), source)).openOr(new NotFoundResponse("image not available")))
    case Req(List("quizProxy", conversation, identity), _, _) =>
      () => StatelessHtml.quizProxy(conversation, identity)
    case Req(List("quizResultsGraphProxy", conversation, identity, width, height), _, _) =>
      () => StatelessHtml.quizResultsGraphProxy(conversation, identity, width.toInt, height.toInt)
    case Req(List("submissionProxy", conversation, author, identity), _, _) =>
      () => StatelessHtml.submissionProxy(conversation, author, identity)
    case Req(List("resourceProxy", identity), _, _) =>
      () => StatelessHtml.resourceProxy(Helpers.urlDecode(identity))
    case Req(List("attachmentProxy", conversationJid, identity), _, _) =>
      () => StatelessHtml.attachmentProxy(conversationJid, Helpers.urlDecode(identity))
    case r@Req("join" :: Nil, _, _) => {
      for {
        conversationJid <- r.param("conversation");
        slide <- r.param("slide");
        sess <- S.session
      } yield {
        val serverConfig = ServerConfiguration.default
        val c = serverConfig.detailsOfConversation(conversationJid)
        debug("Forced to join conversation %s".format(conversationJid))
        if (c.slides.exists(s => slide.toLowerCase.trim == s.id.toString.toLowerCase.trim)) {
          debug("Forced move to slide %s".format(slide))
          RedirectResponse("/board?conversationJid=%s&slideId=%s".format(c.jid, slide))
        } else {
          RedirectResponse("/board?conversationJid=%s".format(c.jid))
        }
      }
    }
    case r@Req("projector" :: conversationJid :: Nil, _, _) => {
      for {
        conversationJid <- r.param("conversation");
        slide <- r.param("slide");
        sess <- S.session
      } yield {
        val serverConfig = ServerConfiguration.default
        val c = serverConfig.detailsOfConversation(conversationJid)
        debug("Forced to join conversation %s".format(conversationJid))
        if (c.slides.exists(s => slide.toLowerCase.trim == s.id.toString.toLowerCase.trim)) {
          debug("Forced move to slide %s".format(slide))
          RedirectResponse("/board?conversationJid=%s&slideId=%s&showTools=false".format(c.jid, slide))
        } else {
          RedirectResponse("/board?conversationJid=%s&showTools=false".format(c.jid))
        }
      }
    }
    case r@Req(List("upload"), _, _) => {
      debug("Upload registered in MeTLStatefulRestHelper")
      //trace(r.body)
        () =>
      Stopwatch.time("MeTLStatefulRestHelper.upload", {
        r.body.map(bytes => {
          val filename = S.params("filename").head
          val jid = S.params("jid").head
          val server = ServerConfiguration.default
          XmlResponse(<resourceUrl>{server.postResource(jid, filename, bytes)}</resourceUrl>)
        })
      })
    }
    case r@Req(List("uploadDataUri"), _, _) => {
      debug("UploadDataUri registered in MeTLStatefulRestHelper")
      //trace(r.body)
        () =>
      Stopwatch.time("MeTLStatefulRestHelper.upload", {
        r.body.map(dataUriBytes => {
          val dataUriString = new String(dataUriBytes)
          val b64Bytes = dataUriString.split(",")(1)
          val bytes = net.liftweb.util.SecurityHelpers.base64Decode(b64Bytes)
          val filename = S.params("filename").head
          val jid = S.params("jid").head
          val server = ServerConfiguration.default
          XmlResponse(<resourceUrl>{server.postResource(jid, filename, bytes)}</resourceUrl>)
        })
      })
    }
    case r@Req(List("uploadSvg"), _, _) => {
      debug("UploadSvg registered in MeTLStatefulRestHelper")
      //trace(r.body)
        () =>
      Stopwatch.time("MeTLStatefulRestHelper.uploadSvg", {
        for {
          svgBytes <- r.body
          w <- r.param("width").map(_.toInt)
          h <- r.param("height").map(_.toInt)
          filename <- r.param("filename")
          jid <- r.param("jid")
        } yield {
          val svg = new String(svgBytes)
          var quality = r.param("quality").map(_.toFloat).getOrElse(0.4f)
          val bytes = SvgConverter.toJpeg(svg, w, h, quality)
          val server = ServerConfiguration.default
          XmlResponse(<resourceUrl>{server.postResource(jid, filename, bytes)}</resourceUrl>)
        }
      })
    }
    case r@Req(List("logDevice"), _, _) => () => {
      r.userAgent.map(ua => {
        debug("UserAgent:" + ua)
        PlainTextResponse("loggedUserAgent")
      })
    }
    case Req("studentActivity" :: Nil,_,_) if Globals.isAnalyst => () => Stopwatch.time("MeTLRestHelper.studentActivity", {
      val courseId = S.param("courseId")
//      val from = S.param("from").map()
//      val to = S.param("to")
      Full(PlainTextResponse(StudentActivityReportHelper.studentActivityCsv(courseId,None,None),
        List(("Content-Type", "text/csv"),
          ("Content-Disposition", "attachment; filename=studentActivity-" + courseId + ".csv"),
          ("Pragma", "no-cache"),
          ("Expires", "0")),
        200))
    })
    case r@Req(List("submitProblemReport"), _, PostRequest) =>
      () =>
      Stopwatch.time("MeTLStatefulRestHelper.submitProblemReport", {
        for {
          t <- S.runTemplate(List("_problemReported"))
        } yield {
          val reporter = r.param("reporter").getOrElse("unknown reporter")
          val context = r.param("context").getOrElse("unknown context")
          val report = r.param("report").getOrElse("unknown report")
          val nextRandom = nextFuncName(new Date().getTime)
          val reportId = nextRandom.substring(nextRandom.length - 7, nextRandom.length - 1)

          val detectedState = (for {
            s <- S.session
            ds <- tryo({S.initIfUninitted(s){
              (Globals.currentUser.is,Option(Globals.casState.is))
            }})
          } yield {
            ds
          }).getOrElse((reporter,None))

          val detectedUser = detectedState._1
          val casState = detectedState._2.getOrElse("").toString

          val userAgent = r.userAgent.getOrElse("")

          error("Problem report from %s (#%s). Reporter: %s, Context: %s, Report: %s, UserAgent: %s, CAS State: %s".format(r.hostName, reportId, detectedUser, context, report, userAgent, casState))
          if (Globals.mailer.nonEmpty) {
            Globals.mailer.get.sendMailMessage("Problem Report from %s (#%s)".format(r.hostName, reportId),
              "Host: %s\nReport ID: %s\nReporter: %s\nContext: %s\n\nReport:\n%s\n\nUserAgent:\n%s\n\nCAS State:\n%s".format(r.hostName, reportId, detectedUser, context, report, userAgent, casState))
          }

          val output = (
            "#reporter *" #> reporter &
              "#context *" #> context &
              "#reportId *" #> reportId
          ).apply(t)
          XhtmlResponse(output.head, Empty, Nil, Nil, 200, false)
        }
      })
  }
}
object WebMeTLStatefulRestHelper extends RestHelper with Logger{
  debug("WebMeTLStatefulRestHelper inline")
  serve {
    case Req("slide" :: jid :: size :: Nil,_,_) => () => Full(HttpResponder.snapshot(jid,size))
    case Req("quizImage" :: jid :: id :: Nil,_,_) => () => Full(HttpResponder.quizImage(jid,id))
    case Req(server :: "quizResponse" :: conversation :: quiz :: response :: Nil,_,_)
      if !List(server, conversation, quiz, response).contains("") => () => {
        val slide = S.param("slide").openOr("")
        Full(QuizResponder.handleResponse(server,conversation,slide,quiz,response))
      }
  }
}
