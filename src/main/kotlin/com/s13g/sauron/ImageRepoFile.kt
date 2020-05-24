package com.s13g.sauron

import com.s13g.sauron.file.FileWrapper
import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime


/**
 * An image file created by Sauron
 */
class ImageRepoFile(private val file: FileWrapper) : Comparable<ImageRepoFile?> {

  /**
   * Deletes the given file.
   *
   * @throws IOException thrown if the file could not be deleted.
   */
  fun delete() {
    if (!file.isRegularFile) {
      throw IOException("Not a regular file: $file")
    }
    try {
      if (!file.deleteIfExists()) {
        throw IOException("File does not exist: $file")
      }
    } catch (ex: IOException) {
      throw IOException("Cannot delete image repo file.", ex)
    } catch (ex: SecurityException) {
      throw IOException("Cannot delete image repo file.", ex)
    }
  }

  override operator fun compareTo(other: ImageRepoFile?): Int {
    if (other == null) {
      return 1
    }
    val otherCreationTime: FileTime
    val thisCreationTime: FileTime
    try {
      val otherAttributes: BasicFileAttributes = other.file.readBasicAttributes()
        ?: throw RuntimeException("Error getting other file attributes for sorting.")
      otherCreationTime = otherAttributes.creationTime()
      val attributes: BasicFileAttributes =
        file.readBasicAttributes() ?: throw RuntimeException("Error getting own file attributes for sorting.")
      thisCreationTime = attributes.creationTime()
    } catch (ex: IOException) {
      throw RuntimeException("Error getting file attributes for sorting.", ex)
    }
    // Sort in ascending order.
    return thisCreationTime.compareTo(otherCreationTime)
  }

  override fun toString() = file.toString()
}