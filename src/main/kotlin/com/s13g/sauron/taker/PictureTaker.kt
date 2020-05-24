package com.s13g.sauron.taker

import com.google.common.util.concurrent.ListenableFuture
import java.io.File

/** Common interface for picture taker.  */
interface PictureTaker {
  enum class Command(val commandLine: String) {
    FSWEBCAM("/usr/bin/fswebcam -r 1920x1080 %s"),
    RASPISTILL("raspistill -w 1200 -h 800 -q 10 -o %s"),
    RASPISTILL_FLIP("raspistill -vf -hf -w 1200 -h 800 -q 10 -o %s");
  }

  /**
   * Captures an image and writes it to the give file.
   * TODO: Convert this to a coroutine.
   *
   * @param file the path to which to write the final image file to.
   */
  fun captureImage(file: File): ListenableFuture<Boolean>
}
