 package com.metl.data

 import org.scalacheck._
 import Gen._
 import Arbitrary.arbitrary

 import scala.collection.mutable.WrappedArray
 import net.liftweb.util.Helpers._
 import net.liftweb.common._

 import com.metl.data._
 import Privacy._

trait MeTLDataGenerators {

	def genPrivacy = for {
		p <- Gen.oneOf(Privacy.PRIVATE, Privacy.PUBLIC, Privacy.NOT_SET)
	} yield p

	def genPoint = for {
		x <- arbitrary[Double]
		y <- arbitrary[Double]
		pressure <- arbitrary[Double]
	} yield Point(x, y, pressure) 

	def genPointList(count:Int) = for {
    p <- Gen.sequence(Range(0,count).map(i => genPoint))
	} yield p.toArray.toList.map(_.asInstanceOf[Point])

	def genIdList(count:Int) = for {
    i <- Gen.sequence(Range(0,count).map(i => Gen.alphaStr))
	} yield i.toArray.toList.map(_.asInstanceOf[String])

	def validTimestamp = new java.util.Date().getTime()

	def genColor = for {
		r <- Gen.choose(0, 255)
		g <- Gen.choose(0, 255)
		b <- Gen.choose(0, 255)
		a <- Gen.choose(0, 255)
	} yield Color(a, r, g, b)

  def genBoxOfBytes = for {
    bytes <- Gen.containerOfN[Array,Byte](10,choose(0,255).map(_.toByte))
  } yield Full(bytes)

  def genBlacklistedPerson = for {
    color <- genColor
    name <- Gen.alphaStr
  } yield SubmissionBlacklistedPerson(name,color)

  def genBlacklist(count:Int) = for {
    bl <- Gen.sequence(Range(0,count).map(i => genBlacklistedPerson))
  } yield bl.toArray.toList.map(_.asInstanceOf[SubmissionBlacklistedPerson])

  def genAudience = for {
    domain <- Gen.alphaStr
    name <- Gen.alphaStr
    audienceType <- Gen.alphaStr
    action <- Gen.alphaStr
  } yield {
    Audience(ServerConfiguration.empty,domain,name,audienceType,action)
  }
  def genAudiences(count:Int) = for {
    audiences <- Gen.sequence(Range(0,count).map(i => genAudience))
  } yield {
    audiences.toArray.toList.map(_.asInstanceOf[Audience])
  }

	def genInk = for {
		author <- Gen.alphaStr 
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
		points <- genPointList(scala.util.Random.nextInt(300) + 1)
		checksum <- arbitrary[Double]
		startingSum <- arbitrary[Double]
		color <- genColor 
		thickness <- arbitrary[Double]
		isHighlighter <- arbitrary[Boolean]
    audiences <- genAudiences(scala.util.Random.nextInt(3))
	} yield MeTLInk(ServerConfiguration.empty, author, timestamp, checksum, startingSum, points, color, thickness, isHighlighter, target, privacy, slide, identity, audiences)

	def genMoveDelta = for {
		author <- Gen.alphaStr 
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
		inkIds <- genIdList(scala.util.Random.nextInt(30))
		textIds <- genIdList(scala.util.Random.nextInt(30))
    richTextIds <- genIdList(scala.util.Random.nextInt(30))
		imageIds <- genIdList(scala.util.Random.nextInt(30))
    videoIds <- genIdList(scala.util.Random.nextInt(30))
		xTrans <- arbitrary[Double]
		yTrans <- arbitrary[Double]
		xOrigin <- arbitrary[Double]
		yOrigin <- arbitrary[Double]
		xScale <- arbitrary[Double]
		yScale <- arbitrary[Double]
		newPrivacy <- genPrivacy
		isDeleted <- arbitrary[Boolean]
    audiences <- genAudiences(scala.util.Random.nextInt(3))
	} yield MeTLMoveDelta(ServerConfiguration.empty, author, timestamp, target, privacy, slide, identity, xOrigin, yOrigin, inkIds, textIds, richTextIds, imageIds, videoIds, xTrans, yTrans, xScale, yScale, newPrivacy, isDeleted, audiences)

	def genImage = for {
		author <- Gen.alphaStr 
		target <- Gen.alphaStr 
		timestamp <- validTimestamp
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
		tag <- Gen.alphaStr
		x <- arbitrary[Double]
		y <- arbitrary[Double]
		width <- arbitrary[Double]
		height <- arbitrary[Double]
		source <- Gen.alphaStr map { s => if (!s.isEmpty) Full(s) else Full("unknown") }
    audiences <- genAudiences(scala.util.Random.nextInt(3))
	} yield MeTLImage(ServerConfiguration.empty, author, timestamp, tag, source, Empty, Empty, width, height, x, y, target, privacy, slide, identity,audiences)
    // WrappedArray.make[Byte]

	def genText = for {
        author <- Gen.alphaStr
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
		timestamp <- validTimestamp
		tag <- Gen.alphaStr
        caret <- Gen.choose(0, 200)
		text <- Gen.alphaStr 
		style <- Gen.alphaStr 
		family <- Gen.alphaStr 
		weight <- Gen.alphaStr 
        size <- arbitrary[Double]
        decoration <- Gen.alphaStr
		color <- genColor 
		x <- arbitrary[Double]
		y <- arbitrary[Double]
		width <- arbitrary[Double]
		height <- arbitrary[Double]
    audiences <- genAudiences(scala.util.Random.nextInt(3))
	} yield MeTLText(ServerConfiguration.empty, author, timestamp, text, height, width, caret, x, y, tag, style, family, weight, size, decoration, identity, target, privacy, slide, color, audiences)

  def genTextWord = for {
		text <- Gen.alphaStr 
    bold <- arbitrary[Boolean]
    underline <- arbitrary[Boolean]
    italic <- arbitrary[Boolean]
    justify <- oneOf(List("left","right","center","justify"))
		color <- genColor 
		family <- Gen.alphaStr 
    size <- arbitrary[Double]
  } yield MeTLTextWord(text,bold,underline,italic,justify,color,family,size)
  
  def genTextWords(count:Int) = for {
    words <- Gen.sequence(Range(0,count).map(i => genTextWord))
  } yield words.toArray.toList.map(_.asInstanceOf[MeTLTextWord])

  def genMultiWordText = for {
    author <- Gen.alphaStr
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
		timestamp <- validTimestamp
		tag <- Gen.alphaStr
		x <- arbitrary[Double]
		y <- arbitrary[Double]
		width <- arbitrary[Double]
		height <- arbitrary[Double]
		requestedWidth <- arbitrary[Double]
    audiences <- genAudiences(scala.util.Random.nextInt(3))
    runs <- genTextWords(scala.util.Random.nextInt(10) + 2)
	} yield MeTLMultiWordText(ServerConfiguration.empty, author, timestamp, height, width, requestedWidth, x, y, tag, identity, target, privacy, slide, runs, audiences)


	def genDirtyInk = for {
        author <- Gen.alphaStr
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
    audiences <- genAudiences(scala.util.Random.nextInt(3))
	} yield MeTLDirtyInk(ServerConfiguration.empty, author, timestamp, target, privacy, slide, identity, audiences)

	def genDirtyText = for {
        author <- Gen.alphaStr
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
    audiences <- genAudiences(scala.util.Random.nextInt(3))
	} yield MeTLDirtyText(ServerConfiguration.empty, author, timestamp, target, privacy, slide, identity, audiences)

	def genDirtyImage = for {
        author <- Gen.alphaStr
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
    audiences <- genAudiences(scala.util.Random.nextInt(3))
	} yield MeTLDirtyImage(ServerConfiguration.empty, author, timestamp, target, privacy, slide, identity, audiences)

    def genCommand = for {
        author <- Gen.alphaStr
        timestamp <- validTimestamp
        command <- Gen.alphaStr
        commandParams <- Gen.containerOfN[List, String](1,Gen.alphaStr)
        audiences <- genAudiences(scala.util.Random.nextInt(3))
    } yield MeTLCommand(ServerConfiguration.empty, author, timestamp, command, commandParams, audiences)

    def genSubmission = for {
        author <- Gen.alphaStr
        timestamp <- validTimestamp
        title <- Gen.alphaStr
        slideJid <- arbitrary[Int] 
        url <- Gen.alphaStr
        privacy <- genPrivacy
        boxOfBytes <- genBoxOfBytes
        blacklist <- genBlacklist(scala.util.Random.nextInt(8))
        audiences <- genAudiences(scala.util.Random.nextInt(3))
    } yield MeTLSubmission(ServerConfiguration.empty, author, timestamp, title, slideJid, url,boxOfBytes,blacklist,"",privacy,"",audiences) 

    def genQuiz = for {
        author <- Gen.alphaStr
        timestamp <- validTimestamp
        created <- arbitrary[Long]
        question <- Gen.alphaStr
        id <- Gen.numStr
        isDeleted <- arbitrary[Boolean]
        url <- Gen.alphaStr
        options <- Gen.containerOfN[List, QuizOption](1,genQuizOption)
        audiences <- genAudiences(scala.util.Random.nextInt(3))
    } yield MeTLQuiz(ServerConfiguration.empty, author, timestamp, created, question, id, Full(url), Empty, isDeleted, options, audiences)

    def genQuizOption = for {
        name <- Gen.alphaStr
        text <- Gen.alphaStr
    } yield QuizOption(name, text)

    def genQuizResponse = for {
        author <- Gen.alphaStr
        timestamp <- validTimestamp
        answer <- Gen.alphaStr
        answerer <- Gen.alphaStr
        id <- Gen.alphaStr
        audiences <- genAudiences(scala.util.Random.nextInt(3))
    } yield MeTLQuizResponse(ServerConfiguration.empty, author, timestamp, answer, answerer, id, audiences)

    def genConversation = for {
        author <- Gen.alphaStr
        lastAccessed <- arbitrary[Long]
        subject <- Gen.alphaStr
        tag <- Gen.alphaStr
        jid <- arbitrary[Int]
        title <- Gen.alphaStr
        created <- arbitrary[Long]
        permissions <- genPermissions
        slides <- Gen.containerOfN[List, Slide](1,genSlide)
    } yield Conversation(ServerConfiguration.empty, author, lastAccessed, slides, subject, tag, jid, title, created, permissions)

    def genSlide = for {
       author <- Gen.alphaStr
       id <- arbitrary[Int]
       index <- arbitrary[Int]
    } yield Slide(ServerConfiguration.empty, author, id, index)

    def genPermissions = for {
        studentsCanOptionFriends <- arbitrary[Boolean] 
        studentsCanPublish <- arbitrary[Boolean] 
        usersAreCompulsorilySynced <- arbitrary[Boolean]
    } yield Permissions(ServerConfiguration.empty, studentsCanOptionFriends, studentsCanPublish, usersAreCompulsorilySynced)
}
