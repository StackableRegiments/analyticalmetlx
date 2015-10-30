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
            <xmppHost>{PCData(cc.xmppHost)}</xmppHost>
            <xmppPort>{cc.xmppPort}</xmppPort>
            <xmppDomain>{PCData(cc.xmppDomain)}</xmppDomain>
            <xmppUsername>{cc.xmppUsername}</xmppUsername>
            <xmppPassword>{cc.xmppPassword}</xmppPassword>
            <conversationSearchUrl>{PCData(cc.conversationSearchUrl)}</conversationSearchUrl>
            <webAuthenticationUrl>{PCData(cc.webAuthenticationUrl)}</webAuthenticationUrl>
            <thumbnailUrl>{PCData(cc.thumbnailUrl)}</thumbnailUrl>
            <resourceUrl>{PCData(cc.resourceUrl)}</resourceUrl>
            <historyUrl>{PCData(cc.historyUrl)}</historyUrl>
            <httpUsername>{PCData(cc.httpUsername)}</httpUsername>
            <httpPassword>{PCData(cc.httpPassword)}</httpPassword>
            <structureDirectory>{PCData(cc.structureDirectory)}</structureDirectory>
            <resourceDirectory>{PCData(cc.resourceDirectory)}</resourceDirectory>
            <uploadPath>{PCData(cc.uploadPath)}</uploadPath>
            <primaryKeyGenerator>{PCData(cc.primaryKeyGenerator)}</primaryKeyGenerator>
            <cryptoKey>{PCData(cc.cryptoKey)}</cryptoKey>
            <cryptoIV>{PCData(cc.cryptoIV)}</cryptoIV>
            <imageUrl>{PCData(cc.imageUrl)}</imageUrl>
          </clientConfig>
        }).getOrElse(NodeSeq.Empty)
      }</authData>

    "#authData *" #> xml
  }
}
