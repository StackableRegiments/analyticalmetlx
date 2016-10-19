package com.metl.comet

import com.metl.utils.Stopwatch
import com.metl.model._
import net.liftweb._
import net.liftweb.common._
import http._
import SHtml._
import actor._
import util._
import util.Helpers._
import js._
import JsCmds._
import JE._
import js.jquery.JqJsCmds.{AppendHtml, FadeOut, Hide, FadeIn}
import java.util.Date
import scala.xml.{Text, NodeSeq}
import com.metl.model.Globals._
import js.jquery
import net.liftweb.json.JsonAST._

case object RefreshAllStandings
case object StartingRepActor

object ReputationServer extends LiftActor with ListenerManager with Logger {
  def createUpdate = StartingRepActor
  override def lowPriority = {
    case reps:List[Informal] => Stopwatch.time("ReputationServer:lowPriority:list[Informal] (%s)".format(reps),reps.foreach(rep => {}))//XMPPRepSyncActor ! ReputationSyncRequest(rep.protagonist.is,rep.action.toInt.openOr(0))))
    case rep:Informal => Stopwatch.time("ReputationServer:lowPriority:informal (%s)".format(rep),{})//XMPPRepSyncActor ! ReputationSyncRequest(rep.protagonist.is,rep.action.toInt.openOr(0)))
    case local:Standing => Stopwatch.time("ReputationServer:lowPriority:standing (%s)".format(local),sendListenersMessage(local) )
    case other => {
      warn("Rep server received unknown message: %s".format(other.toString))
    }
  }
}
object ReputationActor{
  def local(message:Any, session:Box[LiftSession]):Unit = session.map(s => s.sendCometActorMessage("ReputationActor", Box.legacyNullTest(currentUser.is), message))
}
class ReputationActor extends CometActor with CometListener with Logger {
  def registerWith = ReputationServer
  override def lifespan:Box[TimeSpan] = Full(1 minute)
  private var currentStanding:Int = Reputation.standing(currentUser.is).formative
  override def lowPriority = {
    case s:Standing => recieveStanding(s)
    case RefreshAllStandings => recieveRefreshAllStandings
    case StartingRepActor => {}
    case other => warn("ReputationActor received unknown message: %s".format(other.toString))
  }
  def recieveStanding(s:Standing) = Stopwatch.time("ReputationActor:recieveStanding(%s)".format(s),{partialUpdate(updatePersonalRep(s) & updateAll(s))})
  def recieveRefreshAllStandings = Stopwatch.time("ReputationActor:recieveRefreshAllStandings", {
    val allStandings = com.metl.model.Reputation.allStandings
    debug("updating all standings with: %s".format(allStandings))
    partialUpdate(allStandings.foldLeft(Noop)((acc,item) => acc & updateAll(item)))
  })
  private def fadeOutAndRemove(id:String):JsCmd = Call("fadeOutAndRemove", "#%s".format(id))
  def updatePersonalRep(i:Standing):JsCmd = {
    val standingChange = i.formative - currentStanding
    if (i.who == currentUser.is && standingChange  > 0){
      val id = nextFuncName
      val delta = <span id={id} class="informal">{"+%s".format(standingChange.toString)}</span>;
      currentStanding = i.formative
      AppendHtml("delta",delta) &
      fadeOutAndRemove(id) &
      SetHtml("formative",Text(i.formative.toString))
    }
    else Noop
  }
  def updateAll(i:Standing):JsCmd = {
    i match {
      case Standing(who,form,summ) if who != "" => Call("setRepForUser",JArray(List(JString(who),JInt(form),JInt(summ))))
      case _ => Noop
    }
  }
  override def render = NodeSeq.Empty
  override def fixedRender = Stopwatch.time("ReputationActor:fixedRender",{
    val totalScore = Reputation.standing(currentUser.is)
    "#formative *" #> totalScore.formative &
    "#summative *" #> totalScore.summative &
    "#delta *" #> NodeSeq.Empty &
    "#refreshAllButton *" #> a(()=> {
      this ! RefreshAllStandings
      Noop
    },Text("RefreshAll"))
  })
}
