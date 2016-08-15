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
import com.d2lvalence.idkeyauth._
import com.d2lvalence.idkeyauth.implementation._
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json._

case class D2LPagingInfo(
  Bookmark:Option[String],
  HasMoreItems:Boolean
)
case class D2LOrgUnitResponse(
  PagingInfo:D2LPagingInfo,
  Items:List[D2LOrgUnit]
)
case class D2LOrgUnitTypeInfo(
  Id:Int,
  Code:String,
  Name:String
)
case class D2LOrgUnit(
  Identifier:String, //this is actually a number
  Type:D2LOrgUnitTypeInfo,
  Name:String, // this is being filled with unitCode (EDU300)
  Code:Option[String], //this is being used like the class group identifier (EDU300-CA01)
  HomeUrl:Option[String],
  ImageUrl:Option[String]
)
case class D2LClassListUser(
  Identifier:String, //this is actually a number, and is the number used in enrollments elsewhere
  ProfileIdentifier:String,
  DisplayName:String,
  UserName:Option[String],
  OrgDefinedId:Option[String],
  Email:Option[String],
  FirstName:Option[String],
  LastName:Option[String]
)
case class D2LGroupCategory(
  GroupCategoryId:Long,
  Name:String,
  Description:String, //richtext
  EnrollmentStyle:Int,
  EnrollmentQuantity:Option[Int],
  MaxUsersPerGroup:Option[Int],
  AutoEnroll:Boolean,
  RandomizeEnrollments:Boolean,
  Groups:List[Long],
  AllocateAfterExpiry:Boolean,
  SelfEnrollmentExpiryDate:Option[String]
)
case class D2LGroup(
  GroupId:Long,
  Name:String,
  Description:String, //richtext
  Enrollments:List[Long]
)
case class D2LSection(
  SectionId:Long,
  Name:String,
  Description:String, //richtext
  Enrollments:List[Long]
)
 
/*

class PeriodicD2LGroupsProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String,refreshPeriod:TimeSpan,diskStorePath:String) extends GroupsProvider {
  info("created new D2LGroupsProvider for %s (every %s)".format(d2lBaseUrl,refreshPeriod))
  
  import com.github.tototoshi.csv._
  import java.io._
  
  protected val groupName = "GROUP"
  protected val groupTypeName = "TYPE"
  protected val memberName = "MEMBER"

  protected val startingValue = {
    if (new File(diskStorePath).exists()) {
      Some(readFromDisk)
    } else {
      None
    }
  }
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
  protected var lastCache:Map[String,List[Tuple2[String,String]]] = startingValue.getOrElse(Map.empty[String,List[Tuple2[String,String]]])
  protected val cache = new PeriodicallyRefreshingVar[Unit](refreshPeriod,() => {
    val provider = new D2LGroupsProvider(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion)
    lastCache = provider.fetchGroups
    storeToDisk(lastCache)
    println("loaded GroupData for: %s".format(lastCache))
  },startingValue.map(i => {}))
  override def getGroupsFor(username:String):List[Tuple2[String,String]] = lastCache.get(username).getOrElse(Nil)
}
*/

class D2LGroupStoreProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends GroupStoreProvider {

  protected val httpConnectionTimeout = 10 // 10 seconds
  protected val httpReadTimeout = 60 * 1000 // 60 seconds
  val client = new com.metl.utils.CleanHttpClient(com.metl.utils.Http.getConnectionManager){
    override val connectionTimeout = httpConnectionTimeout
    override val readTimeout = httpReadTimeout
  }

  protected implicit def formats = net.liftweb.json.DefaultFormats

  val bookMarkTag = "bookmark"

  protected def fetchListFromD2L[T](url:java.net.URI)(implicit m:Manifest[T]):List[T] = {
    try {
      parse(client.get(url.toString)).extract[List[T]]
    } catch {
      case e:Exception => {
        println("exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
        List.empty[T]
      }
    }
  }

  protected def getOrgUnits(userContext:ID2LUserContext):List[D2LOrgUnit] = {
    val url = userContext.createAuthenticatedUri("/d2l/api/lp/%s/orgstructure/".format(lpApiVersion),"GET")
    try {
      val firstGet = client.get(url.toString)
      var first = parse(firstGet)
      val firstResp = first.extract[D2LOrgUnitResponse]
      var items = firstResp.Items
      var continuing = firstResp.PagingInfo.HasMoreItems
      var bookmark:Option[String] = firstResp.PagingInfo.Bookmark
      while (continuing){
        try {
          val u = userContext.createAuthenticatedUri("/d2l/api/lp/%s/orgstructure/%s".format(lpApiVersion,bookmark.map(b => "?%s=%s".format(bookMarkTag,b)).getOrElse("")),"GET")
          val url = u.toString
          val respObj = parse(client.get(url))
          val resp = respObj.extract[D2LOrgUnitResponse]
          items = items ::: resp.Items
          continuing = resp.PagingInfo.HasMoreItems
          bookmark = resp.PagingInfo.Bookmark
          println("bookmark: %s, items: %s".format(bookmark,items.length))
        } catch {
          case e:Exception => {
            warn("exception while paging: %s =>\r\n%s".format(bookmark,e.getMessage,e.getStackTraceString))
            continuing = false
            bookmark = None
          }
        }
      }
      items
    } catch {
      case e:Exception => {
        warn("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,e.getStackTraceString))
        List.empty[D2LOrgUnit]
      }
    }
  }
  protected def getClasslists(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LClassListUser] = {
    fetchListFromD2L[D2LClassListUser](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/classlist/".format(leApiVersion,orgUnit.Identifier),"GET"))
  }
  protected def getSections(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LSection] = {
    fetchListFromD2L[D2LSection](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/sections/".format(lpApiVersion,orgUnit.Identifier),"GET"))
  }
  protected def getGroupCategories(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LGroupCategory] = {
    fetchListFromD2L[D2LGroupCategory](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/groupcategories/".format(lpApiVersion,orgUnit.Identifier),"GET"))
  }
  protected def getGroups(userContext:ID2LUserContext,orgUnit:D2LOrgUnit,groupCategory:D2LGroupCategory):List[D2LGroup] = {
    fetchListFromD2L[D2LGroup](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/groupcategories/%s/groups/".format(lpApiVersion,orgUnit.Identifier,groupCategory.GroupCategoryId),"GET"))
  }

  def parFlatMap[A,B](coll:List[A],func:A => List[B],threadCount:Int = 1,forkJoinPoolName:String = "default"):List[B] = {
    if (threadCount > 1){
      val pc = coll.par
      pc.tasksupport = new scala.collection.parallel.ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(threadCount))
      val res = pc.flatMap(func).toList
      res
    } else {
      coll.flatMap(func).toList
    }
  }

  lazy val rawData:List[Tuple4[String,String,String,String]] = {
    val appContext:ID2LAppContext = AuthenticationSecurityFactory.createSecurityContext(appId,appKey,d2lBaseUrl)
    val userContext = appContext.createUserContext(userId,userKey)
    val courses = getOrgUnits(userContext).filter(_.Type.Id == 3) // 3 is the typeId of courses
    println("courses found: %s".format(courses.length))
    val remoteDataSet:List[Tuple4[String,String,String,String]] = parFlatMap[D2LOrgUnit,Tuple4[String,String,String,String]](courses,orgUnit => { 
      println("OU: %s (%s) %s".format(orgUnit.Name,orgUnit.Code, orgUnit.Type))
      val members = getClasslists(userContext,orgUnit).groupBy(_.Identifier.toLong)
      val combinedSet:List[() => List[Tuple4[String,String,String,String]]] = List(
        () => {
          val constructedMembers = (for (
            memberLists <- members.values;
            member <- memberLists;
            memberName <- member.OrgDefinedId.filterNot(_ == "").toList
          ) yield {
            (memberName,memberName,GroupKeys.ou,orgUnit.Name) :: 
            (memberName,member.Identifier,PersonalInformation.personalInformation,"D2L_Identifier") :: 
            (memberName,member.ProfileIdentifier,PersonalInformation.personalInformation,"D2L_Profile_Identifier") :: 
            (memberName,member.Identifier,PersonalInformation.personalInformation,"D2L_Identifier") :: 
            (memberName,member.DisplayName,PersonalInformation.personalInformation,PersonalInformation.displayName) :: 
            member.UserName.toList.map(fn => (memberName,fn,PersonalInformation.personalInformation,"D2L_UserName")) ::: 
            orgUnit.Code.toList.map(c => (memberName,memberName,GroupKeys.ou,c)) :::
            member.FirstName.toList.map(fn => (memberName,fn,PersonalInformation.personalInformation,PersonalInformation.firstName)) ::: 
            member.LastName.toList.map(fn => (memberName,fn,PersonalInformation.personalInformation,PersonalInformation.surname)) ::: 
            member.Email.toList.map(fn => (memberName,fn,PersonalInformation.personalInformation,PersonalInformation.email))
          }).flatten
          println("OU-Members: %s %s".format(orgUnit.Name,constructedMembers.toList.length))
          constructedMembers.toList
        },
        () => {
          val constructedSections = for (
            section <- getSections(userContext,orgUnit);
            memberId <- section.Enrollments;
            membersById:List[D2LClassListUser] <- members.get(memberId).toList;
            member:D2LClassListUser <- membersById;
            memberName:String <- member.OrgDefinedId.filterNot(_ == "")
          ) yield {
            (memberName,memberName,GroupKeys.section,section.Name)
          }
          println("OU-Sections: %s %s".format(orgUnit.Name,constructedSections.length))
          constructedSections
        },
        () => {
          val constructedGroups = parFlatMap[D2LGroupCategory,Tuple4[String,String,String,String]](getGroupCategories(userContext,orgUnit),groupCategory => {
            println("OU-GroupCategory: %s %s".format(orgUnit.Name,groupCategory.Name))
            parFlatMap[D2LGroup,Tuple4[String,String,String,String]](getGroups(userContext,orgUnit,groupCategory),group => {
              println("OU-Group: %s %s".format(orgUnit.Name,group.Name))
              for (
                memberId <- group.Enrollments;   
                membersById:List[D2LClassListUser] <- members.get(memberId).toList;
                member:D2LClassListUser <- membersById;
                memberName:String <- member.OrgDefinedId.filterNot(_ == "")
              ) yield {
                (memberName,memberName,GroupKeys.group,group.Name)
              }
            },6,"groups")
          },4,"groupCategories")
          println("OU-Groups: %s %s".format(orgUnit.Name,constructedGroups.length))
          constructedGroups
        }
      )
      parFlatMap[() => List[Tuple4[String,String,String,String]],Tuple4[String,String,String,String]](combinedSet,f => f(),3,"members_sections_groups")
    },16,"ou")
    remoteDataSet
  }
  protected lazy val groupData:Map[String,List[Tuple2[String,String]]] = rawData.filterNot(_._3 == PersonalInformation.personalInformation).groupBy(_._1).map(t => (t._1,t._2.distinct.map(m => (m._3,m._4))))
  protected lazy val members:Map[String,List[String]] = rawData.filterNot(_._3 == PersonalInformation.personalInformation).groupBy(_._4).map(t => (t._1,t._2.distinct.map(_._1)))
  protected lazy val personalDetails:Map[String,List[Tuple2[String,String]]] = rawData.filter(_._3 == PersonalInformation.personalInformation).groupBy(_._1).map(t => (t._1,t._2.distinct.map(m => (m._4,m._2))))

  override def getData:GroupStoreData = GroupStoreData(groupData,members,personalDetails)
}
