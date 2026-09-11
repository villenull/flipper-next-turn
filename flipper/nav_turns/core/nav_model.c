#include "nav_model.h"
#include <string.h>

static void copy(char *dst, const uint8_t *src, uint16_t n) {
  if(n > 64) n = 64;
  memcpy(dst, src, n);
  dst[n] = 0;
}
int nv_model_apply(NvModel *m, const NvFrame *f, uint32_t now) {
  if(f->type != 80) return -1;
  const uint8_t *p = f->payload;
  uint32_t e = nv_u32(p), r = nv_u32(p + 4);
  if(!e) {
    memset(m, 0, sizeof(*m));
    return 1;
  }
  if(m->fresh && (e < m->epoch || (e == m->epoch && r <= m->revision)))
    return 0;
  m->epoch = e;
  m->revision = r;
  m->turn = p[8] <= NvTurnArrive ? p[8] : NvTurnNone;
  m->flags = p[9];
  copy(m->road, p + 16, nv_u16(p + 10));
  copy(m->instr, p + 16 + nv_u16(p + 10), nv_u16(p + 12));
  copy(m->dist, p + 16 + nv_u16(p + 10) + nv_u16(p + 12), nv_u16(p + 14));
  m->synced = true;
  m->fresh = true;
  m->scroll_anchor = now;
  return 1;
}
int nv_scroll_offset(int overflow, uint32_t elapsed) {
  if(overflow <= 0) return 0;
  uint32_t travel = (uint32_t)overflow * 1000u / 12u;
  uint32_t cycle = 5000u + travel + 1500u;
  uint32_t t = elapsed % cycle;
  if(t < 5000u) return 0;
  t -= 5000u;
  if(t < travel) return (int)(t * 12u / 1000u);
  return overflow;
}
void nv_freeze(NvModel *m) {
  m->fresh = false;
}
int nv_input(NvInput *input, int key, int event, bool ready) {
  (void)input;
  (void)ready;
  if(key == 5 && event == 3) return -1;
  if(key == 4 && event == 2) return 1;
  return 0;
}
