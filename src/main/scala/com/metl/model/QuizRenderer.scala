package com.metl.renderer

import com.metl.data._
import com.metl.utils._

import java.awt.{Color=>AWTColor,List=>AWTList,Point=>AWTPoint,_}
import java.awt.image._
import java.awt.font._
import java.awt.geom.AffineTransform
import java.text._
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream
//import com.bric.geom.BasicVectorizer
import net.liftweb.util.Helpers._

import java.awt.Shape
import java.awt.geom._
import net.liftweb.util.Helpers._
import net.liftweb.common.Logger

object QuizRenderer extends QuizRenderer

class QuizRenderer extends Logger {
  protected def makeBlankImage(width:Int,height:Int) = Stopwatch.time("SlideRenderer.makeBlankImage",{
    val blankImage = new BufferedImage(width,height,BufferedImage.TYPE_3BYTE_BGR)
    val g = blankImage.createGraphics.asInstanceOf[Graphics2D]
    g.setPaint(AWTColor.white)
    g.fill(new Rectangle(0,0,width,height))
    blankImage
  })

  protected def imageToByteArray(image:BufferedImage):Array[Byte] = Stopwatch.time("SlideRenderer.imageToByteArray",{
    try {
    val stream = new java.io.ByteArrayOutputStream
    ImageIO.write(image, "jpg", stream)
    stream.toByteArray
    } catch {
      case e:Exception => {
        error("couldn't serialize image",e)
        Array.empty[Byte]
      }
    }
  })
  protected val emptyImage:Image = new BufferedImage(1,1,BufferedImage.TYPE_4BYTE_ABGR)
  def toAwtColor(c:Color,overrideAlpha:Int = -1):AWTColor = {
    if (overrideAlpha < 0)
      new AWTColor(c.red,c.green,c.blue,c.alpha)
    else
      new AWTColor(c.red,c.green,c.blue,Math.max(0,Math.min(255,overrideAlpha)))
  }
  val awtBlack = new AWTColor(0,0,0,255)
  val columnSeparator = 3
  def renderQuiz(quiz:MeTLQuiz,quizResponses:List[MeTLQuizResponse],dimensions:RenderDescription):Array[Byte] = {
    val answersInColumns = quizResponses.groupBy(qr => qr.answerer).flatMap(qrl => qrl._2.sortBy(qr => qr.timestamp).reverse.headOption).groupBy(qr => qr.answer)

    val unscaledImage = new BufferedImage(dimensions.width,dimensions.height,BufferedImage.TYPE_3BYTE_BGR)
    val g = unscaledImage.createGraphics.asInstanceOf[Graphics2D]
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setPaint(AWTColor.white)
    g.fill(new Rectangle(0,0,dimensions.width,dimensions.height))

    val yMax = answersInColumns.map(_.length).max
    val xMax = quiz.options.length

    val graphSpaceY = dimensions.height - 20
    val graphBase = graphSpaceY
    val columnSpace = dimensions.width / xMax
    val columnWidth = columnSpace - columnSeparator

    val frc = g.getFontRenderContext()
    val font = new Font("Arial",Font.PLAIN,12)
    if (yMax > 0){
      var columnStartX = 0
      quiz.options.foreach(option => {
        val answerCount = answersInColumns.find(a => a._1 == option.name).map(_._2.toList.length).getOrElse(0)
        val height = graphSpaceY * (answerCount / yMax)
        val (x,y,w,h) = (columnStartX, graphSpaceY - height, columnWidth, height)
        g.setPaint(awtBlack)
        g.fill(new Rectangle(x,y,w,h))
        g.setPaint(toAwtColor(option.color))
        g.fill(new Rectangle(x + (columnSeparator / 2), y + (columnSeparator / 2), w - columnSeparator, h - columnSeparator))
        g.setPaint(awtBlack)
        val styledText = new AttributedString("%s: (%s)".format(option.name,answerCount))
        val layout = new TextLayout(styledText.getIterator(),frc)
        layout.draw(g, columnStartX + ((columnWidth / 2) - columnSeparator), dimensions.height - columnSeparator)

        println("rendering column: %s : (%s,%s,%s,%s)".format(option.name,x,y,w,h))
        columnStartX += columnSpace
      })
    }
    imageToByteArray(unscaledImage)
  }
}
