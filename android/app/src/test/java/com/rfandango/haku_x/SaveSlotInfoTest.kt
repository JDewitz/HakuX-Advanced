package com.rfandango.haku_x

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SaveSlotInfoTest {
  @Test fun stateOwnsDateAndOrphanThumbnailsAreNotShown() {
    val dir = Files.createTempDirectory("state-browser").toFile()
    try {
      val state = File(dir, "slot1.hxs"); val image = File(dir, "slot1.jpg")
      image.writeText("orphan")
      val empty = SaveSlotInfo.read(1, state, image)
      assertFalse(empty.exists); assertNull(empty.thumbnail); assertEquals(0L, empty.timestamp)
      state.writeBytes(byteArrayOf(1, 2, 3)); assertTrue(state.setLastModified(1600000000000L))
      val saved = SaveSlotInfo.read(1, state, image)
      assertEquals(1600000000000L, saved.timestamp); assertEquals(3L, saved.bytes)
      assertEquals(image, saved.thumbnail)
      image.delete()
      assertTrue(SaveSlotInfo.read(1, state, image).exists)
      assertNull(SaveSlotInfo.read(1, state, image).thumbnail)
    } finally { dir.deleteRecursively() }
  }
  @Test fun thumbnailsAreBoundedForLandscapeAndPortrait() {
    assertEquals(16, SaveSlotInfo.sampleSize(1920, 1080, 96, 64))
    assertEquals(32, SaveSlotInfo.sampleSize(1080, 2400, 96, 64))
    assertEquals(4, SaveSlotInfo.sampleSize(480, 270, 96, 64))
    assertEquals(1, SaveSlotInfo.sampleSize(40, 30, 96, 64))
  }
}
