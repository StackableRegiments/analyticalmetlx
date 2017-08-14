package com.metl.model

import net.liftweb.actor._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import java.util.Date

import com.metl.external.{MeTLingPotAdaptor, MeTLingPotItem}

class PassThroughMeTLingPotAdaptor(a:MeTLingPotAdaptor) extends MeTLingPotAdaptor with Logger {
  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = a.postItems(items)
  override def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] = a.search(after,before,queries)
  override def init:Unit = a.init
  override def shutdown:Unit = a.shutdown
}

class BurstingPassThroughMeTLingPotAdaptor(a:MeTLingPotAdaptor,burstSize:Int = 20,delay:TimeSpan = new TimeSpan(0L)) extends PassThroughMeTLingPotAdaptor(a) with LiftActor {
  case object RequestSend
  protected val buffer = new scala.collection.mutable.ListBuffer[MeTLingPotItem]()
  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = {
    trace("adding items to the queue: %s".format(items.length))
    buffer ++= items
    this ! RequestSend
    Right(true)
  }
  protected var sending:Boolean = false
  protected var lastSend:Long = new Date().getTime()
  override def messageHandler = {
    case RequestSend if sending => {
      trace("delaying queue")
      Schedule.schedule(this,RequestSend,delay)
    }
    case RequestSend if ((lastSend + delay.millis) < new Date().getTime) => {
      sending = true
      val items:List[MeTLingPotItem] = buffer.take(burstSize).toList
      buffer --= items
      trace("processing items: %s".format(items.length))
      a.postItems(items).left.toOption.foreach(e => {
        trace("repeating items: %s".format(items.length))
        items ++=: buffer //put the items back on the queue, at the front, so that they'll be retried later.
        error("failed to send items",e)
      })
      sending = false
      if (buffer.length > 0){
        trace("continuingToProcess items from %s".format(buffer.length))
        Schedule.schedule(this,RequestSend,delay)
      }
    }
    case RequestSend => {
      trace("delaying queue")
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

object MeTLingPot extends Logger {
  import scala.xml._
  protected def wrapWith(in:NodeSeq,mpa:MeTLingPotAdaptor):MeTLingPotAdaptor = {
    List((n:NodeSeq,a:MeTLingPotAdaptor) => {
      (for {
        size <- (n \ "@burstSize").headOption.map(_.text.toInt)
      } yield {
        val bptmpa = new BurstingPassThroughMeTLingPotAdaptor(a,size)
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



