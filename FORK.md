# This fork

A fork of [micro-manager/LightSheetManager](https://github.com/micro-manager/LightSheetManager),
used on a SCAPE light-sheet microscope. `main` follows upstream `main`; each change here is a
branch off `main`, one logical change per pull request.

## How a change goes upstream

1. Write the change so upstream could take it unchanged: no site-specific names, paths or
   assumptions. A change that cannot be written that way is marked fork-only below.
2. Keep fork-only edits (this file, the CI jar download) in their own commits, so an upstream
   branch can leave them out.
3. To propose it: branch from upstream `main`, cherry-pick the change's commits, open the pull
   request against `micro-manager/LightSheetManager`, and fill in the Upstream column here.
4. When upstream merges it, merge upstream `main` into this `main` as usual and mark the row
   merged.

## Changes

Upstream: `candidate` (not proposed yet), a pull request link with its state, or `fork-only`.

| Change | Fork PR | Upstream |
|---|---|---|
| Restore the sheet after a SCAPE run | #1 | candidate |
| CI builds against the Micro-Manager nightly the rig runs, and runs the unit tests | #2 | fork-only |
| Per-camera flip of the Live view (Camera tab), never applied to acquired data | #2 | candidate |
| The camera panel and the channel table follow loaded settings (they kept stale camera boxes and rows) | #4 | candidate |
| Update Offset reads the piezo through the Core (a failed read made 0 the offset) | #4 | candidate |
| Piezo and Autofocus panel on the Experiment Setup tab: reference, live piezo, Test autofocus with a plot of its scores (scapecontrol ADR-0037) | #4 | fork-only |
| Default slice step 1 µm (upstream 0.5 µm); saved settings keep theirs | #4 | fork-only |
| Experiment Setup tab: cameras, one exposure, volume as range along the scan and step in µm (sheet angle from the Settings tab, odd slice counts, optional full coverage from the crop), channels, time points, named positions, configuration groups (lasers in %), Snap, Live, test volume, Stop, the run's status and progress bar; a test acquisition shows the Live flip | #4 | fork-only |
| Main window 70 px taller (750), so the Experiment Setup tab's autofocus panel and status row fit | #4 | fork-only |

## Fork-only pieces

- `.github/workflows/test.yml` downloads `MMJ_.jar` from a pinned Micro-Manager nightly
  (`MM_NIGHTLY`) and checks its SHA-256 (`MMJ_SHA256`). After the rig moves to a new nightly,
  update both lines together. Upstream builds against its own copy of the jar.
- The same workflow keeps the built plugin jar as a run artifact (30 days), named for the
  commit: the PR's Checks page, the run, Artifacts.
- `.github/workflows/release_jar.yml` comes from upstream unchanged and runs on a push to `main`
  that touches `pom.xml`; it would tag and release from this fork too.

## Effects to know about

- Live view orientation adds a step, "Light Sheet Manager Live View Orientation", to
  Micro-Manager's On-The-Fly Image Processing pipeline the first time a flip box is ticked on the
  Camera tab, and opens that window. Micro-Manager saves the pipeline in the user profile. If this
  plugin jar is later replaced by one without the step (upstream LSM before it takes the change),
  Micro-Manager cannot rebuild the step at startup, logs "Failed to reload pipeline" and drops
  the steps saved after it. Before going back to such a jar, delete the step in that window.
