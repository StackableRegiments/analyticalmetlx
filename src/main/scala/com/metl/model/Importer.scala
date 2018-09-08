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


case class ImportDescription(id:String, name:String, author:String, overallProgress:Option[ImportProgress],stageProgress:Option[ImportProgress], result:Option[Either[Exception,Conversation]]){
  val timestamp = new java.util.Date()
}
case class ImportProgress(name:String,numerator:Int,denominator:Int)

case class CloudConvertProcessResponse(url:String,id:String,host:Option[String],expires:Option[String],maxtime:Option[Int],minutes:Option[Int])
case class CloudConvertUploadElement(url:String)
case class CloudConvertProcessDefinitionResponse(url:Option[String],id:String,message:String,step:String,upload:Option[CloudConvertUploadElement],output:Option[CloudConvertUploadElement])
case class CloudConvertUploadResponse(file:String,size:Int,message:String)
case class CloudConvertInputElement(`type`:String,filename:String,size:Long,name:String,ext:String) 
case class CloudConvertOutputElement(url:String,filename:String,size:Option[Long],downloads:Option[Int],ext:String,files:Option[List[String]]) 
case class CloudConvertConverterElement(format:String,`type`:String,options:Option[JObject],duration:Double)
case class CloudConvertStatusMessageResponse(id:String,url:Option[String],percent:Option[String],message:Option[String],step:String,starttime:Option[Long],expire:Long,input:Option[CloudConvertInputElement],converter:Option[CloudConvertConverterElement],output:Option[CloudConvertOutputElement],endtime:Option[Long])

object ExceptionHelper {
  def toStackTrace(in:Seq[JValue]):Array[StackTraceElement] = in.map{
    case JObject(
      List(
        JField("declaringClass",JString(declaringClass)),
        JField("methodName",JString(methodName)),
        JField("filename",JString(fileName)),
        JField("lineNumber",JInt(lineNumber))
      )
    ) => new StackTraceElement(declaringClass,methodName,fileName,lineNumber.toInt)
    case JObject(
      List(
        JField("declaringClass",JString(declaringClass)),
        JField("methodName",JString(methodName)),
        JField("filename",JNull),
        JField("lineNumber",JInt(lineNumber))
      )
    ) => new StackTraceElement(declaringClass,methodName,null,lineNumber.toInt)

  }.toList.toArray
  def fromStackTrace(in:Seq[StackTraceElement]):List[JObject] =in.map{
    case s:StackTraceElement => JObject(
      List(
        JField("declaringClass",JString(s.getClassName())),
        JField("methodName",JString(s.getMethodName())),
        JField("filename",s.getFileName() match {
          case null => JNull
          case s:String => JString(s)
        }),
        JField("lineNumber",JInt(s.getLineNumber()))
      )
    )
  }.toList
}

class ExceptionSerializer extends CustomSerializer[Exception](ser => ({
  case JObject(
    List(
      JField("message",JString(message)),
      JField("stacktrace",JArray(stackTraceSeq))
    )
  ) => {
    
    val ex = new Exception(message)
    ex.setStackTrace(ExceptionHelper.toStackTrace(stackTraceSeq))
    ex
  }
},{
  case ex:Exception => JObject(
    List(
      JField("message",JString(ex.getMessage())),
      JField("stacktrace",JArray(ExceptionHelper.fromStackTrace(ex.getStackTrace())))
    )
  )
}))


class CloudConvertPoweredParser(importId:String, val apiKey:String,onUpdate:ImportDescription=>Unit = (id:ImportDescription) => {}) extends Logger {
  implicit val formats = DefaultFormats + new ExceptionSerializer()
  val downscaler = new ImageDownscaler(16 * 1024 * 1024)
  val unzipper = new Unzipper
  protected val convertUrl = "https://api.cloudconvert.com/convert"

  def importAnything(jid:Int,filename:String,bytes:Array[Byte],server:ServerConfiguration,author:String):Map[Int,History] = {
    onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("",1,1)),None))
    try {
      val parts = filename.split('.').toList
      val name = parts.init.filterNot(_ == "").mkString(".")
      val suffix = parts.last
      debug("CloudConvertPoweredParser anything: %s %s %s => %s %s".format(jid,author,filename,name,suffix))
      onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("parsed input parameters",1,108)),None))
      importToCloudConvert(jid,name,bytes,suffix,"jpg",server,author) match {
        case Right(map) => map
        case Left(e) => {
          error("Exception converting with cloudConvert %s %s=>jpg".format(filename,suffix),e)
          onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("error from cloudConverter",1,1)),Some(Left(e))))
          throw e
//          Map.empty[Int,History]
        }
      }
    } catch {
      case e:Exception => {
        onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("error from parsing input parameters to cloudConverter",1,1)),Some(Left(e))))
        error("Exception comprehending filename %s ?=>jpg".format(filename),e)
        throw e
//        Map.empty[Int,History]
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
      case Some(s) if s == "application/zip" => "bytes(%s)".format(in.bytes.length)
      case other => "string(%s)".format(in.responseAsString)
    }
    debug("Response: %s\r\n%s".format(in,described))
    in
  }
  protected def callComplexCloudConvert(filename:String,bytes:Array[Byte],inFormat:String,outFormat:String):Either[Exception,List[Tuple2[Int,Array[Byte]]]] = {
    var dialogue = List.empty[Any]
    val start = new java.util.Date().getTime()
    def mark(direction:String,item:Any) = {
      dialogue = dialogue ::: List((direction,new java.util.Date().getTime() - start,item))
    }
    try {
      onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("ready to initiate cloudConverter process",2,108)),None))
      val safeRemoteName = nextFuncName
      val encoding = "UTF-8"
      val client = com.metl.utils.Http.getClient(List(("Authorization","Bearer %s".format(apiKey))))
      trace("apiKey: %s".format(apiKey))
      val procReq = (
        "https://api.cloudconvert.com/process",List(
        ("inputformat" -> inFormat),
        ("outputformat" -> outFormat)
      ))
      mark("OUT",procReq)
      val procResponse = describeResponse(client.postFormExpectingHTTPResponse(procReq._1,procReq._2))
      mark("IN",(procResponse,procResponse.responseAsString))
      val procResponseObj = parse(procResponse.responseAsString).extract[CloudConvertProcessResponse]
      onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("cloudConverter process created",3,108)),None))

      // until they fix a bug, we have to use JSON POST for this step.
      //
      val converterOptions:net.liftweb.json.JsonAST.JObject = {
        inFormat match {
          case "pdf" => ("command" -> "-density 300 -colorspace RGB -background white -alpha remove -quality 70 -sharpen 0x1.0 {INPUTFILE} {OUTPUTFILE}")
          case _ => ("quality" -> 70) ~ ("density" -> 300)
        }
      }

      val importSettings:net.liftweb.json.JsonAST.JObject = (
        ("input" -> "upload") ~ 
        ("outputformat" -> outFormat) ~  
        ("converteroptions" -> converterOptions)
      )
      val jsonSettings = render(importSettings)
      val defineProcReq = (schemify(procResponseObj.url),jsonSettings,List(("Content-Type","application/json")))
      mark("PROCESS CREATE OUT",defineProcReq)
      val defineProcResponse = describeResponse(client.postBytesExpectingHTTPResponse(defineProcReq._1,Printer.compact(defineProcReq._2).getBytes(encoding),defineProcReq._3))
      mark("PROCESS CREATE IN",(defineProcResponse,defineProcResponse.responseAsString))
/*
      val defineProcResponse = describeResponse(client.postFormExpectingHTTPResponse(schemify(procResponseObj.url),List(
        ("input" -> "upload"),
        ("outputformat" -> outFormat)
      )))
      */
      val defineProcResponseObj = parse(defineProcResponse.responseAsString).extract[CloudConvertProcessDefinitionResponse]
      onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress(defineProcResponseObj.message,4,108)),None))
      var uploadUrl = defineProcResponseObj.upload.map(u => schemify(u.url)).getOrElse({
        val ex = new Exception("no upload url defined on process response: %s".format(defineProcResponseObj))
        onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("error: "+defineProcResponseObj.message,4,108)),Some(Left(ex))))
        throw ex
      })
      val uploadReq = "%s/%s.%s".format(uploadUrl,safeRemoteName,inFormat)
      mark("UPLOAD OUT",uploadReq)
      val uploadResponse = describeResponse(client.putBytesExpectingHTTPResponse(uploadReq,bytes))
      mark("UPLOAD IN",(uploadResponse,uploadResponse.responseAsString))
      val uploadResponseObj = parse(uploadResponse.responseAsString).extract[CloudConvertUploadResponse]
      onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress(uploadResponseObj.message,5,108)),None))
      var completed = false
      var downloadUrl = ""
      var downloadExt = ""
      while (!completed){
        Thread.sleep(1000)
        mark("STATUS OUT",procResponseObj.url)
        val statusResponse = describeResponse(client.getExpectingHTTPResponse(procResponseObj.url))
        mark("STATUS IN",(statusResponse,statusResponse.responseAsString))
        val statusObj = parse(statusResponse.responseAsString).extract[CloudConvertStatusMessageResponse]
        val percentage = try {
          (for (
            p <- statusObj.percent;
            pDouble <- Option(p.toDouble)
          ) yield {
            pDouble.toInt
          }).getOrElse(0)
        } catch {
          case e:Exception => 0
        }
        onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress(statusObj.message.map(m => "%s : %s : %s".format(statusObj.step,m,statusObj.percent.getOrElse("?%"))).getOrElse("waiting for cloudConverter"),5 + percentage,108)),None))
        info("Status: %s".format(statusObj)) 
        if (statusObj.step == "error"){
          val ex = new Exception("error received while converting: %s".format(statusObj))
          onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("",5,108)),Some(Left(ex))))
          throw ex
        } else if (statusObj.step == "finished"){
          completed = true
          statusObj.output.foreach(out => {
            downloadExt = out.ext
            downloadUrl = schemify(out.url)
          })
        }
      }
      onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("cloudConverter download ready",106,108)),None))
      if (downloadUrl == ""){
        val ex = new Exception("download Url malformed")
        onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("cloudConverter download url malformed",106,108)),Some(Left(ex))))
        throw ex
      }
      mark("DOWNLOAD OUT",downloadUrl)
      val downloadResponse = describeResponse(client.getExpectingHTTPResponse(downloadUrl))
      mark("DOWNLOAD IN",downloadResponse)
      val convertResponseBytes = downloadResponse.bytes
      onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("downloaded from cloudConverter",107,108)),None))
      trace("downloaded bytes: %s".format(convertResponseBytes.length))

      val parsedResponse = downloadExt match {
        case "zip" => {
          unzipper.extractFiles(convertResponseBytes,_.getName.endsWith(".jpg")).right.map(files => files.map(fileTup => {
            // we should read the page number from the filename, which should be:  "filename-%s.jpg".format(pageNumber), where filename should be the original filename, without the suffix.
            // this should TOTALLY be a regex to make it clean and strong
            var newNumber = fileTup._1
            newNumber = newNumber.drop(safeRemoteName.length + 1)
            newNumber = newNumber.take(newNumber.length - 4)
            (newNumber.toInt,fileTup._2)
          }))
        }
        case "jpg" => {
          Right(List((1,convertResponseBytes)))
        }
        case other => Left(new Exception("unknown extension returned from cloudConverter: %s".format(other)))
      }
      trace("parsedResponse: %s".format(parsedResponse))
      onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("extracted content from cloudConverter",108,108)),None))
      parsedResponse
    } catch {
      case e:Exception => {
        error("error during cloud convert:\r\nprogress: %s".format(dialogue),e)
        onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing with cloudConverter",2,4)),Some(ImportProgress("error returned from cloudConverter",1,1)),Some(Left(e))))
        Left(e)
      }
    }
  }

  protected def callSimpleCloudConvert(filename:String,bytes:Array[Byte],inFormat:String,outFormat:String):Either[Throwable,List[Tuple2[Int,Array[Byte]]]] = {
    try {
      val encoding = "UTF-8"
      val client = com.metl.utils.Http.getClient(List(("Authorization","Bearer %s".format(apiKey))))
      trace("apiKey: %s".format(apiKey))
      val formData = List(
        ("inputformat" -> inFormat),
        ("outputformat" -> outFormat),
        ("input" -> "Base64"),
        ("wait" -> "true"),
        ("download" -> "true"),
        ("file" -> base64Encode(bytes)),
        ("filename" -> (filename + "." + inFormat))
      )
      var convertResponse = client.postFormExpectingHTTPResponse(convertUrl,formData)
      while (convertResponse.statusCode >= 300 && convertResponse.statusCode < 400){
        trace("getPrcResponse redirecting: %s".format(convertResponse))
        convertResponse = client.getExpectingHTTPResponse(convertResponse.headers("Location"))
      }
      trace("getPrcResponse: %s".format(convertResponse))
      val convertResponseBytes = convertResponse.bytes
      trace("downloaded bytes: %s".format(convertResponseBytes.length))
      try {
        if (convertResponse.statusCode != 200)
          trace("uplResponseString: %s".format(convertResponse.responseAsString))
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
      trace("parsedResponse: %s".format(parsedResponse))
      parsedResponse
    } catch {
      case e:Exception => Left(e)
    }
  }

  protected def importToCloudConvert(jid:Int,filename:String,bytes:Array[Byte],inFormat:String,outFormat:String,server:ServerConfiguration,author:String):Either[Exception,Map[Int,History]] = {
    //callSimpleCloudConvert(filename,bytes,inFormat,outFormat).right.map(slides => {
    callComplexCloudConvert(filename,bytes,inFormat,outFormat).right.map(slides => {
      Map({
        var minSlideCount = slides.map(_._1).min
        var offset = 1 - minSlideCount //depending on what the value of the lowest is, I need to push it to be 1, and then offset all the values by that much.
        slides.flatMap{
          case (index,imageBytes) => {
            val slideId = (index + jid) + offset
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
            debug("building metlImage: %s".format(bgImg))
            val history = new History(slideId.toString)
            history.addStanza(bgImg)
            debug("created history: %s".format(history))
            Some((slideId,history))
          }
          case _ => None
        }
      }:_*)
    })
  }
}

class ForeignDocumentParser(importId:String,onUpdate:ImportDescription=>Unit = (id:ImportDescription) => {}) extends Logger {
  protected val apiKey = Globals.cloudConverterApiKey
  def importAnything(filename:String,jid:Int,in:Array[Byte],server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Map[Int,History] = {
    new CloudConvertPoweredParser(importId,apiKey,onUpdate).importAnything(jid,filename,in,server,author)
  }
}

object Importer extends Logger {
  def onUpdate(id:ImportDescription):Unit = {
    com.metl.comet.MeTLConversationSearchActorManager ! id
  }
  protected val config: ServerConfiguration = ServerConfiguration.default
  protected val exportSerializer = new ExportXmlSerializer(config)
  def importExportedConversation(xml:NodeSeq,tag:Box[String],rewrittenUsername:Option[String] = Empty):Box[Conversation] = {
    for (
      historyMap <- (xml \ "histories").headOption.map(hNodes => Map((hNodes \ "history").map(h => {
        val hist = exportSerializer.toHistory(h)
        (hist.jid,hist)
      }):_*));
      conversation <- (xml \ "conversation").headOption.map(c => {
        trace("Importing exported conversation %s %s: \n%s".format(xml \\ "jid", xml \\ "created", c))
        exportSerializer.toConversation(c)
      });
      remoteConv = importExportedConversation(rewrittenUsername,tag,conversation,historyMap)
    ) yield {
      remoteConv
    }
  }
  protected def importExportedConversation(rewrittenUsername:Option[String],tag:Box[String],oldConv:Conversation,histories:Map[String,History]):Conversation = {
    val newAuthor = rewrittenUsername.getOrElse(oldConv.author)
    val now = new java.util.Date()
    val newConv = config.createConversation(oldConv.title + " (copied at %s)".format(now),newAuthor)
    val newConvWithOldSlides = newConv.copy(
      created = oldConv.created,
      lastAccessed = oldConv.lastAccessed,
      tag = tag.openOr("Imported by %s @ %s".format(Globals.currentUser.is,now)),
      slides = oldConv.slides.map(s => s.copy(
        groupSet = Nil,
        id = s.id - oldConv.jid + newConv.jid,
        audiences = Nil,
        author = newAuthor
      ))
    )
    val remoteConv = config.updateConversation(newConv.jid.toString,newConvWithOldSlides)
    histories.foreach(h => {
      val oldJid = h._1
      val offset = remoteConv.jid - oldConv.jid
      val server = remoteConv.server
      val serverName = server.name
      val newRoom = RoomMetaDataUtils.fromJid(oldJid) match {
        case PrivateSlideRoom(_sn,_oldConvJid,oldSlideJid,oldAuthor) => Some(PrivateSlideRoom(server,remoteConv.jid.toString,oldSlideJid + offset,newAuthor))
        case SlideRoom(_sn,_oldConvJid,oldSlideJid) => Some(SlideRoom(server,remoteConv.jid.toString,oldSlideJid + offset))
        case ConversationRoom(_sn,_oldConvJid) => Some(ConversationRoom(server,remoteConv.jid.toString))
        case _ => None
      }
      newRoom.foreach(nr => {
        // swap this back in when the stanzas support adjustAuthor as a method
        val newHistory = h._2/*rewrittenUsername.map(newUser => {
          val nh = new History(h._2.jid,h._2.xScale,h._2.yScale,h._2.xOffset,h._2.yOffset)
          h._2.getAll.foreach(stanza => nh.addStanza(stanza.adjustAuthor(newUser))
          nh
        }).getOrElse(h._2)
        */
        ServerSideBackgroundWorker ! CopyContent(
          server,
          newHistory,
          nr
        )
      })
    })
    trace("Imported exported conversation: " + remoteConv.jid + ", " + remoteConv.title)
    remoteConv
  }
  def importConversationAsAuthor(title:String, filename:String, bytes:Array[Byte], author:String):Box[Conversation] = {
    val importId = nextFuncName
    onUpdate(ImportDescription(importId,title,Globals.currentUser.is,Some(ImportProgress("creating conversation",1,4)),Some(ImportProgress("ready to create conversation",1,2)),None))
    try {
      val now = new java.util.Date()
      val conv = Conversation.empty.copy(title = title, author = author,lastAccessed = now.getTime,jid = 0)  
      onUpdate(ImportDescription(importId,title,Globals.currentUser.is,Some(ImportProgress("creating conversation",1,4)),Some(ImportProgress("created conversation",2,2)),None))
      for (
        histories <- foreignConversationParse(importId,filename,conv.jid,bytes,config,author);
        remoteConv <- foreignConversationImport(importId,config,author,conv,histories)
      ) yield {
        onUpdate(ImportDescription(importId,title,Globals.currentUser.is,Some(ImportProgress("finalizing import",4,4)),Some(ImportProgress("conversation imported",1,1)),Some(Right(remoteConv))))
        remoteConv
      }
    } catch {
      case e:Exception => {
        onUpdate(ImportDescription(importId,title,Globals.currentUser.is,Some(ImportProgress("finalizing import",4,4)),Some(ImportProgress("import failed",1,1)),Some(Left(e))))
        Failure("Exception while importing conversation",Full(e),Empty)
      }
    }
  }
  protected def foreignConversationParse(importId:String,filename:String,jid:Int,in:Array[Byte],server:ServerConfiguration,onBehalfOfUser:String):Box[Map[Int,History]] = {
    try {
      Full(new ForeignDocumentParser(importId,onUpdate _).importAnything(filename,jid,in,server,onBehalfOfUser))
    } catch {
      case e:Exception => {
        onUpdate(ImportDescription(importId,filename,Globals.currentUser.is,Some(ImportProgress("parsing conversation",2,4)),Some(ImportProgress("parse failed",1,1)),Some(Left(e))))
        error("exception in foreignConversationImport",e)
        throw e
      }
    }
  }
  protected def foreignConversationImport(importId:String,server:ServerConfiguration,onBehalfOfUser:String,fakeConversation:Conversation,histories:Map[Int,History]):Box[Conversation] = {
    try {
      val finalCount = histories.keys.toList.length + 4
      onUpdate(ImportDescription(importId,fakeConversation.title,Globals.currentUser.is,Some(ImportProgress("",3,4)),Some(ImportProgress("ready to import content",1,finalCount)),None))
      val conversation = server.createConversation(fakeConversation.title,fakeConversation.author)
      val newConvWithAllSlides = conversation.copy(
        lastAccessed = new java.util.Date().getTime,
        slides = histories.map(h => Slide(server,onBehalfOfUser,h._1 + conversation.jid, h._1 - 1)).toList
      )
      onUpdate(ImportDescription(importId,conversation.title,Globals.currentUser.is,Some(ImportProgress("",3,4)),Some(ImportProgress("slides measured",2,finalCount)),None))
      val remoteConv = server.updateConversation(conversation.jid.toString,newConvWithAllSlides)
      onUpdate(ImportDescription(importId,conversation.title,Globals.currentUser.is,Some(ImportProgress("",3,4)),Some(ImportProgress("slides created",3,finalCount)),None))
      var slideCount = 4
      histories.toList.foreach(tup => {
        val newLoc = RoomMetaDataUtils.fromJid((tup._1 + conversation.jid).toString)
        onUpdate(ImportDescription(importId,conversation.title,Globals.currentUser.is,Some(ImportProgress("",3,4)),Some(ImportProgress("queueing content for insertion for %s".format(newLoc),slideCount,finalCount)),None))
        slideCount += 1
        ServerSideBackgroundWorker ! CopyContent(server,tup._2,newLoc)
      })
      onUpdate(ImportDescription(importId,conversation.title,Globals.currentUser.is,Some(ImportProgress("",3,4)),Some(ImportProgress("all content queued for insertion",finalCount,finalCount)),None))
      Full(newConvWithAllSlides)
    } catch {
      case e:Exception => {
        onUpdate(ImportDescription(importId,fakeConversation.title,Globals.currentUser.is,Some(ImportProgress("",3,4)),Some(ImportProgress("failed to import content",1,1)),Some(Left(e))))
        error("exception in foreignConversationImport",e)
        throw e
      }
    }
  }
  
}

class Unzipper extends Logger {
  import java.io._
  import org.apache.commons.io.IOUtils
  import org.apache.commons.compress.archivers.zip._
  import collection.mutable.ListBuffer
  def extractFiles(in:Array[Byte],filter:ZipArchiveEntry=>Boolean = (zae) => true):Either[Exception,List[Tuple2[String,Array[Byte]]]] = {
    debug("extractingFiles: %s".format(in.length))
    try {
      val zis = new ZipArchiveInputStream(new ByteArrayInputStream(in),"UTF-8",false,true)
      val results = ListBuffer.empty[Tuple2[String,Array[Byte]]]
      try {
        var entry:ZipArchiveEntry = zis.getNextZipEntry
        while (entry != null) {
          if (filter(entry)){
            debug("found: %s".format(entry.getName))
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
      case e:Exception => Left(e)
    }
  }
}

case class CopyContent(server:ServerConfiguration,from:History,to:RoomMetaData)
case class CopyLocation(server:ServerConfiguration,from:RoomMetaData,to:RoomMetaData,contentFilter:MeTLStanza=>Boolean)

object ServerSideBackgroundWorker extends net.liftweb.actor.LiftActor with Logger {
  protected val pool = Range(0,Globals.importerParallelism).map(i => new ServerSideBackgroundWorkerChild()).toArray
  protected var position = 0
  override def messageHandler = {
    case message => {
      try {
        position += 1
        if (position >= Globals.importerParallelism)
          position = 0
        val worker = pool(position)
        trace("worker(%s : %s) doing job: %s".format(position,worker,message))
        worker ! message
      } catch {
        case e:Exception => {
          error("error in ServerSideBackgroundWorker - (%s) job %s".format(position,message),e)
        }
      }
    }
  }
}

class ServerSideBackgroundWorkerChild extends net.liftweb.actor.LiftActor with Logger {
  val thisDuplicatorId = nextFuncName
  override def messageHandler = {
    case RoomJoinAcknowledged(server,room) => {}
    case RoomLeaveAcknowledged(server,room) => {}
    case CopyContent(config,oldContent,newLoc) => {
      val room = MeTLXConfiguration.getRoom(newLoc.getJid,config.name,newLoc)
      val slideId = newLoc match {
        case p:PrivateSlideRoom => p.slideId.toString
        case _ => newLoc.getJid
      }
      room ! JoinRoom(ImporterActor.backgroundWorkerName,thisDuplicatorId,this)
      oldContent.getAll.sortWith((a,b) => a.timestamp < b.timestamp).foreach(stanza => {
        room ! ArchiveToServerMeTLStanza(stanza match {
          case m:MeTLInk => m.copy(slide = slideId)
          case m:MeTLImage => m.copy(slide = slideId)
          case m:MeTLText => m.copy(slide = slideId)
          case m:MeTLVideo => m.copy(slide = slideId)
          case m:MeTLMultiWordText => m.copy(slide = slideId)
          case m:MeTLMoveDelta => m.copy(slide = slideId)
          case m:MeTLDirtyInk => m.copy(slide = slideId)
          case m:MeTLDirtyText => m.copy(slide = slideId)
          case m:MeTLDirtyVideo => m.copy(slide = slideId)
          case m:MeTLUndeletedCanvasContent => m.copy(slide = slideId)
          case m:MeTLSubmission => tryo(slideId.toInt).map(ns => m.copy(slideJid = ns)).getOrElse(m)
          case m:MeTLUnhandledCanvasContent => m.copy(slide = slideId)
          case m:MeTLQuiz => m
          case s:MeTLStanza => s
        })
      })
      room ! LeaveRoom(ImporterActor.backgroundWorkerName,thisDuplicatorId,this)
    }
    case CopyLocation(config,oldLoc,newLoc,contentFilter) => {
      val oldContent = config.getHistory(oldLoc.getJid).filter(contentFilter)
      this ! CopyContent(config,oldContent,newLoc)
    }
  }
}

class ImageDownscaler(maximumByteSize:Int) extends Logger {
  import java.awt.{Image,Graphics2D,Canvas,Transparency,RenderingHints}
  import java.awt.image._
  import java.io._
  import javax.imageio.ImageIO
  def filterByMaximumSize(in:Array[Byte]):Option[Array[Byte]] = {
    in match {
      case tooBig if tooBig.length > maximumByteSize => None
      case acceptable => Some(acceptable)
    }
  }
  def getDimensionsOfImage(in:Array[Byte]):Either[Exception,Tuple2[Int,Int]] = {
    try {
      val tempG = new BufferedImage(1,1,BufferedImage.TYPE_3BYTE_BGR).createGraphics.asInstanceOf[Graphics2D]

      val inStream = new ByteArrayInputStream(in)
      val image = ImageIO.read(inStream).asInstanceOf[BufferedImage]
      inStream.close()
      val observer = new Canvas(tempG.getDeviceConfiguration)
      val originalH = image.getHeight(observer)
      val originalW = image.getWidth(observer)
      Right((originalW,originalH))
    } catch {
      case e:Exception => Left(e)
    }
  }
  def downscaleImage(in:Array[Byte],descriptor:String = ""):Array[Byte] = {
    in match {
      case b if b.length <= maximumByteSize => b
      case tooBig => {
        try {
          var resized:Array[Byte] = in
          while (resized.length > maximumByteSize){
            val tempG = new BufferedImage(1,1,BufferedImage.TYPE_3BYTE_BGR).createGraphics.asInstanceOf[Graphics2D]

            val inStream = new ByteArrayInputStream(resized)
            val image = ImageIO.read(inStream).asInstanceOf[BufferedImage]
            inStream.close()
            val observer = new Canvas(tempG.getDeviceConfiguration)
            val originalH = image.getHeight(observer)
            val targetH = (originalH * 0.8).toInt
            val originalW = image.getWidth(observer)
            val targetW = (originalW * 0.8).toInt
            val (typeName,targetType):Tuple2[String,Int] = (image.getTransparency() == Transparency.OPAQUE) match {
              case true => ("jpg",BufferedImage.TYPE_INT_RGB)
              case false => ("png",BufferedImage.TYPE_INT_ARGB)
            }

            val res = new BufferedImage(targetW,targetH,targetType)
            val g = image.createGraphics.asInstanceOf[Graphics2D]
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(res,0,0,targetW,targetH,null)
            g.dispose
            val outStream = new java.io.ByteArrayOutputStream
            ImageIO.write(res,typeName,outStream)
            info("downscaling image [%s]: (%s,%s)=>(%s,%s)".format(descriptor,originalW,originalH,targetW,targetH))
            resized = outStream.toByteArray
            outStream.close
          }
          resized
        } catch {
          case e:Exception => {
            error("Exception while downscaling image [%s].  Returning emptyByteArray instead".format(descriptor),e)
            Array.empty[Byte]
          }
        }
      }
    }
  }
}

class ExportXmlSerializer(config:ServerConfiguration) extends GenericXmlSerializer(config) with Logger {
  lazy val downscaler = new ImageDownscaler(16777216)
  protected def downscaleImage(in:Array[Byte],descriptor:String = ""):Array[Byte] = downscaler.downscaleImage(in,descriptor)
  override def toMeTLImage(input:NodeSeq):MeTLImage = {
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val tag = getStringByName(input,"tag")
    val imageBytes = downscaleImage(base64Decode(getStringByName(input,"imageBytes")),tag)
    val newUrl = config.postResource(c.slide.toString,nextFuncName,imageBytes)
    val source = Full(newUrl)
    val pngBytes = Empty
    val width = getDoubleByName(input,"width")
    val height = getDoubleByName(input,"height")
    val x = getDoubleByName(input,"x")
    val y = getDoubleByName(input,"y")
    MeTLImage(config,m.author,m.timestamp,tag,source,Full(imageBytes),pngBytes,width,height,x,y,c.target,c.privacy,c.slide,c.identity,m.audiences)
  }
  override def fromMeTLImage(input:MeTLImage):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLImage",{
    canvasContentToXml("image",input,List(
      <tag>{input.tag}</tag>,
      <imageBytes>{base64Encode(input.imageBytes.getOrElse(Array.empty[Byte]))}</imageBytes>,
      <width>{input.width}</width>,
      <height>{input.height}</height>,
      <x>{input.x}</x>,
      <y>{input.y}</y>
    ))
  })
  override def toMeTLQuiz(input:NodeSeq):MeTLQuiz = Stopwatch.time("GenericXmlSerializer.toMeTLQuiz", {
    val m = parseMeTLContent(input,config)
    val created = getLongByName(input,"created")
    val question = getStringByName(input,"question") match {
      case q if (q.length > 0) => q
      case _ => getStringByName(input,"title")
    }
    val id = getStringByName(input,"id")
    val quizImage = Full(base64Decode(getStringByName(input,"imageBytes"))).map(ib => downscaleImage(ib,"quiz: %s".format(id)))
    val newUrl = quizImage.map(qi => config.postResource("quizImages",nextFuncName,qi))
    val isDeleted = getBooleanByName(input,"isDeleted")
    val options = getXmlByName(input,"quizOption").map(qo => toQuizOption(qo)).toList
    MeTLQuiz(config,m.author,m.timestamp,created,question,id,newUrl,quizImage,isDeleted,options,m.audiences)
  })
  override def fromMeTLQuiz(input:MeTLQuiz):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLQuiz", {
    metlContentToXml("quiz",input,List(
      <created>{input.created}</created>,
      <question>{input.question}</question>,
      <id>{input.id}</id>,
      <isDeleted>{input.isDeleted}</isDeleted>,
      <options>{input.options.map(o => fromQuizOption(o))}</options>
    ) ::: input.imageBytes.map(ib => List(<imageBytes>{base64Encode(ib)}</imageBytes>)).openOr(List.empty[Node]))
  })
  override def toSubmission(input:NodeSeq):MeTLSubmission = Stopwatch.time("GenericXmlSerializer.toSubmission", {
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val title = getStringByName(input,"title")
    val imageBytes = Full(base64Decode(getStringByName(input,"imageBytes"))).map(ib => downscaleImage(ib,"submission: %s".format(c.identity)))
    val urlBox = imageBytes.map(ib => config.postResource(c.slide.toString,nextFuncName,ib))
    val newId = urlBox.getOrElse(c.identity)
    val url = urlBox.getOrElse("unknown")
    val blacklist = getXmlByName(input,"blacklist").map(bl => {
      val username = getStringByName(bl,"username")
      val highlight = getColorByName(bl,"highlight")
      SubmissionBlacklistedPerson(username,highlight)
    }).toList
    MeTLSubmission(config,m.author,m.timestamp,title,c.slide.toInt,url,imageBytes,blacklist,c.target,c.privacy,newId,m.audiences)
  })
  override def fromSubmission(input:MeTLSubmission):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromSubmission", {
    canvasContentToXml("screenshotSubmission",input,List(
      <imageBytes>{base64Encode(input.imageBytes.getOrElse(Array.empty[Byte]))}</imageBytes>,
      <title>{input.title}</title>,
      <time>{input.timestamp.toString}</time>
    ) ::: input.blacklist.map(bl => <blacklist><username>{bl.username}</username><highlight>{ColorConverter.toRGBAString(bl.highlight)}</highlight></blacklist> ).toList)
  })
  override def toMeTLFile(input:NodeSeq):MeTLFile = Stopwatch.time("GenericXmlSerializer.toMeTLFile",{
    val m = parseMeTLContent(input,config)
    val name = getStringByName(input,"name")
    val id = getStringByName(input,"id")
    val deleted = getBooleanByName(input,"deleted")
    val bytes = downscaler.filterByMaximumSize(base64Decode(getStringByName(input,"bytes")))
    val url = bytes.map(ib => config.postResource("files",nextFuncName,ib))
    MeTLFile(config,m.author,m.timestamp,name,id,url,bytes,deleted)
  })
  override def fromMeTLFile(input:MeTLFile):NodeSeq = Stopwatch.time("GenericXmlSerializer.fromMeTLFile",{
    metlContentToXml("file",input,List(
      <name>{input.name}</name>,
      <deleted>{input.deleted}</deleted>,
      <id>{input.id}</id>
    ) :::
      input.bytes.map(ib => List(<bytes>{base64Encode(ib)}</bytes>)).getOrElse(List.empty[Node]))
  })
  override def toMeTLVideo(input:NodeSeq):MeTLVideo = Stopwatch.time("GenericXmlSerializer.toMeTLVideo",{
    val m = parseMeTLContent(input,config)
    val c = parseCanvasContent(input)
    val source = getStringByName(input,"source") match {
      case s:String if (s.length > 0 && s != "unknown url" && s != "none") => Full(s)
      case _ => Empty
    }
    val videoBytes = Full(base64Decode(getStringByName(input,"videoBytes")))
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
      <videoBytes>{base64Encode(input.videoBytes.getOrElse(Array.empty[Byte]))}</videoBytes>,
      <x>{input.x}</x>,
      <y>{input.y}</y>
    ))
  })
}
