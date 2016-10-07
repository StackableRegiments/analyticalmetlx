package com.metl.snippet

import com.metl.data._
import com.metl.utils._

import net.liftweb._
import http._
import SHtml._
import common._
import util._
import Helpers._
import scala.xml._
import com.metl.comet._
import com.metl.model._
import Globals._

import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.JsonAST._

class Analytics extends Logger {
  def render(in:NodeSeq):NodeSeq = S.param("source").map(source => {
    info("Loading dashboard for %s".format(source))
    val clazz = "lift:comet?type=Analytics&amp;name=%s".format(source)
    <div class={clazz}>{in}</div>
  }).openOr(<div>No context provided</div>)
}
