package com.metl.model

import com.metl.liftAuthenticator._
import net.liftweb.common._
import com.metl.data._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import java.util.Date

abstract class ExternalGradebook(val name:String) extends TryE with Logger {
  def getGradeContexts(username:String = Globals.currentUser.is):Either[Exception,List[OrgUnit]] = Left(notImplemented)
  def getGradesFromContext(context:OrgUnit):Either[Exception,List[MeTLGrade]] = Left(notImplemented)
  def getGradeInContext(context:OrgUnit,gradeId:String):Either[Exception,MeTLGrade] = Left(notImplemented)
  def createGradeInContext(context:OrgUnit,grade:MeTLGrade):Either[Exception,MeTLGrade] = Left(notImplemented)
  def updateGradeInContext(context:OrgUnit,grade:MeTLGrade):Either[Exception,MeTLGrade] = Left(notImplemented)
  def getGradeValuesForGrade(context:OrgUnit,grade:MeTLGrade):Either[Exception,List[MeTLGradeValue]] = Left(notImplemented)
  def updateGradeValuesForGrade(context:OrgUnit,grade:MeTLGrade,grades:List[MeTLGradeValue]):Either[Exception,List[MeTLGradeValue]] = Left(notImplemented)
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
    interface.getUserByUsername(uc,username).map(_.UserId.toString).getOrElse("")
  }
  protected def lookupUsername(uc:ID2LUserContext,d2lId:String):String = {
    interface.getUser(uc,d2lId).flatMap(_.UserName).getOrElse("")
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
      Name = grade.description,
      ShortName = shortName(grade.name),
      GradeType = gradeType,
      CategoryId = None,
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
    MeTLGrade(config,author,new Date().getTime(),id,location,d2lGos.ShortName,d2lGos.Name,gradeType,visible,Some((name,"%s_%s".format(ctx,d2lGos.Id))),None,Some(d2lGos.MaxPoints.toDouble),Some(0.0),Nil)
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
      Comments = D2LDescriptionInput(Content = Some(in.getComment.getOrElse("")),Type=Some("text")),
      PrivateComments = D2LDescriptionInput(Content = Some(in.getPrivateComment.getOrElse("")),Type=Some("text")),
      GradeObjectType = gradeObjectType,
      PointsNumerator = numericScore,
      Pass = passScore,
      Value = valueScore,
      Text = textScore
    )    
  }
  protected def toGradeValue(uc:ID2LUserContext,gradeObjId:String,in:D2LGradeValue):MeTLGradeValue = {
    val timestamp = new Date().getTime()
    val author = "D2L"
    val gradeId = gradeObjId
    val gradedUser = in.UserId.map(d2lId => lookupUsername(uc,d2lId)).getOrElse("")
    val comments = in.Comments.map(_.map(_.Text).mkString)
    val privateComments = in.PrivateComments.map(_.map(_.Text).mkString)
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
    Left(notImplemented)
    trye({
      val uc = interface.getUserContext
      interface.getEnrollments(uc,username).filter(en => acceptableRoles.contains(en.Role.Code)).map(en => {
        OrgUnit("course",en.OrgUnit.Name,Nil,Nil,Some((name,en.OrgUnit.Id.toString)))
      }).toList
    })
  }
  override def getGradesFromContext(context:OrgUnit):Either[Exception,List[MeTLGrade]] = {
    trye({
      context.foreignRelationship.filter(_._1 == name).toList.flatMap(ctx => {
        val uc = interface.getUserContext
        interface.getGradeObjects(uc,ctx._2).map(g => toGrade(uc,ctx._2,g))
      })
    })
  }
  override def getGradeInContext(context:OrgUnit,gradeId:String):Either[Exception,MeTLGrade] = {
    trye({
      val ctx = context.foreignRelationship.filter(_._1 == name).head._2
      val uc = interface.getUserContext
      interface.getGradeObject(uc,ctx,gradeId).map(g => toGrade(uc,ctx,g)).head
    })
  }
  override def createGradeInContext(context:OrgUnit,grade:MeTLGrade):Either[Exception,MeTLGrade] = {
   trye({
      val ctx = context.foreignRelationship.filter(_._1 == name).head._2
      val uc = interface.getUserContext
      val newGrade = fromGrade(uc,grade)
      interface.createGradeObject(uc,ctx,fromGrade(uc,grade)).map(g => toGrade(uc,ctx,g)).head
    })
  }
  override def updateGradeInContext(context:OrgUnit,grade:MeTLGrade):Either[Exception,MeTLGrade] = {
    trye({
      val ctx = context.foreignRelationship.filter(_._1 == name).head._2
      val uc = interface.getUserContext
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
  override def getGradeValuesForGrade(context:OrgUnit,grade:MeTLGrade):Either[Exception,List[MeTLGradeValue]] = {
    trye({
      val ctx = context.foreignRelationship.filter(_._1 == name).head._2
      val uc = interface.getUserContext
       val (orgUnitId:String,gradeId:String) = {
        val gradeObj = grade.foreignRelationship.filter(_._1 == name).head
        val parts = gradeObj._2.split("_").toList
        (parts.head,parts.drop(1).head)
      }
      interface.getGradeObject(uc,ctx,gradeId).toList.flatMap(oldGrade => {
        interface.getGradeValues(uc,ctx,oldGrade).flatMap(_.GradeValue.map(gv => toGradeValue(uc,gradeId,gv)))
      })
    })
  }
  override def updateGradeValuesForGrade(context:OrgUnit,grade:MeTLGrade,grades:List[MeTLGradeValue]):Either[Exception,List[MeTLGradeValue]] = {
    trye({
      val ctx = context.foreignRelationship.filter(_._1 == name).head._2
      val uc = interface.getUserContext
       val (orgUnitId:String,gradeId:String) = {
        val gradeObj = grade.foreignRelationship.filter(_._1 == name).head
        val parts = gradeObj._2.split("_").toList
        (parts.head,parts.drop(1).head)
      }
      interface.getGradeObject(uc,ctx,gradeId).toList.flatMap(oldGrade => {
        grades.flatMap(gv => {
          interface.updateGradeValue(uc,ctx,gradeId,lookupD2LUserId(uc,gv.getGradedUser),fromGradeValue(uc,gv))
        })
        interface.getGradeValues(uc,ctx,oldGrade).flatMap(_.GradeValue.map(gv => toGradeValue(uc,gradeId,gv)))
      })
    })
  }
}
