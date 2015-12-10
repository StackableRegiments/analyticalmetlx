package com.metl.model

import net.liftweb.common._
import scala.xml._
import scala.collection.JavaConverters._
import net.liftweb.util._
import Helpers._

import javax.imageio.ImageIO
import java.io._
//import org.apache.poi.xslf.usermodel._
import org.apache.poi.sl.usermodel.{Slide=>PoiSlide,_}

import java.awt.{Dimension, RenderingHints, Graphics2D}
import java.awt.image.BufferedImage
import java.awt.geom._
import com.metl.data._
import com.metl.utils._

class XSLFPowerpointImporter {
  import org.apache.poi.xslf.usermodel._
  def importAsImages(title:String,in:InputStream,server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Conversation = {
    //val ppt:SlideShow = SlideShowFactory.create(in)//new SlideShow(in)
    val ppt = org.apache.poi.xslf.usermodel.XSLFSlideShowFactory.createSlideShow(in)//new SlideShow(in)
    in.close()

    val zoom:Int = magnification
    val at:AffineTransform = new AffineTransform()
    at.setToScale(zoom, zoom)

    val pgsize:Dimension = ppt.getPageSize()

    //val slide:Array[PoiSlide] = ppt.getSlides()
    val conv:Conversation = server.createConversation(title,author)
    val firstSlide = conv.jid + 1
    val importerId = nextFuncName
    ppt.getSlides.toArray.toList.view.zipWithIndex.foreach{
      case (slideObj,index) => {
        val slide:XSLFSlide = slideObj.asInstanceOf[XSLFSlide]
        val slideId = firstSlide + index
        val identity = ""
        val tag = ""
        val (w,h) = (pgsize.width * zoom,pgsize.height * zoom)
        val img:BufferedImage = new BufferedImage(w,h, BufferedImage.TYPE_INT_RGB)
        val graphics:Graphics2D = img.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setTransform(at)
        graphics.setPaint(java.awt.Color.white)
        graphics.fill(new Rectangle2D.Float(0, 0, w,h))
        slide.draw(graphics)
        val out = new ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpg", out)
        val bytes = out.toByteArray()
        val bgImg = MeTLImage(server,author,-1L,tag,Empty,Full(bytes),Empty,w,h,0,0,"presentationSpace",Privacy.PUBLIC,slideId.toString,identity,Nil,1.0,1.0)
        val newRoom = MeTLXConfiguration.getRoom(conv.jid.toString,server.name,SlideRoom(server.name,conv.jid.toString,slideId)) 
//        newRoom ! JoinRoom("pptAsImageImporter",importerId,this)
        newRoom ! LocalToServerMeTLStanza(bgImg)
//        newRoom ! LeaveRoom("pptAsImageImporter",importerId,this)
      }
    }
    conv
  }

}

class HSLFPowerpointImporter {
  import org.apache.poi.hslf.usermodel._
  def importAsImages(title:String,in:InputStream,server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Conversation = {
    //val ppt:SlideShow = SlideShowFactory.create(in)//new SlideShow(in)
    val ppt = org.apache.poi.hslf.usermodel.HSLFSlideShowFactory.createSlideShow(new org.apache.poi.poifs.filesystem.NPOIFSFileSystem(in))//new SlideShow(in)
    in.close()

    val zoom:Int = magnification
    val at:AffineTransform = new AffineTransform()
    at.setToScale(zoom, zoom)

    val pgsize:Dimension = ppt.getPageSize()

    //val slide:Array[PoiSlide] = ppt.getSlides()
    val conv:Conversation = server.createConversation(title,author)
    val firstSlide = conv.jid + 1
    val importerId = nextFuncName
    ppt.getSlides.toArray.toList.view.zipWithIndex.foreach{
      case (slideObj,index) => {
        val slide:HSLFSlide = slideObj.asInstanceOf[HSLFSlide]
        val slideId = firstSlide + index
        val identity = ""
        val tag = ""
        val (w,h) = (pgsize.width * zoom,pgsize.height * zoom)
        val img:BufferedImage = new BufferedImage(w,h, BufferedImage.TYPE_INT_RGB)
        val graphics:Graphics2D = img.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setTransform(at)
        graphics.setPaint(java.awt.Color.white)
        graphics.fill(new Rectangle2D.Float(0, 0, w,h))
        slide.draw(graphics)
        val out = new ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpg", out)
        val bytes = out.toByteArray()
        val bgImg = MeTLImage(server,author,-1L,tag,Empty,Full(bytes),Empty,w,h,0,0,"presentationSpace",Privacy.PUBLIC,slideId.toString,identity,Nil,1.0,1.0)
        val newRoom = MeTLXConfiguration.getRoom(conv.jid.toString,server.name,SlideRoom(server.name,conv.jid.toString,slideId)) 
//        newRoom ! JoinRoom("pptAsImageImporter",importerId,this)
        newRoom ! LocalToServerMeTLStanza(bgImg)
//        newRoom ! LeaveRoom("pptAsImageImporter",importerId,this)
      }
    }
    conv
  }
}

class HSLFPowerpointParser {
  import org.apache.poi.hslf.usermodel._
  def importAsImages(in:InputStream,server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Map[Int,History] = {
    //val ppt:SlideShow = SlideShowFactory.create(in)//new SlideShow(in)
    val ppt = org.apache.poi.hslf.usermodel.HSLFSlideShowFactory.createSlideShow(new org.apache.poi.poifs.filesystem.NPOIFSFileSystem(in))//new SlideShow(in)
    in.close()

    val zoom:Int = magnification
    val at:AffineTransform = new AffineTransform()
    at.setToScale(zoom, zoom)

    val pgsize:Dimension = ppt.getPageSize()

    var histories:Map[Int,History] = Map.empty[Int,History]

    ppt.getSlides.toArray.toList.view.zipWithIndex.foreach{
      case (slideObj,index) => {
        val slide:HSLFSlide = slideObj.asInstanceOf[HSLFSlide]
        val slideId = index + 1
        val identity = ""
        val tag = ""
        val (w,h) = (pgsize.width * zoom,pgsize.height * zoom)
        val img:BufferedImage = new BufferedImage(w,h, BufferedImage.TYPE_INT_RGB)
        val graphics:Graphics2D = img.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setTransform(at)
        graphics.setPaint(java.awt.Color.white)
        graphics.fill(new Rectangle2D.Float(0, 0, w,h))
        slide.draw(graphics)
        val out = new ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpg", out)
        val bytes = out.toByteArray()
        val bgImg = MeTLImage(server,author,-1L,tag,Empty,Full(bytes),Empty,w,h,0,0,"presentationSpace",Privacy.PUBLIC,slideId.toString,identity,Nil,1.0,1.0)
//        val newRoom = MeTLXConfiguration.getRoom(conv.jid.toString,server.name,SlideRoom(server.name,conv.jid.toString,slideId)) 
//        newRoom ! JoinRoom("pptAsImageImporter",importerId,this)
//        newRoom ! LocalToServerMeTLStanza(bgImg)
//        newRoom ! LeaveRoom("pptAsImageImporter",importerId,this)
        val history = new History(slideId.toString)
        history.addStanza(bgImg)
        histories = histories.updated(slideId,history)
      }
    }
    histories
  }
}
class XSLFPowerpointParser {
  import org.apache.poi.xslf.usermodel._
  def importAsImages(jid:Int,in:InputStream,server:ServerConfiguration,author:String = Globals.currentUser.is,magnification:Int = 1):Map[Int,History] = {
    //val ppt:SlideShow = SlideShowFactory.create(in)//new SlideShow(in)
    val ppt = org.apache.poi.xslf.usermodel.XSLFSlideShowFactory.createSlideShow(in)//new SlideShow(in)
    in.close()

    val zoom:Int = magnification
    val at:AffineTransform = new AffineTransform()
    at.setToScale(zoom, zoom)

    val pgsize:Dimension = ppt.getPageSize()

    var histories:Map[Int,History] = Map.empty[Int,History]

    ppt.getSlides.toArray.toList.view.zipWithIndex.foreach{
      case (slideObj,index) => {
        val slide:XSLFSlide = slideObj.asInstanceOf[XSLFSlide]
        val slideId = index + jid
        val (w,h) = (pgsize.width * zoom,pgsize.height * zoom)
        val img:BufferedImage = new BufferedImage(w,h, BufferedImage.TYPE_INT_RGB)
        val graphics:Graphics2D = img.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setTransform(at)
        graphics.setPaint(java.awt.Color.white)
        graphics.fill(new Rectangle2D.Float(0, 0, w,h))
        slide.draw(graphics)
        val out = new ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpg", out)
        val bytes = out.toByteArray()
        val identity = server.postResource(slideId.toString,nextFuncName,bytes) 
        val tag = "{author: '%s', privacy: '%s', id: '%s', isBackground: false, zIndex: 0, resourceIdentity: '%s', timestamp: %s}".format(author,"public",identity,identity, new java.util.Date().getTime)
        val bgImg = MeTLImage(server,author,-1L,tag,Full(identity),Full(bytes),Empty,w,h,0,0,"presentationSpace",Privacy.PUBLIC,slideId.toString,identity,Nil,1.0,1.0)
//        val newRoom = MeTLXConfiguration.getRoom(conv.jid.toString,server.name,SlideRoom(server.name,conv.jid.toString,slideId)) 
//        newRoom ! JoinRoom("pptAsImageImporter",importerId,this)
//        newRoom ! LocalToServerMeTLStanza(bgImg)
//        newRoom ! LeaveRoom("pptAsImageImporter",importerId,this)
        val history = new History(slideId.toString)
        history.addStanza(bgImg)
        histories = histories.updated(slideId,history)
      }
    }
    histories
  }
}

