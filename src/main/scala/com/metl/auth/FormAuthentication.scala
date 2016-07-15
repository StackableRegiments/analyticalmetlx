package com.metl.formAuthenticator

import com.metl.liftAuthenticator._
import com.metl.utils._

import scala.xml._
import net.liftweb._
import http._
import js._
import JsCmds._
import util._
import Helpers._
import common._
import SHtml._
import scala.collection.immutable.List
import net.liftweb.http.provider.HTTPCookie
import org.apache.commons.io.IOUtils

import java.net.URLEncoder

class FormAuthenticationSystem(mod:FormAuthenticator) extends LiftAuthenticationSystem {
  override def dispatchTableItemFilter = (r) => false
  protected def dispatchTableItemFilterInternal:Req=>Boolean = (r) => !mod.checkWhetherAlreadyLoggedIn
  override def dispatchTableItem(r:Req,originalReqId:String) = Full(mod.constructResponse(r,originalReqId))
  LiftRules.dispatch.prepend {
    case r@Req("formLogon" :: Nil,_,_) if dispatchTableItemFilterInternal(r) && r.post_? => () => {
      val username = S.param("username")
      val password = S.param("password")
      val path = S.param("path")   
      S.param("originalRequestId").map(originalRequestId => {
        mod.tryLogin(username,password) match {
          case Left(e) => {
            mod.constructResponseWithMessages(r,originalRequestId,List(e.getMessage))
          }
          case Right(true) => {
            LiftAuthAuthentication.redirectToOriginalReq(originalRequestId)
            //Full(RedirectResponse(path.openOr(r.uri)))
          }
          case Right(false) => {
            mod.constructResponseWithMessages(r,originalRequestId,List("authentication failure.  Please check your credentials and try again"))
          }
          case _ => {
            mod.constructResponseWithMessages(r,originalRequestId,List("unknown error"))
          }
        }
      })
    }
    /*
    case r:Req if dispatchTableItemFilterInternal(r) => () => {

      dispatchTableItem(r,originalReqId)
    }
    */
  }
}

class FormAuthenticator(loginPage:NodeSeq, formSelector:String, usernameSelector:String, passwordSelector:String, verifyCredentials:Tuple2[String,String]=>LiftAuthStateData, alreadyLoggedIn:() => Boolean,onSuccess:(LiftAuthStateData) => Unit) extends LiftAuthenticator(alreadyLoggedIn,onSuccess) {

  override def checkWhetherAlreadyLoggedIn:Boolean = Stopwatch.time("FormAuthenticator.checkWhetherAlreadyLoggedIn",alreadyLoggedIn() || InSessionLiftAuthState.is.authenticated)

  def tryLogin(username:Box[String],password:Box[String]):Either[Throwable,Boolean] = {
    val response = for (
      u <- username;
      p <- password
    ) yield {
      try {
        val result = verifyCredentials(u,p)
        InSessionLiftAuthState(result)
        onSuccess(result)
        Right(result.authenticated)
      } catch {
        case e:Throwable => {
          Left(e)
        }
      }
    }
    response.openOr(Left(new Exception("username or password not specified")))
  }
  protected def makeUrlFromReq(req:Req):String = {
    "%s%s".format(req.uri,req.request.queryString.map(qs => "?%s".format(qs)).openOr("")) 
  }
  override def constructResponse(req:Req,originalRequestId:String) = constructResponseWithMessages(req,originalRequestId,List.empty[String]) 
  def constructResponseWithMessages(req:Req,originalRequestId:String,additionalMessages:List[String] = List.empty[String]) = Stopwatch.time("FormAuthenticator.constructReq",{
      val loginPageNode = (
        "%s [method]".format(formSelector) #> "POST" &
        "%s [action]".format(formSelector) #> "/formLogon" &
        "%s *".format(formSelector) #> {(formNode:NodeSeq) => {
          <input type="hidden" name="path" value={makeUrlFromReq(req)}></input> ++ 
          <input type="hidden" name="originalRequestId" value={originalRequestId}></input> ++
          additionalMessages.foldLeft(NodeSeq.Empty)((acc,am) => {
            acc ++ <div class="loginError">{am}</div>
          }) ++ (
// these next two lines aren't working, and I'm not sure why not
            "%s [name]".format(usernameSelector) #> "username" &
            "%s [name]".format(passwordSelector) #> "password"
          ).apply(formNode) 
        }} 
      ).apply(loginPage)
      LiftRules.convertResponse(
        (loginPageNode,200),
        S.getHeaders(LiftRules.defaultHeaders((loginPageNode,req))),
        S.responseCookies,
        req
      )
  })
}
