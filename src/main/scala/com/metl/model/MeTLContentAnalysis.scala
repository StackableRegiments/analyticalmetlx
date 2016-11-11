package com.metl.model

import dispatch._
import dispatch.Defaults._
import com.metl.data._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Logger
import scala.xml._
import collection._
import net.liftweb.util.SecurityHelpers._
import com.metl.h2.dbformats.ThemeExtraction
import com.metl.renderer._

case class Theme(author:String,text:String,origin:String)

case class Chunked(timeThreshold:Int, distanceThreshold:Int, chunksets:Seq[Chunkset])
case class Chunkset(author:String,chunks:List[Chunk]){
  val start = chunks.map(_.start).min
  val end = chunks.map(_.end).max
}
case class Chunk(activity:List[MeTLCanvasContent]){
  val start = activity.headOption.map(_.timestamp).getOrElse(0L)
  val end = activity.reverse.headOption.map(_.timestamp).getOrElse(0L)
  val milis = end - start
}

trait Chunker{
  def add(c:MeTLStanza,h:MeTLRoom):Unit
  def emit(t:Theme,h:MeTLRoom):Unit
  def check(h:MeTLRoom):Unit
  def close(h:MeTLRoom):Unit
}
class ChunkAnalyzer extends Logger with Chunker{
  var partialChunks = Map.empty[String,List[MeTLInk]]
  def latest(xs:List[MeTLInk]) = xs.map(_.timestamp).sorted.reverse.head
  def emit(t:Theme,room:MeTLRoom) = {
    debug("Emitting: %s".format(t))
    room.addTheme(t)
  }
  def close(room:MeTLRoom) = partialChunks.foreach{
    case (author,strokes) => {
      val desc = CanvasContentAnalysis.extract(strokes)
      desc._1.foreach(word => emit(Theme(author,word,"imageRecognition"),room))
      desc._2.foreach(word => emit(Theme(author,word,"handwriting"),room))
    }
  }
  def check(room:MeTLRoom) = {
    val time = java.lang.System.currentTimeMillis();
    partialChunks = partialChunks.filter {
      case entry@(author,partial) if (time - latest(partial) < Globals.chunkingTimeout) => true
      case (author,i :: t) => {
        val desc = CanvasContentAnalysis.extract(i :: t)
        desc._1.foreach(word => emit(Theme(i.author,word,"imageRecognition"),room))
        desc._2.foreach(word => emit(Theme(i.author,word,"handwriting"),room))
        false
      }
    }
  }
  def add(c:MeTLStanza,room:MeTLRoom) = {
    if(Globals.liveIntegration){
      c match {
        case i:MeTLImage => CanvasContentAnalysis.ocrOne(i) match {
          case Right(t) => {
            CanvasContentAnalysis.getDescriptions(t) match {
              case (descriptions,words) => List(
                descriptions.foreach(word => emit(Theme(i.author, word, "imageRecognition"),room)),
                words.foreach(word => emit(Theme(i.author,word,"imageTranscription"),room)))
            }
          }
          case failure => warn(failure)
        }
        case t:MeTLMultiWordText => {
          t.words.foreach(word => emit(Theme(t.author,word.text,"keyboarding"),room))
        }
        case i:MeTLInk => partialChunks = partialChunks.get(i.author) match {
          case Some(partial) if (i.timestamp - latest(partial) < Globals.chunkingTimeout) => partialChunks + (i.author -> (i :: partial))
          case Some(partial) => {
            val desc = CanvasContentAnalysis.extract(partial)
            desc._1.foreach(word => emit(Theme(i.author,word,"imageRecognition"),room))
            desc._2.foreach(word => emit(Theme(i.author,word,"handwriting"),room))
            partialChunks + (i.author -> List(i))
          }
          case None => partialChunks + (i.author -> (List(i)))
        }
        case default => {}
      }
    }
  }
}

object CanvasContentAnalysis extends Logger {
  implicit val formats = net.liftweb.json.DefaultFormats
  lazy val propFile = XML.load(Globals.configurationFileLocation)

  def element(c:MeTLCanvasContent) = JArray(List(JString(c.author),JInt(c.timestamp),JDouble(c.left),JDouble(c.top),JDouble(c.right),JDouble(c.bottom)))
  def chunk(es:List[MeTLCanvasContent],timeThreshold:Int=5000,distanceThreshold:Int=100) = Chunked(timeThreshold,distanceThreshold,
    es.sortBy(_.timestamp).groupBy(_.author).map {
      case (author,items) => {
        Chunkset(author, items.foldLeft((0L,List.empty[List[MeTLCanvasContent]])){
          case ((last,runs@(head :: tail)),item) => {
            if((item.timestamp - last) < timeThreshold){
              (item.timestamp, (item :: head) :: tail)
            }
            else{
              (item.timestamp, List(item) :: runs)
            }
          }
          case ((last,runs),item) => (item.timestamp, List(item) :: runs)
        }._2.map(run => Chunk(run.reverse)))
      }
    }.toList)
  def thematize(phrases:List[String]) = {
    val api = "textanalysis.p.mashape.com/textblob-noun-phrase-extraction"
    val keyValue = "exampleKey"

    val req = host(api).secure << Map(
      "text" -> phrases.mkString(",")
    ) <:< Map(
      "X-Mashape-Key" -> keyValue
    )
    req.setContentType("application/x-www-form-urlencoded","UTF-8")
    val f = Http(req OK as.String).either
    val response = for(
      s <- f.right
    ) yield {
      (parse(s) \ "noun_phrases").children.collect{ case JString(s) => s}
    }
    val r = response()
    r match {
      case Right(rs) => rs
      case _ => Nil
    }
  }
  def simplify(s:String) = {
    (parse(s) \\ "textLines" \\ "label").children.collect{ case JField(_,JString(s)) => s }
  }
  def getDescriptions(j:JValue):Tuple2[List[String],List[String]] = {
    val descriptions = for(
      JField("labelAnnotations",f) <- j;
      JField("description",JString(s)) <- f
    ) yield  s.trim
    val words = for(
      JField("textAnnotations",f) <- j;
      JField("description",JString(s)) <- f
    ) yield  s.trim
    (descriptions.distinct,words.distinct)
  }

  def ocr(images:List[MeTLImage]):Tuple2[List[String],List[String]] = images
    .map(ocrOne _)
    .collect{ case Right(js) => js}
    .map(getDescriptions _)
    .foldLeft((List.empty[String],List.empty[String])){
    case (a,b) => (a._1 ::: b._1, a._2 ::: b._2)
  }
  def ocrBytes(bytes:Array[Byte]):Future[Either[Throwable,JValue]] = {
    val key = (propFile \\ "visionApiKey").text
    val uri = "vision.googleapis.com/v1/images:annotate"
    val b64 = base64Encode(bytes)
    val json =
      ("requests" -> List(
        JObject(List(
          JField("image", ("content" ->  b64 )),
          JField("features",JArray(List("TEXT_DETECTION","LABEL_DETECTION").map(featureType =>
            ("type" -> featureType) ~
              ("maxResults" -> 10)
          )))
        ))
      ))
    val renderedJson = compact(render(json))
    val req = host(uri).secure << renderedJson <<? Map("key" -> key)
    val res = Http(req > as.String).either
    res.right.map(response => {
      parse(response)
    })
  }

  def ocrOne(image:MeTLImage):Either[Throwable,JValue] = {
    ThemeExtraction.get(image.identity) match {
      case Some(t) => {
        debug("Cache hit for OCR image: %s".format(image.identity))
        Right(t.extraction.get)
      }
      case _ => {
        debug("Cache miss for OCR image: %s".format(image.identity))
        image.imageBytes.map(bytes => {
          val response = for {
            s <- ocrBytes(bytes).right
          } yield {
            ThemeExtraction.put(image.identity,compact(render(s)))
            s
          }
          val resp:Either[Throwable,JValue] = response()
          resp
        }).openOr({
          val resp:Either[Throwable,JValue] = Right(JArray(Nil))
          resp
        })
      }
    }
  }

  def extract(inks:List[MeTLInk]):Tuple2[List[String],List[String]] = {
    if(inks.size < Globals.chunkingThreshold) {
      trace("Not analysing themes for %s strokes".format(inks.size))
      (Nil,Nil)
    }
    else {
      trace("Analysing themes for %s strokes".format(inks.size))
      val h = new History("snapshot")
      inks.foreach(h.addStanza _)
      val bytes = SlideRenderer.render(h,800,600)
      val response = for(s <- ocrBytes(bytes).right) yield s
      response().right.toOption.map(r => {
        debug("Server response: %s".format(r))
        getDescriptions(r)
      }).getOrElse((Nil,Nil))
    }
  }
}
