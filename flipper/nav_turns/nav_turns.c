#include "core/nav_model.h"
#include "core/nav_protocol.h"
#include "nav_ble_profile.h"
#include "ui/nav_view.h"
#include <bt/bt_service/bt.h>
#include <furi_hal.h>
#include <gui/gui.h>
#include <storage/storage.h>
#include <string.h>

typedef struct {
  NvBleEvents ble;
  NvProfile *profile;
  FuriThread *worker;
  ViewPort *view;
  NvParser parser;
  NvFrame incoming, outgoing;
  uint8_t wire[NV_MAX_FRAME];
  uint8_t last_type;
  uint32_t last_payload_crc;
  uint32_t session, last_id, tx_id, last_peer, error_at, error_count;
  atomic_bool exit, reset, ready;
  FuriMutex *lock;
  NvModel model;
  NvInput keys;
  char status[40], overlay[32];
  uint32_t overlay_until;
} App;
static bool send_frame(App *a, uint8_t type, const uint8_t *p,
                       uint16_t length) {
  if (a->tx_id == UINT32_MAX)
    return false;
  NvFrame *f = &a->outgoing;
  f->type = type;
  f->session = a->session;
  f->id = ++a->tx_id;
  f->length = length;
  if (length)
    memcpy(f->payload, p, length);
  size_t n = nv_encode(f, a->wire);
  for (size_t pos = 0; pos < n; pos += 20) {
    if (atomic_load(&a->ble.stopping) || !atomic_load(&a->ble.subscribed))
      return false;
    atomic_store(&a->ble.confirmed, false);
    if (!nv_ble_tx_submit(a->profile, a->wire + pos, MIN((size_t)20, n - pos)))
      return false;
    uint32_t start = furi_get_tick();
    while (!atomic_load(&a->ble.confirmed)) {
      if (atomic_load(&a->ble.stopping) || atomic_load(&a->ble.disconnected) ||
          (uint32_t)(furi_get_tick() - start) > 5000)
        return false;
      furi_delay_ms(5);
    }
  }
  return n > 0;
}
static void receive(const NvFrame *f, void *ctx) {
  App *a = ctx;
  uint32_t now = furi_get_tick();
  if (atomic_load(&a->reset) || atomic_load(&a->ble.stopping))
    return;
  if (!a->session && f->type == 1 && f->id == 1) {
    a->session = f->session;
    a->last_id = 1;
    a->last_peer = now;
    a->last_type = f->type;
    a->last_payload_crc = nv_crc(f->payload, f->length);
    uint8_t ack[12] = {1, 0, 0, 1, 20, 0, 0, 0, 1, 0, 0, 0};
    if (f->payload[0] > 1) {
      ack[0] = 0;
      ack[1] = 1;
    }
    if (!send_frame(a, 2, ack, 12) || ack[1])
      atomic_store(&a->reset, true);
    furi_mutex_acquire(a->lock, FuriWaitForever);
    snprintf(a->status, sizeof(a->status), "Reading route...");
    furi_mutex_release(a->lock);
    return;
  }
  if (a->session && f->session == a->session && f->id == a->last_id &&
      f->type == a->last_type &&
      nv_crc(f->payload, f->length) == a->last_payload_crc) {
    if (f->type == 1 && !atomic_load(&a->ready)) {
      const uint8_t ack[12] = {1, 0, 0, 1, 20, 0, 0, 0, 1, 0, 0, 0};
      if (!send_frame(a, 2, ack, 12))
        atomic_store(&a->reset, true);
      return;
    }
    if (f->type == 80) {
      uint8_t ack[8];
      nv_put(ack, f->id, 4);
      nv_put(ack + 4, nv_u32(f->payload + 8), 4);
      if (!send_frame(a, 81, ack, 8))
        atomic_store(&a->reset, true);
      return;
    }
  }
  if (!a->session || f->session != a->session || f->id <= a->last_id) {
    atomic_store(&a->reset, true);
    return;
  }
  a->last_id = f->id;
  a->last_type = f->type;
  a->last_payload_crc = nv_crc(f->payload, f->length);
  switch (f->type) {
  case 80: {
    furi_mutex_acquire(a->lock, FuriWaitForever);
    bool ok = !atomic_load(&a->ble.stopping) && !atomic_load(&a->reset);
    int applied = -1;
    if (ok)
      applied = nv_model_apply(&a->model, f, now);
    furi_mutex_release(a->lock);
    uint8_t ack[8];
    nv_put(ack, f->id, 4);
    nv_put(ack + 4, nv_u32(f->payload + 8), 4);
    if (applied < 0 || !send_frame(a, 81, ack, 8))
      atomic_store(&a->reset, true);
    else if (!atomic_load(&a->ble.stopping) &&
             !atomic_load(&a->ble.disconnected))
      atomic_store(&a->ready, true);
    else
      atomic_store(&a->reset, true);
    break;
  }
  case 0x60:
    if (!send_frame(a, 0x61, f->payload, 4))
      atomic_store(&a->reset, true);
    break;
  case 0x7e:
    atomic_store(&a->reset, true);
    break;
  case 0x7f:
    atomic_store(&a->reset, true);
    break;
  default:
    atomic_store(&a->reset, true);
    return;
  }
  a->last_peer = now;
  view_port_update(a->view);
}
static void draw(Canvas *c, void *ctx) {
  App *a = ctx;
  furi_mutex_acquire(a->lock, FuriWaitForever);
  uint32_t now = furi_get_tick();
  if ((int32_t)(now - a->overlay_until) >= 0)
    a->overlay[0] = 0;
  nav_view_draw(c, &a->model, now, a->status, a->overlay);
  furi_mutex_release(a->lock);
}
static void input(InputEvent *e, void *ctx) {
  App *a = ctx;
  int key = -1, event = -1;
  switch (e->key) {
  case InputKeyUp:
    key = 0;
    break;
  case InputKeyDown:
    key = 1;
    break;
  case InputKeyLeft:
    key = 2;
    break;
  case InputKeyRight:
    key = 3;
    break;
  case InputKeyOk:
    key = 4;
    break;
  case InputKeyBack:
    key = 5;
    break;
  default:
    return;
  }
  switch (e->type) {
  case InputTypePress:
    event = 0;
    break;
  case InputTypeRelease:
    event = 1;
    break;
  case InputTypeShort:
    event = 2;
    break;
  case InputTypeLong:
    event = 3;
    break;
  default:
    return;
  }
  furi_mutex_acquire(a->lock, FuriWaitForever);
  uint32_t now = furi_get_tick();
  int command = nv_input(&a->keys, key, event, atomic_load(&a->ready));
  if (command < 0)
    atomic_store(&a->exit, true);
  else if (command > 0) {
    if (!send_frame(a, 82, NULL, 0)) {
      snprintf(a->overlay, sizeof(a->overlay), "No response");
      a->overlay_until = now + 1200;
    }
  }
  if (key == 5 && event == 2) {
    snprintf(a->overlay, sizeof(a->overlay), "Hold Back to exit");
    a->overlay_until = now + 1200;
  }
  furi_mutex_release(a->lock);
  view_port_update(a->view);
}
static int32_t worker(void *ctx) {
  App *a = ctx;
  NvRx r;
  a->error_at = furi_get_tick();
  a->error_count = a->parser.errors;
  while (!atomic_load(&a->ble.stopping)) {
    if (furi_message_queue_get(a->ble.rx, &r, 20) == FuriStatusOk)
      nv_feed(&a->parser, r.bytes, r.length, furi_get_tick(), &a->incoming,
              receive, a);
    uint32_t now = furi_get_tick();
    if (atomic_load(&a->reset) || atomic_load(&a->ble.disconnected)) {
      atomic_store(&a->ready, false);
      continue;
    }
    if (nv_parser_expire(&a->parser, now) || atomic_load(&a->ble.overflow) ||
        (a->session && now - a->last_peer >= 35000))
      atomic_store(&a->reset, true);
    if (now - a->error_at >= 10000) {
      a->error_at = now;
      a->error_count = a->parser.errors;
    }
    if (a->parser.errors - a->error_count >= 3)
      atomic_store(&a->reset, true);
  }
  return 0;
}
static bool storage_ready(Storage *storage) {
  FuriString *path = furi_string_alloc_set(APP_DATA_PATH(".write-probe"));
  storage_common_resolve_path_and_ensure_app_directory(storage, path);
  File *file = storage_file_alloc(storage);
  bool ok = storage_file_open(file, furi_string_get_cstr(path), FSAM_WRITE,
                              FSOM_CREATE_ALWAYS);
  if (ok)
    storage_file_close(file);
  storage_file_free(file);
  if (ok)
    storage_common_remove(storage, furi_string_get_cstr(path));
  furi_string_free(path);
  return ok;
}
int32_t nav_turns_app(void *ctx) {
  UNUSED(ctx);
  App *a = malloc(sizeof(*a));
  if (!a)
    return 1;
  memset(a, 0, sizeof(*a));
  a->lock = furi_mutex_alloc(FuriMutexTypeNormal);
  snprintf(a->status, sizeof(a->status), "Waiting for route");
  a->ble.rx = furi_message_queue_alloc(8, sizeof(NvRx));
  a->view = view_port_alloc();
  Gui *gui = furi_record_open(RECORD_GUI);
  Bt *bt = furi_record_open(RECORD_BT);
  Storage *storage = furi_record_open(RECORD_STORAGE);
  view_port_draw_callback_set(a->view, draw, a);
  view_port_input_callback_set(a->view, input, a);
  gui_add_view_port(gui, a->view, GuiLayerFullscreen);
  bool changed = false;
  const char *startup_error = NULL;
  if (storage_sd_status(storage) != FSE_OK)
    startup_error = "SD card not ready";
  else if (!storage_ready(storage))
    startup_error = "App data not writable";
  else if (!furi_hal_bt_is_gatt_gap_supported())
    startup_error = "BLE stack unavailable";
  if (!startup_error) {
    bt_disconnect(bt);
    furi_delay_ms(200);
    bt_keys_storage_set_storage_path(bt, APP_DATA_PATH("bt.keys"));
    changed = true;
    a->profile = (NvProfile *)bt_profile_start(bt, &nv_profile_template,
                                               &a->ble);
    if (nv_ble_valid(a->profile)) {
      a->worker = furi_thread_alloc_ex("NvTransport", 4096, worker, a);
      furi_thread_start(a->worker);
      furi_hal_bt_start_advertising();
      bool advertising = false;
      for (int i = 0; i < 40 && !advertising; i++) {
        furi_delay_ms(50);
        advertising = furi_hal_bt_is_active();
      }
      if (!advertising) {
        atomic_store(&a->ble.stopping, true);
        furi_thread_join(a->worker);
        furi_thread_free(a->worker);
        a->worker = NULL;
        startup_error = "Advertising failed";
      }
    } else {
      startup_error = "BLE profile failed";
    }
  }
  if (!a->worker) {
    furi_mutex_acquire(a->lock, FuriWaitForever);
    snprintf(a->status, sizeof(a->status), "%s",
             startup_error ? startup_error : "Transport start failed");
    furi_mutex_release(a->lock);
    view_port_update(a->view);
  }
  while (!atomic_load(&a->exit)) {
    if (atomic_load(&a->reset) || atomic_load(&a->ble.disconnected)) {
      atomic_store(&a->ble.stopping, true);
      atomic_store(&a->ready, false);
      furi_mutex_acquire(a->lock, FuriWaitForever);
      nv_freeze(&a->model);
      furi_mutex_release(a->lock);
      view_port_update(a->view);
      atomic_store(&a->ble.stopping, true);
      bt_disconnect(bt);
      if (a->worker) {
        furi_thread_join(a->worker);
        furi_thread_free(a->worker);
        a->worker = NULL;
      }
      atomic_store(&a->ready, false);
      a->session = a->last_id = a->tx_id = 0;
      memset(&a->parser, 0, sizeof(a->parser));
      furi_message_queue_reset(a->ble.rx);
      atomic_store(&a->ble.overflow, false);
      atomic_store(&a->reset, false);
      atomic_store(&a->ble.disconnected, false);
      atomic_store(&a->ble.stopping, false);
      if (nv_ble_valid(a->profile)) {
        a->worker = furi_thread_alloc_ex("NvTransport", 4096, worker, a);
        furi_thread_start(a->worker);
        furi_hal_bt_start_advertising();
      }
    }
    furi_delay_ms(25);
  }
  atomic_store(&a->ble.stopping, true);
  atomic_store(&a->ready, false);
  if (changed) {
    bt_disconnect(bt);
    furi_delay_ms(200);
  }
  if (a->worker) {
    furi_thread_join(a->worker);
    furi_thread_free(a->worker);
  }
  if (changed) {
    bt_keys_storage_set_default_path(bt);
    while (!bt_profile_restore_default(bt)) {
      furi_mutex_acquire(a->lock, FuriWaitForever);
      snprintf(a->status, sizeof(a->status), "Restore failed; retrying");
      furi_mutex_release(a->lock);
      view_port_update(a->view);
      furi_delay_ms(1000);
    }
  }
  gui_remove_view_port(gui, a->view);
  view_port_free(a->view);
  furi_message_queue_free(a->ble.rx);
  furi_mutex_free(a->lock);
  furi_record_close(RECORD_STORAGE);
  furi_record_close(RECORD_BT);
  furi_record_close(RECORD_GUI);
  free(a);
  return 0;
}
