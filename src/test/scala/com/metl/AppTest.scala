package com.metl

import _root_.java.io.File

import _root_.junit.framework._
import Assert._

import _root_.scala.xml.{NodeSeq, XML}
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import jdk.internal.org.xml.sax.InputSource

import scala.xml.parsing.{FatalError, XhtmlParser}

object AppTest {
  def suite: Test = {
    val suite = new TestSuite(classOf[AppTest])
    suite
  }

  def main(args : Array[String]) {
    _root_.junit.textui.TestRunner.run(suite)
  }
}

/**
 * Unit test for simple App.
 */
class AppTest extends TestCase("app") {

  /**
   * Tests to make sure the project's XML files are well-formed.
   *
   * Finds every *.html and *.xml file in src/main/webapp (and its
   * subdirectories) and tests to make sure they are well-formed.
   */
  def testXml() = {
    var failed: List[File] = Nil

    def excluded(file: String): Boolean =
      file.contains("ckeditor")

    def handledXml(file: String) =
      file.endsWith(".xml")

    def handledXHtml(file: String) =
      (file.endsWith(".html") || file.endsWith(".htm") || file.endsWith(".xhtml")) && !excluded(file)

    def wellFormed(file: File) {
      if (file.isDirectory)
        for (f <- file.listFiles) wellFormed(f)

      if (file.isFile && file.exists && handledXHtml(file.getAbsolutePath)) {
        try {
          XhtmlParser.apply(scala.io.Source.fromFile(file.getAbsolutePath))
        }
        catch {
          case t: Throwable =>
            failed = file :: failed
            val msg = "Malformed XML in " + file + ": " + t.getLocalizedMessage
            println(msg)
          case _ =>
        }
      }
    }

    wellFormed(new File("src/main/webapp"))

    val numFails = failed.size
    if (numFails > 0) {
      val fileStr = if (numFails == 1) "file" else "files"
      val msg = "Malformed XML in " + numFails + " " + fileStr //+ ": " + failed.mkString(", ")
      println(msg)
      fail(msg)
    }
  }
}