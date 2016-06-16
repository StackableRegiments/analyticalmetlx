package com.metl.model
import java.net.URLEncoder.encode
import net.liftweb.common._
import scala.xml._
import com.metl.model._
import com.metl.utils._

object OneNote {
  val authUrl = initialize(Globals.configurationFileLocation)
  def initialize(filePath:String) = {
    val propFile = XML.load(filePath)
    val root = propFile \\ "oneNote"
    val redirectUrl = encode((root \ "redirectUrl").text, "utf-8")
    val scopes = encode((root \ "scopes").text, "utf-8")
    val clientId = encode((root \ "clientId").text,"utf-8")
    val responseType = "code"
    "https://login.live.com/oauth20_authorize.srf?client_id=%s&scope=%s&response_type=%s&redirect_uri=%s".format(clientId,scopes,responseType,redirectUrl)
  }
  def export(conversationJid:String,token:String):Box[String] = {
    Full("exported")
  }
}
