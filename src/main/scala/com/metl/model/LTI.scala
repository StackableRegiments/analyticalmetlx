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

case class LtiLaunchResult(success:Boolean, message:String, result:Either[Exception,LtiLaunch])
case class LtiLaunch(user:Any,version:String,messageType:String,resourceLinkId:String,contextId:String,launchPresentationReturnUrl:String,toolConsumerInstanceGuid:String)

case class RemotePluginSession(token:String,secret:String,key:String,launch:LtiLaunchResult)

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
                  case (err,res) if res.getUser != null && res.getUser != "" => {
                    Right(LtiLaunch(res.getUser,res.getVersion,res.getMessageType,res.getResourceLinkId,res.getContextId,res.getLaunchPresentationReturnUrl,res.getToolConsumerInstanceGuid))
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
  def handleLtiRequest(in:Req,onSuccess:RemotePluginSession=>Box[LiftResponse]/*,onFailure:Exception=>LiftResponse*/):Box[LiftResponse] = {
    verifyLtiLaunch(Full(in).map(_.request)) match {
      case Left(e) => {
        error("error while parsing lti request",e)
        Failure(e.getMessage,Full(e),Empty)
      }
      case Right(pluginSession) => {
        println("establishing LTI session: %s => %s".format(in,pluginSession))
        sessionStore(sessionStore.updated(pluginSession.token,pluginSession))
        onSuccess(pluginSession)
      }
    }
  }
}

object RemotePluginIntegration extends BrightSparkIntegration

class BrightSparkIntegration extends LtiIntegration {
  def generateContentResponse(returnUrl:String,htmlContent:String):LiftResponse = {
    RedirectResponse("%s?content=%s".format(returnUrl,urlEncode(htmlContent)))
  }
  def generateQuickLinkResponse(returnUrl:String,url:String,title:String,target:String):LiftResponse = {
    RedirectResponse("%s?quickLink=%s&title=%s&target=%s".format(returnUrl,urlEncode(url),urlEncode(title),urlEncode(target)))
  }
  def generateResponse(returnUrl:String):LiftResponse = {
    RedirectResponse(returnUrl)
  }
}

class BrightSparkIntegrationDispatch extends RestHelper {
  import net.liftweb.json.JsonAST._
  import com.metl.snippet.Metl._
  val lti = RemotePluginIntegration
  val config = com.metl.data.ServerConfiguration.default
  serve {
    case req@Req("testRemotePlugin" :: Nil,_,_) => () => {
      val response = lti.handleLtiRequest(req,pluginSession => {
        val (resultCode,resultDescription) = pluginSession.launch.result match {
          case Left(e) => ("FAILURE","%s :: %s".format(e.getMessage,e.getStackTraceString))
          case Right(launchResult) => ("OK","logged in as: %s, with token: %s".format(pluginSession.token,launchResult.user))
        }
        val jObject = JObject(List(JField("result_code",JString(resultCode)),JField("result_description",JString(resultDescription))))
        println("jsonResponse from testRemotePlugin: %s".format(jObject))
        Full(JsonResponse(jObject))
      })
      response
    }
    case req@Req("token" :: "lti" :: Nil,_,_) => () => {
      lti.handleLtiRequest(req,pluginSession => {
        Full(RedirectResponse(com.metl.snippet.Metl.remotePluginConversationChooser(pluginSession.token)))
      })
    }
    case req@Req("remotePluginConversationChosen" :: Nil,_,_) => () => {
      for (
        ltiToken <- req.param("ltiToken");
        convJid <- req.param("conversationJid");
        details = config.detailsOfConversation(convJid);
        remotePluginSession <- lti.sessionStore.is.get(ltiToken);
        launch <- remotePluginSession.launch.result.right.toOption
      ) yield {
        val request = req.request
        val url = new java.net.URI(request.url)
        val rootUrl = "%s://%s:%s".format(
          url.getScheme,
          url.getHost,
          url.getPort match {
            case i:Int if i < 1 => url.getScheme match {
              case "https" => "443"
              case "http" => "80"
              case _ => "443"
            }
            case portNumber => portNumber
          }
        )
        val targetUrl = rootUrl + boardFor(details.jid)
        //val title = details.title
        //val imageUrl = rootUrl + thumbnailFor(details.jid,details.slides.sortBy(_.index).headOption.map(_.id).getOrElse(0))
        //val responseUrl = lti.generateQuickLinkResponse(launch.launchPresentationReturnUrl,imageUrl,title,targetUrl)
        val iframeContent = <iframe src={targetUrl}></iframe>
        val responseUrl = lti.generateContentResponse(launch.launchPresentationReturnUrl,iframeContent.toString)
        println("redirecting to: %s".format(responseUrl))
        responseUrl
      }
    }
  }
}
