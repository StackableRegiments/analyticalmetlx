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

import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json._

case class CloudConvertProcessResponse(url:String,id:String,host:String,expires:String,maxtime:Int,minutes:Int)
case class CloudConvertUploadElement(url:String)
case class CloudConvertProcessDefinitionResponse(url:Option[String],id:String,message:String,step:String,upload:Option[CloudConvertUploadElement],output:Option[CloudConvertUploadElement])
case class CloudConvertUploadResponse(file:String,size:Int,message:String)
case class CloudConvertInputElement(`type`:String,filename:String,size:Long,name:String,ext:String) 
case class CloudConvertOutputElement(url:String,filename:String,size:Long,downloads:Int,ext:String,files:Option[List[String]]) 
case class CloudConvertConverterElement(format:String,`type`:String,options:Option[JObject],duration:Double)
case class CloudConvertStatusMessageResponse(id:String,url:Option[String],percent:Option[String],message:Option[String],step:String,starttime:Option[Long],expire:Long,input:Option[CloudConvertInputElement],converter:Option[CloudConvertConverterElement],output:Option[CloudConvertOutputElement],endtime:Option[Long])

class CloudConvertPoweredParser(val apiKey:String) extends Logger {
  implicit val formats = DefaultFormats
  val downscaler = new com.metl.view.ImageDownscaler(16 * 1024 * 1024)
  val unzipper = new Unzipper
  protected val convertUrl = "https://api.cloudconvert.com/convert"

  def importAnything(jid:Int,filename:String,bytes:Array[Byte],server:ServerConfiguration,author:String):Map[Int,History] = {
    try {
      val parts = filename.split('.').toList
      val name = parts.init.filterNot(_ == "").mkString(".")
      val suffix = parts.last
      warn("CloudConvertPoweredParser anything: %s %s %s => %s %s".format(jid,author,filename,name,suffix))
      importToCloudConvert(jid,name,bytes,suffix,"jpg",server,author) match {
        case Right(map) => map
        case Left(e) => {
          error("Exception converting with cloudConvert %s %s=>jpg".format(filename,suffix),e)
          Map.empty[Int,History]
        }
      }
    } catch {
      case e:Exception => {
        error("Exception comprehending filename %s ?=>jpg".format(filename),e)
        Map.empty[Int,History]
      }
    }
  }
  protected def schemify(in:String):String = {
    if (in.startsWith("//"))
      "https:" + in
    else in
  }
  protected def describeResponse(in:HTTPResponse):HTTPResponse = {
    val described = in.headers.get("Content-Type") match {
      case Some(s) if s == "application/octet-stream" => "bytes(%s)".format(in.bytes.length)
      case other => "string(%s)".format(in.responseAsString)
    }
    info("Response: %s\r\n%s".format(in,described))
    in
  }
  protected def callComplexCloudConvert(filename:String,bytes:Array[Byte],inFormat:String,outFormat:String):Either[Throwable,List[Tuple2[Int,Array[Byte]]]] = {
    try {
      val encoding = "UTF-8"
      val client = com.metl.utils.Http.getClient(List(("Authorization","Bearer %s".format(apiKey))))
      println("apiKey: %s".format(apiKey))
      val procResponse = describeResponse(client.postFormExpectingHTTPResponse("https://api.cloudconvert.com/process",List(
        ("inputformat" -> inFormat),
        ("outputformat" -> outFormat)
      )))
      val procResponseObj = parse(procResponse.responseAsString).extract[CloudConvertProcessResponse]

      Thread.sleep(5000)

      val defineProcResponse = describeResponse(client.postFormExpectingHTTPResponse(schemify(procResponseObj.url),List(
        ("input" -> "upload"),
        ("outputformat" -> outFormat)
      )))
      val defineProcResponseObj = parse(defineProcResponse.responseAsString).extract[CloudConvertProcessDefinitionResponse]
      var uploadUrl = defineProcResponseObj.upload.map(u => schemify(u.url)).getOrElse({
        throw new Exception("no upload url defined on process response: %s".format(defineProcResponseObj))
      })
      val uploadResponse = describeResponse(client.putBytesExpectingHTTPResponse(uploadUrl + "/" + urlEncode(filename + "." + inFormat),bytes))
      val uploadResponseObj = parse(uploadResponse.responseAsString).extract[CloudConvertUploadResponse]
      var completed = false
      var downloadUrl = ""
      while (!completed){
        Thread.sleep(1000)
        val statusResponse = describeResponse(client.getExpectingHTTPResponse(procResponseObj.url))
        val statusObj = parse(statusResponse.responseAsString).extract[CloudConvertStatusMessageResponse]
        info("Status: %s".format(statusObj)) 
        if (statusObj.step == "error")
          throw new Exception("error received while converting: %s".format(statusObj))
        else if (statusObj.step == "finished"){
          completed = true
          statusObj.output.foreach(out => {
            downloadUrl = schemify(out.url)
          })
        }
      }
      if (downloadUrl == "")
        throw new Exception("download Url malformed")
      val downloadResponse = describeResponse(client.getExpectingHTTPResponse(downloadUrl))
      val convertResponseBytes = downloadResponse.bytes
      println("downloaded bytes: %s".format(convertResponseBytes.length))
      val parsedResponse = unzipper.extractFiles(convertResponseBytes,_.getName.endsWith(".jpg")).right.map(files => files.map(fileTup => {
        // we should read the page number from the filename, which should be:  "filename-%s.jpg".format(pageNumber), where filename should be the original filename, without the suffix.
        // this should TOTALLY be a regex to make it clean and strong
        var newNumber = fileTup._1
        newNumber = newNumber.drop(filename.length + 1)
        newNumber = newNumber.take(newNumber.length - 4)
        (newNumber.toInt,fileTup._2)
      }))
      println("parsedResponse: %s".format(parsedResponse))
      parsedResponse
    } catch {
      case e:Exception => Left(e)
    }
  }


  protected def callSimpleCloudConvert(filename:String,bytes:Array[Byte],inFormat:String,outFormat:String):Either[Throwable,List[Tuple2[Int,Array[Byte]]]] = {
    try {
      val encoding = "UTF-8"
      val client = com.metl.utils.Http.getClient(List(("Authorization","Bearer %s".format(apiKey))))
      println("apiKey: %s".format(apiKey))
      val formData = List(
        ("inputformat" -> inFormat),
        ("outputformat" -> outFormat),
        ("input" -> "Base64"),
        ("wait" -> "true"),
        ("download" -> "true"),
        ("file" -> base64Encode(bytes)),
        ("filename" -> (filename + "." + inFormat))
      )
//      println("posting form to %s: %s".format(convertUrl,formData.filterNot(_._1 == "filename")))
      var convertResponse = client.postFormExpectingHTTPResponse(convertUrl,formData)
      while (convertResponse.statusCode >= 300 && convertResponse.statusCode < 400){
        println("getPrcResponse redirecting: %s".format(convertResponse))
        convertResponse = client.getExpectingHTTPResponse(convertResponse.headers("Location"))
      }
      println("getPrcResponse: %s".format(convertResponse))
      val convertResponseBytes = convertResponse.bytes
      println("downloaded bytes: %s".format(convertResponseBytes.length))
      try {
        if (convertResponse.statusCode != 200)
          println("uplResponseString: %s".format(convertResponse.responseAsString))
      } catch {
        case e:Exception => {
        }
      }
      val parsedResponse = unzipper.extractFiles(convertResponseBytes,_.getName.endsWith(".jpg")).right.map(files => files.map(fileTup => {
        // we should read the page number from the filename, which should be:  "filename-%s.jpg".format(pageNumber), where filename should be the original filename, without the suffix.
        // this should TOTALLY be a regex to make it clean and strong
        var newNumber = fileTup._1
        newNumber = newNumber.drop(filename.length + 1)
        newNumber = newNumber.take(newNumber.length - 4)
        (newNumber.toInt,fileTup._2)
      }))
      println("parsedResponse: %s".format(parsedResponse))
      parsedResponse
    } catch {
      case e:Exception => Left(e)
    }
  }

  protected def importToCloudConvert(jid:Int,filename:String,bytes:Array[Byte],inFormat:String,outFormat:String,server:ServerConfiguration,author:String):Either[Throwable,Map[Int,History]] = {
    callSimpleCloudConvert(filename,bytes,inFormat,outFormat).right.map(slides => {
    //callComplexCloudConvert(filename,bytes,inFormat,outFormat).right.map(slides => {
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
            println("building metlImage: %s".format(bgImg))
            val history = new History(slideId.toString)
            history.addStanza(bgImg)
            println("created history: %s".format(history))
            Some((slideId,history))
          }
          case _ => None
        }
      }:_*)
    })
  }
}

class ForeignDocumentParser extends Logger {
  protected val apiKey = Globals.cloudConverterApiKey
  def importAnything(filename:String,jid:Int,in:Array[Byte],server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Map[Int,History] = {
        new CloudConvertPoweredParser(apiKey).importAnything(jid,filename,in,server,author)
  }
}

class Unzipper extends Logger {
  import java.io._
  import org.apache.commons.io.IOUtils
  import org.apache.commons.compress.archivers.zip._
  import collection.mutable.ListBuffer
  def extractFiles(in:Array[Byte],filter:ZipArchiveEntry=>Boolean = (zae) => true):Either[Throwable,List[Tuple2[String,Array[Byte]]]] = {
    println("extractingFiles: %s".format(in.length))
    try {
      val zis = new ZipArchiveInputStream(new ByteArrayInputStream(in),"UTF-8",false,true)
      val results = ListBuffer.empty[Tuple2[String,Array[Byte]]]
      try {
        var entry:ZipArchiveEntry = zis.getNextZipEntry
        while (entry != null) {
          if (filter(entry)){
            println("found: %s".format(entry.getName))
            results += ((entry.getName,IOUtils.toByteArray(zis)))
          }
          entry = zis.getNextZipEntry
        }
      } catch {
        case e:Exception => {
          error("exception occurred while unzipping: %s".format(e.getMessage),e)
        }
      }
      Right(results.toList)
    } catch {
      case e:Throwable => Left(e)
    }
  }
}
