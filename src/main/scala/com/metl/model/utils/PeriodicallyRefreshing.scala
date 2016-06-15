package com.metl.utils

import net.liftweb.http._
import net.liftweb.common._
import net.liftweb.util.Schedule
import net.liftweb.util.Helpers.TimeSpan
import net.liftweb.actor.LiftActor

case object Refresh
class PeriodicallyRefreshingVar[T](acceptedStaleTime:TimeSpan, valueCreationFunc:()=>T) extends LiftActor{
	private var lastResult:T = valueCreationFunc()
	scheduleRecheck
	private def scheduleRecheck:Unit = Schedule.schedule(this,Refresh,acceptedStaleTime:TimeSpan)
	private def doGet:Unit = {
		lastResult = valueCreationFunc()
		scheduleRecheck	
	}
	def get:T = lastResult
	override def messageHandler = {
		case Refresh => doGet
		case _ => {}
	}
}

class ChangeNotifyingSessionVar[T](dflt: =>T) extends SessionVar[T](dflt){
    private var onChange:List[T=>Unit] = List.empty[T=>Unit]
    override def setFunc(name:String,value:T)={
    		super.setFunc(name,value)
        onChange.foreach(handler=>handler(value))
    }
    def subscribe(handler:T=>Unit)= onChange = handler :: onChange
    def unsubscribe(handler:T=>Unit)= onChange = onChange.filterNot(_ == handler)
}
/*
class PeriodicallyExpiringMap[A,B](frequency:TimeSpan) extends LiftActor{
	private def scheduleRecheck:Unit = Schedule.schedule(this,Refresh,frequency:TimeSpan)
	def apply(k:A) = {
	}
	private def checkForExpiry = {
	}
	override def messageHandler = {
		case Refresh => checkForExpiry
		case _ => {}
	}
}
*/
