#include "native/core/fast_forward.h"

/* Low bits: active, skip allowed. Upper bits: mode generation, including off/on
 * transitions between render calls. No host sleeps or guest clock changes. */
static uint32_t mode;
static uint64_t skipped;
static void set_flag(uint32_t bit, bool on)
{
    uint32_t old = __atomic_load_n(&mode, __ATOMIC_ACQUIRE);
    for (;;) {
        uint32_t bits = on ? (old & 3u) | bit : (old & 3u) & ~bit;
        if (bits == (old & 3u)) return;
        uint32_t next = ((old + 4u) & ~3u) | bits;
        if (__atomic_compare_exchange_n(&mode, &old, next, false, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) return;
    }
}
void nc_ff_active(bool on) { set_flag(1u, on); }
void nc_ff_allow_skip(bool on) { set_flag(2u, on); }
bool nc_ff_skip_active(void) { return (__atomic_load_n(&mode, __ATOMIC_ACQUIRE) & 3u) == 3u; }
void nc_ff_note_drop(void) { __atomic_fetch_add(&skipped, 1, __ATOMIC_RELAXED); }
uint64_t nc_ff_skipped(void) { return __atomic_load_n(&skipped, __ATOMIC_RELAXED); }
bool nc_ff_skip_scanout(NcScanoutGate *gate, uint64_t now, bool force)
{
    uint32_t current = __atomic_load_n(&mode, __ATOMIC_ACQUIRE);
    if (gate->mode != current) { gate->mode = current; gate->valid = false; }
    if ((current & 3u) != 3u) { gate->valid = false; return false; }
    if (!force && gate->valid && now >= gate->last && now - gate->last < 33333333ull) {
        nc_ff_note_drop();
        return true;
    }
    gate->last = now; gate->valid = true;
    return false;
}
