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
  def sanityCheck(g:GroupStoreData):Boolean = {
    g.groupsForMembers.keys.toList.length > 0
  }
  def possiblyFilter(in:NodeSeq,gp:GroupsProvider):GroupsProvider = {
    (for {
      fNodes <- (in \\ "filter").headOption
      blacklistedGroups = (fNodes \\ "group").map(gn => (
        (gn \\ "@keyPrefix").headOption.map(_.text),
        (gn \\ "@keySuffix").headOption.map(_.text),
        (gn \\ "@valuePrefix").headOption.map(_.text),
        (gn \\ "@valueSuffix").headOption.map(_.text)
      ))
      blacklistedInfos = (fNodes \\ "personalDetails").map(gn => (
        (gn \\ "@keyPrefix").headOption.map(_.text),
        (gn \\ "@keySuffix").headOption.map(_.text),
        (gn \\ "@valuePrefix").headOption.map(_.text),
        (gn \\ "@valueSuffix").headOption.map(_.text)
      ))
      blacklistedMembers = (fNodes \\ "member").map(gn => (
        (gn \\ "@prefix").headOption.map(_.text),
        (gn \\ "@suffix").headOption.map(_.text)
      ))
    } yield {
      val groupsFilter = (g:Tuple2[String,String]) => blacklistedGroups.exists(bg => {
        !bg._1.exists(kp => !g._1.startsWith(kp)) &&
        !bg._2.exists(ks => !g._1.endsWith(ks)) &&
        !bg._3.exists(vp => !g._2.startsWith(vp)) &&
        !bg._4.exists(vs => !g._2.endsWith(vs))
      })
      val personalDetailsFilter = (g:Tuple2[String,String]) => blacklistedInfos.exists(bg => {
        !bg._1.exists(kp => !g._1.startsWith(kp)) &&
        !bg._2.exists(ks => !g._1.endsWith(ks)) &&
        !bg._3.exists(vp => !g._2.startsWith(vp)) &&
        !bg._4.exists(vs => !g._2.endsWith(vs))
      })
      val membersFilter = (g:String) => blacklistedMembers.exists(bg => {
        !bg._1.exists(kp => !g.startsWith(kp)) &&
        !bg._2.exists(ks => !g.endsWith(ks)) 
      })
      new FilteringGroupsProvider(gp,groupsFilter,membersFilter,personalDetailsFilter)
    }).getOrElse(gp)
  }
  def createFlatFileGroups(in:NodeSeq):List[GroupsProvider] = {
    (in \\ "@format").headOption.toList.flatMap(ho => ho.text match {
      case "stLeo" => List(new StLeoFlatFileGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text),(in \\ "wantsSubgroups").flatMap(n => (n \\ "@username").map(_.text)).toList))
      case "globalOverrides" => List(new GlobalOverridesGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text)))
     
      case "adfsGroups" => {
        List(new ADFSGroupsExtractor)
      }
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
        }).toList
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
            Some(g => {
              if (sanityCheck(g)){ // don't trash the entire file if the fetch from D2L is empty
                diskCache.write(g)
              }
            })
          )
        ,overrideUsername)
      }).toList
      case _ => Nil
    }).toList.map(gp => possiblyFilter(in,gp))
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

class ADFSGroupsExtractor extends GroupsProvider {
  override def getGroupsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = userData.eligibleGroups.toList
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = userData.informationGroups.toList
}

class PassThroughGroupsProvider(gp:GroupsProvider) extends GroupsProvider {
  override def getGroupsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = gp.getGroupsFor(userData)
  override def getMembersFor(groupName:String):List[String] = gp.getMembersFor(groupName)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = gp.getPersonalDetailsFor(userData)
}

class FilteringGroupsProvider(gp:GroupsProvider,groupsFilter:Tuple2[String,String] => Boolean,membersFilter:String=>Boolean,personalDetailsFilter:Tuple2[String,String]=>Boolean) extends PassThroughGroupsProvider(gp) {
  override def getGroupsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = super.getGroupsFor(userData).filterNot(groupsFilter)
  override def getMembersFor(groupName:String):List[String] = super.getMembersFor(groupName).filterNot(membersFilter)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = super.getPersonalDetailsFor(userData).filterNot(personalDetailsFilter)
}

class StoreBackedGroupsProvider(gs:GroupStoreProvider,usernameOverride:Option[String] = None) extends GroupsProvider {
  protected def resolveUser(userData:LiftAuthStateData):String = {
    val key = usernameOverride.flatMap(uo => userData.informationGroups.find(_._1 == uo).map(_._2)).getOrElse(userData.username)
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
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProvider.sanityCheck(g)
  val startingValue:Option[GroupStoreData] = None
  val startingLastUpdated = 0L
  protected var lastUpdated = startingLastUpdated

  def storeCache(c:GroupStoreData):Unit = {}
  protected def readCache:Option[GroupStoreData] = None
  protected def shouldRefreshCache:Boolean = false
  override def getData = {
    if (shouldRefreshCache){
      val newRes = super.getData
      if (sanityCheck(newRes)){
        storeCache(newRes)
        newRes
      } else {
        readCache.getOrElse(GroupStoreData())
      }
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
    if (cache == None || sanityCheck(c)){
      cache = Some(c)
      lastUpdated = new java.util.Date().getTime()
    }
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
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProvider.sanityCheck(g)
  protected var lastCache:GroupStoreData = startingValue.getOrElse(GroupStoreData())
  protected var cache = new PeriodicallyRefreshingVar[Unit](refreshPeriod,() => {
    val newCheck = new java.util.Date().getTime()
    val newData = gp.getData
    if (sanityCheck(newData)){
      lastCache = newData 
      storeFunc.foreach(sf => sf(lastCache))
    }
  },startingValue.map(sv => {}))
  override def getData = lastCache
}

class GroupStoreDataFile(diskStorePath:String) {
  import com.github.tototoshi.csv._
  import java.io._
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProvider.sanityCheck(g)

  protected val groupName = "GROUP"
  protected val groupTypeName = "TYPE"
  protected val memberName = "MEMBER"
  protected val attributeValue = "VALUE"

  def getLastUpdated:Long = new java.io.File(diskStorePath).lastModified()
  def write(c:GroupStoreData):Unit = {
    if (sanityCheck(c)){
      val writer = CSVWriter.open(new File(diskStorePath))
      writer.writeAll(
        List(memberName,attributeValue,groupTypeName,groupName) :: 
        c.groupsForMembers.toList.flatMap(mi => mi._2.map(g => List(mi._1,mi._1,g._1,g._2))) :::
        c.detailsForMembers.toList.flatMap(mi => mi._2.map(g => List(mi._1,g._2,PersonalInformation.personalInformation,g._1)))
      )
      writer.close
    }
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
