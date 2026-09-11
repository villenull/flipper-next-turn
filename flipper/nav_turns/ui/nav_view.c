#include "nav_view.h"
#include <string.h>
static void row(Canvas *c, const char *text, Font font, int x, int right,
                int top, int bottom, int baseline, int offset) {
  canvas_set_font(c, font);
  canvas_draw_str(c, x - offset, baseline, text);
  canvas_set_color(c, ColorWhite);
  if(x > 0) canvas_draw_box(c, 0, top, x, bottom - top + 1);
  if(right < 127) canvas_draw_box(c, right + 1, top, 127 - right, bottom - top + 1);
  canvas_set_color(c, ColorBlack);
}
/* Arrow glyphs in a 44x44 box at (1,1), center ~(23,23). */
static void arrow(Canvas *c, uint8_t turn) {
  switch(turn) {
  case NvTurnLeft:
    canvas_draw_line(c, 40, 23, 8, 23);
    canvas_draw_line(c, 8, 23, 18, 13);
    canvas_draw_line(c, 8, 23, 18, 33);
    break;
  case NvTurnRight:
    canvas_draw_line(c, 6, 23, 38, 23);
    canvas_draw_line(c, 38, 23, 28, 13);
    canvas_draw_line(c, 38, 23, 28, 33);
    break;
  case NvTurnSlightLeft:
    canvas_draw_line(c, 34, 40, 12, 8);
    canvas_draw_line(c, 12, 8, 12, 20);
    canvas_draw_line(c, 12, 8, 24, 8);
    break;
  case NvTurnSlightRight:
    canvas_draw_line(c, 12, 40, 34, 8);
    canvas_draw_line(c, 34, 8, 34, 20);
    canvas_draw_line(c, 34, 8, 22, 8);
    break;
  case NvTurnSharpLeft:
    canvas_draw_line(c, 38, 40, 38, 14);
    canvas_draw_line(c, 38, 14, 8, 14);
    canvas_draw_line(c, 8, 14, 16, 6);
    canvas_draw_line(c, 8, 14, 16, 22);
    break;
  case NvTurnSharpRight:
    canvas_draw_line(c, 8, 40, 8, 14);
    canvas_draw_line(c, 8, 14, 38, 14);
    canvas_draw_line(c, 38, 14, 30, 6);
    canvas_draw_line(c, 38, 14, 30, 22);
    break;
  case NvTurnUturn:
    canvas_draw_line(c, 32, 40, 32, 12);
    canvas_draw_line(c, 32, 12, 14, 12);
    canvas_draw_line(c, 14, 12, 14, 40);
    canvas_draw_line(c, 14, 40, 8, 32);
    canvas_draw_line(c, 14, 40, 20, 32);
    break;
  case NvTurnRoundabout:
    canvas_draw_box(c, 10, 10, 26, 26);
    canvas_draw_box(c, 15, 15, 16, 16);
    canvas_draw_line(c, 36, 23, 42, 23);
    canvas_draw_line(c, 42, 23, 37, 18);
    canvas_draw_line(c, 42, 23, 37, 28);
    break;
  case NvTurnExit:
    canvas_draw_line(c, 10, 40, 30, 10);
    canvas_draw_line(c, 30, 10, 30, 22);
    canvas_draw_line(c, 30, 10, 40, 10);
    canvas_draw_line(c, 10, 40, 10, 28);
    break;
  case NvTurnMerge:
    canvas_draw_line(c, 8, 40, 20, 20);
    canvas_draw_line(c, 38, 40, 26, 20);
    canvas_draw_line(c, 23, 22, 23, 6);
    canvas_draw_line(c, 23, 6, 17, 12);
    canvas_draw_line(c, 23, 6, 29, 12);
    break;
  case NvTurnKeep:
    canvas_draw_line(c, 23, 40, 23, 8);
    break;
  case NvTurnFerry:
    canvas_draw_line(c, 8, 16, 38, 16);
    canvas_draw_line(c, 12, 25, 34, 25);
    canvas_draw_line(c, 16, 34, 30, 34);
    break;
  case NvTurnArrive:
    canvas_draw_box(c, 16, 16, 14, 14);
    canvas_draw_frame(c, 12, 12, 22, 22);
    break;
  case NvTurnStraight:
  default:
    canvas_draw_line(c, 23, 40, 23, 8);
    canvas_draw_line(c, 23, 8, 13, 18);
    canvas_draw_line(c, 23, 8, 33, 18);
    break;
  }
}
void nav_view_draw(Canvas *c, const NvModel *m, uint32_t now,
                   const char *status, const char *overlay) {
  canvas_clear(c);
  canvas_set_color(c, ColorBlack);
  canvas_set_font(c, FontSecondary);
  if(!m->synced) {
    canvas_draw_str(c, 4, 24, status && *status ? status : "Waiting for route");
    canvas_draw_str(c, 4, 53, "Hold Back to exit");
    return;
  }
  if(!m->epoch) {
    canvas_draw_str(c, 14, 23, "No active route");
    canvas_draw_str(c, 6, 36, "Navigate in Maps");
    canvas_draw_str(c, 8, 57, "Hold Back to exit");
    return;
  }
  const char *dist = *m->dist ? m->dist : "--";
  const char *instr = *m->instr ? m->instr : "Follow route";
  const char *road = *m->road ? m->road : "";
  const char *texts[3] = {dist, instr, road};
  const Font fonts[3] = {FontPrimary, FontSecondary, FontSecondary};
  const int baselines[3] = {10, 25, 40};
  const int tops[3] = {0, 14, 29};
  const int bottoms[3] = {12, 27, 43};
  for(int i = 0; i < 3; i++) {
    if(i == 2 && !*texts[i]) continue;
    canvas_set_font(c, fonts[i]);
    int overflow =
        (int)canvas_string_width(c, texts[i]) - (127 - 49 + 1);
    int offset = (overlay && *overlay)
                     ? 0
                     : nv_scroll_offset(overflow, now - m->scroll_anchor);
    row(c, texts[i], fonts[i], 49, 127, tops[i], bottoms[i], baselines[i],
        offset);
  }
  /* Draw after clipping text so the left masks never erase the arrow. */
  arrow(c, m->turn);
  if(m->flags & 2) {
    canvas_set_font(c, FontSecondary);
    canvas_draw_str(c, 2, 63, "Rerouting");
  } else if(!m->fresh) {
    canvas_set_font(c, FontSecondary);
    canvas_draw_str(c, 2, 63, "Reconnecting");
  }
  if(overlay && *overlay) {
    canvas_set_font(c, FontSecondary);
    canvas_draw_str(c, 2, 55, overlay);
  }
  if(m->fresh) canvas_draw_box(c, 124, 2, 4, 4);
}
