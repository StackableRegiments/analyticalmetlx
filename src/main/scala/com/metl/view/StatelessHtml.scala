package com.metl.view

import com.metl.data._
import com.metl.utils._
import _root_.net.liftweb._
import http._
import common._
import util.Helpers._
import java.io.{BufferedInputStream, BufferedOutputStream, ByteArrayInputStream, ByteArrayOutputStream, File, FileReader}
import javax.imageio._

import org.apache.commons.io.IOUtils

import scala.xml._
import java.util.zip.{ZipEntry, ZipInputStream}

import scala.collection.mutable.StringBuilder
import net.liftweb.util.Helpers._
import bootstrap.liftweb.Boot
import net.liftweb.json._
import java.util.zip._

import com.metl.external.{Detail, LiftAuthStateData, MeTLingPotAdaptor}
import com.metl.model._

/**
  * Use Lift's templating without a session and without state
  */
object StatelessHtml extends Stemmer with Logger {
  private val config = ServerConfiguration.default
  implicit val formats = DefaultFormats
  val serializer = new GenericXmlSerializer(config)
  val jsonSerializer = new JsonSerializer(config)
  val metlClientSerializer = new GenericXmlSerializer(config){
    override def metlXmlToXml(rootName:String,additionalNodes:Seq[Node],wrapWithMessage:Boolean = false,additionalAttributes:List[(String,String)] = List.empty[(String,String)]) = Stopwatch.time("GenericXmlSerializer.metlXmlToXml",  {
      val messageAttrs = List(("xmlns","jabber:client"),("to","nobody@nowhere.nothing"),("from","metl@local.temp"),("type","groupchat"))
      val attrs = (additionalAttributes ::: (rootName match {
        case "quizOption" => List(("xmlns","monash:metl"))
        case _ => messageAttrs
      })).foldLeft(scala.xml.Null.asInstanceOf[scala.xml.MetaData])((acc,item) => {
        item match {
          case (k:String,v:String) => new UnprefixedAttribute(k,v,acc)
          case _ => acc
        }
      })
      wrapWithMessage match {
        case true => {
          new Elem(null, "message", attrs, TopScope, false, new Elem(null, rootName, new UnprefixedAttribute("xmlns","monash:metl",Null.asInstanceOf[scala.xml.MetaData]), TopScope, false, additionalNodes: _*))
        }
        case _ => new Elem(null, rootName, attrs, TopScope, false, additionalNodes:_*)
      }
    })
  }
  val exportSerializer = new ExportXmlSerializer(config){
  }
  private val fakeSession = new LiftSession("/", "fakeSession", Empty)

  def summaries(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.summaries", {
    val xml: Box[NodeSeq] = S.init(req, fakeSession) {
      S.runTemplate(List("summaries"))
    }
    xml.map(ns => XhtmlResponse(ns(0), Empty, Nil, Nil, 200, false))
  })

  def appCache(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.appCache", {
    S.request.flatMap(_.request match {
      case hrs: net.liftweb.http.provider.servlet.HTTPRequestServlet =>{
        val file = hrs.req.getRealPath("static/offline/analytics.appcache")
        debug(file)
        val reader = new FileReader(file)
        val content = IOUtils.toByteArray(reader)
        trace(new String(content))
        reader.close
        Full(InMemoryResponse(content,List(("Content-Type","text/cache-manifest")),Nil,200))
      }
      case _ => Empty
    })
  })

  def listGroups(username:String,informationGroups:List[Tuple2[String,String]] = Nil):Box[LiftResponse] = Stopwatch.time("StatelessHtml.listGroups",{
    val prelimAuthStateData = LiftAuthStateData(false,username,Nil,informationGroups.map(t => Detail(t._1,t._2)))
    val groups = Globals.groupsProviders.flatMap(_.getGroupsFor(prelimAuthStateData))
    val personalDetails = Globals.groupsProviders.flatMap(_.getPersonalDetailsFor(prelimAuthStateData))
    val lasd = LiftAuthStateData(false,username,groups,personalDetails)
    Full(JsonResponse(Extraction.decompose(lasd),200))
  })

  def listRooms:Box[LiftResponse] = Stopwatch.time("StatelessHtml.listRooms", {
    Full(PlainTextResponse(MeTLXConfiguration.listRooms(config.name).map(_.location).mkString("\r\n")))
  })
  def listUsersInRooms:Box[LiftResponse] = Stopwatch.time("StatelessHtml.listUsersInRooms", {
    Full(PlainTextResponse(MeTLXConfiguration.listRooms(config.name).map(room => {
      "%s\r\n%s".format(room.location,room.getChildren.map(c => "\t%s".format(c._1)).mkString("\r\n"))
    }).mkString("\r\n")))
  })
  def listSessions:Box[LiftResponse] = Stopwatch.time("StatelessHtml.listSessions", {
    val now = new java.util.Date().getTime
    val sessions = SecurityListener.activeSessions.map(s => (s,(now - s.lastActivity).toDouble / 1000)).sortBy(_._2).map(s => {
      if (s._1.authenticatedUser == s._1.username){
        "%s (%s) : %s => %s (%.3fs ago)".format(s._1.authenticatedUser,s._1.ipAddress,s._1.started,s._1.lastActivity,s._2)
      } else {
        "%s impersonating %s (%s) : %s => %s (%.3fs ago)".format(s._1.authenticatedUser,s._1.username,s._1.ipAddress,s._1.started,s._1.lastActivity,s._2)
      }
    }).mkString("\r\n")
    Full(PlainTextResponse(sessions))
  })
  def listMeTLingPots:Box[LiftResponse] = Stopwatch.time("StatelessHtml.listMeTLingPots", {
    Full(JsonResponse(JArray(Globals.metlingPots.map(mp => JObject(mp.description))),200))
  })
  def describeUser(user:LiftAuthStateData = Globals.casState.is):Box[LiftResponse] = Stopwatch.time("StatelessHtml.describeUser",{
    Full(JsonResponse(Extraction.decompose(user),200))
  })
  def impersonate(newUsername:String,params:List[Tuple2[String,String]] = Nil):Box[LiftResponse] = Stopwatch.time("StatelessHtml.impersonate", {
    describeUser(Globals.impersonate(newUsername,params))
  })
  def deImpersonate:Box[LiftResponse] = Stopwatch.time("StatelessHtml.deImpersonate", {
    describeUser(Globals.assumeContainerSession)
  })
  def loadSearch(query:String,config:ServerConfiguration = ServerConfiguration.default):Node = Stopwatch.time("StatelessHtml.loadSearch", {
    <conversations>{config.searchForConversation(query).map(c => serializer.fromConversation(c))}</conversations>
  })

  def search(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.search", {
    req.param("query").map(q=>XmlResponse(loadSearch(q)))
  })

  def setUserOptions(req:Req):Box[LiftResponse] = Stopwatch.time("StatelessHtml.setUserOptions", {
    req.body.map(bytes => {
      InMemoryResponse(Array.empty[Byte],Nil,Nil,200)
    })
  })
  def getUserOptions(req:Req):Box[LiftResponse] = Stopwatch.time("StatelessHtml.getUserOptions",{
    Full(InMemoryResponse(config.getResource("userOptionsFor_%s".format(Globals.currentUser)),Nil,Nil,200))
  })
  def externalSlides = {
    XML.load("https://metl.saintleo.edu/search?query=") \\ "slide" \ "id"
  }
  def externalSlideSummary(slide:String):Box[Tuple2[String,Elem]] = Stopwatch.time("StatelessHtml.externalSlideSummary",{
    info("externalSlideSummary %s".format(slide))
    try{
      val outfile = "/stackable/samples/slides/%s.xml".format(slide)
      Full((slide, if(new File(outfile).exists){
        XML.loadFile(outfile)
      }
      else{
        val x = XML.load("https://metl.saintleo.edu/describeHistory?source=%s".format(slide))
        XML.save(outfile,x)
        x
      }))
    }
    catch{
      case e:Exception => {
        error("externalSlideSummary exception for %s:".format(slide,e))
        Empty
      }
    }
  })

  def proxyDataUri(slideJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxyDataUri(%s)".format(identity),
    Full(MeTLXConfiguration.getRoom(slideJid,config.name,RoomMetaDataUtils.fromJid(slideJid)).getHistory.getImageByIdentity(identity).map(image => {
      image.imageBytes.map(bytes => {
        debug("found bytes: %s (%s)".format(bytes,bytes.length))
        val dataUri = "data:image/png;base64,"+net.liftweb.util.SecurityHelpers.base64Encode(bytes)
        InMemoryResponse(dataUri.getBytes(),Boot.cacheStrongly,Nil,200)
      }).openOr(NotFoundResponse("image bytes not available"))
    }).getOrElse(NotFoundResponse("image not available"))))

  def proxyImageUrl(slideJid:String,url:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxyImageUrl(%s)".format(url),{
    val room = MeTLXConfiguration.getRoom(slideJid,config.name,RoomMetaDataUtils.fromJid(slideJid))
    val history = room.getHistory
    val images = history.getImages
    trace("room: %s\r\nhistory: %s\r\nimages: %s\r\nurl: %s".format(room,history,images,url))
    Full(images.find(_.source.exists(_ == url)).map(image => {
      trace("room: %s\r\nhistory: %s\r\nimages: %s\r\nimageOption: %s".format(room,history,images,image))
      image.imageBytes.map(bytes => {
        trace("found bytes: %s (%s)".format(bytes,bytes.length))
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        InMemoryResponse(bytes,headers,Nil,200)
      }).openOr(NotFoundResponse("image bytes not available"))
    }).getOrElse({
      NotFoundResponse("image not available")
    }))
  })
  def proxy(slideJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxy(%s)".format(identity), {
    Full({
      val room = MeTLXConfiguration.getRoom(slideJid,config.name,RoomMetaDataUtils.fromJid(slideJid))
      val history = room.getHistory
      val imageOption = history.getImageByIdentity(identity)
      trace("room: %s\r\nhistory: %s\r\nimages: %s\r\nimageOption: %s".format(room,history,history.getImages,imageOption))
      imageOption.map(image => {
        image.imageBytes.map(bytes => {
          trace("found bytes: %s (%s)".format(bytes,bytes.length))
          val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
          InMemoryResponse(bytes,headers,Nil,200)
        }).openOr(NotFoundResponse("image bytes not available"))
      }).getOrElse(NotFoundResponse("image not available"))
    })
  })
  def quizProxy(conversationJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.quizProxy(%s, %s)".format(conversationJid,identity), {
    Full(MeTLXConfiguration.getRoom(conversationJid,config.name,ConversationRoom(config,conversationJid)).getHistory.getQuizByIdentity(identity).map(quiz => {
      quiz.imageBytes.map(bytes => {
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        InMemoryResponse(bytes,headers,Nil,200)
      }).openOr(NotFoundResponse("quiz image bytes not available"))
    }).getOrElse(NotFoundResponse("quiz not available")))
  })
  def quizResultsGraphProxy(conversationJid:String,identity:String,width:Int,height:Int)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.quizResultsGraphProxy(%s, %s)".format(conversationJid,identity), {
    val history = MeTLXConfiguration.getRoom(conversationJid,config.name,ConversationRoom(config,conversationJid)).getHistory
    val responses = history.getQuizResponses.filter(qr => qr.id == identity)
    Full(history.getQuizByIdentity(identity).map(quiz => {
      val bytes = com.metl.renderer.QuizRenderer.renderQuiz(quiz,responses,new com.metl.renderer.RenderDescription(width,height))
      val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
      InMemoryResponse(bytes,headers,Nil,200)
    }).getOrElse(NotFoundResponse("quiz not available")))
  })
  def resourceProxy(identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.resourceProxy(%s)".format(identity), {
    val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
    Full(InMemoryResponse(config.getResource(identity),headers,Nil,200))
  })
  def attachmentProxy(conversationJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.attachmentProxy(%s)".format(identity), {
    MeTLXConfiguration.getRoom(conversationJid,config.name,ConversationRoom(config,conversationJid)).getHistory.getFiles.find(_.id == identity).map(file => {
      val headers = List(
        ("mime-type","application/octet-stream"),
        ("Content-Disposition","""attachment; filename="%s"""".format(file.name))
      ) ::: Boot.cacheStrongly
      InMemoryResponse(file.bytes.getOrElse(Array.empty[Byte]),headers,Nil,200)
    })
  })
  def submissionProxy(conversationJid:String,author:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.submissionProxy(%s, %s)".format(conversationJid,identity), {
    Full(MeTLXConfiguration.getRoom(conversationJid,config.name,ConversationRoom(config,conversationJid)).getHistory.getSubmissionByAuthorAndIdentity(author,identity).map(sub => {
      sub.imageBytes.map(bytes => {
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        InMemoryResponse(bytes,headers,Nil,200)
      }).openOr(NotFoundResponse("submission image bytes not available"))
    }).getOrElse(NotFoundResponse("submission not available")))
  })
  def loadDetails(jid:String,config:ServerConfiguration = ServerConfiguration.default):Node = Stopwatch.time("StatelessHtml.loadDetails", {
    serializer.fromConversation(config.detailsOfConversation(jid)).head
  })
  def details(jid:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.details", {
    Full(XmlResponse(loadDetails(jid)))
  })

  def loadHistory(jid:String):Node= Stopwatch.time("StatelessHtml.loadHistory(%s)".format(jid), {
    <history>{serializer.fromRenderableHistory(getSecureHistoryForRoom(jid,Globals.currentUser.is))}</history>
  })
  def loadMergedHistory(jid:String,username:String):Node = Stopwatch.time("StatelessHtml.loadMergedHistory(%s)".format(jid),{
    <history>{serializer.fromRenderableHistory(getSecureHistoryForRoom(jid,username).merge(getSecureHistoryForRoom(jid+username,username)))}</history>
  })
  def history(jid:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.history(%s)".format(jid), Full(XmlResponse(loadHistory(jid))))

  def jsonHistory(jid:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.jsonHistory(%s)".format(jid), Full(JsonResponse(jsonSerializer.fromHistory(getSecureHistoryForRoom(jid,Globals.currentUser.is)))))

  protected def getSecureHistoryForRoom(jid:String,username:String):History = {
    val room = MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid))
    val history = room.getHistory
    val (convHistory,isTeacher) = {
      room match {
        case cr:ConversationRoom => {
          val isT = Globals.isSuperUser || config.detailsOfConversation(cr.jid.toString).author == username
          val h = history
          (h,isT)
        }
        case s:SlideRoom => {
          val isT = Globals.isSuperUser || config.detailsOfConversation(s.jid.toString).author == username
          val h = MeTLXConfiguration.getRoom(s.jid.toString,config.name).getHistory
          (h,isT)
        }
        case _ => (History.empty,false)
      }
    }
    lazy val allGrades = Map(convHistory.getGrades.groupBy(_.id).values.toList.flatMap(_.sortWith((a,b) => a.timestamp < b.timestamp).headOption.map(g => (g.id,g)).toList):_*)
    val finalHistory = history.filter{
      case gv:MeTLGradeValue if isTeacher => true
      case gv:MeTLGradeValue if allGrades.get(gv.getGradeId).exists(_.visible == true) && gv.getGradedUser == username => true
      case gv:MeTLGradeValue => false
      case qr:MeTLQuizResponse if isTeacher || qr.author == username => true
      case s:MeTLSubmission if isTeacher || s.author == username => true
      case qr:MeTLQuizResponse  => false
      case s:MeTLSubmission => false
      case _ => true
    }
    finalHistory
  }
  def fullHistory(jid:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.fullHistory(%s)".format(jid), Full(XmlResponse(<history>{
    getSecureHistoryForRoom(jid,Globals.currentUser.is).getAll.map(s => serializer.fromMeTLData(s))
  }</history>)))


  def byteArrayHeaders(filename:String):List[Tuple2[String,String]] = {
    List(
      "Content-Type" -> "application/octet-stream",
      "Content-Disposition" -> "attachment; filename=%s".format(filename)
    )
  }
  def yawsResource(rootPart:String,room:String,item:String,suffix:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.yawsResource(%s/%s)".format(rootPart,room,item,suffix),{
    rootPart match {
      case "Structure" if item == "structure" && suffix == "xml" => {
        Full(XmlResponse(serializer.fromConversation(config.detailsOfConversation(room)).head))
      }
      case "Resource" => {
        val originalPath = "/%s/%s/%s/%s.%s".format(rootPart,stem(room),room,item,suffix)
        debug("yaws looking for: %s".format(originalPath))
        val bytes = Array.empty[Byte] //stil need to find the right resource
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        Full(InMemoryResponse(bytes,byteArrayHeaders("%s.%s".format(item,suffix)),Nil,200))
      }
      case _ => {
        debug("did not find an appropriate downloadable")
        Empty
      }
    }
  })
  def constructZip(in:List[Tuple2[String,Array[Byte]]]):Array[Byte] = {
    // the tuple represents filename (in the format of a path, eg 'firstDepth/nDepth/filename.suffix') and the bytes that file has.  Not worrying about any complex permissions in this zip.
    val baos = new ByteArrayOutputStream()
    val zos = new ZipOutputStream(new BufferedOutputStream(baos))
    in.foreach(tup => {
      zos.putNextEntry(new ZipEntry(tup._1))
      zos.write(tup._2,0,tup._2.length)
    })
    zos.close()
    val zipBytes = baos.toByteArray()
    baos.close()
    zipBytes
  }
  def yawsHistory(jid:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.yawsHistory(%s)".format(jid),{
    val xml = <history>{getSecureHistoryForRoom(jid,Globals.currentUser.is).getAll.map(s => serializer.fromMeTLData(s))}</history>
    val filename = "combined.xml"
    val xmlBytes = xml.toString.getBytes("UTF-8")
    Full(InMemoryResponse(constructZip(List((filename,xml.toString.getBytes("UTF-8")))),byteArrayHeaders("all.zip"),Nil,200))
  })
  def yawsPrimaryKey:Box[LiftResponse] = Stopwatch.time("StatelessHtml.yawsPrimaryKey",{
    val newConv = config.createConversation("createdForTheKey-%s".format(new java.util.Date().getTime.toString),Globals.currentUser.is)
    config.deleteConversation(newConv.jid.toString)
    Full(PlainTextResponse("{id,%s}.".format(newConv.jid),Nil,200))
  })
  def yawsUploadNested(path:String,filename:String,overwrite:Boolean,bytes:Array[Byte])():Box[LiftResponse] = Stopwatch.time("StatelessHtml.yawsUploadNested(%s,%s,%s,%s)".format(path,filename,overwrite,bytes.length),{
    val pathParts = path.split("/").toList
    pathParts match {
      case List("Structure",stemmed,jid) if stem(jid)._1 == stemmed && filename == "structure.xml" => {
        debug("conversationDetails updated detected: %s".format(jid))
      }
      case List("Resource",stemmed,jid) if stem(jid)._1 == stemmed => {
        debug("resource update detected: %s".format(jid))
      }
      case other => {
        debug("other detected (no action taken): %s".format(other))
      }
    }
    val identity = "not-yet-implemented"
    Full(XmlResponse(<resource url={identity}/>))
  })

  def fullClientHistory(jid:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.fullClientHistory(%s)".format(jid), Full(XmlResponse(<history>{getSecureHistoryForRoom(jid,Globals.currentUser.is).getAll.map(s => metlClientSerializer.fromMeTLData(s))}</history>)))

  def mergedHistory(jid:String,onBehalfOf:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.mergedHistory(%s)".format(jid), Full(XmlResponse(loadMergedHistory(jid,onBehalfOf))))

  def describeHistory(jid:String,format:Box[String]=Full("xml"))():Box[LiftResponse] = Stopwatch.time("StatelessHtml.describeHistory(%s)".format(jid),{
    val room = MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid))
    val history = getSecureHistoryForRoom(jid,Globals.currentUser.is)
    val stanzas = history.getAll
    val allContent = stanzas.length
    val publishers = stanzas.groupBy(_.author)
    val canvasContent = history.getCanvasContents.length
    val strokes = history.getInks.length
    val texts = history.getTexts.length
    val images = history.getImages.length
    val files = history.getFiles.length
    val quizzes = history.getQuizzes.length
    val highlighters = history.getHighlighters.length
    val attendances = history.getAttendances
    val uniqueOccupants = attendances.groupBy(_.author)
    val occupantCount = uniqueOccupants.keys.size
    val xResponse = <historyDescription>
    <bounds>
    <left>{Text(history.getLeft.toString)}</left>
    <right>{Text(history.getRight.toString)}</right>
    <top>{Text(history.getTop.toString)}</top>
    <bottom>{Text(history.getBottom.toString)}</bottom>
    </bounds>
    <lastModified>{history.lastModified}</lastModified>
    <lastVisuallyModified>{history.lastVisuallyModified}</lastVisuallyModified>
    <roomType>{Text(room.roomMetaData.toString)}</roomType>
    <jid>{Text(jid)}</jid>
    <stanzaCount>{Text(allContent.toString)}</stanzaCount>
    <canvasContentCount>{Text(canvasContent.toString)}</canvasContentCount>
    <imageCount>{Text(images.toString)}</imageCount>
    <strokeCount>{Text(strokes.toString)}</strokeCount>
    <highlighterCount>{Text(highlighters.toString)}</highlighterCount>
    <textCount>{Text(texts.toString)}</textCount>
    <quizzes>{Text(quizzes.toString)}</quizzes>
    <files>{Text(files.toString)}</files>
    <uniquePublishers>{publishers.map(pub => {
      <publisher>
      <name>{pub._1}</name>
      <activityCount>{pub._2.length}</activityCount>
      </publisher>
    })}</uniquePublishers>
    <uniqueOccupants>{uniqueOccupants.map(occ => {
      <occupant>
      <name>{occ._1}</name>
      <activity>{
        occ._2.map(action => {
          <action>
          <location>{Text(action.location)}</location>
          <timestamp>{Text(action.timestamp.toString)}</timestamp>
          <present>{Text(action.present.toString)}</present>
          </action>
        })
      }</activity>
      </occupant>
    })}</uniqueOccupants>
    <occupants>{Text(occupantCount.toString)}</occupants>
    <snapshot>{
      Text(base64Encode(room.getThumbnail))
    }</snapshot>
    </historyDescription>
    format.flatMap(f => f.toLowerCase().trim() match {
      case "json" => Full(JsonResponse(json.Xml.toJson(xResponse)))
      case "xml" => Full(XmlResponse(xResponse))
      case _ => Empty
    })
  })

  protected val acceptableFormats: Seq[String] = List("xml","json")
  protected val rounding: Int = 5 * 1000 // 5 seconds for rounding purposes
  def generateJson(convs:List[Conversation],commands:Map[Option[String],List[MeTLCommand]]):JObject = {
    JObject(List(
      JField("conversations",JArray(
        convs.map(c => {
          val edits = (c.created :: List(c.lastAccessed).filterNot(_ == c.created) :::
            commands.get(Some(c.jid.toString)).toList.flatten.map(_.timestamp)).groupBy(_ / rounding).values.flatMap(_.headOption).toList.map(JInt(_))
          JObject(List(
            JField("jid",JInt(c.jid)),
            JField("author",JString(c.author)),
            JField("created",JInt(c.created)),
            JField("edits",JArray(edits)),
            JField("slideCount",JInt(c.slides.length))
          ))
        })
      ))
    ))
  }
  protected def jsonToXml(in:JValue):NodeSeq = {
    in match {
      case JObject(fields) => <object>{fields.map(jsonToXml)}</object>
      case JArray(items) => <array>{items.map(i => <item>{jsonToXml(i)}</item>)}</array>
      case JField(name,value) => Elem(null,name,Null,scala.xml.TopScope,false,jsonToXml(value).headOption.getOrElse(Text("")))
      case JString(str) => Text(str)
      case JInt(num) => Text(num.toString)
      case JDouble(num) => Text(num.toString)
      case JBool(bool) => Text(bool.toString)
      case JNull => Text("")
      case JNothing => Text("")
      case _ => Text("")
    }
  }
  def describeConversations(query:String,format:Box[String]=Full("xml"))():Box[LiftResponse] = Stopwatch.time("StatelessHtml.describeConversations(%s)".format(query),{
    describeConversationByList(config.searchForConversation(query),format)
  })
  def describeConversation(jid:String,format:Box[String]=Full("xml"))():Box[LiftResponse] = Stopwatch.time("StatelessHtml.describeConversation(%s)".format(jid),{
    describeConversationByList(List(config.detailsOfConversation(jid)).filterNot(c => c == Conversation.empty),format)
  })
  def describeConversationByList(convs: => List[Conversation],format:Box[String]=Full("xml"))():Box[LiftResponse] = Stopwatch.time("StatelessHtml.describeConversationByList()", {
    format.filter(f => acceptableFormats.contains(f.toLowerCase.trim)).flatMap(f => {
      val globalRoom = MeTLXConfiguration.getRoom("global", config.name)
      val commands:Map[Option[String],List[MeTLCommand]] = globalRoom.getHistory.getCommands.filter(_.command == "/UPDATE_CONVERSATION_DETAILS").groupBy(_.commandParameters.headOption)
      f.trim.toLowerCase match {
        case "json" => Full(JsonResponse(generateJson(convs,commands)))
        case "xml" => jsonToXml(generateJson(convs,commands)).headOption.map(XmlResponse(_))
        case _ => Empty
      }
    })
  })
  def addGroupTo(onBehalfOfUser:String,conversation:String,slideId:String,groupDef:GroupSet):Box[LiftResponse] = Stopwatch.time("StatelessHtml.addGroupTo(%s,%s)".format(conversation,slideId),{
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
  def updateConversation(onBehalfOfUser:String,conversationJid:String,req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.updateConversation(%s)".format(conversationJid),{
    val conv = config.detailsOfConversation(conversationJid)
    for (
      newConvBytes <- req.body;
      newConvString = new String(newConvBytes,"UTF-8");
      newConv = serializer.toConversation(XML.loadString(newConvString));
      updatedConv = config.updateConversation(conversationJid,newConv);
      node <- serializer.fromConversation(updatedConv).headOption
    ) yield {
      XmlResponse(node)
    }
  })
  def createConversation(onBehalfOfUser:String,title:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.createConversation", {
    serializer.fromConversation(config.createConversation(title,onBehalfOfUser)).headOption.map(n => XmlResponse(n))
  })
  def addSlideAtIndex(onBehalfOfUser:String,jid:String,afterSlideId:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.addSlideAtIndex", {
    val conv = config.detailsOfConversation(jid)
    if (onBehalfOfUser == conv.author){
      val newConv = conv.slides.find(_.id.toString == afterSlideId).map(slide => {
        config.addSlideAtIndexOfConversation(jid,slide.index + 1)
      }).getOrElse(conv)
      serializer.fromConversation(newConv).headOption.map(n => XmlResponse(n))
    } else {
      Full(ForbiddenResponse("only the author may duplicate slides in a conversation"))
    }
  })
  protected def shouldModifyConversation(c:Conversation):Boolean = {
    Globals.currentUser.is == c.author
  }
  def addQuizViewSlideToConversationAtIndex(jid:String,index:Int,quizId:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.addQuizViewSlideToConversationAtIndex", {
    val username = Globals.currentUser.is
    val server = config.name
    val c = config.detailsOfConversation(jid)
    serializer.fromConversation(shouldModifyConversation(c) match {
      case true => {
        val newC = config.addSlideAtIndexOfConversation(c.jid.toString,index)
        newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
          val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
          val convHistory = MeTLXConfiguration.getRoom(jid,server).getHistory
          convHistory.getQuizzes.filter(q => q.id == quizId && !q.isDeleted).sortBy(q => q.timestamp).reverse.headOption.map(quiz => {
            val now = new java.util.Date().getTime
            val identity = "%s%s".format(username,now.toString)
            val genText = (text:String,size:Double,offset:Double,identityModifier:String) => MeTLText(config,username,now,text,size * 2,320,0,10,10 + offset,identity+identityModifier,"Normal","Arial","Normal",size,"none",identity+identityModifier,"presentationSpace",Privacy.PUBLIC,ho.id.toString,Color(255,0,0,0))
            val quizTitle = genText(quiz.question,16,0,"title")
            val questionOffset = quiz.url match{
              case Full(_) => 340
              case _ => 100
            };
            val quizOptions = quiz.options.foldLeft(List.empty[MeTLText])((acc,item) => {
              acc ::: List(genText("%s: %s".format(item.name,item.text),10,(acc.length * 10) + questionOffset,"option:"+item.name))
            })
            val allStanzas = quiz.url.map(u => List(MeTLImage(config,username,now,identity+"image",Full(u),Empty,Empty,320,240,10,50,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"image"))).getOrElse(List.empty[MeTLStanza]) ::: quizOptions ::: List(quizTitle)
            allStanzas.foreach(stanza => slideRoom ! LocalToServerMeTLStanza(stanza))
          })
        })
        newC
      }
      case _ => c
    }).headOption.map(n => XmlResponse(n))
  })
  def addQuizResultsViewSlideToConversationAtIndex(jid:String,index:Int,quizId:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.addQuizResultsViewSlideToConversationAtIndex", {
    val username = Globals.currentUser.is
    val server = config.name
    val c = config.detailsOfConversation(jid)
    serializer.fromConversation(shouldModifyConversation(c) match {
      case true => {
        val newC = config.addSlideAtIndexOfConversation(c.jid.toString,index)
        newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
          val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
          val convHistory = MeTLXConfiguration.getRoom(jid,server).getHistory
          convHistory.getQuizzes.filter(q => q.id == quizId && !q.isDeleted).sortBy(q => q.timestamp).reverse.headOption.map(quiz => {
            val now = new java.util.Date().getTime
            val answers = convHistory.getQuizResponses.filter(qr => qr.id == quiz.id).foldLeft(Map.empty[String,MeTLQuizResponse])((acc,item) => {
              acc.get(item.answerer).map(qr => {
                if (acc(item.answerer).timestamp < item.timestamp){
                  acc.updated(item.answerer,item)
                } else {
                  acc
                }
              }).getOrElse(acc.updated(item.answerer,item))
            }).foldLeft(Map(quiz.options.map(qo => (qo,List.empty[MeTLQuizResponse])):_*))((acc,item) => {
              quiz.options.find(qo => qo.name == item._2.answer).map(qo => acc.updated(qo,item._2 :: acc(qo))).getOrElse(acc)
            })
            val serverConfig = config
            val identity = "%s%s".format(username,now.toString)
            def genText(text:String,size:Double,offset:Double,identityModifier:String,maxHeight:Option[Double] = None) = MeTLText(serverConfig,username,now,text,maxHeight.getOrElse(size * 2),640,0,10,10 + offset,identity+identityModifier,"Normal","Arial","Normal",size,"none",identity+identityModifier,"presentationSpace",Privacy.PUBLIC,ho.id.toString,Color(255,0,0,0))
            val quizTitle = genText(quiz.question,32,0,"title",Some(100))

            val graphWidth = 640
            val graphHeight = 480
            val bytes = com.metl.renderer.QuizRenderer.renderQuiz(quiz,answers.flatMap(_._2).toList,new com.metl.renderer.RenderDescription(graphWidth,graphHeight))
            val quizGraphIdentity = serverConfig.postResource(jid,"graphResults_%s_%s".format(quizId,now),bytes)
            val quizGraph = MeTLImage(serverConfig,username,now,identity+"resultsGraph",Full(quizGraphIdentity),Empty,Empty,graphWidth,graphHeight,10,100,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"resultsGraph")
            val questionOffset = graphHeight + 100
            val quizOptions = quiz.options.foldLeft(List.empty[MeTLText])((acc,item) => {
              acc ::: List(genText(
                "%s: %s (%s)".format(item.name,item.text,answers.get(item).map(as => as.length).getOrElse(0)),
                24,
                (acc.length * 30) + questionOffset,
                "option:"+item.name))
            })
            val allStanzas = quiz.url.map(u => List(MeTLImage(serverConfig,username,now,identity+"image",Full(u),Empty,Empty,320,240,330,100,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity+"image"))).getOrElse(List.empty[MeTLStanza]) ::: quizOptions ::: List(quizTitle,quizGraph)
            allStanzas.foreach(stanza => {
              slideRoom ! LocalToServerMeTLStanza(stanza)
            })
          })
        })
        newC
      }
      case _ => c
    }).headOption.map(n => XmlResponse(n))
  })
  def addSubmissionSlideToConversationAtIndex(jid:String,index:Int,req:Req):Box[LiftResponse] = Stopwatch.time("StatelessHtml.addSubmissionSlideToConversationAtIndex", {
    val username = Globals.currentUser.is
    val server = config.name
    val c = config.detailsOfConversation(jid)
    serializer.fromConversation(shouldModifyConversation(c) match {
      case true => {
        val json = req.body.map(bytes => net.liftweb.json.parse(new String(bytes)))
        debug("addSubmissionSlideToConversationAtIndex",json)
        json match {
          case Full(JArray(identities)) => {
            debug("addSubmissionSlideToConversationAtIndex: %s".format(identities))
            val newC = config.addSlideAtIndexOfConversation(c.jid.toString,index)
            newC.slides.sortBy(s => s.id).reverse.headOption.map(ho => {
              val slideRoom = MeTLXConfiguration.getRoom(ho.id.toString,server)
              val existingSubmissions = MeTLXConfiguration.getRoom(jid,server).getHistory.getSubmissions
              trace("addSubmissionSlideToConversationAtIndex: existing submissions",existingSubmissions)
              var y:Double = 0.0
              identities.map{ case JString(submissionId) => existingSubmissions.find(sub => sub.identity == submissionId).map(sub => {
                trace("Matching submission to be inserted",sub)
                val now = new java.util.Date().getTime
                val identity = nextFuncName
                val tempSubImage = MeTLImage(config,username,now,identity,Full(sub.url),sub.imageBytes,Empty,Double.NaN,Double.NaN,10,10,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
                val dimensions = slideRoom.slideRenderer.measureImage(tempSubImage)
                val subImage = MeTLImage(config,username,now,identity,Full(sub.url),sub.imageBytes,Empty,dimensions.width,dimensions.height,dimensions.left,dimensions.top + y,"presentationSpace",Privacy.PUBLIC,ho.id.toString,identity)
                y += dimensions.height
                slideRoom ! LocalToServerMeTLStanza(subImage)
              })}
            })
            newC
          }
          case _ => {
            c
          }
        }
      }
      case _ => c
    }).headOption.map(n => XmlResponse(n))
  })
  def duplicateSlideInternal(onBehalfOfUser:String,slide:String,conversation:String):Box[Conversation] = {
    val conv = config.detailsOfConversation(conversation)
    if (onBehalfOfUser == conv.author){
      val newConv = conv.slides.find(_.id.toString == slide).map(slide => {
        val step1 = config.addSlideAtIndexOfConversation(conversation,slide.index + 1)
        step1.slides.find(_.index == slide.index + 1).foreach(newSlide => {
          val oldId = slide.id
          val newId = newSlide.id
          ServerSideBackgroundWorker ! CopyLocation(
            config,
            SlideRoom(conv.server,conversation,oldId),
            SlideRoom(conv.server,conversation,newId),
            (s:MeTLStanza) => s.author == conv.author
          )
          ServerSideBackgroundWorker ! CopyLocation(
            config,
            PrivateSlideRoom(conv.server,conversation,oldId,conv.author),
            PrivateSlideRoom(conv.server,conversation,newId,conv.author),
            (s:MeTLStanza) => s.author == conv.author
          )
        })
        step1
      }).getOrElse(conv)
      Full(newConv)
    } else {
      Empty
    }
  }
  def duplicateSlide(onBehalfOfUser:String,slide:String,conversation:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.duplicateSlide", {
    duplicateSlideInternal(onBehalfOfUser,slide,conversation).map(conv => {
      serializer.fromConversation(conv).headOption.map(n => XmlResponse(n)) match {
        case Some(r) => Full(r)
        case _ => Empty
      }
    }).getOrElse({
      Full(ForbiddenResponse("only the author may duplicate slides in a conversation"))
    })
  })
  def duplicateConversationInternal(onBehalfOfUser:String,conversation:String):Box[Conversation] = {
    val oldConv = config.detailsOfConversation(conversation)
    if (com.metl.snippet.Metl.shouldModifyConversation(onBehalfOfUser,oldConv)){
      val newConv = config.createConversation(oldConv.title + " (copied at %s)".format(new java.util.Date()),oldConv.author)
      val newConvWithOldSlides = newConv.copy(
        lastAccessed = new java.util.Date().getTime,
        slides = oldConv.slides.map(s => s.copy(
          groupSet = Nil,
          id = s.id - oldConv.jid + newConv.jid,
          audiences = Nil
        ))
      )
      val remoteConv = config.updateConversation(newConv.jid.toString,newConvWithOldSlides)
      remoteConv.slides.foreach(ns => {
        val newSlide = ns.id
        val slide = newSlide - newConv.jid + oldConv.jid
        val conv = remoteConv
        ServerSideBackgroundWorker ! CopyLocation(
          config,
          SlideRoom(conv.server,conversation,slide),
          SlideRoom(conv.server,remoteConv.jid.toString,newSlide),
          (s:MeTLStanza) => s.author == conv.author
        )
        ServerSideBackgroundWorker ! CopyLocation(
          config,
          PrivateSlideRoom(conv.server,conversation,slide,conv.author),
          PrivateSlideRoom(conv.server,remoteConv.jid.toString,newSlide,conv.author),
          (s:MeTLStanza) => s.author == conv.author
        )
      })
      ServerSideBackgroundWorker ! CopyLocation(
        config,
        ConversationRoom(remoteConv.server,remoteConv.jid.toString),
        ConversationRoom(newConv.server,newConv.jid.toString),
        (s:MeTLStanza) => s.author == remoteConv.author && s.isInstanceOf[MeTLQuiz] // || s.isInstanceOf[Attachment]
      )
      val remoteConv2 = config.updateConversation(remoteConv.jid.toString,remoteConv)
      Full(remoteConv2)
    } else Empty
  }
  def duplicateConversation(onBehalfOfUser:String,conversation:String):Box[LiftResponse] = Stopwatch.time("MeTLStatefulRestHelper.duplicateConversation", {
    duplicateConversationInternal(onBehalfOfUser,conversation).map(conv => {
      serializer.fromConversation(conv).headOption.map(n => XmlResponse(n)) match {
        case Some(r) => Full(r)
        case _ => Empty
      }
    }).getOrElse({
      Full(ForbiddenResponse("only the author may duplicate a conversation"))
    })
  })
  def exportMyConversation(onBehalfOfUser:String,conversation:String):Box[LiftResponse] = {
    for (
      conv <- Some(config.detailsOfConversation(conversation));
      histories = exportHistories(conv,Some(List(onBehalfOfUser)));
      xml = {
        <export>
        {exportSerializer.fromConversation(conv)}
        <histories>{histories.map(h => exportSerializer.fromHistory(h))}</histories>
        </export>
      };
      node <- xml.headOption
    ) yield {
      XmlResponse(node)
    }
  }
  def exportConversation(onBehalfOfUser:String,conversation:String):Box[LiftResponse] = Stopwatch.time("StatelessHtml.exportConversation",{
    for (
      conv <- Some(config.detailsOfConversation(conversation));
      if (com.metl.snippet.Metl.shouldModifyConversation(onBehalfOfUser,conv));
      histories = exportHistories(conv,None);
      xml = {
        <export>
        {exportSerializer.fromConversation(conv)}
        <histories>{histories.map(h => exportSerializer.fromHistory(h))}</histories>
        </export>
      };
      node <- xml.headOption
    ) yield {
      XmlResponse(node)
    }
  })
  protected def exportHistories(conversation:Conversation,restrictToPrivateUsers:Option[List[String]]):List[History] = {
    val cHistory = config.getHistory(conversation.jid.toString)
    val allGrades = Map(cHistory.getGrades.groupBy(_.id).values.toList.flatMap(_.sortWith((a,b) => a.timestamp < b.timestamp).headOption.map(g => (g.id,g)).toList):_*)
    val convHistory = cHistory.filter(m => {
      restrictToPrivateUsers.map(users => {
        m match {
          case q:MeTLQuiz => true
          case mcc:MeTLCanvasContent => mcc.privacy == Privacy.PUBLIC || users.contains(mcc.author)
          case g:MeTLGrade => true
          case gv:MeTLGradeValue if allGrades.get(gv.getGradeId).exists(_.visible == true) && users.contains(gv.getGradedUser) => true
          case gv:MeTLGradeValue => false
          case qr:MeTLQuizResponse if users.contains(qr.author) => true
          case s:MeTLSubmission if users.contains(s.author) => true
          case qr:MeTLQuizResponse => false
          case s:MeTLSubmission  => false
          case ms:MeTLStanza => users.contains(ms.author)
        }
      }).getOrElse(true)
    })
    val participants = (convHistory.getAttendances.map(_.author) ::: restrictToPrivateUsers.getOrElse(List.empty[String])).distinct.filter(u => restrictToPrivateUsers.map(_.contains(u)).getOrElse(true))
    val histories = convHistory :: conversation.slides.flatMap(slide => {
      val slideJid = slide.id.toString
      val publicHistory = config.getHistory(slideJid)
      val privateHistories = participants.map(p => config.getHistory(slideJid + p))
      publicHistory :: privateHistories
    })
    histories
  }
  def importExportedConversation(req:Req,rewrittenUsername:Option[String] = Empty):Box[LiftResponse] = Stopwatch.time("StatelessHtml.importExportedConversation",{
    for {
      xml <- req.body.map(bytes => XML.loadString(new String(bytes, "UTF-8")))
      tag = req.param("importDescription")
      conv <- com.metl.model.Importer.importExportedConversation(xml,tag,rewrittenUsername)
      node <- serializer.fromConversation(conv).headOption
    } yield {
      XmlResponse(node)
    }
  })
  def importConversationAsMe(req:Req):Box[LiftResponse] = Stopwatch.time("StatelessHtml.importConversationAsMe",{
    for (
      firstFile <- req.uploadedFiles.headOption;
      bytes = firstFile.file;
      filename = firstFile.fileName;
      author = Globals.currentUser.is;
      title = "%s's (%s) created at %s".format(author, filename, new java.util.Date());

      remoteConv <- com.metl.model.Importer.importConversationAsAuthor(title, filename, bytes, author);
      node <- serializer.fromConversation(remoteConv).headOption
    ) yield {
      XmlResponse(node)
    }
  })
  def powerpointImport(req:Req):Box[LiftResponse] = Stopwatch.time("StatelessHtml.powerpointImport",{
    for (
      title <- req.param("title");
      bytes <- req.body;
      author = Globals.currentUser.is;
      remoteConv <- com.metl.model.Importer.importConversationAsAuthor(title, title, bytes, author);
      node <- serializer.fromConversation(remoteConv).headOption
    ) yield {
      XmlResponse(node)
    }
  })
}
