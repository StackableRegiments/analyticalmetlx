package com.metl.model

import com.metl.data._
import com.metl.model.CanvasContentAnalysis
import org.scalatest._

case class TestContent(username:String,
  override val timestamp:Long,
  override val left:Double,
  override val right:Double,
  override val top:Double,
  override val bottom:Double)
  extends MeTLCanvasContent(null,username,timestamp,"target",Privacy.PUBLIC,"slide","identity")

/*
class ContentAnalysisTest extends FlatSpec with Matchers {
  val sample = List(
    TestContent("a",900,0,0,50,50),
    TestContent("b",910,0,0,20,300),
    TestContent("a",920,0,0,50,500),
    TestContent("b",940,0,0,40,100),
    TestContent("a",950,0,0,30,230),
    TestContent("b",960,0,0,50,100),
    TestContent("a",960,0,0,50,50),
    TestContent("b",920,0,0,50,50),
    TestContent("a",800,0,0,50,50),
    TestContent("b",100,0,0,50,50)
  )
  "Chunking" should "split content into collections on user" in {
    val chunks = CanvasContentAnalysis.chunk(sample)
    chunks should have size 2
  }
  it should "emit ordered collections for users" in {
    val sorts = for {
      chunk <- CanvasContentAnalysis.chunk(sample)
    } yield {
      chunk.chunksets.sortBy(_.timestamp)
    }
    val sorts = List(Boolean)
    all (sorts) should be (true)
  }
}
*/
