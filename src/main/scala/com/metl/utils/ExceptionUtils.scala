package com.metl.utils

import java.io.{PrintWriter, StringWriter}

object ExceptionUtils {
  def getStackTraceAsString(t: Throwable): String = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}
