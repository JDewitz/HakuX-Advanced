#include <stdint.h>
static int hle_prop_get(const char *name, char *out) { out[0] = 0; return 0; }
#include "native/core/memscan.c"
#include <assert.h>
void nc_log(const char *tag, const char *fmt, ...) {}
static unsigned dirtied;
void hle_host_mem_set_dirty(uint32_t pa, uint32_t len) { dirtied += len; }
int main(void) {
    NcVm vm = {0};
    vm.phys = calloc(1, 8192); vm.ram_size = 8192;
    vm.pte = calloc(1u << 20, sizeof(uint32_t));
    vm.pte[1] = NC_PTE_VALID; /* virtual 0x1000 -> physical 0 */
    vm.phys[4094] = 0xa5;
    poke(&vm, "1ffe:11223344:4"); // crosses an unmapped page: no partial write
    assert(vm.phys[4094] == 0xa5 && dirtied == 0);
    poke(&vm, "1000:11223344:4");
    assert(vm.phys[0] == 0x44 && vm.phys[1] == 0x33 && vm.phys[2] == 0x22 && vm.phys[3] == 0x11);
    assert(dirtied == 4);
    poke(&vm, "ffffffff:11223344:4"); assert(dirtied == 4);
    poke(&vm, "1000:ffffff:3"); assert(dirtied == 4);
    free(vm.phys); free(vm.pte);
    puts("PASS: actual guest poke endianness, dirty tracking, invalid sizes, address wrap and all-or-nothing page boundary validation");
}
