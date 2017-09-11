package com.metl.model

import com.metl.external.LiftAuthStateData

import scala.xml._
import net.liftweb.common._
import net.liftweb.util.Helpers._

case class UserProfile(username:String,foreignRelationships:Map[String,String],firstName:Option[String] = None,surname:Option[String] = None,emailAddress:Option[String] = None)

trait UserProfileProvider extends Logger {
  protected val firstNameKey = "firstName"
  protected val surnameKey = "surname"
  protected val emailKey = "emailAddress"
  protected val frKeys = List("sluId","d2lId","emailAddress","logonName","upn")

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
  def updateUserProfile(authState:LiftAuthStateData):Either[Exception,Boolean] = {
    getProfiles(authState.username) match {
      case Left(e) => Left(e)
      case Right(Nil) => Left(new Exception("user profile not found"))
      case Right(p :: _) => {
        var newP = p
        authState.informationGroups.find(_.key == firstNameKey).foreach(fn => newP = newP.copy(firstName = Some(fn.value)))
        authState.informationGroups.find(_.key == surnameKey).foreach(fn => newP = newP.copy(surname = Some(fn.value)))
        authState.informationGroups.find(_.key == emailKey).foreach(fn => newP = newP.copy(emailAddress = Some(fn.value)))
        frKeys.foreach(frk => {
          authState.informationGroups.find(_.key == frk).foreach(fn => newP = newP.copy(foreignRelationships = p.foreignRelationships.updated(frk,fn.value)))
        })
        updateProfile(newP)
      }
    }
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
  import java.io._
  override protected def loadStore:scala.collection.mutable.HashMap[String,UserProfile] = {
    try {
      var newStore = new scala.collection.mutable.HashMap[String,UserProfile]()
      fromXml(scala.xml.XML.load(diskCachePath)).foreach(up => {
        newStore += ((up.username,up))
      })
      newStore
    } catch {
      case e:Exception => {
        new scala.collection.mutable.HashMap[String,UserProfile]()

      }
    }
  }
  override protected def updatedProfileForStore(p:UserProfile):Either[Exception,Boolean] = {
    try {
      new PrintWriter(diskCachePath){
        write(toXml(profileStore.updated(p.username,p).values.toList).toString)
        close
      }
      Right(true)
    } catch {
      case e:Exception => Left(e)
    }
  }
}

import net.liftweb.mapper._

object MappedUserProfile extends MappedUserProfile with LongKeyedMetaMapper[MappedUserProfile] {
  def fromUserProfile(up:UserProfile):MappedUserProfile = {
    val record = find(By(MappedUserProfile.username,up.username)).getOrElse({
      MappedUserProfile.create.username(up.username)
    })
    up.firstName.foreach(fn => record.firstName(fn))
    up.surname.foreach(fn => record.surname(fn))
    up.emailAddress.foreach(fn => record.emailAddress(fn))
    record.foreignRelationships({
      val xml = <frs>{
        up.foreignRelationships.toList.map(fr => {
          <fr sys={fr._1} key={fr._2}/>
        })
      }</frs>
      xml.toString
    })
    record
  }
}
trait SafeMapperExtractors {
  def safeString(in:MappedField[String,_]):Option[String] = in.get match {
    case null => None
    case s:String => Some(s)
  }
}
class MappedUserProfile extends LongKeyedMapper[MappedUserProfile] with SafeMapperExtractors {
  def getSingleton = MappedUserProfile
  def primaryKeyField = id
  object id extends MappedLongIndex(this)
  object username extends MappedString(this,64)
  object firstName extends MappedString(this,128)
  object surname extends MappedString(this,128)
  object emailAddress extends MappedString(this,1024)
  object foreignRelationships extends MappedText(this)
  def toUserProfile:UserProfile = {
    val frs = Map((for {
       frn <- (scala.xml.XML.loadString(foreignRelationships.get) \\ "fr")
       sys <- (frn \ "@sys").headOption.map(_.text)
       key <- (frn \ "@key").headOption.map(_.text)
    } yield {
      (sys,key)
    }).toList:_*)
    UserProfile(username,frs,safeString(firstName),safeString(surname),safeString(emailAddress))
  }
}

class DBBackedProfileProvider extends CachedInMemoryProfileProvider with UserProfileSerializers {
  override protected def loadStore:scala.collection.mutable.HashMap[String,UserProfile] = {
    try {
      var newStore = new scala.collection.mutable.HashMap[String,UserProfile]()
      MappedUserProfile.findAll.map(_.toUserProfile).foreach(up => {
        newStore += ((up.username,up))
      })
      newStore
    } catch {
      case e:Exception => {
        new scala.collection.mutable.HashMap[String,UserProfile]()
      }
    }
  }
  override protected def updatedProfileForStore(p:UserProfile):Either[Exception,Boolean] = {
    try {
      Right(MappedUserProfile.fromUserProfile(p).save)
    } catch {
      case e:Exception => Left(e)
    }
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


trait UserProfileSeed {
  def getValues:List[UserProfile] = Nil
}

class CsvUserProfileSeed(path:String) extends UserProfileSeed {
  import com.github.tototoshi.csv._
  import java.io._

  protected val usernameKey = "USERNAME"
  protected val firstNameKey = "FIRSTNAME"
  protected val surnameKey = "SURNAME"
  protected val emailKey = "EMAILADDRESS"
  protected val foreignRelationshipsKey = "FOREIGNRELATIONSHIPS" 

  override def getValues = {
    val reader = CSVReader.open(new File(path))
    val results = reader.allWithHeaders
    reader.close
    results.flatMap(r => {
      for {
        username <- r.get(usernameKey)
        firstName = r.get(firstNameKey)
        surname = r.get(surnameKey)
        emailAddress = r.get(emailKey)
        foreignRelationships <- r.get(foreignRelationshipsKey)
      } yield {
        val frs = Map(foreignRelationships.split("&").flatMap(pairString => {
          pairString.split("=").toList match {
            case List(k,v) => Some((urlDecode(k),urlDecode(v)))
            case _ => None
          } 
        }).toList:_*)
        UserProfile(username,frs,firstName,surname,emailAddress)
      }
    }).toList
  }
}

class XmlUserProfileSeed(path:String) extends UserProfileSerializers with UserProfileSeed {
  override def getValues = {
    val xml = XML.load(path)
    fromXml(xml)
  }
}
