package io.github.flippernowplaying.nav

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.widget.*
import java.util.UUID

class NavActivity : Activity() {
 private val h = Handler(Looper.getMainLooper()); private lateinit var text: TextView; private lateinit var deviceText: TextView
 private lateinit var list: LinearLayout; private var scanning = false; private var scanner: BluetoothLeScanner? = null
 private val found = mutableSetOf<String>(); private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
 private fun needs(): Array<String> = (if(Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
 private fun access(): Boolean {
  val component = ComponentName(this, NavListenerService::class.java)
  return if(Build.VERSION.SDK_INT >= 27) getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
  else Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).contains(component)
 }
 private fun button(parent: LinearLayout, label: String, click: () -> Unit) { parent.addView(Button(this).apply { text = label; setOnClickListener { click() } }) }
 override fun onCreate(saved: Bundle?) {
  super.onCreate(saved)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
  setContentView(ScrollView(this).apply { addView(root) })
  root.addView(TextView(this).apply { text = "Nav Turns"; textSize = 24f })
  root.addView(TextView(this).apply { text = "Shows Google Maps turn-by-turn on the Flipper. Only Maps navigation notifications are read; no history, no account, no Internet. Start keeps a visible connection service; reopen and Start after reboot or force-stop." })
  deviceText = TextView(this); root.addView(deviceText)
  text = TextView(this); root.addView(text)
  button(root, "Grant Bluetooth access") { val permissions = needs(); if(permissions.isNotEmpty()) requestPermissions(permissions, 1) }
  button(root, "Enable notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
  root.addView(TextView(this).apply { text = "If Android restricts this setting for a sideloaded app, review the app's system App info and its user-controlled Allow restricted settings option." })
  if(Build.VERSION.SDK_INT >= 33) button(root, "Allow connection notifications") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2) }
  button(root, "Find Nav Turns devices") { scan() }
  list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(list)
  button(root, "Start") {
   if(needs().isNotEmpty()) { text.text = "Grant Bluetooth access first"; return@button }
   if(!access()) { text.text = "Enable notification access first"; return@button }
   if(prefs.getString("device", null) == null) { text.text = "Select the device advertising Nav Turns first"; return@button }
   stopScan(); startForegroundService(Intent(this, NavService::class.java))
  }
  button(root, "Stop") { stopService(Intent(this, NavService::class.java)) }
  button(root, "Export redacted diagnostics") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "nav-turns-diagnostics.txt"), 10) }
 }
 @SuppressLint("MissingPermission")
 private fun scan() {
  if(needs().isNotEmpty()) { text.text = "Grant Bluetooth permissions first"; return }
  stopScan(); list.removeAllViews(); found.clear()
  try {
   scanner = getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
   if(scanner == null) { text.text = "Enable Bluetooth and launch Nav Turns on the Flipper"; return }
   scanning = true; scanner!!.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(NavProtocol.SERVICE))).build()), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
   text.text = "Scanning for the dedicated Nav Turns service…"; h.postDelayed({ stopScan() }, 15000)
  } catch(_: SecurityException) { scanning = false; text.text = "Bluetooth permission was revoked" }
 }
 private val callback = object : ScanCallback() {
  @SuppressLint("MissingPermission") override fun onScanResult(type: Int, result: ScanResult) { h.post {
   if(!scanning || needs().isNotEmpty()) return@post
   try { val address = result.device.address
    if(found.add(address)) button(list, "${result.scanRecord?.deviceName ?: "Nav Turns"} · ${address.takeLast(5)}") {
     stopScan(); prefs.edit().putString("device", address).apply(); deviceText.text = "Selected Nav Turns device · ${address.takeLast(5)}. Tap Start, then confirm the matching system pairing code."
    }
   } catch(_: SecurityException) { text.text = "Bluetooth permission was revoked" }
  } }
  override fun onScanFailed(code: Int) { h.post { scanning = false; text.text = "Scan failed ($code). Check Bluetooth and retry." } }
 }
 @SuppressLint("MissingPermission") private fun stopScan() { if(scanning) try { scanner?.stopScan(callback) } catch(_: SecurityException) { }; scanning = false }
 private val refresh = object : Runnable { override fun run() {
  text.text = "Bluetooth permission: ${needs().isEmpty()}\nNotification access: ${access()}\nService: ${NavService.status}\n${NavService.preview}"
  deviceText.text = "Selected device: ${prefs.getString("device", null)?.takeLast(5) ?: "none"}"
  h.postDelayed(this, 1000)
 } }
 override fun onResume() { super.onResume(); h.post(refresh) }
 override fun onPause() { stopScan(); h.removeCallbacks(refresh); super.onPause() }
 @Deprecated("Platform activity result compatibility") override fun onActivityResult(request: Int, result: Int, data: Intent?) { super.onActivityResult(request, result, data)
  if(request == 10 && result == RESULT_OK) data?.data?.let { uri -> try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(NavService.report()) } } catch(_: Exception) { text.text = "Diagnostic export failed" } }
 }
}
