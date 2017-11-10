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
  val awaitTimeout = timeout(5 * 60 * 1000 millis) // 5 minutes
  test("should send chunks of 2, five times") {
    var counter = 0
    val w = new Waiter
    val poll = Some(new TimeSpan(0L))
    val success = Some(new TimeSpan(0L))
    val error = Some(new TimeSpan(0L))
    val a = new BurstingPassThroughMeTLingPotAdaptor(new TimedMeTLingPotAdaptor(0L,0L,()=>false),2,poll,success,error){
      override protected def onSuccess(res:Boolean) = {
        val result = super.onSuccess(res)
        counter += 1
        w.dismiss
        result
      }
    }
    a.init
    val fakeKVP = KVP("testType","testValue")
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    w.await(awaitTimeout, dismissals(5))
    a.shutdown
    assert(counter == 5)
  }
  test("should wait poll times on empty queue"){
    var counter = 0
    var firstFailure:Boolean = true
    val w = new Waiter
    val t = new TimedMeTLingPotAdaptor(0L,0L,() => false)
    val poll = Some(new TimeSpan(2 * 1000L))
    val success = Some(new TimeSpan(0L))
    val error = Some(new TimeSpan(0L))
    val a = new BurstingPassThroughMeTLingPotAdaptor(t,20,poll,success,error){
      override protected def doUpload = {
        val res = super.doUpload
        counter += 1
        w.dismiss
        res
      }
    }
    val now = new Date().getTime
    a.init
    w.await(awaitTimeout, dismissals(1))
    val after = new Date().getTime
    a.shutdown
    assert(counter > 0)
    assert((after - now) > (2 * 1000L))
  }
  test("should wait successDelay times on success"){
    var counter = 0
    var firstFailure:Boolean = true
    val w = new Waiter
    val t = new TimedMeTLingPotAdaptor(0L,0L,() => false)
    val poll = Some(new TimeSpan(0L))
    val success = Some(new TimeSpan(2 * 1000L))
    val error = Some(new TimeSpan(0L))
    val a = new BurstingPassThroughMeTLingPotAdaptor(t,5,poll,success,error){
      override protected def onSuccess(res:Boolean) = {
        val result = super.onSuccess(res)
        counter += 1
        w.dismiss
        result
      }
    }
    val fakeKVP = KVP("testType","testValue")
    val now = new Date().getTime
    a.init
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    w.await(awaitTimeout, dismissals(2))
    val after = new Date().getTime
    a.shutdown
    assert(counter > 0)
    assert((after - now) > (2 * 1000L))
  }
  test("should not wait error times on success"){
    var counter = 0
    val w = new Waiter
    val poll = Some(new TimeSpan(0L))
    val success = Some(new TimeSpan(0L))
    val error = Some(new TimeSpan(5 * 60 * 1000L))
    val a = new BurstingPassThroughMeTLingPotAdaptor(new TimedMeTLingPotAdaptor(0L,0L,()=>false),2,poll,success,error){
      override protected def onSuccess(res:Boolean) = {
        val result = super.onSuccess(res)
        counter += 1
        w.dismiss
        result
      }
    }
    val fakeKVP = KVP("testType","testValue")
    a.init
    val now = new Date().getTime
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    w.await(awaitTimeout, dismissals(5))
    val after = new Date().getTime
    a.shutdown
    assert(counter == 5)
    val timeTaken = (after - now)
    assert(timeTaken < (5 * 60 * 1000L))
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
    val poll = Some(new TimeSpan(0L))
    val success = Some(new TimeSpan(0L))
    val error = Some(new TimeSpan(3 * 1000L))
    val a = new BurstingPassThroughMeTLingPotAdaptor(t,20,poll,success,error){
      override protected def onError(items:List[MeTLingPotItem],e:Exception) = {
        val res = super.onError(items,e)
        counter += 1
        w.dismiss
        res
      }
      override protected def onSuccess(res:Boolean) = {
        val result = super.onSuccess(res)
        counter += 1
        w.dismiss
        result
      }
    }
    val fakeKVP = KVP("testType","testValue")
    a.init
    val now = new Date().getTime
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    w.await(awaitTimeout, dismissals(2))
    val after = new Date().getTime
    a.shutdown
    assert(counter > 0)
    assert((after - now) > (3 * 1000L))
  }
}
