package io.github.flippernowplaying.nav

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.os.*
import java.security.SecureRandom
import java.util.UUID

/** All state is owned by the service's serial Handler. Callbacks copy borrowed data. */
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
class NavBle(private val context: Context, private val h: Handler, private val address: String,
 private val status: (String) -> Unit, private val synced: () -> Unit, private val refresh: () -> Unit) {
 private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
 private var gatt: BluetoothGatt? = null
 private var rx: BluetoothGattCharacteristic? = null
 private var tx: BluetoothGattCharacteristic? = null
 private var stopped = true; private var generation = 0; private val operations = GattGate()
 private val pending: String get() = operations.current?.name.orEmpty()
 private var stage = "STOPPED"; private var retry = 0
 private val parser = NavDecoder(); private val publisher = NavQueue()
 private var active: ByteArray? = null; private var offset = 0; private var sent: (() -> Unit)? = null
 private var session = 0L; private var id = 0L; private var lastId = 0L; private var lastPeer = 0L; private var errorBase = 0; private var errorAt = 0L
 private var snapshotEpoch = 0L; private var snapshotRev = 0L
 var ready = false; private set
 private fun now() = SystemClock.elapsedRealtime()
 private fun state(s: String) { stage = s; status(s) }
 private fun safe(block: () -> Unit) { try { block() } catch(_: SecurityException) { stop(); status("NEEDS_PERMISSION: repair Bluetooth access") } catch(_: IllegalArgumentException) { fail("Invalid device or protocol") } }
 private val receiver = object : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) { h.post { safe {
   if(stopped) return@safe
   when(i.action) {
    BluetoothAdapter.ACTION_STATE_CHANGED -> if(adapter?.isEnabled == true) { if(gatt == null) connect(true) } else { close(); state("BLUETOOTH_OFF") }
    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
     @Suppress("DEPRECATION") val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
     if(d?.address == address && stage == "SECURING") when(d.bondState) {
      BluetoothDevice.BOND_BONDED -> discover()
      BluetoothDevice.BOND_NONE -> { stop(); status("Pairing rejected or failed. Open setup and Start to retry.") }
     }
    }
   }
  } } }
 }
 private var registered = false
 fun start() { if(!stopped) return; stopped = false; registered = true
  val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply { addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) }
  if(Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else context.registerReceiver(receiver, filter)
  safe { connect(false) }; h.postDelayed(tick, 1000)
 }
 private fun connect(auto: Boolean) {
  if(stopped || gatt != null) return
  if(adapter?.isEnabled != true) { state("BLUETOOTH_OFF"); return }
  generation++; val gen = generation
  state(if(auto) "ARMED_WAITING" else "CONNECTING")
  session = generateSequence { SecureRandom().nextInt().toLong() and 0xffffffffL }.first { it != 0L }
  gatt = adapter.getRemoteDevice(address).connectGatt(context, auto, callback, BluetoothDevice.TRANSPORT_LE)
  if(!auto) h.postDelayed({ if(!stopped && gen == generation && stage == "CONNECTING") fail("Connection timeout") }, 15000)
 }
 private fun close() {
  generation++; operations.reset(); ready = false; rx = null; tx = null; active = null; sent = null; offset = 0
  publisher.clear(); parser.reset(); session = 0; id = 0; lastId = 0; snapshotEpoch = 0; snapshotRev = 0
  val old = gatt; gatt = null
  try { old?.disconnect() } catch(_: SecurityException) { } finally { try { old?.close() } catch(_: SecurityException) { } }
 }
 fun stop() { stopped = true; close(); h.removeCallbacks(tick); if(registered) { context.unregisterReceiver(receiver); registered = false }; state("STOPPED") }
 private fun fail(reason: String) { if(stopped) return; close(); state("ERROR: $reason; waiting to reconnect")
  val gen = generation; val delay = listOf(1000L, 2000L, 4000L, 8000L, 15000L, 30000L)[retry.coerceAtMost(5)]; retry++
  h.postDelayed({ if(!stopped && gen == generation) safe { connect(true) } }, delay + (0..250).random())
 }
 private fun begin(name: String, call: () -> Boolean) {
  val ticket = operations.begin(generation, name, now(), if(name == "discover") 10000 else 5000)
  if(!call()) { fail("$name request refused"); return }
  h.postDelayed({ if(!stopped && operations.expired(ticket, now())) fail("$name timed out") }, ticket.deadline - now())
 }
 private fun complete(name: String, result: Int, body: () -> Unit) {
  if(!operations.complete(generation, name)) return
  if(result != BluetoothGatt.GATT_SUCCESS) fail("$name GATT status $result") else body()
 }
 private fun discover() { state("DISCOVERING"); begin("discover") { gatt?.discoverServices() == true } }
 private fun writeCccd() {
  val g = gatt ?: return; val c = tx ?: return
  val d = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) ?: return fail("Missing indication CCCD")
  if(!g.setCharacteristicNotification(c, true)) return fail("Local indication subscription failed")
  state("SUBSCRIBING")
  begin("cccd") {
   if(Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == BluetoothStatusCodes.SUCCESS
   else { d.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE; @Suppress("DEPRECATION") g.writeDescriptor(d) }
  }
 }
 private fun hello() {
  state("HANDSHAKING"); lastPeer = now(); errorAt = now(); errorBase = parser.errors
  enqueue(1, { NavDomain.hello() })
  val gen = generation; h.postDelayed({ if(gen == generation && stage == "HANDSHAKING") fail("HELLO acknowledgement timeout") }, 5000)
 }
 fun enqueue(type: Int, payload: () -> ByteArray, onSent: (Long) -> Unit = {}) {
  if(stopped || gatt == null || stage !in listOf("HANDSHAKING", "SYNCING", "READY")) return
  if(!publisher.enqueue(NavQueue.Pending(type, payload, onSent))) { fail("Control queue overflow"); return }
  pump()
 }
 fun snapshot(epoch: Long, revision: Long, payload: () -> ByteArray) {
  snapshotEpoch = epoch; snapshotRev = revision
  enqueue(80, payload) { snapshotEpoch = epoch; snapshotRev = revision
   val gen = generation
   if(!ready) h.postDelayed({ if(gen == generation && !ready) fail("Snapshot acknowledgement timeout") }, 10000)
  }
 }
 private fun pump() {
  if(active != null || pending.isNotEmpty() || stopped) return
  val p = publisher.next() ?: return
  if(id == 0xffffffffL) return fail("Message counter exhausted")
  val bytes = p.payload()
  val messageId = ++id; active = NavFrame(p.type, session, messageId, bytes).encode(); offset = 0
  sent = { p.sent(messageId) }; writeChunk()
 }
 private fun writeChunk() {
  val bytes = active ?: return; val g = gatt ?: return; val c = rx ?: return
  val value = bytes.copyOfRange(offset, (offset + 20).coerceAtMost(bytes.size))
  begin("write") {
   if(Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
   else { c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; c.value = value; @Suppress("DEPRECATION") g.writeCharacteristic(c) }
  }
 }
 private fun receive(f: NavFrame, at: Long) {
  if(f.session != session) return fail("Wrong session")
  if(f.id <= lastId) return fail("Old message ID")
  if(f.type !in listOf(2, 81, 82, 97, 126, 127)) return fail("Wrong message direction")
  lastId = f.id; lastPeer = at
  when(f.type) {
   2 -> { if(stage != "HANDSHAKING" || f.id != 1L || f.payload[0].toInt() !in 0..2 || f.payload[1] != 0.toByte()) return fail("Protocol version or handshake rejected")
    state("SYNCING"); synced() }
   81 -> { if(NavProtocol.u32(f.payload, 0) == snapshotEpoch && NavProtocol.u32(f.payload, 4) == snapshotRev) { ready = true; retry = 0; state("READY") } }
   82 -> refresh()
   97 -> { }
   127 -> fail("Flipper closed")
   126 -> fail("Peer protocol error ${NavProtocol.u16(f.payload, 4)}")
  }
 }
 private val callback = object : BluetoothGattCallback() {
  private fun post(g: BluetoothGatt, body: () -> Unit) { h.post { if(g === gatt && !stopped) safe(body) } }
  override fun onConnectionStateChange(g: BluetoothGatt, s: Int, newState: Int) = post(g) {
   if(s != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) fail("Link disconnected ($s)")
   else if(newState == BluetoothProfile.STATE_CONNECTED) {
    if(g.device.bondState == BluetoothDevice.BOND_BONDED) discover() else { state("SECURING"); status("SECURING: confirm the matching code on both devices")
     if(g.device.bondState != BluetoothDevice.BOND_BONDING && !g.device.createBond()) fail("Pairing could not start") }
   }
  }
  override fun onServicesDiscovered(g: BluetoothGatt, s: Int) = post(g) { complete("discover", s) {
   val service = g.getService(UUID.fromString(NavProtocol.SERVICE)); rx = service?.getCharacteristic(UUID.fromString(NavProtocol.RX)); tx = service?.getCharacteristic(UUID.fromString(NavProtocol.TX))
   if(rx == null || tx == null || rx!!.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 || tx!!.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE == 0) fail("Not a compatible Nav Turns service") else writeCccd()
  } }
  override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, s: Int) = post(g) { if(d.characteristic.uuid == tx?.uuid) complete("cccd", s) { hello() } }
  override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) = post(g) { if(c.uuid == rx?.uuid) complete("write", s) {
   offset += 20
   if(offset >= (active?.size ?: 0)) { active = null; val done = sent; sent = null; done?.invoke(); pump() } else writeChunk()
  } }
  private fun data(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray) { val copy = bytes.copyOf(); val at = now(); post(g) {
   if(c.uuid != tx?.uuid) return@post
   if(copy.size > 128) return@post fail("Oversized characteristic value")
   parser.feed(copy, at) { receive(it, at) }
  } }
  override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = data(g, c, value)
  @Deprecated("Legacy callback for API 26–32")
  override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { if(Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION") val bytes = c.value; data(g, c, bytes ?: byteArrayOf()) } }
 }
 private val tick = object : Runnable { override fun run() { if(stopped) return; safe {
  val n = now()
  if(parser.expire(n)) fail("Partial frame timeout")
  if(n - errorAt >= 10000) { errorAt = n; errorBase = parser.errors }
  if(parser.errors - errorBase >= 3) fail("Repeated framing errors")
  if(stage in listOf("READY", "SYNCING") && n - lastPeer >= 35000) fail("Peer stale")
 }; if(!stopped) h.postDelayed(this, 1000) } }
}

private data class NavTicket(val generation: Int, val name: String, val deadline: Long)
private class GattGate {
 data class Op(val name: String)
 var current: Op? = null; private set
 private var ticket = 0; private val live = mutableMapOf<Int, NavTicket>()
 fun begin(generation: Int, name: String, now: Long, timeout: Long): NavTicket {
  current = Op(name); ticket++; val t = NavTicket(generation, name, now + timeout); live[ticket] = t; return t
 }
 fun complete(generation: Int, name: String): Boolean {
  val hit = live.entries.firstOrNull { it.value.generation == generation && it.value.name == name } ?: return false
  live.remove(hit.key); if(current?.name == name) current = null; return true
 }
 fun expired(t: NavTicket, now: Long): Boolean = now >= t.deadline && live.containsValue(t)
 fun reset() { current = null; live.clear() }
}

private data class NavFrame(val type: Int, val session: Long, val id: Long, val payload: ByteArray) {
 fun encode(): ByteArray {
  require(session in 1..0xffffffffL && id in 1..0xffffffffL); NavProtocol.validate(type, payload)
  val b = NavProtocol.buffer(20 + payload.size).put("FNP1".toByteArray()).put(1).put(type.toByte()).putShort(payload.size.toShort()).putInt(session.toInt()).putInt(id.toInt()).put(payload)
  val crc = java.util.zip.CRC32().also { it.update(b.array().copyOfRange(4, 16 + payload.size)) }.value
  b.putInt(crc.toInt()); return b.array()
 }
}

private class NavDecoder {
 private val bytes = ByteArray(276); var used = 0; private set
 var errors = 0; private set
 private var first = 0L; private var last = 0L
 fun reset() { used = 0 }
 fun expire(now: Long): Boolean {
  if(used > 0 && (now - last >= 5000 || now - first >= 15000)) { reset(); errors++; return true }; return false
 }
 private fun drop(n: Int) { bytes.copyInto(bytes, 0, n, used); used -= n }
 private fun decodeOne(n: Int): NavFrame? {
  if(!(bytes[0] == 70.toByte() && bytes[1] == 78.toByte() && bytes[2] == 80.toByte() && bytes[3] == 49.toByte() && bytes[4] == 1.toByte())) return null
  val len = NavProtocol.u16(bytes, 6); if(len > NavProtocol.MAX || n != 20 + len) return null
  val crc = java.util.zip.CRC32().also { it.update(bytes.copyOfRange(4, 16 + len)) }.value
  if(crc != NavProtocol.u32(bytes, 16 + len)) return null
  val f = NavFrame(bytes[5].toInt() and 255, NavProtocol.u32(bytes, 8), NavProtocol.u32(bytes, 12), bytes.copyOfRange(16, 16 + len))
  if(f.session == 0L || f.id == 0L) return null
  return try { NavProtocol.validate(f.type, f.payload); f } catch(_: IllegalArgumentException) { null }
 }
 fun feed(input: ByteArray, now: Long, receive: (NavFrame) -> Unit) {
  expire(now)
  for(byte in input) {
   if(used == 0) first = now
   last = now
   if(used == 276) { drop(1); errors++ }
   bytes[used++] = byte
   while(used >= 4) {
    if(!(bytes[0] == 70.toByte() && bytes[1] == 78.toByte() && bytes[2] == 80.toByte() && bytes[3] == 49.toByte())) { drop(1); continue }
    if(used < 16) break
    val n = NavProtocol.u16(bytes, 6)
    if(bytes[4] != 1.toByte() || n > NavProtocol.MAX) { drop(1); errors++; continue }
    if(used < 20 + n) break
    val f = decodeOne(20 + n)
    if(f == null) { drop(1); errors++ } else { drop(20 + n); receive(f) }
   }
  }
 }
}

private class NavQueue {
 data class Pending(val type: Int, val payload: () -> ByteArray, val sent: (Long) -> Unit)
 private val q = ArrayDeque<Pending>()
 fun enqueue(p: Pending): Boolean { if(q.size >= 8) return false; q.addLast(p); return true }
 fun next(): Pending? = q.removeFirstOrNull()
 fun clear() { q.clear() }
}
