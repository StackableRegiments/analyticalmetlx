package com.metl.model

import com.metl.data._
import com.metl.utils._
import com.metl.view._

import net.liftweb.http.SessionVar
import net.liftweb.http.LiftRules
import net.liftweb.common._
import net.liftweb.util.Helpers._

import net.liftweb.util.Props
import scala.xml.{Group=>XmlGroup,_}
import scala.util._
import com.metl.renderer.RenderDescription

import net.liftweb.http._

case class AuthState(authenticated:Boolean,account:Account,groups:List[Group],personalDetails:List[Tuple2[String,String]])
   
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

  val ltiIntegrations = readNodes(readNode(propFile,"lti"),"remotePlugin").map(remotePluginNode => (readAttribute(remotePluginNode,"key"),readAttribute(remotePluginNode,"secret")))
  var metlingPots:List[MeTLingPotAdaptor] = Nil
  val brightSpaceValenceIntegrations = {
    val bsvin = readNode(propFile,"brightSpaceValence")
    (readAttribute(bsvin,"url"),readAttribute(bsvin,"appId"),readAttribute(bsvin,"appKey"))
  }

  val cloudConverterApiKey = readText(propFile,"cloudConverterApiKey").getOrElse("")
  val themeName = readText(propFile,"themeName").getOrElse("neutral")
  val googleAnalytics = ("stackable",readText(propFile,"googleAnalytics"))
  val clientGoogleAnalytics = ("client",readText(propFile,"clientGoogleAnalytics"))

  val d2lThreadPoolMultiplier = readInt(propFile,"d2lThreadPoolMultiplier").getOrElse(5)
  val h2ThreadPoolMultiplier = readInt(propFile,"h2ThreadPoolMultiplier").getOrElse(8)

  case class PropertyNotFoundException(key: String) extends Exception(key) {
    override def getMessage: String = "Property not found: " + key
  }

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

  protected object authenticationState {
    import net.liftweb.http.S
    
    val config = ServerConfiguration.default

    private object actuallyIsImpersonator extends SessionVar[Boolean](false)
    private object LoggedInUser extends SessionVar[Option[AuthState]](None)
    private object UserState extends SessionVar[Option[AuthState]](None)

    protected object AuthzGroupsProvider extends GroupsProvider("authz") {
      def groupsFor(profileId:Option[String] = None, account:Option[Account] = None):List[Group] = LoggedInUser.is.toList.flatMap(_.groups).toList
      def personalDetailsFor(profileId:Option[String] = None, account:Option[Account] = None):List[Tuple2[String,String]] = LoggedInUser.is.toList.flatMap(_.personalDetails).toList
      def membersFor(groupId:Option[String] = None):List[String] = Nil
    }
    Groups.addGroupsProvider(AuthzGroupsProvider)

    protected def createProfileForAccount(account:Account):Profile = {
      val newProf = config.createProfile(account.name,Map(
        "createdByUser" -> account.name,
        "createdByProvider" -> account.provider,
        "autoCreatedProfile" -> "true",
        "avatarUrl" -> ""))
      config.updateAccountRelationship(account.name,account.provider,newProf.id,false,true)
      newProf
    }
    private def updateUser(state:AuthState):AuthState = {
      val updatedState = config.getProfileIds(state.account.name,state.account.provider) match {
        case (Nil,_) => {
          val newProf = createProfileForAccount(state.account)
          Globals.availableProfiles(List(newProf))
          Globals.currentProfile(newProf)
          trace("creating profile: %s".format(newProf))
          val groups = Groups.groupsFor(Some(newProf.id),Some(state.account))
          val personalDetails = Groups.personalDetailsFor(Some(newProf.id),Some(state.account))
          state.copy(groups = groups, personalDetails = personalDetails)
        }
        case (items,defaultId) => {
          val ps = config.getProfiles(items:_*)
          val (prof,profiles) = ps.find(_.id == defaultId).map(p => (p,ps)).getOrElse(ps.headOption.map(ho => (ho,ps)).getOrElse({
            val p = createProfileForAccount(state.account)
            (p,List(p))
          }))
          Globals.availableProfiles(profiles)
          Globals.currentProfile(prof)
          val groups = Groups.groupsFor(Some(prof.id),Some(state.account))
          val personalDetails = Groups.personalDetailsFor(Some(prof.id),Some(state.account))
          state.copy(groups = groups, personalDetails = personalDetails)
        }
      }
      trace("settings user to: %s".format(Globals.currentProfile.is))
      UserState(Some(updatedState))
      SecurityListener.ensureSessionRecord
      updatedState
    }
    protected def notLoggedIn = throw new Exception("no logged-in account")
    def is:AuthState = UserState.is.getOrElse(assumeContainerSession)
    def getUserGroups:List[Group] = UserState.is.map(_.groups).getOrElse(notLoggedIn)
    def isSuperUser:Boolean = is.groups.exists(g => g.category == "special" && g.name == "superuser" && g.provider == "authz")
    def isImpersonator:Boolean = actuallyIsImpersonator.is

    def impersonate(account:Account,groups:List[Group],personalAttributes:List[Tuple2[String,String]] = Nil):AuthState = {
      if (isImpersonator){
        val impersonatedState = AuthState(true,account,groups,personalAttributes)
        updateUser(impersonatedState)
      } else {
        UserState.is.getOrElse(notLoggedIn)
      }
    }
    def assumeContainerSession:AuthState = {
      S.containerSession.map(s => {
        val accountProvider = s.attribute("userAccountProvider").asInstanceOf[String]
        val username = s.attribute("user").asInstanceOf[String]
        val authenticated = s.attribute("authenticated").asInstanceOf[Boolean]
        val userGroups = s.attribute("userGroups").asInstanceOf[List[Tuple2[String,String]]].map(t => Group(0L,"g_%s_%s_%s".format(accountProvider,t._1,t._2),t._2,accountProvider,t._1))
        val userAttributes = s.attribute("userAttributes").asInstanceOf[List[Tuple2[String,String]]]
        val account = Account(username,accountProvider)
        val prelimAuthStateData = AuthState(authenticated,account,userGroups,userAttributes)
        if (authenticated){
          LoggedInUser(Some(prelimAuthStateData))
          val groups = Groups.groupsFor(None,Some(Globals.currentAccount.account))
          actuallyIsImpersonator(groups.exists(g => g.category == "special" && g.name == "impersonator" && g.provider == "authz"))
          val personalDetails = Groups.personalDetailsFor(None,Some(Globals.currentAccount.account))
          updateUser(AuthState(true,Account(username,accountProvider),groups,personalDetails))
        } else {
          trace("authentication failed")
          notLoggedIn
        }
      }).getOrElse({
        warn("not in a container session")
        throw new Exception("not in a container session")
      })
    }
  }
 
  object currentUser {
    def is:String = {
      authenticationState.is  // ensure population of the userIdentities
      currentProfile.is.id
    }
  }

  def getLoggedInUser:AuthState = authenticationState.is
  def getUserGroups:List[Group] = authenticationState.getUserGroups
  
  object currentAccount {
    def account:Account = authenticationState.is.account
    def name:String = account.name
    def provider:String = account.provider
  }
  object availableProfiles extends SessionVar[List[Profile]](Nil)
  object currentProfile extends SessionVar[Profile](Profile.empty)
  // special roles
  def isSuperUser:Boolean = authenticationState.isSuperUser
  def isImpersonator:Boolean = authenticationState.isImpersonator
  def assumeContainerSession:AuthState = authenticationState.assumeContainerSession
  def impersonate(newUsername:String,newAccountProvider:String,groups:List[Tuple2[String,String]] = Nil, personalAttributes:List[Tuple2[String,String]] = Nil):AuthState = authenticationState.impersonate(Account(newUsername,newAccountProvider),groups.map(g => Group(0L,"g_%s_%s_%s".format(newAccountProvider,g._1,g._2),g._2,newAccountProvider,g._1)),personalAttributes)

  object oneNoteAuthToken extends SessionVar[Box[String]](Empty)

  val printDpi = 100
  val ThumbnailSize = new RenderDescription(320,240)
  val SmallSize = new RenderDescription(640,480)
  val MediumSize = new RenderDescription(1024,768)
  val LargeSize = new RenderDescription(1920,1080)
  val PrintSize = new RenderDescription(21 * printDpi, 29 * printDpi)
  val snapshotSizes = List(ThumbnailSize/*,SmallSize,MediumSize,LargeSize*//*,PrintSize*/)
}

