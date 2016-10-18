package com.metl.snippet

import org.apache.commons.io.IOUtils
import net.liftweb.http._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonParser._
import js._
import js.JsCmds._
import js.JE._
import js.jquery.JqJE._
import net.liftweb.http.SHtml._
import net.liftweb.common._
import S._
import net.liftweb.util._
import net.liftweb.actor.LiftActor
import xml.{NodeSeq, Text}
import ElemAttr._
import com.metl.model.Globals._
//import scala.actors.Futures._
import com.metl.model._
import com.metl.comet._

class Summaries extends Logger {
  private var topicNames = Map.empty[String,String]
  private def allQuestions ={
    def allQuestionsForTopic(topic:Topic) = {
      val qs = StackQuestion.findAll("teachingEvent",topic.teachingEventIdentity.get).map(new QuestionPresenter(_))
      topicNames = topicNames.updated(topic.teachingEventIdentity.get.toString,topic.name.get)
      qs
    }
    TopicManager.getAll.map(allQuestionsForTopic).flatten.toList
  }
  def json(v:Vote):JObject=
    JObject(List(
      JField("author",v.author.name),
      JField("shift",JInt(v.shift)),
      JField("time",JInt(v.time))
    ))
  def json(d:DiscussionPoint):JObject=
    JObject(List(
      JField("author",d.author.name),
      JField("content",d.content)
    ))
  def json(c:StackComment):JObject=
    JObject(List(
      JField("id",c.id),
      JField("parentId",c.parentId),
      JField("creationDate",JInt(c.creationDate)),
      JField("comments",JArray(c.comments.sortBy(_.creationDate).map(json))),
      JField("votes",JArray(c.votes.sortBy(_.time).map(json))),
      JField("deleted",JBool(c.deleted)),
      JField("discussion",json(c.about))
    ))
  def json(a:StackAnswer):JObject=
    JObject(List(
      JField("id",a.id),
      JField("parentId",a.parentId),
      JField("creationDate",JInt(a.creationDate)),
      JField("comments",JArray(a.comments.sortBy(_.creationDate).map(json))),
      JField("votes",JArray(a.votes.sortBy(_.time).map(json))),
      JField("deleted",JBool(a.deleted)),
      JField("discussion",json(a.about))
    ))
  def json(q:QuestionPresenter):JObject={
    val c = q.context
    JObject(List(
      JField("id",JString(c.id.toString)),
      JField("teachingEvent",JString(topicNames(c.teachingEvent.get))),
      JField("creationDate",JInt(c.creationDate.get)),
      JField("answers",JArray(c.answers.get.sortBy(_.creationDate).map(json))),
      JField("votes",JArray(c.votes.get.sortBy(_.time).map(json))),
      JField("deleted",JBool(c.deleted.get)),
      JField("discussion",json(c.about.get))
    ))
  }
  def render(x:NodeSeq):NodeSeq = {
    val items = allQuestions
    debug("Immediately rendering for %s stack items".format(items.length))
    Script(OnLoad(
      Call("updateStandings",JObject(List(Reputation.allStandings.map{
        case Standing(who,formative,_)=> JField(who,JInt(formative))
      }:_*))) &
        Call("receiveQuestions",JArray(List(items.map(json):_*)))))
  }
}
