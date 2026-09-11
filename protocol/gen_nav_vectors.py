"""Freeze NAV/1 golden vectors. Deterministic; rerun only to regenerate."""
import json
from pathlib import Path
from nav_reference_codec import encode, snapshot_payload

V = []


def add(name, typ, session, mid, payload):
    V.append({"name": name, "type": typ, "session": session, "id": mid,
              "payload": payload.hex(), "frame": encode(typ, session, mid,
                                                        payload).hex()})


HELLO = bytes((2, 2, 0, 1, 20, 0, 0, 0, 1, 0, 0, 0))
HELLO_ACK = bytes((2, 0, 0, 1, 20, 0, 0, 0, 1, 0, 0, 0))
add("hello_v2", 1, 9, 1, HELLO)
add("hello_ack_v2", 2, 9, 1, HELLO_ACK)
add("idle_snapshot", 80, 9, 2, snapshot_payload(0, 0, 0, 0, "", "", ""))
add("turn_left", 80, 9, 3, snapshot_payload(7, 2, 2, 1, "Main St",
                                            "Turn left", "0.5 mi"))
add("roundabout_exit", 80, 9, 4, snapshot_payload(7, 3, 9, 1,
                                                 "Elm Ave",
                                                 "At roundabout, 2nd exit",
                                                 "300 ft"))
add("reroute", 80, 9, 5, snapshot_payload(8, 1, 0, 3, "", "Rerouting",
                                          ""))
add("arrive", 80, 9, 6, snapshot_payload(9, 4, 14, 1, "Home",
                                         "Arrive", "0 ft"))
add("max_strings", 80, 9, 7, snapshot_payload(10, 5, 3, 1, "R" * 64,
                                              "I" * 64, "D" * 64))
import struct
add("snapshot_ack", 81, 9, 3, struct.pack("<II", 7, 2))
add("request", 82, 9, 8, b"")
add("heartbeat", 96, 9, 9, struct.pack("<I", 123456))
add("heartbeat_ack", 97, 9, 9, struct.pack("<I", 123456))
add("error", 126, 9, 10, struct.pack("<IHH", 3, 2, 0))
add("close", 127, 9, 11, bytes((1,)))

out = Path(__file__).with_name("nav_golden_vectors.json")
out.write_text(json.dumps(V, indent=1) + "\n")
print(f"wrote {len(V)} vectors to {out}")
