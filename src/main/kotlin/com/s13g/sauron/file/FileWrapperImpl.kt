package com.s13g.sauron.file

import com.google.common.base.Preconditions
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Default implementation for file functionality. */
class FileWrapperImpl(path: Path) : FileWrapper {
  private val path: Path = Preconditions.checkNotNull(path)

  override val isRegularFile: Boolean
    get() = Files.isRegularFile(path)

  override fun deleteIfExists() = Files.deleteIfExists(path)

  @Throws(IOException::class)
  override fun readBasicAttributes(): BasicFileAttributes =
    Files.readAttributes(path, BasicFileAttributes::class.java)

  override fun toString() = path.toString()

  override fun equals(other: Any?) = other is FileWrapperImpl && path == other.path

  override fun hashCode() = path.hashCode()
}
