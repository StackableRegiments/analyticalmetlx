package com.metl.model

import com.metl.utils._
import com.metl.data._

import net.liftweb.common._
import net.liftweb.http._
import scala.xml._
import java.util.Date

object QuizResponder {
  def handleResponse(serverName:String,conversationId:String,slideId:String,quizId:String,response:String) ={
    val server = ServerConfiguration.configForName(serverName)
    MeTLXConfiguration.getRoom(conversationId,server.name) ! LocalToServerMeTLStanza(MeTLQuizResponse(server,Globals.currentUser.is,new Date().getTime,response,Globals.currentUser.is,quizId))
    RedirectResponse("/quiz?server=%s&conversation=%s&slide=%s&quiz=%s".format(server.name,conversationId,slideId,quizId))
  }
}
