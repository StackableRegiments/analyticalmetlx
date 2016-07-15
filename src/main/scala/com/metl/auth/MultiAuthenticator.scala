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

case class DescribedAuthenticator(name:String,authenticator:LiftAuthenticator,imageUrl:Option[String] = None)

class MultiAuthenticationSystem(mod:MultiAuthenticator) extends LiftAuthenticationSystem {
  override def dispatchTableItemFilter = (r) => false
  protected def dispatchTableItemFilterInternal:Req=>Boolean = (r) => !mod.checkWhetherAlreadyLoggedIn
  override def dispatchTableItem(r:Req,originalReqId:String) = Full(mod.constructResponse(r,originalReqId))
  LiftRules.dispatch.prepend {
    case r@Req("multiAuth" :: authModule :: _,_,_) if mod.authenticators.exists(_.name == authModule) => () => {
      mod.respondWithAuthenticator(authModule,r)
    }
  }
}

class MultiAuthenticator(val authenticators:List[DescribedAuthenticator],alreadyLoggedIn:() => Boolean,onSuccess:(LiftAuthStateData) => Unit) extends LiftAuthenticator(alreadyLoggedIn,onSuccess) {

  override def checkWhetherAlreadyLoggedIn:Boolean = Stopwatch.time("MultiAuthenticator.checkWhetherAlreadyLoggedIn",alreadyLoggedIn() || InSessionLiftAuthState.is.authenticated)

  def respondWithAuthenticator(authModule:String,req:Req):Box[LiftResponse] = {
    for (
      authenticator <- authenticators.find(_.name == authModule)
    ) yield {
      RedirectResponse("/") // not yet worked out this bit
    }
  }
  protected lazy val defaultNodes = {
    <div>
      <div class="authChoiceContainer">
        <div class="authChoice">
          <a class="authChoiceLink">
            <img class="authChoiceImage"></img>
            <div class="authChoiceName"></div>
          </a>
        </div>
      </div>
    </div>
  }
  protected def constructRedirectUrlForAuthenticator(authenticator:DescribedAuthenticator):String = ""
  override def constructResponse(req:Req,originalRequestId:String):LiftResponse = {
    val nodes = (
      ".authChoice" #> authenticators.map(authenticator => {
        ".authChoiceLink [href]" #> constructRedirectUrlForAuthenticator(authenticator) &
        ".authChoiceImage [src]" #> authenticator.imageUrl &
        ".authChoiceName *" #> Text(authenticator.name) 
      })
    ).apply(Templates(List("multiAuthLoginPage")).getOrElse(defaultNodes))
    LiftRules.convertResponse(
      (nodes,200),
      S.getHeaders(LiftRules.defaultHeaders((nodes,req))),
      S.responseCookies,
      req
    )
  }
}
