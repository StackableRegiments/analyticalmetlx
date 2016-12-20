package com.metl.model

import com.metl.data._

trait ExternalGradebook {
  def getGradeContexts(username:String = Globals.currentUser.is):Either[Exception,List[String]]
  def getGradesFromContext(context:String):Either[Exception,List[MeTLGrade]]
  def updateGradeInContext(context:String,grade:MeTLGrade):Either[Exception,MeTLGrade]
  def getGradeValuesForGrade(context:String,grade:MeTLGrade):Either[Exception,List[MeTLGradeValue]]
  def updateGradeValuesForGrade(context:String,grade:MeTLGrade,grades:List[MeTLGrade]):Either[Exception,List[MeTLGradeValue]]
}
