package com.metl.model

import net.liftweb.actor._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import java.util.Date

import com.metl.external.{MeTLingPotAdaptor, MeTLingPotItem}

class AWSStaticCredentialsProvider(creds:AWSCredentials) extends AWSCredentialsProvider {
  override def getCredentials:AWSCredentials = creds
  override def refresh:Unit = {}
}

case class KVP(`type`:String,name:String)
case class MeTLingPotItem(source:String,timestamp:Long,actor:KVP,action:KVP,context:Option[KVP],target:Option[KVP],value:Option[String])

class APIGatewayClient(val endpoint:String,val region:String,val iamAccessKey:String,iamSecretAccessKey:String,val apiGatewayApiKey:Option[String]) {
  import com.amazonaws.opensdk.config.{ConnectionConfiguration,TimeoutConfiguration}
  def client:MetlingPotInputItem = {
    def attachApiKey(in:MetlingPotInputItemClientBuilder):MetlingPotInputItemClientBuilder = {
      apiGatewayApiKey.map(agak => in.apiKey(agak)).getOrElse(in)
    }
    attachApiKey(
      MetlingPotInputItem.builder().connectionConfiguration(
        new ConnectionConfiguration()
          .maxConnections(100)
          .connectionMaxIdleMillis(1000) // 1 second
       ).timeoutConfiguration(
        new TimeoutConfiguration()
          .httpRequestTimeout(20 * 1000) // 20 seconds
          .totalExecutionTimeout(60 * 1000) // 30 seconds
          .socketTimeout(30 * 1000) // 2 seconds
      ).iamCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(iamAccessKey,iamSecretAccessKey)))
      .iamRegion(region)
      .endpoint(endpoint)
    ).build()
  }
}

trait MeTLingPotAdaptor extends Logger {
  def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] 
  def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] 
  def init:Unit = {}
  def shutdown:Unit = {}
}

class PassThroughMeTLingPotAdaptor(val a:MeTLingPotAdaptor) extends MeTLingPotAdaptor {
  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = a.postItems(items)
  override def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] = a.search(after,before,queries)
  override def init:Unit = a.init
  override def shutdown:Unit = a.shutdown
}

class BurstingPassThroughMeTLingPotAdaptor(override val a:MeTLingPotAdaptor,val burstSize:Int = 20,delay:Option[TimeSpan] = None,val delayOnError:Option[TimeSpan] = None) extends PassThroughMeTLingPotAdaptor(a) with LiftActor {
  case object RequestSend
  protected val buffer = new scala.collection.mutable.ListBuffer[MeTLingPotItem]()
  def getBufferLength:Int = buffer.length
  def getIsShuttingDown = shuttingDown
  def getLastSend:Long = lastSend

  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = {
    trace("adding items to the queue: %s".format(items.length))
    addItems(items)
    Right(true)
  }
  protected val delayTs = delay.getOrElse(new TimeSpan(1000L))
  protected val errorDelayTs = delayOnError.getOrElse(new TimeSpan(delayTs.millis * 10))
  protected var lastSend:Long = new Date().getTime()
  protected var shuttingDown = false
  protected def reschedule(after:TimeSpan):Unit = Schedule.schedule(this,RequestSend,after)
  protected def addItems(items:List[MeTLingPotItem],front:Boolean = false) = {
    try {
      if (front){
        items ++=: buffer
      } else {
        buffer ++= items
      }
    } catch {
      case e:Exception => {
        error("failed to add items to buffer: %s".format(e.getMessage),e)
      }
    }
  }
  protected def doUpload = {
    val items:List[MeTLingPotItem] = buffer.take(burstSize).toList
    buffer --= items
    trace("processing items: %s".format(items.length))
    a.postItems(items).left.map(e => {
      trace("repeating items: %s".format(items.length))
      addItems(items,true)
      error("failed to send items",e)
      reschedule(errorDelayTs)
      e
    }).right.map(res => {
      if (buffer.length > 0){
        trace("continuingToProcess items from %s".format(buffer.length))
      }
      reschedule(delayTs)
    })
  }
  override def messageHandler = {
    case RequestSend if shuttingDown => {
      //eating the requestSend, and shutting down
    }
    case RequestSend if ((lastSend + delayTs.millis) < new Date().getTime) && buffer.length > 0 => doUpload
    case RequestSend => reschedule(delayTs)
    case _ => {}
  }
  override def shutdown:Unit = {
    while (buffer.length > 0){
      trace("waiting for buffer to empty, to shutdown: %s".format(buffer.length))
      Thread.sleep(100) // wait for the buffer to clear before shutting down
    }
    shuttingDown = true
    super.shutdown
  }
  override def init:Unit = {
    super.init
    shuttingDown = false
    this ! RequestSend
  }
}

<<<<<<< HEAD
=======
class ApiGatewayMeTLingPotInterface(val endpoint:String,val region:String,val iamAccessKey:String,iamSecretAccessKey:String,val apiGatewayApiKey:Option[String]) extends MeTLingPotAdaptor {
  val clientFactory = new APIGatewayClient(endpoint,region,iamAccessKey,iamSecretAccessKey,apiGatewayApiKey)
  def client:MetlingPotInputItem = clientFactory.client

  def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = {
    try {
      val outerReq = new PutInputItemRequest()
      val req = new InputItemsPutRequest()
      req.setItems(items.map(i => {
        val item = new InputItem()
        item.setSource(i.source)
        item.setTimestamp(i.timestamp)
        item.setActor({
          val a = new TypedValue()
          a.setName(i.actor.name)
          a.setType(i.actor.`type`)
          a
        })
        item.setAction({
          val a = new TypedValue()
          a.setName(i.action.name)
          a.setType(i.action.`type`)
          a
        })
        item.setTarget({
          val a = new TypedValue()
          a.setName(i.target.map(_.name).getOrElse(""))
          a.setType(i.target.map(_.`type`).getOrElse(""))
          a
        })
        item.setContext({
          val a = new TypedValue()
          a.setName(i.context.map(_.name).getOrElse(""))
          a.setType(i.context.map(_.`type`).getOrElse(""))
          a
        })
        item.setValue(i.value.getOrElse(""))
        item
      }).toList.asJava)
      outerReq.setInputItemsPutRequest(req)
      val response = client.putInputItem(outerReq)
      Right(true)
    } catch {
      case e:Exception => Left(e)
    }
  }
  def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] = {
    try {
      val outerReq = new PutSearchRequest()
      val req = new SearchItemsPutRequest()
      req.setBefore(before)
      req.setAfter(after)
      req.setQuery({
        val q = new Query()
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
      outerReq.setSearchItemsPutRequest(req)
      val response = client.putSearch(outerReq)
      Right(response.getSearchItemsResponse().asScala.flatMap(r => {
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

>>>>>>> master
class MockMeTLingPotAdaptor extends MeTLingPotAdaptor {
  protected val store:scala.collection.mutable.ListBuffer[MeTLingPotItem] = new scala.collection.mutable.ListBuffer[MeTLingPotItem]
  def getStoreSize:Int = store.length
  def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = {
    store ++= items
    Right(true)
  }
  def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] = {
    Right(store.filter(i => i.timestamp > after && i.timestamp < before && !queries.toList.exists{
      case ("source",sourceFilters) => !sourceFilters.contains(i.source)
      case ("actortype",sourceFilters) => !sourceFilters.contains(i.actor.`type`)
      case ("actorname",sourceFilters) => !sourceFilters.contains(i.actor.name)
      case ("actiontype",sourceFilters) => !sourceFilters.contains(i.actor.`type`)
      case ("actionname",sourceFilters) => !sourceFilters.contains(i.actor.name)
      case ("targettype",sourceFilters) => !sourceFilters.contains(i.actor.`type`)
      case ("targetname",sourceFilters) => !sourceFilters.contains(i.actor.name)
      case ("contexttype",sourceFilters) => !sourceFilters.contains(i.actor.`type`)
      case ("contextname",sourceFilters) => !sourceFilters.contains(i.actor.name)
      case _ => false
    }).toList)
  }
}

object MeTLingPot extends Logger {
  import scala.xml._
  def wrapWith(in:NodeSeq,mpa:MeTLingPotAdaptor):MeTLingPotAdaptor = {
    List((n:NodeSeq,a:MeTLingPotAdaptor) => {
      (for {
        size <- (n \ "@burstSize").headOption.map(_.text.toInt)
      } yield {
        val delay = (n \ "@delayBetweenSends").headOption.map(_.text.toLong).map(d => new TimeSpan(d))
        val delayOnError = (n \ "@delayAfterError").headOption.map(_.text.toLong).map(d => new TimeSpan(d))
        val bptmpa = new BurstingPassThroughMeTLingPotAdaptor(a,size,delay,delayOnError)
        info("creating burstingMetlingPotAdaptor: %s".format(bptmpa))
        bptmpa
      }).getOrElse(a)
    }).foldLeft(mpa)((acc,item) => {
      item(in,acc)
    })
  }
  def configureFromXml(in:NodeSeq):List[MeTLingPotAdaptor] = {
    (for {
      x <- (in \\ "mockMetlingPot")
    } yield {
      wrapWith(x,new MockMeTLingPotAdaptor())
    }).toList
  }
}



