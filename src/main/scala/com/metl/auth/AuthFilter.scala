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

import java.io.ByteArrayOutputStream
//import org.apache.commons.httpclient.methods.multipart.MultiPartRequestEntity
import org.apache.commons.httpclient.params.HttpMethodParams

class CloneableHttpServletRequestWrapper(request:HttpServletRequest) extends HttpServletRequestWrapper(request){
  def duplicate:CloneableHttpServletRequestWrapper = {
    val clonedReq = new MockHttpServletRequest(new java.net.URL(request.getRequestURL.toString),request.getContextPath)
    clonedReq.session = request.getSession
    val bytes = getBytes
    clonedReq.contentType = getContentType
    clonedReq.charEncoding = getCharacterEncoding
    clonedReq.method = request.getMethod
    clonedReq.body = bytes
    clonedReq.localPort = request.getLocalPort
    clonedReq.localAddr = request.getLocalAddr
    clonedReq.remoteAddr = request.getRemoteAddr
    clonedReq.servletPath = request.getServletPath
    clonedReq.parameters = request.getParameterNames.toList.flatMap(p => {
      val pn:String = p.toString
      request.getParameterValues(pn).toList.map(pv => (pn,pv.toString))
    })
    clonedReq.headers = Map(request.getHeaderNames.toList.map(h => {
      val hn:String = h.toString
      (hn,request.getHeaders(hn).map(_.toString).toList)
    }):_*)
    clonedReq.cookies = request.getCookies.toList
    clonedReq.attributes = Map(request.getAttributeNames.toList.map(a => {
      val an:String = a.toString
      (an,request.getAttribute(an))
    }):_*)
    new CloneableHttpServletRequestWrapper(clonedReq)
  }
  lazy val getBytes:Array[Byte] = {
    /*try {
      val baos = new ByteArrayOutputStream()
      val mpre = new MultiPartRequestEntity(this.getParts().toArray(),new HttpMethodParams())
      mpre.writeRequest(baos)
      baos.toByteArray()
    } catch {
      case e:Exception => {
        */
        IOUtils.toByteArray(request.getInputStream)
        /*
      }
    }
    */
  }
  def freeze:CloneableHttpServletRequestWrapper = {
    this.finalize
    this
  }
  override def toString:String = {
    "Req(%s,%s)[%s]==[%s]".format(getMethod,getRequestURI,request.getParameterNames.toList.flatMap(p => {
      val pn:String = p.toString
      request.getParameterValues(pn).toList.map(pv => (pn,pv.toString))
    }),new String(getBytes,"UTF-8"))
  }
}

abstract class AuthSession(val session:HttpSession)

case class HealthyAuthSession(override val session:HttpSession,originalRequest:Option[HttpServletRequest] = None,username:String) extends AuthSession(session)
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
        println("expiring session: %s => %s".format(sessionId,s))
        //we're checking this ourselves, because we might hold onto a reference to a session after it should otherwise have been expired, so we'd best check whether it should still be valid.
        sessionStore - sessionId
        Left(SessionExpired) 
      }
      case Some(s) => {
        println("found session: %s => %s".format(sessionId,s))
        Right(s)
      }
    }
    println("got session: %s => %s".format(sessionId,res))
    res
  }
  def updateSession(session:HttpSession,updateFunc:AuthSession=>AuthSession):Either[Exception,AuthSession] = {
    sessionStore.get(session.getId).map(s => {
      val updatedSession = updateFunc(s)
      println("updating session: %s".format(updatedSession))
      sessionStore.update(session.getId,updatedSession)
      Right(updatedSession)
    }).getOrElse({
      val newSession = updateFunc(EmptyAuthSession(session))
      println("inserting session: %s".format(newSession))
      sessionStore.update(session.getId,newSession)
      Right(newSession)
    })
  }
}

object FilterAuthenticators {
  //var authenticators:List[FilterAuthenticator[_]] = List.empty[FilterAuthenticator[_]]
  var authenticators:List[FilterAuthenticator] = List(new FormAuthenticator)

  def passToAuthenticators(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    authenticators.find(_.shouldHandle(authSession,req,session)).map(_.handle(authSession,req,res,session)).getOrElse({
      refuseAuthentication(authSession,req,res,session)
    })
  }
  def refuseAuthentication(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    res.sendError(403,"forbidden, and no available authenticators willing to authenticate this request")
    println("sending 403 forbidden")
    false
  }
  def requestNewAuthenticator(req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    authenticators match {
      case List(auth) => {
        println("generating session state for authenticator")
        LowLevelSessionStore.updateSession(session,(s:AuthSession) => {
          val newAuthSession:InProgressAuthSession = auth.generateStore(EmptyAuthSession(session),req)
          newAuthSession
        })
        true
      }
      case _ => {
        println("not the right number of authenticators")
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

  protected var config:Map[String,String] = Map.empty[String,String]

  override def init(filterConfig:FilterConfig) = {
    config = Map(filterConfig.getInitParameterNames.map(_.toString).toList.map(n => (n,filterConfig.getInitParameter(n))):_*)
    println("MeTLAuthConfig at: %s".format(config))
  }
  override def destroy = {}

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val httpReq = req.asInstanceOf[HttpServletRequest]
    if (exclusions.exists(_.apply(httpReq))){
      chain.doFilter(req,res)
    } else {
      val httpResp = res.asInstanceOf[HttpServletResponse]
      val Session = httpReq.getSession

      val extSess = LowLevelSessionStore.getValidSession(Session)


      extSess match {
        case Left(e) => {
          e match {
            case LowLevelSessionStore.SessionNotFound => {
              println("requesting authenticator")
              if (FilterAuthenticators.requestNewAuthenticator(httpReq,httpResp,Session)){
                doFilter(req,res,chain)
              }
            }
            case other => {
              println("sent error")
              httpResp.sendError(500,e.getMessage)
            }
          }
        }
        case Right(s) => {
          s match {
            case HealthyAuthSession(Session,request,username) => { //let the request through 
              completeAuthentication(httpReq,Session,username)
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
  protected def completeAuthentication(req:HttpServletRequest,session:HttpSession,user:String):Unit = {
//    req.login(user,"") // it claims this isn't available, but it's definitely in the javadoc, so I'm not yet sure what's happening here.
    session.setAttribute("user",user)
  }
}

trait FilterAuthenticator {
  def generateStore(authSession:AuthSession,req:HttpServletRequest):InProgressAuthSession
  def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = false
  def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = true //the boolean represents whether the req should then be passed down the chain
}

case class FormInProgress(override val session:HttpSession,override val originalRequest:HttpServletRequest,formProgress:Option[FormProgress] = None) extends InProgressAuthSession(session,originalRequest)
case class FormProgress(id:String,username:Option[String] = None)

class FormAuthenticator extends FilterAuthenticator {
  override def generateStore(authSession:AuthSession,req:HttpServletRequest):InProgressAuthSession = {
    val fp = FormInProgress(authSession.session,new CloneableHttpServletRequestWrapper(req).duplicate)
    println("generating form progress: %s".format(fp))
    fp
  }
  override def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = authSession match {
    case FormInProgress(s,or,progress) => true
    case _ => false
  }
  override def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    (authSession,req.getMethod.toUpperCase.trim,req.getPathTranslated) match {
      case (FormInProgress(s,or,progress),"GET",_) => {
        (for (
          p <- progress;
          u <- p.username
        ) yield {
          println("switching to healthy because this has completed")
          LowLevelSessionStore.updateSession(authSession.session,s => HealthyAuthSession(s.session,Some(or),u))
          true 
        }).getOrElse(generateForm(res))
      }
      case (fip@FormInProgress(s,or,progress),"POST",_) => {
        println("about to validate form: %s".format(fip))
        validateForm(fip,req,res)
      }
      case other => {
        println("generating form: %s".format(other))
        generateForm(res)
      }
    }
  }
  def generateForm(res:HttpServletResponse):Boolean = {
    val writer = res.getWriter
    println("writing form")
    writer.println(
"""<html>
  <form method="post" action="/">
    <label for="username">username</label>
    <input name="username" type="text"/>
    <input type="submit" value"login"/>
  </form>
</html>"""
    )
    res.setContentType("text/html")
    res.setStatus(200)
    false
  }
  def validateForm(authSession:FormInProgress,req:HttpServletRequest,res:HttpServletResponse):Boolean = {
    val username = req.getParameter("username")
    if (username != null && username != "") {
      println("currentSession: %s".format(authSession))
      LowLevelSessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,Some(authSession.originalRequest),username))
      true
    } else {
      generateForm(res)
      false
    }
  }
}
