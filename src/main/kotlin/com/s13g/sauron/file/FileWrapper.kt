package com.s13g.sauron.file

import java.nio.file.attribute.BasicFileAttributes


/** Common interface for file functionality that can be mocked easily in tests. */
interface FileWrapper {
  /** Whether this file is a regular file. */
  val isRegularFile: Boolean

  /**
   * If the file exists, deletes it.
   *
   * @return Whether the file was deleted.
   */
  fun deleteIfExists(): Boolean

  /** Read basic attributes of the file. */
  fun readBasicAttributes(): BasicFileAttributes?
}
