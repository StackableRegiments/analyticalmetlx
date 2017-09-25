package com.metl.data

import com.metl.utils._

abstract class ProfileProvider(val config:ServerConfiguration){
  def getProfiles(ids:String *):List[Profile]
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile
  def updateProfile(id:String,profile:Profile):Profile
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] 
  def searchForProfile(query:String):List[Tuple2[Profile,SearchExplanation]] 
  def queryAppliesToProfile(query:String,profile:Profile):Boolean 
}

class PassThroughProfileProvider(p:ProfileProvider) extends ProfileProvider(p.config){
  def getProfiles(ids:String *):List[Profile] = p.getProfiles(ids:_*)
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = p.createProfile(name,attrs,audiences)
  def updateProfile(id:String,profile:Profile):Profile = p.updateProfile(id,profile)
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = p.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = p.getProfileIds(accountName,accountProvider)
  def searchForProfile(query:String):List[Tuple2[Profile,SearchExplanation]] = p.searchForProfile(query)
  def queryAppliesToProfile(query:String,profile:Profile):Boolean = p.queryAppliesToProfile(query,profile)

}
