package com.metl.comet

import com.metl.utils.Stopwatch
import net.liftweb.http._
import js.JsCmds
import js.JsCmds._
import net.liftweb.http.js.jquery.JqJsCmds._
import net.liftweb.http.js.jquery.JqJE._
import net.liftweb.http.SHtml._
import net.liftweb.common._
import S._
import net.liftweb.util._
import Helpers._
import net.liftweb.actor.LiftActor
import com.metl.model.Stopwords
import xml.{NodeSeq, Text}
import collection.mutable.ListBuffer
import ElemAttr._
import com.metl.model.Globals._
import com.metl.model._

object MeTLSpamServer extends LiftActor with ListenerManager with Logger{
  private var latestNotice:MeTLMessage = Clear
  def createUpdate = latestNotice
  override def lowPriority = {
    case notice:MeTLMessage =>{
      latestNotice = notice
      updateListeners()
    }
    case other => warn("MeTLSpamServer received unknown message: %s".format(other))
  }
}
trait MeTLMessage{
  val id:String = nextFuncName
  var uniqueId:String = id
}
case object Clear extends MeTLMessage
case class MeTLInteractableMessage(scope:MeTLInteractableMessage=>NodeSeq,cancellable:Boolean=false) extends MeTLMessage{
  val seq = scope(this)
  private type Doable = ()=>Unit
  private var doThese = List.empty[Doable]
  var title:Box[String] = Empty
  def done ={
    doThese.foreach(doThis =>doThis())
    Noop
  }
  def onDone(doThis:Doable){
    doThese = doThis :: doThese
  }
  def entitled(t:String) = {
    title = Full(t)
    this
  }
  def identifiedBy(t:String) = {
    uniqueId = t
    this
  }
}
case class MeTLSpam(val content:NodeSeq) extends MeTLMessage
object Notices{
  def local(message:MeTLMessage) = {
    for(session <- S.session)
      session.sendCometActorMessage("Notices", Box.legacyNullTest(noticesName(currentUser.is)), message)
  }
}
class Notices extends CometActor with CometListener with Logger{
  override def lifespan:Box[TimeSpan] = Full(1 minute)
  private val id = nextFuncName
  private var visibleMessages = List.empty[MeTLMessage]
  def registerWith = MeTLSpamServer
  private def removeMessage(message:MeTLMessage) = Hide(message.id) & Replace(message.id,NodeSeq.Empty)
  private def remove(message:MeTLMessage) = Stopwatch.time("Notices:remove(%s)".format(message),{
    visibleMessages = visibleMessages.filterNot(m => m == message)
    partialUpdate(removeMessage(message))
  })
  private def doSpam(spam:MeTLSpam) = Stopwatch.time("Notices:doSpam(%s)".format(spam),{
    (".spamMessage" #> ((n:NodeSeq) => a(()=>removeMessage(spam),(".noticeContent" #> spam.content).apply(n),("id",spam.id)))
    ).apply(StackTemplateHolder.spamTemplate)
  })
  private def doInteractable(message:MeTLInteractableMessage) = Stopwatch.time("Notices:doInteractable(%s)".format(message),{
    message.onDone(()=>remove(message))
      (".noticeLabel *" #> message.title.openOr("Response") &
        ".noticeContent" #> ajaxForm(message.seq) &
        ".metlSpam [id+]" #> message.id &
        ".noticeClose" #> ((n:NodeSeq) => if (message.cancellable) a(()=>message.done,n) else NodeSeq.Empty)
      ).apply(StackTemplateHolder.interactableMessageTemplate)
  })
  private def renderMessage(message:MeTLMessage) ={
    message match{
      case spam@MeTLSpam(_) => doSpam(spam)
      case interactable@MeTLInteractableMessage(_,_) => doInteractable(interactable)
    }
  }
  override def fixedRender = <div id={id} />
  override def render = NodeSeq.Empty
  override def lowPriority = {
    case Clear => {}
    case message:MeTLMessage=> Stopwatch.time("Notices:message",{
      val removalFunction = visibleMessages.find(m => m.uniqueId == message.uniqueId).map(em => removeMessage(em)).getOrElse(Noop)
      visibleMessages = message :: visibleMessages
      partialUpdate(removalFunction & PrependHtml(id,renderMessage(message)))
    })
    case other => warn("Notices received unknown message: %s".format(other))
  }
}
