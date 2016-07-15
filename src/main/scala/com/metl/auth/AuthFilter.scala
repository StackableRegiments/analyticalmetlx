package com.metl.authenticationFilter

package code.servlets

import java.util.UUID
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse,HttpSession}
import net.liftweb.common.{Empty, Box, Full, Failure}
import net.liftweb.util.Helpers._
import java.util.Date

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
    sessionStore.get(session.getId) match {
      case None => Left(SessionNotFound)
      case Some(s) if isExpired(s) => {
        //we're checking this ourselves, because we might hold onto a reference to a session after it should otherwise have been expired, so we'd best check whether it should still be valid.
        sessionStore - session.getId
        Left(SessionExpired) 
      }
      case Some(s) => Right(s)
    }
  }
  def updateSession(session:HttpSession,updateFunc:AuthSession=>AuthSession):Either[Exception,AuthSession] = {
    sessionStore.get(session.getId).map(s => {
      val updatedSession = updateFunc(s)
      sessionStore.update(session.getId,updatedSession)
      Right(updatedSession)
    }).getOrElse(Left(SessionNotFound))
  }
}

object FilterAuthenticators {
  var authenticators:List[FilterAuthenticator[_]] = List.empty[FilterAuthenticator[_]]

  def passToAuthenticators(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    authenticators.find(_.shouldHandle(authSession,req,session)).map(_.handle(authSession,req,res,session)).getOrElse({
      refuseAuthentication(authSession,req,res,session)
    })
  }
  def refuseAuthentication(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = {
    res.sendError(403,"forbidden, and no available authenticators willing to authenticate this request")
    false
  }
}

class LoggedInFilter extends Filter {

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val httpReq = req.asInstanceOf[HttpServletRequest]
    val httpResp = res.asInstanceOf[HttpServletResponse]
    val Session = httpReq.getSession

    val extSess = LowLevelSessionStore.getValidSession(Session)

    extSess match {
      case Left(e) => {
        e match {
          case LowLevelSessionStore.SessionNotFound => 
          case other => httpResp.sendError(500,e.getMessage)
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
            }).getOrElse({
              chain.doFilter(httpReq, httpResp)
            })
          }
          case ipas:InProgressAuthSession => {
            if (FilterAuthenticators.passToAuthenticators(ipas,httpReq,httpResp,Session)){
              doFilter(req,res,chain) //the filterAuthenticator has hopefully applied some form of change, and would like the process repeated.
            }
          }
        }
      }
    }
  }
  def init(config: FilterConfig): Unit = {
  }
  def destroy(): Unit = {
  }
  protected def completeAuthentication(req:HttpServletRequest,session:HttpSession,user:String):Unit = {
//    req.login(user,"") // it claims this isn't available, but it's definitely in the javadoc, so I'm not yet sure what's happening here.
    session.setAttribute("user",user)
  }
}

trait FilterAuthenticator[T <: InProgressAuthSession] {
  def generateStore(authSession:AuthSession,req:HttpServletRequest):T
  def shouldHandle(authSession:AuthSession,req:HttpServletRequest,session:HttpSession):Boolean = false
  def handle(authSession:AuthSession,req:HttpServletRequest,res:HttpServletResponse,session:HttpSession):Boolean = true //the boolean represents whether the req should then be passed down the chain
}

case class FormInProgress(override val session:HttpSession,override val originalRequest:HttpServletRequest,formProgress:Option[FormProgress] = None) extends InProgressAuthSession(session,originalRequest)
case class FormProgress(id:String,username:Option[String] = None)

class FormAuthenticator extends FilterAuthenticator[FormInProgress] {
  override def generateStore(authSession:AuthSession,req:HttpServletRequest):FormInProgress = FormInProgress(authSession.session,req)
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
          LowLevelSessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,Some(or),u))
          true 
        }).getOrElse(generateForm(res))
      }
      case (fip@FormInProgress(s,or,progress),"POST",_) => {
        validateForm(fip,req,res)
      }
      case _ => generateForm(res)
    }
  }
  def generateForm(res:HttpServletResponse):Boolean = {
    val writer = res.getWriter
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
      LowLevelSessionStore.updateSession(authSession.session,s => HealthyAuthSession(authSession.session,Some(authSession.originalRequest),username))
      true
    } else {
      generateForm(res)
      false
    }
  }
}
