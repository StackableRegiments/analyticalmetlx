package com.metl.model

import net.liftweb.common._
import net.liftweb.util.Helpers._

object TimeSpanParser {
  val unitLookup:Map[String,Long] = Map(
    "milis" -> 1,
    "miliseconds" -> 1,
    "milisecond" -> 1,
    "minute" -> 60 * 1000,
    "minutes" -> 60 * 1000,
    "second" -> 1000,
    "seconds" -> 1000,
    "hour" -> 60 * 60 * 1000,
    "hours" -> 60 * 60 * 1000,
    "day" -> 24 * 60 * 60 * 1000,
    "days" -> 24 * 60 * 60 * 1000,
    "week" -> 7 * 24 * 60 * 60 * 1000,
    "weeks" -> 7 * 24 * 60 * 60 * 1000
  )
  def parse(in:String):TimeSpan = {
    try {
      val segments = in.split(",").toList
      val counts = segments.flatMap(seg => seg.trim.split(" ").toList match {
        case List(number,unit) => unitLookup.get(unit).map(m => m * number.toInt)
        case _ => None
      })
      val total = counts.foldLeft(0L)((acc,item) => acc + item)
      TimeSpan.apply(total)
    } catch {
      case e:Exception => {
        5 minutes
      }
    }
  }
}
