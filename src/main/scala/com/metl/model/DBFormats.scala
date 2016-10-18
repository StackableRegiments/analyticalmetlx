package com.metl.model

import org.bson.types.ObjectId
import net.liftweb.mongodb._
import net.liftweb.common._
import net.liftweb.record.field._
import net.liftweb.mongodb.record._
import net.liftweb.mongodb.record.field._
import java.util.Date
import net.liftweb.util._
import com.metl.comet.{StackServerManager, Notices, MeTLInteractableMessage}
import scala.collection.JavaConversions._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonParser._
import net.liftweb.http.js.JE._
import java.net.URL
import com.mongodb.gridfs._
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._
import org.apache.commons.io.IOUtils
import collection.JavaConversions._
import xml._
import com.metl.model.Globals._
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JsCmds._
import com.mongodb.BasicDBObject
import net.liftweb.mongodb.MongoDB
import net.liftweb.json.Serialization

object GainAction extends Enumeration{
  val MadeQuestionOnStack,
    MadeAnswerOnStack,ReceivedAnswerOnStack,
    MadeCommentOnStack,ReceivedCommentOnStack,
    MadeConversation,JoinedConversation,
    CreatedCourseStream,
    LeftCourseStream,
    AddedTeachingEvent,
    JoinedSlide, JoinedCourseStream,
    AddedQuizToStack,ReceivedQuizOnStack,
    JoinedGroup,InvitedContactToContactList,
    SentPrivateMessage,ReceivedPrivateMessage,
    WasAssignedToGroup,WasSplitIntoSmallerGroup,
    InvitedContactToCollaborate,WasInvitedToContactList,
    WasInvitedToCollaborate,AssignedGroups,
    WasInCorrectlySizedGroup,
    VotedUp, VotedDown,
    AddedUrlToStack,ReceivedUrlOnStack,
    AddedQuizToQuestion,AnsweredQuiz,
    ViewedQuestionOnStack,ViewedAnswerOnStack,ViewedCommentOnStack,
    ReceivedQuestionViewOnStack,ReceivedAnswerViewOnStack,ReceivedCommentViewOnStack,
    DeletedQuestionOnStack,DeletedAnswerOnStack,DeletedCommentOnStack,
    EditedQuestionOnStack,EditedAnswerOnStack,EditedCommentOnStack,
    ReceivedUpVote,ReceivedDownVote
  = Value
  def toInt(action:GainAction.Value) = {
    values.find(a => a == action).map(_.id).getOrElse(0)
  }
}

object StackAdmin extends StackAdmin with MongoMetaRecord[StackAdmin]{
  ensureDBIndex
  def ensureDBIndex = {
    useColl(_.ensureIndex(new BasicDBObject("authcate",1),"stackAdminAuthcateIndex",false))
  }

  def fromAuthcate(authcate:String) ={
    find("authcate",authcate) match {
      case Full(rec) => rec
      case _ => createRecord.authcate(authcate).adminForAllTopics(false).adminForTopics(List.empty[String])
    }
  }
  def canAdminTopic(user:String,topic:String):Boolean = find("authcate",user) match {
    case Full(user) => user.isAdminForTopic(topic)
    case _ => false
  }
  def canAddTopics(user:String):Boolean = find("authcate",user) match {
    case Full(user) => user.canAddTopics
    case _ => false
  }
  def canAccess(user:String):Boolean = find("authcate",user) match {
    case Full(user) => true
    case _ => false
  }
  def canAddTopicsMap:Map[String,Boolean] = Map(findAll("adminForAllTopics",true).map(user => (user.authcate.get,true)):_*).withDefault((user:String) => false)
  def topicMap:Map[String,List[String]] = {
    val userMap = findAll.map(user => (user.authcate.get,user.adminForTopics.get))
    val fullAdmins = findAll("adminForAllTopics",true).map(_.authcate.get)
    val allTopics = (TopicManager.getAll.map(t => t.name.get).toList ::: userMap.flatMap(user => user._2)).distinct
    Map(allTopics.map(topic => {
      (topic, userMap.filter(u => u._2.contains(topic)).map(_._1) ::: fullAdmins)
    }):_*).withDefault((input:String) => fullAdmins)
  }
  def createOrUpdate(authcate:String) = find("authcate",authcate) match {
    case Full(rec) => rec
    case _ => createRecord.authcate(authcate)
  }
}

class StackAdmin extends MongoRecord[StackAdmin] with MongoId[StackAdmin]{
  def meta = StackAdmin
  object authcate extends StringField(this,32)
  object adminForTopics extends MongoListField[StackAdmin,String](this)
  object adminForAllTopics extends BooleanField(this)
  // adding topics requires adminForAllTopics = true
  def isAdminForTopic(topic:String):Boolean = adminForAllTopics.get || adminForTopics.get.contains(topic)
  def canAddTopics:Boolean = adminForAllTopics.get
}
abstract class Rep
class Informal extends Rep with MongoRecord[Informal] with MongoId[Informal]{
  def meta = Informal
  object action extends EnumField(this,GainAction)
  object protagonist extends StringField(this,255)
  object antagonist extends OptionalStringField(this,255)
  object conversation extends OptionalStringField(this, 255)
  object time extends LongField(this)
  object slide extends OptionalIntField(this)
  object question extends ObjectIdRefField(this,StackQuestion)
}
object Informal extends Informal with MongoMetaRecord[Informal]{
  ensureDBIndex
  def ensureDBIndex = {
    useColl(_.ensureIndex(new BasicDBObject("protagonist",1),"informalProtagonistIndex",false))
    useColl(_.ensureIndex(new BasicDBObject("action",1),"informalActionIndex",false))
  }
  import GainAction._
  def standing(who:String):Int=findAll(("protagonist",who)).map((gain:Informal)=>value(gain.action.get)).foldLeft(0){
    case (acc,item)=> acc + item
  }
  val scores = Map(
    MadeQuestionOnStack -> 10,
    MadeAnswerOnStack -> 5,
    ReceivedAnswerOnStack -> 5,
    MadeCommentOnStack -> 5,
    ReceivedCommentOnStack -> 5,
    VotedUp -> 1,
    VotedDown -> 1,
    ViewedQuestionOnStack -> 0,
    ViewedAnswerOnStack -> 0,
    ViewedCommentOnStack -> 0,
    ReceivedQuestionViewOnStack -> 0,
    ReceivedAnswerViewOnStack -> 0,
    ReceivedCommentViewOnStack -> 0,
    DeletedQuestionOnStack -> 0,
    DeletedAnswerOnStack -> 0,
    DeletedCommentOnStack -> 0,
    EditedQuestionOnStack -> 2,
    EditedAnswerOnStack -> 2,
    EditedCommentOnStack -> 2,
    ReceivedUpVote -> 5,
    ReceivedDownVote -> 0
  ).withDefault(action => 0)
  def value(g:GainAction.Value) = scores(g)
}
case class DiscussionPoint(author:Author,content:String) extends JsonObject[DiscussionPoint]{
  def html = <span>{content}{author.html}</span>
  def meta = DiscussionPoint
}
object DiscussionPoint extends JsonObjectMeta[DiscussionPoint]
case class Vote(author:Author, shift:Int, time:Long) extends JsonObject[Vote]
{
  def meta = Vote
}
object Vote extends JsonObjectMeta[Vote]
case class Author(name:String){
  def html ={
    val standing = com.metl.model.Reputation.standing(name)
    val clazz = "authorSummary author_%s".format(name)
    <span class={clazz}>
    <span class="authorName">{name}</span>
    <span class="authorScore">{standing.formative}</span>
    <span class="authorMark">{standing.summative}</span>
    </span>;
  }
}
trait VoteCollector{
  def listDeepVotes:List[Vote]
  def listVotes:List[Vote]
  def addVote(vote:Vote)
}
class StackQuestion private() extends MongoRecord[StackQuestion] with MongoId[StackQuestion] with VoteCollector{
  def meta = StackQuestion
  object teachingEvent extends StringField(this, 128)
  object slideJid extends IntField(this)
  object creationDate extends LongField(this, 100)
  object xPos extends StringField(this, 100)
  object yPos extends StringField(this, 100)
  object answers extends MongoJsonObjectListField[StackQuestion, StackAnswer](this, StackAnswer)
  object votes extends MongoJsonObjectListField[StackQuestion, Vote](this, Vote)
  object deleted extends BooleanField(this)
  object about extends JsonObjectField[StackQuestion, DiscussionPoint](this, DiscussionPoint)
  {
    def defaultValue = DiscussionPoint(Author("noone"), "none")
  }
  private lazy val searchQuery = JObject(List(JField("_id",this._id.asJValue)))
  def addAnswer(answer:StackAnswer) = {
    val answerJson = StackAnswer.toJObject(answer)(StackQuestion.formats)
    val updateQuery = JObject(List(JField("$push",JObject(List(JField("answers",answerJson))))))
    StackQuestion.update(searchQuery,updateQuery)
  }
  def listDeepVotes = answers.get.filter(a => !a.deleted).map(a => a.listDeepVotes).foldLeft(listVotes)((acc,item) => acc ::: item)
  def listVotes = votes.get
  def addVote(vote:Vote) = {
    val voteJson = Vote.toJObject(vote)(StackQuestion.formats)
    val updateQuery = JObject(List(JField("$push",JObject(List(JField("votes",voteJson))))))
    StackQuestion.update(searchQuery,updateQuery)
  }
  def delete = {
    val updateQuery = JObject(List(JField("$set",JObject(List(JField("deleted",JBool(true)))))))
    StackQuestion.update(searchQuery,updateQuery)
  }
  def updateContent(newContent:String) = {
    val updateQuery = JObject(List(JField("$set",JObject(List(JField("about.content",JString(newContent)))))))
    StackQuestion.update(searchQuery,updateQuery)
  }
  def totalChildCount:Int = {
    answers.get.filter(a => !a.deleted).map(a => a.totalChildCount).sum
  }
  def mostRecentActivity:Long = {
    answers.get.filter(a => !a.deleted).map(a => a.mostRecentActivity).foldLeft(creationDate.get)((acc,item) => List(acc,item).max)
  }
}
object StackQuestion extends StackQuestion with MongoMetaRecord[StackQuestion]
{
  ensureDBIndex
  def ensureDBIndex = {
    useColl(_.ensureIndex(new BasicDBObject("teachingEvent",1),"stackQuestionIndex",false))
    useColl(_.ensureIndex(new BasicDBObject("deleted",1),"stackQuestionDeletedIndex",false))
  }
  def defaultValue = null.asInstanceOf[StackQuestion]
  override implicit val formats = net.liftweb.json.DefaultFormats
}

case class StackAnswer(id:String, parentId:String,var about:DiscussionPoint, var votes:List[Vote], var comments:List[StackComment], creationDate:Long, visualSubmission:List[String], redirectingSubmission:List[(String, String)], var deleted:Boolean) extends JsonObject[StackAnswer] with VoteCollector{
  def meta = StackAnswer
  def listVotes = votes
  def listDeepVotes = comments.filter(a => !a.deleted).map(a => a.listDeepVotes).foldLeft(listVotes)((acc,item) => acc ::: item)
  private lazy val mongoContextQuery = "answers.$"
  private lazy val searchQuery = JObject(List(JField("_id",JString(parentId)),JField("answers.id",JString(id))))
  def addVote(vote:Vote) = {
    val voteJson = Vote.toJObject(vote)(StackQuestion.formats)
    val updateQuery = JObject(List(JField("$push",JObject(List(JField(mongoContextQuery+".votes",voteJson))))))
    StackQuestion.update(searchQuery,updateQuery)
  }
  def addComment(comment:StackComment) = {
    val commentJson = StackComment.toJObject(comment)(StackQuestion.formats)
    val updateQuery = JObject(List(JField("$push",JObject(List(JField(mongoContextQuery+".comments",commentJson))))))
    StackQuestion.update(searchQuery,updateQuery)
  }
  def updateContent(newContent:String) = {
    val updateQuery = JObject(List(JField("$set",JObject(List(JField(mongoContextQuery+".about.content",JString(newContent)))))))
    StackQuestion.update(searchQuery,updateQuery)
  }
  def delete = {
    val updateQuery = JObject(List(JField("$set",JObject(List(JField(mongoContextQuery+".deleted",JBool(true)))))))
    StackQuestion.update(searchQuery,updateQuery)
  }
  def totalChildCount:Int = {
    comments.filter(a => !a.deleted).map(c => c.totalChildCount).sum + 1
  }
  def mostRecentActivity:Long = {
    comments.filter(a => !a.deleted).map(a => a.mostRecentActivity).foldLeft(creationDate)((acc,item) => List(acc,item).max)
  }
}
object StackAnswer extends JsonObjectMeta[StackAnswer]

case class StackComment(id:String, parentId:String,var about:DiscussionPoint, creationDate:Long, var votes:List[Vote], var comments:List[StackComment], var deleted:Boolean) extends JsonObject[StackComment] with VoteCollector with Logger {
  def meta = StackComment
  def listVotes = votes
  def listDeepVotes = comments.filter(a => !a.deleted).map(a => a.listDeepVotes).foldLeft(listVotes)((acc,item) => acc ::: item)
  def addVote(vote:Vote) = {
    val addVoteCmd = StackComment.jsCmdFormat.format(parentId,id,"{'comments':[],'votes':[{'author':{'name':'%s'},'shift':%s,'time':%s}]}".format(vote.author.name,vote.shift,vote.time))
    val newQuery = new BasicDBObject("$eval",addVoteCmd)
    newQuery.append("nolock",true)
    MongoDB.useSession(db => db.command(newQuery))
  }
  def addComment(comment:StackComment) = {
    val commentJson:String = Serialization.write(StackComment.toJObject(comment)(StackQuestion.formats))(StackQuestion.formats)
    val addCommentCmd = StackComment.jsCmdFormat.format(parentId,id,"{'comments':[%s],'votes':[]}".format(commentJson))
    val newQuery = new BasicDBObject("$eval",addCommentCmd)
    newQuery.append("nolock",true)
    MongoDB.useSession(db => db.command(newQuery))
  }
  def updateContent(newContent:DiscussionPoint) = {
    val newComment = StackComment("","",newContent,0L,List.empty[Vote],List.empty[StackComment],deleted)
    val commentJson:String = Serialization.write(StackComment.toJObject(newComment)(StackQuestion.formats))(StackQuestion.formats)
    val updateContentCmd = StackComment.jsCmdFormat.format(parentId,id,commentJson)
    val newQuery = new BasicDBObject("$eval",updateContentCmd)
    newQuery.append("nolock",true)
    MongoDB.useSession(db => db.command(newQuery))
  }
  def delete = {
    val deleteCmd = StackComment.jsCmdFormat.format(parentId,id,"{'comments':[],'votes':[],'deleted':true}")
    val newQuery = new BasicDBObject("$eval",deleteCmd)
    newQuery.append("nolock",true)
    trace("[MONGO] - dbResponse: %s".format(MongoDB.useSession(db => db.command(newQuery))))
  }
  def totalChildCount:Int = {
    comments.filter(c => !c.deleted).map(c => c.totalChildCount).sum + 1
  }
  def mostRecentActivity:Long = {
    comments.filter(c => !c.deleted).map(a => a.mostRecentActivity).foldLeft(creationDate)((acc,item) => List(acc,item).max)
  }
}
object StackComment extends JsonObjectMeta[StackComment]{
  val jsCmdFormat = "function(){ var start = function(commentId,deltas){ var id = ObjectId('%s'); var f = function(parent){ if (parent.comments){parent.comments.forEach(function(c){ if(c.id == commentId){ deltas.votes.forEach(function(v){ c.votes.push(v); db.stackquestions.save(q); }); deltas.comments.forEach(function(sc){ c.comments.push(sc); db.stackquestions.save(q); }); if (deltas.deleted){ c.deleted = deltas.deleted; db.stackquestions.save(q); }; if (deltas.about){if (deltas.about.content){ c.about.content = deltas.about.content; db.stackquestions.save(q); }; }; }; f(c); });};}; var q = db.stackquestions.findOne({ _id : id});if (q){q.answers.forEach(function(a){f(a);});}; return q;}; return start('%s',%s);}"
}

object SystemInformation extends SystemInformation with MongoMetaRecord[SystemInformation]{}
class SystemInformation extends MongoRecord[SystemInformation] with MongoId[SystemInformation]{
  def meta = SystemInformation
  object name extends StringField(this,32)
  object alreadyHydratedDB extends BooleanField(this)
}
class Topic extends MongoRecord[Topic] with MongoId[Topic]{
  def meta = Topic
  lazy val identity = _id.get.toString
  object name extends StringField(this,255)
  object creator extends StringField(this,255)
  object deleted extends BooleanField(this)
  object teachingEventIdentity extends StringField(this,255)
  private lazy val searchQuery = JObject(List(JField("_id",this._id.asJValue)))
  def delete = {
    val updateQuery = JObject(List(JField("$set",JObject(List(JField("deleted",JBool(true)))))))
    Topic.update(searchQuery,updateQuery)
  }
  def rename(newName:String) = {
    val updateQuery = JObject(List(JField("$set",JObject(List(JField("votes",JString(newName)))))))
    Topic.update(searchQuery,updateQuery)
  }
}
object Topic extends Topic with MongoMetaRecord[Topic]
{
  ensureDBIndex
  def ensureDBIndex = {
    useColl(_.ensureIndex(new BasicDBObject("name",1),"topicNameIndex",false))
    useColl(_.ensureIndex(new BasicDBObject("teachingEventIdentity",1),"teachingEventIdentityIndex",false))
  }
  def getDefaultValue = find("teachingEventIdentity","default") match {
    case Full(topic) => topic
    case _ => {
      createRecord.name("default").creator("automatically created").deleted(false).teachingEventIdentity("default").save(true)
    }
  }
  def defaultValue = getDefaultValue
}
