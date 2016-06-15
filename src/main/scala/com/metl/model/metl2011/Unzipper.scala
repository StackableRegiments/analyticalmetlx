package com.metl.metl2011

import java.io._
import org.apache.commons.io.IOUtils
import net.liftweb.util.Helpers._
import net.liftweb.common.Logger
import org.apache.commons.compress.archivers.zip._
import xml.XML
import collection.mutable.ListBuffer

object Unzipper  extends Logger{
  def unzip(input:InputStream):collection.mutable.ListBuffer[xml.Node] = {
    try {
      val bytes = IOUtils.toByteArray(input)
      val zis = new ZipArchiveInputStream(new ByteArrayInputStream(bytes), "UTF8",false,true)
      val details = ListBuffer.empty[xml.Node]
      var entry:ZipArchiveEntry = zis.getNextZipEntry
      while(entry != null) {
        if (entry.getName.endsWith(".xml")){
          var zipString = IOUtils.toString(zis)
          try {
            details += XML.loadString(zipString)
          } catch {
            case e:Throwable => {
              error("failed to xmlify entry %s from input stream %s".format(zipString,input),e)
            }
          }
        }
        entry = zis.getNextZipEntry
      }
      zis.close
      details
    } catch {
      case e:Throwable => {
        error("failed to decompress input stream %s".format(input),e)
        throw e
      }
    }
  }
}
