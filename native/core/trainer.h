#ifndef NATIVE_CORE_TRAINER_H
#define NATIVE_CORE_TRAINER_H
#include "native/core/vm.h"
#include "native/cpu/cpu.h"
void nc_trainer_config(const char *path, const char *options);
/* NULL: disabled or installed; error: enabled but could not install. Call
 * only on a fresh boot, after HLE setup, never before loading a savestate. */
const char *nc_trainer_boot(NcVm *vm, NcCpuContext *ctx, uint32_t title);
#endif
