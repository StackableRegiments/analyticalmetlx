package com.metl.snippet

import com.metl.data._
import com.metl.utils._

import com.metl.model._
import com.metl.snippet.Utils._
import scala.xml._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.http._
import Helpers._
import S._

class AuthenticationSnippet {
  def render = {
    val authData = Globals.casState.is
    val xml =
      <authData>
    <username>{ authData.username }</username>
    <authenticated>{ authData.authenticated }</authenticated>
    <eligibleGroups>{ authData.eligibleGroups.map(eg => {
      <authGroup type={eg._1} name={eg._2} />
    }) }</eligibleGroups>
    <informationGroups>{authData.informationGroups.map(ig => {
      <infoGroup type={ig._1} name={ig._2} />
    })}</informationGroups>
    </authData>
    "#authData *" #> xml
  }
}
