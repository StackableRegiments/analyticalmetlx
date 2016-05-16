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

object Metl extends Metl
class Metl extends Logger {
  protected def shouldDisplayConversation(c:Conversation):Boolean = {
    (c.subject.toLowerCase == "unrestricted" || Globals.getUserGroups.exists((ug:Tuple2[String,String]) => ug._2.toLowerCase.trim == c.subject.toLowerCase.trim)) && c != Conversation.empty
  }
  protected def generateName:String = {
    var name = "USERNAME:%s".format(Globals.currentUser.is)
    S.param("conversationJid").foreach(cj => {
      try {
        name += "_CONVERSATION:%s".format(cj.toInt)
        val conversation = ServerConfiguration.default.detailsOfConversation(cj)
        if (!shouldDisplayConversation(conversation)){
          warn("snippet.Metl is kicking the user from this conversation")
          S.redirectTo("/conversationSearch")
        }
      } catch {
        case redir:ResponseShortcutException => throw redir
        case e:Exception => {
          error("invalid argument passed in conversationJid: %s".format(cj),e)
        }
      }
    })
    S.param("slideId").foreach(sid => {
      try {
        name += "_SLIDE:%s".format(sid.toInt)
      } catch {
        case e:Exception => {
          error("invalid argument passed in slideId: %s".format(sid),e)
        }
      }
    })
    S.param("unique").foreach(uniq => {
      try {
        if (uniq.toLowerCase.trim == "true"){
          name += "_UNIQUE:%s".format(nextFuncName)
        }
      } catch {
        case e:Exception => {
          error("invalid argument passed in unique: %s".format(uniq),e)
        }
      }
    })
    S.param("showTools").foreach(tools => {
      try {
        if (tools.toLowerCase.trim == "true"){
          name += "_SHOWTOOLS:%s".format(true)
        } else {
          name += "_SHOWTOOLS:%s".format(false)
        }
      } catch {
        case e:Exception => {
          error("invalid argument passed in showTools: %s".format(tools),e)
        }
      }
    })
    name
  }
  def getShowToolsFromName(in:String):Option[Boolean] = {
    in.split("_").map(_.split(":")).find(_(0) == "SHOWTOOLS").map(_.drop(1).mkString(":")).flatMap(showToolsString => {
      try {
        Some(showToolsString.toBoolean)
      } catch {
        case e:Exception => {
          error("invalid argument passed in showToolsString: %s".format(showToolsString),e)
          None
        }
      }
    })
  }
  def getConversationFromName(in:String):Option[Int] = {
    in.split("_").map(_.split(":")).find(_(0) == "CONVERSATION").map(_.drop(1).mkString(":")).flatMap(convString => {
      try {
        Some(convString.toInt)
      } catch {
        case e:Exception => {
          error("invalid argument passed in convString: %s".format(convString),e)
          None
        }
      }
    })
  }
  def getSlideFromName(in:String):Option[Int] = {
    in.split("_").map(_.split(":")).find(_(0) == "SLIDE").map(_.drop(1).mkString(":")).flatMap(slideString => {
      try {
        Some(slideString.toInt)
      } catch {
        case e:Exception => {
          error("invalid argument passed in slideString: %s".format(slideString),e)
          None
        }
      }
    })
  }
  def getUserFromName(in:String):Option[String] = {
    in.split("_").map(_.split(":")).find(_(0) == "USERNAME").map(_.drop(1).mkString(":"))
  }
  def specific = {
    val name = generateName
    val clazz = "lift:comet?type=MeTLActor&amp;name=%s".format(name)
    val output = <span class={clazz} />
    warn("generating comet html: %s".format(output))
    output
  }
}
