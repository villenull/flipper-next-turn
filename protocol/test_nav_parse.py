"""NAV/1 Maps-notification parser: executable spec for the Kotlin port.

The Kotlin NavParse must produce identical outputs for these cases.
Algorithm: normalize -> reroute? -> distance? -> maneuver keyword (priority
order) -> road/instr split -> sanitize/truncate.
"""
import re
import unittest

DIST = re.compile(r"(\d+(?:\.\d+)?\s*(?:mi|ft|km|m))\b", re.IGNORECASE)
SEP = re.compile(r"\s*[·•|–—]\s*|\s+[/\-]\s+")
WS = re.compile(r"\s+")

KEYWORDS = [
    (("u-turn", "u turn"), 8),
    (("roundabout", "traffic circle", "rotary"), 9),
    (("sharp left",), 6),
    (("sharp right",), 7),
    (("slight left",), 4),
    (("slight right",), 5),
    (("keep left", "keep right", "stay on"), 12),
    (("turn left",), 2),
    (("turn right",), 3),
    (("take exit", "take the exit", "use the exit"), 10),
    (("merge", "merge onto"), 11),
    (("ferry",), 13),
    (("arrive", "destination", "you have arrived"), 14),
    (("straight", "continue on", "continue straight", "head ",
      "go straight"), 1),
]

REROUTE = ("rerouting", "recalculating", "finding new route")
DEAD = ("gps signal lost", "searching for gps", "no gps")


def sanitize(s, n=64):
    return "".join(c if 32 <= ord(c) <= 126 else "?" for c in s)[:n]


def parse(title, text, sub=None):
    parts = [p for p in (title, text, sub) if p]
    raw = " ".join(parts)
    low = WS.sub(" ", raw).strip().lower()
    if any(k in low for k in DEAD):
        return None
    if any(k in low for k in REROUTE):
        return {"turn": 0, "flags": 3, "road": "",
                "instr": "Rerouting", "dist": ""}
    dm = DIST.search(raw)
    dist = dm.group(1) if dm else ""
    turn, key = 0, ""
    for words, tid in KEYWORDS:
        hit = next((w for w in words if w in low), None)
        if hit:
            turn, key = tid, hit
            break
    if not dist and turn == 0:
        return None
    segs = [s.strip() for s in SEP.split(raw) if s.strip()]
    isl = low
    instr = next((s for s in segs if key and key in s.lower()),
                 segs[0] if segs else "")
    cands = [s for s in segs
             if not DIST.search(s) and not (key and key in s.lower())]
    road = max(cands, key=len) if cands else ""
    if not road and title and not DIST.search(title) and \
            not (key and key in title.lower()):
        road = title.strip()
    _ = isl
    return {"turn": turn, "flags": 1, "road": sanitize(road),
            "instr": sanitize(instr), "dist": sanitize(dist)}


class Cases(unittest.TestCase):
    def test_turn_left(self):
        g = parse("Main St", "0.5 mi \u00b7 Turn left")
        self.assertEqual((g["turn"], g["road"], g["dist"]),
                         (2, "Main St", "0.5 mi"))
        self.assertIn("Turn left", g["instr"])

    def test_roundabout(self):
        g = parse("Elm Ave", "300 ft \u00b7 At the roundabout, take the 2nd exit")
        self.assertEqual(g["turn"], 9)
        self.assertEqual(g["dist"], "300 ft")

    def test_reroute(self):
        g = parse("Rerouting", "Finding new route")
        self.assertEqual((g["turn"], g["flags"], g["instr"]), (0, 3, "Rerouting"))

    def test_dead(self):
        self.assertIsNone(parse("GPS signal lost", "Searching for GPS"))

    def test_idle(self):
        self.assertIsNone(parse("Google Maps", "Start driving"))

    def test_arrive(self):
        g = parse("Home", "You have arrived \u00b7 0 ft")
        self.assertEqual((g["turn"], g["dist"]), (14, "0 ft"))

    def test_keep(self):
        g = parse("I-95 N", "Keep left \u00b7 1.2 mi")
        self.assertEqual((g["turn"], g["road"]), (12, "I-95 N"))

    def test_uturn(self):
        g = parse("Oak Rd", "U-turn \u00b7 500 ft")
        self.assertEqual(g["turn"], 8)

    def test_sanitize(self):
        g = parse("Caf\u00e9 M\u00fcller Stra\u00dfe", "Turn right \u00b7 200 m")
        self.assertEqual(g["turn"], 3)
        self.assertTrue(all(32 <= ord(c) <= 126 for c in g["road"]))


if __name__ == "__main__":
    unittest.main()
