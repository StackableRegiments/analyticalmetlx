package com.metl.model
import org.apache.batik.transcoder.image.JPEGTranscoder
import org.apache.batik.transcoder.SVGAbstractTranscoder
import org.apache.batik.transcoder.{TranscoderInput,TranscoderOutput}
import java.io._
import java.awt.Rectangle

object SvgConverter extends BatikConverter 

class BatikConverter {
  val byteEncoding = "UTF-8"
  def transcoder(quality:Float,width:Int,height:Int) = {
    val t = new JPEGTranscoder()
    //t.addTranscodingHint(JPEGTranscoder.KEY_XML_PARSER_CLASSNAME, "org.apache.crimson.parser.XMLReaderImpl")
    t.addTranscodingHint(JPEGTranscoder.KEY_QUALITY, quality)
    t.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toFloat)
    t.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toFloat)
    t.addTranscodingHint(SVGAbstractTranscoder.KEY_AOI, new Rectangle(0,0,width,height))
    t
  }

  def toJpeg(svg:String,width:Int, height:Int, quality:Float = 0.4f):Array[Byte] = {
    val inputStream = new ByteArrayInputStream(svg.getBytes(byteEncoding))
    val outputStream = new ByteArrayOutputStream()
    val input = new TranscoderInput(inputStream)
    val output = new TranscoderOutput(outputStream)
    transcoder(quality,width,height).transcode(input,output)
    val bytes = outputStream.toByteArray
    outputStream.close
    bytes
  }
}
