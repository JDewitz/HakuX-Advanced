#include "native/core/trainer.h"
#include "native/core/etm.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char g_path[512], g_options[NC_ETM_MAX_OPTIONS + 1];

void nc_trainer_config(const char *path, const char *options)
{
    snprintf(g_path, sizeof(g_path), "%s", path);
    snprintf(g_options, sizeof(g_options), "%s", options);
}

const char *nc_trainer_boot(NcVm *vm, NcCpuContext *ctx, uint32_t title)
{
    if (!g_path[0]) {
        return NULL;
    }
    FILE *f = fopen(g_path, "rb");
    if (!f) {
        return "Trainer file cannot be opened";
    }
    uint8_t *data = malloc(NC_ETM_MAX_SIZE + 1);
    if (!data) {
        fclose(f);
        return "No memory for trainer file";
    }
    size_t n = fread(data, 1, NC_ETM_MAX_SIZE + 1, f);
    bool io = ferror(f);
    fclose(f);
    NcEtm t;
    const char *err = io ? "Trainer file cannot be read" : nc_etm_parse(data, n, &t);
    if (!err && !nc_etm_matches(&t, title)) {
        err = "Trainer title ID does not match this XBE";
    }
    if (!err && strlen(g_options) != t.noptions) {
        err = "Trainer option count changed; import it again";
    }
    if (!err) {
        for (unsigned i = 0; i < t.noptions; i++) {
            if (g_options[i] != '0' && g_options[i] != '1') {
                err = "Invalid trainer option state";
            }
        }
    }
    if (err) {
        free(data);
        return err;
    }
    memset(data + t.options, 0, t.capacity);
    for (unsigned i = 0; i < t.noptions; i++) {
        data[t.options + i] = g_options[i] == '1';
    }
    uint32_t len = nc_align_up((uint32_t)n + NC_ETM_STUB_SIZE, NC_PAGE);
    uint32_t pa = nc_vm_alloc_phys_high(vm, len);
    if (pa == UINT32_MAX) {
        free(data);
        return "No guest memory for trainer";
    }
    /* Window mapping already exists. Pin resident code: an installed trainer
     * hook may retain a pointer for the rest of the title. VM pages, pins and
     * bytes are part of the existing savestate format. */
    for (uint32_t p = pa; p < pa + len; p += NC_PAGE) {
        nc_vm_pin(vm, p, true, 0);
    }
    uint32_t base = NC_CONTIG_WINDOW + pa;
    nc_vm_will_write(base, len);
    memcpy(vm->base + base, data, n);
    free(data);
    nc_etm_stub(vm->base + base + n, base, base + t.entry, nc_context_eip(ctx));
    nc_context_set_eip(ctx, base + (uint32_t)n);
    nc_log("hakuX-native-trainer", "ETM scheduled: title %08x, %u options, %zu bytes at %08x; "
           "XBE entry after trainer return", title, t.noptions, n, base);
    return NULL;
}
