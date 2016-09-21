package com.metl.data

import com.metl.utils._

abstract class HttpProvider{
	def getClient:IMeTLHttpClient	
}

object EmptyHttpProvider extends HttpProvider {
	def getClient = Http.getClient
}

class SimpleAuthedHttpProvider(username:String,password:String) extends HttpProvider {
	def getClient = Http.getAuthedClient(username,password)
}
class DynamicallyAuthedHttpProvider(credentialFunc:()=>Tuple2[String,String]) extends HttpProvider {
  def getClient = {
    val creds = credentialFunc()
    Http.getAuthedClient(creds._1,creds._2)
  }
}
