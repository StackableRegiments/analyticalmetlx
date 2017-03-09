package com.metl.view

import java.io.StringWriter
import java.util.Date

import com.github.tototoshi.csv.CSVWriter
import com.metl.data.ServerConfiguration
import com.metl.model.{ConversationRoom, MeTLXConfiguration}

case class RawRow(author:String, conversationJid:Int, index:Int, timestamp:Long, present:Boolean, activity:Long=0)

object ReportHelper {

  /* Activity on page is number of h2ink, h2multiwordtext, h2image, h2quizresponse for that page in conv */
  def getRoomActivity(conversationJid: Int, index: Int):Long = {
    0
  }

  /* Traverse via incrementing counter.
    Increment on enter, decrement on exit, >= 1 is "in", <= 0 is "out".
    Presume some sensible "reset moment" if possible server crash or restart. */
  def getSecondsOnPage(rows:List[RawRow]):(Int,Boolean) = {
    var counter = 0
    for(r <- rows) {
      if(r.present) counter += 1 else counter -= 1
    }
    //    rows.reverse.foldLeft(counter)(_.timestamp + _.timestamp  )
    (counter, counter > 0)
  }

  def studentActivity(courseId:String):String = {
    println("Generating student activity...")
    val start = new Date().toInstant

    val server = ServerConfiguration.default
    val conversations = server.searchForConversationByCourse(courseId)
    println("Conversations: " + conversations.length)

    var rawRows:List[RawRow] = List()
    conversations.foreach(conversation => {
      val room = MeTLXConfiguration.getRoom(conversation.jid.toString, server.name, ConversationRoom(server.name, conversation.jid.toString))
      println( "Room location: " + room.location)

      // Sort attendances just in case.
      val attendances = room.getAttendances.sortBy(_.timestamp)
      println( "Attendances: " + attendances.length)

      conversation.slides.foreach(s => println("Slide" + s.index + " id: " + s.id))

      attendances.foreach(a => {
        println("Attendance location: " + a.location)

        // Translate a.location (jid) into index via conversation.slides
        val index = conversation.slides.find(_.id == a.location.toInt).map(_.index + 1).getOrElse(0)

        val newRow = RawRow(a.author, conversation.jid, index, a.timestamp, a.present)
        rawRows = newRow :: rawRows
      })
    })

    // TODO: split rawRows by (1,2,3) then group results

    // Reassemble CSV rows from rawRows
    var rows:List[List[String]] = List(List())
    rawRows.reverse.foreach(r => {
      rows = List(r.author,
        r.conversationJid.toString,
        r.index.toString,
        r.timestamp.toString,
        getRoomActivity(r.conversationJid, r.index).toString) :: rows
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
