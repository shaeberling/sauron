package com.s13g.sauron.file

import java.io.File
import java.nio.file.Path

typealias SimpleVisitor = (FileWrapper) -> Unit

/** Easy to mock interface for dealing with directories. */
interface Directory {
  /**
   * See [java.nio.file.Files.walkFileTree].
   */
  fun walkFileTree(visitor: SimpleVisitor)

  /** Returns the File representation of this directory.  */
  val file: File

  /** Returns the Path representation of this directory.  */
  val path: Path
}
