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
  info("created new D2LGroupsProvider for %s (every %s)".format(d2lBaseUrl,refreshPeriod))
  override def actuallyFetchGroups:Map[String,List[Tuple2[String,String]]] = {
    val provider = new D2LGroupsProvider(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion)
    val newGroups = provider.fetchGroups
    println("loaded GroupData for: %s".format(newGroups))
    newGroups
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

  def parFlatMap[A,B](coll:List[A],func:A => List[B]):List[B] = {
    val pc = coll.par
    pc.tasksupport = new scala.collection.parallel.ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(16))
    pc.flatMap(func).toList
  }

  def fetchGroups:Map[String,List[Tuple2[String,String]]] = {
    val appContext:ID2LAppContext = AuthenticationSecurityFactory.createSecurityContext(appId,appKey,d2lBaseUrl)
    val userContext = appContext.createUserContext(userId,userKey)
    val courses = getOrgUnits(userContext).filter(_.Type.Id == 3) // 3 is the typeId of courses
    println("courses found: %s".format(courses.length))
    val remoteDataSet:List[Tuple3[String,String,String]] = parFlatMap[D2LOrgUnit,Tuple3[String,String,String]](courses,orgUnit => { 
      println("OU: %s (%s) %s".format(orgUnit.Name,orgUnit.Code, orgUnit.Type))
      val members = getClasslists(userContext,orgUnit).groupBy(_.Identifier.toLong)
      val combinedSet:List[() => List[Tuple3[String,String,String]]] = List(
        () => {
          (for (
            memberLists <- members.values;
            member <- memberLists;
            memberName <- member.OrgDefinedId.filterNot(_ == "").toList;
            membership <- (memberName,"ou",orgUnit.Name) :: orgUnit.Code.toList.map(c => (memberName,"ou",c))
          ) yield {
            membership
          }).toList
        },
        () => {
          parFlatMap[D2LSection,Tuple3[String,String,String]](getSections(userContext,orgUnit),section => {
            parFlatMap[Long,Tuple3[String,String,String]](section.Enrollments,memberId => {
              for (
                membersById:List[D2LClassListUser] <- members.get(memberId).toList;
                member:D2LClassListUser <- membersById;
                memberName:String <- member.OrgDefinedId.filterNot(_ == "")
              ) yield {
                (memberName,"section",section.Name)
              }
            })
          })
        },
        () => {
          parFlatMap[D2LGroupCategory,Tuple3[String,String,String]](getGroupCategories(userContext,orgUnit),groupCategory => {
            debug("GROUP_CATEGORY: %s".format(groupCategory.Name))
            parFlatMap[D2LGroup,Tuple3[String,String,String]](getGroups(userContext,orgUnit,groupCategory),group => {
              debug("GROUP: %s".format(group.Name))
              parFlatMap[Long,Tuple3[String,String,String]](group.Enrollments,memberId => {
                for (
                  membersById:List[D2LClassListUser] <- members.get(memberId).toList;
                  member:D2LClassListUser <- membersById;
                  memberName:String <- member.OrgDefinedId.filterNot(_ == "")
                ) yield {
                  trace("member: %s".format(memberName))
                  (memberName,"group",group.Name)
                }
              })
            })
          })
        }
      )
      val orgUnitMembers = combinedSet.par.flatMap(f => f()).toList
      println("OU: %s %s".format(orgUnit.Name,orgUnitMembers))
      orgUnitMembers
    })
    remoteDataSet.groupBy(_._1).map(t => (t._1,t._2.distinct.map(m => (m._2,m._3))))
  }
}
