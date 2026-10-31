package com.rfandango.haku_x.data

/** Classic EvoX executable trainer. The first two text entries are name and
 * note/author, followed by option labels. XBTF and its helper ABI are separate. */
data class Etm(val name: String, val note: String, val labels: List<String>, val titleIds: List<Long>) {
  fun matches(gameKey: String): Boolean = gameKey.substringBefore('.').toLongOrNull(16)?.let { it != 0L && it in titleIds } == true

  companion object {
    const val MAX_SIZE = 65536
    fun parse(d: ByteArray): Etm {
      require(d.size in 35..MAX_SIZE) { "ETM size must be 35–65536 bytes" }
      fun u(p: Int): Long {
        require(p >= 0 && p <= d.size - 4) { "Truncated ETM table" }
        return (0..3).fold(0L) { a, i -> a or ((d[p+i].toLong() and 255) shl (8*i)) }
      }
      require(u(0) == d.size.toLong() && d[4] == 1.toByte() && d[5] == 0.toByte() && u(6) == 35L && u(26) == 0L) {
        "Unsupported trainer header. Import a classic .etm file; XBTF is unsupported."
      }
      val options = u(10); val text = u(14); val ids = u(18); val entry = u(22)
      require(entry >= 35 && entry < d.size && options >= 35 && text > options && text < d.size && text-options <= 100) { "ETM entry/options outside file" }
      require(ids >= 35 && ids <= options && options-ids >= 8) { "ETM title table outside file" }
      val titleIds = (0 until minOf(3, ((options-ids)/4).toInt())).map { u(ids.toInt()+4*it) }
      val strings = mutableListOf<String>()
      var p = text.toInt()
      while (true) {
        val offset = u(p); p += 4
        if (offset == 0L) break
        require(strings.size < text-options+2 && offset < d.size) { "ETM label outside file" }
        val start = offset.toInt()
        val end = (start until d.size).firstOrNull { d[it] == 0.toByte() }
        require(end != null) { "Unterminated ETM label" }
        strings += String(d, start, end-start, Charsets.ISO_8859_1)
      }
      require(strings.size >= 3) { "ETM has no named options" }
      return Etm(strings[0], strings[1], strings.drop(2), titleIds)
    }
  }
}
