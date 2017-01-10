package com.metl.data

import org.scalacheck._
import Gen._
import Arbitrary.arbitrary

import scala.collection.mutable.WrappedArray
import net.liftweb.util.Helpers._
import net.liftweb.common._

import com.metl.data._
import com.metl.model._
import Privacy._

trait MeTLDataGenerators {
  def genString(length:Int) = for {
    chars <- Gen.sequence(Range(0,length).map(i => Gen.alphaChar))
  } yield {
    new String(chars.toArray.map(_.asInstanceOf[Char]))
  }
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
    i <- Gen.sequence(Range(0,count).map(i => genString(32)))
  } yield i.toArray.toList.map(_.asInstanceOf[String])

  def genSlideString = Gen.choose(0,99999999)
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
    name <- genString(32)
  } yield SubmissionBlacklistedPerson(name,color)

  def genBlacklist(count:Int) = for {
    bl <- Gen.sequence(Range(0,count).map(i => genBlacklistedPerson))
  } yield bl.toArray.toList.map(_.asInstanceOf[SubmissionBlacklistedPerson])

  def genAudience = for {
    domain <- genString(32)
    name <- genString(32)
    audienceType <- genString(32)
    action <- genString(32)
  } yield {
    Audience(ServerConfiguration.empty,domain,name,audienceType,action)
  }
  def genAudiences(count:Int) = for {
    audiences <- Gen.sequence(Range(0,count).map(i => genAudience))
  } yield {
    audiences.toArray.toList.map(_.asInstanceOf[Audience])
  }

  val genTheme = for {
    author <- genString(32)
    target <- genString(32)
    location <- Gen.numStr
    text <- genString(32)
    timestamp <- validTimestamp
    origin <- Gen.numStr
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLTheme(ServerConfiguration.empty,author,timestamp,location,Theme(author,text,origin),audiences)
  def genInk = for {
    author <- genString(32)
    timestamp <- validTimestamp
    target <- genString(32)
    privacy <- genPrivacy
    slide <- genSlideString//Gen.numStr
    identity <- genString(64)//arbitrary[String]//genString(32)
    points <- genPointList(scala.util.Random.nextInt(300) + 1)
    checksum <- arbitrary[Double]
    startingSum <- arbitrary[Double]
    color <- genColor
    thickness <- arbitrary[Double]
    isHighlighter <- arbitrary[Boolean]
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLInk(ServerConfiguration.empty, author, timestamp, checksum, startingSum, points, color, thickness, isHighlighter, target, privacy, slide.toString, identity, audiences)

  def genMoveDelta = for {
    author <- genString(32)
    timestamp <- validTimestamp
    target <- genString(32)
    privacy <- genPrivacy
    slide <- genSlideString//Gen.numStr
    identity <- genString(32)
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
  } yield MeTLMoveDelta(ServerConfiguration.empty, author, timestamp, target, privacy, slide.toString, identity, xOrigin, yOrigin, inkIds, textIds, richTextIds, imageIds, videoIds, xTrans, yTrans, xScale, yScale, newPrivacy, isDeleted, audiences)

  def genImage = for {
    author <- genString(32)
    target <- genString(32)
    timestamp <- validTimestamp
    privacy <- genPrivacy
    slide <- genSlideString//Gen.numStr
    identity <- genString(32)
    tag <- genString(32)
    x <- arbitrary[Double]
    y <- arbitrary[Double]
    width <- arbitrary[Double]
    height <- arbitrary[Double]
    source <- genString(32) map { s => if (!s.isEmpty) Full(s) else Full("unknown") }
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLImage(ServerConfiguration.empty, author, timestamp, tag, source, Empty, Empty, width, height, x, y, target, privacy, slide.toString, identity,audiences)
  // WrappedArray.make[Byte]

  def genText = for {
    author <- genString(32)
    target <- genString(32)
    privacy <- genPrivacy
    slide <- genSlideString//Gen.numStr
    identity <- genString(32)
    timestamp <- validTimestamp
    tag <- genString(32)
    caret <- Gen.choose(0, 200)
    text <- genString(32)
    style <- genString(32)
    family <- genString(32)
    weight <- genString(32)
    size <- arbitrary[Double]
    decoration <- genString(32)
    color <- genColor
    x <- arbitrary[Double]
    y <- arbitrary[Double]
    width <- arbitrary[Double]
    height <- arbitrary[Double]
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLText(ServerConfiguration.empty, author, timestamp, text, height, width, caret, x, y, tag, style, family, weight, size, decoration, identity, target, privacy, slide.toString, color, audiences)

  def genTextWord = for {
    text <- genString(32)
    bold <- arbitrary[Boolean]
    underline <- arbitrary[Boolean]
    italic <- arbitrary[Boolean]
    justify <- oneOf(List("left","right","center","justify"))
    color <- genColor
    family <- genString(32)
    size <- arbitrary[Double]
  } yield MeTLTextWord(text,bold,underline,italic,justify,color,family,size)

  def genTextWords(count:Int) = for {
    words <- Gen.sequence(Range(0,count).map(i => genTextWord))
  } yield words.toArray.toList.map(_.asInstanceOf[MeTLTextWord])

  def genMultiWordText = for {
    author <- genString(32)
    target <- genString(32)
    privacy <- genPrivacy
    slide <- genSlideString//Gen.numStr
    identity <- genString(32)
    timestamp <- validTimestamp
    tag <- genString(32)
    x <- arbitrary[Double]
    y <- arbitrary[Double]
    width <- arbitrary[Double]
    height <- arbitrary[Double]
    requestedWidth <- arbitrary[Double]
    audiences <- genAudiences(scala.util.Random.nextInt(3))
    runs <- genTextWords(scala.util.Random.nextInt(10) + 2)
  } yield MeTLMultiWordText(ServerConfiguration.empty, author, timestamp, height, width, requestedWidth, x, y, tag, identity, target, privacy, slide.toString, runs, audiences)


  def genDirtyInk = for {
    author <- genString(32)
    timestamp <- validTimestamp
    target <- genString(32)
    privacy <- genPrivacy
    slide <- genSlideString//Gen.numStr
    identity <- genString(32)
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLDirtyInk(ServerConfiguration.empty, author, timestamp, target, privacy, slide.toString, identity, audiences)

  def genDirtyText = for {
    author <- genString(32)
    timestamp <- validTimestamp
    target <- genString(32)
    privacy <- genPrivacy
    slide <- genSlideString//Gen.numStr
    identity <- genString(32)
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLDirtyText(ServerConfiguration.empty, author, timestamp, target, privacy, slide.toString, identity, audiences)

  def genDirtyImage = for {
    author <- genString(32)
    timestamp <- validTimestamp
    target <- genString(32)
    privacy <- genPrivacy
    slide <- genSlideString//Gen.numStr
    identity <- genString(32)
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLDirtyImage(ServerConfiguration.empty, author, timestamp, target, privacy, slide.toString, identity, audiences)

  def genCommand = for {
    author <- genString(32)
    timestamp <- validTimestamp
    command <- genString(32)
    commandParams <- Gen.containerOfN[List, String](1,genString(32))
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLCommand(ServerConfiguration.empty, author, timestamp, command, commandParams, audiences)

  def genSubmission = for {
    author <- genString(32)
    timestamp <- validTimestamp
    title <- genString(32)
    slideJid <- arbitrary[Int]
    url <- genString(32)
    privacy <- genPrivacy
    boxOfBytes <- genBoxOfBytes
    blacklist <- genBlacklist(scala.util.Random.nextInt(8))
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLSubmission(ServerConfiguration.empty, author, timestamp, title, slideJid, url,boxOfBytes,blacklist,"",privacy,"",audiences)

  def genQuiz = for {
    author <- genString(32)
    timestamp <- validTimestamp
    created <- arbitrary[Long]
    question <- genString(32)
    id <- genSlideString//Gen.numStr
    isDeleted <- arbitrary[Boolean]
    url <- genString(32)
    options <- Gen.containerOfN[List, QuizOption](1,genQuizOption)
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLQuiz(ServerConfiguration.empty, author, timestamp, created, question, id.toString, Full(url), Empty, isDeleted, options, audiences)

  def genQuizOption = for {
    name <- genString(32)
    text <- genString(32)
  } yield QuizOption(name, text)

  def genQuizResponse = for {
    author <- genString(32)
    timestamp <- validTimestamp
    answer <- genString(32)
    answerer <- genString(32)
    id <- genString(32)
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLQuizResponse(ServerConfiguration.empty, author, timestamp, answer, answerer, id, audiences)

  def genConversation = for {
    author <- genString(32)
    lastAccessed <- arbitrary[Long]
    subject <- genString(32)
    tag <- genString(32)
    jid <- arbitrary[Int]
    title <- genString(32)
    created <- arbitrary[Long]
    permissions <- genPermissions
    slides <- Gen.containerOfN[List, Slide](1,genSlide)
  } yield Conversation(ServerConfiguration.empty, author, lastAccessed, slides, subject, tag, jid, title, created, permissions)

  def genSlide = for {
    author <- genString(32)
    id <- arbitrary[Int]
    index <- arbitrary[Int]
  } yield Slide(ServerConfiguration.empty, author, id, index)

  def genPermissions = for {
    studentsCanOptionFriends <- arbitrary[Boolean]
    studentsCanPublish <- arbitrary[Boolean]
    usersAreCompulsorilySynced <- arbitrary[Boolean]
    studentsMayBroadcast <- arbitrary[Boolean]
    studentsMayChatPublicly <- arbitrary[Boolean]
  } yield Permissions(ServerConfiguration.empty, studentsCanOptionFriends, studentsCanPublish, usersAreCompulsorilySynced, studentsMayBroadcast, studentsMayChatPublicly)
  def genForeignRelationship = for {
    sys <- genString(32)
    key <- genString(32)
    opt = scala.util.Random.nextBoolean
  } yield {
    if (opt){
      Some((sys,key))
    } else {
      None
    }
  }
  def genOpt[A](in:Gen[A]):Gen[Option[A]] = for {
    v <- in
    opt = scala.util.Random.nextBoolean
  } yield {
    if (opt){
      Some(v)
    } else {
      None
    }
  }
  def genSome[A](in:Gen[A]):Gen[Option[A]] = for {
    v <- in
  } yield {
    Some(v)
  }
  def genGrade = for {
    author <- genString(32)
    timestamp <- validTimestamp
    id <- genString(32)
    location <- genString(32)
    name <- genString(32)
    description <- genString(32)
    visible <- arbitrary[Boolean]
    gradeType <- Gen.oneOf(MeTLGradeValueType.Numeric,MeTLGradeValueType.Boolean,MeTLGradeValueType.Text)
    foreignRelationship <- genForeignRelationship
    gradeReferenceUrl <- genOpt(genString(32))
    numericMaximum <- if (gradeType == MeTLGradeValueType.Numeric){
      genSome(arbitrary[Double])
    } else {
      Gen.oneOf(None,None)
    }
    numericMinimum <- if (gradeType == MeTLGradeValueType.Numeric){
      genSome(arbitrary[Double])
    } else {
      Gen.oneOf(None,None)
    }
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLGrade(ServerConfiguration.empty,author,timestamp,id,location,name,description,gradeType,visible,foreignRelationship,gradeReferenceUrl,numericMaximum,numericMinimum,audiences)

  def genNumericGradeValue = for {
    author <- genString(32)
    timestamp <- validTimestamp
    gradeId <- genString(32)
    gradedUser <- genString(32)
    gradeValue <- arbitrary[Double]
    gradeComment <- genOpt(genString(32))
    gradePrivateComment <- genOpt(genString(32))
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLNumericGradeValue(ServerConfiguration.empty,author,timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,audiences)
  def genBooleanGradeValue = for {
    author <- genString(32)
    timestamp <- validTimestamp
    gradeId <- genString(32)
    gradedUser <- genString(32)
    gradeValue <- arbitrary[Boolean]
    gradeComment <- genOpt(genString(32))
    gradePrivateComment <- genOpt(genString(32))
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLBooleanGradeValue(ServerConfiguration.empty,author,timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,audiences)
  def genTextGradeValue = for {
    author <- genString(32)
    timestamp <- validTimestamp
    gradeId <- genString(32)
    gradedUser <- genString(32)
    gradeValue <- genString(32)
    gradeComment <- genOpt(genString(32))
    gradePrivateComment <- genOpt(genString(32))
    audiences <- genAudiences(scala.util.Random.nextInt(3))
  } yield MeTLTextGradeValue(ServerConfiguration.empty,author,timestamp,gradeId,gradedUser,gradeValue,gradeComment,gradePrivateComment,audiences)

}
