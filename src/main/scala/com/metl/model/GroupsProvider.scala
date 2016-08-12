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

object GroupsProvider {
  def createFlatFileGroups(in:NodeSeq):GroupsProvider = {
    (in \\ "@format").text match {
      case "stLeo" => new StLeoFlatFileGroupsProvider((in \\ "@location").text,(in \\ "@refreshPeriod").text,(in \\ "wantsSubgroups").flatMap(n => (n \\ "@username").map(_.text)).toList)
      case "globalOverrides" => new GlobalOverridesGroupsProvider((in \\ "@location").text,(in \\ "@refreshPeriod").text)
      case "specificOverrides" => new SpecificOverridesGroupsProvider((in \\ "@location").text,(in \\ "@refreshPeriod").text)
      case "d2l" => (for {
        host <- (in \\ "@host").headOption.map(_.text)
        apiVersion <- (in \\ "@apiVersion").headOption.map(_.text)
        appId <- (in \\ "@appId").headOption.map(_.text)
        appKey <- (in \\ "@appKey").headOption.map(_.text)
        userId <- (in \\ "@userId").headOption.map(_.text)
        userKey <- (in \\ "@userKey").headOption.map(_.text)
        refreshPeriod = (in \\ "@refreshPeriod").headOption.map(_.text)
      } yield {
        new D2LGroupsProvider(host,appId,appKey,userId,userKey,apiVersion,refreshPeriod.getOrElse("5 minutes"))
      }).getOrElse({
        throw new Exception("missing parameters for d2l groups provider")
      })
      case _ => throw new Exception("unrecognized flatfile format")
    }
  }
}

trait GroupsProvider {
  def getGroupsFor(username:String):List[Tuple2[String,String]] = {
    Nil
  }
}

class SelfGroupsProvider extends GroupsProvider {
  override def getGroupsFor(username:String) = List(("ou",username))
}

abstract class RefreshingFlatFileGroupsProvider(path:String,refreshPeriod:String) extends PeriodicallyRefreshingFileReadingGroupsProvider[Map[String,List[Tuple2[String,String]]]](path,refreshPeriod) with GroupsProvider {
  override def startingValue = Map.empty[String,List[Tuple2[String,String]]]
  override def getGroupsFor(username:String) = {
    lastCache.get(username).getOrElse(Nil)    
  }
}

abstract class PeriodicallyRefreshingFileReadingGroupsProvider[T](path:String,refreshPeriod:String) extends Logger { 
  protected val timespan = 5 minutes
  protected var lastModified:Long = 0
  protected def startingValue:T
  protected var lastCache:T = startingValue
  protected var cache = new PeriodicallyRefreshingVar[Unit](timespan,() => {
    val newCheck = new java.io.File(path).lastModified()
    if (newCheck > lastModified){
      debug("file modification detected: %s".format(path))
      lastCache = actuallyFetchGroups
      lastModified = newCheck
    }
  })
  protected def actuallyFetchGroups:T
}

abstract class PeriodicallyRefreshingGroupsProvider[T](refreshPeriod:String) extends Logger { 
  protected val timespan = 5 minutes
  protected var lastModified:Long = 0
  protected def startingValue:T
  protected var lastCache:T = startingValue
  protected var cache = new PeriodicallyRefreshingVar[Unit](timespan,() => {
    val newCheck = new java.util.Date().getTime()
    lastCache = actuallyFetchGroups
    lastModified = newCheck
  })
  protected def actuallyFetchGroups:T
}


class D2LGroupsProvider(d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,apiVersion:String,refreshPeriod:String) extends PeriodicallyRefreshingGroupsProvider[Map[String,List[Tuple2[String,String]]]](refreshPeriod) with GroupsProvider {
  import org.imsglobal._
  import org.imsglobal.lti._
  import org.imsglobal.lti.launch._
//  import javax.servlet.http.HttpServletRequest
//  import org.imsglobal.pox.IMSPOXRequest
//  import org.apache.http.client.methods.HttpPost

  //brightspark valence
  import com.d2lvalence.idkeyauth._
  import com.d2lvalence.idkeyauth.implementation._

  import net.liftweb.json.JsonAST
  import net.liftweb.json.JsonDSL._
  import net.liftweb.json.Printer._
  import net.liftweb.json._

  case class D2LOrgUnitTypeInfo(
    Id:Long,
    Code:String,
    Name:String
  )
  case class D2LOrgUnit(
    Id:Long,
    Type:D2LOrgUnitTypeInfo,
    Name:String,
    Code:Option[String],
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
 
  def client = com.metl.utils.Http.getClient  

  protected implicit def formats = net.liftweb.json.DefaultFormats

  protected def fetchListFromD2L(url:java.net.URI):JArray = {
    try {
      val resp = client.get(url.toString)
      parse(resp) match {
        case j:JArray => j
        case j:JValue => JArray(List(j))
      }
    } catch {
      case e:Exception => {
        println("exception when accessing: %s => %s\r\n".format(url.toString,e.getMessage,e.getStackTraceString))
        JArray(Nil)
      }
    }
  }

  protected def getOrgUnits(userContext:ID2LUserContext):List[D2LOrgUnit] = {
    fetchListFromD2L(userContext.createAuthenticatedUri("/d2l/api/%s/orgstructure/".format(apiVersion),"GET")).extract[List[D2LOrgUnit]]
  }
  protected def getClasslists(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LClassListUser] = {
    fetchListFromD2L(userContext.createAuthenticatedUri("/d2l/api/le/%s/%s/classlist/".format(apiVersion,orgUnit.Id),"GET")).extract[List[D2LClassListUser]]
  }
  protected def getSections(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LSection] = {
    fetchListFromD2L(userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/sections/".format(apiVersion,orgUnit.Id),"GET")).extract[List[D2LSection]]
  }
  protected def getGroupCategories(userContext:ID2LUserContext,orgUnit:D2LOrgUnit):List[D2LGroupCategory] = {
    fetchListFromD2L(userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/groupcategories/".format(apiVersion,orgUnit.Id),"GET")).extract[List[D2LGroupCategory]]
  }
  protected def getGroups(userContext:ID2LUserContext,orgUnit:D2LOrgUnit,groupCategory:D2LGroupCategory):List[D2LGroup] = {
    fetchListFromD2L(userContext.createAuthenticatedUri("/d2l/api/lp/%s/%s/groupcategories/%s/groups/".format(apiVersion,orgUnit.Id,groupCategory.GroupCategoryId),"GET")).extract[List[D2LGroup]]
  }

  override def actuallyFetchGroups:Map[String,List[Tuple2[String,String]]] = {
    val appContext:ID2LAppContext = AuthenticationSecurityFactory.createSecurityContext(appId,appKey,d2lBaseUrl)
    val userContext = appContext.createUserContext(userId,userKey)
    var rawData = Map.empty[String,List[Tuple2[String,String]]]
    
    getOrgUnits(userContext).foreach(orgUnit => {
      val members = getClasslists(userContext,orgUnit).groupBy(_.Identifier.toLong)
      for (
        memberLists <- members.values;
        member <- memberLists;
        memberName <- member.OrgDefinedId
      ) yield {
        rawData.updated(memberName,("ou",orgUnit.Name) :: rawData.get(memberName).getOrElse(Nil))
      }
      getSections(userContext,orgUnit).foreach(section => {
        section.Enrollments.foreach(memberId => {
          for (
            membersById:List[D2LClassListUser] <- members.get(memberId).toList;
            member:D2LClassListUser <- membersById;
            memberName:String <- member.OrgDefinedId
          ) yield {
            rawData.updated(memberName,("section",section.Name) :: rawData.get(memberName).getOrElse(Nil))
          }
        })
      })
      getGroupCategories(userContext,orgUnit).foreach(groupCategory => {
        getGroups(userContext,orgUnit,groupCategory).foreach(group => {
          group.Enrollments.foreach(memberId => {
            for (
              membersById:List[D2LClassListUser] <- members.get(memberId).toList;
              member:D2LClassListUser <- membersById;
              memberName:String <- member.OrgDefinedId
            ) yield {
              rawData.updated(memberName,("group",group.Name) :: rawData.get(memberName).getOrElse(Nil))
            }
          })
        })
      })
    })
    rawData
  }
  override def startingValue = Map.empty[String,List[Tuple2[String,String]]]
  override def getGroupsFor(username:String) = {
    lastCache.get(username).getOrElse(Nil)    
  }
}

class GlobalOverridesGroupsProvider(path:String,refreshPeriod:String) extends PeriodicallyRefreshingFileReadingGroupsProvider[List[Tuple2[String,String]]](path,refreshPeriod) with GroupsProvider with Logger {
  info("created new globalGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override def getGroupsFor(username:String) = lastCache
  override protected def startingValue = Nil
  override protected def actuallyFetchGroups:List[Tuple2[String,String]] = {
    var rawData = List.empty[Tuple2[String,String]]
    Source.fromFile(path).getLines.foreach(line => {
      line.split("\t") match {
        case Array(groupType,groupKey) => {
          rawData = (List((groupType,groupKey)) ::: rawData).distinct
        }
        case _ => {}
      }
    })
    println("loaded groupData for %s: %s".format(path,rawData))
    rawData
  }
}

class SpecificOverridesGroupsProvider(path:String,refreshPeriod:String) extends RefreshingFlatFileGroupsProvider(path,refreshPeriod) with Logger {
  info("created new specificGroupsProvider(%s,%s)".format(path,refreshPeriod))
  override def actuallyFetchGroups:Map[String,List[Tuple2[String,String]]] = {
    var rawData = Map.empty[String,List[Tuple2[String,String]]]
    Source.fromFile(path).getLines.foreach(line => {
      line.split("\t") match {
        case Array(username,groupType,groupKey) => {
          rawData = rawData.updated(username,(List((groupType,groupKey)) ::: rawData.get(username).toList.flatten).distinct)
        }
        case _ => {}
      }
    })
    println("loaded groupData for %s: %s".format(path,rawData))
    rawData
  }
}

class StLeoFlatFileGroupsProvider(path:String,refreshPeriod:String, facultyWhoWantSubgroups:List[String] = List.empty[String]) extends RefreshingFlatFileGroupsProvider(path,refreshPeriod) with Logger {
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
