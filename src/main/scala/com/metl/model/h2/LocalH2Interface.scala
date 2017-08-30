package com.metl.h2

import com.metl.data._
import com.metl.utils._
import com.metl.persisted._
import com.metl.h2.dbformats._
import java.util.Date

import net.liftweb.mapper._
import net.liftweb.common._

import scala.compat.Platform.EOL
import _root_.net.liftweb.mapper.{DB => _, DefaultConnectionIdentifier => _, Schemifier => _, StandardDBVendor => _, _}
import _root_.java.sql.{Connection, DriverManager}
import java.time.format.DateTimeFormatter
import java.time._

import com.metl.model.Globals
import com.metl.model.Globals._

class H2Interface(config:ServerConfiguration,filename:Option[String],onConversationDetailsUpdated:Conversation=>Unit) extends SqlInterface(config,new StandardDBVendor("org.h2.Driver", filename.map(f => "jdbc:h2:%s;AUTO_SERVER=TRUE".format(f)).getOrElse("jdbc:h2:mem:%s".format(config.name)),Empty,Empty){
  //adding extra db connections - it defaults to 4, with 20 being the maximum
  override def allowTemporaryPoolExpansion = false
  override def maxPoolSize = 500
  override def doNotExpandBeyond = 500
},onConversationDetailsUpdated,500) {
}

class SqlInterface(config:ServerConfiguration,vendor:StandardDBVendor,onConversationDetailsUpdated:Conversation=>Unit,startingPool:Int = 0,maxPoolSize:Int = 0) extends PersistenceInterface(config) with Logger{
  val configName = config.name
  val serializer = new H2Serializer(config)

  override def shutdown = {
    vendor.closeAllConnections_!
    true
  }

  override def isReady = {
    if (!DB.jndiJdbcConnAvailable_?) {
      DB.defineConnectionManager(DefaultConnectionIdentifier, vendor)
    }
    Schemifier.schemify(true,Schemifier.infoF _,
      List(
        H2Ink,
        H2MultiWordText,
        H2Text,
        H2Image,
        H2Video,
        H2DirtyInk,
        H2DirtyText,
        H2DirtyImage,
        H2DirtyVideo,
        H2MoveDelta,
        H2Quiz,
        H2QuizResponse,
        H2Command,
        H2Submission,
        H2Conversation,
        H2Attendance,
        H2File,
        H2VideoStream,
        H2Resource,
        H2ContextualizedResource,
        H2UnhandledCanvasContent,
        H2UnhandledStanza,
        H2UnhandledContent,
        DatabaseVersion,
        H2Theme,
        H2Grade,
        H2NumericGradeValue,
        H2BooleanGradeValue,
        H2TextGradeValue,
        H2ChatMessage,
        H2UndeletedCanvasContent
      ):_*
    )
    // this starts our pool in advance
    Range(0,Math.min(Math.max(startingPool,0),maxPoolSize)).toList.flatMap(ci => {
      val c = vendor.newConnection(DefaultConnectionIdentifier)
      debug("starting connection: %s => %s".format(ci,c))
      c
    }).foreach(c => {
      vendor.releaseConnection(c)
    })

    //database migration script actions go here.  No try/catch, because I want to break if I can't bring it up to an appropriate version.
    DatabaseVersion.find(By(DatabaseVersion.key,"version"),By(DatabaseVersion.scope,"db")).getOrElse({
      DatabaseVersion.create.key("version").scope("db").intValue(-1).save
    })
    DatabaseVersion.find(By(DatabaseVersion.key,"version"),By(DatabaseVersion.scope,"db")).filter(_.intValue.get < 2).map(versionNumber => {
      info("upgrading db to use partialIndexes for mysql limitations (v2)")
      // update the partialIndexes if it's under version 2, and then increment to version 2
      H2Resource.findAllFields(List(H2Resource.id,H2Resource.identity,H2Resource.partialIdentity)).foreach(res => {
        res.partialIdentity(res.identity.get match {
          case null => ""
          case other => other.take(H2Constants.identity)
        }).save
      })
      H2File.findAllFields(List(H2File.id,H2File.identity,H2File.partialIdentity)).foreach(file => {
        file.partialIdentity(file.identity.get match {
          case null => ""
          case other => other.take(H2Constants.identity)
        }).save
      })
      versionNumber.intValue(2).save
      info("upgraded db to use partialIndexes for mysql limitations (v2)")
      versionNumber
    }).foreach(versionNumber => info("using dbSchema version: %s".format(versionNumber.intValue.get)))
    DatabaseVersion.find(By(DatabaseVersion.key,"version"),By(DatabaseVersion.scope,"db")).filter(_.intValue.get < 4).map(versionNumber => {
      info("upgrading db to switch conversation creation from strings to longs (v3)")
      val dateFormats = List(
        new java.text.SimpleDateFormat("dd/MM/yyyy h:mm:ss a"), // I think this is a C# date format -- "dd/MM/yyyy  22/05/2016 1:27:47 AM.  Of course, it misses the original timezone, so let's hope that it doesn't adjust it too badly.
        new java.text.SimpleDateFormat("EEE MMM dd kk:mm:ss z yyyy") // this is the standard java format, which is what we've been using.
      )
      H2Conversation.findAllFields(List(H2Conversation.id,H2Conversation.created,H2Conversation.creation,H2Conversation.lastAccessed)).foreach(conv => {
        if (conv.creation.get == null || conv.creation.get < 1){
          var result = conv.lastAccessed.get
          val creationString = conv.created.get
          dateFormats.foreach(df => {
            try {
              result = df.parse(creationString).getTime()
            } catch {
              case e:Exception => {}
            }
          })
          conv.creation(result).save
        }
      })
      versionNumber.intValue(4).save
      info("upgraded db to switch conversation creation from strings to longs (v3)")
      versionNumber
    }).foreach(versionNumber => info("using dbSchema version: %s".format(versionNumber.intValue.get)))
    DatabaseVersion.find(By(DatabaseVersion.key,"version"),By(DatabaseVersion.scope,"db")).filter(_.intValue.get < 5).map(versionNumber => {
      info("upgrading db to use room to position themes, not location, because room is indexed")
      H2Theme.findAll.foreach(theme => {
        if (theme.room.get == null){
          theme.room(theme.location.get).save
        }
      })
      versionNumber.intValue(5).save
      info("upgraded db to use room to position themes, which is indexed, where location wasn't.")
      versionNumber
    }).foreach(versionNumber => info("using dbSchema version: %s".format(versionNumber.intValue.get)))
    DatabaseVersion.find(By(DatabaseVersion.key,"version"),By(DatabaseVersion.scope,"db")).filter(_.intValue.get < 6).map(versionNumber => {
      info("upgrading db to hold a copy of the mockdata")
      val mockRoom = "mock|data"
      H2Image.findAll(By(H2Image.room,mockRoom)).foreach(i => {
        val id = i.source.get
        H2Resource.find(By(H2Resource.partialIdentity,id.take(H2Constants.identity)),By(H2Resource.identity,id)).foreach(r => {
          r.delete_!
        })
        i.delete_!
      })
      H2Ink.findAll(By(H2Ink.room,mockRoom)).foreach(_.delete_!)
      H2MultiWordText.findAll(By(H2MultiWordText.room,mockRoom)).foreach(_.delete_!)
      H2MoveDelta.findAll(By(H2MoveDelta.room,mockRoom)).foreach(_.delete_!)
      val h = MockData.mockHistoryValue(config)
      val getId = {
        var innerId = 0
        () => {
          innerId += 1
          innerId
        }
      }
      h.getAll.foreach{
        case i:MeTLImage => {
          i.imageBytes.foreach(bytes => {
            val id = config.postResource(mockRoom,"mock_imageResource_%s".format(getId()),bytes)
            H2Resource.find(By(H2Resource.partialIdentity,id.take(H2Constants.identity)),By(H2Resource.identity,id)).foreach(r => {
              val newImageStanza = i.copy(source = Full(id))
              val foundBytes = config.getResource(id)
              storeStanza(mockRoom,newImageStanza)
            })
          })
        }
        case s:MeTLStanza => storeStanza(h.jid,s)
      }
      versionNumber.intValue(6).save
      info("upgraded db to hold a copy of the mockdata")
      versionNumber
    }).foreach(versionNumber => info("using dbSchema version: %s".format(versionNumber.intValue.get)))
    DatabaseVersion.find(By(DatabaseVersion.key,"version"),By(DatabaseVersion.scope,"db")).filter(_.intValue.get < 7).map(versionNumber => {
      info("upgrading db to set all slides to exposed, because they were previously defaulting to false and we didn't mean that.")
      H2Conversation.findAll.foreach(conv => {
        conv.slides(serializer.slidesToString(serializer.slidesFromString(conv.slides.get).map(_.copy(exposed = true)))).save
      })
      versionNumber.intValue(7).save
      info("upgraded db to set all slides to exposed, because they were previously defaulting to false and we didn't mean that.")
      versionNumber
    }).foreach(versionNumber => info("using dbSchema version: %s".format(versionNumber.intValue.get)))
    DatabaseVersion.find(By(DatabaseVersion.key,"version"),By(DatabaseVersion.scope,"db")).filter(_.intValue.get < 8).map(versionNumber => {
      info("upgrading db to set all future conversation-created dates to 1/7/2016, because they were created by erroneous C# clients and are being reset to the data origin date.")
      val defaultZoneId = ZoneId.of("America/New_York")
      // Origin date is 1 July 2016
      val zonedDateTime = ZonedDateTime.of(LocalDate.of(2016,7,1),LocalTime.of(0,0),defaultZoneId)
      val originDateString = zonedDateTime.format(DateTimeFormatter.ofPattern("EEE MMM dd kk:mm:ss z yyyy"))
      val originDateLong = Instant.from(zonedDateTime).toEpochMilli
      val nowPlusADay = Instant.from(ZonedDateTime.now(defaultZoneId).plusDays(1)).toEpochMilli
      val futureConversationCreateds = H2Conversation.findAll.filter(_.creation > nowPlusADay)
      trace("found %s future conversation createds".format(futureConversationCreateds.length))
      futureConversationCreateds.foreach(h2Conv => {
        trace("updating %s created to %s".format(h2Conv.jid,originDateString))
        h2Conv.created(originDateString).save
        h2Conv.creation(originDateLong).save
      })
      val futureConversationLastAccesseds = H2Conversation.findAll.filter(_.lastAccessed > nowPlusADay)
      trace("found %s future conversation last accesseds".format(futureConversationLastAccesseds.length))
      futureConversationLastAccesseds.foreach(h2Conv => {
        trace("updating %s last accessed to %s".format(h2Conv.jid,originDateString))
        h2Conv.lastAccessed(originDateLong).save
      })
      versionNumber.intValue(8).save
      info("upgraded db to set all future conversation-created dates to 1/7/2016, because they were created by erroneous C# clients and are being reset to the data origin date.")
      versionNumber
    }).foreach(versionNumber => info("using dbSchema version: %s".format(versionNumber.intValue.get)))
    true
  }
  type H2Object = Object
  val RESOURCES = "resource"
  val CONVERSATIONS = "conversation"
  val INKS = "ink"
  val TEXTS = "text"
  val MULTITEXTS = "multiWordText"
  val IMAGES = "image"
  val DIRTYINKS = "dirtyInk"
  val DIRTYTEXTS = "dirtyText"
  val DIRTYIMAGES = "dirtyImage"
  val MOVEDELTAS = "moveDelta"
  val SUBMISSIONS = "submission"
  val QUIZZES = "quiz"
  val QUIZRESPONSES = "quizResponse"
  val ATTENDANCES = "attendance"
  val COMMANDS = "command"

  //stanzas table
  def storeStanza[A <: MeTLStanza](jid:String,stanza:A):Option[A] = Stopwatch.time("H2Interface.storeStanza",{
    val transformedStanza:Option[_ <: H2MeTLStanza[_]] = stanza match {
      case s:MeTLStanza if s.isInstanceOf[Attendance] => Some(serializer.fromMeTLAttendance(s.asInstanceOf[Attendance]).room(jid))
      case s:Attendance => Some(serializer.fromMeTLAttendance(s).room(jid)) // for some reason, it just can't make these match
      case s:MeTLStanza if s.isInstanceOf[MeTLTheme] => Some(serializer.fromTheme(s.asInstanceOf[MeTLTheme]).room(jid))
      case s:MeTLTheme => Some(serializer.fromTheme(s).room(jid))
      case s:MeTLChatMessage => Some(serializer.fromChatMessage(s).room(jid))
      case s:MeTLInk => Some(serializer.fromMeTLInk(s).room(jid))
      case s:MeTLMultiWordText => Some(serializer.fromMeTLMultiWordText(s).room(jid))
      case s:MeTLText => Some(serializer.fromMeTLText(s).room(jid))
      case s:MeTLImage => Some(serializer.fromMeTLImage(s).room(jid))
      case s:MeTLVideo => Some(serializer.fromMeTLVideo(s).room(jid))
      case s:MeTLDirtyInk => Some(serializer.fromMeTLDirtyInk(s).room(jid))
      case s:MeTLDirtyText => Some(serializer.fromMeTLDirtyText(s).room(jid))
      case s:MeTLDirtyImage => Some(serializer.fromMeTLDirtyImage(s).room(jid))
      case s:MeTLDirtyVideo => Some(serializer.fromMeTLDirtyVideo(s).room(jid))
      case s:MeTLCommand => Some(serializer.fromMeTLCommand(s).room(jid))
      case s:MeTLQuiz => Some(serializer.fromMeTLQuiz(s).room(jid))
      case s:MeTLQuizResponse => Some(serializer.fromMeTLQuizResponse(s).room(jid))
      case s:MeTLSubmission => Some(serializer.fromSubmission(s).room(jid))
      case s:MeTLMoveDelta => Some(serializer.fromMeTLMoveDelta(s).room(jid))
      case s:MeTLFile => Some(serializer.fromMeTLFile(s).room(jid))
      case s:MeTLVideoStream => Some(serializer.fromMeTLVideoStream(s).room(jid))
      case s:MeTLGrade => Some(serializer.fromGrade(s).room(jid))
      case s:MeTLNumericGradeValue => Some(serializer.fromNumericGradeValue(s).room(jid))
      case s:MeTLBooleanGradeValue => Some(serializer.fromBooleanGradeValue(s).room(jid))
      case s:MeTLTextGradeValue => Some(serializer.fromTextGradeValue(s).room(jid))
      case s:MeTLUndeletedCanvasContent => Some(serializer.fromMeTLUndeletedCanvasContent(s).room(jid))
      case s:MeTLUnhandledStanza => Some(serializer.fromMeTLUnhandledStanza(s).room(jid))
      case s:MeTLUnhandledCanvasContent => Some(serializer.fromMeTLUnhandledCanvasContent(s).room(jid))
      case other => {
        warn("didn't know how to transform stanza: %s, %s".format(other,other.getClass))
        None
      }
    }
    transformedStanza match {
      case Some(s) => {
        if (s.save){
          Some(serializer.toMeTLData(s)).flatMap(data => data match {
            case ms:A => Some(ms)
            case _ => None
          })
        } else {
          warn("store in jid %s failed: %s".format(jid,stanza))
          None
        }
      }
      case _ => None
    }
  })
  def newSerialGetHistory(jid:String):History = Stopwatch.time("H2Interface.newSerialGetHistory",{
    val newHistory = History(jid)
    val images = Stopwatch.time("h2.fetch.images",H2Image.findAll(By(H2Image.room,jid)).map(i => (i.source.get,i)).toList )
    val videos = Stopwatch.time("h2.fetch.videos",H2Video.findAll(By(H2Video.room,jid)).map(v => (v.source.get,v)).toList )
    val submissions = Stopwatch.time("h2.fetch.submissions",H2Submission.findAll(By(H2Submission.room,jid)).map(s => (s.url.get,s)).toList )
    val files = Stopwatch.time("h2.fetch.files",H2File.findAll(By(H2File.room,jid)).map(f => (f.url.get,f)).toList )
    val quizzes = Stopwatch.time("h2.fetch.quizzes",H2Quiz.findAll(By(H2Quiz.room,jid)).map(q => (q.url.get,q)).toList )
    val identityList = Stopwatch.time("h2.fetch.resourceIds",(images ::: videos ::: submissions ::: files ::: quizzes ::: submissions).map(_._1.take(H2Constants.identity)))
    val resources = Map(Stopwatch.time("h2.fetch.resources",H2Resource.findAll(ByList(H2Resource.partialIdentity,identityList))).flatMap(r => {
      r.bytes.get match {
        case null => None
        case bytes => Some((r.identity.get,bytes))
      }
    }):_*)
    val inks = Stopwatch.time("h2.fetch.inks",H2Ink.findAll(By(H2Ink.room,jid)))
    val texts = Stopwatch.time("h2.fetch.texts",H2Text.findAll(By(H2Text.room,jid)))
    val themes = Stopwatch.time("h2.fetch.themes",H2Theme.findAll(By(H2Theme.room,jid)))
    val chatMessages = Stopwatch.time("h2.fetch.chatMessages",H2ChatMessage.findAll(By(H2ChatMessage.room,jid)))
    val multiWords = Stopwatch.time("h2.fetch.multiWords",H2MultiWordText.findAll(By(H2MultiWordText.room,jid)))
    val dirtyInks = Stopwatch.time("h2.fetch.dirtyInks",H2DirtyInk.findAll(By(H2DirtyInk.room,jid)))
    val dirtyTexts = Stopwatch.time("h2.fetch.dirtyTexts",H2DirtyText.findAll(By(H2DirtyText.room,jid)))
    val dirtyImgs = Stopwatch.time("h2.fetch.dirtyImages",H2DirtyImage.findAll(By(H2DirtyImage.room,jid)))
    val dirtyVids = Stopwatch.time("h2.fetch.dirtyVideos",H2DirtyVideo.findAll(By(H2DirtyVideo.room,jid)))
    val mds = Stopwatch.time("h2.fetch.moveDeltas",H2MoveDelta.findAll(By(H2MoveDelta.room,jid)))
    val quizResponses = Stopwatch.time("h2.fetch.quizzes",H2QuizResponse.findAll(By(H2QuizResponse.room,jid)))
    val videoStreams = Stopwatch.time("h2.fetch.videoStreams",H2VideoStream.findAll(By(H2VideoStream.room,jid)))
    val attendances = Stopwatch.time("h2.fetch.attendances",H2Attendance.findAll(By(H2Attendance.room,jid)))
    val grades = Stopwatch.time("h2.fetch.grades",H2Grade.findAll(By(H2Grade.room,jid)))
    val numericGradeValues = Stopwatch.time("h2.fetch.numericGradeValues",H2NumericGradeValue.findAll(By(H2NumericGradeValue.room,jid)))
    val booleanGradeValues = Stopwatch.time("h2.fetch.booleanGradeValues",H2BooleanGradeValue.findAll(By(H2BooleanGradeValue.room,jid)))
    val textGradeValues = Stopwatch.time("h2.fetch.textGradeValues",H2TextGradeValue.findAll(By(H2TextGradeValue.room,jid)))
    val comms = Stopwatch.time("h2.fetch.commands",H2Command.findAll(By(H2Command.room,jid)))
    val ccs = Stopwatch.time("h2.fetch.ccs",H2UnhandledCanvasContent.findAll(By(H2UnhandledCanvasContent.room,jid)))
    val undeletedCanvasContents = Stopwatch.time("h2.fetch.undeletedCanvasContents", H2UndeletedCanvasContent.findAll(By(H2UndeletedCanvasContent.room,jid)))
    val unhandled = Stopwatch.time("h2.fetch.stanzas",H2UnhandledStanza.findAll(By(H2UnhandledStanza.room,jid)))

    videos.foreach(s => newHistory.addStanza(serializer.toMeTLVideo(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
    images.foreach(s => newHistory.addStanza(serializer.toMeTLImage(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
    files.foreach(s => newHistory.addStanza(serializer.toMeTLFile(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
    quizzes.foreach(s => newHistory.addStanza(serializer.toMeTLQuiz(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
    submissions.foreach(s => newHistory.addStanza(serializer.toSubmission(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
      
    inks.foreach(s => newHistory.addStanza(serializer.toMeTLInk(s)))
    texts.foreach(s => newHistory.addStanza(serializer.toMeTLText(s)))
    multiWords.foreach(s => newHistory.addStanza(serializer.toMeTLMultiWordText(s)))
   
    themes.foreach(s => newHistory.addStanza(serializer.toTheme(s)))
    chatMessages.foreach(s => newHistory.addStanza(serializer.toChatMessage(s)))

    dirtyInks.foreach(s => newHistory.addStanza(serializer.toMeTLDirtyInk(s)))
    dirtyTexts.foreach(s => newHistory.addStanza(serializer.toMeTLDirtyText(s)))
    dirtyImgs.foreach(s => newHistory.addStanza(serializer.toMeTLDirtyImage(s)))
    dirtyVids.foreach(s => newHistory.addStanza(serializer.toMeTLDirtyVideo(s)))
    mds.foreach(s => newHistory.addStanza(serializer.toMeTLMoveDelta(s)))
    quizResponses.foreach(s => newHistory.addStanza(serializer.toMeTLQuizResponse(s)))
    videoStreams.foreach(s => newHistory.addStanza(serializer.toMeTLVideoStream(s)))
    attendances.foreach(s => newHistory.addStanza(serializer.toMeTLAttendance(s)))
    comms.foreach(s => newHistory.addStanza(serializer.toMeTLCommand(s)))
    ccs.foreach(s => newHistory.addStanza(serializer.toMeTLUnhandledCanvasContent(s)))
    unhandled.foreach(s => newHistory.addStanza(serializer.toMeTLUnhandledStanza(s)))

    grades.foreach(s => newHistory.addStanza(serializer.toGrade(s)))
    numericGradeValues.foreach(s => newHistory.addStanza(serializer.toNumericGradeValue(s)))
    booleanGradeValues.foreach(s => newHistory.addStanza(serializer.toBooleanGradeValue(s)))
    textGradeValues.foreach(s => newHistory.addStanza(serializer.toTextGradeValue(s)))

    undeletedCanvasContents.foreach(s => newHistory.addStanza(serializer.toMeTLUndeletedCanvasContent(s)))

    newHistory
  })

  protected val identityPoolTaskSupport = new scala.collection.parallel.ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(5 * Globals.h2ThreadPoolMultiplier))
  protected val stanzaTaskSupport = new scala.collection.parallel.ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(15 * Globals.h2ThreadPoolMultiplier))
  def newGetHistory(jid:String):History = Stopwatch.time("H2Interface.newGetHistory",{
    val newHistory = History(jid)
    var moveDeltas:List[MeTLMoveDelta] = Nil
    var parO = List(
      () => {
        var images:List[(String,H2Image)] = Nil
        var videos:List[(String,H2Video)] = Nil
        var submissions:List[(String,H2Submission)] = Nil
        var files:List[(String,H2File)] = Nil
        var quizzes:List[(String,H2Quiz)] = Nil
        
        val parI = List(
          () => { 
            images = H2Image.findAll(By(H2Image.room,jid)).map(i => (i.source.get,i)).toList 
            true
          },
          () => { 
            videos = H2Video.findAll(By(H2Video.room,jid)).map(v => (v.source.get,v)).toList 
            true
          },
          () => { 
            submissions = H2Submission.findAll(By(H2Submission.room,jid)).map(s => (s.url.get,s)).toList 
            true
          },
          () => { 
            files = H2File.findAll(By(H2File.room,jid)).map(f => (f.url.get,f)).toList 
            true
          },
          () => { 
            quizzes = H2Quiz.findAll(By(H2Quiz.room,jid)).map(q => (q.url.get,q)).toList 
            true
          }
        ).par
        parI.tasksupport = identityPoolTaskSupport  
        parI.map(f => f()).toList
        val resources = Map(H2Resource.findAll(ByList(H2Resource.partialIdentity,(images ::: videos ::: submissions ::: files ::: quizzes ::: submissions).map(_._1.take(H2Constants.identity)))).flatMap(r => {
          r.bytes.get match {
            case null => None
            case bytes => Some((r.identity.get,bytes))
          }
        }):_*)

        videos.foreach(s => newHistory.addStanza(serializer.toMeTLVideo(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
        images.foreach(s => newHistory.addStanza(serializer.toMeTLImage(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
        files.foreach(s => newHistory.addStanza(serializer.toMeTLFile(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
        quizzes.foreach(s => newHistory.addStanza(serializer.toMeTLQuiz(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
        submissions.foreach(s => newHistory.addStanza(serializer.toSubmission(s._2,resources.get(s._1).getOrElse(Array.empty[Byte]))))
      },
      () => H2Ink.findAll(By(H2Ink.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLInk(s))),
      () => H2Text.findAll(By(H2Text.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLText(s))),
      () => H2MultiWordText.findAll(By(H2MultiWordText.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLMultiWordText(s))),
      () => H2DirtyInk.findAll(By(H2DirtyInk.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLDirtyInk(s))),
      () => H2DirtyText.findAll(By(H2DirtyText.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLDirtyText(s))),
      () => H2DirtyImage.findAll(By(H2DirtyImage.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLDirtyImage(s))),
      () => H2DirtyVideo.findAll(By(H2DirtyVideo.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLDirtyVideo(s))),
      () => {
        moveDeltas = H2MoveDelta.findAll(By(H2MoveDelta.room,jid)).map(s => serializer.toMeTLMoveDelta(s))
      },
      () => H2QuizResponse.findAll(By(H2QuizResponse.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLQuizResponse(s))),
      () => H2VideoStream.findAll(By(H2VideoStream.room,jid)).toList.par.map(s => newHistory.addStanza(serializer.toMeTLVideoStream(s))).toList,
      () => H2Attendance.findAll(By(H2Attendance.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLAttendance(s))),
      () => H2Theme.findAll(By(H2Theme.room,jid)).foreach(s => newHistory.addStanza(serializer.toTheme(s))),
      () => H2ChatMessage.findAll(By(H2ChatMessage.room,jid)).foreach(s => newHistory.addStanza(serializer.toChatMessage(s))),
      () => H2Command.findAll(By(H2Command.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLCommand(s))),
      () => H2UndeletedCanvasContent.findAll(By(H2UndeletedCanvasContent.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLUndeletedCanvasContent(s))),
      () => H2UnhandledCanvasContent.findAll(By(H2UnhandledCanvasContent.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLUnhandledCanvasContent(s))),
      () => H2UnhandledStanza.findAll(By(H2UnhandledStanza.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLUnhandledStanza(s))),
      () => H2Grade.findAll(By(H2Grade.room,jid)).foreach(s => newHistory.addStanza(serializer.toGrade(s))),
      () => H2NumericGradeValue.findAll(By(H2NumericGradeValue.room,jid)).foreach(s => newHistory.addStanza(serializer.toNumericGradeValue(s))),
      () => H2BooleanGradeValue.findAll(By(H2BooleanGradeValue.room,jid)).foreach(s => newHistory.addStanza(serializer.toBooleanGradeValue(s))),
      () => H2TextGradeValue.findAll(By(H2TextGradeValue.room,jid)).foreach(s => newHistory.addStanza(serializer.toTextGradeValue(s)))
    ).par
    parO.tasksupport = stanzaTaskSupport
    parO.map(f => f()).toList
    moveDeltas.foreach(s => newHistory.addStanza(s))
    newHistory
  })
  def getHistory(jid:String):History = Stopwatch.time("H2Interface.getHistory",{
    //oldGetHistory(jid)
    newGetHistory(jid)
    //newSerialGetHistory(jid)
  })
  def oldGetHistory(jid:String):History = Stopwatch.time("H2Interface.oldGetHistory",{
    val newHistory = History(jid)
    var moveDeltas:List[MeTLMoveDelta] = Nil
    List(
      () => H2Ink.findAll(By(H2Ink.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLInk(s))),
      () => H2Text.findAll(By(H2Text.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLText(s))),
      () => H2Theme.findAll(By(H2Theme.room,jid)).foreach(s => newHistory.addStanza(serializer.toTheme(s))),
      () => H2MultiWordText.findAll(By(H2MultiWordText.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLMultiWordText(s))),
      () => {
        H2Image.findAll(By(H2Image.room,jid)).toList.par.map(s => {
          newHistory.addStanza(serializer.toMeTLImage(s))
        }).toList
      },
      () => H2Video.findAll(By(H2Video.room,jid)).toList.par.map(s => newHistory.addStanza(serializer.toMeTLVideo(s))).toList,
      () => H2DirtyInk.findAll(By(H2DirtyInk.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLDirtyInk(s))),
      () => H2DirtyText.findAll(By(H2DirtyText.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLDirtyText(s))),
      () => H2DirtyImage.findAll(By(H2DirtyImage.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLDirtyImage(s))),
      () => H2DirtyVideo.findAll(By(H2DirtyVideo.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLDirtyVideo(s))),
      () => {
        moveDeltas = H2MoveDelta.findAll(By(H2MoveDelta.room,jid)).map(s => serializer.toMeTLMoveDelta(s))
      },
      () => H2Submission.findAll(By(H2Submission.room,jid)).toList.par.map(s => newHistory.addStanza(serializer.toSubmission(s))).toList,
      () => H2Quiz.findAll(By(H2Quiz.room,jid)).toList.par.map(s => newHistory.addStanza(serializer.toMeTLQuiz(s))).toList,
      () => H2QuizResponse.findAll(By(H2QuizResponse.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLQuizResponse(s))),
      () => H2File.findAll(By(H2File.room,jid)).toList.par.map(s => newHistory.addStanza(serializer.toMeTLFile(s))).toList,
      () => H2VideoStream.findAll(By(H2VideoStream.room,jid)).toList.par.map(s => newHistory.addStanza(serializer.toMeTLVideoStream(s))).toList,
      () => H2Attendance.findAll(By(H2Attendance.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLAttendance(s))),
      () => H2Theme.findAll(By(H2Theme.room,jid)).foreach(s => newHistory.addStanza(serializer.toTheme(s))),
      () => H2ChatMessage.findAll(By(H2ChatMessage.room,jid)).foreach(s => newHistory.addStanza(serializer.toChatMessage(s))),
      () => H2Command.findAll(By(H2Command.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLCommand(s))),
      () => H2UndeletedCanvasContent.findAll(By(H2UndeletedCanvasContent.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLUndeletedCanvasContent(s))),
      () => H2UnhandledCanvasContent.findAll(By(H2UnhandledCanvasContent.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLUnhandledCanvasContent(s))),
      () => H2UnhandledStanza.findAll(By(H2UnhandledStanza.room,jid)).foreach(s => newHistory.addStanza(serializer.toMeTLUnhandledStanza(s))),
      () => H2Grade.findAll(By(H2Grade.room,jid)).foreach(s => newHistory.addStanza(serializer.toGrade(s))),
      () => H2NumericGradeValue.findAll(By(H2NumericGradeValue.room,jid)).foreach(s => newHistory.addStanza(serializer.toNumericGradeValue(s))),
      () => H2BooleanGradeValue.findAll(By(H2BooleanGradeValue.room,jid)).foreach(s => newHistory.addStanza(serializer.toBooleanGradeValue(s))),
      () => H2TextGradeValue.findAll(By(H2TextGradeValue.room,jid)).foreach(s => newHistory.addStanza(serializer.toTextGradeValue(s)))
    ).par.map(f => f()).toList
  //.toList.foreach(group => group.foreach(gf => gf()))
                              //val unhandledContent = H2UnhandledContent.findAll(By(H2UnhandledContent.room,jid)).map(s => serializer.toMeTLUnhandledData(s))
                              //(inks ::: texts ::: images ::: dirtyInks ::: dirtyTexts ::: dirtyImages ::: moveDeltas ::: quizzes ::: quizResponses ::: commands ::: submissions ::: files ::: attendances ::: unhandledCanvasContent ::: unhandledStanzas /*:: unhandledContent */).foreach(s => newHistory.addStanza(s))
      moveDeltas.foreach(s => newHistory.addStanza(s))
      newHistory
  })

  //conversations table
  protected lazy val mbDef = new MessageBusDefinition("global","conversationUpdating",receiveConversationDetailsUpdated _)
  protected lazy val conversationMessageBus = config.getMessageBus(mbDef)
  protected lazy val conversationCache = scala.collection.mutable.Map(H2Conversation.findAll.map(c => (c.jid.get,serializer.toConversation(c))):_*)
  protected def updateConversation(c:Conversation):Boolean = {
    try {
      conversationCache.update(c.jid,c)
      updateMaxJid
      serializer.fromConversation(c).save
      onConversationDetailsUpdated(c)
      true
    } catch {
      case e:Throwable => {
        error("exception thrown while updating conversation: %s\r\n%s".format(e.getMessage,e.getStackTrace))
        false
      }
    }
  }
  protected def updateMaxJid = maxJid = try {
    conversationCache.values.map(_.jid).max
  } catch {
    case _:Throwable => 0
  }
  protected var maxJid = 0
  protected def getNewJid = synchronized {
    if (maxJid == 0){
      updateMaxJid
    }
    val oldMax = maxJid
    maxJid += 1000
    maxJid
  }
  protected def receiveConversationDetailsUpdated(m:MeTLStanza) = {
    m match {
      case c:MeTLCommand if c.command == "/UPDATE_CONVERSATION_DETAILS" && c.commandParameters.length == 1 => {
        try{
          val jidToUpdate = c.commandParameters(0).toInt
          val conversation = detailsOfConversation(jidToUpdate)
          conversationCache.update(conversation.jid,conversation)
          updateMaxJid
          onConversationDetailsUpdated(conversation)
        } catch {
          case e:Throwable => error("exception while attempting to update conversation details",e)
        }
      }
      case _ => {}
    }
  }
  def getAllConversations:List[Conversation] = conversationCache.values.toList
  def searchForConversation(query:String):List[Conversation] = conversationCache.values.filter(c => c.title.toLowerCase.trim.contains(query.toLowerCase.trim) || c.author.toLowerCase.trim == query.toLowerCase.trim).toList
  def searchForConversationByCourse(courseId:String):List[Conversation] = conversationCache.values.filter(c => c.subject.toLowerCase.trim.equals(courseId.toLowerCase.trim) || c.foreignRelationship.exists(_.key.toLowerCase.trim == courseId.toLowerCase.trim)).toList
  def conversationFor(slide:Int):Int = (slide / 1000 ) * 1000
  def detailsOfConversation(jid:Int):Conversation = conversationCache(jid)
  def createConversation(title:String,author:String):Conversation = {
    val now = new Date()
    val newJid = getNewJid
    val details = Conversation(config,author,now.getTime,List(Slide(config,author,newJid + 1,0)),"unrestricted","",newJid,title,now.getTime,Permissions.default(config))
    updateConversation(details)
    details
  }
  protected def findAndModifyConversation(jidString:String,adjustment:Conversation => Conversation):Conversation  = Stopwatch.time("H2Interface.findAndModifyConversation",{
    try {
      val jid = jidString.toInt
      detailsOfConversation(jid) match {
        case c:Conversation if (c.jid == jid) => {
          val updatedConv = adjustment(c)
          if (updateConversation(updatedConv)){
            updatedConv
          } else {
            Conversation.empty
          }
        }
        case other => other
      }
    } catch {
      case e:Throwable => {
        error("failed to alter conversation",e)
        Conversation.empty
      }
    }
  })
  def deleteConversation(jid:String):Conversation = updateSubjectOfConversation(jid,"deleted")
  def renameConversation(jid:String,newTitle:String):Conversation = findAndModifyConversation(jid,c => c.rename(newTitle))
  def changePermissionsOfConversation(jid:String,newPermissions:Permissions):Conversation = findAndModifyConversation(jid,c => c.replacePermissions(newPermissions))
  def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = findAndModifyConversation(jid,c => c.replaceSubject(newSubject))
  def addSlideAtIndexOfConversation(jid:String,index:Int):Conversation = findAndModifyConversation(jid,c => c.addSlideAtIndex(index))
  def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation = findAndModifyConversation(jid,c => c.addGroupSlideAtIndex(index,grouping))
  def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = findAndModifyConversation(jid,c => c.replaceSlides(newSlides))
  def updateConversation(jid:String,conversation:Conversation):Conversation = {
    if (jid == conversation.jid.toString){
      updateConversation(conversation)
      conversation
    } else {
      conversation
    }
  }

  //resources table
  def getResource(identity:String):Array[Byte] = Stopwatch.time("H2Interface.getResource",{
    H2Resource.find(By(H2Resource.partialIdentity,identity.take(H2Constants.identity)),By(H2Resource.identity,identity)).map(r => {
      r.bytes.get
    }).openOr({
      Array.empty[Byte]
    })

  })
  def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = Stopwatch.time("H2Interface.postResource",{
    val now = new Date().getTime.toString
    val possibleNewIdentity = "%s:%s:%s".format(jid,userProposedId,now)
    H2Resource.find(By(H2Resource.partialIdentity,possibleNewIdentity.take(H2Constants.identity)),By(H2Resource.identity,possibleNewIdentity)) match {
      case Full(r) => {
        warn("postResource: identityAlready exists for %s".format(userProposedId))
        val newUserProposedIdentity = "%s_%s".format(userProposedId,now)
        postResource(jid,newUserProposedIdentity,data)
      }
      case _ => {
        H2Resource.create.partialIdentity(possibleNewIdentity.take(H2Constants.identity)).identity(possibleNewIdentity).bytes(data).room(jid).save
        trace("postResource: saved %s bytes in %s at %s".format(data.length,jid,possibleNewIdentity))
        possibleNewIdentity
      }
    }
  })
  def getResource(jid:String,identity:String):Array[Byte] = Stopwatch.time("H2Interface.getResource",{
    H2ContextualizedResource.find(
      By(H2ContextualizedResource.context,jid),
      By(H2ContextualizedResource.identity,identity)
    ).map(r => {
      r.bytes.get
    }).openOr({
      Array.empty[Byte]
    })

  })
  def insertResource(jid:String,data:Array[Byte]):String = Stopwatch.time("H2Interface.insertResource",{
    H2ContextualizedResource.create.context(jid).bytes(data).saveMe.identity.get
  })
  def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = Stopwatch.time("H2Interface.upsertResource",{
    H2ContextualizedResource.find(
      By(H2ContextualizedResource.context,jid),
      By(H2ContextualizedResource.identity,identifier)
    ).map(r => {
      r.bytes(data).saveMe.identity.get
    }).openOr({
      insertResource(jid,data)
    })
  })
}
