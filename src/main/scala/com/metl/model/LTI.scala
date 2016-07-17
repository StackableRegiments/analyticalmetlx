package com.metl.model

import net.liftweb._
import util._
import Helpers._
import common._
import http._
import provider._
import servlet._
import rest._

import org.imsglobal._
import org.imsglobal.lti._
import org.imsglobal.lti.launch._
import javax.servlet.http.HttpServletRequest
import org.imsglobal.pox.IMSPOXRequest
import org.apache.http.client.methods.HttpPost

//brightspark valence
import com.d2lvalence.idkeyauth._
import com.d2lvalence.idkeyauth.implementation._

case class LtiUser(id:String,roles:List[String])
case class LtiLaunchResult(success:Boolean, message:String, result:Either[Exception,LtiLaunch])
case class LtiLaunch(user:LtiUser,version:String,messageType:String,resourceLinkId:String,contextId:String,launchPresentationReturnUrl:String,toolConsumerInstanceGuid:String)

case class RemotePluginSession(token:String,secret:String,key:String,launch:LtiLaunchResult,valenceContext:Option[BrightsparkValenceContext] = None)

case class BrightsparkValenceContext(appContext:ID2LAppContext, userContext: Option[ID2LUserContext] = None)

class LtiIntegration extends Logger {
  object sessionStore extends SessionVar[Map[String,RemotePluginSession]](Map.empty[String,RemotePluginSession])
  val consumerKeyParamName = "oauth_consumer_key"
  val secretMap = Map(Globals.ltiIntegrations:_*)
  def getSecretForKey(key:String):String = {
    val secret = secretMap.get(key).getOrElse("secret")
    println("getting secret: %s => %s".format(key,secret))
    secret
  }

  def sendScore(url:String,key:String,sourcedid:String,score:String,resultData:String):Either[Exception,Unit] = {
    val secret = getSecretForKey(key)
    val postAction:HttpPost = IMSPOXRequest.buildReplaceResult(url,key,secret,sourcedid,score,resultData,true)
    Left(new Exception("not yet implemented"))
  }

  def verifyLtiLaunch(reqBox:Box[HTTPRequest] = S.containerRequest):Either[Exception,RemotePluginSession] = {
    try {
      reqBox match {
        case Full(req) => req match {
          case hrs:HTTPRequestServlet => {
            val cReq:HttpServletRequest = hrs.req
            val verifier = new LtiOauthVerifier()
            req.param(consumerKeyParamName) match {
              case key :: Nil => {
                val secret = getSecretForKey(key)
                val result:LtiVerificationResult = verifier.verify(cReq,secret)
                val token = nextFuncName
                Right(RemotePluginSession(token,secret,key,LtiLaunchResult(result.getSuccess,result.getMessage,(result.getError,result.getLtiLaunchResult) match {
                  case (err,res) if res != null && res.getUser != null && res.getUser.getId != null && res.getUser.getId != "" => {
                    Right(LtiLaunch(LtiUser(res.getUser.getId,res.getUser.getRoles.toArray.toList.map(_.toString)),res.getVersion,res.getMessageType,res.getResourceLinkId,res.getContextId,res.getLaunchPresentationReturnUrl,res.getToolConsumerInstanceGuid))
                  }
                  case (err,_) => Left(new Exception(err.toString))
                })))
              }
              case notAKey => {
                Left(new Exception("request parameter not found: %s => %s".format(consumerKeyParamName,notAKey)))
              }
            }
          }
          case notAHttpReq => {
            Left(new Exception("servlet request is not a httpServletRequest: %s".format(notAHttpReq)))
          }
        }
        case noReq => {
          Left(new Exception("request not available: %s".format(noReq)))
        }
      }
    } catch {
      case e:Exception => Left(e)
    }
  }
  def handleLtiRequest(in:Req,onSuccess:RemotePluginSession=>Box[LiftResponse],storeSession:Boolean = true):Box[LiftResponse] = {
    verifyLtiLaunch(Full(in).map(_.request)) match {
      case Left(e) => {
        error("error while parsing lti request",e)
        Failure(e.getMessage,Full(e),Empty)
      }
      case Right(pluginSession) => {
        println("establishing LTI session: %s => %s".format(in,pluginSession))
        if (storeSession){
          sessionStore(sessionStore.updated(pluginSession.token,pluginSession))
        }
        onSuccess(pluginSession)
      }
    }
  }
}

object RemotePluginIntegration extends BrightSparkIntegration

class BrightSparkIntegration extends LtiIntegration {
//  import com.d2lvalence.idkeyauth._
  import com.metl.data._
  import com.d2lvalence.idkeyauth.implementation._
  protected def ampOrQuestion(url:String):String = {
    val uri = new java.net.URI(url)
    uri.getQuery.trim match {
      case null => "?"
      case "" => "?"
      case other => "&"
    }
  }
  def generateContentResponse(returnUrl:String,htmlContent:String):LiftResponse = {
    RedirectResponse("%s%scontent=%s".format(returnUrl,ampOrQuestion(returnUrl),urlEncode(htmlContent)))
  }
  def generateQuickLinkResponse(returnUrl:String,url:String,title:String,target:String):LiftResponse = {
    RedirectResponse("%s%squickLink=%s&title=%s&target=%s".format(returnUrl,ampOrQuestion(returnUrl),urlEncode(url),urlEncode(title),urlEncode(target)))
  }
  def generateResponse(returnUrl:String):LiftResponse = {
    RedirectResponse(returnUrl)
  }
  protected def postToD2L(ltiToken:String,method:String,apiUrl:String,parameters:Map[String,String]):Unit = {
    for (
      pluginSession <- sessionStore.is.get(ltiToken);
      valenceContext <- pluginSession.valenceContext;
      userContext <- valenceContext.userContext
    ) yield {
      val uri = userContext.createAuthenticatedUri(apiUrl,method)
      println("making API call: %s".format(uri))
    }
//    val appContext = ID2LAppContext = AuthenticationSecurityFactory.createSecurityContext(appId,appKey,appUrl)

  }
  def insertConversationIFrame(ltiToken:String,conversation:Conversation):Unit = {
  }
  def insertConversationQuickLink(ltiToken:String,conversation:Conversation):Unit = {
  }
  def insertConversationSlideIFrame(ltiToken:String,conversation:Conversation,slide:Slide):Unit = {
  }
  def insertConversationSlideQuickLink(ltiToken:String,conversation:Conversation,slide:Slide):Unit = {
  }
}
class BrightSparkIntegrationStatelessDispatch extends RestHelper {
  import net.liftweb.json.JsonAST._
  import com.metl.snippet.Metl._
  val lti:BrightSparkIntegration = RemotePluginIntegration
  val config = com.metl.data.ServerConfiguration.default
  serve {
    case req@Req("testRemotePlugin" :: Nil,_,_) => () => {
      val response = lti.handleLtiRequest(req,pluginSession => {
        val (resultCode,resultDescription) = pluginSession.launch.result match {
          case Left(e) => ("FAILURE","%s :: %s".format(e.getMessage,e.getStackTraceString))
          case Right(launchResult) => ("OK","logged in with Token: %s => %s".format(pluginSession.token,launchResult.user))
        }
        val jObject = JObject(List(JField("result_code",JString(resultCode)),JField("result_description",JString(resultDescription))))
        println("jsonResponse from testRemotePlugin: %s".format(jObject))
        Full(JsonResponse(jObject))
      },false)
      response
    }
  }
}
class BrightSparkIntegrationDispatch extends RestHelper {
  import net.liftweb.json.JsonAST._
  import com.metl.snippet.Metl._
  import java.net.URI
  val lti:BrightSparkIntegration = RemotePluginIntegration
  val config = com.metl.data.ServerConfiguration.default
  val d2lMeTLAppId = Globals.brightSpaceValenceIntegrations._2
  val d2lMeTLAppKey = Globals.brightSpaceValenceIntegrations._3
  val d2lBaseUrl = Globals.brightSpaceValenceIntegrations._1
  protected val brightSparkContextEndpoint = "brightSpark"
  protected val handleUserContextEndpoint = "handleUserContext"
  protected def getBaseUrlFromReq(req:Req):String = {
    val request = req.request
    val url = new URI(request.url)
    "%s://%s%s".format(
      url.getScheme,
      url.getHost,
      url.getPort match {
        case i:Int if i < 1 && request.url.contains(":%s/".format(i.toString)) => url.getScheme match {
          case "https"  => ":443"
          case "http" => ":80"
          case _ => ""
        }
        case portNumber if request.url.contains(":%s/".format(portNumber)) => ":%s".format(portNumber)
        case portNumber => ""
      }
    )
  }
  serve {
    case req@Req(bsce :: "getConversationChooserWithValence" :: Nil,_,_) if bsce == brightSparkContextEndpoint => {
      println("getConversationChooser: %s".format(req))
      lti.handleLtiRequest(req,pluginSession => {
        val appContext:ID2LAppContext = AuthenticationSecurityFactory.createSecurityContext(d2lMeTLAppId,d2lMeTLAppKey,d2lBaseUrl)
        val token = pluginSession.token
        val vctx = BrightsparkValenceContext(appContext)
        val newPluginSession = pluginSession.copy(valenceContext = Some(vctx))
        lti.sessionStore(lti.sessionStore.is.updated(token,newPluginSession))
        val baseUrl = getBaseUrlFromReq(req)
        val redirectUrl = "%s/%s/%s?ltiToken=%s".format(baseUrl,brightSparkContextEndpoint,handleUserContextEndpoint,token)
        val getUserContextUrl = appContext.createWebUrlForAuthentication(new URI(redirectUrl))
        println("redirecting to D2L to get userContext: %s => %s\r\n%s".format(req,getUserContextUrl,newPluginSession))
        Full(RedirectResponse(getUserContextUrl.toString))
      })
    }
    case req@Req(bsce :: "getConversationChooser" :: Nil,_,_) if bsce == brightSparkContextEndpoint => {
      println("getConversationChooser: %s".format(req))
      lti.handleLtiRequest(req,pluginSession => {
        val redirectUrl = com.metl.snippet.Metl.remotePluginConversationChooser(pluginSession.token)
        Full(RedirectResponse(redirectUrl))
      })
    }
    case req@Req(bsce :: huce :: Nil,_,_) if bsce == brightSparkContextEndpoint && huce == handleUserContextEndpoint => () => {
      println("handleUserContext: %s".format(req))
      for (
        token <- req.param("ltiToken");
        response <- lti.handleLtiRequest(req,pluginSession => {
          println("receivedHandleUserContextEndpoint: %s => %s".format(req,pluginSession))
          for (
            userId <- req.param("userId");
            userKey <- req.param("userKey");
            valenceContext <- pluginSession.valenceContext;
            appContext = valenceContext.appContext
          ) yield {
            val uctx = appContext.createUserContext(userId,userKey)
            val newPluginSession = pluginSession.copy(valenceContext = Some(valenceContext.copy(userContext = Some(uctx))))
            lti.sessionStore(lti.sessionStore.is.updated(token,newPluginSession))
            println("redirecting to remoteConversationChooser: %s => %s".format(newPluginSession))
            val redirectUrl = com.metl.snippet.Metl.remotePluginConversationChooser(token)
            RedirectResponse(redirectUrl)
          }
        })
      ) yield {
        response
      }
    }
    case req@Req(bsce :: "remotePluginConversationChosen" :: Nil,_,_) if bsce == brightSparkContextEndpoint => () => {
      println("remotePluginConversationChosen: %s".format(req))
      for (
        ltiToken <- req.param("ltiToken");
        convJid <- req.param("conversationJid");
        details = config.detailsOfConversation(convJid);
        remotePluginSession <- lti.sessionStore.is.get(ltiToken);
        launch <- remotePluginSession.launch.result.right.toOption
      ) yield {
        println("remotePluginConversationChosen in block: %s\r\n%s\r\n%s".format(req,remotePluginSession,details))
        val request = req.request
        val rootUrl = getBaseUrlFromReq(req)
        val targetUrl = rootUrl + boardFor(details.jid)
        //val title = details.title
        //val imageUrl = rootUrl + thumbnailFor(details.jid,details.slides.sortBy(_.index).headOption.map(_.id).getOrElse(0))
        //val responseUrl = lti.generateQuickLinkResponse(launch.launchPresentationReturnUrl,imageUrl,title,targetUrl)
        val iframeContent = <iframe width="100%" height="600px" src={targetUrl}></iframe>
        val responseUrl = lti.generateContentResponse(launch.launchPresentationReturnUrl,iframeContent.toString)
        println("redirecting to: %s".format(responseUrl))
        responseUrl
      }
    }
  }
}
