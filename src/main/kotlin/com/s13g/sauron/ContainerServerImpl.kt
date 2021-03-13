package com.s13g.sauron

import com.google.common.flogger.FluentLogger
import org.simpleframework.http.core.Container
import org.simpleframework.http.core.ContainerSocketProcessor
import org.simpleframework.transport.connect.Connection
import org.simpleframework.transport.connect.SocketConnection
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress

/*** Creates instances of ContainerServer.*/
typealias ContainerServerCreator = (port: Int, numThreads: Int) -> ContainerServer


/** Returns the default creator which will produce a real server. Not suitable for unit testing. */
val defaultContainerServerCreator: ContainerServerCreator
  get() = { port: Int, numThreads: Int -> ContainerServerImpl(port, numThreads) }

/** Starts a server to start serving the given container. */
class ContainerServerImpl(private val port: Int, private val numThreads: Int) : ContainerServer {
  private val log = FluentLogger.forEnclosingClass()

  override fun startServing(container: Container) =
    try {
      val processor = ContainerSocketProcessor(container, numThreads)
      val connection: Connection =
        SocketConnection(processor)
      val address: SocketAddress = InetSocketAddress(port)
      connection.connect(address)
      log.atInfo().log("Listening at $address")
      true
    } catch (e: IOException) {
      log.atSevere().withCause(e).log("Cannot start webserver")
      false
    }
}
