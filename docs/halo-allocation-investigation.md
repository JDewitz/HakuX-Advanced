# Halo CE allocation investigation — appendable mainline patch series

Status: diagnostic instrumentation plus an opt-in compatibility experiment.
This is not a confirmed Halo repair. No Halo binary, game assets, trainers,
or Advanced UI changes are included.

Base: official v1.3.0, a90f01ee4629977b36a76175cbd781d383056293.
The ongoing Advanced5.7 branch remains separate.

## Evidence

Three official 1.3.0 (9) Halo v1.08 runs and four Advanced5.7 runs show
repeated 4 KiB contiguous allocation failures, then a guest access to
0x0000000c at EIP 0x0018110f and a native SIGSEGV. Official runs report
740–1048 KiB free; Advanced reports 748–1048 KiB. These are guest RAM
figures, not Android free memory. The exports include native tombstones.

The existing failure messages omit requested address bounds, alignment,
protection and guest caller. The temporal sequence suggests allocation
failure followed by a null dereference, but does not prove it. There is
no VK_ERROR_DEVICE_LOST in the seven Halo logs reviewed.

Between 1.2.0 and 1.3.0, the initial kernel pool changed from 512 KiB in
high RAM to 64 KiB at physical 0x50000. Halo's logs show a separate 4 KiB
block at 0x60000 and large allocations beginning at 0x61000. A conflict
between a constrained request and the relocated pool is a hypothesis:
we do not yet know the failing request's bounds. The first patch supplies
that missing evidence.

## Commits

1. Allocation diagnostics only. Preserve the existing log throttling and
   add original/rounded size, lower bound, exclusive upper bound, alignment,
   protection, caller and tracking-table occupancy. Failure categories
   distinguish tracking capacity, insufficient free pages and no usable
   window. The last category includes constraints and relocation failure;
   it does not itself identify a cause.
2. Experimental initial pool placement. `pool=high` (or `NC_POOL_HIGH=1`)
   places only the initial 64 KiB chunk high, using the upstream fallback
   allocator. `pool=default` removes the override. Default behavior and
   subsequent growth remain unchanged. A recorded savestate placement
   overrides this experiment. No saved structure/layout changes.

High placement can obstruct other titles' fixed allocations, including
the kind documented for Burnout 2 in kernel.c. Do not enable globally or
automatically for Halo until device testing supports it. This experiment
isolates placement; it does not restore the old 512 KiB size.

## Integration and building

Apply the numbered mail patches in order with `git am`. Each is an ordinary
mainline commit without any dependency on the Advanced fork. They also pass
an application check against Advanced5.7. Rebase the standalone topic when
mainline moves; use cherry-pick when bringing it into Advanced, rather than
copying files or replacing the project with an older snapshot.

Follow android/README.md for the full Android build. It requires SDK 36,
Build Tools 36.1.0, NDK 29.0.14206865, CMake/Ninja, JDK 21, the pinned FEX
checkout/submodules and native dependencies. Rebuild libhakuxcore.so and
refresh jniLibs before assembling an APK: rebuilding Kotlin alone will
package the old core. tools/native/app-libs.sh currently has a Windows
strip-tool path; on Linux select the NDK's linux-x86_64 llvm-strip instead.
Use the existing project's signing configuration privately.

The frontend can pass `pool=high` through the existing native argument
list before boot. This series does not add a menu, so a no-ADB device
test build needs its launcher to supply that argument. Do not require
the tester to use adb. `pool=default` or omitting the argument/environment
override selects the baseline in a fresh native process.

## Validation and device test

Run `bash tests/native/mm/run-pool-boot-test.sh` from the repository root.
It compiles and executes the real boot-placement and physical allocator
code on Linux, with only logging and the Android RAM provider stubbed.
It checks default/high placement, a constrained allocation blocked by a
low pool, saved placement, low-range fallback, OOM and 128 MiB RAM. That
constrained case demonstrates the mechanism, not Halo's unknown request.

Host tests and C syntax checks passed. An Android APK has not been built
or installed for this series; this environment lacks the Android native
toolchain, FEX and native dependency checkouts. Host checks do not validate
Android linking, JIT/GPU behavior, or the Halo outcome.

On device, retain retail 64 MiB and identical settings/savegame for both
tests. Fully exit the game, cold boot, start a new campaign and reach the
bay beyond the intro. Do not load an emulator savestate. Test first with
default placement, then high placement; export both sessions after any
crash. Check `pool: boot ... mode=default|high-experimental` and the new
`mm: request ...` line. Preserve saves and HDD data.

If both runs fail, the new bounds/caller should direct the next patch.
If high placement succeeds, repeat several cold boots and test other
titles before choosing a narrowly scoped compatibility policy. Revert the
experimental commit independently if necessary; keep the diagnostics.

## Continuing the project

Keep this topic independent of the continuing Advanced branch. Append
follow-up commits and numbered patches with validation notes. Record the
exact tested mainline and Advanced revisions for each device build. The
next acceptance criterion is successful repeatable Halo bay rendering on
device, not simply successful patch application or host tests.
