package com.metl.view

import java.io.StringWriter

import com.github.tototoshi.csv.CSVWriter
import com.metl.data.ServerConfiguration
import com.metl.model.{ConversationRoom, MeTLXConfiguration}

object ReportHelper {
  def studentActivity(courseId:String):String = {
    println("Generating student activity...")

    val stringWriter = new StringWriter()
    val writer = CSVWriter.open(stringWriter)

    val server = ServerConfiguration.default
    val conversations = server.searchForConversationByCourse(courseId)
    println("Conversations: " + conversations.length)

    var rawRows:List[List[String]] = List(List())
    conversations.foreach(c => {
      val r = MeTLXConfiguration.getRoom(c.jid.toString, server.name, ConversationRoom(server.name, c.jid.toString))
      println( "Room: " + r.location)

      // Sort attendances just in case.
      val attendances = r.getAttendances.sortBy(_.timestamp)
      println( "Attendances: " + attendances.length)

      c.slides.foreach(s => println("Slide" + s.index + " id: " + s.id))

      attendances.foreach(a => {
        // Translate a.location (jid) into index via c.slides
        println("Attendance location: " + a.location)

        val index = c.slides.find(_.id == a.location).map(_.index).getOrElse(0)
        val newRow:List[String] = List(a.author, c.jid.toString, index.toString, a.timestamp.toString)
        rawRows = newRow :: rawRows
      })
    })

    // Traverse via incrementing counter.
    // Increment on enter, decrement on exit, >= 1 is "in", <= 0 is "out".
    // Presume some sensible "reset moment" if possible server crash or restart.
    var rows:List[List[String]] = List(List())
    rawRows.reverse.foreach(r => {
      val duration = 0
    })

    // Activity on page is number of h2ink, h2multiwordtext, h2image, h2quizresponse for that page in conv

    writer.writeRow(List("StudentID", "ConversationID", "PageLocation", "SecondsOnPage", "VisitsToPage", "ActivityOnPage", "Approximation"))
    rows.foreach(r => writer.writeRow(r))

    //      val providers = Globals.getGroupsProviders
    //      providers.foreach(p => writer.writeRow(List(p.storeId, p.name, """here, "it" is""", "5,2,3", "2", "lots", "y")))


    /*
          writer.writeRow(List("jid", "title", "subject", "fr.key"))
          //      val conversations = server.searchForConversation("d")
          conversations.foreach(c =>
            {
              writer.writeRow(List(c.jid, c.title, c.subject, c.foreignRelationship.map(_.key).getOrElse("")))
            })
    */

    /*
          val provider = Globals.getGroupsProvider("d2l_test")
          provider.foreach({
            p =>
              println("Found groups provider: " + p.name)
    //          val orgUnit = p.getOrgUnit("CO_SWK-640-MWOL6-2017SP1")
              val orgUnit = p.getOrgUnit(courseId)
              orgUnit.foreach(ou => println("Found org unit: " + ou.name))
          })
    */
    writer.close()

    println("Generated student activity.")
    stringWriter.toString
  }
}
