# CI installer testing — Design

**Date:** 2026-08-07
**Status:** Approved, ready for implementation planning

## Context

The repository has no CI at all — no `.github/` directory. Everything is verified by
hand on the maintainer's machine.

Two things made that costly during PR #1 and PR #2:

1. Three installer defects shipped in PR #1 and were only caught by review: a BellSoft
   API endpoint that 404s, a validated `JAVA_HOME` that no launcher ever used, and a
   Linux launcher step that failed while reporting success. All three are the kind of
   defect an end-to-end install test catches trivially.
2. `mvn verify` cannot run on the maintainer's machine — `google-java-format` throws
   `NoSuchMethodError` against JDK 25 — so the `spotless:check` and SpotBugs gates are
   effectively unenforced.

The release artifact is `md2pdf-gui.zip`, built once on a Mac by `gui/createApp.sh` and
installed on macOS, Linux and Windows via `md2pdf-install.sh` at the zip root. Nothing
currently tests that path.

## Goals

- Build the release zip the way a release actually builds it, then install it on all
  three platforms and assert the install is correct.
- Regression-test the three PR #1 defects specifically.
- Restore an enforceable `mvn verify` gate on a JDK that can run it.

## Non-goals

- Launching the GUI. No JavaFX process starts in CI; startup is the flakiest thing to
  assert and the least informative when it breaks.
- Exercising the BellSoft JDK download path on every PR. It downloads ~200 MB and
  depends on a third-party API. Deliberately excluded; a scheduled job is a reasonable
  later addition, and it is the one defect here that shipped broken.
- Signing, notarization, or publishing releases.

## Architecture

A single workflow, `.github/workflows/ci.yml`, on push to `main` and on pull requests.

| Job | Runner | Purpose |
|---|---|---|
| `verify` | `ubuntu-latest` | `mvn verify` — tests, `spotless:check`, SpotBugs |
| `shellcheck` | `ubuntu-latest` | lint the shell scripts |
| `package` | `macos-latest` | build `md2pdf-gui.zip`, upload as an artifact |
| `install` | ubuntu / macos / windows | download that zip, unzip, install, verify |
| `install-failures` | `ubuntu-latest` | assert the failure paths fail loudly |

`install` and `install-failures` declare `needs: package`, so all three platforms
install the byte-identical artifact a user would download. This mirrors the real
release path: one zip, built on a Mac, installed everywhere. Building a separate zip
per platform would instead test a `createApp.sh` code path that never ships.

All jobs use `actions/setup-java` with `distribution: liberica` and
`java-package: jdk+fx` pinned to Java 21 — the GUI module compiles against JavaFX, so a
plain JDK cannot build it.

Windows jobs run installer steps with `shell: bash`, which is Git Bash on GitHub
runners — the documented way to run `md2pdf-install.sh` on Windows.

## Assertions live in scripts, not in YAML

Two checked-in scripts hold the actual assertions; the workflow only calls them.

- `.github/scripts/verify-install.sh`
- `.github/scripts/test-install-failures.sh`

This keeps the YAML thin, makes the assertions reviewable as code, and — most
importantly — lets both scripts run locally on Linux, so the logic can be developed and
debugged without push-and-pray CI cycles.

### `verify-install.sh`

Runs two scenarios per platform against the unzipped release.

**Scenario A — a suitable JDK is on `PATH`.** The installer should use it and not pin
the app to anything.

- installer exits 0
- the app is at the platform's install path
  (`~/.local/share/MarkdownToPdf`, `~/Applications/MarkdownToPdf.app`,
  `~/MarkdownToPdf`)
- the fat-jar and the platform's run script are present
- the launcher exists: `.desktop` entry on Linux, `.lnk` on Windows; on macOS the app
  bundle's `Contents/MacOS/markdownToPdf` is present and executable
- **no** `md2pdf.env` is written

**Scenario B — a specific JDK is pinned.** Re-install with `MD2PDF_JAVA_HOME` set to a
stub JDK: a directory whose `bin/java` answers `-version` with `21.0.5` and
`--list-modules` with `javafx.controls`, which is what `_jdk_is_suitable` checks.

- `md2pdf.env` is written and contains exactly the stub path
- running the installed launcher with a clean environment resolves the stub JDK

Scenario B is the regression test for the P1 "validated `JAVA_HOME`, launched a
different java" defect. Because it installs over scenario A's result, it also exercises
the `MD2PDF_REPLACE_EXISTING=1` opt-in.

### `test-install-failures.sh`

Platform-independent, so it runs on Linux only.

- no suitable JDK on `PATH`, non-interactive → non-zero exit and a clear message,
  rather than hanging on a prompt or silently downloading a JDK
- `MD2PDF_JAVA_HOME` pointing at a non-existent path → warns, ignores it, falls back to
  `PATH` (a stale override must never mask a working JDK)
- launcher creation forced to fail → installer exits non-zero. This is the P2 defect:
  it previously printed the error and exited 0, reporting a successful install that had
  no launcher.

## Installer changes this requires

`md2pdf-install.sh` prompts with `read -rp` under `set -euo pipefail`. At EOF `read`
returns 1, which kills the script. Today `_finalize_mac`'s "Open now?" prompt therefore
aborts the installer in *any* non-TTY shell — CI, a pipe, cron — not just in CI. This
is a live bug, not merely a test-harness inconvenience.

Add non-interactive detection:

- `NONINTERACTIVE=1` when `[ ! -t 0 ]`
- a `_ask` helper that returns the documented default instead of prompting when
  `NONINTERACTIVE` is set

Defaults are the conservative choice in each case:

| Prompt | Non-interactive default |
|---|---|
| Install a JDK? | no — die with a clear message |
| Existing install: remove / keep / cancel? | cancel |
| Open the app now? | no |

Two opt-ins let a caller choose the other branch explicitly: `MD2PDF_INSTALL_JDK=1` and
`MD2PDF_REPLACE_EXISTING=1`.

## Launcher dry-run

Scenario B needs to observe which JDK a launcher resolves without starting a GUI. A
stub `java` covers this on Linux and macOS, but not on Windows: `run.cmd` looks for
`bin\javaw.exe` specifically and invokes it through `start`, so a shell stub cannot
stand in and a real launch would spawn a detached GUI process.

Add `MD2PDF_DRY_RUN` to all four launchers — `run.sh`, `run.zsh`, `run.cmd`, and the
macOS `.app` entry point `markdownToPdf`. When set, each prints the java binary it
resolved and the jar it would run, then exits 0 without launching.

This is a few lines per launcher and puts test-support code in shipped scripts. The
trade-off is accepted deliberately: it makes the JDK-resolution assertion identical on
all three platforms, and Windows JDK resolution is exactly the kind of path that rots
silently when it is the one platform left untested.

## Risks

- **macOS runner cost.** `package` and one `install` job run on `macos-latest`, which
  is billed at a higher multiplier. The jobs are short; if this becomes a problem, the
  macOS install job is the first candidate to move to a nightly schedule.
- **`mvn verify` may not be green immediately.** SpotBugs and `spotless:check` have not
  been enforceable on the maintainer's machine, so the first CI run may surface real
  pre-existing findings. Fixing those is in scope for the implementation; if the volume
  is large, `verify` lands as a separate follow-up rather than blocking the installer
  tests.
- **Shellcheck on existing scripts.** `run.sh`, `build.sh` and `createApp.sh` predate
  this work and will likely need targeted suppressions or fixes to reach green. Scope
  the initial pass to warnings that indicate real defects; do not rewrite working
  scripts to satisfy style rules.

## Verification

The two assertion scripts are the deliverable's own test suite, and both run locally on
Linux. Development order:

1. Make the installer changes; confirm `test-install-failures.sh` passes locally.
2. Build a zip locally and confirm `verify-install.sh` passes locally on Linux.
3. Add the workflow; confirm the macOS and Windows jobs pass in CI.

A deliberate red run — reverting one of the three PR #1 fixes and confirming CI catches
it — is the acceptance test for whether this work was worth doing.
