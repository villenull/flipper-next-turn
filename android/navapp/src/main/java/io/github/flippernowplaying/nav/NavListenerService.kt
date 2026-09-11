package io.github.flippernowplaying.nav

import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Forwards only Google Maps navigation notifications; everything else is ignored. */
class NavListenerService : NotificationListenerService() {
 companion object {
  private const val MAPS = "com.google.android.apps.maps"
  @Volatile private var instance: NavListenerService? = null
  fun current(): NavListenerService? = instance
 }
 private fun forward(sbn: StatusBarNotification?) {
  if(sbn?.packageName != MAPS || sbn.isOngoing != true) return
  val e = sbn.notification?.extras ?: return
  NavService.route(e.getCharSequence("android.title")?.toString(), e.getCharSequence("android.text")?.toString(), e.getCharSequence("android.subText")?.toString())
 }
 override fun onListenerConnected() { instance = this }
 override fun onListenerDisconnected() { instance = null }
 override fun onNotificationPosted(sbn: StatusBarNotification) { forward(sbn) }
 override fun onNotificationRemoved(sbn: StatusBarNotification) {
  if(sbn?.packageName == MAPS) NavService.route(null, null, null)
 }
 @Suppress("unused")
 fun component(): ComponentName = ComponentName(this, NavListenerService::class.java)
 fun api(): Int = Build.VERSION.SDK_INT
}
