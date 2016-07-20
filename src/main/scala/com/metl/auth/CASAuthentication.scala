package com.metl.cas

import com.metl.liftAuthenticator._
import com.metl.utils._

import net.liftweb.http._
import net.liftweb.common._
import scala.collection.immutable.List
import net.liftweb.http.provider.HTTPCookie
import java.net.URLEncoder
import org.apache.commons.io.IOUtils

class CASAuthenticationSystem(mod:CASAuthenticator) extends LiftAuthenticationSystem {
  override def dispatchTableItemFilter = (r) => ((!mod.checkWhetherAlreadyLoggedIn) && (!mod.checkReqForCASCookies(r)))
  override def dispatchTableItem(r:Req,originalReqId:String) = Full(mod.constructResponse(r,originalReqId))
}

class CASAuthenticator(realm:String, baseUrl:String, httpClient: Option[IMeTLHttpClient], alreadyLoggedIn:() => Boolean,onSuccess:(LiftAuthStateData) => Unit) extends LiftAuthenticator(alreadyLoggedIn,onSuccess) {
  protected val overrideHost:Box[String] = Empty
  protected val overridePort:Box[Int] = Empty
  protected val overrideScheme:Box[String] = Empty
  protected val baseCasUrl = baseUrl
  protected val serviceValidatePath = "/serviceValidate"
  protected val loginPath = "/login/"

  def this(realm: String, baseUrl:String, alreadyLoggedIn: () => Boolean, onSuccess: (LiftAuthStateData) => Unit) {
      this(realm, baseUrl, None, alreadyLoggedIn, onSuccess)
  }

  private def getHttpClient: IMeTLHttpClient = httpClient.getOrElse(Http.getClient)

  override def checkWhetherAlreadyLoggedIn:Boolean = Stopwatch.time("CASAuthenticator.checkWhetherAlreadyLoggedIn", alreadyLoggedIn() || InSessionLiftAuthState.is.authenticated)

  def checkReqForCASCookies(req:Req):Boolean = Stopwatch.time("CASAuthenticator.checkReqForCASCookies", {
    val result = verifyCASTicket(req)
    if (result.authenticated) {
      InSessionLiftAuthState.set(result)
      onSuccess(result)
      true
    } else {
      false
    }
  })
  private def ticketlessUrl(originalRequest : Req):String = Stopwatch.time("CASAuthenticator.ticketlessUrl",{
    val scheme = overrideScheme.openOr(originalRequest.request.scheme)
    val url = overrideHost.openOr(originalRequest.request.serverName)
    val port = overridePort.openOr(originalRequest.request.serverPort)
    val path = originalRequest.path.wholePath.mkString("/")
    val newParams = originalRequest.params.toList.sortBy(_._1).foldLeft("")((acc, param) => param match {
       case Tuple2(paramName,listOfParams) if (paramName.toLowerCase == "ticket") => acc
       case Tuple2(paramName,listOfParams) => {
        val newItem = "%s=%s".format(URLEncoder.encode(paramName, "utf-8"), URLEncoder.encode(listOfParams.mkString(""), "utf-8")) 
        acc match {
          case "" => acc+"?"+newItem
          case _ => acc+"&"+newItem
        }
       }
       case _ => acc
     })
   newParams.length match {
     case 0 => "%s://%s:%s/%s".format(scheme,url,port,path)
     case _ => "%s://%s:%s/%s%s".format(scheme,url,port,path,newParams)
   }
  })
  private def verifyCASTicket(req:Req) : LiftAuthStateData = Stopwatch.time("CASAuthenticator.verifyCASTicket",{
      req.param("ticket") match {
      case Full(ticket) =>
      {
        val verifyUrl = baseCasUrl + serviceValidatePath +"?ticket=%s&service=%s"
        val casValidityResponse = getHttpClient.getAsString(verifyUrl.format(ticket,URLEncoder.encode(ticketlessUrl(req), "utf-8")))
        val casValidityResponseXml = xml.XML.loadString(casValidityResponse)
        val state = for (
          success <- (casValidityResponseXml \\ "authenticationSuccess");
          user <- (success \\ "user")
        ) yield LiftAuthStateData(true,user.text,List.empty[Tuple2[String,String]],List.empty[Tuple2[String,String]])
        state match{
          case newState :: Nil if newState.authenticated == true => newState
          case _ => LiftAuthStateDataForbidden
        }
      }
      case Empty => LiftAuthStateDataForbidden
      case _ => LiftAuthStateDataForbidden
    }
  })
  val redirectUrl = baseCasUrl + loginPath + "?service=%s" 
  override def constructResponse(req:Req,originalReqId:String) = Stopwatch.time("CASAuthenticator.constructReq",{
      val url = redirectUrl.format(URLEncoder.encode(ticketlessUrl(req),"utf-8"))
      new RedirectResponse(url, req)
  })
}
