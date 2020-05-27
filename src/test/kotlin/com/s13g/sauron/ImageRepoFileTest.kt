package com.s13g.sauron


import com.s13g.sauron.file.FileWrapper
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime


private fun createFileWithCreationTime(creationTimeMillis: Int): ImageRepoFile {
  val otherAttributes: BasicFileAttributes = mockk()
  every { otherAttributes.creationTime() } returns FileTime.fromMillis(creationTimeMillis.toLong())
  val otherPath: FileWrapper = mockk()
  every { otherPath.readBasicAttributes() } returns otherAttributes
  return ImageRepoFile(otherPath)
}

/**
 * Tests for [ImageRepoFile].
 */
class ImageRepoFileTest {
  private lateinit var path: FileWrapper
  private lateinit var file: ImageRepoFile
  private lateinit var attributes: BasicFileAttributes

  @Before
  fun initialize() {
    path = mockk()
    attributes = mockk()
    file = ImageRepoFile(path)
    every { path.readBasicAttributes() } returns attributes

  }

  @Test
  fun testDeleteFailsNoRegularFile() {
    every { path.isRegularFile } returns false
    every { path.deleteIfExists() } returns true
    try {
      file.delete()
      fail("Delete should throw an exception. Not a regular file.")
    } catch (expected: IOException) {
      // Expected.
    }
  }

  @Test
  fun testDeleteFails() {
    every { path.isRegularFile } returns true
    every { path.deleteIfExists() } returns false
    try {
      file.delete()
      fail("Delete should throw an exception since it failed.")
    } catch (expected: IOException) {
      // Expected.
    }
  }

  @Test
  fun testDeleteSucceeds() {
    every { path.isRegularFile } returns true
    every { path.deleteIfExists() } returns true
    try {
      file.delete()
    } catch (t: Throwable) {
      fail("Delete should succeed")
    }
  }

  @Test
  fun testCompareToSameCreationTime() {
    val otherFile = createFileWithCreationTime(424242)
    every { attributes.creationTime() } returns FileTime.fromMillis(424242)
    assertEquals(0, file.compareTo(otherFile))
  }

  @Test
  fun testCompareToDifferentCreationTime1() {
    val otherFile = createFileWithCreationTime(424243)
    every { attributes.creationTime() } returns FileTime.fromMillis(424242)
    assertEquals(-1, file.compareTo(otherFile))
  }

  @Test
  fun testCompareToDifferentCreationTime2() {
    val otherFile = createFileWithCreationTime(424241)
    every { attributes.creationTime() } returns FileTime.fromMillis(424242)
    assertEquals(1, file.compareTo(otherFile))
  }

  @Test
  fun testCompareToFailsDueToException() {
    val otherFile = createFileWithCreationTime(424242)
    every { path.readBasicAttributes() } throws IOException("Boom!")
    try {
      assertEquals(1, file.compareTo(otherFile))
      fail("Should have thrown due to file level exception.")
    } catch (expected: RuntimeException) {
      // Expected.
    }
  }
}