/* Exercise the actual runtime override and restoration of the base mode. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
static int hle_prop_get(const char *key, char *value) { value[0] = 0; return 0; }
#include "native/dev/nv2a.c"
void nc_log(const char *tag, const char *fmt, ...) {}
int main(int argc, char **argv)
{
    const bool base = argc > 1;
    if (base) setenv("HAKUX_HLE_UNCAP", "1", 1);
    else unsetenv("HAKUX_HLE_UNCAP");
    assert(uncapped() == base);
    nc_dev_nv2a_set_fast_forward(true);
    assert(uncapped());
    nc_dev_nv2a_set_fast_forward(false);
    assert(uncapped() == base);
    puts(base ? "PASS: fast-forward restores uncapped setting" : "PASS: fast-forward restores capped setting");
}
