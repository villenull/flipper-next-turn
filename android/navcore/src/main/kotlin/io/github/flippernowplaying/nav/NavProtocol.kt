package io.github.flippernowplaying.nav

import java.nio.ByteBuffer
import java.nio.ByteOrder

object NavProtocol {
 const val SERVICE = "04b78bcf-1dac-4479-8eaa-db03c328d1e5"
 const val RX = "4bb67121-2f1f-5659-86c9-c1c956b5459f"
 const val TX = "e03b9f24-0a22-5cfa-b323-8c3adb1d7a20"
 const val MAX = 256
 fun buffer(n: Int): ByteBuffer = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
 fun wrap(p: ByteArray): ByteBuffer = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
 fun u32(p: ByteArray, o: Int): Long = wrap(p).getInt(o).toLong() and 0xffffffffL
 fun u16(p: ByteArray, o: Int): Int = wrap(p).getShort(o).toInt() and 65535
 fun ascii(p: ByteArray): Boolean = p.all { it.toInt() in 32..126 }
 fun validate(t: Int, p: ByteArray) {
  require(p.size <= MAX)
  when(t) {
   1, 2 -> {
    require(p.size == 12 && u16(p, 2) == MAX && u16(p, 4) in 20..128 && u16(p, 6) == 0 && u32(p, 8) == 1L)
    if(t == 1) require(p[0].toInt() in 1..p[1].toInt())
    else require((p[0].toInt() in 1..2 && p[1] == 0.toByte()) || (p[0] == 0.toByte() && p[1] == 1.toByte()))
   }
   80 -> {
    require(p.size >= 16)
    val e = u32(p, 0); val r = u32(p, 4)
    val rl = u16(p, 10); val il = u16(p, 12); val dl = u16(p, 14)
    val turn = p[8].toInt() and 255; val flags = p[9].toInt() and 255
    require(turn in 0..14 && flags and 3 == flags)
    require(rl <= 64 && il <= 64 && dl <= 64 && 16 + rl + il + dl == p.size)
    require(ascii(p.copyOfRange(16, p.size)))
    require((e == 0L) == (r == 0L))
    if(e == 0L) require(p[8] == 0.toByte() && rl == 0 && il == 0 && dl == 0)
   }
   81 -> require(p.size == 8 && u32(p, 0) > 0 && u32(p, 4) > 0)
   82 -> require(p.isEmpty())
   96, 97 -> require(p.size == 4)
   126 -> require(p.size == 8 && u16(p, 4) in 1..6 && u16(p, 6) == 0)
   127 -> require(p.size == 1 && p[0].toInt() in 1..3)
   else -> error("Unknown type")
  }
 }
}
