package com.metl.snippet

import com.metl.data._
import com.metl.utils._

import com.metl.model._
import scala.xml._
import net.liftweb.http.SHtml._
import net.liftweb.http.S
import net.liftweb.util.Helpers._
import java.util.Date

object GlobalsSnippet {
  def currentUser = Text(Globals.currentUser.is match {
    case user:String if (user.length > 0) => user
    case _ => "[unknown]"
  })
}
