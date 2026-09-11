package io.github.flippernowplaying.nav

data class Guidance(val turn: Int, val flags: Int, val road: String, val instr: String, val dist: String)

object NavParse {
 private val DIST = Regex("""(\d+(?:\.\d+)?\s*(?:mi|ft|km|m))\b""", RegexOption.IGNORE_CASE)
 private val SEP = Regex("""\s*[·•|–—]\s*|\s+[/\-]\s+""")
 private val WS = Regex("""\s+""")
 private val KEYS = listOf(
  listOf("u-turn", "u turn") to 8,
  listOf("roundabout", "traffic circle", "rotary") to 9,
  listOf("sharp left") to 6,
  listOf("sharp right") to 7,
  listOf("slight left") to 4,
  listOf("slight right") to 5,
  listOf("keep left", "keep right", "stay on") to 12,
  listOf("turn left") to 2,
  listOf("turn right") to 3,
  listOf("take exit", "take the exit", "use the exit") to 10,
  listOf("merge", "merge onto") to 11,
  listOf("ferry") to 13,
  listOf("arrive", "destination", "you have arrived") to 14,
  listOf("straight", "continue on", "continue straight", "head ", "go straight") to 1)
 private val REROUTE = listOf("rerouting", "recalculating", "finding new route")
 private val DEAD = listOf("gps signal lost", "searching for gps", "no gps")
 fun sanitize(s: String, n: Int = 64): String = s.map { if(it.code in 32..126) it else '?' }.joinToString("").take(n)
 fun parse(title: String?, text: String?, sub: String? = null): Guidance? {
  val raw = listOfNotNull(title, text, sub).joinToString(" ")
  val low = WS.replace(raw, " ").trim().lowercase()
  if(DEAD.any { it in low }) return null
  if(REROUTE.any { it in low }) return Guidance(0, 3, "", "Rerouting", "")
  val dist = DIST.find(raw)?.groupValues?.get(1) ?: ""
  var turn = 0; var key = ""
  for((words, tid) in KEYS) { val hit = words.firstOrNull { it in low }; if(hit != null) { turn = tid; key = hit; break } }
  if(dist.isEmpty() && turn == 0) return null
  val segs = SEP.split(raw).map { it.trim() }.filter { it.isNotEmpty() }
  val instr = segs.firstOrNull { key.isNotEmpty() && key in it.lowercase() } ?: segs.firstOrNull().orEmpty()
  val cands = segs.filter { DIST.find(it) == null && !(key.isNotEmpty() && key in it.lowercase()) }
  var road = cands.maxByOrNull { it.length }.orEmpty()
  if(road.isEmpty() && title != null && DIST.find(title) == null && !(key.isNotEmpty() && key in title.lowercase())) road = title.trim()
  return Guidance(turn, 1, sanitize(road), sanitize(instr), sanitize(dist))
 }
}
