package com.metl.snippet

import com.metl.model.Globals
import net.liftweb._
import http._
import SHtml._
import common._
import util._
import Helpers._

object ThemeChooser {
  def render = "#themeCss [href]" #> "/static/assets/styles/%s/main.css".format(Globals.themeName)
}
