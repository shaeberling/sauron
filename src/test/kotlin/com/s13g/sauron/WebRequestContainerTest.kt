package com.s13g.sauron

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.simpleframework.http.Address
import org.simpleframework.http.Request
import org.simpleframework.http.Response
import org.simpleframework.http.Status
import org.simpleframework.http.core.Container
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executor

private abstract class ResourceLoader {
  abstract fun load(resourceName: String): ByteArray
}

/**
 * Tests for [WebRequestContainer].
 */
class WebRequestContainerTest {
  private lateinit var mockServer: ContainerServer
  private lateinit var mockServerCreator: ContainerServerCreator
  private lateinit var mockResourceLoader: ResourceLoader
  private lateinit var imageServer: ImageServer

  @Before
  fun initialize() {
    val immediateExecutor = Executor { obj: Runnable -> obj.run() }
    mockServer = mockk()
    every { mockServer.startServing(any()) } returns true
    mockServerCreator = { _: Int, _: Int -> mockServer }
    mockResourceLoader = mockk()
    imageServer = ImageServer(immediateExecutor)
  }

  @Test
  fun testStartLoadsIndexPage() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    every { mockResourceLoader.load(any()) } returns byteArrayOf(1, 2, 23, 42)

    // Start serving should load the index file into memory once.
    container.start()

    verify { mockResourceLoader.load(any()) }
    verify { mockServer.startServing(any()) }
  }

  @Test
  fun testIndexPageLoadDoesntStart() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    every { mockResourceLoader.load(any()) } throws IOException()

    container.start()

    verify { mockResourceLoader.load(any()) }
    // Make sure startServing is never called.
    verify(exactly = 0) { mockServer.startServing(any()) }
  }

  @Test
  fun testServingUnmappedUrl() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    val containerSlot = slot<Container>()
    every { mockResourceLoader.load(any()) } returns byteArrayOf(1, 2, 23, 42)
    every { mockServer.startServing(capture(containerSlot)) } returns true

    container.start()

    val mockResponse = spyk<Response>()
    every { mockResponse.close() } returns Unit

    // Simulate request to unavailable address.
    containerSlot.captured.handle(createMockRequestForUrl("doesNotExist"), mockResponse)
    verify { mockResponse.status = Status.NOT_FOUND }
  }

  @Test
  fun testServingIndexHtml() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    val containerSlot = slot<Container>()
    every { mockResourceLoader.load(any()) } returns byteArrayOf(1, 2, 23, 42)
    every { mockServer.startServing(capture(containerSlot)) } returns true
    container.start()

    val mockOutputStream = spyk<OutputStream>()
    val mockResponse = spyk<Response>()
    every { mockResponse.close() } returns Unit
    every { mockResponse.outputStream } returns mockOutputStream

    // Simulate request to unavailable address.
    containerSlot.captured.handle(createMockRequestForUrl("/"), mockResponse)
    verify { mockResponse.status = Status.OK }
    verify { mockResponse.setContentType("text/html") }
    verify { mockResponse.close() }
    verify { mockOutputStream.write(eq(byteArrayOf(1, 2, 23, 42))) }
    verify { mockOutputStream.close() }
  }

  @Test
  fun testServingImageFileNotSet() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    val containerSlot = slot<Container>()
    every { mockResourceLoader.load(any()) } returns byteArrayOf(1, 2, 23, 42)
    every { mockServer.startServing(capture(containerSlot)) } returns true
    container.start()

    verify { mockServer.startServing(containerSlot.captured) }

    val mockOutputStream = spyk<OutputStream>()
    val mockResponse = spyk<Response>()
    every { mockResponse.outputStream } returns mockOutputStream

    // Simulate request to image URL..
    containerSlot.captured
      .handle(createMockRequestForUrl("/now.jpg"), mockResponse)
    verify { mockResponse.status = Status.OK }
    verify { mockResponse.setContentType("image/jpeg") }
    verify { mockResponse.close() }

    // We never set the image
    verify { mockOutputStream.write(eq(ByteArray(0))) }
    verify { mockOutputStream.close() }
  }

  @Test
  fun testServingImageFileSet() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    val containerSlot = slot<Container>()
    every { mockResourceLoader.load(any()) } returns byteArrayOf(1, 2, 23, 42)
    every { mockServer.startServing(capture(containerSlot)) } returns true
    container.start()
    imageServer.updateCurrentFile { byteArrayOf(1, 2, 3, 5, 8, 13, 21) }
    val mockOutputStream = spyk<OutputStream>()
    val mockResponse = spyk<Response>()
    every { mockResponse.outputStream } returns mockOutputStream

    // Simulate request to image URL..
    containerSlot.captured
      .handle(createMockRequestForUrl("/now.jpg"), mockResponse)
    verify { mockResponse.status = Status.OK }
    verify { mockResponse.setContentType("image/jpeg") }
    verify { mockResponse.close() }

    // We never set the image
    verify { mockOutputStream.write(eq(byteArrayOf(1, 2, 3, 5, 8, 13, 21)))}
    verify { mockOutputStream.close() }
  }

  @Test
  fun testServingThrowsException() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    val containerSlot = slot<Container>()
    every { mockResourceLoader.load(any()) } returns byteArrayOf(1, 2, 23, 42)
    every { mockServer.startServing(capture(containerSlot)) } returns true
    container.start()
    val mockOutputStream = spyk<OutputStream>()
    val mockResponse = spyk<Response>()

    every { mockOutputStream.write(any<ByteArray>()) } throws IOException()
    every { mockResponse.outputStream } returns mockOutputStream
    containerSlot.captured.handle(createMockRequestForUrl("/"), mockResponse)
    // Even though it throws, response should be closed.
    verify { mockResponse.close() }
  }

  @Test
  fun testResponseCloseThrows() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    val containerSlot = slot<Container>()
    every { mockResourceLoader.load(any()) } returns byteArrayOf(1, 2, 23, 42)
    every { mockServer.startServing(capture(containerSlot)) } returns true
    container.start()
    val mockOutputStream = spyk<OutputStream>()
    val mockResponse = spyk<Response>()
    every { mockResponse.close() } throws IOException()
    every { mockResponse.outputStream } returns mockOutputStream
    containerSlot.captured.handle(createMockRequestForUrl("/"), mockResponse)
    // Nothing horrible should happen if response.close throws.
  }

  @Test
  fun testErrorWhenSettingCurrentFile() {
    val container = WebRequestContainer(
      123, mockServerCreator,
      imageServer, mockResourceLoader::load
    )
    every { mockResourceLoader.load(any()) } returns byteArrayOf(1, 2, 23, 42)
    container.start()
    imageServer.updateCurrentFile { null }
  }

}

private fun createMockRequestForUrl(url: String): Request {
  val mockAddress = mockk<Address>()
  every { mockAddress.toString() } returns url
  val mockRequest = mockk<Request>()
  every { mockRequest.address } returns mockAddress
  return mockRequest
}