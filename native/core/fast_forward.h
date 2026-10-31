#ifndef NC_FAST_FORWARD_H
#define NC_FAST_FORWARD_H
#include <stdbool.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct NcScanoutGate { uint32_t mode; uint64_t last; bool valid; } NcScanoutGate;
void nc_ff_active(bool active);
void nc_ff_allow_skip(bool allow);
bool nc_ff_skip_active(void);
/* Render-thread state. Preserve all guest rendering; only omit host scanout copies. */
bool nc_ff_skip_scanout(NcScanoutGate *gate, uint64_t now, bool force);
void nc_ff_note_drop(void);
uint64_t nc_ff_skipped(void);
#ifdef __cplusplus
}
#endif
#endif
