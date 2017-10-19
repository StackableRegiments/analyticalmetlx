package com.metl.model

import net.liftweb.common._
import net.liftweb.actor._
import org.scalatest._
import org.scalatest.time.SpanSugar._
import matchers.MustMatchers
import concurrent.AsyncAssertions
import com.metl.data._

class RoomsSuite extends FunSuite with AsyncAssertions with MustMatchers {
  private val config = ServerConfiguration.empty
  private val conversationRoomId = "1000"
  private val slideRoomId = "1001"

  test("should send Attendance to ConversationRoom when slide room joined") {
    var attendances = scala.collection.mutable.ListBuffer.empty[Tuple3[String,RoomMetaData,Boolean]]
    val roomJoinedWaiter = new Waiter
    val roomJoinedMetaData = ConversationRoom(config,conversationRoomId)
    val roomJoined = new HistoryCachingRoom(config,slideRoomId,EmptyRoomProvider,roomJoinedMetaData,Some(1000L)) {
      override protected def emitAttendance(username:String,targetRoom: RoomMetaData,present: Boolean):Unit = {
        warn("Received present = %s for room %s from user %s".format(present,targetRoom,username))
        attendances  += ((username,targetRoom,present))
        roomJoinedWaiter.dismiss
      }
    }
    roomJoined.localSetup

    val joinerWaiter = new Waiter
    object joiner extends LiftActor with Logger {
      override def messageHandler: PartialFunction[Any, Unit] = {
        case r@RoomJoinAcknowledged(configName,room) => {
          warn("Received roomJoinAcknowledged %s".format(r))
          joinerWaiter.dismiss
        }
      }
    }

    val user = "bob"
    roomJoined ! JoinRoom(user,"1",joiner)

    joinerWaiter.await(timeout(5000 millis),dismissals(1))
    roomJoinedWaiter.await(timeout(10000 millis),dismissals(1))

    roomJoined.localShutdown

    assert(attendances.length == 1)
    assert(attendances.head._1 == user)
    assert(attendances.head._2 == GlobalRoom(ServerConfiguration.empty))
    assert(attendances.head._3 == true)
  }
}