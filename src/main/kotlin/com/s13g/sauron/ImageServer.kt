package com.s13g.sauron


import com.google.common.flogger.FluentLogger
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.simpleframework.http.Response
import org.simpleframework.http.Status
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
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

  @GuardedBy("mBytesLock")
  private var lastModified: Long

  /** Locks access on the mCurrentImageBytes object  */
  private val bytesLock: Any
  private val numActiveConnections: AtomicInteger

  /**
   * Creates a new image server.
   *
   * @param fileReadExecutor the executor to use to read new image files into memory.
   */
  constructor(fileReadExecutor: Executor) {
    this.fileReadExecutor = fileReadExecutor
    this.serveMjpegExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(200))
    this.currentImageBytes = ByteArray(0)
    this.lastModified = 0L
    this.bytesLock = Any()
    this.numActiveConnections = AtomicInteger(0)
  }

  /** For testing.  */
  internal constructor(fileReadExecutor: Executor, serveMjpegExecutor: ExecutorService) {
    this.fileReadExecutor = fileReadExecutor
    this.serveMjpegExecutor = MoreExecutors.listeningDecorator(serveMjpegExecutor)
    this.currentImageBytes = ByteArray(0)
    this.lastModified = 0L
    this.bytesLock = Any()
    this.numActiveConnections = AtomicInteger(0)
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
          lastModified = System.currentTimeMillis()
        }
      } else {
        log.atSevere().log("Could not load current image. Not updating.")
      }
    }
  }

  /** Serves the current image file to the given response.  */
  fun serveCurrentFile(response: Response) {
    val copyForRequest: ByteArray
    synchronized(bytesLock) {
      copyForRequest = currentImageBytes.copyOf()
    }
    serveData("image/jpeg", copyForRequest, response)
  }

  fun startServingMjpegTo(response: Response) {
    response.setContentType("multipart/x-mixed-replace;boundary=ipcamera")
    response.status = Status.OK

    // Enlist this response so it can get follow-up requests. Each response gets its own thread.
    serveMjpegExecutor.execute {
      log.atInfo().log("Added active mJPEG response. Total now ${numActiveConnections.incrementAndGet()}.")
      var lastServedTimestamp = 0L

      // Serve one frame right away. For some reason the very first frame is not being displayed. This way, a new
      // request will immediately display a result.
      serveMotionJpegFrame(response.outputStream)

      // Until the connection is either closed or a new frame hasn't arrived in a long time (indicating an internal
      // error), serve the new frame.
      while (waitForNewFrame(lastServedTimestamp)) {
        try {
          lastServedTimestamp = serveMotionJpegFrame(response.outputStream)
          log.atInfo().log("Served an mJPEG frame.")
        } catch (ex: IOException) {
          break
        }
      }
      try {
        response.close()
      } catch (ignore: IOException) {
      }
      log.atInfo().log("Connection closed. Total active now ${numActiveConnections.decrementAndGet()}.")
    }
  }

  /** Wait for an image newer than this timestamp. If none can be found after certain time, give up and return false. */
  private fun waitForNewFrame(lastServedTimestamp: Long): Boolean {
    var counter = 0
    while (lastModified <= lastServedTimestamp) {
      Thread.sleep(500)
      if (++counter >= 50) return false
      // Note: We should also exit if the connection is gone, but there is no good way to figure this out except for
      // trying to write to the stream.
    }
    return true
  }

  private fun serveMotionJpegFrame(outputStream: OutputStream): Long {
    // Make copy so that the response can take as long as it wants without blocking anything.
    val copyForRequest: ByteArray
    var servedTimestamp : Long
    synchronized(bytesLock) {
      copyForRequest = currentImageBytes.copyOf()
      servedTimestamp = lastModified
    }
    outputStream.write("--ipcamera\r\n".toByteArray())
    outputStream.write("Content-Type: image/jpeg\r\n".toByteArray())
    outputStream.write("Content-Length: ${copyForRequest.size}\r\n\r\n".toByteArray())
    outputStream.write(copyForRequest)
    return servedTimestamp
  }

  /** Serves the given data and content type to the given response.  */
  private fun serveData(contentType: String, data: ByteArray, response: Response) {
    response.status = Status.OK
    response.setContentType(contentType)
    response.outputStream.write(data)
    response.outputStream.close()
  }
}