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

object GroupsProvider {
  def createFlatFileGroups(in:NodeSeq):GroupsProvider = {
    (in \\ "@format").text match {
      case "stLeo" => new StLeoFlatFileGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text),(in \\ "wantsSubgroups").flatMap(n => (n \\ "@username").map(_.text)).toList)
      case "globalOverrides" => new GlobalOverridesGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text))
      case "specificOverrides" => new SpecificOverridesGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text))
      case "d2l" => (for {
        host <- (in \\ "@host").headOption.map(_.text)
        leApiVersion <- (in \\ "@leApiVersion").headOption.map(_.text)
        lpApiVersion <- (in \\ "@lpApiVersion").headOption.map(_.text)
        appId <- (in \\ "@appId").headOption.map(_.text)
        appKey <- (in \\ "@appKey").headOption.map(_.text)
        userId <- (in \\ "@userId").headOption.map(_.text)
        userKey <- (in \\ "@userKey").headOption.map(_.text)
        diskStore <- (in \\ "@diskStore").headOption.map(_.text)
        refreshPeriod <- (in \\ "@refreshPeriod").headOption.map(s => TimeSpanParser.parse(s.text))
      } yield {
        new PeriodicD2LGroupsProvider(host,appId,appKey,userId,userKey,leApiVersion,lpApiVersion,refreshPeriod,diskStore)
      }).getOrElse({
        throw new Exception("missing parameters for d2l groups provider")
      })
      case _ => throw new Exception("unrecognized flatfile format")
    }
  }
}

trait GroupsProvider extends Logger {
  def getGroupsFor(username:String):List[Tuple2[String,String]] = Nil
}

trait GroupStoreProvider extends Logger {
  def getGroups:Map[String,List[Tuple2[String,String]]] = Map.empty[String,List[Tuple2[String,String]]]
}
/*
class PassThroughGroupStoreProvider(gp:GroupStoreProvider) extends GroupStoreProvider {
  override def getGroups:Map[String,List[Tuple2[String,String]]] = internalGetGroups
  protected def internalGetGroups = Map.empty[String,List[Tuple2[String,String]]]
}

abstract class CachingGroupStoreProvider(gp:GroupStoreProvider) extends PassThroughGroupStoreProvider {
  protected def storeCache(c:Map[String,List[Tuple2[String,String]]]):Unit
  protected def readCache:Map[String,List[Tuple2[String,String]]]
  protected def shouldRefreshCache:Boolean
  override def internalGetGroups = {
    if (shouldRefreshCache){
      val newRes = gp.getGroups
      storeCache(newRes)
      newRes
    } else {
      readCache
    }
  }
}

class InMemoryCachingGroupStoreProvider(gp:GroupStoreProvider,acceptableStaleness:TimeSpan) extends CachingGroupStoreProvider(gp) {
  protected val startingValue:Option[Map[String,List[Tuple2[String,String]]]] = None
  protected val startingLastUpdated = 0L
  protected var lastUpdated = startingLastUpdated
  protected var cache:Option[Map[String,List[Tuple2[String,String]]]] = None
  override protected def storeCache(c:Map[String,List[Tuple2[String,String]]]):Unit = {
    cache = Some(c)
    lastUpdated = new java.util.Date().getTime()
  }
  override protected def readCache:Map[String,List[Tuple2[String,String]]] = cache.getOrElse(Map.empty[String,List[Tuple2[String,String]]])
  protected def shouldRefreshCache:Boolean = {
    cache.map(c => {
      (new java.util.Date().getTime() - lastUpdated) > acceptableStaleness.millis
    }).getOrElse(true)
  }
}

class PeriodicallyRefreshingGroupStoreProvider(gp:GroupStoreProvider,refreshPeriod:TimeSpan,startingValue:Option[Map[String,List[Tuple2[String,String]]]] = None) extends PassThroughGroupStoreProvider { 
  protected var lastCache:Map[String,List[Tuple2[String,String]]] = startingValue.getOrElse(Map.empty[String,List[Tuple2[String,String]]])
  protected var cache = new PeriodicallyRefreshingVar[Unit](refreshPeriod,() => {
    val newCheck = new java.util.Date().getTime()
    lastCache = gp.getGroups 
  },startingValue)
  override def internalGetGroups = lastCache
}
class FileBackedInMemoryCachingGroupStoreProvider(gp:GroupStoreProvider,acceptableStaleness:TimeSpan,diskStorePath:String) extends InMemoryCachingGroupStoreProvider(gp,acceptableStaleness) {
  import com.github.tototoshi.csv._
  import java.io._
  protected val groupName = "GROUP"
  protected val groupTypeName = "TYPE"
  protected val memberName = "MEMBER"

  override protected val startingValue:Option[Map[String,List[Tuple2[String,String]]]] = Some(readFromDisk)
  override protected val startingLastUpdated = getDiskLastUpdated
  override protected def storeCache(c:Map[String,List[Tuple2[String,String]]]):Unit = {
    super.storeCache(c)
    storeToDisk(c)
  }
  protected def getDiskLastUpdated:Long = new java.io.File(diskStorePath).lastModified()
  protected def storeToDisk(c:Map[String,List[Tuple2[String,String]]]):Unit = {
    val writer = CSVWriter.open(new File(diskStorePath))
    writer.writeAll(List(memberName,groupTypeName,groupName) :: c.toList.flatMap(mi => mi._2.map(g => List(mi._1,g._1,g._2))))
    writer.close
  }
  protected def readFromDisk:Map[String,List[Tuple2[String,String]]] = {
    val reader = CSVReader.open(new File(diskStorePath))
    val results = reader.allWithHeaders
    reader.close
    results.flatMap(m => {
      for {
        member <- m.get(memberName)
        groupType <- m.get(groupTypeName)
        group <- m.get(groupName)
      } yield {
        (member,(groupType,group))
      }
    }).groupBy(_._1).map(t => (t._1,t._2.map(_._2)))
  }
}
class PeriodicallyRefreshingFileBackedInMemoryCachingGroupStoreProvider(gp:GroupStoreProvider,acceptableStaleness:TimeSpan,diskStorePath:String) extends FileBackedInMemoryCachingGroupStoreProvider(gp,acceptableStaleness,diskStorePath) {
  val periodicCache = new PeriodicallyRefreshingGroupStoreProvider(gp,acceptableStaleness,startingValue)
}
*/
class SelfGroupsProvider extends GroupsProvider {
  override def getGroupsFor(username:String) = List(("ou",username))
}

abstract class PerUserFlatFileGroupsProvider(path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingFileReadingGroupsProvider[Map[String,List[Tuple2[String,String]]]](path,refreshPeriod) {
  override def startingValue = Map.empty[String,List[Tuple2[String,String]]]
  override protected def parseStore(username:String,store:Map[String,List[Tuple2[String,String]]]):List[Tuple2[String,String]] = store.get(username).getOrElse(Nil)
}
abstract class PeriodicallyRefreshingFileReadingGroupsProvider[T](path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingGroupsProvider[T](refreshPeriod) { 
  override def shouldCheck = {
    val newCheck = new java.io.File(path).lastModified()
    newCheck > lastModified
  }
}

class CachingGroupsProvider(gp:GroupsProvider) extends GroupsProvider {

}

class PeriodicallyCheckGroupsProvider(gp:GroupsProvider) extends GroupsProvider {

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
  override def getGroupsFor(username:String):List[Tuple2[String,String]] = parseStore(username,lastCache)
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
    println("loaded groupData for %s: %s".format(path,rawData))
    rawData
  }
}

class SpecificOverridesGroupsProvider(path:String,refreshPeriod:TimeSpan) extends PerUserFlatFileGroupsProvider(path,refreshPeriod) with Logger {
  info("created new specificGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override def actuallyFetchGroups:Map[String,List[Tuple2[String,String]]] = {
    var rawData = Map.empty[String,List[Tuple2[String,String]]]
    Source.fromFile(path).getLines.foreach(line => {
      line.split("\t") match {
        case Array(username,groupType,groupKey) => {
          rawData = rawData.updated(username,(List((groupType,groupKey)) ::: rawData.get(username).toList.flatten).distinct)
        }
        case _ => {}
      }
    })
    println("loaded groupData for %s: %s".format(path,rawData))
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
