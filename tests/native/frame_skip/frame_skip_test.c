#include "native/core/fast_forward.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
    NcScanoutGate gate = {0};
    assert(!nc_ff_skip_active());
    nc_ff_allow_skip(true); assert(!nc_ff_skip_active());
    for (int i = 0; i < 100; i++) assert(!nc_ff_skip_scanout(&gate, i, false));
    nc_ff_active(true); nc_ff_allow_skip(false);
    for (int i = 0; i < 100; i++) assert(!nc_ff_skip_scanout(&gate, i, false));
    nc_ff_allow_skip(true); assert(nc_ff_skip_active());
    assert(!nc_ff_skip_scanout(&gate, 1000000000, false));
    assert(nc_ff_skip_scanout(&gate, 1010000000, false));
    nc_ff_allow_skip(true); // redundant setting must not reset the gate
    assert(nc_ff_skip_scanout(&gate, 1030000000, false));
    assert(!nc_ff_skip_scanout(&gate, 1033333333, false));
    assert(!nc_ff_skip_scanout(&gate, 1034000000, true)); // explicit capture bypass
    assert(nc_ff_skip_scanout(&gate, 1035000000, false));
    nc_ff_active(false); nc_ff_active(true); // unseen off/on transition = fresh frame
    assert(!nc_ff_skip_scanout(&gate, 1036000000, false));
    assert(nc_ff_skip_scanout(&gate, 1037000000, false));
    assert(nc_ff_skipped() == 4);
    nc_ff_note_drop(); assert(nc_ff_skipped() == 5);
    nc_ff_active(false);
    assert(!nc_ff_skip_scanout(&gate, 1038000000, false));
    assert(!nc_ff_skip_active());
    puts("PASS: opt-in/active-only frame skipping, 30 Hz gate, force capture, toggle transitions and drop counters");
}
