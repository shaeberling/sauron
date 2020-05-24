package com.s13g.sauron


import com.google.common.collect.ImmutableList
import com.google.common.flogger.FluentLogger
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.simpleframework.http.Response
import org.simpleframework.http.Status
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.concurrent.GuardedBy


/** Simple web server to serve the webcam pictures.  */
class ImageServer {
  private val log = FluentLogger.forEnclosingClass()

  /** Used to read the file into memory to serve it.  */
  private val fileReadExecutor: Executor

  /** Serves the responses to the incoming requests.  */
  private val serveMjpegExecutor: ListeningExecutorService

  /** The bytes of the current image to serve.  */
  @GuardedBy("mBytesLock")
  private var currentImageBytes: ByteArray

  /** Locks access on the mCurrentImageBytes object  */
  private val bytesLock: Any
  private val mJpegQueue = LinkedBlockingQueue<ByteArray>(1)
  private val activeMjpegResponses: MutableSet<Response> = HashSet()

  /**
   * Creates a new image server.
   *
   * @param fileReadExecutor the executor to use to read new image files into memory.
   */
  constructor(fileReadExecutor: Executor) {
    this.fileReadExecutor = fileReadExecutor
    serveMjpegExecutor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
    currentImageBytes = ByteArray(0)
    bytesLock = Any()
    startServing()
  }

  /** For testing.  */
  internal constructor(fileReadExecutor: Executor, serveMjpegExecutor: ExecutorService) {
    this.fileReadExecutor = fileReadExecutor
    this.serveMjpegExecutor = MoreExecutors.listeningDecorator(serveMjpegExecutor)
    currentImageBytes = ByteArray(0)
    bytesLock = Any()
    startServing()
  }

  /**
   * Loads a new image from the load into memory from where it will be served when requested.
   * Setting a new current file will override the previous one.
   *
   * @param currentImageLoader loads the current image file.
   */
  fun updateCurrentFile(currentImageLoader: () -> ByteArray?) {
    fileReadExecutor.execute {
      val currentImage: ByteArray? = currentImageLoader()
      if (currentImage != null) {
        synchronized(bytesLock) {
          currentImageBytes = currentImage
          log.atInfo().log("Offering new mJPEG to mJPEG queue.")
          if (!mJpegQueue.offer(currentImageBytes)) {
            log.atWarning().log("Cannot add new image to mJPEG queue.")
          }
        }
      } else {
        log.atSevere().log("Could not load current image. Not updating.")
      }
    }
  }

  /** Serves the current image file to the given response.  */
  fun serveCurrentFile(response: Response) {
    synchronized(bytesLock) { serveData("image/jpeg", currentImageBytes, response) }
  }

  private fun startServing() {
    // One master thread that loops until interrupted.
    Executors.newSingleThreadExecutor()
      .execute {
        while (true) {
          try {
            // Get the data to serve to all outstanding requests.
            val jpegData = mJpegQueue.take()
            val servingFutures: MutableSet<ListenableFuture<*>> = HashSet()
            for (response in ImmutableList.copyOf(activeMjpegResponses)) {

              // Fires up a thread per active response being served.
              val future = serveMjpegExecutor.submit { serveMotionJpeg(response, jpegData) }
              servingFutures.add(future)
            }
            // Wait for all serves to complete.
            // Downside: The slowest connection will pause serving for everybody else.
            // Fixme: Put the serves into a queue, with copied data.
            Futures.allAsList(servingFutures).get()
          } catch (ex: InterruptedException) {
            log.atInfo().log("Got interrupted while retrieving from mJpeg queue.")
            break
          } catch (ex: ExecutionException) {
            log.atInfo().log("Got interrupted while retrieving from mJpeg queue.")
            break
          }
        }
      }
  }

  fun startServingMjpegTo(response: Response) {
    response.setContentType("multipart/x-mixed-replace;boundary=ipcamera")
    response.status = Status.OK

    // Immediately serve the most current frame.
    synchronized(bytesLock) {
      try {
        serveMotionJpegFrame(currentImageBytes, response.outputStream)
      } catch (ignore: IOException) {
      }
    }
    activeMjpegResponses.add(response)
    log.atInfo().log("Added active mJPEG response. Total now ${activeMjpegResponses.size}.")
  }

  private fun serveMotionJpeg(response: Response, jpegData: ByteArray) {
    try {
      val outputStream = response.outputStream

      // When we get an IOException trying to write out data, at which point we end
      // serving data as the request has likely been cancelled, we remove the response so it is
      // no longer being served.
      serveMotionJpegFrame(jpegData, outputStream)
    } catch (ex: IOException) {
      activeMjpegResponses.remove(response)
      log.atInfo()
        .log("Removing inactive response. Total now ${activeMjpegResponses.size}.")
      try {
        response.close()
      } catch (ignore: IOException) {
      }
    }
  }

  private fun serveMotionJpegFrame(jpegData: ByteArray, outputStream: OutputStream) {
    outputStream.write("--ipcamera\r\n".toByteArray())
    outputStream.write("Content-Type: image/jpeg\r\n".toByteArray())
    outputStream.write("Content-Length: ${jpegData.size}\r\n\r\n".toByteArray())
    outputStream.write(jpegData)
    log.atFine().log("Wrote mJpeg frame")
  }

  /** Serves the given data and content type to the given response.  */
  private fun serveData(contentType: String, data: ByteArray, response: Response) {
    response.status = Status.OK
    response.setContentType(contentType)
    response.outputStream.write(data)
    response.outputStream.close()
  }
}
