# Advanced 5.19 · upstream renderer merge

Merged official hakuX master through `a7cdb5a975e31730d488c47f6f10440b16c1e6f4`, 14 commits after the official `v1.3.0` baseline `a90f01ee4629977b36a76175cbd781d383056293`. This is an upstream synchronization build before further lighting experiments.

## Changes from upstream

| Change | Intended effect / scope |
| --- | --- |
| Surface-specific scaling for linear render-target textures | Small targets that remain at 1x no longer have texel coordinates incorrectly divided by the device's larger scale. The upstream report concerns Sega GT 2002 car skins at elevated resolution. |
| Mali-G57 W-buffer workaround | Uses rasterizer depth instead of fragment W-buffer writes on the affected driver/device path to avoid a reported GPU hard stop. |
| Arm driver timeline-wait workaround | On affected pre-r40 Arm drivers, polls completed timeline counters instead of the host semaphore wait that caused reported device loss. Includes more useful failure diagnostics. |
| Mali-G52 timing/query defaults | Adjusts present waiting and timestamp use according to the tested Arm driver behavior. |
| Correct malisx2 driver identification | Prevents proprietary Arm-driver-specific workarounds from being applied indiscriminately to malisx2. |
| Dynamically sized swapchain image arrays | Allocates from the image count actually returned by Android/Vulkan instead of assuming at most eight. Includes acquired-image bounds checking. |
| Mali custom-driver loading | Makes the custom-driver loading path available on Mali/kbase as well as Adreno/KGSL; KGSL priority requests stay restricted to KGSL. |
| Driver advice / native packaging | Turnip-specific advice stays Adreno-only. General custom-driver advice also reaches Mali. Native packaging supports Linux-host NDK tools. |

These changes do not establish a Snowblind skybox fix or a measured Halo speed improvement. Mali driver loading does not guarantee a particular third-party driver will work, nor does it make Vulkan 1.1 devices satisfy Slang's Vulkan 1.3 requirement.

## Merge resolution and Advanced behavior

The only textual conflict was `tools/native/app-libs.sh`. The resolution retains upstream's executable tool discovery and clear missing-tool error, adds the existing Darwin host support, and retains Advanced's pinned Slang runtime and matching libc++ packaging. No runtime source conflict was resolved manually.

The auto-merged presentation code was reviewed to retain the Advanced Slang prepare/draw/fallback path alongside the new swapchain handling. Halo's high-pool Auto/On/Off compatibility behavior, trainers/online protection, themes, library views, HUD and opt-in render stacking remain in the source. The two new named upstream quirks (`no_wbuf`, `no_sem_wait`) are also available through the existing Advanced Auto/Force on/Force off controls; Auto retains upstream driver selection.

## Build and validation

The native core and its C/C++ dependencies are rebuilt with the pinned Android NDK 29.0.14206865, generic default architecture, Release configuration and existing ThinLTO setting. FEX and dependency source revisions are unchanged. The pinned librashader C API binary is reused unchanged from Advanced 5.18; this merge does not modify its ABI or shader runtime source.

Validation includes the native shared-library build, 15 HLE host suites, pool-placement and Advanced/memory/fast-forward regressions, Android JVM tests and APK assembly. The update uses the existing package and signing certificate. Actual driver behavior, state compatibility and game output still need Android device tests.

## First device checks

1. Update without uninstalling. Start with the same driver, resolution and effects as the previous build; leave custom quirk overrides on Auto unless intentionally comparing one.
2. Cold-start Halo revision 8 with its pool workaround on Auto. Check gameplay, pause/HUD, fast forward and a trainer-free save-state load.
3. Retest Snowblind's first-level skybox at 1x and at the previously used scale, keeping shaders off for this comparison. Report whether the flicker changes; no fix is assumed.
4. Try a known-good RenderUpdate mode alone, then the previously working pre/post stack. Check FX status, resume and a second game.
5. Mali testers should report GPU, driver name/version and whether using the system or a custom driver. Test driver loading and sustained play separately.

Official comparison: https://github.com/rfandango/hakuX/compare/a90f01ee4629977b36a76175cbd781d383056293...a7cdb5a975e31730d488c47f6f10440b16c1e6f4
