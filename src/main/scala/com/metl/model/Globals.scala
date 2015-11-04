package com.metl.model

import com.metl.liftAuthenticator._
import monash.SAML._

import com.metl.data._
import com.metl.utils._
import com.metl.view._

import net.liftweb.http.SessionVar
import net.liftweb.http.LiftRules
import net.liftweb.common._
import net.liftweb.util.Helpers._

import net.liftweb.util.Props
import scala.xml._

case class PropertyNotFoundException(key: String) extends Exception(key) {
  override def getMessage: String = "Property not found: " + key
}

trait PropertyReader {
  def readProperty(key: String, default: Option[String] = None): String = {
    Props.get(key).getOrElse(default.getOrElse(throw PropertyNotFoundException(key)))
  }

  def readNodes(node: NodeSeq, tag: String): Seq[NodeSeq] = node \\ tag
  def readNode(node: NodeSeq, tag: String): NodeSeq = readNodes(node, tag).headOption.getOrElse(NodeSeq.Empty)
  def readText(node: NodeSeq, tag: String): String = readNode(node, tag).text
  def readMandatoryText(node: NodeSeq, tag: String): String = readNode(node, tag).text match {
    case s: String if s.trim.isEmpty => throw new Exception("mandatory field (%s) not supplied in expected node %s".format(tag, node))
    case other                       => other.trim
  }
  def readAttribute(node:NodeSeq,attrName:String):String = node match {
    case e:Elem => e.attribute(attrName).map(a => a.text).getOrElse("")
    case _ => ""
  }
  def readMandatoryAttribute(node:NodeSeq,attrName:String):String = readAttribute(node,attrName) match {
    case s: String if s.trim.isEmpty => throw new Exception("mandatory attr (%s) not supplied in expected node %s".format(attrName, node))
    case other                       => other.trim
  }
}

object Globals extends PropertyReader {
  val configurationFileLocation = System.getProperty("metlx.configurationFile")
  List(configurationFileLocation).filter(prop => prop match {
    case null => true
    case "" => true
    case _ => false
  }) match {
    case Nil => {}
    case any => {
      println("please ensure that the following properties are set on the command-line when starting the WAR: %s".format(any))
      throw new Exception("properties not provided, server cannot start")
    }
  }
  var isDevMode:Boolean = true

  def stackOverflowName(location:String):String = "%s_StackOverflow_%s".format(location,currentUser.is)
  def stackOverflowName(who:String,location:String):String = "%s_StackOverflow_%s".format(location,who)

  def noticesName(user:String):String = "%s_Notices".format(user)

  case class PropertyNotFoundException(key: String) extends Exception(key) {
    override def getMessage: String = "Property not found: " + key
  }

  object currentStack extends SessionVar[Topic](Topic.defaultValue)
  def getUserGroups:List[(String,String)] = {
    casState.is.eligibleGroups.toList
  }
  var groupsProviders:List[GroupsProvider] = Nil
  object casState extends SessionVar[com.metl.liftAuthenticator.LiftAuthStateData](com.metl.liftAuthenticator.LiftAuthStateDataForbidden)
  object currentUser extends SessionVar[String](casState.is.username)

  val thumbnailSize = SnapshotResolution(320,240) // MeTL thumbnail
  val snapshotSizes = Map(
    SnapshotSize.Thumbnail -> thumbnailSize,  // MeTL thumbnail
    SnapshotSize.Small  -> SnapshotResolution(640,480),  // MeTL small for phones
    SnapshotSize.Medium -> SnapshotResolution(1024,768), // dunno, seems like a good midpoint
    SnapshotSize.Large  -> SnapshotResolution(2560,1600) // WQXGA, largest reasonable size (we guess)
  )
}
case class SnapshotResolution(width:Int,height:Int){}

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
