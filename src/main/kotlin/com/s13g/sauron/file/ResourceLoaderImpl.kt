package com.s13g.sauron.file

import com.google.common.io.ByteStreams
import java.io.IOException

/** Loads resources through getResourceAsStream through the classloader. */
class ResourceLoaderImpl {
  fun load(resourceName: String): ByteArray {
    val indexPageStream = javaClass.getResourceAsStream(resourceName)
      ?: throw IOException("Cannot load resource [$resourceName]")
    return ByteStreams.toByteArray(indexPageStream)
  }
}
