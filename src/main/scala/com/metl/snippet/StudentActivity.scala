package com.metl.snippet

import java.io.StringReader
import java.util.Date

import com.github.tototoshi.csv.CSVReader
import com.metl.data.ServerConfiguration
import com.metl.liftAuthenticator.OrgUnit
import com.metl.model._
import com.metl.view.StudentActivityReportHelper
import net.liftweb.common.{Empty, Logger}
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds.{OnLoad, Script}
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.util.Helpers._
import net.liftweb.util._
import net.sf.ehcache.config.MemoryUnit
import net.sf.ehcache.store.MemoryStoreEvictionPolicy

class StudentActivity extends Logger {

  protected val config = CacheConfig(10, MemoryUnit.MEGABYTES, MemoryStoreEvictionPolicy.LRU, Some(60))
  protected val courseCache = new ManagedCache[(String, String), Option[String]]("courses", (key: (String, String)) => getCourseNameById(key), config)

  protected lazy val serverConfig: ServerConfiguration = ServerConfiguration.default

  protected val blankOption: (String, String) = "" -> ""

  /*
    def getCourses(key: String):Option[List[OrgUnit]] = {
      key match {
        case "all" => getAllCourses
        case _ => getCoursesForCurrentUser
      }
    }
  */

  def getAllOptions: List[(String, String)] = {
    blankOption ::
      //      getAllCourses.getOrElse(List()).map(c => (c.foreignRelationship.get.key.toString, c.name))
      getCoursesForAllConversations.map(c => (c._2, c._3))
  }

  def getOptionsForCurrentUser: List[(String, String)] = {
    blankOption ::
      getCoursesForCurrentUser.getOrElse(List()).map(c => (c.foreignRelationship.get.key.toString, c.name))
  }

  def handler(courseId: String): JsCmd = {
    val stringReader = new StringReader(StudentActivityReportHelper.studentActivity(courseId))
    val headers = CSVReader.open(stringReader).allWithOrderedHeaders()
    stringReader.close()

    Call("updateActivity", createHtmlTable(headers)).cmd
  }

  def render: CssBindFunc = {
    "#courses" #> ajaxSelect(getAllOptions, Empty, handler) &
      //    "#courses" #> ajaxSelect(getOptionsForCurrentUser, Empty, handler) &
      "#loaderSelectize" #> Script(OnLoad(Call("selectize").cmd))
  }

  def createHtmlTable(results: (List[String], List[Map[String, String]])): JObject = {
    JObject(List(
      JField("headers", JArray(results._1.map(h => JString(h)))),
      JField("data", JArray(results._2.filter(r => r.get("ConversationID").nonEmpty || r.get("D2LStudentID").nonEmpty).map(r => {
        JObject(r.toList.map(kv => JField(kv._1, JString(kv._2))))
      })))
    ))
  }

  protected def getCoursesForAllConversations: List[(String, String, String)] = {
    println("Loading all conversations")
    val start = new Date().getTime
    val allConversations = serverConfig.getAllConversations
    println("All conversations: " + allConversations.map(c => c.foreignRelationship))

    allConversations.sortBy(c => c.lastAccessed).foreach(c => println("Conversation subject: " + c.subject))

    val conversations = allConversations.filter(c => c.foreignRelationship.nonEmpty /*&& c.foreignRelationship.get.displayName.nonEmpty*/).map(c => {
      val relationship = c.foreignRelationship.get
      (relationship.system, relationship.key, relationship.displayName match {
        case Some(s) => s
        case None => courseCache.get(relationship.system, relationship.key).getOrElse("")
      })
    })
    println("Loaded all " + conversations.length + " conversations from MeTL in " + (new Date().getTime - start) / 1000 + "s")

    // Add related course displayName for each conversation
//    conversations.foreach(c => c._3 = courseCache.get((c._1, c._2)))

    conversations
  }

  /** Retrieve from D2L. */
  protected def getAllCourses: Option[List[OrgUnit]] = {
    println("Loading all courses")
    val start = new Date().getTime
    val m = Globals.getGroupsProviders.flatMap {
      case g: D2LGroupsProvider if g.canQuery && g.canRestrictConversations =>
        val groups = g.getAllOrgUnits
        println("Loaded all " + groups.length + " groups from system " + g.storeId + " in " + (new Date().getTime - start) / 1000 + "s")
        groups
      case _ => None
    }
    Some(m)
  }

  /** Retrieve from D2L. */
  protected def getCoursesForCurrentUser: Option[List[OrgUnit]] = {
    val m = Globals.getGroupsProviders.flatMap {
      case g: D2LGroupsProvider if g.canQuery && g.canRestrictConversations =>
        val groups = g.getGroupsFor(Globals.casState.is)
        println("Loaded " + groups.length + " groups from system " + g.storeId)
        groups
      case _ => None
    }
    Some(m)
  }

  /** Retrieve from D2L. */
  protected def getCourseNameById(systemAndCourse: (String, String)): Option[String] = {
    var start = new Date().getTime
    val courseName = Globals.getGroupsProvider(systemAndCourse._1).map(gp => gp.getOrgUnit(systemAndCourse._2) match {
      case Some(ou) => ou.name
      case _ => ""
    })
    println("Loaded conversation group name '" + courseName + "' from D2L in " + (new Date().getTime - start) / 1000 + "s")
    courseName
  }
}
