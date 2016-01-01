package com.metl.view

import com.metl.data._
import com.metl.utils._


import _root_.net.liftweb._
import http._
import common._
import util.Helpers._
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
  val metlClientSerializer = new GenericXmlSerializer("metlClient"){
    override def metlXmlToXml(rootName:String,additionalNodes:Seq[Node],wrapWithMessage:Boolean = false,additionalAttributes:List[(String,String)] = List.empty[(String,String)]) = Stopwatch.time("GenericXmlSerializer.metlXmlToXml", () => {
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
  val exportSerializer = new ExportXmlSerializer("export"){
  }
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

  def setUserOptions(req:Req):Box[LiftResponse] = Stopwatch.time("StatelessHtml.setUserOptions", () => {
    req.body.map(bytes => {
      InMemoryResponse(Array.empty[Byte],Nil,Nil,200)
    })
  })
  def getUserOptions(req:Req):Box[LiftResponse] = Stopwatch.time("StatelessHtml.getUserOptions", () => {
    Full(InMemoryResponse(config.getResource("userOptionsFor_%s".format(Globals.currentUser)),Nil,Nil,200))
  })

  def proxyDataUri(slideJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxyDataUri(%s)".format(identity), () =>
    Full(MeTLXConfiguration.getRoom(slideJid,config.name,RoomMetaDataUtils.fromJid(slideJid)).getHistory.getImageByIdentity(identity).map(image => {
      image.imageBytes.map(bytes => {
        println("found bytes: %s (%s)".format(bytes,bytes.length))
        val dataUri = "data:image/png;base64,"+net.liftweb.util.SecurityHelpers.base64Encode(bytes)
        InMemoryResponse(IOUtils.toByteArray(dataUri),Boot.cacheStrongly,Nil,200)
      }).openOr(NotFoundResponse("image bytes not available"))
    }).getOrElse(NotFoundResponse("image not available"))))

  def proxyImageUrl(slideJid:String,url:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxyImageUrl(%s)".format(url),() => {
    val room = MeTLXConfiguration.getRoom(slideJid,config.name,RoomMetaDataUtils.fromJid(slideJid))
    val history = room.getHistory
    val images = history.getImages
    println("room: %s\r\nhistory: %s\r\nimages: %s\r\nurl: %s".format(room,history,images,url))
    Full(images.find(_.source.exists(_ == url)).map(image => {
      println("room: %s\r\nhistory: %s\r\nimages: %s\r\nimageOption: %s".format(room,history,images,image))
      image.imageBytes.map(bytes => {
        println("found bytes: %s (%s)".format(bytes,bytes.length))
        val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
        InMemoryResponse(bytes,headers,Nil,200)
      }).openOr(NotFoundResponse("image bytes not available"))
    }).getOrElse({
      NotFoundResponse("image not available")
    }))
  })
  def proxy(slideJid:String,identity:String)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.proxy(%s)".format(identity), () => {
    Full({
      val room = MeTLXConfiguration.getRoom(slideJid,config.name,RoomMetaDataUtils.fromJid(slideJid))
      val history = room.getHistory
      val imageOption = history.getImageByIdentity(identity)
      println("room: %s\r\nhistory: %s\r\nimages: %s\r\nimageOption: %s".format(room,history,history.getImages,imageOption))
      imageOption.map(image => {
        image.imageBytes.map(bytes => {
          println("found bytes: %s (%s)".format(bytes,bytes.length))
          val headers = ("mime-type","application/octet-stream") :: Boot.cacheStrongly
          InMemoryResponse(bytes,headers,Nil,200)
        }).openOr(NotFoundResponse("image bytes not available"))
      }).getOrElse(NotFoundResponse("image not available"))
    })
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
  def nouns(jid:String):Node = Stopwatch.time("StatelessHtml.loadAllThemes(%s)".format(jid), () => {
    val history = MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory.getRenderable
    val phrases = List(
      history.collect{case t:MeTLText => t.text}.map(txt => tryo((xml.XML.loadString(txt) \\ "Run").text).openOr("")),
      CanvasContentAnalysis.extract(history.collect{case i:MeTLInk => i})).flatten
    <userThemes><userTheme><user>everyone</user>{ CanvasContentAnalysis.thematize(phrases).map(t => <theme>{t}</theme>) }</userTheme></userThemes>
  })
  def words(jid:String):Node = Stopwatch.time("StatelessHtml.loadThemes(%s)".format(jid), () => {
    val history = MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory
    val themes = List(
      history.getTexts.map(t => Theme(t.author,t.text)).toList,
      history.getInks.groupBy(_.author).flatMap{
        case (author,inks) => CanvasContentAnalysis.extract(inks).map(i => Theme(author, i))
      }).flatten
    println(themes)
    val themesByUser = themes.groupBy(_.author).map(kv =>
      <userTheme>
        <user>{kv._1}</user>
        <themes>{kv._2.map(t => <theme>{t.text}</theme>)}</themes>
        </userTheme>)
    <userThemes>{themesByUser}</userThemes>
  })
  def loadMergedHistory(jid:String,username:String):Node = Stopwatch.time("StatelessHtml.loadMergedHistory(%s)".format(jid),() => {
    <history>{serializer.fromRenderableHistory(MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory.merge(MeTLXConfiguration.getRoom(jid+username,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory))}</history>
  })
  def themes(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.themes(%s)".format(req.param("source")), () => {
    req.param("source").map(jid=> XmlResponse(nouns(jid)))
  })
  def history(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.history(%s)".format(req.param("source")), () => {
    req.param("source").map(jid=> XmlResponse(loadHistory(jid)))
  })
  def fullHistory(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.fullHistory(%s)".format(req.param("source")), () => {
    req.param("source").map(jid => XmlResponse(<history>{MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory.getAll.map(s => serializer.fromMeTLData(s))}</history>))
  })

  def fullClientHistory(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.fullHistory(%s)".format(req.param("source")), () => {
    req.param("source").map(jid => XmlResponse(<history>{MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid)).getHistory.getAll.map(s => metlClientSerializer.fromMeTLData(s))}</history>))
  })
  def mergedHistory(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.history(%s)".format(req.param("source")), () => {
    req.param("source").map(jid=> {
      req.param("username").map(user => {
        XmlResponse(loadMergedHistory(jid,user))
      }).getOrElse(NotFoundResponse("username not provided"))
    })
  })
  def describeHistory(req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.describeHistory(%s)".format(req.param("source")),() => {
    req.param("source").map(jid=>{
      val room = MeTLXConfiguration.getRoom(jid,config.name,RoomMetaDataUtils.fromJid(jid))
      val history = room.getHistory
      val stanzas = history.getAll
      val allContent = stanzas.length
      val publishers = stanzas.groupBy(_.author)
      val canvasContent = history.getCanvasContents.length
      val strokes = history.getInks.length
      val texts = history.getTexts.length
      val images = history.getImages.length
      val attendances = history.getAttendances.length
      val files = history.getAttendances.length
      val quizzes = history.getQuizzes.length
      val highlighters = history.getHighlighters.length
      val occupants = history.getAttendances
      val occupantCount = occupants.length
      val uniqueOccupants = occupants.groupBy(occ => occ.author)
      val xResponse = <historyDescription>
      <bounds>
      <left>{Text(history.getLeft.toString)}</left>
      <right>{Text(history.getRight.toString)}</right>
      <top>{Text(history.getTop.toString)}</top>
      <bottom>{Text(history.getBottom.toString)}</bottom>
      </bounds>
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
      req.param("format").openOr("xml") match {
        case "json" => JsonResponse(json.Xml.toJson(xResponse))
        case "xml" => XmlResponse(xResponse)
      }
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
  def updateConversation(onBehalfOfUser:String,conversationJid:String,req:Req)():Box[LiftResponse] = Stopwatch.time("StatelessHtml.updateConversation(%s)".format(conversationJid),() => {
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
  def createConversation(onBehalfOfUser:String,title:String):Box[LiftResponse] = {
    serializer.fromConversation(config.createConversation(title,onBehalfOfUser)).headOption.map(n => XmlResponse(n))
  }
  def addSlideAtIndex(onBehalfOfUser:String,jid:String,afterSlideId:String):Box[LiftResponse] = {
    val conv = config.detailsOfConversation(jid)
    if (onBehalfOfUser == conv.author){
      val newConv = conv.slides.find(_.id.toString == afterSlideId).map(slide => {
        config.addSlideAtIndexOfConversation(jid,slide.index + 1)
      }).getOrElse(conv)
      serializer.fromConversation(newConv).headOption.map(n => XmlResponse(n))
    } else {
      Full(ForbiddenResponse("only the author may duplicate slides in a conversation"))
    }
  }
  def duplicateSlide(onBehalfOfUser:String,slide:String,conversation:String):Box[LiftResponse] = {
    val conv = config.detailsOfConversation(conversation)
    if (onBehalfOfUser == conv.author){
      val newConv = conv.slides.find(_.id.toString == slide).map(slide => {
        val step1 = config.addSlideAtIndexOfConversation(conversation,slide.index + 1)
        step1.slides.find(_.index == slide.index + 1).foreach(newSlide => {
          val oldId = slide.id
          val newId = newSlide.id
          ServerSideBackgroundWorker ! CopyLocation(
            config,
            SlideRoom(conv.server.name,conversation,oldId),
            SlideRoom(conv.server.name,conversation,newId),
            (s:MeTLStanza) => s.author == conv.author
          )
          ServerSideBackgroundWorker ! CopyLocation(
            config,
            PrivateSlideRoom(conv.server.name,conversation,oldId,conv.author),
            PrivateSlideRoom(conv.server.name,conversation,newId,conv.author),
            (s:MeTLStanza) => s.author == conv.author
          )
        })
        step1
      }).getOrElse(conv)
      serializer.fromConversation(newConv).headOption.map(n => XmlResponse(n))
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
      val remoteConv = config.updateConversation(newConv.jid.toString,newConvWithOldSlides)
      remoteConv.slides.foreach(ns => {
        val newSlide = ns.id
        val slide = newSlide - newConv.jid + oldConv.jid
        val conv = remoteConv
        ServerSideBackgroundWorker ! CopyLocation(
          config,
          SlideRoom(conv.server.name,conversation,slide),
          SlideRoom(conv.server.name,remoteConv.jid.toString,newSlide),
          (s:MeTLStanza) => s.author == conv.author
        )
        ServerSideBackgroundWorker ! CopyLocation(
          config,
          PrivateSlideRoom(conv.server.name,conversation,slide,conv.author),
          PrivateSlideRoom(conv.server.name,remoteConv.jid.toString,newSlide,conv.author),
          (s:MeTLStanza) => s.author == conv.author
        )
      })
      ServerSideBackgroundWorker ! CopyLocation(
        config,
        ConversationRoom(remoteConv.server.name,remoteConv.jid.toString),
        ConversationRoom(newConv.server.name,newConv.jid.toString),
        (s:MeTLStanza) => s.author == remoteConv.author && s.isInstanceOf[MeTLQuiz] // || s.isInstanceOf[Attachment]
      )
      val remoteConv2 = config.updateConversation(remoteConv.jid.toString,remoteConv)
      serializer.fromConversation(remoteConv2).headOption.map(n => XmlResponse(n))
    } else {
      Full(ForbiddenResponse("only the author may duplicate a conversation"))
    }
  }
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
  def exportConversation(onBehalfOfUser:String,conversation:String):Box[LiftResponse] = {
    for (
      conv <- Some(config.detailsOfConversation(conversation));
      if (onBehalfOfUser == conv.author);
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
  }
  protected def exportHistories(conversation:Conversation,restrictToPrivateUsers:Option[List[String]]):List[History] = {
    val convHistory = config.getHistory(conversation.jid.toString).filter(m => {
      restrictToPrivateUsers.map(users => {
        m match {
          case q:MeTLQuiz => true
          case mcc:MeTLCanvasContent => mcc.privacy == Privacy.PUBLIC || users.contains(mcc.author)
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
  def importConversation(req:Req):Box[LiftResponse] =  Stopwatch.time("MeTLStatelessHtml.importConversation",() => {
    (for (
      xml <- req.body.map(bytes => XML.loadString(new String(bytes,"UTF-8")));
      historyMap <- (xml \ "histories").headOption.map(hNodes => Map((hNodes \ "history").map(h => {
        val hist = exportSerializer.toHistory(h)
        (hist.jid,hist)
      }):_*));
      conversation <- (xml \ "conversation").headOption.map(c => exportSerializer.toConversation(c));
      remoteConv = StatelessHtml.importConversation(Globals.currentUser.is,conversation,historyMap);
      node <- serializer.fromConversation(remoteConv).headOption
    ) yield {
      XmlResponse(node)
    })
  })
  def importConversationAsMe(req:Req):Box[LiftResponse] =  Stopwatch.time("MeTLStatelessHtml.importConversation",() => {
    (for (
      xml <- req.body.map(bytes => XML.loadString(new String(bytes,"UTF-8")));
      historyMap <- (xml \ "histories").headOption.map(hNodes => Map((hNodes \ "history").map(h => {
        val hist = exportSerializer.toHistory(h)
        (hist.jid,hist)
      }):_*));
      conversation <- (xml \ "conversation").headOption.map(c => exportSerializer.toConversation(c));
      remoteConv = StatelessHtml.importConversation(Globals.currentUser.is,conversation,historyMap);
      node <- serializer.fromConversation(remoteConv).headOption
    ) yield {
      XmlResponse(node)
    })
  })
  protected def importConversation(onBehalfOfUser:String,oldConv:Conversation,histories:Map[String,History]):Conversation = {
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
    histories.foreach(h => {
      val oldJid = h._1
      val offset = remoteConv.jid - oldConv.jid
      val serverName = remoteConv.server.name
      val newRoom = RoomMetaDataUtils.fromJid(oldJid) match {
        case PrivateSlideRoom(_sn,_oldConvJid,oldSlideJid,oldAuthor) => Some(PrivateSlideRoom(serverName,remoteConv.jid.toString,oldSlideJid + offset,oldAuthor))
        case SlideRoom(_sn,_oldConvJid,oldSlideJid) => Some(SlideRoom(serverName,remoteConv.jid.toString,oldSlideJid + offset))
        case ConversationRoom(_sn,_oldConvJid) => Some(ConversationRoom(serverName,remoteConv.jid.toString))
        case _ => None
      }
      newRoom.foreach(nr => {
        ServerSideBackgroundWorker ! CopyContent(
          remoteConv.server,
          h._2,
          nr
        )
      })
    })
    remoteConv
  }
  def powerpointImport(r:Req):Box[LiftResponse] = {
    (for (
      title <- r.param("title");
      magnification = r.param("magnification").map(_.toInt);
      bytes <- r.body;
      author = Globals.currentUser.is;
      conv = config.createConversation(title,author);
      histories <- foreignConversationParse(conv.jid,bytes,config,author,false,magnification);
      remoteConv <- foreignConversationImport(config,author,conv,histories);
      node <- serializer.fromConversation(remoteConv).headOption
    ) yield {
      XmlResponse(node)
    })
  }
  def powerpointImportFlexible(r:Req):Box[LiftResponse] = {
    (for (
      title <- r.param("title");
      bytes <- r.body;
      author = Globals.currentUser.is;
      conv = config.createConversation(title,author);
      histories <- foreignConversationParse(conv.jid,bytes,config,author,true,None);
      remoteConv <- foreignConversationImport(config,author,conv,histories);
      node <- serializer.fromConversation(remoteConv).headOption
    ) yield {
      XmlResponse(node)
    })
  }

  def foreignConversationImport(r:Req):Box[LiftResponse] = {
    (for (
      title <- r.param("title");
      bytes <- r.body;
      author = Globals.currentUser.is;
      conv = config.createConversation(title,author);
      histories <- foreignConversationParse(conv.jid,bytes,config,author,false,None);
      remoteConv <- foreignConversationImport(config,author,conv,histories);
      node <- serializer.fromConversation(remoteConv).headOption
    ) yield {
      XmlResponse(node)
    })
  }
  protected def foreignConversationParse(jid:Int,in:Array[Byte],server:ServerConfiguration,onBehalfOfUser:String,flexible:Boolean = false,magnification:Option[Int] = Some(3)):Box[Map[Int,History]] = {
    try {
      Full(flexible match {
        case true => new PowerpointParser().importAsShapes(jid,in,server,onBehalfOfUser)
        case false => new PowerpointParser().importAsImages(jid,in,server,onBehalfOfUser,magnification.getOrElse(3))
      })
    } catch {
      case e:Exception => {
        println("exception in foreignConversationImport: %s\r\n%s".format(e.getMessage,e.getStackTraceString))
        Empty
      }
    }
  }
  protected def foreignConversationImport(server:ServerConfiguration,onBehalfOfUser:String,conversation:Conversation,histories:Map[Int,History]):Box[Conversation] = {

    try {
      val newConvWithAllSlides = conversation.copy(
        lastAccessed = new java.util.Date().getTime,
        slides = histories.map(h => Slide(server,onBehalfOfUser,h._1 + 1, h._1 - conversation.jid)).toList
      )
      server.updateConversation(conversation.jid.toString,newConvWithAllSlides)

      histories.toList.foreach(tup => {
        val newLoc = RoomMetaDataUtils.fromJid((tup._1 + 1).toString)
        ServerSideBackgroundWorker ! CopyContent(server,tup._2,newLoc)
      })
      Full(newConvWithAllSlides)
    } catch {
      case e:Exception => {
        println("exception in foreignConversationImport: %s\r\n%s".format(e.getMessage,e.getStackTraceString))
        Empty
      }
    }
  }
}
case class CopyContent(server:ServerConfiguration,from:History,to:RoomMetaData)
case class CopyLocation(server:ServerConfiguration,from:RoomMetaData,to:RoomMetaData,contentFilter:MeTLStanza=>Boolean)

object ServerSideBackgroundWorker extends net.liftweb.actor.LiftActor {
  val thisDuplicatorId = nextFuncName
  override def messageHandler = {
    case RoomJoinAcknowledged(server,room) => {}
    case RoomLeaveAcknowledged(server,room) => {}
    case CopyContent(config,oldContent,newLoc) => {
      val room = MeTLXConfiguration.getRoom(newLoc.getJid,config.name,newLoc)
      room ! JoinRoom("serverSideBackgroundWorker",thisDuplicatorId,this)
      oldContent.getAll.foreach(stanza => {
        room ! LocalToServerMeTLStanza(stanza match {
          case m:MeTLInk => m.copy(slide = newLoc.getJid)
          case m:MeTLImage => m.copy(slide = newLoc.getJid)
          case m:MeTLText => m.copy(slide = newLoc.getJid)
          case m:MeTLMoveDelta => m.copy(slide = newLoc.getJid)
          case m:MeTLDirtyInk => m.copy(slide = newLoc.getJid)
          case m:MeTLDirtyText => m.copy(slide = newLoc.getJid)
          case m:MeTLSubmission => tryo(newLoc.getJid.toInt).map(ns => m.copy(slideJid = ns)).getOrElse(m)
          case m:MeTLUnhandledCanvasContent[_] => m.copy(slide = newLoc.getJid)
          case m:MeTLQuiz => m
          case s:MeTLStanza => s
        })
      })
      room ! LeaveRoom("serverSideBackgroundWorker",thisDuplicatorId,this)
    }
    case CopyLocation(config,oldLoc,newLoc,contentFilter) => {
      val oldContent = config.getHistory(oldLoc.getJid).filter(contentFilter)
      this ! CopyContent(config,oldContent,newLoc)
    }
  }
}

class ExportXmlSerializer(configName:String) extends GenericXmlSerializer(configName){
  override def toMeTLImage(input:NodeSeq):MeTLImage = {
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val tag = getStringByName(input,"tag")
    val imageBytes = base64Decode(getStringByName(input,"imageBytes"))
    val newUrl = config.postResource(c.slide.toString,nextFuncName,imageBytes)
    val source = Full(newUrl)
    val pngBytes = Empty
    val width = getDoubleByName(input,"width")
    val height = getDoubleByName(input,"height")
    val x = getDoubleByName(input,"x")
    val y = getDoubleByName(input,"y")
    MeTLImage(config,m.author,m.timestamp,tag,source,Full(imageBytes),pngBytes,width,height,x,y,c.target,c.privacy,c.slide,c.identity,m.audiences)
  }
  override def fromMeTLImage(input:MeTLImage):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLImage",() => {
    canvasContentToXml("image",input,List(
      <tag>{input.tag}</tag>,
      <imageBytes>{base64Encode(input.imageBytes.getOrElse(Array.empty[Byte]))}</imageBytes>,
      <width>{input.width}</width>,
      <height>{input.height}</height>,
      <x>{input.x}</x>,
      <y>{input.y}</y>
    ))
  })
  override def toMeTLQuiz(input:NodeSeq):MeTLQuiz = Stopwatch.time("GenericXmlSerializer.toMeTLQuiz", () => {
    val m = parseMeTLContent(input,config)
    val created = getLongByName(input,"created")
    val question = getStringByName(input,"question") match {
      case q if (q.length > 0) => q
      case _ => getStringByName(input,"title")
    }
    val id = getStringByName(input,"id")
    val quizImage = Full(base64Decode(getStringByName(input,"imageBytes")))
    val newUrl = quizImage.map(qi => config.postResource("quizImages",nextFuncName,qi))
    val isDeleted = getBooleanByName(input,"isDeleted")
    val options = getXmlByName(input,"quizOption").map(qo => toQuizOption(qo)).toList
    MeTLQuiz(config,m.author,m.timestamp,created,question,id,newUrl,quizImage,isDeleted,options,m.audiences)
  })
  override def fromMeTLQuiz(input:MeTLQuiz):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLQuiz", () => {
    metlContentToXml("quiz",input,List(
      <created>{input.created}</created>,
      <question>{input.question}</question>,
      <id>{input.id}</id>,
      <isDeleted>{input.isDeleted}</isDeleted>,
      <options>{input.options.map(o => fromQuizOption(o))}</options>
    ) ::: input.imageBytes.map(ib => List(<imageBytes>{base64Encode(ib)}</imageBytes>)).openOr(List.empty[Node]))
  })
  override def toSubmission(input:NodeSeq):MeTLSubmission = Stopwatch.time("GenericXmlSerializer.toSubmission", () => {
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val title = getStringByName(input,"title")
    val imageBytes = Full(base64Decode(getStringByName(input,"imageBytes")))
    val url = imageBytes.map(ib => config.postResource(c.slide.toString,nextFuncName,ib)).getOrElse("unknown")
    val blacklist = getXmlByName(input,"blacklist").map(bl => {
      val username = getStringByName(bl,"username")
      val highlight = getColorByName(bl,"highlight")
      SubmissionBlacklistedPerson(username,highlight)
    }).toList
    MeTLSubmission(config,m.author,m.timestamp,title,c.slide.toInt,url,imageBytes,blacklist,c.target,c.privacy,c.identity,m.audiences)
  })
  override def fromSubmission(input:MeTLSubmission):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromSubmission", () => {
    canvasContentToXml("screenshotSubmission",input,List(
      <imageBytes>{base64Encode(input.imageBytes.getOrElse(Array.empty[Byte]))}</imageBytes>,
      <title>{input.title}</title>,
      <time>{input.timestamp.toString}</time>
    ) ::: input.blacklist.map(bl => <blacklist><username>{bl.username}</username><highlight>{ColorConverter.toRGBAString(bl.highlight)}</highlight></blacklist> ).toList)
  })
  override def toMeTLFile(input:NodeSeq):MeTLFile = Stopwatch.time("GenericXmlSerializer.toMeTLFile",() => {
    val m = parseMeTLContent(input,config)
    val name = getStringByName(input,"name")
    val id = getStringByName(input,"id")
    val bytes = Full(base64Decode(getStringByName(input,"bytes")))
    val url = bytes.map(ib => config.postResource("files",nextFuncName,ib))
    MeTLFile(config,m.author,m.timestamp,name,id,url,bytes)
  })
  override def fromMeTLFile(input:MeTLFile):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLFile",() => {
    metlContentToXml("file",input,List(
      <name>{input.name}</name>,
      <id>{input.id}</id>
    ) :::
      input.bytes.map(ib => List(<bytes>{base64Encode(ib)}</bytes>)).getOrElse(List.empty[Node]))
  })
}
