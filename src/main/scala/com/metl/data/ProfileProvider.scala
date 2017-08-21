package com.metl.data

import com.metl.utils._

abstract class ProfileProvider(val config:ServerConfiguration){
  def getProfiles(ids:String *):List[Profile]
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile
  def updateProfile(id:String,profile:Profile):Profile
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] 
}

class PassThroughProfileProvider(p:ProfileProvider) extends ProfileProvider(p.config){
  def getProfiles(ids:String *):List[Profile] = p.getProfiles(ids:_*)
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = p.createProfile(name,attrs,audiences)
  def updateProfile(id:String,profile:Profile):Profile = p.updateProfile(id,profile)
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = p.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = p.getProfileIds(accountName,accountProvider)
}

class CachingProfileProvider(p:ProfileProvider) extends ProfileProvider(p.config){
  protected val profileStore = scala.collection.mutable.HashMap[String,Profile]()
  protected val accountStore = scala.collection.mutable.HashMap[Tuple2[String,String],Tuple2[List[String],String]]()
  def getProfiles(ids:String *):List[Profile] = {
    val (cachedKeys,uncachedKeys) = ids.toList.partition(i => profileStore.contains(i))
    val uncached = p.getProfiles(uncachedKeys:_*)
    val cached = cachedKeys.flatMap(ck => profileStore.get(ck))
    uncached.foreach(uk => profileStore.put(uk.id,uk))
    uncached ::: cached
  }
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = {
    val newP = p.createProfile(name,attrs,audiences)
    profileStore.put(newP.id,newP)
    newP
  }
  def updateProfile(id:String,profile:Profile):Profile = {
    val uP = p.updateProfile(id,profile)
    profileStore.put(uP.id,uP)
    uP
  }
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = {
    val nar = p.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)
    val id = (accountName,accountProvider)
    val current = accountStore.get(id).getOrElse((Nil,""))
    val currentList = current._1
    val currentDefault = current._2
    val updatedValue = {
      (disabled,default) match {
        case (true,_) if profileId == currentDefault => (currentList.filterNot(_ == profileId),"")
        case (true,_) => (currentList.filterNot(_ == profileId),currentDefault)
        case (_,true) => ((profileId :: currentList).distinct,profileId)
        case (_,false) => ((profileId :: currentList).distinct,currentDefault)
      }
    }
    accountStore.put(id,updatedValue)
  }
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = {
    val id = (accountName,accountProvider)
    accountStore.get(id).getOrElse({
      val upstream = p.getProfileIds(accountName,accountProvider)
      accountStore.put(id,upstream)
      upstream
    })
  }
}
