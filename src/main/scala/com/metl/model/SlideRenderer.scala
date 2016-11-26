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

case class Dimensions(left:Double,top:Double,right:Double,bottom:Double,width:Double,height:Double)

class RenderDescription(val width:Int,val height:Int)

class SlideRenderer extends Logger {
  ///*
  protected val JAVA_DEFAULT_DPI = 72.0
  protected val WINDOWS_DEFAULT_DPI = 96.0
  //*/
  /*
  protected val JAVA_DEFAULT_DPI = 72.0
  protected val WINDOWS_DEFAULT_DPI = {
    try {
      java.awt.Toolkit.getDefaultToolkit().getScreenResolution()
    } catch {
      case e:Exception => {
        96.0
      }
    }
  } 
  */
 /*
  protected val WINDOWS_DEFAULT_DPI = 96.0
  protected val JAVA_DEFAULT_DPI = {
    try {
      java.awt.Toolkit.getDefaultToolkit().getScreenResolution()
    } catch {
      case e:Exception => {
        72.0
      }
    }
  } 
*/
  //protected val WINDOWS_DEFAULT_DPI = 96.0

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

  //We assume it came from windows, and that any headful dev env is Windows.
  protected def correctFontSizeForOsDpi(size:Double):Double = {
    /*
    if(java.awt.GraphicsEnvironment.isHeadless)
      Math.round(size * JAVA_DEFAULT_DPI / WINDOWS_DEFAULT_DPI)
    else size
    */
      //Math.round(size * JAVA_DEFAULT_DPI / WINDOWS_DEFAULT_DPI)
      //size
      size * (WINDOWS_DEFAULT_DPI / JAVA_DEFAULT_DPI)
  }

  def toAwtColor(c:Color,overrideAlpha:Int = -1):AWTColor = {
    if (overrideAlpha < 0)
      new AWTColor(c.red,c.green,c.blue,c.alpha)
    else
      new AWTColor(c.red,c.green,c.blue,Math.max(0,Math.min(255,overrideAlpha)))
  }
  protected val emptyImage:Image = new BufferedImage(1,1,BufferedImage.TYPE_4BYTE_ABGR)

  protected val imageCache:scala.collection.mutable.HashMap[String,Image] = scala.collection.mutable.HashMap.empty[String,Image]
  protected def getImageFor(metlImage:MeTLImage):Image = Stopwatch.time("SlideRenderer.getImageFor",{
    imageCache.get(metlImage.identity).getOrElse({
      metlImage.imageBytes.map(ib => {
        val stream = new ByteArrayInputStream(ib)
        val image = ImageIO.read(stream).asInstanceOf[Image]
        stream.close()
        imageCache.update(metlImage.identity,image)
        image
      }).openOr(emptyImage)
    })
  })
  protected val defaultObserver:Graphics2D = {
    val tempImage = new BufferedImage(1,1,BufferedImage.TYPE_3BYTE_BGR)
    tempImage.createGraphics.asInstanceOf[Graphics2D]
  }
  def measureImage(metlImage:MeTLImage):Dimensions = measureImage(metlImage,defaultObserver)
  protected def measureImage(metlImage:MeTLImage,g:Graphics2D):Dimensions = Stopwatch.time("SlideRenderer.measureImage",{
    val x = metlImage.x
    val y = metlImage.y
    val errorSize = Dimensions(x,y,x,y,0.0,0.0)
    try {
      val image:Image = getImageFor(metlImage)
        (metlImage.height,metlImage.width) match {
        case (h:Double,w:Double) if (h.isNaN || w.isNaN) => {
          val imageObserver = new Canvas(g.getDeviceConfiguration)
          val internalHeight = h match {
            case d:Double if (d.isNaN) => {
              val observedHeight = image.getHeight(imageObserver)
              observedHeight * metlImage.scaleFactorY
            }
            case d:Double => d
          }
          val internalWidth = w match {
            case d:Double if (d.isNaN) => {
              val observedWidth = image.getWidth(imageObserver)
              observedWidth * metlImage.scaleFactorX
            }
            case d:Double => d
          }
          Dimensions(x,y,x+internalWidth,y+internalHeight,internalWidth,internalHeight)
        }
        case (h:Double,w:Double) => {
          Dimensions(x,y,x+w,y+h,w,h)
        }
        case other => {
          errorSize
        }
      }
    } catch {
      case e:Throwable => {
        error("failed to measure image: %s",e)
        errorSize
      }
    }
  })
  protected def renderImage(metlImage:MeTLImage,g:Graphics2D):Unit = Stopwatch.time("SlideRenderer.renderImage",{
    try {
      val image:Image = getImageFor(metlImage)
      val dimensions = measureImage(metlImage,g)
      val (finalWidth,finalHeight) = (dimensions.width,dimensions.height)
      image match {
        case i:Image if (finalHeight == 0.0 || finalWidth == 0.0) => {
        }
        case i:Image => {
          g.drawImage(image,metlImage.left.toInt,metlImage.top.toInt,finalWidth.toInt,finalHeight.toInt,null)
        }
        case _ => {}
      }
    } catch {
      case e:Throwable => {
        error("failed to render image: %s",e)
      }
    }
  })

  protected def renderVideo(metlVideo:MeTLVideo,g:Graphics2D):Unit = Stopwatch.time("SlideRenderer.renderVideo",{
    try {
      g.setPaint(toAwtColor(Color(255,0,0,0)))
      g.drawRect(metlVideo.x.toInt,metlVideo.y.toInt,metlVideo.width.toInt,metlVideo.height.toInt)
      g.fillRect(metlVideo.x.toInt,metlVideo.y.toInt,metlVideo.width.toInt,metlVideo.height.toInt)
    } catch {
      case e:Throwable => {
        error("failed to render video: %s",e)
      }
    }
  })

  protected def renderInk(metlInk:MeTLInk,g:Graphics2D) = Stopwatch.time("SlideRenderer.renderInk",{
    try {
      val HIGHLIGHTER_ALPHA  = 55
      val PRESSURE = 0.22
      val color = metlInk.isHighlighter match {
        case true => toAwtColor(metlInk.color, HIGHLIGHTER_ALPHA)
        case false => toAwtColor(metlInk.color)
      }
      g.setPaint(color)
      g.fill(new Stroke(metlInk.points,metlInk.thickness))
    } catch {
      case e:Throwable => {
        error("failed to render ink: %s",e)
      }
    }
  })

  protected def measureText(metlText:MeTLMultiWordText,g:Graphics2D):Dimensions = Stopwatch.time("SlideRenderer.measureMultiWordText",{
    val (l,r,t,b) = measureTextLines(metlText,g).foldLeft((metlText.x,metlText.y,metlText.x,metlText.y))((internalAcc,internalItem) => {
      val newLeft = Math.min(internalAcc._1,internalItem.x)
      val newRight = Math.max(internalAcc._2,internalItem.x+internalItem.width)
      val newTop = Math.min(internalAcc._3,internalItem.y)
      val newBottom = Math.max(internalAcc._4,internalItem.y+internalItem.height)
      (newLeft,newRight,newTop,newBottom)
    })
    Dimensions(l,t,r,b,r-l,b-t)
  })
  def measureWord(word:MeTLTextWord,g:Graphics2D,frc:FontRenderContext) = {
    val weighted = if(word.bold) Font.BOLD else Font.PLAIN
    val italicised = if(word.italic) weighted + Font.ITALIC else weighted
    val adjustedSize = correctFontSizeForOsDpi(word.size)
    //val adjustedSize = word.size
    val baseFont = new Font(word.font, italicised, adjustedSize.toInt).deriveFont(adjustedSize.floatValue)
    val metrics = g.getFontMetrics(baseFont)
    var blankLineHeight:Float = metrics.getHeight.floatValue
    val styledText = new AttributedString(word.text)
    styledText.addAttribute(TextAttribute.FONT,baseFont)
    if(word.underline){
      styledText.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, 0, word.text.length)
    }
    /*
    if(run.isStrikethrough)
      styledText.addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON, runOffset, runLength)
*/
    val textLayout = new TextLayout(styledText.getIterator, frc)
    val bounds = textLayout.getBounds
    PreparedTextRun(word.text,textLayout,word.color,0,0,bounds.getWidth.floatValue,bounds.getHeight.floatValue,metrics)
  }
  protected val textCache = scala.collection.mutable.HashMap[Tuple6[Double,Double,Double,Double,Double,Seq[MeTLTextWord]],List[PreparedTextLine]]()
  protected def measureTextLines(metlText:MeTLMultiWordText,g:Graphics2D):List[PreparedTextLine] = Stopwatch.time("SlideRenderer.measureMultiWordTextLines",{
    val identifier = (metlText.x, metlText.y, metlText.width, metlText.height, metlText.requestedWidth,metlText.words)
    textCache.get(identifier).getOrElse({
      val newRuns = actuallyMeasureTextLines(metlText,g)
      textCache += ((identifier,newRuns))
      newRuns
    })
  })
  protected def actuallyMeasureTextLines(metlText:MeTLMultiWordText,g:Graphics2D):List[PreparedTextLine] = Stopwatch.time("SlideRenderer.actuallyMeasureMultiWordTextLines",{
    val originX = metlText.x.floatValue
    val limit = metlText.x + metlText.width
    val frc = g.getFontRenderContext()
    val lexicalWords = metlText.words.flatMap(multiWord => multiWord.text.split(" ")
      .flatMap(lexicalWord => lexicalWord.split("\n").toList
        .zip(Stream.continually("\n"))
        .flatMap{case (a,b) => List(a,b)}
        .filterNot(_.isEmpty)
        .map(fragment => multiWord.copy(text = fragment))))

    val _lateralRuns = lexicalWords.foldLeft((List.empty[List[PreparedTextRun]],List.empty[PreparedTextRun],originX)){
      case ((complete,partial,extent),word) => {
        val textRun = measureWord(word,g,frc)
        val space = measureWord(word.copy(text = "i"),g,frc).width //textRun.metrics.stringWidth(" ") // many fontmetrics use "i" as the width indicator
        if(extent + textRun.width > limit){
          (complete ::: List(partial), List(textRun.copy(x = originX)), originX + textRun.width + space)
        }
        else {
          (complete, partial ::: List(textRun.copy(x = extent.floatValue)), extent + textRun.width + space)
        }
      }
    }
    val lateralRuns = _lateralRuns._1 ::: List(_lateralRuns._2);
    val verticalRuns = lateralRuns.foldLeft((List.empty[PreparedTextLine],metlText.y)){
      case ((lines,yOffset), runs) => {
        val maxRun = runs.sortBy(_.height).reverse.headOption
        val runHeight = maxRun.map(_.height).getOrElse(0f)
        val lineMetrics = maxRun.map(r => r.metrics.getLineMetrics(r.text,g))
        val leading =  lineMetrics.map(_.getLeading()).getOrElse(0f)
        val descent =  lineMetrics.map(_.getDescent()).getOrElse(0f)
        val ascent =  lineMetrics.map(_.getAscent()).getOrElse(0f)
        //(lines ::: List(PreparedTextLine(runs.map(_.copy(y = yOffset.floatValue + ascent)),originX,yOffset.floatValue,runs.map(_.width).sum, runHeight)),yOffset + descent + leading)
        //(lines ::: List(PreparedTextLine(runs.map(_.copy(y = yOffset.floatValue)),originX,yOffset.floatValue, runs.map(_.width).sum, runHeight)),yOffset + runHeight + leading)
        (lines ::: List(PreparedTextLine(runs.map(_.copy(y = yOffset.floatValue)),originX,yOffset.floatValue,runs.reverse.headOption.map(r => r.x + r.width - originX).getOrElse(runs.map(_.width).sum), runHeight)),yOffset + runHeight + leading)
      }
    }
    verticalRuns._1
  })

  case class PreparedTextLine(textRuns:List[PreparedTextRun],x:Float,y:Float,width:Float,height:Float)
  case class PreparedTextRun(text:String,layout:TextLayout,color:Color,x:Float,y:Float,width:Float,height:Float,metrics:FontMetrics)
  def measureText(metlText:MeTLText):Dimensions = measureText(metlText,defaultObserver)
  def measureText(metlText:MeTLMultiWordText):Dimensions = measureText(metlText,defaultObserver)
  protected def measureText(metlText:MeTLText,g:Graphics2D):Dimensions = Stopwatch.time("SlideRenderer.measureText",{
    val (l,r,t,b) = measureTextLines(metlText,g).foldLeft((metlText.x,metlText.y,metlText.x,metlText.y))((internalAcc,internalItem) => {
      val newLeft = Math.min(internalAcc._1,internalItem.x)
      val newRight = Math.max(internalAcc._2,internalItem.x+internalItem.width)
      val newTop = Math.min(internalAcc._3,internalItem.y)
      val newBottom = Math.max(internalAcc._4,internalItem.y+internalItem.height)
      (newLeft,newRight,newTop,newBottom)
    })
    Dimensions(l,t,r,b,r-l,b-t)
  })
  import scala.xml._
  protected def isRichText(metlText:MeTLText):Boolean = {
    try {
      XML.loadString(metlText.text).namespace.trim.toLowerCase == "http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    } catch {
      case e:Exception => false
    }
  }
  protected def measureTextLines(metlText:MeTLText,g:Graphics2D):List[PreparedTextLine] = Stopwatch.time("SlideRenderer.measureTextLines",{
    if (isRichText(metlText)){
      trace("richText: %s".format(metlText))
      measureRichTextLines(metlText,g)
    } else {
      trace("poorText: %s".format(metlText))
      measurePoorTextLines(metlText,g)
    }
  })
  case class TextRunDefinition(text:String,family:String,size:Double,color:Color,isUnderline:Boolean,isStrikethrough:Boolean,isBold:Boolean,isItalic:Boolean)
  protected def measureRichTextLines(metlText:MeTLText,g:Graphics2D):List[PreparedTextLine] = Stopwatch.time("SlideRenderer.measureRichTextLines",{
    metlText.text match {
      case t:String if (t.length > 0) => {
        val frc = g.getFontRenderContext()
        val xml = XML.loadString(metlText.text)
        val lineSeparators = List("\r\n","\r","\n","\\v")
        val runsByLine:List[List[TextRunDefinition]] = {
          val sectionFontFamily = xml.attribute("FontFamily").headOption.map(_.text).getOrElse(metlText.family)
          val sectionFontSize = xml.attribute("FontSize").headOption.map(_.text.toDouble).getOrElse(metlText.size)
          val sectionFontColor = xml.attribute("Foreground").headOption.map(c => ColorConverter.fromARGBHexString(c.text)).getOrElse(metlText.color)
          val sectionFontIsBold = xml.attribute("FontWeight").headOption.map(c => c.text.toLowerCase.contains("bold")).getOrElse(metlText.weight == "Bold")
          val sectionFontIsItalic = xml.attribute("FontStyle").headOption.map(c => c.text.toLowerCase.contains("italic")).getOrElse(metlText.style == "Italic")
          val sectionFontIsUnderline = xml.attribute("Decoration").headOption.map(c => c.text.toLowerCase.contains("underline")).getOrElse(metlText.decoration.contains("italic"))
          val paragraphs = (xml \\ "Paragraph")
          paragraphs.flatMap(paragraph => {
            val paraFontFamily = paragraph.attribute("FontFamily").headOption.map(_.text).getOrElse(sectionFontFamily)
            val paraFontSize = paragraph.attribute("FontSize").headOption.map(_.text.toDouble).getOrElse(sectionFontSize)
            val paraFontColor = paragraph.attribute("Foreground").headOption.map(c => ColorConverter.fromARGBHexString(c.text)).getOrElse(sectionFontColor)
            val paraFontIsBold = paragraph.attribute("FontWeight").headOption.map(c => c.text.toLowerCase.contains("bold")).getOrElse(sectionFontIsBold)
            val paraFontIsItalic = paragraph.attribute("FontStyle").headOption.map(c => c.text.toLowerCase.contains("italic")).getOrElse(sectionFontIsItalic)
            val paraFontIsUnderline = paragraph.attribute("Decoration").headOption.map(c => c.text.toLowerCase.contains("underline")).getOrElse(sectionFontIsUnderline)
              (paragraph \\ "Run").foldLeft(List.empty[List[TextRunDefinition]])((acc,item) => item match {
                case e:Elem => {
                  val lines:List[String] = lineSeparators.foldLeft(List(e.text))((a,i) => a.flatMap(_.split(i).toList)).toList
                  val runs:List[TextRunDefinition] = lines.flatMap(runText => {
                    val runFontFamily = e.attribute("FontFamily").headOption.map(_.text).getOrElse(paraFontFamily)
                    val runFontSize = e.attribute("FontSize").headOption.map(_.text.toDouble).getOrElse(paraFontSize)
                    val runFontColor = e.attribute("Foreground").headOption.map(c => ColorConverter.fromARGBHexString(c.text)).getOrElse(paraFontColor)
                    val runFontIsBold = e.attribute("FontWeight").headOption.map(c => c.text.toLowerCase.contains("bold")).getOrElse(paraFontIsBold)
                    val runFontIsItalic = e.attribute("FontStyle").headOption.map(c => c.text.toLowerCase.contains("italic")).getOrElse(paraFontIsItalic)
                    val runFontIsUnderline = e.attribute("Decoration").headOption.map(c => c.text.toLowerCase.contains("underline")).getOrElse(paraFontIsUnderline)
                    val trds = e.text.foldLeft((List.empty[String],""))((a,i) => {
                      (a._2 + i) match {
                        case s if s.endsWith("\r\n") => (a._1 ::: List(s.reverse.drop(2).reverse),"")
                        case s if s.endsWith("\n") => (a._1 ::: List(s.reverse.drop(1).reverse),"")
                        case s if s.endsWith("\r") => (a._1 ::: List(s.reverse.drop(1).reverse),"")
                        case s if s.endsWith("\\v") => (a._1 ::: List(s.reverse.drop(2).reverse),"")
                        case s => (a._1,s)
                      }
                    })
                      (trds._1 ::: List(trds._2)).map(s => TextRunDefinition(s,runFontFamily,runFontSize,runFontColor,false,false,false,false))
                    //TextRunDefinition(runText,runFontFamily,runFontSize,runFontColor,false,false,false,false)
                  }).toList
                  val startOfList:List[List[TextRunDefinition]] = (lineSeparators.foldLeft(false)((a,i) => a || e.text.startsWith(i)) match {
                    case true => acc ::: List(runs.headOption.toList)
                    case false => {
                      val part1:List[List[TextRunDefinition]] = acc.reverse.drop(1).reverse.toList
                      val part2:List[List[TextRunDefinition]] = List((acc.reverse.headOption.getOrElse(Nil) ::: runs.headOption.toList).toList)
                      part1 ::: part2
                    }
                  })
                  val endOfList:List[List[TextRunDefinition]] = runs.drop(1).map(r => List(r)).toList ::: Some(List(List.empty[TextRunDefinition])).filter(_i => lineSeparators.foldLeft(false)((a,i) => a || e.text.endsWith(i))).getOrElse(List.empty[List[TextRunDefinition]])
                  startOfList ::: endOfList
                }
                case other => {
                  acc
                }
              })
          }).toList}

        trace("runsByLine: %s".format(runsByLine))

        val baseFont:Font = new Font(metlText.family, metlText.weight match{
          case "Normal" => metlText.style match {
            case s:String if s.contains("Italic") => Font.ITALIC
            case _ => Font.PLAIN
          }
          case "Bold" => metlText.style match {
            case s:String if s.contains("Italic") => Font.BOLD + Font.ITALIC
            case _ => Font.BOLD
          }
          case _ => {
            "renderText: I don't know what to do with font weight '%s'".format(metlText.weight)
            Font.PLAIN
          }
        }, correctFontSizeForOsDpi(metlText.size).toInt)
        var blankLineHeight:Float = g.getFontMetrics(baseFont).getHeight
        def drawLines(preparedLines:List[PreparedTextLine],listOfRuns:List[List[TextRunDefinition]],y:Float):List[PreparedTextLine] ={
          if (listOfRuns.length > 0) {
            val runs:List[TextRunDefinition] = listOfRuns.head
            if (runs.map(_.text.length).sum == 0){
              trace("empty line, so dropping it by blankLineHeight and fetching the next: %s".format(blankLineHeight))
              drawLines(preparedLines,listOfRuns.drop(1),y + blankLineHeight)
            } else if (runs.length > 0){
              val (nextPreparedLines,nextY) = metlText.width match {
                case w:Double if (w.isNaN || w < 0) => {
                  val innerNextY = y + blankLineHeight
                  var offsetX:Float = 0.0f
                  var runOffset:Int = 0
                  val preparedTextRuns:List[PreparedTextRun] = runs.filterNot(_.text.length == 0).foldLeft(List.empty[PreparedTextRun])((acc,run) => {
                    var runLength = run.text.length
                    val styledText = new AttributedString(run.text)
                    val font = new Font(run.family, List(
                      Some(Font.ITALIC).filter(_i => run.isItalic),
                      Some(Font.BOLD).filter(_i => run.isBold)
                    ).flatten.sum, correctFontSizeForOsDpi(run.size).toInt)
                    styledText.addAttribute(TextAttribute.FONT,font,runOffset,runLength)
                    if(run.isUnderline)
                      styledText.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, runOffset, runLength)
                    if(run.isStrikethrough)
                      styledText.addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON, runOffset, runLength)
                    blankLineHeight = Math.max(blankLineHeight, g.getFontMetrics(font).getHeight)
                    runOffset += runLength
                    val textLayout:TextLayout = new TextLayout(styledText.getIterator,frc)
                    val innerNextY:Float = y+blankLineHeight
                    val thisX:Float = offsetX
                    offsetX += textLayout.getBounds.getWidth.toFloat
                    acc ::: List(PreparedTextRun(runs.map(_.text).mkString(""),textLayout,metlText.color,metlText.x.toFloat + thisX,y,textLayout.getBounds.getWidth.toFloat,textLayout.getBounds.getHeight.toFloat,g.getFontMetrics(font)))
                  })
                  val newLine = PreparedTextLine(preparedTextRuns,metlText.x.toFloat,y,preparedTextRuns.map(_.width).sum,preparedTextRuns.map(_.height).max)
                  (List(newLine),y + blankLineHeight)
                }
                case _ =>{
                  var runOffsetX:Double = 0.0
                  def renderLine(internalPreparedLines:List[PreparedTextLine],textRuns:List[TextRunDefinition],lineY:Float,freshLine:Boolean = true,wraps:Int = 0):Tuple2[List[PreparedTextLine],Float] = {
                    trace("renderLine(%s,%s,%s,%s,%s)".format(internalPreparedLines,textRuns,lineY,freshLine,wraps))
                    if (freshLine)
                      runOffsetX = 0
                    textRuns.filterNot(_.text.length == 0).headOption.map(run => {
                      val styledText = new AttributedString(run.text)
                      val font = new Font(run.family, List(
                        Some(Font.ITALIC).filter(_i => run.isItalic),
                        Some(Font.BOLD).filter(_i => run.isBold)
                      ).flatten.sum, correctFontSizeForOsDpi(run.size).toInt)
                      styledText.addAttribute(TextAttribute.FONT,font)
                      if(run.isUnderline)
                        styledText.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)
                      if(run.isStrikethrough)
                        styledText.addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON)
                      val styledTextIterator = styledText.getIterator()
                      val measurer = new LineBreakMeasurer(styledTextIterator, frc)

                      val textLayout = measurer.nextLayout((metlText.width - runOffsetX).toFloat)
                      if (textLayout != null){
                        val currentY:Float = lineY + textLayout.getAscent()
                        val totalHeight:Float = textLayout.getDescent + textLayout.getLeading
                        val preparedTextRun:PreparedTextRun = PreparedTextRun(run.text.take(measurer.getPosition()),textLayout,run.color,(metlText.x + runOffsetX).toFloat,currentY,textLayout.getBounds.getWidth.toFloat,textLayout.getBounds.getHeight.toFloat,g.getFontMetrics(font))
                        runOffsetX += textLayout.getBounds.getWidth
                        val newLine = PreparedTextLine(List(preparedTextRun),preparedTextRun.x,preparedTextRun.y,preparedTextRun.width,preparedTextRun.height)
                        val newLines:List[PreparedTextLine] = freshLine match {
                          case true => internalPreparedLines ::: List(newLine)
                          case false => internalPreparedLines.reverse.drop(1).reverse ::: {
                            internalPreparedLines.reverse.headOption.map(oldLine => {
                              val allRuns:List[PreparedTextRun] = oldLine.textRuns ::: List(preparedTextRun)
                              val newY:Float = allRuns.map(_.y).max
                              val lineHeight:Float = newY - oldLine.y
                              val lineLength:Float = allRuns.map(_.width).sum
                              List(PreparedTextLine((oldLine.textRuns ::: List(preparedTextRun)).map(_.copy(y = newY)).toList,metlText.x.toFloat,newY,lineLength.toFloat,lineHeight.toFloat))
                            }).getOrElse(List(newLine))
                          }
                        }
                        if (measurer.getPosition() < run.text.length){ //the measurer didn't reach the end of the run, so there's more of this run for the next line
                          val originalRun = run.copy(text = run.text.take(measurer.getPosition()))
                          val newRun = run.copy(text = run.text.drop(measurer.getPosition()))
                          val newRuns = newRun :: textRuns.drop(1)
                          trace("textRun split across lines\r\noriginalRun: %s\r\nnewRun: %s".format(originalRun,newRun))
                          renderLine(newLines,newRuns,currentY + totalHeight, true, wraps + 1)
                        } else {
                          trace("finished the run, getting another run")
                          renderLine(newLines,textRuns.drop(1),lineY, false, wraps + 1)
                        }
                      } else {
                        if (freshLine){
                          trace("run didn't fit at all on the line, so retrying it on a new line, dropping by blankHeight: %s".format(blankLineHeight))
                          renderLine(internalPreparedLines,textRuns,lineY + blankLineHeight,true,wraps) // I think this meant no layout fit, so we have to drop a line and try to layout again.
                        } else {
                          trace("run didn't measure, so dropping it and continuing on this line")
                          renderLine(internalPreparedLines,textRuns.drop(1),lineY,false,wraps)
                        }
                      }
                    }).getOrElse((internalPreparedLines,lineY + blankLineHeight))
                  }
                  val inter = renderLine(List.empty[PreparedTextLine],runs,y)
                  inter
                }
              }
              drawLines(nextPreparedLines ::: preparedLines,listOfRuns.drop(1),nextY)
            }
            else {
              trace("empty line, so dropping it by blankLineHeight and fetching the next: %s".format(blankLineHeight))
              drawLines(preparedLines,listOfRuns.drop(1),y + blankLineHeight)
            }
          }
          else {
            trace("finished with the following list of prepared lines: \r\n%s".format(preparedLines))
            preparedLines
          }
        }
        drawLines(List.empty[PreparedTextLine],runsByLine,metlText.y.toFloat)
      }
      case _ => List.empty[PreparedTextLine]
    }
  })


  protected def measurePoorTextLines(metlText:MeTLText,g:Graphics2D):List[PreparedTextLine] = Stopwatch.time("SlideRenderer.measurePoorTextLines",{
    val frc = g.getFontRenderContext()

    val font = new Font(metlText.family, metlText.weight match{
      case "Normal" => metlText.style match {
        case s:String if s.contains("Italic") => Font.ITALIC
        case _ => Font.PLAIN
      }
      case "Bold" => metlText.style match {
        case s:String if s.contains("Italic") => Font.BOLD + Font.ITALIC
        case _ => Font.BOLD
      }
      case _ => {
        "renderText: I don't know what to do with font weight '%s'".format(metlText.weight)
        Font.PLAIN
      }
    }, correctFontSizeForOsDpi(metlText.size).toInt)

    val metrics = g.getFontMetrics(font)
    val blankLineHeight = metrics.getHeight

    metlText.text match {
      case t:String if (t.length > 0) => {
        def drawLines(preparedLines:List[PreparedTextLine],lines:List[String],y:Float):List[PreparedTextLine] ={
          if (lines.length > 0) {
            val line = lines.head
            if (line.length > 0){
              val styledText = new AttributedString(line)

              styledText.addAttribute(TextAttribute.FONT, font)
              if(metlText.decoration.contains("Underline"))
                styledText.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, 0, line.length)
              if(metlText.decoration.contains("Strikethrough"))
                styledText.addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON, 0, line.length)
              val (nextPreparedLines,nextY) = metlText.width match{
                case w:Double if (w.isNaN || w < 0) => {
                  val textLayout = new TextLayout(styledText.getIterator,frc)
                  val innerNextY = y+blankLineHeight
                  val newLine = PreparedTextLine(List(PreparedTextRun(line,textLayout,metlText.color,metlText.x.toFloat,innerNextY,textLayout.getBounds.getWidth.toFloat,textLayout.getBounds.getHeight.toFloat,g.getFontMetrics(font))),metlText.x.toFloat,innerNextY,textLayout.getBounds.getWidth.toFloat,blankLineHeight)
                  (List(newLine),innerNextY)
                }
                case _ =>{
                  val styledTextIterator = styledText.getIterator()
                  val measurer = new LineBreakMeasurer(styledTextIterator, frc)
                  def renderLine(internalPreparedLines:List[PreparedTextLine],lineY:Float,wraps:Int = 0):Tuple2[List[PreparedTextLine],Float] = {
                    if (measurer.getPosition() < t.length()){ //this is a check whether we've run out of string
                      val textLayout = measurer.nextLayout(metlText.width.toFloat)
                      if (textLayout != null){
                        val currentY = lineY + textLayout.getAscent()
                        val totalHeight = textLayout.getDescent + textLayout.getLeading

                        val newLines = PreparedTextLine(List(PreparedTextRun(line,textLayout,metlText.color,metlText.x.toFloat,currentY,textLayout.getBounds.getWidth.toFloat,textLayout.getBounds.getHeight.toFloat,g.getFontMetrics(font))),metlText.x.toFloat,currentY,textLayout.getBounds.getWidth.toFloat,totalHeight) :: internalPreparedLines
                        renderLine(newLines,currentY+totalHeight, wraps + 1)
                      }
                      else (internalPreparedLines,lineY + blankLineHeight)
                    }
                    else (internalPreparedLines,lineY + blankLineHeight)
                  }
                  val inter = renderLine(List.empty[PreparedTextLine],y)
                  inter
                }
              }
              drawLines(nextPreparedLines ::: preparedLines,lines.drop(1),nextY)
            }
            else drawLines(preparedLines,lines.drop(1),y + blankLineHeight)
          }
          else
            preparedLines
        }
        drawLines(List.empty[PreparedTextLine],metlText.text.split("\n").toList,metlText.y.toFloat)
      }
      case _ => List.empty[PreparedTextLine]
    }
  })

  protected def renderText(lines:List[PreparedTextLine],g:Graphics2D) = Stopwatch.time("SlideRenderer.renderText",{
    lines.foreach(line => {
      line.textRuns.foreach(run => {
        try {
          g.setPaint(toAwtColor(run.color))
          run.layout.draw(g,run.x,run.y)
        } catch {
          case e:Throwable => {
            error("failed to render text: %s",e)
          }
        }
      })
    })
  })

  protected def renderMultiWordText(text:MeTLMultiWordText,g:Graphics2D) = Stopwatch.time("SlideRenderer.renderMultiWordText",{
    renderText(measureTextLines(text,g),g)
  })

  protected val ratioConst = 0.75

  protected def filterAccordingToTarget[T](target:String,mccl:List[T]):List[T] = mccl.filter(mcc => {
    mcc match {
      case m:MeTLCanvasContent => m.target.trim.toLowerCase == target.trim.toLowerCase
      case _ => false
    }
  }).toList
  def renderMultiple(h:History,requestedSizes:List[RenderDescription],target:String= "presentationSpace"):Map[RenderDescription,Array[Byte]] = Stopwatch.time("SlideRenderer.renderMultiple",{
    h.shouldRender match {
      case true => {
        val (texts,highlighters,inks,images,multiWordTexts,_videos) = h.getRenderableGrouped
        val dimensions = measureItems(h,texts,highlighters,inks,images,multiWordTexts,target)
        Map(requestedSizes.map(rs => {
          val width = rs.width
          val height = rs.height
          (rs,renderImage(h,dimensions,width,height,target))
        }):_*)
      }
      case false => {
        debug("renderMultiple(%s,%s,%s) didn't render because h.shouldRender returned false".format(h,requestedSizes,target))
        Map(requestedSizes.map(rs => {
          val width = rs.width
          val height = rs.height
          (rs,imageToByteArray(makeBlankImage(width,height)))
        }):_*)
      }
    }
  })
  def measureHistory(h:History, target:String = "presentationSpace"):Dimensions = Stopwatch.time("SlideRenderer.measureHistory",{
    h.shouldRender match {
      case true => {
        val (texts,highlighters,inks,images,multiWordTexts,_videos) = h.getRenderableGrouped
        measureItems(h,texts,highlighters,inks,images,multiWordTexts)
      }
      case false => Dimensions(0.0,0.0,0.0,0.0,0.0,0.0)
    }
  })
  def measureItems(h:History,texts:List[MeTLText],highlighters:List[MeTLInk],inks:List[MeTLInk],images:List[MeTLImage], multiWordTexts:List[MeTLMultiWordText],target:String = "presentationSpace"):Dimensions = Stopwatch.time("SlideRenderer.measureItems",{
    val nativeScaleTextBoxes = filterAccordingToTarget[MeTLText](target,texts).map(t => measureText(t)) :::
    filterAccordingToTarget[MeTLMultiWordText](target,multiWordTexts).map(t => measureText(t))
    val td = nativeScaleTextBoxes.foldLeft(Dimensions(h.getLeft,h.getTop,h.getRight,h.getBottom,0.0,0.0))((acc,item) => {
      val newLeft = Math.min(acc.left,item.left)
      val newTop = Math.min(acc.top,item.top)
      val newRight = Math.max(acc.right,item.right)
      val newBottom = Math.max(acc.bottom,item.bottom)
      Dimensions(newLeft,newTop,newRight,newBottom,0.0,0.0)
    })
    Dimensions(td.left,td.top,td.right,td.bottom,td.right - td.left,td.bottom - td.top)
  })
  def renderImage(h:History,historyDimensions:Dimensions,width:Int,height:Int,target:String):Array[Byte] = Stopwatch.time("SlideRenderer.renderImage",{
    val contentWidth = historyDimensions.width
    val contentHeight = historyDimensions.height
    val contentXOffset = historyDimensions.left * -1
    val contentYOffset = historyDimensions.top * -1
    val historyRatio = tryo(contentHeight/contentWidth).openOr(ratioConst)

    val (renderWidth,renderHeight,scaleFactor) = (historyRatio >= ratioConst) match {
      case true => {
        val initialWidth = Math.max(1.0,width)
        var initialHeight = initialWidth*historyRatio
        val (renderWidth,renderHeight) =
          (initialHeight > height) match {
            case true => (initialWidth*(height/initialHeight),height)
            case false => (initialWidth,initialHeight)
          }
        (renderWidth,renderHeight,renderWidth/contentWidth)
      }
      case false => {
        val initialHeight = Math.max(1.0,height)
        var initialWidth = initialHeight/historyRatio
        val (renderWidth,renderHeight) =
          (initialWidth > width) match {
            case true => (width,initialHeight*(width/initialWidth))
            case false => (initialWidth,initialHeight)
          }
        (renderWidth,renderHeight,renderHeight/contentHeight)
      }
    }
    val unscaledImage = new BufferedImage(width,height,BufferedImage.TYPE_3BYTE_BGR)
    val g = unscaledImage.createGraphics.asInstanceOf[Graphics2D]
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setPaint(AWTColor.white)
    g.fill(new Rectangle(0,0,width,height))
    val scaleApplier = scaleFactor
    val scaledHistory = (scaleFactor != h.xScale || scaleFactor != h.yScale || h.xOffset != 0 || h.yOffset != 0) match {
      case true => {
        h.adjustToVisual(contentXOffset,contentYOffset,scaleApplier,scaleApplier)
      }
      case false => h
    }
    def sort[A <: MeTLStanza](m:List[A]):List[A] = m.sortWith((a,b) => a.timestamp < b.timestamp)
    val (scaledTexts,scaledHighlighters,scaledInks,scaledImages,scaledMultiWordTexts,scaledVideos) = scaledHistory.getRenderableGrouped
    filterAccordingToTarget[MeTLImage](target,sort(scaledImages)).foreach(img => renderImage(img,g))
    filterAccordingToTarget[MeTLVideo](target,sort(scaledVideos)).foreach(img => renderVideo(img,g))
    filterAccordingToTarget[MeTLInk](target,sort(scaledHighlighters)).foreach(renderInk(_,g))
    filterAccordingToTarget[MeTLText](target,sort(scaledTexts)).foreach(t => renderText(measureTextLines(t,g),g))
    filterAccordingToTarget[MeTLMultiWordText](target,sort(scaledMultiWordTexts)).foreach(t => renderMultiWordText(t,g))
    filterAccordingToTarget[MeTLInk](target,sort(scaledInks)).foreach(renderInk(_,g))
    imageToByteArray(unscaledImage)
  })
  def render(h:History,intWidth:Int,intHeight:Int,target:String = "presentationSpace"):Array[Byte] = Stopwatch.time("SlideRenderer.render",{
    val renderDesc = new RenderDescription(intWidth,intHeight)
    render(h,renderDesc,target)
  })
  def render(h:History,renderDesc:RenderDescription,target:String):Array[Byte] = Stopwatch.time("SlideRenderer.render",{
    renderMultiple(h,List(renderDesc),target).get(renderDesc).getOrElse(Array.empty[Byte])
  })
}

case class Vector2(x:Double,y:Double) {
  def +(v:Vector2) = new Vector2(x+v.x,y+v.y)
  def -(v:Vector2) = new Vector2(x-v.x,y-v.y)
  def *(n:Double) = new Vector2(x*n,y*n)

  def length = Math.sqrt(x*x+y*y)
  def normalized ={
    val l = 1.0/length
    new Vector2(x*l,y*l)
  }

  def leftRot90 = new Vector2(y,-x)
  def rightRot90 = new Vector2(-y,x)

  override def toString = "[%f,%f]".format(x,y)
}

class Stroke(points:List[Point],thickness:Double) extends Path2D.Double {
  makeShape

  protected def offsetAt(point:Point) = point.thickness/256*thickness

  protected def leftPoint(start:Vector2,end:Vector2,dist:Double) ={
    val actual = end-start
    val perp = actual.leftRot90
    val norm = perp.normalized
    val point = norm*dist
    point+end
  }

  protected def rightPoint(start:Vector2,end:Vector2,dist:Double) ={
    val actual = end-start
    val perp = actual.rightRot90
    val norm = perp.normalized
    val point = norm*dist
    point+end
  }

  protected def addSegment(start:Point,end:Point) ={
    val vStart = Vector2(start.x,start.y)
    val vEnd = Vector2(end.x,end.y)

    val v1 = leftPoint(vStart,vEnd,offsetAt(end))
    val v2 = rightPoint(vStart,vEnd,offsetAt(end))   // XXX optimise, invert vEnd->v1
    val v3 = leftPoint(vEnd,vStart,offsetAt(start))
    val v4 = rightPoint(vEnd,vStart,offsetAt(start)) // XXX optimise, invert vStart->v3

    moveTo(v1.x,v1.y)
    lineTo(v2.x,v2.y)
    lineTo(v3.x,v3.y)
    lineTo(v4.x,v4.y)
    lineTo(v1.x,v1.y)
  }

  protected def addPoint(p:Point) ={
    val offset = offsetAt(p)
    append(new Ellipse2D.Double(p.x-offset,p.y-offset,offset*2,offset*2),false)
  }

  protected def makeShape ={
    val (first,rest) = points.splitAt(1)
    addPoint(first.head)
    rest.foldLeft(first.head)((prev,current) => {
      addPoint(current)
      addSegment(prev,current)
      current
    })
  }
}
