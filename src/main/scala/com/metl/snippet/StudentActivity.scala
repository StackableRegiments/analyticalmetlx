package com.metl.snippet

import java.io.StringReader
import java.util.Date

import com.github.tototoshi.csv.CSVReader
import com.metl.liftAuthenticator.OrgUnit
import com.metl.model.{CacheConfig, D2LGroupsProvider, Globals, ManagedCache}
import com.metl.view.ReportHelper
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

//  protected val config = CacheConfig(10, MemoryUnit.MEGABYTES, MemoryStoreEvictionPolicy.LRU, Some(60))
//  protected val courseCache = new ManagedCache[String, String]("courses", (key: String) => getCourses(key), config)

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
      getAllCourses.getOrElse(List()).map(c => (c.foreignRelationship.get.key.toString, c.name))
  }

  def getOptionsForCurrentUser: List[(String, String)] = {
    blankOption ::
      getCoursesForCurrentUser.getOrElse(List()).map(c => (c.foreignRelationship.get.key.toString, c.name))
  }

  def handler(courseId: String): JsCmd = {
    val stringReader = new StringReader(ReportHelper.studentActivity(courseId))
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
      JField("data", JArray(results._2.filter(r => r.get("ConversationID").nonEmpty || r.get("D2LStudentID").nonEmpty ).map(r => {
        JObject(r.toList.map(kv => JField(kv._1, JString(kv._2))))
      })))
    ))
  }

  /** Retrieve from D2L. */
  protected def getAllCourses: Option[List[OrgUnit]] = {
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
}
