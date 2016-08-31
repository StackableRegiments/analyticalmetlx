package com.metl.model

import com.opentok.{OpenTok,MediaMode,ArchiveMode,Session,SessionProperties,TokenOptions,Role}

object TokRole extends Enumeration {
  type TokRole = Value
  val Subscriber,Publisher,Moderator = Value
}

case class TokBoxSession(apiKey:Int,sessionId:String,token:String)

class TokBox(apiKey:Int,secret:String) {
  val openTok = new OpenTok(apiKey,secret)
  protected var sessions:Map[String,Session] = Map.empty[String,Session]
  def getSessionToken(description:String,role:TokRole.Value = TokRole.Subscriber):TokBoxSession = {
    val session = sessions.get(description).getOrElse({
      val newSession = openTok.createSession(
        new SessionProperties.Builder()
          .mediaMode(MediaMode.ROUTED)
  //        .location(description)
          .archiveMode(ArchiveMode.ALWAYS)
          .build()
        )
      sessions = sessions.updated(description,newSession)
      newSession
    })
    val sessionId = session.getSessionId()
    val sessionToken = session.generateToken(
      new TokenOptions.Builder()
        .role(role match {
          case TokRole.Moderator => Role.MODERATOR
          case TokRole.Publisher => Role.PUBLISHER
          case _ => Role.SUBSCRIBER
        })
        .expireTime((System.currentTimeMillis() / 1000L) + (7 * 24 * 60 * 60)) // 1 week from now
        .data("name=%s&description=%s".format(Globals.currentUser.is,description))
      .build()
    )
    TokBoxSession(apiKey,sessionId,sessionToken)
  }
}
