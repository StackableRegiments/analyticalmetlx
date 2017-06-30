package com.metl.utils

import net.liftweb.actor.LiftActor
import net.liftweb.http._
import net.liftweb.util.Helpers.TimeSpan
import net.liftweb.util.Schedule

case object Refresh
case object Stop
class PeriodicallyRefreshingVar[T](acceptedStaleTime:TimeSpan, valueCreationFunc:()=>T, startingValue:Option[T] = None) extends LiftActor{
	protected var lastResult:T = startingValue.getOrElse(valueCreationFunc())
  protected var running:Boolean = true;
  protected def stop:Unit = {
    running = false
  }
	scheduleRecheck
	protected def scheduleRecheck:Unit = {
    if (running){
      Schedule.schedule(this,Refresh,acceptedStaleTime:TimeSpan)
    }
  }
	protected def doGet:Unit = {
		lastResult = valueCreationFunc()
		scheduleRecheck	
	}
	def get:T = lastResult
	override def messageHandler = {
		case Refresh => doGet
    case Stop => stop
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
