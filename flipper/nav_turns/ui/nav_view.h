#pragma once
#include "../core/nav_model.h"
#include <gui/gui.h>
/* 128x64: turn arrow at left (1..45, 1..45), distance/instruction/road rows
 * at x49 with white-mask scrolling, status line at bottom. */
void nav_view_draw(Canvas *c, const NvModel *m, uint32_t now,
                   const char *status, const char *overlay);
