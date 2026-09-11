#include "nav_protocol.h"
#include <string.h>

uint16_t nv_u16(const uint8_t *p) {
  return (uint16_t)(p[0] | ((uint16_t)p[1] << 8));
}
uint32_t nv_u32(const uint8_t *p) {
  return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) |
         ((uint32_t)p[3] << 24);
}
void nv_put(uint8_t *p, uint64_t v, size_t n) {
  for(size_t i = 0; i < n; i++) p[i] = (uint8_t)(v >> (8 * i));
}
uint32_t nv_crc(const uint8_t *p, size_t n) {
  uint32_t crc = 0xFFFFFFFFu;
  for(size_t i = 0; i < n; i++) {
    crc ^= p[i];
    for(int k = 0; k < 8; k++) crc = (crc >> 1) ^ (0xEDB88320u & -(crc & 1u));
  }
  return ~crc;
}
static bool ascii(const uint8_t *p, size_t n) {
  for(size_t i = 0; i < n; i++)
    if(p[i] < 32 || p[i] > 126) return false;
  return true;
}
bool nv_validate(uint8_t type, const uint8_t *p, size_t n) {
  if(n > NV_MAX_PAYLOAD) return false;
  switch(type) {
  case 1:
  case 2: {
    if(n != 12 || nv_u16(p + 2) != NV_MAX_PAYLOAD) return false;
    uint16_t mtu = nv_u16(p + 4);
    if(mtu < 20 || mtu > 128 || nv_u16(p + 6) != 0 || nv_u32(p + 8) != 1)
      return false;
    if(type == 1) return p[0] >= 1 && p[0] <= p[1];
    return (p[0] >= 1 && p[0] <= 2 && p[1] == 0) || (p[0] == 0 && p[1] == 1);
  }
  case 80: {
    if(n < 16) return false;
    uint32_t e = nv_u32(p), r = nv_u32(p + 4);
    uint16_t rl = nv_u16(p + 10), il = nv_u16(p + 12), dl = nv_u16(p + 14);
    if(p[8] > 14 || (p[9] & ~3u)) return false;
    if(rl > 64 || il > 64 || dl > 64) return false;
    if(16u + rl + il + dl != n) return false;
    if(!ascii(p + 16, rl + il + dl)) return false;
    if((e == 0) != (r == 0)) return false;
    if(!e && (p[8] || rl || il || dl)) return false;
    return true;
  }
  case 81:
    return n == 8 && nv_u32(p) && nv_u32(p + 4);
  case 82:
    return n == 0;
  case 96:
  case 97:
    return n == 4;
  case 126:
    return n == 8 && nv_u16(p + 4) >= 1 && nv_u16(p + 4) <= 6 &&
           nv_u16(p + 6) == 0;
  case 127:
    return n == 1 && p[0] >= 1 && p[0] <= 3;
  default:
    break;
  }
  return false;
}
bool nv_decode(const uint8_t *bytes, size_t n, NvFrame *out) {
  if(n < 20 || n > NV_MAX_FRAME) return false;
  if(!(bytes[0] == 'F' && bytes[1] == 'N' && bytes[2] == 'P' &&
       bytes[3] == '1' && bytes[4] == 1))
    return false;
  uint16_t len = nv_u16(bytes + 6);
  if(len > NV_MAX_PAYLOAD || n != (size_t)20 + len) return false;
  uint32_t want = (uint32_t)bytes[16 + len] |
                  ((uint32_t)bytes[17 + len] << 8) |
                  ((uint32_t)bytes[18 + len] << 16) |
                  ((uint32_t)bytes[19 + len] << 24);
  if(nv_crc(bytes + 4, 12 + len) != want) return false;
  uint32_t session = nv_u32(bytes + 8), id = nv_u32(bytes + 12);
  if(!session || !id) return false;
  if(!nv_validate(bytes[5], bytes + 16, len)) return false;
  out->type = bytes[5];
  out->session = session;
  out->id = id;
  out->length = len;
  if(len) memcpy(out->payload, bytes + 16, len);
  return true;
}
size_t nv_encode(const NvFrame *frame, uint8_t *out) {
  if(!frame->session || !frame->id || frame->length > NV_MAX_PAYLOAD)
    return 0;
  if(!nv_validate(frame->type, frame->payload, frame->length)) return 0;
  out[0] = 'F';
  out[1] = 'N';
  out[2] = 'P';
  out[3] = '1';
  out[4] = 1;
  out[5] = frame->type;
  nv_put(out + 6, frame->length, 2);
  nv_put(out + 8, frame->session, 4);
  nv_put(out + 12, frame->id, 4);
  if(frame->length) memcpy(out + 16, frame->payload, frame->length);
  nv_put(out + 16 + frame->length, nv_crc(out + 4, 12 + frame->length), 4);
  return (size_t)20 + frame->length;
}
static void nv_drop(NvParser *parser, size_t n) {
  if(n >= parser->used) {
    parser->used = 0;
    return;
  }
  memmove(parser->bytes, parser->bytes + n, parser->used - n);
  parser->used -= n;
}
void nv_feed(NvParser *parser, const uint8_t *bytes, size_t n, uint32_t now,
             NvFrame *scratch, NvReceive cb, void *ctx) {
  nv_parser_expire(parser, now);
  for(size_t i = 0; i < n; i++) {
    if(!parser->used) parser->first = now;
    parser->last = now;
    if(parser->used == NV_MAX_FRAME) {
      nv_drop(parser, 1);
      parser->errors++;
    }
    parser->bytes[parser->used++] = bytes[i];
    for(;;) {
      if(parser->used < 4) break;
      if(!(parser->bytes[0] == 'F' && parser->bytes[1] == 'N' &&
           parser->bytes[2] == 'P' && parser->bytes[3] == '1')) {
        nv_drop(parser, 1);
        continue;
      }
      if(parser->used < 16) break;
      uint16_t len = nv_u16(parser->bytes + 6);
      if(parser->bytes[4] != 1 || len > NV_MAX_PAYLOAD) {
        nv_drop(parser, 1);
        parser->errors++;
        continue;
      }
      if(parser->used < (size_t)20 + len) break;
      if(nv_decode(parser->bytes, (size_t)20 + len, scratch)) {
        nv_drop(parser, (size_t)20 + len);
        cb(scratch, ctx);
      } else {
        nv_drop(parser, 1);
        parser->errors++;
      }
    }
  }
}
bool nv_parser_expire(NvParser *parser, uint32_t now) {
  if(parser->used &&
     (now - parser->last >= 5000 || now - parser->first >= 15000)) {
    parser->used = 0;
    parser->errors++;
    return true;
  }
  return false;
}
