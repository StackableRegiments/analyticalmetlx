package com.metl.metl2011

import com.metl.data._
import com.metl.utils._

import net.liftweb.util._
import net.liftweb.common.Logger
import org.apache.commons.io.IOUtils
import scala.xml._

class MeTL2011Resources(config:ServerConfiguration, http:HttpProvider) extends Logger {
	lazy val utils = new MeTL2011Utils(config)
	lazy val rootAddress = "https://%s:1188".format(config.host)
	def postResource(jid:String,userGeneratedId:String,data:Array[Byte]):String = {
		val uri = "%s/upload_nested.yaws?path=%s&overwrite=false&filename=%s".format(rootAddress,Helpers.urlEncode("Resource/%s/%s".format(utils.stem(jid.toString),jid.toString)),Helpers.urlEncode(userGeneratedId))	
		val response = http.getClient.postBytes(uri,data)
		val responseString = new String(response)
		debug("postedResource response: %s".format(responseString))
		((XML.loadString(responseString) \\ "resource").head \ "@url").text
	}
}
