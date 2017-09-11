package com.metl.data

import com.metl.utils._
import com.metl.model._
import net.liftweb.common._
import net.liftweb.util.Helpers._
import Privacy._
import com.metl.external.ForeignRelationship
import net.liftweb.json.{Formats, MappingException, NoTypeHints, Serialization, TypeInfo}
import net.liftweb.json.JsonAST._

object ConversionHelper extends Logger {
  def toDouble(a:Any):Double = a match{
    case d:Double => d
    case i:scala.math.BigInt => i.doubleValue
    case JString("NaN") => Double.NaN
    case other =>{
      warn("Attempted to apply ConversionHelper.toDouble to [%s]".format(other))
      Double.NaN
    }
  }
}

class PrivacySerializer extends net.liftweb.json.Serializer[Privacy] {
  private val PrivacyClass = classOf[Privacy]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Privacy] = {
    case (TypeInfo(PrivacyClass, _), json) => json match {
      case JString(p) => Privacy.parse(p)
      case x => throw new MappingException("Can't convert " + x + " to Privacy")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: Privacy => JField("privacy", JString(x.toString.toLowerCase))
  }
}

class ColorSerializer extends net.liftweb.json.Serializer[Color] with JsonSerializerHelper {
  import ColorConverter._
  private val ColorClass = classOf[Color]
  protected def toColor(input:AnyRef):Color = Stopwatch.time("JsonSerializer.toColor", {
    input match {
      case List(c,a) => {
        val color = c.asInstanceOf[String]
        val alpha = ConversionHelper.toDouble(a).toInt
        def clamp (n:Integer,min:Integer=0,max:Integer=255) = Math.max(min,Math.min(max,n))
        val r = convert2AfterN(color,1)
        val g = convert2AfterN(color,3)
        val b = convert2AfterN(color,5)
        Color(alpha,clamp(r),clamp(g),clamp(b))
      }
      case _ => Color.empty
    }
  })
  protected def fromColor(input:Color):JValue = Stopwatch.time("JsonSerializer.fromColor",{
    JArray(List(JString("#%02x%02x%02x".format(input.red,input.green,input.blue)),JInt(input.alpha)))
  })
  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Color] = {
    case (TypeInfo(ColorClass, _), json) => json match {
      case JArray(List(JString(hexString),JInt(alpha))) => toColor(List(hexString,alpha))
      case x => throw new MappingException("Can't convert " + x + " to Color")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: Color => fromColor(x)
  }
}



trait JsonSerializerHelper {

  lazy implicit val formats = Serialization.formats(NoTypeHints) + new PrivacySerializer + new ColorSerializer

  def getStringByName(input:JObject,name:String) = (input \ name).extract[String]
  def getBooleanByName(input:JObject,name:String) = (input \ name).extract[Boolean]
  def getOptionalBooleanByName(input:JObject,name:String) = (input \ name).extract[Option[Boolean]]
  def getIntByName(input:JObject,name:String) = (input \ name).extract[Int]
  def getLongByName(input:JObject,name:String) = (input \ name).extract[Long]
  def getDoubleByName(input:JObject,name:String) = tryo((input \ name).extract[Double]) match {
    case Full(d) => d
    case malformedDouble => {
      error("getDoubleByName failed: %s in %s".format(name,input))
        (input \ name).extract[Int].toDouble
    }
  }
  def getPrivacyByName(input:JObject,name:String) = (input \ name).extract[Privacy]
  def getObjectByName(input:JObject,name:String) = input.values(name).asInstanceOf[JObject]
  def getOptionalStringByName(input:JObject,name:String) = (input \ name).extract[Option[String]]
  def getListOfDoublesByName(input:JObject,name:String) = (input \ name).extract[List[Double]]
  def getListOfStringsByName(input:JObject,name:String) = (input \ name).extract[List[String]]
  def getListOfObjectsByName(input:JObject,name:String) = {
    input.obj.find(_.name == name).toList.flatMap(_.value match {
      case JArray(l) => {
        val objs:List[JObject] = l.flatMap((li:JValue) => li match {
          case li:JObject => List(li)
          case _ => Nil
        })
        objs
      }
      case _ => Nil
    })
  }
  def getOptionalObjectByName(input:JObject,name:String) = input.obj.find(_.name == name).toList.headOption 
  def getColorByName(input:JObject,name:String) = input.values(name).asInstanceOf[List[Any]]
}

class JsonSerializer(config:ServerConfiguration) extends Serializer with JsonSerializerHelper with Logger {
  type T = JValue
  val configName = config.name

  protected def parseAudiences(input:MeTLData):List[JField] = {
    val res = List(
      JField("audiences",JArray(input.audiences.map(a => {
        JObject(List(
          JField("domain",JString(a.domain)),
          JField("name",JString(a.name)),
          JField("type",JString(a.audienceType)),
          JField("action",JString(a.action))
        ))
      })))
    )
    res
  }
  protected def parseMeTLContent(input:MeTLStanza):List[JField] = {
    List(
      JField("author",JString(input.author)),
      JField("timestamp",JInt(input.timestamp))
    ) ::: parseAudiences(input)
  }
  protected def parseCanvasContent(input:MeTLCanvasContent):List[JField] = {
    List(
      JField("target",JString(input.target)),
      JField("privacy",JString(input.privacy.toString)),
      JField("slide",JString(input.slide)),
      JField("identity",JString(input.identity))
    )
  }
  protected def parseJObjForAudiences(input:JObject,config:ServerConfiguration = ServerConfiguration.empty):List[Audience] = {
    ((input \ "audiences").extract[List[JObject]]).flatMap(a => {
      try {
        val domain = getStringByName(a,"domain")
        val name = getStringByName(a,"name")
        val audienceType = getStringByName(a,"type")
        val action = getStringByName(a,"action")
        Some(Audience(config,domain,name,audienceType,action))
      } catch {
        case e:Exception => {
          None
        }
      }
    })
  }

  protected def parseJObjForMeTLContent(input:JObject,config:ServerConfiguration = ServerConfiguration.empty):ParsedMeTLContent = {
    val author = (input \ "author").extract[String]
    val timestamp = getLongByName(input,"timestamp")
    val audiences = parseJObjForAudiences(input,config)
    ParsedMeTLContent(author,timestamp,audiences)
  }
  protected def parseJObjForCanvasContent(input:JObject):ParsedCanvasContent = {
    val target = getStringByName(input,"target")
    val privacy = getPrivacyByName(input,"privacy")
    val slide = getStringByName(input,"slide")
    val identity = getStringByName(input,"identity")
    ParsedCanvasContent(target,privacy,slide,identity)
  }
  override def fromHistory(input:History):JValue = Stopwatch.time("JsonSerializer.fromHistory",{
    val (texts,highlighters,inks,images,multiWordTexts,videos) = input.getRenderableGrouped
    val latestTexts = (texts.groupBy(_.identity).toList.flatMap{
      case (identity,Nil) => None
      case (identity,items) => items.sortBy(_.timestamp).reverse.headOption.map(head => JField(identity,fromMeTLText(head)))
    }).toList
    val latestMultiWords = (multiWordTexts.groupBy(_.identity).toList.flatMap{
      case (identity,Nil) => None
      case (identity,items) => items.sortBy(_.timestamp).reverse.headOption.map(head => JField(identity,fromMeTLMultiWordText(head)))
    }).toList
    toJsObj("history",List(
      JField("jid",JString(input.jid)),
      JField("inks",JObject(inks.map(i => JField(i.identity,fromMeTLInk(i))))),
      JField("highlighters",JObject(highlighters.map(i => JField(i.identity,fromMeTLInk(i))))),
      JField("images",JObject(images.map(i => JField(i.identity,fromMeTLImage(i))))),
      JField("videos",JObject(videos.map(i => JField(i.identity,fromMeTLVideo(i))))),
      JField("texts",JObject(latestTexts)),
      //JField("texts",JObject(texts.map(i => JField(i.identity,fromMeTLText(i))))),
      JField("themes",JArray(input.getThemes.map(fromTheme _))),
      JField("chatMessages",JArray(input.getChatMessages.map(fromChatMessage _))),
      JField("multiWordTexts",JObject(latestMultiWords)),
      //JField("multiWordTexts",JObject(multiWordTexts.map(i => JField(i.identity,fromMeTLMultiWordText(i))))),
      JField("quizzes",JArray(input.getQuizzes.map(i => fromMeTLQuiz(i)))),
      JField("quizResponses",JArray(input.getQuizResponses.map(i => fromMeTLQuizResponse(i)))),
      JField("submissions",JArray(input.getSubmissions.map(i => fromSubmission(i)))),
      JField("attendances",JArray(input.getAttendances.map(i => fromMeTLAttendance(i)))),
      JField("commands",JArray(input.getCommands.map(i => fromMeTLCommand(i)))),
      JField("files",JArray(input.getFiles.map(i => fromMeTLFile(i)))),
      JField("videoStreams",JArray(input.getVideoStreams.map(i => fromMeTLVideoStream(i)))),
      JField("grades",JArray(input.getGrades.map(i => fromGrade(i)))),
      JField("gradeValues",JArray(input.getGradeValues.flatMap{
        case i:MeTLNumericGradeValue => Some(fromNumericGradeValue(i))
        case i:MeTLBooleanGradeValue => Some(fromBooleanGradeValue(i))
        case i:MeTLTextGradeValue => Some(fromTextGradeValue(i))
        case _ => None
      })),
      JField("deletedCanvasContents",JArray(input.getDeletedCanvasContents.map(i => fromMeTLData(i)))),
      JField("undeletedCanvasContents",JArray(input.getUndeletedCanvasContents.map(i => fromMeTLUndeletedCanvasContent(i)))),
      JField("unhandledCanvasContents",JArray(input.getUnhandledCanvasContents.map(i => fromMeTLUnhandledCanvasContent(i)))),
      JField("unhandledStanzas",JArray(input.getUnhandledStanzas.map(i => fromMeTLUnhandledStanza(i))))
        //      JField("unhandledData",JArray(input.getUnhandledData.map(i => fromMeTLUnhandledData(i))))
    ))
  })
  protected def getFields(i:JValue,parentName:String):List[JField] = {
    i match {
      case input:JObject => (input \ parentName) match {
        case parent:JObject => parent.obj
        case _ => List.empty[JField]
      }
      case _ => List.empty[JField]
    }
  }
  override def toHistory(i:JValue):History = Stopwatch.time("JsonSerializer.toHistory",{
    val jid = i match {
      case input:JObject => getStringByName(input,"jid")
      case _ => ""
    }
    val history = new History(jid)
    getFields(i,"inks").foreach(jf => history.addStanza(toMeTLInk(jf.value)))
    getFields(i,"highlighters").foreach(jf => history.addStanza(toMeTLInk(jf.value)))
    getFields(i,"images").foreach(jf => history.addStanza(toMeTLImage(jf.value)))
    getFields(i,"videos").foreach(jf => history.addStanza(toMeTLVideo(jf.value)))
    getFields(i,"texts").foreach(jf => history.addStanza(toMeTLText(jf.value)))
    getFields(i,"quizzes").foreach(jf => history.addStanza(toMeTLQuiz(jf.value)))
    getFields(i,"quizResponses").foreach(jf => history.addStanza(toMeTLQuizResponse(jf.value)))
    getFields(i,"submissions").foreach(jf => history.addStanza(toSubmission(jf.value)))
    getFields(i,"attendances").foreach(jf => history.addStanza(toMeTLAttendance(jf.value)))
    getFields(i,"commands").foreach(jf => history.addStanza(toMeTLCommand(jf.value)))
    getFields(i,"files").foreach(jf => history.addStanza(toMeTLFile(jf.value)))
    getFields(i,"videoStreams").foreach(jf => history.addStanza(toMeTLVideoStream(jf.value)))
    getFields(i,"themes").foreach(jf => history.addStanza(toTheme(jf.value)))
    getFields(i,"chatMessages").foreach(jf => history.addStanza(toChatMessage(jf.value)))
    getFields(i,"deletedCanvasContents").foreach(jf => {
      toMeTLData(jf.value) match {
        case cc:MeTLCanvasContent => history.addDeletedCanvasContent(cc)
        case _ => {}
      }
    })
    getFields(i,"grades").foreach(jf => history.addStanza(toGrade(jf.value)))
    getFields(i,"gradeValues").foreach(jf => {
      jf.value match {
        case jo:JObject if (isOfType(jo,"numericGradeValue")) => history.addStanza(toNumericGradeValue(jo))
        case jo:JObject if (isOfType(jo,"textGradeValue")) => history.addStanza(toTextGradeValue(jo))
        case jo:JObject if (isOfType(jo,"booleanGradeValue")) => history.addStanza(toBooleanGradeValue(jo))
      }
    })
    getFields(i,"undeletedCanvasContent").foreach(jf => history.addStanza(toMeTLUndeletedCanvasContent(jf.value)))
    getFields(i,"unhandledCanvasContents").foreach(jf => history.addStanza(toMeTLUnhandledCanvasContent(jf.value)))
    getFields(i,"unhandledStanzas").foreach(jf => history.addStanza(toMeTLUnhandledStanza(jf.value)))
    //    getFields(i,"unhandledData").foreach(jf => history.addStanza(toMeTLUnhandledData(jf.value)))
    history
  })
  protected def hasField(input:JObject,fieldName:String) = Stopwatch.time("JsonSerializer.has",{
    input.values.contains(fieldName)
  })
  protected def hasFields(input:JObject,fieldNames:List[String]) = Stopwatch.time("JsonSerializer.hasFields",{
    val allValues = input.values
    !fieldNames.exists(fn => !allValues.contains(fn))
  })
  protected def isOfType(input:JObject,name:String) = Stopwatch.time("JsonSerializer.isOfType",{
    input.values("type") == name
  })
  protected def toJsObj(name:String,fields:List[JField]) = Stopwatch.time("JsonSerializer.toJsObj",{
    JObject(JField("type",JString(name)) :: fields)
  })
  override def toMeTLData(input:JValue):MeTLData = Stopwatch.time("JsonSerializer.toMeTLStanza",{
    input match {
      case jo:JObject if (isOfType(jo,"ink")) => toMeTLInk(jo)
      case jo:JObject if (isOfType(jo,"text")) => toMeTLText(jo)
      case jo:JObject if (isOfType(jo,"multiWordText")) => toMeTLMultiWordText(jo)
      case jo:JObject if (isOfType(jo,"image")) => toMeTLImage(jo)
      case jo:JObject if (isOfType(jo,"video")) => toMeTLVideo(jo)
      case jo:JObject if (isOfType(jo,"dirtyInk")) => toMeTLDirtyInk(jo)
      case jo:JObject if (isOfType(jo,"dirtyText")) => toMeTLDirtyText(jo)
      case jo:JObject if (isOfType(jo,"dirtyImage")) => toMeTLDirtyImage(jo)
      case jo:JObject if (isOfType(jo,"dirtyVideo")) => toMeTLDirtyVideo(jo)
      case jo:JObject if (isOfType(jo,"submission")) => toSubmission(jo)
      case jo:JObject if (isOfType(jo,"quiz")) => toMeTLQuiz(jo)
      case jo:JObject if (isOfType(jo,"theme")) => toTheme(jo)
      case jo:JObject if (isOfType(jo,"chatMessage")) => toChatMessage(jo)
      case jo:JObject if (isOfType(jo,"quizResponse")) => toMeTLQuizResponse(jo)
      case jo:JObject if (isOfType(jo,"moveDelta")) => toMeTLMoveDelta(jo)
      case jo:JObject if (isOfType(jo,"command")) => toMeTLCommand(jo)
      case jo:JObject if (isOfType(jo,"attendance")) => toMeTLAttendance(jo)
      case jo:JObject if (isOfType(jo,"file")) => toMeTLFile(jo)
      case jo:JObject if (isOfType(jo,"videoStream")) => toMeTLVideoStream(jo)
      case jo:JObject if (isOfType(jo,"undeletedCanvasContent")) => toMeTLUndeletedCanvasContent(jo)
      case jo:JObject if (isOfType(jo,"grade")) => toGrade(jo)
      case jo:JObject if (isOfType(jo,"numericGradeValue")) => toNumericGradeValue(jo)
      case jo:JObject if (isOfType(jo,"textGradeValue")) => toTextGradeValue(jo)
      case jo:JObject if (isOfType(jo,"booleanGradeValue")) => toBooleanGradeValue(jo)
      case other:JObject if hasFields(other,List("target","privacy","slide","identity")) => toMeTLUnhandledCanvasContent(other)
      case other:JObject if hasFields(other,List("author","timestamp")) => toMeTLUnhandledStanza(other)
      case other:JObject => toMeTLUnhandledData(other)
      case nonObjectData => toMeTLUnhandledData(nonObjectData)
    }
  })
  protected val jsonType = "json"
  override def fromMeTLUnhandledData(i:MeTLUnhandledData):JValue = i.valueType.toLowerCase.trim match {
    case s:String if s == jsonType => net.liftweb.json.parse(i.unhandled)
    case _ => JNull
  }
  override def fromMeTLUnhandledStanza(i:MeTLUnhandledStanza):JValue = i.valueType.toLowerCase.trim match {
    case s:String if s == jsonType => net.liftweb.json.parse(i.unhandled)
    case _ => JNull
  }
  override def fromMeTLUnhandledCanvasContent(i:MeTLUnhandledCanvasContent):JValue = i.valueType.toLowerCase.trim match {
    case s:String if s == jsonType => net.liftweb.json.parse(i.unhandled)
    case _ => JNull
  }
  override def toMeTLUnhandledData(i:JValue) = MeTLUnhandledData(config,i.toString,jsonType)
  override def toMeTLUnhandledStanza(i:JValue) = {
    i match {
      case input:JObject => {
        val m = parseJObjForMeTLContent(input,config)
        MeTLUnhandledStanza(config,m.author,m.timestamp,i.toString,jsonType,m.audiences)
      }
      case other => MeTLUnhandledStanza.empty(other.toString,jsonType)
    }
  }

  override def toMeTLUnhandledCanvasContent(i:JValue) = {
    i match {
      case input:JObject => {
        val cc = parseJObjForCanvasContent(input)
        val m = parseJObjForMeTLContent(input,config)
        MeTLUnhandledCanvasContent(config,m.author,m.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,m.audiences,1.0,1.0,i.toString,jsonType)
      }
      case other => MeTLUnhandledCanvasContent.empty(other.toString,jsonType)
    }
  }
  override def fromMeTLFile(input:MeTLFile):JValue = Stopwatch.time("JsonSerializer.fromMeTLFile",{
    toJsObj("file",List(
      JField("name",JString(input.name)),
      JField("deleted",JBool(input.deleted)),
      JField("id",JString(input.id))
    ) :::
      parseMeTLContent(input) :::
      input.url.map(u => JField("url",JString(u))).toList)
  })

  override def toTheme(i:JValue):MeTLTheme = Stopwatch.time("JsonSerializer.toTheme",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val text = getStringByName(input,"text")
        val origin = getStringByName(input,"origin")
        val location = getStringByName(input,"location")
        MeTLTheme(config,mc.author,mc.timestamp,location,Theme(mc.author,text,origin),mc.audiences)
      }
      case _ => MeTLTheme.empty
    }
  })

  override def fromTheme(t:MeTLTheme):JValue = Stopwatch.time("JsonSerializer.fromTheme",{
    toJsObj("theme",List(
      JField("text",JString(t.theme.text)),
      JField("origin",JString(t.theme.origin)),
      JField("location",JString(t.location))
    ) ::: parseMeTLContent(t))
  })
  override def toChatMessage(i:JValue):MeTLChatMessage = Stopwatch.time("JsonSerializer.toChatMessage",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val content = getStringByName(input,"content")
        val contentType = getStringByName(input,"contentType")
        val context = getStringByName(input,"context")
        val identity = getStringByName(input,"identity")
        MeTLChatMessage(config,mc.author,mc.timestamp,identity,contentType,content,context,mc.audiences)
      }
    }
  })

  override def fromChatMessage(t:MeTLChatMessage):JValue = Stopwatch.time("JsonSerializer.fromChatMessage",{
    toJsObj("chatMessage",List(
      JField("identity",JString(t.identity)),
      JField("contentType",JString(t.contentType)),
      JField("content",JString(t.content)),
      JField("context",JString(t.context))
    ) ::: parseMeTLContent(t))
  })

  override def toMeTLFile(i:JValue):MeTLFile = Stopwatch.time("JsonSerializer.toMeTLFile",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val name = getStringByName(input,"name")
        val id = getStringByName(input,"id")
        //        val bytes = (input \ "bytes").extractOpt[String].map(bs => base64Decode(bs))
        val url = (input \ "url").extractOpt[String]
        val deleted = getBooleanByName(input,"deleted")
        val bytes = url.map(u => config.getResource(u))
        MeTLFile(config,mc.author,mc.timestamp,name,id,url,bytes,deleted,mc.audiences)
      }
      case _ => MeTLFile.empty
    }
  })
  override def fromMeTLVideoStream(input:MeTLVideoStream):JValue = Stopwatch.time("JsonSerializer.fromMeTLVideoStream",{
    toJsObj("file",List(
      JField("deleted",JBool(input.isDeleted)),
      JField("id",JString(input.id))
    ) :::
      parseMeTLContent(input) :::
      input.url.map(u => JField("url",JString(u))).toList)
  })

  override def toMeTLVideoStream(i:JValue):MeTLVideoStream = Stopwatch.time("JsonSerializer.toMeTLVideoStream",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val id = getStringByName(input,"id")
        val url = (input \ "url").extractOpt[String]
        val deleted = getBooleanByName(input,"deleted")
        MeTLVideoStream(config,mc.author,id,mc.timestamp,url,deleted,mc.audiences)
      }
      case _ => MeTLVideoStream.empty
    }
  })
  override def fromMeTLMoveDelta(input:MeTLMoveDelta):JValue = Stopwatch.time("JsonSerializer.fromMeTLMoveDelta",{
    toJsObj("moveDelta",List(
      JField("inkIds",JArray(input.inkIds.map(JString).toList)),
      JField("imageIds",JArray(input.imageIds.map(JString).toList)),
      JField("textIds",JArray(input.textIds.map(JString).toList)),
      JField("videoIds",JArray(input.videoIds.map(JString).toList)),
      JField("multiWordTextIds",JArray(input.multiWordTextIds.map(JString).toList)),
      JField("xTranslate",JDouble(input.xTranslate)),
      JField("yTranslate",JDouble(input.yTranslate)),
      JField("xScale",JDouble(input.xScale)),
      JField("yScale",JDouble(input.yScale)),
      JField("newPrivacy",JString(input.newPrivacy.toString.toLowerCase)),
      JField("isDeleted",JBool(input.isDeleted)),
      JField("xOrigin", JDouble(input.xOrigin)),
      JField("yOrigin", JDouble(input.yOrigin))
    ) ::: parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLMoveDelta(i:JValue):MeTLMoveDelta = Stopwatch.time("JsonSerializer.toMeTLMoveDelta",{
    i match{
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        val inkIds = getListOfStringsByName(input,"inkIds")
        val textIds = getListOfStringsByName(input,"textIds")
        val multiWordTextIds = getListOfStringsByName(input,"multiWordTextIds")
        val imageIds = getListOfStringsByName(input,"imageIds")
        val videoIds = getListOfStringsByName(input,"videoIds")
        val xTranslate = getDoubleByName(input,"xTranslate")
        val yTranslate = getDoubleByName(input,"yTranslate")
        val xScale = getDoubleByName(input,"xScale")
        val yScale = getDoubleByName(input,"yScale")
        val newPrivacy = getPrivacyByName(input,"newPrivacy")
        val isDeleted = getBooleanByName(input,"isDeleted")
        val xOrigin = getDoubleByName(input,"xOrigin")
        val yOrigin = getDoubleByName(input,"yOrigin")
        MeTLMoveDelta(config,mc.author,mc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,xOrigin,yOrigin,inkIds,textIds,multiWordTextIds,imageIds,videoIds,xTranslate,yTranslate,xScale,yScale,newPrivacy,isDeleted,mc.audiences)
      }
      case _ => MeTLMoveDelta.empty
    }
  })
  override def toMeTLAttendance(i:JValue):Attendance = Stopwatch.time("JsonSerializer.toMeTLAttendance",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val location = getStringByName(input,"location")
        val present = getBooleanByName(input,"present")
        Attendance(config,mc.author,mc.timestamp,location,present,mc.audiences)
      }
      case _ => Attendance.empty
    }
  })
  override def fromMeTLAttendance(i:Attendance):JValue = Stopwatch.time("JsonSerializer.fromMeTLAttendance",{
    toJsObj("attendance",List(
      JField("location",JString(i.location)),
      JField("present",JBool(i.present))
    ) ::: parseMeTLContent(i))
  })
  override def toMeTLInk(i:JValue):MeTLInk = Stopwatch.time("JsonSerializer.toMeTLInk", {
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        val checksum = getDoubleByName(input,"checksum")
        val startingSum = getDoubleByName(input,"startingSum")
        val points = toPointList(getListOfDoublesByName(input,"points")).asInstanceOf[List[Point]]
        val color = toColor(getColorByName(input,"color"))
        val thickness = getDoubleByName(input,"thickness")
        val isHighlighter = getBooleanByName(input,"isHighlighter")
        MeTLInk(config,mc.author,mc.timestamp,checksum,startingSum,points,color,thickness,isHighlighter,cc.target,cc.privacy,cc.slide,cc.identity,mc.audiences)
      }
      case _ => MeTLInk.empty
    }
  })
  override def fromMeTLInk(input:MeTLInk):JValue = Stopwatch.time("JsonSerializer.fromMeTLInk",{
    toJsObj("ink",List(
      JField("bounds",JArray(List(input.left,input.top,input.right,input.bottom).map(JDouble))),
      JField("checksum",JDouble(input.checksum)),
      JField("startingSum",JDouble(input.startingSum)),
      JField("points",fromPointList(input.points).asInstanceOf[JValue]),
      JField("color",fromColor(input.color).asInstanceOf[JValue]),
      JField("thickness",JDouble(input.thickness)),
      JField("isHighlighter",JBool(input.isHighlighter))
    ) ::: parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLImage(i:JValue):MeTLImage = Stopwatch.time("JsonSerializer.toMeTLImage",{
    try{
      i match {
        case input:JObject => {
          val mc = parseJObjForMeTLContent(input,config)
          val cc = parseJObjForCanvasContent(input)
          val tag = getStringByName(input,"tag")
          val source = Full(getStringByName(input,"source"))
          val imageBytes = source.map(u => config.getResource(u))
          val pngBytes = Empty
          val width = getDoubleByName(input,"width")
          val height = getDoubleByName(input,"height")
          val x = getDoubleByName(input,"x")
          val y = getDoubleByName(input,"y")
          MeTLImage(config,mc.author,mc.timestamp,tag,source,imageBytes,pngBytes,width,height,x,y,cc.target,cc.privacy,cc.slide,cc.identity,mc.audiences)
        }
        case _ => MeTLImage.empty
      }
    }
    catch{
      case e => {
        error("JsonSerializer.toMeTLImage failed on %s: %s".format(i,e))
        throw e
      }
    }
  })
  override def fromMeTLImage(input:MeTLImage):JValue = Stopwatch.time("JsonSerializer.fromMeTLImage",{
    toJsObj("image",List(
      JField("tag",JString(input.tag)),
      JField("width",JDouble(if(input.width.isNaN) 0 else input.width)),
      JField("height",JDouble(if(input.height.isNaN) 0 else input.height)),
      JField("x",JDouble(input.x)),
      JField("y",JDouble(input.y))
    ) ::: input.source.map(u => List(JField("source",JString(u)))).openOr(List.empty[JField]) ::: parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLVideo(i:JValue):MeTLVideo = Stopwatch.time("JsonSerializer.toMeTLVideo",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        val source = Full(getStringByName(input,"source"))
        val videoBytes = source.map(u => config.getResource(u))
        val width = getDoubleByName(input,"width")
        val height = getDoubleByName(input,"height")
        val x = getDoubleByName(input,"x")
        val y = getDoubleByName(input,"y")
        MeTLVideo(config,mc.author,mc.timestamp,source,videoBytes,width,height,x,y,cc.target,cc.privacy,cc.slide,cc.identity,mc.audiences)
      }
      case _ => MeTLVideo.empty
    }
  })
  override def fromMeTLVideo(input:MeTLVideo):JValue = Stopwatch.time("JsonSerializer.fromMeTLVideo",{
    toJsObj("video",List(
      JField("width",JDouble(if(input.width.isNaN) 0 else input.width)),
      JField("height",JDouble(if(input.height.isNaN) 0 else input.height)),
      JField("x",JDouble(input.x)),
      JField("y",JDouble(input.y))
    ) ::: input.source.map(u => List(JField("source",JString(u)))).openOr(List.empty[JField]) ::: parseMeTLContent(input) ::: parseCanvasContent(input))
  })

  override def toMeTLText(i:JValue):MeTLText = Stopwatch.time("JsonSerializer.toMeTLText",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        val text = getStringByName(input,"text")
        val height = getDoubleByName(input,"height")
        val width = getDoubleByName(input,"width")
        val caret = getIntByName(input,"caret")
        val x = getDoubleByName(input,"x")
        val y = getDoubleByName(input,"y")
        val tag = getStringByName(input,"tag")
        val style = getStringByName(input,"style")
        val family = getStringByName(input,"family")
        val weight = getStringByName(input,"weight")
        val size = getDoubleByName(input,"size")
        val decoration = getStringByName(input,"decoration")
        val color = toColor(getColorByName(input,"color"))
        MeTLText(config,mc.author,mc.timestamp,text,height,width,caret,x,y,tag,style,family,weight,size,decoration,cc.identity,cc.target,cc.privacy,cc.slide,color,mc.audiences)
      }
      case _ => MeTLText.empty
    }
  })
  override def toMeTLMultiWordText(j:JValue):MeTLMultiWordText = Stopwatch.time("JsonSerializer.toMeTLMultiWordText",{
    j match {
      case input:JObject => {
        try{
          val mc = parseJObjForMeTLContent(input,config)
          val cc = parseJObjForCanvasContent(input)
          val requestedWidth = getDoubleByName(input,"requestedWidth")
          val tag = getStringByName(input,"tag")
          val words:Seq[MeTLTextWord] = (input \ "words").extract[List[MeTLTextWord]]
          val x = getDoubleByName(input,"x")
          val y = getDoubleByName(input,"y")
          val width = getDoubleByName(input,"width")
          val height = getDoubleByName(input,"height")
          MeTLMultiWordText(config,mc.author,mc.timestamp,height,width,requestedWidth,x,y,tag,cc.identity,cc.target,cc.privacy,cc.slide,words,mc.audiences)
        }
        catch {
          case e:Throwable =>
            e.printStackTrace()
            throw e
        }
      }
      case _ => MeTLMultiWordText.empty
    }
  })
  override def fromMeTLWord(input:MeTLTextWord):JValue = toJsObj("word",List(
    JField("text",JString(input.text)),
    JField("bold",JBool(input.bold)),
    JField("underline",JBool(input.underline)),
    JField("italic",JBool(input.italic)),
    JField("justify",JString(input.justify)),
    JField("color",fromColor(input.color).asInstanceOf[JValue]),
    JField("font",JString(input.font)),
    JField("size",JDouble(input.size))
  ))
  override def fromMeTLMultiWordText(input:MeTLMultiWordText) = Stopwatch.time("JsonSerializer.fromMeTLMultiWordText",{
    val words = input.words.map(fromMeTLWord _).toList
    toJsObj("multiWordText",List(
      JField("x",JDouble(input.x)),
      JField("y",JDouble(input.y)),
      JField("width",JDouble(input.width)),
      JField("height",JDouble(input.height)),
      JField("tag",JString(input.tag)),
      JField("requestedWidth",JDouble(input.requestedWidth)),
      JField("words",JArray(words))
    ) ::: parseMeTLContent(input) ::: parseCanvasContent(input))
  });
  override def fromMeTLText(input:MeTLText):JValue = Stopwatch.time("JsonSerializer.fromMeTLText",{
    toJsObj("text",List(
      JField("text",JString(input.text)),
      JField("height",JDouble(input.height)),
      JField("width",JDouble(input.width)),
      JField("x",JDouble(input.x)),
      JField("y",JDouble(input.y)),
      JField("caret",JInt(input.caret)),
      JField("tag",JString(input.tag)),
      JField("style",JString(input.style)),
      JField("family",JString(input.family)),
      JField("font",JString("%spx %s".format(input.size,input.family))),
      JField("weight",JString(input.weight)),
      JField("size",JDouble(input.size)),
      JField("decoration",JString(input.decoration)),
      JField("color",fromColor(input.color).asInstanceOf[JValue])
    ) ::: parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLDirtyInk(i:JValue):MeTLDirtyInk = Stopwatch.time("JsonSerializer.toMeTLDirtyInk",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        MeTLDirtyInk(config,mc.author,mc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,mc.audiences)
      }
      case _ => MeTLDirtyInk.empty
    }
  })
  override def fromMeTLDirtyInk(input:MeTLDirtyInk):JValue = Stopwatch.time("JsonSerializer.fromMeTLDirtyInk",{
    toJsObj("dirtyInk",parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLDirtyVideo(i:JValue):MeTLDirtyVideo = Stopwatch.time("JsonSerializer.toMeTLDirtyVideo",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        MeTLDirtyVideo(config,mc.author,mc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,mc.audiences)
      }
      case _ => MeTLDirtyVideo.empty
    }
  })
  override def fromMeTLDirtyVideo(input:MeTLDirtyVideo):JValue = Stopwatch.time("JsonSerializer.fromMeTLDirtyVideo",{
    toJsObj("dirtyVideo",parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLDirtyImage(i:JValue):MeTLDirtyImage = Stopwatch.time("JsonSerializer.toMeTLDirtyImage",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        MeTLDirtyImage(config,mc.author,mc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,mc.audiences)
      }
      case _ => MeTLDirtyImage.empty
    }
  })
  override def fromMeTLDirtyImage(input:MeTLDirtyImage):JValue = Stopwatch.time("JsonSerializer.fromMeTLDirtyImage",{
    toJsObj("dirtyImage",parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLDirtyText(i:JValue):MeTLDirtyText = Stopwatch.time("JsonSerializer.toMeTLDirtyText",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        MeTLDirtyText(config,mc.author,mc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,mc.audiences)
      }
      case _ => MeTLDirtyText.empty
    }
  })
  override def fromMeTLDirtyText(input:MeTLDirtyText):JValue = Stopwatch.time("JsonSerializer.fromMeTLDirtyText",{
    toJsObj("dirtyText",parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLCommand(i:JValue):MeTLCommand = Stopwatch.time("JsonSerializer.toMeTLCommand",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val command = getStringByName(input,"command")
        val parameters = getListOfStringsByName(input,"parameters")
        MeTLCommand(config,mc.author,mc.timestamp,command,parameters,mc.audiences)
      }
      case _ => MeTLCommand.empty
    }
  })
  override def fromMeTLCommand(input:MeTLCommand):JValue = Stopwatch.time("JsonSerializer.fromMeTLCommand",{
    toJsObj("command",List(
      JField("command",JString(input.command)),
      JField("parameters",JArray(input.commandParameters.map(cp => JString(cp))))
    ) ::: parseMeTLContent(input))
  })
  override def toSubmission(i:JValue):MeTLSubmission = Stopwatch.time("JsonSerializer.toSubmission",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val cc = parseJObjForCanvasContent(input)
        val slide = getStringByName(input,"slide")
        val url = getStringByName(input,"url")
        val title = getStringByName(input,"title")
        val blacklistObjs = getListOfObjectsByName(input,"blacklist")
        val blacklist = blacklistObjs.map(blo => {
          val username = getStringByName(blo,"username")
          val highlight = toColor(getColorByName(blo,"highlight"))
          SubmissionBlacklistedPerson(username,highlight)
        }).toList
        MeTLSubmission(config,mc.author,mc.timestamp,title,slide.toInt,url,Empty,blacklist,cc.target,cc.privacy,cc.identity,mc.audiences)
      }
      case _ => MeTLSubmission.empty
    }
  })
  override def fromSubmission(input:MeTLSubmission):JValue = Stopwatch.time("JsonSerializer.fromSubmission",{
    toJsObj("submission",List(
      JField("url",JString(input.url)),
      JField("title",JString(input.title)),
      JField("blacklist",JArray(input.blacklist.map(bl => JObject(List(JField("username",JString(bl.username)),JField("highlight",fromColor(bl.highlight).asInstanceOf[JValue]))))))
    ) ::: parseMeTLContent(input) ::: parseCanvasContent(input))
  })
  override def toMeTLQuiz(i:JValue):MeTLQuiz = Stopwatch.time("JsonSerializer.toMeTLQuiz",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val created = getLongByName(input,"created")
        val question = getStringByName(input,"question")
        val id = getStringByName(input,"id")
        val isDeleted = getBooleanByName(input,"isDeleted")
        val options = getListOfObjectsByName(input,"options").map(o => toQuizOption(o))
        val url = tryo(getStringByName(input,"url"))
        val quizImage = url.map(u =>config.getResource(u))
        MeTLQuiz(config,mc.author,mc.timestamp,created,question,id,url,quizImage,isDeleted,options,mc.audiences)
      }
      case _ => MeTLQuiz.empty
    }
  })
  override def fromMeTLQuiz(input:MeTLQuiz):JValue = Stopwatch.time("JsonSerializer.fromMeTLQuiz",{
    toJsObj("quiz",List(
      JField("created",JInt(input.created)),
      JField("question",JString(input.question)),
      JField("id",JString(input.id)),
      JField("isDeleted",JBool(input.isDeleted)),
      JField("options",JArray(input.options.map(o => fromQuizOption(o))))
    ) ::: input.url.map(u => List(JField("url",JString(u)))).openOr(List.empty[JField]) ::: parseMeTLContent(input))
  })
  def toQuizOption(i:JValue):QuizOption = Stopwatch.time("JsonSerializer.toQuizOption",{
    i match {
      case input:JObject => {
        val name = getStringByName(input,"name")
        val text = getStringByName(input,"text")
        val correct = getBooleanByName(input,"correct")
        val color = toColor(getColorByName(input,"color"))
        QuizOption(name,text,correct,color)
      }
      case _ => QuizOption.empty
    }
  })
  def fromQuizOption(input:QuizOption):JValue = Stopwatch.time("JsonSerializer.fromQuizOption",{
    toJsObj("quizOption",List(
      JField("name",JString(input.name)),
      JField("text",JString(input.text)),
      JField("correct",JBool(input.correct)),
      JField("color",fromColor(input.color).asInstanceOf[JValue])
    ))
  })
  override def toMeTLQuizResponse(i:JValue):MeTLQuizResponse = Stopwatch.time("JsonSerializer.toMeTLQuizResponse",{
    i match {
      case input:JObject => {
        val mc = parseJObjForMeTLContent(input,config)
        val answer = getStringByName(input,"answer")
        val answerer = getStringByName(input,"answerer")
        val id = getStringByName(input,"id")
        MeTLQuizResponse(config,mc.author,mc.timestamp,answer,answerer,id,mc.audiences)
      }
      case _ => MeTLQuizResponse.empty
    }
  })
  override def fromMeTLQuizResponse(input:MeTLQuizResponse):JValue = Stopwatch.time("JsonSerializer.fromMeTLQuizResponse",{
    toJsObj("quizResponse",List(
      JField("answer",JString(input.answer)),
      JField("answerer",JString(input.answerer)),
      JField("id",JString(input.id))
    ) ::: parseMeTLContent(input))
  })
  override def toMeTLUndeletedCanvasContent(input:JValue):MeTLUndeletedCanvasContent = Stopwatch.time("JsonSerializer.toMeTLUndeletedCanvasContent",{
    input match {
      case j:JObject => {
        val mc = parseJObjForMeTLContent(j,config)
        val cc = parseJObjForCanvasContent(j)
        val oldElementIdentity = getStringByName(j,"oldIdentity")
        val newElementIdentity = getStringByName(j,"newIdentity")
        val elementType = getStringByName(j,"elementType")
        MeTLUndeletedCanvasContent(config,mc.author,mc.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,elementType,oldElementIdentity,newElementIdentity,mc.audiences)
      }
      case _ => MeTLUndeletedCanvasContent.empty
    }
  })
  override def fromMeTLUndeletedCanvasContent(input:MeTLUndeletedCanvasContent):JValue = Stopwatch.time("JsonSerializer.fromMeTLUndeletedCanvasContent",{
    toJsObj("undeletedCanvasContent",List(
      JField("oldIdentity",JString(input.oldElementIdentity)),
      JField("newIdentity",JString(input.newElementIdentity)),
      JField("elementType",JString(input.elementType))
    ) ::: parseMeTLContent(input) ::: parseCanvasContent(input))
  })

  protected val dateFormat = new java.text.SimpleDateFormat("EEE MMM dd kk:mm:ss z yyyy") // this is the standard java format, which is what we've been using.
  override def toConversation(i:JValue):Conversation = Stopwatch.time("JsonSerializer.toConversation",{
    i match {
      case input:JObject => {
        val author = getStringByName(input,"author")
        val lastAccessed = getLongByName(input,"lastAccessed")
        val slides = getListOfObjectsByName(input,"slides").map(s => toSlide(s)).toList
        val subject = getStringByName(input,"subject")
        val tag = getStringByName(input,"tag")
        val jid = getIntByName(input,"jid")
        val title = getStringByName(input,"title")
        val created = try {
          getLongByName(input,"creation")
        } catch {
          case e:Exception => {
            dateFormat.parse(getStringByName(input,"created")).getTime
          }
        }
        val permissions = toPermissions(getObjectByName(input,"permissions"))
        val blacklist = getListOfStringsByName(input,"blacklist")
        val audiences = parseJObjForAudiences(input,config)
        val thisConfig = getStringByName(input,"configName") match {
          case "" => config
          case other => ServerConfiguration.configForName(other)
        }
        val foreignRelationship = getOptionalObjectByName(input,"foreignRelationship").flatMap(n => {
          n.value match {
            case jo:JObject => {
              for {
                sys <- getOptionalStringByName(jo,"system")
                key <- getOptionalStringByName(jo,"key")
                displayName = getOptionalStringByName(jo,"displayName")
              } yield {
                ForeignRelationship(sys,key,displayName)
              }
            }
            case _ => None
          }
        })
        Conversation(thisConfig,author,lastAccessed,slides,subject,tag,jid,title,created,permissions,blacklist,audiences,foreignRelationship)
      }
      case _ => Conversation.empty
    }
  })
  override def fromConversationList(input:List[Conversation]):JValue = Stopwatch.time("JsonSerializer.fromConversationList",JArray(input.map(c => fromConversation(c))))
  override def fromConversation(input:Conversation):JValue = Stopwatch.time("JsonSerializer.fromConversation",{
    JObject(List(
      JField("author",JString(input.author)),
      JField("lastAccessed",JInt(input.lastAccessed)),
      JField("slides",JArray(input.slides.map(s => fromSlide(s)).toList)),
      JField("subject",JString(input.subject)),
      JField("tag",JString(input.tag)),
      JField("jid",JInt(input.jid)),
      JField("title",JString(input.title)),
      JField("created",JString(new java.util.Date(input.created).toString())),
      JField("creation",JInt(input.created)),
      JField("permissions",fromPermissions(input.permissions)),
      JField("blacklist",JArray(input.blackList.map(bli => JString(bli)).toList)),
      JField("configName",JString(input.server.name))
    ) ::: input.foreignRelationship.toList.map(fr => {
      JField("foreignRelationship",JObject(List(
        JField("system",JString(fr.system)),
        JField("key",JString(fr.key))
      ) ::: fr.displayName.toList.map(dn => JField("displayName",JString(dn)))))
    }) ::: parseAudiences(input))
  })
  override def toSlide(i:JValue):Slide = Stopwatch.time("JsonSerializer.toSlide",{
    i match {
      case input:JObject => {
        val author = getStringByName(input,"author")
        val id = getIntByName(input,"id")
        val index = getIntByName(input,"index")
        val defaultHeight = getIntByName(input,"defaultHeight")
        val defaultWidth = getIntByName(input,"defaultWidth")
        val exposed = getBooleanByName(input,"exposed")
        val slideType = getStringByName(input,"slideType")
        val groupSet = getListOfObjectsByName(input,"groupSets").map(gs => toGroupSet(gs))
        Slide(config,author,id,index,defaultHeight,defaultWidth,exposed,slideType,groupSet)
      }
      case _ => Slide.empty
    }
  })
  override def fromSlide(input:Slide):JValue = Stopwatch.time("JsonSerializer.fromSlide",{
    JObject(List(
      JField("id",JInt(input.id)),
      JField("author",JString(input.author)),
      JField("index",JInt(input.index)),
      JField("defaultHeight",JInt(input.defaultHeight)),
      JField("defaultWidth",JInt(input.defaultWidth)),
      JField("exposed",JBool(input.exposed)),
      JField("slideType",JString(input.slideType)),
      JField("groupSets",JArray(input.groupSet.map(fromGroupSet _)))))
  })
  override def toGroupSet(i:JValue):GroupSet = Stopwatch.time("JsonSerializer.toGroupSet",{
    i match {
      case input:JObject => {
        val audiences = parseJObjForAudiences(input)
        val id = getStringByName(input,"id")
        val location = getStringByName(input,"location")
        val groupingStrategy = toGroupingStrategy((input \ "groupingStrategy").extract[JObject])
        val groups = getListOfObjectsByName(input,"groups").map(gn => toGroup(gn))
        GroupSet(config,id,location,groupingStrategy,groups,audiences)
      }
      case _ => GroupSet.empty
    }
  })
  override def fromGroupSet(input:GroupSet):JValue = Stopwatch.time("JsonSerializer.fromGroupSet",{
    toJsObj("groupSet",List(
      JField("id",JString(input.id)),
      JField("location",JString(input.location)),
      JField("groupingStrategy",fromGroupingStrategy(input.groupingStrategy)),
      JField("groups",JArray(input.groups.map(g => fromGroup(g)).toList))
    ) ::: parseAudiences(input))
  })

  override def toGroupingStrategy(i:JValue):GroupingStrategy = Stopwatch.time("JsonSerializer.toGroupingStrategy",{
    i match {
      case input:JObject => {
        getStringByName(input,"name") match {
          case "byMaximumSize" => ByMaximumSize(getIntByName(input,"groupSize"))
          case "byTotalGroups" => ByTotalGroups(getIntByName(input,"groupCount"))
          case "onePersonPerGroup" => OnePersonPerGroup
          case "everyoneInOneGroup" => EveryoneInOneGroup
          case "complexGroupingStrategy" => ComplexGroupingStrategy(Map("json" -> (input \ "data").extract[JObject].toString)) // let's make this actually read the JFields of the JObject at input \ data and put them recursively into a Map.
          case _ => EveryoneInOneGroup
        }
      }
      case _ => EveryoneInOneGroup
    }
  })
  override def fromGroupingStrategy(input:GroupingStrategy):JValue = Stopwatch.time("JsonSerializer.fromGroupingStrategy",{
    val contents = input match {
      case ByMaximumSize(groupSize) => List(JField("name",JString("byMaximumSize")),JField("groupSize",JInt(groupSize)))
      case ByTotalGroups(groupCount) => List(JField("name",JString("byTotalGroups")),JField("groupCount",JInt(groupCount)))
      case OnePersonPerGroup => List(JField("name",JString("onePersonPerGroup")))
      case EveryoneInOneGroup => List(JField("name",JString("everyoneInOneGroup")))
      case ComplexGroupingStrategy(data) => List(JField("name",JString("complexGroupingStrategy")),JField("data",JString(data.toString))) // let's serialize this more strongly into a recursive JObject
      case _ => List(JField("name",JString("unknown")))
    }
    toJsObj("groupingStrategy",contents)
  })

  override def toGroup(i:JValue):Group = Stopwatch.time("JsonSerializer.toGroup",{
    i match {
      case input:JObject => {
        val audiences = parseJObjForAudiences(input,config)
        val id = getStringByName(input,"id")
        val location = getStringByName(input,"location")
        val timestamp = getLongByName(input,"timestamp")
        val members = getListOfStringsByName(input,"members")
        Group(config,id,location,timestamp,members,audiences)
      }
      case _ => Group.empty
    }
  })
  override def fromGroup(input:Group):JValue = Stopwatch.time("JsonSerializer.fromGroup",{
    toJsObj("group",List(
      JField("id",JString(input.id)),
      JField("location",JString(input.location)),
      JField("timestamp",JInt(input.timestamp)),
      JField("members",JArray(input.members.map(m => JString(m))))
    ) ::: parseAudiences(input))
  })
  override def toPermissions(i:JValue):Permissions = Stopwatch.time("JsonSerializer.toPermissions", {
    i match {
      case input:JObject => {
        val studentsCanOpenFriends = getBooleanByName(input,"studentCanOpenFriends")
        val studentsCanPublish = getBooleanByName(input,"studentCanPublish")
        val usersAreCompulsorilySynced = getBooleanByName(input,"usersAreCompulsorilySynced")
        val studentsMayBroadcast = getOptionalBooleanByName(input,"studentsMayBroadcast").getOrElse(false)
        val studentsMayChatPublicly = getOptionalBooleanByName(input,"studentsMayChatPublicly").getOrElse(true)
        Permissions(config,studentsCanOpenFriends,studentsCanPublish,usersAreCompulsorilySynced,studentsMayBroadcast,studentsMayChatPublicly)
      }
      case _ => Permissions.default(config)
    }
  })
  override def fromPermissions(input:Permissions):JValue = Stopwatch.time("JsonSerializer.fromPermissions",{
    JObject(List(
      JField("studentCanOpenFriends",JBool(input.studentsCanOpenFriends)),
      JField("studentCanPublish",JBool(input.studentsCanPublish)),
      JField("usersAreCompulsorilySynced",JBool(input.usersAreCompulsorilySynced)),
      JField("studentsMayBroadcast",JBool(input.studentsMayBroadcast)),
      JField("studentsMayChatPublicly",JBool(input.studentsMayChatPublicly))
    ))
  })
  protected def convert2AfterN(h:String,n:Int):Int = hexToInt(h.drop(n).take(2).mkString)
  protected def hexToInt(h:String):Int = tryo(Integer.parseInt(h,16)).openOr(0)
  override def toColor(input:AnyRef):Color = Stopwatch.time("JsonSerializer.toColor", {
    input match {
      case List(c,a) => {
        val color = c.asInstanceOf[String]
        val alpha = ConversionHelper.toDouble(a).toInt
        def clamp (n:Integer,min:Integer=0,max:Integer=255) = Math.max(min,Math.min(max,n))
        val r = convert2AfterN(color,1)
        val g = convert2AfterN(color,3)
        val b = convert2AfterN(color,5)
        Color(alpha,clamp(r),clamp(g),clamp(b))
      }
      case _ => Color.empty
    }
  })
  override def fromColor(input:Color):AnyRef = Stopwatch.time("JsonSerializer.fromColor",{
    JArray(List(JString("#%02x%02x%02x".format(input.red,input.green,input.blue)),JInt(input.alpha)))
  })
  override def toPointList(input:AnyRef):List[Point] = Stopwatch.time("JsonSerializer.toPointList",{
    input match {
      case l:List[Any] if (l.length >= 3) => {
        toPoint(l.take(3)) :: toPointList(l.drop(3))
      }
      case _ => List.empty[Point]
    }
  })
  override def fromPointList(input:List[Point]):AnyRef = Stopwatch.time("JsonSerializer.fromPointList",{
    JArray(input.map(p => fromPoint(p).asInstanceOf[List[JValue]]).flatten)
  })
  override def toPoint(input:AnyRef):Point = Stopwatch.time("JsonSerializer.toPoint",{
    input match {
      case l:List[Any] if (l.length == 3) => {
        val x = ConversionHelper.toDouble(l(0))
        val y = ConversionHelper.toDouble(l(1))
        val thickness = ConversionHelper.toDouble(l(2))
        Point(x,y,thickness)
      }
      case _ => Point.empty
    }
  })
  override def fromPoint(input:Point):AnyRef = Stopwatch.time("JsonSerializer.fromPoint",{
    List(
      JDouble(input.x),
      JDouble(input.y),
      JDouble(input.thickness)
    )
  })
  override def toGrade(input:JValue):MeTLGrade = Stopwatch.time("JsonSerializer.toGrade",{
    input match {
      case j:JObject => {
        val m = parseJObjForMeTLContent(j,config)
        val id = getStringByName(j,"id")
        val name = getStringByName(j,"name")
        val description = getStringByName(j,"description")
        val location = getStringByName(j,"location")
        val visible = getBooleanByName(j,"visible")
        val gradeType = MeTLGradeValueType.parse(getStringByName(j,"gradeType"))
        val numericMaximum = if (gradeType == MeTLGradeValueType.Numeric){
          Some(getDoubleByName(j,"numericMaximum"))
        } else {
          None
        }
        val numericMinimum = if (gradeType == MeTLGradeValueType.Numeric){
          Some(getDoubleByName(j,"numericMinimum"))
        } else {
          None
        }
        val foreignRelationship = getOptionalObjectByName(j,"foreignRelationship").flatMap(n => {
          n.value match {
            case jo:JObject => {
              for {
                sys <- getOptionalStringByName(jo,"sys")
                key <- getOptionalStringByName(jo,"key")
              } yield {
                (sys,key)
              }
            }
            case _ => None
          }
        })
        val gradeReferenceUrl = getOptionalStringByName(j,"gradeReferenceUrl")
        MeTLGrade(config,m.author,m.timestamp,id,location,name,description,gradeType,visible,foreignRelationship,gradeReferenceUrl,numericMaximum,numericMinimum,m.audiences)
      }
      case _ => MeTLGrade.empty
    }
  })
  override def fromGrade(input:MeTLGrade):JValue = Stopwatch.time("JsonSerializer.fromGrade",{
    toJsObj("grade",List(
      JField("id",JString(input.id)),
      JField("name",JString(input.name)),
      JField("description",JString(input.description)),
      JField("visible",JBool(input.visible)),
      JField("location",JString(input.location)),
      JField("gradeType",JString(MeTLGradeValueType.print(input.gradeType)))
    ) ::: input.gradeReferenceUrl.toList.map(gru => {
      JField("gradeReferenceUrl",JString(gru))
    }) ::: input.foreignRelationship.toList.map(frs => {
      JField("foreignRelationship",JObject(List(
        JField("sys",JString(frs._1)),
        JField("key",JString(frs._2))
      )))
    }) ::: input.numericMaximum.toList.map(nm => {
      JField("numericMaximum",JDouble(nm))
    }) ::: input.numericMinimum.toList.map(nm => {
      JField("numericMinimum",JDouble(nm))
    }) :::parseMeTLContent(input))
  })
  override def toNumericGradeValue(input:JValue):MeTLNumericGradeValue = Stopwatch.time("JsonSerializer.toNumericGradeValue",{
    input match {
      case j:JObject => {
        val m = parseJObjForMeTLContent(j,config)
        val gradeId = getStringByName(j,"gradeId")
        val gradedUser = getStringByName(j,"gradedUser")
        val gradeValue = getDoubleByName(j,"gradeValue")
        val gradeComment = getOptionalStringByName(j,"gradeComment")
        val gradePrivateComment = getOptionalStringByName(j,"gradePrivateComment")
        MeTLNumericGradeValue(config,m.author,m.timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,m.audiences)
      }
      case _ => MeTLNumericGradeValue.empty
    }
  })
  override def fromNumericGradeValue(input:MeTLNumericGradeValue):JValue = Stopwatch.time("JsonSerializer.fromNumericGradeValue",{
    toJsObj("numericGradeValue",List(
      JField("gradeId",JString(input.gradeId)),
      JField("gradedUser",JString(input.gradedUser)),
      JField("gradeValue",JDouble(input.gradeValue))
    ) ::: input.gradeComment.toList.map(s => {
      JField("gradeComment",JString(s))
    }) ::: input.gradePrivateComment.toList.map(s => {
      JField("gradePrivateComment",JString(s))
    }) ::: parseMeTLContent(input))
  })
  override def toBooleanGradeValue(input:JValue):MeTLBooleanGradeValue = Stopwatch.time("JsonSerializer.toBooleanGradeValue",{
    input match {
      case j:JObject => {
        val m = parseJObjForMeTLContent(j,config)
        val gradeId = getStringByName(j,"gradeId")
        val gradedUser = getStringByName(j,"gradedUser")
        val gradeValue = getBooleanByName(j,"gradeValue")
        val gradeComment = getOptionalStringByName(j,"gradeComment")
        val gradePrivateComment = getOptionalStringByName(j,"gradePrivateComment")
        MeTLBooleanGradeValue(config,m.author,m.timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,m.audiences)
      }
      case _ => MeTLBooleanGradeValue.empty
    }
  })
  override def fromBooleanGradeValue(input:MeTLBooleanGradeValue):JValue = Stopwatch.time("JsonSerializer.fromBooleanGradeValue",{
    toJsObj("booleanGradeValue",List(
      JField("gradeId",JString(input.gradeId)),
      JField("gradedUser",JString(input.gradedUser)),
      JField("gradeValue",JBool(input.gradeValue))
    ) ::: input.gradeComment.toList.map(s => {
      JField("gradeComment",JString(s))
    }) ::: input.gradePrivateComment.toList.map(s => {
      JField("gradePrivateComment",JString(s))
    }) ::: parseMeTLContent(input))
  })
  override def toTextGradeValue(input:JValue):MeTLTextGradeValue = Stopwatch.time("JsonSerializer.toTextGradeValue",{
    input match {
      case j:JObject => {
        val m = parseJObjForMeTLContent(j,config)
        val gradeId = getStringByName(j,"gradeId")
        val gradedUser = getStringByName(j,"gradedUser")
        val gradeValue = getStringByName(j,"gradeValue")
        val gradeComment = getOptionalStringByName(j,"gradeComment")
        val gradePrivateComment = getOptionalStringByName(j,"gradePrivateComment")
        MeTLTextGradeValue(config,m.author,m.timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,m.audiences)
      }
      case _ => MeTLTextGradeValue.empty
    }
  })
  override def fromTextGradeValue(input:MeTLTextGradeValue):JValue = Stopwatch.time("JsonSerializer.fromTextGradeValue",{
    toJsObj("textGradeValue",List(
      JField("gradeId",JString(input.gradeId)),
      JField("gradedUser",JString(input.gradedUser)),
      JField("gradeValue",JString(input.gradeValue))
    ) ::: input.gradeComment.toList.map(s => {
      JField("gradeComment",JString(s))
    }) ::: input.gradePrivateComment.toList.map(s => {
      JField("gradePrivateComment",JString(s))
    }) ::: parseMeTLContent(input))
  })
}
