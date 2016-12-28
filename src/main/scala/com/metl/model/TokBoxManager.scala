package com.metl.model

import com.opentok.android.{OpentokError,Session,Stream,Connection}
import net.liftweb.common._

object TokBoxHelper {
  def getSessionNamesFor(room:RoomMetaData):List[String] = {
    room match {
      case cr:ConversationRoom => List(cr.jid)
      case gs:SlideRoom if !gs.s.groupSet.isEmpty => gs.s.groupSet.flatMap(_.groups.map(_.id))
      case _ => Nil
    }
  }
  def getMonitors(room:RoomMetaData):List[TokBoxMonitor] = {
    for {
      tb <- Globals.tokBox.toList
      sn <- getSessionNamesFor(room)
      s <- tb.getSessionToken(sn,TokRole.Moderator).right.toOption.toList
    } yield {
      new TokBoxMonitor(s.apiKey.toString,s.sessionId,s.token)
    }
  }
}

class TokBoxMonitor(apiKey:String,sessionId:String,token:String) extends Session.SessionListener with Session.ArchiveListener with Session.ConnectionListener with Session.ReconnectionListener with Logger {
    protected val mSession:Session = {
      val session = new Session(new android.test.mock.MockContext(), apiKey,sessionId)
      session.setSessionListener(this)
      session.setArchiveListener(this)
      session.setConnectionListener(this)
      session
    }
    // from ArchiveListener 
    protected val archiveList = new scala.collection.mutable.ListBuffer[String]()
    override def onArchiveStarted(session:Session,archiveId:String,name:String):Unit = {
      info("archive started: %s %s".format(session.getSessionId,archiveId))
      archiveList += archiveId
    }
    override def onArchiveStopped(session:Session,archiveId:String):Unit = {
      info("archive stopped: %s %s".format(session.getSessionId,archiveId))
      archiveList -= archiveId
    }
    // from ReconnectionListener 
    override def onReconnected(session:Session):Unit = {
      info("reconnected to tokbox: %s".format(session.getSessionId))
    }
    override def onReconnecting(session:Session):Unit = {
      warn("reconnecting to tokbox: %s".format(session.getSessionId))
    }
    // from ConnectionListener
    protected val connectionList = new scala.collection.mutable.ListBuffer[String]()
    override def onConnectionCreated(session:Session,connection:Connection):Unit = {
      info("connection created: %s %s".format(session.getSessionId,connection.getConnectionId)) 
      connectionList += connection.getConnectionId
    }
    override def onConnectionDestroyed(session:Session,connection:Connection):Unit = {
      info("connection destroyed: %s %s".format(session.getSessionId,connection.getConnectionId)) 
      connectionList -= connection.getConnectionId
    }
    // from SessionListener
    override def onConnected(session:Session):Unit = {
      info("connected: %s".format(session.getSessionId))
    }
    override def onDisconnected(session:Session):Unit = {
      warn("disconnected: %s".format(session.getSessionId))
      if (keepAlive){
        mSession.connect(token)
      }
    }
    override def onError(session:Session,e:OpentokError):Unit = {
      error("error: %s %s".format(session.getSessionId(),e.getMessage()))
    }
    protected val streamList = new scala.collection.mutable.ListBuffer[String]()
    override def onStreamDropped(session:Session,stream:Stream):Unit = {
      // this should fire whenever a client stops publishing
      info("stream dropped: %s %s".format(session.getSessionId,stream.getStreamId)) 
      streamList -= stream.getStreamId()
    }
    override def onStreamReceived(session:Session,stream:Stream):Unit = {
      // this should fire whenever a client starts publishing
      info("stream created: %s %s".format(session.getSessionId,stream.getStreamId)) 
      streamList += stream.getStreamId()
    }

    protected var keepAlive = false
    def init:Unit = {
      keepAlive = true
      mSession.connect(token)
    }
    def shutdown:Unit = {
      keepAlive = false
      mSession.disconnect()
    }

    def getArchives:List[String] = {
      archiveList.toList
    }
    def getConnections:List[String] = {
      connectionList.toList
    }
    def getStreams:List[String] = {
      streamList.toList
    }
}

