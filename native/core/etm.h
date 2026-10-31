/* Classic EvoX ETM layout, as documented by Team Xored's XBMC loader:
 * https://github.com/Rocky5/XBMC4Xbox-3.6/blob/main/xbmc/utils/Trainer.cpp
 * Own bounds-checked parser; no executable trainer code bundled. */
#ifndef NATIVE_CORE_ETM_H
#define NATIVE_CORE_ETM_H
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#define NC_ETM_MAX_SIZE 65536u
#define NC_ETM_MAX_OPTIONS 100u
#define NC_ETM_STUB_SIZE 28u

typedef struct NcEtm {
    uint32_t options, capacity, entry, noptions, nids;
    uint32_t ids[3];
} NcEtm;
/* Returns a constant error string, NULL on success. Labels are name, note,
 * then noptions labels, all NUL-terminated inside the file. */
const char *nc_etm_parse(const uint8_t *data, size_t size, NcEtm *out);
bool nc_etm_matches(const NcEtm *t, uint32_t title);
/* Saves flags/registers, sets EBP/EDX to trainer base, calls entry, restores
 * and returns to the original XBE entry with its original stack intact. */
void nc_etm_stub(uint8_t out[NC_ETM_STUB_SIZE], uint32_t base, uint32_t entry, uint32_t resume);
#endif
