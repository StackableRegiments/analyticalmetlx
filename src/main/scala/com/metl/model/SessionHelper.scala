package net.liftweb.http

import net.liftweb.common._

object SessionVarHelper {
  def getFromSession[T](s:LiftSession,name:String):Box[T] = {
    s.get(name)
  }
}
