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
  def shouldModifyConversation(username:String, c:Conversation):Boolean = {
    username.toLowerCase.trim == c.author.toLowerCase.trim && c != Conversation.empty
  }
  def shouldDisplayConversation(c:Conversation):Boolean = {
    (c.subject.toLowerCase == "unrestricted" || Globals.getUserGroups.exists((ug:Tuple2[String,String]) => ug._2.toLowerCase.trim == c.subject.toLowerCase.trim)) && c != Conversation.empty
  }
  def shouldPublishInConversation(username:String,c:Conversation):Boolean = {
    (shouldModifyConversation(username,c) || (c.permissions.studentsCanPublish && !c.blackList.contains(username))) && c != Conversation.empty
  }
  def boardFor():String = {
    "/board"   
  }
  def boardFor(conversationJid:Int):String = {
    "/board?conversationJid=%s".format(conversationJid)
  }
  def boardFor(conversationJid:Int,slideId:Int):String = {
    "/board?conversationJid=%s&slideId=%s".format(conversationJid,slideId)
  }
  def noBoard:String = {
    "/"///conversationSearch"
  }

  lazy val serverConfig = ServerConfiguration.default
  protected def generateName:String = {
    var name = "USERNAME:%s".format(Globals.currentUser.is)
    S.param("conversationJid").foreach(cj => {
      try {
        name += "_CONVERSATION:%s".format(cj.toInt)
        val conversation = serverConfig.detailsOfConversation(cj)
        if (!shouldDisplayConversation(conversation)){
          warn("snippet.Metl is kicking the user from this conversation")
          S.redirectTo(noBoard)
        }
        S.param("slideId").foreach(sid => {
          try {
            name += "_SLIDE:%s".format(sid.toInt)
            if (shouldModifyConversation(Globals.currentUser.is,conversation)){
              MeTLXConfiguration.getRoom(cj,serverConfig.name) ! LocalToServerMeTLStanza(MeTLCommand(serverConfig,Globals.currentUser.is,-1L,"/SYNC_MOVE",List(sid)))
            }
          } catch {
            case e:Exception => {
              error("invalid argument passed in slideId: %s".format(sid),e)
            }
          }
        })
      } catch {
        case redir:ResponseShortcutException => throw redir
        case e:Exception => {
          error("invalid argument passed in conversationJid: %s".format(cj),e)
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
  def specificSimple(in:NodeSeq):NodeSeq = {
    val name = generateName
    val clazz = "lift:comet?type=SinglePageMeTLActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    warn("generating single page comet html: %s".format(output))
    output
  }
  def specificSlideDisplay(in:NodeSeq):NodeSeq = {
    val name = generateName
    val clazz = "lift:comet?type=MeTLSlideDisplayActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    warn("generating single page comet html: %s".format(output))
    output
  }
  def specific(in:NodeSeq):NodeSeq = {
    val name = generateName
    val clazz = "lift:comet?type=MeTLActor&amp;name=%s".format(name)
    val output = <span class={clazz}>{in}</span>
    warn("generating comet html: %s".format(output))
    output
  }
}
