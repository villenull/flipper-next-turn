package io.github.flippernowplaying.nav

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*

class NavService : Service() {
 companion object {
  @Volatile var status = "STOPPED"; private set
  @Volatile var preview = ""; private set
  @Volatile private var instance: NavService? = null
  private val diagnostics = ArrayDeque<String>()
  @Synchronized fun report(): String = "Nav Turns 0.1\nAndroid API ${Build.VERSION.SDK_INT}\n" + diagnostics.joinToString("\n")
  fun route(title: String?, text: String?, sub: String?) { instance?.let { s -> s.h.post { s.repo.route(title, text, sub) } } }
 }
 private lateinit var thread: HandlerThread; private lateinit var h: Handler; private lateinit var repo: NavRepository
 private var ble: NavBle? = null
 private var lastEpoch = -1L; private var lastRevision = -1L; private var armed = false
 private fun now() = SystemClock.elapsedRealtime()
 private fun notification(text: String): Notification {
  val open = PendingIntent.getActivity(this, 0, Intent(this, NavActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val stop = PendingIntent.getService(this, 1, Intent(this, NavService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this, "nav").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Nav Turns").setContentText(text).setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
 }
 private fun update(s: String) { status = s; synchronized(Companion) { diagnostics.addLast("${now()} $s"); while(diagnostics.size > 100) diagnostics.removeFirst() }
  if(armed) { try { getSystemService(NotificationManager::class.java).notify(1, notification(s)) } catch(_: SecurityException) { } }
 }
 override fun onCreate() {
  super.onCreate(); thread = HandlerThread("NavTurnsActor").also { it.start() }; h = Handler(thread.looper)
  repo = NavRepository({ full -> publish(full) }); instance = this
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("nav", "Connection", NotificationManager.IMPORTANCE_LOW))
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if(intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
  if(armed) return START_NOT_STICKY
  try {
   if(Build.VERSION.SDK_INT >= 29) startForeground(1, notification("Starting"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(1, notification("Starting"))
  } catch(_: SecurityException) { update("NEEDS_PERMISSION: open setup and grant Bluetooth"); stopSelf(); return START_NOT_STICKY }
  armed = true
  h.post {
   val address = getSharedPreferences("settings", MODE_PRIVATE).getString("device", null)
   if(address == null) { update("Select a Nav Turns device in setup"); stopSelf(); return@post }
   ble = NavBle(this, h, address, { update(it) }, { repo.resetConnection(); lastEpoch = -1; lastRevision = -1; publish(true) }, { publish(true) }).also { it.start() }
   h.postDelayed(heartbeat, 15000)
  }
  return START_NOT_STICKY
 }
 private fun publish(full: Boolean) {
  if(!armed) return
  val (epoch, revision, g) = repo.sample()
  preview = repo.preview()
  val snapshot = full || epoch != lastEpoch || revision != lastRevision
  lastEpoch = epoch; lastRevision = revision
  if(snapshot) ble?.snapshot(epoch, revision) { NavDomain.snapshot(epoch, revision, g) }
 }
 private val heartbeat = object : Runnable { override fun run() { if(!armed) return; ble?.enqueue(96, { NavProtocol.buffer(4).putInt(now().toInt()).array() }); h.postDelayed(this, 15000) } }
 override fun onDestroy() {
  instance = null; armed = false
  h.post { h.removeCallbacksAndMessages(null); ble?.stop(); thread.quitSafely() }
  stopForeground(STOP_FOREGROUND_REMOVE); status = "STOPPED"; preview = ""; super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder? = null
}
