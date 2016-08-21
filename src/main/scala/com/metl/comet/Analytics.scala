package com.metl.comet

import com.metl.data._
import com.metl.utils._
import com.metl.liftExtensions._

import net.liftweb._
import common._
import http._
import util._
import Helpers._
import HttpHelpers._
import actor._
import scala.xml._
import com.metl.model._
import SHtml._

import js._
import JsCmds._
import JE._
import net.liftweb.http.js.jquery.JqJsCmds._

import net.liftweb.http.js.jquery.JqJE._

import java.util.Date
import com.metl.renderer.SlideRenderer

import json.JsonAST._

import com.metl.snippet.Metl._


class Analytics extends CometActor {
  val NO_CONTEXT = <a href="/conversationSearch">Analytics not available without context.  Click here to find a conversation.</a>

  def render = name match {
    case Full(jid) if jid.trim.length > 0 => "#conversationContext *" #>  jid &
      "#bootstrap *" #> Script(OnLoad(Call("Analytics.prime",jid).cmd))
    case _ => "#conversationContext *" #> NO_CONTEXT
  }
}
