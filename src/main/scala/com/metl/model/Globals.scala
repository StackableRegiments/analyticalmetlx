package com.metl.model

import com.metl.data._
import com.metl.utils._
import com.metl.view._

import net.liftweb.http.SessionVar
import net.liftweb.http.LiftRules
import net.liftweb.common._
import net.liftweb.util.Helpers._

object Globals {
  var isDevMode:Boolean = true

  def stackOverflowName(location:String):String = "%s_StackOverflow_%s".format(location,currentUser.is)
  def stackOverflowName(who:String,location:String):String = "%s_StackOverflow_%s".format(location,who)

  def noticesName(user:String):String = "%s_Notices".format(user)

  object currentStack extends SessionVar[Topic](Topic.defaultValue)

  object casState extends SessionVar[com.metl.cas.CASStateData](com.metl.cas.CASStateDataForbidden)

  def getUserGroups:List[(String,String)] = {
    if (isDevMode){
      List(
        ("ou","Gotham Residents"),
        ("ou","Vigilantes"),
        ("ou","Unrestricted"),
        ("ou","Monash"),
        ("ou","Staff"),
        ("ou","Outpatients"),
        ("ou","Detectives"),
        ("ou","villians")
      )
    } else {
      casState.is.eligibleGroups.toList
    }
  }
  object currentUser extends SessionVar[String](casState.is.username)

  val thumbnailSize = SnapshotResolution(320,240) // MeTL thumbnail
  val snapshotSizes = Map(
    SnapshotSize.Thumbnail -> thumbnailSize,  // MeTL thumbnail
    SnapshotSize.Small  -> SnapshotResolution(640,480),  // MeTL small for phones
    SnapshotSize.Medium -> SnapshotResolution(1024,768), // dunno, seems like a good midpoint
    SnapshotSize.Large  -> SnapshotResolution(2560,1600) // WQXGA, largest reasonable size (we guess)
  )
}
case class SnapshotResolution(width:Int,height:Int)

object SnapshotSize extends Enumeration {
  type SnapshotSize = Value
  val Thumbnail, Small, Medium, Large = Value

  def parse(name:String) ={
    name match {
      case "thumbnail" => Thumbnail
      case "small"  => Small
      case "medium" => Medium
      case "large"  => Large
      case _ => Medium
    }
  }
}

object CurrentSlide extends SessionVar[Box[String]](Empty)
object CurrentConversation extends SessionVar[Box[Conversation]](Empty)

object IsInteractiveUser extends SessionVar[Box[Boolean]](Full(true))

object CurrentStreamEncryptor extends SessionVar[Box[Crypto]](Empty)
object CurrentHandshakeEncryptor extends SessionVar[Box[Crypto]](Empty)
