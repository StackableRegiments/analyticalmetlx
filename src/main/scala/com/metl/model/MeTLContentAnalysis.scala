package com.metl.model

import dispatch._, Defaults._
import com.metl.data._
import net.liftweb.json._
import net.liftweb.common.Logger

case class Theme(author:String,text:String)

case class Chunked(timeThreshold:Int, distanceThreshold:Int, chunksets:Seq[Chunkset])
case class Chunkset(author:String,chunks:Seq[Chunk]){
  val start = chunks.map(_.start).min
  val end = chunks.map(_.end).max
}
case class Chunk(activity:Seq[MeTLCanvasContent]){
  val start = activity.headOption.map(_.timestamp).getOrElse(0L)
  val end = activity.reverse.headOption.map(_.timestamp).getOrElse(0L)
  val milis = end - start
}

object CanvasContentAnalysis extends Logger {
  implicit val formats = net.liftweb.json.DefaultFormats
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
    val keyValue = "exampleApiKey"

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
  def extract(inks:List[MeTLInk]):List[String] = {
    if(inks.size < analysisThreshold) {
      debug("Not analysing themes for %s strokes".format(inks.size))
      Nil
    }
    else{
      debug("Loading themes for %s strokes".format(inks.size))
      val myScriptKey = "exampleApiKey"
      val myScriptUrl = "cloud.myscript.com/api/v3.0/recognition/rest/analyzer/doSimpleRecognition.json";

      val json = JObject(List(
        JField("components",JArray(inks.map(i => JObject(List(
          JField("type",JString("stroke")),
          JField("x",JArray(i.points.map(i => JInt(i.x.intValue)))),
          JField("y",JArray(i.points.map(i => JInt(i.y.intValue))))))))),
        JField("parameter",JObject(List(
          JField("textParameter",JObject(List(
            JField("language",JString("en_US"))))))))))

      val req = host(myScriptUrl).secure << Map(
        ("applicationKey" -> myScriptKey),
        ("analyzerInput" -> compact(render(json))))

      req.setContentType("application/x-www-form-urlencoded","UTF-8")

      val f = Http(req OK as.String).either
      debug(f)
      val response = for(
        s <- f.right
      ) yield {
        val labels = (parse(s) \\ "textLines" \\ "label").children
        labels.collect{ case JField(_,JString(s)) => s }
      }
      val r = response()
      debug(r)
      r match {
        case Right(rs) => rs
        case _ => Nil
      }
    }
  }
}
