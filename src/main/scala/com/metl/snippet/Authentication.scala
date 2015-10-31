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
          <authGroup type={eg._1} name={eg._2} />
        }) }</eligibleGroups>
        <informationGroups>{authData.informationGroups.map(ig => {
          <infoGroup type={ig._1} name={ig._2} />
        })}</informationGroups> ++ 
        (for(
          cp <- MeTLXConfiguration.configurationProvider;
          cc <- cp.vendClientConfiguration(authData.username)
        ) yield {
          <clientConfig> 
            <xmppHost>{cc.xmppHost}</xmppHost>
            <xmppPort>{cc.xmppPort}</xmppPort>
            <xmppDomain>{cc.xmppDomain}</xmppDomain>
            <xmppUsername>{cc.xmppUsername}</xmppUsername>
            <xmppPassword>{cc.xmppPassword}</xmppPassword>
            <conversationSearchUrl>{cc.conversationSearchUrl}</conversationSearchUrl>
            <webAuthenticationUrl>{cc.webAuthenticationUrl}</webAuthenticationUrl>
            <thumbnailUrl>{cc.thumbnailUrl + ServerConfiguration.default.name + "/"}</thumbnailUrl>
            <resourceUrl>{cc.resourceUrl}</resourceUrl>
            <historyUrl>{cc.historyUrl}</historyUrl>
            <httpUsername>{cc.httpUsername}</httpUsername>
            <httpPassword>{cc.httpPassword}</httpPassword>
            <structureDirectory>{cc.structureDirectory}</structureDirectory>
            <resourceDirectory>{cc.resourceDirectory}</resourceDirectory>
            <uploadPath>{cc.uploadPath}</uploadPath>
            <primaryKeyGenerator>{cc.primaryKeyGenerator}</primaryKeyGenerator>
            <cryptoKey>{cc.cryptoKey}</cryptoKey>
            <cryptoIV>{cc.cryptoIV}</cryptoIV>
            <imageUrl>{cc.imageUrl}</imageUrl>
          </clientConfig>
        }).getOrElse(NodeSeq.Empty)
      }</authData>

    "#authData *" #> xml
  }
}
