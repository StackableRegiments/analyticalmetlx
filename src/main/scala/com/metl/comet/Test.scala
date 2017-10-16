package com.metl.comet

import net.liftweb._
import common._
import http._
import util._
import Helpers._
import actor._

import scala.xml._

object TestCounter {
  protected var count:Int = 0
  def add:Unit = {
    synchronized {
      count += 1
    }
  }
  def remove:Unit = {
    synchronized {
      count -= 1
    }
  }
  def getCount:Int = count
}

class TestActor extends CometActor with Logger {
  override def lifespan = Full(1 minute)
  override def render = <span>comet created successfully, {TestCounter.getCount.toString()} live</span>
  override def localSetup = {
    super.localSetup
    TestCounter.add
  }
  override def localShutdown = {
    super.localShutdown
    TestCounter.remove
  }
  override def lowPriority = {
    case _ => {}
  }
}

object SessionMonitor extends LiftActor {
  protected var currentInfo:Option[SessionWatcherInfo] = None
  override def messageHandler = {
    case swi:SessionWatcherInfo => {
      currentInfo = Some(swi)
    }
    case _ => {}
  }
  def getSessionInfo:Option[SessionWatcherInfo] = currentInfo
}
