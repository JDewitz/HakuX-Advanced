package com.rfandango.haku_x

import java.io.File

/** Immutable file metadata: timestamps belong to the state, not its optional image. */
data class SaveSlotInfo(val slot: Int, val exists: Boolean, val timestamp: Long, val bytes: Long, val thumbnail: File?) {
  companion object {
    fun read(slot: Int, state: File, thumb: File) = SaveSlotInfo(slot, state.isFile,
      if (state.isFile) state.lastModified() else 0L, if (state.isFile) state.length() else 0L,
      thumb.takeIf { state.isFile && it.isFile })
    fun sampleSize(width: Int, height: Int, targetW: Int, targetH: Int): Int {
      require(targetW > 0 && targetH > 0)
      var sample = 1
      while ((width / (sample * 2) >= targetW || height / (sample * 2) >= targetH) && sample <= 512) sample *= 2
      return sample
    }
  }
}
