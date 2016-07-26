package com.metl.liftAuthenticator

import net.liftweb.http._
import net.liftweb.common._

case class LiftAuthStateData(authenticated:Boolean,username:String,eligibleGroups:Seq[(String,String)],informationGroups:Seq[(String,String)])
object LiftAuthStateDataForbidden extends LiftAuthStateData(false,"forbidden",List.empty[Tuple2[String,String]],List.empty[Tuple2[String,String]]) {}

object liftAuthStateDevelopmentData {
  lazy val all = List(
    LiftAuthStateData(false,"Roger",List(("ou","Unrestricted"),("uid","UnauthenticatedRoger"),("ou","Student"),("enrolledsubject","PSY2011"),("enrolledsubject","ENG2011"),("enrolledsubject","PHS2012"),("enrolledsubject","BIO2011")),List(("givenname","Roger"),("sn","Dodger"),("mail","roger.dodger@monash.edu"),("cn","Rogey"),("initials","RD"),("gender","male"),("personaltitle","mr"))),
    LiftAuthStateData(false,"Jane",List(("ou","Unrestricted"),("uid","UnauthenticatedJane"),("ou","Student"),("enrolledsubject","PSY2011"),("enrolledsubject","ENG2011"),("enrolledsubject","PHS2012"),("enrolledsubject","BIO2011")),List(("givenname","Jane"),("sn","Normal"),("mail","jane.normal@monash.edu"),("cn","Janey"),("initials","JN"),("gender","female"),("personaltitle","mrs"))),
    LiftAuthStateData(false,"John",List(("ou","Unrestricted"),("uid","UnauthenticatedJohn"),("ou","Student"),("enrolledsubject","PSY2011"),("enrolledsubject","ENG2011"),("enrolledsubject","PHS2012"),("enrolledsubject","BIO2011")),List(("givenname","John"),("sn","Doe"),("mail","John.Doe@monash.edu"),("cn","Jonno"),("initials","JD"),("gender","male"),("personaltitle","mr"))), 
    LiftAuthStateData(false,"Dick",List(("ou","Unrestricted"),("uid","UnauthenticatedDick"),("ou","Staff"),("monashteachingcommitment","PSY2011"),("monashteachingcommitment","ENG2011"),("monashteachingcommitment","PHS2012"),("monashteachingcommitment","BIO2011")),List(("givenname","Dick"),("sn","Tracey"),("mail","richard.tracey@monash.edu"),("cn","Dickey"),("initials","DT"),("gender","male"),("personaltitle","dr")))
  )
  def state(user:String)=all.filter(_.username == user).toList match{
    case List(data, _) => data
    case _ => LiftAuthStateData(false,user,List(("ou","Unrestricted"),("uid","Unauthenticated"+user),("ou","Student")),List(("givenname",user),("sn","Tracey"),("mail",user+"@monash.edu"),("cn",user+"y"),("initials",user.take(2)),("gender","male"),("personaltitle","dr")))
  }
  val default = all(0)
}
