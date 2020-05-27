package com.s13g.sauron


import com.s13g.sauron.file.Directory
import com.s13g.sauron.file.FileWrapper
import com.s13g.sauron.file.SimpleVisitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.ArrayList


/** Tests for ImageRepository. */
class ImageRepositoryTest {
  @Rule
  @JvmField
  val imageRepoTempRoot = TemporaryFolder()
  private lateinit var fakeFreeSpaceReporter: FakeFreeSpaceReporter
  private lateinit var mockRootDir: Directory

  @Before
  fun initialize() {
    fakeFreeSpaceReporter = FakeFreeSpaceReporter()
    mockRootDir = mockk()
  }

  @Test
  fun testFileName() {
    val root = imageRepoTempRoot.newFolder("foo")
    // Time this test was written. Cheers!
    assertFileForDate(
      absFile(root, "dev", "null", "foo", "2015", "06", "27", "23_54_42__000000000.jpg"),
      absFile(root, "dev", "null", "foo"),
      2015, 6, 27, 23, 54, 42, 0
    )

    // Make sure padding is right everywhere.
    assertFileForDate(
      absFile(root, "dev", "null", "foo", "0001", "02", "03", "04_05_06__000000007.jpg"),
      absFile(root, "dev", "null", "foo"),
      1, 2, 3, 4, 5, 6, 7
    )

    // Edge case, last nano seconds of the year
    assertFileForDate(
      absFile(root, "dev", "null", "foo", "2015", "12", "31", "23_59_59__999999999.jpg"),
      absFile(root, "dev", "null", "foo"),
      2015, 12, 31, 23, 59, 59, 999999999
    )

    // Edge case, first nano second of the year.
    assertFileForDate(
      absFile(root, "dev", "null", "foo", "2016", "01", "01", "00_00_00__000000000.jpg"),
      absFile(root, "dev", "null", "foo"),
      2016, 1, 1, 0, 0, 0, 0
    )

    // Root... why not.
    assertFileForDate(
      absFile(root, "/", "2015", "06", "27", "23_54_42__000000000.jpg"),
      absFile(root, "/"),
      2015, 6, 27, 23, 54, 42, 0
    )
  }


  @Test
  @Throws(IOException::class)
  fun ensureDiskSpaceStaysSane() {
    // First, let's create a temporary tree of jpeg files which the repo can use to initialize
    // itself.
//    File subDir1 = new File(new File(new File(mRoot.getFile(), "2016"), "03"), "01");
//    assertTrue(subDir1.mkdirs());
//    File subDir2 = new File(new File(new File(mRoot.getFile(), "2016"), "03"), "02");
//    assertTrue(subDir2.mkdirs());
//
//    for (int i = 0; i < 23; ++i) {
//      assertTrue((new File(subDir1, "File_" + i + ".jpg")).createNewFile());
//    }
//    for (int i = 0; i < 7; ++i) {
//      assertTrue((new File(subDir2, "File_" + i + ".jpg")).createNewFile());
//    }
    val filesInRepo: MutableList<FileWrapper> = ArrayList()
    every { mockRootDir.walkFileTree(any()) } answers {
      val visitor: SimpleVisitor = it.invocation.args[0] as SimpleVisitor
      for (i in 0..41) {
        val fileInRepo: FileWrapper = createMockedFile("File_$i.jpg", 1000 + i.toLong())
        visitor(fileInRepo)
        filesInRepo.add(fileInRepo)
      }
    }
    val repository = ImageRepository(mockRootDir, fakeFreeSpaceReporter)
    repository.init()

    // If enough space is free, no files should be deleted when a new file was added.
    fakeFreeSpaceReporter.setFreeSpaceAvailableCountDown(0)
    var newFile: FileWrapper = createMockedFile("File_1000.jpg", 2000)
    repository.onFileWritten(newFile)
    filesInRepo.add(newFile)

    // No file should be deleted
    for (file in filesInRepo) {
      verify(exactly = 0) { file.deleteIfExists() }
    }

    // Now lets pretend we ran out of space and need to delete a single file.
    fakeFreeSpaceReporter.setFreeSpaceAvailableCountDown(1)
    newFile = createMockedFile("File_1001.jpg", 2001)
    repository.onFileWritten(newFile)
    filesInRepo.add(newFile)

    // The oldest file should be deleted, not the others.
    verify { filesInRepo.removeAt(0).deleteIfExists() }
    for (file in filesInRepo) {
      verify(exactly = 0) { file.deleteIfExists() }
    }
    newFile = createMockedFile("File_1002.jpg", 2002)
    repository.onFileWritten(newFile)
    filesInRepo.add(newFile)
    for (file in filesInRepo) {
      verify(exactly = 0) { file.deleteIfExists() }
    }

    // Now let's say something else wrote data onto the disk and there is now less space available.
    // This should make the image repo delete ten files until it starts growing the list again.
    fakeFreeSpaceReporter.setFreeSpaceAvailableCountDown(15)
    newFile = createMockedFile("File_1003.jpg", 2003)
    repository.onFileWritten(newFile)
    filesInRepo.add(newFile)
    for (i in 0..14) {
      verify { filesInRepo.removeAt(0).deleteIfExists() }
    }
    for (file in filesInRepo) {
      verify(exactly = 0) { file.deleteIfExists() }
    }

    // And finally, since enough space is available, we should be able to add three new files
    // without further deletions.
    newFile = createMockedFile("File_1004.jpg", 2004)
    repository.onFileWritten(newFile)
    filesInRepo.add(newFile)
    newFile = createMockedFile("File_1005.jpg", 2005)
    repository.onFileWritten(newFile)
    filesInRepo.add(newFile)
    newFile = createMockedFile("File_1006.jpg", 2006)
    repository.onFileWritten(newFile)
    filesInRepo.add(newFile)
    for (file in filesInRepo) {
      verify(exactly = 0) { file.deleteIfExists() }
    }
  }
}

private fun createMockedFile(name: String, createdMillis: Long): FileWrapper {
  val attributes = mockk<BasicFileAttributes>()
  every { attributes.creationTime() } returns FileTime.fromMillis(createdMillis)
  val file = mockk<FileWrapper>()
  every { file.toString() } returns name
  every { file.readBasicAttributes() } returns attributes
  every { file.isRegularFile } returns true
  every { file.deleteIfExists() } returns true
  return file
}


private fun assertFileForDate(
  expectedFile: File, rootDir: File, year: Int, month: Int,
  day: Int, hour: Int, minute: Int, second: Int, nanos: Int
) {
  val mockReporter = mockk<FreeSpaceReporter>()
  every { mockReporter.isMinSpaceAvailable } returns true
  val mockDirectory: Directory = mockk()
  every { mockDirectory.file } returns rootDir
  val repository = ImageRepository(mockDirectory, mockReporter)
  val date = LocalDate.of(year, month, day)
  val time = LocalTime.of(hour, minute, second, nanos)
  val dateTime = LocalDateTime.of(date, time)
  val file = repository.getFileForTime(dateTime)
  Assert.assertEquals(expectedFile.absolutePath, file.absolutePath)
}

/** Create an absolute file with the given path, relative to mRoot.  */
private fun absFile(folder: File, first: String, vararg components: String): File {
  var file = File(folder, first)
  for (component in components) {
    file = File(file, component)
  }
  return file
}

/** Enables us to fake the state of the file system.  */
private class FakeFreeSpaceReporter : FreeSpaceReporter {
  // How many times isMinSpaceAvailable needs to be called to report 'true'.
  private var mCountdownToSpace = 0
  override val isMinSpaceAvailable: Boolean
    get() {
      mCountdownToSpace--
      if (mCountdownToSpace < 0) {
        mCountdownToSpace = 0
      }
      return mCountdownToSpace == 0
    }

  fun setFreeSpaceAvailableCountDown(countdownToSpace: Int) {
    mCountdownToSpace = countdownToSpace + 1
  }
}

