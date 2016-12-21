package com.metl.liftAuthenticator

import net.liftweb.http._
import net.liftweb.common._

case class Detail(key:String,value:String)
case class LiftAuthStateData(authenticated:Boolean,username:String,eligibleGroups:Seq[OrgUnit],informationGroups:Seq[Detail])
object LiftAuthStateDataForbidden extends LiftAuthStateData(false,"forbidden",List.empty[OrgUnit],List.empty[Detail]) {}

case class ForeignRelationship(system:String,key:String)
case class Member(name:String,details:List[Detail],foreignRelationship:Option[ForeignRelationship] = None)
case class OrgUnit(ouType:String,name:String,members:List[Member] = Nil,groupSets:List[GroupSet] = Nil,foreignRelationship:Option[ForeignRelationship] = None)
case class GroupSet(groupSetType:String,name:String,members:List[Member] = Nil,groups:List[Group] = Nil,foreignRelationship:Option[ForeignRelationship] = None)
case class Group(groupType:String,name:String,members:List[Member] = Nil,foreignRelationship:Option[ForeignRelationship] = None)
