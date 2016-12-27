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

/*
async:
current process
then
(http://docs.valence.desire2learn.com/res/user.html#roles)
GET /d2l/api/lp/(version)/roles/
returns:
[{
    "Identifier": <string>,
    "DisplayName": <string>,
    "Code": <string>,
    "Description": <string>,  // Appears in LP's unstable contract as of LP v10.4.12
    "RoleAlias": <string>,  // Appears in LP's unstable contract as of LP v10.4.12
    "IsCascading": <boolean>,  // Appears in LP's unstable contract as of LP v10.4.12
    "AccessFutureCourses": <boolean>,  // Appears in LP's unstable contract as of LP v10.4.12
    "AccessInactiveCourses": <boolean>,  // Appears in LP's unstable contract as of LP v10.4.12
    "AccessPastCourses": <boolean>,  // Appears in LP's unstable contract as of LP v10.4.12
    "ShowInGrades": <boolean>  // Appears in LP's unstable contract as of LP v10.6.7
}]

this'll be used to correlate the roles fetched from the other part.

sync on logon:
(http://docs.valence.desire2learn.com/res/enroll.html)
GET /d2l/api/lp/(version)/enrollments/users/(userId)/orgUnits/

returns pagedSet:
[{
    "OrgUnitInfo": { <composite:Enrollment.OrgUnitInfo> },  //D2LOrgUnit
    "RoleInfo": { <composite:Enrollment.RoleInfo> }   //see below
},...,bookmark]

where:
Enrollment.RoleInfo
{
    "Id": <number:D2LID>,
    "Code": <string>|null,
    "Name": <string>
}

*/

case class D2LAssociatedTool(
  ToolId:Long,
  ToolItemId:Long
)

case class D2LGradeObjectCreator(
  MaxPoints:Long,
  CanExceedMaxPoints:Boolean,
  IsBonus:Boolean,
  ExcludeFromFinalGradeCalculation:Boolean,
  GradeSchemeId:Long, // must not be null on input actions
  Name:String, //max 128 chars, must be unique, cannot contain /"*<>+=|,%
  ShortName:String, //max 128 chars
  GradeType:String, //Numeric,PassFail,SelectBox,Text
  //5 - 9 cannot be set from API
  //Calculated, Formula, FinalCalculated, FinalAdjusted, Category
  CategoryId:Option[Long],
  Description:Option[D2LDescriptionInput], //RichText?
  AssociatedTool: Option[D2LAssociatedTool]
)

case class D2LGradeObject(
  MaxPoints:Long,
  CanExceedMaxPoints:Boolean,
  IsBonus:Boolean,
  ExcludeFromFinalGradeCalculation:Boolean,
  GradeSchemeId:Option[Long], // must not be null on input actions
  Id:Option[Long], // don't pass this in when POSTing
  Name:String, //max 128 chars, must be unique, cannot contain /"*<>+=|,%
  ShortName:String, //max 128 chars
  GradeType:String, //Numeric,PassFail,SelectBox,Text
  //5 - 9 cannot be set from API
  //Calculated, Formula, FinalCalculated, FinalAdjusted, Category
  CategoryId:Option[Long],
  Description:Option[D2LDescription], //RichText?
  GradeSchemeUrl:Option[String], // API URL - not when POSTing
  Weight:Option[Long], // not when POSTing
  ActivityId: Option[String], // not when POSTing
  AssociatedTool: Option[D2LAssociatedTool]
)
case class D2LIncomingGradeValue(
  Comments:D2LDescriptionInput,
  PrivateComments:D2LDescriptionInput,
  GradeObjectType: Int, // 1 = Numeric,2 = PassFail,3 = SelectBox,4 = Text
  PointsNumerator:Option[Double], // include this if GradeObjectType = 1
  Pass:Option[Boolean], // include this if GradeObjectType = 2
  Value:Option[String], // include this if GradeObjectType = 3
  Text:Option[String] // include this if GradeObjectType = 4
)
case class D2LGradeValue(
  UserId: Option[String],  // Added to LE unstable API contract as of LMS v10.6.3
  OrgUnitId: Option[String],  // Added to LE unstable API contract as of LMS v10.6.3
  DisplayedGrade: String,
  GradeObjectIdentifier: String,
  GradeObjectName: String,
  GradeObjectType: Int, // 1 = Numeric,2 = PassFail,3 = SelectBox,4 = Text
//5 - 9 cannot be set from API
//5 = Calculated, 6 = Formula, 7 = FinalCalculated, 8 = FinalAdjusted, 9 = Category
  PointsNumerator:Option[Double], // include this if GradeObjectType = 1
  Pass:Option[Boolean], // include this if GradeObjectType = 2
  Value:Option[String], // include this if GradeObjectType = 3
  Text:Option[String], // include this if GradeObjectType = 4
  GradeObjectTypeName: Option[String],
  Comments: Option[D2LDescription],  // Added with LE v1.13 API - provide when POSTing
  PrivateComments: Option[D2LDescription],  // Added with LE v1.13 API - provide when POSTing
  //PointsNumerator: Option[Long], //computable only
  PointsDenominator: Option[Long], //computable only
  WeightedDenominator: Option[Long], // computable only
  WeightedNumerator: Option[Double] // computable only
)
case class D2LGradeValueResponse(
  Next:Option[String],
  Objects:List[D2LUserGradeValue]
)
case class D2LUserGradeValue(
  User:D2LUserFlyWeight,
  GradeValue:Option[D2LGradeValue]
)
case class D2LGradeScheme(
  Id:Option[Long],
  Name:String,
  ShortName:String,
  Ranges:List[D2LGradeSchemeRange]
)
case class D2LGradeSchemeRange(
  PercentStart:Double,
  Symbol:String,
  AssignedValue:Option[Double],
  Colour:String
)

case class D2LCompetencyObjectsPage(
  Objects:List[D2LCompetencyObject],
  Next:Option[String] // API URL for next page
)
case class D2LCompetencyObject(
  Id:Long,
  ObjectTypeId:Long, //1 = Competency, 2 = Objective
  Name:String,
  Description:String,
  ChildrenPage:Option[D2LCompetencyObjectsPage],
  MoreChildren:Option[String] // API URL for next page
)

case class D2LActivation(
  IsActive:Boolean
)

case class D2LUserResponse(
  PagingInfo:D2LPagingInfo,
  Items:List[D2LUser]
)
case class D2LUser(
  OrgId:Long,
  UserId:Long,
  FirstName:Option[String],
  MiddleName:Option[String],
  LastName:Option[String],
  UserName:Option[String],
  ExternalEmail:Option[String],
  OrgDefinedId:String,
  UniqueIdentifier:String,
  Activation:D2LActivation
)

case class D2LEnrollment(
  OrgUnit:D2LOrgUnitInfo,
  Role:D2LRoleInfo
)

case class D2LOrgUnitInfo(
  Id:Long, //this is actually a number
  Type:D2LOrgUnitTypeInfo,
  Name:String, // this is being filled with unitCode (EDU300)
  Code:Option[String], //this is being used like the class group identifier (EDU300-CA01)
  HomeUrl:Option[String],
  ImageUrl:Option[String]
)

case class D2LRoleInfo(
  Id:Long,
  Code:String,
  Name:String
)

case class D2LRole(
  Identifier:Option[String],
  DisplayName:String,
  Code:String,
  Description:Option[String],
  RoleAlias:Option[String],
  IsCascading:Option[Boolean],
  AccessFutureCourses:Option[Boolean],
  AccessInactiveCourses:Option[Boolean],
  AccessPastCourses:Option[Boolean],
  ShowInGrades:Option[Boolean]
)

case class D2LEnrollmentResponse(
  PagingInfo:D2LPagingInfo,
  Items:List[D2LEnrollment]
)
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
case class D2LUserFlyWeight(
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
  Username:Option[String],
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
  AllocateAfterExpiry:Option[String],
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
case class D2LDescriptionInput(
  Content:Option[String],
  Type:Option[String]
)

class D2LInterface(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends Logger {
  protected val pageSize = 200
  protected val httpConnectionTimeout = 10 // 10 seconds
  protected val httpReadTimeout = 60 * 1000 // 60 seconds
  protected val client = new com.metl.utils.CleanHttpClient(com.metl.utils.Http.getConnectionManager){
    override val connectionTimeout = httpConnectionTimeout
    override val readTimeout = httpReadTimeout
  }

  protected implicit def formats = net.liftweb.json.DefaultFormats

  protected val bookMarkTag = "bookmark"
  protected def postToD2L[T](url:java.net.URI,json:JValue,expectHttpFailure:Boolean = false)(implicit m:Manifest[T]):Option[T] = {
    try {
      Some(parse(new String(client.postBytes(url.toString,compactRender(json).getBytes("UTF-8")),"UTF-8")).extract[T])
    } catch {
      case e:WebException if expectHttpFailure => {
        trace("exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
        None
      }
      case e:Exception => {
        error("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
        None
      }
    }
  }
  protected def putToD2L[T](url:java.net.URI,json:JValue,expectHttpFailure:Boolean = false)(implicit m:Manifest[T]):Option[T] = {
    try {
      val jValueString = compactRender(json)
      val headers = List(("Content-Type","application/json"))
      val response = client.putStringExpectingHTTPResponse(url.toString,jValueString,headers)
      //println("PUTing to %s [%s] : %s".format(url.toString,headers,jValueString))
      None//Some(parse(response).extract[T])
    } catch {
      case e:WebException if expectHttpFailure => {
        trace("web exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
        None
      }
      case e:Exception => {
        error("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
        None
      }
    }
  }


  protected def fetchFromD2L[T](url:java.net.URI,expectHttpFailure:Boolean = false)(implicit m:Manifest[T]):Option[T] = {
    try {
      val response = client.get(url.toString)
      val parsedResponse = parse(response)
      Some(parsedResponse.extract[T])
    } catch {
      case e:WebException if expectHttpFailure => {
        trace("exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
        None
      }
      case e:Exception => {
        error("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
        None
      }
    }
  }
  protected def fetchListFromD2L[T](url:java.net.URI,expectHttpFailure:Boolean = false)(implicit m:Manifest[T]):List[T] = {
    try {
      val response = client.get(url.toString)
      val parsedResponse = parse(response)
      parsedResponse.extract[List[T]]
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
  def getUser(userContext:ID2LUserContext,userId:String):Option[D2LUser] = {
    fetchFromD2L[D2LUser](userContext.createAuthenticatedUri("/d2l/api/lp/%s/users/%s".format(lpApiVersion,userId),"GET"),true)
  }
  def getOrgUnit(userContext:ID2LUserContext,orgUnit:String):Option[D2LOrgUnit] = {
    fetchFromD2L[D2LOrgUnit](userContext.createAuthenticatedUri("/d2l/api/lp/%s/orgstructure/%s".format(lpApiVersion,orgUnit),"GET"),true)
  }
  def getClasslists(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LClassListUser] = {
    fetchListFromD2L[D2LClassListUser](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/classlist/".format(leApiVersion,orgUnit.Identifier),"GET"))
  }
  def getSections(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LSection] = {
    fetchListFromD2L[D2LSection](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/sections/".format(lpApiVersion,orgUnit.Identifier),"GET"),true)
  }
  def getGroupCategories(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LGroupCategory] = {
    fetchListFromD2L[D2LGroupCategory](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/groupcategories/".format(lpApiVersion,orgUnit.Identifier),"GET"))
  }
  def getGroupCategory(userContext:ID2LUserContext,orgUnit:D2LOrgUnit,groupCategoryId:String):Option[D2LGroupCategory] = {
    fetchFromD2L[D2LGroupCategory](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/groupcategories/%s".format(lpApiVersion,orgUnit.Identifier,groupCategoryId),"GET"))
  }
  def getGroups(userContext:ID2LUserContext,orgUnit:D2LOrgUnit,groupCategory:D2LGroupCategory):List[D2LGroup] = {
    fetchListFromD2L[D2LGroup](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/groupcategories/%s/groups/".format(lpApiVersion,orgUnit.Identifier,groupCategory.GroupCategoryId),"GET"))
  }
  def getGroup(userContext:ID2LUserContext,orgUnit:D2LOrgUnit,groupCategory:D2LGroupCategory,groupId:String):Option[D2LGroup] = {
    fetchFromD2L[D2LGroup](userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/groupcategories/%s/groups/%s".format(lpApiVersion,orgUnit.Identifier,groupCategory.GroupCategoryId,groupId),"GET"))
  }

  def getRoles(userContext:ID2LUserContext):List[D2LRole] = {
    fetchListFromD2L[D2LRole](userContext.createAuthenticatedUri("/d2l/api/lp/%s/roles/".format(lpApiVersion),"GET"))
  }
  def getObjectives(userContext:ID2LUserContext,orgUnitId:String):Option[D2LCompetencyObjectsPage] = {
    fetchFromD2L[D2LCompetencyObjectsPage](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/competencies/structure/".format(leApiVersion,orgUnitId),"GET"),true)
  }
  def getGradeObjects(userContext:ID2LUserContext,orgUnitId:String):List[D2LGradeObject] = {
    fetchListFromD2L[D2LGradeObject](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/grades/".format(leApiVersion,orgUnitId),"GET"),true)
  }
  def getGradeObject(userContext:ID2LUserContext,orgUnitId:String,gradeObjectId:String):Option[D2LGradeObject] = {
    fetchFromD2L[D2LGradeObject](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/grades/%s".format(leApiVersion,orgUnitId,gradeObjectId),"GET"),true)
  }
  def getGradeSchemes(userContext:ID2LUserContext,orgUnitId:String):List[D2LGradeScheme] = {
    fetchListFromD2L[D2LGradeScheme](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/grades/schemes/".format(leApiVersion,orgUnitId),"GET"),true)
  }
  def getGradeScheme(userContext:ID2LUserContext,orgUnitId:String,gradeSchemeId:String):Option[D2LGradeScheme] = {
    fetchFromD2L[D2LGradeScheme](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/grades/schemes/%s".format(leApiVersion,orgUnitId,gradeSchemeId),"GET"),true)
  }
  def createGradeObject(userContext:ID2LUserContext,orgUnitId:String,grade:D2LGradeObjectCreator):Option[D2LGradeObject] = {
    val gradeJObj = Extraction.decompose(grade)
    postToD2L[D2LGradeObject](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/grades/".format(leApiVersion,orgUnitId),"POST"),gradeJObj,true)
  }
  def updateGradeObject(userContext:ID2LUserContext,orgUnitId:String,grade:D2LGradeObject):Option[D2LGradeObject] = {
    putToD2L[D2LGradeObject](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/grades/%s".format(leApiVersion,orgUnitId,grade.Id),"PUT"),Extraction.decompose(grade),true)
  }
  def getGradeValues(userContext:ID2LUserContext,orgUnitId:String,gradeObjectId:String):List[D2LUserGradeValue] = {
    val url = userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/grades/%s/values/".format(leApiVersion,orgUnitId,gradeObjectId),"GET")
    var items:List[D2LUserGradeValue] = Nil
    try {
      val firstGet = client.get(url.toString)
      var first = parse(firstGet)
      //println("found remote grades: %s".format(firstGet))
      val firstResp = first.extract[D2LGradeValueResponse]
      items = items ::: firstResp.Objects
      var continuing = firstResp.Next
      while (continuing.isDefined){
        try {
          continuing.foreach(apiUrl => {
            val u = userContext.createAuthenticatedUri(apiUrl,"GET")
            val respString = client.get(u.toString)
            //println("found remote grades: %s".format(respString))
            val respObj = parse(respString)
            val resp = respObj.extract[D2LGradeValueResponse]
            items = items ::: resp.Objects
            continuing = resp.Next
          })
        } catch {
          case e:Exception => {
            warn("exception while paging: %s =>\r\n%s".format(continuing,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
            continuing = None
          }
        }
      }
      items
    } catch {
      case e:Exception => {
        warn("exception when accessing: %s => %s\r\n%s".format(url.toString,e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
        List.empty[D2LUserGradeValue]
      }
    }
  }
 
  def updateGradeValue(userContext:ID2LUserContext,orgUnitId:String,gradeObjectId:String,userId:String,gradeValue:D2LIncomingGradeValue):Option[D2LIncomingGradeValue] = {
    val gv = Extraction.decompose(gradeValue)
    //println("sending new gradeValue: %s".format(compactRender(gv)))
    putToD2L[D2LIncomingGradeValue](userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/grades/%s/values/%s".format(leApiVersion,orgUnitId,gradeObjectId,userId),"PUT"),gv,true)
  }

  def getEnrollments(userContext:ID2LUserContext,userId:String):List[D2LEnrollment] = {
    val url = userContext.createAuthenticatedUri("/d2l/api/lp/%s/enrollments/users/%s/orgUnits/?pagesize=%s".format(lpApiVersion,userId,pageSize),"GET")
    try {
      val firstGet = client.get(url.toString)
      var first = parse(firstGet)
      val firstResp = first.extract[D2LEnrollmentResponse]
      var items = firstResp.Items
      var continuing = firstResp.PagingInfo.HasMoreItems
      var bookmark:Option[String] = firstResp.PagingInfo.Bookmark
      trace("bookmark: %s, items: %s".format(bookmark,items.length))
      while (continuing){
        try {
          val u = userContext.createAuthenticatedUri("/d2l/api/lp/%s/enrollments/users/%s/orgUnits/%s".format(lpApiVersion,userId,bookmark.map(b => "?%s=%s&pagesize=%s".format(bookMarkTag,b,pageSize)).getOrElse("?pagesize=%s".format(pageSize))),"GET")
          val url = u.toString
          val respObj = parse(client.get(url))
          val resp = respObj.extract[D2LEnrollmentResponse]
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
        List.empty[D2LEnrollment]
      }
    }
  }
  def getUserByOrgDefinedId(userContext:ID2LUserContext,orgDefinedId:String):List[D2LUser] = {
    fetchListFromD2L[D2LUser](userContext.createAuthenticatedUri("/d2l/api/lp/%s/users/?orgDefinedId=%s".format(lpApiVersion,orgDefinedId),"GET"))
  }
  def getUserByUsername(userContext:ID2LUserContext,username:String):Option[D2LUser] = {
    fetchFromD2L[D2LUser](userContext.createAuthenticatedUri("/d2l/api/lp/%s/users/?userName=%s".format(lpApiVersion,username),"GET"))
  }
  def getUsers(userContext:ID2LUserContext):List[D2LUser] = {
    val url = userContext.createAuthenticatedUri("/d2l/api/lp/%s/users/?pagesize=%s".format(lpApiVersion,pageSize),"GET")
    try {
      val firstGet = client.get(url.toString)
      var first = parse(firstGet)
      val firstResp = first.extract[D2LUserResponse]
      var items = firstResp.Items
      var continuing = firstResp.PagingInfo.HasMoreItems
      var bookmark:Option[String] = firstResp.PagingInfo.Bookmark
      trace("bookmark: %s, items: %s".format(bookmark,items.length))
      while (continuing){
        try {
          val u = userContext.createAuthenticatedUri("/d2l/api/lp/%s/users/%s".format(lpApiVersion,bookmark.map(b => "?%s=%s&pagesize=%s".format(bookMarkTag,b,pageSize)).getOrElse("?pagesize=%s".format(pageSize))),"GET")
          val url = u.toString
          val respObj = parse(client.get(url))
          val resp = respObj.extract[D2LUserResponse]
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
        List.empty[D2LUser]
      }
    }
  }
}

class D2LGroupsProvider(override val storeId:String, d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends GroupsProvider(storeId){
  val interface = new D2LInterface(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion) 
  override val canQuery:Boolean = true
  
  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = {
    val uc = interface.getUserContext
    interface.getUserByUsername(uc,userData.username).toList.flatMap(user => {
      val enrollments = interface.getEnrollments(uc,user.UserId.toString)  
      enrollments.map(en => {
        OrgUnit(en.OrgUnit.Type.Name,en.OrgUnit.Name,Nil,Nil,Some(ForeignRelationship(storeId,en.OrgUnit.Id.toString)))
      })
    })
  }
  override def getOrgUnit(orgUnitId:String):Option[OrgUnit] = {
    val uc = interface.getUserContext
    interface.getOrgUnit(uc,orgUnitId).map(en => {
      OrgUnit(en.Type.Name,en.Name,Nil,Nil,Some(ForeignRelationship(storeId,en.Identifier.toString)))
    })
  }
  override def getMembersFor(orgUnit:OrgUnit):List[Member] = {
    orgUnit.foreignRelationship.filter(_.system == storeId).toList.flatMap(fr => {
      val orgUnitId = fr.key
      withMembersFor(orgUnitId,t => {
        val members = t._3
        members
      })
    })
  }
  protected def withMembersFor[A](orgUnitId:String,action:Tuple3[ID2LUserContext,D2LOrgUnit,List[Member]]=>A):A = {
    val fakeOrgUnit = D2LOrgUnit(orgUnitId,D2LOrgUnitTypeInfo(0,"",""),"",None,None,None)
    val uc = interface.getUserContext
    val groupSets = interface.getGroupCategories(uc,fakeOrgUnit)
    val classlists = interface.getClasslists(uc,D2LOrgUnit(orgUnitId,D2LOrgUnitTypeInfo(0,"",""),"",None,None,None))
    val members = classlists.flatMap(cm => {
      cm.Username.map(username => {
        Member(
          name = username,
          details = (List(
            "Identifier" -> cm.Identifier,
            "ProfileIdentifier" -> cm.ProfileIdentifier,
            "DisplayName" -> cm.DisplayName
          ) ::: 
          cm.Username.toList.map(un => "UserName" -> un) :::
          cm.OrgDefinedId.toList.map(un => "OrgDefinedId" -> un) :::
          cm.Email.toList.map(un => "Email" -> un) :::
          cm.FirstName.toList.map(un => "FirstName" -> un) :::
          cm.LastName.toList.map(un => "LastName" -> un)).map(t => Detail(t._1,t._2)),
          foreignRelationship = Some(ForeignRelationship(storeId,"%s_%s".format(orgUnitId,cm.Identifier)))
        )
      })
    })
    action((uc,fakeOrgUnit,members))    //does the orgUnit need to be a real one at this stage, I wonder?
  }
  override def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = {
    orgUnit.foreignRelationship.filter(_.system == storeId).toList.flatMap(fr => {
      val orgUnitId = fr.key
      withMembersFor(orgUnitId,t => {
        val uc = t._1
        val d2lOrgUnit = t._2
        val members = t._3
        val groupSets = interface.getGroupCategories(uc,d2lOrgUnit)
        groupSets.map(gs => {
          GroupSet(
            groupSetType = "D2LGroupCategory",
            name = gs.Name,
            members = members, //I think groupSets always have all the members available in them.  It's groups which have a subset, I think, from D2L.
            groups = Nil,
            foreignRelationship = Some(ForeignRelationship(storeId,"%s_%s".format(orgUnitId,gs.GroupCategoryId.toString)))
          )
        })
      })
    })
  }
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = {
    groupSet.foreignRelationship.filter(_.system == storeId).toList.flatMap(gsId => {
      val parts = gsId.key.split("_").toList 
      val orgUnitId = parts.head
      val groupSetCategoryId = parts.drop(1).head
      withMembersFor(orgUnitId,t => {
        val uc = t._1
        val d2lOrgUnit = t._2
        val members = t._3
        val fakeGroupCategory = D2LGroupCategory(groupSetCategoryId.toLong,"",D2LDescription(None,None),None,None,None,false,false,Nil,None,None)
        val groups = interface.getGroups(uc,d2lOrgUnit,fakeGroupCategory)
        groups.flatMap(g => {
          g.Enrollments.flatMap(en => {
            members.find(_.foreignRelationship.exists(fr => fr.system == storeId && fr.key == "%s_%s".format(orgUnitId,en.toString)))
          })
        })
      })
    })
  }
  override def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = {
    groupSet.foreignRelationship.filter(_.system == storeId).toList.flatMap(gsId => {
      val parts = gsId.key.split("_").toList 
      val orgUnitId = parts.head
      val groupSetCategoryId = parts.drop(1).head
      withMembersFor(orgUnitId,t => {
        val uc = t._1
        val d2lOrgUnit = t._2
        val members = t._3
        val groupSets = interface.getGroupCategories(uc,d2lOrgUnit)
        val fakeGroupCategory = D2LGroupCategory(groupSetCategoryId.toLong,"",D2LDescription(None,None),None,None,None,false,false,Nil,None,None)
        val groups = interface.getGroups(uc,d2lOrgUnit,fakeGroupCategory)
        groups.map(g => {
          Group("D2L_Group",g.Name,g.Enrollments.flatMap(en => {
            members.find(_.foreignRelationship.exists(fr => fr.system == storeId && fr.key == "%s_%s".format(orgUnitId,en.toString)))
          }),Some(ForeignRelationship(storeId,"%s_%s_%s".format(orgUnitId,groupSetCategoryId,g.GroupId))))
        })
      })
    })
  }
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = {
    group.foreignRelationship.filter(_.system == storeId).toList.flatMap(gId => {
      val parts = gId.key.split("_").toList 
      val orgUnitId = parts.head
      val groupSetCategoryId = parts.drop(1).head
      val groupId = parts.drop(2).head
      withMembersFor(orgUnitId,t => {
        val uc = t._1
        val d2lOrgUnit = t._2
        val members = t._3
        val groupSets = interface.getGroupCategories(uc,d2lOrgUnit)
        val fakeGroupCategory = D2LGroupCategory(groupSetCategoryId.toLong,"",D2LDescription(None,None),None,None,None,false,false,Nil,None,None)
        interface.getGroup(uc,d2lOrgUnit,fakeGroupCategory,groupId).toList.flatMap(group => {
          group.Enrollments.flatMap(en => {
            members.find(_.foreignRelationship.exists(fr => fr.system == storeId && fr.key == "%s_%s".format(orgUnitId,en.toString)))
          })
        })
      })
    })
  }
  
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = {
    val uc = interface.getUserContext
    interface.getUserByUsername(uc,userData.username).toList.flatMap(cm => {
      List(
        "Identifier" -> cm.UserId.toString,
        "OrgDefinedId" -> cm.OrgDefinedId,
        "OrgId" -> cm.OrgId.toString
      ) ::: 
      cm.UserName.toList.map(un => "UserName" -> un) :::
      cm.ExternalEmail.toList.map(un => "Email" -> un) :::
      cm.FirstName.toList.map(un => "FirstName" -> un) :::
      cm.MiddleName.toList.map(un => "MiddleName" -> un) :::
      cm.LastName.toList.map(un => "LastName" -> un)
    }).map(t => Detail(t._1,t._2))
  } 
}

class D2LGroupStoreProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends D2LInterface(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion) with GroupStoreProvider {
  override val canQuery = true
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
    val roles = getRoles(userContext)
    val courses = getOrgUnits(userContext).filter(_.Type.Id == 3) // 3 is the typeId of courses
    trace("courses found: %s".format(courses.length))

    val intermediaryOrgUnits = new scala.collection.mutable.HashMap[String,OrgUnit]()
    val intermediaryGroupSets = new scala.collection.mutable.HashMap[OrgUnit,List[GroupSet]]()
    val intermediaryGroups = new scala.collection.mutable.HashMap[Tuple2[OrgUnit,GroupSet],List[Group]]()

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
        member.Username.toList.map(fn => (memberName,fn,"D2L_UserName")) ::: 
        member.FirstName.toList.map(fn => (memberName,fn,PersonalInformation.firstName)) ::: 
        member.LastName.toList.map(fn => (memberName,fn,PersonalInformation.surname)) ::: 
        member.Email.toList.map(fn => (memberName,fn,PersonalInformation.email))
      }).flatten.toList

      val sections = getSections(userContext,orgUnit).map(section => {
        Group(GroupKeys.section,section.Name,section.Enrollments.flatMap(mId => members.get(mId).toList.flatten.flatMap(_.OrgDefinedId).filterNot(_ == "").map(mn => Member(mn,Nil,None))))
      })
      val sectionGroupSets = sections match {
        case Nil => Nil
        case some => List(GroupSet(GroupKeys.sectionCategory,nameSection(orgUnit,d2lSectionPlaceholder),some.flatMap(_.members),some))
      }
      val groupCategories = parFlatMap[D2LGroupCategory,GroupSet](getGroupCategories(userContext,orgUnit),groupCategory => {
        val groups = getGroups(userContext,orgUnit,groupCategory).map(group => {
          Group(GroupKeys.group,nameGroup(orgUnit,groupCategory,group),group.Enrollments.flatMap(mId => members.get(mId).toList.flatten.flatMap(_.OrgDefinedId).filterNot(_ == "").map(mn => Member(mn,Nil,None))))
        })
        List(GroupSet(GroupKeys.groupCategory,nameGroupCategory(orgUnit,groupCategory),groups.flatMap(_.members).distinct,groups))
      },4,"groupCategories")
      val children =  sectionGroupSets ::: groupCategories

      val orgUnits = (orgUnit.Name :: orgUnit.Code.toList).map(ou => OrgUnit(GroupKeys.course,ou,(members.values.toList.flatten.flatMap(_.OrgDefinedId).map(mn => Member(mn,Nil,None)).toList ::: children.flatMap(_.members)).distinct,children))

      orgUnits.foreach(newOrgUnit => {
        intermediaryOrgUnits += ((newOrgUnit.name,newOrgUnit))
        intermediaryGroupSets += ((newOrgUnit,newOrgUnit.groupSets))
        newOrgUnit.groupSets.foreach(gs => {
          intermediaryGroups += (((newOrgUnit,gs),gs.groups))
        })
      })

      List((memberDetails,orgUnits))
    },16,"ou")
    val personalInformation = compoundItems.map(_._1)
    val personalDetails:Map[String,List[Detail]] = personalInformation.flatten.groupBy(_._1).map(t => (t._1,t._2.map(m => Detail(m._3,m._2)).distinct))
    val personalRoles:Map[String,List[D2LEnrollment]] = Map(personalDetails.toList.flatMap(tup => {
      tup._2.find(_.key == "D2L_Identifier").map(d2lId => {
        (tup._1,getEnrollments(userContext,d2lId.value))
      })
    }):_*)
    val groupData = compoundItems.flatMap(_._2)
    val groupsByMember:Map[String,List[OrgUnit]] = Map(personalDetails.keys.toList.map(u => (u,groupData.filter(_.members.contains(u)))):_*)
    val membersByGroup:Map[String,List[Member]] = groupData.groupBy(_.name).map(g => (g._1,g._2.flatMap(_.members).toList))

    val orgUnitsByName:Map[String,OrgUnit] = intermediaryOrgUnits.toMap
    val groupSetsByOrgUnit:Map[OrgUnit,List[GroupSet]] = intermediaryGroupSets.toMap
    val groupsByGroupSet:Map[Tuple2[OrgUnit,GroupSet],List[Group]] = intermediaryGroups.toMap

    GroupStoreData(groupsByMember,membersByGroup,personalDetails)
  }
}
