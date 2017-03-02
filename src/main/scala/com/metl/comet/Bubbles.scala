package com.metl.comet

import com.metl.utils.{PeriodicallyRefreshingVar, Stopwatch}
import java.util.Date

import org.apache.commons.io.IOUtils
import net.liftweb.http._

import scala.collection.mutable.{HashMap, SynchronizedMap}
import net.liftweb.mongodb.Limit
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
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
//import scala.concurrent.ops._
import org.bson.types.ObjectId
import com.metl.utils.FunctionConverter._

case class Emerge(onPageSelector:String)
trait Bob{}
case class BobUp(onPageId:String) extends Bob
case class BobDown(onPageId:String) extends Bob
case class Silently(present:QuestionPresenter)
case class ChangeCourse(stream:String)
case class Emphasize(word:String)
case class Toggle(questionId:String, strategy:ExpansionStrategy)
case class Detail(questionId:String, overrideShow:Boolean = false)
case class SetExpansionState(questionId:String,openItems:List[String])
case object HideAll
case class RemoteSilentQuestionRecieved(questionId:String)
case class RemoteQuestionRecieved(questionId:String)

object BubbleConstants extends Logger{
  //spec change has decided that it's not possible to vote a discussion point lower than zero
  setDefaultAdmins
  def setDefaultAdmins = {
    List("hagand","wmck","sajames","chagan","designa","rob","joshuaj").map(user => {
      StackAdmin.createOrUpdate(user).authcate(user).adminForAllTopics(true).save(true)
    })
    List("tburns","rnor2","designb","mthicks","ahannah","jpjor1").map(user => {
      StackAdmin.createOrUpdate(user).authcate(user).save(true)
    })
    Map("eecrole" -> List("aggregate","load-testing","loadTest")).map(userTuple => {
      val user = userTuple._1
      val topics = userTuple._2
      StackAdmin.createOrUpdate(user).authcate(user).adminForTopics(topics).save(true)
    })
  }
  val minimumVote = 1
  val maximumVote = 1
  val teachersData = new PeriodicallyRefreshingVar[Map[String,List[String]]](2 minutes,()=>{
    val teacherMap = StackAdmin.topicMap
    teacherMap
  })
  val canAddTopicsData = new PeriodicallyRefreshingVar[Map[String,Boolean]](2 minutes,()=>{
    val canAddTopicMap = StackAdmin.canAddTopicsMap
    canAddTopicMap
  })
  def fullAdmins = Stopwatch.time("BubbleConstants:fullAdmins",canAddTopicsData.get match {
    case map:Map[String,Boolean] if map.keys.toList.length > 0 => map
    case _ => Map.empty[String,Boolean].withDefault((input:String) => false)
  })
  def teachers = teachersData.get match {
    case map:Map[String,List[String]] if map.keys.toList.length > 0 => map
    case _ => Map.empty[String,List[String]].withDefault((input:String) => List.empty[String])
  }
}

object StackTemplateHolder{
  val isDev = if (Props.mode == Props.RunModes.Production || Props.mode == Props.RunModes.Staging) false else true
  def getQuestionDetailTemplate = Templates(List("_questionDetail")).openOr(NodeSeq.Empty)
  def getCommentDetailTemplate = Templates(List("_commentDetail")).openOr(NodeSeq.Empty)
  def getAnswerDetailTemplate = Templates(List("_answerDetail")).openOr(NodeSeq.Empty)
  def getVotingTemplate = Templates(List("_voterDetail")).openOr(NodeSeq.Empty)
  def getSummaryHeaderTemplate = Templates(List("_summaryHeader")).openOr(NodeSeq.Empty)
  def getSummaryTemplate = Templates(List("_summaryDetail")).openOr(NodeSeq.Empty)
  def getInputDialogTemplate = Templates(List("_inputDialog")).openOr(NodeSeq.Empty)
  def getInteractableMessageTemplate = Templates(List("_interactableMessage")).openOr(NodeSeq.Empty)
  def getSpamTemplate = Templates(List("_spam")).openOr(NodeSeq.Empty)
  def getTopicTemplate = Templates(List("_topic")).openOr(NodeSeq.Empty)
  def getSearchTemplate = Templates(List("_search")).openOr(NodeSeq.Empty)
  val useQuestionDetailTemplate = getQuestionDetailTemplate
  val useCommentDetailTemplate = getCommentDetailTemplate
  val useAnswerDetailTemplate = getAnswerDetailTemplate
  val useVotingTemplate = getVotingTemplate
  val useSummaryTemplate = getSummaryTemplate
  val useInputDialogTemplate = getInputDialogTemplate
  val useInteractableMessageTemplate = getInteractableMessageTemplate
  val useSpamTemplate = getSpamTemplate
  val useSummaryHeaderTemplate = getSummaryHeaderTemplate
  val useTopicTemplate = getTopicTemplate
  val useSearchTemplate = getSearchTemplate
  def questionDetailTemplate = if (isDev) getQuestionDetailTemplate else useQuestionDetailTemplate
  def commentDetailTemplate = if (isDev) getCommentDetailTemplate else useCommentDetailTemplate
  def answerDetailTemplate = if (isDev) getAnswerDetailTemplate else useAnswerDetailTemplate
  def votingTemplate = if (isDev) getVotingTemplate else useVotingTemplate
  def summaryHeaderTemplate = if(isDev) getSummaryHeaderTemplate else useSummaryHeaderTemplate
  def summaryTemplate = if (isDev) getSummaryTemplate else useSummaryTemplate
  def inputDialogTemplate = if (isDev) getInputDialogTemplate else useInputDialogTemplate
  def interactableMessageTemplate = if(isDev) getInteractableMessageTemplate else useInteractableMessageTemplate
  def spamTemplate = if (isDev) getSpamTemplate else useSpamTemplate
  def topicTemplate = if (isDev) getTopicTemplate else useTopicTemplate
  def searchTemplate = if (isDev) getSearchTemplate else useSearchTemplate
  val useClientMessageTemplate = getClientMessageTemplate
  def getClientMessageTemplate = Templates(List("_s2cMessage")).openOr(NodeSeq.Empty)
  def clientMessageTemplate = if (Globals.isDevMode) getClientMessageTemplate else useClientMessageTemplate
}

class QuestionPresenter(val context:StackQuestion) extends Discussable(context,context) with Logger {
  override val id = context._id.get.toString
  val summaryId = "q%s".format(id)
  val about = context.about.get
  override val depth = context.totalChildCount
  override val mostRecentActivity = context.mostRecentActivity
  def update(discussionPoint:DiscussionPoint) = stackWorker ! WorkUpdateQuestion(context,discussionPoint,timeticks)
  override def equals(other:Any) = other != null && other.isInstanceOf[QuestionPresenter] && other.asInstanceOf[QuestionPresenter].context._id.get == context._id.get
  override def hashCode = context.hashCode
  private def addAnswer(x:NodeSeq) = {
    a(()=>{
      var validSubmit:Boolean = false
      var hasSubmitted:Boolean = false
      val inputId = "answerInputOnQuestion_%s".format(id)
      val submitId = "answerSubmitOnQuestion_%s".format(id)
      val message = MeTLInteractableMessage(
        self =>
        {
          (".inputDialog [class+]" #> "answerOnQuestionDialog" &
            ".inputDialog [id+]" #> "answerQuestionDialog_%s".format(id) &
            ".inputDialogHeader *" #> Text("What is your response?") &
            ".inputDialogContentBox" #> textarea("", (t:String) =>
              {
                if(!hasSubmitted && t.trim.length > 0){
                  stackWorker ! WorkCreateAnswerToQuestion(context,currentUser.is,t,session,timeticks)
                  hasSubmitted = true
                  validSubmit = true
                }
              }, ("id", inputId)) &
            ".inputDialogSubmitButton" #> ajaxSubmit("Submit", ()=>
              {
                if (validSubmit){
                  self.done
                  Noop
                }
                else{
                  Notices.local(MeTLSpam(Text("please enter a response")))
                }
              }, ("id",submitId))
          ).apply(StackTemplateHolder.inputDialogTemplate) ++ activateNewInputBox(inputId,submitId)
        },true).entitled("Type your response").identifiedBy("answerQuestion_%s".format(id))
      Notices.local(message)
      Noop
    }, x, ("id","answerQuestion_%s".format(id)),("class","answerOnQuestion"))
  }
  def questionVoter:NodeSeq = {
    val votingContainerId = "questionVoter_%s".format(id)
    votingContainer(rating, ratingFrom(com.metl.model.Author(currentUser.is)), ()=>{
      stackWorker ! WorkVoteUpQuestion(context,this,currentUser.is)
    },
      ()=>{
        stackWorker ! WorkVoteDownQuestion(context,this,currentUser.is)
      },votingContainerId, List("questionVoter"))
  }
  private val answers = context.answers.get.filter(!_.deleted)
  private val visibleAnswers = answers.map(a => Answer(context,a))
  def pluralize(input:Int = depth) = input match{
    case 1 => "response"
    case _ => "responses"
  }
  private def toggle(body:NodeSeq):NodeSeq = a(()=>{
    StackOverflow.localOpenAction(WorkOpenQuestion(context,currentUser.is,session,timeticks))
    Noop
  }, body)
  private val answerCount = "%s %s (%s %s in total)".format(answers.size, pluralize(answers.size), depth, pluralize(depth))
  def deleteQuestion = ()=> {
    stackWorker ! WorkDeleteQuestion(context,currentUser.is,timeticks)
    Noop
  }
  private def bindResponseOptions = {
    ".answerAQuestionContainer *" #> {x:NodeSeq => addAnswer(x)} &
    (if(canEditContent){
      (if(canDeleteContent){
        ".deleteContentContainer *" #> {x:NodeSeq => deleteContent(deleteQuestion, x,List("deleteQuestion"))}
      }
      else{
        ".deleteContentContainer *" #> NodeSeq.Empty
      }) &
      ".editContentContainer *" #> {x:NodeSeq => editContent(x,List("editQuestion"))}
    }
    else{
      ".deleteContentContainer" #> NodeSeq.Empty &
      ".editContentContainer" #> NodeSeq.Empty
    })
  }
  def truncate(s:String,length:Int)= if(s.length > length) s.take(length - 3) + "..." else s
  def detail = dynamicDetail(staticDetail)
  def dynamicDetail(x:NodeSeq) = {
    (bindResponseOptions &
      bindAuthor &
      ".questionText *" #> toggle(Text(about.content)) &
      ".questionVoter" #> questionVoter &
      ".answer" #> visibleAnswers.map(va => va.display)).apply(x)
  }
  val staticDetail = {
    (bindLiking &
      ".branchDepth *" #> depth &
      ".branchLastModified *" #> context.mostRecentActivity &
      ".branchRating *" #> deepRating &
      ".branchAuthor *" #> about.author.name &
      ".branchDepth [id]" #> summaryId &
      ".branchLastModified [id]" #> summaryId &
      ".branchRating [id]" #> summaryId &
      ".branchAuthor [id]" #> summaryId &
      ".bubbleContent [class+]" #> summaryId andThen
      ".answerCount *" #> answerCount &
      ".detailedQuestionCreationTime *" #> <div class="recency">{ timestamp(context.creationDate.get) }</div> &
      ".pluralizable" #> pluralize() &
      ".questionTitle *" #> truncate(about.content,30)).apply(StackTemplateHolder.questionDetailTemplate)
  }
  def summaryHtml = dynamicSummaryHtml.apply(staticSummaryHtml)
  val staticSummaryHtml = {
    (".questionSummaryItem [id+]" #> summaryId &
      ".branchDepth [id+]" #> "branchDepth_%s".format(summaryId) &
      ".branchLastModified [id+]" #> "branchLastModified_%s".format(summaryId) &
      ".branchRating [id+]" #> "branchRating_%s".format(summaryId) &
      ".branchAuthor [id+]" #> "branchAuthor_%s".format(summaryId) &
      ".summaryTextCell [title]" #> about.content &
      ".branchDepth *" #> depth &
      ".branchLastModified *" #> context.mostRecentActivity &
      ".branchRating *" #> deepRating &
      ".branchAuthor *" #> about.author.name &
      ".voteCount *" #> deepRating &
      ".summaryAnswerCount *" #> context.totalChildCount &
      ".recency *" #> timestamp(context.mostRecentActivity)
    ).apply(StackTemplateHolder.summaryTemplate)
  }
  def dynamicSummaryHtml = {
    ".author *" #> about.author.html &
    ".summaryQuestionText" #> toggle(Text(truncate(about.content,65)))
  }
}

case class Answer(parent:StackQuestion,context:StackAnswer) extends Discussable(parent,context) with Logger {
  lazy val about = context.about
  override val id = context.id.toString
  def update(discussionPoint:DiscussionPoint) = stackWorker ! WorkUpdateAnswer(parent,context,discussionPoint,timeticks)
  override val depth = context.totalChildCount
  override val mostRecentActivity = context.mostRecentActivity
  var emphasize = List.empty[String]
  var deemphasize = List.empty[String]
  private def bindResponseOptions = {
    ".addChildContainer *" #> {x:NodeSeq => addComment(x)} &
    (if(canEditContent){
      ".editContentContainer *" #> {x:NodeSeq => editContent(x,List("editAnswer"))} &
      (if (canDeleteContent)
        ".deleteAnswerContainer *" #> {x:NodeSeq =>deleteContent(deleteAnswer, x,List("deleteAnswer"))}
      else
        ".deleteAnswerContainer *" #> NodeSeq.Empty)
    }
    else{
      ".editContentContainer" #> NodeSeq.Empty &
      ".deleteAnswerContainer" #> NodeSeq.Empty
    })
  }
  private def bindExpansion = {
    if (depth - 1 > 0){
      ".expandableChildrenCount *" #> (depth - 1).toString &
      ".commentChildExpander [id+]" #> "commentChildExpander_%s".format(id) &
      ".commentChildExpander" #> ((n:NodeSeq) => a(()=> {
        StackOverflow.localOpenAction(WorkOpenAnswer(parent,context,currentUser.is,session,timeticks))
        Noop
      },n,("id","serverInformingExpander_%s".format(id)),("class","serverInformingExpander"))) &
      ".comment *" #> visibleComments.map(sc => Comment(parent,sc).detail) &
      ".comments [id+]" #> "commentsFor_%s".format(id)
    }
    else {
      ".expandableChildrenCount" #> NodeSeq.Empty &
      ".commentChildExpanderContainer *" #> <span>&#160;</span> &
      ".comments *" #> NodeSeq.Empty
    }
  }
  lazy val visibleComments = context.comments.filter(!_.deleted)
  def display = dynamicDisplay(staticDisplay)
  val staticDisplay = {
    (bindLiking &
      ".branchDepth [id+]" #> "branchDepth_%s".format(id) &
      ".branchLastModified [id+]" #> "branchLastModified_%s".format(id) &
      ".branchRating [id+]" #> "branchRating_%s".format(id) &
      ".branchAuthor [id+]" #> "branchAuthor_%s".format(id) &
      ".branchDepth *" #> depth &
      ".branchLastModified *" #> context.mostRecentActivity &
      ".branchRating *" #> deepRating &
      ".branchAuthor *" #> about.author.name &
      ".answer [id]" #> context.id andThen
      ".childCount" #> context.comments.size.toString &
      ".bubbleText *" #> context.about.content &
      ".detailedAnswerCreationTime *" #> <div class="recency">{
        timestamp(context.creationDate)
      }</div>).apply(StackTemplateHolder.answerDetailTemplate)
  }
  def dynamicDisplay(x:NodeSeq) = {
    (bindResponseOptions &
      bindAuthor &
      ".submissions *" #> submissions &
      ".answerVoter" #> answerVoter &
      ".links" #> links &
      bindExpansion).apply(x)
  }
  private def deleteAnswer = ()=>{
    stackWorker ! WorkDeleteAnswer(parent,context,currentUser.is,timeticks)
    Noop
  }
  private def addComment(x:NodeSeq) ={
    a(()=>{
      var validSubmit:Boolean = false
      var hasSubmitted:Boolean = false
      val inputId = "commentInputOnAnswer_%s".format(id)
      val submitId = "commentSubmitOnAnswer_%s".format(id)
      val message = MeTLInteractableMessage(
        self =>
        (".inputDialog [class+]" #> "commentOnAnswerDialog" &
          ".inputDialog [id+]" #> "commentOnAnswerDialog_%s".format(id) &
          ".inputDialogHeader *" #> Text("What is your response?") &
          ".inputDialogContentBox" #> textarea("",
            (t:String) =>{
              if(!hasSubmitted && t.trim.length > 0){
                stackWorker ! WorkCreateCommentOnAnswer(parent,context,currentUser.is,t,session,timeticks)
                hasSubmitted = true
                validSubmit = true
              }
            }, ("id", inputId)) &
          ".inputDialogSubmitButton" #>        ajaxSubmit("Submit", ()=>{
            if (validSubmit){
              self.done
              Noop
            }
            else Notices.local(MeTLSpam(Text("please enter a response")))
          },("id",submitId))
        ).apply(StackTemplateHolder.inputDialogTemplate) ++ activateNewInputBox(inputId,submitId),true).entitled("Type your response").identifiedBy("commentOnAnswer_".format(id))
      Notices.local(message)
      Noop
    }, x, ("id","addComment_%s".format(id)),("class","commentOnAnswer"))
  }
  def answerVoter:NodeSeq = {
    val votingContainerId = "answerVoter_%s".format(id)
    votingContainer(rating,ratingFrom(com.metl.model.Author(currentUser.is)), () => {
      stackWorker ! WorkVoteUpAnswer(parent,context,this,currentUser.is)
    },
      ()=>{
        stackWorker ! WorkVoteDownAnswer(parent,context,this,currentUser.is)
      },votingContainerId, List("answerVoter"))
  }
  lazy val commentViewLabel = context.comments.length match{
    case 1 => "1 comment"
    case n => "%s comments".format(n)
  }
  private def submissions:NodeSeq = context.visualSubmission.map(u=> <img src={u.toString} class="stackScreenshot" />)
  private def links:NodeSeq = context.redirectingSubmission.map(r => <a href={r._2.toString}>{
    if(r._1.toString.length > 0)
      r._1.toString
    else
      "Link"
  }</a>)
}
case class Comment(parent:StackQuestion, context:StackComment) extends Discussable(parent,context) with Logger {
  private var commentsElement = nextFuncName
  override val id = context.id.toString
  lazy val about = context.about
  def update(discussionPoint:DiscussionPoint) = stackWorker ! WorkUpdateComment(parent,context,discussionPoint,timeticks)
  override val depth = {
    context.totalChildCount
  }
  override val mostRecentActivity = {
    context.mostRecentActivity
  }

  val html = <div class="commentText">{about.html}</div>
  private def addComment(linkBody:NodeSeq)={
    a(()=>{
      var validSubmit:Boolean = false
      var hasSubmitted:Boolean = false
      val inputId = "commentInputOnComment_%s".format(id)
      val submitButtonId = "commentSubmitOnComment_%s".format(id)
      val message = MeTLInteractableMessage(
        self =>
        (".inputDialog [class+]" #> "commentOnCommentDialog" &
          ".inputDialog [id+]" #> "commentOnCommentDialog_%s".format(id) &
          ".inputDialogHeader *" #> Text("What is your response?") &
          ".inputDialogContentBox" #> textarea("",
            (t:String) =>{
              if(!hasSubmitted && t.trim.length > 0){
                stackWorker ! WorkCreateCommentOnComment(parent,context,currentUser.is,t,session,timeticks)
                hasSubmitted = true
                validSubmit = true
              }
            }, ("id", inputId)) &
          ".inputDialogSubmitButton" #> ajaxSubmit("Submit", ()=>{
            if (validSubmit){
              self.done
              Noop
            }
            else Notices.local(MeTLSpam(Text("please enter a response")))
          },("id",submitButtonId))
        ).apply(StackTemplateHolder.inputDialogTemplate) ++ activateNewInputBox(inputId,submitButtonId),true).entitled("Type your response").identifiedBy("commentOnComment_%s".format(id))
      Notices.local(message)
      Noop
    }, linkBody, ("class","commentOnComment"),("id","addComment_%s".format(id)))
  }
  private def bindResponseOptions = {
    ".addChildContainer *" #> addComment _ &
    (if(canEditContent){
      (if (canDeleteContent){
        ".deleteCommentContainer * " #> {x:NodeSeq => deleteContent(deleteComment, x, List("deleteComment"))}
      }
      else {
        ".deleteCommentContainer *" #> NodeSeq.Empty
      }) &
      ".editContentContainer *" #> {x:NodeSeq => editContent(x,List("editComment"))}
    }
    else{
      ".deleteCommentContainer" #> NodeSeq.Empty &
      ".editContentContainer" #>  NodeSeq.Empty
    })
  }
  lazy val visibleComments = context.comments.filter(!_.deleted)
  private def bindExpansion:CssSel = {
    if (depth - 1 > 0){
      ".expandableChildrenCount *" #> (depth - 1).toString &
      ".commentChildExpander [id+]" #> "commentChildExpander_%s".format(id) &
      ".commentChildExpander" #> ((n:NodeSeq) => a(()=> {
        StackOverflow.localOpenAction(WorkOpenComment(parent,context,currentUser.is,session,timeticks))
        Noop
      },n,("id","serverInformingExpander_%s".format(id)),("class","serverInformingExpander"))) &
      ".commentChild *" #> visibleComments.map(sc => Comment(parent,sc).detail).toList &
      ".comments [id+]" #> "commentsFor_%s".format(id)
    }
    else {
      ".expandableChildrenCount" #> NodeSeq.Empty &
      ".commentChildExpanderContainer *" #> <span>&#160;</span> &
      ".comments *" #> NodeSeq.Empty
    }
  }
  def detail = dynamicDetail(staticDetail)
  def dynamicDetail(x:NodeSeq) = {
    (bindLiking &
      bindAuthor &
      bindExpansion &
      bindResponseOptions &
      ".commentVoter" #> commentVoter).apply(x)
  }
  val staticDetail = {
    (".branchDepth [id+]" #> "branchDepth_%s".format(id) &
      ".branchLastModified [id+]" #> "branchLastModified_%s".format(id) &
      ".branchRating [id+]" #> "branchRating_%s".format(id) &
      ".branchAuthor [id+]" #> "branchAuthor_%s".format(id) &
      ".branchDepth *" #> depth &
      ".branchLastModified *" #> context.mostRecentActivity &
      ".branchRating *" #> deepRating &
      ".branchAuthor *" #> about.author.name &
      ".comment [id]" #> context.id andThen
      ".bubbleText *" #> about.content &
      ".detailedCommentCreationTime *" #> <div class="recency">{
        timestamp(context.creationDate)
      }</div>).apply(StackTemplateHolder.commentDetailTemplate)
  }
  def deleteComment = ()=>{
    stackWorker ! WorkDeleteComment(parent,context,currentUser.is,timeticks)
    Noop
  }
  def commentVoter:NodeSeq = {
    val votingContainerId = "commentVoter_%s".format(id)
    votingContainer(rating,ratingFrom(com.metl.model.Author(currentUser.is)), () => {
      stackWorker ! WorkVoteUpComment(parent,context,this,currentUser.is)
    },
      ()=>{
        stackWorker ! WorkVoteDownComment(parent,context,this,currentUser.is)
      },votingContainerId, List("commentVoter"))
  }
}
trait Rated{
  def rating:Int
  def deepRating:Int
  def ratingFrom(author:com.metl.model.Author):Int
}
object Rater{
  def rating(d:VoteCollector) = d.listVotes.foldLeft(0)((acc, item) => acc + item.shift)
  def deepRating(d:VoteCollector) = d.listDeepVotes.foldLeft(0)((acc,item) => acc + item.shift)
  def ratingFrom(d:VoteCollector,author:com.metl.model.Author) = d.listVotes.foldLeft(0)((acc, item) => if (item.author == author) acc + item.shift else acc)
}

object StackHelpers{
  def activateNewInputBox(inputId:String,submitButtonId:String) = Script(After(0,Call("receiveInputBox",JArray(List(JString("#%s".format(inputId)),JString("#%s".format(submitButtonId)))))))
}

object Discussable {
  val dateFormat = new SimpleDateFormat("""EEEE d/M, HH:mm""")
}

abstract class Discussable(var saveContext:StackQuestion,voted:VoteCollector) extends Rated with Logger {
  def activateNewInputBox(inputId:String,submitButtonId:String) = StackHelpers.activateNewInputBox(inputId,submitButtonId)
  def stackServer = StackServerManager.get(saveContext.teachingEvent.get)
  def stackWorker = StackServerManager.getWorker(saveContext.teachingEvent.get)
  private def session = S.session.openOrThrowException("S should contain a current Lift session")
  val location:String = saveContext.teachingEvent.get
  def id:String
  def timestamp(timeSinceEpoch:Long):String = {
    Discussable.dateFormat.format(new java.util.Date(timeSinceEpoch))
  }
  def timeticks:Long = {
    new java.util.Date().getTime
  }
  def about:DiscussionPoint
  def depth:Int
  def mostRecentActivity:Long
  lazy val rating = Rater.rating(voted)
  lazy val deepRating = Rater.deepRating(voted)
  def ratingFrom(author:com.metl.model.Author) = Rater.ratingFrom(voted,author)
  def update(discussionPoint:DiscussionPoint):Unit
  def bindAuthor = ".authorDetails *" #> about.author.html
  def sendLocalMessage(message:Any) = {
    StackOverflow.local(message,saveContext.teachingEvent.get)
  }
  def voteDown:Boolean = voteDown(currentUser.is)
  def voteDown(who:String):Boolean = {
    val auth = com.metl.model.Author(who)
    if (ratingFrom(auth) > BubbleConstants.minimumVote){
      val rep = Informal.createRecord.time(timeticks).protagonist(who).antagonist(about.author.name).action(GainAction.VotedDown).conversation(saveContext.teachingEvent.get)
      voted.addVote(Vote(auth, -1, timeticks))
      Reputation.accrue(rep)
      true
    }
    else{
      false
    }
  }
  def voteUp:Boolean = voteUp(currentUser.is)
  def voteUp(who:String):Boolean = {
    val auth = com.metl.model.Author(who)
    if(ratingFrom(auth) < BubbleConstants.maximumVote){
      val rep = Informal.createRecord.time(timeticks).protagonist(who).antagonist(about.author.name).action(GainAction.VotedUp).conversation(saveContext.teachingEvent.get)
      voted.addVote(Vote(auth, 1, timeticks))
      Reputation.accrue(rep)
      true
    }
    else{
      false
    }
  }
  def canEditContent = currentUser.is == about.author.name || BubbleConstants.teachers(location).contains(currentUser.is)
  def canDeleteContent = BubbleConstants.teachers(location).contains(currentUser.is)
  def editContent(x:NodeSeq,additionalClasses:List[String] = List.empty[String]) = {
    if(canEditContent){
      var validSubmit:Boolean = false
      var hasSubmitted:Boolean = false
      val editId = "editContent_%s".format(id)
      val editInputId = "editCurrentQuestionInput_%s".format(id)
      val editContentSubmitId = "editContentSubmit_%s".format(id)
      a(()=>{
        Notices.local(MeTLInteractableMessage(self=>
          (".inputDialog [class+]" #> "editContentDialog" &
            ".inputDialog [id+]" #> "editContentDialog_%s".format(id) &
            ".inputDialogHeader *" #> Text("Please edit only for clarity.  Respect the other authors who may already have replied") &
            ".inputDialogContentBox" #> textarea(about.content,t=>{
              if (!hasSubmitted && t.length > 0){
                val updated = about.copy(content=t)
                update(updated)
                self.done
                validSubmit = true
                hasSubmitted = true
                Noop
              }
            }, ("id",editInputId)) &
            ".inputDialogSubmitButton" #> ajaxSubmit("Submit",()=>{
              if (validSubmit){
                self.done
                Noop
              }
              else Notices.local(MeTLSpam(Text("please enter a response")))
            },("id",editContentSubmitId),("class","submitButton"))
          ).apply(StackTemplateHolder.inputDialogTemplate) ++ activateNewInputBox(editInputId,editContentSubmitId)
            ,true).entitled("Enter your edit").identifiedBy("editContent_%s".format(id)))
        Noop
      }, x, ("id",editId), ("class",("editContent" :: additionalClasses).mkString(" ")))
    }
    else <span />
  }
  def deleteContent(delete:Function0[JsCmd], x:NodeSeq, additionalClasses:List[String] = List.empty[String]) ={
    if(canDeleteContent) {
      val deleteId = "deleteContent_%s".format(id)
      a(()=>{
        Notices.local(MeTLInteractableMessage(self=>
          (".inputDialog [class+]" #> "deleteContentDialog" &
            ".inputDialog [id+]" #> "deleteContentDialog_%s".format(id) &
            ".inputDialogHeader *" #> Text("Are you sure you want to delete this?") &
            ".inputDialogContentBox" #> NodeSeq.Empty &
            ".inputDialogSubmitButton *" #> ajaxButton("Yes", ()=>{
              self.done
              delete()
            },("id","deleteContentYes_%s".format(id))) &
            ".inputDialogCancelButton *" #> ajaxButton("No", ()=>{
              self.done
              Noop}
              ,("id","deleteContentNo_%s".format(id)))
          ).apply(StackTemplateHolder.inputDialogTemplate)
            ,true).entitled("Really delete?").identifiedBy("deleteContent_%s".format(id)))
        Noop
      }, x, ("id",deleteId), ("class",("deleteContent" :: additionalClasses).mkString(" ")))
    }
    else <span/>
  }
  def likes(who:String) = voted.listVotes.contains(who)
  val bindLiking = ".count *" #> voted.listVotes.size
  def votingContainer(currentRating:Int, currentUserRating:Int, upVote:Function0[js.JsCmd], downVote:Function0[js.JsCmd],containerId:String = nextFuncName, additionalClasses:List[String] = List.empty[String]):NodeSeq ={
    (".voter [id+]" #> containerId &
      ".voter [class+]" #> additionalClasses.mkString(" ") &
      ".voteCount *" #> currentRating.toString &
      ".upArrow" #> ((n:NodeSeq) => {
        if (currentUserRating < BubbleConstants.maximumVote) a(()=>upVote(), n,("id","upVote_%s".format(id)))
        else <span class="disabledVoter">{n}</span>
      }) &
      ".downArrow" #> ((n:NodeSeq) => {
        if (currentUserRating > BubbleConstants.minimumVote) a(()=>downVote(), n,("id","downVote_%s".format(id)))
        else <span class="disabledVoter">{n}</span>
      })).apply(StackTemplateHolder.votingTemplate)
  }
}
trait StackRouter extends LiftActor with ListenerManager with Logger {
  def createUpdate = questions
  def questions:List[QuestionPresenter]
  def addQuestion(question:QuestionPresenter)
  def possiblyUpdateQuestionSilentlyById(id:String) = {
    try{
      StackQuestion.find("_id",new ObjectId(id)).map(sq => {
        val qp = new QuestionPresenter(sq)
        addQuestion(qp)
        sendListenersMessage(Silently(qp))
      })
    } catch {
      case e:Throwable => error("exception thrown while fetching remoteQuestion from DB: %s".format(e.getMessage))
    }
  }
  def possiblyUpdateQuestionById(id:String) = {
    try {
      StackQuestion.find("_id",new ObjectId(id)).map(sq => {
        val qp = new QuestionPresenter(sq)
        addQuestion(qp)
        sendListenersMessage(qp)
      })
    } catch {
      case e:Throwable => error("exception thrown while fetching remoteQuestion from DB: %s".format(e.getMessage))
    }
  }
  override def lowPriority = {
    case r:RemoteSilentQuestionRecieved => Stopwatch.time("StackRouter:remoteSilentQuestionReceived",possiblyUpdateQuestionSilentlyById(r.questionId))
    case r:RemoteQuestionRecieved => Stopwatch.time("StackRouter:remoteQuestionReceived",possiblyUpdateQuestionById(r.questionId))
    case b:Bob=> Stopwatch.time("StackRouter:bob",sendListenersMessage(b))
    case e:Emerge=> Stopwatch.time("StackRouter:emerge",sendListenersMessage(e))
    case s:Silently=> Stopwatch.time("StackRouter:silently",{
      val qp = s.present
      //XMPPQuestionSyncActor ! QuestionSyncRequest(qp.location,qp.id,true)
    })
    case d:Detail => Stopwatch.time("StackRouter:detail",sendListenersMessage(d))
    case q:StackQuestion => Stopwatch.time("StackRouter:stackQuestion",{}) //XMPPQuestionSyncActor ! QuestionSyncRequest(q.teachingEvent.is,q._id.is.toString,false))
    case q:QuestionPresenter => Stopwatch.time("StackRouter:questionPresenter", {})//XMPPQuestionSyncActor ! QuestionSyncRequest(q.location,q.id,false))
    case other => warn("StackRouter received unknown: %s".format(other))
  }
}
class StackServer(location:String) extends StackRouter with Logger{
  private var hasFetched = false
  private var evaluatedQuestions:List[QuestionPresenter] = List.empty[QuestionPresenter]
  override def addQuestion(question:QuestionPresenter) = Stopwatch.time("StackServer:addQuestion",{
    TopicServer ! TopicActivity(location)
    val otherQuestions = evaluatedQuestions.filterNot(q => q.id == question.id)
    if (!question.context.deleted.get)
      evaluatedQuestions = question :: otherQuestions
    else evaluatedQuestions = otherQuestions
  })
  def fetchQuestionsFromDB = StackQuestion.findAll("teachingEvent",location).map(new QuestionPresenter(_))
  override def questions:List[QuestionPresenter] = Stopwatch.time("StackServer:questions",{
    if (!hasFetched){
      evaluatedQuestions = fetchQuestionsFromDB.filterNot(_.context.deleted.get)
      hasFetched = true
    }
    evaluatedQuestions
  })
}

case class WorkUpdateQuestion(question:StackQuestion,discussionPoint:DiscussionPoint,when:Long)
case class WorkUpdateAnswer(question:StackQuestion,answer:StackAnswer,discussionPoint:DiscussionPoint,when:Long)
case class WorkUpdateComment(question:StackQuestion,comment:StackComment,discussionPoint:DiscussionPoint,when:Long)
case class WorkCreateQuestion(author:String,text:String,session:Box[LiftSession],when:Long)
case class WorkCreateAnswerToQuestion(question:StackQuestion,author:String,text:String,session:Box[LiftSession],when:Long)
case class WorkCreateCommentOnAnswer(question:StackQuestion,context:StackAnswer,author:String,text:String,session:Box[LiftSession],when:Long)
case class WorkCreateCommentOnComment(question:StackQuestion,context:StackComment,author:String,text:String,session:Box[LiftSession],when:Long)
case class WorkVoteUpQuestion(context:StackQuestion,question:QuestionPresenter,who:String)
case class WorkVoteDownQuestion(context:StackQuestion,question:QuestionPresenter,who:String)
case class WorkVoteUpAnswer(parent:StackQuestion,context:StackAnswer,answer:Answer,who:String)
case class WorkVoteDownAnswer(parent:StackQuestion,context:StackAnswer,answer:Answer,who:String)
case class WorkVoteUpComment(parent:StackQuestion,context:StackComment,comment:Comment,who:String)
case class WorkVoteDownComment(parent:StackQuestion,context:StackComment,comment:Comment,who:String)
case class WorkDeleteQuestion(context:StackQuestion,who:String,when:Long)
case class WorkDeleteAnswer(parent:StackQuestion,context:StackAnswer,who:String,when:Long)
case class WorkDeleteComment(parent:StackQuestion,context:StackComment,who:String,when:Long)

abstract class OpenRequest {}
case class WorkOpenQuestion(context:StackQuestion,who:String,session:Box[LiftSession],when:Long) extends OpenRequest
case class WorkOpenAnswer(parentContext:StackQuestion,context:StackAnswer,who:String,session:Box[LiftSession],when:Long) extends OpenRequest
case class WorkOpenComment(parentContext:StackQuestion,context:StackComment,who:String,session:Box[LiftSession],when:Long) extends OpenRequest

class StackWorker(location:String) extends LiftActor with Logger {
  private lazy val stackServer = StackServerManager.get(location)
  override def messageHandler = {
    case WorkCreateQuestion(author,text,session,timeticks) => Stopwatch.time("StackWorker:workCreateQuestion",{
      val q = StackQuestion.createRecord.about(DiscussionPoint(com.metl.model.Author(author), text)).teachingEvent(location).creationDate(timeticks).save(true)
      val rep = Informal.createRecord.time(timeticks).protagonist(author).action(GainAction.MadeQuestionOnStack).conversation(location)
      Reputation.accrue(rep)
      val newQ = new QuestionPresenter(q)
      stackServer ! newQ
      StackOverflow.local(Detail(q._id.get.toString),author,location,session)
    })
    case WorkCreateAnswerToQuestion(context,author,text,session,timeticks) => Stopwatch.time("StackWorker:workCreateAnswerToQuestion",{
      val stackAnswer = StackAnswer(nextFuncName, context._id.get.toString,DiscussionPoint(com.metl.model.Author(author),text), List.empty[Vote], List.empty[StackComment], timeticks, Nil, Nil, false)
      context.addAnswer(stackAnswer)
      val rep = Informal.createRecord.time(timeticks).protagonist(author).antagonist(context.about.get.author.name).action(GainAction.MadeAnswerOnStack).conversation(context.teachingEvent.get).question(context._id.get)
      Reputation.accrue(rep)
      stackServer ! Silently(new QuestionPresenter(context))
      stackServer ! Emerge("#%s div".format(context.id))
    })
    case WorkCreateCommentOnAnswer(parent,context,author,text,session,timeticks) => Stopwatch.time("StackWorker:workCreateCommentOnAnswer",{
      val stackComment = StackComment(nextFuncName,parent._id.get.toString,DiscussionPoint(com.metl.model.Author(author),text), timeticks, List.empty[Vote], List.empty[StackComment], false)
      context.addComment(stackComment)
      StackOverflow.setCommentState(location,parent.id.toString,context.id,true,author,session)
      val rep = Informal.createRecord.time(timeticks).protagonist(author).antagonist(context.about.author.name).action(GainAction.MadeCommentOnStack).conversation(location).question(parent._id.get)
      stackServer ! Silently(new QuestionPresenter(parent))
      stackServer ! Emerge("#%s .comments".format(context.id))
      Reputation.accrue(rep)
    })
    case WorkCreateCommentOnComment(parent,context,author,text,session,timeticks) => Stopwatch.time("StackWorker:workCreateCommentOnComment",{
      val stackComment = StackComment(nextFuncName,parent._id.get.toString,DiscussionPoint(com.metl.model.Author(author),text), timeticks, List.empty[Vote], List.empty[StackComment], false)
      context.addComment(stackComment)
      StackOverflow.setCommentState(location,parent.id.toString,context.id,true,author,session)
      val rep = Informal.createRecord.time(timeticks).protagonist(author).antagonist(context.about.author.name).action(GainAction.MadeCommentOnStack).conversation(location).question(parent._id.get)
      stackServer ! Silently(new QuestionPresenter(parent))
      stackServer ! Emerge("#%s .comments".format(context.id))
      Reputation.accrue(rep)
    })
    case WorkUpdateQuestion(context,discussionPoint,timeticks) => Stopwatch.time("StackWorker:workUpdateQuestion",{
      context.updateContent(discussionPoint.content)
      val rep = Informal.createRecord.time(timeticks).protagonist(discussionPoint.author.name).antagonist(context.about.get.author.name).action(GainAction.EditedQuestionOnStack).conversation(context.teachingEvent.get).question(context._id.get)
      stackServer ! Silently(new QuestionPresenter(context))
      stackServer ! Emerge("#%s div".format(context.id))
      Reputation.accrue(rep)
    })
    case WorkUpdateAnswer(parent,context,discussionPoint,timeticks) => Stopwatch.time("StackWorker:workUpdateAnswer",{
      context.updateContent(discussionPoint.content)
      val rep = Informal.createRecord.time(timeticks).protagonist(discussionPoint.author.name).antagonist(context.about.author.name).action(GainAction.EditedAnswerOnStack).conversation(parent.teachingEvent.get).slide(parent.slideJid.get).question(parent._id.get)
      stackServer ! Silently(new QuestionPresenter(parent))
      stackServer ! Emerge("#%s div".format(context.id))
      Reputation.accrue(rep)
    })
    case WorkUpdateComment(parent,context,discussionPoint,timeticks) => Stopwatch.time("StackWorker:workUpdateComment",{
      context.updateContent(discussionPoint)
      val rep = Informal.createRecord.time(timeticks).protagonist(discussionPoint.author.name).antagonist(context.about.author.name).action(GainAction.EditedCommentOnStack).conversation(parent.teachingEvent.get).slide(parent.slideJid.get).question(parent._id.get)
      stackServer ! Silently(new QuestionPresenter(parent))
      stackServer ! Emerge("#%s div".format(context.id))
      Reputation.accrue(rep)
    })
    case WorkVoteUpQuestion(context:StackQuestion,question:QuestionPresenter,who:String) => Stopwatch.time("StackWorker:workVoteUpQuestion",{
      if(question.voteUp(who)){
        stackServer ! Silently(new QuestionPresenter(context))
        stackServer ! BobUp(".%s .voteCount".format(question.summaryId))
      }
    })
    case WorkVoteDownQuestion(context:StackQuestion,question:QuestionPresenter,who:String) => Stopwatch.time("StackWorker:workVoteDownQuestion",{
      if(question.voteDown(who)){
        stackServer ! Silently(new QuestionPresenter(context))
        stackServer ! BobDown(".%s .voteCount".format(question.summaryId))
      }
    })
    case WorkVoteUpAnswer(parent:StackQuestion,context:StackAnswer,answer:Answer,who:String) => Stopwatch.time("StackWorker:workVoteUpAnswer",{
      if(answer.voteUp(who)){
        stackServer ! Silently(new QuestionPresenter(parent))
        stackServer ! BobUp(".%s .voteCount".format(context.id))
      }
    })
    case WorkVoteDownAnswer(parent:StackQuestion,context:StackAnswer,answer:Answer,who:String) => Stopwatch.time("StackWorker:workVoteDownAnswer",{
      if(answer.voteDown(who)){
        stackServer ! Silently(new QuestionPresenter(parent))
        stackServer ! BobDown(".%s .voteCount".format(context.id))
      }
    })
    case WorkVoteUpComment(parent:StackQuestion,context:StackComment,comment:Comment,who:String) => Stopwatch.time("StackWorker:workVoteUpComment",{
      if(comment.voteUp(who)){
        stackServer ! Silently(new QuestionPresenter(parent))
        stackServer ! BobUp(".%s .voteCount".format(context.id))
      }
    })
    case WorkVoteDownComment(parent:StackQuestion,context:StackComment,comment:Comment,who:String) => Stopwatch.time("StackWorker:workVoteDownComment",{
      if(comment.voteDown(who)){
        stackServer ! Silently(new QuestionPresenter(parent))
        stackServer ! BobDown(".%s .voteCount".format(context.id))
      }
    })
    case WorkDeleteQuestion(context,who,timeticks) => Stopwatch.time("StackWorker:workDeleteQuestion",{
      context.delete
      stackServer ! Silently(new QuestionPresenter(context))
      stackServer ! Emerge("#%s".format(context.id))
      val rep = Informal.createRecord.time(timeticks).protagonist(who).antagonist(context.about.get.author.name).action(GainAction.DeletedQuestionOnStack).conversation(location)
      Reputation.accrue(rep)
    })
    case WorkDeleteAnswer(parent,context,who,timeticks) => Stopwatch.time("StackWorker:workDeleteAnswer",{
      context.delete
      stackServer ! Silently(new QuestionPresenter(parent))
      stackServer ! Emerge("#%s".format(context.id))
      val rep = Informal.createRecord.time(timeticks).protagonist(who).antagonist(context.about.author.name).action(GainAction.DeletedAnswerOnStack).conversation(location)
      Reputation.accrue(rep)
    })
    case WorkDeleteComment(parent,context,who,timeticks) => Stopwatch.time("StackWorker:workDeleteComment",{
      context.delete
      stackServer ! Silently(new QuestionPresenter(parent))
      stackServer ! Emerge("#%s".format(context.id))
      val rep = Informal.createRecord.time(timeticks).protagonist(who).antagonist(context.about.author.name).action(GainAction.DeletedCommentOnStack).conversation(location)
      Reputation.accrue(rep)
    })
    case other => warn("stackWorker received unknown message: %s".format(other.toString))
  }
}

object StackServerManager {
  private lazy val stackServers = new ConcurrentHashMap[String, StackServer]()
  def get(location: String) = {
    stackServers.computeIfAbsent(location, (l:String) => createNewStackServer(l))
  }
  def createNewStackServer(location:String) = {
    new StackServer(location)
  }
  private lazy val stackWorkers = new ConcurrentHashMap[String,StackWorker]()
  def getWorker(location:String) = {
    stackWorkers.computeIfAbsent(location, (l:String) => createNewStackWorker(l))
  }
  def createNewStackWorker(location:String) = {
    new StackWorker(location)
  }
}
case class ExpansionStrategy(label:String,value:String){}
object ExpansionStrategy{
  val all =  List(
    ExpansionStrategy("Listing","list"),
    ExpansionStrategy("Summarizing","summary")
  )
  val default = all(0)
  def apply(label:String):ExpansionStrategy = all.filter(_.value == label).head
}
object StackOverflow extends StackOverflow {
  def localOpenAction(a:OpenRequest):Unit = a match {
    case WorkOpenQuestion(context,author,session,timeticks) => Stopwatch.time("StackWorker:workOpenQuestion",{
      val id = context._id.toString
      val location = context.teachingEvent.get
      val rep = Informal.createRecord.time(timeticks).protagonist(author).antagonist(context.about.get.author.name).action(GainAction.ViewedQuestionOnStack).conversation(location)
      Reputation.accrue(rep)
      local(Detail(id),author,location,session)
    })
    case WorkOpenAnswer(parent,context,who,session,timeticks) => Stopwatch.time("StackWorker:workOpenAnswer",{
      val contextId = context.id
      val parentId = parent._id.get.toString
      val location = parent.teachingEvent.get
      val rep = Informal.createRecord.time(timeticks).protagonist(who).antagonist(context.about.author.name).action(GainAction.ViewedAnswerOnStack).conversation(location)
      Reputation.accrue(rep)
      val currentState = getCommentState(location,parentId,contextId,who)
      val newState = !currentState
      setCommentState(location,parentId,contextId,newState,who,session)
    })
    case WorkOpenComment(parent,context,who,session,timeticks) => Stopwatch.time("StackWorker:workOpenComment",{
      val contextId = context.id
      val parentId = parent._id.get.toString
      val location = parent.teachingEvent.get
      val rep = Informal.createRecord.time(timeticks).protagonist(who).antagonist(context.about.author.name).action(GainAction.ViewedCommentOnStack).conversation(location)
      Reputation.accrue(rep)
      val currentState = getCommentState(location,parentId,contextId,who)
      val newState = !currentState
      setCommentState(location,parentId,contextId,newState,who,session)
    })
    case other => warn("StackOverflow:localOpenAction received unknown message: %s".format(other))
  }

  private lazy val openedComments = new ConcurrentHashMap[Tuple2[String,String],List[String]]()
  def getOpenedComments(location:Tuple2[String,String]) = {
    openedComments.computeIfAbsent(location, (location:Tuple2[String,String]) => List.empty[String])
  }
  private lazy val requestedDetailedQuestion = new ConcurrentHashMap[Tuple2[String,String],Box[String]]()
  def getRequestedDetailedQuestion(where:Tuple2[String,String]):Box[String] = {
    requestedDetailedQuestion.computeIfAbsent(where, (where:Tuple2[String,String]) => Empty)
  }
  def setRequestedDetailedQuestion(where:Tuple2[String,String],what:String):Unit = requestedDetailedQuestion.put(where, Full(what))
  def localSession = S.session
  def remoteMessageRecieved(message:Any, location:String):Unit = {
    StackServerManager.get(location) ! message
  }
  def local(message:Any, location:String):Unit = local(message,location,localSession)
  def local(message:Any, location:String, session:Box[LiftSession]):Unit = {
    val where = Globals.stackOverflowName(location)
    for(s <- session)
      s.sendCometActorMessage("StackOverflow", Box.legacyNullTest(where), message)
  }
  def local(message:Any, who:String, location:String, session:Box[LiftSession]):Unit = {
    val where = Globals.stackOverflowName(who,location)
    for (s <- session)
      s.sendCometActorMessage("StackOverflow", Box.legacyNullTest(where), message)
  }
  def setCommentState(location:String, questionId:String,changedNode:String,newState:Boolean):Unit = setCommentState(location, questionId,changedNode,newState,localSession)
  def setCommentState(location:String, questionId:String,changedNode:String,newState:Boolean,session:Box[LiftSession]):Unit = {
    val identifier = Tuple2(currentUser.is,questionId)
    val oldList = getOpenedComments(identifier).filterNot(a => a == changedNode)
    val newList = if (newState){ changedNode :: oldList} else oldList
    openedComments.put(identifier, newList)
    local(SetExpansionState(questionId,newList),location,session)
  }
  def setCommentState(location:String, questionId:String,changedNode:String,newState:Boolean,who:String,session:Box[LiftSession]):Unit = {
    val identifier = Tuple2(who,questionId)
    val oldList = getOpenedComments(identifier).filterNot(a => a == changedNode)
    val newList = if (newState){ changedNode :: oldList} else oldList
    openedComments.put(identifier, newList)
    local(SetExpansionState(questionId,newList),who,location,session)
  }
  def getCommentState(where:String,qId:String,rqT:String):Boolean = getCommentState(where,qId,rqT,currentUser.is)
  def getCommentState(location:String, questionId:String,requestedTopic:String,who:String):Boolean = getOpenedComments(Tuple2(who,questionId)).contains(requestedTopic)
  def setInitialState(location:String,questionId:String,openTopics:List[String]):Unit = setInitialState(location,questionId,openTopics,localSession)
  def setInitialState(location:String,questionId:String,openTopics:List[String],session:Box[LiftSession]):Unit = setInitialState(location,questionId,openTopics,currentUser.is,session)
  def setInitialState(location:String,questionId:String,openTopics:List[String],who:String,session:Box[LiftSession]):Unit = {
    openedComments.put(Tuple2(who,questionId), openTopics)
    local(SetExpansionState(questionId,openTopics),who,location,session)
    setDetailedQuestion(location,questionId,who,session)
  }
  private def setDetailedQuestion(location:String,questionId:String,who:String):Unit = setDetailedQuestion(location,questionId,who,localSession)
  private def setDetailedQuestion(location:String,questionId:String,who:String,session:Box[LiftSession]):Unit = {
    setRequestedDetailedQuestion((who,location),questionId)
    local(Detail(questionId,true),who,location,session)
  }
}

class StackOverflow extends CometActor with CometListener with Logger {
  private var starting:Boolean = true
  private lazy val location = currentStack.is.teachingEventIdentity.get
  lazy val stackServer = StackServerManager.get(location)
  private lazy val stackWorker = StackServerManager.getWorker(location)
  val id = nextFuncName
  private var AddQuestionDialog:Box[MeTLInteractableMessage] = Empty
  private var AddAnswerDialog:Box[MeTLInteractableMessage] = Empty
  private var AddCommentDialog:Box[MeTLInteractableMessage] = Empty
  private def session = S.session
  private var expansion = ExpansionStrategy.default
  def questions:List[QuestionPresenter] = stackServer.questions
  var oldQuestions:List[String] = List.empty[String]
  private var detailedQuestion:Box[QuestionPresenter] = Empty
  override def exceptionHandler = {
    case e => {
      error("StackOverflow threw exception: %s".format(e))
    }
  }

  def registerWith:StackRouter = stackServer
  override def lifespan:Box[TimeSpan] = Full(1 minute)
  private def setStartupQuestion = {
    detailedQuestion = StackOverflow.getRequestedDetailedQuestion((currentUser.is,location)) match {
      case Full(qId) => questions.find(q => q.context.id == qId) match {
        case Some(question) => {
          Full(question)
        }
        case _ => Empty
      }
      case _ => Empty
    }
  }
  private def acceptNewQuestion(q:QuestionPresenter, notifySummaries:Boolean, act:()=>JsCmd)={
    if(q.context.deleted.get){
      detailedQuestion.map(dq=>{
        if(dq.summaryId == q.summaryId) {
          detailedQuestion = Empty
          Notices.local(MeTLSpam(Text("The question you were viewing has been deleted by the author")))
          partialUpdate(updateDetailedQuestion)
        }
      })
      partialUpdate(Run("$('#%s').remove()".format(q.summaryId)))
    }
    else{
      detailedQuestion.map(fq=>if(fq.id == q.id){
        partialUpdate(act())
      })
      partialUpdate(
        (if(notifySummaries){
          Call("ensureId",q.summaryId) & Replace(q.summaryId,q.summaryHtml) &
          (oldQuestions.contains(q.id) match {
            case true => Call("bobUp", "#%s".format(q.summaryId))
            case _ => Call("jiggle", "#%s".format(q.summaryId))
          })
        }
        else
          Noop) &
          SetHtml("headerQuestionCount",Text(questions.size.toString)))
    }
  }
  def setDefaultClientSideExpansionState = detailedQuestion.map(dq=> {
    setClientSideExpansionState(dq.id,StackOverflow.getOpenedComments((currentUser.is,dq.id)))
  }).foldLeft(Noop)((acc,item) => acc & item)
  def setClientSideExpansionState(questionid:String,openItems:List[String]) = {
    Call("setCommentExpansionState",JArray(List(openItems.map(JString(_)):_*)))
  }
  def refreshClientSideFilterAndSort = Call("refreshFilterAndSort")
  override def lowPriority = {
    case anything => if (!starting) actUponLowPriority(anything)
  }
  def actUponLowPriority(a:Any):Unit = a match {
    case BobUp(id)=> Stopwatch.time("Bubbles:bobUp",partialUpdate(Call("bobUp",id)))
    case BobDown(id)=> Stopwatch.time("Bubbles:bobDown",partialUpdate(Call("bobDown",id)))
    case Emerge(selector)=> Stopwatch.time("bubbles:emerge",partialUpdate(Call("emerge",selector)))
    case Silently(q)=> Stopwatch.time("Bubbles:silently",acceptNewQuestion(q,true,silentlyUpdateDetailedQuestion _))
    case SetExpansionState(q,l) => Stopwatch.time("Bubbles:setExpansionState",partialUpdate(setClientSideExpansionState(q,l)))
    case q:QuestionPresenter=> Stopwatch.time("Bubbles:questionPresenter",partialUpdate(updateLocation & updateDetailedQuestion & updateSummaryFor(q)))
    case qs:List[QuestionPresenter]=> Stopwatch.time("Bubbles:list[questionPresenter]",partialUpdate(updateLocation & updateDetailedQuestion & updateQuestions))
    case Detail(questionId,overrideShow) => Stopwatch.time("Bubbles:detail",{
      val start = new Date().getTime
      AddAnswerDialog.map(_.done)
      AddCommentDialog.map(_.done)
      val newQuestion = questions.find(q => q.context.id == questionId)
      val oldDetailedQuestionId = detailedQuestion.map(dq => dq.context.id.toString).openOr("")
      if (overrideShow) detailedQuestion = newQuestion
      else detailedQuestion = newQuestion.filter(q => q.context.id.toString != oldDetailedQuestionId)
      val newDetailedQuestionId = detailedQuestion.map(dq => dq.context.id.toString).openOr("")
      StackOverflow.setRequestedDetailedQuestion((currentUser.is,location),newDetailedQuestionId)
      partialUpdate(updateDetailedQuestion & setDefaultClientSideExpansionState)
    })
    case unknown => warn("StackOverflow: I do not know what to do with -> %s".format(unknown))
  }
  def updateDetailedQuestion = {
    Call("$('#questionDetail').slideUp",
      JString("fast"),
      AnonFunc("", silentlyUpdateDetailedQuestion & Run("$('#questionDetail').slideDown()")))
  }
  def silentlyUpdateDetailedQuestion = {
    detailedQuestion match {
      case Full(question) => {
        detailedQuestion = questions.find(q => q.id == question.id)
      }
      case _ => ()
    }
    SetHtml("questionDetail", detailedQuestion.map(_.detail).getOrElse(NodeSeq.Empty)) & refreshClientSideFilterAndSort
  }
  def visibleQuestions = questions
  def summaries(qs:Seq[QuestionPresenter]) = StackTemplateHolder.summaryHeaderTemplate ++ qs.map(_.summaryHtml)
  def pluralize(input:Int = questions.size) = input match{
    case 1 => "Question"
    case _ => "Questions"
  }
  def render = Stopwatch.time("Bubbles:render",{
    partialUpdate(updateLocation & updateQuestions & updateDetailedQuestion)
    starting = false
    NodeSeq.Empty
  })
  def updateLocation = Call("setActiveTopic", JString(location))
  def updateSummaryFor(q:QuestionPresenter) = {
    val summary = q.summaryHtml
    val internalQuestions = questions
    oldQuestions = internalQuestions.map(q => q.id)
    Stopwatch.time("Bubbles:updateSummaryFor:jsCmds",{
      Call("ensureId",q.summaryId) &
      Replace(q.summaryId,summary) &
      Call("bobUp", "#%s".format(q.summaryId)) &
      SetHtml("headerQuestionCount", Text(internalQuestions.size.toString)) &
      SetHtml("pluralizedQuestionCount", Text(pluralize(internalQuestions.size))) &
      refreshClientSideFilterAndSort &
      setDefaultClientSideExpansionState})
  }
  def updateQuestions = {
    val internalQuestions = questions
    oldQuestions = internalQuestions.map(q => q.id)
    val renderedQuestions = Stopwatch.time("Bubbles:updateQuestions:summaries",summaries(internalQuestions))
    val res = Stopwatch.time("Bubbles:updateQuestions:jsCmds",{SetHtml("headerQuestionCount", Text(internalQuestions.size.toString)) &
      SetHtml("pluralizedQuestionCount", Text(pluralize(internalQuestions.size))) &
      SetHtml("stackQuestions",
        expansion.value match{
          case "list" =>  <table id={id}>{renderedQuestions}</table>
        }) &
      refreshClientSideFilterAndSort &
      setDefaultClientSideExpansionState})
    res
  }

  override def fixedRender = Stopwatch.time("Bubbles:fixedRender",{
    setStartupQuestion
    "#teachersContainer *" #> BubbleConstants.teachers(location).map(teacher => <div class='teacher'>{teacher}</div>) &
    ".addChild *" #> {x:NodeSeq => addQuestion(x)}
  })
  private val PING_LATENCY = "pingLatency"
  override def autoIncludeJsonCode = true
  override def receiveJson = {
    case JObject(List(JField("command",JString(PING_LATENCY)), JField("params",(JInt(start))))) =>{
      partialUpdate(Call("pongLatency",JInt(start)))
    }
    case other => warn("Stack did not understand: %s".format(other))
  }
  def pingLatency = Script(Function(PING_LATENCY,List.empty[String],jsonSend(PING_LATENCY,JsRaw("new Date().getTime()"))) & OnLoad(Call(PING_LATENCY)))
  def latencyGauge(x:NodeSeq) = {
    val id = nextFuncName
    <div id={id}>{x}</div> ++ pingLatency
  }

  private def sendAddQuestion = {
    AddQuestionDialog.map(_.done)
    var validSubmit:Boolean = false
    var hasSubmitted:Boolean = false
    val id = "addQuestionInput"
    val submitId = "addQuestionSubmit"
    val message = MeTLInteractableMessage(
      self =>{
        (".inputDialog [class+]" #> "addQuestionDialog" &
          ".inputDialog [id+]" #> "addQuestionDialog_%s".format(id) &
          ".inputDialogHeader *" #> Text("What is your question?") &
          ".inputDialogContentBox" #> textarea("", (t:String) =>{
            if(!hasSubmitted && t.trim.length > 0){
              stackWorker ! WorkCreateQuestion(currentUser.is,t,session,new java.util.Date().getTime)
              hasSubmitted = true
              validSubmit = true
            }
          }, ("id",id)) &
          ".inputDialogSubmitButton" #> ajaxSubmit("Submit", ()=>{
            if (validSubmit){
              self.done
              Noop
            }
            else Notices.local(MeTLSpam(Text("please enter a question")))
          }, ("id",submitId))
        ).apply(StackTemplateHolder.inputDialogTemplate) ++ StackHelpers.activateNewInputBox(id,submitId)
      },true).entitled("Type your question").identifiedBy("addQuestion")
    AddQuestionDialog = Full(message)
    Notices.local(message)
  }
  private def addQuestion(x:NodeSeq) ={
    a(()=>{
      sendAddQuestion
      Noop
    }, x, ("id","addQuestion"))
  }
}
