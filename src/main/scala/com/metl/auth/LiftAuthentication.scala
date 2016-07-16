package com.metl.liftAuthenticator

import net.liftweb.http._
import net.liftweb.common._

case class LiftAuthStateData(authenticated:Boolean,username:String,eligibleGroups:Seq[(String,String)],informationGroups:Seq[(String,String)])
object LiftAuthStateDataForbidden extends LiftAuthStateData(false,"forbidden",List.empty[Tuple2[String,String]],List.empty[Tuple2[String,String]]) {}

object liftAuthStateDevelopmentData {
  lazy val all = List(
    LiftAuthStateData(false,"Roger",List(("ou","Unrestricted"),("uid","UnauthenticatedRoger"),("ou","Student"),("enrolledsubject","PSY2011"),("enrolledsubject","ENG2011"),("enrolledsubject","PHS2012"),("enrolledsubject","BIO2011")),List(("givenname","Roger"),("sn","Dodger"),("mail","roger.dodger@monash.edu"),("cn","Rogey"),("initials","RD"),("gender","male"),("personaltitle","mr"))),
    LiftAuthStateData(false,"Jane",List(("ou","Unrestricted"),("uid","UnauthenticatedJane"),("ou","Student"),("enrolledsubject","PSY2011"),("enrolledsubject","ENG2011"),("enrolledsubject","PHS2012"),("enrolledsubject","BIO2011")),List(("givenname","Jane"),("sn","Normal"),("mail","jane.normal@monash.edu"),("cn","Janey"),("initials","JN"),("gender","female"),("personaltitle","mrs"))),
    LiftAuthStateData(false,"John",List(("ou","Unrestricted"),("uid","UnauthenticatedJohn"),("ou","Student"),("enrolledsubject","PSY2011"),("enrolledsubject","ENG2011"),("enrolledsubject","PHS2012"),("enrolledsubject","BIO2011")),List(("givenname","John"),("sn","Doe"),("mail","John.Doe@monash.edu"),("cn","Jonno"),("initials","JD"),("gender","male"),("personaltitle","mr"))), 
    LiftAuthStateData(false,"Dick",List(("ou","Unrestricted"),("uid","UnauthenticatedDick"),("ou","Staff"),("monashteachingcommitment","PSY2011"),("monashteachingcommitment","ENG2011"),("monashteachingcommitment","PHS2012"),("monashteachingcommitment","BIO2011")),List(("givenname","Dick"),("sn","Tracey"),("mail","richard.tracey@monash.edu"),("cn","Dickey"),("initials","DT"),("gender","male"),("personaltitle","dr")))
  )
  def state(user:String)=all.filter(_.username == user).toList match{
    case List(data, _) => data
    case _ => LiftAuthStateData(false,user,List(("ou","Unrestricted"),("uid","Unauthenticated"+user),("ou","Student")),List(("givenname",user),("sn","Tracey"),("mail",user+"@monash.edu"),("cn",user+"y"),("initials",user.take(2)),("gender","male"),("personaltitle","dr")))
  }
  val default = all(0)
}


import net.liftweb._
import http._
import common._
import util._
import Helpers._

object LiftAuthAuthentication {
  object originalReq extends SessionVar[Map[String,Req]](Map.empty[String,Req])
  val OriginalReqPath = List("auth","replay")
  val originalReqParam = "reqId"
  /*
  def replayReq(req:Req):Box[LiftResponse] = {
    req.request match {
      case hr:HTTPRequestServlet => {
        val provider = hr.provider
        val servletFilter = provider.liftServlet
        service(req,
      }

    // this isn't the right way of replaying an incoming req, I don't think - not sure whether snippets will evaluate against it.  I might need to reach up to the liftFilter to reapply a stored Req
    var response:Box[LiftResponse] = Empty
    LiftRules.statelessDispatch.toList.foreach(pf => {
      if (response == Empty){
        response = pf.apply(req)()
      }
    })
    LiftRules.dispatch.toList.foreach(pf => {
      if (response == Empty){
        response = pf.apply(req)()
      }
    })
//    LiftRules.sitemap.apply() //not yet sure how to do this bit
  }
  */
  def attachAuthenticator(mod:LiftAuthenticationSystem):Unit = {

    LiftRules.dispatch.append {
      case r@Req(List("testForm"),_,_) => () => Full({
        (for (
          a <- r.param("a");
          b <- r.param("b");
          files = r.uploadedFiles
        ) yield {
          PlainTextResponse("form posted okay\r\na:%s\r\nb:%s\r\nfiles:%s".format(a,b,files.map(f => {
            "%s => %s (%s) %s bytes".format(f.name,f.fileName,f.mimeType,f.length)
          })))
        }).getOrElse({
          val nodes = 
            <html>
              <body>
                <form action="/testForm" method="post" enctype="multipart/form-data">
                  <label for="a">a</label>
                  <input name="a" type="text"/>
                  <label for="b">b</label>
                  <input name="b" type="text"/>
                  <label for="file1">file1</label>
                  <input name="file1" type="file"/>
                  <label for="file2">file1</label>
                  <input name="file2" type="file"/>
                  <input type="submit" value="testSubmit"/>
                </form>
              </body>
            </html>
          val response = LiftRules.convertResponse(((nodes,200), S.getHeaders(LiftRules.defaultHeaders((nodes,r))), r.cookies, r))
          response
        })
      })
    }

    /*
    LiftRules.earlyInStateful.prepend {
      case req@Req(OriginalReqPath,_,_) => () => {
        */
    LiftRules.early.prepend {
      case req:net.liftweb.http.provider.HTTPRequest if req.uri.startsWith("/%s".format(OriginalReqPath.mkString("/"))) => () => {
        for (
          reqId <- req.param(originalReqParam).headOption;
          oReq <- originalReq.is.get(reqId)
        ) yield {
          originalReq(originalReq.is - reqId)
          CurrentReq.set(oReq)
        }
      }
    }
    LiftRules.dispatch.prepend {
      /*
      case req@Req(OriginalReqPath,_,_) => () => {
        for (
          reqId <- req.param(originalReqParam);
          oReq <- originalReq.is.get(reqId);
          resp <- replayReq(oReq)
        ) yield {
          originalReq(originalReq.is - reqId)
          resp
        }
      }
      */
      case req if mod.dispatchTableItemFilter(req) => () => {
        val reqId = nextFuncName
        originalReq(originalReq.is.updated(reqId,req))
        mod.dispatchTableItem(req,reqId)
      }
    }
  }
  def redirectToOriginalReq(reqId:String):RedirectResponse = RedirectResponse("/%s?%s=%s".format(OriginalReqPath.mkString("/"),originalReqParam,reqId))
}

trait LiftAuthenticationSystem {
  def dispatchTableItemFilter:Req=>Boolean
  def dispatchTableItem(req:Req,reqId:String):Box[LiftResponse]
}

abstract class LiftAuthenticator(alreadyLoggedIn:()=>Boolean,onSuccess:(LiftAuthStateData) => Unit) {
  object InSessionLiftAuthState extends SessionVar[LiftAuthStateData](LiftAuthStateDataForbidden)
  def checkWhetherAlreadyLoggedIn:Boolean = alreadyLoggedIn() || InSessionLiftAuthState.is.authenticated
  def constructResponse(input:Req,requestId:String):LiftResponse 
}
