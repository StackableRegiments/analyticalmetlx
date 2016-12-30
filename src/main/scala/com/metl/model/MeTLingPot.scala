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

import net.liftweb.actor._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import java.util.Date

class AWSStaticCredentialsProvider(creds:AWSCredentials) extends AWSCredentialsProvider {
  override def getCredentials:AWSCredentials = creds
  override def refresh:Unit = {}
}

case class KVP(`type`:String,name:String)
case class MeTLingPotItem(source:String,timestamp:Long,actor:KVP,action:KVP,context:Option[KVP],target:Option[KVP],value:Option[String])

class APIGatewayClient(endpoint:String,region:String,iamAccessKey:String,iamSecretAccessKey:String,apiGatewayApiKey:Option[String]) {
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
          .totalExecutionTimeout(30 * 1000) // 30 seconds
          .socketTimeout(2 * 1000) // 2 seconds
      ).iamCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(iamAccessKey,iamSecretAccessKey)))
      .iamRegion(region)
      .endpoint(endpoint)
    ).build()
  }
}

trait MeTLingPotAdaptor {
  def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] 
  def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] 
  def init:Unit = {}
  def shutdown:Unit = {}
}

class PassThroughMeTLingPotAdaptor(a:MeTLingPotAdaptor) extends MeTLingPotAdaptor {
  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = a.postItems(items)
  override def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] = a.search(after,before,queries)
  override def init:Unit = a.init
  override def shutdown:Unit = a.shutdown
}

class BurstingPassThroughMeTLingPotAdaptor(a:MeTLingPotAdaptor,burstSize:Int = 20,delay:TimeSpan = new TimeSpan(0L)) extends PassThroughMeTLingPotAdaptor(a) with LiftActor with Logger {
  case object RequestSend
  protected val buffer = new scala.collection.mutable.ListBuffer[MeTLingPotItem]()
  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = {
    println("adding items to the queue: %s".format(items.length))
    buffer ++= items
    this ! RequestSend
    Right(true)
  }
  protected var sending:Boolean = false
  protected var lastSend:Long = new Date().getTime()
  override def messageHandler = {
    case RequestSend if sending => {
      println("delaying queue")
      Schedule.schedule(this,RequestSend,delay)
    }
    case RequestSend if ((lastSend + delay.millis) < new Date().getTime) => {
      sending = true
      val items:List[MeTLingPotItem] = buffer.take(burstSize).toList
      buffer --= items
      println("processing items: %s".format(items.length))
      a.postItems(items).left.toOption.foreach(e => {
        println("repeating items: %s".format(items.length))
        items ++=: buffer //put the items back on the queue, at the front, so that they'll be retried later.
        error("failed to send items",e)
      })
      sending = false
      if (buffer.length > 0){
        println("continuingToProcess items from %s".format(buffer.length))
        Schedule.schedule(this,RequestSend,delay)
      }
    }
    case RequestSend => {
      println("delaying queue")
      Schedule.schedule(this,RequestSend,delay)
    }
    case _ => {}
  }
  override def shutdown:Unit = {
    while (sending && buffer.length > 0){
      Thread.sleep(100) // wait for the buffer to clear before shutting down
    }
    super.shutdown
  }
}

class ApiGatewayMeTLingPotInterface(endpoint:String,region:String,iamAccessKey:String,iamSecretAccessKey:String,apiGatewayApiKey:Option[String]) extends MeTLingPotAdaptor {
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

class MockMeTLingPotAdaptor extends MeTLingPotAdaptor {
  protected val store:scala.collection.mutable.ListBuffer[MeTLingPotItem] = new scala.collection.mutable.ListBuffer[MeTLingPotItem]
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

object MeTLingPot {
  import scala.xml._
  protected def wrapWith(in:NodeSeq,mpa:MeTLingPotAdaptor):MeTLingPotAdaptor = {
    List((n:NodeSeq,a:MeTLingPotAdaptor) => {
      (for {
        size <- (n \ "@burstSize").headOption.map(_.text.toInt)
      } yield {
        val bptmpa = new BurstingPassThroughMeTLingPotAdaptor(a,size)
        println("creating burstingMetlingPotAdaptor: %s".format(bptmpa))
        bptmpa
      }).getOrElse(a)
    }).foldLeft(mpa)((acc,item) => {
      item(in,acc)
    })
  }
  def configureFromXml(in:NodeSeq):List[MeTLingPotAdaptor] = {
    ((for {
      x <- (in \\ "mockMetlingPot")
    } yield {
      wrapWith(x,new MockMeTLingPotAdaptor())
    }).toList :::
    (for {
      x <- (in \\ "ApiGatewayMetlingPotAdaptor")
      endpoint <- (x \ "@endpoint").headOption.map(_.text)
      region <- (x \ "@region").headOption.map(_.text)
      iamAccessKey <- (x \ "@accessKey").headOption.map(_.text)
      iamSecretAccessKey <- (x \ "@secretAccessKey").headOption.map(_.text)
      apiKey = (x \ "@apiKey").headOption.map(_.text)
    } yield {
      val agmpi = new ApiGatewayMeTLingPotInterface(endpoint,region,iamAccessKey,iamSecretAccessKey,apiKey)
      println("creating apiGatewayMetlingPotInterface: %s".format(agmpi))
      wrapWith(x,agmpi)
    }).toList)
  }
}

class SmartGroupsProvider(override val storeId:String, endpoint:String,region:String,iamAccessKey:String,iamSecretAccessKey:String,apiGatewayApiKey:Option[String],groupSize:Int) extends GroupsProvider(storeId) {
  import com.metl.liftAuthenticator._
  override val canQuery:Boolean = true
  protected val clientFactory = new APIGatewayClient(endpoint,region,iamAccessKey,iamSecretAccessKey,apiGatewayApiKey)
  def client:MetlingPotInputItem = clientFactory.client

  override def getGroupsFor(userData:LiftAuthStateData):List[OrgUnit] = List(OrgUnit("smart","Smart Groups",Nil,Nil,None))
  override def getMembersFor(orgUnit:OrgUnit):List[Member] = orgUnit.members
  override def getGroupSetsFor(orgUnit:OrgUnit,members:List[Member] = Nil):List[GroupSet] = getGroupCategoriesForMembers(members)
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet):List[Member] = groupSet.members
  override def getGroupsFor(orgUnit:OrgUnit,groupSet:GroupSet,members:List[Member] = Nil):List[Group] = groupSet.groups
  override def getMembersFor(orgUnit:OrgUnit,groupSet:GroupSet,group:Group):List[Member] = group.members
  
  override def getOrgUnit(name:String):Option[OrgUnit] = None
  override def getPersonalDetailsFor(userData:LiftAuthStateData):List[Detail] = userData.informationGroups.toList

  protected def getGroupCategoriesForMembers(members:List[Member]):List[GroupSet] = {
    val outerReq = new PutSmartgroupsRequest()
    val req = new SmartGroupsRequest()
    req.setGroupCount(Math.max(2,members.length / Math.max(1,groupSize)))
    req.setMembers(members.map(_.name).asJava)
    outerReq.setSmartGroupsRequest(req)
    val resp = client.putSmartgroups(outerReq)
    resp.getSmartGroupsResponse().getGroupSets.asScala.map(gs => {
      val groups = gs.getGroups.asScala.map(g => {
        val members = g.getMembers.asScala.map(m => {
          Member(m,Nil,None)
        }).toList
        Group("smartGroup",g.getName,members,None)
      }).toList
      GroupSet("smartGroupSet",gs.getName,groups.flatMap(_.members),groups,None)
    }).toList
  }
}

