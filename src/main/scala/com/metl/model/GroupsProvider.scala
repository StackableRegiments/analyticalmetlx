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
      val groupsFilter = (g:OrgUnit) => !blacklistedGroups.exists(bg => {
        bg._1.exists(kp => g.ouType.startsWith(kp)) ||
        bg._2.exists(ks => g.ouType.endsWith(ks)) ||
        bg._3.exists(vp => g.name.startsWith(vp)) ||
        bg._4.exists(vs => g.name.endsWith(vs))
      })
      val personalDetailsFilter = (g:Detail) => !blacklistedInfos.exists(bg => {
        bg._1.exists(kp => g.key.startsWith(kp)) ||
        bg._2.exists(ks => g.key.endsWith(ks)) ||
        bg._3.exists(vp => g.value.startsWith(vp)) ||
        bg._4.exists(vs => g.value.endsWith(vs))
      })
      val membersFilter = (g:Member) => !blacklistedMembers.exists(bg => {
        bg._1.exists(kp => g.name.startsWith(kp)) ||
        bg._2.exists(ks => g.name.endsWith(ks)) 
      })
      new FilteringGroupsProvider(gp.storeId,gp,groupsFilter,membersFilter,personalDetailsFilter)
    }).getOrElse(gp)
  }
  def constructFromXml(outerNodes:NodeSeq):List[GroupsProvider] = {
    (for {
      x <- (outerNodes \\ "smartGroups")
      endpoint <- (x \ "@endpoint").headOption.map(_.text)
      region <- (x \ "@region").headOption.map(_.text)
      iamAccessKey <- (x \ "@accessKey").headOption.map(_.text)
      iamSecretAccessKey <- (x \ "@secretAccessKey").headOption.map(_.text)
      apiGatewayKey = (x \ "@apiKey").headOption.map(_.text)
      groupSize <- (x \ "@groupSize").headOption.map(_.text.toInt)
    } yield {
      val name = (x \ "@name").headOption.map(_.text)
      val n = name.getOrElse("smartGroups_from_%s".format(endpoint))
      new SmartGroupsProvider(n,endpoint,region,iamAccessKey,iamSecretAccessKey,apiGatewayKey,groupSize)
    }).toList :::
    (for {
      in <- (outerNodes \\ "selfGroups")
    } yield {
      val name = (in \ "@name").headOption.map(_.text)
      new SelfGroupsProvider(name.getOrElse("selfGroups"))
    }).toList ::: 
    (for {
      dNodes <- (outerNodes \\ "d2lGroupsProvider")
      host <- (dNodes \ "@host").headOption.map(_.text)
      leApiVersion <- (dNodes \ "@leApiVersion").headOption.map(_.text)
      lpApiVersion <- (dNodes \ "@lpApiVersion").headOption.map(_.text)
      appId <- (dNodes \ "@appId").headOption.map(_.text)
      appKey <- (dNodes \ "@appKey").headOption.map(_.text)
      userId <- (dNodes \ "@userId").headOption.map(_.text)
      userKey <- (dNodes \ "@userKey").headOption.map(_.text)
    } yield {
      val acceptableRoleList:List[Int] = (dNodes \\ "acceptableRoleId").map(_.text.toInt).toList
      val name = (dNodes \\ "@name").headOption.map(_.text)
      val n = name.getOrElse("d2lInteface_to_%s".format(host))
      possiblyFilter(dNodes,new D2LGroupsProvider(n,host,appId,appKey,userId,userKey,leApiVersion,lpApiVersion){
        override protected val acceptableRoleIds = acceptableRoleList
      })
    }).toList ::: 
    (for {
      in <- (outerNodes \\ "flatFileGroups")
      } yield {
      val name = (in \ "@name").headOption.map(_.text)
      (in \\ "@format").headOption.toList.flatMap(ho => ho.text match {
        case "stLeo" => {
          (in \\ "@location").headOption.map(_.text).map(loc => {
            new StLeoFlatFileGroupsProvider(name.getOrElse("SLU_from_%s".format(loc)),loc,TimeSpanParser.parse((in \\ "@refreshPeriod").text),(in \\ "wantsSubgroups").flatMap(n => (n \\ "@username").map(_.text)).toList)
          }).toList
        }
        case "globalOverrides" => {
          (in \\ "@location").headOption.map(_.text).map(loc => {
            new GlobalOverridesGroupsProvider(name.getOrElse("Globals_from_%s".format(loc)),loc,TimeSpanParser.parse((in \\ "@refreshPeriod").text))
          }).toList
        }
        case "adfsGroups" => {
          List(new ADFSGroupsExtractor(name.getOrElse("ADFS groups")))
        }
        case "xmlSpecificOverrides" => {
          (for {
            path <- (in \\ "@location").headOption.map(_.text)
            period <- (in \\ "@refreshPeriod").headOption.map(p => TimeSpanParser.parse(p.text))
          } yield {
            val n = name.getOrElse("xmlUserOverrides_from_%s".format(path))
            new StoreBackedGroupsProvider(n,
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
            new StoreBackedGroupsProvider(n,
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
          val n = name.getOrElse("d2lGroups_from_%s".format(host))
          val acceptableRoleList:List[Int] = (in \\ "acceptableRoleId").map(_.text.toInt).toList
          val diskCache = new XmlGroupStoreDataFile(n,diskStore)
          new StoreBackedGroupsProvider(n,
            new PeriodicallyRefreshingGroupStoreProvider(
              new D2LGroupStoreProvider(host,appId,appKey,userId,userKey,leApiVersion,lpApiVersion){
                override protected val acceptableRoleIds = acceptableRoleList
              },
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
    }).toList.flatten
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



abstract class GroupsProvider(val storeId:String) extends Logger {
  val canQuery:Boolean = false
  
  def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = userData.eligibleGroups.toList
  def getMembersFor(orgUnit:OrgUnit):List[Member] = orgUnit.members
  def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = orgUnit.groupSets
  def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = groupSet.members
  def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = groupSet.groups
  def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = group.members
  
  def getOrgUnit(name:String):Option[OrgUnit]
  def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = userData.informationGroups.toList
}

class ADFSGroupsExtractor(override val storeId:String) extends GroupsProvider(storeId) {
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = userData.eligibleGroups.toList
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = userData.informationGroups.toList
  override def getOrgUnit(name:String):Option[OrgUnit] = None
}

class PassThroughGroupsProvider(override val storeId:String,gp:GroupsProvider) extends GroupsProvider(storeId) {
  override val canQuery:Boolean = gp.canQuery
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = gp.getGroupsFor(userData)
  override def getMembersFor(orgUnit:OrgUnit):List[Member] = gp.getMembersFor(orgUnit)
  override def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = gp.getGroupSetsFor(orgUnit,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = gp.getMembersFor(orgUnit,groupSet)
  override def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = gp.getGroupsFor(orgUnit,groupSet,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = gp.getMembersFor(orgUnit,groupSet,group)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = gp.getPersonalDetailsFor(userData)
  override def getOrgUnit(name:String):Option[OrgUnit] = None
}

class FilteringGroupsProvider(override val storeId:String,gp:GroupsProvider,groupsFilter:OrgUnit => Boolean,membersFilter:Member=>Boolean,personalDetailsFilter:Detail=>Boolean) extends PassThroughGroupsProvider(storeId,gp) {
  override val canQuery:Boolean = gp.canQuery
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = gp.getGroupsFor(userData).filter(groupsFilter)
  override def getMembersFor(orgUnit:OrgUnit):List[Member] = gp.getMembersFor(orgUnit).filter(membersFilter)
  override def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = gp.getGroupSetsFor(orgUnit,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = gp.getMembersFor(orgUnit,groupSet).filter(membersFilter)
  override def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = gp.getGroupsFor(orgUnit,groupSet,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = gp.getMembersFor(orgUnit,groupSet,group).filter(membersFilter)
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = gp.getPersonalDetailsFor(userData).filter(personalDetailsFilter)
  override def getOrgUnit(name:String):Option[OrgUnit] = None
}

class StoreBackedGroupsProvider(override val storeId:String,gs:GroupStoreProvider,usernameOverride:Option[String] = None) extends GroupsProvider(storeId) {
  override val canQuery:Boolean = gs.canQuery
  protected def resolveUser(userData:LiftAuthStateData):String = usernameOverride.flatMap(uo => userData.informationGroups.find(_.key == uo).map(_.value)).getOrElse(userData.username)
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = gs.getGroups.get(resolveUser(userData)).getOrElse(Nil)
  
  override def getMembersFor(orgUnit:OrgUnit):List[Member] = gs.getMembersFor(orgUnit)
  override def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = gs.getGroupSetsFor(orgUnit,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = gs.getMembersFor(orgUnit,groupSet)
  override def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = gs.getGroupsFor(orgUnit,groupSet,members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = gs.getMembersFor(orgUnit,groupSet,group)

  override def getOrgUnit(name:String):Option[OrgUnit] = gs.getOrgUnit(name)
  
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = gs.getPersonalDetails.get(resolveUser(userData)).getOrElse(Nil)
}

case class GroupStoreData(
  groupsForMembers:Map[String,List[OrgUnit]] = Map.empty[String,List[OrgUnit]],
  membersForGroups:Map[String,List[Member]] = Map.empty[String,List[Member]],
  detailsForMembers:Map[String,List[Detail]] = Map.empty[String,List[Detail]],
  orgUnitsByName:Map[String,OrgUnit] = Map.empty[String,OrgUnit],
  groupSetsByOrgUnit:Map[OrgUnit,List[GroupSet]] = Map.empty[OrgUnit,List[GroupSet]],
  groupsByGroupSet:Map[Tuple2[OrgUnit,GroupSet],List[Group]] = Map.empty[Tuple2[OrgUnit,GroupSet],List[Group]]
)

trait GroupStoreProvider extends Logger {
  val canQuery:Boolean = false
  def getData:GroupStoreData = GroupStoreData()
  def getGroups:Map[String,List[OrgUnit]] = getData.groupsForMembers
  def getMembers:Map[String,List[Member]] = getData.membersForGroups
  def getPersonalDetails:Map[String,List[Detail]] = getData.detailsForMembers

  def getOrgUnit(name:String):Option[OrgUnit] = getData.orgUnitsByName.get(name)
  
  def getGroupSet(orgUnit:OrgUnit,name:String):Option[GroupSet] = getData.groupSetsByOrgUnit.get(orgUnit).getOrElse(Nil).find(_.name == name)
  
  def getGroup(orgUnit:OrgUnit,groupSet:GroupSet,name:String):Option[Group] = getData.groupsByGroupSet.get((orgUnit,groupSet)).getOrElse(Nil).find(_.name == name)

  def getMembersFor(orgUnit:OrgUnit):List[Member] = {
    val res = getOrgUnit(orgUnit.name).toList.flatMap(_.members)
    trace("getMembersFor(%s) => %s".format(orgUnit,res))
    res
  }
  def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = getData.groupSetsByOrgUnit.get(orgUnit).getOrElse(Nil)

  def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = {
    val res = getGroupSet(orgUnit,groupSet.name).toList.flatMap(_.members)
    trace("getMembersFor(%s,%s) => %s".format(orgUnit,groupSet,res))
    res
  }
  def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = getData.groupsByGroupSet.get((orgUnit,groupSet)).getOrElse(Nil)
  
  def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = {
    val res = getGroup(orgUnit,groupSet,group.name).toList.flatMap(_.members)
    trace("getMembersFor(%s,%s,%s) => %s".format(orgUnit,groupSet,group,res))
    res
  }
}
class PassThroughGroupStoreProvider(gp:GroupStoreProvider) extends GroupStoreProvider {
  override val canQuery:Boolean = gp.canQuery
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

class GroupStoreDataFile(diskStorePath:String) extends GroupStoreDataSerializers {
  import java.io._
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProvider.sanityCheck(g)

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

class XmlGroupStoreDataFile(storeId:String,diskStorePath:String) extends GroupStoreDataSerializers {
  import scala.xml._
  def sanityCheck(g:GroupStoreData):Boolean = GroupsProvider.sanityCheck(g)
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

class SelfGroupsProvider(override val storeId:String) extends GroupsProvider(storeId) {
  override val canQuery:Boolean = false
  override def getGroupsFor(userData:LiftAuthStateData) = List(OrgUnit("special",userData.username))
  override def getOrgUnit(name:String):Option[OrgUnit] = None
}


abstract class PeriodicallyRefreshingGroupsProvider[T](override val storeId:String,refreshPeriod:TimeSpan) extends GroupsProvider(storeId) { 
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

abstract class PeriodicallyRefreshingFileReadingGroupsProvider[T](override val storeId:String,path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingGroupsProvider[T](storeId,refreshPeriod) { 
  override def shouldCheck = {
    val newCheck = new java.io.File(path).lastModified()
    newCheck > lastModified
  }
}

abstract class PerUserFlatFileGroupsProvider(override val storeId:String,path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingFileReadingGroupsProvider[Map[String,List[OrgUnit]]](storeId,path,refreshPeriod) {
  override def startingValue = Map.empty[String,List[OrgUnit]]
  override protected def parseStore(username:String,store:Map[String,List[OrgUnit]]):List[OrgUnit] = store.get(username).getOrElse(Nil)
}

class GlobalOverridesGroupsProvider(override val storeId:String,path:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingFileReadingGroupsProvider[List[Tuple2[String,String]]](storeId,path,refreshPeriod) with Logger {
  override val canQuery:Boolean = false
  info("created new globalGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override protected def startingValue = Nil
  override def parseStore(username:String,store:List[Tuple2[String,String]]) = store.map(sv => OrgUnit(sv._1,sv._2,List(Member(username,Nil,Some(ForeignRelationship(storeId,username))))))
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

class StLeoFlatFileGroupsProvider(override val storeId:String,path:String,refreshPeriod:TimeSpan, facultyWhoWantSubgroups:List[String] = List.empty[String]) extends PerUserFlatFileGroupsProvider(storeId,path,refreshPeriod) with Logger {
  info("created new stLeoFlatFileGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override val canQuery:Boolean = false
  override def getOrgUnit(name:String):Option[OrgUnit] = None
  override def actuallyFetchGroups:Map[String,List[OrgUnit]] = {
    var rawData = Map.empty[String,List[OrgUnit]]
    Source.fromFile(path).getLines.foreach(line => {
      //sometimes it comes as a csv and other times as a tsv, so converting commas into tabs to begin with
      line.replace(",","\t").split("\t") match {
        case Array(facId,_facFirstName,_facSurname,facUsername,course,section,studentId,studentFirstName,studentSurname,studentUsername,studentStatus) => {
          val subgroups:List[OrgUnit] = facultyWhoWantSubgroups.find(f => f == facUsername).map(f => OrgUnit("ou","%s and %s".format(f,studentUsername))).toList
          studentStatus match {
            case "ACTIVE" => {
              val stuMember = Member(studentUsername,List("firstName" -> studentFirstName,"surname" -> studentSurname).map(t => Detail(t._1,t._2)),Some(ForeignRelationship(storeId,studentId)))
              rawData = rawData.updated(studentUsername,(List(OrgUnit("course",course,List(stuMember),List(GroupSet("section",section,List(stuMember)),GroupSet("ou","%s_%s".format(course,section),List(stuMember))))) ::: subgroups ::: rawData.get(studentUsername).toList.flatten).distinct)
            }
            case _ =>  {}
          }
          val facMember = Member(facUsername,List("firstName" -> _facFirstName,"surname" -> _facSurname).map(t => Detail(t._1,t._2)),Some(ForeignRelationship(storeId,facId)))
          rawData = rawData.updated(facUsername,(List(OrgUnit("course",course,List(facMember),List(GroupSet("section",section,List(facMember)),GroupSet("ou","%s_%s".format(course,section),List(facMember))))) ::: subgroups ::: rawData.get(facUsername).toList.flatten).distinct)
        }
        case _ => {}
      }
    })
    debug("loaded groupData for %s: %s".format(path,rawData))
    rawData
  }
}
