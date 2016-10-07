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
  def close(h:MeTLRoom):Unit
}
class ChunkAnalyzer(timeout:Int=3000) extends Logger with Chunker{
  var partialChunks = Map.empty[String,List[MeTLInk]]
  def latest(xs:List[MeTLInk]) = xs.map(_.timestamp).sorted.reverse.head
  def emit(t:Theme,room:MeTLRoom) = room.addTheme(t)
  def close(room:MeTLRoom) = partialChunks.foreach{
    case (author,strokes) => CanvasContentAnalysis.extract(strokes).foreach(word => emit(Theme(author,word,"handwriting"),room))
  }
  def add(c:MeTLStanza,room:MeTLRoom) = c match {
    /*This has a bug; it will not emit a sequence which has completed but not started a new one.
     There needs to be a trigger of some sort on that.*/
    case i:MeTLImage => CanvasContentAnalysis.ocrOne(i) match {
      case Right(t) => {
        CanvasContentAnalysis.getDescriptions(t) match {
          case (descriptions,words) => List(
            descriptions.foreach(word => emit(Theme(i.author, word, "imageRecognition"),room)),
            words.foreach(word => emit(Theme(i.author,word,"imageTranscription"),room)))
        }
      }
      case failure => debug(failure)
    }
    case t:MeTLMultiWordText => t.words.foreach(word => emit(Theme(t.author,word.text,"keyboarding"),room))
    case i:MeTLInk => partialChunks = partialChunks.get(i.author) match {
      case Some(partial) if (i.timestamp - latest(partial) < timeout) => partialChunks + (i.author -> (i :: partial))
      case Some(partial) => {
        CanvasContentAnalysis.extract(partial).foreach(word => emit(Theme(i.author,word,"handwriting"),room))
        partialChunks + (i.author -> List(i))
      }
      case None => partialChunks + (i.author -> (List(i)))
    }
    case default => {}
  }
}

object CanvasContentAnalysis extends Logger {
  implicit val formats = net.liftweb.json.DefaultFormats
  lazy val propFile = XML.load(Globals.configurationFileLocation)

  val analysisThreshold = 5
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
      debug(s)
        (parse(s) \ "noun_phrases").children.collect{ case JString(s) => s}
    }
    val r = response()
    debug(r)
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
    val res = (descriptions.distinct,words.distinct)
    debug(res)
    res
  }

  def ocr(images:List[MeTLImage]):Tuple2[List[String],List[String]] = images
    .map(ocrOne _)
    .collect{ case Right(js) => js}
    .map(parse)
    .map(getDescriptions _)
    .foldLeft((List.empty[String],List.empty[String])){
    case (a,b) => (a._1 ::: b._1, a._2 ::: b._2)
  }
  def ocrOne(image:MeTLImage) = {
    ThemeExtraction.get(image.identity) match {
      case Some(t) => {
        debug("Cache hit for OCR image: %s".format(image.identity))
        Right(t.extraction.get)
      }
      case _ => {
        debug("Cache miss for OCR image: %s".format(image.identity))
        val key = (propFile \\ "visionApiKey").text
        val uri = "vision.googleapis.com/v1/images:annotate"
        val json =
          ("requests" -> List(
            ("image" ->
              ("content" ->  image.imageBytes.map(base64Encode _).openOr(""))) ~
              ("features" -> List("TEXT_DETECTION","LABEL_DETECTION").map(featureType =>
                ("type" -> featureType) ~
                  ("maxResults" -> 10)))))

        val req = host(uri).secure << compact(render(json)) <<? Map("key" -> key)
        val f = Http(req > as.String).either
        debug(f)
        val response = for(
          s <- f.right
        ) yield ThemeExtraction.put(image.identity,s)
        response()
      }
    }
  }

  def extract(inks:List[MeTLInk]):List[String] = {
    if(inks.size < analysisThreshold) {
      debug("Not analysing themes for %s strokes".format(inks.size))
      Nil
    }
    else {
      val key = inks.map(_.identity).toString
      debug("Loading themes for %s strokes".format(inks.size))
      ThemeExtraction.get(key) match {
        case Some(cached) => {
          debug("Cache hit for ink set: %s".format(key))
          simplify(cached.extraction.get)
        }
        case _ => {
          debug("Cache miss for ink set: %s".format(key))
          val myScriptKey = (propFile \\ "myScriptApiKey").text
          val myScriptUrl = "cloud.myscript.com/api/v3.0/recognition/rest/analyzer/doSimpleRecognition.json";

          val json = JObject(List(
            JField("components",JArray(inks.map(i => JObject(List(
              JField("type",JString("stroke")),
              JField("x",JArray(i.points.map(i => JInt(i.x.intValue)))),
              JField("y",JArray(i.points.map(i => JInt(i.y.intValue))))))))),
            JField("parameter",JObject(List(
              JField("textParameter",JObject(List(
                JField("language",JString("en_US"))))))))))

          val input = compact(render(json))
          val req = host(myScriptUrl).secure << Map(
            ("applicationKey" -> myScriptKey),
            ("analyzerInput" -> input))

          req.setContentType("application/x-www-form-urlencoded","UTF-8")

          val f = Http(req OK as.String).either
          debug("Consuming %s bytes of MyScript cartridge".format(input.length))
          val response = for(
            s <- f.right
          ) yield {
            ThemeExtraction.put(key,s)
          }
          val r = response()
          debug(r)
          r.right.toOption.map(simplify _).getOrElse(Nil)
        }
      }
    }
  }
}
