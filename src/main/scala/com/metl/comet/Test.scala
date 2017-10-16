package com.metl.comet

import net.liftweb._
import common._
import http._
import util._
import Helpers._
import actor._

import scala.xml._

class TestActor extends CometActor with Logger {
  override def lifespan = Full(1 minute)
  override def render = NodeSeq.Empty
  protected var hasSetup = false
  protected var hasShutdown = false
  override def localSetup = {
    warn("creating testActor: %s".format(this))
    super.localSetup
    hasSetup = true
    warn("created testActor: %s".format(this))
  }
  override def localShutdown = {
    warn("shutting down testActor: %s".format(this))
    super.localShutdown
    hasShutdown = true
    warn("shutdown testActor: %s".format(this))
  }
  override def lowPriority = {
    case _ => {}
  }
}
