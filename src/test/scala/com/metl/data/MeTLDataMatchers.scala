 package com.metl.data

 import org.scalatest._
 import org.scalatest.matchers.{HavePropertyMatcher, HavePropertyMatchResult}

 import net.liftweb.util.Helpers._
 import net.liftweb.common._
 import com.metl.data._
 import Privacy._

trait ArrayHelpers {

	def compareBoxedArray[A](left: Box[Array[A]], right: Box[Array[A]]) : Boolean = 
	  (left, right) match {
		  case (Empty, Empty) => true  
		  case (Full(x), Full(y)) => x.sameElements(y)
		  case _ => false
	  }
}

trait MeTLStanzaMatchers extends ArrayHelpers {

	def server(expectedValue: ServerConfiguration) =
	  new HavePropertyMatcher[MeTLStanza, ServerConfiguration] {
		def apply(stanza: MeTLStanza) =
		  HavePropertyMatchResult(
			stanza.server == expectedValue,
			"server",
			expectedValue,
			stanza.server
		  )
	  }

	def author(expectedValue: String) =
	  new HavePropertyMatcher[MeTLStanza, String] {
		def apply(stanza: MeTLStanza) =
		  HavePropertyMatchResult(
			stanza.author == expectedValue,
			"author",
			expectedValue,
			stanza.author
		  )
	  }

	def timestamp(expectedValue: Long) =
	  new HavePropertyMatcher[MeTLStanza, Long] {
		def apply(stanza: MeTLStanza) =
		  HavePropertyMatchResult(
			stanza.timestamp == expectedValue,
			"timestamp",
			expectedValue,
			stanza.timestamp
		  )
	  }
}

trait MeTLSubmissionMatchers extends MeTLStanzaMatchers with MeTLCanvasContentMatchers {
	
	def title(expectedValue: String) =
	  new HavePropertyMatcher[MeTLSubmission, String] {
		def apply(stanza: MeTLSubmission) =
		  HavePropertyMatchResult(
			stanza.title == expectedValue,
			"title",
			expectedValue,
			stanza.title
		  )
	  }

	def url(expectedValue: String) =
	  new HavePropertyMatcher[MeTLSubmission, String] {
		def apply(stanza: MeTLSubmission) =
		  HavePropertyMatchResult(
			stanza.url == expectedValue,
			"url",
			expectedValue,
			stanza.url
		  )
	  }

	def imageBytes(expectedValue: Box[Array[Byte]]) =
	  new HavePropertyMatcher[MeTLSubmission, Box[Array[Byte]]] {
		def apply(stanza: MeTLSubmission) =
		  HavePropertyMatchResult(
			compareBoxedArray(stanza.imageBytes, expectedValue),
			"imageBytes",
			expectedValue,
			stanza.imageBytes
		  )
	  }

	def blacklist(expectedValue: List[SubmissionBlacklistedPerson]) =
	  new HavePropertyMatcher[MeTLSubmission, List[SubmissionBlacklistedPerson]] {
		def apply(stanza: MeTLSubmission) =
		  HavePropertyMatchResult(
			stanza.blacklist == expectedValue,
			"blacklist",
			expectedValue,
			stanza.blacklist
		  )
	  }

	def slideJid(expectedValue: Int) =
	  new HavePropertyMatcher[MeTLSubmission, Int] {
		def apply(stanza: MeTLSubmission) =
		  HavePropertyMatchResult(
			stanza.slideJid == expectedValue,
			"slideJid",
			expectedValue,
			stanza.slideJid
		  )
	  }
}

trait MeTLMoveDeltaMatchers extends MeTLStanzaMatchers with MeTLCanvasContentMatchers {

	  def inkIds(expectedValue: Seq[String]) =
		new HavePropertyMatcher[MeTLMoveDelta, Seq[String]] {
		  def apply(stanza: MeTLMoveDelta) =
			HavePropertyMatchResult(
			  stanza.inkIds == expectedValue,
			  "inkIds",
			  expectedValue,
			  stanza.inkIds
			)
		}

	  def imageIds(expectedValue: Seq[String]) =
		new HavePropertyMatcher[MeTLMoveDelta, Seq[String]] {
		  def apply(stanza: MeTLMoveDelta) =
			HavePropertyMatchResult(
			  stanza.imageIds == expectedValue,
			  "imageIds",
			  expectedValue,
			  stanza.imageIds
			)
		}

	  def textIds(expectedValue: Seq[String]) =
		new HavePropertyMatcher[MeTLMoveDelta, Seq[String]] {
		  def apply(stanza: MeTLMoveDelta) =
			HavePropertyMatchResult(
			  stanza.textIds == expectedValue,
			  "textIds",
			  expectedValue,
			  stanza.textIds
			)
		}

	def xTranslate(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLMoveDelta, Double] {
		def apply(stanza: MeTLMoveDelta) =
		  HavePropertyMatchResult(
			stanza.xTranslate == expectedValue,
			"xTranslate",
			expectedValue,
			stanza.xTranslate
		  )
	  }

	def yTranslate(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLMoveDelta, Double] {
		def apply(stanza: MeTLMoveDelta) =
		  HavePropertyMatchResult(
			stanza.yTranslate == expectedValue,
			"yTranslate",
			expectedValue,
			stanza.yTranslate
		  )
	  }

	def xScale(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLMoveDelta, Double] {
		def apply(stanza: MeTLMoveDelta) =
		  HavePropertyMatchResult(
			stanza.xScale == expectedValue,
			"xScale",
			expectedValue,
			stanza.xScale
		  )
	  }

	def yScale(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLMoveDelta, Double] {
		def apply(stanza: MeTLMoveDelta) =
		  HavePropertyMatchResult(
			stanza.yScale == expectedValue,
			"yScale",
			expectedValue,
			stanza.yScale
		  )
	  }

	def newPrivacy(expectedValue: Privacy) =
	  new HavePropertyMatcher[MeTLMoveDelta, Privacy] {
		def apply(stanza: MeTLMoveDelta) =
		  HavePropertyMatchResult(
			stanza.newPrivacy == expectedValue,
			"newPrivacy",
			expectedValue,
			stanza.newPrivacy
		  )
	  }

	def isDeleted(expectedValue: Boolean) =
	  new HavePropertyMatcher[MeTLMoveDelta, Boolean] {
		def apply(stanza: MeTLMoveDelta) =
		  HavePropertyMatchResult(
			stanza.isDeleted == expectedValue,
			"isDeleted",
			expectedValue,
			stanza.isDeleted
		  )
	  }
}

trait MeTLCanvasContentMatchers {

	def target(expectedValue: String) =
	  new HavePropertyMatcher[MeTLCanvasContent, String] {
		def apply(stanza: MeTLCanvasContent) =
		  HavePropertyMatchResult(
			stanza.target == expectedValue,
			"target",
			expectedValue,
			stanza.target
		  )
	  }

	def privacy(expectedValue: Privacy) =
	  new HavePropertyMatcher[MeTLCanvasContent, Privacy] {
		def apply(stanza: MeTLCanvasContent) =
		  HavePropertyMatchResult(
			stanza.privacy == expectedValue,
			"privacy",
			expectedValue,
			stanza.privacy
		  )
	  }

	def slide(expectedValue: String) =
	  new HavePropertyMatcher[MeTLCanvasContent, String] {
		def apply(stanza: MeTLCanvasContent) =
		  HavePropertyMatchResult(
			stanza.slide == expectedValue,
			"slide",
			expectedValue,
			stanza.slide
		  )
	  }

	def identity(expectedValue: String) =
	  new HavePropertyMatcher[MeTLCanvasContent, String] {
		def apply(stanza: MeTLCanvasContent) =
		  HavePropertyMatchResult(
			stanza.identity == expectedValue,
			"identity",
			expectedValue,
			stanza.identity
		  )
	  }

	def scaleFactorX(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLCanvasContent, Double] {
		def apply(stanza: MeTLCanvasContent) =
		  HavePropertyMatchResult(
			stanza.scaleFactorX == expectedValue,
			"scaleFactorX",
			expectedValue,
			stanza.scaleFactorX
		  )
	  }

	def scaleFactorY(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLCanvasContent, Double] {
		def apply(stanza: MeTLCanvasContent) =
		  HavePropertyMatchResult(
			stanza.scaleFactorY == expectedValue,
			"scaleFactorY",
			expectedValue,
			stanza.scaleFactorY
		  )
	  }
}

trait MeTLImageMatchers extends ArrayHelpers with MeTLStanzaMatchers with MeTLCanvasContentMatchers {

	def tag(expectedValue: String) =
	  new HavePropertyMatcher[MeTLImage, String] {
		def apply(stanza: MeTLImage) =
		  HavePropertyMatchResult(
			stanza.tag == expectedValue,
			"tag",
			expectedValue,
			stanza.tag
		  )
	  }

	def source(expectedValue: Box[String]) =
	  new HavePropertyMatcher[MeTLImage, Box[String]] {
		def apply(stanza: MeTLImage) =
		  HavePropertyMatchResult(
			stanza.source == expectedValue,
			"source",
			expectedValue,
			stanza.source
		  )
	  }

	def imageBytes(expectedValue: Box[Array[Byte]]) =
	  new HavePropertyMatcher[MeTLImage, Box[Array[Byte]]] {
		def apply(stanza: MeTLImage) =
		  HavePropertyMatchResult(
			compareBoxedArray(stanza.imageBytes, expectedValue),
			"imageBytes",
			expectedValue,
			stanza.imageBytes
		  )
	  }

	def pngBytes(expectedValue: Box[Array[Byte]]) =
	  new HavePropertyMatcher[MeTLImage, Box[Array[Byte]]] {
		def apply(stanza: MeTLImage) =
		  HavePropertyMatchResult(
			compareBoxedArray(stanza.pngBytes, expectedValue),
			"pngBytes",
			expectedValue,
			stanza.pngBytes
		  )
	  }

	def width(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLImage, Double] {
		def apply(stanza: MeTLImage) =
		  HavePropertyMatchResult(
			stanza.width == expectedValue,
			"width",
			expectedValue,
			stanza.width
		  )
	  }

	def height(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLImage, Double] {
		def apply(stanza: MeTLImage) =
		  HavePropertyMatchResult(
			stanza.height == expectedValue,
			"height",
			expectedValue,
			stanza.height
		  )
	  }

	def x(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLImage, Double] {
		def apply(stanza: MeTLImage) =
		  HavePropertyMatchResult(
			stanza.x == expectedValue,
			"x",
			expectedValue,
			stanza.x
		  )
	  }

	def y(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLImage, Double] {
		def apply(stanza: MeTLImage) =
		  HavePropertyMatchResult(
			stanza.y == expectedValue,
			"y",
			expectedValue,
			stanza.y
		  )
	  }
}

trait MeTLInkMatchers extends MeTLStanzaMatchers with MeTLCanvasContentMatchers {

	  def checksum(expectedValue: Double) =
		new HavePropertyMatcher[MeTLInk, Double] {
		  def apply(stanza: MeTLInk) =
			HavePropertyMatchResult(
			  stanza.checksum == expectedValue,
			  "checksum",
			  expectedValue,
			  stanza.checksum
			)
		}

	  def startingSum(expectedValue: Double) =
		new HavePropertyMatcher[MeTLInk, Double] {
		  def apply(stanza: MeTLInk) =
			HavePropertyMatchResult(
			  stanza.startingSum == expectedValue,
			  "startingSum",
			  expectedValue,
			  stanza.startingSum
			)
		}

	  def points(expectedValue: List[Point]) =
		new HavePropertyMatcher[MeTLInk, List[Point]] {
		  def apply(stanza: MeTLInk) =
			HavePropertyMatchResult(
			  stanza.points == expectedValue,
			  "points",
			  expectedValue,
			  stanza.points
			)
		}

	def color(expectedValue: Color) =
	  new HavePropertyMatcher[MeTLInk, Color] {
		def apply(stanza: MeTLInk) =
		  HavePropertyMatchResult(
			stanza.color == expectedValue,
			"color",
			expectedValue,
			stanza.color
		  )
	  }

	def thickness(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLInk, Double] {
		def apply(stanza: MeTLInk) =
		  HavePropertyMatchResult(
			stanza.thickness == expectedValue,
			"thickness",
			expectedValue,
			stanza.thickness
		  )
	  }

	def isHighlighter(expectedValue: Boolean) =
	  new HavePropertyMatcher[MeTLInk, Boolean] {
		def apply(stanza: MeTLInk) =
		  HavePropertyMatchResult(
			stanza.isHighlighter == expectedValue,
			"isHighlighter",
			expectedValue,
			stanza.isHighlighter
		  )
	  }
}

trait MeTLTextMatchers extends MeTLStanzaMatchers with MeTLCanvasContentMatchers {

	def tag(expectedValue: String) =
	  new HavePropertyMatcher[MeTLText, String] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.tag == expectedValue,
			"tag",
			expectedValue,
			stanza.tag
		  )
	  }

	def caret(expectedValue: Int) =
	  new HavePropertyMatcher[MeTLText, Int] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.caret == expectedValue,
			"caret",
			expectedValue,
			stanza.caret
		  )
	  }
	
	def text(expectedValue: String) =
	  new HavePropertyMatcher[MeTLText, String] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.text == expectedValue,
			"text",
			expectedValue,
			stanza.text
		  )
	  }

	def style(expectedValue: String) =
	  new HavePropertyMatcher[MeTLText, String] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.style == expectedValue,
			"style",
			expectedValue,
			stanza.style
		  )
	  }

	def family(expectedValue: String) =
	  new HavePropertyMatcher[MeTLText, String] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.family == expectedValue,
			"family",
			expectedValue,
			stanza.family
		  )
	  }

	def weight(expectedValue: String) =
	  new HavePropertyMatcher[MeTLText, String] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.weight == expectedValue,
			"weight",
			expectedValue,
			stanza.weight
		  )
	  }

	def decoration(expectedValue: String) =
	  new HavePropertyMatcher[MeTLText, String] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.decoration == expectedValue,
			"decoration",
			expectedValue,
			stanza.decoration
		  )
	  }

	def color(expectedValue: Color) =
	  new HavePropertyMatcher[MeTLText, Color] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.color == expectedValue,
			"color",
			expectedValue,
			stanza.color
		  )
	  }

	def size(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLText, Double] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.size == expectedValue,
			"size",
			expectedValue,
			stanza.size
		  )
	  }

	def width(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLText, Double] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.width == expectedValue,
			"width",
			expectedValue,
			stanza.width
		  )
	  }

	def height(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLText, Double] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.height == expectedValue,
			"height",
			expectedValue,
			stanza.height
		  )
	  }

	def x(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLText, Double] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.x == expectedValue,
			"x",
			expectedValue,
			stanza.x
		  )
	  }

	def y(expectedValue: Double) =
	  new HavePropertyMatcher[MeTLText, Double] {
		def apply(stanza: MeTLText) =
		  HavePropertyMatchResult(
			stanza.y == expectedValue,
			"y",
			expectedValue,
			stanza.y
		  )
	  }
}

trait MeTLQuizMatchers extends ArrayHelpers with MeTLStanzaMatchers {

	def isDeleted(expectedValue: Boolean) =
	  new HavePropertyMatcher[MeTLQuiz, Boolean] {
		def apply(stanza: MeTLQuiz) =
		  HavePropertyMatchResult(
			stanza.isDeleted == expectedValue,
			"isDeleted",
			expectedValue,
			stanza.isDeleted
		  )
	  }

	def question(expectedValue: String) =
	  new HavePropertyMatcher[MeTLQuiz, String] {
		def apply(stanza: MeTLQuiz) =
		  HavePropertyMatchResult(
			stanza.question == expectedValue,
			"question",
			expectedValue,
			stanza.question
		  )
	  }

	def options(expectedValue: List[QuizOption]) =
	  new HavePropertyMatcher[MeTLQuiz, List[QuizOption]] {
		def apply(stanza: MeTLQuiz) = 
		  HavePropertyMatchResult(
			stanza.options == expectedValue,
			"options",
			expectedValue,
			stanza.options
		  )
	  }

	def imageBytes(expectedValue: Box[Array[Byte]]) = 
	  new HavePropertyMatcher[MeTLQuiz, Box[Array[Byte]]] {
		def apply(stanza: MeTLQuiz) = 
		  HavePropertyMatchResult(
			compareBoxedArray(stanza.imageBytes, expectedValue),
			"imageBytes",
			expectedValue,
			stanza.imageBytes
		  )
	  }

	def id(expectedValue: String) = 
	  new HavePropertyMatcher[MeTLQuiz, String] {
		def apply(stanza: MeTLQuiz) = 
		  HavePropertyMatchResult(
			stanza.id == expectedValue,
			"id",
			expectedValue,
			stanza.id
		  )
	  }

	def created(expectedValue: Long) = 
	  new HavePropertyMatcher[MeTLQuiz, Long] {
		def apply(stanza: MeTLQuiz) = 
		  HavePropertyMatchResult(
			stanza.created == expectedValue,
			"created",
			expectedValue,
			stanza.created
		  )
	  }

	def url(expectedValue: Box[String]) = 
	  new HavePropertyMatcher[MeTLQuiz, Box[String]] {
		def apply(stanza: MeTLQuiz) = 
		  HavePropertyMatchResult(
			stanza.url == expectedValue,
			"url",
			expectedValue,
			stanza.url
		  )
	  }
}

trait MeTLQuizResponseMatchers extends MeTLStanzaMatchers {

	def answer(expectedValue: String) =
	  new HavePropertyMatcher[MeTLQuizResponse, String] {
		def apply(stanza: MeTLQuizResponse) =
		  HavePropertyMatchResult(
			stanza.answer == expectedValue,
			"answer",
			expectedValue,
			stanza.answer
		  )
	  }

	def answerer(expectedValue: String) =
	  new HavePropertyMatcher[MeTLQuizResponse, String] {
		def apply(stanza: MeTLQuizResponse) =
		  HavePropertyMatchResult(
			stanza.answerer == expectedValue,
			"answerer",
			expectedValue,
			stanza.answerer
		  )
	  }

	def id(expectedValue: String) = 
	  new HavePropertyMatcher[MeTLQuizResponse, String] {
		def apply(stanza: MeTLQuizResponse) = 
		  HavePropertyMatchResult(
			stanza.id == expectedValue,
			"id",
			expectedValue,
			stanza.id
		  )
	  }
}

trait MeTLCommandMatchers extends MeTLStanzaMatchers {

	def command(expectedValue: String) =
	  new HavePropertyMatcher[MeTLCommand, String] {
		def apply(stanza: MeTLCommand) =
		  HavePropertyMatchResult(
        stanza.command == expectedValue,
        "command",
        expectedValue,
        stanza.command
		  )
	  }

	def commandParameters(expectedValue: List[String]) =
	  new HavePropertyMatcher[MeTLCommand, List[String]] {
		def apply(stanza: MeTLCommand) =
		  HavePropertyMatchResult(
			stanza.commandParameters == expectedValue,
			"commandParameters",
			expectedValue,
			stanza.commandParameters
		  )
	  }
}

trait ConversationMatchers {

	def author(expectedValue: String) =
	  new HavePropertyMatcher[Conversation, String] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.author == expectedValue,
			"author",
			expectedValue,
			stanza.author
		  )
	  }

	def lastAccessed(expectedValue: Long) =
	  new HavePropertyMatcher[Conversation, Long] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.lastAccessed == expectedValue,
			"lastAccessed",
			expectedValue,
			stanza.lastAccessed
		  )
	  }

	def slides(expectedValue: List[Slide]) =
	  new HavePropertyMatcher[Conversation, List[Slide]] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.slides == expectedValue,
			"slides",
			expectedValue,
			stanza.slides
		  )
	  }

	def subject(expectedValue: String) =
	  new HavePropertyMatcher[Conversation, String] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.subject == expectedValue,
			"subject",
			expectedValue,
			stanza.subject
		  )
	  }

	def tag(expectedValue: String) =
	  new HavePropertyMatcher[Conversation, String] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.tag == expectedValue,
			"tag",
			expectedValue,
			stanza.tag
		  )
	  }

	def jid(expectedValue: Int) =
	  new HavePropertyMatcher[Conversation, Int] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.jid == expectedValue,
			"jid",
			expectedValue,
			stanza.jid
		  )
	  }

	def title(expectedValue: String) =
	  new HavePropertyMatcher[Conversation, String] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.title == expectedValue,
			"title",
			expectedValue,
			stanza.title
		  )
	  }

	def created(expectedValue: Long) =
	  new HavePropertyMatcher[Conversation, Long] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.created == expectedValue,
			"created",
			expectedValue,
			stanza.created
		  )
	  }

	def permissions(expectedValue: Permissions) =
	  new HavePropertyMatcher[Conversation, Permissions] {
		def apply(stanza: Conversation) =
		  HavePropertyMatchResult(
			stanza.permissions == expectedValue,
			"permissions",
			expectedValue,
			stanza.permissions
		  )
	  }
}

