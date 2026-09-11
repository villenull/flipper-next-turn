#pragma once
#include "nav_protocol.h"
/* Turn ids mirror protocol/nav_constants.json. */
enum {
  NvTurnNone,
  NvTurnStraight,
  NvTurnLeft,
  NvTurnRight,
  NvTurnSlightLeft,
  NvTurnSlightRight,
  NvTurnSharpLeft,
  NvTurnSharpRight,
  NvTurnUturn,
  NvTurnRoundabout,
  NvTurnExit,
  NvTurnMerge,
  NvTurnKeep,
  NvTurnFerry,
  NvTurnArrive,
  NvTurnCount
};
typedef struct {
  uint32_t epoch, revision;
  uint8_t turn, flags;
  char road[65], instr[65], dist[65];
  uint32_t scroll_anchor;
  bool synced, fresh;
} NvModel;
/* 1 applied, 0 old/duplicate, -1 stale/wrong direction. */
int nv_model_apply(NvModel *m, const NvFrame *f, uint32_t now);
void nv_freeze(NvModel *m);
/* Pixels to shift a row that overflows by `overflow` after `elapsed` ms:
 * 5 s pause, 12 px/s, end pause 1.5 s, then repeat. */
int nv_scroll_offset(int overflow, uint32_t elapsed);
typedef struct {
  uint8_t held;
} NvInput;
/* key: 0 up, 1 down, 2 left, 3 right, 4 OK, 5 Back; event: 0 press,1 release,2
 * short,3 long. Returns -1 to exit, 1 to request a refresh, 0 otherwise.
 * Only long Back exits; Right is reserved (single-maneuver feed). */
int nv_input(NvInput *input, int key, int event, bool ready);
