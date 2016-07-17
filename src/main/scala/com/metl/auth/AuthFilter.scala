package com.metl.auth

import java.util.UUID
import javax.servlet._
import javax.servlet.http.{HttpServletRequestWrapper,HttpServletRequest, HttpServletResponse,HttpSession}
import net.liftweb.common.{Empty, Box, Full, Failure}
import net.liftweb.util.Helpers._
import java.util.Date
import scala.collection.JavaConversions._

import net.liftweb.mocks.MockHttpServletRequest
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayOutputStream,ByteArrayInputStream,InputStreamReader,BufferedReader,Reader}
//import org.apache.commons.httpclient.methods.multipart.MultiPartRequestEntity
import org.apache.commons.httpclient.params.HttpMethodParams


class CachingHttpServletRequestWrapper(request:HttpServletRequest) extends HttpServletRequestWrapper(request){
  protected val cachedData = IOUtils.toByteArray(request.getInputStream)
  class CachedServletInputStream extends ServletInputStream {
    val bis = new ByteArrayInputStream(cachedData)
    override def read():Int = bis.read
    override def read(b:Array[Byte]):Int = bis.read(b)
    override def read(b:Array[Byte],off:Int,len:Int):Int = bis.read(b,off,len)
    override def reset = bis.reset
    override def skip(n:Long) = bis.skip(n)
    override def available:Int = bis.available
    override def close = bis.close
    override def mark(readlimit:Int) = bis.mark(readlimit)
    override def markSupported:Boolean = bis.markSupported
  }
  override def getInputStream:ServletInputStream = {
    new CachedServletInputStream
  }
  override def getReader:BufferedReader = {
    new BufferedReader(new InputStreamReader(getInputStream))
  }
  protected lazy val paramMap:Map[String,Array[String]] = Map(getParameterMap.toList:_*)
  override def getParameterMap:java.util.Map[String,Array[String]] = {
    var qpMap:Map[String,Array[String]] = Map(super.getParameterMap().asInstanceOf[java.util.Map[String,Array[String]]].toList:_*)
    try {
      val charset = getCharacterEncoding match {
        case null => "UTF-8"
        case other => other
      }
      val bodyString = new String(cachedData,charset)
      Some(getContentType).filterNot(_ == null).map(_.trim.toLowerCase) match {
        case Some("application/x-www-form-urlencoded") => {
          bodyString.split("&").toList.foreach(line => {
            var key :: values = line.split("=").toList
            var value:String = values.mkString("=")
            val priorValues = qpMap.get(key).map(_.toList).getOrElse(List.empty[String])
            qpMap = qpMap.updated(key,(value :: priorValues).toArray)
          })
        }
        case Some("multipart/form-data") => {
          //got to decode multipart data next, which includes decoding files, oh what fun.  there must be a library about which does all of this neatly.
        }
        case _ => {}
      }
    } catch {
      case e:Exception => {
      }
    }
    qpMap
  }
  override def getParameter(key:String):String = {
    paramMap.get(key).flatMap(_.headOption).getOrElse(null)
  }
  override def getParameterValues(key:String):Array[String] = {
    paramMap.get(key).toList.flatMap(_.toList).toArray
  }
  override def getParameterNames:java.util.Enumeration[String] = {
    paramMap.keys.toList.iterator
  }
}

class CloneableHttpServletRequestWrapper(request:HttpServletRequest) extends HttpServletRequestWrapper(request){
  protected val cachedData = IOUtils.toByteArray(getInputStream)
  def duplicate:CloneableHttpServletRequestWrapper = {
    val clonedReq = new MockHttpServletRequest(new java.net.URL(request.getRequestURL.toString),request.getContextPath)    
    clonedReq.session = request.getSession
    clonedReq.parameters = parameters
    val bytes = getBytes
    clonedReq.contentType = getContentType
    clonedReq.charEncoding = getCharacterEncoding
    clonedReq.method = request.getMethod
    clonedReq.body = bytes
    clonedReq.localPort = request.getLocalPort
    clonedReq.localAddr = request.getLocalAddr
    clonedReq.remoteAddr = request.getRemoteAddr
    clonedReq.servletPath = request.getServletPath
    clonedReq.headers = headers
    clonedReq.cookies = cookies
    clonedReq.attributes = attributes
    new CloneableHttpServletRequestWrapper(clonedReq){
      override def getRequest = request
    }

  }
  lazy val getBytes:Array[Byte] = {
    cachedData
  }
  lazy val attributes = Map(request.getAttributeNames.toList.map(a => {
    val an:String = a.toString
    (an,request.getAttribute(an))
  }):_*)
  lazy val cookies = request.getCookies.toList
  lazy val parameters = request.getParameterNames.toList.flatMap(p => {
    val pn:String = p.toString
    request.getParameterValues(pn).toList.map(pv => (pn,pv.asInstanceOf[String]))
  })
  lazy val headers:Map[String,List[String]] = {
    Map(request.getHeaderNames.toList.map(h => {
      val hn:String = h.toString
      (hn,request.getHeaders(hn).map(_.asInstanceOf[String]).toList)
    }):_*)
  }
  def freeze:CloneableHttpServletRequestWrapper = {
    this.finalize
    this
  }
  override def toString:String = {
    "Req(method=%s,uri=%s,params=%s,bytes=%s,attributes=%s,cookies=%s,headers=%s]".format(getMethod,getRequestURI,parameters,new String(getBytes,"UTF-8").take(40),attributes,cookies,headers)
  }
}

abstract class AuthSession(val session:HttpSession)

case class HealthyAuthSession(override val session:HttpSession,originalRequest:Option[HttpServletRequest] = None,username:String,groups:List[Tuple2[String,String]] = Nil,attrs:List[Tuple2[String,String]] = Nil) extends AuthSession(session)
abstract class InProgressAuthSession(override val session:HttpSession,val originalRequest:HttpServletRequest) extends AuthSession(session)
case class EmptyAuthSession(override val session:HttpSession) extends AuthSession(session)

object LowLevelSessionStore {
  val SessionNotFound = new Exception("session not found")
  val SessionExpired = new Exception("session expired")
  protected val sessionStore:scala.collection.mutable.Map[String,AuthSession] = scala.collection.mutable.Map.empty[String,AuthSession] 

  protected def isExpired(authSession:AuthSession):Boolean = {
    (new Date().getTime - authSession.session.getLastAccessedTime) > (authSession.session.getMaxInactiveInterval * 1000) //the interval is in seconds, while the lastAccessed is measured in miliseconds
  }

  def getValidSession(session:HttpSession):Either[Exception,AuthSession] = {
    val sessionId = session.getId
    val res = sessionStore.get(sessionId) match {
      case None => Left(SessionNotFound)
      case Some(s) if isExpired(s) => {
        //we're checking this ourselves, because we might hold onto a reference to a session after it should otherwise have been expired, so we'd best check whether it should still be valid.
        sessionStore - sessionId
        Left(SessionExpired) 
      }
      case Some(s) => {
        Right(s)
      }
    }
    res
  }
  def updateSession(session:HttpSession,updateFunc:AuthSession=>AuthSession):Either[Exception,AuthSession] = {
    sessionStore.get(session.getId).map(s => {
      val updatedSession = updateFunc(s)
      sessionStore.update(session.getId,updatedSession)
      Right(updatedSession)
    }).getOrElse({
      val newSession = updateFunc(EmptyAuthSession(session))
      sessionStore.update(session.getId,newSession)
      Right(newSession)
    })
  }
}

object FilterAuthenticators {
  //var authenticators:List[FilterAuthenticator[_]] = List.empty[FilterAuthenticator[_]]
  var authenticators:List[FilterAuthenticator] = List(new UsernameSettingForm)//FormAuthenticator)

  def passToAuthenticators(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    authenticators.find(_.shouldHandle(authSession,req,session)).map(_.handle(authSession,req,res,session)).getOrElse({
      refuseAuthentication(authSession,req,res,session)
    })
  }
  def refuseAuthentication(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    res.sendError(403,"forbidden, and no available authenticators willing to authenticate this request")
    false
  }
  def requestNewAuthenticator(req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    authenticators match {
      case List(auth) => {
        LowLevelSessionStore.updateSession(session,(s:AuthSession) => {
          val newAuthSession:InProgressAuthSession = auth.generateStore(EmptyAuthSession(session),req)
          newAuthSession
        })
        true
      }
      case _ => {
        throw new Exception("not the right number of authenticators")
        false
      }
    }
  }
}

class LoggedInFilter extends Filter {

  val exclusions:List[HttpServletRequest => Boolean] = List(
    (r) => {
      val path = r.getRequestURI
      path.startsWith("/static/") || path.startsWith("/favicon.ico")
    }
  )
  val rejectWhenNotAuthenticated:List[HttpServletRequest => Boolean] = List(
    (r) => {
      val path = r.getRequestURI
      path.startsWith("/comet_request/") || path.startsWith("/ajax_request/")
    }
  )

  protected var config:Map[String,String] = Map.empty[String,String]

  override def init(filterConfig:FilterConfig) = {
    config = Map(filterConfig.getInitParameterNames.map(_.toString).toList.map(n => (n,filterConfig.getInitParameter(n))):_*)
  }
  override def destroy = {}

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val httpReq = req.asInstanceOf[HttpServletRequest]
    if (exclusions.exists(_.apply(httpReq))){
      chain.doFilter(req,res)
    } else {
      val httpResp = res.asInstanceOf[HttpServletResponse]
      val Session = httpReq.getSession

      LowLevelSessionStore.getValidSession(Session) match {
        case Left(e) => {
          if (rejectWhenNotAuthenticated.exists(_.apply(httpReq))){
            httpResp.sendError(403,"forbidden.  please authenticate")
          } else {
            e match {
              case LowLevelSessionStore.SessionNotFound => {
                if (FilterAuthenticators.requestNewAuthenticator(httpReq,httpResp,Session)){
                  doFilter(req,res,chain)
                }
              }
              case other => {
                httpResp.sendError(500,e.getMessage)
              }
            }
          }
        }
        case Right(s) => {
          s match {
            case HealthyAuthSession(Session,request,username,groups,attrs) => { //let the request through 
              completeAuthentication(httpReq,httpResp,Session,username,groups,attrs)
              request.map(originalReq => {
                /*
                 // not sure whether this is necessary - I think the implementation will turn out that the req has only the sessionId attribute, and it looks up in the container's sessionStore, so if I've updated the session has the sessionId the original req has, then it should apply to the original req I've deferred.
                val originalSession = originalReq.getSession()
                originalSession.setAttribute("user",Session.getAttribute("user"))
                */
                LowLevelSessionStore.updateSession(Session,s => HealthyAuthSession(Session,None,username)) // clear the rewrite
                chain.doFilter(originalReq,httpResp)
                //super.doFilter(originalReq,httpResp,chain)
              }).getOrElse({
                chain.doFilter(httpReq,httpResp)
                //super.doFilter(httpReq,httpResp,chain)
              })
            }
            case ipas:InProgressAuthSession => {
              if (FilterAuthenticators.passToAuthenticators(ipas,httpReq,httpResp,Session)){
                doFilter(req,res,chain) //the filterAuthenticator has hopefully applied some change to the sessionState without committing the response, and would like the process repeated.  Otherwise the response must've been committed
              }
            }
          }
        }
      }
    }
  }
  protected def completeAuthentication(req:HttpServletRequest,res:HttpServletResponse,session:HttpSession,user:String,groups:List[Tuple2[String,String]] = Nil,attrs:List[Tuple2[String,String]] = Nil):Unit = {
//    req.login(user,"") // it claims this isn't available, but it's definitely in the javadoc, so I'm not yet sure what's happening here.
    session.setAttribute("authenticated",true)
    session.setAttribute("user",user)
    session.setAttribute("userGroups",groups)
    session.setAttribute("userAttributes",attrs)
    //req.authenticate(res)
    //req.login(user,"")
    println("User: %s".format(req.getRemoteUser))
  }
}

trait FilterAuthenticator {
  protected def freezeRequest(req:HttpServletRequest):HttpServletRequest = new CloneableHttpServletRequestWrapper(new CachingHttpServletRequestWrapper(req)).duplicate
  def generateStore(authSession:AuthSession,req:HttpServletRequest):InProgressAuthSession
  def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = false
  def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = true //the boolean represents whether the req should then be passed down the chain
}


class FormAuthenticator(fields:List[String],validateFunc:Map[String,String]=>Option[Tuple3[String,List[Tuple2[String,String]],List[Tuple2[String,String]]]]) extends FilterAuthenticator {
  case class FormInProgress(override val session:HttpSession,override val originalRequest:HttpServletRequest) extends InProgressAuthSession(session,originalRequest)
  override def generateStore(authSession:AuthSession,req:HttpServletRequest):InProgressAuthSession = FormInProgress(authSession.session,freezeRequest(req))
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case FormInProgress(s,or) => true
    case _ => false
  }
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    (authSession,req.getMethod.toUpperCase.trim,req.getRequestURL) match {
      case (fip@FormInProgress(s,or),"POST",_) => {
        validateForm(fip,req,res)
      }
      case other => {
        generateForm(res,req)
      }
    }
  }
  def generateForm(res:HttpServletResponse,req:HttpServletRequest):Boolean = {
    val writer = res.getWriter
    writer.println(
"""<html>
  <form method="post" action="%s">
    %s
    <input type="submit" value"login"/>
  </form>
</html>""".format(req.getRequestURI,fields.map(f => {
  """<label for="%s">%s</label>
     <input name="%s" type="text"/>""".format(f,f,f)
}).mkString(""))
    )
    res.setContentType("text/html")
    res.setStatus(200)
    false
  }
  def validateForm(authSession:FormInProgress,req:HttpServletRequest,res:HttpServletResponse):Boolean = {
    validateFunc(Map(fields.map(f => (f,req.getParameter(f))):_*)).map(userTup => {
      LowLevelSessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,Some(authSession.originalRequest),userTup._1,userTup._2,userTup._3))
      true
    }).getOrElse({
      generateForm(res,authSession.originalRequest)
      false
    })
  }
}

class UsernameSettingForm extends FormAuthenticator(List("username"),(m:Map[String,String]) => {
  m.get("username").filterNot(s => s == null || s.trim == "").map(username => (username,Nil,Nil))
})

import com.metl.saml._
import net.liftweb.util._
import net.liftweb.common._

class SAMLFilterAuthenticator(samlConfiguration:SAMLConfiguration) extends FilterAuthenticator with Logger {
  import org.pac4j.core.client.RedirectAction
  import org.pac4j.core.context._
  import org.pac4j.core.exception.RequiresHttpAction
  import org.pac4j.saml.client.Saml2Client
  import org.pac4j.saml.profile.Saml2Profile
  import scala.collection.JavaConverters._
  import scala.collection.immutable.List
  import scala.collection.mutable.{Map => scalaMutableMap}
  case class SAMLProgress(override val session:HttpSession,override val originalRequest:HttpServletRequest) extends InProgressAuthSession(session,originalRequest)
  override def generateStore(authSession:AuthSession,req:HttpServletRequest):InProgressAuthSession = SAMLProgress(authSession.session,freezeRequest(req))
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case SAMLProgress(s,or) => true
    case _ => false
  }
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    (authSession,req.getMethod.toUpperCase.trim,req.getRequestURI) match {
      case (sp@SAMLProgress(s,or),_,cbu) if isRequestForSAMLCallbackUrl(req) => handleSAMLResponseCallback(sp,req,res,session)
      case (sp@SAMLProgress(s,or),_,_) => sendSAMLRequest(sp,req,res,session)
      case _ => {
        res.sendError(403,"forbidden\r\nsaml authentication still in progress")
        false
      }
    }
  }
  
  class ServletWebContext(req:HttpServletRequest,resp:HttpServletResponse) extends WebContext {
    override def setResponseHeader(name: String, value: String) = resp.setHeader(name,value)
    override def setResponseStatus(code: Int) = resp.setStatus(code)
    override def getRequestParameters: java.util.Map[String, Array[String]] = req.getParameterMap.asInstanceOf[java.util.Map[String,Array[String]]]
    override def getRequestHeader(name: String): String = req.getHeader(name)
    override def writeResponseContent(content: String) = resp.getWriter.write(content)
    override def getServerName: String = req.getServerName
    override def getRequestParameter(name: String): String = req.getParameter(name)
    override def getRequestMethod: String = req.getMethod

    override def setSessionAttribute(name: String, value: scala.AnyRef): Unit = req.getSession.setAttribute(name,value)
    override def getServerPort: Int = req.getServerPort
    override def getSessionAttribute(name: String): AnyRef = req.getSession.getAttribute(name)
    override def getScheme: String = req.getScheme
    override def getFullRequestURL: String = req.getRequestURL.toString
  }

  protected def redirectHome(resp:HttpServletResponse) = resp.sendRedirect("/")

  protected val samlClient: Saml2Client = getSaml2Client(samlConfiguration)

  protected def internalServerErrorResponseWithUnknownError(resp:HttpServletResponse,message:String = "unknown error"):Unit = resp.sendError(500,message)

  protected def liftWebContext(req:HttpServletRequest,resp:HttpServletResponse):WebContext = new ServletWebContext(req,resp)

  protected def getSaml2Client(samlConfiguration: SAMLConfiguration):Saml2Client = {
    val saml2Client: Saml2Client = new Saml2Client {

      // Override method "getStateParameter" to retrieve RelayState from the current request
      /*
      override def getStateParameter(webContext: WebContext): String = {

        val requestUri = CurrentReq.value.request.uri
        val boxOfQueryString = CurrentReq.value.request.queryString

        val relayState = requestUri + boxOfQueryString.map("?%s".format(_)).getOrElse("")
        relayState
      }
      */
    }

    samlConfiguration.optionOfKeyStoreInfo match {
      case Some(keyStoreInfo: keyStoreInfo) => {
        saml2Client.setKeystorePath(keyStoreInfo.keystorePath)
        saml2Client.setKeystorePassword(keyStoreInfo.keystorePassword)
        saml2Client.setPrivateKeyPassword(keyStoreInfo.privateKeyPassword)
      }
      case _ => {}
    }

    val serverBaseUrl =
      if (samlConfiguration.serverPort == 80) {
        "%s://%s".format(samlConfiguration.serverScheme, samlConfiguration.serverName)
      } else {
        "%s://%s:%s".format(samlConfiguration.serverScheme, samlConfiguration.serverName, samlConfiguration.serverPort)
      }

    saml2Client.setIdpMetadataPath(samlConfiguration.idpMetaDataPath)
    saml2Client.setCallbackUrl("%s/%s".format(serverBaseUrl, samlConfiguration.callBackUrl))

    samlConfiguration.optionOfSettingsForADFS match {
      case Some(settingsForADFS: SettingsForADFS) => saml2Client.setMaximumAuthenticationLifetime(settingsForADFS.maximumAuthenticationLifetime)
      case _ => {}
    }

    samlConfiguration.optionOfEntityId.foreach(entityId => {
      saml2Client.setIdpEntityId(entityId)
    })

    saml2Client
  }

  def getSAMLClient = samlClient
  def isRequestForSAMLCallbackUrl(request: HttpServletRequest): Boolean = request.getRequestURI.startsWith(samlConfiguration.callBackUrl)

  def sendSAMLRequest(authSession:InProgressAuthSession,request: HttpServletRequest, resp: HttpServletResponse,session:HttpSession): Boolean  = { 
    /***********************************
    ************************************
    *******Sample SAML Request:*********
    ************************************
    *8*********************************/

//  <?xml version="1.0" encoding="UTF-8"?>
//  <saml2p:AuthnRequest
//    xmlns:saml2p="urn:oasis:names:tc:SAML:2.0:protocol"
//    AssertionConsumerServiceURL="http://localhost:8081/saml-callback"
//    Destination="https://login-qa.monash.edu/adfs/ls/"
//    ForceAuthn="false"
//    ID="_48899f2c16f845e9354fdf282bbb928a"
//    IsPassive="false"
//    IssueInstant="2015-03-25T06:08:50.379Z"
//    ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
//    ProviderName="pac4j-saml"
//    Version="2.0"
//  >
//    <saml2:Issuer xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion">
//      Monash Default SAML Service Provider
//    </saml2:Issuer>
//  </saml2p:AuthnRequest>

    (tryo { samlClient.getRedirectAction(liftWebContext(request,resp), true, false) }).map(redirectAction => {
      redirectAction.getType match {
        case RedirectAction.RedirectType.REDIRECT => {
          resp.sendRedirect(redirectAction.getLocation)
        }
        case RedirectAction.RedirectType.SUCCESS => {
          resp.getWriter.write(redirectAction.getContent)
          resp.setStatus(200)
        }
        case other => {
          resp.sendError(400,"bad redirect type")
        }
      }

    }).openOr({
      resp.sendError(500,"unknown error during SAML request sending")
    })
    false
  }

  def handleSAMLResponseCallback(authSession:InProgressAuthSession,request: HttpServletRequest, resp:HttpServletResponse,session:HttpSession): Boolean = {
    try {
      val context = liftWebContext(request,resp)
      val credentials = samlClient.getCredentials(context)
      val userProfile: Saml2Profile = samlClient.getUserProfile(credentials, context)

      val rawAttributes = userProfile.getAttributes
      debug("raw saml attrs: %s".format(rawAttributes))
      val attributes:List[Tuple2[String,String]] = rawAttributes.asScala.toList.flatMap {
        case ( name: String, arrayList: java.util.ArrayList[String] ) => {
          debug("decoding attribute: %s as an arrayList[String]: %s".format(name,arrayList))
          arrayList.toArray.toList.map(arr => ( name, arr.toString ) )
        }
        case ( name: String, arrayList: java.util.ArrayList[Object] ) => {
          debug("decoding attribute: %s as an arrayList[Object]: %s".format(name,arrayList))
          arrayList.toArray.toList.map(arr => ( name, arr.toString ) )
        }
        case ( name: String, str: String ) => {
          debug("decoding attribute: %s as a string: %s".format(name,str))
          List(( name, str ))
        }
      }.toList

      debug("all attrs for '%s': %s\r\nroles: %s".format(userProfile.getId,attributes,userProfile.getRoles))

      // This is where we might want to adjust the LiftAuthStateData to
      // support the attributes and groups returned by the SAML packet.
      val groups = attributes.flatMap(attr => {
        samlConfiguration.eligibleGroups.get(attr._1).map(groupType => (groupType,attr._2))
      }).toList
      val transformedAttrs = attributes.flatMap(attr => {
        samlConfiguration.attributeTransformers.get(attr._1).map(attrName => (attrName,attr._2))
      }).toList

      debug("firing onSuccess")
      LowLevelSessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,Some(authSession.originalRequest),userProfile.getId,groups,attributes ::: transformedAttrs))
      true
    } catch {
      case e:Exception => {
        resp.sendError(500,e.getMessage) //change this later
        false
      }
    }
  }
}

