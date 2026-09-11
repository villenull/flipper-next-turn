#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define NV_MAX_PAYLOAD 256
#define NV_MAX_FRAME 276
typedef struct {
  uint8_t type;
  uint32_t session, id;
  uint16_t length;
  uint8_t payload[NV_MAX_PAYLOAD];
} NvFrame;
typedef struct {
  uint8_t bytes[NV_MAX_FRAME];
  size_t used;
  uint32_t first, last, errors;
} NvParser;
typedef void (*NvReceive)(const NvFrame *, void *);
uint16_t nv_u16(const uint8_t *p);
uint32_t nv_u32(const uint8_t *p);
void nv_put(uint8_t *p, uint64_t v, size_t n);
uint32_t nv_crc(const uint8_t *p, size_t n);
bool nv_validate(uint8_t type, const uint8_t *p, size_t n);
bool nv_decode(const uint8_t *bytes, size_t n, NvFrame *out);
size_t nv_encode(const NvFrame *frame, uint8_t *out);
void nv_feed(NvParser *parser, const uint8_t *bytes, size_t n, uint32_t now,
             NvFrame *scratch, NvReceive cb, void *ctx);
bool nv_parser_expire(NvParser *parser, uint32_t now);
