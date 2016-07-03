package com.metl.model

import net.liftweb._
import util._
import Helpers._
import common._
import http._
import provider._
import servlet._

import org.imsglobal._
import org.imsglobal.lti._
import org.imsglobal.lti.launch._
import javax.servlet.http.HttpServletRequest
import org.imsglobal.pox.IMSPOXRequest
import org.apache.http.client.methods.HttpPost

case class LtiLaunchResult(success:Boolean, message:String, result:Either[Exception,LtiLaunch])
case class LtiLaunch(user:Any,version:String,messageType:String,resourceLinkId:String,contextId:String,launchPresentationReturnUrl:String,toolConsumerInstanceGuid:String)

class LtiIntegration extends Logger {
  val consumerKeyParamName = "oauth_consumer_key"
  def getSecretForKey(key:String):String = {
    val secret = "secret" //this is about having a corresponding secret for a given key
    secret
  }

  def sendScore(url:String,key:String,sourcedid:String,score:String,resultData:String):Either[Exception,Unit] = {
    val secret = getSecretForKey(key)
    val postAction:HttpPost = IMSPOXRequest.buildReplaceResult(url,key,secret,sourcedid,score,resultData,true)
    Left(new Exception("not yet implemented"))
  }

  def verifyLtiLaunch(reqBox:Box[HTTPRequest] = S.containerRequest):Either[Exception,LtiLaunchResult] = {
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
                Right(LtiLaunchResult(result.getSuccess,result.getMessage,(result.getError,result.getLtiLaunchResult) match {
                  case (err,res) if res.getUser != null && res.getUser != "" => {
                    Right(LtiLaunch(res.getUser,res.getVersion,res.getMessageType,res.getResourceLinkId,res.getContextId,res.getLaunchPresentationReturnUrl,res.getToolConsumerInstanceGuid))
                  }
                  case (err,_) => Left(new Exception(err.toString))
                }))
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
}

class BrightSparkIntegration extends LtiIntegration {
  def handleLtiRequest(in:Req,onSuccess:LtiLaunchResult=>LiftResponse,onFailure:Exception=>LiftResponse):LiftResponse = {
    verifyLtiLaunch(Full(in).map(_.request)) match {
      case Left(e) => {
        onFailure(e)
      }
      case Right(res) => {
        onSuccess(res)
      }
    }
  }
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
