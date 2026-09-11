package io.github.flippernowplaying.nav

object NavDomain {
 fun hello(): ByteArray = byteArrayOf(1, 2, 0, 1, 20, 0, 0, 0, 1, 0, 0, 0)
 fun snapshot(epoch: Long, revision: Long, g: Guidance?): ByteArray {
  if(g == null) return NavProtocol.buffer(16).putInt(0).putInt(0).put(0).put(0).putShort(0).putShort(0).putShort(0).array()
  val r = g.road.toByteArray(); val i = g.instr.toByteArray(); val d = g.dist.toByteArray()
  require(r.size <= 64 && i.size <= 64 && d.size <= 64)
  return NavProtocol.buffer(16 + r.size + i.size + d.size).putInt(epoch.toInt()).putInt(revision.toInt()).put(g.turn.toByte()).put(g.flags.toByte()).putShort(r.size.toShort()).putShort(i.size.toShort()).putShort(d.size.toShort()).put(r).put(i).put(d).array()
 }
}
