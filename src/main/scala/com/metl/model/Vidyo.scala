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

class VidyoProvider(applicationId:String,developerKey:String){
  val tokenProvider = new VidyoTokenProvider(applicationId,developerKey)
  def generateToken(username:String,secondsUntilExpiry:Long):String = tokenProvider.generateToken(username,secondsUntilExpiry)
}

class VidyoTokenProvider(applicationId:String,developerKey:String){
  protected val provisionToken = "provision"
  protected val epochSeconds = 62167219200L
  protected val delimiter = "\0"
  def generateToken(username:String,secondsUntilExpiry:Long):String = {
    val expiresInSeconds:Long = epochSeconds + (new Date().getTime / 1000) + secondsUntilExpiry
    val jid = "%s@%s".format(username,applicationId)
    val payload = List(provisionToken,jid,expiresInSeconds.toString).mkString(delimiter)
    new String(Base64.encodeBase64(
      (List(payload,HmacUtils.hmacSha384Hex(developerKey,payload)).mkString(delimiter)).getBytes()
    ))
  }
}
