package com.metl.liftAuthenticator

import net.liftweb.http._
import net.liftweb.common._

case class LiftAuthStateData(authenticated:Boolean,username:String,eligibleGroups:Seq[OrgUnit],informationGroups:Seq[(String,String)])
object LiftAuthStateDataForbidden extends LiftAuthStateData(false,"forbidden",List.empty[OrgUnit],List.empty[Tuple2[String,String]]) {}

case class Member(name:String,details:List[Tuple2[String,String]],foreignRelationship:Option[Tuple2[String,String]] = None)
case class OrgUnit(ouType:String,name:String,members:List[Member] = Nil,groupSets:List[GroupSet] = Nil,foreignRelationship:Option[Tuple2[String,String]] = None)
case class GroupSet(groupSetType:String,name:String,members:List[Member] = Nil,groups:List[Group] = Nil,foreignRelationship:Option[Tuple2[String,String]] = None)
case class Group(groupType:String,name:String,members:List[Member] = Nil,foreignRelationship:Option[Tuple2[String,String]] = None)
