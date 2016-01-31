package com.metl.h2

import com.metl.utils._
import com.metl.data._
import com.metl.persisted._
import scala.xml._

object LocalH2ServerConfiguration{
	def initialize = ServerConfiguration.addServerConfigurator(LocalH2ServerConfigurator) 
}

class LocalH2BackendAdaptor(name:String,filename:Option[String],onConversationDetailsUpdated:Conversation=>Unit) extends PersistedAdaptor(name,"localhost",onConversationDetailsUpdated){
	override lazy val dbInterface = new H2Interface(name,filename,onConversationDetailsUpdated)
	override def shutdown = dbInterface.shutdown
}
object LocalH2ServerConfigurator extends ServerConfigurator{
	override def matchFunction(e:Node) = (e \\ "type").headOption.exists(_.text == "localH2")
  override def interpret(e:Node,onConversationDetailsUpdated:Conversation=>Unit,messageBusCredentailsFunc:()=>Tuple2[String,String],conversationListenerCredentialsFunc:()=>Tuple2[String,String],httpCredentialsFunc:()=>Tuple2[String,String]) = {
    Some(new LocalH2BackendAdaptor("localH2",(e \ "filename").headOption.map(_.text),onConversationDetailsUpdated))
  }
}
