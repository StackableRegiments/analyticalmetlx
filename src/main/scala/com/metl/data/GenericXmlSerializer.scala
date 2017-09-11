package com.metl.data

import java.time.ZoneId

import com.metl.utils._
import com.metl.model._

import scala.xml._
import net.liftweb.common._
import net.liftweb.util.Helpers._
import Privacy._
import com.metl.external.ForeignRelationship

trait XmlUtils {
  def getPrivacyByName(content:NodeSeq,name:String):Privacy = tryo(Privacy.parse((content \\ name).text)).openOr(Privacy.PUBLIC)
  def getColorByName(content:NodeSeq,name:String):Color = tryo(ColorConverter.fromText(getValueOfNode(content,name))).openOr(Color.default)
  def getStringByName(content:NodeSeq,name:String):String = tryo(getValueOfNode(content,name)).openOr("unknown "+name)
  def getBooleanByName(content:NodeSeq,name:String):Boolean = tryo(getValueOfNode(content,name).toBoolean).openOr(false)
  def getDoubleByName(content:NodeSeq,name:String):Double = tryo(getValueOfNode(content,name).toDouble).openOr(-1.0)
  def getLongByName(content:NodeSeq,name:String):Long = tryo(getValueOfNode(content,name).toLong).openOr(-1L)
  def getIntByName(content:NodeSeq,name:String):Int = tryo(getValueOfNode(content,name).toInt).openOr(-1)
  def getListOfStringsByNameWithin(content:NodeSeq,name:String,containerName:String) = tryo((getXmlByName(content,containerName) \\ name).map(i => i.head.text).toList).openOr(List.empty[String])
  def getValueOfNode(content:NodeSeq,nodeName:String):String = tryo((content \\ nodeName).head.text).openOr("")
  def getXmlByName(content:NodeSeq,name:String):NodeSeq = tryo((content \\ name)).openOr(NodeSeq.Empty)
  def getAttributeOfNode(content:NodeSeq,nodeName:String,attributeName:String):String = tryo((content \\ nodeName).seq(0).attribute(attributeName).getOrElse(NodeSeq.Empty).text).openOr("")
  def parseCanvasContent(i:NodeSeq):ParsedCanvasContent = {
    val target = getStringByName(i,"target")
    val privacy = getPrivacyByName(i,"privacy")
    val slide = getStringByName(i,"slide")
    val identity = getStringByName(i,"identity")
    ParsedCanvasContent(target,privacy,slide,identity)
  }

  def parsedCanvasContentToXml(p:ParsedCanvasContent):Seq[Node] = {
    <target>{p.target}</target>
    <privacy>{p.privacy.toString.toLowerCase}</privacy>
    <slide>{p.slide}</slide>
    <identity>{p.identity}</identity>
  }
  def parseMeTLContent(i:NodeSeq,config:ServerConfiguration = ServerConfiguration.empty):ParsedMeTLContent = {
    val author = getStringByName(i,"author")
    val timestamp = {
      val failed = -1L
      tryo(getAttributeOfNode(i,"message","time").toLong).openOr({
        getLongByName(getXmlByName(i,"metlMetaData"),"timestamp") match {
          case l:Long if l == failed => tryo(getAttributeOfNode(i,"message","timestamp").toLong).openOr(failed)
          case l:Long => l
            //case _ => failed
        }
      })
    }
    val audiences = tryo(((i \\ "audiences") \\ "audience").flatMap(an => {
      for (
        domain <- (an \ "@domain").headOption;
        name <- (an \ "@name").headOption;
        audienceType <- (an \ "@type").headOption;
        action <- (an \ "@action").headOption
      ) yield {
        Audience(config,domain.text,name.text,audienceType.text,action.text)
      }
    })).openOr(List.empty[Audience]) //this is where I've got to parse it out.
                                     //val timestamp = tryo(getAttributeOfNode(i,"message","timestamp").toLong).openOr(-1L)
                                     //val timestamp = getLongByName(getXmlByName(i,"metlMetaData"),"timestamp")
      ParsedMeTLContent(author,timestamp,audiences.toList)
  }
  def parsedMeTLContentToXml(p:ParsedMeTLContent):Seq[Node] = {
    <author>{p.author}</author>
    <audiences>{p.audiences.map(a => {
      <audience domain={a.domain} name={a.name} type={a.audienceType} action={a.action}/>
    })}</audiences>
  }
  def hasChild(in:NodeSeq,tagName:String):Boolean = (in \ tagName).length > 0
  def hasSubChild(in:NodeSeq,tagName:String):Boolean = (in \\ tagName).length > 0
}
case class ParsedMeTLContent(author:String,timestamp:Long,audiences:List[Audience])
case class ParsedCanvasContent(target:String,privacy:Privacy,slide:String,identity:String)

class GenericXmlSerializer(config:ServerConfiguration) extends Serializer with XmlUtils with Logger {
  type T = NodeSeq
  val configName = config.name

  override def toMeTLData(input:NodeSeq):MeTLData = Stopwatch.time("GenericXmlSerializer.toMeTLStanza",{
    input match {
      case i:NodeSeq if hasChild(i,"ink") => toMeTLInk(i)
      case i:NodeSeq if hasChild(i,"textbox") => toMeTLText(i)
      case i:NodeSeq if hasChild(i,"image") => toMeTLImage(i)
      case i:NodeSeq if hasChild(i,"video") => toMeTLVideo(i)
      case i:NodeSeq if hasChild(i,"dirtyInk") => toMeTLDirtyInk(i)
      case i:NodeSeq if hasChild(i,"dirtyText") => toMeTLDirtyText(i)
      case i:NodeSeq if hasChild(i,"dirtyImage") => toMeTLDirtyImage(i)
      case i:NodeSeq if hasChild(i,"dirtyVideo") => toMeTLDirtyVideo(i)
      case i:NodeSeq if hasChild(i,"moveDelta") => toMeTLMoveDelta(i)
      case i:NodeSeq if hasChild(i,"quiz") => toMeTLQuiz(i)
      case i:NodeSeq if hasChild(i,"quizResponse") => toMeTLQuizResponse(i)
      case i:NodeSeq if hasChild(i,"screenshotSubmission") => toSubmission(i)
      case i:NodeSeq if hasChild(i,"attendance") => toMeTLAttendance(i)
      //      case i:NodeSeq if hasChild(i,"body") => toMeTLCommand(i)
      case i:NodeSeq if hasChild(i,"command") => toMeTLCommand(i)
      case i:NodeSeq if hasChild(i,"fileResource") => toMeTLFile(i)
      case i:NodeSeq if hasChild(i,"videoStream") => toMeTLVideoStream(i)
      case i:NodeSeq if hasChild(i,"theme") => toTheme(i)
      case i:NodeSeq if hasChild(i,"grade") => toGrade(i)
      case i:NodeSeq if hasChild(i,"numericGradeValue") => toNumericGradeValue(i)
      case i:NodeSeq if hasChild(i,"booleanGradeValue") => toBooleanGradeValue(i)
      case i:NodeSeq if hasChild(i,"textGradeValue") => toTextGradeValue(i)
      case i:NodeSeq if hasChild(i,"chatMessage") => toChatMessage(i)
      case i:NodeSeq if hasChild(i,"undeletedCanvasContent") => toMeTLUndeletedCanvasContent(i)
      case i:NodeSeq if hasSubChild(i,"target") && hasSubChild(i,"privacy") && hasSubChild(i,"slide") && hasSubChild(i,"identity") => toMeTLUnhandledCanvasContent(i)
      case i:NodeSeq if (((i \\ "author").length > 0) && ((i \\ "message").length > 0)) => toMeTLUnhandledStanza(i)
      case other:NodeSeq => toMeTLUnhandledData(other)
    }
  })
  protected def metlXmlToXml(rootName:String,additionalNodes:Seq[Node],wrapWithMessage:Boolean = false,additionalAttributes:List[(String,String)] = List.empty[(String,String)]) = Stopwatch.time("GenericXmlSerializer.metlXmlToXml", {
    val attrs = additionalAttributes.foldLeft(scala.xml.Null.asInstanceOf[scala.xml.MetaData])((acc,item) => {
      item match {
        case (k:String,v:String) => new UnprefixedAttribute(k,v,acc)
        case _ => acc
      }
    })
    wrapWithMessage match {
      case true => {
        new Elem(null, "message", attrs, TopScope, false, new Elem(null, rootName, Null, TopScope, false, additionalNodes: _*))
      }
      case _ => new Elem(null, rootName, attrs, TopScope, false, additionalNodes:_*)
    }
  })
  protected def metlContentToXml(rootName:String,input:MeTLStanza,additionalNodes:Seq[Node]) = Stopwatch.time("GenericXmlSerializer.metlContentToXml", {
    val pmc = parsedMeTLContentToXml(ParsedMeTLContent(input.author,input.timestamp,input.audiences)) ++ additionalNodes
    metlXmlToXml(rootName,pmc,true,List(("timestamp",input.timestamp.toString)))
  })
  protected def canvasContentToXml(rootName:String,input:MeTLCanvasContent,additionalNodes:Seq[Node]) = Stopwatch.time("GenericXmlSerializer.canvasContentToXml", {
    metlContentToXml(rootName,input,parsedCanvasContentToXml(ParsedCanvasContent(input.target,input.privacy,input.slide,input.identity)) ++ additionalNodes)
  })
  override def fromHistory(input:History):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromHistory", {
    <history jid={input.jid}>{input.getAll.map(i => fromMeTLData(i))}<deletedContents>{input.getDeletedCanvasContents.map(dcc => fromMeTLData(dcc))}</deletedContents></history>
  })

  override def toHistory(input:NodeSeq):History = Stopwatch.time("GenericXmlSerializer.toHistory",{
    val history = new History((input \ "@jid").headOption.map(_.text).getOrElse(""))
    input match {
      case e:Elem => e.child.foreach{
        case el:Elem if el.label == "deletedContents" => {
          el.child.foreach{
            case c:NodeSeq => toMeTLData(c) match {
              case ms:MeTLCanvasContent => history.addDeletedCanvasContent(ms)
              case _ => {}
            }
          }
        }
        case c:NodeSeq => toMeTLData(c) match {
          case ms:MeTLStanza => history.addStanza(ms)
          case _ => {}
        }
      }
    }
    history
  })
  protected val xmlType = "xml"
  override def toMeTLUnhandledData(i:NodeSeq) = MeTLUnhandledData(config,i.toString,xmlType)
  override def toMeTLUnhandledStanza(i:NodeSeq) = {
    val m = parseMeTLContent(i,config)
    MeTLUnhandledStanza(config,m.author,m.timestamp,i.toString,xmlType,m.audiences)
  }

  override def toMeTLUnhandledCanvasContent(i:NodeSeq) = {
    val cc = parseCanvasContent(i)
    val m = parseMeTLContent(i,config)
    MeTLUnhandledCanvasContent(config,m.author,m.timestamp,cc.target,cc.privacy,cc.slide,cc.identity,m.audiences,1.0,1.0,i.toString,xmlType)
  }
  override def fromMeTLUnhandledData(i:MeTLUnhandledData) = i.valueType.toLowerCase.trim match {
    case s:String if s == xmlType => XML.loadString(i.unhandled)
    case _ => NodeSeq.Empty
  }
  override def fromMeTLUnhandledStanza(i:MeTLUnhandledStanza) = i.valueType.toLowerCase.trim match {
    case s:String if s == xmlType => XML.loadString(i.unhandled)
    case _ => NodeSeq.Empty
  }
  override def fromMeTLUnhandledCanvasContent(i:MeTLUnhandledCanvasContent) = i.valueType.toLowerCase.trim match {
    case s:String if s == xmlType => XML.loadString(i.unhandled)
    case _ => NodeSeq.Empty
  }
  override def toMeTLUndeletedCanvasContent(input:NodeSeq):MeTLUndeletedCanvasContent = {
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val elementType = getStringByName(input,"elementType")
    val oldIdentity = getStringByName(input,"oldIdentity")
    val newIdentity = getStringByName(input,"newIdentity")
    MeTLUndeletedCanvasContent(config,m.author,m.timestamp,c.target,c.privacy,c.slide,c.identity,elementType,oldIdentity,newIdentity,m.audiences)
  }
  override def fromMeTLUndeletedCanvasContent(input:MeTLUndeletedCanvasContent):NodeSeq = {
    canvasContentToXml("undeletedCanvasContent",input,Seq(
      <elementType>{input.elementType}</elementType>,
      <oldIdentity>{input.oldElementIdentity}</oldIdentity>,
      <newIdentity>{input.newElementIdentity}</newIdentity>
    ))
  }
  override def toMeTLMoveDelta(input:NodeSeq):MeTLMoveDelta = Stopwatch.time("GenericXmlSerializer.toMeTLMoveDelta", {
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val inkIds = getListOfStringsByNameWithin(input,"inkId","inkIds")
    val textIds = getListOfStringsByNameWithin(input,"textId","textIds")
    val multiWordTextIds = getListOfStringsByNameWithin(input,"multiWordTextId","multiWordTextIds")
    val imageIds = getListOfStringsByNameWithin(input,"imageId","imageIds")
    val videoIds = getListOfStringsByNameWithin(input,"videoId","videoIds")
    val xTranslate = getDoubleByName(input,"xTranslate")
    val yTranslate = getDoubleByName(input,"yTranslate")
    val xScale = getDoubleByName(input,"xScale")
    val yScale = getDoubleByName(input,"yScale")
    val newPrivacy = getPrivacyByName(input,"newPrivacy")
    val isDeleted = getBooleanByName(input,"isDeleted")
    val xOrigin = getDoubleByName(input,"xOrigin")
    val yOrigin = getDoubleByName(input,"yOrigin")
    MeTLMoveDelta(config,m.author,m.timestamp,c.target,c.privacy,c.slide,c.identity,xOrigin,yOrigin,inkIds,textIds,multiWordTextIds,imageIds,videoIds,xTranslate,yTranslate,xScale,yScale,newPrivacy,isDeleted,m.audiences)
  })
  override def fromMeTLMoveDelta(input:MeTLMoveDelta):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLMoveDelta", {
    canvasContentToXml("moveDelta",input, Seq(
      <inkIds>{input.inkIds.map(i => <inkId>{i}</inkId>)}</inkIds>,
      <imageIds>{input.imageIds.map(i => <imageId>{i}</imageId>)}</imageIds>,
      <textIds>{input.textIds.map(i => <textId>{i}</textId>)}</textIds>,
      <videoIds>{input.videoIds.map(i => <videoId>{i}</videoId>)}</videoIds>,
      <multiWordTextIds>{input.multiWordTextIds.map(i => <multiWordTextId>{i}</multiWordTextId>)}</multiWordTextIds>,
      <xTranslate>{input.xTranslate}</xTranslate>,
      <yTranslate>{input.yTranslate}</yTranslate>,
      <xScale>{input.xScale}</xScale>,
      <yScale>{input.yScale}</yScale>,
      <newPrivacy>{input.newPrivacy}</newPrivacy>,
      <isDeleted>{input.isDeleted}</isDeleted>,
      <xOrigin>{input.xOrigin}</xOrigin>,
      <yOrigin>{input.yOrigin}</yOrigin>
    ))
  })
  override def toMeTLAttendance(input:NodeSeq):Attendance = Stopwatch.time("GenericXmlSerializer.toMeTLAttendance",{
    val m = parseMeTLContent(input,config)
    val location = getStringByName(input,"location")
    val present = getBooleanByName(input,"present")
    Attendance(config,m.author,m.timestamp,location,present,m.audiences)
  })
  override def fromMeTLAttendance(input:Attendance):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLAttenance",{
    metlContentToXml("attendance",input,List(
      <location>{input.location}</location>,
      <present>{input.present}</present>
    ))
  })
  override def fromTheme(t:MeTLTheme):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromTheme",{
    metlContentToXml("theme",t,List(
      <text>{t.theme.text}</text>,
      <origin>{t.theme.origin}</origin>,
      <location>{t.location}</location>
    ))
  })
  override def toTheme(x:NodeSeq):MeTLTheme = Stopwatch.time("GenericXmlSerializer.toTheme",{
    val m = parseMeTLContent(x)
    val text = getStringByName(x,"text")
    val location = getStringByName(x,"location")
    val origin = getStringByName(x,"origin")
    MeTLTheme(config,m.author,m.timestamp,location,Theme(m.author,text,origin),m.audiences)
  })
  override def fromChatMessage(t:MeTLChatMessage):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromChatMessage",{
    metlContentToXml("chatMessage",t,List(
      <context>{t.context}</context>,
      <content>{t.content}</content>,
      <contentType>{t.contentType}</contentType>,
      <identity>{t.identity}</identity>
    ))
  })
  override def toChatMessage(x:NodeSeq):MeTLChatMessage = Stopwatch.time("GenericXmlSerializer.toChatMessage",{
    val m = parseMeTLContent(x)
    val context = getStringByName(x,"context")
    val contentType = getStringByName(x,"contentType")
    val content = getStringByName(x,"content")
    val identity = getStringByName(x,"identity")
    MeTLChatMessage(config,m.author,m.timestamp,identity,contentType,content,context,m.audiences)
  })

  override def toMeTLInk(input:NodeSeq):MeTLInk = Stopwatch.time("GenericXmlSerializer.toMeTLInk",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val checksum = getDoubleByName(input,"checksum")
    val startingSum = getDoubleByName(input,"startingSum")
    val points = tryo(PointConverter.fromText(getStringByName(input,"points"))).openOr(List.empty[Point])
    val color = getColorByName(input,"color")
    val thickness = getDoubleByName(input,"thickness")
    val isHighlighter = getBooleanByName(input,"highlight")
    val identity = c.identity match {
      case "" => startingSum.toString
      case other => other
    }
    MeTLInk(config,m.author,m.timestamp,checksum,startingSum,points,color,thickness,isHighlighter,c.target,c.privacy,c.slide,identity,m.audiences)
  })
  override def fromMeTLInk(input:MeTLInk):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLInk",{
    canvasContentToXml("ink",input,List(
      <checksum>{input.checksum}</checksum>,
      <startingSum>{input.startingSum}</startingSum>,
      <points>{PointConverter.toText(input.points)}</points>,
      <color>{ColorConverter.toRGBAString(input.color)}</color>,
      <thickness>{input.thickness}</thickness>,
      <highlight>{input.isHighlighter}</highlight>
    ))
  })
  override def toMeTLImage(input:NodeSeq):MeTLImage = Stopwatch.time("GenericXmlSerializer.toMeTLImage",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val tag = getStringByName(input,"tag")
    val source = getStringByName(input,"source") match {
      case s:String if (s.length > 0 && s != "unknown url" && s != "none") => Full(s)
      case _ => Empty
    }
    val imageBytes = source.map(u => config.getResource(u))
    val pngBytes = Empty
    val width = getDoubleByName(input,"width")
    val height = getDoubleByName(input,"height")
    val x = getDoubleByName(input,"x")
    val y = getDoubleByName(input,"y")
    MeTLImage(config,m.author,m.timestamp,tag,source,imageBytes,pngBytes,width,height,x,y,c.target,c.privacy,c.slide,c.identity,m.audiences)
  })
  override def fromMeTLImage(input:MeTLImage):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLImage",{
    canvasContentToXml("image",input,List(
      <tag>{input.tag}</tag>,
      <source>{input.source.openOr("unknown")}</source>,
      <width>{input.width}</width>,
      <height>{input.height}</height>,
      <x>{input.x}</x>,
      <y>{input.y}</y>
    ))
  })
  override def toMeTLVideo(input:NodeSeq):MeTLVideo = Stopwatch.time("GenericXmlSerializer.toMeTLVideo",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val source = getStringByName(input,"source") match {
      case s:String if (s.length > 0 && s != "unknown url" && s != "none") => Full(s)
      case _ => Empty
    }
    val videoBytes = source.map(u => config.getResource(u))
    val width = getDoubleByName(input,"width")
    val height = getDoubleByName(input,"height")
    val x = getDoubleByName(input,"x")
    val y = getDoubleByName(input,"y")
    MeTLVideo(config,m.author,m.timestamp,source,videoBytes,width,height,x,y,c.target,c.privacy,c.slide,c.identity,m.audiences)
  })
  override def fromMeTLVideo(input:MeTLVideo):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLVideo",{
    canvasContentToXml("video",input,List(
      <source>{input.source.openOr("unknown")}</source>,
      <width>{input.width}</width>,
      <height>{input.height}</height>,
      <x>{input.x}</x>,
      <y>{input.y}</y>
    ))
  })
  override def fromMeTLWord(input:MeTLTextWord) = <word>
  <text>{input.text}</text>
  <bold>{input.bold}</bold>
  <underline>{input.underline}</underline>
  <italic>{input.italic}</italic>
  <justify>{input.justify}</justify>
  <font>{input.font}</font>
  <size>{input.size}</size>
  <color>
  <alpha>{input.color.alpha}</alpha>
  <red>{input.color.red}</red>
  <green>{input.color.green}</green>
  <blue>{input.color.blue}</blue>
  </color>
  </word>
  def fromAudience(input:Audience) = <audience>
  <domain>{input.domain}</domain>
  <name>{input.name}</name>
  <audienceType>{input.name}</audienceType>
  <action>{input.name}</action>
  </audience>
  override def toMeTLWord(input:NodeSeq) = (for {
    text <- (input \\ "text").headOption.map(_.text)
    bold <- (input \\ "bold").headOption.map(_.text.toBoolean)
    underline <- (input \\ "underline").headOption.map(_.text.toBoolean)
    italic <- (input \\ "italic").headOption.map(_.text.toBoolean)
    justify <- (input \\ "justify").headOption.map(_.text)
    font <- (input \\ "font").headOption.map(_.text)
    size <- (input \\ "size").headOption.map(_.text.toDouble)
    colorNode <- (input \\ "color").headOption
    a <- (colorNode \\ "alpha").headOption.map(_.text.toInt)
    r <- (colorNode \\ "red").headOption.map(_.text.toInt)
    g <- (colorNode \\ "green").headOption.map(_.text.toInt)
    b <- (colorNode \\ "blue").headOption.map(_.text.toInt)
  } yield {
    MeTLTextWord(text,bold,underline,italic,justify,Color(a,r,g,b),font,size)
  }).getOrElse(MeTLTextWord.empty)
  override def toMeTLMultiWordText(input:NodeSeq):MeTLMultiWordText = Stopwatch.time("GenericXmlSerializer.toMeTLMultiWordText",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val width = getDoubleByName(input,"width")
    val requestedWidth = getDoubleByName(input,"requestedWidth")
    val height = getDoubleByName(input,"height")
    val x = getDoubleByName(input,"x")
    val y = getDoubleByName(input,"y")
    val words = (input \\ "words" \\ "word").toList.map(toMeTLWord _)
    val tag = getStringByName(input,"tag")
    MeTLMultiWordText(config,m.author,m.timestamp,height,width,requestedWidth,x,y,tag,c.identity,c.target,c.privacy,c.slide,words,m.audiences)
  })
  override def fromMeTLMultiWordText(input:MeTLMultiWordText) = Stopwatch.time("GenericXmlSerializer.fromMeTLMultiWordText", canvasContentToXml("multiWordText",input,List(
    <x>{input.x}</x>,
    <y>{input.y}</y>,
    <width>{input.width}</width>,
    <height>{input.height}</height>,
    <tag>{input.tag}</tag>,
    <requestedWidth>{input.requestedWidth}</requestedWidth>,
    <words>{input.words.map(fromMeTLWord _)}</words>,
    <audiences>{input.audiences.map(fromAudience _)}</audiences>
  )))
  override def toMeTLText(input:NodeSeq):MeTLText = Stopwatch.time("GenericXmlSerializer.toMeTLText",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val tag = getStringByName(input,"tag")
    val caret = getIntByName(input,"caret")
    val text = getStringByName(input,"text")
    val style = getStringByName(input,"style")
    val family = getStringByName(input,"family")
    val weight = getStringByName(input,"weight")
    val size = getDoubleByName(input,"size")
    val decoration = getStringByName(input,"decoration")
    val color = getColorByName(input,"color")
    val width = getDoubleByName(input,"width")
    val height = getDoubleByName(input,"height")
    val x = getDoubleByName(input,"x")
    val y = getDoubleByName(input,"y")
    MeTLText(config,m.author,m.timestamp,text,height,width,caret,x,y,tag,style,family,weight,size,decoration,c.identity,c.target,c.privacy,c.slide,color,m.audiences)
  })
  override def fromMeTLText(input:MeTLText):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLText",{
    canvasContentToXml("textbox",input,List(
      <tag>{input.tag}</tag>,
      <caret>{input.caret}</caret>,
      <text>{input.text}</text>,
      <style>{input.style}</style>,
      <family>{input.family}</family>,
      <weight>{input.weight}</weight>,
      <size>{input.size}</size>,
      <decoration>{input.decoration}</decoration>,
      <color>{ColorConverter.toHexString(input.color)}</color>,
      <width>{input.width}</width>,
      <height>{input.height}</height>,
      <x>{input.x}</x>,
      <y>{input.y}</y>
    ))
  })
  override def toMeTLDirtyInk(input:NodeSeq):MeTLDirtyInk = Stopwatch.time("GenericXmlSerializer.toMeTLDirtyInk",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    MeTLDirtyInk(config,m.author,m.timestamp,c.target,c.privacy,c.slide,c.identity,m.audiences)
  })
  override def fromMeTLDirtyInk(input:MeTLDirtyInk):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLDirtyInk",{
    canvasContentToXml("dirtyInk",input,NodeSeq.Empty)
  })
  override def toMeTLDirtyImage(input:NodeSeq):MeTLDirtyImage = Stopwatch.time("GenericXmlSerializer.toMeTLDirtyImage",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    MeTLDirtyImage(config,m.author,m.timestamp,c.target,c.privacy,c.slide,c.identity,m.audiences)
  })
  override def fromMeTLDirtyImage(input:MeTLDirtyImage):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLDirtyImage",{
    canvasContentToXml("dirtyImage",input,NodeSeq.Empty)
  })
  override def toMeTLDirtyVideo(input:NodeSeq):MeTLDirtyVideo = Stopwatch.time("GenericXmlSerializer.toMeTLDirtyVideo",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    MeTLDirtyVideo(config,m.author,m.timestamp,c.target,c.privacy,c.slide,c.identity,m.audiences)
  })
  override def fromMeTLDirtyVideo(input:MeTLDirtyVideo):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLDirtyVideo",{
    canvasContentToXml("dirtyVideo",input,NodeSeq.Empty)
  })
  override def toMeTLDirtyText(input:NodeSeq):MeTLDirtyText = Stopwatch.time("GenericXmlSerializer.toMeTLDirtyText",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    MeTLDirtyText(config,m.author,m.timestamp,c.target,c.privacy,c.slide,c.identity,m.audiences)
  })
  override def fromMeTLDirtyText(input:MeTLDirtyText):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLDirtyText",{
    canvasContentToXml("dirtyText",input,NodeSeq.Empty)
  })
  override def toMeTLCommand(input:NodeSeq):MeTLCommand = Stopwatch.time("GenericXmlSerializer.toMeTLCommand",{
    val m = parseMeTLContent(input,config)
    val comm = getStringByName(input,"name")
    val parameters = getListOfStringsByNameWithin(input,"parameter","parameters")
    MeTLCommand(config,m.author,m.timestamp,comm,parameters,m.audiences)
  })
  override def fromMeTLCommand(input:MeTLCommand):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLCommand",{
    metlContentToXml("command",input,List(
      <name>{input.command}</name>,
      <parameters>{input.commandParameters.map(cp => <parameter>{cp}</parameter>)}</parameters>
    ))
  })
  override def toSubmission(input:NodeSeq):MeTLSubmission = Stopwatch.time("GenericXmlSerializer.toSubmission",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val title = getStringByName(input,"title")
    val url = getStringByName(input,"url")
    val imageBytes = Full(config.getResource(url))
    val blacklist = getXmlByName(input,"blacklist").map(bl => {
      val username = getStringByName(bl,"username")
      val highlight = getColorByName(bl,"highlight")
      SubmissionBlacklistedPerson(username,highlight)
    }).toList
    MeTLSubmission(config,m.author,m.timestamp,title,c.slide.toInt,url,imageBytes,blacklist,c.target,c.privacy,c.identity,m.audiences)
  })
  override def fromSubmission(input:MeTLSubmission):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromSubmission",{
    canvasContentToXml("screenshotSubmission",input,List(
      <url>{input.url}</url>,
      <title>{input.title}</title>,
      <time>{input.timestamp.toString}</time>
    ) ::: input.blacklist.map(bl => <blacklist><username>{bl.username}</username><highlight>{ColorConverter.toRGBAString(bl.highlight)}</highlight></blacklist> ).toList)
  })
  override def toMeTLQuiz(input:NodeSeq):MeTLQuiz = Stopwatch.time("GenericXmlSerializer.toMeTLQuiz",{
    val m = parseMeTLContent(input,config)
    val created = getLongByName(input,"created")
    val question = getStringByName(input,"question") match {
      case q if (q.length > 0) => q
      case _ => getStringByName(input,"title")
    }
    val id = getStringByName(input,"id")
    val url = getStringByName(input,"url") match {
      case s:String if (s.length > 0 && s != "unknown url" && s != "none") => Full(s)
      case _ => Empty
    }
    val quizImage = url.map(u => config.getResource(u))
    val isDeleted = getBooleanByName(input,"isDeleted")
    val options = getXmlByName(input,"quizOption").map(qo => toQuizOption(qo)).toList
    MeTLQuiz(config,m.author,m.timestamp,created,question,id,url,quizImage,isDeleted,options,m.audiences)
  })
  override def fromMeTLQuiz(input:MeTLQuiz):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLQuiz",{
    metlContentToXml("quiz",input,List(
      <created>{input.created}</created>,
      <question>{input.question}</question>,
      <id>{input.id}</id>,
      <isDeleted>{input.isDeleted}</isDeleted>,
      <options>{input.options.map(o => fromQuizOption(o))}</options>
    ) ::: input.url.map(u => List(<url>{u}</url>)).openOr(List.empty[Node]))
  })
  override def toMeTLFile(input:NodeSeq):MeTLFile = Stopwatch.time("GenericXmlSerializer.toMeTLFile",{
    val m = parseMeTLContent(input,config)
    val name = getStringByName(input,"name")
    val id = getStringByName(input,"identity")
    val deleted = getBooleanByName(input,"deleted")
    val url = (input \ "url").headOption.map(_.text)
    val bytes = url.map(u => config.getResource(u))
    MeTLFile(config,m.author,m.timestamp,name,id,url,bytes)
  })
  override def fromMeTLFile(input:MeTLFile):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLFile",{
    metlContentToXml("fileResource",input,List(
      <name>{input.name}</name>,
      <identity>{input.id}</identity>,
      <deleted>{input.deleted}</deleted>
    ) :::
      input.url.map(u => List(<url>{u}</url>)).getOrElse(List.empty[Node]))
  })
  override def toMeTLVideoStream(input:NodeSeq):MeTLVideoStream = Stopwatch.time("GenericXmlSerializer.toMeTLVideoStream",{
    val m = parseMeTLContent(input,config)
    val id = getStringByName(input,"identity")
    val deleted = getBooleanByName(input,"deleted")
    val url = (input \ "url").headOption.map(_.text)
    MeTLVideoStream(config,m.author,id,m.timestamp,url,deleted)
  })
  override def fromMeTLVideoStream(input:MeTLVideoStream):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLVideoStream",{
    metlContentToXml("videoStream",input,List(
      <identity>{input.id}</identity>,
      <deleted>{input.isDeleted}</deleted>
    ) :::
      input.url.map(u => List(<url>{u}</url>)).getOrElse(List.empty[Node]))
  })

  def toQuizOption(input:NodeSeq):QuizOption = Stopwatch.time("GenericXmlSerializer.toMeTLQuizOption",{
    val name = getStringByName(input,"name")
    val text = getStringByName(input,"text")
    val correct = getBooleanByName(input,"correct")
    val color = getColorByName(input,"color")
    QuizOption(name,text,correct,color)
  })
  def fromQuizOption(input:QuizOption):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLQuizOption",{
    metlXmlToXml("quizOption",List(
      <name>{input.name}</name>,
      <text>{input.text}</text>,
      <correct>{input.correct}</correct>,
      <color>{input.color}</color>
    ))
  })
  override def toMeTLQuizResponse(input:NodeSeq):MeTLQuizResponse = Stopwatch.time("GenericXmlSerializer.toMeTLQuizResponse", {
    val m = parseMeTLContent(input,config)
    val answer = getStringByName(input,"answer")
    val answerer = getStringByName(input,"answerer")
    val id = getStringByName(input,"id")
    MeTLQuizResponse(config,m.author,m.timestamp,answer,answerer,id,m.audiences)
  })
  override def fromMeTLQuizResponse(input:MeTLQuizResponse):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLQuizResponse",{
    metlContentToXml("quizResponse",input,List(
      <answer>{input.answer}</answer>,
      <answerer>{input.answerer}</answerer>,
      <id>{input.id}</id>
    ))
  })
  protected val usZone: ZoneId = ZoneId.of("America/New_York")
  val dateTimeFormatter = new MultiFormatDateFormatter(
    Left("EEE MMM dd kk:mm:ss z yyyy"),
    Right("MM/dd/yyyy h:mm:ss a", usZone),
    Right("MM/d/yyyy h:mm:ss a", usZone),
    Right("M/dd/yyyy h:mm:ss a", usZone),
    Right("M/d/yyyy h:mm:ss a", usZone),
    Right("MM/dd/yyyy HH:mm:ss", usZone),
    Right("MM/d/yyyy HH:mm:ss", usZone),
    Right("M/dd/yyyy HH:mm:ss", usZone),
    Right("M/d/yyyy HH:mm:ss", usZone),
    Right("dd/MM/yyyy h:mm:ss a", usZone),
    Right("d/MM/yyyy h:mm:ss a", usZone),
    Right("dd/M/yyyy h:mm:ss a", usZone),
    Right("d/M/yyyy h:mm:ss a", usZone),
    Right("dd/MM/yyyy HH:mm:ss a", usZone),
    Right("d/MM/yyyy HH:mm:ss a", usZone),
    Right("dd/M/yyyy HH:mm:ss a", usZone),
    Right("d/M/yyyy HH:mm:ss a", usZone)
  )
  override def toConversation(input:NodeSeq):Conversation = Stopwatch.time("GenericXmlSerializer.toConversation",{
    val m = parseMeTLContent(input,config)
    val author = getStringByName(input,"author")
    val lastAccessed = getLongByName(input,"lastAccessed")
    val slides = getXmlByName(input,"slide").map(s => toSlide(s)).toList
    val subject = getStringByName(input,"subject")
    val tag = getStringByName(input,"tag")
    val jid = getIntByName(input,"jid")
    val title = getStringByName(input,"title")
    val creationString = getStringByName(input,"creation")
    val created = try {
      creationString.toLong
    } catch {
      case e:Exception => {
        dateTimeFormatter.parse(getStringByName(input, "created"))
      }
    }
    val permissions = getXmlByName(input,"permissions").map(p => toPermissions(p)).headOption.getOrElse(Permissions.default(config))
    val blacklist = getXmlByName(input,"blacklist").flatMap(bl => getXmlByName(bl,"user")).map(_.text)
    val thisConfig = getStringByName(input,"configName") match {
      case "unknown configName" => config
      case "" => config
      case other => ServerConfiguration.configForName(other)
    }
    val foreignRelationship = (input \\ "foreignRelationship").headOption.flatMap(n => {
      for {
        sys <- (n \ "@system").headOption.map(_.text)
        key <- (n \ "@key").headOption.map(_.text)
        displayName = (n \ "@displayName").headOption.map(_.text)
      } yield {
        ForeignRelationship(sys,key)
      }
    })
    Conversation(thisConfig,author,lastAccessed,slides,subject,tag,jid,title,created,permissions,blacklist.toList,m.audiences,foreignRelationship)
  })
  override def fromConversation(input:Conversation):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromConversation",{
    metlXmlToXml("conversation",List(
      <author>{input.author}</author>,
      <lastAccessed>{input.lastAccessed}</lastAccessed>,
      <slides>{input.slides.map(s => fromSlide(s))}</slides>,
      <subject>{input.subject}</subject>,
      <tag>{input.tag}</tag>,
      <jid>{input.jid}</jid>,
      <title>{input.title}</title>,
      <created>{new java.util.Date(input.created).toString()}</created>,
      <creation>{input.created}</creation>,
      <blacklist>{
        input.blackList.map(bu => <user>{bu}</user> )
      }</blacklist>,
      //                        <configName>{input.server.name}</configName>,
      fromPermissions(input.permissions)
    ) ::: input.foreignRelationship.toList.map(t => {
      <foreignRelationship system={t.system} key={t.key} displayName={t.displayName.map(dn => Text(dn))} />
    }))
  })
  override def fromConversationList(input:List[Conversation]):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromConversationList",{
    <conversations>{input.map(c => fromConversation(c))}</conversations>
  })
  override def toSlide(input:NodeSeq):Slide = Stopwatch.time("GenericXmlSerializer.toSlide",{
    val m = parseMeTLContent(input,config)
    val author = getStringByName(input,"author")
    val id = getIntByName(input,"id")
    val index = getIntByName(input,"index")
    val defHeight = getIntByName(input,"defaultHeight")
    val defWidth = getIntByName(input,"defaultWidth")
    val exposed = getBooleanByName(input,"exposed")
    val slideType = getStringByName(input,"type")
    val groupSets = (input \ "groupSet").map(gs => toGroupSet(gs)).toList
    Slide(config,author,id,index,defHeight,defWidth,exposed,slideType,groupSets,m.audiences)
  })
  override def fromSlide(input:Slide):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromSlide",{
    metlXmlToXml("slide",List(
      <id>{input.id}</id>,
      <author>{input.author}</author>,
      <index>{input.index}</index>,
      <defaultHeight>{input.defaultHeight}</defaultHeight>,
      <defaultWidth>{input.defaultWidth}</defaultWidth>,
      <exposed>{input.exposed}</exposed>,
      <type>{input.slideType}</type>
    ) ::: List(input.groupSet.map(gs => fromGroupSet(gs))).flatten.flatMap(_.theSeq).toList)
  })
  override def toGroupSet(input:NodeSeq):GroupSet = Stopwatch.time("GenericXmlSerializer.toGroupSet",{
    val m = parseMeTLContent(input,config)
    val id = getStringByName(input,"id")
    val location = getStringByName(input,"location")
    val groupingStrategy = toGroupingStrategy((input \ "groupingStrategy"))
    val groups = ((input \ "groups") \ "group").map(gn => toGroup(gn)).toList
    GroupSet(config,id,location,groupingStrategy,groups,m.audiences)
  })
  override def fromGroupSet(input:GroupSet):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromGroupSet",{
    metlXmlToXml("groupSet",List(
      <id>{input.id}</id>,
      <location>{input.location}</location>,
      fromGroupingStrategy(input.groupingStrategy).head,
      <groups>{input.groups.map(g => fromGroup(g))}</groups>
    ))
  })

  override def toGroupingStrategy(input:NodeSeq):GroupingStrategy = {
    getStringByName(input,"name") match {
      case "byMaximumSize" => ByMaximumSize(getIntByName(input,"groupSize"))
      case "byTotalGroups" => ByTotalGroups(getIntByName(input,"groupCount"))
      case "onePersonPerGroup" => OnePersonPerGroup
      case "everyoneInOneGroup" => EveryoneInOneGroup
      case "complexGroupingStrategy" => ComplexGroupingStrategy(Map("xml" -> input.toString))
      case _ => EveryoneInOneGroup
    }
  }
  override def fromGroupingStrategy(input:GroupingStrategy):NodeSeq = {
    input match {
      case ByMaximumSize(groupSize) => <groupingStrategy><name>byMaximumSize</name><groupSize>{groupSize.toString}</groupSize></groupingStrategy>
      case ByTotalGroups(groupCount) => <groupingStrategy><name>byTotalGroups</name><groupCount>{groupCount.toString}</groupCount></groupingStrategy>
      case OnePersonPerGroup => <groupingStrategy><name>onePersonPerGroup</name></groupingStrategy>
      case EveryoneInOneGroup => <groupingStrategy><name>everyoneInOneGroup</name></groupingStrategy>
      case ComplexGroupingStrategy(data) => <groupingStrategy><name>complexGroupingStrategy</name>{data.toString}<data></data></groupingStrategy>
      case _ => <groupingStrategy><name>everyoneInOneGroup</name></groupingStrategy>
    }
  }

  override def toGroup(input:NodeSeq):Group = Stopwatch.time("GenericXmlSerializer.toGroup",{
    val m = parseMeTLContent(input,config)
    val id = getStringByName(input,"id")
    val location = getStringByName(input,"location")
    val timestamp = getLongByName(input,"timestamp")
    val members = ((input \ "members") \ "member").map(_.text).toList
    Group(config,id,location,timestamp,members,m.audiences)
  })
  override def fromGroup(input:Group):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromGroup",{
    metlXmlToXml("group",List(
      <id>{input.id}</id>,
      <location>{input.location}</location>,
      <timestamp>{input.timestamp}</timestamp>,
      <members>{input.members.map(m => {
        <member>{m}</member>
      })}</members>
    ))
  })

  override def toPermissions(input:NodeSeq):Permissions = Stopwatch.time("GenericXmlSerializer.toPermissions",{
    try {
      val studentsCanOpenFriends = getBooleanByName(input,"studentCanOpenFriends")
      val studentsCanPublish = getBooleanByName(input,"studentCanPublish")
      val usersAreCompulsorilySynced = getBooleanByName(input,"usersAreCompulsorilySynced")
      val studentsMayBroadcast = tryo(getValueOfNode(input,"studentsMayBroadcast").toBoolean).openOr(true)
      val studentsMayChatPublicly = tryo(getValueOfNode(input,"studentsMayChatPublicly").toBoolean).openOr(true)
      Permissions(config,studentsCanOpenFriends,studentsCanPublish,usersAreCompulsorilySynced,studentsMayBroadcast,studentsMayChatPublicly)
    } catch {
      case e:Exception => {
        Permissions.default(config)
      }
    }
  })
  override def fromPermissions(input:Permissions):Node = Stopwatch.time("GenericXmlSerializer.fromPermissions",{
    <permissions><studentCanOpenFriends>{input.studentsCanOpenFriends}</studentCanOpenFriends><studentCanPublish>{input.studentsCanPublish}</studentCanPublish><usersAreCompulsorilySynced>{input.usersAreCompulsorilySynced}</usersAreCompulsorilySynced><studentsMayBroadcast>{input.studentsMayBroadcast}</studentsMayBroadcast><studentsMayChatPublicly>{input.studentsMayChatPublicly}</studentsMayChatPublicly></permissions>
  })
  override def toColor(input:AnyRef):Color = Stopwatch.time("GenericXmlSerializer.toColor",{
    Color.empty
  })
  override def fromColor(input:Color):AnyRef = "%s %s %s %s".format(input.alpha,input.red,input.green,input.blue)
  override def toPointList(input:AnyRef):List[Point] = List.empty[Point]
  override def fromPointList(input:List[Point]):AnyRef = ""
  override def toPoint(input:AnyRef):Point = {
    Point.empty
  }
  override def fromPoint(input:Point):String = "%s %s %s".format(input.x,input.y,input.thickness)
  override def toGrade(input:NodeSeq):MeTLGrade = Stopwatch.time("GenericXmlSerializer.toGrade",{
    val m = parseMeTLContent(input,config)
    val id = getStringByName(input,"id")
    val name = getStringByName(input,"name")
    val description = getStringByName(input,"description")
    val location = getStringByName(input,"location")
    val visible = getBooleanByName(input,"visible")
    val gradeType = MeTLGradeValueType.parse(getStringByName(input,"gradeType"))
    val numericMaximum = if (gradeType == MeTLGradeValueType.Numeric){
      Some(getDoubleByName(input,"numericMaximum"))
    } else {
      None
    }
    val numericMinimum = if (gradeType == MeTLGradeValueType.Numeric){
      Some(getDoubleByName(input,"numericMinimum"))
    } else {
      None
    }
    val foreignRelationship = (input \\ "foreignRelationship").headOption.flatMap(n => {
      for {
        sys <- (n \ "@sys").headOption.map(_.text)
        key <- (n \ "@key").headOption.map(_.text)
      } yield {
        (sys,key)
      }
    })
    val gradeReferenceUrl = (input \\ "gradeReferenceUrl").headOption.map(_.text)
    MeTLGrade(config,m.author,m.timestamp,id,location,name,description,gradeType,visible,foreignRelationship,gradeReferenceUrl,numericMaximum,numericMinimum,m.audiences)
  })
  override def fromGrade(input:MeTLGrade):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromGrade",{
    metlContentToXml("grade",input,List(
      <id>{input.id}</id>,
      <name>{input.name}</name>,
      <location>{input.location}</location>,
      <visible>{input.visible.toString}</visible>,
      <gradeType>{MeTLGradeValueType.print(input.gradeType)}</gradeType>,
      <description>{input.description}</description>
    ) ::: input.foreignRelationship.toList.map(t => {
      <foreignRelationship sys={t._1} key={t._2} />
    }) ::: input.gradeReferenceUrl.toList.map(s => {
      <gradeReferenceUrl>{s}</gradeReferenceUrl>
    }) ::: input.numericMaximum.toList.map(nm => {
      <numericMaximum>{nm.toString}</numericMaximum>
    }) ::: input.numericMinimum.toList.map(nm => {
      <numericMinimum>{nm.toString}</numericMinimum>
    }))
  })

  override def toNumericGradeValue(input:NodeSeq):MeTLNumericGradeValue = Stopwatch.time("GenericXmlSerializer.toNumericGradeValue",{
    val m = parseMeTLContent(input,config)
    val gradeId = getStringByName(input,"gradeId")
    val gradedUser = getStringByName(input,"gradedUser")
    val gradeValue = getDoubleByName(input,"gradeValue")
    val gradeComment = (input \\ "gradeComment").headOption.map(_.text)
    val gradePrivateComment = (input \\ "gradePrivateComment").headOption.map(_.text)
    MeTLNumericGradeValue(config,m.author,m.timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,m.audiences)
  })
  override def fromNumericGradeValue(input:MeTLNumericGradeValue):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromNumericGradeValue",{
    metlContentToXml("numericGradeValue",input,List(
      <gradeId>{input.gradeId}</gradeId>,
      <gradedUser>{input.gradedUser}</gradedUser>,
      <gradeValue>{input.gradeValue.toString}</gradeValue>
    ) ::: input.gradeComment.toList.map(s => {
      <gradeComment>{s}</gradeComment>
    }) ::: input.gradePrivateComment.toList.map(s => {
      <gradePrivateComment>{s}</gradePrivateComment>
    }))
  })
  override def toBooleanGradeValue(input:NodeSeq):MeTLBooleanGradeValue = Stopwatch.time("GenericXmlSerializer.toBooleanGradeValue",{
    val m = parseMeTLContent(input,config)
    val gradeId = getStringByName(input,"gradeId")
    val gradedUser = getStringByName(input,"gradedUser")
    val gradeValue = getBooleanByName(input,"gradeValue")
    val gradeComment = (input \\ "gradeComment").headOption.map(_.text)
    val gradePrivateComment = (input \\ "gradePrivateComment").headOption.map(_.text)
    MeTLBooleanGradeValue(config,m.author,m.timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,m.audiences)
  })
  override def fromBooleanGradeValue(input:MeTLBooleanGradeValue):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromBooleanGradeValue",{
    metlContentToXml("booleanGradeValue",input,List(
      <gradeId>{input.gradeId}</gradeId>,
      <gradedUser>{input.gradedUser}</gradedUser>,
      <gradeValue>{input.gradeValue.toString}</gradeValue>
    ) ::: input.gradeComment.toList.map(s => {
      <gradeComment>{s}</gradeComment>
    }) ::: input.gradePrivateComment.toList.map(s => {
      <gradePrivateComment>{s}</gradePrivateComment>
    }))
  })
  override def toTextGradeValue(input:NodeSeq):MeTLTextGradeValue = Stopwatch.time("GenericXmlSerializer.toTextGradeValue",{
    val m = parseMeTLContent(input,config)
    val gradeId = getStringByName(input,"gradeId")
    val gradedUser = getStringByName(input,"gradedUser")
    val gradeValue = getStringByName(input,"gradeValue")
    val gradeComment = (input \\ "gradeComment").headOption.map(_.text)
    val gradePrivateComment = (input \\ "gradePrivateComment").headOption.map(_.text)
    MeTLTextGradeValue(config,m.author,m.timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,m.audiences)
  })
  override def fromTextGradeValue(input:MeTLTextGradeValue):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromTextGradeValue",{
    metlContentToXml("textGradeValue",input,List(
      <gradeId>{input.gradeId}</gradeId>,
      <gradedUser>{input.gradedUser}</gradedUser>,
      <gradeValue>{input.gradeValue.toString}</gradeValue>
    ) ::: input.gradeComment.toList.map(s => {
      <gradeComment>{s}</gradeComment>
    }) ::: input.gradePrivateComment.toList.map(s => {
      <gradePrivateComment>{s}</gradePrivateComment>
    }))
  })
}
