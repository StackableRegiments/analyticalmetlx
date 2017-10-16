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
  /*
  test("should send chunks of 2, for approximately 5 seconds") {
    val a = new BurstingPassThroughMeTLingPotAdaptor(new TimedMeTLingPotAdaptor(3000L,0L,() => false),2,Some(new TimeSpan(1000L)),Some(new TimeSpan(10 * 1000L))){
      def getBufferLength:Int = buffer.length
      override protected def reschedule(after:TimeSpan):Unit = {
        println("TICK: %s".format(buffer.length))
        super.reschedule(after)
      }
    }
    a.init
    val fakeKVP = KVP("testType","testValue")
    a.postItems(Range(0,10).map(i => MeTLingPotItem("test",0L,fakeKVP,fakeKVP,None,None,None)).toList)
    val b1 = a.getBufferLength
    Thread.sleep(4000L)
    val b2 = a.getBufferLength
    Thread.sleep(4000L)
    val b3 = a.getBufferLength
    Thread.sleep(4000L)
    val b4 = a.getBufferLength
    Thread.sleep(4000L)
    val b5 = a.getBufferLength
    Thread.sleep(4000L)
    val b6 = a.getBufferLength
    a.shutdown
    assert(b1 == 10 || b1 == 8)
    assert(b2 == 8 || b2 == 6)
    assert(b3 == 6 || b3 == 4)
    assert(b4 == 4 || b4 == 2)
    assert(b5 == 2 || b5 == 0)
    assert(b6 == 0)
  }
  */
  test("should send chunks of 2, five times") {
    var counter = 0
    val w = new Waiter
    val a = new BurstingPassThroughMeTLingPotAdaptor(new TimedMeTLingPotAdaptor(0L,0L,()=>false),2,Some(new TimeSpan(0L)),Some(new TimeSpan(0L))){
      def getBufferLength:Int = buffer.length
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
  /*
  test("should not wait delay times on errors"){
    var counter = 0
    val w = new Waiter
    val a = new BurstingPassThroughMeTLingPotAdaptor(new TimedMeTLingPotAdaptor(0L,0L,()=>true),2,Some(new TimeSpan(20*1000L)),Some(new TimeSpan(0L))){
      def getBufferLength:Int = buffer.length
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
  */
  test("should wait delay times on success"){
    var counter = 0
    var firstFailure:Boolean = true
    val w = new Waiter
    val t = new TimedMeTLingPotAdaptor(0L,0L,() => false)
    val a = new BurstingPassThroughMeTLingPotAdaptor(t,20,Some(new TimeSpan(2 * 1000L)),Some(new TimeSpan(0L))){
      def getBufferLength:Int = buffer.length
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
      def getBufferLength:Int = buffer.length
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
      def getBufferLength:Int = buffer.length
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
