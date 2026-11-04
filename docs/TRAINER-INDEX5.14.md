# Advanced5.14 — import-time trainer indexing

Version `1.3.0-advanced5.14-trainerindex`, versionCode 31.

Previously, opening or resuming the trainer browser walked the selected folder,
read every ETM again, and rebuilt the catalog. Even a catalog read reopened,
hashed and parsed every cached executable. This was especially slow for packs
with roughly 2,200 files and Android document providers.

Imports now save names, notes, option labels, embedded title IDs, source names
and content hashes in a versioned metadata index. Choosing a trainer folder
indexes it once, with progress. Opening the browser uses the saved index; it
neither traverses the document provider nor opens individual trainer payloads.
Title-ID lookup matches indexed trainers to a game; the game dropdown also uses
a title-ID map. Search text and sorted browser data are retained during UI
recomposition. SHA-256 formatting no longer invokes a formatter for every byte.

Use **Refresh index** after adding, removing or replacing files in the external
folder. Folder notifications show a refresh reminder instead of triggering a
full background scan while selecting a game. Some providers do not send change
notifications, so explicit refresh remains the reliable way to update it.
Individual file imports update the index immediately. The older ID-only catalog
is parsed and migrated once on first use; that first upgrade load can still take
time. Future loads, including after process restart, use metadata only.

Only the chosen trainer is reopened for selection: its hash and ETM structure
are validated, then TrainerStore checks its actual embedded title ID. Startup
still verifies the selected per-game trainer file independently. Modified,
missing or corrupt cached files can therefore appear in indexed listings until
selected or refreshed, but cannot bypass the selected-file validation path.
Title-ID matching does not establish executable revision/region compatibility.
Online protection and the native trainer engine are unchanged.

Index writes are atomic. Catalog merge/update operations use process and OS
file locks; folder refresh preserves concurrently imported entries. A failed or
cancelled folder traversal keeps the previous index. Cached executables and
per-game options remain independent of external folder availability.

Regression checks cover a 2,200-item catalog reopened in a new catalog instance,
repeated title-ID lookups with a payload reader that must never be called,
one-time legacy migration, duplicate sources, independent game selections,
selected-payload corruption/missing files, and preservation of manual imports
during folder replacement. Actual Android document-provider performance and
device UI validation remain pending; host tests prove the I/O behavior, not a
particular handheld timing.
