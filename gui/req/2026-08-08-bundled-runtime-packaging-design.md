# Bundled Java runtime packaging — Design

**Date:** 2026-08-08
**Status:** Approved, ready for implementation planning
**Supersedes:** `2026-08-07-ci-installer-testing-design.md`

## Context

MarkdownToPdf ships one `md2pdf-gui.zip` containing a fat-jar and launch scripts for
all three platforms. It has no Java runtime, so it requires the user to already have a
JavaFX-bundled JDK 21+ — an unusual thing to have. `md2pdf-install.sh` therefore carries
~200 lines that detect a suitable JDK, offer to download Liberica Full from BellSoft's
API, and record the result in `md2pdf.env` for the launchers to read.

That machinery is the least reliable part of the project. Two of the three P1/P2 defects
found in PR #1 review were in it: the BellSoft endpoint 404'd, and the validated
`JAVA_HOME` was never used at launch. It also makes "which JVM is the app actually
running on?" a question with a different answer on every machine.

Bundling a `jlink`-slimmed runtime built from BellSoft Liberica Full JDK 21 removes the
question entirely. It also removes the reason the release was built once on a Mac: a
runtime is platform-specific, so each platform must build its own artifact.

## Goals

- Ship a self-contained application per platform: no JDK required, nothing to install
  beyond unzipping.
- Delete the JDK detection and download machinery, and the defects living in it.
- Build, package, install and verify each platform's artifact on that platform in CI.
- Turn `release.sh` into a driver that publishes exactly the artifacts CI tested.

## Non-goals

- Apple notarization. It needs a Developer account that is applied for but not yet
  granted, and credentials that cannot be tested without it. Signing is designed for;
  notarization is a documented follow-up.
- Windows code signing. Same reasoning, no certificate.
- Architectures beyond the three chosen targets.
- Modularising the application. It stays a classpath fat-jar; `jlink` builds only the
  runtime it runs on.

## Decisions

| Decision | Choice |
|---|---|
| Runtime source | BellSoft Liberica Full JDK 21 (`jdk+fx`) |
| Bring-your-own JDK | Removed entirely, including `MD2PDF_JAVA_HOME` |
| Targets | `linux-x64`, `macos-aarch64`, `windows-x64` |
| Where `jlink` runs | Natively, one CI runner per platform |
| Installers | Three separate: bash / zsh / cmd |
| Windows shortcut | `createShortcut.ps1`, falling back to a generated stub |
| macOS signing | Ad-hoc now; Developer ID when the account lands |

### Why bring-your-own-JDK is removed rather than kept as a fallback

Keeping the existing detection as a fallback preserves the exact variance the bundled
runtime exists to eliminate, and keeps the code that produced two P1 defects. The
mitigation for users who do have a suitable JDK is at the release level, not in the
zip: the bare fat-jar remains a published release asset (see "release.sh").

### Why three installers rather than one cross-platform script

Each zip now contains exactly one platform's files, so the cross-platform script's OS
branching has nothing left to do. Splitting also removes the Git Bash requirement for
Windows end users. `cmd` was chosen over PowerShell because PowerShell execution is
frequently restricted by policy on corporate Windows machines, while `.cmd` always runs.

### Why the module list is hand-curated

Log4j, Batik, PDFBox and RichTextFX resolve classes reflectively, so `jdeps` under-reports
and would yield a runtime that fails only once a user reaches a particular feature. An
explicit list plus the smoke tests below is the safer trade.

### Why not bundle `nircmd.exe` for shortcut creation

It was considered as a `cmd`-native way to create a `.lnk`. Rejected: Microsoft Defender
ships a signature for it (`HackTool:Win32/NirCmd!MTB`) and removes it on detection. On an
already-unsigned download that turns a cosmetic failure into an apparent malware finding.
NirSoft's licence also requires redistributing its package unmodified, which sits awkwardly
in an MIT repository.

## Artifact layout

Three zips named `md2pdf-<version>-<platform>.zip` replace `md2pdf-gui.zip`.

**`…-linux-x64.zip`**

```
MarkdownToPdf/
  runtime/                                  jlink image; bin/java
  MarkdownToPdf-<ver>-with-dependencies.jar
  run.sh
  createLauncher.sh
  MarkdownToPdf-rounded.png
md2pdf-install.sh
```

**`…-windows-x64.zip`**

```
MarkdownToPdf/
  runtime/                                  jlink image; bin/javaw.exe
  MarkdownToPdf-<ver>-with-dependencies.jar
  run.cmd
  createShortcut.ps1
  MarkdownToPdf-rounded.ico
md2pdf-install.cmd
```

**`…-macos-aarch64.zip`**

```
MarkdownToPdf.app/
  Contents/
    Info.plist
    MacOS/markdownToPdf                     the only launcher
    Resources/md2pdf.icns
    runtime/                                jlink image
    app/MarkdownToPdf-<ver>-with-dependencies.jar
md2pdf-install.zsh
```

The macOS bundle becomes a valid bundle. Today the jar and `run.zsh` sit at the `.app`
root, outside `Contents/`, which works only because nothing enforces it. Everything moves
under `Contents/`, and `run.zsh` is deleted: `Contents/MacOS/markdownToPdf` becomes the
single entry point and execs the bundled runtime directly.

Expect each zip to be roughly 80–110 MB against 13.7 MB today. `javafx.web` carries the
WebKit native library, which dominates the image and does not compress.

## The jlink runtime

Modules requested explicitly:

```
javafx.controls, javafx.swing, javafx.web,
java.desktop, java.logging, java.management, java.naming,
java.net.http, java.prefs, java.scripting, java.sql, java.xml,
jdk.charsets, jdk.crypto.ec, jdk.unsupported, jdk.zipfs
```

`java.base` is implicit and `jlink` resolves transitively — `javafx.web` pulls
`javafx.media`, `javafx.graphics` and `javafx.base` on its own. The non-obvious entries:
`jdk.unsupported` provides `sun.misc.Unsafe`, which several dependencies still use;
`jdk.crypto.ec` is required for TLS, because `WebView` can load `https:` resources in a
preview; `jdk.charsets` covers Markdown files saved in legacy encodings such as
`windows-1252`. Excluded is the whole development toolchain — `jdk.compiler`, `jdk.jshell`,
`jdk.javadoc`, `jdk.jdi` — which is where most of the saving comes from.

`jdk.localedata` is deliberately omitted, saving roughly 15 MB. Consequence: `java.base`
carries only root/English locale data, so any locale-dependent date or number formatting
renders in English regardless of system settings. Reversible with
`--include-locales=en,sv` if it ever matters.

Flags: `--strip-debug --no-header-files --no-man-pages --compress=zip-6`.
`--strip-debug` affects only JDK classes; application code lives in the fat-jar, so
application stack traces keep their line numbers.

`jlink` runs from the platform's packaging script rather than from Maven, keeping the
build shell-driven and the "zero new Maven dependencies" constraint intact.

**Build scripts and installers have different audiences.** Build scripts run in CI or on a
developer machine, so the Windows packaging step may use `bash` under Git Bash.
Installers run on end-user machines, which is why `md2pdf-install.cmd` must be native
`cmd`. `createApp.sh` therefore stays a single bash script that branches on target
platform for layout and `jlink` flags.

## Launchers

All three collapse to: resolve own directory, find the newest
`MarkdownToPdf-*-with-dependencies.jar`, exec the bundled runtime. `run.sh` goes from
about 45 lines to about 8.

Deleted everywhere: `-version` output parsing, the sdkman recovery loop, `md2pdf.env`
reading and writing, `MD2PDF_JAVA_HOME`, the `osascript` "no suitable JDK" dialog in
`markdownToPdf`, and `run.zsh` as a file.

## Installers

| | `md2pdf-install.sh` | `md2pdf-install.zsh` | `md2pdf-install.cmd` |
|---|---|---|---|
| Shell | bash | zsh | cmd |
| Default target | `${XDG_DATA_HOME:-~/.local/share}/MarkdownToPdf` | `~/Applications` | `%LOCALAPPDATA%\Programs\MarkdownToPdf` |
| Launcher | `createLauncher.sh` → `.desktop` | the app bundle itself | `.lnk`, else generated stub |
| Platform extra | — | quarantine removal | — |

Common to all three:

- optional target-directory argument
- a guard against installing a directory onto itself
- replace / keep / cancel when an install already exists
- re-applying execute bits after the copy, since not every unzip tool preserves Unix
  modes — this covers `runtime/bin/*` and `runtime/lib/jspawnhelper`, not only the
  launcher script

The P2 fix survives: on Linux, if `createLauncher.sh` fails the installer exits non-zero
rather than reporting success with no launcher.

### Non-interactive behaviour

`read` at EOF under `set -euo pipefail` returns 1 and kills the script, so today's
installer aborts in any non-TTY context — CI, a pipe, cron. Each installer detects a
non-TTY and takes documented defaults: cancel on an existing install, do not open the
app afterwards. `MD2PDF_REPLACE_EXISTING=1` is the explicit opt-in to replace.

### Windows shortcut creation

`cmd` cannot create a `.lnk`. The installer runs the existing PowerShell script:

```
powershell -NoProfile -ExecutionPolicy Bypass -File createShortcut.ps1
```

**Success is determined by testing for the `.lnk` file, not by the exit code.** Exit codes
are unreliable here: `-ExecutionPolicy Bypass` is silently ignored when policy is enforced
by Group Policy, and under AppLocker's Constrained Language Mode PowerShell runs the
script but fails at `New-Object -ComObject WScript.Shell`. Checking for the artifact covers
those, plus `powershell.exe` being absent.

On failure the installer writes a stub to the Desktop and the Start Menu:

```cmd
@echo off
call "<install-dir>\run.cmd"
```

It must be a generated stub, not a copy or a link of `run.cmd`. `run.cmd` locates
everything through `%~dp0`, its own directory; a copy on the Desktop resolves `%~dp0` to
the Desktop and finds neither runtime nor jar. Symlinks and hard links have the same
problem, and additionally need administrator rights or a single volume respectively.

`createShortcut.ps1` is also updated to target `runtime\bin\javaw.exe` with the jar as an
argument, rather than `cmd.exe /c run.cmd`. This removes the console window entirely and
is now possible because the runtime is at a known path inside the install directory.

## macOS code signing

On Apple Silicon the kernel refuses to execute binaries with an absent or invalid
signature; a `jlink` image inside an unsigned `.app` dies on launch with no useful
message. The macOS packaging step therefore always signs.

Identity detection:

```
security find-identity -v -p codesigning | grep -m1 'Developer ID Application'
```

If an identity is found — locally from the login keychain, or in CI from a `.p12` imported
into a temporary keychain when a `MACOS_CERTIFICATE` secret is present — sign with it.
Otherwise ad-hoc sign with `-`. A missing identity is not an error: GitHub does not expose
secrets to workflow runs from forked pull requests, so fork builds always take the ad-hoc
path, correctly.

The two branches are not one command with a swapped argument. Ad-hoc signing may use
`--deep`; Developer ID signing must not, because Apple discourages it for distribution —
nested code (the runtime's `.dylib`s and `bin/java`) is signed first, then the bundle.

Signing is not notarization. Users still see the unidentified-developer warning, which is
why the installer continues to strip the quarantine attribute.

For the later notarization work: it requires the hardened runtime (`--options runtime`),
and a JVM under hardened runtime does not start without entitlements — at minimum
`com.apple.security.cs.allow-jit`,
`com.apple.security.cs.allow-unsigned-executable-memory`, and
`com.apple.security.cs.disable-library-validation`. The entitlements plist is committed
alongside the packaging script even while unused.

## CI workflow

`.github/workflows/ci.yml`, on push to `main` and on pull requests.

| Job | Runner | Purpose |
|---|---|---|
| `verify` | `ubuntu-latest` | `mvn verify` — tests, `spotless:check`, SpotBugs |
| `lint-scripts` | `ubuntu-latest` | `shellcheck` on bash, `zsh -n` on zsh |
| `build` | ubuntu / macos / windows | `jlink`, package, install, verify, upload zip |
| `install-failures` | `ubuntu-latest` | assert failure paths fail loudly |

All jobs use `actions/setup-java` with `distribution: liberica` and
`java-package: jdk+fx` at Java 21.

`shellcheck` refuses zsh and is not a zsh linter, so `md2pdf-install.zsh`,
`markdownToPdf` and `mkicns.zsh` get `zsh -n`, a syntax check only. `md2pdf-install.cmd`
has no linter; its only safety net is the `build` job running it.

### The `build` job

Per platform: `setup-java` → `mvn install` and the fat-jar → `createApp.sh` → upload the
zip as an artifact → unzip to a temp directory → run the platform's installer
non-interactively → run the verification script.

Building and testing in one job is sound only if the test cannot lean on the runner's
JDK — which is the property being asserted. The verification step **scrubs `JAVA_HOME` and
removes the JDK from `PATH`** first: `env -i` on Linux and macOS, an explicit
`set JAVA_HOME=` and trimmed `PATH` on Windows. Without this every assertion below is
vacuous.

Assertions:

- installer exits 0
- the app is at the platform's default install path
- `runtime/bin/java` (or `javaw.exe`) exists and is executable
- `runtime/bin/java -version` reports 21, and `--list-modules` contains `javafx.web`
- the fat-jar is present
- the launcher exists: `.desktop` on Linux, an executable
  `Contents/MacOS/markdownToPdf` on macOS, a `.lnk` or stub on Windows

### Smoke tests

A hand-curated module set makes "does it actually run" the central risk, so it is tested
directly. JDK 21 runs a single source file, so both live in `.github/scripts/` and are
never shipped.

- **Engine smoke, all three platforms.** Runs on the bundled runtime with the fat-jar on
  the classpath, converts a Markdown string to PDF through `Md2PdfEngine`, and asserts the
  bytes begin with `%PDF`. Exercises Batik, PDFBox and OpenHTMLtoPDF, and catches a missing
  `java.*` module immediately.
- **Toolkit smoke, all three platforms.** Starts the JavaFX toolkit, constructs a
  `WebView`, exits 0 — under `xvfb-run` on Linux, natively on macOS and Windows. This is
  the only assertion proving that `javafx.web`'s WebKit native library loads; a class-load
  check cannot do that.

The toolkit smoke is the most likely source of CI flakiness, particularly on Windows. It
is included anyway: a missing or broken `javafx.web` native library is precisely the
failure this change could introduce. If it proves unstable, restrict it to Linux under
Xvfb rather than deleting it.

### `install-failures`

Much smaller now that there is no JDK detection:

- forcing `createLauncher.sh` to fail must exit non-zero
- a non-interactive re-install over an existing install must cancel without clobbering it

## release.sh

`release.sh` stops building platform artifacts — a Linux machine cannot produce the macOS
or Windows runtimes — and becomes a release driver:

1. Preconditions: clean tree, on `main`, `${revision}` is not a `-SNAPSHOT`, `gh`
   authenticated.
2. `mvn -Prelease clean site deploy` — the Maven Central publication of `lib`, unchanged.
3. Tag `v<version>` and push it.
4. `gh run watch` the CI run for that commit until the three `build` jobs pass.
5. `gh run download` the three platform zips from that run.
6. Sanity-check them: all three present, non-trivial size, expected top-level entries.
7. Generate `SHA256SUMS` over the assets.
8. `gh release create v<version>` with the three zips, the checksums, `lib`'s javadoc jar,
   and the bare fat-jar.

Publishing the bare `MarkdownToPdf-<ver>-jar-with-dependencies.jar` is deliberate. Removing
bring-your-own-JDK from the zips leaves users who already have a Liberica Full JDK with no
small download; the fat-jar is 14.5 MB and already built.

Because the assets come from CI rather than from a local build, what ships is
byte-identical to what was tested — which the current manual flow cannot promise.

`release.sh` does no signing of its own. Signing happens in the macOS `build` job, so the
downloaded artifact is already signed and re-signing would break the byte-identical
guarantee above. When the Developer account lands, adding the certificate as a GitHub
secret is sufficient — `release.sh` does not change. The alternative, signing locally in
`release.sh`, keeps the certificate off GitHub but restricts `release.sh` to macOS and
gives up that guarantee.

## Risks

- **Liberica Full may not ship `jmods/`.** Everything rests on it and `jlink` cannot work
  without it. Verifying this is implementation step one.
- **The curated module set may be incomplete.** A missing module surfaces only when a user
  reaches the feature that needs it. The two smoke tests are the mitigation; anything they
  miss is found by use.
- **Download size grows roughly sevenfold.** Accepted as the cost of removing the JDK
  prerequisite.
- **Toolkit smoke flakiness**, especially on the Windows runner. Fallback is Linux-only
  under Xvfb.
- **`mvn verify` may not be green immediately.** `spotless:check` and SpotBugs have not
  been enforceable on the maintainer's machine — `google-java-format` throws
  `NoSuchMethodError` under JDK 25 — so the first CI run may surface real pre-existing
  findings. Fixing them is in scope; if the volume is large, `verify` lands separately
  rather than blocking this work.
- **Three platform builds triple the release surface.** A platform that fails to build is
  now a platform with no working download at all, since there is no bring-your-own-JDK
  fallback.

## Implementation order

1. Verify Liberica Full ships `jmods/`.
2. `jlink` and the new layout on Linux only; get both smoke tests green locally.
3. Linux installer and launcher; failure tests green locally.
4. macOS and Windows packaging and installers.
5. The CI workflow.
6. `release.sh`.
7. Documentation — both READMEs currently document JDK requirements that will cease to
   exist.

Steps 1–3 are the risky ones and are all verifiable on a Linux workstation, which keeps
the push-and-pray CI cycles for the end.
