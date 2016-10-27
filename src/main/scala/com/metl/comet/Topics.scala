package com.metl.comet
import com.metl.utils.Stopwatch
import com.metl.model._
import net.liftweb._
import http._
import actor._
import util._
import common._
import util.Helpers._
import js._
import JsCmds._
import JE._
import js.jquery.JqJsCmds.{AppendHtml, FadeOut, Hide, FadeIn}
import java.util.Date
import scala.xml.{Text, NodeSeq}
import com.metl.model.Globals._
import net.liftweb.http.SHtml._

case class TopicActivity(topicIdentity:String)
case class NewTopic(topic:Topic)

object TopicServer extends LiftActor with ListenerManager with Logger{
  def fetchFromDB = TopicManager.getAll
  var topics = fetchFromDB
  def getTopics = topics
  def createUpdate = topics
  override def lowPriority = {
    case TopicActivity(topicActivity) => Stopwatch.time("TopicServer:topicActivity",{
      topics.find(t => t.identity == topicActivity).map(t => sendListenersMessage(t))
    })
    case NewTopic(topic) => {
      topics = topic :: (topics.filterNot(t => t.teachingEventIdentity.get == topic.teachingEventIdentity.get))
      sendListenersMessage(topics)
      sendListenersMessage(topic)
    }
    case other => warn("TopicServer received unknown message: %s".format(other))
  }
}

class TopicActor extends CometActor with CometListener with Logger {
  def registerWith = TopicServer
  override def lifespan:Box[TimeSpan] = Full(1 minute)
  def namesHtmlCssBind(topics:List[Topic]) =
    "#topicListing *" #> topics.filter(topic => !topic.deleted.get).sortWith((a,b) => a.name.get < a.name.get).map(topic => {
      ".topicName [href]" #> "/stack/%s".format(topic.teachingEventIdentity.get) &
      ".topicName [id]" #> "topic_%s".format(topic.teachingEventIdentity.get) &
      ".topicName *" #> Text(topic.name.get) &
      ".topicName [title]" #> topic.name.get
    })
  def updateNames(topics:List[Topic]) = {
    partialUpdate(SetHtml("topicListing",namesHtmlCssBind(topics).apply(StackTemplateHolder.topicTemplate)))
  }
  val createTopicLink = {
    a(()=>{
      val message = MeTLInteractableMessage(
        self =>  ajaxForm(
          NodeSeq.fromSeq(
            List(
              <div>{
                Text("What will your new topic be called?")
              }</div>,
              <div>{
                text("",(input:String) => {
                  TopicManager.createTopic(input)
                }, ("id","createTopicInputBox"),("class","oneLineTextbox"))
              }</div>,
              <div>{
                ajaxSubmit("Submit",()=>{
                  self.done
                  Noop
                },("id","createTopicSubmitButton"),("class","submitButton"))
              }</div>
            )
          )
        ) ++ Script(After(0,Focus("createTopicInputBox"))),true).entitled("Create a topic")
      Notices.local(message)
      Noop
    }, Text("Add topic") ,("id","createTopicLink"))
  }
  def indicateActivity(topic:String) = partialUpdate(Call("divJiggle","#topic_%s".format(topic)))
  override def lowPriority = {
    case names:List[Topic] => Stopwatch.time("Topics:list[string]",updateNames(names))
    case topic:Topic => Stopwatch.time("Topics:topicActivity",indicateActivity(topic.identity))
    case other => warn("TopicActor received unknown message: %s".format(other))
  }
  override def fixedRender = Stopwatch.time("Topics:fixedRender",((BubbleConstants.fullAdmins(Globals.currentUser.is) match {
    case true => "#topicAddButton *" #> createTopicLink
    case _ => "#topicAddButton" #> NodeSeq.Empty
  })))
  override def render = Stopwatch.time("Topics:render",{
    updateNames(TopicServer.getTopics)
    NodeSeq.Empty
  })
}
