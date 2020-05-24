package com.s13g.sauron.taker

import com.google.common.flogger.FluentLogger
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor


/**
 * Accesses the webcam and produces images using a variety of underlying capture commands.
 */
class PictureTakerImpl(private val command: PictureTaker.Command, private val executor: Executor) : PictureTaker {
  private val log: FluentLogger = FluentLogger.forEnclosingClass()

  /**
   * The command to execute to capture an image. Note: Add '--no-banner' to remove the timestamp
   * banner at the bottom of the image file.
   */
  override fun captureImage(file: File): ListenableFuture<Boolean> {
    val result = SettableFuture.create<Boolean>()
    try {
      val foo = createCommandForFileName(file.absolutePath)
      val process = ProcessBuilder(foo)
        .redirectErrorStream(true)
        .start()
      handleProcess(process, result)
    } catch (e: IOException) {
      log.atSevere().withCause(e).log("Could not capture image")
      result.setException(e)
    }
    return result
  }

  /**
   * Asynchronously waits to the process to finish and informs the given future.
   *
   * @param process the process to wait for to end.
   * @param result the future to set when the process is done.
   */
  private fun handleProcess(process: Process, result: SettableFuture<Boolean>) {
    executor.execute {
      try {
        val returnValue = process.waitFor()
        result.set(returnValue == 0)
      } catch (e: InterruptedException) {
        log.atSevere().withCause(e).log("Interrupted while capturing image")
        result.set(false)
      }
    }
  }

  private fun createCommandForFileName(fileName: String) =
    command.commandLine.format(fileName).split("\\s+".toRegex())
}
