package com.metl.view

import java.io.StringWriter
import java.util.Date

import com.github.tototoshi.csv.CSVWriter
import com.metl.data.ServerConfiguration
import com.metl.liftAuthenticator.{ForeignRelationship, Member}
import com.metl.model._
import net.liftweb.mapper.DB
import net.sf.ehcache.config.MemoryUnit
import net.sf.ehcache.store.MemoryStoreEvictionPolicy

private case class RawRow(author: String, conversationJid: Int, conversationTitle: String, conversationForeignRelationship: Option[ForeignRelationship], location: String, index: Int, timestamp: Long, present: Boolean, activity: Long = 0)

private case class ProcessedRow(author: String, conversationJid: Int, conversationTitle: String, conversationForeignRelationship: Option[ForeignRelationship], location: String, index: Int, duration: Long, approx: Boolean, visits: Int, activity: Long)

case class RowTime(timestamp: Long, present: Boolean)

object ReportHelper {

  def studentActivity(courseId: String): String = {
    println("Generating student activity...")
    val start = new Date().toInstant

    val server = ServerConfiguration.default
    val conversations = server.searchForConversationByCourse(courseId)

    /*
        val globalRoom = MeTLXConfiguration.getRoom("global", server.name, GlobalRoom(server.name))
        val conversationAttendances = globalRoom.getHistory.getAttendances
        println( "Conversation attendances: " + conversationAttendances.length)
    */

    var rawRows: List[RawRow] = List()
    conversations.foreach(conversation => {
      val room = MeTLXConfiguration.getRoom(conversation.jid.toString, server.name, ConversationRoom(server.name, conversation.jid.toString))

      // Sort attendances just in case.
      val slideAttendances = room.getHistory.getAttendances.sortBy(_.timestamp)

      println("Room " + room.location + ": " + slideAttendances.length + " attendances")

      slideAttendances.foreach(a => {
        // Translate a.location (jid) into index via conversation.slides
        val index = conversation.slides.find(_.id == a.location.toInt).map(_.index + 1).getOrElse(0)
        val newRow = RawRow(a.author, conversation.jid, conversation.title, conversation.foreignRelationship, a.location, index, a.timestamp, a.present)
        rawRows = newRow :: rawRows
      })
    })

    // Split rawRows by (1,2,3) to give a Map of Lists that have the same (1,2,3)
    val grouped = rawRows.reverse.groupBy(r => (r.author, r.conversationJid, r.location))

    var processedRows: List[ProcessedRow] = List()
    grouped.foreach(g => {
      val head = g._2.head
      val duration = getSecondsOnPage(g._2.map(r => RowTime(r.timestamp, r.present)))
      processedRows = ProcessedRow(head.author, head.conversationJid, head.conversationTitle, head.conversationForeignRelationship, head.location, head.index,
        duration._1, duration._2, g._2.length,
        getRoomActivity(head.author, head.location)) :: processedRows
    })

    // Reassemble CSV rows from rawRows
    var rows: List[List[String]] = List(List())
    processedRows.reverse.foreach(r => {
      rows = List(r.author,
        getD2LUserId(r.conversationForeignRelationship, r.author),
        r.conversationJid.toString,
        r.index.toString,
        r.duration.toString,
        r.visits.toString,
        r.activity.toString,
        r.approx.toString,
        r.conversationTitle) :: rows
    })

    val stringWriter = new StringWriter()
    val writer = CSVWriter.open(stringWriter)
    writer.writeRow(List("MeTLStudentID", "D2LStudentID", "ConversationID", "PageLocation", "SecondsOnPage", "VisitsToPage", "ActivityOnPage", "Approximation", "ConversationTitle"))
    rows.filter(r => r.nonEmpty && r.head.nonEmpty).sortWith(sortRows).foreach(r => writer.writeRow(r))
    writer.close()

    println("Generated student activity in %ds".format(new Date().toInstant.getEpochSecond - start.getEpochSecond))
    stringWriter.toString
  }

  private val config = CacheConfig(100, MemoryUnit.MEGABYTES, MemoryStoreEvictionPolicy.LRU)
  private val membersCache = new ManagedCache[(String, String), Option[List[Member]]]("d2lMembersByCourseId", (key: (String, String)) => getD2LMembers(key._1, key._2), config)

  /** Retrieve from D2L for insert into cache. */
  private def getD2LMembers(system: String, courseId: String): Option[List[Member]] = {
    val groupsProviders = Globals.getGroupsProviders.filter(gp => gp.canQuery && gp.canRestrictConversations && gp.storeId.equals(system))
    val m = groupsProviders.flatMap {
      case g: D2LGroupsProvider =>
        for {
          orgUnit <- g.getOrgUnit(courseId)
          members = g.getMembersFor(orgUnit)
        } yield {
          println("Loaded " + members.length + " members from D2L for courseId: " + courseId)
          members
        }
      case _ => None
    }
    Some(m.flatten)
  }

  private def getD2LUserId(conversationForeignRelationship: Option[ForeignRelationship], metlUser: String): String = {
    conversationForeignRelationship match {
      case Some(fr) =>
        val members = membersCache.get((fr.system, fr.key))
        members match {
          case Some(m) =>
            findUserIdInMembers(m, metlUser)
          case _ => ""
        }
      case _ => ""
    }
  }

  private def findUserIdInMembers(members: List[Member], metlUser: String): String = {
    members.find(m => m.name.toLowerCase.equals(metlUser.toLowerCase)) match {
      case Some(m) =>
        m.details.find(d => d.key.equals("OrgDefinedId")) match {
          case Some(d) => d.value
          case _ => ""
        }
      case None => ""
    }
  }

  /* Activity on page is number of h2ink, h2multiwordtext, h2image for that page in conv */
  private def getRoomActivity(author: String, location: String): Long = {
    // results: (List[String] headers, List[List[String]] data)
    val results = DB.runQuery("select (" +
      "(select count(*) from h2ink where author = ? and slide = ?) + " +
      "(select count(*) from h2multiwordtext where author = ? and slide = ?) + " +
      "(select count(*) from h2image where author = ? and slide = ?)" +
      ") as total", List.fill(3)(List(author, location)).flatten)
    results._2.head.head.toLong
  }

  /* Traverse via incrementing counter. Increment on enter, decrement on exit, >= 1 is "in", <= 0 is "out". */
  def getSecondsOnPage(pageRows: List[RowTime]): (Long, Boolean) = {
    val rows = pageRows.sortBy(_.timestamp)
//    println("Getting seconds for " + rows.length + " rows")
    var counter = 0
    var totalTime: Long = 0
    var lastTime: Long = rows.head.timestamp
    var lastPresent: Boolean = false
    for (r <- rows) {
      if (r.present) counter += 1
//      println("Timestamp: " + r.timestamp + " (c = " + counter + ", p = " + r.present + ")")
      if (counter > 0 && lastPresent) {
        // In room
        val currentDuration: Long = r.timestamp - lastTime
//        println("Adding duration: " + currentDuration)
        totalTime += currentDuration
      }
      if (!r.present) counter = 0
      lastPresent = r.present
      lastTime = r.timestamp
    }
    // Number of exits was less than the number of entries.
    val approx = counter > 0
    // Cap duration at 5 min per segment.
    val duration = Math.min(totalTime / 1000, 300)
//    println("Total time (s) in room: " + duration)
    (duration, approx)
  }

  private def sortRows(l1: List[String], l2: List[String]): Boolean = {
    if (l1.length >= 3) {
      if (l2.length >= 3) {
        val compare0 = l1.head.compareTo(l2.head)
        if (compare0 == 0) {
          val compare1 = l1(1).compareTo(l2(1))
          if (compare1 == 0)
            l1(2).compareTo(l2(2)) < 0
          else
            compare1 < 0
        }
        else
          compare0 < 0
      }
      else
        true
    }
    else
      false
  }
}
