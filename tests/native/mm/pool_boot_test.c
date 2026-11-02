/* Host regression tests: link the real pool and physical allocator, no JIT/GPU. */
#include "native/kernel/kernel.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <unistd.h>

int hle_uma_ram_fd(size_t size) { (void)size; return -1; }
void nc_log(const char *tag, const char *fmt, ...) { (void)tag; (void)fmt; }

static void start(NcVm *vm, uint32_t size)
{
    assert(nc_vm_init(vm, size));
    assert(nc_vm_claim_phys(vm, NC_KERNEL_PA, NC_KERNEL_SIZE));
}

static void finish(NcVm *vm)
{
    munmap(vm->phys, vm->ram_size);
    munmap(vm->reservation, vm->reservation_len);
    close(vm->ram_fd);
    free(vm->page_used); free(vm->pte); free(vm->rmap); free(vm->wp); free(vm->pin);
}

int main(void)
{
    const uint32_t size = 0x10000, low = NC_KERNEL_PA + NC_KERNEL_SIZE;
    NcVm vm;
    uint32_t moved;
    start(&vm, NC_RAM_RETAIL);
    assert(nc_pool_boot_alloc(&vm, size, low, false) == low);
    assert(nc_vm_free_bytes(&vm) > 1024 * 1024);
    assert(nc_vm_alloc_phys_top(&vm, NC_PAGE, low, low + size, NC_PAGE, &moved) == UINT32_MAX);
    finish(&vm);

    start(&vm, NC_RAM_RETAIL);
    const uint32_t high = nc_pool_boot_alloc(&vm, size, low, true);
    assert(high == vm.ram_size - NC_TOP_RESERVE - size);
    assert(nc_vm_alloc_phys_top(&vm, NC_PAGE, low, low + size, NC_PAGE, &moved) != UINT32_MAX);
    finish(&vm);

    /* Saved-state exact placement: caller disables the experiment. */
    start(&vm, NC_RAM_RETAIL);
    const uint32_t saved = 0x3e80000;
    assert(nc_pool_boot_alloc(&vm, size, saved, false) == saved);
    finish(&vm);

    /* Preserve upstream fallback when the requested low range is taken. */
    start(&vm, NC_RAM_RETAIL);
    assert(nc_vm_claim_phys(&vm, low, size));
    assert(nc_pool_boot_alloc(&vm, size, low, false) == high);
    finish(&vm);

    start(&vm, NC_RAM_RETAIL);
    assert(nc_vm_claim_phys(&vm, low, vm.ram_size - low));
    assert(nc_pool_boot_alloc(&vm, size, low, true) == UINT32_MAX);
    finish(&vm);

    start(&vm, 128u << 20);
    assert(nc_pool_boot_alloc(&vm, size, low, true) == vm.ram_size - NC_TOP_RESERVE - size);
    finish(&vm);
    puts("PASS: default/high placement, bounded request, saved placement, fallback, OOM, 128 MiB");
    return 0;
}
