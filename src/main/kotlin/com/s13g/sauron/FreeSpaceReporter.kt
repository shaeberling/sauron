package com.s13g.sauron

/**
 * Reports on whether there is enough free space on a given path.
 */
interface FreeSpaceReporter {
  /**
   * @return Whether at least the given free bytes are available on the given path.
   */
  val isMinSpaceAvailable: Boolean
}
