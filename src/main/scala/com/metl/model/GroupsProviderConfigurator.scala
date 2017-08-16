package com.metl.model

import com.metl.TimeSpanParser
import com.metl.external.{Detail, ExternalGroupStoreProviderConfigurator, ExternalGroupsProviderConfigurator, ForeignRelationship, Group, GroupSet, GroupStoreData, GroupStoreProvider, GroupsProvider, LiftAuthStateData, Member, OrgUnit, PeriodicallyRefreshingFileReadingGroupsProvider, PersonalInformation}
import com.metl.utils._
import net.liftweb.common._
import net.liftweb.util.Helpers._

import scala.io.Source
import scala.xml.NodeSeq

object GroupsProviderConfigurator extends ReflectionUtil {
  def sanityCheck(g:GroupStoreData):Boolean = {
    g.groupsForMembers.keys.toList.nonEmpty
  }
  def existsDefaultTrue[A](in:Option[A],pred:A=>Boolean):Boolean = {
    in match {
      case None => true
      case items@Some(_item) => items.exists(pred)
    }
  }
  def allDefaultTrue[A](in:Seq[A],pred:A=>Boolean):Boolean = {
    in match {
      case Nil => true
      case items => !items.exists(i => !pred(i))
    }
  }

  def possiblyFilter(in:NodeSeq,gp:GroupsProvider):GroupsProvider = {
    (for {
      fNodes <- (in \\ "filterNot").headOption
      blacklistedGroups = (fNodes \ "group").map(gn => (
        (gn \ "@keyPrefix").headOption.map(_.text),
        (gn \ "@keySuffix").headOption.map(_.text),
        (gn \ "@valuePrefix").headOption.map(_.text),
        (gn \ "@valueSuffix").headOption.map(_.text),
        (gn \ "@key").headOption.map(_.text),
        (gn \ "@value").headOption.map(_.text)
      ))
    } yield {
      val groupsFilter = (g:OrgUnit) => allDefaultTrue(blacklistedGroups,(bg:Tuple6[Option[String],Option[String],Option[String],Option[String],Option[String],Option[String]]) => {
        !(
          existsDefaultTrue(bg._1,(kp:String) => g.ouType.startsWith(kp)) &&
          existsDefaultTrue(bg._2,(ks:String) => g.ouType.endsWith(ks)) &&
          existsDefaultTrue(bg._3,(vp:String) => g.name.startsWith(vp)) &&
          existsDefaultTrue(bg._4,(vs:String) => g.name.endsWith(vs)) &&
          existsDefaultTrue(bg._5,(k:String) => g.ouType.startsWith(k)) &&
          existsDefaultTrue(bg._6,(v:String) => g.name.startsWith(v))
        )
      })
      new FilteringGroupsProvider(gp.storeId,gp.name,gp,groupsFilter)
    }).getOrElse(gp)
  }
  def constructFromXml(outerNodes:NodeSeq):List[GroupsProvider] = {
    (for {
      x <- (outerNodes \\ "externalLibGroupProvider")
      className <- (x \ "@className").headOption.map(_.text).toList
      result:GroupsProvider <- getExternalClasses[GroupsProvider,ExternalGroupsProviderConfigurator](className,x).right.toOption.getOrElse(Nil)
    } yield {
      possiblyFilter(x,result)
    }).toList :::
      (for {
        in <- (outerNodes \\ "externalLibGroupStoreProvider")
        name = (in \\ "@name").headOption.map(_.text)
        className <- (in \ "@className").headOption.map(_.text).toList
        possibleGroupStoreProviders:Option[List[GroupStoreProvider]] = getExternalClasses[GroupStoreProvider,ExternalGroupStoreProviderConfigurator](className,in).right.toOption
        groupStoreProviders:List[GroupStoreProvider] <- possibleGroupStoreProviders
        diskStore <- (in \\ "@diskStore").headOption.map(_.text)
        overrideUsername = (in \\ "@overrideUsername").headOption.map(_.text)
        refreshPeriod <- (in \\ "@refreshPeriod").headOption.map(s => TimeSpanParser.parse(s.text))
      } yield {
        groupStoreProviders.map(result => {
        val n = name.getOrElse("externalGroupStoreProvider")
        val id = (in \\ "@id").headOption.map(_.text).getOrElse(n)
        val diskCache = new XmlGroupStoreDataFile(id,n,diskStore)
        possiblyFilter(in,new StoreBackedGroupsProvider(id,n,
          new PeriodicallyRefreshingGroupStoreProvider(
            result,
            refreshPeriod,
            diskCache.read,
            Some(g => {
              if (sanityCheck(g)){ // don't trash the entire file if the fetch from the external provider is empty
                diskCache.write(g)
              }
            })
          )
          ,overrideUsername))
        })
      }).flatten.toList :::
    (for {
      in <- (outerNodes \\ "selfGroups")
    } yield {
      val name = (in \ "@name").headOption.map(_.text)
      val n = name.getOrElse("selfGroups")
      val id = (in \ "@id").headOption.map(_.text).getOrElse(n)
      new SelfGroupsProvider(id,n)
    }).toList :::
    (for {
      in <- (outerNodes \\ "flatFileGroups")
      } yield {
      val name = (in \ "@name").headOption.map(_.text)
      (in \\ "@format").headOption.toList.flatMap(ho => ho.text match {
        case "globalOverrides" => {
          (in \\ "@location").headOption.map(_.text).map(loc => {
            val n = name.getOrElse("Globals_from_%s".format(loc))
            val id = (in \ "@id").headOption.map(_.text).getOrElse(n)
            new GlobalOverridesGroupsProvider(id,n,loc,TimeSpanParser.parse((in \\ "@refreshPeriod").text))
          }).toList
        }
        case "adfsGroups" => {
          val n = name.getOrElse("ADFS groups")
          val id = (in \ "@id").headOption.map(_.text).getOrElse(n)
          List(new ADFSGroupsExtractor(id,n))
        }
        case "xmlSpecificOverrides" => {
          (for {
            path <- (in \\ "@location").headOption.map(_.text)
            period <- (in \\ "@refreshPeriod").headOption.map(p => TimeSpanParser.parse(p.text))
          } yield {
            val n = name.getOrElse("xmlUserOverrides_from_%s".format(path))
            val id = (in \ "@id").headOption.map(_.text).getOrElse(n)
            new StoreBackedGroupsProvider(id,n,
              new PeriodicallyRefreshingGroupStoreProvider(
                new FileWatchingCachingGroupStoreProvider(
                  new XmlSpecificOverridesGroupStoreProvider(n,path),
                path),
                period
              )
            )
          }).toList
        }
        case "specificOverrides" => {
          (for {
            path <- (in \\ "@location").headOption.map(_.text)
            period <- (in \\ "@refreshPeriod").headOption.map(p => TimeSpanParser.parse(p.text))
          } yield {
            val n = name.getOrElse("csvUserOverrides_from_%s".format(path))
            val id = (in \ "@id").headOption.map(_.text).getOrElse(n)
            new StoreBackedGroupsProvider(id,n,
              new PeriodicallyRefreshingGroupStoreProvider(
                new FileWatchingCachingGroupStoreProvider(
                  new SpecificOverridesGroupStoreProvider(path),
                path),
                period
              )
            )
          }).toList
        }
        case _ => Nil
      }).toList.map(gp => possiblyFilter(in,gp))
    }).toList.flatten
  }
}

class ADFSGroupsExtractor(override val storeId:String,override val name:String) extends GroupsProvider(storeId,name) {
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = userData.eligibleGroups.toList
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = userData.informationGroups.toList
  override def getAllOrgUnits:List[OrgUnit] = List()
  override def getOrgUnit(name:String):Option[OrgUnit] = None
}

class PassThroughGroupsProvider(override val storeId:String,override val name:String,gp:GroupsProvider) extends GroupsProvider(storeId,name) {
  override val canQuery:Boolean = gp.canQuery
  override val canRestrictConversations:Boolean = gp.canRestrictConversations
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = gp.getGroupsFor(userData)
  override def getMembersFor(orgUnit:OrgUnit):List[Member] = gp.getMembersFor(orgUnit)
  override def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = gp.getGroupSetsFor(orgUnit,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = gp.getMembersFor(orgUnit,groupSet)
  override def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = gp.getGroupsFor(orgUnit,groupSet,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = gp.getMembersFor(orgUnit,groupSet,group)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = gp.getPersonalDetailsFor(userData)
  override def getAllOrgUnits:List[OrgUnit] = List()
  override def getOrgUnit(name:String):Option[OrgUnit] = None
}

class FilteringGroupsProvider(override val storeId:String,override val name:String,gp:GroupsProvider,groupsFilter:OrgUnit => Boolean) extends PassThroughGroupsProvider(storeId,name,gp) {
  protected def filterOrgUnit(in:OrgUnit):Boolean = groupsFilter(in)
  override val canQuery:Boolean = gp.canQuery
  override val canRestrictConversations:Boolean = gp.canRestrictConversations
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = gp.getGroupsFor(userData).filter(filterOrgUnit _)
  override def getMembersFor(orgUnit:OrgUnit):List[Member] = gp.getMembersFor(orgUnit)
  override def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = gp.getGroupSetsFor(orgUnit,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = gp.getMembersFor(orgUnit,groupSet)
  override def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = gp.getGroupsFor(orgUnit,groupSet,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = gp.getMembersFor(orgUnit,groupSet,group)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = gp.getPersonalDetailsFor(userData)
  override def getAllOrgUnits:List[OrgUnit] = List()
  override def getOrgUnit(name:String):Option[OrgUnit] = None
}

class StoreBackedGroupsProvider(override val storeId:String,override val name:String,gs:GroupStoreProvider,usernameOverride:Option[String] = None) extends GroupsProvider(storeId,name) {
  override val canQuery:Boolean = gs.canQuery
  protected def resolveUser(userData:LiftAuthStateData):String = usernameOverride.flatMap(uo => userData.informationGroups.find(_.key == uo).map(_.value)).getOrElse(userData.username)
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = gs.getGroups.get(resolveUser(userData)).getOrElse(Nil)

  override def getMembersFor(orgUnit:OrgUnit):List[Member] = gs.getMembersFor(orgUnit)
  override def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = gs.getGroupSetsFor(orgUnit,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = gs.getMembersFor(orgUnit,groupSet)
  override def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = gs.getGroupsFor(orgUnit,groupSet,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = gs.getMembersFor(orgUnit,groupSet,group)

  override def getAllOrgUnits:List[OrgUnit] = gs.getAllOrgUnits
  override def getOrgUnit(name:String):Option[OrgUnit] = gs.getOrgUnit(name)

  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = gs.getPersonalDetails.get(resolveUser(userData)).getOrElse(Nil)
}

class PassThroughGroupStoreProvider(gp:GroupStoreProvider) extends GroupStoreProvider {
  override val canQuery:Boolean = gp.canQuery
  override def getData:GroupStoreData = gp.getData
}

class CachingGroupStoreProvider(gp:GroupStoreProvider) extends PassThroughGroupStoreProvider(gp) {
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProviderConfigurator.sanityCheck(g)
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

  override val canQuery:Boolean = false
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
        (Member(member,Nil,None),groupType,group)
      }
    })
    val groupsForMembers = groupData.groupBy(_._1).map(t => (t._1.name,t._2.map(i => OrgUnit(i._2,i._3,List(i._1)))))
    val membersForGroups = Map.empty[String,List[Member]]//groupData.groupBy(_._3).map(t => (t._1,t._2.map(_._1)))
    val data = GroupStoreData(groupsForMembers,membersForGroups)
    data
  }
}

trait GroupStoreDataSerializers {
  import com.github.tototoshi.csv._
  import java.io._
  protected val memberName = "MEMBER"
  protected val attributeValue = "VALUE"
  protected val groupTypeName = "TYPE"
  protected val groupName = "GROUP"

  def toCsv(c:GroupStoreData):String = {
    val baos = new ByteArrayOutputStream()
    val writer = CSVWriter.open(baos)
    writer.writeAll(
      List(memberName,attributeValue,groupTypeName,groupName) ::
      c.groupsForMembers.toList.flatMap(mi => mi._2.map(g => g.members.map(m => (m,m,g.ouType,g.name)))) :::
      c.detailsForMembers.toList.flatMap(mi => mi._2.map(g => List(mi._1,g.value,PersonalInformation.personalInformation,g.key)))
    )
    writer.close
    baos.toString("UTF-8")
  }
  def fromCsv(s:String):GroupStoreData = {
    val reader = CSVReader.open(new InputStreamReader(new ByteArrayInputStream(s.getBytes("UTF-8"))))
    val results = reader.allWithHeaders
    reader.close
    val (groupData,information) = results.flatMap(m => {
      for {
        member <- m.get(memberName)
        attr <- m.get(attributeValue)
        groupType <- m.get(groupTypeName)
        group <- m.get(groupName)
      } yield {
        (Member(member,Nil,None),attr,groupType,group)
      }
    }).partition(_._3 != PersonalInformation.personalInformation)

    val groupsForMembers = groupData.groupBy(_._1).map(t => (t._1.name,t._2.map(i => OrgUnit(i._3,i._4,List(i._1)))))
    val membersForGroups = groupData.groupBy(_._4).map(t => (t._1,t._2.map(_._1)))
    val infoForMembers = information.groupBy(_._1).map(t => (t._1.name,t._2.map(i => Detail(i._4,i._2))))
    GroupStoreData(groupsForMembers,membersForGroups,infoForMembers)
  }
  def toXml(g:GroupStoreData):NodeSeq = {
    <groupStoreData>{
      g.groupsForMembers.values.toList.flatten.distinct.map(orgUnit => {
        <orgUnit name={orgUnit.name} type={orgUnit.ouType}>{
          orgUnit.members.map(ouMember => {
            <member>{ouMember.name}</member>
          })
        }{
          orgUnit.groupSets.map(groupSet => {
            <groupSet name={groupSet.name} type={groupSet.groupSetType}>{
              groupSet.members.map(gsMember => {
                <member>{gsMember.name}</member>
              })
            }{
              groupSet.groups.map(group => {
                <group name={group.name} type={group.groupType}>{
                  group.members.map(gMember => {
                    <member>{gMember.name}</member>
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
          <detail key={d.key} value={d.value} />
        })
      }</personalDetails>
    })
    }</groupStoreData>
  }
  def fromXml(storeId:String,xml:NodeSeq):GroupStoreData = {
    val userDetails = Map((xml \\ "personalDetails").flatMap(personalDetailsNode => {
      for {
        username <- (personalDetailsNode \ "@username").headOption.map(_.text)
      } yield {
        val details = (personalDetailsNode \\ "detail").flatMap(detailNode => {
          for {
            key <- (detailNode \ "@key").headOption.map(_.text)
            value <- (detailNode \ "@value").headOption.map(_.text)
          } yield {
            Detail(key,value)
          }
        })
        (username,details.toList)
      }
    }).toList:_*)
    //mark("fromXml personalDetails loaded")
    //
    val intermediaryOrgUnits = new scala.collection.mutable.HashMap[String,OrgUnit]()
    val intermediaryGroupSets = new scala.collection.mutable.HashMap[OrgUnit,List[GroupSet]]()
    val intermediaryGroups = new scala.collection.mutable.HashMap[Tuple2[OrgUnit,GroupSet],List[Group]]()

    val orgUnits = (xml \\ "orgUnit").flatMap(orgUnitNode => {
      for {
        ouName <- (orgUnitNode \ "@name").headOption.map(_.text)
        ouType <- (orgUnitNode \ "@type").headOption.map(_.text)
      } yield {
        val ouMembers = (orgUnitNode \ "member").map(_.text).map(gm => Member(gm,Nil,Some(ForeignRelationship(storeId,gm)))).toList
        val groupSets = (orgUnitNode \\ "groupSet").flatMap(groupSetNode => {
          for {
            gsName <- (groupSetNode \ "@name").headOption.map(_.text)
            gsType <- (groupSetNode \ "@type").headOption.map(_.text)
          } yield {
            val gsMembers = (groupSetNode \ "member").map(_.text).map(gm => Member(gm,Nil,Some(ForeignRelationship(storeId,gm)))).toList
            val groups = (groupSetNode \\ "group").flatMap(groupNode => {
              for {
                gName <- (groupNode \ "@name").headOption.map(_.text)
                gType <- (groupNode \ "@type").headOption.map(_.text)
              } yield {
                val gMembers = (groupNode \ "member").map(_.text).toList
                Group(gType,gName,gMembers.map(gm => Member(gm,Nil,Some(ForeignRelationship(storeId,gm)))),Some(ForeignRelationship(storeId,gName)))
              }
            }).toList
            GroupSet(gsType,gsName,(gsMembers ::: groups.flatMap(_.members)).distinct,groups,Some(ForeignRelationship(storeId,gsName)))
          }
        }).toList
        val ou = OrgUnit(ouType,ouName,(ouMembers ::: groupSets.flatMap(_.members)).distinct,groupSets,Some(ForeignRelationship(storeId,ouName)))
        intermediaryOrgUnits += ((ouName,ou))
        intermediaryGroupSets += ((ou.copy(groupSets = Nil,members = Nil),ou.groupSets))
        ou.groupSets.foreach(gs => {
          intermediaryGroups += (((ou.copy(groupSets = Nil,members = Nil),gs.copy(groups = Nil,members = Nil)),gs.groups))
        })
        ou
      }
    })
    //mark("fromXml orgUnits loaded")
    val intermediaryMemberGrouping = new scala.collection.mutable.HashMap[Member,List[OrgUnit]]()
    for {
      ou <- orgUnits
      member <- ou.members
    } yield {
      intermediaryMemberGrouping += ((member,ou :: intermediaryMemberGrouping.get(member).getOrElse(Nil)))
    }
    val groupsForMembers = intermediaryMemberGrouping.map(t => (t._1.name,t._2)).toMap
  /*
    val members = orgUnits.flatMap(_.members).distinct
    val groupsForMembers = Map(members.map(m => {
      (m,orgUnits.filter(_.members.contains(m)).toList)
    }):_*)
  */
    //mark("fromXml groupsForMembers(%s (%s avg)) formed".format(groupsForMembers.keys.toList.length,groupsForMembers.values.toList.map(_.length).sum))
    val membersForGroups = orgUnits.groupBy(_.name).map(g => (g._1,g._2.flatMap(_.members).toList))
    //mark("fromXml membersForGroups formed")
    val personalDetails = userDetails
    //mark("fromXml personalDetails formed")
    //
    //
    val orgUnitsByName:Map[String,OrgUnit] = intermediaryOrgUnits.toMap
    val groupSetsByOrgUnit:Map[OrgUnit,List[GroupSet]] = intermediaryGroupSets.toMap
    val groupsByGroupSet:Map[Tuple2[OrgUnit,GroupSet],List[Group]] = intermediaryGroups.toMap
    GroupStoreData(groupsForMembers,membersForGroups,personalDetails,orgUnitsByName,groupSetsByOrgUnit,groupsByGroupSet)
  }
}
class XmlSpecificOverridesGroupStoreProvider(storeId:String,path:String) extends GroupStoreProvider with GroupStoreDataSerializers {
  import scala.xml._
  override val canQuery = true
  override def getData = {
    val xml = XML.load(path)
    fromXml(storeId,xml)
  }
}

class InMemoryCachingGroupStoreProvider(gp:GroupStoreProvider) extends CachingGroupStoreProvider(gp) {
  override val canQuery:Boolean = gp.canQuery
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
  override val canQuery:Boolean = gp.canQuery
  override val startingValue:Option[GroupStoreData] = Some(gp.getData)
  protected def getFileLastModified = new java.io.File(path).lastModified()
  override val startingLastUpdated = getFileLastModified
  override def shouldRefreshCache = lastUpdated < getFileLastModified
}

class PeriodicallyRefreshingGroupStoreProvider(gp:GroupStoreProvider,refreshPeriod:TimeSpan,startingValue:Option[GroupStoreData] = None,storeFunc:Option[GroupStoreData=>Unit] = None) extends GroupStoreProvider {
  override val canQuery:Boolean = gp.canQuery
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProviderConfigurator.sanityCheck(g)
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

class GroupStoreDataFile(diskStorePath:String) extends GroupStoreDataSerializers {
  import java.io._
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProviderConfigurator.sanityCheck(g)

  def getLastUpdated:Long = new java.io.File(diskStorePath).lastModified()
  def write(c:GroupStoreData):Unit = {
    new PrintWriter(diskStorePath){
      write(toCsv(c))
      close
    }
  }
  def read:Option[GroupStoreData] = {
    try {
      Some(fromCsv(Source.fromFile(diskStorePath).mkString))
    } catch {
      case e:Exception => {
        None
      }
    }
  }
}

class XmlGroupStoreDataFile(storeId:String,name:String,diskStorePath:String) extends GroupStoreDataSerializers {
  import scala.xml._
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProviderConfigurator.sanityCheck(g)
  def getLastUpdated:Long = new java.io.File(diskStorePath).lastModified()
  def write(c:GroupStoreData):Unit = {
    if (sanityCheck(c)){
      XML.save(diskStorePath,toXml(c).head)
    }
  }
  def read:Option[GroupStoreData] = {
    try {
      val xml = XML.load(diskStorePath)
      Some(fromXml(storeId,xml))
    } catch {
      case e:Exception => {
        None
      }
    }
  }
}

// original stuff

class SelfGroupsProvider(override val storeId:String,override val name:String) extends GroupsProvider(storeId,name) {
  override val canQuery:Boolean = false
  override def getGroupsFor(userData:LiftAuthStateData) = List(OrgUnit("special",userData.username))
  override def getAllOrgUnits:List[OrgUnit] = List()
  override def getOrgUnit(name:String):Option[OrgUnit] = None
}



class GlobalOverridesGroupsProvider(override val storeId:String,override val name:String,path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingFileReadingGroupsProvider[List[Tuple2[String,String]]](storeId,name,path,refreshPeriod) with Logger {
  override val canQuery:Boolean = false
  info("created new globalGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override protected def startingValue = Nil
  override def parseStore(username:String,store:List[Tuple2[String,String]]) = store.map(sv => OrgUnit(sv._1,sv._2,List(Member(username,Nil,Some(ForeignRelationship(storeId,username))))))
  override def getAllOrgUnits:List[OrgUnit] = List()
  override def getOrgUnit(name:String):Option[OrgUnit] = None
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

