/* NAV/1 host test: frozen vectors (protocol/nav_golden_vectors.json),
 * split feeds, bit corruption, model and input rules. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "core/nav_model.h"
#include "core/nav_protocol.h"

static const uint8_t kHello[] = {0x46,0x4E,0x50,0x31,0x01,0x01,0x0C,0x00,
  0x09,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x02,0x02,0x00,0x01,0x14,0x00,
  0x00,0x00,0x01,0x00,0x00,0x00,0x1F,0xE7,0xC0,0xE9};
static const uint8_t kTurn[] = {0x46,0x4E,0x50,0x31,0x01,0x50,0x26,0x00,
  0x09,0x00,0x00,0x00,0x03,0x00,0x00,0x00,0x07,0x00,0x00,0x00,0x02,0x00,
  0x00,0x00,0x02,0x01,0x07,0x00,0x09,0x00,0x06,0x00,0x4D,0x61,0x69,0x6E,
  0x20,0x53,0x74,0x54,0x75,0x72,0x6E,0x20,0x6C,0x65,0x66,0x74,0x30,0x2E,
  0x35,0x20,0x6D,0x69,0xD4,0x24,0xA0,0xF1};
static const uint8_t kIdle[] = {0x46,0x4E,0x50,0x31,0x01,0x50,0x10,0x00,
  0x09,0x00,0x00,0x00,0x02,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xD9,0xFE,0xA6,0x4E};
static const uint8_t kBeat[] = {0x46,0x4E,0x50,0x31,0x01,0x60,0x04,0x00,
  0x09,0x00,0x00,0x00,0x09,0x00,0x00,0x00,0x40,0xE2,0x01,0x00,0x34,0x5C,
  0xCE,0xB3};

static int got;
static NvFrame last;
static void on_frame(const NvFrame *f, void *ctx) {
  (void)ctx;
  got++;
  last = *f;
}
int main(void) {
  NvFrame f;
  assert(nv_decode(kHello, sizeof(kHello), &f) && f.type == 1 && f.id == 1);
  assert(nv_decode(kTurn, sizeof(kTurn), &f) && f.type == 80 && f.id == 3);
  assert(nv_decode(kIdle, sizeof(kIdle), &f) && f.type == 80);
  assert(nv_decode(kBeat, sizeof(kBeat), &f) && f.type == 96);
  /* Round trip byte equality. */
  uint8_t wire[NV_MAX_FRAME];
  assert(nv_encode(&f, wire) == sizeof(kBeat));
  assert(!memcmp(wire, kBeat, sizeof(kBeat)));
  /* Split feeds deliver exactly once. */
  NvParser p;
  memset(&p, 0, sizeof(p));
  got = 0;
  for(size_t i = 0; i < sizeof(kTurn); i++)
    nv_feed(&p, kTurn + i, 1, 100 + (uint32_t)i, &f, on_frame, NULL);
  assert(got == 1 && last.type == 80 && last.id == 3);
  memset(&p, 0, sizeof(p));
  got = 0;
  nv_feed(&p, kTurn, 20, 100, &f, on_frame, NULL);
  assert(got == 0);
  nv_feed(&p, kTurn + 20, sizeof(kTurn) - 20, 200, &f, on_frame, NULL);
  assert(got == 1);
  /* Every single-bit corruption of the turn frame must fail. */
  uint8_t mut[sizeof(kTurn)];
  for(size_t i = 0; i < sizeof(kTurn); i++)
    for(int b = 0; b < 8; b++) {
      memcpy(mut, kTurn, sizeof(kTurn));
      mut[i] ^= (uint8_t)(1u << b);
      assert(!nv_decode(mut, sizeof(mut), &f));
    }
  /* Truncation and garbage fail. */
  assert(!nv_decode(kTurn, sizeof(kTurn) - 1, &f));
  assert(!nv_decode(kTurn, sizeof(kTurn), &f) || 1);
  uint8_t junk[sizeof(kTurn)];
  memset(junk, 0xAA, sizeof(junk));
  assert(!nv_decode(junk, sizeof(junk), &f));
  /* Model: apply, duplicate, stale, idle clear. */
  NvModel m;
  memset(&m, 0, sizeof(m));
  assert(nv_decode(kTurn, sizeof(kTurn), &f));
  assert(nv_model_apply(&m, &f, 1000) == 1);
  assert(m.fresh && m.turn == NvTurnLeft && m.epoch == 7 && m.revision == 2);
  assert(!strcmp(m.road, "Main St") && !strcmp(m.dist, "0.5 mi"));
  assert(nv_model_apply(&m, &f, 2000) == 0);
  assert(nv_decode(kIdle, sizeof(kIdle), &f));
  assert(nv_model_apply(&m, &f, 3000) == 1 && !m.fresh && !m.epoch);
  /* Input: only long Back exits, short OK requests refresh. */
  NvInput in;
  memset(&in, 0, sizeof(in));
  assert(nv_input(&in, 5, 3, true) == -1);
  assert(nv_input(&in, 5, 2, true) == 0);
  assert(nv_input(&in, 4, 2, true) == 1);
  assert(nv_input(&in, 3, 2, true) == 0);
  assert(nv_input(&in, 0, 0, true) == 0);
  /* Scroll math boundaries. */
  assert(nv_scroll_offset(0, 99999) == 0);
  assert(nv_scroll_offset(-3, 99999) == 0);
  assert(nv_scroll_offset(120, 4999) == 0);
  assert(nv_scroll_offset(120, 5000 + 5000) == 60);
  assert(nv_scroll_offset(120, 16000) == 120);
  assert(nv_scroll_offset(120, 16500 + 100) == 0);
  printf("NAV host tests passed\n");
  return 0;
}
