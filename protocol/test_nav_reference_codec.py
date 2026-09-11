"""Executable NAV/1 checks: frozen vectors decode and malformed frames die."""
import json
import struct
import unittest
from pathlib import Path
from nav_reference_codec import decode, encode, snapshot_payload, validate

VECTORS = json.loads(Path(__file__).with_name(
    "nav_golden_vectors.json").read_text())


class Vectors(unittest.TestCase):
    def test_count(self):
        self.assertEqual(len(VECTORS), 14)

    def test_round_trip(self):
        for v in VECTORS:
            frame = bytes.fromhex(v["frame"])
            typ, session, mid, payload = decode(frame)
            self.assertEqual(typ, v["type"])
            self.assertEqual(session, v["session"])
            self.assertEqual(mid, v["id"])
            self.assertEqual(payload.hex(), v["payload"])
            self.assertEqual(encode(typ, session, mid, payload), frame)

    def test_malformed(self):
        base = bytes.fromhex(VECTORS[3]["frame"])
        for bad in (base[:10], base + b"\x00", base[:-4] + b"\x00\x00\x00\x00",
                    b"XXXX" + base[4:], base[:5] + b"\x7f" + base[6:]):
            with self.assertRaises(ValueError):
                decode(bad)
        with self.assertRaises(ValueError):
            validate(80, snapshot_payload(1, 1, 99, 0, "R", "I", "D"))
        with self.assertRaises(ValueError):
            validate(80, snapshot_payload(0, 1, 2, 0, "R", "I", "D"))
        with self.assertRaises(ValueError):
            validate(80, snapshot_payload(1, 1, 2, 0, "R" * 65, "", ""))
        with self.assertRaises(ValueError):
            validate(80, b"\xc3\xa9" + bytes(14))


if __name__ == "__main__":
    unittest.main()
