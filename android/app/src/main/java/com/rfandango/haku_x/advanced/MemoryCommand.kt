package com.rfandango.haku_x.advanced

/** Validate UI input before handing a command to the guest-thread scanner. */
object MemoryCommand {
  fun search(type: String, value: String): String {
    require(type in listOf("u16", "u32", "f32")) { "Choose a supported value type" }
    val v = value.trim()
    if (type == "f32") {
      val n = v.toFloatOrNull()
      require(n?.isFinite() == true) { "Enter a finite decimal number" }
      return "$type=$n"
    }
    val n = if (v.startsWith("0x", true)) v.drop(2).toLongOrNull(16) else v.toLongOrNull()
    require(n != null && n >= 0 && n <= if (type == "u16") 65535L else 4294967295L) { "Value is outside this unsigned type" }
    return "$type=$n" // canonical decimal avoids strtoul's octal interpretation
  }

  private fun address(value: String): Long = value.trim().removePrefix("0x").removePrefix("0X")
    .toLongOrNull(16)?.takeIf { it in 0..0xffffffffL } ?: error("Enter a 32-bit hexadecimal address")
  fun peek(value: String): String {
    val n = address(value)
    require(n <= 0xffffffc0L) { "Address range wraps around" }
    return "peek=${n.toString(16)}:40"
  }
  fun poke(addr: String, value: String, size: Int): String {
    require(size in listOf(1, 2, 4))
    val a = address(addr); val v = address(value)
    require(a + size - 1 <= 0xffffffffL && v <= (if (size == 4) 0xffffffffL else (1L shl (size * 8)) - 1)) {
      "Address or value exceeds the selected write size"
    }
    return "poke=${a.toString(16)}:${v.toString(16)}:$size"
  }
}
