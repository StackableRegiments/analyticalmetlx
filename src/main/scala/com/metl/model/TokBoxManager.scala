package com.metl.model

//import com.opentok.android.{OpentokError,Session,Stream,Connection}
import net.liftweb.common._

object TokBoxHelper extends Logger {
  def getSessionNamesFor(room:RoomMetaData):List[String] = {
    room match {
      case cr:ConversationRoom => List(cr.jid)
      case gs:SlideRoom if !gs.s.groupSet.isEmpty => gs.s.groupSet.flatMap(_.groups.map(_.id))
      case _ => Nil
    }
  }
  def getMonitors(room:RoomMetaData):List[TokBoxMonitor] = {
    try {
      for {
        tb <- Globals.tokBox.toList
        sn <- getSessionNamesFor(room)
        s <- tb.getSessionToken(sn,TokRole.Moderator).right.toOption.toList
      } yield {
        new TokBoxMonitor(s)
      }
    } catch {
      case e:Throwable => {
        error("tokboxHelper getMonitors error",e)
        Nil
      }
    }
  }
}

class TokBoxMonitor(tokboxSession:TokBoxSession) extends Logger {
    protected val oldArchives:scala.collection.mutable.ListBuffer[TokBoxArchive] = new scala.collection.mutable.ListBuffer[TokBoxArchive]()
    protected var archive:Option[TokBoxArchive] = None
    protected var broadcast:Option[TokBoxBroadcast] = None
    def init:Unit = {
      for {
        tb <- Globals.tokBox.toList
      } yield {
        startArchive(tb)
        startBroadcast(tb)
      }
    }
    def shutdown:Unit = {
      for {
        tb <- Globals.tokBox.toList
      } yield {
        stopBroadcast(tb)
        stopArchive(tb)
      }
    }
    def rollArchive:Unit = {
      for {
        tb <- Globals.tokBox.toList
      } yield {
        oldArchives ++= archive.toList
        stopArchive(tb)
        startArchive(tb)
      }
    }
    def getArchives:List[TokBoxArchive] = oldArchives.toList ::: archive.toList
    def getCurrentArchive:Option[TokBoxArchive] = archive
    def getBroadcasts:Option[TokBoxBroadcast] = broadcast

    protected def startArchive(tb:TokBox) = {
      archive = Some(tb.startArchive(
        session = tokboxSession,
        compositedVideo = false
      ))
    }
    protected def stopArchive(tb:TokBox) = {
      archive.foreach(a => tb.stopArchive(tokboxSession,a))
    }
    protected def startBroadcast(tb:TokBox) = {
      broadcast = Some(tb.startBroadcast(tokboxSession,""))
    }
    protected def stopBroadcast(tb:TokBox) = {
      broadcast.foreach(b => tb.stopBroadcast(tokboxSession,b.id))
    }
}

