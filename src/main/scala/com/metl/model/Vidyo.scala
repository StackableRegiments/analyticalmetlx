package com.metl.model

import net.liftweb._
import http._
import util._
import common._
import Helpers._
import json._
import JsonDSL._
import java.util.Date
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.HmacUtils

case class VidyoSession(sessionToken:String,username:String,expiresAt:Long)

class VidyoProvider(applicationId:String,developerKey:String){
  //protected val tokenLifetimeInSeconds:Long = 60 * 60 * 24 // 1 day tokens for the moment
  protected val tokenLifetimeInSeconds:Long = 10 * 1000
  val tokenProvider = new VidyoTokenProvider(applicationId,developerKey)
  def generateSession(username:String,expireInSeconds:Long = tokenLifetimeInSeconds):VidyoSession = {
    val (token,expiryTime) = tokenProvider.generateToken(username,expireInSeconds)
    VidyoSession(token,username,expiryTime)
  }
}

class VidyoTokenProvider(applicationId:String,developerKey:String){
  protected val encoding:String = "UTF-8"
  protected val provisionToken:String = "provision"
  protected val epochSeconds:Long = 62167219200l //long seconds since 0
  protected val innerDelimiter:String = new String(Array(0.toChar))
  protected val outerDelimiter:String = new String(Array(0.toChar))
  def generateToken(username:String,secondsUntilExpiry:Long,vCard:String = ""):Tuple2[String,Long] = {
    val expiresInSeconds:Long = epochSeconds + (System.currentTimeMillis() / 1000) + secondsUntilExpiry
    val jid = "%s@%s".format(username,applicationId)
    val payload = String.join(innerDelimiter,provisionToken,jid,expiresInSeconds.toString,vCard)
    val finalString = String.join(outerDelimiter,payload,HmacUtils.hmacSha384Hex(developerKey,payload))
    println("generating token:\r\nusername:%s\r\nsecondsUntilExpiry:%s\r\nexpiresInSeconds:%s\r\njid:%s\r\npayload:%s\r\nfinalString:%s".format(username,secondsUntilExpiry,expiresInSeconds,jid,payload,finalString))
    (new String(Base64.encodeBase64(finalString.getBytes(encoding)),encoding),expiresInSeconds * 1000)
  }
}
