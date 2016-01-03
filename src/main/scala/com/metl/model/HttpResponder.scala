package com.metl.model

import com.metl.utils._
import com.metl.data._
import com.metl.liftExtensions._

import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.util.Helpers._
import java.util.{Date,Locale}
import java.text.SimpleDateFormat
import org.apache.commons.io._
import javax.xml.bind.DatatypeConverter

object HttpResponder extends HttpCacher{
  private val snapshotExpiry = 10 seconds
  private val quizImageExpiry = 30 seconds
  protected val server = ServerConfiguration.default
  protected def getSnapshot(jid:String,size:String) = {
    MeTLXConfiguration.getRoom(jid,server.name).getSnapshot(size.trim.toLowerCase match {
      case "thumbnail" => Globals.ThumbnailSize
      case "small" => Globals.SmallSize
      case "medium" => Globals.MediumSize
      case "large" => Globals.LargeSize
      case _ => Globals.ThumbnailSize
    })
  }
  def snapshot(jid:String,size:String) ={
    val cachedBinary = CachedBinary(getSnapshot(jid,size),new Date().getTime)
    constructResponse(cachedBinary,"image/jpg",snapshotExpiry)
  }
  def snapshotDataUri(jid:String,size:String) = {
    val dataUri = "data:image/jpeg;base64," + DatatypeConverter.printBase64Binary(getSnapshot(jid,size))
    constructResponse(CachedBinary(IOUtils.toByteArray(dataUri),new Date().getTime),"image/jpg",snapshotExpiry)
  }
  def quizImage(jid:String,id:String) = {
    //val serverConfig = ServerConfiguration.configForName(server)
    val binary = MeTLXConfiguration.getRoom(jid,server.name).getHistory.getQuizByIdentity(id).map(q => q.imageBytes.getOrElse(Array.empty[Byte]))
    val cachedBinary = binary.map(b => CachedBinary(b,new Date().getTime)).getOrElse(CachedBinary(Array.empty[Byte],new Date(0).getTime))
    constructResponse(cachedBinary,"image/jpg",quizImageExpiry)
  }
}
