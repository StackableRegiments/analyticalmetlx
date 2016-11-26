package com.metl.utils

import java.util.Date

import net.liftweb.actor._
import net.liftweb.common.Logger

case class TimerResult(label:String,startTime:Long,duration:Long)

object StopwatchActor extends LiftActor with Logger {
  protected def toMilis(nanos:Long):Double = {
    nanos / (1000 * 1000d)
  }
  protected val defaultConsoleInterest = 1000 * 1000 * 1000 // 1 second
  val consoleInterest = System.getProperty("metl.stopwatch.minimumLog") match {
    case s:String if s.length > 0 => {
      try {
        s.toInt * 1000 * 1000
      } catch {
        case e:Throwable => defaultConsoleInterest
      }
    }
    case _ => defaultConsoleInterest
  }
  override def messageHandler ={
    case r@TimerResult(label,start,duration) => {
      if (duration > consoleInterest)
        info("Stopwatch [%.3fms] [%s]".format(toMilis(duration),label))
    }
    case _ => warn("StopwatchActor received unknown message")
  }
}

object Stopwatch extends Logger {
  val stopwatchEnabled = System.getProperty("metl.stopwatch.enabled") match {
    case s:String if s.toLowerCase.trim == "true" => true
    case _ => false
  }
  private def start(label:String) ={
    val zero = System.nanoTime()//new Date().getTime
      ()=>{
      val elapsed = System.nanoTime() - zero //new Date().getTime - zero
      StopwatchActor ! TimerResult(label,zero,elapsed)
    }
  }
  def time[T](label: => String,action: => T) = {
    if (stopwatchEnabled){
      val timer = Stopwatch.start(label)
      val result = action
      timer()
      result
    } else {
      action
    }
  }
}
