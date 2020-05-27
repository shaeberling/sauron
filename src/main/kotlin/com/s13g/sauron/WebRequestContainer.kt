package com.s13g.sauron


import com.google.common.flogger.FluentLogger
import org.simpleframework.http.Request
import org.simpleframework.http.Response
import org.simpleframework.http.Status
import org.simpleframework.http.core.Container
import java.io.IOException

private const val NUM_SERVER_THREADS = 10
private const val CURRENT_IMAGE_PATH = "/now.jpg"
private const val HTML_TEMPLATE_FILE = "/sauron.html"
private const val FOSCAM_VIDEO_STREAM = "/videostream.cgi"

/** Container for the web frontend of Sauron. Handles all incoming requests. */
internal class WebRequestContainer(
  port: Int,
  serverCreator: ContainerServerCreator,
  private val imageServer: ImageServer,
  /** Loads resources, such as the index file via the classpath.  */
  private val resourceLoader: (String) -> ByteArray
) : Container {
  private val log = FluentLogger.forEnclosingClass()

  /** Used to serve containers.  */
  private val containerServer = serverCreator(port, NUM_SERVER_THREADS)

  /** The main HTML site.  */
  private var indexPageBytes: ByteArray = ByteArray(0)

  /** Starts a web server to serve the webcam images.*/
  fun start(): Boolean {
    try {
      loadIndexPage()
    } catch (e: IOException) {
      log.atSevere().log("Cannot start webserver: " + e.message)
      return false
    }
    return containerServer.startServing(this)
  }

  override fun handle(
    request: Request,
    response: Response
  ) {
    val requestUrl = request.address.toString()
    var closeResponse = true
    try {
      when {
        "/" == requestUrl -> {
          serveData("text/html", indexPageBytes, response)
        }
        requestUrl.startsWith(CURRENT_IMAGE_PATH) -> {
          imageServer.serveCurrentFile(response)
        }
        requestUrl.startsWith(FOSCAM_VIDEO_STREAM) -> {
          imageServer.startServingMjpegTo(response)
          closeResponse = false
        }
        else -> {
          response.status = Status.NOT_FOUND
        }
      }
    } catch (e: IOException) {
      log.atSevere().log("Error while serving [" + requestUrl + "]: " + e.message)
    } finally {
      if (closeResponse) {
        try {
          response.close()
        } catch (e: IOException) {
          log.atWarning().log("Cannot close response: " + e.message)
        }
      }
    }
  }

  private fun serveData(contentType: String, data: ByteArray, response: Response) {
    response.status = Status.OK
    response.setContentType(contentType)
    response.outputStream.write(data)
    response.outputStream.close()
  }

  /** Load the index page so it can be easily served from memory.  */
  private fun loadIndexPage() {
    indexPageBytes = resourceLoader(HTML_TEMPLATE_FILE)
  }
}