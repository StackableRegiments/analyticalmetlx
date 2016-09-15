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

	val genPrivacy = for {
		p <- Gen.oneOf(Privacy.PRIVATE, Privacy.PUBLIC, Privacy.NOT_SET)
	} yield p

	val genPoint = for {
		x <- arbitrary[Double]
		y <- arbitrary[Double]
		pressure <- arbitrary[Double]
	} yield Point(x, y, pressure) 

	val genPointList = for {
		p <- Gen.containerOfN[List, Point](1,genPoint)
	} yield p

	val genIdList = for {
		i <- Gen.containerOfN[List, String](1,Gen.alphaStr)
	} yield i

	val validTimestamp = new java.util.Date().getTime()

	val genColor = for {
		r <- Gen.choose(0, 255)
		g <- Gen.choose(0, 255)
		b <- Gen.choose(0, 255)
		a <- Gen.choose(0, 255)
	} yield Color(a, r, g, b)

  val genBoxOfBytes = for {
    bytes <- Gen.containerOfN[Array,Byte](10,choose(0,255).map(_.toByte))
  } yield Full(bytes)

  val genBlacklistedPerson = for {
    color <- genColor
    name <- Gen.alphaStr
  } yield SubmissionBlacklistedPerson(name,color)

  val genBlacklist = for {
    person <- genBlacklistedPerson
    bl <- Gen.containerOfN[List,SubmissionBlacklistedPerson](1,person)
  } yield bl

  val genAudience = for {
    domain <- Gen.alphaStr
    name <- Gen.alphaStr
    audienceType <- Gen.alphaStr
    action <- Gen.alphaStr
  } yield {
    Audience(ServerConfiguration.empty,domain,name,audienceType,action)
  }
  val genAudiences = for {
    audience <- genAudience
    audiences <- Gen.containerOfN[List,Audience](1,audience)
  } yield {
    audiences
  }

	val genInk = for {
		author <- Gen.alphaStr 
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
		points <- genPointList
		checksum <- arbitrary[Double]
		startingSum <- arbitrary[Double]
		color <- genColor 
		thickness <- arbitrary[Double]
		isHighlighter <- arbitrary[Boolean]
    audiences <- genAudiences
	} yield MeTLInk(ServerConfiguration.empty, author, timestamp, checksum, startingSum, points, color, thickness, isHighlighter, target, privacy, slide, identity, audiences)

	val genMoveDelta = for {
		author <- Gen.alphaStr 
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
		inkIds <- genIdList
		textIds <- genIdList
    richTextIds <- genIdList
		imageIds <- genIdList
    videoIds <- genIdList
		xTrans <- arbitrary[Double]
		yTrans <- arbitrary[Double]
		xOrigin <- arbitrary[Double]
		yOrigin <- arbitrary[Double]
		xScale <- arbitrary[Double]
		yScale <- arbitrary[Double]
		newPrivacy <- genPrivacy
		isDeleted <- arbitrary[Boolean]
    audiences <- genAudiences
	} yield MeTLMoveDelta(ServerConfiguration.empty, author, timestamp, target, privacy, slide, identity, xOrigin, yOrigin, inkIds, textIds, richTextIds, imageIds, videoIds, xTrans, yTrans, xScale, yScale, newPrivacy, isDeleted, audiences)

	val genImage = for {
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
    audiences <- genAudiences
	} yield MeTLImage(ServerConfiguration.empty, author, timestamp, tag, source, Empty, Empty, width, height, x, y, target, privacy, slide, identity,audiences)
    // WrappedArray.make[Byte]

	val genText = for {
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
    audiences <- genAudiences
	} yield MeTLText(ServerConfiguration.empty, author, timestamp, text, height, width, caret, x, y, tag, style, family, weight, size, decoration, identity, target, privacy, slide, color, audiences)

	val genDirtyInk = for {
        author <- Gen.alphaStr
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
    audiences <- genAudiences
	} yield MeTLDirtyInk(ServerConfiguration.empty, author, timestamp, target, privacy, slide, identity, audiences)

	val genDirtyText = for {
        author <- Gen.alphaStr
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
    audiences <- genAudiences
	} yield MeTLDirtyText(ServerConfiguration.empty, author, timestamp, target, privacy, slide, identity, audiences)

	val genDirtyImage = for {
        author <- Gen.alphaStr
		timestamp <- validTimestamp
		target <- Gen.alphaStr 
		privacy <- genPrivacy
		slide <- Gen.numStr 
		identity <- Gen.alphaStr 
    audiences <- genAudiences
	} yield MeTLDirtyImage(ServerConfiguration.empty, author, timestamp, target, privacy, slide, identity, audiences)

    val genCommand = for {
        author <- Gen.alphaStr
        timestamp <- validTimestamp
        command <- Gen.alphaStr
        commandParams <- Gen.containerOfN[List, String](1,Gen.alphaStr)
        audiences <- genAudiences
    } yield MeTLCommand(ServerConfiguration.empty, author, timestamp, command, commandParams, audiences)

    val genSubmission = for {
        author <- Gen.alphaStr
        timestamp <- validTimestamp
        title <- Gen.alphaStr
        slideJid <- arbitrary[Int] 
        url <- Gen.alphaStr
        privacy <- genPrivacy
        boxOfBytes <- genBoxOfBytes
        blacklist <- genBlacklist
        audiences <- genAudiences
    } yield MeTLSubmission(ServerConfiguration.empty, author, timestamp, title, slideJid, url,boxOfBytes,blacklist,"",privacy,"",audiences) 

    val genQuiz = for {
        author <- Gen.alphaStr
        timestamp <- validTimestamp
        created <- arbitrary[Long]
        question <- Gen.alphaStr
        id <- Gen.numStr
        isDeleted <- arbitrary[Boolean]
        url <- Gen.alphaStr
        options <- Gen.containerOfN[List, QuizOption](1,genQuizOption)
        audiences <- genAudiences
    } yield MeTLQuiz(ServerConfiguration.empty, author, timestamp, created, question, id, Full(url), Empty, isDeleted, options, audiences)

    val genQuizOption = for {
        name <- Gen.alphaStr
        text <- Gen.alphaStr
    } yield QuizOption(name, text)

    val genQuizResponse = for {
        author <- Gen.alphaStr
        timestamp <- validTimestamp
        answer <- Gen.alphaStr
        answerer <- Gen.alphaStr
        id <- Gen.alphaStr
        audiences <- genAudiences
    } yield MeTLQuizResponse(ServerConfiguration.empty, author, timestamp, answer, answerer, id, audiences)

    val genConversation = for {
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

    val genSlide = for {
       author <- Gen.alphaStr
       id <- arbitrary[Int]
       index <- arbitrary[Int]
    } yield Slide(ServerConfiguration.empty, author, id, index)

    val genPermissions = for {
        studentsCanOptionFriends <- arbitrary[Boolean] 
        studentsCanPublish <- arbitrary[Boolean] 
        usersAreCompulsorilySynced <- arbitrary[Boolean]
    } yield Permissions(ServerConfiguration.empty, studentsCanOptionFriends, studentsCanPublish, usersAreCompulsorilySynced)
}
