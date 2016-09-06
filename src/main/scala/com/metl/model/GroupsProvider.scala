package com.metl.model

import com.metl.data.{GroupSet=>MeTLGroupSet,Group=>MeTLGroup,_}
import com.metl.utils._
import com.metl.view._

import net.liftweb.http.SessionVar
import net.liftweb.http.LiftRules
import net.liftweb.common._
import net.liftweb.util.Helpers._

import net.liftweb.util.Props
import scala.io.Source
import scala.xml.{Source=>XmlSource,Group=>XmlGroup,_}
import com.metl.liftAuthenticator._//LiftAuthStateData

object GroupsProvider {
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
      val groupsFilter = (g:OrgUnit) => blacklistedGroups.exists(bg => {
        !bg._1.exists(kp => !g.ouType.startsWith(kp)) &&
        !bg._2.exists(ks => !g.ouType.endsWith(ks)) &&
        !bg._3.exists(vp => !g.name.startsWith(vp)) &&
        !bg._4.exists(vs => !g.name.endsWith(vs))
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
  def createFlatFileGroups(in:NodeSeq):GroupsProvider = {
    possiblyFilter(in,(in \\ "@format").text match {
      case "stLeo" => new StLeoFlatFileGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text),(in \\ "wantsSubgroups").flatMap(n => (n \\ "@username").map(_.text)).toList)
      case "globalOverrides" => new GlobalOverridesGroupsProvider((in \\ "@location").text,TimeSpanParser.parse((in \\ "@refreshPeriod").text))
     
      case "adfsGroups" => {
        new ADFSGroupsExtractor
      }
      case "xmlSpecificOverrides" => {
        (for {
          path <- (in \\ "@location").headOption.map(_.text)
          period <- (in \\ "@refreshPeriod").headOption.map(p => TimeSpanParser.parse(p.text))
        } yield {
          new StoreBackedGroupsProvider(
            new PeriodicallyRefreshingGroupStoreProvider(
              new FileWatchingCachingGroupStoreProvider(
                new XmlSpecificOverridesGroupStoreProvider(path),
              path),
              period
            )
          )
        }).getOrElse({
          throw new Exception("missing parameters for specificOverrides groups provider")
        })
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
        val diskCache = new XmlGroupStoreDataFile(diskStore)
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
    })
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
  val course = "course"
  val special = "special"
  val section = "section"
  val sectionCategory = "sectionCategory"
  val groupCategory = "groupCategory"
  val group = "group"
}



trait GroupsProvider extends Logger {
  def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = Nil
  def getMembersFor(groupName:String):List[String] = Nil
  def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = Nil
}

class ADFSGroupsExtractor extends GroupsProvider {
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = userData.eligibleGroups.toList
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = userData.informationGroups.toList
}

class PassThroughGroupsProvider(gp:GroupsProvider) extends GroupsProvider {
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = gp.getGroupsFor(userData)
  override def getMembersFor(groupName:String):List[String] = gp.getMembersFor(groupName)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = gp.getPersonalDetailsFor(userData)
}

class FilteringGroupsProvider(gp:GroupsProvider,groupsFilter:OrgUnit => Boolean,membersFilter:String=>Boolean,personalDetailsFilter:Tuple2[String,String]=>Boolean) extends PassThroughGroupsProvider(gp) {
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = super.getGroupsFor(userData).filterNot(groupsFilter)
  override def getMembersFor(groupName:String):List[String] = super.getMembersFor(groupName).filterNot(membersFilter)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = super.getPersonalDetailsFor(userData).filterNot(personalDetailsFilter)
}

class StoreBackedGroupsProvider(gs:GroupStoreProvider,usernameOverride:Option[String] = None) extends GroupsProvider {
  protected def resolveUser(userData:LiftAuthStateData):String = {
    val key = usernameOverride.flatMap(uo => userData.informationGroups.find(_._1 == uo).map(_._2)).getOrElse(userData.username)
    key
  }
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = gs.getGroups.get(resolveUser(userData)).getOrElse(Nil)
  override def getMembersFor(groupName:String):List[String] = gs.getMembers.get(groupName).getOrElse(Nil)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = gs.getPersonalDetails.get(resolveUser(userData)).getOrElse(Nil)
}

case class GroupStoreData(
  groupsForMembers:Map[String,List[OrgUnit]] = Map.empty[String,List[OrgUnit]],
  membersForGroups:Map[String,List[String]] = Map.empty[String,List[String]],
  detailsForMembers:Map[String,List[Tuple2[String,String]]] = Map.empty[String,List[Tuple2[String,String]]]
)

trait GroupStoreProvider extends Logger {
  def getData:GroupStoreData = GroupStoreData()
  def getGroups:Map[String,List[OrgUnit]] = getData.groupsForMembers
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
    val groupsForMembers = groupData.groupBy(_._1).map(t => (t._1,t._2.map(i => OrgUnit(i._2,i._3,List(i._1)))))
    val membersForGroups = Map.empty[String,List[String]]//groupData.groupBy(_._3).map(t => (t._1,t._2.map(_._1)))
    val data = GroupStoreData(groupsForMembers,membersForGroups)
    data
  }
}

trait GroupHelper {
  def toXml(g:GroupStoreData):NodeSeq = {
    <groupStoreData>{
      g.groupsForMembers.values.toList.flatten.distinct.map(orgUnit => {
        <orgUnit name={orgUnit.name} type={orgUnit.ouType}>{
          orgUnit.members.map(ouMember => {
            <member>{ouMember}</member>
          })
        }{
          orgUnit.groupSets.map(groupSet => {
            <groupSet name={groupSet.name} type={groupSet.groupSetType}>{
              groupSet.members.map(gsMember => {
                <member>{gsMember}</member>
              })
            }{
              groupSet.groups.map(group => {
                <group name={group.name} type={group.groupType}>{
                  group.members.map(gMember => {
                    <member>{gMember}</member>
                  })
                }</group>
              })
            }</groupSet>
          })
        }</orgUnit>
    })}
    {g.detailsForMembers.toList.map(m => {
      <personalDetails username={m._1}>{
        m._2.map(d => {
          <detail key={d._1} value={d._2} />
        })
      }</personalDetails>
    })
    }</groupStoreData>
  }
  def fromXml(xml:NodeSeq):GroupStoreData = {
    val userDetails = Map((xml \\ "personalDetails").flatMap(personalDetailsNode => {
      for {
        username <- (personalDetailsNode \ "@username").headOption.map(_.text)
      } yield {
        val details = (personalDetailsNode \\ "detail").flatMap(detailNode => {
          for {
            key <- (detailNode \ "@key").headOption.map(_.text)
            value <- (detailNode \ "@value").headOption.map(_.text)
          } yield {
            (key,value)
          }
        })
        (username,details.toList)
      }
    }).toList:_*)
    val orgUnits = (xml \\ "orgUnit").flatMap(orgUnitNode => {
      for {
        ouName <- (orgUnitNode \ "@name").headOption.map(_.text) 
        ouType <- (orgUnitNode \ "@type").headOption.map(_.text)
      } yield {
        val ouMembers = (orgUnitNode \ "member").map(_.text).toList
        val groupSets = (orgUnitNode \\ "groupSet").flatMap(groupSetNode => {
          for {
            gsName <- (groupSetNode \ "@name").headOption.map(_.text)
            gsType <- (groupSetNode \ "@type").headOption.map(_.text)
          } yield {
            val gsMembers = (groupSetNode \ "member").map(_.text).toList
            val groups = (groupSetNode \\ "group").flatMap(groupNode => {
              for {
                gName <- (groupNode \ "@name").headOption.map(_.text)
                gType <- (groupNode \ "@type").headOption.map(_.text)
              } yield {
                val gMembers = (groupNode \ "member").map(_.text).toList
                Group(gType,gName,gMembers)
              }
            }).toList
            GroupSet(gsType,gsName,(gsMembers ::: groups.flatMap(_.members)).distinct,groups)
          }
        }).toList
        OrgUnit(ouType,ouName,(ouMembers ::: groupSets.flatMap(_.members)).distinct,groupSets)
      }
    })
    val groupsForMembers = Map(orgUnits.flatMap(_.members).map(m => {
      (m,orgUnits.filter(_.members.contains(m)).toList)
    }):_*)
    val membersForGroups = orgUnits.groupBy(_.name).map(g => (g._1,g._2.flatMap(_.members).toList))
    val personalDetails = userDetails
    GroupStoreData(groupsForMembers,membersForGroups,personalDetails)

  }
}
class XmlSpecificOverridesGroupStoreProvider(path:String) extends GroupStoreProvider with GroupHelper {
  import scala.xml._

  override def getData = {
    val xml = XML.load(path)
    fromXml(xml)
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

  protected val memberName = "MEMBER"
  protected val attributeValue = "VALUE"
  protected val groupTypeName = "TYPE"
  protected val groupName = "GROUP"

  def getLastUpdated:Long = new java.io.File(diskStorePath).lastModified()
  def write(c:GroupStoreData):Unit = {
    val writer = CSVWriter.open(new File(diskStorePath))
    writer.writeAll(
      List(memberName,attributeValue,groupTypeName,groupName) :: 
      c.groupsForMembers.toList.flatMap(mi => mi._2.map(g => g.members.map(m => (m,m,g.ouType,g.name)))) :::
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
        
      val groupsForMembers = groupData.groupBy(_._1).map(t => (t._1,t._2.map(i => OrgUnit(i._3,i._4,List(i._1)))))
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

class XmlGroupStoreDataFile(diskStorePath:String) extends GroupHelper {
  import scala.xml._
  def getLastUpdated:Long = new java.io.File(diskStorePath).lastModified()
  def write(c:GroupStoreData):Unit = {
    XML.save(diskStorePath,toXml(c).head)
  }
  def read:Option[GroupStoreData] = {
    try {
      Some(fromXml(XML.load(diskStorePath)))
    } catch {
      case e:Exception => {
        None
      }
    }
  }
}


// original stuff

class SelfGroupsProvider extends GroupsProvider {
  override def getGroupsFor(userData:LiftAuthStateData) = List(OrgUnit("special",userData.username))
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
  protected def parseStore(username:String,store:T):List[OrgUnit] 
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = parseStore(userData.username,lastCache)
}

abstract class PeriodicallyRefreshingFileReadingGroupsProvider[T](path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingGroupsProvider[T](refreshPeriod) { 
  override def shouldCheck = {
    val newCheck = new java.io.File(path).lastModified()
    newCheck > lastModified
  }
}

abstract class PerUserFlatFileGroupsProvider(path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingFileReadingGroupsProvider[Map[String,List[OrgUnit]]](path,refreshPeriod) {
  override def startingValue = Map.empty[String,List[OrgUnit]]
  override protected def parseStore(username:String,store:Map[String,List[OrgUnit]]):List[OrgUnit] = store.get(username).getOrElse(Nil)
}

class GlobalOverridesGroupsProvider(path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingFileReadingGroupsProvider[List[Tuple2[String,String]]](path,refreshPeriod) with GroupsProvider with Logger {
  info("created new globalGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override protected def startingValue = Nil
  override def parseStore(username:String,store:List[Tuple2[String,String]]) = store.map(sv => OrgUnit(sv._1,sv._2,List(username)))
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
  override def actuallyFetchGroups:Map[String,List[OrgUnit]] = {
    var rawData = Map.empty[String,List[OrgUnit]]
    Source.fromFile(path).getLines.foreach(line => {
      //sometimes it comes as a csv and other times as a tsv, so converting commas into tabs to begin with
      line.replace(",","\t").split("\t") match {
        case Array(facId,_facFirstName,_facSurname,facUsername,course,section,studentId,studentFirstName,studentSurname,studentUsername,studentStatus) => {
          val subgroups:List[OrgUnit] = facultyWhoWantSubgroups.find(f => f == facUsername).map(f => OrgUnit("ou","%s and %s".format(f,studentUsername))).toList
          studentStatus match {
            case "ACTIVE" => {
              rawData = rawData.updated(studentUsername,(List(OrgUnit("course",course,List(studentUsername),List(GroupSet("section",section,List(studentUsername)),GroupSet("ou","%s_%s".format(course,section),List(studentUsername))))) ::: subgroups ::: rawData.get(studentUsername).toList.flatten).distinct)
            }
            case _ =>  {}
          }
          rawData = rawData.updated(facUsername,(List(OrgUnit("course",course,List(facUsername),List(GroupSet("section",section,List(facUsername)),GroupSet("ou","%s_%s".format(course,section),List(facUsername))))) ::: subgroups ::: rawData.get(facUsername).toList.flatten).distinct)
        }
        case _ => {}
      }
    })
    debug("loaded groupData for %s: %s".format(path,rawData))
    rawData
  }
}
