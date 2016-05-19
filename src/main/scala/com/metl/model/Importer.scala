package com.metl.model

import net.liftweb.common._
import scala.xml._
import scala.collection.JavaConverters._
import net.liftweb.util._
import Helpers._

import javax.imageio.ImageIO
import java.io._
//import org.apache.poi.xslf.usermodel._
import org.apache.poi.sl.usermodel.{Slide=>PoiSlide,_}

import java.awt.{Dimension, RenderingHints, Graphics2D}
import java.awt.image.BufferedImage
import java.awt.geom._
import com.metl.data._
import com.metl.utils._

import dispatch._ 
import Defaults._

class HSLFPowerpointParser {
  import org.apache.poi.hslf.usermodel._
  def importAsShapes(jid:Int,in:InputStream,server:ServerConfiguration,author:String = Globals.currentUser.is):Map[Int,History] = {
    importAsImages(jid,in,server,author,1) // not yet implemented
  }
  def importAsImages(jid:Int,in:InputStream,server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Map[Int,History] = {
    //val ppt:SlideShow = SlideShowFactory.create(in)//new SlideShow(in)
    val ppt = org.apache.poi.hslf.usermodel.HSLFSlideShowFactory.createSlideShow(new org.apache.poi.poifs.filesystem.NPOIFSFileSystem(in))//new SlideShow(in)
    in.close()

    val zoom:Int = magnification
    val at:AffineTransform = new AffineTransform()
    at.setToScale(zoom, zoom)

    val pgsize:Dimension = ppt.getPageSize()

    var histories:Map[Int,History] = Map.empty[Int,History]

    ppt.getSlides.toArray.toList.view.zipWithIndex.foreach{
      case (slideObj,index) => {
        val slide:HSLFSlide = slideObj.asInstanceOf[HSLFSlide]
        val slideId = jid + index 
        val identity = ""
        val tag = ""
        val (w,h) = (pgsize.width * zoom,pgsize.height * zoom)
        val img:BufferedImage = new BufferedImage(w,h, BufferedImage.TYPE_INT_RGB)
        val graphics:Graphics2D = img.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setTransform(at)
        graphics.setPaint(java.awt.Color.white)
        graphics.fill(new Rectangle2D.Float(0, 0, w,h))
        slide.draw(graphics)
        val out = new ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpg", out)
        val bytes = out.toByteArray()
        val bgImg = MeTLImage(server,author,-1L,tag,Empty,Full(bytes),Empty,w,h,0,0,"presentationSpace",Privacy.PUBLIC,slideId.toString,identity,Nil,1.0,1.0)
        val history = new History(slideId.toString)
        history.addStanza(bgImg)
        histories = histories.updated(slideId,history)
      }
    }
    histories
  }
}
class XSLFPowerpointParser {
  import org.apache.poi.xslf.usermodel._
  def importAsShapes(jid:Int,in:InputStream,server:ServerConfiguration,author:String = Globals.currentUser.is):Map[Int,History] = {
    importAsImages(jid,in,server,author,1) // not yet implemented
  }
  def importAsImages(jid:Int,in:InputStream,server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Map[Int,History] = {
    val ppt = org.apache.poi.xslf.usermodel.XSLFSlideShowFactory.createSlideShow(in)
    in.close()

    val zoom:Int = magnification
    val at:AffineTransform = new AffineTransform()
    at.setToScale(zoom, zoom)

    val pgsize:Dimension = ppt.getPageSize()

    var histories:Map[Int,History] = Map.empty[Int,History]

    ppt.getSlides.toArray.toList.view.zipWithIndex.foreach{
      case (slideObj,index) => {
        val slide:XSLFSlide = slideObj.asInstanceOf[XSLFSlide]
        val slideId = index + jid
        val (w,h) = (pgsize.width * zoom,pgsize.height * zoom)
        val img:BufferedImage = new BufferedImage(w,h, BufferedImage.TYPE_INT_RGB)
        val graphics:Graphics2D = img.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setTransform(at)
        graphics.setPaint(java.awt.Color.white)
        graphics.fill(new Rectangle2D.Float(0, 0, w,h))
        slide.draw(graphics)
        val out = new ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpg", out)
        val bytes = out.toByteArray()
        val identity = server.postResource(slideId.toString,nextFuncName,bytes) 
        val tag = "{author: '%s', privacy: '%s', id: '%s', isBackground: false, zIndex: 0, resourceIdentity: '%s', timestamp: %s}".format(author,"public",identity,identity, new java.util.Date().getTime)
        val bgImg = MeTLImage(server,author,-1L,tag,Full(identity),Full(bytes),Empty,w,h,0,0,"presentationSpace",Privacy.PUBLIC,slideId.toString,identity,Nil,1.0,1.0)
        val history = new History(slideId.toString)
        history.addStanza(bgImg)
        histories = histories.updated(slideId,history)
      }
    }
    histories
  }
}
object PowerpointVersion extends Enumeration {
  type PowerpointVersion = Value
  val PptXml, PptOle, NotParseable = Value
}

case class CloudConvertProcessResponse(url:String,id:String,host:String,expires:String,maxtime:Int,minutes:Int)
case class CloudConvertUploadElement(url:String)
case class CloudConvertUploadResponse(url:String,id:String,message:String,step:String,upload:CloudConvertUploadElement)

import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json._

class CloudConvertPoweredParser(val apiKey:String) extends Logger {
  val downscaler = new com.metl.view.ImageDownscaler(16 * 1024 * 1024)
  val unzipper = new Unzipper
  def importPpt(jid:Int,bytes:Array[Byte],server:ServerConfiguration,author:String):Map[Int,History] = {
    warn("CloudConvertPoweredParser ppt: %s %s".format(jid,author))
    importToCloudConvert(jid,bytes,"ppt","jpg",server,author) match {
      case Right(map) => map
      case Left(e) => {
        error("Exception converting with cloudConvert ppt=>jpg",e)
        
        Map.empty[Int,History]
      }
    }
  }
  def importPptx(jid:Int,bytes:Array[Byte],server:ServerConfiguration,author:String):Map[Int,History] = {
    warn("CloudConvertPoweredParser pptx: %s %s".format(jid,author))
    importToCloudConvert(jid,bytes,"pptx","jpg",server,author) match {
      case Right(map) => map
      case Left(e) => {
        error("Exception converting with cloudConvert pptx=>jpg",e)
        Map.empty[Int,History]
      }
    }
  }

  implicit val formats = DefaultFormats
  protected val host = "https://api.cloudconvert.com/process"
  protected val getProcessUrl = host
  protected def callCloudConvert(bytes:Array[Byte],inFormat:String,outFormat:String):Either[Throwable,List[Tuple2[Int,Array[Byte]]]] = {
    try {
      val encoding = "UTF-8"
      val client = com.metl.utils.Http.getClient(List(("Authorization","Bearer %s".format(apiKey))))
      println("apiKey: %s".format(apiKey))
      val getPrcRequestJson = ("inputformat" -> inFormat) ~ ("outputformat" -> outFormat)
      val getPrcRequestJsonString:String = Printer.compact(JsonAST.render(getPrcRequestJson))
      println("getPrcRequest: %s".format(getPrcRequestJsonString))
      val getPrcResponse = client.postBytesExpectingHTTPResponse(getProcessUrl,getPrcRequestJsonString.getBytes(encoding),List(("Contenta-Type","application/json")))
      println("getPrcResponse: %s".format(getPrcResponse))
      val getPrcResponseString = getPrcResponse.responseAsString
      println("getPrcResponseString: %s".format(getPrcResponseString))
      val getPrcResponseJson = parse(getPrcResponseString).extract[CloudConvertProcessResponse]
      var procUrl = getPrcResponseJson.url
      if (procUrl.startsWith("//"))
        procUrl = "https:"+procUrl
      println("callCloudConvert.prc.procUrl: %s".format(procUrl))
      val prcRequestJson = ("inputformat" -> inFormat) ~ ("outputformat" -> outFormat) ~ ("input" -> "upload") ~ ("wait" -> true) ~ ("download" -> true)
      val prcRequestJsonString = Printer.compact(JsonAST.render(prcRequestJson))
      println("prcRequestJsonString: %s".format(prcRequestJsonString))
      val prcResponse = client.postBytesExpectingHTTPResponse(procUrl,prcRequestJsonString.getBytes(encoding),List(("Content-Type","application/json")))
      println("prc response: %s".format(prcResponse))
      val prcResponseString = prcResponse.responseAsString
      println("prcResponseString: %s".format(prcResponseString))
      val prcResponseJson = parse(prcResponseString).extract[CloudConvertUploadResponse]
      var uplUrl = prcResponseJson.upload.url
      if (uplUrl.startsWith("//"))
        uplUrl = "https:"+uplUrl
      uplUrl = uplUrl + "/" + nextFuncName + "." + inFormat
      println("uplUrl: %s".format(uplUrl))
/*
 * make the third call, and get the bytes returned
      unzipper.extractFiles(bytes).right.map(files => files.map(fileTup => {
        (fileTup._1.split("-").reverse.head.split(".").head.toInt,fileTup._2)
      }))
*/
      Left(new Exception("not yet implemented"))
    } catch {
      case e:Exception => Left(e)
    }
  }


  protected def dispatchCallCloudConvert(bytes:Array[Byte],inFormat:String,outFormat:String):Either[Throwable,List[Tuple2[Int,Array[Byte]]]] = {
    try {
      println("apiKey: %s".format(apiKey))
      val prc = url(host).POST.setContentType("application/json","UTF-8").setHeader("Authorization","Bearer %s".format(apiKey)) << """{"inputformat":"%s","outputformat":"%s"}""".format(inFormat,outFormat)
      val prcResponse = dispatch.Http(prc OK as.String).either
      prcResponse() match {
        case Right(prcJsonString) => {
          println("callCloudConvert.prc.right: %s".format(prcJsonString))
          val procJson = parse(prcJsonString)
          val procRespObj = procJson.extract[CloudConvertProcessResponse]
          var procUrl = procRespObj.url
          if (procUrl.startsWith("//"))
            procUrl = "https:"+procUrl
          println("callCloudConvert.prc.procUrl: %s".format(procUrl))
          val svc = url(procUrl).POST.setContentType("application/json","UTF-8").setHeader("Authorization","Bearer %s".format(apiKey)) << """{"inputformat":"%s","outputformat":"%s","input":"upload","wait":"true","download":"true"}""".format(inFormat,outFormat)

          val svcResp = dispatch.Http(svc OK as.String).either
          svcResp() match {
            case Right(svcJsonString) => {
              println("callCloudConvert.upl.right: %s".format(svcJsonString))
              val uplJson = parse(svcJsonString)
              val uplRespObj = uplJson.extract[CloudConvertUploadResponse]
              var uplUrl = uplRespObj.upload.url
              if (uplUrl.startsWith("//"))
                uplUrl = "https:"+uplUrl
              uplUrl = uplUrl + "/" + nextFuncName + "." + inFormat
              println("callCloudConvert.prc.uplUrl: %s".format(uplUrl))
              val upl = url(uplUrl).PUT.setBody(bytes).setHeader("Authorization","Bearer %s".format(apiKey))   
              val result = dispatch.Http(upl OK as.Bytes).either
              result() match {
                case Right(bytes) => {
                  println("callCloudConvert.upl.bytes: %s".format(bytes.length))
                  unzipper.extractFiles(bytes).right.map(files => files.map(fileTup => {
                    (fileTup._1.split("-").reverse.head.split(".").head.toInt,fileTup._2)
                  }))
                }
                case Left(e) => Left(e)
              }
            }
            case Left(e) => Left(e)
          }
        }
        case Left(e) => Left(e)
      }
    } catch {
      case e:Exception => Left(e)
    }
  }

  protected def importToCloudConvert(jid:Int,bytes:Array[Byte],inFormat:String,outFormat:String,server:ServerConfiguration,author:String):Either[Throwable,Map[Int,History]] = {
    callCloudConvert(bytes,inFormat,outFormat).right.map(slides => {
      Map({
        slides.flatMap{
          case (index,imageBytes) => {
            val slideId = index + jid
            val identity = server.postResource(slideId.toString,nextFuncName,imageBytes) 
            val tag = "{author: '%s', privacy: '%s', id: '%s', isBackground: false, zIndex: 0, resourceIdentity: '%s', timestamp: %s}".format(author,"public",identity,identity, new java.util.Date().getTime)
            val dimensions = downscaler.getDimensionsOfImage(imageBytes) match {
              case Right(dims) => dims
              case Left(e) => {
                error("exception while getting dimensions of returned image",e)
                (720,576)
              }
            }
            val bgImg = MeTLImage(server,author,-1L,tag,Full(identity),Full(imageBytes),Empty,dimensions._1,dimensions._2,0,0,"presentationSpace",Privacy.PUBLIC,slideId.toString,identity,Nil,1.0,1.0)
            val history = new History(slideId.toString)
            history.addStanza(bgImg)
            Some((slideId,history))
          }
          case _ => None
        }
      }:_*)
    })
  }
}

class PowerpointParser extends Logger {
  protected val apiKey = Globals.cloudConverterApiKey
  protected def detectPptVersion(in:Array[Byte]):PowerpointVersion.Value = {
    try {
        org.apache.poi.hslf.usermodel.HSLFSlideShowFactory.createSlideShow(new org.apache.poi.poifs.filesystem.NPOIFSFileSystem(new ByteArrayInputStream(in)))
        PowerpointVersion.PptOle
    } catch {
      case e:Exception => {
        trace("not an OLE slideshow: %s".format(e.getMessage))
        try {
          org.apache.poi.xslf.usermodel.XSLFSlideShowFactory.createSlideShow(new ByteArrayInputStream(in))
          PowerpointVersion.PptXml
        } catch {
          case ex:Exception => {
            trace("not an XML slideshow: %s".format(ex.getMessage))
            PowerpointVersion.NotParseable
          }
        }
      }
    }
  }
  def importAsImages(jid:Int,in:Array[Byte],server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Map[Int,History] = {
    detectPptVersion(in) match {
      case PowerpointVersion.PptXml => {
        debug("version 2007+")
        new CloudConvertPoweredParser(apiKey).importPptx(jid,in,server,author)
      }
      case PowerpointVersion.PptOle => {
        debug("version 2003")
        new CloudConvertPoweredParser(apiKey).importPpt(jid,in,server,author)
      }
      case _ => {
        debug("not sure of version")
        Map.empty[Int,History]
      }
    }
  }
  def deprecatedImportAsImages(jid:Int,in:Array[Byte],server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Map[Int,History] = {
    detectPptVersion(in) match {
      case PowerpointVersion.PptXml => {
        debug("version 2007+")
        new XSLFPowerpointParser().importAsImages(jid,new ByteArrayInputStream(in),server,author,magnification)
      }
      case PowerpointVersion.PptOle => {
        debug("version 2003")
        new HSLFPowerpointParser().importAsImages(jid,new ByteArrayInputStream(in),server,author,magnification)
      }
      case _ => {
        debug("not sure of version")
        Map.empty[Int,History]
      }
    }
  }
  def importAsShapes(jid:Int,in:Array[Byte],server:ServerConfiguration,author:String = Globals.currentUser.is):Map[Int,History] = {
    detectPptVersion(in) match {
      case PowerpointVersion.PptXml => {
        debug("version 2007+")
        new XSLFPowerpointParser().importAsShapes(jid,new ByteArrayInputStream(in),server,author)
      }
      case PowerpointVersion.PptOle => {
        debug("version 2003")
        new HSLFPowerpointParser().importAsShapes(jid,new ByteArrayInputStream(in),server,author)
      }
      case _ => {
        debug("not sure of version")
        Map.empty[Int,History]
      }
    }
  }
}

class Unzipper extends Logger {
  import java.io._
  import org.apache.commons.io.IOUtils
  import org.apache.commons.compress.archivers.zip._
  import collection.mutable.ListBuffer
  def extractFiles(in:Array[Byte],filter:ZipArchiveEntry=>Boolean = (zae) => true):Either[Throwable,List[Tuple2[String,Array[Byte]]]] = {
    try {
      val zis = new ZipArchiveInputStream(new ByteArrayInputStream(in),"UTF-8",false,true)
      val results = ListBuffer.empty[Tuple2[String,Array[Byte]]]
      var entry:ZipArchiveEntry = zis.getNextZipEntry
      while (entry != null) {
        if (filter(entry)){
          results += ((entry.getName,IOUtils.toByteArray(zis)))
        }
      }
      Right(results.toList)
    } catch {
      case e:Throwable => Left(e)
    }
  }
}
