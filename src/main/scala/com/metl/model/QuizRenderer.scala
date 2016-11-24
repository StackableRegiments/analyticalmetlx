package com.metl.renderer

import com.metl.data._
import com.metl.utils._

import java.awt.{Color=>AWTColor,List=>AWTList,Point=>AWTPoint,Panel=>AwtPanel,LinearGradientPaint=>AwtLinearGradientPaint,_}
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
  protected val gradientPaintPot = {
    val start = new Point2D.Float(0, 0);
    val end = new Point2D.Float(100,0);
    val colorsAndDists = List(
      (new AWTColor(255,255,255,255),0.0f),
      (new AWTColor(220,237,200,255),0.25f),
      (new AWTColor(66,179,213,255),0.45f),
      (new AWTColor(26,35,126,255),0.75f),
      (new AWTColor(0,0,0,255),1.0f)
    ).toArray
    val colors = colorsAndDists.map(_._1).toArray
    val dist = colorsAndDists.map(_._2).toArray
    val p = new AwtLinearGradientPaint(start, end, dist, colors)
    val paintPot = new BufferedImage(100,1,BufferedImage.TYPE_INT_RGB)
    val g = paintPot.createGraphics()
    g.setPaint(p)
    g.fill(new Rectangle(0,0,100,1))
    val panel = new AwtPanel()
    panel.print(g)
    paintPot
  }
  protected def getColorFromGradient(position:Int,max:Int):AWTColor = {
    val x = ((100/max) * position)
    val rgb = gradientPaintPot.getRGB(x,0)
    val r = (rgb >> 16) & 0xFF
    val g = (rgb >> 8) & 0xFF
    val b = rgb & 0xFF
    trace("getting color from gradient: %s %s (%s,%s,%s)".format(position,max,r,g,b))
    new AWTColor(r,g,b,255)
  }
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
  def renderQuiz(quiz:MeTLQuiz,quizResponses:List[MeTLQuizResponse],dimensions:RenderDescription):Array[Byte] = {
    val answersInColumns = quizResponses.groupBy(qr => qr.answerer).flatMap(qrl => qrl._2.sortBy(qr => qr.timestamp).reverse.headOption).groupBy(qr => qr.answer)

    val unscaledImage = new BufferedImage(dimensions.width,dimensions.height,BufferedImage.TYPE_3BYTE_BGR)
    val g = unscaledImage.createGraphics.asInstanceOf[Graphics2D]
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setPaint(AWTColor.white)
    g.fill(new Rectangle(0,0,dimensions.width,dimensions.height))

    val yMax:Double = answersInColumns.map(_._2.toList.length).max
    val xMax = quiz.options.length

    val totalTextHeight:Int = dimensions.height / 6
    val textHeight:Int = totalTextHeight / 3
    val columnSeparator:Int = Math.max(textHeight * 0.1,3).toInt
    val graphSpaceY = dimensions.height - totalTextHeight 
    val graphBase = graphSpaceY
    val axisSpace = dimensions.width / 16
    val graphSpaceX = dimensions.width - axisSpace
    val textRow1 = graphSpaceY + textHeight
    val textRow2 = graphSpaceY + (textHeight * 2)
    val textRow3 = graphSpaceY + (textHeight * 3)
    val columnSpace = graphSpaceX / xMax
    val columnWidth = columnSpace - columnSeparator

    val frc = g.getFontRenderContext()
    val fontSize = (textHeight * 0.7).toInt
    val boldFont = new Font("Arial",Font.BOLD,fontSize)
    val font = new Font("Arial",Font.PLAIN,fontSize)
    val totalFinalResponses = answersInColumns.map(_._2.toList.length).sum
    if (yMax > 0){
      var columnStartX = axisSpace
      quiz.options.zipWithIndex.foreach(optionTup => {
        val option = optionTup._1
        val index = optionTup._2
        val answerCount:Double = answersInColumns.get(option.name).map(_.toList.length.toDouble).getOrElse(0.0)
        val height:Int = (graphSpaceY * (answerCount / yMax)).toInt
        val (x,y,w,h) = (columnStartX, graphSpaceY - height, columnWidth, height)
        //removing the black outline
        //g.setPaint(awtBlack)
        //g.fill(new Rectangle(x,y,w,h))
        //
        //g.setPaint(toAwtColor(option.color))
        //painting the colours with a cool blue gradient
        val columnColor = getColorFromGradient(index + 1, quiz.options.length + 2)
        g.setPaint(columnColor)
        g.fill(new Rectangle(x + (columnSeparator / 2), y + (columnSeparator / 2), w - columnSeparator, h - columnSeparator))
        g.setPaint(awtBlack)
        val answerPercentage = {
          val answerCountDouble = answerCount.toFloat
          val totalFinalResponsesDouble = totalFinalResponses.toFloat
          val result = (answerCountDouble / totalFinalResponsesDouble) * 100
          "%1.0f".format(result)
        }
        val textRow1Text = option.name
        val textRow1Length:Double = boldFont.getStringBounds(textRow1Text,frc).getWidth()
        val textRow1Offset:Int = ((columnSpace - textRow1Length) / 2).toInt
        val styledText = new AttributedString(textRow1Text)
        styledText.addAttribute(TextAttribute.FONT, boldFont)
        val layout = new TextLayout(styledText.getIterator(),frc)
        layout.draw(g, columnStartX + textRow1Offset, textRow1 - columnSeparator)
        val textRow2Text = "%s%%".format(answerPercentage)
        val textRow2Length:Double = font.getStringBounds(textRow2Text,frc).getWidth()
        val textRow2Offset:Int = ((columnSpace - textRow2Length) / 2).toInt
        val styledText2 = new AttributedString(textRow2Text)
        styledText2.addAttribute(TextAttribute.FONT, font)
        val layout2 = new TextLayout(styledText2.getIterator(),frc)
        layout2.draw(g, columnStartX + textRow2Offset, textRow2 - columnSeparator)
        val textRow3Text = "(%s)".format(answerCount.toInt)
        val textRow3Length:Double = font.getStringBounds(textRow3Text,frc).getWidth()
        val textRow3Offset:Int = ((columnSpace - textRow3Length) / 2).toInt
        val styledText3 = new AttributedString(textRow3Text)
        styledText3.addAttribute(TextAttribute.FONT, font)
        val layout3 = new TextLayout(styledText3.getIterator(),frc)
        layout3.draw(g, columnStartX + textRow3Offset, textRow3 - columnSeparator)

        trace("rendering column: %s : (%s,%s,%s,%s)".format(option.name,x,y,w,h))
        columnStartX += columnSpace
      })
    }
    imageToByteArray(unscaledImage)
  }
}
