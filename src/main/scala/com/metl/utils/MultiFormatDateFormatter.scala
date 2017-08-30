package com.metl.utils

import java.text.SimpleDateFormat
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}

import net.liftweb.common.Logger

class MultiFormatDateFormatter(datePatternString: Either[String, Tuple2[String, ZoneId]]*) extends Logger {
  protected val dateFormatters: List[Either[SimpleDateFormat, Tuple2[DateTimeFormatter, ZoneId]]] = datePatternString.toList.map(dps => dps.left.map(dp => {
    if (!dp.toLowerCase.contains("z")) {
      throw new IllegalArgumentException("Format string [%s] must specify timezone".format(dp))
    }
    new SimpleDateFormat(dp)
  }).right.map(dpt => {
    (new DateTimeFormatterBuilder().parseStrict().append(DateTimeFormatter.ofPattern("[" + dpt._1 + "]")).toFormatter, dpt._2)
  }))

  def parse(input: String): Long = {
    var result: Either[List[Tuple3[String, Option[ZoneId], Exception]], Long] = Left(Nil)
    dateFormatters.foreach {

      case Left(implicitFormatter) if result.isLeft => result = parseDateStringWithSimpleFormatter(input, implicitFormatter).left.map(l => result.left.toOption.getOrElse(Nil) ::: List((implicitFormatter.toPattern, None, l)))
      case Right((explicitFormatter, zoneId)) if result.isLeft => result = parseDateStringWithZoneId(input, zoneId, explicitFormatter).left.map(l => result.left.toOption.getOrElse(Nil) ::: List((explicitFormatter.toString, Some(zoneId), l)))
      case _ => {}
    }
    result match {
      case Right(value) => value
      case Left(es) => {
        throw es.headOption.map(_._3).getOrElse(new Exception("no formatters tried"))
      }
    }
  }

  protected def parseDateStringWithZoneId(input: String, zoneId: ZoneId, dateTimeFormatter: DateTimeFormatter): Either[Exception, Long] = {
    try {
      val parsedDate = dateTimeFormatter.parse(input)
      val localDateTime = LocalDateTime.from(parsedDate)
      val zonedDateTime = ZonedDateTime.of(localDateTime, zoneId)
      Right(Instant.from(zonedDateTime).toEpochMilli)
    } catch {
      case ex: Exception => {
        Left(ex)
      }
    }
  }

  protected def parseDateStringWithSimpleFormatter(input: String, formatter: SimpleDateFormat): Either[Exception, Long] = {
    try {
      Right(formatter.parse(input).getTime)
    } catch {
      case e: Exception => Left(e)
    }
  }
}
