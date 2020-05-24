package com.s13g.sauron

import com.google.common.flogger.FluentLogger
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.s13g.sauron.taker.PictureTaker
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Drives the picture taker to take repeated pictures.
 *
 * TODO: See if we can use coroutines for this.
 */
class Scheduler internal constructor(
  private val pictureTaker: PictureTaker, private val imageRepository: ImageRepository,
  private val executor: ScheduledExecutorService
) {
  private val log = FluentLogger.forEnclosingClass()

  /**
   * Start the picture taking scheduler.
   *
   * @param delayMillis how much time should pass in between shots (in milliseconds).
   * @param onPictureReady this listener is called when a new image has been captured. Don't do a lot of
   * work here since it might delay the next shot.
   */
  fun start(delayMillis: Int, onPictureReady: (File) -> Unit) {
    executor.scheduleAtFixedRate(
      {
        log.atInfo().log("About to take a new pic.")
        val nextImageFile: File = imageRepository.currentFile
        val captureResult: ListenableFuture<Boolean> = pictureTaker.captureImage(nextImageFile)
        Futures.addCallback(
          captureResult,
          object : FutureCallback<Boolean?> {
            override fun onSuccess(result: Boolean?) {
              if (result == null || !result) {
                log.atWarning().log("Taking picture not successful")
                return
              }
              log.atInfo().log("Taking pic was successful.")
              // TODO: We might want to do ths on a different executor so that a long-running
              // listener is not blocking the taking queue.
              onPictureReady(nextImageFile)
            }

            override fun onFailure(t: Throwable) {
              log.atWarning().withCause(t).log("Taking picture failed")
            }
          },
          Executors.newCachedThreadPool()
        )
      }, 0 /* No initial delay */, delayMillis.toLong(), TimeUnit.MILLISECONDS
    )
  }

  /** Shuts down the executor and thus stops scheduler.  */
  fun stop() {
    executor.shutdown()
  }
}
