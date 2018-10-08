package com.metl.model

import com.metl.utils._
import com.metl.data._

import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.util.Helpers._
import java.util.{Date,Locale}
import java.text.SimpleDateFormat
import org.apache.commons.io._
import javax.xml.bind.DatatypeConverter

object HttpResponder extends HttpCacher with Logger {
  private val snapshotExpiry = 0 seconds
  private val quizImageExpiry = 0 seconds
  protected val server = ServerConfiguration.default
  debug("HttpResponder for server: %s".format(server))
  def getSnapshot(jid:String,size:String) = {
    val room = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid))
    val snap = room.getSnapshot(ThumbnailSizes.parse(size))
    debug("getSnapshot: (%s => %s, %s) => %s".format(jid, room, size,snap))
    snap
  }
  def getSnapshotWithPrivate(jid:String,size:String) = {
    val publicRoom = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid))
    val privateRoom = MeTLXConfiguration.getRoom(jid+Globals.currentUser.is,server.name,RoomMetaDataUtils.fromJid(jid+Globals.currentUser.is))
    val merged = publicRoom.getHistory.merge(privateRoom.getHistory)
    
    val snap = privateRoom.slideRenderer.render(merged,ThumbnailSizes.parse(size),"presentationSpace")
    debug("getSnapshotWithPrivate: (%s => (%s,%s), %s) => %s".format(jid, publicRoom, privateRoom, size, snap))
    snap
  }

  def snapshot(jid:String,size:String) ={
    val cachedBinary = CachedBinary(getSnapshot(jid,size),new Date().getTime)
    constructResponse(cachedBinary,"image/jpg",snapshotExpiry)
  }
  def snapshotWithPrivate(jid:String,size:String) ={
    val cachedBinary = CachedBinary(getSnapshotWithPrivate(jid,size),new Date().getTime)
    constructResponse(cachedBinary,"image/jpg",snapshotExpiry)
  }

  def snapshotDataUri(jid:String,size:String) = {
    val dataUri = "data:image/jpeg;base64," + DatatypeConverter.printBase64Binary(getSnapshot(jid,size))
    constructResponse(CachedBinary(dataUri.getBytes,new Date().getTime),"image/jpg",snapshotExpiry)
  }
  def quizImage(jid:String,id:String) = {
    //val serverConfig = ServerConfiguration.configForName(server)
    val binary = MeTLXConfiguration.getRoom(jid,server.name,RoomMetaDataUtils.fromJid(jid)).getHistory.getQuizByIdentity(id).map(q => q.imageBytes.getOrElse(Array.empty[Byte]))
    val cachedBinary = binary.map(b => CachedBinary(b,new Date().getTime)).getOrElse(CachedBinary(Array.empty[Byte],new Date(0).getTime))
    constructResponse(cachedBinary,"image/jpg",quizImageExpiry)
  }
}
