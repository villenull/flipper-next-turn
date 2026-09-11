package io.github.flippernowplaying.nav

import android.os.Handler

/** Guidance identity/epoch tracking. All calls happen on the service actor thread. */
class NavRepository(private val changed: (Boolean) -> Unit) {
 private var guidance: Guidance? = null
 private var identity: String? = null
 private var epochCounter = 0L
 var epoch = 0L; private set
 var revision = 0L; private set
 fun resetConnection() { epochCounter = 0; epoch = 0; revision = 0; identity = null }
 fun route(title: String?, text: String?, sub: String?) {
  val g = try { NavParse.parse(title, text, sub) } catch(_: Exception) { null }
  val key = g?.let { "${it.turn}|${it.flags}|${it.road}|${it.instr}|${it.dist}" }
  if(key == identity) { changed(false); return }
  identity = key; guidance = g
  if(g == null) { epoch = 0; revision = 0; changed(true); return }
  if(epochCounter == 0xffffffffL) { epoch = 0; revision = 0; changed(true); return }
  if(epoch == 0L) { epoch = ++epochCounter; revision = 1 } else revision++
  changed(true)
 }
 fun sample(): Triple<Long, Long, Guidance?> = Triple(epoch, revision, guidance)
 fun preview(): String = guidance?.let { "${it.dist} · ${it.instr}\n${it.road}" }.orEmpty()
}
