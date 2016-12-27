package com.metl.model

import com.metl.liftAuthenticator._
import net.liftweb.common._
import com.metl.data._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import java.util.Date
import scala.xml._

object ExternalGradebooks {
  def configureFromXml(in:NodeSeq):List[ExternalGradebook] = {
    (in \\ "d2lGradebook").toList.flatMap(n => {
      for {
        name <- (n \ "@name").headOption.map(_.text)
        d2lBaseUrl <- (n \ "@host").headOption.map(_.text)
        appId <- (n \ "@appId").headOption.map(_.text)
        appKey <- (n \ "@appKey").headOption.map(_.text)
        userId <- (n \ "@userId").headOption.map(_.text)
        userKey <- (n \ "@userKey").headOption.map(_.text)
        leApiVersion <- (n \ "@leApiVersion").headOption.map(_.text)
        lpApiVersion <- (n \ "@lpApiVersion").headOption.map(_.text)
        acceptableRoleList = (n \ "acceptableRole").toList.map(_.text)
      } yield {
        new D2LGradebook(name,d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion){
          override protected val acceptableRoles = acceptableRoleList
        }
      }
    }) ::: Nil
  }
}

abstract class ExternalGradebook(val name:String) extends TryE with Logger {
  def getGradeContexts(username:String = Globals.currentUser.is):Either[Exception,List[OrgUnit]] = Left(notImplemented)
  def getGradeContextClasslist(orgUnitId:String):Either[Exception,List[Map[String,String]]] = Left(notImplemented)
  def getGradesFromContext(context:String):Either[Exception,List[MeTLGrade]] = Left(notImplemented)
  def getGradeInContext(context:String,gradeId:String):Either[Exception,MeTLGrade] = Left(notImplemented)
  def createGradeInContext(context:String,grade:MeTLGrade):Either[Exception,MeTLGrade] = Left(notImplemented)
  def updateGradeInContext(context:String,grade:MeTLGrade):Either[Exception,MeTLGrade] = Left(notImplemented)
  def getGradeValuesForGrade(context:String,gradeId:String):Either[Exception,List[MeTLGradeValue]] = Left(notImplemented)
  def updateGradeValuesForGrade(context:String,gradeId:String,grades:List[MeTLGradeValue]):Either[Exception,List[MeTLGradeValue]] = Left(notImplemented)
}

trait TryE {
  val notImplemented = new Exception("not yet implemented")
  def trye[A](in: => A):Either[Exception,A] = {
    try {
      Right(in)
    } catch {
      case e:Exception => Left(e)
    }
  }
}

class D2LGradebook(override val name:String,d2lBaseUrl:String,appId:String,appKey:String,userId:String,userKey:String,leApiVersion:String,lpApiVersion:String) extends ExternalGradebook(name){
  import com.d2lvalence.idkeyauth._
  import com.d2lvalence.idkeyauth.implementation._

  val config = ServerConfiguration.default
  protected val acceptableRoles = List("teacher","instructor")

  protected def lookupD2LUserId(uc:ID2LUserContext,username:String):String = {
    val rawUser = interface.getUserByUsername(uc,username)
    rawUser.map(_.UserId.toString).getOrElse("")
  }
  protected def lookupUsername(uc:ID2LUserContext,d2lId:String):String = {
    val rawUser = interface.getUser(uc,d2lId)
    rawUser.flatMap(_.UserName).getOrElse("")
  }
  protected val illegalChars = Map(
    '/' -> "slash",
    '"' -> "'",
    '*' -> "asterisk",
    '<' -> "lessThan",
    '>' -> "greaterThan",
    '+' -> "plus",
    '=' -> "equals",
    '|' -> "pipe",
    ',' -> "comma",
    '%' -> "percent"
  )
  protected def shortName(in:String):String = {
    in.foldLeft("")((acc,item) => acc + illegalChars.get(item).getOrElse(new String(Array(item)))).take(128)
  }
  protected def fromGrade(uc:ID2LUserContext,grade:MeTLGrade):D2LGradeObjectCreator = {
    val gradeType = grade.gradeType match {
      case MeTLGradeValueType.Numeric => "Numeric"
      case MeTLGradeValueType.Boolean => "PassFail"
      case MeTLGradeValueType.Text => "Text"
    }
    D2LGradeObjectCreator(
      MaxPoints = grade.numericMaximum.map(_.toLong).getOrElse(100),
      CanExceedMaxPoints = true,
      IsBonus = true,
      ExcludeFromFinalGradeCalculation = true,
      GradeSchemeId = 0L, // this is that nastiness of gradeSchemes, but there's always a default 0-100 on id0
      Name = grade.name,
      ShortName = shortName(grade.name),
      GradeType = gradeType,
      CategoryId = Some(0),
      Description = Some(D2LDescriptionInput(Content = Some(grade.description), Type = Some("text"))),
      AssociatedTool = None
    )
  }
  protected def toGrade(uc:ID2LUserContext,ctx:String,d2lGos:D2LGradeObject):MeTLGrade = {
    val id = "D2L_%s".format(nextFuncName)
    val location = "D2L_%s_%s".format(name,ctx)
    val author = "D2L"
    val visible = false
    val gradeType = d2lGos.GradeType.toLowerCase.trim match {
      case "numeric" => MeTLGradeValueType.Numeric
      case "passfail" => MeTLGradeValueType.Boolean
      case _ => MeTLGradeValueType.Text
    }
    MeTLGrade(config,author,new Date().getTime(),id,location,d2lGos.Name,d2lGos.Description.flatMap(_.Text).getOrElse(""),gradeType,visible,d2lGos.Id.map(foreignId => (name,"%s_%s".format(ctx,foreignId))),None,Some(d2lGos.MaxPoints.toDouble),Some(0.0),Nil)
  }
  protected def fromGradeValue(uc:ID2LUserContext,in:MeTLGradeValue):D2LIncomingGradeValue = {
    val gradeObjectType = in.getType match {
      case MeTLGradeValueType.Numeric => 1
      case MeTLGradeValueType.Boolean => 2
      case MeTLGradeValueType.Text => 4
    }
    val numericScore = in.getNumericGrade
    val passScore = in.getBooleanGrade
    val valueScore = None
    val textScore = in.getTextGrade
    D2LIncomingGradeValue(
      Comments = D2LDescriptionInput(Content = Some(in.getComment.getOrElse("")),Type=Some("Text")),
      PrivateComments = D2LDescriptionInput(Content = Some(in.getPrivateComment.getOrElse("")),Type=Some("Text")),
      GradeObjectType = gradeObjectType,
      PointsNumerator = numericScore,
      Pass = passScore,
      Value = valueScore,
      Text = textScore
    )    
  }
  protected def toGradeValue(uc:ID2LUserContext,gradeObjId:String,in:D2LGradeValue,lookupUsernameFunc:Tuple2[ID2LUserContext,String] => String = t => lookupUsername(t._1,t._2)):MeTLGradeValue = {
    val timestamp = new Date().getTime()
    val author = "D2L"
    val gradeId = gradeObjId
    val gradedUser = in.UserId.map(d2lId => lookupUsernameFunc((uc,d2lId))).getOrElse("")
    val comments = in.Comments.map(c => (c.Text.toList ::: c.Html.toList).filterNot(_ == "").mkString(", "))
    val privateComments = in.PrivateComments.map(c => (c.Text.toList ::: c.Html.toList).filterNot(_ == "").mkString(", "))
    in.GradeObjectType match {
      case 1 => {
        val gradeValue = in.PointsNumerator.getOrElse(0.0)
        MeTLNumericGradeValue(config,author,timestamp,gradeId,gradedUser,gradeValue,comments,privateComments,Nil)
      }
      case 2 => {
        val gradeValue = in.Pass.getOrElse(false)
        MeTLBooleanGradeValue(config,author,timestamp,gradeId,gradedUser,gradeValue,comments,privateComments,Nil)
      }
      case _ => {
        val gradeValue = in.Text.getOrElse("")
        MeTLTextGradeValue(config,author,timestamp,gradeId,gradedUser,gradeValue,comments,privateComments,Nil)
      }
    }
  }

  val interface = new D2LInterface(d2lBaseUrl,appId,appKey,userId,userKey,leApiVersion,lpApiVersion)
  override def getGradeContexts(username:String = Globals.currentUser.is):Either[Exception,List[OrgUnit]] = {
    trye({
      val uc = interface.getUserContext
      val d2lUser = lookupD2LUserId(uc,username)
      val enrollments = interface.getEnrollments(uc,d2lUser)
      enrollments.filter(en => acceptableRoles.contains(en.Role.Name)).map(en => {
        OrgUnit("course",en.OrgUnit.Name,Nil,Nil,Some(ForeignRelationship(name,en.OrgUnit.Id.toString)))
      }).toList
    })
  }
  protected def tryeInAuth[A](orgUnitId:String,action:Tuple2[ID2LUserContext,String] => A,username:String = Globals.currentUser.is):Either[Exception,A] = {
    try {
      val uc = interface.getUserContext
      val d2lId = lookupD2LUserId(uc,username)
      val enrollments = interface.getEnrollments(uc,d2lId)
      if (enrollments.exists(en => en.OrgUnit.Id.toString == orgUnitId && acceptableRoles.contains(en.Role.Name))){
        trye(action((uc,d2lId)))
      } else {
        Left(new Exception("not authorized to assess orgUnit: %s".format(orgUnitId)))
      }
    } catch {
      case e:Exception => Left(e)
    }
  }
  override def getGradeContextClasslist(orgUnitId:String):Either[Exception,List[Map[String,String]]] = {
    tryeInAuth(orgUnitId,tup => {
      val uc = tup._1
      val d2lId = tup._2
      val classlist = interface.getClasslists(uc,D2LOrgUnit(orgUnitId,D2LOrgUnitTypeInfo(0,"",""),"",None,None,None))
      classlist.map(cm => {
        Map(
          (List(
            "Identifier" -> cm.Identifier,
            "ProfileIdentifier" -> cm.ProfileIdentifier,
            "DisplayName" -> cm.DisplayName
          ) ::: 
          cm.Username.toList.map(un => "UserName" -> un) :::
          cm.OrgDefinedId.toList.map(un => "OrgDefinedId" -> un) :::
          cm.Email.toList.map(un => "Email" -> un) :::
          cm.FirstName.toList.map(un => "FirstName" -> un) :::
          cm.LastName.toList.map(un => "LastName" -> un)):_* 
        )
      })
    })
  }
  override def getGradesFromContext(context:String):Either[Exception,List[MeTLGrade]] = {
    tryeInAuth(context,tup => {
      val uc = tup._1
      val d2lId = tup._2
      interface.getGradeObjects(uc,context).map(g => toGrade(uc,context,g))
    })
  }
  override def getGradeInContext(ctx:String,gradeId:String):Either[Exception,MeTLGrade] = {
    tryeInAuth(ctx,tup => {
      val uc = tup._1
      val d2lId = tup._2
      interface.getGradeObject(uc,ctx,gradeId).map(g => toGrade(uc,ctx,g)).head
    })
  }
  override def createGradeInContext(ctx:String,grade:MeTLGrade):Either[Exception,MeTLGrade] = {
    tryeInAuth(ctx,tup => {
      val uc = tup._1
      val d2lId = tup._2
      val newGrade = fromGrade(uc,grade)
      interface.createGradeObject(uc,ctx,fromGrade(uc,grade)).map(g => toGrade(uc,ctx,g)).head
    })
  }
  override def updateGradeInContext(ctx:String,grade:MeTLGrade):Either[Exception,MeTLGrade] = {
    tryeInAuth(ctx,tup => {
      val uc = tup._1
      val d2lId = tup._2
      val (orgUnitId:String,gradeId:String) = {
        val gradeObj = grade.foreignRelationship.filter(_._1 == name).head
        val parts = gradeObj._2.split("_").toList
        (parts.head,parts.drop(1).head)
      }
      interface.getGradeObject(uc,ctx,gradeId).flatMap(oldGrade => {
        val newGrade = oldGrade.copy(
          MaxPoints = grade.numericMaximum.map(_.toLong).getOrElse(oldGrade.MaxPoints),
          Name = grade.name,
          ShortName = shortName(grade.name)/*,
          Description = Some(D2LDescriptionInput(Content = Some(grade.description),Type = Some("text"))),
          GradeType = */
        )
      interface.updateGradeObject(uc,ctx,newGrade).map(g => toGrade(uc,ctx,g))
      }).head
    })
  }
  override def getGradeValuesForGrade(ctx:String,gradeId:String):Either[Exception,List[MeTLGradeValue]] = {
    tryeInAuth(ctx,tup => {
      val uc = tup._1
      val d2lId = tup._2
      val classlists = interface.getClasslists(uc,D2LOrgUnit(ctx,D2LOrgUnitTypeInfo(0,"",""),"",None,None,None))
      interface.getGradeValues(uc,ctx,gradeId).flatMap(ugv => {
        (for {
          gv <- ugv.GradeValue
          ufw = ugv.User
          cgv = gv.copy(UserId = ufw.Identifier)
        } yield {
          cgv
        }).map(gv => toGradeValue(uc,gradeId,gv,(t) => classlists.find(_.Identifier == t._2).flatMap(_.Username).getOrElse(lookupUsername(t._1,t._2))))
      })
    })
  }
  override def updateGradeValuesForGrade(ctx:String,gradeId:String,grades:List[MeTLGradeValue]):Either[Exception,List[MeTLGradeValue]] = {
    tryeInAuth(ctx,tup => {
      val uc = tup._1
      val d2lId = tup._2
      val classlists = interface.getClasslists(uc,D2LOrgUnit(ctx,D2LOrgUnitTypeInfo(0,"",""),"",None,None,None))
      //println("incoming grades: %s".format(grades))
      if (grades.length > 1){
        val originalGrades = interface.getGradeValues(uc,ctx,gradeId).flatMap(_.GradeValue.map(gv => toGradeValue(uc,gradeId,gv,(t) => classlists.find(_.Identifier == t._2).flatMap(_.Username).getOrElse(lookupUsername(t._1,t._2))))) 
        grades.filterNot(gv => originalGrades.exists(og => {
          og.getType == gv.getType &&
          og.getGradedUser == gv.getGradedUser && (
            og.getNumericGrade == gv.getNumericGrade ||
            og.getTextGrade == gv.getTextGrade ||
            og.getBooleanGrade == gv.getBooleanGrade 
          ) &&
          og.getPrivateComment == gv.getPrivateComment &&
          og.getComment == gv.getComment
        })).flatMap(gv => { //only update the ones which have a changed value
          interface.updateGradeValue(uc,ctx,gradeId,classlists.find(_.Username.exists(_ == gv.getGradedUser)).map(_.Identifier).getOrElse(lookupD2LUserId(uc,gv.getGradedUser)),fromGradeValue(uc,gv))
        })
      } else {
        grades.flatMap(gv => {
          interface.updateGradeValue(uc,ctx,gradeId,classlists.find(_.Username.exists(_ == gv.getGradedUser)).map(_.Identifier).getOrElse(lookupD2LUserId(uc,gv.getGradedUser)),fromGradeValue(uc,gv))
        })
      }
      interface.getGradeValues(uc,ctx,gradeId).flatMap(ugv => {
        (for {
          gv <- ugv.GradeValue
          ufw = ugv.User
          cgv = gv.copy(UserId = ufw.Identifier)
        } yield {
          cgv
        }).map(gv => toGradeValue(uc,gradeId,gv,(t) => classlists.find(_.Identifier == t._2).flatMap(_.Username).getOrElse(lookupUsername(t._1,t._2))))
      })
    })
  }
}
