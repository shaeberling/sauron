package com.s13g.sauron.file

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes


fun createDirectoryFrom(path: String?): Directory {
  return createDirectoryFrom(Paths.get(path))
}

fun createDirectoryFrom(path: Path): Directory {
  require(Files.isDirectory(path)) { "The given path is not a directory: $path" }
  return DirectoryImpl(path)
}

/** Default implementation for the Directory interface. */
class DirectoryImpl internal constructor(override val path: Path) : Directory {
  override fun walkFileTree(visitor: SimpleVisitor) {
    Files.walkFileTree(path, object : java.nio.file.SimpleFileVisitor<Path>() {
      @Throws(IOException::class)
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        visitor.invoke(FileWrapperImpl(file))
        return FileVisitResult.CONTINUE
      }
    })
  }

  override val file: File get() = path.toFile()
}
