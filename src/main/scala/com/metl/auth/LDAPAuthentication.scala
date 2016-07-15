package com.metl.ldapAuthenticator

import com.metl.liftAuthenticator._
import com.metl.formAuthenticator._
import com.metl.utils._
import com.metl.ldap._

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

class LDAPAuthenticationSystem(mod:LDAPAuthenticator) extends FormAuthenticationSystem(mod) {
}

class LDAPAuthenticator(loginPage:NodeSeq, formSelector:String, usernameSelector:String, passwordSelector:String, ldap: Option[IMeTLLDAP], alreadyLoggedIn:() => Boolean,onSuccess:(LiftAuthStateData) => Unit) extends FormAuthenticator(loginPage,formSelector,usernameSelector,passwordSelector,c => ldap.map(l => {
  val u = c._1
  val p = c._2
  l.authenticate(u,p) match {
    case Some(true) => {
      LiftAuthStateData(true,u,List.empty[Tuple2[String,String]],List.empty[Tuple2[String,String]])
    }
    case _ => {
      LiftAuthStateDataForbidden
    }
  }
}).getOrElse({
  LiftAuthStateDataForbidden
}),alreadyLoggedIn,onSuccess) {
}
