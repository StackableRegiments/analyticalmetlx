package com.metl.h2

import com.metl.data._
import com.metl.utils._
import com.metl.persisted._
import com.metl.h2.dbformats._
import java.util.Date

import net.liftweb.mapper._
import net.liftweb.common._
import net.liftweb.util._
import Helpers._

import scala.compat.Platform.EOL
import _root_.net.liftweb.mapper.{ConnectionManager, DB, DefaultConnectionIdentifier, Schemifier, StandardDBVendor}
import _root_.java.sql.{Connection, DriverManager}

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
        H2UndeletedCanvasContent,
        H2Profile,
        H2AccountRelationships,
        H2Slide,
        H2SessionRecord,
        H2ForumPost,
        H2WootOperation
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
    /*
    DatabaseVersion.find(By(DatabaseVersion.key,"version"),By(DatabaseVersion.scope,"db")).filter(_.intValue.get < 7).map(versionNumber => {
      info("upgrading db to set all slides to exposed, because they were previously defaulting to false and we didn't mean that.")
      
      H2Conversation.findAll.foreach(conv => {
        conv.slides(serializer.slidesToString(serializer.slidesFromString(conv.slides.get).map(_.copy(exposed = true)))).save
      })
      versionNumber.intValue(7).save
      info("upgraded db to set all slides to exposed, because they were previously defaulting to false and we didn't mean that.")
      versionNumber
    }).foreach(versionNumber => info("using dbSchema version: %s".format(versionNumber.intValue.get)))
    */
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
      case s:ForumPost => Some(serializer.fromForumPost(s).room(jid))
      case s:WootOperation => Some(serializer.fromWootOperation(s).room(jid))
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
  protected val identityPoolTaskSupport = new scala.collection.parallel.ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(5 * Globals.h2ThreadPoolMultiplier))
  protected val stanzaTaskSupport = new scala.collection.parallel.ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(23 * Globals.h2ThreadPoolMultiplier))
  def getHistory(jid:String):History = Stopwatch.time("H2Interface.getHistory",{
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
      () => H2WootOperation.findAll(By(H2WootOperation.room,jid)).foreach(s => newHistory.addStanza(serializer.toWootOperation(s))),
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
      () => H2TextGradeValue.findAll(By(H2TextGradeValue.room,jid)).foreach(s => newHistory.addStanza(serializer.toTextGradeValue(s))),
      () => H2ForumPost.findAll(By(H2ForumPost.room,jid)).foreach(s => newHistory.addStanza(serializer.toForumPost(s)))
    ).par
    parO.tasksupport = stanzaTaskSupport
    parO.map(f => f()).toList
    moveDeltas.foreach(s => newHistory.addStanza(s))
    newHistory
  })
  //conversations table
  protected lazy val mbDef = new MessageBusDefinition("global","conversationUpdating",receiveConversationDetailsUpdated _)
  protected lazy val conversationMessageBus = config.getMessageBus(mbDef)

  protected def updateConversation(c:Conversation):Boolean = {
    try {
      serializer.fromConversation(c.copy(lastAccessed = new java.util.Date().getTime)).save
      onConversationDetailsUpdated(c)
      true
    } catch {
      case e:Throwable => {
        error("exception thrown while updating conversation: %s\r\n%s".format(e.getMessage,e.getStackTrace))
        false
      }
    }
  }
  protected def receiveConversationDetailsUpdated(mTup:Tuple2[MeTLStanza,String]) = {
    val (m,location) = mTup
    m match {
      case c:MeTLCommand if c.command == "/UPDATE_CONVERSATION_DETAILS" && c.commandParameters.length == 1 => {
        try{
          val jidToUpdate = c.commandParameters(0)
          val conversation = detailsOfConversation(jidToUpdate)
          onConversationDetailsUpdated(conversation)
        } catch {
          case e:Throwable => error("exception while attempting to update conversation details",e)
        }
      }
      case _ => {}
    }
  }
  def getAllConversations:List[Conversation] = {
    warn("H2 getAllConversations")
    val s = new java.util.Date().getTime
    /*
    val table = H2Conversation.dbTableName
    val idCol = H2Conversation.jid.dbColumnName
    val timeCol = H2Conversation.lastAccessed.dbColumnName

    val sql = """SELECT * FROM (
      SELECT * FROM %s
      ORDER BY %s DESC) 
    GROUP BY %s
    """.format(
      table,
      timeCol,
      idCol
      )
    */
/*
    val sql = """SELECT * FROM %s maintable
WHERE %s = (SELECT MAX(%s) FROM %s subtable WHERE subtable.%s = maintable.%s)
GROUP BY maintable.%s""".format(
      table,
      timeCol,timeCol,table,idCol,idCol,
      idCol
    )
*/
/*
    val results = H2Conversation.findAllByInsecureSql(sql,IHaveValidatedThisSQL("dave","24/08/2017")).map(serializer.toConversation _)
    warn("H2 getAllConversations (%sms) => %s".format(s - new java.util.Date().getTime,results))
    results
    */
    /*
    H2Conversation.findAll(BySql("""%s = (SELECT MAX(%s) FROM %s subquery WHERE %s = subquery.%s)""".format(H2Conversation.creation.dbColumnName,H2Conversation.creation.dbColumnName,H2Conversation.dbTableName,H2Conversation.jid.dbColumnName,H2Conversation.jid.dbColumnName),IHaveValidatedThisSQL("dave","23/08/2017"))).map(serializer.toConversation _)
    */

    val results = H2Conversation.findAll.groupBy(_.jid.get).toList.flatMap(c => {
      val allForThisJid = c._2
      val latest = allForThisJid.sortBy(_.lastAccessed.get).reverse.headOption
      latest.map(l => serializer.toConversation(l))
    })
    warn("H2 getAllConversations: %s (%sms)".format(results.length,new java.util.Date().getTime - s))
    results
  }
  
  override def getConversationsForSlideId(jid:String):List[String] = getAllConversations.filter(_.slides.exists(_.id == jid)).map(_.jid).toList
  def searchForConversation(query:String):List[Tuple2[Conversation,SearchExplanation]] = getAllConversations.filter(c => queryAppliesToConversation(query,c)).toList.map(c => {
    (c,SearchExplanation(query,c.jid,true,1.0f,"",Nil))
  })
  def searchForSlide(query:String):List[Tuple2[Slide,SearchExplanation]] = getAllSlides.filter(s => queryAppliesToSlide(query,s)).map(s => {
    (s,SearchExplanation(query,s.id,true,1.0f,"",Nil))
  })
  def queryAppliesToSlide(query:String,slide:Slide) = slide.id == query || slide.author == query
  def queryAppliesToConversation(query:String,conversation:Conversation) = conversation.title.toLowerCase.trim.contains(query.toLowerCase.trim) || conversation.author.toLowerCase.trim == query.toLowerCase.trim
  def searchForConversationByCourse(courseId:String):List[Conversation] = getAllConversations.filter(c => c.subject.toLowerCase.trim.equals(courseId.toLowerCase.trim) || c.foreignRelationship.exists(_.key.toLowerCase.trim == courseId.toLowerCase.trim)).toList
  def detailsOfConversation(jid:String):Conversation = {
    val all = H2Conversation.findAll(By(H2Conversation.jid,jid),OrderBy(H2Conversation.lastAccessed,Descending)).map(hc => serializer.toConversation(hc))
    val found = H2Conversation.findAll(By(H2Conversation.jid,jid),OrderBy(H2Conversation.lastAccessed,Descending),MaxRows(1)).headOption.map(hc => serializer.toConversation(hc))
   /*
    val all = H2Conversation.findAll(By(H2Conversation.jid,jid)).sortWith((a,b) => a.creation.get < b.creation.get)
    val found = all.headOption.map(hc => serializer.toConversation(hc))
    */
    warn("detailsOfConversation: %s => %s".format(all,found))
    found.getOrElse(Conversation.empty)
  }
  def getAllSlides:List[Slide] = {
    warn("H2 getAllSlides")
    val s = new java.util.Date().getTime
    /*
    val table = H2Slide.dbTableName
    val idCol = H2Slide.jid.dbColumnName
    val timeCol = H2Slide.modified.dbColumnName

    val sql = """SELECT * FROM (
      SELECT * FROM %s
      ORDER BY %s DESC) 
    GROUP BY %s
    """.format(
      table,
      timeCol,
      idCol
      )
    */
    /*
    val sql = """SELECT * FROM %s maintable
WHERE %s = (SELECT MAX(%s) FROM %s subtable WHERE subtable.%s = maintable.%s)
GROUP BY %s""".format(
      table,
      timeCol,timeCol,table,idCol,idCol,
      idCol
    )
*/
    /*
    val results = H2Slide.findAllByInsecureSql(sql,IHaveValidatedThisSQL("dave","24/08/2017")).map(serializer.toSlide _)
    warn("H2 getAllSlides (%sms) => %s".format(s - new java.util.Date().getTime,results))
    results
    */
   /*
    H2Slide.findAll(BySql("""%s = (SELECT MAX(%s) FROM %s subquery WHERE %s = subquery.%s)""".format(H2Slide.creation.dbColumnName,H2Slide.creation.dbColumnName,H2Slide.dbTableName,H2Slide.id.dbColumnName,H2Slide.id.dbColumnName),IHaveValidatedThisSQL("dave","23/08/2017"))).map(serializer.toSlide _)
    */
    val results = H2Slide.findAll.groupBy(_.jid.get).toList.flatMap(s => {
      val allForThisJid = s._2
      val latest = allForThisJid.sortBy(_.modified.get).reverse.headOption
      latest.map(l => serializer.toSlide(l))
    })
    warn("H2 getAllSlides:%s (%sms)".format(results.length,new java.util.Date().getTime - s))
    results
  }
  def detailsOfSlide(jid:String):Slide = H2Slide.findAll(By(H2Slide.jid,jid),OrderBy(H2Slide.modified,Descending),MaxRows(1)).headOption.map(hs => serializer.toSlide(hs)).getOrElse(Slide.empty)
  def generateConversationJid:String = "c_%s_t_%s_".format(nextFuncName,new java.util.Date().getTime)
  def generateSlideJid:String = "s_%s_t_%s_".format(nextFuncName,new java.util.Date().getTime)
  def createSlide(author:String,slideType:String = "SLIDE",grouping:List[GroupSet] = Nil):Slide = {
    val now = new java.util.Date().getTime
    val slide = H2Slide.create.slideType(slideType).creation(now).modified(now).author(author).jid(generateSlideJid).defaultHeight(540).defaultWidth(720).saveMe
    Slide(slide.author.get,slide.jid.get,0,now,now,slide.defaultHeight.get,slide.defaultWidth.get,true,slideType,grouping)
  }
  def createConversation(title:String,author:String):Conversation = {
    val now = new Date()
    val newJid = generateConversationJid
    val slide = createSlide(author).copy(index = 0,exposed = true) 
    val details = Conversation(author,now.getTime,List(slide),"unrestricted","",newJid,title,now.getTime,Permissions.default)
    updateConversation(details)
    details
  }
  protected def findAndModifyConversation(jidString:String,adjustment:Conversation => Conversation):Conversation  = Stopwatch.time("H2Interface.findAndModifyConversation",{
    try {
      val jid = jidString
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
  def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation = {
    findAndModifyConversation(jid,c => {
      val slide = createSlide(c.author,slideType)
      c.addSlideAtIndex(index,slide)
    })
  }
  def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:GroupSet):Conversation = {
    findAndModifyConversation(jid,c => {
      val slide = createSlide(c.author,"SLIDE",List(grouping))
      c.addSlideAtIndex(index,slide)
    })
  }
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

  def getAllProfiles:List[Profile] = Stopwatch.time("H2Interface.getAllProfiles",{
    val s = new java.util.Date().getTime
    val raw = H2Profile.findAll()
    val results = raw.groupBy(_.profileId.get).toList.map(_._2).flatMap(_.sortBy(_.timestamp.get).reverse.headOption.map(serializer.toProfile _))
    warn("H2GetAllProfiles %s (%s)".format(results.length,new java.util.Date().getTime - s))
    results 
  })
  def getProfiles(ids:String *):List[Profile] = Stopwatch.time("H2Interface.getProfiles",{
    warn("H2 getProfiles(%s)".format(ids))
    val s = new java.util.Date().getTime
    /*
    val table = H2Profile.dbTableName
    val idCol = H2Profile.profileId.dbColumnName
    val timeCol = H2Profile.timestamp.dbColumnName
    val sql = """SELECT * FROM (
      SELECT * FROM %s
      WHERE %s in (%s)
      ORDER BY %s DESC) 
    GROUP BY %s
    """.format(
      table,
      idCol,ids.map(id => "'%s'".format(id)).mkString(","),
      timeCol,
      idCol
      )
    */
   /*
    val sql = """SELECT * FROM %s maintable
WHERE %s IN (%s)
AND %s = (SELECT MAX(%s) FROM %s subtable WHERE subtable.%s = maintable.%s)
GROUP BY %s""".format(
      table,
      idCol,ids.map(id => "'%s'".format(id)).mkString(","),
      timeCol,timeCol,table,idCol,idCol,
      idCol
    )
    val results = H2Profile.findAllByInsecureSql(sql,IHaveValidatedThisSQL("dave","24/08/2017")).map(serializer.toProfile _)
    warn("H2 getProfiles(%s) (%sms) => %s".format(ids,s - new java.util.Date().getTime,results))
    results
*/

    /*
    val results = H2Profile.findAll(ByList(H2Profile.profileId,ids.toList),BySql("""%s = (SELECT MAX(%s) FROM %s subquery WHERE %s = subquery.%s) GROUP BY %s""".format(H2Profile.timestamp.dbColumnName,H2Profile.timestamp.dbColumnName,H2Profile.dbTableName,H2Profile.profileId.dbColumnName,H2Profile.profileId.dbColumnName,H2Profile.profileId.dbColumnName),IHaveValidatedThisSQL("dave","23/08/2017"))).map(serializer.toProfile _)
    println("H2GetProfiles(%s) %s => %s".format(ids,results.length, results))
    */
    val raw = H2Profile.findAll(ByList(H2Profile.profileId,ids.toList))
    val results = raw.groupBy(_.profileId.get).toList.map(_._2).flatMap(_.sortBy(_.timestamp.get).reverse.headOption.map(serializer.toProfile _))
    warn("H2GetProfiles(%s) %s => %s (%s)".format(ids,raw.length, results,new java.util.Date().getTime - s))
    results 
  })
  protected def createProfileId:String = "p_%s".format(nextFuncName)
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = {
    val newP = Profile(new Date().getTime,createProfileId,name,attrs,Nil)
    val hp = serializer.fromProfile(newP)
    hp.save
    serializer.toProfile(hp)
  }
  def updateProfile(id:String,profile:Profile):Profile = {
    serializer.toProfile(serializer.fromProfile(profile.adjustTimestamp(new Date().getTime)).profileId(id).saveMe)
  }
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = {
    if (default){
      H2AccountRelationships.findAll(By(H2AccountRelationships.accountName,accountName),By(H2AccountRelationships.accountProvider,accountProvider)).groupBy(_.profileId.get).flatMap(_._2.sortBy(_.timestamp.get).reverse.headOption.filter(_.default.get)).foreach(old => {
        old.default(false).save
      })
    }
    H2AccountRelationships.create.timestamp(new Date().getTime).default(default).disabled(disabled).profileId(profileId).accountName(accountName).accountProvider(accountProvider).accountName(accountName).save
  }
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = Stopwatch.time("H2Interface.getProfileIds",{
    /*
    val s = new java.util.Date().getTime
    val table = H2AccountRelationships.dbTableName
    val idCol = H2AccountRelationships.profileId.dbColumnName
    val timeCol = H2AccountRelationships.timestamp.dbColumnName

    val sql = """SELECT * FROM %s maintable
WHERE %s = (SELECT MAX(%s) FROM %s subtable WHERE subtable.%s = maintable.%s)
GROUP BY %s""".format(
      table,
      timeCol,timeCol,table,idCol,idCol,
      idCol
    )
    val results = H2AccountRelationships.findAllByInsecureSql(sql,IHaveValidatedThisSQL("dave","24/08/2017")).groupBy(_.profileId.get map(serializer.toProfile _)
    warn("H2 getProfileIds(%s,%s) (%sms) => %s".format(accountName,accountProvider,s - new java.util.Date().getTime,results))
    results
    */
    val profs = H2AccountRelationships.findAll(By(H2AccountRelationships.accountName,accountName),By(H2AccountRelationships.accountProvider,accountProvider)).groupBy(_.profileId.get).flatMap(_._2.sortBy(_.timestamp.get).reverse.headOption.filterNot(_.disabled.get))
    val defaultProf = profs.filter(_.default.get).toList.sortBy(_.timestamp.get).reverse.headOption.map(_.profileId.get).getOrElse("")
    (profs.map(_.profileId.get).toList,defaultProf)
  })

  protected def toSessionRecord(in:H2SessionRecord):SessionRecord = {
    SessionRecord(
      sid = in.sessionId.get,
      accountProvider = in.accountProvider.get,
      accountName = in.accountName.get,
      profileId = in.profileId.get,
      ipAddress = in.ipAddress.get,
      userAgent = in.userAgent.get,
      action = in.action.get,
      timestamp = in.timestamp.get
    )
  }
  protected def fromSessionRecord(sessionRecord:SessionRecord,onto:H2SessionRecord = H2SessionRecord.create):H2SessionRecord = {
    onto
      .timestamp(sessionRecord.timestamp)
      .sessionId(sessionRecord.sid)
      .profileId(sessionRecord.profileId)
      .accountProvider(sessionRecord.accountProvider)
      .accountName(sessionRecord.accountName)
      .ipAddress(sessionRecord.ipAddress)
      .userAgent(sessionRecord.userAgent)
      .action(sessionRecord.action)
  }
  override def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord] = {
    H2SessionRecord.findAll(By(H2SessionRecord.accountName,accountName),By(H2SessionRecord.accountProvider,accountProvider)).map(toSessionRecord _)
  }
  override def getSessionsForProfile(profileId:String):List[SessionRecord] = {
    H2SessionRecord.findAll(By(H2SessionRecord.profileId,profileId)).map(toSessionRecord _)
  }
  override def updateSession(sessionRecord:SessionRecord):SessionRecord = {
    val newS = fromSessionRecord(sessionRecord).saveMe
    warn("writing to sessionTable: %s => %s".format(sessionRecord,newS))
    sessionRecord
  }
  override def getCurrentSessions:List[SessionRecord] = H2SessionRecord.findAll().map(toSessionRecord _) // naive - this'll get big, but it's not going to be a normal user action, so perhaps it won't matter?! 
  override def getThemesByAuthor(author:String):List[Theme] = H2Theme.findAll(By(H2Theme.author,author)).map(t => serializer.toTheme(t).theme)
  override def getSlidesByThemeKeyword(theme:String):List[String] = H2Theme.findAllFields(List(H2Theme.location),BySql("%s LIKE ?".format(H2Theme.text.dbColumnName),IHaveValidatedThisSQL("Dave","20170817"),"%"+theme+"%")).map(_.location.get)
  override def getConversationsByTheme(theme:String):List[String] = {
    for {
      conv <- getAllConversations
      relevant <- getSlidesByThemeKeyword(theme)
      slide <- conv.slides
      if (slide.id == relevant)
    } yield {
      conv.jid
    }
  }
  override def getAttendancesByAuthor(author:String):List[Attendance] = H2Attendance.findAll(By(H2Attendance.author,author)).map(serializer.toMeTLAttendance)
  override def getConversationsByAuthor(author:String):List[Conversation] = getAllConversations.filter(_.author == author)
  override def getAuthorsByTheme(theme:String):List[String] = H2Theme.findAllFields(List(H2Theme.author),BySql("%s LIKE ?".format(H2Theme.text.dbColumnName),IHaveValidatedThisSQL("Dave","20170817"),"%"+theme+"%")).map(_.author.get)

}
