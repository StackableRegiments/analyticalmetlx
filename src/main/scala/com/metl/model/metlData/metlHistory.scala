package com.metl.data

import com.metl.utils._

import net.liftweb.util.Helpers._
import net.liftweb.common._
import java.util.zip.{ZipInputStream,ZipEntry}
import org.apache.commons.io.IOUtils
import scala.xml.NodeSeq
import java.io.ByteArrayInputStream
import java.util.Date
import Privacy._

import scala.collection.mutable.{ListBuffer=>MutList}

case class History(jid:String,xScale:Double = 1.0, yScale:Double = 1.0,xOffset:Double = 0,yOffset:Double = 0) extends Logger {
  protected def createHistory(jid:String,xScale:Double,yScale:Double,xOffset:Double,yOffset:Double) = History(jid,xScale,yScale,xOffset,yOffset)
  protected var lastModifiedTime:Long = 0L
  protected var lastVisuallyModifiedTime:Long = 0L
  protected var latestTimestamp = 0L
  private val defaultXScale:Double = 1.0
  private val defaultYScale:Double = 1.0
  private val defaultXOffset:Double = 0.0
  private val defaultYOffset:Double = 0.0
  protected def update(visual:Boolean) = {
    val now = new Date().getTime
    lastModifiedTime = now
    if (visual){
      lastVisuallyModifiedTime = now
    }
  }
  def lastModified = lastModifiedTime
  def lastVisuallyModified = lastVisuallyModifiedTime
  def getLatestTimestamp = latestTimestamp

  protected def scaleItemToSuitHistory(cc:MeTLCanvasContent):MeTLCanvasContent = {
    if(shouldAdjust) cc.adjustVisual(xOffset,yOffset,1.0,1.0).scale(xScale,yScale) else cc
  }
  protected def unscaleItemToSuitHistory(cc:MeTLCanvasContent):MeTLCanvasContent = {
    cc.adjustVisual(xOffset * -1, yOffset * -1,1.0,1.0).scale(1 / xScale, 1 / yScale)
  }

  def attachRealtimeHook(hook:MeTLStanza=>Unit):History = {
    outputHook = hook
    this
  }

  protected var outputHook:MeTLStanza => Unit = (s) => {}

  protected var stanzas:MutList[MeTLStanza] = MutList.empty[MeTLStanza]
  protected var canvasContents:MutList[MeTLCanvasContent] = MutList.empty[MeTLCanvasContent]
  protected var highlighters:MutList[MeTLInk] = MutList.empty[MeTLInk]
  protected var inks:MutList[MeTLInk] = MutList.empty[MeTLInk]
  protected var dirtyInks:MutList[MeTLDirtyInk] = MutList.empty[MeTLDirtyInk]
  protected var images:MutList[MeTLImage] = MutList.empty[MeTLImage]
  protected var dirtyImages:MutList[MeTLDirtyImage] = MutList.empty[MeTLDirtyImage]
  protected var videos:MutList[MeTLVideo] = MutList.empty[MeTLVideo]
  protected var dirtyVideos:MutList[MeTLDirtyVideo] = MutList.empty[MeTLDirtyVideo]
  protected var texts:MutList[MeTLText] = MutList.empty[MeTLText]
  protected var dirtyTexts:MutList[MeTLDirtyText] = MutList.empty[MeTLDirtyText]
  protected var multiWordTexts:MutList[MeTLMultiWordText] = MutList.empty[MeTLMultiWordText]
  protected var metlMoveDeltas:MutList[MeTLMoveDelta] = MutList.empty[MeTLMoveDelta]
  protected var quizzes:MutList[MeTLQuiz] = MutList.empty[MeTLQuiz]
  protected var quizResponses:MutList[MeTLQuizResponse] = MutList.empty[MeTLQuizResponse]
  protected var submissions:MutList[MeTLSubmission] = MutList.empty[MeTLSubmission]
  protected var commands:MutList[MeTLCommand] = MutList.empty[MeTLCommand]
  protected var latestCommands:Map[String,MeTLCommand] = Map.empty[String,MeTLCommand]
  protected var files:MutList[MeTLFile] = MutList.empty[MeTLFile]
  protected var attendances:MutList[Attendance] = MutList.empty[Attendance]
  protected var videoStreams:MutList[MeTLVideoStream] = MutList.empty[MeTLVideoStream]
  protected var unhandledCanvasContents:MutList[MeTLUnhandledCanvasContent] = MutList.empty[MeTLUnhandledCanvasContent]
  protected var unhandledStanzas:MutList[MeTLUnhandledStanza] = MutList.empty[MeTLUnhandledStanza]

  def getLatestCommands:Map[String,MeTLCommand] = latestCommands

  def getAll = stanzas.toList
  def getCanvasContents = canvasContents.toList
  def getHighlighters = highlighters.toList
  def getInks = inks.toList
  def getImages = images.toList
  def getVideos = videos.toList
  def getTexts = texts.toList
  def getMultiWordTexts = multiWordTexts.toList 
  def getQuizzes = quizzes.toList
  def getQuizResponses = quizResponses.toList
  def getSubmissions = submissions.toList
  def getAttendances = attendances.toList
  def getFiles = files.toList
  def getCommands = commands.toList
  def getVideoStreams = videoStreams.toList
  def getUnhandledCanvasContents = unhandledCanvasContents.toList
  def getUnhandledStanzas = unhandledStanzas.toList

  def getRenderable = Stopwatch.time("History.getRenderable",getCanvasContents.map(scaleItemToSuitHistory(_)))
  def getRenderableGrouped:Tuple6[List[MeTLText],List[MeTLInk],List[MeTLInk],List[MeTLImage],List[MeTLMultiWordText],List[MeTLVideo]] = Stopwatch.time("History.getRenderableGrouped",{
    val grouped = getRenderableGroupedInternal
    (grouped._1.toList,grouped._2.toList,grouped._3.toList,grouped._4.toList,grouped._5.toList,grouped._6.toList)
  })
  def getRenderableGroupedInternal:Tuple6[MutList[MeTLText],MutList[MeTLInk],MutList[MeTLInk],MutList[MeTLImage],MutList[MeTLMultiWordText],MutList[MeTLVideo]] = Stopwatch.time("History.getRenderableGroupedInternal",{
    getRenderable.foldLeft((MutList.empty[MeTLText],MutList.empty[MeTLInk],MutList.empty[MeTLInk],MutList.empty[MeTLImage],MutList.empty[MeTLMultiWordText],MutList.empty[MeTLVideo]))((acc,item) => item match {
      case t:MeTLText => (acc._1 += t,acc._2,acc._3,acc._4,acc._5,acc._6)
      case h:MeTLInk if h.isHighlighter => (acc._1,acc._2 += h,acc._3,acc._4,acc._5,acc._6)
      case s:MeTLInk => (acc._1,acc._2,acc._3 += s,acc._4,acc._5,acc._6)
      case i:MeTLImage => (acc._1,acc._2,acc._3,acc._4 += i,acc._5,acc._6)
      case i:MeTLMultiWordText => (acc._1,acc._2,acc._3,acc._4,acc._5 += i,acc._6)
      case i:MeTLVideo => (acc._1,acc._2,acc._3,acc._4,acc._5,acc._6 += i)
      case _ => acc
    })
  })

  def merge(other:History):History = Stopwatch.time("History.merge",{
    val newHistory = createHistory(jid,xScale,yScale,xOffset,yOffset)
      (getAll ::: other.getAll).foreach(i => newHistory.addStanza(i))
    newHistory
  })

  def getImageBySource(source:String) = Stopwatch.time("History.getImageBySource",getImages.find(i => i.source.map(s => s == source).openOr(false)))
  def getImageByIdentity(identity:String) = Stopwatch.time("History.getImageByIdentity",getImages.find(i => i.identity == identity))
  def getVideoBySource(source:String) = Stopwatch.time("History.getVideoBySource",getVideos.find(i => i.source.map(s => s == source).openOr(false)))
  def getVideoByIdentity(identity:String) = Stopwatch.time("History.getVideoByIdentity",getVideos.find(i => i.identity == identity))
  def getQuizByIdentity(identity:String) = Stopwatch.time("History.getQuizImageByIdentity",getQuizzes.filter(i => i.id == identity).sortBy(q => q.timestamp).reverse.headOption)
  def getFileByIdentity(identity:String) = Stopwatch.time("History.getFileByIdentity",getFiles.filter(_.id == identity).sortBy(_.timestamp).reverse.headOption)
  def getSubmissionByAuthorAndIdentity(author:String,identity:String) = Stopwatch.time("History.getSubmissionByAuthorAndIdentity",getSubmissions.find(s => s.author == author && s.identity == identity))

  protected def processNewStanza(s:MeTLStanza) = s match {
    case s:MeTLDirtyInk => removeInk(s)
    case s:MeTLDirtyText => removeText(s)
    case s:MeTLDirtyImage => removeImage(s)
    case s:MeTLDirtyVideo => removeVideo(s)
    case s:MeTLMoveDelta => addMeTLMoveDelta(s)
    case s:MeTLInk if s.isHighlighter => addHighlighter(s)
    case s:MeTLInk => addInk(s)
    case s:MeTLImage => addImage(s)
    case s:MeTLVideo => addVideo(s)
    case s:MeTLText => addText(s)
    case s:MeTLMultiWordText => addMultiWordText(s)
    case s:MeTLQuiz => addQuiz(s)
    case s:MeTLQuizResponse => addQuizResponse(s)
    case s:MeTLSubmission => addSubmission(s)
    case s:MeTLCommand => addCommand(s)
    case s:Attendance => addAttendance(s)
    case s:MeTLFile => addFile(s)
    case s:MeTLVideoStream => addVideoStream(s)
    case s:MeTLUnhandledCanvasContent => addMeTLUnhandledCanvasContent(s)
    case s:MeTLUnhandledStanza => addMeTLUnhandledStanza(s)
    case _ => {
      warn("makeHistory: I don't know what to do with a MeTLStanza: %s".format(s))
      this
    }
  }

  def addStanza(s:MeTLStanza) = Stopwatch.time("History.addStanza",{
    stanzas += s
    latestTimestamp = List(s.timestamp,latestTimestamp).max
    processNewStanza(s)
  })


  protected def moveIndividualContent(s:MeTLMoveDelta,c:MeTLCanvasContent,left:Double = 0, top:Double = 0):Unit = Stopwatch.time("History.moveIndividualContent", {
    def matches(coll:Seq[String],i:MeTLCanvasContent):Boolean = coll.contains(i.identity) && i.timestamp < s.timestamp && i.privacy == s.privacy
    c match {
      case i:MeTLInk if matches(s.inkIds,i) => {
        removeInk(i.generateDirty(s.timestamp),false)
        if (!s.isDeleted){
          addInk(s.adjustIndividualContent(i,true,left,top).asInstanceOf[MeTLInk],false)
        }
      }
      case i:MeTLMultiWordText if matches(s.multiWordTextIds,i) => {
        removeMultiWordText(i.generateDirty(s.timestamp),false)
        if(!s.isDeleted)
          addMultiWordText(s.adjustIndividualContent(i,true,left,top).asInstanceOf[MeTLMultiWordText],false)
      }
      case i:MeTLText if matches(s.textIds,i) => {
        removeText(i.generateDirty(s.timestamp),false)
        if (!s.isDeleted)
          addText(s.adjustIndividualContent(i,true,left,top).asInstanceOf[MeTLText],false)
      }
      case i:MeTLImage if matches(s.imageIds,i) => {
        removeImage(i.generateDirty(s.timestamp),false)
        if (!s.isDeleted)
          addImage(s.adjustIndividualContent(i,true,left,top).asInstanceOf[MeTLImage],false)
      }
      case i:MeTLVideo if matches(s.videoIds,i) => {
        removeVideo(i.generateDirty(s.timestamp),false)
        if (!s.isDeleted)
          addVideo(s.adjustIndividualContent(i,true,left,top).asInstanceOf[MeTLVideo],false)
      }
      case _ => {}
    }
  })
  protected def moveContent(s:MeTLMoveDelta) = Stopwatch.time("History.moveContent",{
    def matches(cc:MeTLCanvasContent):Boolean = cc match {
      case i:MeTLInk => s.inkIds.contains(i.identity) && i.timestamp < s.timestamp && i.privacy == s.privacy
      case i:MeTLText => s.textIds.contains(i.identity) && i.timestamp < s.timestamp && i.privacy == s.privacy
      case i:MeTLMultiWordText => s.multiWordTextIds.contains(i.identity) && i.timestamp < s.timestamp && i.privacy == s.privacy
      case i:MeTLImage => s.imageIds.contains(i.identity) && i.timestamp < s.timestamp && i.privacy == s.privacy
      case i:MeTLVideo => s.videoIds.contains(i.identity) && i.timestamp < s.timestamp && i.privacy == s.privacy
    }
    val relevantContents = getCanvasContents.filter(cc => matches(cc))
    val (boundsLeft,boundsTop) = {
      if (Double.NaN.equals(s.xOrigin) || Double.NaN.equals(s.yOrigin)){
        var first = true;
        relevantContents.foldLeft((0.0,0.0))((acc,item) => {
          if (first)
            (item.left,item.top)
          else
            (Math.min(item.left,acc._1),Math.min(item.top,acc._2))
        });
      } else (s.xOrigin,s.yOrigin)
    }
    relevantContents.foreach(cc => moveIndividualContent(s,cc,boundsLeft,boundsTop))
  })
  protected def shouldAdd(cc:MeTLCanvasContent):Boolean = {
    val dirtyTest = cc match {
      case ink:MeTLInk => dirtyInks.exists(dInk => dInk.isDirtierFor(ink))
      case text:MeTLText => dirtyTexts.exists(dText => dText.isDirtierFor(text))
      case multiWordText:MeTLMultiWordText => dirtyTexts.exists(dText => dText.isDirtierFor(multiWordText))
      case image:MeTLImage => dirtyImages.exists(dImage => dImage.isDirtierFor(image))
      case video:MeTLVideo => dirtyVideos.exists(dVideo => dVideo.isDirtierFor(video))
      case _ => false
    }
    !(dirtyTest || metlMoveDeltas.filter(md => md.isDirtierFor(cc)).exists(md => md.isDeleted))
  }

  def addMeTLUnhandledCanvasContent(s:MeTLUnhandledCanvasContent,store:Boolean = true) = Stopwatch.time("History.addUnhandledCanvasContent",{
    //growBounds(s.left,s.right,s.top,s.bottom) can't grow bounds - not all canvas contents know their L,R,T,B.  If they did, we could make it part of their constructor, but as a result, MeTLUnhandledCanvasContent doesn't know its bounds.
    if (store){
      outputHook(s)
      unhandledCanvasContents += s
    }
    this
  })
  def addMeTLUnhandledStanza(s:MeTLUnhandledStanza,store:Boolean = true) = Stopwatch.time("History.addMeTLUnhandledStanza",{
    if (store){
      outputHook(s)
      unhandledStanzas += s
    }
    this
  })
  def addMeTLMoveDelta(s:MeTLMoveDelta,store:Boolean = true) = Stopwatch.time("History.addMeTLMoveDelta",{
    if (!metlMoveDeltas.exists(mmd => mmd.matches(s))){
      moveContent(s)
      if (store){
        outputHook(s)
        metlMoveDeltas += s
      }
    }
    this
  })

  def addHighlighter(s:MeTLInk,store:Boolean = true) = Stopwatch.time("History.addHighlighter",{
    if (shouldAdd(s)){
      val adjustedInk = metlMoveDeltas.filter(md => !md.isDeleted && md.isDirtierFor(s)).sortBy(_.timestamp).foldLeft(s)((acc,item) => {
        item.adjustIndividualContent(acc).asInstanceOf[MeTLInk]
      })
      canvasContents = canvasContents.filterNot(cc => cc match {
        case i:MeTLInk => i.matches(s)
        case _ => false
      }) += adjustedInk
      growBounds(adjustedInk.left,adjustedInk.right,adjustedInk.top,adjustedInk.bottom)
      if (store)
        outputHook(adjustedInk)
      update(true)
    }
    if (store)
      highlighters = highlighters += s
    this
  })
  def addInk(s:MeTLInk,store:Boolean = true) = Stopwatch.time("History.addInk", {
    if (shouldAdd(s)){
      val adjustedInk = metlMoveDeltas.filter(md => !md.isDeleted && md.isDirtierFor(s)).sortBy(_.timestamp).foldLeft(s)((acc,item) => {
        item.adjustIndividualContent(acc).asInstanceOf[MeTLInk]
      })
      canvasContents = canvasContents.filterNot(cc => cc match {
        case i:MeTLInk => i.matches(s)
        case _ => false
      }) += adjustedInk
      growBounds(adjustedInk.left,adjustedInk.right,adjustedInk.top,adjustedInk.bottom)
      if (store)
        outputHook(adjustedInk)
      update(true)
    }
    if (store)
      inks = inks += s
    this
  })

  def addAttendance(s:Attendance,store:Boolean = true) = Stopwatch.time("History.addAttendance",{
    if (store){
      outputHook(s)
      attendances = attendances += s
    }
    this
  })
  def addFile(s:MeTLFile,store:Boolean = true) = Stopwatch.time("History.addFile",{
    if (store){
      outputHook(s)
      val candidates = files.filter(_.id == s.id)
      files --= candidates
      (candidates += s).sortWith((a,b) => a.timestamp > b.timestamp).headOption.filterNot(_.deleted).foreach(candidate => {
        files += candidate
      })
    }
    this
  })
  def addVideoStream(s:MeTLVideoStream,store:Boolean = true) = Stopwatch.time("History.addVideoStream",{
    if (store){
      outputHook(s)
      val candidates = videoStreams.filter(_.id == s.id)
      videoStreams --= candidates
      (candidates += s).sortWith((a,b) => a.timestamp > b.timestamp).headOption.filterNot(_.isDeleted).foreach(candidate => {
        videoStreams += candidate
      })
    }
    this
  })
  def addVideo(s:MeTLVideo,store:Boolean = true) = Stopwatch.time("History.addVideo",{
    if (shouldAdd(s)){
      val adjustedVideo = metlMoveDeltas.filter(md => !md.isDeleted && md.isDirtierFor(s)).sortBy(_.timestamp).foldLeft(s)((acc,item) => {
        item.adjustIndividualContent(acc).asInstanceOf[MeTLVideo]
      })
      canvasContents = canvasContents.filterNot(cc => cc match {
        case i:MeTLVideo => i.matches(s)
        case _ => false
      }) += adjustedVideo
      growBounds(adjustedVideo.left,adjustedVideo.right,adjustedVideo.top,adjustedVideo.bottom)
      if (store)
        outputHook(adjustedVideo)
      update(true)
    }
    if (store)
      videos += s
    this
  })

  def addImage(s:MeTLImage,store:Boolean = true) = Stopwatch.time("History.addImage",{
    if (shouldAdd(s)){
      val adjustedImage = metlMoveDeltas.filter(md => !md.isDeleted && md.isDirtierFor(s)).sortBy(_.timestamp).foldLeft(s)((acc,item) => {
        item.adjustIndividualContent(acc).asInstanceOf[MeTLImage]
      })
      canvasContents = canvasContents.filterNot(cc => cc match {
        case i:MeTLImage => i.matches(s)
        case _ => false
      }) += adjustedImage
      growBounds(adjustedImage.left,adjustedImage.right,adjustedImage.top,adjustedImage.bottom)
      if (store)
        outputHook(adjustedImage)
      update(true)
    }
    if (store)
      images += s
    this
  })
  def addMultiWordText(s:MeTLMultiWordText,store:Boolean = true) = Stopwatch.time("History.addMultiWordText",{
    if(shouldAdd(s)){
      val suspectTexts = canvasContents.filter{
        case t:MeTLMultiWordText => t.matches(s)
        case _ => false
      }
      canvasContents --= suspectTexts
      val identifiedTexts = (suspectTexts += s).sortBy(q => q.timestamp).reverse
      identifiedTexts.headOption.foreach{
        case hot:MeTLMultiWordText => {
          val adjustedText = metlMoveDeltas.filter(md => !md.isDeleted && md.isDirtierFor(hot)).sortBy(_.timestamp).foldLeft(hot)((acc,item) => {
            item.adjustIndividualContent(acc).asInstanceOf[MeTLMultiWordText]
          })
          canvasContents += adjustedText
          if (adjustedText.left < getLeft || adjustedText.right > getRight || getBottom < adjustedText.bottom || adjustedText.top < getTop)
            growBounds(adjustedText.left,adjustedText.right,adjustedText.top,adjustedText.bottom)
          else if (identifiedTexts.length > 1){
            identifiedTexts(1) match {
              case st:MeTLMultiWordText if ((st.right == getRight && adjustedText.right < getRight) || (st.bottom == getBottom && adjustedText.bottom < getBottom) || (st.top == getTop && adjustedText.top > getTop) || (st.left == getLeft && adjustedText.left > getLeft)) =>
                calculateBoundsWithout(adjustedText.left,adjustedText.right,adjustedText.top,adjustedText.bottom)
              case _ => {}
            }
          }
          if (store)
            outputHook(adjustedText)
        }
        case _ => {}
      }
      update(true)
    }
    if (store)
      multiWordTexts += s
    this
  })
  def addText(s:MeTLText,store:Boolean = true) = Stopwatch.time("History.addText",{
    if (shouldAdd(s)){
      val (suspectTexts,remainingContent) = canvasContents.partition(cc => cc match {
        case t:MeTLText => t.matches(s)
        case _ => false
      })
      val identifiedTexts = (suspectTexts  += s).sortBy(q => q.timestamp).reverse
      canvasContents = identifiedTexts.headOption.map(ho => ho match {
        case hot:MeTLText => {
          val adjustedText = metlMoveDeltas.filter(md => !md.isDeleted && md.isDirtierFor(hot)).sortBy(_.timestamp).foldLeft(hot)((acc,item) => {
            item.adjustIndividualContent(acc).asInstanceOf[MeTLText]
          })
          val newCanvasContents = remainingContent += adjustedText
          if (adjustedText.left < getLeft || adjustedText.right > getRight || getBottom < adjustedText.bottom || adjustedText.top < getTop)
            growBounds(adjustedText.left,adjustedText.right,adjustedText.top,adjustedText.bottom)
          else if (identifiedTexts.length > 1){
            identifiedTexts(1) match {
              case st:MeTLText if ((st.right == getRight && adjustedText.right < getRight) || (st.bottom == getBottom && adjustedText.bottom < getBottom) || (st.top == getTop && adjustedText.top > getTop) || (st.left == getLeft && adjustedText.left > getLeft)) =>
                calculateBoundsWithout(adjustedText.left,adjustedText.right,adjustedText.top,adjustedText.bottom)
              case _ => {}
            }
          }
          if (store)
            outputHook(adjustedText)
          newCanvasContents
        }
        case _ => remainingContent
      }).getOrElse(remainingContent)
      update(true)
    }
    if (store)
      texts += s
    this
  })
  def addQuiz(s:MeTLQuiz,store:Boolean = true) = Stopwatch.time("History.addQuiz",{
    if (store){
      val suspectQuizzes = quizzes.filter(_.id == s.id)
      val newQuiz = (suspectQuizzes += s).sortBy(q => q.timestamp).reverse.head
      quizzes --= suspectQuizzes
      if (!newQuiz.isDeleted){
        quizzes += newQuiz
      }
      outputHook(newQuiz)
      update(false)
    }
    this
  })
  def addQuizResponse(s:MeTLQuizResponse,store:Boolean = true) = Stopwatch.time("History.addQuizResponse",{
    if (store) {
      quizResponses += s
      outputHook(s)
      update(false)
    }
    this
  })
  def addSubmission(s:MeTLSubmission,store:Boolean = true) = Stopwatch.time("History.addSubmission",{
    if (store){
      submissions += s
      outputHook(s)
      update(false)
    }
    this
  })
  def addCommand(s:MeTLCommand,store:Boolean = true) = Stopwatch.time("History.addCommand",{
    if (store){
      latestCommands = latestCommands.updated(s.command,s)
      commands += s
      outputHook(s)
      update(false)
    }
    this
  })
  def removeInk(dirtyInk:MeTLDirtyInk,store:Boolean = true) = Stopwatch.time("History.removeInk",{
    val items = canvasContents.filter{
      case i:MeTLInk => dirtyInk.isDirtierFor(i)
      case _ => false
    }
    canvasContents --= items
    items.map(s => s match {
      case i:MeTLInk => {
        calculateBoundsWithout(i.left,i.right,i.top,i.bottom)
        if (store)
          outputHook(dirtyInk)
        update(true)
      }
      case _ => {}
    })
    if (store)
      dirtyInks += dirtyInk
    this
  })
  def removeImage(dirtyImage:MeTLDirtyImage,store:Boolean = true) = Stopwatch.time("History.removeImage",{
    val items = getCanvasContents.filter{
      case i:MeTLImage => dirtyImage.isDirtierFor(i)
      case _ => false
    }
    canvasContents --= items
    items.map(s => s match {
      case i:MeTLImage => {
        calculateBoundsWithout(i.left,i.right,i.top,i.bottom)
        if (store)
          outputHook(dirtyImage)
        update(true)
      }
      case _ => {}
    })
    if (store)
      dirtyImages += dirtyImage
    this
  })
  def removeVideo(dirtyVideo:MeTLDirtyVideo,store:Boolean = true) = Stopwatch.time("History.removeImage",{
    val items = getCanvasContents.filter{
      case i:MeTLVideo => dirtyVideo.isDirtierFor(i)
      case _ => false
    }
    canvasContents --= items
    items.map(s => s match {
      case i:MeTLVideo => {
        calculateBoundsWithout(i.left,i.right,i.top,i.bottom)
        if (store)
          outputHook(dirtyVideo)
        update(true)
      }
      case _ => {}
    })
    if (store)
      dirtyVideos += dirtyVideo
    this
  })

  def removeText(dirtyText:MeTLDirtyText,store:Boolean = true) = Stopwatch.time("History.removeText",{
    val items = getCanvasContents.filter{
      case t:MeTLText => dirtyText.isDirtierFor(t)
      case _ => false
    }
    canvasContents --= items
    items.foreach(s => s match {
      case t:MeTLText => {
        calculateBoundsWithout(t.left,t.right,t.top,t.bottom)
        if (store)
          outputHook(dirtyText)
        update(true)
      }
      case t:MeTLMultiWordText => {
        calculateBoundsWithout(t.left,t.right,t.top,t.bottom)
        if (store)
          outputHook(dirtyText)
        update(true)
      }
      case _ => {}
    })
    if (store)
      dirtyTexts += dirtyText
    this
  })

  def removeMultiWordText(dirtyText:MeTLDirtyText,store:Boolean = true) = Stopwatch.time("History.removeMultiWordText",{
    val items = getCanvasContents.filter{
      case t:MeTLMultiWordText => dirtyText.isDirtierFor(t)
      case _ => false
    }
    canvasContents --= items
    items.foreach(s => s match {
      case t:MeTLMultiWordText => {
        calculateBoundsWithout(t.left,t.right,t.top,t.bottom)
        if (store)
          outputHook(dirtyText)
        update(true)
      }
      case _ => {}
    })
    if (store)
      dirtyTexts += dirtyText
    this
  })

  protected var left:Double = 0
  protected var right:Double = 0
  protected var top:Double = 0
  protected var bottom:Double = 0

  def getLeft = left
  def getRight = right
  def getTop = top
  def getBottom = bottom

  protected def growBounds(sLeft:Double,sRight:Double,sTop:Double,sBottom:Double) = Stopwatch.time("History.growBounds",{
    if (!sLeft.isNaN)
      left = Math.min(left,sLeft)
    if (!sRight.isNaN)
      right = Math.max(right,sRight)
    if (!sTop.isNaN)
      top = Math.min(top,sTop)
    if (!sBottom.isNaN)
      bottom = Math.max(bottom,sBottom)
  })

  protected def calculateBoundsWithout(sLeft:Double,sRight:Double,sTop:Double,sBottom:Double) = Stopwatch.time("History.calculateBoundsWithout",{
    if (sLeft == left || sRight == right || sTop == top || sBottom == bottom)
      calculateBounds
  })

  protected def calculateBounds = Stopwatch.time("History.calculateBounds", {
    top = 0
    left = 0
    right = 0
    bottom = 0
    getCanvasContents.foreach(s => s match {
      case i:MeTLInk => growBounds(i.left,i.right,i.top,i.bottom)
      case i:MeTLImage => growBounds(i.left,i.right,i.top,i.bottom)
      case i:MeTLVideo => growBounds(i.left,i.right,i.top,i.bottom)
      case t:MeTLText => growBounds(t.left,t.right,t.top,t.bottom)
      case t:MeTLMultiWordText => growBounds(t.left,t.right,t.top,t.bottom)
    })
  })

  def until(before:Long):History = Stopwatch.time("History.until",{
    filter(i => i.timestamp < before)
  })
  def filter(filterFunc:(MeTLStanza) => Boolean):History = Stopwatch.time("History.filter",{
    val newHistory = createHistory(jid,xScale,yScale,xOffset,yOffset)
    getAll.filter(filterFunc).foreach(i => newHistory.addStanza(i))
    newHistory
  })
  def filterCanvasContents(filterFunc:(MeTLCanvasContent) => Boolean, includeNonCanvasContents:Boolean = true):History = Stopwatch.time("History.filterCanvasContents",{
    filter(i => i match {
      case cc:MeTLCanvasContent => filterFunc(cc)
      case _ => includeNonCanvasContents
    })
  })

  def filterCanvasContentsForMoveDelta(md:MeTLMoveDelta):History = Stopwatch.time("History.filterCanvasContentForMoveDelta",{
    filter(i => i match {
      case mmd:MeTLMoveDelta => mmd.timestamp < md.timestamp && (mmd.inkIds.exists(i => md.inkIds.contains(i)) || mmd.multiWordTextIds.exists(i => md.multiWordTextIds.contains(i)) || mmd.textIds.exists(i => md.textIds.contains(i)) || mmd.imageIds.exists(i => md.imageIds.contains(i)))
      case di:MeTLDirtyInk => md.inkIds.contains(di.identity)
      case dt:MeTLDirtyText => md.textIds.contains(dt.identity) || md.multiWordTextIds.contains(dt.identity)
      case di:MeTLDirtyImage => md.imageIds.contains(di.identity)
      case di:MeTLDirtyVideo => md.imageIds.contains(di.identity)
      case cc:MeTLCanvasContent => md.isDirtierFor(cc,false)
      case _ => false
    })
  })

  def scale(factor:Double) = Stopwatch.time("History.scale",{
    val newHistory = createHistory(jid,factor,factor,0,0)
    getAll.foreach(i => newHistory.addStanza(i))
    newHistory
  })
  def resetToOriginalVisual = Stopwatch.time("History.resetToOriginalVisual",{
    val newHistory = createHistory(jid, defaultXOffset, defaultYOffset, defaultXScale, defaultYScale)
    getAll.foreach(i => newHistory.addStanza(i))
    newHistory
  })
  def adjustToVisual(xT:Double,yT:Double,xS:Double,yS:Double) = Stopwatch.time("History.adjustVisual",{
    val newHistory = createHistory(jid,xS * xScale,yS * yScale,xT + xOffset,yT + yOffset)
    getAll.foreach(i => newHistory.addStanza(i))
    newHistory
  })
  def getUserSpecificHistory(user:String, isTeacher:Boolean = false) = Stopwatch.time("History.getUserSpecificHistory(%s)".format(user),{
    val newHistory = createHistory(jid,xScale,yScale,xOffset,yOffset)
    getAll.foreach(i => i match {
      case q:MeTLQuiz => newHistory.addStanza(q)
      case c:MeTLCommand => newHistory.addStanza(c)
      case f:MeTLFile => newHistory.addStanza(f)
      case s:MeTLStanza => {
        if (isTeacher || s.author.toLowerCase == user)
          newHistory.addStanza(s)
      }
    })
    newHistory
  })
  protected def shouldAdjust:Boolean = (xScale != 1.0 || yScale != 1.0 || xOffset != 0 || yOffset != 0)
  def shouldRender:Boolean = ((getLeft < 0 || getRight > 0 || getTop < 0 || getBottom > 0) && getCanvasContents.length > 0)
}

object History {
  def empty = History("")
}

abstract class HistoryRetriever(serverName:String) {
  lazy val server = ServerConfiguration.configForName(serverName)
  def getMeTLHistory(jid:String):History
  def makeHistory(jid:String,stanzas:List[MeTLStanza]):History = Stopwatch.time("History.makeHistory",{
    stanzas.sortBy(s => s.timestamp).foldLeft(new History(jid))((h,item) => h.addStanza(item))
  })
}

object EmptyHistory extends HistoryRetriever("empty") {
  def getMeTLHistory(jid:String) = History.empty
}
