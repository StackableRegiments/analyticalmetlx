package com.metl.metl2011

import com.metl.data._
import com.metl.utils._

import net.liftweb._
import http._
import common._
import util._
import Helpers._
import collection._

class MeTL2011Utils(config:ServerConfiguration) {
	def stem(path:String):String = (("0"*(5 - path.length)) + path).takeRight(5).take(2).mkString
	def reabsolutizeUri(uri:String,prefix:String):Box[String]={
		try {
			val path = new java.net.URI(uri).getPath
			val pathparts = path.split("/").filter(_ != "").toList 
			def construct(stemmed:String):Box[String] = Full("https://%s:1188/%s".format(config.host,stemmed))
			pathparts match{
				case List(prefix,stemmed,stemmable,_*) if (stem(stemmable) == stemmed) => construct((List(prefix,stemmed,stemmable) ::: pathparts.drop(3)).mkString("/"))
				case List(prefix,unstemmed,_*)=> construct((List(prefix,stem(unstemmed),unstemmed) ::: pathparts.drop(2)).mkString("/"))
				case List(noPrefix)=> construct(noPrefix)
				case Nil => Empty
			}
		} catch {
			case e:Throwable => {
				Failure("reabsolutizeUri(%s,%s) failed".format(uri,prefix),Full(e),Empty)
			}
		}
	}
	def deabsolutizeUri(uriString:String,serverConfig:ServerConfiguration):Box[String] ={
		val uri =  new java.net.URI(uriString)
		val path = uri.getRawPath
		uri.getHost match {
			case h if (h == serverConfig.host) => Full(path)
			case s if (path.length > 0 && path.startsWith("/")) => Full(path)
			case _ => Empty
		}
	}
}
