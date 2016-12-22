package com.metl.snippet

import com.metl.data._
import com.metl.utils._

import com.metl.model._
import com.metl.snippet.Utils._
import scala.xml._
import net.liftweb.common._
import net.liftweb.util.{PCData=>LiftPCData,_}
import net.liftweb.http._
import Helpers._
import S._



class AuthenticationSnippet {
  def render = {
    val authData = Globals.casState.is
    val xml =
      <authData>{
        <username>{ authData.username }</username>
        <authenticated>{ authData.authenticated }</authenticated>
        <eligibleGroups>{ authData.eligibleGroups.map(eg => {
          <authGroup type={eg.ouType} name={eg.name} />
        }) }</eligibleGroups>
        <informationGroups>{authData.informationGroups.map(ig => {
          <infoGroup type={ig.key} name={ig.value} />
        })}</informationGroups> ++ 
        (for(
          cp <- MeTLXConfiguration.configurationProvider;
          cc <- cp.vendClientConfiguration(authData.username)
        ) yield {
          <clientConfig> 
            <xmppDomain>{cc.xmppDomain}</xmppDomain>
            <xmppUsername>{cc.xmppUsername}</xmppUsername>
            <xmppPassword>{cc.xmppPassword}</xmppPassword>
            <imageUrl>{cc.imageUrl}</imageUrl>
          </clientConfig>
        }).getOrElse(NodeSeq.Empty)
      }</authData>

    "#authData *" #> xml
  }
}
