package com.s13g.sauron.file

import com.google.common.flogger.FluentLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import javax.annotation.concurrent.GuardedBy

/** Loads data from a given file. Doesn't cache loaded data.*/
class FileDataLoader(@field:GuardedBy("mLock") private val mFile: File) {
  private val log = FluentLogger.forEnclosingClass()
  private val lock: Any = Any()

  fun load(): ByteArray? {
    synchronized(lock) {
      val fileInputStream: FileInputStream
      fileInputStream = try {
        FileInputStream(mFile)
      } catch (e: FileNotFoundException) {
        log.atWarning().log("Cannot not find file [%s]: %s", mFile.absolutePath, e.message)
        return null
      }
      return fileInputStream.readAllBytes()
    }
  }
}
