package com.metl.model

import com.metl.data.{Group=>MeTLGroup,GroupSet=>MeTLGroupSet,_}
import com.metl.utils._
import com.metl.view._

import com.metl.liftAuthenticator._

import net.liftweb.http.SessionVar
import net.liftweb.http.LiftRules
import net.liftweb.common._
import net.liftweb.util.Helpers._

import net.liftweb.util.Props
import scala.io.Source
import scala.xml.{Source=>XmlSource,Group=>XmlGroup,_}
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
  OrgDefinedId:Option[String], // this is the SLU Id number or username, I believe
  Email:Option[String],
  FirstName:Option[String],
  LastName:Option[String]
)
case class D2LGroupCategory(
  GroupCategoryId:Long,
  Name:String,
  Description:D2LDescription, //richtext
  EnrollmentStyle:Option[String], //this is probably mandatory, but I thought it was an int representing an enum, and it's a string representation of the enum
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
        error("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
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
            warn("exception while paging: %s =>\r\n%s".format(bookmark,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
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
        warn("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
        List.empty[T]
      }
    }
  }
  def getUserContext = {
    val appContext:ID2LAppContext = AuthenticationSecurityFactory.createSecurityContext(appId,appKey,d2lBaseUrl)
    appContext.createUserContext(userId,userKey)
  }
}
/*
class D2LGroupsProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends D2LInterface(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion) with GroupsProvider {
  val myContext = getUserContext
  protected def getMyUser(userContext:ID2LUserContext,username:String):List[D2LUser] = {
    fetchListFromD2L[D2LUser](userContext.createAuthenticatedUri("/d2l/api/lp/%s/users/?orgDefinedId=%s".format(lpApiVersion,username),"GET"))
  }
  protected def getMyEnrollments(userContext:ID2LUserContext,user:D2LUser):List[D2LMyOrgUnit] = {
    fetchPagedListFromD2L[D2LMyOrgUnit]("/d2l/api/lp/%s/myenrollments/users/%s/orgUnits/".format(lpApiVersion,user.OrgDefinedId.getOrElse("")),userContext) 
  }
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = getMyUser(myContext,userData.username).flatMap(id => getMyEnrollments(myContext,id).filter(_.OrgUnit.Type.Id == 3).flatMap(en => {
    (en.OrgUnit.Name :: en.OrgUnit.Code.toList).map(v => (GroupKeys.ou,v))
  }))
  override def getMembersFor(groupName:String):List[String] = Nil
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Tuple2[String,String]] = Nil
}
*/

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
            warn("exception while paging: %s =>\r\n%s".format(bookmark,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
            continuing = false
            bookmark = None
          }
        }
      }
      items
    } catch {
      case e:Exception => {
        warn("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
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
  protected val d2lSectionPlaceholder = D2LSection(-1,"D2LProvidedSections",D2LDescription(None,None),Nil)
  override def getData:GroupStoreData = {
    val userContext = getUserContext
    val courses = getOrgUnits(userContext).filter(_.Type.Id == 3) // 3 is the typeId of courses
    trace("courses found: %s".format(courses.length))
    val compoundItems = parFlatMap[D2LOrgUnit,Tuple2[List[Tuple3[String,String,String]],List[OrgUnit]]](courses,orgUnit => { 
      val members = getClasslists(userContext,orgUnit).groupBy(_.Identifier.toLong)
      val memberDetails = (for (
        memberLists <- members.values;
        member <- memberLists;
        memberName <- member.OrgDefinedId.filterNot(_ == "").toList
      ) yield {
        (memberName,member.Identifier,"D2L_Identifier") :: 
        (memberName,member.ProfileIdentifier,"D2L_Profile_Identifier") :: 
        (memberName,member.Identifier,"D2L_Identifier") :: 
        (memberName,member.DisplayName,PersonalInformation.displayName) :: 
        member.UserName.toList.map(fn => (memberName,fn,"D2L_UserName")) ::: 
        member.FirstName.toList.map(fn => (memberName,fn,PersonalInformation.firstName)) ::: 
        member.LastName.toList.map(fn => (memberName,fn,PersonalInformation.surname)) ::: 
        member.Email.toList.map(fn => (memberName,fn,PersonalInformation.email))
      }).flatten.toList

      val sections = getSections(userContext,orgUnit).map(section => {
        Group(GroupKeys.section,section.Name,section.Enrollments.flatMap(mId => members.get(mId).toList.flatten.flatMap(_.OrgDefinedId).filterNot(_ == "")))
      })
      val sectionGroupSets = sections match {
        case Nil => Nil
        case some => List(GroupSet(GroupKeys.sectionCategory,nameSection(orgUnit,d2lSectionPlaceholder),some.flatMap(_.members),some))
      }
      val groupCategories = parFlatMap[D2LGroupCategory,GroupSet](getGroupCategories(userContext,orgUnit),groupCategory => {
        val groups = getGroups(userContext,orgUnit,groupCategory).map(group => {
          Group(GroupKeys.group,nameGroup(orgUnit,groupCategory,group),group.Enrollments.flatMap(mId => members.get(mId).toList.flatten.flatMap(_.OrgDefinedId).filterNot(_ == "")))
        })
        List(GroupSet(GroupKeys.groupCategory,nameGroupCategory(orgUnit,groupCategory),groups.flatMap(_.members).distinct,groups))
      },4,"groupCategories")
      val children =  sectionGroupSets ::: groupCategories
      val orgUnits = (orgUnit.Name :: orgUnit.Code.toList).map(ou => OrgUnit(GroupKeys.course,ou,(members.values.toList.flatten.flatMap(_.OrgDefinedId).toList ::: children.flatMap(_.members)).distinct,children))
      List((memberDetails,orgUnits))
    },16,"ou")
    val personalInformation = compoundItems.map(_._1)
    val personalDetails:Map[String,List[Tuple2[String,String]]] = personalInformation.flatten.groupBy(_._1).map(t => (t._1,t._2.map(m => (m._3,m._2)).distinct))
    val groupData = compoundItems.flatMap(_._2)
    val groupsByMember:Map[String,List[OrgUnit]] = Map(personalDetails.keys.toList.map(u => (u,groupData.filter(_.members.contains(u)))):_*)
    val membersByGroup:Map[String,List[String]] = groupData.groupBy(_.name).map(g => (g._1,g._2.flatMap(_.members).toList))
    GroupStoreData(groupsByMember,membersByGroup,personalDetails)
  }
}
