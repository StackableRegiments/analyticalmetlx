package com.metl.data

import org.scalatest._
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.{ShouldMatchers, HavePropertyMatcher, HavePropertyMatchResult}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.OptionValues._

import net.liftweb.util.Helpers._
import net.liftweb.common._
import scala.xml._
import com.metl.data._
import com.metl.model._
import Privacy._

class MeTLHistorySuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with QueryXml with MeTLTextMatchers with MeTLDataGenerators {
  test("add an ink") {
    forAll (genInk) { (ink: MeTLInk) =>
      val h = new History("test")
      h.addStanza(ink)
      h.getInks == List(ink) || h.getHighlighters == List(ink)
    }
  }
  test("add a textbox") {
    forAll (genText) { (text: MeTLText) =>
      val h = new History("test")
      h.addStanza(text)
      h.getTexts == List(text)
    }
  }
  test("add an image") {
    forAll (genImage) { (image: MeTLImage) =>
      val h = new History("test")
      h.addStanza(image)
      h.getImages == List(image)
    }
  }
  test("add a submission") {
    forAll (genSubmission) { (sub: MeTLSubmission) =>
      val h = new History("test")
      h.addStanza(sub)
      h.getSubmissions == List(sub)
    }
  }
  test("add a command") {
    forAll (genCommand) { (comm: MeTLCommand) =>
      val h = new History("test")
      h.addStanza(comm)
      h.getCommands == List(comm)
    }
  }
  test("add an ink and then delete it") {
    forAll (genInk) { (ink: MeTLInk) =>
      val h = new History("test")
      val dirtyInk = ink.generateDirty().adjustTimestamp(ink.timestamp + 1)
      h.addStanza(ink)
      h.addStanza(dirtyInk)
      (h.getCanvasContents should not contain (ink))
      (h.getDeletedCanvasContents should contain (ink))
    }
  }
  test("add an ink and then delete it and then undelete it") {
    forAll (genInk) { (ink: MeTLInk) =>
      val h = new History("test")
      val dirtyInk = ink.generateDirty().adjustTimestamp(ink.timestamp + 1)
      h.addStanza(ink)
      h.addStanza(dirtyInk)
      (h.getCanvasContents should not contain (ink))
      (h.getDeletedCanvasContents should contain (ink))
      h.getDeletedCanvasContents.foreach((undeletedInk:MeTLCanvasContent) => {
        val newUndeletedInk = undeletedInk.generateNewIdentity(nextFuncName).adjustTimestamp(ink.timestamp + 2)
        val undeleteMarker = MeTLUndeletedCanvasContent(undeletedInk.server,undeletedInk.author,undeletedInk.timestamp + 1,undeletedInk.target,undeletedInk.privacy,undeletedInk.slide,nextFuncName,"ink",ink.identity,newUndeletedInk.identity)
        h.addStanza(newUndeletedInk)
        h.addStanza(undeleteMarker)
        (h.getCanvasContents should contain (newUndeletedInk))
        (h.getUndeletedCanvasContents should contain (undeleteMarker))
      })
    }
  }
  test("add an ink and then delete it with a moveDelta") {
    forAll (genInk) { (ink: MeTLInk) =>
      val h = new History("test")
      val moveDelta = MeTLMoveDelta(ink.server,ink.author,ink.timestamp + 1,ink.target,ink.privacy,ink.slide,nextFuncName,0.0,0.0,List(ink.identity),Nil,Nil,Nil,Nil,0.0,0.0,1.0,1.0,Privacy.NOT_SET,true)
      h.addStanza(ink)
      h.addStanza(moveDelta)
      (h.getCanvasContents should not contain (ink))
      (h.getDeletedCanvasContents should contain (ink))
    }
  }
  test("add an ink and then delete it with a moveDelta and then undelete it") {
    forAll (genInk) { (ink: MeTLInk) =>
      val h = new History("test")
      val moveDelta = MeTLMoveDelta(ink.server,ink.author,ink.timestamp + 1,ink.target,ink.privacy,ink.slide,nextFuncName,0.0,0.0,List(ink.identity),Nil,Nil,Nil,Nil,0.0,0.0,1.0,1.0,Privacy.NOT_SET,true)
      h.addStanza(ink)
      h.addStanza(moveDelta)
      (h.getCanvasContents should not contain (ink))
      (h.getDeletedCanvasContents should contain (ink))
      h.getDeletedCanvasContents.foreach((undeletedInk:MeTLCanvasContent) => {
        val newUndeletedInk = undeletedInk.generateNewIdentity(nextFuncName).adjustTimestamp(ink.timestamp + 2)
        val undeleteMarker = MeTLUndeletedCanvasContent(undeletedInk.server,undeletedInk.author,undeletedInk.timestamp + 1,undeletedInk.target,undeletedInk.privacy,undeletedInk.slide,nextFuncName,"ink",ink.identity,newUndeletedInk.identity)
        h.addStanza(newUndeletedInk)
        h.addStanza(undeleteMarker)
        (h.getCanvasContents should contain (newUndeletedInk))
        (h.getUndeletedCanvasContents should contain (undeleteMarker))
      })
    }
  }
  test("add an ink and then delete it with a moveDelta and then undelete it and then filter the history") {
    forAll (genInk) { (ink: MeTLInk) =>
      val h = new History("test")
      val moveDelta = MeTLMoveDelta(ink.server,ink.author,ink.timestamp + 1,ink.target,ink.privacy,ink.slide,nextFuncName,0.0,0.0,List(ink.identity),Nil,Nil,Nil,Nil,0.0,0.0,1.0,1.0,Privacy.NOT_SET,true)
      h.addStanza(ink)
      h.addStanza(moveDelta)
      (h.getCanvasContents should not contain (ink))
      (h.getDeletedCanvasContents should contain (ink))
      h.getDeletedCanvasContents.foreach((undeletedInk:MeTLCanvasContent) => {
        val newUndeletedInk = undeletedInk.generateNewIdentity(nextFuncName).adjustTimestamp(ink.timestamp + 2)
        val undeleteMarker = MeTLUndeletedCanvasContent(undeletedInk.server,undeletedInk.author,undeletedInk.timestamp + 1,undeletedInk.target,undeletedInk.privacy,undeletedInk.slide,nextFuncName,"ink",ink.identity,newUndeletedInk.identity)
        h.addStanza(newUndeletedInk)
        h.addStanza(undeleteMarker)
        val nh = h.filter(c => true)
        (nh.getCanvasContents should not contain (ink))
        (nh.getDeletedCanvasContents should contain (ink))
        (nh.getCanvasContents should contain (newUndeletedInk))
        (nh.getUndeletedCanvasContents should contain (undeleteMarker))
      })
    }
  }
  test("add an ink and then delete it with a moveDelta and then undelete it and then filter the history and then merge the history") {
    forAll (genInk) { (ink: MeTLInk) =>
      val h = new History("test")
      val moveDelta = MeTLMoveDelta(ink.server,ink.author,ink.timestamp + 1,ink.target,ink.privacy,ink.slide,nextFuncName,0.0,0.0,List(ink.identity),Nil,Nil,Nil,Nil,0.0,0.0,1.0,1.0,Privacy.NOT_SET,true)
      h.addStanza(ink)
      h.addStanza(moveDelta)
      (h.getCanvasContents should not contain (ink))
      (h.getDeletedCanvasContents should contain (ink))
      h.getDeletedCanvasContents.foreach((undeletedInk:MeTLCanvasContent) => {
        val newUndeletedInk = undeletedInk.generateNewIdentity(nextFuncName).adjustTimestamp(ink.timestamp + 2)
        val undeleteMarker = MeTLUndeletedCanvasContent(undeletedInk.server,undeletedInk.author,undeletedInk.timestamp + 1,undeletedInk.target,undeletedInk.privacy,undeletedInk.slide,nextFuncName,"ink",ink.identity,newUndeletedInk.identity)
        h.addStanza(newUndeletedInk)
        h.addStanza(undeleteMarker)
        val nh = h.filter(c => true)
        val mh = new History("merged").merge(nh)
        (mh.getCanvasContents should not contain (ink))
        (mh.getDeletedCanvasContents should contain (ink))
        (mh.getCanvasContents should contain (newUndeletedInk))
        (mh.getUndeletedCanvasContents should contain (undeleteMarker))
      })
    }
  }
}
