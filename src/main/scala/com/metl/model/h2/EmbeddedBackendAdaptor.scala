package com.metl.h2

import com.metl.utils._
import com.metl.data._
import com.metl.persisted._
import scala.xml._
import net.liftweb.mapper.StandardDBVendor

object LocalH2ServerConfiguration{
  def initialize = {
    ServerConfiguration.addServerConfigurator(LocalH2ServerConfigurator)
    ServerConfiguration.addServerConfigurator(SqlServerConfigurator)
  }
}

class LocalH2BackendAdaptor(name:String,filename:Option[String],onConversationDetailsUpdated:Conversation=>Unit) extends PersistedAdaptor(name,"localhost",onConversationDetailsUpdated){
  override val dbInterface = new H2Interface(this,filename,onConversationDetailsUpdated)
}
object LocalH2ServerConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").headOption.exists(_.text == "localH2")
  override def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentialsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = {
    Some(new LocalH2BackendAdaptor((e \ "name").headOption.map(_.text).getOrElse("localH2"),(e \ "filename").headOption.map(_.text),onConversationDetailsUpdated))
  }
}

class SqlBackendAdaptor(name:String,vendor:StandardDBVendor,onConversationDetailsUpdated:Conversation=>Unit,startingPoolSize:Int = 0,maxPoolSize:Int = 0) extends PersistedAdaptor(name,"localhost",onConversationDetailsUpdated){
  override val dbInterface = new SqlInterface(this,vendor,onConversationDetailsUpdated,startingPoolSize,maxPoolSize)
}
object SqlServerConfigurator extends ServerConfigurator{
  override def matchFunction(e:Node) = (e \\ "type").headOption.exists(_.text == "sql")
  override def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentialsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = {
    for (
      driver <- (e \\ "driver").headOption.map(_.text);
      url <- (e \\ "url").headOption.map(_.text)
    ) yield {
      val username = (e \\ "username").headOption.map(_.text)
      val password = (e \\ "password").headOption.map(_.text)
      val vendorAllowPoolExpansion = (e \\ "allowPoolExpansion").headOption.exists(_.text.toLowerCase.trim == "true")
      val vendorMaxPoolSize = (e \\ "maxPoolSize").headOption.map(_.text.toInt).getOrElse(100)
      val vendorMaxExpandedSize = (e \\ "maxExpansion").headOption.map(_.text.toInt).getOrElse(200)
      val startingPool = (e \\ "poolStartingSize").headOption.map(_.text.toInt).getOrElse(10)
      val vendor = new StandardDBVendor(driver,url,username,password){
        override def allowTemporaryPoolExpansion = vendorAllowPoolExpansion
        override def maxPoolSize = vendorMaxPoolSize
        override def doNotExpandBeyond = vendorMaxExpandedSize
      }
      new SqlBackendAdaptor((e \\ "name").headOption.map(_.text).getOrElse("sql"),vendor,onConversationDetailsUpdated,startingPool,vendorMaxPoolSize)
    }
  }
}
