package com.s13g.sauron.file

import com.google.common.base.Preconditions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import javax.annotation.ParametersAreNonnullByDefault

/** Default implementation for file functionality. */
@ParametersAreNonnullByDefault
class FileWrapperImpl(path: Path) : FileWrapper {
  private val path: Path = Preconditions.checkNotNull(path)

  override val isRegularFile: Boolean
    get() = Files.isRegularFile(path)

  override fun deleteIfExists() = Files.deleteIfExists(path)

  override fun readBasicAttributes(): BasicFileAttributes =
    Files.readAttributes(path, BasicFileAttributes::class.java)

  override fun toString() = path.toString()

  override fun equals(obj: Any?) = obj is FileWrapperImpl && path == obj.path
}
