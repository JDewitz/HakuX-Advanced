# Advanced4.3: experimental classic EvoX trainers

Based on Advanced4.2 Slang (4604702). Local branch `trainer-experimental`;
shader branch `advanced-slang` and its 4.2 tag remain available. No remote was
created or pushed. APK version code 15, version name
`1.2.0-advanced4.3-trainers`. This updates the existing `hakuX Shaders Test`
application (`com.rfandango.haku_x.shaders`) with the same development signature.
Use the 4.2 APK to return to the previous shader build; Android may require
uninstalling the newer version first, which removes its private app data.

## Use

1. Download an individual `.etm` matching your game's region and revision from
   https://archive.org/download/ETMTrainers . The collection contains many
   duplicates and regional variants; its listing is not a compatibility list.
2. Open the game's library menu → **Mods, cheats & trainers** →
   **EvoX trainer (experimental)** → **Import / replace .etm**.
3. Choose the file using Android's system file picker. It is copied into app
   storage. No directory permission or continued access to the source is needed.
4. Read the trainer's name/note, select the individual options, and turn on
   **Enable trainer on next fresh start**. Imports start disabled with all
   options off. One trainer is selected per game/version.
5. Stop the running game and start it fresh, without loading a save state.
   Trainer changes take effect at this start. Disabling/removing a trainer
   takes effect on a subsequent fresh start, rather than undoing running code.

Start with one option. Confirm the effect in gameplay, then test further
options individually. Keep normal game saves available. Report the exact
trainer filename, game title ID/certificate version, region, enabled options,
device/Android version, and whether the run was fresh or a state load.

## What runs

The importer reads classic ETM headers, title IDs, name, note/author and option
labels. A bounds-checked native parser independently validates the file again
at boot. The first two text entries are name and note, followed by option
labels; some trainers reserve more option bytes than their named options.
Real files commonly have only two title IDs before the option bytes, so neither
parser reads a third ID out of those bytes.

The run manifest adds `etm<TAB>absolute-file<TAB>010...`. The core matches the
loaded XBE's title ID, writes only the selected option bytes into a private
copy, allocates/pins guest RAM for that copy and a 28-byte x86 wrapper, and
redirects the first thread through the wrapper after HLE setup. The wrapper
saves flags/GPRs, supplies the trainer base in EBP and EDX, calls its entry
point, restores the original flags/GPRs/stack, and continues at the XBE entry.
The trainer's actual Xbox code executes through the existing FEX CPU backend.
This is not a conversion into text cheats. Its resident memory remains usable
by hooks installed by the trainer. It occupies page-rounded guest RAM
(file size plus 28 bytes) until the emulated console is torn down.

The traditional Xored/EvoX BIOS kernel interception and hardware LED sequence
are not installed. There are no changes to the kernel implementation or FEX.
The boot/manifest path changes, however, are experimental runtime changes.
The supported file envelope is classic ETM version 1, 35-byte header, at most
64 KiB and 100 reserved option bytes. XBTF (including its helper function ABI)
and BIOS-specific TSR globals are unsupported. Valid metadata does not prove
that arbitrary trainer code will run successfully.

## Save states and compatibility limits

A save state contains the trainer's memory, installed patches and CPU context.
Loading one skips fresh trainer installation. Changing the trainer's switches
or removing its import does not undo code in an older state; use a fresh boot
to change trainer settings. Existing VM state serialization already includes
physical bytes, allocation maps and page pins; the state format is unchanged.
Keep other mods consistent when comparing trainer states, because hakuX's
existing mod restore handling still applies.

Matching a title ID cannot determine the exact game revision or region. The
trainer may patch fixed addresses, depend on a particular XBE layout, install
hooks into code later replaced by an overlay, or assume a modified BIOS.
Some patched functions may also interact poorly with existing HLE hooks.
A trainer can therefore do nothing, crash, freeze, or corrupt game data even
when its file imports correctly. This build has no live trainer option toggle,
trainer browser/downloader, ZIP import, or whole-pack auto-selection.

## Validation and handoff

- Both parsers accepted nine files fetched from the supplied archive: Agent
  Under Fire regional/+1/+3 variants, Everything Or Nothing +6, Red Star +7,
  and Splinter Cell Double Agent +22. The latter's 22 labels are checked in JVM
  tests. The EON file advertises six reserved bytes but has five named options;
  the importer intentionally exposes its five supplied labels.
- Host checks cover valid metadata, title gating, every synthetic truncation,
  malformed offsets/string termination, and 10,000 deterministic mutations.
- The exact C-generated wrapper was executed in Unicorn's x86 mode. A real
  Agent Under Fire +1 trainer wrote eight NOPs at its expected ammo patch
  address when enabled and left the address untouched when disabled. Both
  returned to the original game entry with all GPRs, ESP and EFLAGS preserved.
  This verifies the bytecode path in an independent x86 emulator, not on FEX
  or an Android game run.
- Native ARM64 library and APK builds, JVM tests, and existing native regression
  checks are recorded in the accompanying validation file.
- No Android device, game image or GPU was available for an end-to-end test.
  Treat this as a first experimental test build, not verified support for the
  full archive or proof that any particular game works.

Relevant files: `data/Etm.kt`, `data/TrainerStore.kt`,
`ui/mods/TrainerSection.kt`, `data/Mods.kt`, `native/core/etm.{c,h}`,
`native/core/trainer.{c,h}`, `native/core/mods.c`, `native/core/core.c`.

Primary format/entry ABI reference: Team Xored's loader in XBMC4Xbox,
https://github.com/Rocky5/XBMC4Xbox-3.6/blob/main/xbmc/utils/Trainer.cpp and
https://github.com/Rocky5/XBMC4Xbox-3.6/blob/main/xbmc/Util.cpp . These are
references, not code copied into this implementation. No archive trainer
payloads are bundled or redistributed with this build/source.

Reproduce host execution checks (Python + Unicorn + host C compiler):

```sh
python3 -m pip install unicorn
python3 tests/native/trainers/check.py /path/to/downloaded/etm-samples
bash tests/native/advanced/run.sh
```

The optional samples directory must contain `007 Agent Under Fire p1.ETM`
for the actual code patch check. Omit the argument for synthetic-only checks.
Set `HAKUX_ETM_SAMPLES` to that directory when running JVM tests to also check
its `.etm` files; include `evt-scda.etm` for the 22-option assertion. Samples
are external test inputs, not an Internet dependency of the normal build.
Build native libraries and the Android app using the existing Advanced4 build
instructions. Publish matching source/licenses if distributing an APK. The
portable Git archive retains history and submodule references; it excludes
SDK/NDK, build outputs, trainer files and private signing keys.
