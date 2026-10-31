#include "native/core/etm.h"
#include <string.h>

static uint32_t rd(const uint8_t *p)
{
    return p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}

static void wr(uint8_t *p, uint32_t v)
{
    for (unsigned i = 0; i < 4; i++) {
        p[i] = (uint8_t)(v >> (8 * i));
    }
}

const char *nc_etm_parse(const uint8_t *d, size_t n, NcEtm *t)
{
    memset(t, 0, sizeof(*t));
    if (n < 35 || n > NC_ETM_MAX_SIZE) {
        return "ETM size outside 35..65536 bytes";
    }
    if (rd(d) != n || d[4] != 1 || d[5] || rd(d + 6) != 35 || rd(d + 26)) {
        return "Not a supported classic ETM header (XBTF is unsupported)";
    }
    t->options = rd(d + 10);
    uint32_t text = rd(d + 14), ids = rd(d + 18);
    t->entry = rd(d + 22);
    if (t->entry < 35 || t->entry >= n || t->options < 35 || text <= t->options ||
        text >= n || text - t->options > NC_ETM_MAX_OPTIONS) {
        return "ETM entry/options outside file";
    }
    t->capacity = text - t->options;
    if (ids < 35 || ids > t->options || t->options - ids < 8) {
        return "ETM title table outside file";
    }
    /* Many real ETMs have two IDs followed immediately by option bytes.
     * Do not read a third ID out of those option bytes. */
    t->nids = (t->options - ids) / 4;
    if (t->nids > 3) {
        t->nids = 3;
    }
    for (unsigned i = 0; i < t->nids; i++) {
        t->ids[i] = rd(d + ids + 4 * i);
    }
    unsigned count = 0;
    for (;;) {
        if (text > n - 4) {
            return "ETM text table truncated";
        }
        uint32_t a = rd(d + text);
        text += 4;
        if (!a) {
            break;
        }
        if (count >= t->capacity + 2 || a >= n || !memchr(d + a, 0, n - a)) {
            return "ETM label outside file";
        }
        count++;
    }
    if (count < 3) {
        return "ETM has no named options";
    }
    t->noptions = count - 2;
    return NULL;
}

bool nc_etm_matches(const NcEtm *t, uint32_t title)
{
    if (!title) {
        return false;
    }
    for (unsigned i = 0; i < t->nids; i++) {
        if (t->ids[i] == title) {
            return true;
        }
    }
    return false;
}

void nc_etm_stub(uint8_t p[NC_ETM_STUB_SIZE], uint32_t base, uint32_t entry, uint32_t resume)
{
    const uint8_t code[NC_ETM_STUB_SIZE] = {
        0x9c, 0x60,                         /* pushfd; pushad */
        0xbd, 0, 0, 0, 0,                 /* mov ebp, base */
        0xba, 0, 0, 0, 0,                 /* mov edx, base */
        0xfc, 0xb8, 0, 0, 0, 0,           /* cld; mov eax, entry */
        0xff, 0xd0, 0x61, 0x9d,           /* call eax; popad; popfd */
        0x68, 0, 0, 0, 0, 0xc3            /* push resume; ret */
    };
    memcpy(p, code, sizeof(code));
    wr(p + 3, base);
    wr(p + 8, base);
    wr(p + 14, entry);
    wr(p + 23, resume);
}
