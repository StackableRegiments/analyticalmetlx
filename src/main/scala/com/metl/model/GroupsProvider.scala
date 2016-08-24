package com.metl.model

import com.metl.data._
import com.metl.utils._
import com.metl.view._

import net.liftweb.http.SessionVar
import net.liftweb.http.LiftRules
import net.liftweb.common._
import net.liftweb.util.Helpers._

import net.liftweb.util.Props
import scala.io.Source
import scala.xml.{Source=>XmlSource,_}
import com.metl.liftAuthenticator.LiftAuthStateData

object GroupsProvider {
  def createFlatFileGroups(in:NodeSeq):GroupsProvider = {
    (in \\ "@format").text match {
      case "stLeo" => new StLeoFlatFileGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text),(in \\ "wantsSubgroups").flatMap(n => (n \\ "@username").map(_.text)).toList)
      case "globalOverrides" => new GlobalOverridesGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text))
      
      case "specificOverrides" => {
        (for {
          path <- (in \\ "@location").headOption.map(_.text)
          period <- (in \\ "@refreshPeriod").headOption.map(p => TimeSpanParser.parse(p.text))
        } yield {
          new StoreBackedGroupsProvider(
            new PeriodicallyRefreshingGroupStoreProvider(
              new FileWatchingCachingGroupStoreProvider(
                new SpecificOverridesGroupStoreProvider(path),
              path),
              period
            )
          )
        }).getOrElse({
          throw new Exception("missing parameters for specificOverrides groups provider")
        })
      }
      case "d2l" => (for {
        host <- (in \\ "@host").headOption.map(_.text)
        leApiVersion <- (in \\ "@leApiVersion").headOption.map(_.text)
        lpApiVersion <- (in \\ "@lpApiVersion").headOption.map(_.text)
        appId <- (in \\ "@appId").headOption.map(_.text)
        appKey <- (in \\ "@appKey").headOption.map(_.text)
        userId <- (in \\ "@userId").headOption.map(_.text)
        userKey <- (in \\ "@userKey").headOption.map(_.text)
        diskStore <- (in \\ "@diskStore").headOption.map(_.text)
        overrideUsername = (in \\ "@overrideUsername").headOption.map(_.text)
        refreshPeriod <- (in \\ "@refreshPeriod").headOption.map(s => TimeSpanParser.parse(s.text))
      } yield {
        val diskCache = new GroupStoreDataFile(diskStore)
        new StoreBackedGroupsProvider(
          new PeriodicallyRefreshingGroupStoreProvider(
            new D2LGroupStoreProvider(host,appId,appKey,userId,userKey,leApiVersion,lpApiVersion),
            refreshPeriod,
            diskCache.read,
            Some(g => diskCache.write(g))
          )
        ,overrideUsername)
      }).getOrElse({
        throw new Exception("missing parameters for d2l groups provider")
      })
      case _ => throw new Exception("unrecognized flatfile format")
    }
  }
}

object PersonalInformation {
  val personalInformation = "personalInformation"
  val email = "email"
  val firstName = "firstName"
  val surname = "surname"
  val displayName = "displayName"
}

object GroupKeys {
  val ou = "ou"
  val section = "section"
  val groupCategory = "groupCategory"
  val group = "group"
}

trait GroupsProvider extends Logger {
  def getGroupsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = Nil
  def getMembersFor(groupName:String):List[String] = Nil
  def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = Nil
}

class StoreBackedGroupsProvider(gs:GroupStoreProvider,usernameOverride:Option[String] = None) extends GroupsProvider {
  protected def resolveUser(userData:LiftAuthStateData):String = {
    val key = usernameOverride.flatMap(uo => userData.informationGroups.find(_._1 == uo).map(_._2)).getOrElse(userData.username)
    println("resolveUser: %s => %s".format(userData,key))
    key
  }
  override def getGroupsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = gs.getGroups.get(resolveUser(userData)).getOrElse(Nil)
  override def getMembersFor(groupName:String):List[String] = gs.getMembers.get(groupName).getOrElse(Nil)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = gs.getPersonalDetails.get(resolveUser(userData)).getOrElse(Nil)
}

case class GroupStoreData(
  groupsForMembers:Map[String,List[Tuple2[String,String]]] = Map.empty[String,List[Tuple2[String,String]]],
  membersForGroups:Map[String,List[String]] = Map.empty[String,List[String]],
  detailsForMembers:Map[String,List[Tuple2[String,String]]] = Map.empty[String,List[Tuple2[String,String]]]
)

trait GroupStoreProvider extends Logger {
  def getData:GroupStoreData = GroupStoreData()
  def getGroups:Map[String,List[Tuple2[String,String]]] = getData.groupsForMembers
  def getMembers:Map[String,List[String]] = getData.membersForGroups
  def getPersonalDetails:Map[String,List[Tuple2[String,String]]] = getData.detailsForMembers
}
class PassThroughGroupStoreProvider(gp:GroupStoreProvider) extends GroupStoreProvider {
  override def getData:GroupStoreData = gp.getData
}

class CachingGroupStoreProvider(gp:GroupStoreProvider) extends PassThroughGroupStoreProvider(gp) {
  val startingValue:Option[GroupStoreData] = None
  val startingLastUpdated = 0L
  protected var lastUpdated = startingLastUpdated

  def storeCache(c:GroupStoreData):Unit = {}
  protected def readCache:Option[GroupStoreData] = None
  protected def shouldRefreshCache:Boolean = false
  override def getData = {
    if (shouldRefreshCache){
      val newRes = super.getData
      storeCache(newRes)
      newRes
    } else {
      readCache.getOrElse(GroupStoreData())
    }
  }
}


class SpecificOverridesGroupStoreProvider(path:String) extends GroupStoreProvider {
  import com.github.tototoshi.csv._
  import java.io._

  protected val groupName = "GROUP"
  protected val groupTypeName = "TYPE"
  protected val memberName = "MEMBER"

  override def getData = {
    val reader = CSVReader.open(new File(path))
    val results = reader.allWithHeaders
    reader.close
    val groupData = results.flatMap(m => {
      for {
        member <- m.get(memberName)
        groupType <- m.get(groupTypeName)
        group <- m.get(groupName)
      } yield {
        (member,groupType,group)
      }
    })
    val groupsForMembers = groupData.groupBy(_._1).map(t => (t._1,t._2.map(i => (i._2,i._3))))
    val membersForGroups = groupData.groupBy(_._3).map(t => (t._1,t._2.map(_._1)))
    val data = GroupStoreData(groupsForMembers,membersForGroups)
    data
  }
}

class InMemoryCachingGroupStoreProvider(gp:GroupStoreProvider) extends CachingGroupStoreProvider(gp) {
  protected var cache:Option[GroupStoreData] = None
  override def storeCache(c:GroupStoreData):Unit = {
    cache = Some(c)
    lastUpdated = new java.util.Date().getTime()
  }
  override protected def readCache:Option[GroupStoreData] = cache
}

class FileWatchingCachingGroupStoreProvider(gp:GroupStoreProvider,path:String) extends InMemoryCachingGroupStoreProvider(gp) {
  override val startingValue:Option[GroupStoreData] = Some(gp.getData)
  protected def getFileLastModified = new java.io.File(path).lastModified()
  override val startingLastUpdated = getFileLastModified
  override def shouldRefreshCache = lastUpdated < getFileLastModified
}

class PeriodicallyRefreshingGroupStoreProvider(gp:GroupStoreProvider,refreshPeriod:TimeSpan,startingValue:Option[GroupStoreData] = None,storeFunc:Option[GroupStoreData=>Unit] = None) extends GroupStoreProvider { 
  protected var lastCache:GroupStoreData = startingValue.getOrElse(GroupStoreData())
  protected var cache = new PeriodicallyRefreshingVar[Unit](refreshPeriod,() => {
    val newCheck = new java.util.Date().getTime()
    lastCache = gp.getData 
    storeFunc.foreach(sf => sf(lastCache))
  },startingValue.map(sv => {}))
  override def getData = lastCache
}

class GroupStoreDataFile(diskStorePath:String) {
  import com.github.tototoshi.csv._
  import java.io._

  protected val groupName = "GROUP"
  protected val groupTypeName = "TYPE"
  protected val memberName = "MEMBER"
  protected val attributeValue = "VALUE"

  def getLastUpdated:Long = new java.io.File(diskStorePath).lastModified()
  def write(c:GroupStoreData):Unit = {
    val writer = CSVWriter.open(new File(diskStorePath))
    writer.writeAll(
      List(memberName,attributeValue,groupTypeName,groupName) :: 
      c.groupsForMembers.toList.flatMap(mi => mi._2.map(g => List(mi._1,mi._1,g._1,g._2))) :::
      c.detailsForMembers.toList.flatMap(mi => mi._2.map(g => List(mi._1,g._2,PersonalInformation.personalInformation,g._1)))
    )
    writer.close
  }
  def read:Option[GroupStoreData] = {
    try {
      val reader = CSVReader.open(new File(diskStorePath))
      val results = reader.allWithHeaders
      reader.close
      val (groupData,information) = results.flatMap(m => {
        for {
          member <- m.get(memberName)
          attr <- m.get(attributeValue)
          groupType <- m.get(groupTypeName)
          group <- m.get(groupName)
        } yield {
          (member,attr,groupType,group)
        }
      }).partition(_._3 != PersonalInformation.personalInformation)
        
      val groupsForMembers = groupData.groupBy(_._1).map(t => (t._1,t._2.map(i => (i._3,i._4))))
      val membersForGroups = groupData.groupBy(_._4).map(t => (t._1,t._2.map(_._1)))
      val infoForMembers = information.groupBy(_._1).map(t => (t._1,t._2.map(i => (i._4,i._2))))
      Some(GroupStoreData(groupsForMembers,membersForGroups,infoForMembers))
    } catch {
      case e:Exception => {
        None
      }
    }
  }
}

// original stuff

class SelfGroupsProvider extends GroupsProvider {
  override def getGroupsFor(userData:LiftAuthStateData) = List(("ou",userData.username))
}


abstract class PeriodicallyRefreshingGroupsProvider[T](refreshPeriod:TimeSpan) extends GroupsProvider { 
  protected val timespan = refreshPeriod
  protected var lastModified:Long = 0
  protected def startingValue:T
  protected var lastCache:T = startingValue
  protected var cache = new PeriodicallyRefreshingVar[Unit](timespan,() => {
    if (shouldCheck){
      val newCheck = new java.util.Date().getTime()
      lastCache = actuallyFetchGroups
      lastModified = newCheck
    }
  })
  protected def shouldCheck:Boolean
  protected def actuallyFetchGroups:T
  protected def parseStore(username:String,store:T):List[Tuple2[String,String]] 
  override def getGroupsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = parseStore(userData.username,lastCache)
}

abstract class PeriodicallyRefreshingFileReadingGroupsProvider[T](path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingGroupsProvider[T](refreshPeriod) { 
  override def shouldCheck = {
    val newCheck = new java.io.File(path).lastModified()
    newCheck > lastModified
  }
}

abstract class PerUserFlatFileGroupsProvider(path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingFileReadingGroupsProvider[Map[String,List[Tuple2[String,String]]]](path,refreshPeriod) {
  override def startingValue = Map.empty[String,List[Tuple2[String,String]]]
  override protected def parseStore(username:String,store:Map[String,List[Tuple2[String,String]]]):List[Tuple2[String,String]] = store.get(username).getOrElse(Nil)
}

class GlobalOverridesGroupsProvider(path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingFileReadingGroupsProvider[List[Tuple2[String,String]]](path,refreshPeriod) with GroupsProvider with Logger {
  info("created new globalGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override protected def startingValue = Nil
  override def parseStore(username:String,store:List[Tuple2[String,String]]) = store
  override def actuallyFetchGroups:List[Tuple2[String,String]] = {
    var rawData = List.empty[Tuple2[String,String]]
    Source.fromFile(path).getLines.foreach(line => {
      line.split("\t") match {
        case Array(groupType,groupKey) => {
          rawData = (List((groupType,groupKey)) ::: rawData).distinct
        }
        case _ => {}
      }
    })
    rawData
  }
}

class StLeoFlatFileGroupsProvider(path:String,refreshPeriod:TimeSpan, facultyWhoWantSubgroups:List[String] = List.empty[String]) extends PerUserFlatFileGroupsProvider(path,refreshPeriod) with Logger {
  info("created new stLeoFlatFileGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override def actuallyFetchGroups:Map[String,List[Tuple2[String,String]]] = {
    var rawData = Map.empty[String,List[Tuple2[String,String]]]
    Source.fromFile(path).getLines.foreach(line => {
      //sometimes it comes as a csv and other times as a tsv, so converting commas into tabs to begin with
      line.replace(",","\t").split("\t") match {
        case Array(facId,_facFirstName,_facSurname,facUsername,course,section,studentId,studentFirstName,studentSurname,studentUsername,studentStatus) => {
          val subgroups:List[Tuple2[String,String]] = facultyWhoWantSubgroups.find(f => f == facUsername).map(f => ("ou","%s and %s".format(f,studentUsername))).toList
          studentStatus match {
            case "ACTIVE" => {
              rawData = rawData.updated(studentUsername,(List(("ou",section),("ou",course),("ou","%s_%s".format(course,section))) ::: subgroups ::: rawData.get(studentUsername).toList.flatten).distinct)
            }
            case _ =>  {}
          }
          rawData = rawData.updated(facUsername,(List(("ou",section),("ou",course),("ou","%s_%s".format(course,section))) ::: subgroups ::: rawData.get(facUsername).toList.flatten).distinct)
        }
        case _ => {}
      }
    })
    debug("loaded groupData for %s: %s".format(path,rawData))
    rawData
  }
}
