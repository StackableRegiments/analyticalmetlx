package com.metl.model

import net.liftweb.actor._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import java.util.Date

import com.metl.external.{MeTLingPotAdaptor, MeTLingPotItem}
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.{JBool, JField, JInt, JString}

class PassThroughMeTLingPotAdaptor(val a:MeTLingPotAdaptor) extends MeTLingPotAdaptor {
  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = a.postItems(items)
  override def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] = a.search(after,before,queries)
  override def init:Unit = a.init
  override def shutdown:Unit = a.shutdown
  override def description:List[JField] = {
    List(
      JField("type",JString("passThroughMeTLingPotAdaptor")),
      JField("adaptor",JObject(a.description))
    )
  }
}

class BurstingPassThroughMeTLingPotAdaptor(override val a:MeTLingPotAdaptor,val burstSize:Int = 20,val delayBetweenPolls:Option[TimeSpan] = None,val delayBetweenSends:Option[TimeSpan] = None,val delayOnError:Option[TimeSpan] = None) extends PassThroughMeTLingPotAdaptor(a) with LiftActor with Logger {
  case object RequestSend
  protected val buffer = new scala.collection.mutable.Queue[MeTLingPotItem]()
  def getBufferLength:Int = buffer.length
  def getIsShuttingDown = shuttingDown
  def getLastSend:Long = lastSend
  def getLastCheck:Long = lastCheck

  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = {
    trace("adding items to the queue: %s".format(items.length))
    this ! items 
    Right(true)
  }
  protected val pollDelayTs = delayBetweenPolls.getOrElse(new TimeSpan(1000L))
  protected val sendDelayTs = delayBetweenSends.getOrElse(new TimeSpan(10L))
  protected val errorDelayTs = delayOnError.getOrElse(new TimeSpan(sendDelayTs.millis * 10))
  protected var lastSend:Long = 0L//new Date().getTime()
  protected var lastCheck:Long = 0L//new Date().getTime()
  protected var shuttingDown = false
  protected def reschedule(after:TimeSpan):Unit = Schedule.schedule(this,RequestSend,after)
  protected def isNotEmpty:Boolean = buffer.headOption.isDefined
  protected def addItems(items:List[MeTLingPotItem],front:Boolean = false) = {
    try {
      if (front){
        items.foreach(item => {
          item +=: buffer //prepending only takes one element at a time.
        })
      } else {
        buffer ++= items
      }
    } catch {
      case e:Exception => {
        error("failed to add items to buffer: %s".format(e.getMessage),e)
      }
    }
  }
  protected def onSuccess(res:Boolean) = {
    lastSend = new Date().getTime
    if (isNotEmpty){
      trace("continuingToProcess items from %s".format(getBufferLength))
      reschedule(sendDelayTs)
    } else {
      reschedule(pollDelayTs)
    }
  }
  protected def onError(items:List[MeTLingPotItem],e:Exception) = {
    lastSend = new Date().getTime
    trace("repeating items: %s".format(items.length))
    addItems(items,true)
    error("failed to send items",e)
    reschedule(errorDelayTs)
    e
  }
  protected def doUpload = {
    lastCheck = new Date().getTime
    if (isNotEmpty){
      var items:List[MeTLingPotItem] = List.empty[MeTLingPotItem]
      try {
        Range.inclusive(1,burstSize).foreach(_i => { // dequeueing only takes one element at a time.
          buffer.headOption.map(ho => {
            items = items ::: List(buffer.dequeue)
          })
        })
      } catch {
        case e:java.util.NoSuchElementException => {
        }
      }
      trace("processing items: %s".format(items.length))
      a.postItems(items).left.map(e => onError(items,e)).right.map(onSuccess)
    } else {
      reschedule(pollDelayTs)
    }
  }
  override def messageHandler = {
    case items:List[MeTLingPotItem] => {
      addItems(items)
    }
    case RequestSend if shuttingDown => {
      //eating the requestSend, and shutting down
    }
    case RequestSend => doUpload
    case _ => {}
  }
  override def shutdown:Unit = {
    // wait for the buffer to clear before shutting down
    while (isNotEmpty){
      trace("waiting for buffer to empty, to shutdown: %s".format(getBufferLength))
      Thread.sleep(100) 
    }
    shuttingDown = true
    super.shutdown
  }
  override def init:Unit = {
    super.init
    shuttingDown = false
    reschedule(pollDelayTs)
  }
  override def description:List[JField] = {
    List(
      JField("type",JString("passThroughMeTLingPotAdaptor")),
      JField("bufferLength",JInt(getBufferLength)),
      JField("isShuttingDown",JBool(getIsShuttingDown)),
      JField("lastCheckForItems",JInt(getLastCheck)),
      JField("lastSend",JInt(getLastSend)),
      JField("adaptor",JObject(a.description))
    )
  }
}

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
  override def description:List[JField] = {
    List(
      JField("type",JString("MockMeTLingPotAdaptor")),
      JField("storeSize",JInt(getStoreSize))
    )
  }
}

object MeTLingPot extends Logger {
  import scala.xml._
  def wrapWith(in:NodeSeq,mpa:MeTLingPotAdaptor):MeTLingPotAdaptor = {
    List((n:NodeSeq,a:MeTLingPotAdaptor) => {
      (for {
        size <- (n \ "@burstSize").headOption.map(_.text.toInt)
      } yield {
        val delayBetweenPolls = (n \ "@delayBetweenPolls").headOption.map(_.text.toLong).map(d => new TimeSpan(d))
        val delayBetweenSends = (n \ "@delayBetweenSends").headOption.map(_.text.toLong).map(d => new TimeSpan(d))
        val delayOnError = (n \ "@delayAfterError").headOption.map(_.text.toLong).map(d => new TimeSpan(d))
        val bptmpa = new BurstingPassThroughMeTLingPotAdaptor(a,size,delayBetweenPolls,delayBetweenSends,delayOnError)
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



