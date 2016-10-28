package com.metl.utils

import java.io.{PrintWriter, StringWriter}

object ExceptionUtils {
  def getStackTraceAsString(t: Throwable) = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}
