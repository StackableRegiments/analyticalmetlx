package com.metl.model

import com.metl.utils._
import com.metl.data._
import com.metl.liftExtensions._

import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.util.Helpers._
import java.util.{Date,Locale}
import java.text.SimpleDateFormat

object HttpResponder extends HttpCacher{
  private val snapshotExpiry = 10 seconds
  private val quizImageExpiry = 30 seconds

  def snapshot(server:String,jid:String,size:String) ={
    val serverConfig = ServerConfiguration.configForName(server)
    val binary = MeTLXConfiguration.getRoom(jid,server).getSnapshot(SnapshotSize.parse(size))
    val cachedBinary = CachedBinary(binary,new Date().getTime)
    constructResponse(cachedBinary,"image/jpg",snapshotExpiry)
  }
  def quizImage(server:String,jid:String,id:String) = {
    val serverConfig = ServerConfiguration.configForName(server)
    val binary = MeTLXConfiguration.getRoom(jid,server).getHistory.getQuizByIdentity(id).map(q => q.imageBytes.getOrElse(Array.empty[Byte]))
    val cachedBinary = binary.map(b => CachedBinary(b,new Date().getTime)).getOrElse(CachedBinary(Array.empty[Byte],new Date(0).getTime))
    constructResponse(cachedBinary,"image/jpg",quizImageExpiry)
  }
}
