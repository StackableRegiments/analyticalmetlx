package com.metl.metl2011

import com.metl.data._
import com.metl.utils._
import com.metl._

import scala.xml._
import net.liftweb.common._
import net.liftweb.util.Helpers._
import Privacy._
import javax.imageio._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConversions

class MeTL2011XmlSerializer(config:ServerConfiguration,cacheImages:Boolean = false,transcodePng:Boolean = false) extends GenericXmlSerializer(config) with Logger {

  private val imageCache = JavaConversions.mapAsScalaConcurrentMap(new ConcurrentHashMap[String,Array[Byte]]())
  private def getCachedImage(url:String) = Stopwatch.time("MeTL2011XmlSerializer.getCachedImage", imageCache.getOrElseUpdate(url, config.getResource(url)))
  private val metlUtils = new MeTL2011Utils(config)
  override def fromMeTLImage(input:MeTLImage):NodeSeq = Stopwatch.time("MeTL2011XmlSerializer.fromMeTLImage",{
    val newSource = input.source.map(u => metlUtils.deabsolutizeUri(u,config)).getOrElse(Empty)
    val newImage = MeTLImage(input.server,input.author,input.timestamp,input.tag,newSource,input.imageBytes,input.pngBytes,input.width,input.height,input.x,input.y,input.target,input.privacy,input.slide,input.identity,input.audiences,input.scaleFactorX,input.scaleFactorY)
    trace("serializing newImage for xmpp: %s".format(newImage))
    super.fromMeTLImage(newImage)
  })
  override def toMeTLImage(input:NodeSeq):MeTLImage = Stopwatch.time("MeTL2011XmlSerializer.toMeTLImage",{
    trace("deserializing image from xmpp: %s".format(input))
    val m = parseMeTLContent(input)
    val c = parseCanvasContent(input)
    val tag = getStringByName(input,"tag")
    val source = getStringByName(input,"source") match {
      case s:String if (s.length > 0 && s != "unknown url" && s != "none") => metlUtils.reabsolutizeUri(s,"Resource")
      case _ => Empty
    }
    val imageBytes = source.map(u => {
      if (cacheImages)
        getCachedImage(u)
      else
        config.getResource(u)
    })
    val pngBytes = {
      if (transcodePng)
        imageBytes.map(b => {
          val inMs = new ByteArrayInputStream(b)
          val anyFormat = ImageIO.read(inMs)
          val out = new ByteArrayOutputStream
          ImageIO.write(anyFormat,"png",out)
          out.toByteArray
        })
        else Empty
    }
    val width = getDoubleByName(input,"width")
    val height = getDoubleByName(input,"height")
    val x = getDoubleByName(input,"x")
    val y = getDoubleByName(input,"y")
    MeTLImage(config,m.author,m.timestamp,tag,source,imageBytes,pngBytes,width,height,x,y,c.target,c.privacy,c.slide,c.identity,m.audiences)
  })
  override def toMeTLCommand(input:NodeSeq):MeTLCommand = Stopwatch.time("MeTL2011XmlSerializer.toMeTLCommand",{
    val m = parseMeTLContent(input)
    val body = input match {
      case t:Text => t.toString.split(" ")
      case e:Elem => getStringByName(e,"body").split(" ")
      case other => other.toString.split(" ")
    }
    val comm = body.head
    val parameters = body.tail.toList
    MeTLCommand(config,m.author,m.timestamp,comm,parameters,m.audiences)
  })
  override def fromMeTLCommand(input:MeTLCommand):NodeSeq = Stopwatch.time("MeTL2011XmlSerializer.fromMeTLCommand",{
    <body>{Text((input.command :: input.commandParameters).mkString(" "))}</body>
  })
  override def toSubmission(input:NodeSeq):MeTLSubmission = Stopwatch.time("GenericXmlSerializer.toSubmission",{
		trace("submission attempted: %s".format(input))
    val m = parseMeTLContent(input)
    val c = parseCanvasContent(input)
    val title = getStringByName(input,"title")
    val urlBox = getStringByName(input,"url") match {
			case s:String if s.length > 0 => {
				if (s.startsWith("http")) {
					Full(s)
				} else {
					metlUtils.reabsolutizeUri(s,"Resource")
				}
			}
			case _ => Empty
		}
    val imageBytes = urlBox.map(url => {
			if (cacheImages)
				getCachedImage(url)
			else 
				config.getResource(url)
		})
		val url = urlBox.openOr("no valid url specified")
    val blacklist = getXmlByName(input,"blacklist").map(bl => {
      val username = getStringByName(bl,"username")
      val highlight = getColorByName(bl,"highlight")
      SubmissionBlacklistedPerson(username,highlight)
    }).toList
    MeTLSubmission(config,m.author,m.timestamp,title,c.slide.toInt,url,imageBytes,blacklist,c.target,c.privacy,c.identity,m.audiences)
  })
  override def toMeTLQuiz(input:NodeSeq):MeTLQuiz = Stopwatch.time("MeTL2011XmlSerializer.toMeTLQuiz",{
		trace("quiz attempted: %s".format(input))
    val m = parseMeTLContent(input)
		try {
			val created = getLongByName(input,"created")
			val question = getStringByName(input,"question") match {
				case q if (q.length > 0) => q
				case _ => getStringByName(input,"title")
			}
			val id = getStringByName(input,"id")
			val url = getStringByName(input,"url") match {
				case s:String if (s.length > 0 && s != "unknown url" && s != "none") => metlUtils.reabsolutizeUri(s,"Resource")
				case _ => Empty
			}
			val quizImage = url.map(u => {
				if (cacheImages)
					getCachedImage(u)
				else
					config.getResource(u)
			})
			val isDeleted = getBooleanByName(input,"isDeleted")
			val options = getXmlByName(input,"quizOption").map(qo => toQuizOption(qo)).toList
			MeTLQuiz(config,m.author,m.timestamp,created,question,id,url,quizImage,isDeleted,options,m.audiences)
		} catch {
			case e:Throwable => {
				error("failed to construct MeTLQuiz",e)
				MeTLQuiz(config,m.author,m.timestamp,0L,"","",Empty,Empty,true,List.empty[QuizOption],m.audiences)	
			}
		}
  })
  override def toMeTLFile(input:NodeSeq):MeTLFile = Stopwatch.time("MeTL2011XmlSerializer.toMeTLFile",{
		trace("file attempted: %s".format(input))
    val m = parseMeTLContent(input)
		try {
      val name = getStringByName(input,"name")
      val id = getStringByName(input,"id")
      val deleted = getBooleanByName(input,"deleted")
			val url = getStringByName(input,"url") match {
				case s:String if (s.length > 0 && s != "unknown url" && s != "none") => metlUtils.reabsolutizeUri(s,"Resource")
				case _ => Empty
			}
			val bytes = url.map(u => {
				if (cacheImages)
					getCachedImage(u)
				else
					config.getResource(u)
			})
			MeTLFile(config,m.author,m.timestamp,name,id,url,bytes,deleted,m.audiences)
		} catch {
			case e:Throwable => {
				error("failed to construct MeTLFile",e)
        MeTLFile.empty
			}
		}
  })

}
