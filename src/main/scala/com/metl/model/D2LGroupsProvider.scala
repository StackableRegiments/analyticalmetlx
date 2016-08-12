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
 


class PeriodicD2LGroupsProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String,refreshPeriod:TimeSpan) extends PeriodicallyRefreshingGroupsProvider[Map[String,List[Tuple2[String,String]]]](refreshPeriod){
  val provider = new D2LGroupsProvider(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion)
  override def actuallyFetchGroups:Map[String,List[Tuple2[String,String]]] = {
    provider.fetchGroups
  }
  override protected def shouldCheck = true
  protected def parseStore(username:String,store:Map[String,List[Tuple2[String,String]]]) = store.get(username).getOrElse(Nil)
  override def startingValue = Map.empty[String,List[Tuple2[String,String]]]
}

class D2LGroupsProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends Logger {

  def client = com.metl.utils.Http.getClient  

  protected implicit def formats = net.liftweb.json.DefaultFormats

  val bookMarkTag = "bookmark"

  protected def fetchListFromD2L[T](url:java.net.URI)(implicit m:Manifest[T]):List[T] = {
    try {
      parse(client.get(url.toString)).extract[List[T]]
    } catch {
      case e:Exception => {
        debug("exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
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
        val u = "%s%s".format(url.toString,bookmark.map(b => "%s=%s".format(bookMarkTag,b)).getOrElse(""))
        val respObj = parse(client.get(u))
        val resp = respObj.extract[D2LOrgUnitResponse]
        items = items ::: resp.Items
        continuing = resp.PagingInfo.HasMoreItems
        bookmark = resp.PagingInfo.Bookmark
      }
      items
    } catch {
      case e:Exception => {
        debug("exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
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

  def fetchGroups:Map[String,List[Tuple2[String,String]]] = {
    val appContext:ID2LAppContext = AuthenticationSecurityFactory.createSecurityContext(appId,appKey,d2lBaseUrl)
    val userContext = appContext.createUserContext(userId,userKey)
    var rawData = scala.collection.mutable.Map.empty[String,List[Tuple2[String,String]]]
    
    getOrgUnits(userContext).filter(_.Type.Id == 3L).foreach(orgUnit => {
      trace("OU: %s (%s)".format(orgUnit.Name,orgUnit.Code))
      val members = getClasslists(userContext,orgUnit).groupBy(_.Identifier.toLong)
      for (
        memberLists <- members.values;
        member <- memberLists;
        memberName <- member.OrgDefinedId.filterNot(_ == "")
      ) yield {
        trace("member: %s".format(memberName))
        rawData.update(memberName,(("ou",orgUnit.Name) :: orgUnit.Code.toList.map(c => ("ou",c)) ::: rawData.get(memberName).getOrElse(Nil)).distinct)
      }
      getSections(userContext,orgUnit).foreach(section => {
        trace("SECTION: %s".format(section.Name))
        section.Enrollments.foreach(memberId => {
          for (
            membersById:List[D2LClassListUser] <- members.get(memberId).toList;
            member:D2LClassListUser <- membersById;
            memberName:String <- member.OrgDefinedId.filterNot(_ == "")
          ) yield {
            trace("member: %s".format(memberName))
            rawData.update(memberName,(("section",section.Name) :: rawData.get(memberName).getOrElse(Nil)).distinct)
          }
        })
      })
      getGroupCategories(userContext,orgUnit).foreach(groupCategory => {
        trace("GROUP_CATEGORY: %s".format(groupCategory.Name))
        getGroups(userContext,orgUnit,groupCategory).foreach(group => {
          trace("GROUP: %s".format(group.Name))
          group.Enrollments.foreach(memberId => {
            for (
              membersById:List[D2LClassListUser] <- members.get(memberId).toList;
              member:D2LClassListUser <- membersById;
              memberName:String <- member.OrgDefinedId.filterNot(_ == "")
            ) yield {
              trace("member: %s".format(memberName))
              rawData.update(memberName,(("group",group.Name) :: rawData.get(memberName).getOrElse(Nil)).distinct)
            }
          })
        })
      })
    })
    rawData.toMap
  }
}
