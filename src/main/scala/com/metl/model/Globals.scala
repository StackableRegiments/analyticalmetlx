package com.metl.model

import com.metl.liftAuthenticator._

import com.metl.data._
import com.metl.utils._
import com.metl.view._

import net.liftweb.http.SessionVar
import net.liftweb.http.LiftRules
import net.liftweb.common._
import net.liftweb.util.Helpers._

import net.liftweb.util.Props
import scala.xml._
import com.metl.renderer.RenderDescription

import net.liftweb.http._

case class PropertyNotFoundException(key: String) extends Exception(key) {
  override def getMessage: String = "Property not found: " + key
}

trait PropertyReader extends Logger {
  def readProperty(key: String, default: Option[String] = None): String = traceIt("readProperty",key,{
    Props.get(key).getOrElse(default.getOrElse(throw PropertyNotFoundException(key)))
  })

  protected def traceIt[A](label:String,param:String,in: => A):A = {
    val res = in
    info("%s(%s) : %s".format(label,param,in))
    res
  }

  def readNodes(node: NodeSeq, tag: String): Seq[NodeSeq] = traceIt("readNodes",tag,node \\ tag)
  def readNode(node: NodeSeq, tag: String): NodeSeq = traceIt("readNode",tag,readNodes(node, tag).headOption.getOrElse(NodeSeq.Empty))
  def readText(node: NodeSeq, tag: String): String = traceIt("readText",tag,readNode(node, tag).text)
  def readInt(node:NodeSeq,tag:String):Option[Int] = traceIt("readInt",tag,Option(readNode(node,tag).text.toInt))
  def readLong(node:NodeSeq,tag:String):Option[Long] = traceIt("readLong",tag,Option(readNode(node,tag).text.toLong))
  def readBool(node:NodeSeq,tag:String):Option[Boolean] = traceIt("readBool",tag,Option(readNode(node,tag).text.toBoolean))
  def readMandatoryText(node: NodeSeq, tag: String): String = traceIt("readMandatoryText",tag,readNode(node, tag).text match {
    case s: String if s.trim.isEmpty => throw new Exception("mandatory field (%s) not supplied in expected node %s".format(tag, node))
    case other                       => other.trim
  })
  def readAttribute(node:NodeSeq,attrName:String):String = traceIt("readAttribute",attrName,node match {
    case e:Elem => e.attribute(attrName).map(a => a.text).getOrElse("")
    case _ => ""
  })
  def readMandatoryAttribute(node:NodeSeq,attrName:String):String = traceIt("readMandatoryAttribute",attrName,readAttribute(node,attrName) match {
    case s: String if s.trim.isEmpty => throw new Exception("mandatory attr (%s) not supplied in expected node %s".format(attrName, node))
    case other                       => other.trim
  })
}

object Globals extends PropertyReader with Logger {
  val configurationFileLocation = System.getProperty("metlx.configurationFile")
  List(configurationFileLocation).filter(prop => prop match {
    case null => true
    case "" => true
    case _ => false
  }) match {
    case Nil => {}
    case any => {
      val e = new Exception("properties not provided, server cannot start")
      error("please ensure that the following properties are set on the command-line when starting the WAR: %s".format(any),e)
      throw e
    }
  }
  val propFile = XML.load(configurationFileLocation)
  val scheme = Some(readText((propFile \\ "serverAddress"),"scheme")).filterNot(_ == "")
  val host = Some(readText((propFile \\ "serverAddress"),"hostname")).filterNot(_ == "")
  val port = Some(readText((propFile \\ "serverAddress"),"port")).filterNot(_ == "").map(_.toInt)
  val importerParallelism = (propFile \\ "importerPerformance").headOption.map(ipn => readAttribute(ipn,"parallelism").toInt).filter(_ > 0).getOrElse(1)
  var isDevMode:Boolean = true


  val liftConfig = (propFile \\ "liftConfiguration")
  val allowParallelSnippets:Boolean = readBool(liftConfig,"allowParallelSnippets").getOrElse(true)
  LiftRules.allowParallelSnippets.session.set(allowParallelSnippets)
  val maxRequests = readInt(liftConfig,"maxConcurrentRequestsPerSession").getOrElse(1000)
  LiftRules.maxConcurrentRequests.session.set((r:net.liftweb.http.Req)=>maxRequests)
  LiftRules.cometRequestTimeout = Full(readInt(liftConfig,"cometRequestTimeout").getOrElse(25))
  LiftRules.maxMimeFileSize = readLong(liftConfig,"maxMimeFileSize").getOrElse(50L * 1024 * 1024) // 50MB file uploads allowed
  LiftRules.maxMimeSize = readLong(liftConfig,"maxMimeSize").getOrElse(100L * 1024 * 1024) // 50MB file uploads allowed
  readBool(liftConfig,"bufferUploadsOnDisk").filter(_ == true).foreach(y => {
    LiftRules.handleMimeFile = net.liftweb.http.OnDiskFileParamHolder.apply
  })

  val ltiIntegrations = readNodes(readNode(propFile,"lti"),"remotePlugin").map(remotePluginNode => (readAttribute(remotePluginNode,"key"),readAttribute(remotePluginNode,"secret")))
  val brightSpaceValenceIntegrations = {
    val bsvin = readNode(propFile,"brightSpaceValence")
    (readAttribute(bsvin,"url"),readAttribute(bsvin,"appId"),readAttribute(bsvin,"appKey"))
  }

  val cloudConverterApiKey = readText(propFile,"cloudConverterApiKey")

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

  object casState {
    import com.metl.liftAuthenticator._
    import net.liftweb.http.S
    def is:LiftAuthStateData = {
      S.containerSession.map(s => {
        val username = s.attribute("user").asInstanceOf[String]
        val authenticated = s.attribute("authenticated").asInstanceOf[Boolean]
        val userGroups = s.attribute("userAttributes").asInstanceOf[List[Tuple2[String,String]]]
        val additionalGroupsFromProviders = Globals.groupsProviders.flatMap(_.getGroupsFor(username))
        val userAttributes = s.attribute("userAttributes").asInstanceOf[List[Tuple2[String,String]]]
        val groups = userGroups ::: additionalGroupsFromProviders
        val lasd = LiftAuthStateData(true,s.attribute("user").asInstanceOf[String],groups,userAttributes)
        println("got state: %s".format(lasd))
        lasd
      }).getOrElse({
        LiftAuthStateDataForbidden
      })
    }
  }
  object currentUser {
    def is:String = casState.is.username
  }
  def isSuperUser:Boolean = false

  object oneNoteAuthToken extends SessionVar[Box[String]](Empty)

  val printDpi = 100
  val ThumbnailSize = new RenderDescription(320,240)
  val SmallSize = new RenderDescription(640,480)
  val MediumSize = new RenderDescription(1024,768)
  val LargeSize = new RenderDescription(1920,1080)
  val PrintSize = new RenderDescription(21 * printDpi, 29 * printDpi)
  val snapshotSizes = List(ThumbnailSize,SmallSize,MediumSize,LargeSize/*,PrintSize*/)

  //this is for testing
  //
  LiftRules.dispatch.append {
    case r@Req(List("testForm"),_,_) => () => Full({
      (for (
        a <- r.param("a");
        b <- r.param("b");
        files = r.uploadedFiles
      ) yield {
        PlainTextResponse("form posted okay\r\na:%s\r\nb:%s\r\nfiles:%s".format(a,b,files.map(f => {
          "%s => %s (%s) %s bytes".format(f.name,f.fileName,f.mimeType,f.length)
        })))
      }).getOrElse({
        val nodes =
          <html>
        <body>
        <form action="/testForm" method="post" enctype="multipart/form-data">
        <label for="a">a</label>
        <input name="a" type="text"/>
        <label for="b">b</label>
        <input name="b" type="text"/>
        <label for="file1">file1</label>
        <input name="file1" type="file"/>
        <label for="file2">file1</label>
        <input name="file2" type="file"/>
        <input type="submit" value="testSubmit"/>
        </form>
        </body>
        </html>
        val response = LiftRules.convertResponse(((nodes,200), S.getHeaders(LiftRules.defaultHeaders((nodes,r))), r.cookies, r))
        response
      })
    })
  }
}

object IsInteractiveUser extends SessionVar[Box[Boolean]](Full(true))

object CurrentStreamEncryptor extends SessionVar[Box[Crypto]](Empty)
object CurrentHandshakeEncryptor extends SessionVar[Box[Crypto]](Empty)
