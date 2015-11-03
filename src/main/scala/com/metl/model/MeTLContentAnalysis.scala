package com.metl.model

import dispatch._, Defaults._
import com.metl.data._
import net.liftweb.json._

object CanvasContentAnalysis {
  implicit val formats = net.liftweb.json.DefaultFormats
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
    val myScriptKey = "be12b804 - 5c13 - 4c31 - 89f3 - d49b1da5847e";
    val myScriptUrl = "http://cloud.myscript.com/api/v3.0/recognition/rest/text/doSimpleRecognition.json";

    val json = JObject(List(
      JField("components",JArray(inks.map(i => JObject(List(
        JField("type",JString("stroke")),
        JField("x",JArray(i.points.map(i => JDouble(i.x)))),
        JField("y",JArray(i.points.map(i => JDouble(i.y))))))))),
      JField("parameter",JObject(List(
        JField("textParameter",JObject(List(
          JField("language",JString("en-US"))))))))))

    val req = host(myScriptUrl).secure << Map(
      ("applicationKey" -> myScriptKey),
      ("analyzerInput" -> compact(render(json))))

    val f = Http(req OK as.String)
    val response = for(s <- f) yield {
      println(s)
    }
    response()
  }
}
