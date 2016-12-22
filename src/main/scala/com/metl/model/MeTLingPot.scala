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
      val req = new SearchItemsPutRequest()
      req.setBefore(new java.math.BigDecimal(before))
      req.setAfter(new java.math.BigDecimal(after))
      req.setQuery({
        val q = new SearchItemsPutRequestQuery()
        queries.get("source").foreach(s => {
          q.setSource(s.asJava)
        })
        queries.get("actortype").foreach(s => {
          q.setActortype(s.asJava)
        })
       queries.get("actorname").foreach(s => {
          q.setActorname(s.asJava)
        })
       queries.get("actiontype").foreach(s => {
          q.setActiontype(s.asJava)
        })
       queries.get("actionname").foreach(s => {
          q.setActionname(s.asJava)
        })
       queries.get("targettype").foreach(s => {
          q.setTargettype(s.asJava)
        })
       queries.get("targetname").foreach(s => {
          q.setTargetname(s.asJava)
        })
       queries.get("contexttype").foreach(s => {
          q.setContexttype(s.asJava)
        })
       queries.get("contextname").foreach(s => {
          q.setContextname(s.asJava)
        })
        q
      })

      val response = client.searchPut(req)
      Right(response.asScala.flatMap(r => {
        r.getItems.asScala.flatMap(i => {
          for {
            source <- Some(i.getSource)
            timestamp = i.getTimestamp.longValue
            action <- {
              for {
                t <- Some(i.getActiontype)
                n <- Some(i.getActionname)
              } yield {
                KVP(t,n)
              }
            }
            actor <- {
              for {
                t <- Some(i.getActortype)
                n <- Some(i.getActorname)
              } yield {
                KVP(t,n)
              }
            }
            context = {
              for {
                t <- Some(i.getContexttype)
                n <- Some(i.getContextname)
                if (t != null && n != null)
              } yield {
                KVP(t,n)
              }
            }
            target = {
              for {
                t <- Some(i.getTargettype)
                n <- Some(i.getTargetname)
                if (t != null && n != null)
              } yield {
                KVP(t,n)
              }
            }
            value = Some(i.getValue)
          } yield {
            MeTLingPotItem(source,timestamp,actor,action,context,target,value)
          }
        })
     }).toList)
    } catch {
      case e:Exception => Left(e)
    }
  }
}
