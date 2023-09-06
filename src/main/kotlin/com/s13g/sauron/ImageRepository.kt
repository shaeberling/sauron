package com.s13g.sauron


import com.google.common.annotations.VisibleForTesting
import com.google.common.flogger.FluentLogger
import com.s13g.sauron.file.Directory
import com.s13g.sauron.file.FileWrapper
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.LinkedList

private const val FILE_EXTENSION = ".jpg"


fun createImageRepo(minFreeSpaceBytes: Long, rootDirectory: Directory): ImageRepository {
  val reporter: FreeSpaceReporter = createFreeSpaceReporterFrom(minFreeSpaceBytes, rootDirectory.path)
  val repository = ImageRepository(rootDirectory, reporter)
  repository.init()
  return repository
}

/**
 * The image repository is initialize in a certain directory, which can always contain valid
 * repository data or a new directory.
 *
 * Based on the current time, the repository is able to store new images into a folder structure
 * that works well for archiving and retrieval.
 */
class ImageRepository @VisibleForTesting internal constructor(
  rootDirectory: Directory,
  freeSpaceReporter: FreeSpaceReporter
) {
  private val log = FluentLogger.forEnclosingClass()
  private val mRootDirectory: Directory = rootDirectory
  private val mFreeSpaceReporter: FreeSpaceReporter = freeSpaceReporter

  /** All image files are stored as a reference.  */
  private val mImageFiles: LinkedList<ImageRepoFile> = LinkedList()

  /**
   * Based on the current time, determines the path and file name for the image to be created.
   *
   *
   * Timestamp will be nano-second precise so avoid duplicates.
   *
   * @return A writable file that an image can be written to.
   */
  val currentFile: File
    get() = getFileForTime(LocalDateTime.now())

  /**
   * Call this when a new image file was written to disk.
   *
   *
   * We append the new file to the list of existing files so we can access is later for e.g
   * deletion.
   *
   *
   * We also check if the number of bytes left on disk is higher or equal to the number of minimum
   * bytes allowed to be left (see constructor parameter). If the available space is too low, we
   * delete the oldest image in the list. NOTE: We do not delete images until enough space is
   * available, but only the last one. Since only one new image was added, this should result in a
   * stable system that is no more complicated than it needs to be.
   *
   * @param file the file that has just been successfully written to disk.
   * @throws IOException if the oldest file is to be deleted but deletion fails.
   */
  fun onFileWritten(file: FileWrapper) {
    mImageFiles.add(ImageRepoFile(file))
    while (!mFreeSpaceReporter.isMinSpaceAvailable) {
      val oldestImage = mImageFiles.removeFirst()
      log.atInfo().log("Not enough space. Deleting: %d", oldestImage)
      oldestImage.delete()
    }
  }

  /**
   * Initializes the repository at the given location. Builds the list of file reference for fast
   * access and ability to delete LRU.
   */
  @VisibleForTesting
  fun init() {
    check(mImageFiles.isEmpty()) { "ImageRepo already initialized." }
    log.atInfo().log("Initializing ImageRepository")

    // Build list of existing image repo files.
    try {
      mRootDirectory.walkFileTree { file ->
        if (file.toString().lowercase().endsWith(".jpg")) {
          mImageFiles.add(ImageRepoFile(file))
          log.atInfo().log("INIT: Adding existing file: %s", file)
        }
      }
      log.atInfo().log("Found %d existing files.", mImageFiles.size)
      log.atInfo().log("Sorting...")
      mImageFiles.sort()
      log.atInfo().log("Sorting Done.")
    } catch (ex: IOException) {
      log.atSevere().withCause(ex).log("Cannot scan existing image repo.")
    } catch (ex: RuntimeException) {
      log.atSevere().withCause(ex).log("Cannot scan existing image repo.")
    }
  }

  /**
   * See []#getCurrentFile}.
   *
   * @param time the time for which to generate the path and file name.
   * @return A writable file that an image can be written to.
   */
  @VisibleForTesting
  fun getFileForTime(time: LocalDateTime): File {
    val directory = getDirectory(time)
    if (directory.exists() && !directory.isDirectory) {
      throw RuntimeException("Location is not a directory: " + directory.absolutePath)
    }
    if (!directory.exists() && !directory.mkdirs()) {
      throw RuntimeException("Cannot not create directory: " + directory.absolutePath)
    }
    return File(directory, getFilename(time))
  }

  private fun getDirectory(time: LocalDateTime): File {
    val year = time.year
    val month = time.monthValue
    val day = time.dayOfMonth

    // Just double checking that documentation says the right thing ;)
    check(year >= 1)
    check(month in 1..12)
    check(day >= 1 && month <= 31)
    val yearStr = String.format("%04d", year)
    val monthStr = String.format("%02d", month)
    val dayStr = String.format("%02d", day)
    return File(File(File(mRootDirectory.file, yearStr), monthStr), dayStr)
  }

  private fun getFilename(time: LocalDateTime) =
    "%02d_%02d_%02d__%09d%s".format(time.hour, time.minute, time.second, time.nano, FILE_EXTENSION)
}