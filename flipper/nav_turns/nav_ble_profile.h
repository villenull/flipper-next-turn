#pragma once
#include <furi.h>
#include <furi_ble/profile_interface.h>
#include <stdatomic.h>
typedef struct {
  uint16_t length;
  uint8_t bytes[128];
} NvRx;
typedef struct {
  FuriMessageQueue *rx;
  atomic_bool stopping, overflow, subscribed, confirmed, disconnected;
} NvBleEvents;
typedef struct NvProfile NvProfile;
extern const FuriHalBleProfileTemplate nv_profile_template;
bool nv_ble_tx_submit(NvProfile *profile, const uint8_t *bytes,
                      uint16_t length);
bool nv_ble_valid(const NvProfile *profile);
#include "core/nav_ble_adapter.h"
