package com.metl.h2

import com.metl.data._
import com.metl.model._
import com.metl.utils._
import com.metl.h2.dbformats._
import net.liftweb.common.{Logger => LiftLogger, _}
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import Privacy._
import com.metl.external.ForeignRelationship

class H2Serializer(config:ServerConfiguration) extends Serializer with LiftLogger {
  implicit val formats = net.liftweb.json.DefaultFormats
  override type T = Object
  val configName = config.name
  val xmlSerializer = new GenericXmlSerializer(config){
    import scala.xml._
    protected def getBooleanByNames(input:NodeSeq,tags:List[String]):Option[Boolean] = {
      tags.foldLeft(None:Option[Boolean])((acc,item) => {
        (input \\ item).headOption.map(ho => ho.text.toLowerCase.trim) match {
          case Some("true") if acc == None => Some(true)
          case Some("false") if acc == None => Some(false)
          case _ => acc
        }
      })
    }
    override def toPermissions(input:NodeSeq):Permissions = Stopwatch.time("H2XmlSerializer.toPermissions",{
      try {
        val studentsCanOpenFriends = getBooleanByNames(input,List("friends","studentCanOpenFriends")).getOrElse(false)
        val studentsCanPublish = getBooleanByNames(input,List("publish","studentCanPublish")).getOrElse(true)
        val usersAreCompulsorilySynced = getBooleanByNames(input,List("follow","usersAreCompulsorilySynced")).getOrElse(false)
        val studentsMayBroadcast = getBooleanByNames(input,List("video","studentsMayBroadcast")).getOrElse(false)
        val studentsMayChatPublicly = getBooleanByNames(input,List("chat","studentsMayChatPublicly")).getOrElse(true)
        Permissions(config,studentsCanOpenFriends,studentsCanPublish,usersAreCompulsorilySynced,studentsMayBroadcast,studentsMayChatPublicly)
      } catch {
        case e:Exception => {
          Permissions.default(config)
        }
      }
    })
    override def fromPermissions(input:Permissions):Node = Stopwatch.time("H2XmlSerializer.fromPermissions",{
      <p><friends>{input.studentsCanOpenFriends}</friends><publish>{input.studentsCanPublish}</publish><follow>{input.usersAreCompulsorilySynced}</follow><video>{input.studentsMayBroadcast}</video><chat>{input.studentsMayChatPublicly}</chat></p>
    })
  }

  case class ParsedCanvasContent(target:String,identity:String,slide:String,privacy:Privacy,author:String,timestamp:Long,audiences:List[Audience])
  case class ParsedMeTLContent(author:String,timestamp:Long,audiences:List[Audience])

  def toPrivacy(i:String):Privacy = i.toLowerCase.trim match {
    case "public" => Privacy.PUBLIC
    case "private" => Privacy.PRIVATE
    case _ => Privacy.NOT_SET
  }
  def fromPrivacy(i:Privacy):String = i.toString.toLowerCase.trim

  protected def parseAudiences(in:String):List[Audience] = {
    tryo((scala.xml.XML.loadString(in) \ "audience").flatMap(a => {
      for (
        domain <- (a \ "@domain").headOption;
        name <- (a \ "@name").headOption;
        audienceType <- (a \ "@type").headOption;
        action <- (a \ "@action").headOption
      ) yield {
        Audience(config,domain.text,name.text,audienceType.text,action.text)
      }
    }).toList).openOr(Nil)
  }
  protected def incAudiences(in:List[Audience]) = {
    trace("incAudiences",in)
    <audiences>{
      in.map(a => {
        <audience domain={a.domain} name={a.name} type={a.audienceType} action={a.action}/>
      })
    }</audiences>
  }
  protected def decStanza[A <:H2MeTLStanza[A]](rec:A):ParsedMeTLContent = {
    val res = ParsedMeTLContent(rec.author.get,rec.timestamp.get,parseAudiences(rec.audiences.get))
    trace("decStanza",res)
    res
  }
  protected def decCanvasContent[A <: H2MeTLCanvasContent[A]](rec:A):ParsedCanvasContent = {
    val mc = decStanza(rec)
    ParsedCanvasContent(rec.target.get,rec.identity.get,rec.slide.get,toPrivacy(rec.privacy.get),mc.author,mc.timestamp,mc.audiences)
  }
  protected def incMeTLContent[A <: H2MeTLContent[A]](rec:A,s:MeTLData,metlType:String):A = rec.metlType(metlType)
  protected def incStanza[A <: H2MeTLStanza[A]](rec:A,s:MeTLStanza,metlType:String):A = incMeTLContent(rec,s,metlType).timestamp(s.timestamp).author(s.author).audiences(incAudiences(s.audiences).toString)
  protected def incCanvasContent[A <: H2MeTLCanvasContent[A]](rec:A,cc:MeTLCanvasContent,metlType:String):A = incStanza(rec,cc,metlType).target(cc.target).privacy(fromPrivacy(cc.privacy)).slide(cc.slide).identity(cc.identity)
  protected def incUnhandled[A <: H2MeTLUnhandled[A], B <: {val unhandled:String; val valueType:String}](rec:A,cc:B):A = {
    rec.unhandled(cc.unhandled).valueType(cc.valueType)
  }
  override def toMeTLData(inputObject:T):MeTLData = internalToMeTLStanza(inputObject)
  def internalToMeTLStanza[A <: H2MeTLStanza[A]](inputObject:T):MeTLStanza = Stopwatch.time("H2Serializer.toMeTLStanza",{
    inputObject match {
      case i:A => {
        i.metlType.get match {
          case "ink" => toMeTLInk(i.asInstanceOf[H2Ink])
          case "theme" => toTheme(i.asInstanceOf[H2Theme])
          case "chatMessage" => toChatMessage(i.asInstanceOf[H2ChatMessage])
          case "text" => toMeTLText(i.asInstanceOf[H2Text])
          case "multiWordText" => toMeTLMultiWordText(i.asInstanceOf[H2MultiWordText])
          case "image" => toMeTLImage(i.asInstanceOf[H2Image])
          case "video" => toMeTLVideo(i.asInstanceOf[H2Video])
          case "dirtyInk" => toMeTLDirtyInk(i.asInstanceOf[H2DirtyInk])
          case "dirtyText" => toMeTLDirtyText(i.asInstanceOf[H2DirtyText])
          case "dirtyImage" => toMeTLDirtyImage(i.asInstanceOf[H2DirtyImage])
          case "dirtyVideo" => toMeTLDirtyVideo(i.asInstanceOf[H2DirtyVideo])
          case "moveDelta" => toMeTLMoveDelta(i.asInstanceOf[H2MoveDelta])
          case "submission" => toSubmission(i.asInstanceOf[H2Submission])
          case "command" => toMeTLCommand(i.asInstanceOf[H2Command])
          case "quiz" => toMeTLQuiz(i.asInstanceOf[H2Quiz])
          case "attendance" => toMeTLAttendance(i.asInstanceOf[H2Attendance])
          case "file" => toMeTLFile(i.asInstanceOf[H2File])
          case "videoStream" => toMeTLVideoStream(i.asInstanceOf[H2VideoStream])
          case "quizResponse" => toMeTLQuizResponse(i.asInstanceOf[H2QuizResponse])
          case "grade" => toGrade(i.asInstanceOf[H2Grade])
          case "numericGradeValue" => toNumericGradeValue(i.asInstanceOf[H2NumericGradeValue])
          case "booleanGradeValue" => toBooleanGradeValue(i.asInstanceOf[H2BooleanGradeValue])
          case "textGradeValue" => toTextGradeValue(i.asInstanceOf[H2TextGradeValue])
          case "undeletedCanvasContent" => toMeTLUndeletedCanvasContent(i.asInstanceOf[H2UndeletedCanvasContent])
          case "unhandledCanvasContent" => toMeTLUnhandledCanvasContent(i.asInstanceOf[H2UnhandledCanvasContent])
          case "unhandledStanza" => toMeTLUnhandledStanza(i.asInstanceOf[H2UnhandledStanza])
          //case "unhandledData" => toMeTLUnhandledData(i.asInstanceOf[H2UnhandledContent]) //this is below the interest of the serializer
          case _ => throw new SerializationNotImplementedException
        }
      }
      case other => {
        warn("H2Serializer didn't know how to serialize: %s".format(other))
        throw new SerializationNotImplementedException
      }
    }
  })

  def toMeTLUnhandledData(i:H2UnhandledContent):MeTLUnhandledData = {
    MeTLUnhandledData(config, i.unhandled, i.valueType)
  }
  override def fromMeTLUnhandledData(i:MeTLUnhandledData):H2UnhandledContent = {
    incUnhandled(incMeTLContent(H2UnhandledContent.create,i,"unhandledData"),i)
    //.unhandled(i.unhandled).valueType(i.valueType)
  }

  def toMeTLUnhandledStanza(i:H2UnhandledStanza):MeTLUnhandledStanza = {
    val c = decStanza(i)
    MeTLUnhandledStanza(config,c.author,c.timestamp, i.unhandled, i.valueType, c.audiences)
  }
  override def fromMeTLUnhandledStanza(i:MeTLUnhandledStanza):H2UnhandledStanza = {
    incUnhandled(incStanza(H2UnhandledStanza.create,i,"unhandledStanza"),i)//.unhandled(i.unhandled).valueType(i.valueType)
  }

  def toMeTLUnhandledCanvasContent(i:H2UnhandledCanvasContent):MeTLUnhandledCanvasContent = {
    val cc = decCanvasContent(i)
    MeTLUnhandledCanvasContent(config,cc.author,cc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences,1.0,1.0, i.unhandled, i.valueType)
  }
  override def fromMeTLUnhandledCanvasContent(i:MeTLUnhandledCanvasContent):H2UnhandledCanvasContent = {
    incUnhandled(incCanvasContent(H2UnhandledCanvasContent.create,i,"unhandledCanvasContent"),i)//.unhandled(i.unhandled).valueType(i.valueType)
  }

  def toMeTLAttendance(i:H2Attendance):Attendance = {
    val c = decStanza(i)
    Attendance(config,c.author,c.timestamp,i.location.get,i.present.get,c.audiences)
  }
  override def fromMeTLAttendance(i:Attendance):H2Attendance = {
    incStanza(H2Attendance.create,i,"attendance").location(i.location).present(i.present)
  }
  def toMeTLUndeletedCanvasContent(input:H2UndeletedCanvasContent):MeTLUndeletedCanvasContent = {
    val cc = decCanvasContent(input)
    MeTLUndeletedCanvasContent(config,cc.author,cc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,input.elementType.get,input.oldIdentity.get,input.newIdentity.get,cc.audiences)
  }
  override def fromMeTLUndeletedCanvasContent(input:MeTLUndeletedCanvasContent):H2UndeletedCanvasContent = {
    incCanvasContent(H2UndeletedCanvasContent.create,input,"undeletedCanvasContent").oldIdentity(input.oldElementIdentity).newIdentity(input.newElementIdentity).elementType(input.elementType)
  }

  def toMeTLInk(i:H2Ink):MeTLInk = {
    val cc = decCanvasContent(i)
    MeTLInk(config,cc.author,cc.timestamp,i.checksum.get,i.startingSum.get,toPointList(i.points.get),toColor(i.color.get),i.thickness.get,i.isHighlighter.get,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  override def fromMeTLInk(i:MeTLInk):H2Ink = incCanvasContent(H2Ink.create,i,"ink").checksum(i.checksum).startingSum(i.startingSum).points(fromPointList(i.points).toString).color(fromColor(i.color).toString).thickness(i.thickness).isHighlighter(i.isHighlighter)
  def toMeTLVideo(i:H2Video):MeTLVideo = {
    val cc = decCanvasContent(i)
    val url = i.source.get match {
      case other:String if other.length > 0 => Full(other)
      case _ => Empty
    }
    val videoBytes = url.map(u => config.getResource(u))
    MeTLVideo(config,cc.author,cc.timestamp,url,videoBytes,i.width.get,i.height.get,i.x.get,i.y.get,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  def toMeTLVideo(i:H2Video,videoData:Array[Byte]):MeTLVideo = {
    val cc = decCanvasContent(i)
    val url = i.source.get match {
      case other:String if other.length > 0 => Full(other)
      case _ => Empty
    }
    val videoBytes = Full(videoData)//url.map(u => config.getResource(u))
    MeTLVideo(config,cc.author,cc.timestamp,url,videoBytes,i.width.get,i.height.get,i.x.get,i.y.get,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  override def fromMeTLVideo(i:MeTLVideo):H2Video = incCanvasContent(H2Video.create,i,"video").source(i.source.openOr("")).width(i.width).height(i.height).x(i.x).y(i.y)

  def toTheme(h:H2Theme):MeTLTheme = {
    val c = decStanza(h)
    MeTLTheme(config,c.author,c.timestamp,h.location.get,Theme(c.author,h.text.get,h.origin.get),c.audiences)
  }
  override def fromTheme(i:MeTLTheme):H2Theme = incStanza(H2Theme.create,i,"theme").location(i.location).text(i.theme.text).origin(i.theme.origin).author(i.theme.author).room(i.location)

  def toChatMessage(h:H2ChatMessage):MeTLChatMessage = {
    val c = decStanza(h)
    MeTLChatMessage(config,c.author,c.timestamp,h.identity.get,h.contentType.get,h.content.get,h.context.get,c.audiences)
  }
  override def fromChatMessage(i:MeTLChatMessage):H2ChatMessage = incStanza(H2ChatMessage.create,i,"chatMessage").identity(i.identity).contentType(i.contentType).content(i.content).context(i.context)

  def toMeTLImage(i:H2Image):MeTLImage = {
    val cc = decCanvasContent(i)
    val url = i.source.get match {
      case other:String if other.length > 0 => Full(other)
      case _ => Empty
    }
    val imageBytes = url.map(u => config.getResource(u))
    MeTLImage(config,cc.author,cc.timestamp,i.tag.get,url,imageBytes,Empty,i.width.get,i.height.get,i.x.get,i.y.get,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  def toMeTLImage(i:H2Image,imageData:Array[Byte]):MeTLImage = {
    val cc = decCanvasContent(i)
    val url = i.source.get match {
      case other:String if other.length > 0 => Full(other)
      case _ => Empty
    }
    val imageBytes = Full(imageData)//url.map(u => config.getResource(u))
    MeTLImage(config,cc.author,cc.timestamp,i.tag.get,url,imageBytes,Empty,i.width.get,i.height.get,i.x.get,i.y.get,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  override def fromMeTLImage(i:MeTLImage):H2Image = incCanvasContent(H2Image.create,i,"image").tag(i.tag).source(i.source.openOr("")).width(i.width).height(i.height).x(i.x).y(i.y)
  def decodeMultiWords(wordString:String) = net.liftweb.json.parse(wordString).extract[List[MeTLTextWord]]
  def decodeAudiences(audienceString:String) = {
    trace(audienceString)
    net.liftweb.json.parse(audienceString).extract[List[Audience]]
  }
  def toMeTLMultiWordText(i:H2MultiWordText):MeTLMultiWordText = {
    val cc = decCanvasContent(i)
    MeTLMultiWordText(config,cc.author,cc.timestamp,i.height,i.width,i.requestedWidth.get,i.x.get,i.y.get,i.tag.get,cc.identity,cc.target,cc.privacy,cc.slide,decodeMultiWords(i.words.get),cc.audiences)
  }
  def encodeMultiWords(words:Seq[MeTLTextWord]):String = net.liftweb.json.Serialization.write(words)
  
  override def fromMeTLMultiWordText(t:MeTLMultiWordText):H2MultiWordText = incCanvasContent(H2MultiWordText.create,t,"multiWordText").x(t.x).y(t.y).width(t.width).height(t.height).requestedWidth(t.requestedWidth).tag(t.tag).words(encodeMultiWords(t.words))
  def toMeTLText(i:H2Text):MeTLText = {
    val cc = decCanvasContent(i)
    MeTLText(config,cc.author,cc.timestamp,i.text.get,i.height.get,i.width.get,i.caret.get,i.x.get,i.y.get,i.tag.get,i.style.get,i.family.get,i.weight.get,i.size.get,i.decoration.get,cc.identity,cc.target,cc.privacy,cc.slide,toColor(i.color.get),cc.audiences)
  }
  override def fromMeTLText(i:MeTLText):H2Text = incCanvasContent(H2Text.create,i,"text").text(i.text).height(i.height).width(i.width).caret(i.caret).x(i.x).y(i.y).tag(i.tag).style(i.style).family(i.family).weight(i.weight).size(i.size).decoration(i.decoration).color(fromColor(i.color).toString)
  def toMeTLMoveDelta(i:H2MoveDelta):MeTLMoveDelta = {
    val cc = decCanvasContent(i)
    MeTLMoveDelta(config,cc.author,cc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,i.xOrigin,i.yOrigin,stringToStrings(i.inkIds.get),stringToStrings(i.textIds.get),stringToStrings(i.multiWordTextIds.get),stringToStrings(i.imageIds.get),stringToStrings(i.videoIds.get),i.xTranslate.get,i.yTranslate.get,i.xScale.get,i.yScale.get,toPrivacy(i.newPrivacy.get),i.isDeleted.get,cc.audiences)
  }
  protected def stringToStrings(s:String):Seq[String] = s match{
    case ss if ss == null => Nil
    case ss => ss.split("_:_").filter(s => s != "")
  }
  protected def stringsToString(ls:Seq[String]):String = ls.foldLeft("")((acc,s) => {
    acc match {
      case other:String if other.length > 0 => "%s_:_%s".format(other,s)
      case _ => s
    }
  })
  override def fromMeTLMoveDelta(i:MeTLMoveDelta):H2MoveDelta = {
    incCanvasContent(H2MoveDelta.create,i,"moveDelta").inkIds(stringsToString(i.inkIds)).textIds(stringsToString(i.textIds)).multiWordTextIds(stringsToString(i.multiWordTextIds)).imageIds(stringsToString(i.imageIds)).videoIds(stringsToString(i.videoIds)).xTranslate(i.xTranslate).yTranslate(i.yTranslate).xScale(i.xScale).yScale(i.yScale).newPrivacy(fromPrivacy(i.newPrivacy)).isDeleted(i.isDeleted).xOrigin(i.xOrigin).yOrigin(i.yOrigin)
  }
  def toMeTLDirtyInk(i:H2DirtyInk):MeTLDirtyInk = {
    val cc = decCanvasContent(i)
    MeTLDirtyInk(config,cc.author,cc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  override def fromMeTLDirtyInk(i:MeTLDirtyInk):H2DirtyInk = incCanvasContent(H2DirtyInk.create,i,"dirtyInk")
  def toMeTLDirtyImage(i:H2DirtyImage):MeTLDirtyImage = {
    val cc = decCanvasContent(i)
    MeTLDirtyImage(config,cc.author,cc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  override def fromMeTLDirtyImage(i:MeTLDirtyImage):H2DirtyImage = incCanvasContent(H2DirtyImage.create,i,"dirtyImage")
  def toMeTLDirtyVideo(i:H2DirtyVideo):MeTLDirtyVideo = {
    val cc = decCanvasContent(i)
    MeTLDirtyVideo(config,cc.author,cc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  override def fromMeTLDirtyVideo(i:MeTLDirtyVideo):H2DirtyVideo = incCanvasContent(H2DirtyVideo.create,i,"dirtyVideo")
  def toMeTLDirtyText(i:H2DirtyText):MeTLDirtyText = {
    val cc = decCanvasContent(i)
    MeTLDirtyText(config,cc.author,cc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,cc.audiences)
  }
  override def fromMeTLDirtyText(i:MeTLDirtyText):H2DirtyText = incCanvasContent(H2DirtyText.create,i,"dirtyText")
  def toMeTLCommand(i:H2Command):MeTLCommand = {
    val c = decStanza(i)
    MeTLCommand(config,c.author,c.timestamp,i.command.get,stringToStrings(i.commandParameters.get).toList,c.audiences)
  }
  override def fromMeTLCommand(i:MeTLCommand):H2Command = incStanza(H2Command.create,i,"command").command(i.command).commandParameters(stringsToString(i.commandParameters))
  def toMeTLFile(i:H2File):MeTLFile = {
    val c = decStanza(i)
    val url = i.url.get match {
      case null | "" => None
      case other => Some(other)
    }
    val bytes = url.map(u => config.getResource(u))
    MeTLFile(config,c.author,c.timestamp,i.name.get,i.identity.get,url,bytes,i.deleted.get,c.audiences)
  }
  def toMeTLFile(i:H2File,fileBytes:Array[Byte]):MeTLFile = {
    val c = decStanza(i)
    val url = i.url.get match {
      case null | "" => None
      case other => Some(other)
    }
    val bytes = Full(fileBytes)
    MeTLFile(config,c.author,c.timestamp,i.name.get,i.identity.get,url,bytes,i.deleted.get,c.audiences)
  }
  override def fromMeTLFile(i:MeTLFile):H2File = {
    incStanza(H2File.create,i,"file").name(i.name).partialIdentity(i.id.take(H2Constants.identity)).identity(i.id).url(i.url.getOrElse("")).deleted(i.deleted)
  }
  def toMeTLVideoStream(i:H2VideoStream):MeTLVideoStream = {
    val c = decStanza(i)
    val url = i.url.get match {
      case null | "" => None
      case other => Some(other)
    }
    MeTLVideoStream(config,c.author,i.identity.get,c.timestamp,url,i.deleted.get,c.audiences)
  }
  override def fromMeTLVideoStream(i:MeTLVideoStream):H2VideoStream = {
    incStanza(H2VideoStream.create,i,"videoStream").partialIdentity(i.id.take(H2Constants.identity)).identity(i.id).url(i.url.getOrElse("")).deleted(i.isDeleted)
  }

  def toSubmission(i:H2Submission):MeTLSubmission = {
    val c = decCanvasContent(i)
    val url = i.url.get
    val bytes = config.getResource(url)
    val blacklist = blacklistFromString(i.blacklist.get)
    MeTLSubmission(config,c.author,c.timestamp,i.title.get,i.slideJid.get,url,Full(bytes),blacklist,c.target,c.privacy,c.identity,c.audiences)
  }
  def toSubmission(i:H2Submission,imageData:Array[Byte]):MeTLSubmission = {
    val c = decCanvasContent(i)
    val url = i.url.get
    val bytes = imageData//config.getResource(url)
    val blacklist = blacklistFromString(i.blacklist.get)
    MeTLSubmission(config,c.author,c.timestamp,i.title.get,i.slideJid.get,url,Full(bytes),blacklist,c.target,c.privacy,c.identity,c.audiences)
  }

  override def fromSubmission(i:MeTLSubmission):H2Submission = incCanvasContent(H2Submission.create,i,"submission").title(i.title).slideJid(i.slideJid).url(i.url).blacklist(blacklistToString(i.blacklist))
  def toMeTLQuiz(i:H2Quiz):MeTLQuiz = {
    val url = i.url.get match {
      case s:String if (s.length > 0) => Full(s)
      case _ => Empty
    }
    val bytes = url.map(u => config.getResource(u))
    val c = decStanza(i)
    MeTLQuiz(config,c.author,c.timestamp,i.created.get,i.question.get,i.quizId.get,url,bytes,i.isDeleted.get,optionsFromString(i.options.get),c.audiences)
  }
  def toMeTLQuiz(i:H2Quiz,imageData:Array[Byte]):MeTLQuiz = {
    val url = i.url.get match {
      case s:String if (s.length > 0) => Full(s)
      case _ => Empty
    }
    val bytes = Full(imageData)//url.map(u => config.getResource(u))
    val c = decStanza(i)
    MeTLQuiz(config,c.author,c.timestamp,i.created.get,i.question.get,i.quizId.get,url,bytes,i.isDeleted.get,optionsFromString(i.options.get),c.audiences)
  }
  override def fromMeTLQuiz(i:MeTLQuiz):H2Quiz = {
    incStanza(H2Quiz.create,i,"quiz").created(i.created).question(i.question).quizId(i.id).url(i.url.openOr("")).isDeleted(i.isDeleted).options(optionsToString(i.options))
  }
  def toMeTLQuizResponse(i:H2QuizResponse):MeTLQuizResponse = {
    val c = decStanza(i)
    MeTLQuizResponse(config,c.author,c.timestamp,i.answer.get,i.answerer.get,i.quizId.get,c.audiences)
  }
  override def fromMeTLQuizResponse(i:MeTLQuizResponse):H2QuizResponse = {
    incStanza(H2QuizResponse.create,i,"quizResponse").answer(i.answer).answerer(i.answerer).quizId(i.id)
  }
  def toConversation(i:H2Conversation):Conversation = {
    val fr = for {
      sys <- Some(i.foreignRelationshipSystem.get)
      if (sys != null && sys != "")
      key <- Some(i.foreignRelationshipKey.get)
      if (key != null && key != "")
    } yield {
      ForeignRelationship(sys,key)
    }
    val audiences = parseAudiences(i.audiences.get)
    Conversation(config,i.author.get,i.lastAccessed.get,slidesFromString(i.slides.get),i.subject.get,i.tag.get,i.jid.get,i.title.get,i.creation.get,permissionsFromString(i.permissions.get),stringToStrings(i.blackList.get).toList,parseAudiences(i.audiences.get),fr)
  }
  override def fromConversation(i:Conversation):H2Conversation = {
    val rec = H2Conversation.find(By(H2Conversation.jid,i.jid)) match {
      case Full(c) => c
      case _ => H2Conversation.create
    }
    incMeTLContent(rec,i,"conversation").author(i.author).lastAccessed(i.lastAccessed).subject(i.subject).tag(i.tag).jid(i.jid).title(i.title).created(new java.util.Date(i.created).toString()).creation(i.created).permissions(permissionsToString(i.permissions)).blackList(stringsToString(i.blackList)).slides(slidesToString(i.slides)).foreignRelationshipSystem(i.foreignRelationship.map(_.system).getOrElse("")).foreignRelationshipKey(i.foreignRelationship.map(_.key).getOrElse("")).audiences(incAudiences(i.audiences).toString)
  }
  def optionsToString(ls:List[QuizOption]):String = {
    val xml = <options>{ls.map(o => xmlSerializer.fromQuizOption(o))}</options>
    xml.toString
  }
  def optionsFromString(s:String):List[QuizOption] = {
    (scala.xml.XML.loadString(s) \\ "quizOption").map(o => xmlSerializer.toQuizOption(o)).toList
  }
  def blacklistToString(bl:List[SubmissionBlacklistedPerson]):String = {
    try {
      (
        <blacklist>{bl.map(b => {
          <blacklistedPerson>
          <name>{b.username}</name>
          <highlight>{fromColor(b.highlight)}</highlight>
          </blacklistedPerson>
        })}</blacklist>
      ).toString
    } catch {
      case e:Exception => ""
    }
  }
  def blacklistFromString(s:String):List[SubmissionBlacklistedPerson] = {
    try {
      (scala.xml.XML.loadString(s) \\ "blacklistedPerson").flatMap(blp => {
        for (
          u <- (blp \\ "name").map(_.text);
          h <- (blp \\ "highlight").map(hl => toColor(hl.text))
        ) yield {
          SubmissionBlacklistedPerson(u,h)
        }
      }).toList
    } catch {
      case e:Exception => Nil
    }
  }
  def slidesToString(ls:List[Slide]):String = {
    "<slides>%s</slides>".format(ls.map(s => xmlSerializer.fromSlide(s).toString).mkString(""))
  }
  def slidesFromString(s:String):List[Slide] = {
    (scala.xml.XML.loadString(s) \\ "slide").map(sl => xmlSerializer.toSlide(sl)).toList
  }
  def permissionsToString(p:Permissions):String = {
    xmlSerializer.fromPermissions(p).toString
  }
  def permissionsFromString(s:String):Permissions = {
    try {
      xmlSerializer.toPermissions(scala.xml.XML.loadString(s))
    } catch {
      case e:Exception => {
        Permissions.default(config)
      }
    }
  }
  override def toPointList(input:AnyRef):List[Point] = Stopwatch.time("H2Serializer.toPointList",PointConverter.fromText(input.toString))
  override def fromPointList(input:List[Point]):AnyRef = Stopwatch.time("H2Serializer.fromPointList",PointConverter.toText(input))
  override def toColor(input:AnyRef):Color = ColorConverter.fromARGBHexString(input.toString)
  override def fromColor(input:Color):AnyRef = ColorConverter.toARGBHexString(input)

  def toGrade(i:H2Grade):MeTLGrade = Stopwatch.time("H2Serializer.toGrade",{
    val c = decStanza(i)
    val frs = for {
      us <- Some(i.foreignRelationshipSystem.get)
      if (us != null && us != "")
      uk <- Some(i.foreignRelationshipKey.get)
      if (uk != null && uk != "")
    } yield {
      (us,uk)
    }
    val gru = for {
      ugru <- Some(i.gradeReferenceUrl.get)
      if (ugru != null && ugru != "")
    } yield {
      ugru
    }
    val nmax = for {
      nMax <- Some(i.numericMaximum.get)
      if (i.gradeType.get == MeTLGradeValueType.Numeric)
    } yield {
      nMax
    }
    val nmin = for {
      nMin <- Some(i.numericMinimum.get)
      if (i.gradeType.get == MeTLGradeValueType.Numeric)
    } yield {
      nMin
    }
    MeTLGrade(config,c.author,c.timestamp,i.gradeId.get,i.location.get,i.name.get,i.description.get,i.gradeType.get,i.visible.get,frs,gru,nmax,nmin,c.audiences)
  })
  override def fromGrade(i:MeTLGrade):H2Grade = Stopwatch.time("H2Serializer.fromGrade",{
    val g = incStanza(H2Grade.create,i,"grade").gradeId(i.id).name(i.name).description(i.description).location(i.location).visible(i.visible).gradeType(i.gradeType)
    i.foreignRelationship.foreach(fr => {
      g.foreignRelationshipSystem(fr._1).foreignRelationshipKey(fr._2)
    })
    i.gradeReferenceUrl.foreach(gru => {
      g.gradeReferenceUrl(gru)
    })
    i.numericMaximum.foreach(nm => {
      g.numericMaximum(nm)
    })
    i.numericMinimum.foreach(nm => {
      g.numericMinimum(nm)
    })
    g
  })
  def toNumericGradeValue(i:H2NumericGradeValue):MeTLNumericGradeValue = Stopwatch.time("H2Serializer.toNumericGradeValue",{
    val c = decStanza(i)
    val comment = for {
      comm <- Some(i.comments.get)
      if (comm != null && comm != "")
    } yield {
      comm
    }
    val privateComment = for {
      pc <- Some(i.privateComments.get)
      if (pc != null && pc != "")
    } yield {
      pc
    }
    MeTLNumericGradeValue(config,c.author,c.timestamp,i.gradeId.get,i.gradedUser.get,i.gradeValue.get,comment,privateComment,c.audiences)
  })  
  override def fromNumericGradeValue(i:MeTLNumericGradeValue):H2NumericGradeValue = Stopwatch.time("H2Serializer.fromNumericGradeValue",{
    val g = incStanza(H2NumericGradeValue.create,i,"numericGradeValue").gradeId(i.gradeId).gradedUser(i.gradedUser).gradeValue(i.gradeValue)
    i.gradeComment.foreach(c => {
      g.comments(c)
    })
    i.gradePrivateComment.foreach(c => {
      g.privateComments(c)
    })
    g
  })
  def toBooleanGradeValue(i:H2BooleanGradeValue):MeTLBooleanGradeValue = Stopwatch.time("H2Serializer.toBooleanGradeValue",{
    val c = decStanza(i)
    val comment = for {
      comm <- Some(i.comments.get)
      if (comm != null && comm != "")
    } yield {
      comm
    }
    val privateComment = for {
      pc <- Some(i.privateComments.get)
      if (pc != null && pc != "")
    } yield {
      pc
    }
    MeTLBooleanGradeValue(config,c.author,c.timestamp,i.gradeId.get,i.gradedUser.get,i.gradeValue.get,comment,privateComment,c.audiences)
  })  
  override def fromBooleanGradeValue(i:MeTLBooleanGradeValue):H2BooleanGradeValue = Stopwatch.time("H2Serializer.fromBooleanGradeValue",{
    val g = incStanza(H2BooleanGradeValue.create,i,"booleanGradeValue").gradeId(i.gradeId).gradedUser(i.gradedUser).gradeValue(i.gradeValue)
    i.gradeComment.foreach(c => {
      g.comments(c)
    })
    i.gradePrivateComment.foreach(c => {
      g.privateComments(c)
    })
    g
  })
  def toTextGradeValue(i:H2TextGradeValue):MeTLTextGradeValue = Stopwatch.time("H2Serializer.toTextGradeValue",{
    val c = decStanza(i)
    val comment = for {
      comm <- Some(i.comments.get)
      if (comm != null && comm != "")
    } yield {
      comm
    }
    val privateComment = for {
      pc <- Some(i.privateComments.get)
      if (pc != null && pc != "")
    } yield {
      pc
    }
    MeTLTextGradeValue(config,c.author,c.timestamp,i.gradeId.get,i.gradedUser.get,i.gradeValue.get,comment,privateComment,c.audiences)
  })  
  override def fromTextGradeValue(i:MeTLTextGradeValue):H2TextGradeValue = Stopwatch.time("H2Serializer.fromTextGradeValue",{
    val g = incStanza(H2TextGradeValue.create,i,"textGradeValue").gradeId(i.gradeId).gradedUser(i.gradedUser).gradeValue(i.gradeValue)
    i.gradeComment.foreach(c => {
      g.comments(c)
    })
    i.gradePrivateComment.foreach(c => {
      g.privateComments(c)
    })
    g
  })

}
