package com.metl

import _root_.java.io.File
import _root_.junit.framework._
import Assert._
import _root_.scala.xml.XML
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.Keys
import com.mongodb._
import net.liftweb.mongodb._
import scala.util.Random
import collection.JavaConversions._
import concurrent.ops._
import comet.Rater
import net.liftweb.util.ControlHelpers._
import org.openqa.selenium.remote._
import java.util.Date
import scala.concurrent.ops._
import scala.actors.Futures
import net.liftweb.record.field._
import net.liftweb.mongodb._
import net.liftweb.common._
import net.liftweb.record.field._
import net.liftweb.mongodb.record._
import net.liftweb.mongodb.record.field._
import net.liftweb.actor._
import net.liftweb.http.ListenerManager

case class TestResult(name:String, user:SeleniumUser, startTime:Long, duration:Long, result:Boolean, exception:Box[Throwable])

class TestResultRecord extends MongoRecord[TestResultRecord] with MongoId[TestResultRecord]{
	def meta = TestResultRecord

	object name extends StringField(this, 50)
	object user extends StringField(this, 50)
	object startTime extends LongField(this)
	object duration extends LongField(this)
	object result extends BooleanField(this)
	object exception extends StringField(this,250)
	object testRun extends StringField(this,100)
}

object TestResultRecord extends TestResultRecord with MongoMetaRecord[TestResultRecord] {
	def fromTestResult(t:TestResult) = {
		val name = t.name
		val user = t.user.username
		val duration = t.duration
		val startTime = t.startTime
		val result = t.result
		val exception = t.exception.map(e => e.getMessage.toString.take(250)).openOr("no exception")
		val testRun = StackTestHelpers.currentTest
		createRecord.name(name).user(user).startTime(startTime).duration(duration).result(result).exception(exception).testRun(testRun)
	}
}

object MeTLXTestHelpers{
	var currentTest = ""
}

object StackTestHelpers{
	var currentTest = ""
}

case class TestingAction(name:String,weight:Int,doable:()=>Unit,requirements:()=>Boolean){
	def act = if (requirements()) doable()
	def canAct = requirements()
}

abstract class SeleniumUser(usr:String,svr:String){
	val username:String = usr
	val server:String = svr
	def setup:Unit = {}
	def teardown:Unit = {
		driver.quit
	}
	lazy val driver = new ChromeDriver
	protected val globalWait = 120

	// WebElement.getText won't return text for hidden (via CSS) elements.
	// this is the workaround
	def getElementText(element:WebElement) = {
		tryo(driver.executeScript("return $(arguments[0]).text();", element)).openOr("")
	}

	protected def getDisplayedElement(selector:String) ={
		waitUntilDisplayed(selector)
		driver.findElement(new By.ByCssSelector(selector))
	}

	protected def getDisplayedElements(selector:String) ={
		waitUntilDisplayed(selector)
		driver.findElements(new By.ByCssSelector(selector))
	}

	protected def enterText(selector:String,text:String,pressEnter:Boolean = false) ={
		waitUntilDisplayed(selector)
		driver.executeScript("$('%s').val('%s')".format(selector,text))
		if (pressEnter){
			val element = getDisplayedElement(selector)
			element.sendKeys(Keys.RETURN);
		}
	}

	protected def updateText(selector:String,text:String) ={
		waitUntilDisplayed(selector)
		driver.executeScript("var n = $('%s'); n.val([n.val(),'%s'].join('\\n'))".format(selector,text))
	}

	protected def optionallyApplyIdentifier(optionalId:Box[String],input:String):String = optionalId.map{ case id:String if id.length > 0 => List(input,id).mkString("_") }.openOr(input)

	protected def openEnterSubmit(openSelector:String, inputSelectorPrefix:String, submitSelectorPrefix:String, text:String, trace:Boolean = true) ={
		val openElement = getDisplayedElement(openSelector)
		val id = tryo(openElement.getAttribute("id").split("_")(1))
		openElement.click

		val act = () => {
			updateText(optionallyApplyIdentifier(id,inputSelectorPrefix),text)
			var submitSelector = optionallyApplyIdentifier(id,submitSelectorPrefix)
			getDisplayedElement(submitSelector).click
			waitUntilNotDisplayed(submitSelector)
		}

		if (trace)
			waitAcrossRefresh(openSelector, act)
		else
			act()
	}

	def clickOneOf(selector:String) = {
		var elements = getDisplayedElements(selector)
		var element = elements(Random.nextInt(elements.size))
		element.click
	}

	protected def trace(label:String,action: =>Any)= action

	protected def attemptToCloseAlerts:Unit = {
		tryo({
			val alert = driver.switchTo().alert
			alert.dismiss
			alert.accept
		})
	}
	protected def performTestOrRefresh(label:String, action:() => Unit, needsToRefresh:Boolean = false):Unit ={
		var start = new Date().getTime
		try {
			if (needsToRefresh)
				driver.navigate().refresh()
			attemptToCloseAlerts
			action()
			DBActor ! TestResult(label,this,start,new Date().getTime-start,true,Empty)
		}
		catch {
			case (e:Exception) => {
				if(!needsToRefresh)
					performTestOrRefresh(label,action,true)
				else
					DBActor ! TestResult(label,this,start,new Date().getTime-start,false,Full(e))
			}
		}
	}


	def elementIsDisplayed(selector:String):Boolean = tryo(driver.findElements(By.cssSelector(selector)).first.isDisplayed).openOr(false)

	def allHaveClass(selector:String,clazz:String,permitEmpty:Boolean = false):Boolean = {
		applyCheckToCollectionOfElements(selector,(elements) => {
			elements.filter(e => e.getAttribute("class").split(" ").contains(clazz)).length == elements.length
		},permitEmpty)
	}
	def anyHaveClass(selector:String,clazz:String,permitEmpty:Boolean = false):Boolean = {
		applyCheckToCollectionOfElements(selector,(elements) => {
			elements.exists(e => e.getAttribute("class").split(" ").contains(clazz))
		},permitEmpty)
	}
	def allLackClass(selector:String,clazz:String,permitEmpty:Boolean):Boolean = {
		applyCheckToCollectionOfElements(selector,(elements) => {
			!(elements.exists(e => e.getAttribute("class").split(" ").contains(clazz)))
		},permitEmpty)
	}
	def someLackClass(selector:String,clazz:String,permitEmpty:Boolean):Boolean = {
		applyCheckToCollectionOfElements(selector,(elements) => {
			!(elements.filter(e => e.getAttribute("class").split(" ").contains(clazz)).length == elements.length)
		},permitEmpty)
	}
	protected def applyCheckToCollectionOfElements(cssSelector:String,check:List[WebElement]=>Boolean,emptySucceeds:Boolean):Boolean = {
		val elements = driver.findElements(By.cssSelector(cssSelector)).toList
		if (!emptySucceeds && elements.length == 0)
			false
		else
			check(elements)
	}

	protected val allTests = List.empty[TestingAction]

	protected def perform(name:String):Unit ={
		val testingAction = allTests.filter(a => a.name == name).head
		performTestOrRefresh(testingAction.name, ()=> testingAction.act)
	}
	protected def performPossible:Unit = {
		val possibleTests = allTests.filter(a => a.canAct)
		performRandom(possibleTests)
	}
	protected def performRandom(actions:List[TestingAction]):Unit = {
		val weightedActions = actions.foldLeft(List[TestingAction]())((acc,item) => acc ::: List.fill(item.weight)(item))
		val action = weightedActions(Random.nextInt(weightedActions.length))
		performTestOrRefresh(action.name, () => action.act)
	}

	def act = performPossible
	def act(action:String) = perform(action)

	def waitUntilDisplayed(by:By):Unit = {
      new WebDriverWait(driver, globalWait).until(
        new ExpectedCondition[Boolean](){
          override def apply(driver:WebDriver) = tryo(driver.findElement(by).isDisplayed()).openOr(false)
        }
      )
    }
	def waitUntilDisplayed(id:String):Unit = waitUntilDisplayed(new By.ByCssSelector(id))

	def waitUntilNotDisplayed(by:By):Unit = {
      new WebDriverWait(driver, globalWait).until(
        new ExpectedCondition[Boolean](){
          override def apply(driver:WebDriver) = {
						driver.findElements(by).filter(e => tryo(e.isDisplayed).openOr(false)).isEmpty
		 			}
        }
      )
    }
	def waitUntilNotDisplayed(id:String):Unit = waitUntilNotDisplayed(new By.ByCssSelector(id))

	def waitAcrossRefresh(traceSelector:String, trigger:()=>Unit) {
		driver.findElements(By.cssSelector(traceSelector)).map(e => driver.asInstanceOf[JavascriptExecutor].executeScript("arguments[0].style.display='none'", e))
		waitUntilNotDisplayed(traceSelector)
		trigger()
		waitUntilDisplayed(traceSelector)
	}
}

case class MetlXUser(override val username:String, override val server:String) extends SeleniumUser(username,server){
	override def setup = {
		driver.get("http://%s/board".format(server))
	}
	override val allTests = List(
		TestingAction("toggleApplicationButton",1,toggleApplicationButton _,canToggleApplicationButton _),
		TestingAction("searchForConversation",1,()=> searchForConversation("forb"),canEnterConversationIntoSearchbox _),
		TestingAction("clickSearchButton",1,clickSearchButton _,canSearchConversations _),
		TestingAction("chooseAConversationRandomly",1,chooseAConversation _,canSelectConversation _),
		TestingAction("openConversations",1,openConversations _,canOpenConversations _),
		TestingAction("chooseASlide",1,chooseASlide _,canSelectSlide _),
		TestingAction("showMyConversations",1,showMyConversations _,canShowMyConversations _),
		TestingAction("createConversation",1,createConversation _,canCreateConversation _),
		TestingAction("switchToPrivate",1,switchToPrivate _,canSwitchToPrivate _),
		TestingAction("switchToPublic",1,switchToPublic _,canSwitchToPublic _),
		TestingAction("switchToCollaborate",1,switchToCollaborate _,canSwitchToCollaborate _),
		TestingAction("switchToPresent",1,switchToPresent _,canSwitchToPresent _),
		TestingAction("switchToSelectMode",1,switchToSelectMode _,canSwitchToSelectMode _),
		TestingAction("deleteSelection",1,deleteSelection _,canDeleteSelection _),
		TestingAction("resizeSelection",1,resizeSelection _,canResizeSelection _),
		TestingAction("showSelection",1,showSelection _,canShowSelection _),
		TestingAction("hideSelection",1,hideSelection _,canHideSelection _),
		TestingAction("switchToDrawMode",1,switchToDrawMode _,canSwitchToDrawMode _),
/*
		TestingAction("switchToPen1",1,switchToDrawMode _,canSwitchToDrawMode _),
		TestingAction("switchToPen2",1,switchToDrawMode _,canSwitchToDrawMode _),
		TestingAction("switchToPen3",1,switchToDrawMode _,canSwitchToDrawMode _),
		TestingAction("switchToEraser",1,switchToDrawMode _,canSwitchToDrawMode _),
		TestingAction("openPenCustomization",1,switchToDrawMode _,canSwitchToDrawMode _),
*/
		TestingAction("switchToInsertMode",1,switchToInsertMode _,canSwitchToInsertMode _),
		TestingAction("insertText",1,insertText _,canInsertText _),
		TestingAction("insertImage",1,insertImage _,canInsertImage _),
		TestingAction("switchToFeedbackMode",1,switchToFeedbackMode _,canSwitchToFeedbackMode _),
		TestingAction("submitScreenshot",1,submitScreenshot _,canSubmitScreenshot _),
		TestingAction("openSubmissions",1,openSubmissions _,canOpenSubmissions _),
		TestingAction("openQuizzes",1,openQuizzes _,canOpenQuizzes _)

	
	)


	def canToggleApplicationButton = elementIsDisplayed("#applicationMenuButton")
	def backstageIsOpen = elementIsDisplayed("#backstageContainer")
	def canOpenBackstage = canToggleApplicationButton && !backstageIsOpen
	def canCloseBackstage = canToggleApplicationButton && backstageIsOpen
	def toggleApplicationButton = trace("toggleApplicationButton", { getDisplayedElement("#applicationMenuButton").click })
	
	def canOpenConversations = backstageIsOpen && elementIsDisplayed("#conversations")
	def conversationsOpen = backstageIsOpen && elementIsDisplayed("#conversationsContainer")
	def openConversations = trace("openConversations", { getDisplayedElement("#conversations").click })
	def canSearchConversations = conversationsOpen && elementIsDisplayed("#searchButton")
	def clickSearchButton = trace("clickSearchButton", { getDisplayedElement("#searchButton").click })

	def canCreateConversation = conversationsOpen && elementIsDisplayed("#createConversationButton")
	def createConversation = trace("createConversation", { getDisplayedElement("#createConversationButton").click })

	def canShowMyConversations = conversationsOpen && elementIsDisplayed("#myConversationsButton")
	def showMyConversations = trace("showMyConversations", { getDisplayedElement("#myConversationsButton").click })
	def canEnterConversationIntoSearchbox = conversationsOpen && elementIsDisplayed("#searchForConversationBox")
	def searchForConversation(convName:String) = trace("searchForConversation(%s)".format(convName), {
		enterText("#searchForConversationBox",convName,true) 
	})
	def canSelectConversation = conversationsOpen && elementIsDisplayed(".searchResult")
	def chooseAConversation = trace("chooseAConversation", { clickOneOf(".searchResult") })

//	def canDeleteConversation = conversationsOpen && elementIsDisplayed("
//	def canRenameConversation = conversationsOpen && elementIsDisplayed("
//	def canShareConversation = conversationsOpen && elementIsDisplayed("

	def canSwitchToPrivate = toolsAreVisible && elementIsDisplayed("#privateMode")
	def switchToPrivate = trace("switchToPrivate", {clickOneOf("#privateMode")})
	def canSwitchToPublic = toolsAreVisible && elementIsDisplayed("#publicMode")
	def switchToPublic = trace("switchToPublic", {clickOneOf("#publicMode")})
	
	def canSwitchToCollaborate = toolsAreVisible && elementIsDisplayed("#enableCollaboration")
	def switchToCollaborate = trace("switchToCollaborate", {clickOneOf("#enableCollaboration")})
	def canSwitchToPresent = toolsAreVisible && elementIsDisplayed("#disableCollaboration")
	def switchToPresent = trace("switchToPresent", {clickOneOf("#disableCollaboration")})	

	def canSwitchToSelectMode = toolsAreVisible && elementIsDisplayed("#selectMode")
	def switchToSelectMode = trace("switchToSelectMode", {clickOneOf("#selectMode")})	
	def canDeleteSelection = toolsAreVisible && elementIsDisplayed("#delete") && !allHaveClass("#delete","disabledButton")
	def deleteSelection = trace("deleteSelection", {clickOneOf("#delete")})
	def canResizeSelection = toolsAreVisible && elementIsDisplayed("#resize") && !allHaveClass("#resize","disabledButton")
	def resizeSelection = trace("resizeSelection", {clickOneOf("#resize")})
	def canShowSelection = toolsAreVisible && elementIsDisplayed("#publicize") && !allHaveClass("#publicize","disabledButton")
	def showSelection = trace("showSelection", {clickOneOf("#publicize")})
	def canHideSelection = toolsAreVisible && elementIsDisplayed("#privatize") && !allHaveClass("#privatize","disabledButton")
	def hideSelection = trace("hideSelection", {clickOneOf("#privatize")})
				
	def canSwitchToDrawMode = toolsAreVisible && elementIsDisplayed("#drawMode")		
	def switchToDrawMode = trace("switchToDrawMode", {clickOneOf("#drawMode")})
//	def canSelectPen1 = toolsAreVisible && elementIsDisplayed("#pen1Button")
//	def canSelectPen2 = toolsAreVisible && elementIsDisplayed("#pen2Button")
//	def canSelectPen3 = toolsAreVisible && elementIsDisplayed("#pen3Button")
//	def canSelectErase = toolsAreVisible && elementIsDisplayed("#eraseButton")
//	def canSelectPenCustomization = toolsAreVisible && elementIsDisplayed("#penCustomizationButton")

	def canSwitchToInsertMode = toolsAreVisible && elementIsDisplayed("#insertMode")		
	def switchToInsertMode = trace("switchToInsertMode",{clickOneOf("#insertMode")})
	def canInsertText = toolsAreVisible && elementIsDisplayed("#insertTextButton")
	def insertText = trace("insertText", {clickOneOf("#insertTextButton")})
	def canInsertImage = toolsAreVisible && elementIsDisplayed("#insertImageButton")
	def insertImage = trace("insertImage", {clickOneOf("#insertImageButton")})

	def canSwitchToFeedbackMode = toolsAreVisible && elementIsDisplayed("#feedbackMode")		
	def switchToFeedbackMode = trace("switchToFeedbackMode",{clickOneOf("#feedbackMode")})
	def canSubmitScreenshot = toolsAreVisible && elementIsDisplayed("#submitScreenshotButton")
	def submitScreenshot = trace("submitScreenshot",{clickOneOf("#submitScreenshotButton")})
	def canOpenQuizzes = (toolsAreVisible && elementIsDisplayed("#quizzesSubToolButton")) || elementIsDisplayed("#quizzes") ||  elementIsDisplayed("#quizCount")
	def openQuizzes = trace("openQuizzes",{clickOneOf("#quizCount")})
	def canOpenSubmissions = (toolsAreVisible && elementIsDisplayed("#submissionsSubToolButton")) || elementIsDisplayed("#submissions") || elementIsDisplayed("#submissionCount")
	def openSubmissions = trace("openSubmissions",{clickOneOf("#submissionCount")})

	def canSwitchToZoomMode = toolsAreVisible && elementIsDisplayed("#zoomMode")		
	def canSwitchToPanMode = toolsAreVisible && elementIsDisplayed("#panMode")		
	def canSelectSelectSubtool = toolsAreVisible && elementIsDisplayed("#selectTools")
	def canSelectDrawSubtool = toolsAreVisible && elementIsDisplayed("#drawTools")
	def canSelectInsertSubtool = toolsAreVisible && elementIsDisplayed("#insertTools")
	def canSelectFeedbackSubtool = toolsAreVisible && elementIsDisplayed("#feedbackTools")
	def canSelectZoomSubtool = toolsAreVisible && elementIsDisplayed("#zoomTools")
	def canSelectPanSubtool = toolsAreVisible && elementIsDisplayed("#panTools")
	def canResetZoom = toolsAreVisible && elementIsDisplayed("#zoomToOriginal")
	def canAutoFitZoom = toolsAreVisible && elementIsDisplayed("#zoomToFull")
	def canZoomToPage = toolsAreVisible && elementIsDisplayed("#zoomToPage")
	def canZoomIn = toolsAreVisible && elementIsDisplayed("#in")
	def canZoomOut = toolsAreVisible && elementIsDisplayed("#out")
	def canPanLeft = toolsAreVisible && elementIsDisplayed("#left")
	def canPanRight = toolsAreVisible && elementIsDisplayed("#right")
	def canPanUp = toolsAreVisible && elementIsDisplayed("#up")
	def canPanDown = toolsAreVisible && elementIsDisplayed("#down")
	def canOpenPreferences = backstageIsOpen && elementIsDisplayed("#preferences")
	def canOpenConversation = backstageIsOpen && elementIsDisplayed("#conversations")
	def toolsAreVisible = elementIsDisplayed("#toolsColumn")
	def slidesAreVisible = elementIsDisplayed("#slidesColumn")
	def canShowTools = elementIsDisplayed("#restoreTools") && !toolsAreVisible
	def canHideTools = elementIsDisplayed("#restoreTools") && toolsAreVisible
	def canShowSlides = elementIsDisplayed("#restoreSlides") && !slidesAreVisible
	def canHideSlides = elementIsDisplayed("#restoreSlides") && slidesAreVisible 
	def canAddSlide = elementIsDisplayed("#addSlideButton") && slidesAreVisible

	def canSelectSlide = elementIsDisplayed(".slideContainer") && slidesAreVisible
	def chooseASlide = trace("chooseASlide", { clickOneOf(".slideContainer") })

	def submissionsAreVisible = backstageIsOpen && elementIsDisplayed("#submissionsContainer")
	def canSelectSubmission = submissionsAreVisible && elementIsDisplayed(".submissionSummary")
	def quizzesAreVisible = backstageIsOpen && elementIsDisplayed("#quizzesContainer")
	def canSelectQuiz = quizzesAreVisible && elementIsDisplayed(".quizSummary")
	def canCreateQuiz = quizzesAreVisible && elementIsDisplayed("#quizCreationButton")
	def quizCreationDialogOpen = quizzesAreVisible && elementIsDisplayed("#createQuizForm")
	def canAddOptionToQuiz = quizCreationDialogOpen && elementIsDisplayed(".quizAddOptionButton")
	def canDeleteOptionFromQuiz = quizCreationDialogOpen && elementIsDisplayed(".quizRemoveOptionButton")	
	def caAttachCurrentSlideToQuiz = quizCreationDialogOpen && elementIsDisplayed(".quizAttachImageButton")
	def canDeleteQuiz = quizCreationDialogOpen && elementIsDisplayed(".quizDeleteButton")
	def canSubmitQuiz = quizCreationDialogOpen && elementIsDisplayed(".quizSubmitButton")
	def canEnterQuizQuestion = quizCreationDialogOpen && elementIsDisplayed(".quizQuestion")
	def canEnterQuizOptionText = quizCreationDialogOpen && elementIsDisplayed(".quizText")
	def canEditQuiz = quizCreationDialogOpen && elementIsDisplayed(".quizSummaryEditButton")
	def canFocusQuiz = quizzesAreVisible && elementIsDisplayed(".quizSummary")
	def quizFocused = quizzesAreVisible && elementIsDisplayed(".quizItem")
	def canAnswerQuiz = quizFocused && elementIsDisplayed(".quizOption")
//	def canDisplayQuizOnNextSlide = quizFocused && elementIsDisplayed("
}

case class StackUser(override val username:String, override val server:String) extends SeleniumUser(username,server){
  override def setup = {
		openTopic("default")
	}
	
	def availableQuestions = driver.findElements(By.cssSelector("#stackQuestions .branchAuthor"))
	def randomQuestionIndex = Random.nextInt(availableQuestions.size)
	def availableQuestionsMine = availableQuestions.map(q => getElementText(q)).zipWithIndex.filter(n => n._1 == username).map(_._2)
	def randomQuestionIndexMine = availableQuestionsMine(Random.nextInt(availableQuestionsMine.size))

	protected def upVote(voterSelector:String) ={
		var voterElement = getDisplayedElement(voterSelector)
		var id = voterElement.getAttribute("id").split("_")(1)
		var voterClickableSelector = "#upVote_"+id
		var voterClickable = getDisplayedElement(voterClickableSelector)
		voterClickable.click
		waitUntilNotDisplayed(voterClickableSelector)
	}

	def openTopic(topic:String) = driver.get("http://%s/impersonate/%s/stack/%s".format(server,username,topic))
	
	protected def openRandomQuestion ={
		trace("openRandomQuestion", {
			var index = randomQuestionIndex
			getDisplayedElements(".summaryQuestionText")(index).click
			waitUntilDisplayed(".bubbleContainer")
		})
	}
	protected def openRandomQuestionMine ={
		trace("Opening random question (mine)", {
			var index = randomQuestionIndex
			getDisplayedElements(".summaryQuestionText")(index).click
			waitUntilDisplayed(".bubbleContainer")
		})
	}

	// requirements
	def canAnswerQuestion      = elementIsDisplayed(".bubbleContainer")
	def canVoteQuestionUp      = elementIsDisplayed(".questionVoter a.upArrow")
	//def canVoteQuestionDown    = elementIsDisplayed(".questionVoter a.downArrow")
	def canEditQuestion        = elementIsDisplayed(".editContent.editQuestion")
	def canEditAnswer          = elementIsDisplayed(".editContent.editAnswer")
	def canEditComment         = elementIsDisplayed(".editContent.editComment")
	def canCommentOnAnswer     = elementIsDisplayed(".commentOnAnswer")
	def canCommentOnComment    = elementIsDisplayed(".commentOnComment")
	def canVoteAnswerUp        = elementIsDisplayed(".answerVoter a.upArrow")
	//def canVoteAnswerDown      = elementIsDisplayed(".answerVoter a.downArrow")
	def canVoteCommentUp       = elementIsDisplayed(".commentVoter a.upArrow")
	//def canVoteCommentDown     = elementIsDisplayed(".commentVoter a.downArrow")
	def canExpandCommentTree   = elementIsDisplayed(".commentExpander.commentsCollapsed")
	def canCollapseCommentTree = elementIsDisplayed(".commentExpander.commentsExpanded")
	def canChooseSearchResult  = elementIsDisplayed(".immediateSearchResult")

	// tests for questions
	def askAQuestion:Unit = askAQuestion("Can you count to %s? %s is wondering...".format(Random.nextInt(100),username))
	def askAQuestion(text:String):Unit = {
		trace("askAQuestion",{
			openEnterSubmit("#askQuestion","#addQuestionInput","#addQuestionSubmit",text,false)
		})
	}
	def editAQuestion:Unit = editAQuestion("Edited by %s".format(username))
	def editAQuestion(text:String):Unit = {
		trace("editAQuestion",{
			openEnterSubmit(".editContent.editQuestion","#editCurrentQuestionInput","#editContentSubmit",text)
		})
	}

	def voteAQuestionUp = {
		trace("voteAQuestionUp", {
			upVote(".questionVoter")
		})
	}
	/*
	def voteAQuestionDown = {
		trace("voteAQuestionDown", {
			getDisplayedElement(".questionVoter .downArrow").click()
		})
	}
	*/

	// tests for answers
	def answerAQuestion:Unit = answerAQuestion("The answer is %s.".format(Random.nextInt(100)))
	def answerAQuestion(text:String):Unit = {
		trace("answerAQuestion", {
			openEnterSubmit(".answerOnQuestion","#answerInputOnQuestion","#answerSubmitOnQuestion",text)
		})
	}
	def editAnAnswer:Unit = editAnAnswer("Edited by %s".format(username))
	def editAnAnswer(text:String):Unit = {
		trace("editAnAnswer", {
			openEnterSubmit(".editContent.editAnswer","#editCurrentQuestionInput","#editContentSubmit",text)
		})
	}

	def voteAnAnswerUp = {
		trace("voteAnAnswerUp", {
			upVote(".answerVoter")
		})
	}
	/*
	def voteAnAnswerDown = {
		trace("voteAnAnswerDown", {
			getDisplayedElement(".answerVoter .downArrow").click()
		})
	}
	*/

	// tests for comments
	def commentOnAnAnswer:Unit = commentOnAnAnswer("commented on by %s".format(username))
	def commentOnAnAnswer(text:String) = {
		trace("commentOnAnAnswer",{
			openEnterSubmit(".commentOnAnswer","#commentInputOnAnswer","#commentSubmitOnAnswer",text)
		})
	}
	def commentOnAComment:Unit = commentOnAComment("commented on by %s".format(username))
	def commentOnAComment(text:String) = {
		trace("commentOnAComment",{
			openEnterSubmit(".commentOnComment","#commentInputOnComment","#commentSubmitOnComment",text)
		})
	}

	def editAComment:Unit = editAComment("Edited by %s".format(username))
	def editAComment(text:String) = {
		openEnterSubmit(".editContent.editComment","#editCurrentQuestionInput","#editContentSubmit",text)
	}

	def expandACommentTree = {
		getDisplayedElement(".commentExpander.commentsCollapsed").click
	}
	def collapseACommentTree = {
		getDisplayedElement(".commentExpander.commentsExpanded").click
	}

	def voteACommentUp = {
		trace("voteACommentUp", {
			upVote(".commentVoter")
		})
	}
	/*
	def voteACommentDown = {
		trace("voteACommentDown", {
			getDisplayedElement(".commentVoter .downArrow").click()
		})
	}
	*/

	// tests for search
	def searchContent {
		val searchTerms = List(username,"wondering","Edited by","commented on")
		trace("searchContent", {
			enterText(".searchInput",searchTerms(Random.nextInt(searchTerms.size)))
			getDisplayedElement(".searchInputSubmit").click
			getDisplayedElement("#searchResults")
			if (elementIsDisplayed(".emptySearchResults")) {
				getDisplayedElement(".closeSearchResults").click
				waitUntilNotDisplayed("#searchResults")
			}
		})
	}
	def chooseASearchResult {
		trace("chooseASearchResult", {
			waitAcrossRefresh(".bubbleContainer", ()=> getDisplayedElement(".immediateSearchResultClickable").click)
		})
	}

	// tests for topics
	def chooseATopic {
		trace("chooseATopic", {
			waitAcrossRefresh("#appLabel", ()=> { clickOneOf(".topicName") })
		})
	}

	protected def seedTopic(topicNum:Int) ={
		performTestOrRefresh("Seed topic", ()=> {
			openTopic("topic%s".format(topicNum))
			askAQuestion("seed question for topic %s".format(topicNum))
		})
	}
	def seedTopics = Range(0,5,1).map(topicNum => seedTopic(topicNum))
	override val allTests = List(
		TestingAction("Open a question",         2, ()=> openRandomQuestion,     ()=> !canChooseSearchResult && availableQuestions.size > 0),
		TestingAction("Open a question (mine)",  1, ()=> openRandomQuestionMine, ()=> !canChooseSearchResult && availableQuestionsMine.size > 0),
		TestingAction("Ask a question",          5, ()=> askAQuestion,           ()=> !canChooseSearchResult && true),
		TestingAction("Answer a question",       5, ()=> answerAQuestion,        ()=> !canChooseSearchResult && canAnswerQuestion),
		TestingAction("Vote a question up",      10, ()=> voteAQuestionUp,        ()=> !canChooseSearchResult && canVoteQuestionUp),
		//TestingAction("Vote a question down",    10, ()=> voteAQuestionDown,      ()=> !canChooseSearchResult && canVoteQuestionDown),
		TestingAction("Edit a question",         3, ()=> editAQuestion,          ()=> !canChooseSearchResult && canEditQuestion),
		TestingAction("Edit an answer",          3, ()=> editAnAnswer,           ()=> !canChooseSearchResult && canEditAnswer),
		TestingAction("Comment on an answer",    6, ()=> commentOnAnAnswer,      ()=> !canChooseSearchResult && canCommentOnAnswer),
		TestingAction("Comment on comment",      6, ()=> commentOnAComment,      ()=> !canChooseSearchResult && canCommentOnComment),
		TestingAction("Edit a comment",          3, ()=> editAComment,           ()=> !canChooseSearchResult && canEditComment),
		TestingAction("Vote an answer up",       10, ()=> voteAnAnswerUp,         ()=> !canChooseSearchResult && canVoteAnswerUp),
		//TestingAction("Vote an answer down",     10, ()=> voteAnAnswerDown,       ()=> !canChooseSearchResult && canVoteAnswerDown),
		TestingAction("Vote a comment up",       10, ()=> voteACommentUp,         ()=> !canChooseSearchResult && canVoteCommentUp),
		//TestingAction("Vote a comment down",     10, ()=> voteACommentDown,       ()=> !canChooseSearchResult && canVoteCommentDown),
		TestingAction("Expand a comment tree",   8, ()=> expandACommentTree,     ()=> !canChooseSearchResult && canExpandCommentTree),
		TestingAction("Collapse a comment tree", 6, ()=> collapseACommentTree,   ()=> !canChooseSearchResult && canCollapseCommentTree),
		TestingAction("Search content",          1, ()=> searchContent,          ()=> !canChooseSearchResult && true),
		TestingAction("Choose a search result",  1, ()=> chooseASearchResult,    ()=> canChooseSearchResult),
		TestingAction("Choose a topic",          1, ()=> chooseATopic,           ()=> !canChooseSearchResult && true)
	)
}

class MongoTrackedTestCase(name:String) extends TestCase(name){
	protected val mongoServer:String = "127.0.0.1"
	protected val mongoPort:Int = 27017
  protected var users:List[SeleniumUser] = List.empty[SeleniumUser]
	def setUsers(newUsers:List[SeleniumUser]):Unit = {
		users = newUsers
		users.foreach(_.setup)
	}
	def clearUsers:Unit = {
		users.foreach(_.teardown)
		users = List.empty[SeleniumUser]
	}
  override def setUp={
		val srvr = new ServerAddress(mongoServer, mongoPort)
		val mo = new MongoOptions
		mo.socketTimeout = 10000
		mo.socketKeepAlive = true
		MongoDB.defineDb(DefaultMongoIdentifier, new Mongo(srvr, mo), "%s_results".format(name))

		val currentTestQuery = new BasicDBObject("started", new BasicDBObject("$gt", new Date().getTime-5*60*1000))
  }
  override def tearDown ={
		clearUsers
  }
  protected def iter(i:Int):Unit ={
		val timeout = 9000000L
    try{
			val range = Range(0,i)
			val iterationFunc = (user:SeleniumUser) => range.map(index => {
				//Thread.sleep((Random.nextInt(30)+1)*1000)
				user.act
			}).toList
			val userFutures = users.map(u => Futures.future(iterationFunc(u)))
			Futures.awaitAll(timeout,userFutures:_*).toList
    }
    catch{
      case e:Throwable=> println("%s: %s".format(new java.util.Date(),e))
		}
  } 
	protected def successRate(input:List[TestResult]):Double = {
		val total = input.length.toDouble
		val successes = input.filter(sr => sr.result).length.toDouble
		if (total > 0)
			((successes / total)*100)  
		else 0d
	}
	protected def averageDuration(input:List[TestResult]):Double = {
		val total = input.length.toDouble
		if (total > 0)
			(input.map(sr => sr.duration).sum.toDouble / total)
		else
			-1d
	}
	def testDefaultTestingHarness = {
		assertEquals(true,true)
	}
}

class MeTLXTest extends MongoTrackedTestCase("metlx"){
	val server = "localhost:8080"
	//val server = "racy.its.monash.edu:8080"
	val localhost = "lt%s".format(Random.nextInt(10000)) 
	def testInheritance = {
		assertEquals(true,true)
	}
	def _testSingleUser = {
		setUsers(List(MetlXUser("singleUser_from_%s".format(localhost),server)))
		val testActions = List(
			"searchFor(bear)",
			"clickSearchButton",
			"chooseAConversationRandomly",
			"toggleApplicationButton",
			"chooseASlide",
			"chooseASlide",
			"chooseASlide",
			"chooseASlide"
		)
		testActions.map(name => { users.map({
			Thread.sleep(1500)
			_.act(name)
		})})
		Thread.sleep(5000)
		clearUsers
		assertEquals(true,true)
	}
	def testMultipleUsersInteracting = {
		val count = 5
		setUsers(Range(0,count).map(t=>{
			Thread.sleep((Random.nextInt(4)+1)*1000)
			MetlXUser("m%sf%s".format(t,localhost),server)}
		).toList)
    /*val results = */iter(120)
/*		val activities = results.map(tr => tr.name).distinct
		println("results")
		println
		("all" :: activities).foreach(a => {
			val specificResults = if (a == "all") results else results.filter(tr => tr.name == a)
			println("activity: %s".format(a))
			println("action count: %s".format(specificResults.length))
			println("average duration: %s".format(averageDuration(specificResults)))
			println("success rate: %s".format(successRate(specificResults)))
			println("exceptions: %s".format(specificResults.filter(sr => sr.exception != Empty).map(sr => sr.exception)))
			println	
	})*/
		assertEquals(true,true)
  }

}

class StackTest extends MongoTrackedTestCase("stack"){
	val server = "kayak.adm.monash.edu"
	val localhost = "lt%s".format(Random.nextInt(10000)) 

	def _testSingleUserSequential = {
		setUsers(List(StackUser("sequentialTester_from_%s".format(localhost), server)))
		val testActions = List(
			"Choose a topic",
			"Ask a question",
			"Edit a question",
			"Vote a question up",
			//"Vote a question down",
			"Answer a question",
			"Edit an answer",
			"Vote an answer up",
			//"Vote an answer down",
			"Comment on an answer",
			"Comment on comment",
			"Edit a comment",
			"Vote a comment up",
			//"Vote a comment down",
			"Comment on comment",
			"Comment on comment",
			"Collapse a comment tree",
			"Expand a comment tree",
			"Collapse a comment tree",
			"Open a question",
			"Open a question (mine)",
			"Search content",
			"Choose a search result",
			"Choose a topic"
		)
		testActions.map(name => { users.map({
			Thread.sleep(500)
			_.act(name)
		})})
		clearUsers
		assertEquals(true,true)
	}

  def _testMultipleUsersInteracting = {
    val count = 5
    setUsers(Range(0,count).map(t=>{
      Thread.sleep((Random.nextInt(4)+1)*1000)
      StackUser("m%sf%s".format(t,localhost),server)}
    ).toList)
    /*val results = */iter(120)
/*		val activities = results.map(tr => tr.name).distinct
		println("results")
		println
		("all" :: activities).foreach(a => {
			val specificResults = if (a == "all") results else results.filter(tr => tr.name == a)
			println("activity: %s".format(a))
			println("action count: %s".format(specificResults.length))
			println("average duration: %s".format(averageDuration(specificResults)))
			println("success rate: %s".format(successRate(specificResults)))
			println("exceptions: %s".format(specificResults.filter(sr => sr.exception != Empty).map(sr => sr.exception)))
			println	
	})*/
		assertEquals(true,true)
  }
}

object DBActor extends LiftActor {
	override def messageHandler = {
		case t:TestResult => sendToDB(t)
		case _ => println("DBActor recieved unknown message")
	}
	def sendToDB(result:TestResult) = {
		TestResultRecord.fromTestResult(result).save
	}
}


