package com.metl.model

import dispatch._, Defaults._
import com.metl.data._
import net.liftweb.json._

object CanvasContentAnalysis {
  implicit val formats = net.liftweb.json.DefaultFormats
  val analysisThreshold = 5
  def element(c:MeTLCanvasContent) = JArray(List(JString(c.author),JInt(c.timestamp),JDouble(c.left),JDouble(c.top),JDouble(c.right),JDouble(c.bottom)))
  def chunk(es:List[MeTLCanvasContent],timeThreshold:Int=5000,distanceThreshold:Int=100) = es.sortBy(_.timestamp).groupBy(_.author).map {
    case (author,items) => {
      items.foldLeft((0L,List.empty[List[MeTLCanvasContent]])){
        case ((last,runs@(head :: tail)),item) => {
          if((item.timestamp - last) < timeThreshold){
            (item.timestamp, (item :: head) :: tail)
          }
          else{
            (item.timestamp, List(item) :: runs)
          }
        }
        case ((last,runs),item) => (item.timestamp, List(item) :: runs)
      }
    }._2.map(_.reverse)
  }
  def extract(inks:List[MeTLInk]) = {
    if(inks.size < analysisThreshold) {
      println("Not analysing themes for %s strokes".format(inks.size))
    }
    else{
      println("Loading themes for %s strokes".format(inks.size))
      val myScriptKey = "1b822746-8aa1-4b9c-8437-419f15ba71b4"
      val myScriptUrl = "cloud.myscript.com/api/v3.0/recognition/rest/analyzer/doSimpleRecognition.json";

      val json = JObject(List(
        JField("components",JArray(inks.map(i => JObject(List(
          JField("type",JString("stroke")),
          JField("x",JArray(i.points.map(i => JInt(i.x.intValue)))),
          JField("y",JArray(i.points.map(i => JInt(i.y.intValue))))))))),
        JField("parameter",JObject(List(
          JField("textParameter",JObject(List(
            JField("language",JString("en_US"))))))))))

      println(compact(render(json)))

      val req = host(myScriptUrl).secure << Map(
        ("applicationKey" -> myScriptKey),
        ("analyzerInput" -> compact(render(json))))

      req.setContentType("application/x-www-form-urlencoded","UTF-8")

      val f = Http(req OK as.String).either
      val response = for(
        s <- f.right
      ) yield {
        println(s)
        val responseJson = parse(s)
        val labels = (responseJson \\ "label")
        println(labels)
        val recognitionCandidates = labels.children.collect{ case JField(_,JString(s)) => s }
        println(recognitionCandidates)
        recognitionCandidates.map(println _)
        recognitionCandidates
      }
      response()
    }
  }
}
