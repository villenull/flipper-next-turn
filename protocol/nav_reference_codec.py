"""NAV/1 reference codec: dependency-free encoder/decoder and validators.

Shares the FNP1/v1 frame envelope with FNP/1 (transports are separated by
BLE service UUID). Not a production transport; C and Kotlin ports must
independently match the frozen vectors in nav_golden_vectors.json.
"""
import struct
import zlib

MAGIC = b"FNP1"
VERSION = 1
MAX_PAYLOAD = 256
TURNS = {
    0: "none", 1: "straight", 2: "left", 3: "right",
    4: "slight-left", 5: "slight-right", 6: "sharp-left",
    7: "sharp-right", 8: "uturn", 9: "roundabout", 10: "exit",
    11: "merge", 12: "keep", 13: "ferry", 14: "arrive",
}


def _u16(b, o):
    return struct.unpack_from("<H", b, o)[0]


def _u32(b, o):
    return struct.unpack_from("<I", b, o)[0]


def validate(typ, payload):
    if len(payload) > MAX_PAYLOAD:
        raise ValueError("payload too large")
    if typ in (1, 2):
        if len(payload) != 12:
            raise ValueError("hello size")
        if _u16(payload, 2) != MAX_PAYLOAD or not 20 <= _u16(payload, 4) <= 128:
            raise ValueError("hello limits")
        if _u16(payload, 6) != 0 or _u32(payload, 8) != 1:
            raise ValueError("hello reserved")
        if typ == 1:
            if not 1 <= payload[0] <= payload[1]:
                raise ValueError("hello versions")
        else:
            if not ((1 <= payload[0] <= 2 and payload[1] == 0) or
                    (payload[0] == 0 and payload[1] == 1)):
                raise ValueError("hello ack versions")
    elif typ == 80:
        if len(payload) < 14:
            raise ValueError("snapshot too small")
        if payload[8] not in TURNS or payload[9] & ~3:
            raise ValueError("turn/flags")
        if len(payload) < 16:
            raise ValueError("snapshot header")
        rl, il, dl = _u16(payload, 10), _u16(payload, 12), _u16(payload, 14)
        if not all(v <= 64 for v in (rl, il, dl)):
            raise ValueError("string lengths")
        if 16 + rl + il + dl != len(payload):
            raise ValueError("snapshot framing")
        body = payload[16:]
        if any(b < 32 or b > 126 for b in body):
            raise ValueError("printable ASCII only")
        e, r = _u32(payload, 0), _u32(payload, 4)
        if (e == 0) != (r == 0):
            raise ValueError("epoch/revision paired")
        if e == 0 and (payload[8] != 0 or rl or il or dl):
            raise ValueError("idle snapshot must be zeroed")
    elif typ == 81:
        if len(payload) != 8 or _u32(payload, 0) == 0 or _u32(payload, 4) == 0:
            raise ValueError("snapshot ack")
    elif typ == 82:
        if len(payload) != 0:
            raise ValueError("request must be empty")
    elif typ in (96, 97):
        if len(payload) != 4:
            raise ValueError("heartbeat size")
    elif typ == 126:
        if len(payload) != 8 or _u16(payload, 4) not in (1, 2, 3, 4, 5, 6) \
                or _u16(payload, 6) != 0:
            raise ValueError("error format")
    elif typ == 127:
        if len(payload) != 1 or payload[0] not in (1, 2, 3):
            raise ValueError("close format")
    else:
        raise ValueError("unknown type")


def snapshot_payload(epoch, revision, turn, flags, road, instr, dist):
    parts = [s.encode("ascii") for s in (road, instr, dist)]
    head = struct.pack("<IIBBHHH", epoch, revision, turn, flags,
                       *(len(p) for p in parts))
    return head + b"".join(parts)


def encode(typ, session, mid, payload):
    if not 1 <= session <= 0xFFFFFFFF:
        raise ValueError("session")
    if not 1 <= mid <= 0xFFFFFFFF:
        raise ValueError("id")
    validate(typ, payload)
    head = MAGIC + bytes((VERSION, typ)) + struct.pack("<HII", len(payload),
                                                       session, mid)
    crc = zlib.crc32(head[4:] + payload) & 0xFFFFFFFF
    return head + payload + struct.pack("<I", crc)


def decode(frame):
    if len(frame) < 20 or frame[0:4] != MAGIC or frame[4] != VERSION:
        raise ValueError("envelope")
    n = _u16(frame, 6)
    if n > MAX_PAYLOAD or len(frame) != 20 + n:
        raise ValueError("length")
    if zlib.crc32(frame[4:16 + n]) & 0xFFFFFFFF != _u32(frame, 16 + n):
        raise ValueError("crc")
    typ, session, mid = frame[5], _u32(frame, 8), _u32(frame, 12)
    payload = frame[16:16 + n]
    if session == 0 or mid == 0:
        raise ValueError("ids")
    validate(typ, payload)
    return typ, session, mid, payload
