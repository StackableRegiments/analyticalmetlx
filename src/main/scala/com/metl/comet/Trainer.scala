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

object TrainingManual {
  def pages = List(
    TrainingPage(Text("Getting started"),
      Text("These controls will create virtual students for your classroom, who will interact with you.  Bring one in now."),
      Text("Get a feel for it."),
      List(TrainingControl("Create",() => {}))))
}

class TrainerActor extends StronglyTypedJsonActor with Logger {
  def registerWith = MeTLActorManager
  var currentPage:TrainingPage = TrainingManual.pages(0)
  protected var currentSlide:Box[String] = Empty
  protected val username = Globals.currentUser.is
  protected lazy val serverConfig = ServerConfiguration.default

  override lazy val functionDefinitions = List.empty[ClientSideFunction]

  override def localSetup = {
    super.localSetup
    val newConversation = serverConfig.createConversation(nextFuncName,username)
    val slide = newConversation.slides(0).id.toString
    currentSlide = Full(slide)
    partialUpdate(Call("simulationOn",newConversation.jid,slide).cmd)
  }

  override def render = "#exerciseTitle *" #> currentPage.title &
  "#exerciseBlurb *" #> currentPage.blurb &
  "#exerciseInstructions *" #> currentPage.instructions &
  "#exerciseControls *" #> currentPage.controls.map(c =>
    ajaxButton(c.label,c.behaviour))
}
