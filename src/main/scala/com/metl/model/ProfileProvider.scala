package com.metl.model

import scala.xml._

case class UserProfile(username:String,foreignRelationships:Map[String,String],firstName:Option[String] = None,surname:Option[String] = None,emailAddress:Option[String] = None)

trait UserProfileProvider {
  def onProfileUpdated(profile:UserProfile):Unit = {}
  def getProfiles(usernames:String*):Either[Exception,List[UserProfile]]
  def updateProfile(profile:UserProfile):Either[Exception,Boolean] = {
    internalUpdateProfile(profile).right.map(r => {
      if (r){ 
        onProfileUpdated(profile)
      }
      r
    })
  }
  protected def internalUpdateProfile(profile:UserProfile):Either[Exception,Boolean]
}

class CachedInMemoryProfileProvider extends UserProfileProvider {
  protected val profileStore = loadStore
  protected def loadStore:scala.collection.mutable.HashMap[String,UserProfile] = new scala.collection.mutable.HashMap[String,UserProfile]()
  protected def updatedProfileForStore(p:UserProfile):Either[Exception,Boolean] = Right(true)
  override def getProfiles(usernames:String*):Either[Exception,List[UserProfile]] = {
    Right(usernames.toList.flatMap(u => profileStore.get(u)))
  }
  override def internalUpdateProfile(profile:UserProfile):Either[Exception,Boolean] = {
    updatedProfileForStore(profile).right.map(r => {
      if (r){
        profileStore += ((profile.username,profile))
      }
      r
    })
  }
}

class DiskCachedProfileProvider(diskCachePath:String) extends CachedInMemoryProfileProvider with UserProfileSerializers {
  override protected def loadStore:scala.collection.mutable.HashMap[String,UserProfile] = new scala.collection.mutable.HashMap[String,UserProfile]()
  override protected def updatedProfileForStore(p:UserProfile):Either[Exception,Boolean] = {
    Right(true)
  }
}

trait UserProfileSerializers {
  def fromXml(in:NodeSeq):List[UserProfile] = {
    (in \\ "userProfile").toList.flatMap(upn => {
      (upn \ "@username").headOption.map(username => {
        val foreignRelationships = (upn \ "foreignRelationship").toList.flatMap(frn => {
          for {
            system <- (frn \ "@system").headOption.map(_.text)
            key <- (frn \ "@key").headOption.map(_.text)
          } yield {
            (system,key)
          }
        })
        val firstName = (upn \ "@firstName").headOption.map(_.text)
        val surname = (upn \ "@surname").headOption.map(_.text)
        val emailAddress = (upn \ "@emailAddress").headOption.map(_.text)
        UserProfile(username.text,Map(foreignRelationships:_*),firstName,surname,emailAddress)
      })
    })
  }
  def toXml(in:List[UserProfile]):NodeSeq = {
    <userProfiles>{
      in.map(up => {
        <userProfile username={up.username} firstName={up.firstName.map(fn => Text(fn))} surname={up.surname.map(sn => Text(sn))} emailAddress={up.emailAddress.map(ea => Text(ea))}>{
          up.foreignRelationships.toList.map(fr => {
            <foreignRelationship system={fr._1} key={fr._2}/>
          })
        }</userProfile>
      })
    }</userProfiles>
  }
}

