package com.s13g.sauron.taker

import com.google.common.flogger.FluentLogger
import com.google.common.io.ByteStreams
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

private val log = FluentLogger.forEnclosingClass()

/**
 * Loads the jpeg files from the given directory and offers them in a round-robin way.
 */
fun createTestPictureTakerFrom(directory: File): PictureTaker {
  if (!directory.exists() || !directory.isDirectory) {
    throw RuntimeException("Directory does not exist: " + directory.absolutePath)
  }
  val jpegFiles = directory.listFiles { _: File?, name: String ->
    name.toLowerCase().endsWith(".jpg")
  }
  if (jpegFiles == null || jpegFiles.isEmpty()) {
    throw RuntimeException("No JPEG files in directory " + directory.absolutePath)
  }
  val files: MutableList<ByteArray> =
    ArrayList(jpegFiles.size)
  for (jpegFile in jpegFiles) {
    val file = readFile(jpegFile)
    if (file != null) {
      files.add(file)
    }
  }
  return PictureTakerTestingImpl(files)
}

/**
 * A PictureTaker that can be used for testing, when a webcam is not accessible.
 */
class PictureTakerTestingImpl internal constructor(private val testImages: List<ByteArray?>) :
  PictureTaker {
  private var counter = 0

  override fun captureImage(file: File): ListenableFuture<Boolean> {
    val future = SettableFuture.create<Boolean>()
    try {
      FileOutputStream(file).use { out ->
        ByteArrayInputStream(testImages[counter]).use { `in` ->
          val bytesWritten = ByteStreams.copy(`in`, out)
          future.set(bytesWritten > 0)
        }
      }
    } catch (e: IOException) {
      log.atSevere().log("Cannot write file.", e)
      future.set(false)
    }
    if (++counter >= testImages.size) {
      counter = 0
    }
    return future
  }
}

private fun readFile(file: File): ByteArray? {
  val data = ByteArrayOutputStream()
  try {
    FileInputStream(file).use { input ->
      ByteStreams.copy(input, data)
      return data.toByteArray()
    }
  } catch (e: IOException) {
    log.atWarning().log("Cannot read file. ", e)
    return null
  }
}