package com.metl.model

import com.metl.data._
import com.metl.utils._


object Stats {
  def uniqueUsersOfSlide(server:ServerConfiguration,slideJid:Int):List[String] = Stopwatch.time("Stats.uniqueUsersOfSlide", {
    try {
      server.getHistory(slideJid.toString).getAll.map(i => i match {
        case m:MeTLStanza => m.author
        case _ => "noAuthorFound"
      }).filterNot(i => i == "noAuthorFound" || i == "").distinct
    } catch {
      case e:Throwable => List.empty[String]
    }
  })
  def uniqueUsersOfConversation(server:ServerConfiguration,convJid:Int):List[String] = Stopwatch.time("Stats.uniqueUsersOfConversation", {
    try {
      (convJid :: server.detailsOfConversation(convJid.toString).slides.map(s => s.id).toList).map(j => uniqueUsersOfSlide(server,j)).flatten.distinct
    } catch {
      case e:Throwable => {
        List.empty[String]
      }
    }
  })
  def uniqueUsersOfConversations(convJids:List[Int]):List[(Int,List[String],Int)] = {
    val servers = List("deified","reifier").map(s => ServerConfiguration.configForName(s))
    convJids.map(cj => {
      val list = servers.map(s => uniqueUsersOfConversation(s,cj)).flatten.distinct
      (cj,list,list.length)
    })
  }
}
