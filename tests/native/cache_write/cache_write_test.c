/* Exercise the real rule parser, title binding and arming/path guards. */
#include "native/core/mods.c"
#include <assert.h>

/* Unrelated callbacks retained by sanitizer metadata must never run here. */
void hle_host_mem_set_dirty(uint32_t pa, uint32_t len)
{
    (void)pa; (void)len; assert(!"unexpected guest memory write");
}
void nc_cpu_invalidate(NcCpu *cpu, uint32_t va, uint32_t len)
{
    (void)cpu; (void)va; (void)len; assert(!"unexpected code invalidation");
}

static void bind_rule(const char *rule, uint32_t title)
{
    cache_title = 0;
    cache_rule(rule);
    HleXbeInfo xbe = {.title_id = title};
    nc_mods_bind(NULL, NULL, &xbe);
}

int main(void)
{
    const char *target = "\\Device\\Harddisk0\\Partition5\\Bank_en.xwb";
    bind_rule("5343000E Bank_en.xwb", 0x5343000e);
    assert(!nc_mods_cache_pending(target));
    nc_mods_cache_disc_read("\\Device\\CdRom0\\Sounds\\Other.xwb");
    assert(!nc_mods_cache_pending(target));
    nc_mods_cache_disc_read("\\Device\\CdRom0\\Sounds\\BANK_EN.XWB");
    assert(nc_mods_cache_pending(target));
    assert(!nc_mods_cache_pending("\\Device\\Harddisk0\\Partition5\\sub\\Bank_en.xwb"));
    assert(!nc_mods_cache_pending("\\Device\\Harddisk0\\Partition4\\Bank_en.xwb"));
    assert(!nc_mods_cache_pending("\\Device\\Harddisk0\\Partition5\\Bank_en.xwb.bak"));
    bind_rule("5343000E Bank_en.xwb", 0x4d530004);
    nc_mods_cache_disc_read("\\Device\\CdRom0\\Sounds\\Bank_en.xwb");
    assert(!nc_mods_cache_pending(target));
    const char *bad[] = {"5343000E ../Bank_en.xwb", "5343000E .", "5343000E ..", "5343000E Bank_en.xwb extra", "5343000 Bank_en.xwb"};
    for (unsigned i = 0; i < sizeof(bad) / sizeof(*bad); i++) {
        bind_rule(bad[i], 0x5343000e);
        nc_mods_cache_disc_read("\\Device\\CdRom0\\Sounds\\Bank_en.xwb");
        assert(!nc_mods_cache_pending(target));
    }
    bind_rule("4D530004 Other.xwb", 0x4d530004);
    nc_mods_cache_disc_read("\\Device\\CdRom0\\Other.xwb");
    assert(nc_mods_cache_pending("\\Device\\Harddisk0\\Partition5\\Other.xwb"));
    bind_rule("", 0x4d530004);
    assert(!nc_mods_cache_pending("\\Device\\Harddisk0\\Partition5\\Other.xwb"));
    puts("Cache-write rule parsing, scoping, opt-in, arming and lifecycle checks passed.");
}
