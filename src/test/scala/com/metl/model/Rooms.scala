package com.metl.model

import net.liftweb.common._
import net.liftweb.actor._
import org.scalatest._
import org.scalatest.time.SpanSugar._
import matchers.MustMatchers
import concurrent.AsyncAssertions
import com.metl.data._

class RoomsSuite extends FunSuite with AsyncAssertions with MustMatchers with Logger {
  val awaitTimeout = timeout(5 * 60 * 1000 millis) // 5 minutes
  private val config = ServerConfiguration.empty
  private val conversationRoomId = "1000"
  private val slideRoomId = "1001"

  test("should send Attendance(present=true) to ConversationRoom when slide room joined") {
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

    joinerWaiter.await(awaitTimeout,dismissals(1))
    roomJoinedWaiter.await(awaitTimeout,dismissals(1))

    roomJoined.localShutdown

    assert(attendances.length == 1)
    assert(attendances.head._1 == user)
    assert(attendances.head._2 == GlobalRoom(ServerConfiguration.empty))
    assert(attendances.head._3 == true)
  }

  test("should send Attendance(present=false) to ConversationRoom when slide room left") {
    var attendances = scala.collection.mutable.ListBuffer.empty[Tuple3[String,RoomMetaData,Boolean]]
    val roomLeftWaiter = new Waiter
    val roomLeftMetaData = ConversationRoom(config,conversationRoomId)
    val leaverWaiter = new Waiter
    object leaver extends LiftActor with Logger {
      override def messageHandler: PartialFunction[Any, Unit] = {
        case r@RoomLeaveAcknowledged(configName, room) => {
          warn("Received roomLeaveAcknowledged %s".format(r))
          leaverWaiter.dismiss
        }
      }
    }
    val userCometId = "1"
    val user = "bob"
    val roomLeft = new HistoryCachingRoom(config,slideRoomId,EmptyRoomProvider,roomLeftMetaData,Some(1000L)) {
      joinedUsers = (user,userCometId,leaver) :: joinedUsers
      override protected def emitAttendance(username:String,targetRoom: RoomMetaData,present: Boolean):Unit = {
        warn("Received present = %s for room %s from user %s".format(present,targetRoom,username))
        attendances  += ((username,targetRoom,present))
        roomLeftWaiter.dismiss
      }
    }
    roomLeft.localSetup

    roomLeft ! LeaveRoom(user,userCometId,leaver)

    leaverWaiter.await(awaitTimeout,dismissals(1))
    roomLeftWaiter.await(awaitTimeout,dismissals(1))

    roomLeft.localShutdown

    warn("Attendances: %s".format(attendances))

    assert(attendances.length == 1)
    val attendanceLeftRoom = attendances.head
    assert(attendanceLeftRoom._1 == user)
    assert(attendanceLeftRoom._2 == GlobalRoom(ServerConfiguration.empty))
    assert(attendanceLeftRoom._3 == false)
  }

  test("should not send Attendance to ConversationRoom when a different slide room joined") {
    var attendances1 = scala.collection.mutable.ListBuffer.empty[Tuple3[String,RoomMetaData,Boolean]]
    val roomJoinedWaiter = new Waiter
    val roomJoinedMetaData = ConversationRoom(config,conversationRoomId)
    val roomJoined = new HistoryCachingRoom(config,slideRoomId,EmptyRoomProvider,roomJoinedMetaData,Some(1000L)) {
      override protected def emitAttendance(username:String,targetRoom: RoomMetaData,present: Boolean):Unit = {
        warn("Received present = %s for room %s from user %s".format(present,targetRoom,username))
        attendances1 += ((username,targetRoom,present))
        roomJoinedWaiter.dismiss
      }
    }
    roomJoined.localSetup

    var attendances2 = scala.collection.mutable.ListBuffer.empty[Tuple3[String,RoomMetaData,Boolean]]
    val roomNotJoinedMetaData = ConversationRoom(config,"2000")
    val roomNotJoined = new HistoryCachingRoom(config,"2001",EmptyRoomProvider,roomNotJoinedMetaData,Some(1000L)) {
      override protected def emitAttendance(username:String,targetRoom: RoomMetaData,present: Boolean):Unit = {
        warn("Received present = %s for room %s from user %s".format(present,targetRoom,username))
        attendances2 += ((username,targetRoom,present))
        roomJoinedWaiter.dismiss
      }
    }
    roomNotJoined.localSetup

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

    joinerWaiter.await(awaitTimeout,dismissals(1))
    roomJoinedWaiter.await(awaitTimeout,dismissals(1))

    roomNotJoined.localShutdown
    roomJoined.localShutdown

    assert(attendances1.length == 1)
    assert(attendances1.head._1 == user)
    assert(attendances1.head._2 == GlobalRoom(ServerConfiguration.empty))
    assert(attendances1.head._3 == true)

    assert(attendances2.length == 0)
  }

  test("should not send multiple Attendance(present=true) to ConversationRoom when slide room joined by different actors for the same user") {
    var attendances = scala.collection.mutable.ListBuffer.empty[Tuple3[String,RoomMetaData,Boolean]]
    val roomJoinedWaiter = new Waiter
    val roomJoinedMetaData = ConversationRoom(config,conversationRoomId)
    val roomJoined = new HistoryCachingRoom(config,slideRoomId,EmptyRoomProvider,roomJoinedMetaData,Some(1000L)) {
      override protected def emitAttendance(username:String,targetRoom: RoomMetaData,present: Boolean):Unit = {
        warn("Received present = %s for room %s from user %s".format(present,targetRoom,username))
        attendances += ((username,targetRoom,present))
        roomJoinedWaiter.dismiss
      }
    }
    roomJoined.localSetup

    val joinerWaiter1 = new Waiter
    object joiner1 extends LiftActor with Logger {
      override def messageHandler: PartialFunction[Any, Unit] = {
        case r@RoomJoinAcknowledged(configName,room) => {
          warn("Received roomJoinAcknowledged %s".format(r))
          joinerWaiter1.dismiss
        }
      }
    }

    val joinerWaiter2 = new Waiter
    object joiner2 extends LiftActor with Logger {
      override def messageHandler: PartialFunction[Any, Unit] = {
        case r@RoomJoinAcknowledged(configName,room) => {
          warn("Received roomJoinAcknowledged %s".format(r))
          joinerWaiter2.dismiss
        }
      }
    }

    val user = "bob"
    roomJoined ! JoinRoom(user,"1",joiner1)
    roomJoined ! JoinRoom(user,"1",joiner2)

    joinerWaiter1.await(awaitTimeout,dismissals(1))
    joinerWaiter2.await(awaitTimeout,dismissals(1))
    roomJoinedWaiter.await(awaitTimeout,dismissals(1))

    roomJoined.localShutdown

    assert(attendances.length == 1)
    assert(attendances.head._1 == user)
    assert(attendances.head._2 == GlobalRoom(ServerConfiguration.empty))
    assert(attendances.head._3 == true)
  }
}
