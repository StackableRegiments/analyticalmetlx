package com.metl.view

import java.io.StringWriter
import java.util.Date

import com.github.tototoshi.csv.CSVWriter
import com.metl.data.ServerConfiguration
import com.metl.model.{ConversationRoom, GlobalRoom, MeTLXConfiguration}
import net.liftweb.mapper.DB

case class RawRow(author: String, conversationJid: Int, location: String, index: Int, timestamp: Long, present: Boolean, activity: Long = 0)

case class ProcessedRow(author: String, conversationJid: Int, location: String, index: Int, duration: Int, approx: Boolean, visits: Int, activity: Long)

object ReportHelper {

  /* Activity on page is number of h2ink, h2multiwordtext, h2image for that page in conv */
  def getRoomActivity(author: String, location: String): Long = {
    // results: (List[String] headers, List[List[String]] data)
    val results = DB.runQuery("select (" +
      "(select count(*) from h2ink where author = ? and slide = ?) + " +
      "(select count(*) from h2multiwordtext where author = ? and slide = ?) + " +
      "(select count(*) from h2image where author = ? and slide = ?)" +
      ") as total", List.fill(3)(List(author, location)).flatten)
    results._2.head.head.toLong
  }

  /* Traverse via incrementing counter. Increment on enter, decrement on exit, >= 1 is "in", <= 0 is "out". */
  def getSecondsOnPage(rows: List[RawRow]): (Int, Boolean) = {
    var counter = 0
    for (r <- rows) {
      if (r.present) counter += 1 else counter -= 1
    }
    (counter, counter > 0)
  }

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

      println("Room " + room.location + ", " + slideAttendances.length + "attendances")

      slideAttendances.foreach(a => {
        // Translate a.location (jid) into index via conversation.slides
        val index = conversation.slides.find(_.id == a.location.toInt).map(_.index + 1).getOrElse(0)
        val newRow = RawRow(a.author, conversation.jid, a.location, index, a.timestamp, a.present)
        rawRows = newRow :: rawRows
      })
    })

    // Split rawRows by (1,2,3) to give a Map of Lists that have the same (1,2,3)
    val grouped = rawRows.reverse.groupBy(r => (r.author, r.conversationJid, r.location))

    var processedRows: List[ProcessedRow] = List()
    grouped.foreach(g => {
      val head = g._2.head
      val duration = getSecondsOnPage(g._2)
      processedRows = ProcessedRow(head.author, head.conversationJid, head.location, head.index,
        duration._1, duration._2, g._2.length,
        getRoomActivity(head.author, head.location)) :: processedRows
    })

    // Reassemble CSV rows from rawRows
    var rows: List[List[String]] = List(List())
    processedRows.reverse.foreach(r => {
      rows = List(r.author,
        r.conversationJid.toString,
        r.index.toString,
        r.duration.toString,
        r.visits.toString,
        r.activity.toString,
        r.approx.toString) :: rows
    })

    val stringWriter = new StringWriter()
    val writer = CSVWriter.open(stringWriter)
    writer.writeRow(List("StudentID", "ConversationID", "PageLocation", "SecondsOnPage", "VisitsToPage", "ActivityOnPage", "Approximation"))
    rows.reverse.foreach(r => writer.writeRow(r))
    writer.close()

    println("Generated student activity in %ds".format(new Date().toInstant.getEpochSecond - start.getEpochSecond))
    stringWriter.toString
  }
}
