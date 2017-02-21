package com.metl.comet

import com.metl.data._
import com.metl.utils._
import com.metl.liftExtensions._

import net.liftweb._
import common._
import http._
import util._
import Helpers._
import HttpHelpers._
import actor._
import scala.xml._
import com.metl.model._
import SHtml._

import js._
import JsCmds._
import JE._
import net.liftweb.http.js.jquery.JqJsCmds._

import net.liftweb.http.js.jquery.JqJE._

case class TrainingControl(label:String,behaviour:()=>JsCmd)
case class TrainingPage(title:NodeSeq,instructions:NodeSeq,blurb:NodeSeq,controls:Seq[TrainingControl])

case object SimulatorTick

trait SimulatedActivity
case class Watching(ticks:Int) extends SimulatedActivity
case class Scribbling(what:List[Char]) extends SimulatedActivity

case class SimulatedUser(name:String,claim:List[Point],focus:Point,intention:String,activity:SimulatedActivity,history:List[SimulatedActivity]){
  def pan(x:Int,y:Int = 0) = copy(focus = Point(focus.x + x,focus.y + y,0))
}
case class TrainingManual(actor:TrainerActor) {
  val pages = List(
    TrainingPage(Text("Exercise 1"),
      Text("These controls will bring virtual students into your classroom.  Bring one in now."),
      Text("Sharing an open space"),
      List("benign","malicious").map(intent => TrainingControl(
        "Bring in a %s student".format(intent),() => {
          actor.users = SimulatedUser(nextFuncName,List(Point(0,0,0),Point(0,0,0)),Point(0,0,0),"benign",Watching(0),List.empty[SimulatedActivity]) :: actor.users
        }))))
}

class TrainerActor extends StronglyTypedJsonActor with Logger {
  var manual = new TrainingManual(this)
  var users = List.empty[SimulatedUser]
  def registerWith = MeTLActorManager
  var currentPage:TrainingPage = manual.pages(0)
  protected var currentSlide:Box[String] = Empty
  protected val username = Globals.currentUser.is
  protected lazy val serverConfig = ServerConfiguration.default
  protected lazy val server = serverConfig.name

  override lazy val functionDefinitions = List.empty[ClientSideFunction]

  val rand = new scala.util.Random
  val sum = 0.0

  override def lowPriority  = {
    case SimulatorTick => {
      users = users.map {
        case u@SimulatedUser(name,claim,focus,intention,Watching(ticks),history) => rand.nextInt(10) match {
          case 1 => u.copy(activity = Scribbling(name.toList))
          case _ => u.copy(activity = Watching(ticks + 1))
        }
        case u@SimulatedUser(name,claim,focus,intention,Scribbling(Nil),history) => u.copy(activity = Watching(0))
        case u@SimulatedUser(name,claim,focus,intention,Scribbling(h :: t),history) if h == ' ' => u.pan(50)
        case u@SimulatedUser(name,claim,focus,intention,Scribbling(h :: t),history) => {
          currentSlide.map(slide => {
            val room = MeTLXConfiguration.getRoom(slide,server)
            Alphabet.geometry(h,focus).map(geometry => room ! LocalToServerMeTLStanza(MeTLInk(serverConfig,name,new java.util.Date().getTime,sum,sum,
              points,intention match {
                case "benign" => Color(255,255,0,0)
                case "malicious" => Color(255,0,0,255)
                case _ => Color(255,0,255,0)
              },2.0,false,"presentationSpace",Privacy.PUBLIC,slide.toString,nextFuncName)))
            u.pan(50).copy(activity = Scribbling(t),history = Scribbling(List(h)) :: u.history)
          })
        }
      }
      ActorPing.schedule(this,SimulatorTick,500)
    }
  }

  override def localSetup = {
    super.localSetup
    val newConversation = serverConfig.createConversation(nextFuncName,username)
    val slide = newConversation.slides(0).id.toString
    currentSlide = Full(slide)
    partialUpdate(Call("simulationOn",newConversation.jid,slide).cmd)
    ActorPing.schedule(this,SimulatorTick,500)
  }

  override def render = "#exerciseTitle *" #> currentPage.title &
  "#exerciseBlurb *" #> currentPage.blurb &
  "#exerciseInstructions *" #> currentPage.instructions &
  "#exerciseControls .control *" #> currentPage.controls.map(c =>
    ajaxButton(c.label,c.behaviour))
}

object Alphabet {
  def geometry(c:Char,p:Point) = Range(0,50).map(i => Point(p.x,p.y + i,0)).toList
}
