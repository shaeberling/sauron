package com.s13g.sauron

import com.google.common.flogger.FluentLogger
import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path


/**
 * Creates a reporter for the given path.
 *
 * @param minFreeBytes the minimum number of free bytes required on the path. Once the available
 * space is less than this, [.isMinSpaceAvailable] will return false.
 */
fun createFreeSpaceReporterFrom(minFreeBytes: Long, path: Path): FreeSpaceReporter =
  FreeSpaceReporterImpl(minFreeBytes, Files.getFileStore(path))

private fun bytesToMb(bytes: Long) = bytes / 1000000L

/**
 * Reports whether enough free space is available on the given path.
 */
class FreeSpaceReporterImpl internal constructor(private val minFreeBytes: Long, private val fileStore: FileStore) :
  FreeSpaceReporter {

  private val log = FluentLogger.forEnclosingClass()

  override val isMinSpaceAvailable: Boolean
    get() = try {
      val freeSpaceBytes = fileStore.usableSpace
      log.atInfo().log(
        "Space available: " + bytesToMb(freeSpaceBytes) + " MB. (Max: " +
            bytesToMb(minFreeBytes) + "MB)"
      )
      freeSpaceBytes >= minFreeBytes
    } catch (ex: IOException) {
      log.atSevere().log("Cannot determine free space.", ex)
      false
    }
}