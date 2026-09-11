package io.github.flippernowplaying.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavTest {
 @Test fun vectors() {
  NavProtocol.validate(1, byteArrayOf(2, 2, 0, 1, 20, 0, 0, 0, 1, 0, 0, 0))
  NavProtocol.validate(80, NavDomain.snapshot(7, 2, Guidance(2, 1, "Main St", "Turn left", "0.5 mi")))
  NavProtocol.validate(81, NavProtocol.buffer(8).putInt(7).putInt(2).array())
  NavProtocol.validate(82, byteArrayOf())
 }
 @Test fun parseCases() {
  val left = NavParse.parse("Main St", "0.5 mi · Turn left")!!
  assertEquals(2, left.turn); assertEquals("Main St", left.road); assertEquals("0.5 mi", left.dist)
  assertTrue("Turn left" in left.instr)
  assertEquals(9, NavParse.parse("Elm Ave", "300 ft · At the roundabout, take the 2nd exit")!!.turn)
  assertEquals(0, NavParse.parse("Rerouting", "Finding new route")!!.turn)
  assertNull(NavParse.parse("GPS signal lost", "Searching for GPS"))
  assertNull(NavParse.parse("Google Maps", "Start driving"))
  assertEquals(14, NavParse.parse("Home", "You have arrived · 0 ft")!!.turn)
  assertEquals(12, NavParse.parse("I-95 N", "Keep left · 1.2 mi")!!.turn)
  assertEquals(8, NavParse.parse("Oak Rd", "U-turn · 500 ft")!!.turn)
  val road = NavParse.parse("Café Müller Straße", "Turn right · 200 m")!!
  assertEquals(3, road.turn)
  assertTrue(road.road.all { it.code in 32..126 })
 }
}
