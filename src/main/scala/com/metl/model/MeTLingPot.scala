package com.metl.model
/*
import com.amazonaws.services.apigateway._
*/
import com.amazonaws.auth._
import com.metlingpot.model._
import com.metlingpot._
import scala.reflect._
import scala.reflect.ClassTag
import scala.collection.JavaConverters._

class AWSStaticCredentialsProvider(creds:AWSCredentials) extends AWSCredentialsProvider {
  override def getCredentials:AWSCredentials = creds
  override def refresh:Unit = {}
}

case class KVP(`type`:String,name:String)
case class MeTLingPotItem(source:String,timestamp:Long,actor:KVP,action:KVP,context:Option[KVP],target:Option[KVP],value:Option[String])

class APIGatewayClient(/*region:String,invokeHost:String,*/iamAccessKey:String,iamSecretAccessKey:String,apiGatewayApiKey:Option[String]) {
  def client[B: ClassTag]:B = {
    var factory = new com.amazonaws.mobileconnectors.apigateway.ApiClientFactory().credentialsProvider(new AWSStaticCredentialsProvider(new BasicAWSCredentials(iamAccessKey,iamSecretAccessKey)))
    apiGatewayApiKey.foreach(agak => {
      factory = factory.apiKey(agak)
    })
    factory.build(classTag[B].runtimeClass).asInstanceOf[B]
  }
}

class MeTLingPotInterfac(/*region:String,invokeHost:String,*/iamAccessKey:String,iamSecretAccessKey:String,apiGatewayApiKey:Option[String]) {
  val clientFactory = new APIGatewayClient(iamAccessKey,iamSecretAccessKey,apiGatewayApiKey)
  def client:MetlingPotInputItemClient = clientFactory.client[MetlingPotInputItemClient]

  def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = {
    try {
      val req = new InputItemsPutRequest()
      req.setItems(items.map(i => {
        val item = new InputItemsPutRequestItemsItem()
        item.setSource(i.source)
        item.setTimestamp(new java.math.BigDecimal(i.timestamp))
        item.setActor({
          val a = new InputItemsPutRequestItemsItemActor()
          a.setName(i.actor.name)
          a.setType(i.actor.`type`)
          a
        })
        item.setAction({
          val a = new InputItemsPutRequestItemsItemActor()
          a.setName(i.action.name)
          a.setType(i.action.`type`)
          a
        })

        i.target.foreach(t => {
          item.setTarget({
            val a = new InputItemsPutRequestItemsItemActor()
            a.setName(t.name)
            a.setType(t.`type`)
            a
          })
        })
        i.context.foreach(t => {
          item.setContext({
            val a = new InputItemsPutRequestItemsItemActor()
            a.setName(t.name)
            a.setType(t.`type`)
            a
          })
        })
        item
      }).asJava)
      val response = client.inputItemPut(req)
      Right(true)
    } catch {
      case e:Exception => Left(e)
    }
  }
  def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] = {
    try {
      Right(Nil)
    } catch {
      case e:Exception => Left(e)
    }
  }
}
