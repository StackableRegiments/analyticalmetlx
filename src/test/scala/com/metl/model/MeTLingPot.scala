package com.metl.model

import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.util.Helpers.TimeSpan
import org.scalatest._
import org.scalatest.time.SpanSugar._
import matchers.MustMatchers
import concurrent.AsyncAssertions
import com.metl.utils._
import java.util.Date

import com.metl.external.{KVP, MeTLingPotAdaptor, MeTLingPotItem}

class TimedMeTLingPotAdaptor(perRequest:Long,perItem:Long,shouldError:() => Boolean) extends MeTLingPotAdaptor {
  override def postItems(items:List[MeTLingPotItem]):Either[Exception,Boolean] = {
    Thread.sleep(perRequest)
    items.foreach(i => {
      Thread.sleep(perItem)
    })
    val se:Boolean = shouldError()
    if (se){
      Left(new Exception("deliberate error"))
    } else {
      Right(true)
    }
  }
  override def search(after:Long,before:Long,queries:Map[String,List[String]]):Either[Exception,List[MeTLingPotItem]] = Right(Nil)
  override def init:Unit = {}
  override def shutdown:Unit = {}
}

class BurstingPassThroughMeTLingPotSuite extends FunSuite with AsyncAssertions with MustMatchers {
  test("should send chunks of 2, five times") {
    var counter = 0
    val w = new Waiter
    val a = new BurstingPassThroughMeTLingPotAdaptor(new TimedMeTLingPotAdaptor(0L,0L,()=>false),2,Some(new TimeSpan(0L)),Some(new TimeSpan(0L))){
      override protected def doUpload = {
        val res = super.doUpload
        counter += 1
        w.dismiss
        res
      }
    }
    a.init
    val fakeKVP = KVP("testType","testValue")
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    w.await(timeout(6 * 1000 millis), dismissals(5))
    a.shutdown
    assert(counter == 5)
  }
  test("should wait delay times on success"){
    var counter = 0
    var firstFailure:Boolean = true
    val w = new Waiter
    val t = new TimedMeTLingPotAdaptor(0L,0L,() => false)
    val a = new BurstingPassThroughMeTLingPotAdaptor(t,20,Some(new TimeSpan(2 * 1000L)),Some(new TimeSpan(0L))){
      override protected def doUpload = {
        val res = super.doUpload
        counter += 1
        w.dismiss
        res
      }
    }
    a.init
    val fakeKVP = KVP("testType","testValue")
    val now = new Date().getTime
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    w.await(timeout(10 * 1000 millis), dismissals(1))
    val after = new Date().getTime
    a.shutdown
    assert(counter > 0)
    assert((after - now) > (2 * 1000L))
  }
  test("should not wait error times on success"){
    var counter = 0
    val w = new Waiter
    val a = new BurstingPassThroughMeTLingPotAdaptor(new TimedMeTLingPotAdaptor(0L,0L,()=>false),2,Some(new TimeSpan(0L)),Some(new TimeSpan(20 * 1000L))){
      override protected def doUpload = {
        val res = super.doUpload
        counter += 1
        w.dismiss
        res
      }
    }
    a.init
    val fakeKVP = KVP("testType","testValue")
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    w.await(timeout(1000 millis), dismissals(5))
    a.shutdown
    assert(counter == 5)
  }
  test("should wait error times on errors"){
    var counter = 0
    var firstFailure:Boolean = true
    val w = new Waiter
    val t = new TimedMeTLingPotAdaptor(0L,0L,() => {
      if (firstFailure){
        firstFailure = false
        true
      } else {
        false
      }
    })
    val a = new BurstingPassThroughMeTLingPotAdaptor(t,20,Some(new TimeSpan(0L)),Some(new TimeSpan(3 * 1000L))){
      override protected def doUpload = {
        val res = super.doUpload
        counter += 1
        w.dismiss
        res
      }
    }
    a.init
    val fakeKVP = KVP("testType","testValue")
    val now = new Date().getTime
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    w.await(timeout(10 * 1000 millis), dismissals(2))
    val after = new Date().getTime
    a.shutdown
    assert(counter > 0)
    assert((after - now) > (3 * 1000L))
  }
}
