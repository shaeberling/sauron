package com.s13g.sauron

import java.time.LocalDateTime


private val FILE_EXTENSION = ".png"

fun main() {
  val now = LocalDateTime.now()
  println("OLD: ${getFilenameOld(now)}")
  println("NEW: ${getFilename(now)}")
}

private fun getFilename(time: LocalDateTime) =
  "%02d_%02d_%02d__%09d%s".format(time.hour, time.minute, time.second, time.nano, FILE_EXTENSION)

private fun getFilenameOld(time: LocalDateTime): String {
  val filename = StringBuilder()
  filename.append(String.format("%02d", time.hour))
  filename.append("_")
  filename.append(String.format("%02d", time.minute))
  filename.append("_")
  filename.append(String.format("%02d", time.second))
  filename.append("__")
  filename.append(String.format("%09d", time.nano))
  filename.append(".jpg")
  return filename.toString()
}