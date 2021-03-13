package com.s13g.sauron

import com.google.common.flogger.FluentLogger
import com.s13g.sauron.file.FileDataLoader
import com.s13g.sauron.file.FileWrapperImpl
import com.s13g.sauron.file.ResourceLoaderImpl
import com.s13g.sauron.file.createDirectoryFrom
import com.s13g.sauron.taker.PictureTaker
import com.s13g.sauron.taker.PictureTakerImpl
import com.s13g.sauron.taker.createTestPictureTakerFrom
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

private val log = FluentLogger.forEnclosingClass()

/** Only enable this when you are testing without a real camera. */
private const val EMULATE_CAMERA = false

/** Define the command that should be used to take pictures. */
private val COMMAND: PictureTaker.Command = PictureTaker.Command.RASPISTILL_FLIP

/* Note: Set this when you do testing. */
private const val TEST_PICTURES_ROOT = "/dev/null"

/** This is where all the images will be stores. */
private const val REPOSITORY_ROOT = "/home/pi/image_repo"

/** Defines the frequency at which the images will be taken. */
private const val SHOT_DELAY_MILLIS = 6000 // Once every 6 seconds.

/** On which port to serve the image stream. */
private const val HTTP_PORT = 1986

/** Defines a limit of data to leave free on disk to avoid system crashes due to a full file system. */
private const val MIN_BYTES_AVAILABLE = 500L * 1000L * 1000L // 100 MB

/** Main entry point for the Sauron service. */
fun main() {
  println("Hi, this is Sauron 2021-03-13")

  val cameraCommandExecutor = Executors.newSingleThreadExecutor()
  val schedulerExecutor = Executors.newSingleThreadScheduledExecutor()
  val fileReadingExecutor = Executors.newSingleThreadExecutor()

  val pictureTaker: PictureTaker =
    if (EMULATE_CAMERA) {
      createTestPictureTakerFrom(File(TEST_PICTURES_ROOT))
    } else {
      PictureTakerImpl(COMMAND, cameraCommandExecutor)
    }
  val imageRepository = createImageRepo(
    MIN_BYTES_AVAILABLE,
    createDirectoryFrom(REPOSITORY_ROOT)
  )
  val serverCreator = defaultContainerServerCreator
  val resourceLoader = ResourceLoaderImpl()::load
  val imageServer = ImageServer(fileReadingExecutor)
  val requestContainer = WebRequestContainer(
    HTTP_PORT, serverCreator, imageServer, resourceLoader
  )

  // Start web server for serving data.
  if (!requestContainer.start()) {
    log.atSevere().log("Unable to start request container. Exiting.")
    return
  }

  val scheduler = Scheduler(pictureTaker, imageRepository, schedulerExecutor)
  // Start the scheduler to take pictures.
  scheduler.start(SHOT_DELAY_MILLIS) { pictureFile ->
    log.atInfo().log("New picture available at ${pictureFile.absolutePath}")
    try {
      imageRepository.onFileWritten(FileWrapperImpl(pictureFile.toPath()))
    } catch (ex: IOException) {
      // TODO: We should probably kill the daemon to prevent a system out of memory condition.
      log.atSevere().withCause(ex).log(
        "Cannot delete oldest file. System might run out of memory soon."
      )
    }
    imageServer.updateCurrentFile(FileDataLoader(pictureFile)::load)
  }
}

