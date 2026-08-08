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

- Ship a self-contained application per platform: no JDK required. On Windows and macOS
  nothing further is needed. On Linux the JavaFX host libraries are still required — see
  "Linux host prerequisites"; the claim "nothing to install beyond unzipping" is false
  there and must not appear in user-facing documentation.
- Delete the JDK detection and download machinery, and the defects living in it.
- Build, package, install and verify each platform's artifact on that platform in CI.
- Turn `release.sh` into a driver that publishes exactly the artifacts CI tested.

## Non-goals

- Apple notarization. It needs a Developer account that is applied for but not yet
  granted, and credentials that cannot be tested without it. Signing is designed for;
  notarization is a documented follow-up.
- Windows code signing. Same reasoning, no certificate.
- Architectures beyond the three chosen targets.
- Modularising the application. It stays on the classpath; `jlink` builds only the runtime
  it runs on.

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
| JavaFX version | Pinned to the version inside the runtime, asserted in CI |
| Compression | `--compress=2` — the only ZIP level JDK 21 accepts |
| Application assembly | `lib/` of unmodified dependency jars; the shade fat-jar is dropped |

### Why bring-your-own-JDK is removed rather than kept as a fallback

Keeping the existing detection as a fallback preserves the exact variance the bundled
runtime exists to eliminate, and keeps the code that produced two P1 defects. The
mitigation for users who do have a suitable JDK is at the release level, not in the
zip: a runtime-free `-no-jdk` archive remains a published release asset (see
"release.sh").

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
  MarkdownToPdf.jar
  lib/                                      dependency jars, unmodified
  run.sh
  createLauncher.sh
  MarkdownToPdf-rounded.png
md2pdf-install.sh
```

**`…-windows-x64.zip`**

```
MarkdownToPdf/
  runtime/                                  jlink image; bin/javaw.exe
  MarkdownToPdf.jar
  lib/                                      dependency jars, unmodified
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
    app/MarkdownToPdf.jar
    app/lib/                                dependency jars, unmodified
md2pdf-install.zsh
```

The macOS bundle becomes a valid bundle. Today the jar and `run.zsh` sit at the `.app`
root, outside `Contents/`, which works only because nothing enforces it. Everything moves
under `Contents/`, and `run.zsh` is deleted: `Contents/MacOS/markdownToPdf` becomes the
single entry point and execs the bundled runtime directly.

### Info.plist

The current `Info.plist` has three defects that must be fixed as part of this work:

**`CFBundleExecutable` is `MarkdownToPdf`; the file is `markdownToPdf`.** Finder launches
the executable named by the key, so the declared name must match the file on disk exactly.
This currently appears to work only because APFS is case-insensitive by default — on a
case-sensitive volume the app does not launch at all. The packaging script writes the
launcher and the key from one variable so they cannot diverge again.

**`CFBundleVersion` and `CFBundleShortVersionString` are hardcoded to `0.1.0`** while
`${revision}` is `0.1.1-SNAPSHOT`. `Info.plist` becomes a template with the version
substituted at package time, and the "bump here too" comment in the root `pom.xml` is
removed along with the manual step it describes.

**`${revision}` cannot be substituted verbatim.** Apple specifies both version keys as one
to three period-separated integers, so `0.1.1-SNAPSHOT` is invalid — and CI runs on
branches and pull requests where `${revision}` is exactly that. Packaging therefore
normalises rather than copies:

```
BUNDLE_VERSION = ${revision} with any -qualifier suffix removed
```

`0.1.1-SNAPSHOT` → `0.1.1`; a release version passes through unchanged. Both keys take
`BUNDLE_VERSION`, and packaging fails if the result does not match
`^[0-9]+(\.[0-9]+){0,2}$`, so a future version scheme cannot silently produce an invalid
bundle.

Snapshot-ness is not lost, only moved somewhere the format allows: `CFBundleGetInfoString`
is free-form text and carries the full `${revision}` plus the commit SHA, so a CI-built
`.app` remains identifiable as such.

**There is no `CFBundleIdentifier`.** `codesign` requires a bundle identifier to sign a
bundle, so this blocks the signing step outright — it is not merely a metadata omission.
`se.alipsa.md2pdf` is added, along with `CFBundlePackageType` (`APPL`) and `CFBundleName`,
which are conventional and required for correct Finder and Launch Services behaviour.

A CI assertion checks that `CFBundleExecutable` names a file that exists and is executable,
and that the version keys equal `BUNDLE_VERSION`.

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

Flags: `--strip-debug --no-header-files --no-man-pages --compress=2`.
`--strip-debug` affects only JDK classes; application code lives in the application and
dependency jars, so application stack traces keep their line numbers.

`--compress=2` is ZIP compression and is the strongest level JDK 21's `jlink` accepts —
its documented values are `0` (none), `1` (constant string sharing) and `2` (ZIP). The
newer `zip-<n>` syntax arrived in JDK 22 and makes JDK 21 `jlink` fail rather than fall
back, so it cannot be used here.

## JavaFX version alignment

The GUI compiles against JavaFX 23.0.2 (`gui/pom.xml`), while Liberica Full JDK 21 bundles
JavaFX 21.0.12. Compiling against a newer API than the runtime provides is a
`NoSuchMethodError` waiting for whichever user first reaches the affected call — and
nothing in the build catches it today, because JavaFX is `provided` scope and never runs
during `mvn test`.

`javafx.version` is therefore pinned to the JavaFX version inside the chosen Liberica
build, and the GUI is compiled against exactly that. Any API used from a later JavaFX
release must be found and replaced during implementation; this is a compile-time failure,
so it surfaces immediately rather than in the field.

CI asserts the alignment so it cannot silently drift when either version is bumped:
`runtime/bin/java --list-modules` reports `javafx.controls@<version>`, which must equal the
`javafx.version` property. A mismatch fails the `build` job.

The stale comment at `gui/pom.xml:24` ("don't forget to update run.sh / run.zsh / run.cmd
if javafx version is changed") is removed — the launchers no longer reference JavaFX at
all.

## Application assembly

The shade fat-jar is dropped. `maven-dependency-plugin:copy-dependencies` writes the
dependencies into `lib/` unmodified, beside `MarkdownToPdf.jar`.

**`includeScope=runtime` is mandatory, not a default.** `copy-dependencies` otherwise
copies every scope, including `provided` and `test` — which would put the JavaFX jars into
`lib/` alongside a runtime that already contains the JavaFX modules. Two copies of JavaFX,
one on the classpath and one in the module path, is not merely wasteful: it is the kind of
split that produces `LinkageError` or silently loads the wrong classes. JUnit and the test
tree would come along too.

CI asserts the resulting file set rather than trusting the configuration: no `javafx-*`
jar, no `junit`/`opentest4j`/`apiguardian` jar in `lib/`, and the file count matches the
runtime dependency list. A scope regression is invisible by inspection and this is the only
thing that catches it.
`maven-jar-plugin` already sets `addClasspath=true`; adding
`<classpathPrefix>lib/</classpathPrefix>` puts a `Class-Path` in the manifest so
`java -jar MarkdownToPdf.jar` works unchanged.

The manifest `Class-Path` is used rather than `-cp "lib/*"` because the JVM leaves
wildcard expansion order unspecified, while the manifest order is deterministic.

**Flat copying can collide, so the collision is checked for rather than designed around.**
`copy-dependencies` names each file `artifactId-version[-classifier].jar` and writes them
all into one directory, so two dependencies from different groups sharing an artifactId and
version overwrite each other — last one wins, silently. The obvious fix, `prependGroupId`,
is not available: `maven-jar-plugin` generates `Class-Path` entries with the same `simple`
layout, and there is no matching option on the archiver side, so renaming the files would
desynchronise them from the manifest that references them.

The check instead compares the two directly, in the `build` job:

- every `Class-Path` entry in the manifest resolves to an existing file in `lib/`
- the entries are unique — a duplicate name is exactly what a collision produces
- the number of files in `lib/` equals the number of entries

A collision fails all three at once: two artifacts yield two identical `Class-Path` entries
but one file. This subsumes the file-count check above and is the assertion that actually
runs.

**Packaging renames the application jar.** Maven produces
`MarkdownToPdf-<version>.jar`; packaging copies it into the zip as `MarkdownToPdf.jar`, so
launchers, `.desktop` entries and Windows shortcuts can reference a fixed path that does not
change on every version bump. Dependency jars in `lib/` keep their Maven names — they stay
identifiable, and the manifest `Class-Path` names them explicitly. The version remains
available from the jar manifest's `Implementation-Version`, which
`addDefaultImplementationEntries` already writes.

Three reasons beyond "the runtime is bundled so a single file buys nothing":

**The current fat-jar has a live multi-release defect.** It contains 30
`META-INF/versions/` entries, but the manifest regenerated by `ManifestResourceTransformer`
does not declare `Multi-Release: true`. Every versioned class is therefore ignored and the
pre-Java-9 fallbacks run instead — Log4j among them. Unmodified jars carry their own
manifests, so this cannot happen.

**The shade configuration strips `META-INF/LICENSE` and `META-INF/NOTICE` from all 45
dependencies.** Apache-2.0 §4(d) requires redistributing NOTICE contents, so the current
artifact is arguably non-compliant. Unmodified jars resolve it at no cost.

**It removes about fifteen hand-maintained filter rules and two transformers** whose
correctness silently depends on the dependency set — the kind of configuration that stops
being right without anyone noticing.

The one hazard of a `lib/` directory — duplicate classes resolved by classpath order — was
checked and does not apply: `commons-logging` is not on the runtime classpath (the POM
exclusions handle it), only `jcl-over-slf4j` is. The `commons-logging` shade filter is
obsolete.

Consequences elsewhere: the launchers reference a fixed `MarkdownToPdf.jar` instead of
searching for the newest `*-with-dependencies.jar`; the `fatjar` profile, `build.sh`'s
`-Pfatjar`, and the corresponding steps in `createApp.sh` and `release.sh` are removed.

## Linux host prerequisites

The bundled runtime removes the JDK dependency but not JavaFX's dependency on host
libraries. JavaFX on Linux requires GTK 3, and `javafx.web`'s WebKit library pulls further
shared libraries still. A `jlink` image cannot contain these; they are the user's
responsibility.

Two consequences:

- Both READMEs state the Linux prerequisites explicitly, with the install command for the
  common distribution families. "No JDK required" is accurate; "nothing to install" is not.
- `md2pdf-install.sh` runs a preflight check and reports missing libraries by name with a
  suggested install command. It warns rather than aborting — the libraries may be present
  under names the check does not recognise, and a wrong guess must not block a working
  install.

The Linux `build` job's toolkit smoke test covers this implicitly: the GitHub runner image
is a known baseline, so a missing library there is a packaging bug rather than a user
environment problem.

`jlink` runs from the platform's packaging script rather than from Maven, keeping the
build shell-driven and the "zero new Maven dependencies" constraint intact.

**Build scripts and installers have different audiences.** Build scripts run in CI or on a
developer machine, so the Windows packaging step may use `bash` under Git Bash.
Installers run on end-user machines, which is why `md2pdf-install.cmd` must be native
`cmd`. `createApp.sh` therefore stays a single bash script that branches on target
platform for layout and `jlink` flags.

## Launchers

All three collapse to: resolve own directory, exec the bundled runtime on
`MarkdownToPdf.jar`. `run.sh` goes from about 45 lines to about 8. The jar name is now
fixed, so the "find the newest matching jar" search goes too.

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

**Any existing shortcut is deleted before `createShortcut.ps1` runs.** Otherwise a
shortcut left by an earlier install — pointing at a path that has since moved, or at the
old `cmd.exe /c run.cmd` target — satisfies the existence test and the installer reports
success while the user's shortcut is broken. Deleting first makes the test mean "this run
created a working shortcut" rather than "a file with this name exists", and is more robust
than parsing a `.lnk` to validate its target, which `cmd` cannot do anyway.

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
`--deep`; Developer ID signing must not, because Apple discourages it for distribution.
Apple requires nested code to be signed inside-out: every Mach-O file within the bundle
individually, deepest first, and the bundle itself last.

"Nested code" is not only the `.dylib`s and `bin/java`. A `jlink` image also contains
`bin/keytool`, `bin/jrunscript` and any other launchers the module set brings in, plus
`lib/jspawnhelper` — which is not in `bin/` and is the one most often missed. The signing
step therefore enumerates Mach-O files rather than signing a fixed list:

```
find MarkdownToPdf.app -type f -perm -u+x -o -name '*.dylib'
```

filtered to actual Mach-O binaries via `file`, sorted deepest-first, each signed
individually before the bundle. A hardcoded list silently stops being correct the next time
the module set changes, which is exactly the kind of drift that surfaces as a rejected
notarization months later.

`codesign --verify --deep --strict` on the finished bundle is run as an assertion in the
macOS `build` job, so incomplete signing fails CI rather than reaching a user.

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
| `build` | see below | `jlink`, package, install, verify, upload zip |
| `install-failures` | `ubuntu-latest` | assert failure paths fail loudly |

All jobs use `actions/setup-java` with `distribution: liberica` and
`java-package: jdk+fx`.

**The JDK is pinned to an exact Liberica version, not to `21`.** `java-version: 21`
resolves the newest available 21.x, so a BellSoft patch release would change the bundled
JavaFX underneath us and start failing the alignment assertion on an unrelated commit —
turning a routine upstream release into a red build on a branch nobody touched.

The exact version lives in `.github/versions.env` as a single line:

```
LIBERICA_VERSION=21.0.12
```

Shell-sourcing a file does not populate a later action's inputs, so each job's first step
appends the file to `$GITHUB_ENV`, and `setup-java` then reads it:

```yaml
- run: cat .github/versions.env >> "$GITHUB_ENV"
- uses: actions/setup-java@v5
  with:
    java-version: ${{ env.LIBERICA_VERSION }}
    distribution: liberica
    java-package: jdk+fx
```

`$GITHUB_ENV` is visible to all subsequent steps, which is what makes the value reach the
`with:` block. The value must be a version `setup-java` resolves exactly — a full
`major.minor.patch`, optionally with a build (`21.0.12+10`) — never a bare major, which
would defeat the pinning. Implementation confirms the pinned version resolves on all three
runners before anything else depends on it.

That file holds only the Liberica version. `javafx.version` stays in `gui/pom.xml` as the
single source of truth for the compile-time API, and the existing assertion — the runtime's
`javafx.controls` module version must equal the `javafx.version` property — is what keeps
the two files honest. Bumping Liberica therefore fails CI until `javafx.version` is bumped
to match, which is the intended workflow rather than an obstacle.

**Runner labels are pinned, not `-latest`.** The macOS target is specifically
`macos-aarch64`, and `macos-latest` is a moving alias whose architecture GitHub has already
changed once — a future migration could silently start producing an x64 runtime under an
`aarch64` filename. The `build` matrix pins explicit versioned labels for all three
platforms, and each job asserts its architecture before packaging (`uname -m` on Linux and
macOS, `PROCESSOR_ARCHITECTURE` on Windows), failing if it does not match the artifact name
it is about to produce. Pinned labels are reviewed when GitHub deprecates an image, which
is a deliberate, visible action rather than a silent change.

`shellcheck` refuses zsh and is not a zsh linter, so `md2pdf-install.zsh`,
`markdownToPdf` and `mkicns.zsh` get `zsh -n`, a syntax check only. `md2pdf-install.cmd`
has no linter; its only safety net is the `build` job running it.

### The `build` job

Per platform: `setup-java` → `mvn install` and `copy-dependencies` → `createApp.sh` → upload the
zip as an artifact → unzip to a temp directory → run the platform's installer
non-interactively → run the verification script.

**The Linux job also uploads `lib/target/md2pdf-<version>-javadoc.jar`** as an artifact. It
is a release asset, and building it in CI is what lets `release.sh` assemble the complete
asset set before the irreversible Maven deploy rather than after it — see "release.sh". The
`verify` job already runs `mvn verify`; producing the javadoc jar needs only
`maven-javadoc-plugin:jar` in the same invocation, and a javadoc error is worth failing on
in its own right.

**The Linux job additionally produces `md2pdf-<version>-no-jdk.zip`**, since it is
platform-independent and building it three times would upload three artifacts under one
name. It is assembled from the same `MarkdownToPdf.jar` and `lib/` as the Linux zip, with
no `runtime/`, no launchers and no installer, plus `README.txt`.

It gets its own verification, and deliberately a different one: unzip it and run **both**
smoke tests on **the runner's `setup-java` JDK** — Liberica Full 21, i.e. `jdk+fx` — rather
than on a bundled runtime. That is precisely how its users will run it, and it is the only
check that the manifest `Class-Path` resolves `lib/` correctly without a runtime beside it.

Both tests are needed here, and the toolkit smoke is the important one. The engine smoke
touches no JavaFX at all, so it would pass on a plain OpenJDK and prove nothing about the
archive's actual requirement. The toolkit smoke starting a `WebView` is what demonstrates
that this archive plus a JavaFX-bundled JDK is a working installation — and, equally, that
the archive supplies no JavaFX of its own, since `lib/` is asserted to contain no `javafx-*`
jar and the JavaFX modules therefore have to come from the JDK. That is the module
resolution the archive depends on, so it is asserted rather than assumed.

The archive is also asserted to contain no `runtime/` directory — an assembly slip that
added one would produce a 100 MB "no-jdk" download.

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
- `MarkdownToPdf.jar` is present, and `lib/` contains the expected dependency count
- the launcher exists: `.desktop` on Linux, an executable
  `Contents/MacOS/markdownToPdf` on macOS, a `.lnk` or stub on Windows
- the runtime's `javafx.controls` module version equals the `javafx.version` property
- the runner architecture matches the artifact name
- macOS only: `CFBundleExecutable` names an existing executable file, both version keys
  equal `BUNDLE_VERSION` and match `^[0-9]+(\.[0-9]+){0,2}$`, `CFBundleIdentifier` is
  present, and `codesign --verify --deep --strict` passes

### Smoke tests

A hand-curated module set makes "does it actually run" the central risk, so it is tested
directly. JDK 21 runs a single source file, so both live in `.github/scripts/` and are
never shipped.

- **Engine smoke, all three platforms.** Runs on the bundled runtime against the installed
  `MarkdownToPdf.jar`, converts a Markdown string to PDF through `Md2PdfEngine`, and
  asserts the bytes begin with `%PDF`. Exercises Batik, PDFBox and OpenHTMLtoPDF, catches a
  missing `java.*` module immediately, and — because it resolves dependencies through the
  manifest `Class-Path` — also proves `lib/` is complete and correctly referenced.
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
or Windows runtimes — and becomes a release driver.

Because it now builds nothing, it runs on **either Linux or macOS**: it needs `git`, `gh`,
`mvn`, a JDK and a SHA-256 tool, and nothing platform-specific. This is a change worth
stating, since the old flow could only run on a Mac.

1. Preconditions: clean tree, on `main`, `${revision}` is not a `-SNAPSHOT`, `gh`
   authenticated, `HEAD` pushed, and a usable SHA-256 tool present (see "Checksums"). Also,
   everything that would fail *after* the irreversible step, checked *before* it: `v<version>` must not already exist locally or on the remote,
   `git push --dry-run` must succeed, and the version must not already be present on Maven
   Central. A tag collision discovered after deploying is unrecoverable in the sense that
   matters — the deploy cannot be taken back.
2. Locate the CI run for `HEAD` and `gh run watch` it until **the whole run concludes
   successfully** — not just the three `build` jobs. `verify`, `lint-scripts` and
   `install-failures` are separate gates, and a release that proceeds with failing tests or
   a broken installer-failure check defeats the point of running them. Abort if the commit
   has no run.
3. Download the five release assets from that run into one flat staging directory, **one
   `gh run download -n <name> -D "$STAGING"` call per artifact** — see below.
4. Sanity-check the staging directory: all five assets present, non-trivial size, expected
   top-level entries.
5. Generate `SHA256SUMS` into the staging directory, hashing the five assets. Every asset is
   already present, so the checksums cover the exact set that gets uploaded. Run the hashing
   command with the staging directory as the working directory so the file records bare
   basenames — see below.
6. `mvn -Prelease -pl lib -am clean site deploy` — the Maven Central publication of `lib`
   and the parent POM, and nothing else.
7. Tag `v<version>` and push it.
8. `gh release create v<version> "$STAGING"/*` — six files: the five assets and
   `SHA256SUMS`. Its synopsis is
   `gh release create [<tag>] [<filename>... | <pattern>...]`; it takes filenames or globs,
   never a directory, so the glob must be expanded by the shell. The upload is therefore
   only as complete as the staging directory, which is what step 4 exists to check.

### Downloading the assets

`gh run download <run-id> -D dir` does not produce a flat directory: "the contents of each
artifact will be extracted under separate directories based on the artifact name", and only
"if only a single artifact is specified, it will be extracted into the current directory".
Downloading the run in one call would therefore give five subdirectories, and every
subsequent step — the sanity check, `SHA256SUMS`, `gh release create` — assumes flat files.

Step 3 loops instead, downloading each artifact by name into the staging directory. Each
artifact contains exactly one file, so a per-name download lands that file directly in
`$STAGING`. **The artifact name equals the file's basename** — artifact
`md2pdf-<version>-linux-x64` contains `md2pdf-<version>-linux-x64.zip` — which keeps the
loop a list of names and makes a missing artifact fail on the download rather than as a
confusing gap in the sanity check.

### Checksums

`sha256sum` is GNU coreutils and is not present on macOS, which ships `shasum` (Perl) and
`openssl` instead. The old release flow ran on a Mac because it built the macOS app; that
reason is gone — no asset is built locally any more — but `release.sh` must not silently
acquire a Linux-only requirement, and a `command not found` at step 5 would abort a release
that is otherwise ready to go.

`release.sh` therefore resolves the tool once, at the top with the other preconditions
rather than at the point of use, so an unusable host fails before anything is downloaded:

```sh
if command -v sha256sum >/dev/null; then
  sha256() { sha256sum "$@"; }
elif command -v shasum >/dev/null; then
  sha256() { shasum -a 256 "$@"; }
else
  die "need sha256sum or shasum"
fi
```

The two produce byte-identical output — `<hash>  <name>`, two spaces — so `SHA256SUMS` does
not reveal which host built it, and either tool's `-c` verifies the other's file. `openssl
dgst` is not used as a third fallback: its default output format is different, and a host
with neither of the first two is not a host anyone releases from.

The release notes give both verification commands, because the asymmetry is the user's
problem too:

- Linux: `sha256sum -c SHA256SUMS`
- macOS: `shasum -a 256 -c SHA256SUMS`

Windows users get `certutil -hashfile <file> SHA256` and a manual comparison; `certutil`
cannot read a checksum file, so there is no `-c` equivalent to offer.

### What the release deploy publishes

`-pl lib -am` is not optional. The root POM aggregates both `lib` and `gui`, and the
`release` profile is defined on the parent, so a bare `mvn -Prelease deploy` at the root
runs the whole reactor and hands the GUI application to the central-publishing plugin
alongside the library. `gui` is not a library, is not intended for Maven Central, and does
not produce the sources and javadoc jars Central validation requires — so the deploy either
fails after doing part of the work, or succeeds and publishes an artifact that cannot be
withdrawn. `-am` is required with `-pl lib` because the parent POM is itself a published
artifact that `lib` resolves against.

As a second guard, `gui/pom.xml` sets `<skipPublishing>true</skipPublishing>` for the
central-publishing plugin, so an unqualified `mvn -Prelease deploy` at the root cannot
publish the GUI even if someone runs it by hand. The flag on the command line states the
intent; the POM setting is what enforces it.

**No asset is built locally.** An earlier draft rebuilt the javadoc jar as a side effect of
step 6, which forced staging and checksumming to happen after the irreversible deploy and
made `--skip-deploy` recovery impossible from a clean checkout — the jar simply would not
exist. Downloading it from CI with everything else removes both problems and shortens the
irreversible tail to three steps.

Maven Central receives its own javadoc jar, built and signed by the `deploy` in step 6. That
copy is not the release asset and the two are not compared; requiring them to be identical
would mean either uploading an unsigned jar to Central or signing a CI artifact locally,
both worse than tolerating two builds of the same generated documentation.

### Recovery after a partial release

Step 6 is the point of no return: Maven Central publication cannot be undone or
overwritten. The preflight in step 1 exists to move every foreseeable failure ahead of it,
and staging and checksumming now precede it as well, but steps 7–8 can still fail on
something unforeseeable — a GitHub outage, a revoked token.

`release.sh` therefore takes `--skip-deploy`, and detects on start-up that `${revision}` is
already published to Maven Central, reporting it and requiring the flag to continue. Re-running
after a late failure then skips step 6 rather than attempting a second deploy, which
would fail anyway and obscure the real problem.

Recovery works from a clean checkout because steps 3–5 are pure downloads and hashing: a
re-run rebuilds the staging directory from the same CI run, byte for byte, with no local
build of any kind.

The tag and the GitHub release are both reversible by hand, so the documented recovery is:
delete the tag and any partial release, then re-run with `--skip-deploy`. This is recorded
in `gui/readme.md` alongside the release instructions, because it will be needed on a day
when nobody wants to reason it out from first principles.

**The ordering is the point.** Deploying to Maven Central before the platform artifacts
exist and have been verified means a failed macOS signing or Windows packaging step leaves
the library published, and possibly a tag pushed, with no complete set of GUI downloads —
and Maven Central publication cannot be undone. Every fallible and reversible step
therefore runs first; the irreversible one runs only once all five assets are in hand,
checked and checksummed.

`md2pdf-<version>-no-jdk.zip` is deliberate. Removing bring-your-own-JDK from the platform
zips leaves users who already have a JavaFX-bundled JDK with no small download — roughly
15 MB against 80–110 MB.

**It contains no launchers and no installer**: `MarkdownToPdf.jar`, `lib/`, and a
`README.txt` giving the one command that runs it and the JDK it needs.

```
md2pdf-<version>-no-jdk.zip
  MarkdownToPdf.jar
  lib/
  README.txt          → java -jar MarkdownToPdf.jar (needs a JavaFX-bundled JDK 21+)
```

The platform launchers cannot be reused: they exec `./runtime/bin/java`, which this archive
does not contain. The alternative — PATH-based launchers that validate the JDK version and
check for `javafx.controls` — would reintroduce a second copy of exactly the detection logic
this design deletes, and that logic is where two of the three PR #1 defects lived. It is not
worth carrying for an audience that, by definition, already has a working JavaFX JDK and
can run `java -jar`. The manifest `Class-Path` makes that single command sufficient.

The cost is honest: a user with an unsuitable JDK gets a Java module error rather than a
friendly message. `README.txt` and the release notes state the requirement and point at the
platform zips, which need no JDK at all.

Being platform-independent, it is assembled once — in the Linux `build` job — rather than
three times.

Because every release asset comes from CI rather than from a local build, what ships is
byte-identical to what was tested — which the current manual flow cannot promise. The claim
covers all five assets, including the javadoc jar, which is why it is a CI artifact rather
than a by-product of the deploy.

`release.sh` does no signing of its own. Signing happens in the macOS `build` job, so the
downloaded artifact is already signed and re-signing would break the byte-identical
guarantee above. When the Developer account lands, adding the certificate as a GitHub
secret is sufficient — `release.sh` does not change. The alternative, signing locally in
`release.sh`, keeps the certificate off GitHub but restricts `release.sh` to macOS and
gives up that guarantee.

## Risks

- ~~**Liberica Full may not ship `jmods/`.**~~ Resolved: verified present, 76 modules.
- **Downgrading JavaFX 23.0.2 → 21.0.12 may break compilation.** The extent is unknown until
  the version is changed and the GUI recompiled. If the code turns out to depend
  substantially on post-21 JavaFX APIs, the alternative is a Liberica Full JDK whose
  bundled JavaFX is new enough — which changes the runtime baseline this design is built
  on. Establishing this early is why it precedes the packaging work.
- **Linux host libraries are outside our control.** GTK 3 and the WebKit dependencies must
  be present on the user's machine. The preflight check reports them but cannot install
  them, so some Linux users will still hit a failure the zip cannot prevent.
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

1. ~~Verify Liberica Full ships `jmods/`, and record the JavaFX version it bundles.~~
   **Done 2026-08-08.** Confirmed against `21.0.12.fx-librca`: `jmods/` contains 76 modules
   including all seven `javafx.*` ones, and the bundled JavaFX is **21.0.12**.
2. Pin `javafx.version` to 21.0.12 and recompile the GUI. Resolve any post-21 API usage.
   This gates everything else: if it cannot be resolved, the runtime baseline changes and
   the design needs revisiting before any packaging work is done.
3. Replace the shade fat-jar with `copy-dependencies` into `lib/` and a manifest
   `Class-Path`. Independent of the runtime work and verifiable on its own — the app should
   still start from `java -jar MarkdownToPdf.jar` on a **JavaFX-bundled JDK 21** (the
   maintainer's `21.0.12.fx-librca` is one). Not on a plain OpenJDK: JavaFX is `provided`
   scope and deliberately absent from `lib/`, so the JavaFX modules must come from the JDK.
4. `jlink` and the new layout on Linux only; get both smoke tests green locally.
5. Linux installer and launcher, including the host-library preflight; failure tests green
   locally.
6. macOS packaging: bundle layout, the `Info.plist` fixes, and signing.
7. Windows packaging and installer.
8. The CI workflow.
9. `release.sh`, plus the `skipPublishing` guard in `gui/pom.xml`.
10. Documentation — both READMEs currently document JDK requirements that will cease to
    exist, and must gain the Linux prerequisites.

Steps 2–5 are the risky ones and are all verifiable on a Linux workstation, which keeps the
push-and-pray CI cycles for the end. Step 2 is cheap and can invalidate the rest, so it
comes first.

`mvn verify` is green on the maintainer's machine when `JAVA_HOME` points at the Liberica
21 JDK; the `google-java-format` `NoSuchMethodError` recorded earlier was a JDK 25 problem
only.
