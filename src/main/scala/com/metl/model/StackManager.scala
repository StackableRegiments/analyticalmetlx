package com.metl.model

import com.metl.utils.{Stopwatch,PeriodicallyRefreshingVar}
import java.util.Date
import org.apache.commons.io.IOUtils
import net.liftweb.http._
import scala.collection.mutable.{HashMap, SynchronizedMap}
import net.liftweb.mongodb.{Limit}
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
import Helpers._
import net.liftweb.actor.LiftActor
import xml.{NodeSeq, Text}
import collection.mutable.ListBuffer
import ElemAttr._
import org.bson.types.ObjectId
import com.metl.model._
import com.metl.model.Globals._

case object Refresh

object TopicManager extends Logger {
  protected val cachedTopics = Stopwatch.time("TopicManager:cachedTopics", new PeriodicallyRefreshingVar[List[Topic]](2 minutes,()=>{
    debug("TopicManager loading all topics")
    try {
      Topic.findAll
    } catch {
      case e:Throwable => {
        error("failed to get topics",e)
        List.empty[Topic]
      }
    }
  }))
  def preloadAllTopics = Stopwatch.time("TopicManager:preloadAllTopics", getAll.foreach(preloadTopic))
  def preloadTopic(topic:Topic) = Stopwatch.time("TopicManager:preloadTopic %s".format(topic), com.metl.comet.StackServerManager.get(topic.teachingEventIdentity.get).questions)
  def getAll = Stopwatch.time("TopicManager:getAll", cachedTopics.get match {
    case listOfTopics:List[Topic] => listOfTopics.filter(_.creator.get != "rob")
    case _ => List.empty[Topic]
  })
  def get(dbId:String):Box[Topic] = Stopwatch.time("TopicManager:get(%s)".format(dbId), {
    cachedTopics.get match {
      case listOfTopics:List[Topic] => {
        listOfTopics.find(t => t.teachingEventIdentity.get == dbId) match {
          case Some(topic) => Full(topic)
          case _ => Empty
        }
      }
      case _ => Empty
    }
  })
  def updateTopics(topicId:String) = {
    cachedTopics ! Refresh
    val topic = try{
      Topic.find("_id",new ObjectId(topicId))
    } catch {
      case e:Throwable => Topic.find("teachingEventIdentity",topicId)
    }
    topic.map(t => com.metl.comet.TopicServer ! com.metl.comet.NewTopic(t))
  }
  def createTopic(location:String):Unit = Stopwatch.time("TopicManager:createTopic %s".format(location),{
    val newTopic = Topic.createRecord.name(location).creator(currentUser.is).deleted(false)
    newTopic.teachingEventIdentity(newTopic.identity).save(true)
    //XMPPQuestionSyncActor ! TopicSyncRequest(newTopic.identity)
  })
  def renameTopic(topicId:String,newName:String):Unit = Stopwatch.time("TopicManager:renameTopic",{
    Topic.find("_id",new ObjectId(topicId)) match {
      case t:Box[Topic] => {
        t.openOrThrowException("Expected Topic to be in Box").rename(newName)
        //XMPPQuestionSyncActor ! TopicSyncRequest(t.identity)
      }
      case _ => {}
    }
  })
  def deleteTopic(topicId:String):Unit = Stopwatch.time("TopicManager:deleteTopic",{
    Topic.find("_id",new ObjectId(topicId)) match {
      case t:Box[Topic] => {
        t.openOrThrowException("Expected Topic to be in Box").delete
        //XMPPQuestionSyncActor ! TopicSyncRequest(t.identity)
      }
      case _ => {}
    }
  })
}
