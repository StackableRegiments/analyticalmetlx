package com.metl.model

import com.metl.TimeSpanParser
import com.metl.external.{Detail, ExternalGradebook, LiftAuthStateData, LiftAuthStateDataForbidden, LtiIntegration, MeTLingPotAdaptor, Member, OrgUnit, GroupsProvider => ExternalGroupsProvider}
import com.metl.utils._
import net.liftweb.http.{LiftRules, LiftSession, SessionVar, SessionVarHelper}
import net.liftweb.common._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

import scala.xml._
import scala.util._
import com.metl.renderer.RenderDescription

case class PropertyNotFoundException(key: String) extends Exception(key) {
  override def getMessage: String = "Property not found: " + key
}

trait PropertyReader extends Logger {
  def readProperty(key: String, default: Option[String] = None): String = traceIt("readProperty",key,{
    Props.get(key).getOrElse(default.getOrElse(throw PropertyNotFoundException(key)))
  })

  protected def traceIt[A](label:String,param:String,in: => A):A = {
    val res = in
    trace("%s(%s) : %s".format(label,param,in))
    res
  }

  def readNodes(node: NodeSeq, tag: String): Seq[NodeSeq] = traceIt("readNodes",tag,node \\ tag)
  def readNode(node: NodeSeq, tag: String): NodeSeq = traceIt("readNode",tag,readNodes(node, tag).headOption.getOrElse(NodeSeq.Empty))
  def readText(node: NodeSeq, tag: String): Option[String] = traceIt("readText",tag,readNodes(node, tag).headOption.map(_.text))
  def readInt(node:NodeSeq,tag:String):Option[Int] = traceIt("readInt",tag,readNodes(node,tag).headOption.map(_.text.toInt))
  def readLong(node:NodeSeq,tag:String):Option[Long] = traceIt("readLong",tag,readNodes(node,tag).headOption.map(_.text.toLong))
  def readBool(node:NodeSeq,tag:String):Option[Boolean] = traceIt("readBool",tag,readNodes(node,tag).headOption.map(_.text.toBoolean))
  def readTimespan(node:NodeSeq,tag:String):Option[TimeSpan] = traceIt("readTimespan",tag,readNodes(node,tag).headOption.map(v => TimeSpanParser.parse(v.text)))
  def readMandatoryText(node: NodeSeq, tag: String): String = traceIt("readMandatoryText",tag,readNodes(node, tag).headOption.map(_.text match {
    case s: String if s.trim.isEmpty => throw new Exception("mandatory field (%s) not supplied in expected node %s".format(tag, node))
    case other                       => other.trim
  }).getOrElse({
    throw new Exception("mandatory field (%s) not supplied in expected node %s".format(tag, node))
  }))
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
  val liveIntegration = System.getProperty("stackable.spending") match {
    case "enabled" =>  true
    case _ => false
  }
  val chunkingTimeout = Try(System.getProperty("metlingpot.chunking.timeout").toInt).toOption match {
    case Some(milis) =>  milis
    case _ => 3000
  }
  val chunkingThreshold = Try(System.getProperty("metlingpot.chunking.strokeThreshold").toInt).toOption match {
    case Some(strokes) =>  strokes
    case _ => 5
  }
  warn("Integrations are live: %s".format(liveIntegration))
  warn("Chunking: %s %s".format(chunkingTimeout,chunkingThreshold))
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
  val scheme = readText((propFile \\ "serverAddress"),"scheme").filterNot(_ == "")
  val host = readText((propFile \\ "serverAddress"),"hostname").filterNot(_ == "")
  val port = readText((propFile \\ "serverAddress"),"port").filterNot(_ == "").map(_.toInt)
  val importerParallelism = (propFile \\ "importerPerformance").headOption.map(ipn => readAttribute(ipn,"parallelism").toInt).filter(_ > 0).getOrElse(1)
  var isDevMode:Boolean = true

  var tokBox = if(liveIntegration) for {
    tbNode <- (propFile \\ "tokBox").headOption
    apiKey <- (tbNode \\ "@apiKey").headOption.map(_.text.toInt)
    secret <- (tbNode \\ "@secret").headOption.map(_.text)
  } yield {
    new TokBox(apiKey,secret)
  } else None


  val liftConfig = (propFile \\ "liftConfiguration")
  readBool(liftConfig,"allowParallelSnippets").foreach(allowParallelSnippets => {
    LiftRules.allowParallelSnippets.session.set(allowParallelSnippets)
  })
  readInt(liftConfig,"maxConcurrentRequestsPerSession").foreach(maxRequests => {
    LiftRules.maxConcurrentRequests.session.set((r:net.liftweb.http.Req)=>maxRequests)
  })
  readInt(liftConfig,"cometRequestTimeout").foreach(cometTimeout => {
    LiftRules.cometRequestTimeout = Full(cometTimeout) //defaults to Empty, which results in 120000
  })
  readLong(liftConfig,"cometRenderTimeout").foreach(cometTimeout => {
    LiftRules.cometRenderTimeout = cometTimeout //defaults to 30000
  })
  readLong(liftConfig,"cometFailureRetryTimeout").foreach(cometTimeout => {
    LiftRules.cometFailureRetryTimeout = cometTimeout //defaults to 10000
  })
  readLong(liftConfig,"cometProcessingTimeout").foreach(cometTimeout => {
    LiftRules.cometProcessingTimeout = cometTimeout //defaults to 5000
  })

  readInt(liftConfig,"cometGetTimeout").foreach(cometTimeout => {
    LiftRules.cometGetTimeout = cometTimeout // this defaults to 140000
  })
  readLong(liftConfig,"maxMimeFileSize").foreach(maxUploadSize => {
    LiftRules.maxMimeFileSize = maxUploadSize
  })
  readLong(liftConfig,"maxMimeSize").foreach(maxMimeSize => {
    LiftRules.maxMimeSize = maxMimeSize
  })
  readBool(liftConfig,"bufferUploadsOnDisk").filter(_ == true).foreach(y => {
    LiftRules.handleMimeFile = net.liftweb.http.OnDiskFileParamHolder.apply
  })
  readInt(liftConfig,"ajaxPostTimeout").foreach(ajaxTimeout => {
    LiftRules.ajaxPostTimeout = ajaxTimeout // this defaults to 5000
  })
  readInt(liftConfig,"ajaxRetryCount").foreach(retryCount => {
    LiftRules.ajaxRetryCount = Full(retryCount) // this defaults to empty, which means keep retrying forever
  })
  readBool(liftConfig,"enableLiftGC").foreach(gc => {
    LiftRules.enableLiftGC = gc
  })
  readLong(liftConfig,"liftGCFailureRetryTimeout").foreach(value => {
    LiftRules.liftGCFailureRetryTimeout = value
  })
  readLong(liftConfig,"liftGCPollingInterval").foreach(value => {
    LiftRules.liftGCPollingInterval = value
  })
  readLong(liftConfig,"unusedFunctionsLifeTime").foreach(value => {
    LiftRules.unusedFunctionsLifeTime = value
  })
  readInt(liftConfig,"stdRequestTimeout").foreach(value => {
    LiftRules.stdRequestTimeout = Full(value)
  })

  val cometConfig = (propFile \\ "cometConfiguration")
  val metlActorLifespan = Full(readTimespan(cometConfig,"metlActorLifespan").getOrElse(2 minutes))
  val searchActorLifespan = Full(readTimespan(cometConfig,"conversationSearchActorLifespan").getOrElse(2 minutes))
  val conversationChooserActorLifespan = Full(readTimespan(cometConfig,"remotePluginConversationChooserActorLifespan").getOrElse(2 minutes))
  val remotePluginConversationChooserActorLifespan = Full(readTimespan(cometConfig,"remotePluginConversationChooserActorLifespan").getOrElse(2 minutes))
  val editConversationActorLifespan = Full(readTimespan(cometConfig,"conversationEditActorLifespan").getOrElse(2 minutes))

  var metlingPots:List[MeTLingPotAdaptor] = ExternalMeTLingPotAdaptors.configureFromXml(readNode(propFile,"metlingPotAdaptors")).right.toOption.getOrElse(Nil)

  var ltiIntegrationPlugins:List[LtiIntegration] = ExternalLtiIntegrations.configureFromXml(readNode(propFile,"lti")).right.toOption.getOrElse(Nil)

  val cloudConverterApiKey = readText(propFile,"cloudConverterApiKey").getOrElse("")
  val themeName = readText(propFile,"themeName").getOrElse("neutral")
  val googleAnalytics = ("stackable",readText(propFile,"googleAnalytics"))
  val clientGoogleAnalytics = ("client",readText(propFile,"clientGoogleAnalytics"))

  val h2ThreadPoolMultiplier = readInt(propFile,"h2ThreadPoolMultiplier").getOrElse(8)

  def stackOverflowName(location:String):String = "%s_StackOverflow_%s".format(location,currentUser.is)
  def stackOverflowName(who:String,location:String):String = "%s_StackOverflow_%s".format(location,who)

  def noticesName(user:String):String = "%s_Notices".format(user)

  case class PropertyNotFoundException(key: String) extends Exception(key) {
    override def getMessage: String = "Property not found: " + key
  }

  object currentStack extends SessionVar[Topic](Topic.defaultValue)
  def getUserGroups:List[OrgUnit] = {
    casState.is.eligibleGroups.toList
  }
  var userProfileProvider:Option[UserProfileProvider] = Some(new CachedInMemoryProfileProvider())

  var groupsProviders:List[ExternalGroupsProvider] = Nil

  var gradebookProviders:List[ExternalGradebook] = Nil
  def getGradebookProvider(providerId:String):Option[ExternalGradebook] = gradebookProviders.find(_.id == providerId)
  def getGradebookProviders:List[ExternalGradebook] = gradebookProviders

  def getGroupsProvider(providerStoreId:String):Option[ExternalGroupsProvider] = getGroupsProviders.find(_.storeId == providerStoreId)
  def getGroupsProviders:List[ExternalGroupsProvider] = groupsProviders

  var mailer:Option[SimpleMailer] = for {
    mailerNode <- (propFile \\ "mailer").headOption
    smtp <- readText(mailerNode, "smtp")
    port <- readInt(mailerNode, "port")
    ssl <- readBool(mailerNode, "ssl")
    username <- readText(mailerNode, "username")
    password <- readText(mailerNode, "password")
    fromAddress <- readText(mailerNode, "fromAddress")
    recipients <- Some(readNodes(readNode(mailerNode, "recipients"),"recipient").map(_.text).toList)
  } yield {
    SimpleMailer(smtp, port, ssl, username, password, Full(fromAddress), recipients)
  }

  object casState {
    import net.liftweb.http.S
    private object validState extends SessionVar[Option[LiftAuthStateData]](None){
      def getForSession(s:LiftSession):Option[LiftAuthStateData] = {
        SessionVarHelper.getFromSession(s,name).openOr(None)
      }
    }
    def getAuthStateForSession(s:LiftSession):Option[LiftAuthStateData] = validState.getForSession(s)
    def is:LiftAuthStateData = {
      validState.is.getOrElse({
        assumeContainerSession
      })
    }
    private object actualUsername extends SessionVar[String]("forbidden")
    private object actuallyIsImpersonator extends SessionVar[Boolean](false)
    def isSuperUser:Boolean = {
      is.eligibleGroups.exists(g => g.ouType == "special" && g.name == "superuser")
    }
    def isAnalyst:Boolean = {
      is.eligibleGroups.exists(g => g.ouType == "special" && g.name == "analyst")
    }
    def isImpersonator:Boolean = actuallyIsImpersonator.is
    def authenticatedUsername:String = actualUsername.is
    def impersonate(newUsername:String,personalAttributes:List[Tuple2[String,String]] = Nil):LiftAuthStateData = {
      if (isImpersonator){
        val prelimAuthStateData = LiftAuthStateData(true,newUsername,Nil,(personalAttributes.map(pa => Detail(pa._1,pa._2)) ::: userProfileProvider.toList.flatMap(_.getProfiles(newUsername).right.toOption.toList.flatten.flatMap(_.foreignRelationships.toList)).map(pa => Detail(pa._1,pa._2))))
        val groups = Globals.groupsProviders.filter(_.canRestrictConversations).flatMap(_.getGroupsFor(prelimAuthStateData))
        val personalDetails = Globals.groupsProviders.flatMap(_.getPersonalDetailsFor(prelimAuthStateData))
        val impersonatedState = LiftAuthStateData(true,newUsername,groups,personalDetails)
        validState(Some(impersonatedState))
        SecurityListener.ensureSessionRecord
        impersonatedState
      } else {
        LiftAuthStateDataForbidden
      }
    }
    def assumeContainerSession:LiftAuthStateData = {
      S.containerSession.map(s => {
        val username = s.attribute("user").asInstanceOf[String]
        val authenticated = s.attribute("authenticated").asInstanceOf[Boolean]
        val userGroups = s.attribute("userGroups").asInstanceOf[List[Tuple2[String,String]]].map(t => OrgUnit(t._1,t._2,List(Member(username,Nil,None)),Nil))
        val userAttributes = s.attribute("userAttributes").asInstanceOf[List[Tuple2[String,String]]]
        val prelimAuthStateData = LiftAuthStateData(authenticated,username,userGroups,userAttributes.map(ua => Detail(ua._1,ua._2)))
        if (authenticated){
          actualUsername(username)
          val groups = Globals.groupsProviders.filter(_.canRestrictConversations).flatMap(_.getGroupsFor(prelimAuthStateData))
          actuallyIsImpersonator(groups.exists(g => g.ouType == "special" && g.name == "impersonator"))
          val personalDetails = Globals.groupsProviders.flatMap(_.getPersonalDetailsFor(prelimAuthStateData))
          val lasd = LiftAuthStateData(true,username,groups,personalDetails)
          validState(Some(lasd))
          userProfileProvider.foreach(upp => {
            upp.updateUserProfile(lasd)
          })
          info("generated authState: %s".format(lasd))
          lasd
        } else {
          LiftAuthStateDataForbidden
        }
      }).getOrElse({
        LiftAuthStateDataForbidden
      })

    }
  }
  object currentUser {
    def is:String = casState.is.username
  }
  // special roles
  def isSuperUser:Boolean = casState.isSuperUser
  def isImpersonator:Boolean = casState.isImpersonator
  def isAnalyst:Boolean = casState.isAnalyst
  def assumeContainerSession:LiftAuthStateData = casState.assumeContainerSession
  def impersonate(newUsername:String,personalAttributes:List[Tuple2[String,String]] = Nil):LiftAuthStateData = casState.impersonate(newUsername,personalAttributes)
}

object IsInteractiveUser extends SessionVar[Box[Boolean]](Full(true))

object CurrentStreamEncryptor extends SessionVar[Box[Crypto]](Empty)
object CurrentHandshakeEncryptor extends SessionVar[Box[Crypto]](Empty)

object ThumbnailSizes {
  val printDpi = 100
  val ThumbnailSize = new RenderDescription(320,240)
  val SmallSize = new RenderDescription(640,480)
  val MediumSize = new RenderDescription(1024,768)
  val LargeSize = new RenderDescription(1920,1080)
  val PrintSize = new RenderDescription(21 * printDpi, 29 * printDpi)
  val snapshotSizes = List(ThumbnailSize/*,SmallSize,MediumSize,LargeSize*//*,PrintSize*/)
  def parse(size:String):RenderDescription = {
    size.trim.toLowerCase match {
      case "thumbnail" => ThumbnailSize
      case "small" => SmallSize
      case "medium" => MediumSize
      case "large" => LargeSize
      case "print" => PrintSize
      case _ => ThumbnailSize
    }
  }
}

//object UserAgent extends SessionVar[Box[String]](S.userAgent)
