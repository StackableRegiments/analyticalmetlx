package com.metl.model

import com.metl.data._
import com.metl.utils._
import com.metl.view._

import com.metl.liftAuthenticator.LiftAuthStateData

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
case class PagedResponse[T](
  PagingInfo:D2LPagingInfo,
  Items:List[T]
)
case class D2LOrgUnitTypeInfo(
  Id:Int,
  Code:String,
  Name:String
)
case class D2LUser(
  Identifier:Option[String],
  DisplayName:Option[String],
  EmailAddress:Option[String],
  OrgDefinedId:Option[String],
  ProfileBadgeUrl:Option[String],
  ProfileIdentifier:Option[String]
)
case class D2LOrgUnit(
  Identifier:String, //this is actually a number
  Type:D2LOrgUnitTypeInfo,
  Name:String, // this is being filled with unitCode (EDU300)
  Code:Option[String], //this is being used like the class group identifier (EDU300-CA01)
  HomeUrl:Option[String],
  ImageUrl:Option[String]
)
case class D2LOrgUnitAccess(
  IsActive:Boolean,
  StartDate:Option[String],
  EndDate:Option[String],
  CanAccess:Boolean,
  ClassListRoleName:Option[String],
  LISRoles:List[String]
)
case class D2LMyOrgUnit(
  OrgUnit:D2LOrgUnit,
  Access:D2LOrgUnitAccess,
  PinDate:Option[String]
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
  Description:D2LDescription, //richtext
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
  Description:D2LDescription, //richtext
  Enrollments:List[Long]
)
case class D2LSection(
  SectionId:Long,
  Name:String,
  Description:D2LDescription, //richtext
  Enrollments:List[Long]
)
case class D2LDescription(
  Text:Option[String],
  Html:Option[String]
)

class D2LInterface(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends Logger {
  protected val httpConnectionTimeout = 10 // 10 seconds
  protected val httpReadTimeout = 60 * 1000 // 60 seconds
  protected val client = new com.metl.utils.CleanHttpClient(com.metl.utils.Http.getConnectionManager){
    override val connectionTimeout = httpConnectionTimeout
    override val readTimeout = httpReadTimeout
  }

  protected implicit def formats = net.liftweb.json.DefaultFormats

  protected val bookMarkTag = "bookmark"

  protected def fetchListFromD2L[T](url:java.net.URI,expectHttpFailure:Boolean = false)(implicit m:Manifest[T]):List[T] = {
    try {
      parse(client.get(url.toString)).extract[List[T]]
    } catch {
      case e:WebException if expectHttpFailure => {
        trace("exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
        List.empty[T]
      }
      case e:Exception => {
        error("exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
        List.empty[T]
      }
    }
  }
  protected def fetchPagedListFromD2L[T](masterUrl:String,userContext:ID2LUserContext,expectHttpFailure:Boolean = false)(implicit m:Manifest[T]):List[T] = {
    val url = userContext.createAuthenticatedUri(masterUrl,"GET")
    try {
      val firstGet = client.get(url.toString)
      var first = parse(firstGet)
      val firstResp = first.extract[PagedResponse[T]]
      var items = firstResp.Items
      var continuing = firstResp.PagingInfo.HasMoreItems
      var bookmark:Option[String] = firstResp.PagingInfo.Bookmark
      while (continuing){
        try {
          val u = userContext.createAuthenticatedUri("%s%s".format(masterUrl,bookmark.map(b => "?%s=%s".format(bookMarkTag,b)).getOrElse("")),"GET")
          val url = u.toString
          val respObj = parse(client.get(url))
          val resp = respObj.extract[PagedResponse[T]]
          items = items ::: resp.Items
          continuing = resp.PagingInfo.HasMoreItems
          bookmark = resp.PagingInfo.Bookmark
          trace("bookmark: %s, items: %s".format(bookmark,items.length))
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
      case e:WebException if expectHttpFailure => {
        trace("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,e.getStackTraceString))
        List.empty[T]
      }
      case e:Exception => {
        warn("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,e.getStackTraceString))
        List.empty[T]
      }
    }
  }
  def getUserContext = {
    val appContext:ID2LAppContext = AuthenticationSecurityFactory.createSecurityContext(appId,appKey,d2lBaseUrl)
    appContext.createUserContext(userId,userKey)
  }
}

class D2LGroupsProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends D2LInterface(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion) with GroupsProvider {
  val myContext = getUserContext
  protected def getMyUser(userContext:ID2LUserContext,username:String):List[D2LUser] = {
    fetchListFromD2L[D2LUser](userContext.createAuthenticatedUri("/d2l/api/lp/%s/users/?orgDefinedId=%s".format(lpApiVersion,username),"GET"))
  }
  protected def getMyEnrollments(userContext:ID2LUserContext,user:D2LUser):List[D2LMyOrgUnit] = {
    fetchPagedListFromD2L[D2LMyOrgUnit]("/d2l/api/lp/%s/myenrollments/users/%s/orgUnits/".format(lpApiVersion,user.OrgDefinedId.getOrElse("")),userContext) 
  }
  override def getGroupsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = getMyUser(myContext,userData.username).flatMap(id => getMyEnrollments(myContext,id).filter(_.OrgUnit.Type.Id == 3).flatMap(en => {
    (en.OrgUnit.Name :: en.OrgUnit.Code.toList).map(v => (GroupKeys.ou,v))
  }))
  override def getMembersFor(groupName:String):List[String] = Nil
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = Nil
}

class D2LGroupStoreProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends D2LInterface(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion) with GroupStoreProvider {

  protected def getOrgUnits(userContext:ID2LUserContext):List[D2LOrgUnit] = {
    val url = userContext.createAuthenticatedUri("/d2l/api/lp/%s/orgstructure/".format(lpApiVersion),"GET")
    try {
      val firstGet = client.get(url.toString)
      var first = parse(firstGet)
      val firstResp = first.extract[D2LOrgUnitResponse]
      var items = firstResp.Items
      var continuing = firstResp.PagingInfo.HasMoreItems
      var bookmark:Option[String] = firstResp.PagingInfo.Bookmark
      trace("bookmark: %s, items: %s".format(bookmark,items.length))
      while (continuing){
        try {
          val u = userContext.createAuthenticatedUri("/d2l/api/lp/%s/orgstructure/%s".format(lpApiVersion,bookmark.map(b => "?%s=%s".format(bookMarkTag,b)).getOrElse("")),"GET")
          val url = u.toString
          val respObj = parse(client.get(url))
          val resp = respObj.extract[D2LOrgUnitResponse]
          items = items ::: resp.Items
          continuing = resp.PagingInfo.HasMoreItems
          bookmark = resp.PagingInfo.Bookmark
          trace("bookmark: %s, items: %s".format(bookmark,items.length))
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
    fetchListFromD2L[D2LSection](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/sections/".format(lpApiVersion,orgUnit.Identifier),"GET"),true)
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

  protected def nameSection(orgUnit:D2LOrgUnit,section:D2LSection):String = "%s_%s".format(orgUnit.Name,section.Name)
  protected def nameGroupCategory(orgUnit:D2LOrgUnit,groupCategory:D2LGroupCategory):String = "%s_%s".format(orgUnit.Name,groupCategory.Name)
  protected def nameGroup(orgUnit:D2LOrgUnit,groupCategory:D2LGroupCategory,group:D2LGroup):String = "%s_%s_%s".format(orgUnit.Name,groupCategory.Name,group.Name)
  override def getData:GroupStoreData = {
    val userContext = getUserContext
    val courses = getOrgUnits(userContext).filter(_.Type.Id == 3) // 3 is the typeId of courses
    info("courses found: %s".format(courses.length))
    val (groupData,personalInformation) = parFlatMap[D2LOrgUnit,Tuple4[String,String,String,String]](courses,orgUnit => {
      trace("OU: %s (%s) %s".format(orgUnit.Name,orgUnit.Code, orgUnit.Type))
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
          trace("OU-Members: %s %s".format(orgUnit.Name,constructedMembers.toList.length))
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
            (memberName,memberName,GroupKeys.section,nameSection(orgUnit,section))
          }
          trace("OU-Sections: %s %s".format(orgUnit.Name,constructedSections.length))
          constructedSections
        },
        () => {
          val constructedGroups = parFlatMap[D2LGroupCategory,Tuple4[String,String,String,String]](getGroupCategories(userContext,orgUnit),groupCategory => {
            trace("OU-GroupCategory: %s %s".format(orgUnit.Name,groupCategory.Name))
            parFlatMap[D2LGroup,Tuple4[String,String,String,String]](getGroups(userContext,orgUnit,groupCategory),group => {
              trace("OU-Group: %s %s".format(orgUnit.Name,group.Name))
              for (
                memberId <- group.Enrollments;   
                membersById:List[D2LClassListUser] <- members.get(memberId).toList;
                member:D2LClassListUser <- membersById;
                memberName:String <- member.OrgDefinedId.filterNot(_ == "")
              ) yield {
                (memberName,memberName,GroupKeys.group,nameGroup(orgUnit,groupCategory,group))
              }
            },6,"groups")
          },4,"groupCategories")
          trace("OU-Groups: %s %s".format(orgUnit.Name,constructedGroups.length))
          constructedGroups
        }
      )
      parFlatMap[() => List[Tuple4[String,String,String,String]],Tuple4[String,String,String,String]](combinedSet,f => f(),3,"members_sections_groups")
    },16,"ou").partition(_._3 != PersonalInformation.personalInformation)
    val groupsByMember:Map[String,List[Tuple2[String,String]]] = groupData.groupBy(_._1).map(t => (t._1,t._2.distinct.map(m => (m._3,m._4))))
    val membersByGroup:Map[String,List[String]] = groupData.groupBy(_._4).map(t => (t._1,t._2.distinct.map(_._1)))
    val personalDetails:Map[String,List[Tuple2[String,String]]] = personalInformation.groupBy(_._1).map(t => (t._1,t._2.distinct.map(m => (m._4,m._2))))
    GroupStoreData(groupsByMember,membersByGroup,personalDetails)
  }
}
