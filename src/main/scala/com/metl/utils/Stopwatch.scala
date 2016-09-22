package com.metl.utils

import java.util.Date

import net.liftweb.actor._
import net.liftweb.common.Logger

case class TimerResult(label:String,startTime:Long,duration:Long)

object StopwatchActor extends LiftActor with Logger {
  protected val defaultConsoleInterest = 1000
  val consoleInterest = System.getProperty("metl.stopwatch.minimumLog") match {
    case s:String if s.length > 0 => {
      try {
        s.toInt
      } catch {
        case e:Throwable => defaultConsoleInterest
      }
    }
    case _ => defaultConsoleInterest
  }
  override def messageHandler ={
    case r@TimerResult(label,start,duration) => {
      if (duration > consoleInterest)
        info("[%s] miliseconds [%s]".format(duration,label))
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
    val zero = new Date().getTime
      ()=>{
      val elapsed = new Date().getTime - zero
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
