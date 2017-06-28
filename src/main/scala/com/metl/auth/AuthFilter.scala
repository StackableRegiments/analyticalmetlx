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
import org.apache.commons.httpclient.params.HttpMethodParams

import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.util.Helpers._
import scala.collection.JavaConverters._
import java.security.Principal

case class MeTLPrincipal(authenticated:Boolean,username:String,groups:List[Tuple2[String,String]],attrs:List[Tuple2[String,String]]) extends Principal {
  override def getName:String = username
}
class AuthenticedHttpServletRequestWrapper(request:HttpServletRequest,principal:MeTLPrincipal) extends HttpServletRequestWrapper(request){
  override def getRemoteUser:String = {
    if (principal.authenticated){
      principal.username
    } else {
      null
    }
  }
  override def getUserPrincipal:Principal = principal
  override def getAuthType:String = "MeTL"
}

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
            val rawKey :: values = line.split("=").toList
            val key = net.liftweb.util.Helpers.urlDecode(rawKey)
            val value:String = net.liftweb.util.Helpers.urlDecode(values.mkString("="))
            val priorValues = qpMap.get(key).map(_.toList).getOrElse(List.empty[String])
            qpMap = qpMap.updated(key,(value :: priorValues).toArray)
          })
        }
        case Some("multipart/form-data") => {
          //got to decode multipart data next, which includes decoding files, oh what fun.  there must be a library about which does all of this neatly.
          //looks like Lift does this once we pass it on, so that's fine.
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
  lazy val cookies = request.getCookies match {
    case null => Nil
    case Array() => Nil
    case other => other.toList
  }
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

abstract class AuthSession(val session:HttpSession) {
  def getStoredRequests:Map[String,HttpServletRequest] = Map.empty[String,HttpServletRequest]
}

case class HealthyAuthSession(override val session:HttpSession,storedRequests:Map[String,HttpServletRequest] = Map.empty[String,HttpServletRequest],username:String,groups:List[Tuple2[String,String]] = Nil,attrs:List[Tuple2[String,String]] = Nil) extends AuthSession(session){
  override def getStoredRequests = storedRequests
}
abstract class InProgressAuthSession(override val session:HttpSession,val storedRequests:Map[String,HttpServletRequest],val authenticator:String) extends AuthSession(session){
  override def getStoredRequests = storedRequests
}
case class EmptyAuthSession(override val session:HttpSession) extends AuthSession(session)

class LowLevelSessionStore {
  val SessionNotFound = new Exception("session not found")
  val SessionExpired = new Exception("session expired")
  val UnknownAttribute = new Exception("attribute returned unknown value")
  val storeAttr = "authSessionState"
  def getValidSession(session:HttpSession):Either[Exception,AuthSession] = {
    session.getAttribute(storeAttr) match {
      case null => Left(SessionNotFound)
      case a:AuthSession => Right(a)
      case _ => Left(UnknownAttribute)
    }
  }
  def updateSession(session:HttpSession,updateFunc:AuthSession=>AuthSession):Either[Exception,AuthSession] = {
    session.getAttribute(storeAttr) match {
      case s:AuthSession => {
        val updatedSession = updateFunc(s)
        session.setAttribute(storeAttr,updatedSession)
        Right(updatedSession)
      }
      case other => {
        val newSession = updateFunc(EmptyAuthSession(session))
        session.setAttribute(storeAttr,newSession)
        Right(newSession)
      }
    }
  }
}

trait HttpReqUtils {
  protected val reqIdParameter = "replayRequest"
  protected def getIdFromReq(req:HttpServletRequest):String = {
    getReqId(req).getOrElse(req.getParameter(reqIdParameter))
  }
  protected def generateIdForReq(req:HttpServletRequest):String = {
    nextFuncName
  }
  protected def freezeRequest(req:HttpServletRequest):HttpServletRequest = new CloneableHttpServletRequestWrapper(new CachingHttpServletRequestWrapper(req)).duplicate
  protected def getOriginalRequest(authSession:AuthSession,req:HttpServletRequest):HttpServletRequest = {
    authSession.getStoredRequests.get(getIdFromReq(req)).getOrElse(req)
  }
  protected def updatedStore(authSession:AuthSession,req:HttpServletRequest):Map[String,HttpServletRequest] = {
    val reqId = generateIdForReq(req)
    embedReqId(req,reqId)
    authSession.getStoredRequests.updated(reqId,freezeRequest(req))
  }
  protected def updatedStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):Map[String,HttpServletRequest] = {
    authSession.getStoredRequests ++ reqs
  }
  protected def wrapWithReqId(req:HttpServletRequest,url:String):String = {
    getReqId(req).map(reqId => {
      new org.apache.http.client.utils.URIBuilder(url).addParameter(reqIdParameter,reqId).build().toString
    }).getOrElse(url)
  }
  protected def getReqId(req:HttpServletRequest):Option[String] = {
    req.getAttribute(reqIdParameter) match {
      case null => None
      case s:String if s.length > 0 => Some(s)
      case other => Some(other.toString)
    }
  }
  protected def embedReqId(req:HttpServletRequest,id:String):HttpServletRequest = {
    req.setAttribute(reqIdParameter,id)
    req
  }
  protected def possiblyGenerateStoredRequest(req:HttpServletRequest):Option[Tuple2[String,HttpServletRequest]] = {
    val paramValue = getIdFromReq(req)
    getReqId(req) match {
      case Some(str) => None
      case None if paramValue == null || paramValue.length == 0 => {
        val reqId = generateIdForReq(req)
        val frozenReq = freezeRequest(req)
        Some((reqId,frozenReq))
      }
      case None => {
        embedReqId(req,paramValue)
        None
      }
    }
  }
}

class LoggedInFilter extends Filter with HttpReqUtils {
  import scala.xml._
  protected val sessionStore = new LowLevelSessionStore
  protected object FilterAuthenticators {
    var authenticator:Option[FilterAuthenticator] = None

    def passToAuthenticators(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
      authenticator.filter(_.shouldHandle(authSession,req,session)).map(_.handle(authSession,req,res,session)).getOrElse({
        refuseAuthentication(authSession,req,res,session)
      })
    }
    def refuseAuthentication(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
      res.sendError(403,"forbidden, and no available authenticator willing to authenticate this request")
      false
    }
    def requestNewAuthenticator(req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
      authenticator match {
        case Some(auth) => {
          sessionStore.updateSession(session,(s:AuthSession) => {
            val newId = generateIdForReq(req)
            val newAuthSession:InProgressAuthSession = auth.generateStore(EmptyAuthSession(session),s.getStoredRequests.updated(newId,freezeRequest(req)))
            embedReqId(req,newId)
            newAuthSession
          })
          true
        }
        case _ => {
          throw new Exception("no configured authenticator")
          false
        }
      }
    }
  }

  protected var exclusions:List[HttpServletRequest => Boolean] = Nil
  protected var rejectWhenNotAuthenticated:List[HttpServletRequest => Boolean] = Nil

  override def init(filterConfig:FilterConfig) = {
    val filterConfigMap:Map[String,String] = Map(filterConfig.getInitParameterNames.map(_.toString).toList.map(n => (n,filterConfig.getInitParameter(n))):_*)
    val environmentVariables:Map[String,String] = System.getenv.asScala.toMap
    def trimSystemProp(in:String):Option[String] = {
      try {
        var value = in.trim
        if (value.startsWith("\"")){
          value = value.drop(1)
        }
        if (value.endsWith("\"")){
          value = value.reverse.drop(1).reverse
        }
        Some(value)
      } catch {
        case e:Exception => None
      }
    }
    def getProp(systemEnvName:String,javaPropName:String):Option[String] = {
      val envProp = environmentVariables.get(systemEnvName).filterNot(v => v == null || v == "").map(v => trimSystemProp(v))
      envProp.getOrElse({
        val sysProp = net.liftweb.util.Props.get(javaPropName).map(v => Full(v)).openOr(Full(System.getProperty(javaPropName)))
        sysProp
      })
    }
    for (
      configPathVariable <- filterConfigMap.get("configPathVariableName");
      configPath <- getProp(configPathVariable,configPathVariable);
      configRoot <- tryo(XML.load(configPath))
    ) yield {
      def predicateFunc(e:Elem):Option[HttpServletRequest=>Boolean] = {
        e.label match {
          case "requestUriStartsWith" => {
            (e \\ "@value").headOption.map(_.text).map(prefix => {
              (r:HttpServletRequest) => {
                val value:String = r.getRequestURI
                val result = value.startsWith(prefix)
                result
              }
            })
          }
          case "requestUriEndsWith" => {
            (e \\ "@value").headOption.map(_.text).map(suffix => {
              (r:HttpServletRequest) => {
                r.getRequestURI.endsWith(suffix)
              }
            })
          }
          case _ => None
        }
      }
      def getChildElemsFrom(in:NodeSeq):List[Elem] = {
        val out = in match {
          case e:Elem => e.child.toList.flatMap{
            case el:Elem => List(el)
            case _ => Nil
          }
          case _ => Nil
        }
        out
      }
      exclusions = (configRoot \\ "serverConfiguration" \\ "authenticationConfiguration" \\ "exclusions").flatMap(exclusions => getChildElemsFrom(exclusions).flatMap(childElem => predicateFunc(childElem))).toList
      rejectWhenNotAuthenticated = (configRoot \\ "serverConfiguration" \\ "authenticationConfiguration" \\ "rejectWhenNotAuthenticated").flatMap(rejections => getChildElemsFrom(rejections).flatMap(childElem => predicateFunc(childElem))).toList

      FilterAuthenticators.authenticator = Some(new MultiAuthenticator(sessionStore,(configRoot \\ "serverConfiguration" \\ "authentication").theSeq.flatMap{
        case e:Elem => e.child.toList
        case _ => Nil
      }.flatMap(elem => {
        val name = (elem \\ "@name").headOption.map(_.text).getOrElse("unlabelled authenticator")
        val imageUrl = (elem \\ "@imageUrl").headOption.map(_.text).getOrElse("")
        val prefix = (elem \\ "@prefix").headOption.map(_.text).getOrElse("")
        (elem match {
          case n:Elem if (n.label == "saml") => {
            for (
              serverScheme <- (n \\ "serverScheme").headOption.map(_.text);
              serverName <- (n \\ "serverName").headOption.map(_.text);
              serverPort <- (n \\ "serverPort").headOption.map(_.text.toInt);
              samlCallbackUrl <- (n \\ "callbackUrl").headOption.map(_.text);
              idpMetadataFileName <- (n \\ "idpMetadataFileName").headOption.map(_.text);
              optionOfKeyStoreInfo = {
                for (
                  keystorePath <- (n \\ "keystorePath").headOption.map(_.text);
                  keystorePassword <- (n \\ "keystorePassword").headOption.map(_.text);
                  privateKeyPassword <- (n \\ "keystorePrivateKeyPassword").headOption.map(_.text)
                ) yield {
                  keyStoreInfo(keystorePath,keystorePassword,privateKeyPassword)
                }
              };
              optionOfSettingsForADFS = {
                for (
                  maximumAuthenticationLifetime <- (n \\ "maximumAuthenticationLifetime").headOption.map(_.text.toInt)
                ) yield {
                  SettingsForADFS(maximumAuthenticationLifetime = maximumAuthenticationLifetime)
                }
              };
              attrTransformers = (n \\ "informationAttributes" \\ "informationAttribute").flatMap(elem => {
                for (
                  attrName <- (elem \\ "@samlAttribute").headOption.map(_.text);
                  attrValue <- (elem \\ "@attributeType").headOption.map(_.text)
                ) yield {
                  (attrName,attrValue)
                }
              });
              groupMap = (n \\ "eligibleGroups" \\ "eligibleGroup").flatMap(group => {
                for (
                  attrName <- (group \\ "@samlAttribute").headOption.map(_.text);
                  groupType <- (group \\ "@groupType").headOption.map(_.text)
                ) yield {
                  (attrName,groupType)
                }
              })
            ) yield {
              new SAMLFilterAuthenticator(sessionStore,SAMLConfiguration(
                idpMetaDataPath = idpMetadataFileName,
                serverScheme = serverScheme,
                serverName = serverName,
                serverPort = serverPort.toInt,
                callBackUrl = samlCallbackUrl,
                optionOfSettingsForADFS = optionOfSettingsForADFS,
                eligibleGroups = Map(groupMap:_*),
                attributeTransformers = Map(attrTransformers:_*),
                optionOfKeyStoreInfo = optionOfKeyStoreInfo
              ))
            }
          }
          case n:Elem if (n.label == "mock") => Some(new UsernameSettingForm(sessionStore,n.child.map(_.text).mkString("")))
          case n:Elem if (n.label == "openIdConnect") => {
            for (
              googleClientId <- (n \\ "@clientId").headOption.map(_.text);
              googleAppDomainName = (n \\ "@appDomain").headOption.map(_.text);
              beforeHtml = (n \\ "beforeHtml").headOption.map(_.text);
              afterHtml = (n \\ "afterHtml").headOption.map(_.text)
            ) yield {
              new OpenIdConnectAuthenticator(sessionStore,googleClientId,googleAppDomainName,beforeHtml.getOrElse(""),afterHtml.getOrElse(""))
            }
          }
          case n:Elem if (n.label == "cas") => {
            for (
              loginUrl <- (n \\ "@loginUrl").headOption.map(_.text);
              validateUrl <- (n \\ "@validateUrl").headOption.map(_.text)
            ) yield {
              new CASFilterAuthenticator(sessionStore,loginUrl,validateUrl)
            }
          }
          case n:Elem if (n.label == "basicInMemory") => {
            for (
              realm <- (n \\ "@realm").headOption.map(_.text);
              creds = (n \\ "credential").flatMap(cNode => {
                for (
                  username <- (cNode \\ "@username").headOption.map(_.text);
                  password <- (cNode \\ "@password").headOption.map(_.text)
                ) yield {
                  (username,password)
                }
              })
            ) yield {
              new TimePeriodInMemoryBasicAuthenticator(sessionStore,realm,Map(creds:_*))
            }
          }
          case _ => None
        }).map(auth => DescribedAuthenticator(name,imageUrl,prefix,auth))
      }).toList,""))
    }
  }
  override def destroy = {}

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val httpReq = req.asInstanceOf[HttpServletRequest]
    if (exclusions.exists(_.apply(httpReq))){
      chain.doFilter(req,res)
    } else {
      val httpResp = res.asInstanceOf[HttpServletResponse]

      val Session = httpReq.getSession
      sessionStore.getValidSession(Session) match {
        case Left(e) => {
          if (rejectWhenNotAuthenticated.exists(_.apply(httpReq))){
            httpResp.sendError(403,"forbidden.  please authenticate")
          } else {
            e match {
              case sessionStore.SessionNotFound => {
                if (FilterAuthenticators.requestNewAuthenticator(httpReq,httpResp,Session)){
                  doFilter(req,res,chain)
                } else {
                  httpResp.sendError(500,"unable to initialize authenticator")
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
            case has@HealthyAuthSession(Session,requests,username,groups,attrs) => { //let the request through
              val storedReqId = getIdFromReq(httpReq)
              requests.get(storedReqId).map(storedReq => {
                sessionStore.updateSession(Session,s => HealthyAuthSession(Session,requests - storedReqId,username,groups,attrs)) // clear the rewrite
                //sessionStore.updateSession(Session,s => HealthyAuthSession(Session,Map.empty[String,HttpServletRequest],username,groups,attrs)) // clear the rewrite
                val authedReq = completeAuthentication(storedReq,httpResp,Session,username,groups,attrs)
                chain.doFilter(authedReq,httpResp)
              }).getOrElse({
                val authedReq = completeAuthentication(httpReq,httpResp,Session,username,groups,attrs)
                chain.doFilter(authedReq,httpResp)
              })
            }
            case ipas:InProgressAuthSession => {
              if (rejectWhenNotAuthenticated.exists(_.apply(httpReq))){
                httpResp.sendError(403,"forbidden.  please authenticate")
              } else if (FilterAuthenticators.passToAuthenticators(ipas,httpReq,httpResp,Session)){
                doFilter(req,res,chain) //the filterAuthenticator has hopefully applied some change to the sessionState without committing the response, and would like the process repeated.  Otherwise the response must've been committed
              }
            }
          }
        }
      }
    }
  }
  protected def completeAuthentication(req:HttpServletRequest,res:HttpServletResponse,session:HttpSession,user:String,groups:List[Tuple2[String,String]] = Nil,attrs:List[Tuple2[String,String]] = Nil):HttpServletRequest = {
//    req.login(user,"") // it claims this isn't available, but it's definitely in the javadoc, so I'm not yet sure what's happening here.
    session.setAttribute("authenticated",true)
    session.setAttribute("user",user)
    session.setAttribute("userGroups",groups)
    session.setAttribute("userAttributes",attrs)
    //res.setHeader("REMOTE_USER",user) //I was hoping that this would drive the logger's remoteUser behaviour, but it doesn't appear to.
    val principal = MeTLPrincipal(true,user,groups,attrs)
    /*  //no, this entire block won't work.  Jetty now (didn't used to, and doesn't when embedded) correctly implements a particular JSR which specifies that all server/container libraries should be hidden from the servlet, and it does so by subtly changing the class of the outer servlet code from the libraries inside, such that the org.eclipse.jetty.server.Request is not the same type as the one given to us by the server.
    try { //Jetty specific code for setting the remoteUser, though the final pattern match is probably an appropriate place to handle other containers as well.
      var baseReq:ServletRequest = req
      var finished = false
      while (!finished){
        baseReq match {
          case rw:HttpServletRequestWrapper => {
            baseReq = rw.getRequest
          }
          case r => {
            finished = true
          }
        }
      }
      baseReq match {
        case r:org.eclipse.jetty.server.Request => {
          val userId = new org.eclipse.jetty.security.DefaultUserIdentity(null,principal,null)
          r.setAuthentication(new org.eclipse.jetty.security.UserAuthentication(null,userId))
        }
        case r:ServletRequest if r.getClass.toString == "org.eclipse.jetty.server.Request" => {
          val userId = new org.eclipse.jetty.security.DefaultUserIdentity(null,principal,null)
          (r.asInstanceOf[HttpServletRequest].asInstanceOf[org.eclipse.jetty.server.Request]).setAuthentication(new org.eclipse.jetty.security.UserAuthentication(null,userId))
        }
        case otherReq => {
        }
      }
    } catch {
      case e:Exception => {
      }
    }
    */
    new AuthenticedHttpServletRequestWrapper(req,principal)
  }
}

abstract class FilterAuthenticator(sessionStore:LowLevelSessionStore) extends HttpReqUtils  {
  val identifier = nextFuncName
  protected def getRequestRedirect(req:HttpServletRequest):String = req.getRequestURL.toString
  def getSessionStore = sessionStore
  def generateStore(authSession:AuthSession,req:HttpServletRequest):InProgressAuthSession = {
    generateStore(authSession,updatedStore(authSession,req))
  }
  def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession
  def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = false
  def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = true //the boolean represents whether the req should then be passed down the chain
}

case class DescribedAuthenticator(name:String,imageUrl:String,prefix:String,authenticator:FilterAuthenticator) extends FilterAuthenticator(authenticator.getSessionStore) {
  override val identifier = authenticator.identifier
  override def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession = authenticator.generateStore(authSession,reqs)
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authenticator.shouldHandle(authSession,req,session)
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = authenticator.handle(authSession,req,res,session)
}

class MultiAuthenticator(sessionStore:LowLevelSessionStore,authenticators:List[DescribedAuthenticator],descriptiveHtml:String = "") extends FilterAuthenticator(sessionStore) {
  case class MultiChoiceInProgress(override val session:HttpSession,override val storedRequests:Map[String,HttpServletRequest],choice:Option[DescribedAuthenticator]) extends InProgressAuthSession(session,storedRequests,identifier)
  override def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession = {
    MultiChoiceInProgress(authSession.session,updatedStore(authSession,reqs),None)
  }
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case mcip@MultiChoiceInProgress(s,or,choice) if mcip.authenticator == identifier => true // if this is the one handling it all
    case _ => authenticators.exists(_.authenticator.shouldHandle(authSession,req,session)) 
  }
  override def toString = "MultiAuthenticator(%s)".format(authenticators)
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    (authSession,req.getMethod.toUpperCase,req.getRequestURI.split("/").toList.dropWhile(_ == "")) match {
      case (mcip@MultiChoiceInProgress(session,storedRequests,None),_,_) if authenticators.length == 1 => {
        sessionStore.updateSession(authSession.session,s => mcip.copy(choice = Some(authenticators.head)))
        res.sendRedirect(wrapWithReqId(req,getOriginalRequest(authSession,req).getRequestURL.toString))
        false
      }
      case (mcip@MultiChoiceInProgress(session,storedRequests,None),"GET","choice" :: choice :: Nil) => {
        authenticators.find(_.identifier == choice).map(authenticator => {
          sessionStore.updateSession(authSession.session,s => mcip.copy(choice = Some(authenticator)))
          res.sendRedirect(wrapWithReqId(req,getOriginalRequest(authSession,req).getRequestURL.toString))
          false
        }).getOrElse({
          res.sendRedirect(wrapWithReqId(req,getOriginalRequest(authSession,req).getRequestURL.toString))
          false
        })
      }
      case (mcip@MultiChoiceInProgress(session,storedRequests,Some(describedAuthenticator)),_,_) => {
        sessionStore.updateSession(authSession.session,s => describedAuthenticator.generateStore(authSession,storedRequests))
        true
      }
      case other => {
        authenticators.find(_.shouldHandle(authSession,req,session)).map(describedAuthenticator => {
          val result = describedAuthenticator.handle(authSession,req,res,session)
          sessionStore.getValidSession(session) match {
            case Right(has@HealthyAuthSession(session,storedRequests,user,groups,attrs)) => {
              sessionStore.updateSession(authSession.session,s => has.copy(username = "%s%s".format(describedAuthenticator.prefix,user)))
              true
            }
            case _ => result
          }
        }).getOrElse({
          possiblyGenerateStoredRequest(req).foreach(reqTup => {
            sessionStore.updateSession(authSession.session,s => generateStore(other._1,Map(reqTup._1 -> reqTup._2)))
            embedReqId(req,reqTup._1)
          })
          res.getWriter.write("""<html>
  %s
  %s
  </html>""".format(
              descriptiveHtml,
              authenticators.foldLeft("")((acc,item) => {
                """%s<div class="authenticatorChoice">
    <a href="/choice/%s%s">
      <img class="authenticatorImage" src="%s"/>
      <div class="authenticatorName">%s</div>
    </a>
  </div>""".format(acc,item.identifier,getReqId(req).map(reqId => "?%s=%s".format(reqIdParameter,reqId)).getOrElse(""),item.imageUrl,item.name)
              })
            )
          )
          res.setStatus(200)
          false
        })
      }
    }
  }
}

class FormAuthenticator(sessionStore:LowLevelSessionStore,fields:List[(String,String)],validateFunc:Map[String,String]=>Option[Tuple3[String,List[Tuple2[String,String]],List[Tuple2[String,String]]]],descriptiveHtml:String) extends FilterAuthenticator(sessionStore) {
  case class FormInProgress(override val session:HttpSession,override val storedRequests:Map[String,HttpServletRequest],gensymMap:Map[String,String]) extends InProgressAuthSession(session,storedRequests,identifier)
  override def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession = {
    val securedFormNamesLookup = Map(fields.map(f => (f._1,nextFuncName)):_*)
    FormInProgress(authSession.session,updatedStore(authSession,reqs),securedFormNamesLookup)
  }
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case fip@FormInProgress(s,or,fieldMap) if fip.authenticator == identifier => true
    case _ => false
  }
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    (authSession,req.getMethod.toUpperCase.trim,req.getRequestURL) match {
      case (fip@FormInProgress(s,or,fieldMap),"POST",_) => {
        validateForm(fip,req,res)
      }
      case (fip@FormInProgress(s,or,fieldMap),_,_) => {
        generateForm(fip,res,req)
      }
      case other => {
        res.sendError(500,"session state lost")
        false
      }
    }
  }
  def generateForm(authSession:FormInProgress,res:HttpServletResponse,req:HttpServletRequest):Boolean = {
    possiblyGenerateStoredRequest(req).foreach(reqTup => {
      sessionStore.updateSession(authSession.session,s => {
        s match {
          case fip:FormInProgress => fip.copy(storedRequests = fip.getStoredRequests.updated(reqTup._1,reqTup._2))
          case other => generateStore(authSession,Map(reqTup._1 -> reqTup._2))
        }
      })
      embedReqId(req,reqTup._1)
    })
    res.getWriter.println(
"""<html>
    %s
    <form method="post" action="%s">
      %s
      %s
      <input type="submit" value"login"/>
    </form>
    <script>
    window.onload = function(){
      document.getElementById("username").focus();
    }
    </script>
</html>""".format(
      descriptiveHtml,
      getRequestRedirect(req),
      getReqId(req).map(reqId => """<input type="hidden" name="%s" value="%s"/>""".format(reqIdParameter,reqId)).getOrElse(""),
      fields.flatMap(f => {
        authSession.gensymMap.get(f._1).map(securedName => {
          """<label for="%s">%s</label> <input id="username" name="%s" type="text"/>""".format(securedName,f._2,securedName)
        })
      }).mkString(""))
    )
    res.setContentType("text/html")
    res.setStatus(200)
    false
  }
  def validateForm(authSession:FormInProgress,req:HttpServletRequest,res:HttpServletResponse):Boolean = {
    val fieldMap = Map(fields.flatMap(f => {
      authSession.gensymMap.get(f._1).map(securedName => (f._1,req.getParameter(securedName)))
    }):_*)
    validateFunc(fieldMap).map(userTup => {
      sessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,authSession.storedRequests,userTup._1,userTup._2,userTup._3))
      true
    }).getOrElse({
      generateForm(authSession,res,getOriginalRequest(authSession,req))
    })
  }
}

class UsernameSettingForm(sessionStore:LowLevelSessionStore,descriptiveHtml:String) extends FormAuthenticator(sessionStore,List(("username","Username")),(m:Map[String,String]) => {
  m.get("username").filterNot(s => s == null || s.trim == "").map(username => (username,Nil,Nil))
},descriptiveHtml)

trait CredentialValidator {
  def validateCredentials(req:HttpServletRequest,username:String,password:String):Boolean
}
class LDAPCredentialValidator(ldap: com.metl.ldap.IMeTLLDAP) extends CredentialValidator {
  override def validateCredentials(req:HttpServletRequest,username:String,password:String):Boolean = ldap.authenticate(username,password).getOrElse(false)
}
class MockCredentialValidator extends CredentialValidator {
  override def validateCredentials(req:HttpServletRequest,username:String,password:String):Boolean = true
}

case class SettingsForADFS(
  maximumAuthenticationLifetime:Int
)

case class keyStoreInfo(
  keystorePath:String,
  keystorePassword:String,
  privateKeyPassword:String
)

case class SAMLConfiguration(
  optionOfKeyStoreInfo:Option[keyStoreInfo] = None,
  idpMetaDataPath:String,
  serverScheme:String,
  serverName:String,
  serverPort:Int = 80,
  callBackUrl:String,
  optionOfSettingsForADFS:Option[SettingsForADFS] = None,
  optionOfEntityId:Option[String] = None,
  eligibleGroups:Map[String,String] = Map.empty[String,String],
  attributeTransformers:Map[String,String] = Map.empty[String,String]
)

class SAMLFilterAuthenticator(sessionStore:LowLevelSessionStore,samlConfiguration:SAMLConfiguration) extends FilterAuthenticator(sessionStore) with Logger {
  import org.pac4j.core.client.RedirectAction
  import org.pac4j.core.context._
  import org.pac4j.core.exception.RequiresHttpAction
  import org.pac4j.saml.client.Saml2Client
  import org.pac4j.saml.profile.Saml2Profile
  case class SAMLProgress(override val session:HttpSession,override val storedRequests:Map[String,HttpServletRequest]) extends InProgressAuthSession(session,storedRequests,identifier)
  override def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession = SAMLProgress(authSession.session,updatedStore(authSession,reqs))
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case sp@SAMLProgress(s,or) if sp.authenticator == identifier => true
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
  
  class ServletWebContext(authSession:AuthSession,req:HttpServletRequest,resp:HttpServletResponse) extends WebContext {
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
    override def getFullRequestURL: String = getReqId(req).getOrElse(wrapWithReqId(req,getOriginalRequest(authSession,req).getRequestURL.toString))
  }

  protected def redirectHome(resp:HttpServletResponse) = resp.sendRedirect("/")

  protected val samlClient: Saml2Client = getSaml2Client(samlConfiguration)

  protected def internalServerErrorResponseWithUnknownError(resp:HttpServletResponse,message:String = "unknown error"):Unit = resp.sendError(500,message)

  protected def liftWebContext(authSession:AuthSession,req:HttpServletRequest,resp:HttpServletResponse):WebContext = new ServletWebContext(authSession,req,resp)

  protected def getSaml2Client(samlConfiguration: SAMLConfiguration):Saml2Client = {
    val saml2Client: Saml2Client = new Saml2Client(){
      override def getStateParameter(webContext: WebContext): String = {
        val relayState = webContext.getFullRequestURL
        relayState
      }
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
  def isRequestForSAMLCallbackUrl(request: HttpServletRequest): Boolean = request.getRequestURI.startsWith("/" + samlConfiguration.callBackUrl)

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

    try {
      possiblyGenerateStoredRequest(request).foreach(reqTup => {
        sessionStore.updateSession(authSession.session,s => generateStore(authSession,Map(reqTup._1 -> reqTup._2)))
        embedReqId(request,reqTup._1)
      })
      val redirectAction = samlClient.getRedirectAction(liftWebContext(authSession,request,resp), true, false) 
      redirectAction.getType match {
        case RedirectAction.RedirectType.REDIRECT => {
          val redirectLoc = redirectAction.getLocation
          resp.sendRedirect(redirectLoc)
        }
        case RedirectAction.RedirectType.SUCCESS => {
          resp.getWriter.write(redirectAction.getContent) // this might be bad?
          resp.setStatus(200)
        }
        case other => {
          resp.sendError(400,"bad redirect type")
        }
      }
    } catch {
      case e:Exception => {
        resp.sendError(500,"unknown error during SAML request sending")
      }
    }
    false
  }

  def handleSAMLResponseCallback(authSession:InProgressAuthSession,request: HttpServletRequest, resp:HttpServletResponse,session:HttpSession): Boolean = {
    try {
      val context = liftWebContext(authSession,request,resp)
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

      val groups = attributes.flatMap(attr => {
        samlConfiguration.eligibleGroups.get(attr._1).map(groupType => (groupType,attr._2))
      }).toList
      val transformedAttrs = attributes.flatMap(attr => {
        samlConfiguration.attributeTransformers.get(attr._1).map(attrName => (attrName,attr._2))
      }).toList
      Some(request.getParameter("RelayState")).filterNot(rs => rs == null || rs.length == 0).foreach(relayState => {
        embedReqId(request,relayState)
      })
      sessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,authSession.getStoredRequests,userProfile.getId,groups,attributes ::: transformedAttrs))
      true
    } catch {
      case e:Exception => {
        resp.sendError(500,e.getMessage) //change this later
        false
      }
    }
  }
}

class CASFilterAuthenticator(sessionStore:LowLevelSessionStore,casLoginUrl:String,casServiceValidatePath:String) extends FilterAuthenticator(sessionStore){
  import com.metl.utils._
  case class CASProgress(override val session:HttpSession,override val storedRequests:Map[String,HttpServletRequest]) extends InProgressAuthSession(session,storedRequests,identifier)
  override def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession = CASProgress(authSession.session,updatedStore(authSession,reqs))
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case cp@CASProgress(s,or) if cp.authenticator == identifier => true
    case _ => false
  }
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    (authSession,req.getParameter("ticket")) match {
      case (sp@CASProgress(s,or),ticket) if ticket != null && ticket.trim != "" => validateTicket(sp,req,res,session,ticket)
      case (sp@CASProgress(s,or),_) => redirectToCasBaseUrl(sp,req,res,session)
      case _ => {
        res.sendError(403,"forbidden\r\ncas authentication still in progress")
        false
      }
    }
  }
  protected def getHttpClient: IMeTLHttpClient = Http.getClient
  protected def redirectToCasBaseUrl(sp:CASProgress,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    possiblyGenerateStoredRequest(req).foreach(reqTup => {
      sessionStore.updateSession(sp.session,s => generateStore(sp,Map(reqTup._1 -> reqTup._2)))
      embedReqId(req,reqTup._1)
    })
    res.sendRedirect(casLoginUrl + "?service=%s".format(getService(req)))
    false
  }
  protected def getService(req:HttpServletRequest):String = java.net.URLEncoder.encode(wrapWithReqId(req,req.getRequestURL.toString),"utf-8")
  protected def validateTicket(authSession:CASProgress,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession,ticket:String):Boolean = {
    val verifyUrl = casServiceValidatePath +"?ticket=%s&service=%s".format(ticket,getService(req))
    val casValidityResponse = getHttpClient.getAsString(verifyUrl)
    val casValidityResponseXml = xml.XML.loadString(casValidityResponse)
    (for (
      success <- (casValidityResponseXml \\ "authenticationSuccess").headOption;
      user <- (success \\ "user").headOption.map(_.text)
    ) yield {
      sessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,authSession.getStoredRequests,user,Nil,Nil))
      true
    }).getOrElse({
      res.sendError(403,"forbidden")
      false
    })
  }
}

abstract class BasicAuthenticator(sessionStore:LowLevelSessionStore,realm:String) extends FilterAuthenticator(sessionStore){
  import net.liftweb.util.Helpers._
  case class BasicProgress(override val session:HttpSession,override val storedRequests:Map[String,HttpServletRequest]) extends InProgressAuthSession(session,storedRequests,identifier)
  override def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession = BasicProgress(authSession.session,updatedStore(authSession,reqs))
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case bp@BasicProgress(s,or) if bp.authenticator == identifier => true
    case _ => false
  }
  protected val authHeaderName = "Authorization"
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    req.getHeader(authHeaderName) match {
      case null => challenge(authSession,req,res)
      case authHeader:String => {
        authHeader.split(" ").toList match {
          case List("Basic",b64EncodedCreds) => {
            val creds = new String(base64Decode(b64EncodedCreds),"UTF-8")
            creds.split(":").toList match {
              case List(u,p) if validateCredentials(req,u,p) => {
                sessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,authSession.getStoredRequests,u,Nil,Nil))
                true
              }
              case _ => {
                challenge(authSession,req,res,true)
              }
            }
          }
          case _ => {
            challenge(authSession,req,res)
          }
        }
      }
    }
  }
  protected def reject(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse) = {
    res.sendError(403,"forbidden")
    false
  }
  protected def challenge(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,failedAttempt:Boolean = false) = {
    res.addHeader("WWW-Authenticate","""Basic realm="%s"""".format(realm))
    res.sendError(401,"Access denied")
    false
  }
  protected def validateCredentials(req:HttpServletRequest,username:String,password:String):Boolean
}

abstract class ThrottlingBasicAuthenticator(sessionStore:LowLevelSessionStore,realm:String) extends BasicAuthenticator(sessionStore,realm) {
  case class ThrottledBasicProgress(override val session:HttpSession,override val storedRequests:Map[String,HttpServletRequest],failedAttempts:List[Tuple2[HttpServletRequest,Long]]) extends InProgressAuthSession(session,storedRequests,identifier)
  override def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession = ThrottledBasicProgress(authSession.session,updatedStore(authSession,reqs),Nil)
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case bp@ThrottledBasicProgress(s,or,fa) if bp.authenticator == identifier => true
    case _ => false
  }
  protected def shouldThrottle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse):Boolean
  protected def expireFailedAttempt(req:HttpServletRequest,attempt:Long):Boolean
  override def challenge(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,failedAttempt:Boolean = false) = {
    if (shouldThrottle(authSession,req,res)) {
      super.reject(authSession,req,res)
    } else {
      authSession match {
        case tbp@ThrottledBasicProgress(s,sr,fa) if failedAttempt => {
          val now = new java.util.Date().getTime
          sessionStore.updateSession(authSession.session,s => tbp.copy(failedAttempts = (freezeRequest(req),now) :: tbp.failedAttempts.filterNot(fat => expireFailedAttempt(fat._1,fat._2)).toList))
        }
        case _ => {}
      }
      super.challenge(authSession,req,res)
    }
  }
}
class TimePeriodInMemoryBasicAuthenticator(sessionStore:LowLevelSessionStore,realm:String,userMap:Map[String,String]) extends ThrottlingBasicAuthenticator(sessionStore,realm) {
  protected val maxFailures = 5
  protected val period = 30 * 1000
  override def validateCredentials(req:HttpServletRequest,username:String,password:String):Boolean = userMap.get(username).exists(_ == password)
  override def shouldThrottle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse):Boolean = authSession match {
    case tbp@ThrottledBasicProgress(s,sr,fa) => fa.filterNot(fat => expireFailedAttempt(fat._1,fat._2)).length > maxFailures
    case _ => false
  }
  override def expireFailedAttempt(req:HttpServletRequest,when:Long):Boolean = {
    val now = new java.util.Date().getTime
    when < (now - period)
  }
}

class OpenIdConnectAuthenticator(sessionStore:LowLevelSessionStore,googleClientId:String,googleAppDomainName:Option[String],beforeHtml:String = "",afterHtml:String = "") extends FilterAuthenticator(sessionStore){
  import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken,GoogleIdTokenVerifier}
  import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload
  case class OpenIdConnectProgress(override val session:HttpSession,override val storedRequests:Map[String,HttpServletRequest]) extends InProgressAuthSession(session,storedRequests,identifier)
  override def generateStore(authSession:AuthSession,reqs:Map[String,HttpServletRequest]):InProgressAuthSession = OpenIdConnectProgress(authSession.session,updatedStore(authSession,reqs))
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case cp@OpenIdConnectProgress(s,or) if cp.authenticator == identifier => true
    case _ => false
  }
  protected val EndpointUrl = """/verifyOpenIdConnectToken"""
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    (authSession,req.getRequestURI(),req.getMethod.toLowerCase) match {
      case (sp@OpenIdConnectProgress(s,or),EndpointUrl,"post") => validateResponse(sp,req,res)
      case (sp@OpenIdConnectProgress(s,or),_,_) => renderLoginPage(authSession,req,res)
      case _ => renderLoginPage(authSession,req,res)
    }
  }

  protected val transport = new com.google.api.client.http.javanet.NetHttpTransport()
  protected val jsonFactory = new com.google.api.client.json.jackson2.JacksonFactory()
  protected val verifier:GoogleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
      .setAudience(scala.collection.JavaConversions.asJavaCollection(List(googleClientId)))
      .build()

  protected def renderLoginPage(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse):Boolean = {
    val originalRequestId = getIdFromReq(req)
    val nodes = 
"""
      <html lang="en">
        <head>
          <meta name="google-signin-scope" content="profile email"></meta>
          <meta name="google-signin-client_id" content="%s"></meta>
          <script src="https://apis.google.com/js/platform.js" async="true" defer="true"></script>
        </head>
        <body>
          %s
          <div class="g-signin2" data-onsuccess="onSignIn" data-theme="dark"></div>
          <script>
            function onSignIn(googleUser) {
              var id_token = googleUser.getAuthResponse().id_token;
              console.log("ID Token: " + id_token);
              var form = document.createElement("form");
              form.setAttribute("method","post");
              form.setAttribute("action","%s");
              var tokenField = document.createElement("input");
              tokenField.setAttribute("type","hidden");
              tokenField.setAttribute("name","googleIdToken");
              tokenField.setAttribute("value",id_token);
              form.appendChild(tokenField);
              var originalRequestField = document.createElement("input");
              originalRequestField.setAttribute("type","hidden");
              originalRequestField.setAttribute("name","%s");
              originalRequestField.setAttribute("value","%s");
              form.appendChild(originalRequestField);
              document.body.appendChild(form);
              form.submit();
            };
          </script>
          %s
        </body>
      </html>""".format(googleClientId,beforeHtml,EndpointUrl,reqIdParameter,originalRequestId,afterHtml)
    res.getWriter.write(nodes)
    res.setStatus(200)
    false
  }

  protected def validateResponse(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse):Boolean = {
    (for (
      idTokenString <- Some(req.getParameter("googleIdToken")).filterNot(_ == null);
      //idToken <- tryo(verifier.verify(idTokenString)).filterNot(_ == null);
      idToken <- Some(verifier.verify(idTokenString)).filterNot(_ == null);
      payload:Payload = idToken.getPayload;
      if (googleAppDomainName.map(gadn => payload.getHostedDomain() == gadn).getOrElse(true));
      userId:String = payload.getSubject()
    ) yield {
      //fetch further information with:
      //"https://www.googleapis.com/plus/v1/people/%s".format(userId)
      sessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,authSession.getStoredRequests,userId,Nil,Some(payload.getEmail).filterNot(_ == null).map(e => ("email",e)).toList))
      true
    }).getOrElse(renderLoginPage(authSession,req,res))
  }
}
